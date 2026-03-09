/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
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
@DisplayName("SecretProvider")
class SecretProviderTest {

    private static final String TENANT_A = "tenant-alpha";
    private static final String TENANT_B = "tenant-beta";
    private static final String TENANT_C = "tenant-gamma";

    private InMemorySecretProvider secretProvider;

    @BeforeEach
    void setUp() {
        secretProvider = new InMemorySecretProvider();
    }

    // =========================================================================
    // 1. Basic CRUD
    // =========================================================================

    @Nested
    @DisplayName("Basic CRUD")
    class BasicCrud {

        @Test
        @DisplayName("putSecret and getSecret round-trip works")
        void putAndGet() {
            secretProvider.putSecret(TENANT_A, "api-key", "sk-12345");
            Optional<String> result = secretProvider.getSecret(TENANT_A, "api-key");

            assertThat(result).isPresent().contains("sk-12345");
        }

        @Test
        @DisplayName("getSecret for non-existent key returns empty")
        void getNonExistent() {
            assertThat(secretProvider.getSecret(TENANT_A, "no-such-key")).isEmpty();
        }

        @Test
        @DisplayName("putSecret overwrites existing value")
        void putOverwrites() {
            secretProvider.putSecret(TENANT_A, "api-key", "old-value");
            secretProvider.putSecret(TENANT_A, "api-key", "new-value");

            assertThat(secretProvider.getSecret(TENANT_A, "api-key"))
                    .isPresent().contains("new-value");
        }

        @Test
        @DisplayName("deleteSecret removes and returns true")
        void deleteRemovesAndReturnsTrue() {
            secretProvider.putSecret(TENANT_A, "api-key", "value");
            boolean deleted = secretProvider.deleteSecret(TENANT_A, "api-key");

            assertThat(deleted).isTrue();
            assertThat(secretProvider.getSecret(TENANT_A, "api-key")).isEmpty();
        }

        @Test
        @DisplayName("deleteSecret for non-existent returns false")
        void deleteNonExistentReturnsFalse() {
            assertThat(secretProvider.deleteSecret(TENANT_A, "no-such-key")).isFalse();
        }

        @Test
        @DisplayName("hasSecret detects existing and missing keys")
        void hasSecretDetection() {
            secretProvider.putSecret(TENANT_A, "exists", "value");

            assertThat(secretProvider.hasSecret(TENANT_A, "exists")).isTrue();
            assertThat(secretProvider.hasSecret(TENANT_A, "missing")).isFalse();
        }
    }

    // =========================================================================
    // 2. Tenant Isolation
    // =========================================================================

    @Nested
    @DisplayName("Tenant Isolation")
    class TenantIsolation {

        @Test
        @DisplayName("Secrets from tenant A are invisible to tenant B")
        void secretsInvisibleAcrossTenants() {
            secretProvider.putSecret(TENANT_A, "model-key", "sk-alpha-secret");

            assertThat(secretProvider.getSecret(TENANT_B, "model-key")).isEmpty();
        }

        @Test
        @DisplayName("Same secret name in different tenants holds different values")
        void sameNameDifferentValues() {
            secretProvider.putSecret(TENANT_A, "api-key", "alpha-key-123");
            secretProvider.putSecret(TENANT_B, "api-key", "beta-key-456");

            assertThat(secretProvider.getSecret(TENANT_A, "api-key"))
                    .isPresent().contains("alpha-key-123");
            assertThat(secretProvider.getSecret(TENANT_B, "api-key"))
                    .isPresent().contains("beta-key-456");
        }

        @Test
        @DisplayName("Deleting tenant A's secret does not affect tenant B")
        void deleteTenantADoesNotAffectB() {
            secretProvider.putSecret(TENANT_A, "shared-key", "value-a");
            secretProvider.putSecret(TENANT_B, "shared-key", "value-b");

            secretProvider.deleteSecret(TENANT_A, "shared-key");

            assertThat(secretProvider.getSecret(TENANT_A, "shared-key")).isEmpty();
            assertThat(secretProvider.getSecret(TENANT_B, "shared-key"))
                    .isPresent().contains("value-b");
        }

