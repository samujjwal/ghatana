/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.observability;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Defines the pattern rules for detecting Personal Identifiable Information (PII) in
 * log event fields and structured data (STORY-K06-016).
 *
 * <p>PII categories covered:
 * <ul>
 *   <li>National ID numbers (Nepal NID, Aadhaar-style 12-digit)</li>
 *   <li>Email addresses</li>
 *   <li>Phone numbers (10–15 digits, with country code)</li>
 *   <li>Credit / debit card numbers (Luhn-compatible patterns)</li>
 *   <li>Bank account numbers (simple numeric patterns > 8 digits)</li>
 *   <li>IP addresses (IPv4 and IPv6)</li>
 *   <li>Passport numbers (general alphanumeric)</li>
 * </ul>
 *
 * @doc.type  class
 * @doc.purpose Regex-based PII detection rules used by PiiMaskingService (K06-016)
 * @doc.layer kernel
 * @doc.pattern Config
 */
public final class PiiDetectionRules {

    /** Key field names whose values are always treated as PII regardless of content. */
    public static final Set<String> ALWAYS_MASKED_FIELDS = Set.of(
            "password", "secret", "apiKey", "api_key",
            "token", "refreshToken", "ssn", "nin",
            "nationalId", "national_id", "cardNumber", "card_number",
            "cvv", "pin", "privatekKey", "privateKey"
    );

    /** Patterns used by {@link PiiMaskingService} to detect PII in string values. */
    public static final List<PiiPattern> PATTERNS = List.of(
            new PiiPattern("EMAIL",
                    Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}")),

            new PiiPattern("PHONE",
                    Pattern.compile("\\b(?:\\+?\\d{1,3}[\\s-])?\\(?\\d{3}\\)?[\\s.\\-]?\\d{3}[\\s.\\-]?\\d{4,6}\\b")),

            new PiiPattern("CARD_NUMBER",
                    // Matches 13–19 digit sequences with optional spaces/dashes (e.g. Visa, MasterCard)
                    Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b")),

            new PiiPattern("NEPAL_NID",
                    // Nepal NID: 11-digit numeric (e.g. 73174502)
                    Pattern.compile("\\b\\d{8,11}\\b")),

            new PiiPattern("AADHAAR",
                    // Aadhaar-style 12-digit, optionally space-separated
                    Pattern.compile("\\b\\d{4}\\s?\\d{4}\\s?\\d{4}\\b")),

            new PiiPattern("BANK_ACCOUNT",
                    Pattern.compile("\\b\\d{9,18}\\b")),

            new PiiPattern("IPV4",
                    Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")),

            new PiiPattern("IPV6",
                    Pattern.compile("\\b(?:[A-Fa-f0-9]{1,4}:){7}[A-Fa-f0-9]{1,4}\\b")),

            new PiiPattern("PASSPORT",
                    // General passport: 1-2 letters followed by 6-7 digits
                    Pattern.compile("\\b[A-Z]{1,2}\\d{6,7}\\b"))
    );

    private PiiDetectionRules() {}

    /**
     * Associates a PII type name with its detection regex.
     *
     * @param type    human-readable PII category name
     * @param pattern regex pattern to match PII occurrences
     */
    public record PiiPattern(String type, Pattern pattern) {}
}
