# AI guide — Savings and investments

## Purpose

The file covers very different products: regulated/non-regulated savings accounts, home savings, term deposits, life insurance, PER and equity wrappers. They must not be ranked on yield alone.

## Main comparison criteria

To recommend a product, analyse:
1. the customer's goal;
2. liquidity needs;
3. horizon;
4. risk/capital loss tolerance;
5. eligibility;
6. documented taxation;
7. minimum/ceiling payment;
8. yield when documented;
9. recommendation rules.

## Risk and guarantee

The schema is not uniform:
- `risque: "nul"` is a simple value;
- `risque: "perte_capital_possible"` indicates a risk of loss;
- for life insurance/PER, `risque` may be an object distinguishing the euro fund and the unit-linked funds;
- `capital_garanti` is a boolean when provided.

Never apply the euro fund guarantee to unit-linked funds.

## Liquidity and availability

- `liquidite: immediate` / `disponible`: funds presented as available.
- life insurance describes `rachat_partiel` and `rachat_total`.
- the PER uses `disponibilite` and indicates a lock-up in principle until retirement with cases of release.
- the term deposit indicates an `retrait_anticipe` with possible penalties.

Emergency savings must favour availability before the search for yield.

## Payments and ceilings

`versement` varies by product:
- `minimum`, `minimum_ouverture`, `initial_minimum`;
- periodic payments;
- `plafond` / `plafond_reglementaire`;
- `unique` payment for the term deposit.

`plafond: null` means ceiling not documented in the catalogue, not a certain absence of ceiling.

## Yield

The fields may be:
- `taux_net_annuel_pct`;
- `taux_standard_brut_pct`;
- `prime_fidelite_brute_pct`;
- rate by bracket;
- `taux: "taux_SG_en_vigueur"`;
- `taux_public: false`.

Respect `brut` versus `net`. Do not compare a gross rate directly with a net rate without allowing for this difference.

`date_taux` indicates the date associated with the documented rate and must be taken into account for data likely to change.

## Taxation

The file describes only certain aspects:
- tax/levy exemption for certain savings accounts;
- taxable nature for certain savings accounts;
- five-year advantage for the PEA;
- deductibility under conditions for the PER.

Do not automatically supplement with tax rules that are not present if the answer must remain strictly based on the catalogue.

## Regulated savings

For Livret A, LDDS, LEP and Livret Jeune, `eligibilite` and `nombre_maximum_par_personne` are structuring constraints.

The LEP carries a very high priority subject to eligibility. Do not recommend it as accessible without checking the income condition.

## Home savings

CEL and PEL include `droits_a_pret_immobilier`. This is an advantage linked to the product, not a guarantee of obtaining a loan.

The PEL has a `horizon` with maximum payment duration and automatic closure indicated in the catalogue.

## Life insurance

Érable Essentiel and Séquoia indicate a euro fund and unit-linked funds. The presence of unit-linked funds implies the indicated risk of loss.

`gestion` describes the management modes offered.

Ébène is described mainly by its `positionnement` and its recommendation rules; do not invent absent thresholds or underlying funds.

## PER

`objectif`, `disponibilite`, `sortie`, `fiscalite` and `risque` are central. The need for short-term liquidity and the absence of emergency savings are explicitly reasons to avoid the product.

## PEA, PEA-PME and CTO

These products carry a risk of capital loss.

- `horizon_recommande.minimum_annees` is a recommended horizon.
- `supports` describes the accessible instruments.
- the CTO is notably positioned when the PEA does not match the assets sought or when access to international markets is desired.

Do not recommend these products as emergency savings when the catalogue rules indicate the opposite.

## Priorities

`priorite_avant` and `prioritaire_par_rapport_a` express an explicit preference. Use them after checking eligibility.

Yield must never be the only criterion: risk, liquidity, horizon and taxation must be taken into account.
