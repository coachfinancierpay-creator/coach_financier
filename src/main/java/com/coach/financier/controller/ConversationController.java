package com.coach.financier.controller;

import com.coach.financier.model.AIModels;
import com.coach.financier.model.ConversationModels;
import com.coach.financier.model.PromptOptimizationModels;
import com.coach.financier.model.QualityModels;
import com.coach.financier.model.SuiviModels;
import com.coach.financier.service.ConversationClosureService;
import com.coach.financier.service.ConversationService;
import com.coach.financier.service.QualityFeedbackService;
import com.coach.financier.service.SimulatedClientService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API des conversations : relecture d'un historique, CLÔTURE (dossier de suivi conseiller) et
 * CLIENT AUTO (l'IA propose la question suivante du client, à relire avant envoi).
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationService conversationService;
    private final ConversationClosureService closureService;
    private final QualityFeedbackService qualityFeedbackService;
    private final SimulatedClientService simulatedClientService;

    public ConversationController(ConversationService conversationService,
                                  ConversationClosureService closureService,
                                  QualityFeedbackService qualityFeedbackService,
                                  SimulatedClientService simulatedClientService) {
        this.conversationService = conversationService;
        this.closureService = closureService;
        this.qualityFeedbackService = qualityFeedbackService;
        this.simulatedClientService = simulatedClientService;
    }

    /** Historique complet d'une session (messages client/coach), pour la page Logs. */
    @GetMapping("/{sessionId}")
    public Map<String, Object> history(@PathVariable String sessionId) {
        ConversationModels.Conversation conversation = conversationService.find(sessionId);
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation inconnue : " + sessionId);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", sessionId);
        out.put("summary", conversation.summary());
        out.put("messages", conversation.transcript());
        return out;
    }

    /** Retourne une conversation juste avant la question client sélectionnée par l'utilisateur. */
    @PostMapping("/{sessionId}/rewind")
    public Map<String, Object> rewind(@PathVariable String sessionId, @RequestBody RewindRequest request) {
        ConversationModels.Conversation conversation = conversationService.find(sessionId);
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation inconnue : " + sessionId);
        }
        if (request == null || request.messageCount() == null) {
            throw new IllegalArgumentException("Le nombre de messages est obligatoire");
        }
        conversation.rewind(request.messageCount());
        return Map.of("sessionId", sessionId, "messageCount", conversation.transcript().size());
    }

    public record RewindRequest(Integer messageCount) {
    }

    /**
     * Clôture d'une conversation : génère le dossier de suivi (email conseiller + brouillon
     * client en pièce jointe) et envoie UNIQUEMENT l'email au conseiller.
     * <p>
     * Corps optionnel : {@code {advisorEmail, advisorName, attachmentFormat, send, provider}}.
     * {@code send=false} = dry-run (prépare sans envoyer).
     */
    @PostMapping("/{sessionId}/close")
    public SuiviModels.CloseConversationResponse close(
            @PathVariable String sessionId,
            @RequestBody(required = false) SuiviModels.CloseConversationRequest request) {
        return closureService.close(sessionId, request);
    }

    /**
     * FEEDBACK CLIENT de fin de conversation (pop-in 1 à 5 étoiles, commentaire facultatif, motifs
     * conditionnels). Toujours OPTIONNEL : un corps vide (« Passer ») ou une erreur de stockage ne
     * bloque jamais la clôture de la conversation (§43). Réponse idempotente par session (§44).
     */
    @PostMapping("/{sessionId}/feedback")
    public QualityFeedbackService.FeedbackResponse feedback(
            @PathVariable String sessionId,
            @RequestBody(required = false) QualityModels.FeedbackRequest request) {
        return qualityFeedbackService.submit(sessionId, request);
    }

    /** Corps d'une demande au CLIENT AUTO : le fournisseur IA à utiliser (doit être réel). */
    public record ClientQuestionRequest(AIModels.AIProvider provider) {
    }

    /**
     * CLIENT AUTO (bouton du bandeau de la page coach) : l'IA joue le client et propose la question
     * SUIVANTE à partir de l'historique de la conversation. Rien n'est envoyé au Coach ici : la question est
     * renvoyée à l'IHM, qui la place dans le champ de saisie — l'humain décide de l'envoyer ou non.
     */
    @PostMapping("/{sessionId}/client-question")
    public PromptOptimizationModels.ClientTurn clientQuestion(
            @PathVariable String sessionId,
            @RequestBody(required = false) ClientQuestionRequest request) {
        return simulatedClientService.nextQuestion(sessionId, request == null ? null : request.provider());
    }

    /**
     * Demande invalide (aucun échange à lire, fournisseur de démonstration, corps illisible) → <b>400</b>
     * avec un message exploitable par l'IHM (même contrat que l'annuaire du centre d'appels).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(IllegalArgumentException error) {
        return Map.of("error", "BAD_REQUEST",
                "message", error.getMessage() == null ? "Requête invalide" : error.getMessage());
    }
}
