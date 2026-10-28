package com.coach.financier.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Libellés métier du module Marketing : la page ne doit jamais afficher un code technique brut
 * comme {@code REAL_ESTATE · NO_SUITABLE_PRODUCT} (retour utilisateur).
 */
class MarketingLabelsTest {

    @Test
    @DisplayName("les codes connus sont traduits en libellés métier lisibles")
    void knownCodesAreTranslated() {
        assertThat(MarketingModels.projectTypeLabel("REAL_ESTATE")).isEqualTo("Real estate project");
        assertThat(MarketingModels.projectTypeLabel("VEHICLE")).isEqualTo("Vehicle purchase");
        assertThat(MarketingModels.unmetReasonLabel("NO_SUITABLE_PRODUCT")).isEqualTo("No offer suited to the need");
        assertThat(MarketingModels.rejectionReasonLabel("PRICE")).isEqualTo("Price / cost too high");
        assertThat(MarketingModels.missingInfoReasonLabel("MISSING_PRICING_INFORMATION")).isEqualTo("Pricing not documented");
        assertThat(MarketingModels.interestReasonLabel("RATE_REQUEST")).isEqualTo("Rate request");
        assertThat(MarketingModels.productFamilyLabel("MORTGAGE")).isEqualTo("Mortgage");
        assertThat(MarketingModels.productFamilyLabel("SAVINGS_PRODUCT")).isEqualTo("Savings product");
    }

    @Test
    @DisplayName("un code inconnu est humanisé, jamais renvoyé brut")
    void unknownCodesAreHumanized() {
        assertThat(MarketingModels.projectTypeLabel("SOLAR_PANELS")).isEqualTo("Solar panels");
        assertThat(MarketingModels.unmetReasonLabel("budget_too_low")).isEqualTo("Budget too low");
    }

    @Test
    @DisplayName("les entrées vides ou nulles ne produisent pas de libellé technique")
    void blankAndNullAreSafe() {
        assertThat(MarketingModels.projectTypeLabel(null)).isEmpty();
        assertThat(MarketingModels.projectTypeLabel("   ")).isEmpty();
        assertThat(MarketingModels.unmetReasonLabel(null)).isEmpty();
    }

    @Test
    @DisplayName("les tables de libellés exposées par /api/marketing/status sont complètes et non vides")
    void statusMapsAreExposed() {
        assertThat(MarketingModels.projectTypeLabels())
                .containsKeys("REAL_ESTATE", "VEHICLE", "UNKNOWN")
                .allSatisfy((code, label) -> assertThat(label).isNotBlank());
        assertThat(MarketingModels.productFamilyLabels())
                .containsEntry("MORTGAGE", "Mortgage")
                .containsKey("INSURANCE_AUTO");
        assertThat(MarketingModels.unmetReasonLabels())
                .containsEntry("NO_SUITABLE_PRODUCT", "No offer suited to the need");
        assertThat(MarketingModels.rejectionReasonLabels()).isNotEmpty();
        assertThat(MarketingModels.missingInfoReasonLabels()).isNotEmpty();
        assertThat(MarketingModels.interestReasonLabels()).isNotEmpty();
    }
}
