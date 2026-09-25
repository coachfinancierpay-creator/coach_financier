package com.coach.financier.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Référentiel des codes client et des agences de gestion disponibles dans la démonstration. */
@Service
public class CustomerDirectoryService {
    private static final Pattern CUSTOMER_ID = Pattern.compile("[A-Z]{3}[0-9]{4}");
    private static final Pattern LEGACY_CUSTOMER_ID = Pattern.compile("DEMO([0-9]+)");

    private static final List<Agency> AGENCIES = List.of(
            new Agency("03101", "Agence de Paris"),
            new Agency("02360", "Agence de Strasbourg"),
            new Agency("01280", "Agence de Lille"));

    private static final Map<String, String> LEGACY_IDS = Map.ofEntries(
            Map.entry("DEMO001", "QJA6874"),
            Map.entry("DEMO002", "MXP1532"),
            Map.entry("DEMO003", "LTR4819"),
            Map.entry("DEMO229", "TPD8175"),
            Map.entry("DEMO326", "CYS6913"),
            Map.entry("DEMO355", "WQA2087"),
            Map.entry("DEMO394", "NBF5740"),
            Map.entry("DEMO442", "PXR1649"),
            Map.entry("DEMO560", "RZS8461"),
            Map.entry("DEMO736", "KDL7218"),
            Map.entry("DEMO770", "HBC2706"),
            Map.entry("DEMO829", "VNE3904"),
            Map.entry("DEMO851", "GTV9356"),
            Map.entry("DEMO945", "JKM4526"));

    /** Fiche de gestion : code client normalisé et agence responsable. */
    public record CustomerProfile(String customerId, Agency agency) {
    }

    /** Référence d'une agence; son code est un identifiant interne à cinq chiffres. */
    public record Agency(String code, String name) {
    }

    /** Génère un code client au format trois lettres majuscules et quatre chiffres. */
    public String nextCustomerId(Set<String> assignedCustomerIds) {
        String customerId;
        do {
            customerId = randomLetters(3) + String.format(Locale.ROOT, "%04d",
                    ThreadLocalRandom.current().nextInt(10_000));
        } while (!assignedCustomerIds.add(customerId));
        return customerId;
    }

    /** Résout une référence client, en convertissant les anciennes références {@code DEMO...} si nécessaire. */
    public CustomerProfile profile(String customerId) {
        String normalizedCustomerId = normalizeCustomerId(customerId);
        if (normalizedCustomerId == null) {
            return new CustomerProfile(null, null);
        }
        Agency agency = AGENCIES.get(Math.floorMod(normalizedCustomerId.hashCode(), AGENCIES.size()));
        return new CustomerProfile(normalizedCustomerId, agency);
    }

    /** Rend une ancienne référence compatible avec le nouveau format sans perdre son rattachement client. */
    public String normalizeCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return null;
        }
        String candidate = customerId.trim().toUpperCase(Locale.ROOT);
        if (CUSTOMER_ID.matcher(candidate).matches()) {
            return candidate;
        }
        String mapped = LEGACY_IDS.get(candidate);
        if (mapped != null) {
            return mapped;
        }
        Matcher legacy = LEGACY_CUSTOMER_ID.matcher(candidate);
        if (legacy.matches()) {
            int number = Integer.parseInt(legacy.group(1));
            return legacyPrefix(number) + String.format(Locale.ROOT, "%04d", Math.floorMod(number, 10_000));
        }
        int stableNumber = candidate.hashCode();
        return legacyPrefix(stableNumber) + String.format(Locale.ROOT, "%04d", Math.floorMod(stableNumber, 10_000));
    }

    private static String randomLetters(int length) {
        StringBuilder letters = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            letters.append((char) ('A' + ThreadLocalRandom.current().nextInt(26)));
        }
        return letters.toString();
    }

    private static String legacyPrefix(int number) {
        char first = (char) ('A' + Math.floorMod(number / (26 * 26), 26));
        char second = (char) ('A' + Math.floorMod(number / 26, 26));
        char third = (char) ('A' + Math.floorMod(number, 26));
        return new String(new char[] {first, second, third});
    }
}