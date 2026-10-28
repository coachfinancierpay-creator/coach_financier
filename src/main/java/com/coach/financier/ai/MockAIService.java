package com.coach.financier.ai;

import com.coach.financier.model.AIModels;
import com.coach.financier.model.ConfidenceLevel;
import com.coach.financier.model.ConversationModels;
import com.coach.financier.model.FinancialIntent;
import com.coach.financier.model.FinancialSummary;
import com.coach.financier.model.IntentClassification;
import com.coach.financier.model.ProjectType;
import com.coach.financier.model.SuiviModels;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MockAIService implements AIService {
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{1,3}(?:[ .]\\d{3})*(?:[.,]\\d+)?|\\d+(?:[.,]\\d+)?)\\s*(€|eur|euros|euro)?",
            Pattern.CASE_INSENSITIVE);

    /**
     * Mode démo : classification d'intention déterministe par mots-clés.
     * (Le vrai classifieur enrichi est utilisé par les fournisseurs GPT/DeepSeek.)
     */
    @Override
    public IntentClassification classifyIntent(String userMessage, String currentProjectDescription,
                                               AIModels.AIProvider provider) {
        String m = userMessage == null ? "" : userMessage.toLowerCase();
        if (containsAny(m, "vacances", "météo", "football", "coupe du monde", "recette", "programmation",
                "holidays", "weather", "world cup", "recipe", "programming")) {
            return IntentClassification.outOfScope("The request does not relate to personal finance.");
        }
        IntentClassification c = new IntentClassification();
        c.setInScope(true);
        c.setConfidence(ConfidenceLevel.HIGH);

        Map<String, String> current = parseCurrentProject(currentProjectDescription);
        boolean refers = containsAny(m, "pour mon cas", "mon projet", "ce projet", "3 fois", "trois fois",
                "moitié", "moitié comptant", "celui-ci", "cette voiture", "le crédit", "financer",
                "finalement", "en fait", "for my case", "my project", "this project", "three times",
                "half", "that car", "the loan", "finance", "finally", "actually");
        c.setRefersToCurrentProject(refers);

        if (containsAny(m, "crédit", "credit", "prêt", "pret", "financement", "en 3 fois", "plusieurs fois", "échéance",
                "loan", "financing", "instalment", "installment", "repayment")) {
            c.setIntent(FinancialIntent.FINANCING_REQUEST);
        } else if (containsAny(m, "permettre", "raisonnable", "puis-je", "puis je", "capacité", "peut-on", "affordable",
                "can i afford", "afford", "reasonable", "capacity", "can we")) {
            c.setIntent(FinancialIntent.AFFORDABILITY_CHECK);
        } else if (containsAny(m, "moins cher", "comparer", "comparaison", "cheaper", "compare", "comparison")) {
            c.setIntent(FinancialIntent.COMPARE_OPTIONS);
        } else if (containsAny(m, "finalement", "en fait", "plutôt", "en définitive", "finally", "actually", "rather", "in the end")) {
            c.setIntent(FinancialIntent.PROJECT_UPDATE);
        } else if (containsAny(m, "assurance", "mutuelle", "assureur", "garantie", "insurance", "insurer", "cover", "guarantee")) {
            c.setIntent(FinancialIntent.PRODUCT_INFORMATION);
        } else if (containsAny(m, "acheter", "achat", "veux", "besoin", "veut", "buy", "purchase", "want", "need")) {
            c.setIntent(FinancialIntent.PURCHASE);
        } else if (containsAny(m, "budget", "dépense", "depense", "dépenses", "expenses", "spending")) {
            c.setIntent(FinancialIntent.BUDGET_ANALYSIS);
        } else if (containsAny(m, "épargne", "epargne", "livret", "épargner", "savings", "save", "passbook")) {
            c.setIntent(FinancialIntent.SAVINGS_ANALYSIS);
        } else {
            c.setIntent(FinancialIntent.OTHER);
        }

        String typeToken = detectProjectType(m);
        if (typeToken == null && (refers || needsCurrent(c.getIntent()))) {
            typeToken = current.get("type");
        }
        c.setProjectType(typeToken == null ? ProjectType.UNKNOWN : ProjectType.valueOf(typeToken));

        if (c.getProjectType() == ProjectType.UNKNOWN && !refers) {
            c.setConfidence(ConfidenceLevel.LOW);
        }

        String object = current.get("object");
        BigDecimal amount = extractAmount(m);
        if (amount == null) {
            String rawAmount = current.get("amount");
            if (rawAmount != null && !rawAmount.isBlank()) {
                try { amount = new BigDecimal(rawAmount.replace(" ", "").replace(',', '.')); } catch (Exception ignored) { amount = null; }
            }
        }
        if (amount != null) c.setAmount(amount);
        if (object != null && (c.getProjectObject() == null || refers)) c.setProjectObject(object);

        boolean hasNewInfo = amount != null && !containsAny(m, "mon cas", "mon projet", "my case", "my project");
        c.setProjectChanged(c.getProjectType() != ProjectType.UNKNOWN && (hasNewInfo || !refers || c.getIntent() == FinancialIntent.PROJECT_UPDATE));
        c.setReason("Demo classification (keywords).");
        return c;
    }

    private static boolean needsCurrent(FinancialIntent intent) {
        return intent == FinancialIntent.FINANCING_REQUEST || intent == FinancialIntent.AFFORDABILITY_CHECK
                || intent == FinancialIntent.COMPARE_OPTIONS || intent == FinancialIntent.PROJECT_UPDATE
                || intent == FinancialIntent.FOLLOW_UP || intent == FinancialIntent.CREDIT_INFORMATION;
    }

    private static Map<String, String> parseCurrentProject(String description) {
        Map<String, String> map = new HashMap<>();
        if (description == null) return map;
        for (String line : description.split("\n")) {
            int idx = line.indexOf('=');
            if (idx > 0) {
                map.put(line.substring(0, idx).trim().toLowerCase(),
                        line.substring(idx + 1).trim());
            }
        }
        return map;
    }

    private static String detectProjectType(String m) {
        // Placé avant VEHICLE : « assurance auto » doit rester INSURANCE (sinon « auto » ferait VEHICLE).
        if (containsAny(m, "assurance", "mutuelle", "assureur", "garantie", "insurance", "insurer", "cover", "guarantee")) return "INSURANCE";
        if (containsAny(m, "clio", "tesla", "voiture", "véhicule", "vehicule", "auto ", "auto'", "moto", "utilitaire",
                "car ", " car", "vehicle", "motorbike", "van")) return "VEHICLE";
        if (containsAny(m, "maison", "appartement", "résidence", "immo", "acheter un logement",
                "house", "apartment", "flat", "property", "real estate", "buy a home")) return "REAL_ESTATE_PURCHASE";
        if (containsAny(m, "cuisine", "salle de bain", "rénovation", "renovation", "travaux", "isolation", "refaire",
                "kitchen", "bathroom", "renovate", "renovation", "home improvement", "insulation", "redo")) return "HOME_WORK";
        if (containsAny(m, "ordinateur", "macbook", "pc gamer", "télévision", "television", "smartphone", "portable",
                "computer", "laptop", "tv", "phone")) return "ELECTRONICS";
        if (containsAny(m, "canapé", "canape", "meuble", "lit", "ameublement",
                "sofa", "couch", "furniture", "bed")) return "FURNITURE";
        if (containsAny(m, "vacances", "voyage", "billet d'avion", "holiday", "trip", "travel", "flight")) return "TRAVEL";
        if (containsAny(m, "formation", "études", "etudes", "école", "ecole", "scolarité",
                "training", "studies", "school", "tuition", "course")) return "EDUCATION";
        if (containsAny(m, "mariage", "noces", "wedding")) return "WEDDING";
        if (containsAny(m, "soin", "dentiste", "opération", "operation", "santé",
                "care", "dentist", "surgery", "health")) return "HEALTH_EXPENSE";
        if (containsAny(m, "regroupement", "restructurer", "crédits", "consolidation", "restructure", "debts")) return "DEBT_RESTRUCTURING";
        return null;
    }

    private static BigDecimal extractAmount(String message) {
        if (message == null) return null;
        Matcher matcher = AMOUNT_PATTERN.matcher(message);
        while (matcher.find()) {
            String number = matcher.group(1).replace(" ", "");
            boolean hasCurrency = matcher.group(2) != null;
            boolean big = number.replace(".", "").replace(",", "").length() >= 4;
            if (hasCurrency || big) {
                try {
                    return new BigDecimal(number.replace(",", "."));
                } catch (Exception ignored) {
                    // continue
                }
            }
        }
        return null;
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
    }

    @Override
    public AIModels.AIAnswer answer(String customerMessage, AIModels.Classification classification,
                                    FinancialSummary financialSummary, Object bankingData,
                                    AIModels.BankingContextMode contextMode,
                                    Map<String, Object> additionalData,
                                    List<ConversationModels.Message> history, AIModels.AIProvider provider) {
        String answer = "Demo mode is active. Based on your banking data, your average monthly income is "
                + money(financialSummary.averageMonthlyIncome()) + " € and your average monthly expenses are "
                + money(financialSummary.averageMonthlyExpenses()) + " €. Your current account balance is about "
                + money(financialSummary.currentAccountBalance()) + " € and your savings are "
                + money(financialSummary.savingsBalance()) + " €.\n\n"
                + "For a detailed AI answer, configure OPENAI_API_KEY or DEEPSEEK_API_KEY and select the matching provider.";
        return new AIModels.AIAnswer(AIModels.AIStatus.ANSWER, answer, null, Map.of(), "Financial analysis computed by the backend.", null);
    }

    /**
     * Mode démo : le prompt système n'a AUCUN effet (la réponse est construite à partir de la synthèse
     * calculée par le backend). La zone éditable d'une campagne n'est donc pas rejouable en mode démo,
     * d'où {@link #NO_REAL_PROVIDER_MESSAGE} pour les agents de l'atelier.
     */
    @Override
    public AIModels.AIAnswer answerWithSystemPrompt(String systemPrompt, String customerMessage,
                                                    AIModels.Classification classification,
                                                    FinancialSummary financialSummary, Object bankingData,
                                                    AIModels.BankingContextMode contextMode,
                                                    Map<String, Object> additionalData,
                                                    List<ConversationModels.Message> history,
                                                    AIModels.AIProvider provider) {
        return answer(customerMessage, classification, financialSummary, bankingData, contextMode, additionalData,
                history, provider);
    }

    /** Message unique expliquant pourquoi l'atelier exige un fournisseur IA réel. */
    public static final String NO_REAL_PROVIDER_MESSAGE =
            "The prompt optimisation workshop requires a real AI provider (GPT, DEEPSEEK or "
            + "LOCAL — locally served model): "
            + "demo mode does not produce any answer that depends on the prompt.";

    /**
     * L'atelier compare les RÉPONSES RÉELLES du Coach à une question figée : cela n'a de sens qu'avec un
     * modèle réel, seul capable d'exploiter la zone éditable du prompt.
     */
    @Override
    public com.coach.financier.model.PromptOptimizationModels.ControllerFeedback reviewCoachAnswer(
            Map<String, Object> context, AIModels.AIProvider provider) {
        throw new IllegalStateException(NO_REAL_PROVIDER_MESSAGE);
    }

    @Override
    public com.coach.financier.model.PromptOptimizationModels.EditorResult editPromptSection(
            Map<String, Object> context, AIModels.AIProvider provider) {
        throw new IllegalStateException(NO_REAL_PROVIDER_MESSAGE);
    }

    /**
     * CLIENT simulé de l'atelier (« Agent C ») : en mode démo, ce rôle n'a pas de sens (il faudrait un
     * modèle réel pour tenir une conversation crédible) — refus explicite, comme les deux autres agents.
     */
    @Override
    public com.coach.financier.model.PromptOptimizationModels.ClientTurn clientTurn(
            Map<String, Object> context, AIModels.AIProvider provider) {
        throw new IllegalStateException(NO_REAL_PROVIDER_MESSAGE);
    }

    /**
     * CONCEPTION DU PROJET par l'Agent C (« Générer projet ») : en mode démo, inventer un client et son projet
     * demanderait un modèle réel — refus explicite, comme les autres rôles de l'atelier. Un projet déterministe
     * n'aurait aucune valeur ici : c'est justement la variété des scénarios qui teste le prompt.
     */
    @Override
    public com.coach.financier.model.PromptOptimizationModels.ClientBrief clientBrief(
            Map<String, Object> context, AIModels.AIProvider provider) {
        throw new IllegalStateException(NO_REAL_PROVIDER_MESSAGE);
    }

    /**
     * Mode démo (aucun appel LLM) : synthèse DÉTERMINISTE construite uniquement à partir du
     * contexte fourni. Elle respecte les mêmes règles que l'agent réel : pas d'invention,
     * produits refusés exclus, brouillon client jamais présenté comme déjà envoyé.
     */
    @Override
    public SuiviModels.SuiviResult summarizeConversation(Map<String, Object> context, AIModels.AIProvider provider) {
        Map<String, Object> root = asMap(context);
        List<Map<String, Object>> history = asListOfMaps(root.get("conversationHistory"));
        List<Map<String, Object>> products = asListOfMaps(root.get("products"));
        Map<String, Object> customerContext = asMap(root.get("customerContext"));
        Map<String, Object> advisorContext = asMap(root.get("advisorContext"));
        Map<String, Object> usefulUrls = asMap(root.get("usefulUrls"));
        List<Map<String, Object>> projects = asListOfMaps(customerContext.get("currentProjects"));

        List<String> userTexts = new ArrayList<>();
        List<String> assistantTexts = new ArrayList<>();
        for (Map<String, Object> message : history) {
            String content = str(message.get("content"));
            if (content == null) continue;
            if ("user".equalsIgnoreCase(str(message.get("role")))) userTexts.add(content);
            else assistantTexts.add(content);
        }

        String mainProject = "Project not specified";
        List<String> otherProjects = new ArrayList<>();
        if (!projects.isEmpty()) {
            mainProject = describeProject(projects.get(0));
            for (int i = 1; i < projects.size(); i++) {
                otherProjects.add(describeProject(projects.get(i)));
            }
        }

        List<String> preferences = new ArrayList<>();
        String normalizedUser = normalize(String.join(" \n ", userTexts));
        if (normalizedUser.contains("epargne") || normalizedUser.contains("preserver")) {
            preferences.add("Wants to keep part of their savings.");
        }
        if (normalizedUser.contains("apport") || normalizedUser.contains("comptant")) {
            preferences.add("Appears open to partial financing (down payment, cash payment).");
        }
        if (normalizedUser.contains("mensualite") || normalizedUser.contains("par mois")) {
            preferences.add("Pays attention to the monthly payment amount.");
        }

        List<SuiviModels.ProductOfInterest> interests = new ArrayList<>();
        for (Map<String, Object> product : products) {
            String id = str(product.get("id"));
            String name = str(product.get("name"));
            if (name == null || name.isBlank()) continue;
            String category = str(product.get("family"));
            String url = str(product.get("productUrl"));
            boolean rejected = userTexts.stream()
                    .anyMatch(text -> mentions(text, name, id) && isRejection(text));
            boolean highUser = userTexts.stream().anyMatch(text -> mentions(text, name, id));
            boolean mediumAssistant = assistantTexts.stream().anyMatch(text -> mentions(text, name, id));
            String level;
            String reason;
            if (rejected) {
                level = "REJECTED";
                reason = "The customer explicitly ruled out this offer.";
            } else if (highUser) {
                level = "HIGH";
                reason = "The customer asked for details, compared options or showed explicit interest.";
            } else if (mediumAssistant) {
                level = "MEDIUM";
                reason = "Offer recommended by the coach, consistent with the project.";
            } else {
                level = "LOW";
                reason = "Offer loaded in the context but not discussed with the customer.";
            }
            interests.add(new SuiviModels.ProductOfInterest(id, name, category, level, reason, url));
        }

        String customerName = str(customerContext.get("customerName"));
        String customerReference = str(customerContext.get("customerReference"));
        String advisorName = str(advisorContext.get("advisorName"));
        String appointmentUrl = str(usefulUrls.get("advisorAppointment"));

        String advisorEmail = buildAdvisorEmail(mainProject, otherProjects, preferences, interests,
                customerName, customerReference, advisorName);
        String customerEmail = buildCustomerEmail(mainProject, interests, customerName, advisorName, appointmentUrl);

        return new SuiviModels.SuiviResult(
                new SuiviModels.ConversationSummary(mainProject, otherProjects, preferences),
                interests,
                new SuiviModels.EmailContent("Customer follow-up — " + mainProject, advisorEmail),
                new SuiviModels.EmailContent("Your project: " + mainProject, customerEmail),
                marketingDrafts(projects, interests));
    }

    /**
     * Signaux Marketing déterministes (mode démo, §6) : projet détecté, produits recommandés,
     * intérêts (HIGH/MEDIUM) et refus. Les identifiants techniques et la tranche de montant sont
     * ajoutés ensuite par le backend. Aucune donnée personnelle.
     */
    private static List<com.coach.financier.model.MarketingModels.MarketingEventDraft> marketingDrafts(
            List<Map<String, Object>> projects, List<SuiviModels.ProductOfInterest> interests) {
        List<com.coach.financier.model.MarketingModels.MarketingEventDraft> drafts = new ArrayList<>();
        for (Map<String, Object> project : projects) {
            String type = str(project.get("type"));
            if (type != null && !type.isBlank()) {
                drafts.add(new com.coach.financier.model.MarketingModels.MarketingEventDraft(
                        com.coach.financier.model.MarketingModels.PROJECT_DETECTED,
                        type, null, null, null, null, null, null, null, null, 0.9));
            }
        }
        for (SuiviModels.ProductOfInterest interest : interests) {
            String level = interest.interestLevel();
            com.coach.financier.model.MarketingModels.MarketingEventDraft recommended =
                    new com.coach.financier.model.MarketingModels.MarketingEventDraft(
                            com.coach.financier.model.MarketingModels.PRODUCT_RECOMMENDED,
                            null, null, interest.productId(), interest.name(), interest.category(),
                            null, null, "Offer presented by the coach.", null, 0.8);
            drafts.add(recommended);
            if ("REJECTED".equals(level)) {
                drafts.add(new com.coach.financier.model.MarketingModels.MarketingEventDraft(
                        com.coach.financier.model.MarketingModels.PRODUCT_REJECTED,
                        null, null, interest.productId(), interest.name(), interest.category(),
                        null, "OTHER", interest.interestReason(), null, 0.8));
            } else if ("HIGH".equals(level) || "MEDIUM".equals(level)) {
                drafts.add(new com.coach.financier.model.MarketingModels.MarketingEventDraft(
                        com.coach.financier.model.MarketingModels.PRODUCT_INTEREST,
                        null, null, interest.productId(), interest.name(), interest.category(),
                        level, "DETAIL_REQUEST", interest.interestReason(), true, 0.85));
            }
        }
        return drafts;
    }

    private String buildAdvisorEmail(String mainProject, List<String> otherProjects, List<String> preferences,
                                     List<SuiviModels.ProductOfInterest> interests, String customerName,
                                     String customerReference, String advisorName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hello").append(advisorName == null ? "" : " " + advisorName).append(",\n\n");
        sb.append("Here is the customer follow-up summary");
        if (customerName != null && !customerName.isBlank()) {
            sb.append(" ").append(customerName);
        }
        if (customerReference != null && !customerReference.isBlank()) {
            sb.append(" (ref. ").append(customerReference).append(")");
        }
        sb.append(".\n\n");
        sb.append("Customer project\n").append(mainProject).append("\n\n");
        if (!otherProjects.isEmpty()) {
            sb.append("Other topics discussed\n");
            for (String project : otherProjects) sb.append("- ").append(project).append('\n');
            sb.append('\n');
        }
        if (!preferences.isEmpty()) {
            sb.append("Important preferences\n");
            for (String preference : preferences) sb.append("- ").append(preference).append('\n');
            sb.append('\n');
        }
        sb.append("Products / offers of interest\n");
        boolean any = false;
        for (SuiviModels.ProductOfInterest interest : interests) {
            if ("LOW".equals(interest.interestLevel()) || "REJECTED".equals(interest.interestLevel())) continue;
            any = true;
            sb.append("- ").append(interest.name());
            if (interest.category() != null) sb.append(" (").append(interest.category()).append(')');
            sb.append(" — interest ").append(interest.interestLevel()).append(": ").append(interest.interestReason());
            if (interest.productUrl() != null) {
                sb.append(" [URL|View the offer|").append(interest.productUrl()).append(']');
            }
            sb.append('\n');
        }
        if (!any) sb.append("- No product was clearly requested; see the exchanges.\n");
        sb.append('\n');
        List<String> rejected = interests.stream()
                .filter(i -> "REJECTED".equals(i.interestLevel())).map(SuiviModels.ProductOfInterest::name).toList();
        if (!rejected.isEmpty()) {
            sb.append("Points still to confirm\n- The customer ruled out: ")
                    .append(String.join(", ", rejected)).append(". Do not offer again without a discussion.\n\n");
        }
        sb.append("Recommended follow-up\n- Contact the customer again to confirm their need and check eligibility, ")
                .append("then verify the pricing from the rate grid and prepare the firm offer ")
                .append("(the subscription is the binding document).\n\n");
        sb.append("You will find attached a draft email prepared for the customer, based on the ")
                .append("needs and interests identified during the discussion. Please review and adapt it ")
                .append("if necessary before sending. The content has not been sent to the customer yet.");
        return sb.toString();
    }

    private String buildCustomerEmail(String mainProject, List<SuiviModels.ProductOfInterest> interests,
                                      String customerName, String advisorName, String appointmentUrl) {
        StringBuilder sb = new StringBuilder();
        String firstName = null;
        if (customerName != null && !customerName.isBlank()) {
            firstName = customerName.trim().split("\\s+")[0];
        }
        sb.append("Hello").append(firstName == null ? "" : " " + firstName).append(",\n\n");
        sb.append("Following your conversation with our Financial Coach about ").append(mainProject)
                .append(", here are the solutions that may interest you:\n\n");
        for (SuiviModels.ProductOfInterest interest : interests) {
            if (!"HIGH".equals(interest.interestLevel()) && !"MEDIUM".equals(interest.interestLevel())) continue;
            sb.append("- ").append(interest.name());
            if (interest.interestReason() != null) sb.append(": ").append(interest.interestReason());
            if (interest.productUrl() != null) {
                sb.append(" [URL|Learn more|").append(interest.productUrl()).append(']');
            }
            sb.append('\n');
        }
        sb.append('\n');
        if (appointmentUrl != null && !appointmentUrl.isBlank()) {
            sb.append("To discuss this, you can [URL|book an appointment with your advisor|")
                    .append(appointmentUrl).append("].\n\n");
        } else {
            sb.append("Feel free to contact your advisor to discuss this.\n\n");
        }
        sb.append("Kind regards,\n");
        sb.append(advisorName == null || advisorName.isBlank() ? "Your advisor" : advisorName);
        return sb.toString();
    }

    private static String describeProject(Map<String, Object> project) {
        String object = str(project.get("object"));
        String type = str(project.get("type"));
        Object amount = project.get("amount");
        StringBuilder sb = new StringBuilder();
        if (object != null && !object.isBlank()) {
            sb.append(object);
        } else if (type != null) {
            sb.append(type.replace('_', ' ').toLowerCase(java.util.Locale.ROOT));
        }
        if (amount != null) {
            if (sb.length() > 0) sb.append(" — ");
            sb.append(amount).append(" €");
        }
        return sb.length() == 0 ? "Project not specified" : sb.toString();
    }

    /** Un texte mentionne-t-il le produit ? (nom complet ou mots-clés distinctifs) */
    private static boolean mentions(String text, String productName, String productId) {
        String haystack = normalize(text);
        String name = normalize(productName);
        if (name.length() >= 4 && haystack.contains(name)) {
            return true;
        }
        for (String token : signatureTokens(productName, productId)) {
            if (haystack.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static final java.util.Set<String> STOPWORDS = java.util.Set.of(
            "sg", "bfm", "de", "du", "des", "la", "le", "les", "et", "en", "au", "aux", "un", "une",
            "pour", "avec", "sur", "assurance", "credit", "pret", "livret", "compte", "plan", "contrat",
            "offre", "formule", "tous", "toutes", "votre", "nos", "plus");

    private static List<String> signatureTokens(String productName, String productId) {
        List<String> tokens = new ArrayList<>();
        collectTokens(tokens, productName);
        collectTokens(tokens, productId == null ? null : productId.replace('_', ' '));
        return tokens;
    }

    private static void collectTokens(List<String> tokens, String value) {
        if (value == null) return;
        for (String token : normalize(value).split("[^a-z0-9]+")) {
            if (token.length() >= 4 && !STOPWORDS.contains(token) && !tokens.contains(token)) {
                tokens.add(token);
            }
        }
    }

    private static boolean isRejection(String text) {
        String value = normalize(text);
        return containsAny(value, "pas interesse", "pas intéressé", "non merci", "je refuse", "refuse cette",
                "trop cher", "sans interet", "sans intérêt", "finalement pas",
                "not interested", "no thanks", "i refuse", "refuse this", "too expensive", "not worth", "finally not");
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return Map.of();
    }

    private static List<Map<String, Object>> asListOfMaps(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?>) {
                    result.add(asMap(item));
                }
            }
        }
        return result;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Mode démo : rapport Marketing DÉTERMINISTE construit à partir des agrégats fournis.
     * Aucun chiffre n'est inventé — seules les valeurs déjà calculées par le backend sont reprises,
     * et faits/hypothèses sont distingués comme le demande le prompt analyste.
     */
    @Override
    public com.coach.financier.model.MarketingModels.MarketingReport analyzeMarketing(
            com.coach.financier.model.MarketingModels.MarketingAggregates aggregates,
            AIModels.AIProvider provider) {
        List<com.coach.financier.model.MarketingModels.ReportItem> summary = new ArrayList<>();
        List<com.coach.financier.model.MarketingModels.MainTrend> mainTrends = new ArrayList<>();
        List<com.coach.financier.model.MarketingModels.RecommendationPerformance> recommendations = new ArrayList<>();
        List<com.coach.financier.model.MarketingModels.FrictionItem> friction = new ArrayList<>();
        List<com.coach.financier.model.MarketingModels.CrossSellInsight> crossSell = new ArrayList<>();
        List<com.coach.financier.model.MarketingModels.UnmetNeedInsight> unmetNeeds = new ArrayList<>();
        List<com.coach.financier.model.MarketingModels.MissingInfoInsight> missingInfo = new ArrayList<>();
        List<com.coach.financier.model.MarketingModels.MarketingAlert> alerts = new ArrayList<>();
        List<com.coach.financier.model.MarketingModels.MarketingOpportunity> opportunities = new ArrayList<>();

        String reportDate = LocalDate.now().toString();
        if (aggregates != null) {
            reportDate = aggregates.dateTo() == null ? reportDate : aggregates.dateTo();
            com.coach.financier.model.MarketingModels.MarketingOverview overview = aggregates.overview();
            if (overview != null) {
                summary.add(item("Volume analysed", overview.conversationCount() + " conversation(s) analysed, "
                        + overview.sessionsWithInterest() + " with product interest, "
                        + overview.highInterestCount() + " HIGH interest(s).", "HIGH"));
                summary.add(item("Strong intentions", overview.subscriptionIntentCount()
                        + " subscription intention(s) and " + overview.appointmentRequestCount()
                        + " appointment request(s).", "HIGH"));
                if (overview.unmetNeedCount() > 0) {
                    summary.add(item("Unmet needs", overview.unmetNeedCount()
                            + " need(s) expressed without a clearly suitable offer.", "HIGH"));
                    alerts.add(new com.coach.financier.model.MarketingModels.MarketingAlert("IMPORTANT",
                            "Unmet needs", overview.unmetNeedCount()
                            + " need(s) without a suitable solution over the period."));
                }
                if (overview.missingInformationCount() > 0) {
                    alerts.add(new com.coach.financier.model.MarketingModels.MarketingAlert("WATCH",
                            "Missing product information", overview.missingInformationCount()
                            + " unanswered question(s) in the catalogue."));
                }
                opportunities.add(new com.coach.financier.model.MarketingModels.MarketingOpportunity(
                        "Enrich product knowledge",
                        "Customer questions not covered by the catalogue provide leads for enrichment.",
                        "Study the most frequent missing product information before any decision."));
            }
            for (com.coach.financier.model.MarketingModels.TrendMetric trend : aggregates.trends()) {
                if (mainTrends.size() >= 5 || trend.evolutionPercent() == null) continue;
                boolean up = trend.evolutionPercent() >= 0;
                mainTrends.add(new com.coach.financier.model.MarketingModels.MainTrend(up ? "UP" : "DOWN",
                        trend.entityType(), trend.entityId(), trend.entityName(),
                        trend.current() + " vs " + trend.previous() + " (" + trend.evolutionPercent() + " %)"));
                if (Math.abs(trend.evolutionPercent()) >= 50) {
                    alerts.add(new com.coach.financier.model.MarketingModels.MarketingAlert("WATCH",
                            (up ? "Sharp rise" : "Sharp drop") + ": " + trend.entityName(),
                            "Change of " + trend.evolutionPercent() + " % — to be confirmed over several periods."));
                }
            }
            for (com.coach.financier.model.MarketingModels.ProductMetric product : aggregates.products()) {
                if (recommendations.size() >= 5 || product.recommendedSessions() == 0) continue;
                String rate = product.interestRate() == null ? "not computable"
                        : Math.round(product.interestRate() * 1000) / 10.0 + " %";
                recommendations.add(new com.coach.financier.model.MarketingModels.RecommendationPerformance(
                        product.productId(), product.productName(),
                        "Recommended in " + product.recommendedSessions() + " session(s), "
                        + product.interestedSessions() + " with real interest (rate " + rate + ")."));
            }
            for (com.coach.financier.model.MarketingModels.RejectionMetric rejection : aggregates.rejections()) {
                if (friction.size() >= 4) break;
                friction.add(new com.coach.financier.model.MarketingModels.FrictionItem(rejection.reasonCategory(),
                        rejection.count() + " rejection(s)" + (rejection.share() == null ? ""
                                : " (" + Math.round(rejection.share() * 1000) / 10.0 + " %)") + "."));
            }
            for (com.coach.financier.model.MarketingModels.CrossSellMetric pair : aggregates.crossSell()) {
                if (crossSell.size() >= 4) break;
                crossSell.add(new com.coach.financier.model.MarketingModels.CrossSellInsight(
                        pair.sourceProductName(), pair.targetProductName(),
                        pair.commonSessions() + " shared session(s)"
                                + (pair.rate() == null ? "" : " (" + Math.round(pair.rate() * 100) + " %)")
                                + " — association to study as a journey lead."));
            }
            for (com.coach.financier.model.MarketingModels.UnmetNeedMetric need : aggregates.unmetNeeds()) {
                if (unmetNeeds.size() >= 4) break;
                unmetNeeds.add(new com.coach.financier.model.MarketingModels.UnmetNeedInsight(need.projectType(),
                        need.count() + " occurrence(s) — " + String.valueOf(need.reason())));
            }
            for (com.coach.financier.model.MarketingModels.MissingInfoMetric info : aggregates.missingInformation()) {
                if (missingInfo.size() >= 4) break;
                missingInfo.add(new com.coach.financier.model.MarketingModels.MissingInfoInsight(
                        info.productId(), info.productName(),
                        info.count() + " unanswered question(s) (" + info.reasonCategory() + ")."));
            }
        }

        return new com.coach.financier.model.MarketingModels.MarketingReport(
                reportDate, summary, mainTrends, recommendations, friction, crossSell, unmetNeeds, missingInfo,
                List.of(), alerts.size() > 8 ? alerts.subList(0, 8) : alerts, opportunities,
                "Report generated in demo mode from the aggregated statistics.",
                java.time.Instant.now().toString(), "MOCK", Boolean.TRUE, null);
    }

    private static com.coach.financier.model.MarketingModels.ReportItem item(String title, String description,
                                                                             String importance) {
        return new com.coach.financier.model.MarketingModels.ReportItem(title, description, importance);
    }

    /**
     * Mode démo — AGENT ANALYSTE QUALITÉ : rapport DÉTERMINISTE construit uniquement à partir des
     * agrégats calculés par le backend (satisfaction d'un côté, conformité de l'autre).
     * <p>
     * Le mock respecte les règles du prompt : aucun chiffre inventé, satisfaction et conformité
     * TENUES SÉPARÉES, une mauvaise note n'est jamais transformée en anomalie du Coach, et la
     * frustration liée à une règle correctement appliquée (chiffrage de crédit non réalisé malgré la
     * grille de taux disponible) est explicitée.
     */
    @Override
    public com.coach.financier.model.QualityModels.QualityReport analyzeQuality(
            com.coach.financier.model.QualityModels.QualityAggregates aggregates,
            AIModels.AIProvider provider) {
        java.util.List<String> positivePoints = new ArrayList<>();
        java.util.List<String> irritants = new ArrayList<>();
        java.util.List<String> issues = new ArrayList<>();
        java.util.List<String> criticalIssues = new ArrayList<>();
        java.util.List<String> notableCases = new ArrayList<>();
        java.util.List<com.coach.financier.model.QualityModels.RuleFriction> friction = new ArrayList<>();
        java.util.List<com.coach.financier.model.QualityModels.QualityTrend> trends = new ArrayList<>();
        java.util.List<com.coach.financier.model.QualityModels.PriorityImprovement> improvements = new ArrayList<>();
        java.util.List<com.coach.financier.model.QualityModels.QualityAlert> alerts = new ArrayList<>();

        String reportDate = LocalDate.now().toString();
        String status = com.coach.financier.model.QualityModels.STATUS_INSUFFICIENT;
        String satisfactionSummary = "No usable review over the period: the data provided does not "
                + "allow any conclusion on customer satisfaction.";
        String complianceSummary = "No usable check over the period: the data provided does not "
                + "allow assessing the Coach's compliance.";
        String crossSummary = "The satisfaction × compliance cross-analysis is not usable over the period.";

        if (aggregates != null) {
            reportDate = aggregates.dateTo() == null ? reportDate : aggregates.dateTo();
            com.coach.financier.model.QualityModels.SatisfactionKpis satisfaction = aggregates.satisfaction();
            com.coach.financier.model.QualityModels.ConformityKpis compliance = aggregates.conformity();
            com.coach.financier.model.QualityModels.SatisfactionComplianceMatrix matrix =
                    aggregates.satisfactionVsCompliance();

            if (satisfaction != null && satisfaction.feedbackCount() > 0) {
                satisfactionSummary = satisfaction.feedbackCount() + " review(s) received for "
                        + satisfaction.conversationsClosed() + " closed conversation(s)"
                        + (satisfaction.averageRating() == null ? "" : ", average rating "
                        + satisfaction.averageRating() + " / 5")
                        + (satisfaction.positiveRate() == null ? "" : ", positive reviews "
                        + percent(satisfaction.positiveRate()) + ", negative reviews "
                        + percent(satisfaction.negativeRate()) + ".")
                        + (satisfaction.sufficientSample() ? ""
                        : " The review volume remains limited: these observations are preliminary.");
                if (satisfaction.averageRating() != null && satisfaction.averageRating() >= 4) {
                    positivePoints.add("Average satisfaction is high ("
                            + satisfaction.averageRating() + " / 5).");
                }
                if (satisfaction.positiveRate() != null && satisfaction.positiveRate() >= 0.7) {
                    positivePoints.add(percent(satisfaction.positiveRate())
                            + " of the reviews are positive (4 or 5 stars).");
                }
                if (!satisfaction.sufficientSample()) {
                    status = com.coach.financier.model.QualityModels.STATUS_INSUFFICIENT;
                } else if (satisfaction.negativeRate() != null && satisfaction.negativeRate() >= 0.2) {
                    status = com.coach.financier.model.QualityModels.STATUS_ATTENTION;
                } else {
                    status = com.coach.financier.model.QualityModels.STATUS_WATCH;
                }
            }

            for (com.coach.financier.model.QualityModels.ReasonMetric reason : aggregates.feedbackCategories()) {
                if (irritants.size() >= 5) break;
                irritants.add(reason.label() + " (" + reason.count() + " review(s)"
                        + (reason.evolutionPercent() == null ? "" : ", change "
                        + reason.evolutionPercent() + " %") + ")");
            }
            for (com.coach.financier.model.QualityModels.CommentTheme theme : aggregates.commentThemes()) {
                if (irritants.size() >= 5) break;
                irritants.add(theme.label() + " — " + theme.count() + " comment(s)");
            }

            if (compliance != null && compliance.checksRun() > 0) {
                long creditViolations = detected(aggregates, com.coach.financier.model.QualityModels
                        .CREDIT_SIMULATION_VIOLATION);
                complianceSummary = compliance.checksRun() + " check(s) run, "
                        + compliance.anomalies() + " issue(s) including " + compliance.highAnomalies()
                        + " of HIGH severity.";
                if (creditViolations == 0) {
                    complianceSummary += " No credit pricing outside the rate grid was detected.";
                }
                for (com.coach.financier.model.QualityModels.CheckMetric check : aggregates.qualityChecks()) {
                    if (check.detected() == 0) continue;
                    String line = check.label() + ": " + check.detected() + " detection(s) out of "
                            + check.checksRun() + " check(s) run (severity " + check.severity() + ")";
                    if (com.coach.financier.model.QualityModels.SEVERITY_HIGH.equals(check.severity())) {
                        criticalIssues.add(line);
                    } else {
                        issues.add(line);
                    }
                }
                if (!criticalIssues.isEmpty()) {
                    status = com.coach.financier.model.QualityModels.STATUS_ATTENTION;
                }
            }

            if (matrix != null) {
                crossSummary = "Cross-analysis of the reviews received: " + matrix.satisfiedCompliant()
                        + " satisfied customer(s) with a compliant Coach, " + matrix.satisfiedAnomaly()
                        + " satisfied with an issue, " + matrix.unsatisfiedCompliant()
                        + " unsatisfied with a COMPLIANT Coach, " + matrix.unsatisfiedAnomaly()
                        + " unsatisfied with a real issue.";
                if (matrix.unsatisfiedCompliant() > 0) {
                    notableCases.add("Customer dissatisfaction although the Coach correctly applied the "
                            + "rules: the improvement must focus on teaching and explaining the "
                            + "rule, not on the rule itself.");
                }
                if (matrix.unsatisfiedAnomaly() > 0) {
                    notableCases.add("Priority case: dissatisfaction AND confirmed Coach issue ("
                            + matrix.unsatisfiedAnomaly() + ").");
                }
                if (matrix.satisfiedAnomaly() > 0) {
                    notableCases.add("Issue(s) not felt by the customer ("
                            + matrix.satisfiedAnomaly() + "): to be fixed even if satisfaction is good.");
                }
            }

            // Chiffrage de crédit attendu mais NON réalisé alors que la grille de taux était disponible : la
            // règle est correctement appliquée (aucune violation détectée) mais elle frustre le client.
            long simulationRefusals = reasonCount(aggregates,
                    com.coach.financier.model.QualityModels.ACTION_NOT_POSSIBLE);
            long creditViolations = detected(aggregates, com.coach.financier.model.QualityModels
                    .CREDIT_SIMULATION_VIOLATION);
            if (simulationRefusals > 0 && creditViolations == 0) {
                friction.add(new com.coach.financier.model.QualityModels.RuleFriction(
                        "Credit pricing expected but not provided (rate grid not used)",
                        simulationRefusals + " review(s) mention an impossible action (referral to the "
                                + "official simulator) while no pricing outside the grid was detected: "
                                + "the rule is respected, the customer's expectation is not.",
                        Boolean.TRUE,
                        "Remind the Coach that it can quote from the rate grid while stating that the "
                                + "simulation is indicative and that the subscription is the binding document, and provide the link to the "
                                + "official simulator for the firm offer."));
            }

            for (com.coach.financier.model.MarketingModels.TrendMetric trend : aggregates.trends()) {
                if (trends.size() >= 5 || trend.evolutionPercent() == null) continue;
                String type = trend.evolutionPercent() > 0 ? "IMPROVING" : "DEGRADING";
                if ("anomalies".equals(trend.entityId())) {
                    type = trend.evolutionPercent() > 0 ? "DEGRADING" : "IMPROVING";
                }
                if ("feedback".equals(trend.entityId()) || "anomalies".equals(trend.entityId())) {
                    type = "STABLE";
                }
                trends.add(new com.coach.financier.model.QualityModels.QualityTrend(type, trend.entityName(),
                        trend.current() + " vs " + trend.previous() + " (" + trend.evolutionPercent()
                                + " %) versus the previous period."));
            }

            if (!criticalIssues.isEmpty()) {
                improvements.add(new com.coach.financier.model.QualityModels.PriorityImprovement(
                        "HIGH", "Fix the critical issues detected",
                        String.join(" | ", criticalIssues),
                        "Analyse each HIGH issue (check the messages concerned) then fix the "
                                + "faulty rule or wording.",
                        "Fewer critical issues and stronger compliance."));
                alerts.add(new com.coach.financier.model.QualityModels.QualityAlert("IMPORTANT",
                        "Critical issues detected",
                        criticalIssues.size() + " type(s) of HIGH severity issue over the period."));
            }
            if (!irritants.isEmpty()) {
                improvements.add(new com.coach.financier.model.QualityModels.PriorityImprovement(
                        "MEDIUM", "Address the main customer irritant",
                        irritants.get(0),
                        "Test a more concise and less repetitive answer on follow-up questions, "
                                + "without changing the business safeguards.",
                        "Better perceived readability and higher satisfaction."));
            }
            if (matrix != null && matrix.unsatisfiedCompliant() > 0) {
                improvements.add(new com.coach.financier.model.QualityModels.PriorityImprovement(
                        "MEDIUM", "Explain frustrating rules better",
                        matrix.unsatisfiedCompliant() + " unsatisfied customer(s) while the Coach was "
                                + "compliant.",
                        "Remind that pricing is based on the rate grid (indicative simulation, the "
                                + "subscription is binding) and provide the link to the official simulator.",
                        "Reduced frustration without weakening compliance."));
            }
            if (satisfaction != null && !satisfaction.sufficientSample()) {
                alerts.add(new com.coach.financier.model.QualityModels.QualityAlert("WATCH",
                        "Limited review sample",
                        "The review volume is low: the observations must be confirmed over "
                                + "several periods."));
            }
            if (satisfaction != null && satisfaction.negativeRate() != null && satisfaction.negativeRate() >= 0.2) {
                alerts.add(new com.coach.financier.model.QualityModels.QualityAlert("WATCH",
                        "High share of negative reviews",
                        percent(satisfaction.negativeRate()) + " of the reviews are negative (1 or 2 stars)."));
            }
        }

        return new com.coach.financier.model.QualityModels.QualityReport(
                reportDate,
                new com.coach.financier.model.QualityModels.ReportPeriod(
                        aggregates == null ? reportDate : aggregates.dateFrom(),
                        aggregates == null ? reportDate : aggregates.dateTo()),
                new com.coach.financier.model.QualityModels.ExecutiveSummary(status, satisfactionSummary),
                new com.coach.financier.model.QualityModels.SatisfactionAnalysis(satisfactionSummary,
                        positivePoints, irritants),
                new com.coach.financier.model.QualityModels.QualityAndCompliance(complianceSummary,
                        issues, criticalIssues),
                new com.coach.financier.model.QualityModels.SatisfactionVsCompliance(crossSummary, notableCases),
                friction, trends, improvements, alerts,
                "Report generated in demo mode from the aggregated statistics: customer satisfaction and "
                        + "Coach compliance remain two distinct dimensions, no issue is "
                        + "inferred from a bad rating.",
                java.time.Instant.now().toString(), "MOCK", Boolean.TRUE, null);
    }

    private static long detected(com.coach.financier.model.QualityModels.QualityAggregates aggregates,
                                 String checkType) {
        if (aggregates == null) {
            return 0;
        }
        return aggregates.qualityChecks().stream()
                .filter(check -> checkType.equals(check.checkType()))
                .mapToLong(com.coach.financier.model.QualityModels.CheckMetric::detected)
                .sum();
    }

    private static long reasonCount(com.coach.financier.model.QualityModels.QualityAggregates aggregates,
                                    String reason) {
        if (aggregates == null) {
            return 0;
        }
        return aggregates.feedbackCategories().stream()
                .filter(metric -> reason.equals(metric.reason()))
                .mapToLong(com.coach.financier.model.QualityModels.ReasonMetric::count)
                .sum();
    }

    private static String percent(Double rate) {
        return rate == null ? "n/a" : Math.round(rate * 1000) / 10.0 + " %";
    }

    /**
     * Mode démo — ANALYSTE FEEDBACK CONSEILLER : rapport DÉTERMINISTE construit uniquement à partir
     * des KPI calculés par le backend. Aucun chiffre inventé, aucune instruction de commentaire
     * suivie, aucune recommandation de modification automatique du Coach.
     */
    @Override
    public com.coach.financier.model.AdvisorFeedbackModels.AdvisorFeedbackReport analyzeAdvisorFeedback(
            com.coach.financier.model.AdvisorFeedbackModels.AdvisorFeedbackAggregates aggregates,
            AIModels.AIProvider provider) {
        java.util.List<com.coach.financier.model.AdvisorFeedbackModels.StrengthItem> strengths = new ArrayList<>();
        java.util.List<com.coach.financier.model.AdvisorFeedbackModels.IssueItem> issues = new ArrayList<>();
        java.util.List<com.coach.financier.model.AdvisorFeedbackModels.ProductAnalysisItem> productAnalysis =
                new ArrayList<>();
        java.util.List<String> overestimation = new ArrayList<>();
        java.util.List<com.coach.financier.model.AdvisorFeedbackModels.AdvisorTrend> trends = new ArrayList<>();
        java.util.List<com.coach.financier.model.AdvisorFeedbackModels.AdvisorPriorityImprovement> improvements =
                new ArrayList<>();
        java.util.List<String> watchPoints = new ArrayList<>();

        String reportDate = LocalDate.now().toString();
        String status = com.coach.financier.model.AdvisorFeedbackModels.STATUS_INSUFFICIENT;
        String summaryText = "No usable advisor feedback over the period: the data provided does not "
                + "allow any conclusion on the relevance of the Coach's work.";
        String interestSummary = "No interest level correction provided.";
        String nextActionSummary = "No assessment of the recommended follow-up provided.";
        String emailSummary = "No email assessment provided.";

        if (aggregates != null) {
            reportDate = aggregates.dateTo() == null ? reportDate : aggregates.dateTo();
            com.coach.financier.model.AdvisorFeedbackModels.AdvisorKpis kpis = aggregates.kpis();
            if (kpis != null && kpis.feedbackCount() > 0) {
                summaryText = kpis.sessionsEvaluated() + " case file(s) evaluated: "
                        + percent(kpis.relevantRate()) + " judged relevant, "
                        + percent(kpis.needsImprovementRate()) + " to improve, "
                        + percent(kpis.incorrectRate()) + " incorrect."
                        + (kpis.sufficientSample() ? ""
                        : " The feedback volume remains limited: these observations are preliminary.");
                status = kpis.relevantRate() != null && kpis.relevantRate() >= 0.7
                        ? com.coach.financier.model.AdvisorFeedbackModels.STATUS_GOOD
                        : (kpis.incorrectRate() != null && kpis.incorrectRate() >= 0.15
                        ? com.coach.financier.model.AdvisorFeedbackModels.STATUS_ATTENTION
                        : com.coach.financier.model.AdvisorFeedbackModels.STATUS_WATCH);
                if (kpis.relevantRate() != null && kpis.relevantRate() >= 0.7) {
                    strengths.add(new com.coach.financier.model.AdvisorFeedbackModels.StrengthItem(
                            "Analyses generally validated",
                            percent(kpis.relevantRate()) + " of the case files evaluated are judged relevant by the "
                                    + "advisors."));
                }
                if (kpis.emailReadyOrMinorRate() != null && kpis.emailReadyOrMinorRate() >= 0.6) {
                    strengths.add(new com.coach.financier.model.AdvisorFeedbackModels.StrengthItem(
                            "Usable prepared emails",
                            percent(kpis.emailReadyOrMinorRate()) + " of the emails evaluated are ready to use or "
                                    + "only require minor changes."));
                }
                if (kpis.productRelevanceRate() != null && kpis.productRelevanceRate() >= 0.8) {
                    strengths.add(new com.coach.financier.model.AdvisorFeedbackModels.StrengthItem(
                            "Products judged relevant",
                            percent(kpis.productRelevanceRate()) + " of the products evaluated are validated by the "
                                    + "advisors."));
                }
            }
            for (com.coach.financier.model.AdvisorFeedbackModels.AreaMetric area : aggregates.areas()) {
                if (issues.size() >= 5) break;
                issues.add(new com.coach.financier.model.AdvisorFeedbackModels.IssueItem(area.area(),
                        area.label() + ": " + area.count() + " correction(s) reported"
                                + (area.evolutionPercent() == null ? "" : " (change "
                                + area.evolutionPercent() + " %)"), "MEDIUM"));
            }
            for (com.coach.financier.model.AdvisorFeedbackModels.ReasonMetric reason : aggregates.reasons()) {
                if (issues.size() >= 6) break;
                issues.add(new com.coach.financier.model.AdvisorFeedbackModels.IssueItem(
                        com.coach.financier.model.AdvisorFeedbackModels.AREA_OTHER,
                        "Reason \"" + reason.label() + "\" reported " + reason.count() + " times", "LOW"));
            }
            for (com.coach.financier.model.AdvisorFeedbackModels.ProductFeedbackMetric product : aggregates.products()) {
                if (productAnalysis.size() >= 8) break;
                String signal = product.relevanceRate() == null
                        ? com.coach.financier.model.AdvisorFeedbackModels.STATUS_INSUFFICIENT
                        : (product.relevanceRate() >= 0.8 ? "POSITIVE"
                        : (product.relevanceRate() >= 0.5 ? "MIXED" : "NEGATIVE"));
                productAnalysis.add(new com.coach.financier.model.AdvisorFeedbackModels.ProductAnalysisItem(
                        product.productId(), product.productName(),
                        product.assessments() + " assessment(s), " + product.relevant() + " relevant, "
                                + product.notRelevant() + " not relevant, " + product.interestCorrections()
                                + " level correction(s)"
                                + (product.addedByAdvisor() > 0 ? ", added " + product.addedByAdvisor()
                                + " times by an advisor" : ""), signal));
            }
            for (com.coach.financier.model.AdvisorFeedbackModels.InterestCorrectionMetric correction
                    : aggregates.interestCorrections()) {
                if (overestimation.size() >= 5) break;
                String direction = "HIGH".equals(correction.fromLevel()) || "MEDIUM".equals(correction.fromLevel())
                        && "LOW".equals(correction.toLevel()) ? "overestimation" : "correction";
                overestimation.add(correction.productName() + ": " + correction.fromLevel() + " → "
                        + correction.toLevel() + " (" + correction.count() + " times, " + direction + ")");
            }
            if (!aggregates.interestCorrections().isEmpty()) {
                interestSummary = aggregates.kpis().interestCorrections()
                        + " interest level correction(s): the AI value and the advisor value are "
                        + "kept separately.";
            }
            if (!aggregates.emailQuality().isEmpty()) {
                emailSummary = "Distribution of the emails evaluated: "
                        + aggregates.emailQuality().stream()
                        .map(item -> item.label() + " = " + item.count())
                        .collect(java.util.stream.Collectors.joining(", ")) + ".";
            }
            if (aggregates.kpis() != null && aggregates.kpis().productAssessments() > 0) {
                nextActionSummary = "The recommended follow-up is assessed by the advisors; "
                        + aggregates.kpis().productAssessments() + " product(s) assessed over the period.";
            }
            for (com.coach.financier.model.MarketingModels.TrendMetric trend : aggregates.trends()) {
                if (trends.size() >= 5 || trend.evolutionPercent() == null) continue;
                trends.add(new com.coach.financier.model.AdvisorFeedbackModels.AdvisorTrend(
                        trend.evolutionPercent() > 0 ? "DEGRADING" : "IMPROVING",
                        trend.entityName(), trend.current() + " vs " + trend.previous() + " ("
                                + trend.evolutionPercent() + " %) versus the previous period."));
            }
            if (!aggregates.areas().isEmpty()) {
                var top = aggregates.areas().get(0);
                improvements.add(new com.coach.financier.model.AdvisorFeedbackModels.AdvisorPriorityImprovement(
                        "MEDIUM", "Address the most frequent correction",
                        top.label() + " (" + top.count() + " report(s))",
                        "Analyse the case files concerned and examine the criteria used by the Coach, "
                                + "without automatically changing any threshold or rule.",
                        "Fewer manual corrections and time saved for the advisors."));
            }
            if (!overestimation.isEmpty()) {
                improvements.add(new com.coach.financier.model.AdvisorFeedbackModels.AdvisorPriorityImprovement(
                        "HIGH", "Reduce overestimation of interest levels",
                        String.join(" | ", overestimation),
                        "Study the criteria distinguishing explicit interest from a simple information "
                                + "request (comparison of the corrected case files).",
                        "Better sales prioritisation and greater trust in the case file."));
                watchPoints.add("Several interest levels corrected by the advisors: converge with "
                        + "the product signals.");
            }
            if (aggregates.kpis() != null && !aggregates.kpis().sufficientSample()) {
                watchPoints.add("Limited advisor feedback volume: confirm over several periods.");
            }
            if (aggregates.demo()) {
                watchPoints.add("Demo feedbacks (source=DEMO) are present over the period.");
            }
        }

        return new com.coach.financier.model.AdvisorFeedbackModels.AdvisorFeedbackReport(
                reportDate,
                new com.coach.financier.model.AdvisorFeedbackModels.AdvisorReportPeriod(
                        aggregates == null ? reportDate : aggregates.dateFrom(),
                        aggregates == null ? reportDate : aggregates.dateTo()),
                new com.coach.financier.model.AdvisorFeedbackModels.AdvisorExecutiveSummary(status, summaryText),
                strengths, issues, productAnalysis,
                new com.coach.financier.model.AdvisorFeedbackModels.InterestLevelAnalysis(interestSummary,
                        overestimation, List.of()),
                new com.coach.financier.model.AdvisorFeedbackModels.SectionAnalysis(nextActionSummary, List.of()),
                new com.coach.financier.model.AdvisorFeedbackModels.SectionAnalysis(emailSummary, List.of()),
                trends, improvements, watchPoints,
                "Report generated in demo mode from the computed KPIs: it suggests leads, it never automatically "
                        + "modifies the Coach (prompt, rules, thresholds, catalogue, code).",
                java.time.Instant.now().toString(), "MOCK", Boolean.TRUE, null);
    }

    private String money(double value) { return String.format(java.util.Locale.FRANCE, "%.2f", value); }
}
