package com.coach.financier.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Persists the AI-generated financial synthesis (synthese_financier.json) so that
 * subsequent chat calls can send this compact summary to the model instead of the
 * full raw banking JSON (thousands of transactions).
 */
@Component
public class FinancialSynthesisStore {
    private static final Logger log = LoggerFactory.getLogger(FinancialSynthesisStore.class);

    private final ObjectMapper objectMapper;
    private final Path filePath;

    public FinancialSynthesisStore(ObjectMapper objectMapper,
                                   @Value("${app.ai.synthesis-file:./data/synthese_financier.json}") String synthesisFile) {
        this.objectMapper = objectMapper;
        this.filePath = Path.of(synthesisFile).toAbsolutePath().normalize();
    }

    public Optional<JsonNode> load() {
        return loadForDataset("jdd1");
    }

    public Optional<JsonNode> loadForDataset(String dataset) {
        Path selectedPath = "jdd2".equalsIgnoreCase(dataset)
                ? filePath.getParent().resolve("jdd2").resolve("synthese_financier.json")
                : filePath;
        JsonNode node = readFile(selectedPath);
        if (node != null) {
            log.info("Synthèse financière chargée depuis {}", selectedPath);
            return Optional.of(node);
        }
        // Repli : une synthèse placée manuellement dans resources/data est aussi acceptée.
        try (InputStream in = new ClassPathResource("data/synthese_financier.json").getInputStream()) {
            JsonNode classpath = objectMapper.readTree(in);
            if (classpath != null && classpath.isObject()) {
                log.info("Synthèse financière chargée depuis le classpath data/synthese_financier.json");
                return Optional.of(classpath);
            }
        } catch (Exception ignored) {
            // Aucune synthèse disponible ni sur disque ni sur le classpath.
        }
        return Optional.empty();
    }

    /** Charge la synthèse et remplace uniquement l'identifiant client pour une session de démo. */
    public Optional<JsonNode> loadForCustomer(String customerId) {
        return loadForDatasetForCustomer("jdd1", customerId);
    }

    public Optional<JsonNode> loadForDatasetForCustomer(String dataset, String customerId) {
        return loadForDataset(dataset).map(node -> {
            JsonNode copy = node.deepCopy();
            if (copy instanceof ObjectNode root && customerId != null && !customerId.isBlank()) {
                JsonNode customer = root.path("customer");
                if (customer instanceof ObjectNode customerObject) {
                    customerObject.put("customerId", customerId);
                }
            }
            return copy;
        });
    }

    private JsonNode readFile(Path path) {
        try {
            if (Files.exists(path)) {
                JsonNode node = objectMapper.readTree(path.toFile());
                if (node != null && node.isObject()) {
                    return node;
                }
            }
        } catch (Exception e) {
            log.warn("Lecture impossible de {} : {}", path, e.getMessage());
        }
        return null;
    }

    public void save(JsonNode synthesis) {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), synthesis);
            log.info("Synthèse financière enregistrée dans {}", filePath);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible d'écrire la synthèse financière dans " + filePath, e);
        }
    }

    public Path path() {
        return filePath;
    }
}
