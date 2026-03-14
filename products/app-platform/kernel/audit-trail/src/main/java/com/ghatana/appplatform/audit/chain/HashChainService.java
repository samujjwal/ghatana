package com.ghatana.appplatform.audit.chain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.appplatform.audit.domain.AuditEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SHA-256 hash chain service for the audit trail.
 *
 * <p>Implements the cryptographic chaining algorithm:
 * <pre>
 *   message = previous_hash + sorted_canonical_json({ action, actor, resource,
 *                                                      details, outcome,
 *                                                      timestamp_gregorian,
 *                                                      sequence_number })
 *   current_hash = SHA-256(message)
 * </pre>
 *
 * <p>All operations are synchronous and CPU-bound; call them inside
 * {@code Promise.ofBlocking} when used from an ActiveJ eventloop context.
 *
 * @doc.type class
 * @doc.purpose SHA-256 hash chain computation and verification for the audit trail
 * @doc.layer product
 * @doc.pattern Service
 */
public final class HashChainService {

    private static final Logger log = LoggerFactory.getLogger(HashChainService.class);

    /** 64-character all-zero hex string used as the previous_hash for the genesis entry. */
    public static final String GENESIS_HASH =
        "0000000000000000000000000000000000000000000000000000000000000000";

    private final ObjectMapper canonicalMapper;

    public HashChainService() {
        this.canonicalMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            // Sorted keys ensure canonical JSON regardless of insertion order
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    /**
     * Compute the hash for a new audit log entry.
     *
     * @param previousHash 64-char hex SHA-256 of the preceding entry; use {@link #GENESIS_HASH} for the first entry
     * @param entry        the audit entry being persisted
     * @param sequenceNumber per-tenant sequence number assigned by the store
     * @return 64-char lowercase hex SHA-256 string
     */
    public String computeHash(String previousHash, AuditEntry entry, long sequenceNumber) {
        String canonical = buildCanonicalJson(entry, sequenceNumber);
        String message   = previousHash + canonical;
        return sha256Hex(message);
    }

    /**
     * Verify a stored hash against the expected chain computation.
     *
     * @param previousHash the hash of the preceding entry
     * @param entry        the stored audit entry fields reproduced for verification
     * @param sequenceNumber the stored sequence number
     * @param storedHash   the hash stored in the database
     * @return {@code true} when the stored hash matches the recomputed hash
     */
    public boolean verify(String previousHash, AuditEntry entry,
                          long sequenceNumber, String storedHash) {
        String expected = computeHash(previousHash, entry, sequenceNumber);
        // Timing-safe comparison
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            storedHash.getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String buildCanonicalJson(AuditEntry entry, long sequenceNumber) {
        // Fields must be in a deterministic, sorted order
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action",               entry.action());
        payload.put("actor_user_id",        entry.actor().userId());
        payload.put("actor_role",           entry.actor().role());
        payload.put("outcome",              entry.outcome().name());
        payload.put("resource_id",          entry.resource().id());
        payload.put("resource_type",        entry.resource().type());
        payload.put("sequence_number",      sequenceNumber);
        payload.put("tenant_id",            entry.tenantId());
        payload.put("timestamp_gregorian",  entry.timestampGregorian().toString());

        try {
            return canonicalMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize canonical JSON for hash", e);
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JDK spec — this cannot happen
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
