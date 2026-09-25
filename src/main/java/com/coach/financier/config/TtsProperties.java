package com.coach.financier.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Configuration Azure AI Speech, lue uniquement côté serveur. */
@Component
public class TtsProperties {

    private static final String AZURE_SPEECH_REGION = "francecentral";
    private static final String AZURE_SPEECH_ENDPOINT = "https://francecentral.tts.speech.microsoft.com";
    private final boolean enabled;
    private final String azureRegion;
    private final String endpoint;
    private final String apiKey;
    private final int readTimeoutSeconds;

    public TtsProperties(
            @Value("${app.tts.enabled:true}") boolean enabled,
            @Value("${app.tts.api-key:}") String apiKey,
            @Value("${app.tts.read-timeout-seconds:30}") int readTimeoutSeconds) {
        this.enabled = enabled;
        this.azureRegion = AZURE_SPEECH_REGION;
        this.endpoint = AZURE_SPEECH_ENDPOINT;
        this.apiKey = normalized(apiKey);
        this.readTimeoutSeconds = Math.max(5, Math.min(60, readTimeoutSeconds));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String azureRegion() {
        return azureRegion;
    }

    public String endpoint() {
        return endpoint;
    }

    public String apiKey() {
        return apiKey;
    }

    public int readTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public boolean isConfigured() {
        return enabled && !endpoint.isBlank() && !apiKey.isBlank();
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}