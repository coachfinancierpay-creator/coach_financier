package com.coach.financier.service;

import com.coach.financier.model.ConversationModels;
import com.coach.financier.model.FinancialSummary;
import com.coach.financier.model.PromptOptimizationModels;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contexte du CLIENT SIMULÉ — l'IA qui JOUE le client (elle ne conseille jamais, elle questionne).
 * <p>
 * Deux entrées du projet s'en servent, avec EXACTEMENT les mêmes règles :
 * <ul>
 *   <li>l'atelier d'optimisation des prompts (« Agent C », {@code agent/prompt_client.txt}) ;</li>
 *   <li>le bouton « Client auto » de la page coach, qui propose la question suivante du client à partir de
 *       l'historique réel de la conversation.</li>
 * </ul>
 * Une seule source de vérité pour ce que le client est autorisé à savoir : les trois chiffres de son dossier
 * et le brief de son projet — jamais la fiche produit, ni les indicateurs internes du Coach.
 */
public final class SimulatedClientContext {

    /** Longueur maximale du rappel de la première demande du client (le contexte envoyé reste court). */
    private static final int MAX_BRIEF_LENGTH = 600;

    private SimulatedClientContext() {
    }

    /**
     * Les TROIS chiffres que le client connaît (demande explicite) : solde du compte courant, épargne totale et
     * mensualité de crédit en cours. Aucune autre donnée du dossier n'est transmise au client simulé.
     */
    public static Map<String, Object> figures(FinancialSummary summary) {
        Map<String, Object> figures = new LinkedHashMap<>();
        figures.put("compteCourant", Map.of(
                "libelle", "Solde du compte courant",
                "montant", summary.currentAccountBalance(),
                "devise", "EUR"));
        figures.put("epargne", Map.of(
                "libelle", "Épargne disponible",
                "montant", summary.savingsBalance(),
                "devise", "EUR"));
        figures.put("creditEnCours", Map.of(
                "libelle", "Mensualité de crédit en cours",
                "montant", summary.monthlyLoanPayments(),
                "devise", "EUR"));
        return figures;
    }

    /**
     * Brief du client DANS LA PAGE COACH : le client est celui de la conversation réelle — il n'y en a pas
     * d'autre. Le brief rappelle donc sa <b>première demande</b>, mot pour mot, et interdit explicitement
     * d'inventer un profil, un projet ou un chiffre absents de la conversation.
     * <p>
     * La suite de l'échange vit dans {@code previousExchanges} (le transcript complet) : inutile de la répéter
     * ici, ce qui garderait un contexte deux fois plus gros pour la même information.
     */
    public static String briefFromConversation(List<ConversationModels.Message> transcript) {
        String firstRequest = firstClientMessage(transcript);
        StringBuilder brief = new StringBuilder();
        brief.append("Le client est celui de CETTE conversation : tu reprends SON projet et SA situation, ")
                .append("tels qu'ils ressortent de ses messages (ils te sont fournis dans previousExchanges). ")
                .append("N'invente ni un autre client, ni un autre projet, ni un chiffre qui ne serait pas ")
                .append("dans la conversation ou dans clientFigures.");
        if (!firstRequest.isEmpty()) {
            brief.append("\nSa demande initiale, mot pour mot : « ").append(bounded(firstRequest)).append(" »");
        }
        return brief.toString();
    }

    /** Premier message du CLIENT : c'est lui qui porte le projet initial de la conversation. */
    private static String firstClientMessage(List<ConversationModels.Message> transcript) {
        if (transcript == null) {
            return "";
        }
        return transcript.stream()
                .filter(message -> PromptOptimizationModels.ROLE_USER.equals(message.role()))
                .map(ConversationModels.Message::content)
                .filter(content -> content != null && !content.isBlank())
                .findFirst()
                .map(String::strip)
                .orElse("");
    }

    private static String bounded(String text) {
        return text.length() <= MAX_BRIEF_LENGTH ? text : text.substring(0, MAX_BRIEF_LENGTH) + "…";
    }
}
