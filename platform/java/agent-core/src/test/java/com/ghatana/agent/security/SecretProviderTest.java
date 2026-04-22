/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 *
 * Task 4.9 — Security Hardening: Tests for SecretProvider with tenant isolation.
 */
package com.ghatana.agent.security;

import com.ghatana.agent.security.SecretProvider.InMemorySecretProvider;
import com.ghatana.agent.security.SecretProvider.RotationResult;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for {@link SecretProvider} tenant-scoped secret management.
 * Tests cover CRUD isolation, rotation, concurrent access, and boundary enforcements.
 */
@DisplayName("SecretProvider [GH-90000]")
class SecretProviderTest {

    private static final String TENANT_A = "tenant-alpha";
    private static final String TENANT_B = "tenant-beta";
    private static final String TENANT_C = "tenant-gamma";

    private InMemorySecretProvider secretProvider;

    @BeforeEach
    void setUp() { // GH-90000
        secretProvider = new InMemorySecretProvider(); // GH-90000
    }

    // =========================================================================
    // 1. Basic CRUD
    // =========================================================================

    @Nested
    @DisplayName("Basic CRUD [GH-90000]")
    class BasicCrud {

        @Test
        @DisplayName("putSecret and getSecret round-trip works [GH-90000]")
        void putAndGet() { // GH-90000
            secretProvider.putSecret(TENANT_A, "api-key", "sk-12345"); // GH-90000
            Optional<String> result = secretProvider.getSecret(TENANT_A, "api-key"); // GH-90000

            assertThat(result).isPresent().contains("sk-12345 [GH-90000]");
        }