        @Test
        @DisplayName("listSecretNames only returns names for the specified tenant")
        void listSecretNamesTenantScoped() {
            secretProvider.putSecret(TENANT_A, "key-1", "v1");
            secretProvider.putSecret(TENANT_A, "key-2", "v2");
            secretProvider.putSecret(TENANT_B, "key-3", "v3");

            Set<String> tenantANames = secretProvider.listSecretNames(TENANT_A);
            Set<String> tenantBNames = secretProvider.listSecretNames(TENANT_B);
            Set<String> tenantCNames = secretProvider.listSecretNames(TENANT_C);

            assertThat(tenantANames).containsExactlyInAnyOrder("key-1", "key-2");
            assertThat(tenantBNames).containsExactly("key-3");
            assertThat(tenantCNames).isEmpty();
        }

        @Test
        @DisplayName("hasSecret is tenant-scoped")
        void hasSecretTenantScoped() {
            secretProvider.putSecret(TENANT_A, "my-secret", "value");

            assertThat(secretProvider.hasSecret(TENANT_A, "my-secret")).isTrue();
            assertThat(secretProvider.hasSecret(TENANT_B, "my-secret")).isFalse();
        }
    }

    // =========================================================================
    // 3. Secret Rotation
    // =========================================================================

    @Nested
    @DisplayName("Secret Rotation")
    class SecretRotation {

        @Test
        @DisplayName("Rotation replaces value and returns metadata")
        void rotationReplacesValue() {
            secretProvider.putSecret(TENANT_A, "api-key", "old-key");

            RotationResult result = secretProvider.rotateSecret(TENANT_A, "api-key", "new-key");

            assertThat(secretProvider.getSecret(TENANT_A, "api-key"))
                    .isPresent().contains("new-key");
            assertThat(result.tenantId()).isEqualTo(TENANT_A);
            assertThat(result.secretName()).isEqualTo("api-key");
            assertThat(result.previousVersionAt()).isNotNull();
            assertThat(result.newVersionAt()).isNotNull();
            assertThat(result.newVersionAt()).isAfterOrEqualTo(result.previousVersionAt());
        }

        @Test
        @DisplayName("Rotation of non-existent secret throws NoSuchElementException")
        void rotationNonExistentThrows() {
            assertThatThrownBy(() ->
                    secretProvider.rotateSecret(TENANT_A, "ghost-key", "value"))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("ghost-key")
                    .hasMessageContaining(TENANT_A);
        }

        @Test
        @DisplayName("Rotation for tenant A does not affect tenant B's same-name secret")
        void rotationTenantIsolated() {
            secretProvider.putSecret(TENANT_A, "shared-key", "alpha-v1");
            secretProvider.putSecret(TENANT_B, "shared-key", "beta-v1");

            secretProvider.rotateSecret(TENANT_A, "shared-key", "alpha-v2");

            assertThat(secretProvider.getSecret(TENANT_A, "shared-key"))
                    .isPresent().contains("alpha-v2");
            assertThat(secretProvider.getSecret(TENANT_B, "shared-key"))
                    .isPresent().contains("beta-v1"); // Unchanged
        }
    }

    // =========================================================================
    // 4. Input Validation
    // =========================================================================

    @Nested
    @DisplayName("Input Validation")
    class InputValidation {

