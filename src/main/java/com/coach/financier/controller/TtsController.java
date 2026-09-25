package com.coach.financier.controller;

import com.coach.financier.model.TtsModels;
import com.coach.financier.service.AzureSpeechService;
import com.coach.financier.service.TextToSpeechUnavailableException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Point d'accès binaire pour la lecture volontaire d'une réponse du Coach. */
@RestController
@RequestMapping("/api/tts")
public class TtsController {
    private static final Logger log = LoggerFactory.getLogger(TtsController.class);

    private final AzureSpeechService azureSpeechService;

    public TtsController(AzureSpeechService azureSpeechService) {
        this.azureSpeechService = azureSpeechService;
    }
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Map.of(
                "provider", "AZURE",
                "status", azureSpeechService.startupStatus()));
    }

    @PostMapping("/synthesize")
    public ResponseEntity<?> synthesize(@Valid @RequestBody TtsModels.SpeechRequest request) {
        try {
            if (!request.hasSupportedVoice()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", HttpStatus.BAD_REQUEST.value(),
                        "error", "VOIX_TTS_INVALIDE",
                        "message", "La voix française demandée n'est pas supportée."));
            }
            byte[] audio = azureSpeechService.synthesize(request.text(), request.rate(), request.voice());
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("audio/mpeg"))
                    .contentLength(audio.length)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(audio);
        } catch (TextToSpeechUnavailableException exception) {
            log.debug("[TTS] lecture neuronale indisponible: {}", exception.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                            "error", "TTS_INDISPONIBLE",
                            "message", exception.getMessage()));
        }
    }
    @PostMapping("/token")
    public ResponseEntity<?> token() {
        try {
            return ResponseEntity.ok(Map.of(
                    "token", azureSpeechService.issueToken(),
                    "region", azureSpeechService.region()));
        } catch (TextToSpeechUnavailableException exception) {
            log.debug("[STT] reconnaissance Azure indisponible: {}", exception.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                            "error", "STT_INDISPONIBLE",
                            "message", exception.getMessage()));
        }
    }
}