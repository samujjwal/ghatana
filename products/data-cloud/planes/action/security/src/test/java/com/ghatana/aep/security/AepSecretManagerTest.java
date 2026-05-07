/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AepSecretManager}.
 *
 * <p>All tests use {@link AepSecretManager#forTesting(Map)} which disables the
 * K8s volume and Vault tiers, leaving only the environment-variable tier active.
 * This makes tests hermetic without requiring any external system.
 *
 * @doc.type class
 * @doc.purpose Verifies AepSecretManager resolution, error handling, and cache invalidation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepSecretManager")
class AepSecretManagerTest {

    // =========================================================================
    //  get() – resolution and absence
    // =========================================================================

    @Nested
    @DisplayName("get()")
    class GetTests {

        @Test
        @DisplayName("returns the env-var value when the key is present")
        void get_presentKey_returnsValue() {
            AepSecretManager manager = AepSecretManager.forTesting(
                    Map.of("MY_SECRET", "s3cr3t-value"));

            Optional<String> result = manager.get("MY_SECRET");

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo("s3cr3t-value");
        }

        @Test
        @DisplayName("returns empty when the key is not present in any tier")
        void get_missingKey_returnsEmpty() {
            AepSecretManager manager = AepSecretManager.forTesting(Map.of());

            Optional<String> result = manager.get("NONEXISTENT_KEY");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("resolves multiple keys from the same env map")
        void get_multipleKeys_resolvesEachIndependently() {
            AepSecretManager manager = AepSecretManager.forTesting(
                    Map.of("KEY_A", "alpha", "KEY_B", "beta"));

            assertThat(manager.get("KEY_A")).contains("alpha");
            assertThat(manager.get("KEY_B")).contains("beta");
            assertThat(manager.get("KEY_C")).isEmpty();
        }

        @Test
        @DisplayName("throws NullPointerException when key is null")
        void get_nullKey_throwsNpe() {
            AepSecretManager manager = AepSecretManager.forTesting(Map.of());

            assertThatThrownBy(() -> manager.get(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    //  require() – mandatory resolution
    // =========================================================================

    @Nested
    @DisplayName("require()")
    class RequireTests {

        @Test
        @DisplayName("returns the value when the key is present")
        void require_presentKey_returnsValue() {
            AepSecretManager manager = AepSecretManager.forTesting(
                    Map.of("DB_PASSWORD", "supersecret"));

            String result = manager.require("DB_PASSWORD");

            assertThat(result).isEqualTo("supersecret");
        }

        @Test
        @DisplayName("throws IllegalStateException when the key is absent, with key name in message")
        void require_missingKey_throwsIllegalStateExceptionWithKeyName() {
            AepSecretManager manager = AepSecretManager.forTesting(Map.of());

            assertThatThrownBy(() -> manager.require("MISSING_SECRET"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MISSING_SECRET");
        }

        @Test
        @DisplayName("exception message mentions all source tiers")
        void require_missingKey_exceptionMentionsTiers() {
            AepSecretManager manager = AepSecretManager.forTesting(Map.of());

            assertThatThrownBy(() -> manager.require("ABSENT_KEY"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContainingAll("K8s", "Vault", "environment");
        }
    }

    // =========================================================================
    //  has() – presence check
    // =========================================================================

    @Nested
    @DisplayName("has()")
    class HasTests {

        @Test
        @DisplayName("returns true when the key is present")
        void has_presentKey_returnsTrue() {
            AepSecretManager manager = AepSecretManager.forTesting(
                    Map.of("FEATURE_FLAG", "enabled"));

            assertThat(manager.has("FEATURE_FLAG")).isTrue();
        }

        @Test
        @DisplayName("returns false when the key is absent")
        void has_missingKey_returnsFalse() {
            AepSecretManager manager = AepSecretManager.forTesting(Map.of());

            assertThat(manager.has("DOES_NOT_EXIST")).isFalse();
        }
    }

    // =========================================================================
    //  invalidate() and invalidateAll() – cache management
    // =========================================================================

    @Nested
    @DisplayName("invalidate() and invalidateAll()")
    class CacheInvalidationTests {

        @Test
        @DisplayName("invalidate() succeeds for a key that was never cached (no-op)")
        void invalidate_uncachedKey_noOp() {
            AepSecretManager manager = AepSecretManager.forTesting(
                    Map.of("MY_KEY", "my-value"));

            // Should not throw even if key was not in Vault cache
            manager.invalidate("MY_KEY");

            // Resolution from env var still works
            assertThat(manager.get("MY_KEY")).contains("my-value");
        }

        @Test
        @DisplayName("invalidateAll() succeeds with empty cache and resolution still works")
        void invalidateAll_emptyCache_resolutionUnaffected() {
            AepSecretManager manager = AepSecretManager.forTesting(
                    Map.of("TOKEN", "abc123"));

            manager.invalidateAll();

            // Env-var resolution is not cached, so it should still resolve
            assertThat(manager.get("TOKEN")).contains("abc123");
        }

        @Test
        @DisplayName("invalidateAll() clears all entries and subsequent resolution still works")
        void invalidateAll_afterMultipleGets_cacheCleared() {
            AepSecretManager manager = AepSecretManager.forTesting(
                    Map.of("A", "1", "B", "2"));

            // Populate the path (Vault cache would be populated in real Vault mode)
            manager.get("A");
            manager.get("B");
            manager.invalidateAll();

            // Env-var tier still resolves after clear
            assertThat(manager.get("A")).contains("1");
            assertThat(manager.get("B")).contains("2");
        }
    }

    // =========================================================================
    //  Constants validation
    // =========================================================================

    @Nested
    @DisplayName("AepSecretManager constants")
    class ConstantsTests {

        @Test
        @DisplayName("VAULT_CACHE_TTL_MS is exactly 60 seconds")
        void vaultCacheTtl_is60Seconds() {
            assertThat(AepSecretManager.VAULT_CACHE_TTL_MS).isEqualTo(60_000L);
        }

        @Test
        @DisplayName("DEFAULT_SECRETS_DIR is the standard K8s path for AEP")
        void defaultSecretsDir_isStandardK8sPath() {
            assertThat(AepSecretManager.DEFAULT_SECRETS_DIR)
                    .isEqualTo("/var/run/secrets/aep");
        }

        @Test
        @DisplayName("DEFAULT_VAULT_PATH matches the KV v2 AEP mount convention")
        void defaultVaultPath_matchesKvV2Convention() {
            assertThat(AepSecretManager.DEFAULT_VAULT_PATH)
                    .isEqualTo("secret/data/aep");
        }
    }

    // =========================================================================
    //  startRotationChecker / stopRotationChecker
    // =========================================================================

    @Nested
    @DisplayName("startRotationChecker()")
    class RotationCheckerTests {

        @Test
        @DisplayName("startRotationChecker() returns the same instance for chaining")
        void startRotationChecker_returnsSelf() {
            AepSecretManager manager = AepSecretManager.forTesting(Map.of());

            AepSecretManager returned = manager.startRotationChecker();

            assertThat(returned).isSameAs(manager);
            manager.stopRotationChecker(); // clean up
        }

        @Test
        @DisplayName("startRotationChecker() called twice is idempotent")
        void startRotationChecker_calledTwice_idempotent() {
            AepSecretManager manager = AepSecretManager.forTesting(Map.of());

            manager.startRotationChecker();
            manager.startRotationChecker(); // second call is a no-op

            // Secret resolution still works normally
            assertThat(manager.get("ANYTHING")).isEmpty();
            manager.stopRotationChecker();
        }

        @Test
        @DisplayName("stopRotationChecker() is safe when checker was never started")
        void stopRotationChecker_neverStarted_noOp() {
            AepSecretManager manager = AepSecretManager.forTesting(Map.of());

            // Should not throw
            manager.stopRotationChecker();
        }
    }

    // =========================================================================
    //  forTesting() factory validation
    // =========================================================================

    @Nested
    @DisplayName("forTesting() factory")
    class ForTestingFactoryTests {

        @Test
        @DisplayName("forTesting() creates a manager that resolves env entries")
        void forTesting_resolvesProvidedEntries() {
            AepSecretManager manager = AepSecretManager.forTesting(
                    Map.of("ALPHA", "one", "BETA", "two"));

            assertThat(manager.get("ALPHA")).contains("one");
            assertThat(manager.get("BETA")).contains("two");
        }

        @Test
        @DisplayName("forTesting() with empty map resolves no keys")
        void forTesting_emptyMap_resolvesNothing() {
            AepSecretManager manager = AepSecretManager.forTesting(Map.of());

            assertThat(manager.has("ANY_KEY")).isFalse();
        }
    }
}