        @Test
        @DisplayName("putSecret rejects null arguments")
        void putRejectsNulls() {
            assertThatThrownBy(() -> secretProvider.putSecret(null, "key", "value"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> secretProvider.putSecret(TENANT_A, null, "value"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> secretProvider.putSecret(TENANT_A, "key", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // 5. Concurrent Access
    // =========================================================================

    @Nested
    @DisplayName("Concurrent Access")
    class ConcurrentAccess {

        @Test
        @DisplayName("Concurrent put/get across tenants maintains isolation")
        void concurrentPutGetIsolation() throws Exception {
            int opsPerTenant = 30;
            ExecutorService executor = Executors.newFixedThreadPool(6);
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<?>> futures = new java.util.ArrayList<>();

            String[] tenants = {TENANT_A, TENANT_B, TENANT_C};
            for (String tenant : tenants) {
                for (int i = 0; i < opsPerTenant; i++) {
                    final int idx = i;
                    futures.add(executor.submit(() -> {
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        secretProvider.putSecret(tenant, "key-" + idx, tenant + "-value-" + idx);
                    }));
                }
            }

            latch.countDown();
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }

            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            // Verify isolation
            for (String tenant : tenants) {
                Set<String> names = secretProvider.listSecretNames(tenant);
                assertThat(names).hasSize(opsPerTenant);
                for (int i = 0; i < opsPerTenant; i++) {
                    assertThat(secretProvider.getSecret(tenant, "key-" + i))
                            .isPresent()
                            .contains(tenant + "-value-" + i);
                }
            }
        }
    }

    // =========================================================================
    // 6. End-to-End Secret Lifecycle
    // =========================================================================

    @Nested
    @DisplayName("End-to-End Lifecycle")
    class EndToEndLifecycle {

        @Test
        @DisplayName("Full lifecycle: create, read, rotate, list, delete")
        void fullLifecycle() {
            // Create
            secretProvider.putSecret(TENANT_A, "model-endpoint-key", "sk-initial-key");
            secretProvider.putSecret(TENANT_A, "db-password", "p@ssw0rd");

            // Read
            assertThat(secretProvider.getSecret(TENANT_A, "model-endpoint-key"))
                    .isPresent().contains("sk-initial-key");

            // List
            assertThat(secretProvider.listSecretNames(TENANT_A))
                    .containsExactlyInAnyOrder("model-endpoint-key", "db-password");

            // Rotate
            RotationResult rotation = secretProvider.rotateSecret(
                    TENANT_A, "model-endpoint-key", "sk-rotated-key");
            assertThat(rotation.secretName()).isEqualTo("model-endpoint-key");
            assertThat(secretProvider.getSecret(TENANT_A, "model-endpoint-key"))
                    .isPresent().contains("sk-rotated-key");

            // Delete one
            secretProvider.deleteSecret(TENANT_A, "db-password");
            assertThat(secretProvider.listSecretNames(TENANT_A))
                    .containsExactly("model-endpoint-key");

            // Delete last
            secretProvider.deleteSecret(TENANT_A, "model-endpoint-key");
            assertThat(secretProvider.listSecretNames(TENANT_A)).isEmpty();
            assertThat(secretProvider.size()).isZero();
        }

        @Test
        @DisplayName("Multi-tenant parallel lifecycle maintains full isolation")
        void multiTenantParallelLifecycle() {
            // Tenant A: model credentials
            secretProvider.putSecret(TENANT_A, "openai-key", "sk-alpha");
            secretProvider.putSecret(TENANT_A, "anthropic-key", "sk-anthropic-alpha");

            // Tenant B: different credentials
            secretProvider.putSecret(TENANT_B, "openai-key", "sk-beta");

            // Verify isolation
            assertThat(secretProvider.listSecretNames(TENANT_A)).hasSize(2);
            assertThat(secretProvider.listSecretNames(TENANT_B)).hasSize(1);

            // Rotate tenant A — tenant B unaffected
            secretProvider.rotateSecret(TENANT_A, "openai-key", "sk-alpha-v2");
            assertThat(secretProvider.getSecret(TENANT_A, "openai-key"))
                    .contains("sk-alpha-v2");
            assertThat(secretProvider.getSecret(TENANT_B, "openai-key"))
                    .contains("sk-beta"); // unchanged

            // Delete all tenant A secrets
            secretProvider.deleteSecret(TENANT_A, "openai-key");
            secretProvider.deleteSecret(TENANT_A, "anthropic-key");

            // Tenant B still has its secret
            assertThat(secretProvider.listSecretNames(TENANT_A)).isEmpty();
            assertThat(secretProvider.listSecretNames(TENANT_B)).containsExactly("openai-key");
        }
    }
}
