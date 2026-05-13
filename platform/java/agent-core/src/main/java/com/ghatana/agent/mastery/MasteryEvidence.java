/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/**
 * Evidence supporting a mastery state transition.
 *
 * <p>Evidence is tenant-scoped for governance and isolation.
 *
 * @doc.type record
 * @doc.purpose Evidence for mastery transitions
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record MasteryEvidence(
        @NotNull String evidenceId,
        @NotNull String tenantId,
        @NotNull MasteryEvidenceType type,
        @NotNull String ref,
        @NotNull String digest,
        @NotNull Instant createdAt,
        @NotNull String createdBy,
        double weight,
        @NotNull Map<String, String> labels
) {
    public MasteryEvidence {
        Objects.requireNonNull(evidenceId, "evidenceId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(ref, "ref must not be null");
        Objects.requireNonNull(digest, "digest must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(createdBy, "createdBy must not be null");
        Objects.requireNonNull(labels, "labels must not be null");
        if (weight < 0.0 || weight > 1.0) {
            throw new IllegalArgumentException("weight must be between 0.0 and 1.0");
        }
        if (digest.isEmpty()) {
            throw new IllegalArgumentException("digest must not be empty");
        }
        labels = Map.copyOf(labels);
    }

    /**
     * Computes SHA-256 digest of a string.
     *
     * @param input string to digest
     * @return hexadecimal digest string
     */
    @NotNull
    private static String computeDigest(@NotNull String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in all Java implementations
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Creates a new evidence with a generated ID and current timestamp.
     *
     * <p>Digest is computed from the ref for auditability.
     *
     * @param tenantId tenant identifier
     * @param type evidence type
     * @param ref reference to the evidence source
     * @param createdBy creator of the evidence
     * @return new mastery evidence
     */
    @NotNull
    public static MasteryEvidence create(
            @NotNull String tenantId,
            @NotNull MasteryEvidenceType type,
            @NotNull String ref,
            @NotNull String createdBy
    ) {
        return new MasteryEvidence(
                java.util.UUID.randomUUID().toString(),
                tenantId,
                type,
                ref,
                computeDigest(ref),
                Instant.now(),
                createdBy,
                1.0,
                Map.of()
        );
    }

    /**
     * Creates a new evidence with custom weight.
     *
     * <p>Digest is computed from the ref for auditability.
     *
     * @param tenantId tenant identifier
     * @param type evidence type
     * @param ref reference to the evidence source
     * @param createdBy creator of the evidence
     * @param weight evidence weight (0.0 to 1.0)
     * @return new mastery evidence
     */
    @NotNull
    public static MasteryEvidence create(
            @NotNull String tenantId,
            @NotNull MasteryEvidenceType type,
            @NotNull String ref,
            @NotNull String createdBy,
            double weight
    ) {
        return new MasteryEvidence(
                java.util.UUID.randomUUID().toString(),
                tenantId,
                type,
                ref,
                computeDigest(ref),
                Instant.now(),
                createdBy,
                weight,
                Map.of()
        );
    }
}
