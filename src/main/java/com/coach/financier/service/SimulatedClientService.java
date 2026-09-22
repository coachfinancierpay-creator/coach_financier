package com.coach.financier.service;

import com.coach.financier.ai.AIServiceFactory;
import com.coach.financier.model.AIModels;
import com.coach.financier.model.ConversationModels;
import com.coach.financier.model.FinancialSummary;
import com.coach.financier.model.PromptOptimizationModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CLIENT AUTO de la page coach : l'IA joue le CLIENT et propose la question suivante, à partir de
 * l'historique réel de la conversation (bouton « Client auto » du bandeau).
 * <p>
 * C'est le même rôle que l'« Agent C » de l'atelier d'optimisation des prompts — mêmes garanties :
 * <ul>
 *   <li>le client ne conseille jamais, ne calcule rien et n'invente aucun chiffre : il ne connaît que les
 *       <b>trois chiffres de son dossier</b> ({@link SimulatedClientContext#figures}) ;</li>
 *   <li>il lit TOUT l'échange déjà présent, pour ne jamais reposer une question ni redemander une information
 *       déjà donnée ;</li>
 *   <li>la proposition n'est <b>jamais envoyée</b> : elle remplit le champ de saisie, que l'humain relit,
 *       corrige et envoie (ou pas) — c'est un outil de DÉMO, pas un automatisme.</li>
 * </ul>
 * Le mode démo (MOCK) est refusé : tenir une conversation crédible demande un modèle réel.
 */
@Service
public class SimulatedClientService {
    private static final Logger log = LoggerFactory.getLogger(SimulatedClientService.class);

    private final ConversationService conversationService;
    private final FinancialAnalysisService financialAnalysis;
    private final AIServiceFactory aiServiceFactory;

    public SimulatedClientService(ConversationService conversationService,
                                  FinancialAnalysisService financialAnalysis,
                                  AIServiceFactory aiServiceFactory) {
        this.conversationService = conversationService;
        this.financialAnalysis = financialAnalysis;
        this.aiServiceFactory = aiServiceFactory;
    }

    /**
     * Propose la question suivante du client pour une conversation en cours.
     *
     * @param sessionId session de la page coach (l'historique vit en mémoire côté serveur)
     * @param provider  fournisseur IA choisi dans l'IHM — doit être RÉEL (pas le mode démo)
     * @return le tour du client simulé ({@code question}, {@code endConversation}, {@code reason})
     * @throws IllegalArgumentException session sans aucun échange, ou fournisseur de démonstration
     */
    public PromptOptimizationModels.ClientTurn nextQuestion(String sessionId, AIModels.AIProvider provider) {
        AIModels.AIProvider agentProvider = requireRealProvider(provider);
        ConversationModels.Conversation conversation =
                sessionId == null ? null : conversationService.find(sessionId);
        List<ConversationModels.Message> transcript =
                conversation == null ? List.of() : conversation.transcript();
        if (transcript.isEmpty()) {
            // Rien à lire : sans historique, le modèle inventerait un client. On explique quoi faire à la place.
            throw new IllegalArgumentException("Aucun échange dans cette conversation : posez d'abord une "
                    + "question au Coach, puis cliquez de nouveau (après un redémarrage du backend, "
                    + "l'historique en mémoire est perdu).");
        }
        FinancialSummary summary = financialAnalysis.analyze();
        int asked = (int) transcript.stream()
                .filter(message -> PromptOptimizationModels.ROLE_USER.equals(message.role()))
                .count();
        int turnNumber = asked + 1;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientBrief", SimulatedClientContext.briefFromConversation(transcript));
        payload.put("clientFigures", SimulatedClientContext.figures(summary));
        payload.put("turnNumber", turnNumber);
        payload.put("previousExchanges", transcript);
        // Profondeur placée APRÈS la question courante : le compteur ne force donc jamais la clôture ici.
        // Le client s'arrête seulement s'il a réellement obtenu ce qu'il voulait (`endConversation`).
        payload.put("depth", turnNumber + 1);

        PromptOptimizationModels.ClientTurn turn = aiServiceFactory.get(agentProvider)
                .clientTurn(payload, agentProvider);
        log.info("Client auto (page coach) : question {} proposée pour la session {} « {} »{}",
                turnNumber, sessionId, abbreviate(turn.question()),
                turn.finished() ? " (le client n'a plus de question)" : "");
        return turn;
    }

    /** Le mode démo ne peut pas jouer un client crédible : seule une IA réelle est acceptée. */
    private static AIModels.AIProvider requireRealProvider(AIModels.AIProvider provider) {
        if (provider == null || provider == AIModels.AIProvider.MOCK) {
            throw new IllegalArgumentException("Client auto : il faut une IA réelle pour jouer le client "
                    + "(DeepSeek, GPT ou un modèle local) — le mode démo ne produit pas de conversation. "
                    + "Choisissez un fournisseur dans « Avancé ».");
        }
        return provider;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String clean = text.replace('\n', ' ').strip();
        return clean.length() <= 80 ? clean : clean.substring(0, 80) + "…";
    }
}
