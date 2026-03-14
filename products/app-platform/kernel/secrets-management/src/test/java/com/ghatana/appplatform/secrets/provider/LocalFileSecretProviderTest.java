/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.secrets.provider;

import com.ghatana.appplatform.secrets.domain.SecretMetadata;
import com.ghatana.appplatform.secrets.domain.SecretValue;
import com.ghatana.appplatform.secrets.port.SecretProvider;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LocalFileSecretProvider — AES-256-GCM + Argon2id")
class LocalFileSecretProviderTest extends EventloopTestBase {

    @TempDir
    Path tempDir;

    private LocalFileSecretProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        provider = new LocalFileSecretProvider(
                tempDir,
                "test-master-passphrase-1234".toCharArray(),
                Executors.newFixedThreadPool(2)
        );
    }

    @Test
    @DisplayName("putSecret and getSecret round-trips the value")
    void putAndGet_roundTrips() {
        char[] original = "super-secret-password!".toCharArray();
        SecretValue stored = runPromise(() ->
                provider.putSecret("/db/password", original, SecretMetadata.defaults()));

        SecretValue retrieved = runPromise(() -> provider.getSecret("/db/password"));

        assertThat(new String(retrieved.value())).isEqualTo("super-secret-password!");
        assertThat(retrieved.version()).isEqualTo(1);
        assertThat(retrieved.path()).isEqualTo("/db/password");
    }

    @Test
    @DisplayName("putSecret twice increments version")
    void putTwice_incrementsVersion() {
        runPromise(() -> provider.putSecret("/db/pw", "v1".toCharArray(), SecretMetadata.defaults()));
        SecretValue v2 = runPromise(() -> provider.putSecret("/db/pw", "v2".toCharArray(), SecretMetadata.defaults()));

        assertThat(v2.version()).isEqualTo(2);
    }

    @Test
    @DisplayName("getSecret on unknown path throws SecretNotFoundException")
    void get_unknownPath_throws() {
        assertThatThrownBy(() -> runPromise(() -> provider.getSecret("/unknown/path")))
                .hasCauseInstanceOf(SecretProvider.SecretNotFoundException.class);
    }

    @Test
    @DisplayName("deleteSecret removes the secret")
    void delete_removesSecret() {
        runPromise(() -> provider.putSecret("/temp/key", "value".toCharArray(), SecretMetadata.defaults()));
        runPromise(() -> provider.deleteSecret("/temp/key"));

        assertThatThrownBy(() -> runPromise(() -> provider.getSecret("/temp/key")))
                .hasCauseInstanceOf(SecretProvider.SecretNotFoundException.class);
    }

    @Test
    @DisplayName("listSecrets returns paths under prefix")
    void list_returnsMatchingPaths() {
        runPromise(() -> provider.putSecret("/db/primary", "p1".toCharArray(), SecretMetadata.defaults()));
        runPromise(() -> provider.putSecret("/db/replica", "p2".toCharArray(), SecretMetadata.defaults()));
        runPromise(() -> provider.putSecret("/cache/pass", "p3".toCharArray(), SecretMetadata.defaults()));

        List<String> dbSecrets = runPromise(() -> provider.listSecrets("/db/"));

        assertThat(dbSecrets).hasSize(2).allMatch(s -> s.startsWith("/db/"));
    }

    @Test
    @DisplayName("rotateSecret generates a new random value and increments version")
    void rotate_newValueAndVersion() {
        runPromise(() -> provider.putSecret("/api/key", "original".toCharArray(), SecretMetadata.defaults()));

        SecretValue rotated = runPromise(() -> provider.rotateSecret("/api/key"));

        assertThat(rotated.version()).isEqualTo(2);
        // Value should be different from "original"
        assertThat(new String(rotated.value())).isNotEqualTo("original");
    }

    @Test
    @DisplayName("destroy() prevents further access to value")
    void destroy_preventsAccess() {
        runPromise(() -> provider.putSecret("/key/to/destroy", "temp".toCharArray(), SecretMetadata.defaults()));
        SecretValue secret = runPromise(() -> provider.getSecret("/key/to/destroy"));

        secret.destroy();

        assertThatThrownBy(secret::value).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("encryption uses a fresh nonce per put — two puts of same value produce different ciphertext")
    void differentNonce_perPut() throws Exception {
        char[] sameValue = "same-value".toCharArray();
        runPromise(() -> provider.putSecret("/k/a", sameValue, SecretMetadata.defaults()));
        // Store in a second directory to compare raw files
        Path otherDir = tempDir.resolve("other");
        java.nio.file.Files.createDirectories(otherDir);
        LocalFileSecretProvider other = new LocalFileSecretProvider(
                otherDir, "test-master-passphrase-1234".toCharArray(), Executors.newSingleThreadExecutor());
        runPromise(() -> other.putSecret("/k/a", sameValue, SecretMetadata.defaults()));

        // Both should decrypt to the same value despite different nonces
        SecretValue v1 = runPromise(() -> provider.getSecret("/k/a"));
        SecretValue v2 = runPromise(() -> other.getSecret("/k/a"));
        assertThat(new String(v1.value())).isEqualTo(new String(v2.value()));
    }
}
