package com.coach.financier.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/** Modèles de l'API de synthèse vocale. */
public final class TtsModels {
    private TtsModels() {}

    public static final String DEFAULT_VOICE = "fr-FR-DeniseNeural";
    public static final Map<String, String> FRENCH_VOICES = Map.of(
            "fr-FR-DeniseNeural", "Denise · voix féminine",
            "fr-FR-HenriNeural", "Henri · voix masculine",
            "fr-FR-EloiseNeural", "Eloise · voix féminine"
    );

    /** Demande de synthèse d'un texte à la vitesse demandée. */
    public record SpeechRequest(
            @NotBlank(message = "Le texte est obligatoire") String text,
            @DecimalMin("0.5") @DecimalMax("2.0") double rate,
            @NotBlank(message = "La voix est obligatoire") String voice
    ) {
        public SpeechRequest(String text, double rate) {
            this(text, rate, DEFAULT_VOICE);
        }

        public boolean hasSupportedVoice() {
            return FRENCH_VOICES.containsKey(voice);
        }
    }
}