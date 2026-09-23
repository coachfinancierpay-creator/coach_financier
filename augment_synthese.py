# -*- coding: utf-8 -*-
"""Ajoute à la synthèse les champs structurés consommés par FinancialSummary."""
import json
import statistics
from pathlib import Path

path = Path("data/synthese_financier.json")
data = json.loads(path.read_text(encoding="utf-8"))
months = data.get("monthlySummaries", [])
incomes = [float(m.get("income") or 0) for m in months]
expenses = [float(m.get("expenses") or 0) for m in months]
savings = [float(m.get("savings") or 0) for m in months]
balances = [float(m["balance"]) for m in months if m.get("balance") is not None]
categories = {}
for month in months:
    for key, value in (month.get("categories") or {}).items():
        categories[key] = categories.get(key, 0) + float(value or 0)

average_income = round(statistics.mean(incomes), 2) if incomes else 0
average_expenses = round(statistics.mean(expenses), 2) if expenses else 0
average_savings = round(statistics.mean(savings), 2) if savings else 0
fixed_categories = {"HOUSING", "UTILITIES", "TELECOM", "TAXES", "INSURANCE", "LOAN"}
fixed_expenses = round(sum(v for k, v in categories.items() if k in fixed_categories) / len(months), 2) if months else 0
monthly_loan_payments = round(sum(float(c.get("mensualite") or 0) for c in data.get("credits", [])), 2)
current_balance = float((data.get("accounts") or [{}])[0].get("balance") or 0)
savings_balance = float(data.get("savingsTotal") or 0)
last_three = months[-3:]
last_income = sum(float(m.get("income") or 0) for m in last_three)
last_savings = sum(float(m.get("savings") or 0) for m in last_three)
monthly_categories = {k: round(v / len(months), 2) for k, v in categories.items()} if months else {}
top_expenses = [item for month in months for item in (month.get("topExpenses") or [])]
largest = max(top_expenses, key=lambda item: float(item.get("amount") or 0), default={})

summary = {
    "periodMonths": len(months),
    "periodStart": data.get("period", {}).get("from"),
    "periodEnd": data.get("period", {}).get("to"),
    "averageMonthlyIncome": average_income,
    "medianMonthlyIncome": round(statistics.median(incomes), 2) if incomes else 0,
    "averageMonthlyExpenses": average_expenses,
    "fixedExpenses": fixed_expenses,
    "variableExpenses": round(max(0, average_expenses - fixed_expenses), 2),
    "averageMonthlySavings": average_savings,
    "averageDisposableIncome": average_savings,
    "currentAccountBalance": current_balance,
    "savingsBalance": savings_balance,
    "monthlyLoanPayments": monthly_loan_payments,
    "debtServiceToIncomeRatio": round(monthly_loan_payments / average_income, 4) if average_income else 0,
    "savingsToIncomeRatio": round(average_savings / average_income, 4) if average_income else 0,
    "overdraftOccurrences": 0,
    "minimumObservedBalance": round(min(balances), 2) if balances else 0,
    "largestRecentExpense": round(float(largest.get("amount") or 0), 2),
    "largestRecentExpenseLabel": largest.get("label", ""),
    "transactionCount": None,
    "averageMonthlyExpensesByCategory": monthly_categories,
    "savingsToIncomeRatio3Months": round(last_savings / last_income, 4) if last_income else 0,
    "savingsRatePeriodLabel": f"{last_three[0].get('month')} – {last_three[-1].get('month')}" if last_three else "",
}
data["financialSummary"] = summary
data.setdefault("global", {}).update(summary)
data["global"]["savingsTotal"] = savings_balance
path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Synthèse enrichie : {len(summary)} champs, {len(months)} mois")

