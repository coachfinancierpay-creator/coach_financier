package com.coach.financier.service;

import com.coach.financier.model.ConversationModels;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

@Service
public class ConversationService {
    private final ConcurrentHashMap<String, ConversationModels.Conversation> conversations = new ConcurrentHashMap<>();
    private final Set<String> assignedCustomerIds = ConcurrentHashMap.newKeySet();

    /**
     * Nombre de messages d'historique transmis au coach ({@code app.chat.history-limit}) :
     * {@code 0} = TOUT l'historique (défaut), une valeur &gt; 0 borne volontairement le contexte.
     */
    private final int historyLimit;

    public ConversationService(@Value("${app.chat.history-limit:0}") int historyLimit) {
        this.historyLimit = historyLimit;
    }

    public ConversationModels.Conversation getOrCreate(String sessionId) {
        ConversationModels.Conversation conversation =
                conversations.computeIfAbsent(sessionId, this::newConversation);
        conversation.setHistoryLimit(historyLimit);
        return conversation;
    }

    /** Conversation existante pour une session, ou {@code null} si aucune. */
    public ConversationModels.Conversation find(String sessionId) {
        return conversations.get(sessionId);
    }

    /**
     * Installe une conversation VIERGE pour une session, en remplaçant celle qui existait.
     * <p>
     * Utilisé par l'ATELIER : avant de clore un scénario, il reconstruit une session de chat à partir de son fil
     * de conversation (même pipeline que la page coach : dossier de suivi, mail conseiller, score, annuaire du
     * centre d'appels). Le remplacement garantit qu'une clôture rejouée ne duplique pas les messages.
     */
    public ConversationModels.Conversation reset(String sessionId) {
        ConversationModels.Conversation conversation = newConversation(sessionId);
        conversation.setHistoryLimit(historyLimit);
        conversations.put(sessionId, conversation);
        return conversation;
    }

    private ConversationModels.Conversation newConversation(String sessionId) {
        ConversationModels.Conversation conversation = new ConversationModels.Conversation(sessionId);
        String customerId;
        do {
            customerId = "DEMO" + ThreadLocalRandom.current().nextInt(100, 1000);
        } while (!assignedCustomerIds.add(customerId));
        conversation.setCustomerId(customerId);
        return conversation;
    }
}
