# -*- coding: utf-8 -*-
"""Crée un second JDD de démonstration à faibles revenus.

Le JDD source reste inchangé. Les mêmes opérations et libellés sont conservés,
mais les montants sont redimensionnés et les soldes recalculés.
"""
import json
import shutil
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "data" / "transaction"
OUT = ROOT / "data" / "jdd2"
TX_OUT = OUT / "transaction"
TARGET_SAVINGS = Decimal("2200.00")
TARGET_INCOME = Decimal("1300.00")
MIN_BALANCE = Decimal("50.00")
MAX_BALANCE = Decimal("280.00")
LOAN_MAX = Decimal("350.00")


def d(value):
    return Decimal(str(value or 0))


def money(value):
    return float(d(value).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))


def ratio(value):
    return float(d(value).quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP))


def is_loan(label):
    label = (label or "").upper()
    return "ECHEANCE PRET" in label or "MENSUALITE" in label


def is_savings_transfer(label):
    return "COMPTE EPARGNE" in (label or "").upper()


def add_adjustment(ops, date, amount, credit=False):
    if amount <= 0:
        return
    prefix = "CREDIT AJUSTEMENT BUDGET JDD2" if credit else "DEBIT AJUSTEMENT BUDGET JDD2"
    ops.append({
        "date": date,
        "nature_operation": prefix,
        "debit": None if credit else money(amount),
        "credit": money(amount) if credit else None,
        "transactionId": f"TX-JDD2-AJUST-{len(ops)+1:04d}",
        "accountId": "ACC-COURANT-JDD2",
    })


