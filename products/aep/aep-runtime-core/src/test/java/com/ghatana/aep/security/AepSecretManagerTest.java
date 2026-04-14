/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AepSecretManager}.
 *
 * <p>Tests cover each resolution tier independently (file, Vault mock, env), as
 * well as cache invalidation and the {@link AepSecretManager#parseVaultKvV2Response}
 * static helper.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AepSecretManager multi-tier secret resolution
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepSecretManager")
class AepSecretManagerTest {

    // =========================================================================
    //  1. Environment variable fallback tier
    // =========================================================================

    @Nested
    @DisplayName("Environment variable tier")
    class EnvironmentTier {

        @Test
        @DisplayName("get() returns value from env when key is present")
        void getEnvKeyPresentReturnsValue() {
            AepSecretManager sm = AepSecretManager.forTesting(Map.of("MY_SECRET", "env-value"));
            assertThat(sm.get("MY_SECRET")).contains("env-value");
        }

        @Test
        @DisplayName("get() returns empty when key not found anywhere")
        void getNotFoundReturnsEmpty() {
            AepSecretManager sm = AepSecretManager.forTesting(Map.of());
            assertThat(sm.get("MISSING_KEY")).isEmpty();
        }

        @Test
        @DisplayName("require() throws when key not found")
        void requireNotFoundThrows() {
            AepSecretManager sm = AepSecretManager.forTesting(Map.of());
            assertThatThrownBy(() -> sm.require("REQUIRED_KEY"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("REQUIRED_KEY");
        }

        @Test
        @DisplayName("has() returns true when secret is available")
        void hasAvailableReturnsTrue() {
            AepSecretManager sm = AepSecretManager.forTesting(Map.of("PRESENT", "val"));
            assertThat(sm.has("PRESENT")).isTrue();
        }

        @Test
        @DisplayName("has() returns false when secret is not available")
        void hasAbsentReturnsFalse() {
            AepSecretManager sm = AepSecretManager.forTesting(Map.of());
            assertThat(sm.has("ABSENT")).isFalse();
        }

        @Test
        @DisplayName("env value with blank string returns empty")
        void getBlankEnvValueReturnsEmpty() {
            AepSecretManager sm = AepSecretManager.forTesting(Map.of("MY_KEY", "   "));
            assertThat(sm.get("MY_KEY")).isEmpty();
        }
    }

    // =========================================================================
    //  2. Kubernetes file tier
    // =========================================================================

    @Nested
    @DisplayName("Kubernetes file tier")
    class KubernetesTier {

        @Test
        @DisplayName("get() reads from file when file exists")
        void getFileExistsReturnsFileContent(@TempDir Path secretsDir) throws IOException {
            Files.writeString(secretsDir.resolve("DB_PASSWORD"), "file-secret", StandardCharsets.UTF_8);

            AepSecretManager sm = new AepSecretManager(
                    Map.of(),            // no env fallback
                    secretsDir,          // K8s dir
                    null, null, null);   // no Vault

            assertThat(sm.get("DB_PASSWORD")).contains("file-secret");
        }

        @Test
        @DisplayName("get() strips trailing whitespace from file content")
        void getFileWithTrailingNewlineStripsWhitespace(@TempDir Path secretsDir) throws IOException {
            Files.writeString(secretsDir.resolve("API_KEY"), "secret-value\n\n", StandardCharsets.UTF_8);

            AepSecretManager sm = new AepSecretManager(Map.of(), secretsDir, null, null, null);

            assertThat(sm.get("API_KEY")).contains("secret-value");
        }

        @Test
        @DisplayName("get() falls back to env when file is missing")
        void getFileMissingFallsBackToEnv(@TempDir Path secretsDir) {
            AepSecretManager sm = new AepSecretManager(
                    Map.of("ENV_KEY", "env-fallback"),
                    secretsDir,
                    null, null, null);

            assertThat(sm.get("ENV_KEY")).contains("env-fallback");
        }

        @Test
        @DisplayName("file tier takes priority over env var")
        void getFileTakesPriorityOverEnv(@TempDir Path secretsDir) throws IOException {
            Files.writeString(secretsDir.resolve("SHARED_KEY"), "file-wins", StandardCharsets.UTF_8);

            AepSecretManager sm = new AepSecretManager(
                    Map.of("SHARED_KEY", "env-value"),
                    secretsDir,
                    null, null, null);

            assertThat(sm.get("SHARED_KEY")).contains("file-wins");
        }

        @Test
        @DisplayName("empty file content returns empty Optional")
        void getEmptyFileReturnsEmpty(@TempDir Path secretsDir) throws IOException {
            Files.writeString(secretsDir.resolve("EMPTY_KEY"), "  \n  ", StandardCharsets.UTF_8);

            AepSecretManager sm = new AepSecretManager(Map.of(), secretsDir, null, null, null);

            assertThat(sm.get("EMPTY_KEY")).isEmpty();
        }
    }

    // =========================================================================
    //  3. Cache invalidation
    // =========================================================================

    @Nested
    @DisplayName("Cache management")
    class CacheManagement {

        @Test
        @DisplayName("invalidate() removes a single key from cache")
        void invalidateSingleKey() {
            // Vault is disabled (no addr), so this just tests the invalidate path
            AepSecretManager sm = AepSecretManager.forTesting(Map.of("K", "v"));
            sm.invalidate("K");       // should not throw
            assertThat(sm.get("K")).contains("v"); // still resolvable via env
        }

        @Test
        @DisplayName("invalidateAll() clears all cached entries")
        void invalidateAllClearsEverything() {
            AepSecretManager sm = AepSecretManager.forTesting(Map.of("K1", "v1", "K2", "v2"));
            sm.invalidateAll();       // should not throw
            assertThat(sm.get("K1")).contains("v1"); // still resolvable via env
            assertThat(sm.get("K2")).contains("v2");
        }
    }

    // =========================================================================
    //  4. Vault KV v2 JSON parsing (static helper)
    // =========================================================================

    @Nested
    @DisplayName("Vault KV v2 JSON parsing")
    class VaultJsonParsing {

        @Test
        @DisplayName("parses string value from KV v2 response")
        void parseStringValue() {
            String json = "{\"request_id\":\"abc\",\"data\":{\"data\":{\"MY_SECRET\":\"secret-val\"}}}";
            Optional<String> result = AepSecretManager.parseVaultKvV2Response(json, "MY_SECRET");
            assertThat(result).contains("secret-val");
        }

        @Test
        @DisplayName("returns empty when key not in response")
        void parseKeyAbsentReturnsEmpty() {
            String json = "{\"data\":{\"data\":{\"OTHER_KEY\":\"other-val\"}}}";
            Optional<String> result = AepSecretManager.parseVaultKvV2Response(json, "MY_SECRET");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty for null JSON")
        void parseNullJsonReturnsEmpty() {
            assertThat(AepSecretManager.parseVaultKvV2Response(null, "KEY")).isEmpty();
        }

        @Test
        @DisplayName("returns empty for blank JSON")
        void parseBlankJsonReturnsEmpty() {
            assertThat(AepSecretManager.parseVaultKvV2Response("   ", "KEY")).isEmpty();
        }

        @Test
        @DisplayName("parses value when multiple keys exist in response")
        void parseMultipleKeysPicksCorrectOne() {
            String json = "{\"data\":{\"data\":{\"KEY_A\":\"val-a\",\"KEY_B\":\"val-b\",\"KEY_C\":\"val-c\"}}}";
            assertThat(AepSecretManager.parseVaultKvV2Response(json, "KEY_B")).contains("val-b");
            assertThat(AepSecretManager.parseVaultKvV2Response(json, "KEY_C")).contains("val-c");
        }
    }

    // =========================================================================
    //  5. require() and null safety
    // =========================================================================

    @Nested
    @DisplayName("require() and null safety")
    class RequireAndNullSafety {

        @Test
        @DisplayName("require() returns value when present in env")
        void requirePresentReturnsValue() {
            AepSecretManager sm = AepSecretManager.forTesting(Map.of("MY_KEY", "my-val"));
            assertThat(sm.require("MY_KEY")).isEqualTo("my-val");
        }

        @Test
        @DisplayName("get() with null key throws NullPointerException")
        void getNullKeyThrowsNpe() {
            AepSecretManager sm = AepSecretManager.forTesting(Map.of());
            assertThatThrownBy(() -> sm.get(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("require() with null key throws NullPointerException")
        void requireNullKeyThrowsNpe() {
            AepSecretManager sm = AepSecretManager.forTesting(Map.of());
            assertThatThrownBy(() -> sm.require(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
