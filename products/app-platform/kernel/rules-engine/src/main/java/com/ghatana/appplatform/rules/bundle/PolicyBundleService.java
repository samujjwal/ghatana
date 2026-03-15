/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.rules.bundle;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for OPA policy bundle lifecycle management (STORY-K03-002).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li><b>Upload</b>: compute SHA-256 hash of raw bundle content, assign the next version
 *       number, and store via {@link PolicyBundleStore}.</li>
 *   <li><b>Activate</b>: mark a specific bundle version as active; deactivate any
 *       previously active bundle. OPA polls the active bundle via its bundle API.</li>
 *   <li><b>Integrity verification</b>: recompute SHA-256 and compare against the stored
 *       hash before serving a bundle. Corrupt bundles are rejected with
 *       {@link PolicyBundleCorruptException}; the previously active bundle is retained.</li>
 *   <li><b>Versioning</b>: each upload increments the monotonic version counter.
 *       All versions are persisted for audit and rollback.</li>
 * </ul>
 *
 * <p>This service is synchronous. Callers are responsible for wrapping calls in
 * {@code Promise.ofBlocking} when used from an ActiveJ event loop.
 *
 * @doc.type class
 * @doc.purpose Policy bundle upload, activation, versioning and integrity verification (K03-002)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PolicyBundleService {

    private final PolicyBundleStore store;

    /**
     * @param store underlying bundle storage (S3/MinIO or in-memory for tests)
     */
    public PolicyBundleService(PolicyBundleStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Uploads a new bundle version.
     *
     * <p>The SHA-256 hash of {@code content} is computed and stored alongside the
     * bundle content. The version number is {@code (current max version) + 1}.
     *
     * @param name    bundle name identifying the policy scope (e.g. {@code "authz"})
     * @param content raw bundle bytes (typically a tar.gz archive of Rego + data files)
     * @return the stored bundle with assigned version and computed hash
     * @throws IllegalArgumentException if name is blank or content is empty
     */
    public PolicyBundle uploadBundle(String name, byte[] content) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(content, "content");
        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (content.length == 0) throw new IllegalArgumentException("content must not be empty");

        int nextVersion = store.latestVersion() + 1;
        String sha256 = sha256Hex(content);
        String bundleId = UUID.randomUUID().toString();

        PolicyBundle bundle = new PolicyBundle(
                bundleId, name, nextVersion, sha256, content, false, Instant.now());
        store.save(bundle);
        return bundle;
    }

    // ── Activation ────────────────────────────────────────────────────────────

    /**
     * Activates the bundle with the given ID for OPA polling.
     *
     * <p>Any previously active bundle is deactivated. OPA will download the newly
     * active bundle on its next poll cycle. Integrity is <em>not</em> re-verified here —
     * it was verified at upload time. Call {@link #getVerifiedBundle(String)} to verify
     * before serving to OPA.
     *
     * @param bundleId ID of the bundle to activate
     * @return the activated bundle
     * @throws java.util.NoSuchElementException if the bundle is not found
     */
    public PolicyBundle activateBundle(String bundleId) {
        Objects.requireNonNull(bundleId, "bundleId");

        // Deactivate any currently active bundle
        store.findActive().ifPresent(prev -> store.update(prev.deactivated()));

        PolicyBundle bundle = store.findById(bundleId)
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Bundle not found: " + bundleId));
        PolicyBundle activated = bundle.activated();
        store.update(activated);
        return activated;
    }

    // ── Integrity verification ────────────────────────────────────────────────

    /**
     * Verifies that a stored bundle's content matches its SHA-256 hash.
     *
     * @param bundleId ID of the bundle to verify
     * @return {@code true} if the hash matches; {@code false} if the bundle is not found
     */
    public boolean verifyIntegrity(String bundleId) {
        Objects.requireNonNull(bundleId, "bundleId");
        return store.findById(bundleId)
                .map(b -> sha256Hex(b.content()).equals(b.sha256Hash()))
                .orElse(false);
    }

    /**
     * Returns a bundle after verifying its integrity.
     *
     * <p>Recomputes the SHA-256 from the stored content and compares against
     * {@link PolicyBundle#sha256Hash()}. Throws {@link PolicyBundleCorruptException}
     * if they differ (storage corruption or tampering detected). OPA must retain the
     * previous active bundle in this case.
     *
     * @param bundleId ID of the bundle to retrieve
     * @return verified bundle
     * @throws PolicyBundleCorruptException     if the content hash does not match
     * @throws java.util.NoSuchElementException if the bundle is not found
     */
    public PolicyBundle getVerifiedBundle(String bundleId) {
        Objects.requireNonNull(bundleId, "bundleId");
        PolicyBundle bundle = store.findById(bundleId)
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Bundle not found: " + bundleId));
        String computed = sha256Hex(bundle.content());
        if (!computed.equals(bundle.sha256Hash())) {
            throw new PolicyBundleCorruptException(bundleId);
        }
        return bundle;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Returns the currently active bundle, if any.
     */
    public Optional<PolicyBundle> getActiveBundle() {
        return store.findActive();
    }

    /**
     * Returns all bundle versions in ascending version order.
     *
     * <p>Each upload creates a new version; none are deleted. This list is used
     * for audit, rollback, and version comparison.
     */
    public List<PolicyBundle> getAllVersions() {
        return store.findAll();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
