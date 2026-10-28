# AI guide — SG Car Insurance

## Structure

The JSON contains a `produit` object with several `formules`, then `reductions` and a general `assistance_generale`.

A plan's characteristics and the product's general characteristics must be combined without assuming an option is included when it is only listed as an option.

## Plans

Each plan has:
- `id`, `nom`;
- `niveau_protection` when provided;
- `tarif_indicatif_mensuel`;
- `garanties`;
- `assistance`;
- `options`;
- `regles_recommandation`.

The protection level is used to order the levels described, but does not replace comparing the guarantees.

## Price

`tarif_indicatif_mensuel.a_partir_de` is an indicative monthly price from which the plan is presented. It is not a personalised quote or a guaranteed rate.

The prices of the `options` are also indicative.

## Guarantees

The `garanties` list describes the guarantees included in the plan according to the catalogue.

Do not confuse:
- a guarantee included in `garanties`;
- a guarantee/service in `options`;
- an item in `avantages`.

An option must not be presented as included as standard.

## Assistance

- `accident_km: 0`: accident assistance indicated from 0 km.
- `panne_km: 25`: breakdown threshold indicated.
- some plans add services (`remise_route_rapide`, `service_voiture_a_domicile`).

`assistance_generale` contains characteristics common to the product: availability, breakdown objective, garage network, etc.

## Compensation

`minimum_indemnisation` and the `indemnisation` object describe the levels indicated. `valeur_a_neuf_jusqua_mois` must be interpreted as a maximum duration indicated and not as a universal guarantee outside contractual conditions.

## Leasing

The `Leasing` plan has an explicit `cible` (`vehicule_leasing`, `loa`, `lld`) and a `priorite_tres_elevee_si`. For a vehicle under LOA/LLD, it must therefore be examined as a priority.

## Discounts

Each `reductions` entry contains an identifier, a percentage and `conditions`.

A discount must only be applied if its conditions are met. The file does not necessarily state that several discounts can be combined: do not assume they stack.

`reduction_pct_maximum` means maximum discount and not systematic discount.

## Recommendation

Compare in particular:
- budget;
- vehicle age and value;
- new/recent/older vehicle;
- need for own-damage cover;
- leasing;
- desired guarantees;
- assistance needs;
- desired options.

The `priorite_si` rules are relevance signals, not eligibility criteria.

Do not automatically select the most protective plan: the recommendation must take the need and the budget into account.
