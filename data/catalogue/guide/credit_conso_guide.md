# AI guide — Consumer loan

## Structure

The file contains a `catalogue` then an `offres` list. Each offer is identified by `id` and `nom`.

## Classification fields

- `famille`: functional family (personal loan, car, student, consolidation, BFM, etc.).
- `type_credit`: how the credit works, for example amortising, revolving or consolidation.
- `cible`: profiles the offer is intended for.
- `distributeur`, `preteur`: distinguish SG distribution from the lender when Banque Française Mutualiste is involved.

## Suitability to the need

- `projets_adaptes`: uses for which the offer is relevant.
- `projets_exclus`: explicitly excluded projects.
- `credits_exclus`: categories of credit that cannot be included in a consolidation operation.
- `besoin_complementaire_possible`: indicated possibility of including an additional need.

`projets_adaptes` does not on its own imply that the credit is legally earmarked for the purchase.

## Eligibility

`eligibilite` contains the known criteria: age, status, higher education enrolment, public sector/BFM membership, SG account, etc.

`cible` is a positioning; `eligibilite` must be considered stronger when determining access to the offer.

A value such as `"limite_age": null` means the limit is not documented in the catalogue.

## Amount

In `montant`:
- `minimum` / `maximum`: bounds indicated;
- `maximum_public`: published maximum; `null` = not documented;
- `reponse_principe_immediate_jusqua`: threshold linked to the approval in principle, not the credit ceiling;
- `minimum_virement`: minimum amount of a drawdown/transfer;
- `montant_unique`: identical minimum and maximum correspond to a single amount;
- `determination`: textual rule for determining the amount;
- `devise`: monetary unit.

## Term

- `minimum_mois`, `maximum_mois`: term range.
- `duree_unique`: indicates a fixed term when both bounds are identical.

## Rate and cost

In `taux`:
- `type`: fixed or other behaviour;
- `taeg` / `taeg_fixe_pct`: APR documented in the catalogue;
- `depend_du_dossier`: the displayed rate must not be presented as guaranteed for all customers;
- `depend_de_la_duree`: the rate depends on the term;
- `conditions_preferentielles`: signals preferential positioning.

`frais_dossier: 0` means the catalogue indicates zero arrangement fees for the offer concerned.

## Revolving credit

For Alterna, `fonctionnement` describes the reserve:
- `reserve_reutilisable`;
- interest on the amounts used;
- cost when the reserve is not used.

Do not compare a revolving credit and an amortising loan on the monthly payment alone. The `vigilance` rule explicitly requires a comparison of the total cost.

## Flexibility and repayment

`flexibilite` may contain:
- adjustable monthly payments;
- instalment deferral;
- grace period;
- early repayment;
- maximum number of deferrals.

`remboursement_anticipe` may also be an object with `possible` and `sans_frais`.

## Insurance and surety

- `assurance_emprunteur.disponible`: insurance offered.
- `assurance_emprunteur.obligatoire`: obligation indicated in the catalogue.
- `caution.generalement_requise`: do not turn "generally" into "always".
- `alternative_sans_caution_personnelle`: alternative solution described for the student loan.

## Recommendation rules

Apply eligibility first, then:
- `forte_priorite_si` / `priorite_tres_elevee_si`;
- `priorite_si`;
- `comparaison_obligatoire_avec`;
- `eviter_si`;
- `vigilance`.

Important cases:
- an eligible student must have the student loan compared with a generic personal loan;
- a BFM-eligible customer must have the corresponding BFM offers examined;
- a loan consolidation must be assessed on the total cost, not only on the reduction of the monthly payment;
- a recurring need for small cash amounts may correspond to revolving credit, but the cost vigilance remains a priority.

## Missing values

Do not invent a maximum, an APR or a term when the field is absent or `null`.
