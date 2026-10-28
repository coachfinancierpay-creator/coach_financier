# AI guide — Mortgage

## Structure

The file contains an `offres` list covering fixed-rate mortgages, regulated loans, bridging loans, in fine, home savings and BFM advantages.

## Classification and project

- `type_credit`: nature of the financing.
- `cible`: mainly targeted profile.
- `projets_adaptes`: operations compatible according to the catalogue.
- the `_sous_conditions` suffixes mean an additional check is required.

## Eligibility and conditions

The file uses both `eligibilite` and `conditions`.

`eligibilite` groups in particular first-time purchase, income, PEL/CEL and regulatory conditions.

`conditions` appears in particular on the fixed-rate loan and the bridging loan. Interpret the concrete keys (`majeur`, `compte_sg`, `bien_actuel_en_vente`, `acceptation_dossier`) as indicated prerequisites, without extrapolating beyond them.

## Rate

- `type: fixe`: fixed rate.
- `taux_personnalise: true`: no universal rate may be invented.
- `taux_interet_pct: 0`: interest rate indicated as zero for the loan concerned.
- `type: plafonne`: the catalogue indicates a capped rate, without necessarily providing the customer's rate.
- `determination`: explains how the rate is determined.

Do not confuse interest rate and APR when an APR is not provided.

## Financing

- `jusqua_pct_operation`: maximum share of the operation indicated.
- `finance_totalite_operation: false`: additional financing required.
- `quotite_indicative`: indicative proportions; do not turn them into an automatic entitlement.
- `montant.maximum_pct_valeur_bien_a_vendre`: ceiling relative to the property to be sold for the bridging loan.

## Term

Loans may use `minimum_annees`/`maximum_annees` or `maximum_mois`.

`maximum_mois_vefa_ou_construction` is a maximum term specific to the case indicated.

`duree_indicative` means the term is presented as indicative.

## Repayment and flexibility

- `mensualites_modulables`, `report_echeances_possible`, `paliers_remboursement`: flexibilities indicated.
- for an in fine loan, `capital: fin_du_pret` means the capital is repaid at maturity according to the catalogue.
- for the bridging loan, partial/total deferral and repayment of the capital at the end of the loan are described separately.

## Guarantees

`garanties_possibles` lists possible forms of guarantee. "Possible" does not mean they are all available or selected for each case file.

## Regulated loans

PTZ, PAS, Subsidised Loan, Home Savings Loan and Eco-PTZ include regulatory eligibility rules. The AI must:
1. check the project's suitability;
2. check the known conditions;
3. report the regulatory conditions that cannot be determined;
4. never declare eligibility as certain from insufficient customer information.

`doit_etre_associe_a` is an explicit association constraint from the catalogue.

## BFM

`sg_bfm_avantages_immo` is described as a set of advantages for certain public sector customers, and not as a classic standalone mortgage.

`produits_concernes` references product identifiers. If a reference is not defined in the file, do not invent its characteristics.

## Recommendation

Reasoning priority:
1. property project;
2. eligibility for regulated schemes;
3. particular situation (property to sell, rental investment, PEL/CEL, public sector);
4. main financing;
5. comparison of solutions explicitly requested by `comparaison_obligatoire_avec`;
6. points of vigilance, in particular for in fine.

For in fine, the `cout_total`, `risque_capital_a_rembourser_a_echeance` and `necessite_epargne_ou_garantie_associee` elements must be considered important points of vigilance.
