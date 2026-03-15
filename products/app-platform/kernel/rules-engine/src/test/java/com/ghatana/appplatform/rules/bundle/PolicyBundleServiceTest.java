/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.rules.bundle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PolicyBundleService} (STORY-K03-002).
 *
 * <p>Uses an in-memory {@link PolicyBundleStore} to avoid requiring S3/MinIO infrastructure.
 *
 * @doc.type class
 * @doc.purpose Unit tests for OPA policy bundle upload, activation, verification, versioning
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PolicyBundleService — bundle upload, activation, verification, versioning")
class PolicyBundleServiceTest {

    /** Minimal in-memory PolicyBundleStore for testing. */
    private static final class InMemoryBundleStore implements PolicyBundleStore {
        private final Map<String, PolicyBundle> store = new ConcurrentHashMap<>();

        @Override public void save(PolicyBundle b)   { store.put(b.bundleId(), b); }
        @Override public void update(PolicyBundle b)  { store.put(b.bundleId(), b); }
        @Override public Optional<PolicyBundle> findById(String id) { return Optional.ofNullable(store.get(id)); }
        @Override public Optional<PolicyBundle> findActive() {
            return store.values().stream().filter(PolicyBundle::active).findFirst();
        }
        @Override public List<PolicyBundle> findAll() {
            return store.values().stream()
                    .sorted(Comparator.comparingInt(PolicyBundle::version))
                    .toList();
        }
        @Override public int latestVersion() {
            return store.values().stream()
                    .mapToInt(PolicyBundle::version).max().orElse(0);
        }
    }

    private InMemoryBundleStore bundleStore;
    private PolicyBundleService service;

    @BeforeEach
    void setUp() {
        bundleStore = new InMemoryBundleStore();
        service = new PolicyBundleService(bundleStore);
    }

    // ── AC1: bundle upload + hash ─────────────────────────────────────────────

    @Test
    @DisplayName("bundle_upload: uploaded bundle receives SHA-256 hash and version 1")
    void bundle_upload() {
        byte[] content = "package authz\ndefault allow = false".getBytes(StandardCharsets.UTF_8);

        PolicyBundle bundle = service.uploadBundle("authz", content);

        assertThat(bundle.bundleId()).isNotBlank();
        assertThat(bundle.version()).isEqualTo(1);
        assertThat(bundle.sha256Hash()).isNotBlank().hasSize(64); // 256-bit hex = 64 chars
        assertThat(bundle.active()).isFalse(); // not active until explicitly activated
    }

