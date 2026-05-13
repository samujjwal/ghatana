/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Resolves stable skill IDs from situation descriptions.
 *
 * <p>Uses SHA-256 hashing of normalized situation text to generate
 * stable, deterministic skill IDs across JVM restarts and version changes.
 * This ensures that the same skill always receives the same identifier,
 * enabling stable mastery tracking.
 *
 * @doc.type class
 * @doc.purpose Stable skill ID generation for mastery tracking
 * @doc.layer agent-core
 * @doc.pattern Utility
 */
public final class SkillIdResolver {

    private static final String SKILL_ID_PREFIX = "skill";

    private SkillIdResolver() {
        // Utility class
    }

    /**
     * Generates a stable skill ID from agent ID and situation description.
     *
     * <p>The ID is deterministic: the same agent and situation will always
     * produce the same skill ID, regardless of JVM instance or version.
     *
     * @param agentId agent identifier
     * @param situation situation description
     * @return stable skill ID
     */
    @NotNull
    public static String resolveSkillId(@NotNull String agentId, @NotNull String situation) {
        // Normalize the situation for consistent hashing
        String normalizedSituation = normalizeSituation(situation);
        
        // Combine agent ID with normalized situation
        String combined = agentId + "::" + normalizedSituation;
        
        // Generate SHA-256 hash and convert to hex
        String hash = sha256Hex(combined);
        
        // Use first 16 characters of hash for a stable but readable ID
        String shortHash = hash.substring(0, 16);
        
        return SKILL_ID_PREFIX + "-" + agentId + "-" + shortHash;
    }

    /**
     * Normalizes a situation description for consistent hashing.
     *
     * <p>Normalization includes:
     * <ul>
     *   <li>Trimming whitespace</li>
     *   <li>Converting to lowercase</li>
     *   <li>Removing extra whitespace</li>
     *   <li>Removing common punctuation variations</li>
     * </ul>
     *
     * @param situation raw situation description
     * @return normalized situation
     */
    @NotNull
    private static String normalizeSituation(@NotNull String situation) {
        return situation
                .trim()
                .toLowerCase()
                .replaceAll("\\s+", " ")
                .replaceAll("[.,;!?]", "")
                .strip();
    }

    /**
     * Computes SHA-256 hash of a string and returns hex representation.
     *
     * @param input string to hash
     * @return hexadecimal hash string
     */
    @NotNull
    private static String sha256Hex(@NotNull String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in all Java implementations
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