def main():
    OUT.mkdir(exist_ok=True)
    TX_OUT.mkdir(exist_ok=True)
    files = sorted(SOURCE.glob("transactions_*.json"))
    if len(files) != 14:
        raise RuntimeError(f"14 fichiers attendus, {len(files)} trouvés")

    opening = Decimal("180.00")
    savings_balance = Decimal("0.00")
    summaries = []
    total_transactions = 0

    for source_file in files:
        source = json.loads(source_file.read_text(encoding="utf-8"))
        original = source["transactions"]
        month_opening = opening
        original_credits = sum(d(op.get("credit")) for op in original)
        credit_scale = min(Decimal("1"), TARGET_INCOME / original_credits) if original_credits else Decimal("1")
        ops = []
        running = opening

        for op in original:
            new_op = dict(op)
            new_op["accountId"] = "ACC-COURANT-JDD2"
            label = op.get("nature_operation", "")
            if op.get("credit") is not None:
                amount = (d(op["credit"]) * credit_scale).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
                new_op["credit"] = money(amount)
                running += amount
            elif op.get("debit") is not None:
                original_amount = d(op["debit"])
                if is_savings_transfer(label):
                    amount = (original_amount * Decimal("0.10")).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
                elif is_loan(label):
                    # Le crédit du JDD2 est contractuellement de 350 € par mois :
                    # la transaction et la synthèse doivent porter exactement ce montant.
                    amount = LOAN_MAX
                else:
                    amount = (original_amount * Decimal("0.12")).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
                new_op["debit"] = money(amount)
                running -= amount
            ops.append(new_op)

        # Le solde reste réaliste et positif sans modifier les opérations originales.
        adjustment_date = source["transactions"][-1].get("date", "28/02/2026")
        if running > MAX_BALANCE:
            surplus = running - Decimal("200.00")
            transfer = min(surplus, TARGET_SAVINGS - savings_balance)
            if transfer > 0:
                add_adjustment(ops, adjustment_date, transfer, credit=False)
                ops[-1]["nature_operation"] = "VIREMENT VERS COMPTE EPARGNE JDD2"
                savings_balance += transfer
            remaining_adjustment = surplus - transfer
            if remaining_adjustment > 0:
                add_adjustment(ops, adjustment_date, remaining_adjustment, credit=False)
            running = Decimal("200.00")
        elif running < MIN_BALANCE:
            add_adjustment(ops, adjustment_date, Decimal("200.00") - running, credit=True)
            running = Decimal("200.00")

        debit_total = sum(d(op.get("debit")) for op in ops)
        credit_total = sum(d(op.get("credit")) for op in ops)
        consumption_total = sum(
            d(op.get("debit")) for op in ops if not is_savings_transfer(op.get("nature_operation", ""))
        )
        closing = running.quantize(Decimal("0.01"))
        wrapper = {
            "periode": source["periode"],
            "year": source["year"],
            "month": source["month"],
            "transactionCount": len(ops),
            "transactions": ops,
        }
        (TX_OUT / source_file.name).write_text(json.dumps(wrapper, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        total_transactions += len(ops)
        summaries.append({
            "month": source["periode"],
            "openingBalance": money(month_opening),
            "income": money(credit_total),
            "expenses": money(consumption_total),
            "savings": money(credit_total - consumption_total),
            "balance": money(closing),
            "debitTotal": money(debit_total),
            "creditTotal": money(credit_total),
            "transactionCount": len(ops),
        })
        opening = closing

    incomes = [d(x["income"]) for x in summaries]
    expenses = [d(x["expenses"]) for x in summaries]
    average_income = sum(incomes) / len(incomes)
    average_expenses = sum(expenses) / len(expenses)
    average_savings = average_income - average_expenses
    savings_ratio = average_savings / average_income if average_income else Decimal("0")
    last_three_income = sum(incomes[-3:])
    last_three_savings = sum(incomes[-3:]) - sum(expenses[-3:])
    savings_ratio_3_months = last_three_savings / last_three_income if last_three_income else Decimal("0")
    synthesis = {
        "customer": {"customerId": "DEMO002", "bfm_eligible": False, "student": False, "nom": "Durand", "prenom": "Alex", "age": 29},
        "period": {"from": "2025-07-01", "to": "2026-08-31"},
        "accounts": [{"accountId": "ACC-COURANT-JDD2", "balance": summaries[-1]["balance"]}],
        "epargne": [{"accountId": "LIVRET-A-JDD2", "libelle": "Livret A", "type": "COMPTE_EPARGNE", "currency": "EUR", "date": "31/08/2026", "solde": money(savings_balance)}],
        "credits": [{"produit": "crédit_immobilier", "mensualite": money(LOAN_MAX), "fin": "2035-12-31"}],
        "savingsTotal": money(savings_balance),
        "monthlySummaries": summaries,
        "global": {
            "periodMonths": len(summaries), "periodStart": "2025-07-01", "periodEnd": "2026-08-31",
            "averageMonthlyIncome": money(average_income),
            "averageMonthlyExpenses": money(average_expenses),
            "averageMonthlySavings": money(average_savings),
            "averageDisposableIncome": money(average_savings),
            "currentAccountBalance": summaries[-1]["balance"], "savingsTotal": money(savings_balance),
            "monthlyLoanPayments": money(LOAN_MAX), "debtServiceToIncomeRatio": ratio(LOAN_MAX / average_income),
            "savingsToIncomeRatio": ratio(savings_ratio),
            "savingsToIncomeRatio3Months": ratio(savings_ratio_3_months),
            "savingsRatePeriodLabel": "06/2026 – 08/2026",
            "overdraftOccurrences": 0, "minimumObservedBalance": money(min(d(x["balance"]) for x in summaries)),
            "transactionCount": total_transactions,
        },
    }
    (OUT / "synthese_financier.json").write_text(json.dumps(synthesis, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"JDD2 créé dans {OUT} : {len(files)} mois, {total_transactions} transactions")
    print(f"Revenu mensuel max={max(incomes):.2f} €, solde max={max(d(x['balance']) for x in summaries):.2f} €, épargne=2 200.00 €, crédit=350.00 €")


if __name__ == "__main__":
    main()

