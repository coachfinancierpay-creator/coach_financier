package com.coach.financier.service;

import com.coach.financier.config.QualityProperties;
import com.coach.financier.model.ConversationModels;
import com.coach.financier.model.LogEntry;
import com.coach.financier.model.ProductFamily;
import com.coach.financier.model.ProjectType;
import com.coach.financier.model.QualityModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CONTRÔLES AUTOMATIQUES de la qualité / conformité du Coach (§15 à §21), indépendants de la
 * satisfaction exprimée par le client.
 * <p>
 * Principe : un contrôle n'est exécuté que s'il est <b>fiable</b> avec les données disponibles.
 * Les contrôles réellement implémentés sont déclarés par {@link QualityProperties#enabledChecks()} ;
 * les autres ne sont ni exécutés ni affichés (jamais de faux « 0 », §25).
 * <p>
 * Contrôles implémentés :
 * <ul>
 *   <li>{@code CREDIT_SIMULATION_VIOLATION} — le Coach a produit un chiffrage de crédit
 *       (mensualité, coût total, capacité d'emprunt…) SANS s'appuyer sur la grille de taux fournie ni
 *       préciser que la simulation est indicative. Une simulation issue de la grille, annoncée comme NON
 *       CONTRACTUELLE (la souscription fait foi), est conforme — comme la REDIRECTION vers le simulateur
 *       officiel lorsqu'aucune grille n'est disponible (§16).</li>
 *   <li>{@code PRODUCT_MISMATCH} — un produit d'une famille non autorisée pour le projet a été
 *       présenté (les crédits EXISTANTS ne sont jamais concernés : ils ne sont pas des offres).</li>
 *   <li>{@code INVENTED_URL} — une URL citée par le Coach n'existe pas dans les fiches officielles.</li>
 *   <li>{@code UNANSWERED_REQUEST} — la conversation s'est terminée sans réponse du Coach (ou sur un
 *       échec technique de l'IA).</li>
 *   <li>{@code MISSING_DATA_NOT_RETRIEVED} — l'IA a demandé des données qui n'ont jamais été fournies
 *       (dernier statut {@code NEED_DATA}).</li>
 *   <li>{@code EXCESSIVE_REPETITION} — phrases reformulées à l'identique, salutations répétées
 *       (contrôle volontairement PRUDENT, §18).</li>
 * </ul>
 * NON implémentés dans cette version (documentés comme tels) : {@code UNNECESSARY_ADVISOR_REDIRECT},
 * {@code UNSUPPORTED_PRODUCT_CLAIM}, {@code INVENTED_DATA}, {@code CONVERSATION_CONTEXT_LOST}.
 */
@Service
public class CoachQualityCheckService {
    private static final Logger log = LoggerFactory.getLogger(CoachQualityCheckService.class);

    private static final String COACH_ROLE = "assistant";
    private static final String USER_ROLE = "user";

    /** Marqueurs d'un chiffrage de crédit réalisé par le Coach lui-même. */
    private static final List<Pattern> SIMULATION_TRIGGERS = List.of(
            Pattern.compile("(?i)(i\\s+(can|will|could)\\s+(calculate|simulate|compute))"),
            Pattern.compile("(?i)here\\s+is\\s+(the|a|an)\\s+(monthly payment|simulation|estimate|estimation)"),
            Pattern.compile("(?i)monthly\\s+payment\\s*(would be|is|of|:|=)"
                    + "[^.]{0,20}?[\\d][\\d\\s.,]{2,}\\s?(€|euros)"),
            Pattern.compile("(?i)total\\s+cost[^.]{0,25}?[\\d][\\d\\s.,]{2,}\\s?(€|euros)"),
            Pattern.compile("(?i)(interest|interests)[^.]{0,20}?[\\d][\\d\\s.,]{2,}\\s?(€|euros)"),
            Pattern.compile("(?i)(borrowing\\s+capacity|financing\\s+capacity|financeable\\s+amount)"
                    + "[^.]{0,30}?[\\d][\\d\\s.,]{2,}\\s?(€|euros)"),
            Pattern.compile("(?i)simulation[^.]{0,30}?[\\d][\\d\\s.,]{2,}\\s?(€|euros)"),
            Pattern.compile("(?i)[\\d][\\d\\s.,]{2,}\\s?(€|euros)\\s*(per\\s+month|/\\s*month|monthly)"));

    /** Libellés d'un chiffrage de crédit (prérequis de la détection d'un TABLEAU de chiffrage ci-dessous). */
    private static final Pattern SIMULATION_LABEL = Pattern.compile(
            "(?i)(monthly\\s+payment|total\\s+cost|total\\s+amount|debit\\s+rate|interest\\s+rate)");

    /**
     * Ligne de TABLEAU de chiffrage (plusieurs durées / mensualités comparées) : deux montants en euros
     * séparés par une barre de tableau. Associée à {@link #SIMULATION_LABEL} dans le MÊME message, elle évite
     * de signaler une simple fourchette de montant (« de 1 000 € à 75 000 € ») ou un tableau de comparaison
     * de produits ; et parce que le libellé peut précéder la ligne, les deux motifs sont testés séparément.
     */
    private static final Pattern SIMULATION_TABLE_ROW = Pattern.compile(
            "[^\\n]*?[\\d][\\d\\s.,]{2,}\\s?(?:€|euros)[^\\n|]{0,12}\\|[^\\n]*?[\\d][\\d\\s.,]{2,}\\s?(?:€|euros)");

    /**
     * Marqueurs d'un comportement CONFORME : soit le Coach s'appuie sur la GRILLE DE TAUX fournie et annonce
     * une simulation indicative et non contractuelle, soit il refuse / redirige vers l'outil officiel
     * (cas d'une conversation sans grille de taux disponible).
     */
    private static final Pattern COMPLIANT_MARKERS = Pattern.compile(
            "(?i)(rate\\s+grid|pricing\\s+grid|the\\s+subscription\\s+is\\s+binding|loan\\s+agreement"
                    + "|indicative|non[\\s-]?contractual|assumptions?\\s+(of|from)\\s+the\\s+grid"
                    + "|simulator|official\\s+tool|official\\s+website|i\\s+(cannot|can't|am\\s+not\\s+able\\s+to)\\s+(calculate|simulate|perform)"
                    + "|i\\s+am\\s+not\\s+allowed|regulation"
                    + "|we\\s+do\\s+not\\s+(calculate|perform)|estimate\\s+it\\s+yourself)");

    /** Montants liés à un crédit EXISTANT (données du client) : hors périmètre du contrôle. */
    private static final Pattern EXISTING_CREDIT = Pattern.compile(
            "(?i)(existing\\s+(credit|loan)|current\\s+(credit|loan)|your\\s+(credit|loan)|current\\s+monthly\\s+payment"
                    + "|current\\s+commitment)");

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("[.!?\\n]+");
    /** URLs brutes écrites directement par le Coach (en plus du format [URL|nom|url]). */
    private static final Pattern RAW_URL = Pattern.compile("https?://[^\\s\\)\\]\"'<>]+");
    private static final Pattern GREETING = Pattern.compile("(?i)^(hi|hello|hey|good\\s+(morning|afternoon|evening))\\b.*");
    private static final int MIN_REPEATED_SENTENCE = 40;

    private final QualityProperties properties;
    private final QualityCheckStore checkStore;
    private final ProjectProductMappingService mappingService;
    private final ProductUrlIndex productUrlIndex;
    private final AILogService aiLogService;
    private final String appointmentUrl;

    public CoachQualityCheckService(QualityProperties properties,
                                    QualityCheckStore checkStore,
                                    ProjectProductMappingService mappingService,
                                    ProductUrlIndex productUrlIndex,
                                    AILogService aiLogService,
                                    @Value("${app.suivi.advisor-appointment-url:}") String appointmentUrl) {
        this.properties = properties;
        this.checkStore = checkStore;
        this.mappingService = mappingService;
        this.productUrlIndex = productUrlIndex;
        this.aiLogService = aiLogService;
        this.appointmentUrl = appointmentUrl;
    }

    /** Contrôles réellement exécutés par cette version. */
    public List<String> implementedChecks() {
        return properties.enabledChecks();
    }

    /**
     * Exécute les contrôles d'une conversation et les PERSISTE (best effort : un échec d'écriture ou
     * de contrôle ne doit jamais faire échouer la clôture de conversation).
     */
    public List<QualityModels.QualityCheck> run(String sessionId, ConversationModels.Conversation conversation) {
        if (!properties.isEnabled() || conversation == null) {
            return List.of();
        }
        List<QualityModels.QualityCheck> evaluated = evaluate(sessionId, conversation,
                aiLogService.latest());
        try {
            return checkStore.append(evaluated);
        } catch (Exception e) {
            log.warn("Contrôles qualité non persistés pour la session {} : {}", sessionId, e.getMessage());
            return List.of();
        }
    }

    /** Évaluation PURE (sans écriture) des contrôles implémentés — utilisée par les tests. */
    public List<QualityModels.QualityCheck> evaluate(String sessionId,
                                                     ConversationModels.Conversation conversation,
                                                     List<LogEntry> logs) {
        List<QualityModels.QualityCheck> checks = new ArrayList<>();
        if (conversation == null) {
            return checks;
        }
        List<ConversationModels.Message> transcript = conversation.transcript();
        List<String> coachMessages = transcript.stream()
                .filter(message -> COACH_ROLE.equalsIgnoreCase(message.role()))
                .map(ConversationModels.Message::content)
                .filter(content -> content != null && !content.isBlank())
                .toList();
        List<LogEntry> sessionLogs = logs == null ? List.of() : logs.stream()
                .filter(entry -> sessionId == null || sessionId.equals(entry.sessionId()))
                .toList();

        if (properties.isCheckEnabled(QualityModels.CREDIT_SIMULATION_VIOLATION)) {
            checks.add(creditSimulation(sessionId, coachMessages));
        }
        if (properties.isCheckEnabled(QualityModels.PRODUCT_MISMATCH)) {
            checks.add(productMismatch(sessionId, conversation));
        }
        if (properties.isCheckEnabled(QualityModels.INVENTED_URL)) {
            checks.add(inventedUrl(sessionId, coachMessages));
        }
        if (properties.isCheckEnabled(QualityModels.UNANSWERED_REQUEST)) {
            checks.add(unansweredRequest(sessionId, transcript, sessionLogs));
        }
        if (properties.isCheckEnabled(QualityModels.MISSING_DATA_NOT_RETRIEVED)) {
            checks.add(missingDataNotRetrieved(sessionId, sessionLogs));
        }
        if (properties.isCheckEnabled(QualityModels.EXCESSIVE_REPETITION)) {
            checks.add(excessiveRepetition(sessionId, coachMessages));
        }
        return checks;
    }

    // ------------------------------------------------------------------ contrôles

    private QualityModels.QualityCheck creditSimulation(String sessionId, List<String> coachMessages) {
        for (String message : coachMessages) {
            if (EXISTING_CREDIT.matcher(message).find()) {
                continue; // rappel d'un engagement réel : hors périmètre
            }
            boolean triggered = SIMULATION_TRIGGERS.stream().anyMatch(pattern -> pattern.matcher(message).find())
                    || (SIMULATION_LABEL.matcher(message).find() && SIMULATION_TABLE_ROW.matcher(message).find());
            if (!triggered) {
                continue;
            }
            if (COMPLIANT_MARKERS.matcher(message).find()) {
                continue; // grille de taux citée (simulation autorisée) ou refus/redirection explicite = conforme (§16)
            }
            return check(sessionId, QualityModels.CREDIT_SIMULATION_VIOLATION, true,
                    "The Coach appears to have produced a credit quote itself (monthly payment, total cost or "
                            + "borrowing capacity) without relying on the rate grid provided: an allowed simulation "
                            + "must cite the grid and state that it is indicative (the subscription is binding).", 0.7);
        }
        return check(sessionId, QualityModels.CREDIT_SIMULATION_VIOLATION, false,
                "No credit quote produced by the Coach was detected.", null);
    }

    private QualityModels.QualityCheck productMismatch(String sessionId,
                                                       ConversationModels.Conversation conversation) {
        Set<ProjectType> projects = new LinkedHashSet<>();
        conversation.projects().forEach(project -> {
            if (project != null && project.getType() != null && project.getType() != ProjectType.UNKNOWN) {
                projects.add(project.getType());
            }
        });
        if (projects.isEmpty()) {
            return check(sessionId, QualityModels.PRODUCT_MISMATCH, false,
                    "No identifiable project: check not applicable.", null);
        }
        List<String> anomalies = new ArrayList<>();
        for (Map<String, Object> product : conversation.discussedProducts()) {
            Object family = product.get("family");
            Object id = product.get("id");
            if (family == null || id == null) {
                continue;
            }
            ProductFamily parsed = parseFamily(String.valueOf(family));
            if (parsed == null) {
                continue;
            }
            boolean allowed = projects.stream()
                    .anyMatch(project -> mappingService.isFamilyAllowed(project, parsed));
            if (!allowed) {
                anomalies.add(String.valueOf(id) + " (" + parsed + ")");
            }
        }
        if (anomalies.isEmpty()) {
            return check(sessionId, QualityModels.PRODUCT_MISMATCH, false,
                    "All the offers presented belong to allowed families.", null);
        }
        return check(sessionId, QualityModels.PRODUCT_MISMATCH, true,
                "Offer(s) presented outside the allowed families for the project "
                        + projects + ": " + String.join(", ", anomalies) + ".", 0.9);
    }

    private QualityModels.QualityCheck inventedUrl(String sessionId, List<String> coachMessages) {
        Set<String> allowed = new LinkedHashSet<>(productUrlIndex.allUrls());
        if (appointmentUrl != null && !appointmentUrl.isBlank()) {
            allowed.add(normalizeUrl(appointmentUrl));
        }
        Set<String> suspects = new LinkedHashSet<>();
        for (String message : coachMessages) {
            Set<String> urls = new LinkedHashSet<>(UrlLinkRenderer.extractUrls(message));
            Matcher raw = RAW_URL.matcher(message);
            while (raw.find()) {
                urls.add(raw.group());
            }
            for (String url : urls) {
                if (UrlLinkRenderer.isHttpUrl(url) && !allowed.contains(normalizeUrl(url))) {
                    suspects.add(url);
                }
            }
        }
        if (suspects.isEmpty()) {
            return check(sessionId, QualityModels.INVENTED_URL, false,
                    "All the URLs cited correspond to official product pages.", null);
        }
        return check(sessionId, QualityModels.INVENTED_URL, true,
                "URL(s) missing from the official product pages: " + String.join(", ", suspects) + ".", 0.9);
    }

    private QualityModels.QualityCheck unansweredRequest(String sessionId,
                                                         List<ConversationModels.Message> transcript,
                                                         List<LogEntry> sessionLogs) {
        boolean error = sessionLogs.stream().anyMatch(entry -> "ERROR".equalsIgnoreCase(entry.status()));
        if (error) {
            return check(sessionId, QualityModels.UNANSWERED_REQUEST, true,
                    "An AI call in the conversation ended in error: the request could not be processed.",
                    0.9);
        }
        if (!transcript.isEmpty()) {
            ConversationModels.Message last = transcript.get(transcript.size() - 1);
            if (last.role() != null && USER_ROLE.equalsIgnoreCase(last.role())) {
                return check(sessionId, QualityModels.UNANSWERED_REQUEST, true,
                        "The conversation ends with a customer message unanswered by the Coach.", 0.8);
            }
        }
        return check(sessionId, QualityModels.UNANSWERED_REQUEST, false,
                "Every customer request received an answer from the Coach.", null);
    }

    private QualityModels.QualityCheck missingDataNotRetrieved(String sessionId, List<LogEntry> sessionLogs) {
        for (LogEntry entry : sessionLogs) {
            if ("NEED_DATA".equalsIgnoreCase(entry.status())
                    && entry.requestedData() != null && !entry.requestedData().isEmpty()) {
                return check(sessionId, QualityModels.MISSING_DATA_NOT_RETRIEVED, true,
                        "Data requested by the AI and not provided: "
                                + String.join(", ", entry.requestedData()) + ".", 0.6);
            }
        }
        return check(sessionId, QualityModels.MISSING_DATA_NOT_RETRIEVED, false,
                "No data request was left unanswered.", null);
    }

    private QualityModels.QualityCheck excessiveRepetition(String sessionId, List<String> coachMessages) {
        Map<String, Integer> sentences = new LinkedHashMap<>();
        int greetings = 0;
        for (String message : coachMessages) {
            for (String raw : SENTENCE_SPLIT.split(message)) {
                String sentence = normalizeSentence(raw);
                if (sentence.length() < MIN_REPEATED_SENTENCE) {
                    continue;
                }
                sentences.merge(sentence, 1, Integer::sum);
            }
            if (GREETING.matcher(message.strip()).find()) {
                greetings++;
            }
        }
        long repeated = sentences.values().stream().filter(count -> count >= 2).count();
        if (repeated > 0 || greetings >= 2) {
            List<String> details = new ArrayList<>();
            if (repeated > 0) {
                details.add(repeated + " sentence(s) repeated identically");
            }
            if (greetings >= 2) {
                details.add(greetings + " greeting(s) in the same conversation");
            }
            return check(sessionId, QualityModels.EXCESSIVE_REPETITION, true,
                    "Repetition detected: " + String.join(" and ", details)
                            + " (prudent check: information needed for the reasoning is not an error).",
                    0.5);
        }
        return check(sessionId, QualityModels.EXCESSIVE_REPETITION, false,
                "No significant repetition detected.", null);
    }

    // ------------------------------------------------------------------ utilitaires

    private QualityModels.QualityCheck check(String sessionId, String checkType, boolean detected,
                                             String details, Double confidence) {
        return new QualityModels.QualityCheck(
                checkId(sessionId, checkType),
                Instant.now().toString(),
                sessionId,
                checkType,
                properties.severityFor(checkType),
                detected,
                QualityModels.SOURCE_AUTOMATIC,
                details,
                detected ? confidence : null);
    }

    /** Identifiant DÉTERMINISTE : relancer les contrôles d'une même session ne crée pas de doublon. */
    public static String checkId(String sessionId, String checkType) {
        return "chk-" + (sessionId == null ? "unknown" : sessionId) + "-" + checkType;
    }

    private static ProductFamily parseFamily(String raw) {
        try {
            return ProductFamily.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeUrl(String url) {
        String value = url == null ? "" : url.trim().toLowerCase(Locale.ROOT);
        while (value.endsWith("/") || value.endsWith(".") || value.endsWith(")") || value.endsWith(",")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String normalizeSentence(String raw) {
        Matcher matcher = Pattern.compile("[^\\p{L}\\p{N}\\s]").matcher(raw);
        return matcher.replaceAll(" ").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