        @Test
        @DisplayName("getSecret for non-existent key returns empty [GH-90000]")
        void getNonExistent() { // GH-90000
            assertThat(secretProvider.getSecret(TENANT_A, "no-such-key")).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("putSecret overwrites existing value [GH-90000]")
        void putOverwrites() { // GH-90000
            secretProvider.putSecret(TENANT_A, "api-key", "old-value"); // GH-90000
            secretProvider.putSecret(TENANT_A, "api-key", "new-value"); // GH-90000

            assertThat(secretProvider.getSecret(TENANT_A, "api-key")) // GH-90000
                    .isPresent().contains("new-value [GH-90000]");
        }

        @Test
        @DisplayName("deleteSecret removes and returns true [GH-90000]")
        void deleteRemovesAndReturnsTrue() { // GH-90000
            secretProvider.putSecret(TENANT_A, "api-key", "value"); // GH-90000
            boolean deleted = secretProvider.deleteSecret(TENANT_A, "api-key"); // GH-90000

            assertThat(deleted).isTrue(); // GH-90000
            assertThat(secretProvider.getSecret(TENANT_A, "api-key")).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("deleteSecret for non-existent returns false [GH-90000]")
        void deleteNonExistentReturnsFalse() { // GH-90000
            assertThat(secretProvider.deleteSecret(TENANT_A, "no-such-key")).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("hasSecret detects existing and missing keys [GH-90000]")
        void hasSecretDetection() { // GH-90000
            secretProvider.putSecret(TENANT_A, "exists", "value"); // GH-90000

            assertThat(secretProvider.hasSecret(TENANT_A, "exists")).isTrue(); // GH-90000
            assertThat(secretProvider.hasSecret(TENANT_A, "missing")).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // 2. Tenant Isolation
    // =========================================================================

    @Nested
    @DisplayName("Tenant Isolation [GH-90000]")
    class TenantIsolation {

        @Test
        @DisplayName("Secrets from tenant A are invisible to tenant B [GH-90000]")
        void secretsInvisibleAcrossTenants() { // GH-90000
            secretProvider.putSecret(TENANT_A, "model-key", "sk-alpha-secret"); // GH-90000

            assertThat(secretProvider.getSecret(TENANT_B, "model-key")).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Same secret name in different tenants holds different values [GH-90000]")
        void sameNameDifferentValues() { // GH-90000
            secretProvider.putSecret(TENANT_A, "api-key", "alpha-key-123"); // GH-90000
            secretProvider.putSecret(TENANT_B, "api-key", "beta-key-456"); // GH-90000

            assertThat(secretProvider.getSecret(TENANT_A, "api-key")) // GH-90000
                    .isPresent().contains("alpha-key-123 [GH-90000]");
            assertThat(secretProvider.getSecret(TENANT_B, "api-key")) // GH-90000
                    .isPresent().contains("beta-key-456 [GH-90000]");
        }

        @Test
        @DisplayName("Deleting tenant A's secret does not affect tenant B [GH-90000]")
        void deleteTenantADoesNotAffectB() { // GH-90000
            secretProvider.putSecret(TENANT_A, "shared-key", "value-a"); // GH-90000
            secretProvider.putSecret(TENANT_B, "shared-key", "value-b"); // GH-90000

            secretProvider.deleteSecret(TENANT_A, "shared-key"); // GH-90000

            assertThat(secretProvider.getSecret(TENANT_A, "shared-key")).isEmpty(); // GH-90000
            assertThat(secretProvider.getSecret(TENANT_B, "shared-key")) // GH-90000
                    .isPresent().contains("value-b [GH-90000]");
        }

        @Test
        @DisplayName("listSecretNames only returns names for the specified tenant [GH-90000]")
        void listSecretNamesTenantScoped() { // GH-90000
            secretProvider.putSecret(TENANT_A, "key-1", "v1"); // GH-90000
            secretProvider.putSecret(TENANT_A, "key-2", "v2"); // GH-90000
            secretProvider.putSecret(TENANT_B, "key-3", "v3"); // GH-90000

            Set<String> tenantANames = secretProvider.listSecretNames(TENANT_A); // GH-90000
            Set<String> tenantBNames = secretProvider.listSecretNames(TENANT_B); // GH-90000
            Set<String> tenantCNames = secretProvider.listSecretNames(TENANT_C); // GH-90000

            assertThat(tenantANames).containsExactlyInAnyOrder("key-1", "key-2"); // GH-90000
            assertThat(tenantBNames).containsExactly("key-3 [GH-90000]");
            assertThat(tenantCNames).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("hasSecret is tenant-scoped [GH-90000]")
        void hasSecretTenantScoped() { // GH-90000
            secretProvider.putSecret(TENANT_A, "my-secret", "value"); // GH-90000

            assertThat(secretProvider.hasSecret(TENANT_A, "my-secret")).isTrue(); // GH-90000
            assertThat(secretProvider.hasSecret(TENANT_B, "my-secret")).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // 3. Secret Rotation
    // =========================================================================

    @Nested
    @DisplayName("Secret Rotation [GH-90000]")
    class SecretRotation {

        @Test
        @DisplayName("Rotation replaces value and returns metadata [GH-90000]")
        void rotationReplacesValue() { // GH-90000
            secretProvider.putSecret(TENANT_A, "api-key", "old-key"); // GH-90000

            RotationResult result = secretProvider.rotateSecret(TENANT_A, "api-key", "new-key"); // GH-90000

            assertThat(secretProvider.getSecret(TENANT_A, "api-key")) // GH-90000
                    .isPresent().contains("new-key [GH-90000]");
            assertThat(result.tenantId()).isEqualTo(TENANT_A); // GH-90000
            assertThat(result.secretName()).isEqualTo("api-key [GH-90000]");
            assertThat(result.previousVersionAt()).isNotNull(); // GH-90000
            assertThat(result.newVersionAt()).isNotNull(); // GH-90000
            assertThat(result.newVersionAt()).isAfterOrEqualTo(result.previousVersionAt()); // GH-90000
        }

        @Test
        @DisplayName("Rotation of non-existent secret throws NoSuchElementException [GH-90000]")
        void rotationNonExistentThrows() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    secretProvider.rotateSecret(TENANT_A, "ghost-key", "value")) // GH-90000
                    .isInstanceOf(NoSuchElementException.class) // GH-90000
                    .hasMessageContaining("ghost-key [GH-90000]")
                    .hasMessageContaining(TENANT_A); // GH-90000
        }

        @Test
        @DisplayName("Rotation for tenant A does not affect tenant B's same-name secret [GH-90000]")
        void rotationTenantIsolated() { // GH-90000
            secretProvider.putSecret(TENANT_A, "shared-key", "alpha-v1"); // GH-90000
            secretProvider.putSecret(TENANT_B, "shared-key", "beta-v1"); // GH-90000

            secretProvider.rotateSecret(TENANT_A, "shared-key", "alpha-v2"); // GH-90000

            assertThat(secretProvider.getSecret(TENANT_A, "shared-key")) // GH-90000
                    .isPresent().contains("alpha-v2 [GH-90000]");
            assertThat(secretProvider.getSecret(TENANT_B, "shared-key")) // GH-90000
                    .isPresent().contains("beta-v1 [GH-90000]"); // Unchanged
        }
    }

    // =========================================================================
    // 4. Input Validation
    // =========================================================================

    @Nested
    @DisplayName("Input Validation [GH-90000]")
    class InputValidation {

        @Test
        @DisplayName("putSecret rejects null arguments [GH-90000]")
        void putRejectsNulls() { // GH-90000
            assertThatThrownBy(() -> secretProvider.putSecret(null, "key", "value")) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> secretProvider.putSecret(TENANT_A, null, "value")) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
            assertThatThrownBy(() -> secretProvider.putSecret(TENANT_A, "key", null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // =========================================================================
    // 5. Concurrent Access
    // =========================================================================

    @Nested
    @DisplayName("Concurrent Access [GH-90000]")
    class ConcurrentAccess {

        @Test
        @DisplayName("Concurrent put/get across tenants maintains isolation [GH-90000]")
        void concurrentPutGetIsolation() throws Exception { // GH-90000
            int opsPerTenant = 30;
            ExecutorService executor = Executors.newFixedThreadPool(6); // GH-90000
            CountDownLatch latch = new CountDownLatch(1); // GH-90000
            List<Future<?>> futures = new java.util.ArrayList<>(); // GH-90000

            String[] tenants = {TENANT_A, TENANT_B, TENANT_C};
            for (String tenant : tenants) { // GH-90000
                for (int i = 0; i < opsPerTenant; i++) { // GH-90000
                    final int idx = i;
                    futures.add(executor.submit(() -> { // GH-90000
                        try {
                            latch.await(); // GH-90000
                        } catch (InterruptedException e) { // GH-90000
                            Thread.currentThread().interrupt(); // GH-90000
                            return;
                        }
                        secretProvider.putSecret(tenant, "key-" + idx, tenant + "-value-" + idx); // GH-90000
                    }));
                }
            }

            latch.countDown(); // GH-90000
            for (Future<?> f : futures) { // GH-90000
                f.get(10, TimeUnit.SECONDS); // GH-90000
            }

            executor.shutdown(); // GH-90000
            executor.awaitTermination(5, TimeUnit.SECONDS); // GH-90000

            // Verify isolation
            for (String tenant : tenants) { // GH-90000
                Set<String> names = secretProvider.listSecretNames(tenant); // GH-90000
                assertThat(names).hasSize(opsPerTenant); // GH-90000
                for (int i = 0; i < opsPerTenant; i++) { // GH-90000
                    assertThat(secretProvider.getSecret(tenant, "key-" + i)) // GH-90000
                            .isPresent() // GH-90000
                            .contains(tenant + "-value-" + i); // GH-90000
                }
            }
        }
    }

    // =========================================================================
    // 6. End-to-End Secret Lifecycle
    // =========================================================================

    @Nested
    @DisplayName("End-to-End Lifecycle [GH-90000]")
    class EndToEndLifecycle {

        @Test
        @DisplayName("Full lifecycle: create, read, rotate, list, delete [GH-90000]")
        void fullLifecycle() { // GH-90000
            // Create
            secretProvider.putSecret(TENANT_A, "model-endpoint-key", "sk-initial-key"); // GH-90000
            secretProvider.putSecret(TENANT_A, "db-password", "p@ssw0rd"); // GH-90000

            // Read
            assertThat(secretProvider.getSecret(TENANT_A, "model-endpoint-key")) // GH-90000
                    .isPresent().contains("sk-initial-key [GH-90000]");

            // List
            assertThat(secretProvider.listSecretNames(TENANT_A)) // GH-90000
                    .containsExactlyInAnyOrder("model-endpoint-key", "db-password"); // GH-90000

            // Rotate
            RotationResult rotation = secretProvider.rotateSecret( // GH-90000
                    TENANT_A, "model-endpoint-key", "sk-rotated-key");
            assertThat(rotation.secretName()).isEqualTo("model-endpoint-key [GH-90000]");
            assertThat(secretProvider.getSecret(TENANT_A, "model-endpoint-key")) // GH-90000
                    .isPresent().contains("sk-rotated-key [GH-90000]");

            // Delete one
            secretProvider.deleteSecret(TENANT_A, "db-password"); // GH-90000
            assertThat(secretProvider.listSecretNames(TENANT_A)) // GH-90000
                    .containsExactly("model-endpoint-key [GH-90000]");

            // Delete last
            secretProvider.deleteSecret(TENANT_A, "model-endpoint-key"); // GH-90000
            assertThat(secretProvider.listSecretNames(TENANT_A)).isEmpty(); // GH-90000
            assertThat(secretProvider.size()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("Multi-tenant parallel lifecycle maintains full isolation [GH-90000]")
        void multiTenantParallelLifecycle() { // GH-90000
            // Tenant A: model credentials
            secretProvider.putSecret(TENANT_A, "openai-key", "sk-alpha"); // GH-90000
            secretProvider.putSecret(TENANT_A, "anthropic-key", "sk-anthropic-alpha"); // GH-90000

            // Tenant B: different credentials
            secretProvider.putSecret(TENANT_B, "openai-key", "sk-beta"); // GH-90000

            // Verify isolation
            assertThat(secretProvider.listSecretNames(TENANT_A)).hasSize(2); // GH-90000
            assertThat(secretProvider.listSecretNames(TENANT_B)).hasSize(1); // GH-90000

            // Rotate tenant A — tenant B unaffected
            secretProvider.rotateSecret(TENANT_A, "openai-key", "sk-alpha-v2"); // GH-90000
            assertThat(secretProvider.getSecret(TENANT_A, "openai-key")) // GH-90000
                    .contains("sk-alpha-v2 [GH-90000]");
            assertThat(secretProvider.getSecret(TENANT_B, "openai-key")) // GH-90000
                    .contains("sk-beta [GH-90000]"); // unchanged

            // Delete all tenant A secrets
            secretProvider.deleteSecret(TENANT_A, "openai-key"); // GH-90000
            secretProvider.deleteSecret(TENANT_A, "anthropic-key"); // GH-90000

            // Tenant B still has its secret
            assertThat(secretProvider.listSecretNames(TENANT_A)).isEmpty(); // GH-90000
            assertThat(secretProvider.listSecretNames(TENANT_B)).containsExactly("openai-key [GH-90000]");
        }
    }
}
