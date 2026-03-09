package com.ghatana.products.collection.domain.policy;

import java.util.Objects;

/**
 * Represents detected personally identifiable information (PII) in content.
 *
 * <p><b>Purpose</b><br>
 * Captures details about PII found during content scanning, including
 * the type of PII, matched text, position, and confidence score.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PIIDetection email = new PIIDetection(
 *     PIIType.EMAIL,
 *     "user@example.com",
 *     50,   // position in text
 *     0.99  // confidence
 * );
 * 
 * // Redact PII
 * String redacted = email.redact();  // "***@***.***"
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record - thread-safe by design.
 *
 * @param piiType type of PII detected
 * @param matchedText exact PII text found
 * @param position character position in original text (0-based)
 * @param confidence confidence score (0-1, higher = more certain)
 *
 * @doc.type record
 * @doc.purpose PII detection result
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record PIIDetection(
        PIIType piiType,
        String matchedText,
        int position,
        double confidence
) {
    public PIIDetection {
        Objects.requireNonNull(piiType, "piiType cannot be null");
        Objects.requireNonNull(matchedText, "matchedText cannot be null");
        if (position < 0) {
            throw new IllegalArgumentException("position cannot be negative");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
    }

    /**
     * Redacts the PII with asterisks.
     *
     * <p>Strategy:
     * - EMAIL: user@***.***
     * - PHONE: ***-***-1234 (last 4 digits visible)
     * - SSN: ***-**-1234 (last 4 digits visible)
     * - CREDIT_CARD: **** **** **** 1234 (last 4 digits visible)
     * - IP_ADDRESS: ***.***.***.***
     * - POSTAL_CODE: *** (fully redacted)
     *
     * @return redacted version of matched text
     */
    public String redact() {
        return switch (piiType) {
            case EMAIL -> {
                int atIndex = matchedText.indexOf('@');
                if (atIndex > 0) {
                    yield matchedText.charAt(0) + "***@***." + getExtension(matchedText);
                }
                yield "***@***.***";
            }
            case PHONE -> {
                if (matchedText.length() >= 4) {
                    yield "***-***-" + matchedText.substring(matchedText.length() - 4);
                }
                yield "***-***-****";
            }
            case SSN -> {
                if (matchedText.length() >= 4) {
                    yield "***-**-" + matchedText.substring(matchedText.length() - 4);
                }
                yield "***-**-****";
            }
            case CREDIT_CARD -> {
                if (matchedText.length() >= 4) {
                    yield "**** **** **** " + matchedText.substring(matchedText.length() - 4);
                }
                yield "**** **** **** ****";
            }
            case IP_ADDRESS -> "***.***.***." + (matchedText.contains(".") ?
                    matchedText.substring(matchedText.lastIndexOf('.') + 1) : "***");
            case POSTAL_CODE -> "***";
        };
    }

    private String getExtension(String email) {
        int lastDot = email.lastIndexOf('.');
        if (lastDot > 0 && lastDot < email.length() - 1) {
            return email.substring(lastDot + 1);
        }
        return "***";
    }
}

/**
 * Types of personally identifiable information.
 *
 * @doc.type enum
 * @doc.purpose PII type enumeration
 * @doc.layer domain
 * @doc.pattern Value Object
 */
enum PIIType {
    /** Email addresses */
    EMAIL,

    /** Phone numbers (various formats) */
    PHONE,

    /** Social Security Numbers */
    SSN,

    /** Credit card numbers */
    CREDIT_CARD,

    /** IPv4/IPv6 addresses */
    IP_ADDRESS,

    /** ZIP codes, postal codes */
    POSTAL_CODE
}
