/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Service for computing and verifying audit event hash-chain integrity.
 *
 * <p>Implements append-only hash-chain verification where each audit event
 * contains a hash of the previous event, creating a tamper-evident chain.
 * If any event is modified or deleted, the chain will break and verification will fail.</p>
 *
 * <p><b>Hash Computation:</b>
 * SHA-256 hash of: previous_hash + id + tenantId + eventType + principal +
 * resourceType + resourceId + success + timestamp + detailsJson</p>
 *
 * @doc.type class
 * @doc.purpose Compute and verify audit event hash-chain integrity
 * @doc.layer infrastructure
 * @doc.pattern Service
 */
public class AuditIntegrityService {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    /**
     * Computes the chain hash for an audit event.
     *
     * @param event the audit event entity
     * @param previousHash the hash of the previous event (null for first event)
     * @return the computed chain hash
     */
    public String computeChainHash(AuditEventEntity event, String previousHash) {
        Objects.requireNonNull(event, "event cannot be null");
        
        String input = String.join("|",
            previousHash != null ? previousHash : "",
            event.getId(),
            event.getTenantId(),
            event.getEventType(),
            event.getPrincipal() != null ? event.getPrincipal() : "",
            event.getResourceType() != null ? event.getResourceType() : "",
            event.getResourceId() != null ? event.getResourceId() : "",
            event.getSuccess() != null ? event.getSuccess().toString() : "",
            event.getTimestamp() != null ? event.getTimestamp().toString() : "",
            event.getDetailsJson() != null ? event.getDetailsJson() : ""
        );
        
        return sha256Hash(input);
    }

    /**
     * Verifies the integrity of an audit event chain.
     *
     * @param events the ordered list of audit events (oldest to newest)
     * @return true if the chain is intact, false otherwise
     */
    public boolean verifyChainIntegrity(List<AuditEventEntity> events) {
        if (events == null || events.isEmpty()) {
            return true;
        }

        String previousHash = null;
        for (AuditEventEntity event : events) {
            String expectedHash = computeChainHash(event, previousHash);
            if (!expectedHash.equals(event.getChainHash())) {
                return false;
            }
            previousHash = event.getChainHash();
        }

        return true;
    }

    /**
     * Verifies the integrity of a single event against its expected hash.
     *
     * @param event the audit event entity
     * @param previousHash the expected previous hash
     * @return true if the event's hash matches the computed hash
     */
    public boolean verifyEventHash(AuditEventEntity event, String previousHash) {
        Objects.requireNonNull(event, "event cannot be null");
        String expectedHash = computeChainHash(event, previousHash);
        return expectedHash.equals(event.getChainHash());
    }

    /**
     * Computes SHA-256 hash of the input string.
     *
     * @param input the string to hash
     * @return hex-encoded hash
     */
    private String sha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HEX_FORMAT.formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
