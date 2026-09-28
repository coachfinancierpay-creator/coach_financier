package com.coach.financier.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AzureSpeechServiceTest {

    @Test
    void prepareSpokenTextRemovesEmojiAndUsesFrenchMythosPronunciation() {
        String spokenText = AzureSpeechService.prepareSpokenText(
                "Mythos 🏆 protège 1 250 € 👨‍👩‍👧‍👦. mythos et Mythoscope restent distincts.");

        assertEquals("Mitoss protège 1 250 €. Mitoss et Mythoscope restent distincts.", spokenText);
    }
}