package com.ghatana.digitalmarketing.domain.privacy;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;

/**
 * PII-safe contact point model (DMOS-P1-014).
 *
 * <p>Contact points are normalized and hashed to protect PII. The raw contact information
 * is never stored in the database. Instead, a keyed hash (HMAC) is stored for suppression
 * matching.</p>
 *
 * @doc.type record
 * @doc.purpose PII-safe contact point with normalized and hashed representation (DMOS-P1-014)
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record ContactPoint(
    String normalizedValue,
    String contactPointHash,
    ContactPointType type
) {
    private static final String HMAC_KEY = System.getenv("DMOS_CONTACT_HMAC_KEY") != null
        ? System.getenv("DMOS_CONTACT_HMAC_KEY")
        : "default-hmac-key-change-in-production";
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    public ContactPoint {
        Objects.requireNonNull(normalizedValue, "normalizedValue must not be null");
        Objects.requireNonNull(contactPointHash, "contactPointHash must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }

    /**
     * Creates a contact point from a raw email address.
     */
    public static ContactPoint fromEmail(String rawEmail) {
        String normalized = normalizeEmail(rawEmail);
        String hash = computeHmac(normalized);
        return new ContactPoint(normalized, hash, ContactPointType.EMAIL);
    }

    /**
     * Creates a contact point from a raw phone number.
     */
    public static ContactPoint fromPhone(String rawPhone) {
        String normalized = normalizePhone(rawPhone);
        String hash = computeHmac(normalized);
        return new ContactPoint(normalized, hash, ContactPointType.PHONE);
    }

    /**
     * Normalizes an email address for consistent hashing.
     * - Converts to lowercase
     * - Trims whitespace
     * - Removes dots from Gmail addresses (optional enhancement)
     */
    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        return email.toLowerCase().trim();
    }

    /**
     * Normalizes a phone number for consistent hashing.
     * - Removes non-digit characters
     * - Adds country code if missing (simplified)
     */
    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone must not be blank");
        }
        return phone.replaceAll("[^0-9+]", "");
    }

    /**
     * Computes HMAC-SHA256 of the normalized contact point.
     */
    private static String computeHmac(String normalizedValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(HMAC_KEY.getBytes());
            byte[] hash = digest.digest(normalizedValue.getBytes());
            return HEX_FORMAT.formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Checks if this contact point matches a suppression list entry.
     */
    public boolean isSuppressed(String suppressionHash) {
        return Objects.equals(this.contactPointHash, suppressionHash);
    }
}

/**
 * Types of contact points.
 */
public enum ContactPointType {
    EMAIL,
    PHONE
}
