package com.coach.financier.service;

import com.coach.financier.model.ConversationModels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationServiceTest {

    @Test
    void assignsStableDemoCustomerIdPerConversation() {
        ConversationService service = new ConversationService(0);

        ConversationModels.Conversation first = service.getOrCreate("session-1");
        ConversationModels.Conversation same = service.getOrCreate("session-1");
        ConversationModels.Conversation second = service.getOrCreate("session-2");

        assertTrue(first.customerId().matches("[A-Z]{3}[0-9]{4}"));
        assertEquals(first.customerId(), same.customerId());
        assertNotEquals(first.customerId(), second.customerId());
    }
}

