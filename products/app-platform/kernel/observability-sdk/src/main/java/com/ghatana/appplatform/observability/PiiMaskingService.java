/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;

/**
 * Masks PII values in log fields and structured data before emission (STORY-K06-017).
 *
 * <p>Applied before {@link StructuredLogContext} events are written to ensure no PII
 * appears in log indexes (Kibana/ELK). Uses patterns defined in {@link PiiDetectionRules}.
 *
 * <p>Masking strategy:
 * <ul>
 *   <li>Email: {@code j***@example.com} (preserve domain)</li>
 *   <li>Phone / NID / account: preserve first 2 and last 2 digits, mask middle</li>
 *   <li>Card number: preserve last 4 digits, mask rest</li>
 *   <li>IP address: mask last octet</li>
 *   <li>Generic: replace with {@code [REDACTED]}</li>
 * </ul>
 *
 * @doc.type  class
 * @doc.purpose Redacts PII from structured log fields and free-text strings (K06-017)
 * @doc.layer kernel
 * @doc.pattern Adapter
 */
public final class PiiMaskingService {

    private static final Logger log = LoggerFactory.getLogger(PiiMaskingService.class);

    private static final String REDACTED = "[REDACTED]";

    private final Counter maskedFieldsTotal;

    public PiiMaskingService(MeterRegistry registry) {
        this.maskedFieldsTotal = Counter.builder("observability.pii.masked.fields.total")
                .description("Number of PII field values masked before log emission")
                .register(Objects.requireNonNull(registry, "registry"));
    }

    /**
     * Scans all values in {@code fields} and returns a new map with PII masked.
     *
     * @param fields structured log context fields
     * @return a new map with sensitive values replaced
     */
    public Map<String, String> maskFields(Map<String, String> fields) {
        Objects.requireNonNull(fields, "fields");
        Map<String, String> result = new java.util.LinkedHashMap<>(fields.size());
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String key   = entry.getKey();
            String value = entry.getValue();
            if (PiiDetectionRules.ALWAYS_MASKED_FIELDS.contains(key)) {
                result.put(key, REDACTED);
                maskedFieldsTotal.increment();
            } else {
                result.put(key, maskValue(value));
            }
        }
        return result;
    }

    /**
     * Scans a free-text string and replaces all detected PII patterns with masked equivalents.
     *
     * @param text raw text (e.g. error message, description)
     * @return text with PII replaced
     */
    public String maskText(String text) {
        if (text == null || text.isBlank()) return text;

        String result = text;
        for (PiiDetectionRules.PiiPattern rule : PiiDetectionRules.PATTERNS) {
            Matcher m = rule.pattern().matcher(result);
            if (m.find()) {
                maskedFieldsTotal.increment();
                result = m.replaceAll(match -> applyMask(rule.type(), match.group()));
            }
        }
        return result;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private String maskValue(String value) {
        if (value == null || value.isBlank()) return value;

        for (PiiDetectionRules.PiiPattern rule : PiiDetectionRules.PATTERNS) {
            Matcher m = rule.pattern().matcher(value);
            if (m.matches()) {
                maskedFieldsTotal.increment();
                return applyMask(rule.type(), value);
            }
        }
        return value;
    }

    private static String applyMask(String type, String value) {
        return switch (type) {
            case "EMAIL" -> maskEmail(value);
            case "CARD_NUMBER" -> maskCard(value);
            case "IPV4"  -> maskIpv4(value);
            case "PHONE", "NEPAL_NID", "AADHAAR", "BANK_ACCOUNT", "PASSPORT" ->
                    maskMiddle(value);
            default -> REDACTED;
        };
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return REDACTED;
        String local  = email.substring(0, at);
        String domain = email.substring(at);
        return (local.isEmpty() ? "*" : String.valueOf(local.charAt(0)))
                + "***" + domain;
    }

    private static String maskCard(String card) {
        String digits = card.replaceAll("[^\\d]", "");
        if (digits.length() < 4) return REDACTED;
        return "****-****-****-" + digits.substring(digits.length() - 4);
    }

    private static String maskIpv4(String ip) {
        int lastDot = ip.lastIndexOf('.');
        if (lastDot < 0) return REDACTED;
        return ip.substring(0, lastDot) + ".***";
    }

    private static String maskMiddle(String value) {
        String digits = value.replaceAll("[^\\dA-Za-z]", "");
        if (digits.length() <= 4) return REDACTED;
        String prefix = digits.substring(0, 2);
        String suffix = digits.substring(digits.length() - 2);
        return prefix + "*".repeat(digits.length() - 4) + suffix;
    }
}
