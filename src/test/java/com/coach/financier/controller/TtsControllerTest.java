package com.coach.financier.controller;

import com.coach.financier.model.TtsModels;
import com.coach.financier.service.AzureSpeechService;
import com.coach.financier.service.TextToSpeechUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TtsControllerTest {

    @Test
    void synthesisReturnsNonCacheableMp3Audio() {
        AzureSpeechService service = mock(AzureSpeechService.class);
        byte[] expectedAudio = {1, 2, 3};
        when(service.synthesize(eq("Bonjour"), eq(1d), eq(TtsModels.DEFAULT_VOICE))).thenReturn(expectedAudio);
        TtsController controller = new TtsController(service);

        ResponseEntity<?> response = controller.synthesize(new TtsModels.SpeechRequest("Bonjour", 1d));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("audio/mpeg", response.getHeaders().getContentType().toString());
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertArrayEquals(expectedAudio, assertInstanceOf(byte[].class, response.getBody()));
    }

    @Test
    void unavailableAzureReturnsAReadableServiceUnavailableResponse() {
        AzureSpeechService service = mock(AzureSpeechService.class);
        when(service.synthesize(eq("Bonjour"), eq(1d), eq(TtsModels.DEFAULT_VOICE)))
                .thenThrow(new TextToSpeechUnavailableException("La synthèse vocale neuronale n'est pas activée."));
        TtsController controller = new TtsController(service);

        ResponseEntity<?> response = controller.synthesize(new TtsModels.SpeechRequest("Bonjour", 1d));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals("TTS_INDISPONIBLE", body.get("error"));
        assertEquals("La synthèse vocale neuronale n'est pas activée.", body.get("message"));
    }
}