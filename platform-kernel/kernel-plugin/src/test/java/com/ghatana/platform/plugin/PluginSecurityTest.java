/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.plugin;

import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.plugin.impl.DefaultPluginContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security tests for the kernel plugin subsystem.
 *
 * <p>Verifies tenant boundary isolation, resource quota enforcement,
 * tier capability sandboxing, and secret access denial.
 *
 * @doc.type class
 * @doc.purpose Plugin security enforcement test suite
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Plugin Security")
class PluginSecurityTest extends EventloopTestBase {

    private PluginResourceEnforcer resourceEnforcer;
    private PluginTierEnforcer tierEnforcer;
    private PluginCapabilityVerifier capabilityVerifier;

    @BeforeEach
    void setUp() {
        resourceEnforcer = new PluginResourceEnforcer();
        tierEnforcer = new PluginTierEnforcer();
        capabilityVerifier = new PluginCapabilityVerifier();
    }

    // -----------------------------------------------------------------------
    // SEC-1: Tenant boundary isolation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("SEC-1: Tenant boundary isolation")
    class TenantBoundaryTests {

        @Test
        @DisplayName("SEC-1a: Plugin context scoped to tenant A cannot see tenant B's configuration")
        void pluginContextForTenantACannotSeeTenantBConfig() {
            PluginRegistry registryA = new PluginRegistry();
            PluginRegistry registryB = new PluginRegistry();

            TenantAConfig tenantASecret = new TenantAConfig();
            TenantBConfig tenantBSecret = new TenantBConfig();

            DefaultPluginContext ctxA = new DefaultPluginContext(
                    registryA, Map.of(TenantAConfig.class, tenantASecret));
            DefaultPluginContext ctxB = new DefaultPluginContext(
                    registryB, Map.of(TenantBConfig.class, tenantBSecret));

            // ctxA can retrieve its own config
            assertThat(ctxA.getConfig(TenantAConfig.class)).isSameAs(tenantASecret);

            // ctxA cannot retrieve tenantB's config type — different registry and configuration
            assertThat(ctxA.getConfig(TenantBConfig.class)).isNull();

            // ctxB cannot retrieve tenantA's config type
            assertThat(ctxB.getConfig(TenantAConfig.class)).isNull();
        }

        @Test
        @DisplayName("SEC-1b: Plugin registered in tenant A's registry is invisible to tenant B's context")
        void pluginInTenantARegistryIsInvisibleToTenantBContext() {
            PluginRegistry registryA = new PluginRegistry();
            PluginRegistry registryB = new PluginRegistry();

            registryA.register(new NamedPlugin("tenant-a-private-plugin"));

            DefaultPluginContext ctxB = new DefaultPluginContext(registryB, Map.of());

            // Tenant B context should not find a plugin that only lives in tenant A's registry
            assertThat(ctxB.<Plugin>findPlugin("tenant-a-private-plugin")).isEmpty();
        }