    @Test
    @DisplayName("bundle_upload: computed SHA-256 matches expected hash of content")
    void bundle_upload_hashIsCorrect() throws Exception {
        byte[] content = "package authz".getBytes(StandardCharsets.UTF_8);
        String expectedHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content));

        PolicyBundle bundle = service.uploadBundle("authz", content);

        assertThat(bundle.sha256Hash()).isEqualTo(expectedHash);
    }

    // ── AC2: bundle activation ────────────────────────────────────────────────

    @Test
    @DisplayName("bundle_activate: activated bundle is returned by getActiveBundle()")
    void bundle_activate() {
        byte[] content = bundleBytes("v1");
        PolicyBundle uploaded = service.uploadBundle("authz", content);

        service.activateBundle(uploaded.bundleId());

        Optional<PolicyBundle> active = service.getActiveBundle();
        assertThat(active).isPresent();
        assertThat(active.get().bundleId()).isEqualTo(uploaded.bundleId());
        assertThat(active.get().active()).isTrue();
    }

    @Test
    @DisplayName("bundle_activate: activating new bundle deactivates previous active bundle")
    void bundle_activate_deactivatesPrevious() {
        PolicyBundle v1 = service.uploadBundle("authz", bundleBytes("v1"));
        PolicyBundle v2 = service.uploadBundle("authz", bundleBytes("v2"));

        service.activateBundle(v1.bundleId());
        assertThat(service.getActiveBundle().get().bundleId()).isEqualTo(v1.bundleId());

        service.activateBundle(v2.bundleId());

        assertThat(service.getActiveBundle().get().bundleId()).isEqualTo(v2.bundleId());
        // v1 is no longer active
        assertThat(bundleStore.findById(v1.bundleId()).get().active()).isFalse();
    }

    // ── AC3: hash verification ────────────────────────────────────────────────

    @Test
    @DisplayName("bundle_hashVerification: verifyIntegrity returns true for valid bundle")
    void bundle_hashVerification() {
        PolicyBundle bundle = service.uploadBundle("authz", bundleBytes("good content"));

        assertThat(service.verifyIntegrity(bundle.bundleId())).isTrue();
    }

    @Test
    @DisplayName("bundle_hashVerification: getVerifiedBundle succeeds for untampered bundle")
    void bundle_hashVerification_getVerified_passes() {
        PolicyBundle bundle = service.uploadBundle("authz", bundleBytes("valid rego"));

        assertThatCode(() -> service.getVerifiedBundle(bundle.bundleId()))
                .doesNotThrowAnyException();
    }

    // ── AC4: corrupt bundle rejected ──────────────────────────────────────────

    @Test
    @DisplayName("bundle_corruptRejected: getVerifiedBundle throws PolicyBundleCorruptException on hash mismatch")
    void bundle_corruptRejected() {
        PolicyBundle original = service.uploadBundle("authz", bundleBytes("original"));

        // Simulate storage corruption: replace with a tampered bundle that has wrong hash
        PolicyBundle tampered = new PolicyBundle(
                original.bundleId(), original.name(), original.version(),
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", // fake hash
                "tampered content".getBytes(StandardCharsets.UTF_8),
                original.active(), original.uploadedAt());
        bundleStore.update(tampered);

        assertThatThrownBy(() -> service.getVerifiedBundle(original.bundleId()))
                .isInstanceOf(PolicyBundleCorruptException.class)
                .hasMessageContaining(original.bundleId());
    }

    @Test
    @DisplayName("bundle_corruptRejected: verifyIntegrity returns false for corrupt bundle")
    void bundle_corruptRejected_verifyReturnsFalse() {
        PolicyBundle original = service.uploadBundle("authz", bundleBytes("good"));
        PolicyBundle tampered = new PolicyBundle(
                original.bundleId(), original.name(), original.version(),
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "bad".getBytes(StandardCharsets.UTF_8),
                false, original.uploadedAt());
        bundleStore.update(tampered);

        assertThat(service.verifyIntegrity(original.bundleId())).isFalse();
    }

    // ── AC5: bundle versioning ────────────────────────────────────────────────

    @Test
    @DisplayName("bundle_versioning: each upload increments the version number")
    void bundle_versioning() {
        PolicyBundle v1 = service.uploadBundle("authz", bundleBytes("v1"));
        PolicyBundle v2 = service.uploadBundle("authz", bundleBytes("v2"));
        PolicyBundle v3 = service.uploadBundle("authz", bundleBytes("v3"));

        assertThat(v1.version()).isEqualTo(1);
        assertThat(v2.version()).isEqualTo(2);
        assertThat(v3.version()).isEqualTo(3);
    }

    @Test
    @DisplayName("bundle_versioning: getAllVersions returns all versions in ascending order")
    void bundle_versioning_ascending() {
        service.uploadBundle("authz", bundleBytes("v1"));
        service.uploadBundle("authz", bundleBytes("v2"));
        service.uploadBundle("rules", bundleBytes("v1"));

        List<PolicyBundle> versions = service.getAllVersions();

        assertThat(versions).hasSize(3);
        assertThat(versions.get(0).version()).isEqualTo(1);
        assertThat(versions.get(1).version()).isEqualTo(2);
        assertThat(versions.get(2).version()).isEqualTo(3);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("upload rejects blank name")
    void upload_blankName_rejected() {
        assertThatThrownBy(() -> service.uploadBundle("", bundleBytes("x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("upload rejects empty content")
    void upload_emptyContent_rejected() {
        assertThatThrownBy(() -> service.uploadBundle("authz", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static byte[] bundleBytes(String marker) {
        return ("package authz\n# " + marker).getBytes(StandardCharsets.UTF_8);
    }
}
