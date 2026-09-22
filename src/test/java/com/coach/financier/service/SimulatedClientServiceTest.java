package com.coach.financier.service;

import com.coach.financier.ai.AIService;
import com.coach.financier.ai.AIServiceFactory;
import com.coach.financier.model.AIModels;
import com.coach.financier.model.ConversationModels;
import com.coach.financier.model.FinancialSummary;
import com.coach.financier.model.PromptOptimizationModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CLIENT AUTO de la page coach : l'IA joue le client et propose la question suivante à partir de
 * l'historique RÉEL de la conversation. Vérifie le contexte transmis au modèle (mémoire complète, numéro de
 * question, trois chiffres du dossier, brief qui reprend la demande initiale du client) et les refus
 * (mode démo, conversation vide) : aucune proposition ne doit être fabriquée sans historique.
 */
class SimulatedClientServiceTest {

    private ConversationService conversations;
    private AIService ai;
    private SimulatedClientService service;

    @BeforeEach
    void setUp() {
        conversations = new ConversationService(0);
        FinancialAnalysisService financialAnalysis = mock(FinancialAnalysisService.class);
        when(financialAnalysis.analyze()).thenReturn(summary());
        ai = mock(AIService.class);
        AIServiceFactory factory = mock(AIServiceFactory.class);
        when(factory.get(any())).thenReturn(ai);
        service = new SimulatedClientService(conversations, financialAnalysis, factory);
    }

    @Test
    void theNextQuestionIsBuiltFromTheWholeConversation() {
        ConversationModels.Conversation conversation = conversations.getOrCreate("s-coach");
        conversation.addMessage("user", "Bonjour, je veux financer une voiture d'occasion à 8 700 €.");
        conversation.addMessage("assistant", "Bonjour, votre projet est soutenable.");
        conversation.addMessage("user", "Et quelle mensualité je devrais prévoir ?");
        conversation.addMessage("assistant", "Environ 250 € par mois sur 24 mois.");
        PromptOptimizationModels.ClientTurn scripted =
                new PromptOptimizationModels.ClientTurn("Et si j'allongeais la durée à 36 mois ?", false, "suite");
        when(ai.clientTurn(anyMap(), any())).thenReturn(scripted);

        PromptOptimizationModels.ClientTurn turn =
                service.nextQuestion("s-coach", AIModels.AIProvider.DEEPSEEK);

        assertEquals(scripted.question(), turn.question());
        assertFalse(turn.finished());
        Map<String, Object> context = capturedContext();
        assertEquals(3, context.get("turnNumber"),
                "deux questions du client ont déjà été posées : la proposition suivante est la 3e");
        assertEquals(4, context.get("depth"),
                "la profondeur se place APRÈS la question courante : le compteur ne clôt jamais la conversation");
        assertEquals(4, ((List<?>) context.get("previousExchanges")).size(),
                "TOUT l'échange est relu (le client ne se répète pas)");
        String brief = String.valueOf(context.get("clientBrief"));
        assertTrue(brief.contains("financer une voiture d'occasion à 8 700 €"),
                "le brief reprend la demande initiale du client, mot pour mot : " + brief);
        assertTrue(brief.contains("N'invente"),
                "le client ne doit inventer ni un autre projet ni un autre chiffre");
        Map<?, ?> figures = (Map<?, ?>) context.get("clientFigures");
        assertEquals(3, figures.size(), "exactement trois chiffres : compte courant, épargne, crédit en cours");
        assertTrue(figures.containsKey("creditEnCours"));
    }

    @Test
    void aDemoProviderOrAnEmptyConversationIsRefusedWithoutCallingTheAi() {
        var demo = assertThrows(IllegalArgumentException.class,
                () -> service.nextQuestion("s-coach", AIModels.AIProvider.MOCK));
        assertTrue(demo.getMessage().contains("Client auto"), demo.getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.nextQuestion("s-coach", null));

        conversations.getOrCreate("s-vide"); // session connue, mais aucun échange
        var empty = assertThrows(IllegalArgumentException.class,
                () -> service.nextQuestion("s-vide", AIModels.AIProvider.DEEPSEEK));
        assertTrue(empty.getMessage().contains("Aucun échange"), empty.getMessage());

        var unknown = assertThrows(IllegalArgumentException.class,
                () -> service.nextQuestion("s-inconnue", AIModels.AIProvider.DEEPSEEK));
        assertTrue(unknown.getMessage().contains("Aucun échange"),
                "session inconnue (backend redémarré) : on explique au lieu d'inventer un client");

        verify(ai, never()).clientTurn(anyMap(), any());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedContext() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(ai).clientTurn(captor.capture(), eq(AIModels.AIProvider.DEEPSEEK));
        return captor.getValue();
    }

    /** Dossier de démonstration : seuls trois chiffres sont transmis au client simulé. */
    private static FinancialSummary summary() {
        return new FinancialSummary(14, LocalDate.now().minusMonths(14), LocalDate.now(), 3200, 3200, 2500,
                1500, 1000, 12000, 700, 2400, 12000, 450, 0.2, 3.5, 0, 150, 900, "Loyer",
                320, Map.of("ALIMENTATION", 400d), 0.2, "3 derniers mois");
    }
}
