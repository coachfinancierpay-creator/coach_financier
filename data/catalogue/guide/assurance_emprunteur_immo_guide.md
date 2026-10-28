# AI guide — SG Mortgage Protection Insurance

## Structure

The JSON uses `produits` for a single product object. It contains:
- objectives;
- obligation;
- pricing;
- plans;
- guarantee dictionary;
- insured share;
- insurance delegation;
- subscription;
- recommendation rules.

## Obligation

The `obligation` block explicitly distinguishes:
- `obligatoire_legalement: false`;
- `generalement_exigee_par_sg_pour_octroi_credit: true`.

These two statements are not contradictory. The AI must preserve this distinction and must not simply say "the insurance is legally mandatory".

## Pricing

- `cotisation_constante: true`: characteristic indicated.
- `tarif_personnalise: true`: never invent a generic price.

## Plans and targets

The plans distinguish:
- primary or secondary residence;
- rental investment;
- senior;
- BFM.

`cible` guides towards the plan corresponding to the project/profile.

`eligibilite_indicative` is deliberately described as indicative: the age provided must be used to guide/check, not to invent additional conditions.

## Guarantees

The global `garanties` dictionary defines the acronyms and some descriptions:
- death;
- PTIA;
- ITT;
- ITP;
- IPT;
- IPP;
- family support;
- loss of employment.

A plan may use `garanties` or `garanties_principales`. Respect this difference in wording.

The primary/secondary residence plan lists more guarantees; rental investment and senior list death and PTIA as main guarantees in this catalogue.

For BFM, the guarantees are split between `residence_principale` and `investissement_locatif_ou_senior`.

## Options

`perte_emploi` appears in `options` for the primary/secondary residence plan and the global dictionary marks it as optional. It must not be presented as automatically included.

## Insured share

`quotite.description` defines the insured share as the percentage of the insured capital for each borrower.

The `exemple_couple` and `couverture_renforcee_possible` blocks are possible examples/configurations. Do not assume that a 50/50 or 100/100 split automatically suits every case file.

## Insurance delegation

The catalogue indicates:
- delegation possible;
- change during the loan possible;
- condition of equivalence of guarantees with SG requirements;
- response time indicated in business days.

The AI must present the equivalence condition when it mentions delegation.

## Subscription

`souscription` indicates the online/electronic signature possibilities and the existence of medical formalities if necessary. Do not state that no medical formality will be requested.

## Recommendation rules

The global rules are conditional:
- primary residence loan → propose the corresponding plan;
- rental investment → propose the corresponding plan;
- age 65+ → check the senior offer;
- civil servant → check the BFM insurance.

The verb `verifier` (check) is important: it does not mean the customer is automatically eligible.

## Recommendation

To choose a plan, determine at least:
1. use of the financed property;
2. senior profile or not according to the available information;
3. possible membership of the BFM profile;
4. necessary/required guarantees;
5. desired insured share or share to be studied;
6. possible need for the loss of employment option.

Never invent a price or a guarantee absent from the plan.
