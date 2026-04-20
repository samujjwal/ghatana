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
 * <p>This scanner can be used before event persistence to detect and log potential PII leaks.
 * For production use, consider integrating with ML-based detection for higher accuracy.
 *
 * @doc.type class
 * @doc.purpose Detect PII in text data using regex patterns
 * @doc.layer product
 * @doc.pattern Scanner
 */
public final class PIIScanner {

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
