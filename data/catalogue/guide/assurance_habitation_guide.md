# AI guide — SG Home Insurance

## Structure

The JSON uses `produits` to contain a **single product object**, despite the plural name. This object contains `cibles`, `formules`, the possible guarantees, the additional protections, an advantage linked to the SG loan and recommendation rules.

## Targets

At product level, `cibles` lists the main profiles: tenant, owner-occupier, student and non-occupant owner.

At plan level, the field is `cible` in the singular and specifies the associated profiles.

Do not confuse `cibles` with an eligibility condition.

## Plans

- `Initial`: `niveau_protection: 1`, essential positioning.
- `Comfort`: level 2, intermediate.
- `Optimal`: level 3, enhanced.
- `Student`: student housing positioning.
- `Non-occupant owner`: landlord/rental investor target and specific information.

`positionnement` describes the plan's commercial role; it does not on its own provide the detailed list of its guarantees.

## Possible guarantees

`garanties_possibles` is a product-level list. In this JSON it does not specify exactly which guarantees belong to Initial, Comfort or Optimal.

The AI must therefore **not invent a guarantee matrix per plan**.

Same rule for `protections_complementaires`: they are described as possible at product level, not necessarily included in every plan.

## Non-occupant owner plan

The non-occupant owner plan contains:
- a price `a_partir_de_mensuel`;
- `eligibilite`;
- `contrat`.

The price is indicative.

`eligibilite` must be checked: natural person, of legal age, holder of an SG account and maximum number of rooms according to the catalogue data.

## Contract

`duree_initiale_mois` and `renouvellement_tacite` describe the contractual behaviour indicated for the plan where these fields appear. Do not automatically generalise them to the other plans if the JSON does not say so.

## Banking Peace of Mind Plus

`avantage_credit_sg` describes **possible** coverage, subject to conditions, of SG mortgage monthly payments in the event of a serious claim.

`maximum_mensualites` is a maximum indicated, not automatic coverage.

## Recommendation rules

The `si_*` fields are conditional rules:
- tenant → take the indicated obligation into account;
- co-ownership owner → third-party liability indicated as mandatory;
- owner of a detached house → insurance indicated as not legally mandatory but strongly recommended;
- rental investment → propose the non-occupant owner plan.

These rules guide the choice of type/plan. They do not allow deducing the detailed guarantees absent from the JSON.

## Important point for the AI

This file is less detailed per plan than the car insurance file. The AI must recognise this limit and say that a specific characteristic is not documented rather than arbitrarily attributing a guarantee to Initial, Comfort or Optimal.