        @Test
        @DisplayName("SEC-1c: Two isolated registries maintain separate plugin state")
        void twoRegistriesMaintainSeparatePluginState() {
            PluginRegistry registryA = new PluginRegistry();
            PluginRegistry registryB = new PluginRegistry();

            registryA.register(new NamedPlugin("shared-id"));
            // tenant B has no such plugin

            assertThat(registryA.isRegistered("shared-id")).isTrue();
            assertThat(registryB.isRegistered("shared-id")).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // SEC-2: Resource quota enforcement
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("SEC-2: Resource quota enforcement")
    class ResourceQuotaTests {

        @Test
        @DisplayName("SEC-2a: Quota with null reference is rejected")
        void nullQuotaIsRejected() {
            assertThatThrownBy(() -> resourceEnforcer.validateQuotas(null))
                    .isInstanceOf(PluginResourceException.class)
                    .hasMessageContaining("null");
        }

        @Test
        @DisplayName("SEC-2b: Memory quota exceeding T1 tier limit is rejected")
        void memoryExceedingT1TierLimitIsRejected() {
            // T1 default max is 64 MB; request 65 MB
            PluginResourceQuota oversized = PluginResourceQuota.builder()
                    .tier(PluginTier.T1)
                    .maxMemoryMB(65)
                    .maxCpuPercent(5)
                    .maxFileDescriptors(10)
                    .build();

            assertThatThrownBy(() -> resourceEnforcer.validateQuotas(oversized))
                    .isInstanceOf(PluginResourceException.class)
                    .hasMessageContaining("memory");
        }

        @Test
        @DisplayName("SEC-2c: Zero memory quota is rejected")
        void zeroMemoryIsRejected() {
            PluginResourceQuota quota = PluginResourceQuota.builder()
                    .tier(PluginTier.T2)
                    .maxMemoryMB(0)
                    .maxCpuPercent(10)
                    .maxFileDescriptors(20)
                    .build();

            assertThatThrownBy(() -> resourceEnforcer.validateQuotas(quota))
                    .isInstanceOf(PluginResourceException.class);
        }

        @Test
        @DisplayName("SEC-2d: Valid T2 quotas pass validation")
        void validT2QuotasPassValidation() throws Exception {
            PluginResourceQuota validQuota = PluginResourceQuota.defaults(PluginTier.T2);

            // Must not throw
            resourceEnforcer.validateQuotas(validQuota);
        }

        @Test
        @DisplayName("SEC-2e: CPU quota exceeding tier limit is rejected")
        void cpuQuotaExceedingLimitIsRejected() {
            // T1 max CPU is 5%; requesting 100% is a policy violation
            PluginResourceQuota oversizedCpu = PluginResourceQuota.builder()
                    .tier(PluginTier.T1)
                    .maxMemoryMB(32)
                    .maxCpuPercent(100)
                    .maxFileDescriptors(10)
                    .build();

            assertThatThrownBy(() -> resourceEnforcer.validateQuotas(oversizedCpu))
                    .isInstanceOf(PluginResourceException.class)
                    .hasMessageContaining("CPU");
        }
    }

    // -----------------------------------------------------------------------
    // SEC-3: Tier escalation prevention (sandbox)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("SEC-3: Tier escalation prevention")
    class TierEscalationTests {

        @Test
        @DisplayName("SEC-3a: T1 plugin cannot access network capability")
        void t1PluginCannotAccessNetworkCapability() {
            assertThatThrownBy(() -> tierEnforcer.preventTierEscalation(PluginTier.T1, "network.access"))
                    .isInstanceOf(PluginTierViolationException.class)
                    .hasMessageContaining("network.access");
        }

        @Test
        @DisplayName("SEC-3b: T1 plugin cannot access file system capability")
        void t1PluginCannotAccessFileSystemCapability() {
            assertThatThrownBy(() -> tierEnforcer.preventTierEscalation(PluginTier.T1, "file.system"))
                    .isInstanceOf(PluginTierViolationException.class);
        }

        @Test
        @DisplayName("SEC-3c: T2 plugin cannot access network or process spawn capability")
        void t2PluginCannotAccessNetworkOrProcessSpawn() {
            assertThatThrownBy(() -> tierEnforcer.preventTierEscalation(PluginTier.T2, "network.access"))
                    .isInstanceOf(PluginTierViolationException.class);

            assertThatThrownBy(() -> tierEnforcer.preventTierEscalation(PluginTier.T2, "process.spawn"))
                    .isInstanceOf(PluginTierViolationException.class);
        }

        @Test
        @DisplayName("SEC-3d: T1 plugin can access config.read and config.write")
        void t1PluginCanAccessAllowedCapabilities() throws Exception {
            // Must not throw for allowed capabilities
            tierEnforcer.preventTierEscalation(PluginTier.T1, "config.read");
            tierEnforcer.preventTierEscalation(PluginTier.T1, "config.write");
        }

        @Test
        @DisplayName("SEC-3e: T3 plugin can access all capabilities")
        void t3PluginCanAccessAllCapabilities() throws Exception {
            // T3 is unrestricted
            tierEnforcer.preventTierEscalation(PluginTier.T3, "network.access");
            tierEnforcer.preventTierEscalation(PluginTier.T3, "file.system");
            tierEnforcer.preventTierEscalation(PluginTier.T3, "process.spawn");
        }

        @Test
        @DisplayName("SEC-3f: canAccessCapability reflects correct tier boundaries")
        void canAccessCapabilityReflectsTierBoundaries() {
            assertThat(tierEnforcer.canAccessCapability(PluginTier.T1, "config.read")).isTrue();
            assertThat(tierEnforcer.canAccessCapability(PluginTier.T1, "network.access")).isFalse();
            assertThat(tierEnforcer.canAccessCapability(PluginTier.T2, "script.execute")).isTrue();
            assertThat(tierEnforcer.canAccessCapability(PluginTier.T2, "network.access")).isFalse();
            assertThat(tierEnforcer.canAccessCapability(PluginTier.T3, "network.access")).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // SEC-4: Capability verifier rejects unauthorized declarations
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("SEC-4: Capability verification")
    class CapabilityVerificationTests {

        @Test
        @DisplayName("SEC-4a: Plugin declaring empty capabilities is rejected")
        void emptyCapabilitiesIsRejected() {
            assertThatThrownBy(() -> capabilityVerifier.verifyCapabilities(Set.of()))
                    .isInstanceOf(PluginCapabilityException.class)
                    .hasMessageContaining("at least one capability");
        }

        @Test
        @DisplayName("SEC-4b: Plugin declaring null capabilities is rejected")
        void nullCapabilitiesIsRejected() {
            assertThatThrownBy(() -> capabilityVerifier.verifyCapabilities(null))
                    .isInstanceOf(PluginCapabilityException.class);
        }

        @Test
        @DisplayName("SEC-4c: Plugin declaring valid T1 capabilities passes verification")
        void validT1CapabilitiesPassVerification() throws Exception {
            capabilityVerifier.verifyCapabilities(Set.of("config.read"));
        }

        @Test
        @DisplayName("SEC-4d: Plugin declaring unknown / unsupported capability is rejected")
        void unknownCapabilityIsRejected() {
            assertThatThrownBy(() -> capabilityVerifier.verifyCapabilities(Set.of("admin.root.bypass")))
                    .isInstanceOf(PluginCapabilityException.class)
                    .hasMessageContaining("Invalid capability");
        }
    }

    // -----------------------------------------------------------------------
    // SEC-5: Secret access denial via PluginContext tenant scoping
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("SEC-5: Secret access denial")
    class SecretAccessDenialTests {

        @Test
        @DisplayName("SEC-5a: Plugin cannot locate another tenant's plugin via interaction bus")
        void pluginCannotInteractWithAnotherTenantPlugin() {
            PluginRegistry registryA = new PluginRegistry();
            registryA.register(new NamedPlugin("secret-holder"));

            PluginRegistry registryB = new PluginRegistry();
            DefaultPluginContext ctxB = new DefaultPluginContext(registryB, Map.of());

            // Plugin in tenant B's context cannot find secret-holder from tenant A
            assertThat(ctxB.<Plugin>findPlugin("secret-holder")).isEmpty();
        }

        @Test
        @DisplayName("SEC-5b: Plugin interaction bus returns error for unknown plugin target")
        void interactionBusReturnsErrorForUnknownPlugin() {
            PluginRegistry registry = new PluginRegistry();
            DefaultPluginContext ctx = new DefaultPluginContext(registry, Map.of());

            // The bus rejects requests to plugins that are not registered
            assertThatThrownBy(() -> runPromise(() ->
                    ctx.getInteractionBus().request(
                            "nonexistent-plugin", "payload", String.class,
                            java.time.Duration.ofSeconds(1))))
                    .isInstanceOf(PluginCapabilityException.class)
                    .hasMessageContaining("not registered");
        }
    }

    // -----------------------------------------------------------------------
    // Helper types used only in this test class
    // -----------------------------------------------------------------------

    /** Marker config type for tenant A — not visible to tenant B contexts. */
    private static final class TenantAConfig {}

    /** Marker config type for tenant B — not visible to tenant A contexts. */
    private static final class TenantBConfig {}

    /**
     * Minimal plugin implementation used only in security tests.
     */
    private static final class NamedPlugin implements Plugin {
        private final PluginMetadata metadata;
        private PluginState state = PluginState.UNLOADED;

        NamedPlugin(String id) {
            this.metadata = PluginMetadata.builder()
                    .id(id)
                    .name(id)
                    .version("1.0.0")
                    .type(PluginType.CUSTOM)
                    .build();
        }

        @Override
        public @NotNull PluginMetadata metadata() { return metadata; }

        @Override
        public @NotNull PluginState getState() { return state; }

        @Override
        public @NotNull Promise<Void> initialize(@NotNull PluginContext context) {
            state = PluginState.INITIALIZED;
            return Promise.complete();
        }

        @Override
        public @NotNull Promise<Void> start() {
            state = PluginState.RUNNING;
            return Promise.complete();
        }

        @Override
        public @NotNull Promise<Void> stop() {
            state = PluginState.STOPPED;
            return Promise.complete();
        }

        @Override
        public @NotNull Promise<Void> shutdown() {
            state = PluginState.SHUTDOWN;
            return Promise.complete();
        }

        @Override
        public @NotNull Promise<HealthStatus> healthCheck() {
            return Promise.of(HealthStatus.ok());
        }
    }
}
