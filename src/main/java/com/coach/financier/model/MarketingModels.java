package com.coach.financier.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Modèles du module MARKETING INTELLIGENCE (POC, sans base de données).
 * <p>
 * Chaîne : conversation → signaux IA ({@code marketingEvents}) → événements JSONL →
 * agrégations déterministes (code Java) → rapport IA marketing → API → page {@code #/marketing}.
 * <p>
 * Tous les CHIFFRES des agrégats sont calculés par le code, jamais par le LLM.
 */
public final class MarketingModels {
    private MarketingModels() {}

    // ------------------------------------------------------------------ types d'événements (§6)

    public static final String PROJECT_DETECTED = "PROJECT_DETECTED";
    public static final String PRODUCT_RECOMMENDED = "PRODUCT_RECOMMENDED";
    public static final String PRODUCT_INTEREST = "PRODUCT_INTEREST";
    public static final String PRODUCT_REJECTED = "PRODUCT_REJECTED";
    public static final String PRODUCT_COMPARISON = "PRODUCT_COMPARISON";
    public static final String SUBSCRIPTION_INTEREST = "SUBSCRIPTION_INTEREST";
    public static final String APPOINTMENT_INTEREST = "APPOINTMENT_INTEREST";
    public static final String ADVISOR_HANDOFF = "ADVISOR_HANDOFF";
    public static final String UNMET_NEED = "UNMET_NEED";
    public static final String MISSING_PRODUCT_INFORMATION = "MISSING_PRODUCT_INFORMATION";
    // Événements QA « qualité du parcours IA », à ne pas mélanger avec l'intérêt client (§6/§13).
    public static final String COACH_DATA_MISSING = "COACH_DATA_MISSING";
    public static final String COACH_PRODUCT_MISMATCH = "COACH_PRODUCT_MISMATCH";
    public static final String COACH_UNANSWERED_REQUEST = "COACH_UNANSWERED_REQUEST";

    public static final List<String> EVENT_TYPES = List.of(
            PROJECT_DETECTED, PRODUCT_RECOMMENDED, PRODUCT_INTEREST, PRODUCT_REJECTED, PRODUCT_COMPARISON,
            SUBSCRIPTION_INTEREST, APPOINTMENT_INTEREST, ADVISOR_HANDOFF, UNMET_NEED,
            MISSING_PRODUCT_INFORMATION, COACH_DATA_MISSING, COACH_PRODUCT_MISMATCH, COACH_UNANSWERED_REQUEST);

    public static boolean isCoachQuality(String eventType) {
        return COACH_DATA_MISSING.equals(eventType)
                || COACH_PRODUCT_MISMATCH.equals(eventType)
                || COACH_UNANSWERED_REQUEST.equals(eventType);
    }

    // ------------------------------------------------------------------ libellés lisibles

    /**
     * Libellés MÉTIER des codes techniques (types de projet, motifs de refus, besoins non couverts,
     * informations manquantes) : la page Marketing ne doit jamais afficher un code brut comme
     * {@code REAL_ESTATE · NO_SUITABLE_PRODUCT}.
     * <p>
     * Les libellés vivent côté BACKEND (source unique) et sont exposés par {@code GET /api/marketing/status}.
     * Un code inconnu est « humanisé » (underscores remplacés, première lettre en majuscule) : jamais de
     * code brut, jamais d'invention.
     */
    private static final Map<String, String> PROJECT_TYPE_LABELS = labels(new String[][]{
            {"VEHICLE", "Vehicle purchase"},
            {"REAL_ESTATE", "Real estate project"},
            {"HOME_WORK", "Home improvement / renovation"},
            {"ELECTRONICS", "Electronics purchase"},
            {"FURNITURE", "Furniture purchase"},
            {"TRAVEL", "Travel"},
            {"EDUCATION", "Studies / education"},
            {"WEDDING", "Wedding"},
            {"HEALTH_EXPENSE", "Health expense"},
            {"CASH_NEED", "Cash need"},
            {"DEBT_RESTRUCTURING", "Debt consolidation"},
            {"SAVINGS", "Savings / investment"},
            {"INVESTMENT", "Investment"},
            {"INSURANCE", "Insurance"},
            {"BUDGET", "Budget management"},
            // variantes observées côté IA (libellé français malgré tout, jamais un code brut)
            {"REAL_ESTATE_PURCHASE", "Property purchase"},
            {"REAL_ESTATE_INVESTMENT", "Property investment"},
            {"CAR_PURCHASE", "Vehicle purchase"},
            {"HOME_RENOVATION", "Home improvement / renovation"},
            {"LIQUIDITY_NEED", "Cash need"},
            {"OTHER", "Other project"},
            {"UNKNOWN", "Unidentified project"}});

    /** Familles de produits du catalogue (crédits, assurances, épargne). */
    private static final Map<String, String> PRODUCT_FAMILY_LABELS = labels(new String[][]{
            {"AUTO_LOAN", "Car loan"},
            {"PERSONAL_LOAN", "Personal loan"},
            {"MORTGAGE", "Mortgage"},
            {"STUDENT_LOAN", "Student loan"},
            {"YOUNG_ACTIVE_LOAN", "Young professional loan"},
            {"DRIVER_LICENSE_LOAN", "Driving licence loan"},
            {"REVOLVING_CREDIT", "Revolving credit"},
            {"DEBT_CONSOLIDATION", "Debt consolidation"},
            {"INSURANCE_AUTO", "Car insurance"},
            {"INSURANCE_HOME", "Home insurance"},
            {"INSURANCE_BORROWER", "Mortgage protection insurance"},
            {"LIFE_INSURANCE", "Life insurance"},
            {"SAVINGS_PRODUCT", "Savings product"},
            {"HOME_SAVINGS", "Home savings"},
            {"RETIREMENT_SAVINGS", "Retirement savings"},
            {"TERM_DEPOSIT", "Term deposit"},
            {"EQUITY_INVESTMENT", "Equity investment"}});

    /** Motifs de REFUS d'un produit (le client écarte l'offre). */
    private static final Map<String, String> REJECTION_REASON_LABELS = labels(new String[][]{
            {"PRICE", "Price / cost too high"},
            {"RATE", "Rate considered too high"},
            {"PREFERS_CASH", "Prefers to pay cash"},
            {"DOES_NOT_WANT_CREDIT", "Does not want credit"},
            {"INSUFFICIENT_COVERAGE", "Insufficient coverage"},
            {"DURATION", "Unsuitable term"},
            {"CONDITIONS", "Conditions not accepted"},
            {"COMPETITOR", "Prefers a competitor offer"},
            {"NOT_NEEDED", "Need not confirmed"},
            {"TOO_COMPLEX", "Offer considered too complex"},
            {"OTHER", "Other reason"}});

    /** Motifs d'INTÉRÊT du client (pourquoi il s'intéresse au produit). */
    private static final Map<String, String> INTEREST_REASON_LABELS = labels(new String[][]{
            {"DETAIL_REQUEST", "Request for details"},
            {"PRICE_REQUEST", "Price request"},
            {"RATE_REQUEST", "Rate request"},
            {"COVERAGE_REQUEST", "Request about coverage"},
            {"COMPARISON", "Offer comparison"},
            {"SUBSCRIPTION_REQUEST", "Wants to subscribe"},
            {"QUOTE_REQUEST", "Quote request"},
            {"ADVISOR_REQUEST", "Wants to speak to an advisor"},
            {"OTHER", "Other reason"}});

    /** Motifs d'un BESOIN NON COUVERT (aucune offre adaptée au catalogue). */
    private static final Map<String, String> UNMET_REASON_LABELS = labels(new String[][]{
            {"NO_SUITABLE_PRODUCT", "No offer suited to the need"},
            {"PRICE_RANGE_NOT_COVERED", "Amount outside the offer range"},
            {"ELIGIBILITY_NOT_MET", "Eligibility criteria not covered"},
            {"PRODUCT_MISSING", "Product missing from catalogue"},
            {"OTHER", "Other reason"}});

    /** Motifs d'INFORMATION PRODUIT MANQUANTE (question restée sans réponse). */
    private static final Map<String, String> MISSING_INFO_REASON_LABELS = labels(new String[][]{
            {"MISSING_COVERAGE_INFORMATION", "Coverage not documented"},
            {"MISSING_PRICING_INFORMATION", "Pricing not documented"},
            {"MISSING_CONDITIONS_INFORMATION", "Conditions not documented"},
            {"MISSING_ELIGIBILITY_INFORMATION", "Eligibility not documented"},
            {"OTHER", "Other missing information"}});

    private static Map<String, String> labels(String[][] entries) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String[] entry : entries) {
            map.put(entry[0], entry[1]);
        }
        return Map.copyOf(map);
    }

    public static Map<String, String> projectTypeLabels() {
        return PROJECT_TYPE_LABELS;
    }

    public static Map<String, String> rejectionReasonLabels() {
        return REJECTION_REASON_LABELS;
    }

    public static Map<String, String> productFamilyLabels() {
        return PRODUCT_FAMILY_LABELS;
    }

    public static String productFamilyLabel(String raw) {
        return label(PRODUCT_FAMILY_LABELS, raw);
    }

    public static Map<String, String> interestReasonLabels() {
        return INTEREST_REASON_LABELS;
    }

    public static Map<String, String> unmetReasonLabels() {
        return UNMET_REASON_LABELS;
    }

    public static Map<String, String> missingInfoReasonLabels() {
        return MISSING_INFO_REASON_LABELS;
    }

    public static String projectTypeLabel(String raw) {
        return label(PROJECT_TYPE_LABELS, raw);
    }

    public static String rejectionReasonLabel(String raw) {
        return label(REJECTION_REASON_LABELS, raw);
    }

    public static String interestReasonLabel(String raw) {
        return label(INTEREST_REASON_LABELS, raw);
    }

    public static String unmetReasonLabel(String raw) {
        return label(UNMET_REASON_LABELS, raw);
    }

    public static String missingInfoReasonLabel(String raw) {
        return label(MISSING_INFO_REASON_LABELS, raw);
    }

    /** Libellé d'un code : table de libellés, sinon version humanisée (jamais le code brut). */
    private static String label(Map<String, String> labels, String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        String known = labels.get(code);
        if (known != null) {
            return known;
        }
        String humanized = code.replace('_', ' ').toLowerCase(Locale.ROOT);
        return humanized.isEmpty() ? raw : humanized.substring(0, 1).toUpperCase(Locale.ROOT) + humanized.substring(1);
    }

    // ------------------------------------------------------------------ événements

    /**
     * Brouillon d'événement produit par l'IA de fin de conversation (même appel que le dossier de
     * suivi) : uniquement des signaux qualitatifs. Le backend ajoute les identifiants techniques
     * (eventId, session, client pseudonymisé, horodatage, versions, modèle).
     */
    public record MarketingEventDraft(
            String eventType,
            String projectType,
            String projectAmountRange,
            String productId,
            String productName,
            String productFamily,
            String interestLevel,
            String reasonCategory,
            String reason,
            Boolean advisorFollowUpRecommended,
            Double confidence
    ) {}

    /** Événement persisté : une ligne du fichier JSONL quotidien. */
    public record MarketingEvent(
            String eventId,
            String eventType,
            String timestamp,
            String sessionId,
            String anonymousCustomerId,
            String projectType,
            String projectAmountRange,
            String productId,
            String productName,
            String productFamily,
            String interestLevel,
            String reasonCategory,
            String reason,
            Boolean advisorFollowUpRecommended,
            Double confidence,
            String extractorVersion,
            String promptVersion,
            String model,
            String createdAt,
            Boolean demo
    ) {}

    // ------------------------------------------------------------------ agrégats (calculés par le code)

    /** KPI globaux (§19). */
    public record MarketingOverview(
            long conversationCount,
            long sessionsWithInterest,
            long uniqueCustomers,
            long highInterestCount,
            long subscriptionIntentCount,
            long appointmentRequestCount,
            long unmetNeedCount,
            long missingInformationCount,
            long coachQualityIssueCount
    ) {}

    /** Métriques par produit (§20/§24). */
    public record ProductMetric(
            String productId,
            String productName,
            String productFamily,
            long recommendedSessions,
            long interestedSessions,
            long uniqueInterestedCustomers,
            long highCount,
            long mediumCount,
            long lowCount,
            long rejectedCount,
            long comparisonCount,
            long subscriptionIntentCount,
            long appointmentRequestCount,
            Double interestRate,
            double score,
            Long previousInterestedSessions,
            Double evolutionPercent
    ) {}

    /** Compteur produit simple (projets). */
    public record ProductCount(String productId, String productName, long count) {}

    /** Métriques par projet (§21). */
    public record ProjectMetric(
            String projectType,
            long volume,
            Double share,
            Map<String, Long> amountRanges,
            long interestCount,
            long rejectedCount,
            long unmetNeedCount,
            List<ProductCount> topProducts,
            Long previousVolume,
            Double evolutionPercent
    ) {}

    /** Analyse des refus (§22). */
    public record RejectionMetric(
            String reasonCategory,
            String productId,
            String productName,
            long count,
            Double share
    ) {}

    /** Associations de produits (§23). */
    public record CrossSellMetric(
            String sourceProductId,
            String sourceProductName,
            String targetProductId,
            String targetProductName,
            long commonSessions,
            long sourceSessions,
            Double rate
    ) {}

    /** Besoins non couverts (§12). */
    public record UnmetNeedMetric(
            String projectType,
            String reasonCategory,
            String reason,
            long count,
            Double averageConfidence
    ) {}

    /** Informations produit manquantes (§13). */
    public record MissingInfoMetric(
            String productId,
            String productName,
            String reasonCategory,
            long count
    ) {}

    /** Filtres appliqués côté backend avant agrégation (§30). Champs {@code null} = non filtré. */
    public record MarketingFilter(
            String productId,
            String productFamily,
            String projectType,
            String interestLevel,
            String eventType
    ) {
        public static MarketingFilter none() {
            return new MarketingFilter(null, null, null, null, null);
        }

        public MarketingFilter {
            productId = blankToNull(productId);
            productFamily = blankToNull(productFamily);
            projectType = blankToNull(projectType);
            interestLevel = blankToNull(interestLevel);
            eventType = blankToNull(eventType);
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }

        public boolean isEmpty() {
            return productId == null && productFamily == null && projectType == null
                    && interestLevel == null && eventType == null;
        }
    }

    /** Point de série temporelle (un jour). */
    public record DailyPoint(
            String date,
            long conversationCount,
            long interestCount,
            long highInterestCount,
            long subscriptionIntentCount,
            long appointmentRequestCount
    ) {}

    /** Tendance (courant vs période précédente), calculée par code (§25). */
    public record TrendMetric(
            String entityType,
            String entityId,
            String entityName,
            long current,
            long previous,
            Double evolutionPercent
    ) {}

    /** Agrégats complets d'une période (ce que consomment l'API et l'IA analyste). */
    public record MarketingAggregates(
            String dateFrom,
            String dateTo,
            MarketingOverview overview,
            List<ProductMetric> products,
            List<ProjectMetric> projects,
            List<RejectionMetric> rejections,
            List<CrossSellMetric> crossSell,
            List<UnmetNeedMetric> unmetNeeds,
            List<MissingInfoMetric> missingInformation,
            List<DailyPoint> series,
            List<TrendMetric> trends,
            boolean demo,
            long invalidEventLines,
            String generatedAt
    ) {}

    // ------------------------------------------------------------------ rapport IA marketing (§21)

    public record ReportItem(String title, String description, String importance) {}

    public record MainTrend(String type, String entityType, String entityId, String entityName, String observation) {}

    public record RecommendationPerformance(String productId, String productName, String observation) {}

    public record FrictionItem(String category, String observation) {}

    public record CrossSellInsight(String sourceProduct, String targetProduct, String observation) {}

    public record UnmetNeedInsight(String projectType, String observation) {}

    public record MissingInfoInsight(String productId, String productName, String observation) {}

    public record CoachQualityItem(String type, String observation) {}

    public record MarketingAlert(String level, String title, String description) {}

    public record MarketingOpportunity(String title, String description, String recommendation) {}

    /** Rapport marketing généré par l'IA analyste (chiffres fournis, jamais recalculés par l'IA). */
    public record MarketingReport(
            String reportDate,
            List<ReportItem> executiveSummary,
            List<MainTrend> mainTrends,
            List<RecommendationPerformance> recommendationPerformance,
            List<FrictionItem> customerFriction,
            List<CrossSellInsight> crossSellInsights,
            List<UnmetNeedInsight> unmetNeeds,
            List<MissingInfoInsight> missingProductInformation,
            List<CoachQualityItem> aiCoachQuality,
            List<MarketingAlert> alerts,
            List<MarketingOpportunity> opportunities,
            String finalSummary,
            String generatedAt,
            String model,
            Boolean aiGenerated,
            String error
    ) {}
}
