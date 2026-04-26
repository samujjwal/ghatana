/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * P3-18: PII scanner that detects potentially sensitive personal information using regex patterns.
 *
 * <p>Scans text data for common PII patterns including:
 * <ul>
 *   <li>Email addresses</li>
 *   <li>Phone numbers</li>
 *   <li>Credit card numbers</li>
 *   <li>Social Security Numbers (SSN)</li>
 *   <li>IP addresses</li>
 *   <li>Bank account numbers</li>
 * </ul>
 *
 * <p>T-14: Enforcement is controlled by {@link PiiEnforcementPolicy}:
 * <ul>
 *   <li>{@code LOG} — detect and log only (no payload change)</li>
 *   <li>{@code REDACT} — replace each matching value with a type-labelled placeholder</li>
 *   <li>{@code BLOCK} — reject the event entirely when PII is found</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Detect and optionally enforce PII policy in event payloads
 * @doc.layer product
 * @doc.pattern Scanner
 */
public final class PIIScanner {

    /**
     * T-14: Controls what happens when PII is detected in an event payload.
     *
     * <p>The active policy is resolved at startup from the {@code AEP_PII_ENFORCEMENT}
     * environment variable. Defaults to {@code LOG} so existing behaviour is unchanged
     * unless the operator explicitly opts in to stricter enforcement.
     */
    public enum PiiEnforcementPolicy {
        /** Detect and log PII; pass the payload unchanged. */
        LOG,
        /** Replace each PII-matched field value with a redaction placeholder. */
        REDACT,
        /** Reject the entire event when any PII is found. */
        BLOCK;

        /**
         * Resolves the active policy from the environment.
         * Falls back to {@link #LOG} when the variable is absent or unrecognised.
         */
        public static PiiEnforcementPolicy resolve() {
            String raw = System.getenv("AEP_PII_ENFORCEMENT");
            if (raw == null || raw.isBlank()) return LOG;
            try {
                return valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return LOG;
            }
        }
    }

    private static final Logger log = LoggerFactory.getLogger(PIIScanner.class);

    // Email pattern: standard email format
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", Pattern.CASE_INSENSITIVE);

    // Phone number pattern: various formats (US and international)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "(?:\\+?1[-.\\s]?)?\\(?[0-9]{3}\\)?[-.\\s]?[0-9]{3}[-.\\s]?[0-9]{4}", Pattern.CASE_INSENSITIVE);

    // Credit card pattern: Luhn-valid looking numbers (13-19 digits, spaces/dashes allowed)
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
        "\\b(?:\\d[ -]*?){13,19}\\b", Pattern.CASE_INSENSITIVE);

    // SSN pattern: XXX-XX-XXXX or XXXXXXXXX
    private static final Pattern SSN_PATTERN = Pattern.compile(
        "\\b\\d{3}[-.]?\\d{2}[-.]?\\d{4}\\b", Pattern.CASE_INSENSITIVE);

    // IP address pattern (IPv4)
    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile(
        "\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b", Pattern.CASE_INSENSITIVE);

    // Bank account pattern: common IBAN or US account formats
    private static final Pattern BANK_ACCOUNT_PATTERN = Pattern.compile(
        "\\b[A-Z]{2}[0-9]{2}[A-Z0-9]{4}[0-9]{7}(?:[A-Z0-9]{0,17})?\\b|" +  // IBAN
        "\\b\\d{8,17}\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Result of a PII scan containing detected PII items and their types.
     */
    public static class PIIResult {
        private final boolean hasPII;
        private final List<PIIItem> items;

        public PIIResult(boolean hasPII, List<PIIItem> items) {
            this.hasPII = hasPII;
            this.items = List.copyOf(items);
        }

        public boolean hasPII() {
            return hasPII;
        }

        public List<PIIItem> items() {
            return items;
        }
    }

    /**
     * Represents a single detected PII item.
     */
    public static class PIIItem {
        private final String type;
        private final String matchedText;
        private final int start;
        private final int end;

        public PIIItem(String type, String matchedText, int start, int end) {
            this.type = type;
            this.matchedText = matchedText;
            this.start = start;
            this.end = end;
        }

        public String type() {
            return type;
        }

        public String matchedText() {
            return matchedText;
        }

        public int start() {
            return start;
        }

        public int end() {
            return end;
        }
    }

    /**
     * Scans the given text for PII patterns.
     *
     * @param text the text to scan
     * @return PII scan result
     */
    public PIIResult scan(String text) {
        if (text == null || text.isBlank()) {
            return new PIIResult(false, List.of());
        }

        List<PIIItem> items = new ArrayList<>();

        scanPattern(text, EMAIL_PATTERN, "email", items);
        scanPattern(text, PHONE_PATTERN, "phone", items);
        scanPattern(text, CREDIT_CARD_PATTERN, "credit_card", items);
        scanPattern(text, SSN_PATTERN, "ssn", items);
        scanPattern(text, IP_ADDRESS_PATTERN, "ip_address", items);
        scanPattern(text, BANK_ACCOUNT_PATTERN, "bank_account", items);

        boolean hasPII = !items.isEmpty();
        if (hasPII) {
            log.warn("[PIIScanner] Detected {} potential PII items in text: {}",
                items.size(), summarizeItems(items));
        }

        return new PIIResult(hasPII, items);
    }

    /**
     * Scans a map of string values for PII patterns.
     *
     * @param data the map to scan
     * @return PII scan result
     */
    public PIIResult scanMap(Map<String, Object> data) {
        List<PIIItem> allItems = new ArrayList<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getValue() instanceof String text) {
                PIIResult result = scan(text);
                allItems.addAll(result.items());
            }
        }

        boolean hasPII = !allItems.isEmpty();
        if (hasPII) {
            log.warn("[PIIScanner] Detected {} potential PII items in map data", allItems.size());
        }

        return new PIIResult(hasPII, allItems);
    }

    /**
     * T-14: Returns a sanitised copy of the map where every string-typed value that
     * contains PII is replaced with a {@code [REDACTED:<type>]} placeholder.
     *
     * <p>The replacement uses the first detected PII type for each field. The original
     * map is never mutated.
     *
     * @param data the original payload map
     * @return a new map where PII-bearing string values are redacted
     */
    public Map<String, Object> redactMap(Map<String, Object> data) {
        Map<String, Object> redacted = new java.util.LinkedHashMap<>(data.size());
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getValue() instanceof String text) {
                PIIResult result = scan(text);
                if (result.hasPII()) {
                    String firstType = result.items().get(0).type();
                    redacted.put(entry.getKey(), "[REDACTED:" + firstType.toUpperCase() + "]");
                } else {
                    redacted.put(entry.getKey(), entry.getValue());
                }
            } else {
                redacted.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(redacted);
    }

    private void scanPattern(String text, Pattern pattern, String type, List<PIIItem> items) {
        java.util.regex.Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            items.add(new PIIItem(type, matcher.group(), matcher.start(), matcher.end()));
        }
    }

    private String summarizeItems(List<PIIItem> items) {
        return items.stream()
            .map(PIIItem::type)
            .distinct()
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }
}
