package com.coach.financier.service;

import com.coach.financier.config.TtsProperties;
import com.coach.financier.model.TtsModels;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.net.http.HttpClient;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Appel serveur-à-serveur à Azure AI Speech; la clé ne quitte jamais le backend. */
@Service
public class AzureSpeechService {
    private static final Logger log = LoggerFactory.getLogger(AzureSpeechService.class);
    private static final String OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3";
    private static final MediaType SSML_MEDIA_TYPE = MediaType.parseMediaType("application/ssml+xml;charset=UTF-8");
    private static final Pattern MYTHOS_WORD = Pattern.compile("(?i)(?<![\\p{L}\\p{N}])mythos(?![\\p{L}\\p{N}])");

    private final TtsProperties properties;
    private final RestClient client;

    public AzureSpeechService(TtsProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds()));
        String endpoint = properties.endpoint().isBlank() ? "https://localhost" : properties.endpoint();
        this.client = RestClient.builder().baseUrl(endpoint).requestFactory(requestFactory).build();
    }
    @PostConstruct
    void logStartupStatus() {
        if (!properties.isEnabled()) {
            log.info("[TTS] Azure AI Speech indisponible au démarrage: app.tts.enabled=false");
        } else if (properties.endpoint().isBlank()) {
            log.warn("[TTS] Azure AI Speech indisponible au démarrage: endpoint Azure absent");
        } else if (properties.apiKey().isBlank()) {
            log.warn("[TTS] Azure AI Speech indisponible au démarrage: clé AZURE_SPEECH_KEY absente");
        } else {
            log.info("[TTS] Azure AI Speech prêt: région={}, voix françaises disponibles={}",
                    properties.azureRegion(), TtsModels.FRENCH_VOICES.keySet());
        }
    }

    public String startupStatus() {
        if (!properties.isEnabled()) return "désactivé (app.tts.enabled=false)";
        if (properties.endpoint().isBlank()) return "indisponible (endpoint Azure absent)";
        if (properties.apiKey().isBlank()) return "indisponible (AZURE_SPEECH_KEY absente)";
        return "configuré (région " + properties.azureRegion() + ", voix françaises disponibles)";
    }

    public String region() {
        return properties.azureRegion();
    }

    public byte[] synthesize(String text, double rate, String voice) {
        requireConfiguration();
        if (!TtsModels.FRENCH_VOICES.containsKey(voice)) {
            throw new IllegalArgumentException("Voix française non supportée.");
        }
        String ssml = toSsml(text, rate, voice);
        try {
            byte[] audio = client.post()
                    .uri("/cognitiveservices/v1")
                    .contentType(SSML_MEDIA_TYPE)
                    .accept(MediaType.valueOf("audio/mpeg"))
                    .header("Ocp-Apim-Subscription-Key", properties.apiKey())
                    .header("X-Microsoft-OutputFormat", OUTPUT_FORMAT)
                    .header("User-Agent", "coach-financier")
                    .body(ssml.getBytes(StandardCharsets.UTF_8))
                    .retrieve()
                    .body(byte[].class);
            if (audio == null || audio.length == 0) {
                throw new TextToSpeechUnavailableException("Azure AI Speech n'a produit aucun audio.");
            }
            return audio;
        } catch (RestClientResponseException exception) {
            log.warn("[TTS] Azure AI Speech a répondu HTTP {}", exception.getStatusCode().value());
            throw new TextToSpeechUnavailableException(
                    "La synthèse vocale Azure est indisponible (HTTP " + exception.getStatusCode().value() + ").",
                    exception);
        } catch (RestClientException exception) {
            log.warn("[TTS] Azure AI Speech est injoignable: {}", exception.getMessage());
            throw new TextToSpeechUnavailableException("La synthèse vocale Azure est momentanément indisponible.", exception);
        }
    }

    public byte[] synthesize(String text, double rate) {
        return synthesize(text, rate, TtsModels.DEFAULT_VOICE);
    }

    public String issueToken() {
        requireConfiguration();
        if (properties.azureRegion().isBlank()) {
            throw new TextToSpeechUnavailableException("La région Azure Speech n'est pas configurée.");
        }
        try {
            String token = client.post()
                    .uri("https://" + properties.azureRegion() + ".api.cognitive.microsoft.com/sts/v1.0/issueToken")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("Ocp-Apim-Subscription-Key", properties.apiKey())
                    .header("User-Agent", "coach-financier")
                    .body("")
                    .retrieve()
                    .body(String.class);
            if (token == null || token.isBlank()) {
                throw new TextToSpeechUnavailableException("Azure AI Speech n'a fourni aucun jeton.");
            }
            return token;
        } catch (RestClientResponseException exception) {
            log.warn("[STT] Azure AI Speech a répondu HTTP {} lors de l'émission du jeton", exception.getStatusCode().value());
            throw new TextToSpeechUnavailableException(
                    "La reconnaissance vocale Azure est indisponible (HTTP " + exception.getStatusCode().value() + ").",
                    exception);
        } catch (RestClientException exception) {
            log.warn("[STT] Azure AI Speech est injoignable lors de l'émission du jeton: {}", exception.getMessage());
            throw new TextToSpeechUnavailableException("La reconnaissance vocale Azure est momentanément indisponible.", exception);
        }
    }

    private void requireConfiguration() {
        if (!properties.isEnabled()) {
            throw new TextToSpeechUnavailableException("La synthèse vocale neuronale n'est pas activée.");
        }
        if (!properties.isConfigured()) {
            throw new TextToSpeechUnavailableException(
                    "La synthèse vocale neuronale n'est pas configurée sur le serveur.");
        }
    }

    private String toSsml(String text, double requestedRate, String voice) {
        double rate = Math.max(0.5, Math.min(2.0, requestedRate));
        StringWriter output = new StringWriter();
        try {
            XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeStartElement("speak");
            writer.writeDefaultNamespace("http://www.w3.org/2001/10/synthesis");
            writer.writeAttribute("version", "1.0");
            writer.writeAttribute("xml", "http://www.w3.org/XML/1998/namespace", "lang", "fr-FR");
            writer.writeStartElement("voice");
            writer.writeAttribute("name", voice);
            writer.writeStartElement("prosody");
            writer.writeAttribute("rate", String.format(Locale.ROOT, "%+.0f%%", (rate - 1d) * 100d));
            String spokenText = prepareSpokenText(text);
            writer.writeCharacters(spokenText);
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.close();
            return output.toString();
        } catch (XMLStreamException exception) {
            throw new TextToSpeechUnavailableException("La synthèse vocale n'a pas pu préparer la demande.", exception);
        }
    }

    static String prepareSpokenText(String text) {
        if (text == null || text.isBlank()) return "";

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
        StringBuilder withoutEmoji = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(codePoint -> !isEmojiCodePoint(codePoint))
                .forEach(withoutEmoji::appendCodePoint);

        return MYTHOS_WORD.matcher(withoutEmoji.toString())
                .replaceAll("Mitoss")
                .replaceAll("[ \\t]{2,}", " ")
            .replaceAll("\\s+([.,])", "$1")
                .trim();
    }

    private static boolean isEmojiCodePoint(int codePoint) {
        return (codePoint >= 0x1F000 && codePoint <= 0x1FAFF)
                || (codePoint >= 0x2600 && codePoint <= 0x27BF)
                || (codePoint >= 0x2300 && codePoint <= 0x23FF)
                || codePoint == 0x200D
                || codePoint == 0x20E3
                || (codePoint >= 0xFE00 && codePoint <= 0xFE0F);
    }
}