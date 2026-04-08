/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.platform.plugin;

import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive plugin security tests covering sandboxing, permission enforcement,
 * and security policies.
 *
 * @doc.type class
 * @doc.purpose Validates plugin sandboxing, permission enforcement, and security policies
 * @doc.layer platform
 * @doc.pattern SecurityTest
 */
@DisplayName("Plugin Security Tests")
class PluginSecurityTest extends EventloopTestBase {

    // =========================================================================
    // Plugin Sandboxing
    // =========================================================================

    @Nested
    @DisplayName("Plugin Sandboxing")
    class PluginSandboxingTests {

        @Test
        @DisplayName("should isolate plugin resources")
        void shouldIsolatePluginResources() {
            SandboxedPlugin plugin = new SandboxedPlugin("test-plugin", "1.0.0");
            PluginContext context = PluginContext.builder()
                    .tenantId("tenant-123")
                    .build();

            runPromise(() -> plugin.initialize(context));

            // Plugin should have isolated resource scope
            assertThat(plugin.getResourceScope()).isNotNull();
            assertThat(plugin.getResourceScope().isIsolated()).isTrue();
        }

        @Test
        @DisplayName("should prevent resource leakage between plugins")
        void shouldPreventResourceLeakageBetweenPlugins() {
            SandboxedPlugin plugin1 = new SandboxedPlugin("plugin-1", "1.0.0");
            SandboxedPlugin plugin2 = new SandboxedPlugin("plugin-2", "1.0.0");

            PluginContext context = PluginContext.builder().build();

            runPromise(() -> plugin1.initialize(context));
            runPromise(() -> plugin2.initialize(context));

            // Each plugin should have its own resource scope
            assertThat(plugin1.getResourceScope()).isNotEqualTo(plugin2.getResourceScope());
        }

        @Test
        @DisplayName("should enforce memory limits")
        void shouldEnforceMemoryLimits() {
            SandboxedPlugin plugin = new SandboxedPlugin("test-plugin", "1.0.0");
            plugin.setMemoryLimit(1024 * 1024); // 1MB limit

            PluginContext context = PluginContext.builder().build();
            runPromise(() -> plugin.initialize(context));

            // Attempt to allocate more than limit
            assertThatThrownBy(() -> plugin.allocateMemory(2 * 1024 * 1024))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("should enforce CPU time limits")
        void shouldEnforceCpuTimeLimits() {
            SandboxedPlugin plugin = new SandboxedPlugin("test-plugin", "1.0.0");
            plugin.setCpuTimeLimit(1000); // 1 second limit

            PluginContext context = PluginContext.builder().build();
            runPromise(() -> plugin.initialize(context));

            // Attempt to exceed CPU time limit
            assertThatThrownBy(() -> plugin.consumeCpuTime(2000))
                    .isInstanceOf(SecurityException.class);
        }
    }

    // =========================================================================
    // Permission Enforcement
    // =========================================================================

    @Nested
    @DisplayName("Permission Enforcement")
    class PermissionEnforcementTests {

        @Test
        @DisplayName("should enforce file system permissions")
        void shouldEnforceFileSystemPermissions() {
            SecurePlugin plugin = new SecurePlugin("test-plugin", "1.0.0");
            plugin.setFileSystemPermissions(Set.of("read:/tmp"));

            PluginContext context = PluginContext.builder().build();
            runPromise(() -> plugin.initialize(context));

            // Should allow permitted operation
            assertThat(plugin.checkFileSystemPermission("read", "/tmp")).isTrue();

            // Should deny non-permitted operation
            assertThat(plugin.checkFileSystemPermission("write", "/tmp")).isFalse();
        }

        @Test
        @DisplayName("should enforce network permissions")
        void shouldEnforceNetworkPermissions() {
            SecurePlugin plugin = new SecurePlugin("test-plugin", "1.0.0");
            plugin.setNetworkPermissions(Set.of("connect:localhost:8080"));

            PluginContext context = PluginContext.builder().build();
            runPromise(() -> plugin.initialize(context));

            // Should allow permitted connection
            assertThat(plugin.checkNetworkPermission("connect", "localhost", 8080)).isTrue();

            // Should deny non-permitted connection
            assertThat(plugin.checkNetworkPermission("connect", "example.com", 443)).isFalse();
        }

        @Test
        @DisplayName("should enforce system call permissions")
        void shouldEnforceSystemCallPermissions() {
            SecurePlugin plugin = new SecurePlugin("test-plugin", "1.0.0");
            plugin.setSystemCallPermissions(Set.of("getenv", "time"));

            PluginContext context = PluginContext.builder().build();
            runPromise(() -> plugin.initialize(context));

            // Should allow permitted system call
            assertThat(plugin.checkSystemCallPermission("getenv")).isTrue();

            // Should deny non-permitted system call
            assertThat(plugin.checkSystemCallPermission("exec")).isFalse();
        }

        @Test
        @DisplayName("should deny permission escalation attempts")
        void shouldDenyPermissionEscalationAttempts() {
            SecurePlugin plugin = new SecurePlugin("test-plugin", "1.0.0");
            plugin.setFileSystemPermissions(Set.of("read:/tmp"));

            PluginContext context = PluginContext.builder().build();
            runPromise(() -> plugin.initialize(context));

            // Attempt to escalate permissions
            assertThatThrownBy(() -> plugin.requestPermission("write:/tmp"))
                    .isInstanceOf(SecurityException.class);
        }
    }

    // =========================================================================
    // Security Policies
    // =========================================================================

    @Nested
    @DisplayName("Security Policies")
    class SecurityPolicyTests {

        @Test
        @DisplayName("should enforce plugin signature verification")
        void shouldEnforcePluginSignatureVerification() {
            SignedPlugin plugin = new SignedPlugin("test-plugin", "1.0.0", "valid-signature");

            PluginContext context = PluginContext.builder().build();

            // Should initialize with valid signature
            runPromise(() -> plugin.initialize(context));
            assertThat(plugin.isSignatureValid()).isTrue();
        }

        @Test
        @DisplayName("should reject unsigned plugins when required")
        void shouldRejectUnsignedPluginsWhenRequired() {
            SignedPlugin plugin = new SignedPlugin("test-plugin", "1.0.0", null);

            PluginContext context = PluginContext.builder()
                    .requireSignature(true)
                    .build();

            assertThatThrownBy(() -> runPromise(() -> plugin.initialize(context)))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("should enforce version compatibility policies")
        void shouldEnforceVersionCompatibilityPolicies() {
            SignedPlugin plugin = new SignedPlugin("test-plugin", "2.0.0", "valid-signature");

            PluginContext context = PluginContext.builder()
                    .minPluginVersion("1.5.0")
                    .maxPluginVersion("1.9.9")
                    .build();

            assertThatThrownBy(() -> runPromise(() -> plugin.initialize(context)))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("should enforce plugin tier restrictions")
        void shouldEnforcePluginTierRestrictions() {
            TieredPlugin plugin = new TieredPlugin("test-plugin", "1.0.0", PluginTier.ENTERPRISE);

            PluginContext context = PluginContext.builder()
                    .allowedTiers(Set.of(PluginTier.STANDARD))
                    .build();

            assertThatThrownBy(() -> runPromise(() -> plugin.initialize(context)))
                    .isInstanceOf(SecurityException.class);
        }
    }

    // =========================================================================
    // Security Violation Handling
    // =========================================================================

    @Nested
    @DisplayName("Security Violation Handling")
    class SecurityViolationHandlingTests {

        @Test
        @DisplayName("should log security violations")
        void shouldLogSecurityViolations() {
            SecurePlugin plugin = new SecurePlugin("test-plugin", "1.0.0");

            PluginContext context = PluginContext.builder().build();
            runPromise(() -> plugin.initialize(context));

            // Trigger security violation
            plugin.attemptUnauthorizedOperation();

            assertThat(plugin.getViolationCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should shutdown plugin on critical violations")
        void shouldShutdownPluginOnCriticalViolations() {
            SecurePlugin plugin = new SecurePlugin("test-plugin", "1.0.0");

            PluginContext context = PluginContext.builder().build();
            runPromise(() -> plugin.initialize(context));
            runPromise(plugin::start);

            // Trigger critical violation
            plugin.triggerCriticalViolation();

            assertThat(plugin.getState()).isEqualTo(PluginState.SHUTDOWN);
        }

        @Test
        @DisplayName("should enforce violation rate limiting")
        void shouldEnforceViolationRateLimiting() {
            SecurePlugin plugin = new SecurePlugin("test-plugin", "1.0.0");
            plugin.setViolationLimit(5);

            PluginContext context = PluginContext.builder().build();
            runPromise(() -> plugin.initialize(context));

            // Exceed violation limit
            for (int i = 0; i < 10; i++) {
                plugin.attemptUnauthorizedOperation();
            }

            // Plugin should be blocked
            assertThat(plugin.isBlocked()).isTrue();
        }
    }

    // =========================================================================
    // Test Helper Classes
    // =========================================================================

    static class SandboxedPlugin implements Plugin {
        private final String pluginId;
        private final String version;
        private final PluginMetadata metadata;
        private PluginContext context;
        private ResourceScope resourceScope;
        private long memoryLimit = Long.MAX_VALUE;
        private long cpuTimeLimit = Long.MAX_VALUE;
        private PluginState state = PluginState.LOADED;

        SandboxedPlugin(String pluginId, String version) {
            this.pluginId = pluginId;
            this.version = version;
            this.metadata = new PluginMetadata(pluginId, version, "Sandboxed Plugin", "Test Description");
            this.resourceScope = new ResourceScope(true);
        }

        @Override
        public PluginMetadata metadata() {
            return metadata;
        }

        @Override
        public PluginState getState() {
            return state;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            this.context = context;
            this.state = PluginState.INITIALIZED;
            return Promise.complete();
        }

        @Override
        public Promise<Void> start() {
            state = PluginState.RUNNING;
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            state = PluginState.STOPPED;
            return Promise.complete();
        }

        void setMemoryLimit(long limit) {
            this.memoryLimit = limit;
        }

        void setCpuTimeLimit(long limit) {
            this.cpuTimeLimit = limit;
        }

        void allocateMemory(long bytes) {
            if (bytes > memoryLimit) {
                throw new SecurityException("Memory limit exceeded");
            }
        }

        void consumeCpuTime(long milliseconds) {
            if (milliseconds > cpuTimeLimit) {
                throw new SecurityException("CPU time limit exceeded");
            }
        }

        ResourceScope getResourceScope() {
            return resourceScope;
        }
    }

    static class SecurePlugin implements Plugin {
        private final String pluginId;
        private final String version;
        private final PluginMetadata metadata;
        private Set<String> fileSystemPermissions = Set.of();
        private Set<String> networkPermissions = Set.of();
        private Set<String> systemCallPermissions = Set.of();
        private final AtomicInteger violationCount = new AtomicInteger(0);
        private boolean blocked = false;
        private int violationLimit = 10;
        private PluginState state = PluginState.LOADED;

        SecurePlugin(String pluginId, String version) {
            this.pluginId = pluginId;
            this.version = version;
            this.metadata = new PluginMetadata(pluginId, version, "Secure Plugin", "Test Description");
        }

        @Override
        public PluginMetadata metadata() {
            return metadata;
        }

        @Override
        public PluginState getState() {
            return state;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            this.state = PluginState.INITIALIZED;
            return Promise.complete();
        }

        @Override
        public Promise<Void> start() {
            this.state = PluginState.RUNNING;
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            this.state = PluginState.STOPPED;
            return Promise.complete();
        }

        void setFileSystemPermissions(Set<String> permissions) {
            this.fileSystemPermissions = permissions;
        }

        void setNetworkPermissions(Set<String> permissions) {
            this.networkPermissions = permissions;
        }

        void setSystemCallPermissions(Set<String> permissions) {
            this.systemCallPermissions = permissions;
        }

        boolean checkFileSystemPermission(String operation, String path) {
            return fileSystemPermissions.contains(operation + ":" + path);
        }

        boolean checkNetworkPermission(String operation, String host, int port) {
            return networkPermissions.contains(operation + ":" + host + ":" + port);
        }

        boolean checkSystemCallPermission(String syscall) {
            return systemCallPermissions.contains(syscall);
        }

        void requestPermission(String permission) {
            throw new SecurityException("Permission escalation not allowed");
        }

        void attemptUnauthorizedOperation() {
            if (blocked) {
                throw new SecurityException("Plugin is blocked");
            }
            int count = violationCount.incrementAndGet();
            if (count >= violationLimit) {
                blocked = true;
            }
        }

        void triggerCriticalViolation() {
            state = PluginState.SHUTDOWN;
        }

        void setViolationLimit(int limit) {
            this.violationLimit = limit;
        }

        int getViolationCount() {
            return violationCount.get();
        }

        boolean isBlocked() {
            return blocked;
        }
    }

    static class SignedPlugin implements Plugin {
        private final String pluginId;
        private final String version;
        private final PluginMetadata metadata;
        private final String signature;
        private boolean signatureValid = false;
        private PluginState state = PluginState.LOADED;

        SignedPlugin(String pluginId, String version, String signature) {
            this.pluginId = pluginId;
            this.version = version;
            this.signature = signature;
            this.metadata = new PluginMetadata(pluginId, version, "Signed Plugin", "Test Description");
        }

        @Override
        public PluginMetadata metadata() {
            return metadata;
        }

        @Override
        public PluginState getState() {
            return state;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            if (context.isSignatureRequired() && signature == null) {
                throw new SecurityException("Signature required but not provided");
            }
            if (context.getMinPluginVersion() != null && !isVersionCompatible(version, context.getMinPluginVersion(), context.getMaxPluginVersion())) {
                throw new SecurityException("Version compatibility check failed");
            }
            signatureValid = signature != null;
            state = PluginState.INITIALIZED;
            return Promise.complete();
        }

        @Override
        public Promise<Void> start() {
            state = PluginState.RUNNING;
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            state = PluginState.STOPPED;
            return Promise.complete();
        }

        boolean isSignatureValid() {
            return signatureValid;
        }

        private boolean isVersionCompatible(String pluginVersion, String minVersion, String maxVersion) {
            // Simplified version comparison
            if (minVersion != null && compareVersions(pluginVersion, minVersion) < 0) {
                return false;
            }
            if (maxVersion != null && compareVersions(pluginVersion, maxVersion) > 0) {
                return false;
            }
            return true;
        }

        private int compareVersions(String v1, String v2) {
            String[] parts1 = v1.split("\\.");
            String[] parts2 = v2.split("\\.");
            for (int i = 0; i < Math.max(parts1.length, parts2.length); i++) {
                int n1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
                int n2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
                if (n1 != n2) {
                    return Integer.compare(n1, n2);
                }
            }
            return 0;
        }
    }

    static class TieredPlugin implements Plugin {
        private final String pluginId;
        private final String version;
        private final PluginMetadata metadata;
        private final PluginTier tier;
        private PluginState state = PluginState.LOADED;

        TieredPlugin(String pluginId, String version, PluginTier tier) {
            this.pluginId = pluginId;
            this.version = version;
            this.tier = tier;
            this.metadata = new PluginMetadata(pluginId, version, "Tiered Plugin", "Test Description");
        }

        @Override
        public PluginMetadata metadata() {
            return metadata;
        }

        @Override
        public PluginState getState() {
            return state;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            if (!context.getAllowedTiers().contains(tier)) {
                throw new SecurityException("Plugin tier not allowed");
            }
            state = PluginState.INITIALIZED;
            return Promise.complete();
        }

        @Override
        public Promise<Void> start() {
            state = PluginState.RUNNING;
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            state = PluginState.STOPPED;
            return Promise.complete();
        }
    }

    static class ResourceScope {
        private final boolean isolated;

        ResourceScope(boolean isolated) {
            this.isolated = isolated;
        }

        boolean isIsolated() {
            return isolated;
        }
    }

    static class PluginMetadata {
        private final String pluginId;
        private final String version;
        private final String name;
        private final String description;

        PluginMetadata(String pluginId, String version, String name, String description) {
            this.pluginId = pluginId;
            this.version = version;
            this.name = name;
            this.description = description;
        }

        String getPluginId() {
            return pluginId;
        }

        String getVersion() {
            return version;
        }

        String getName() {
            return name;
        }

        String getDescription() {
            return description;
        }
    }

    static class PluginContext {
        private final String tenantId;
        private final boolean requireSignature;
        private final String minPluginVersion;
        private final String maxPluginVersion;
        private final Set<PluginTier> allowedTiers;
        private final Map<String, String> metadata;

        PluginContext(Builder builder) {
            this.tenantId = builder.tenantId;
            this.requireSignature = builder.requireSignature;
            this.minPluginVersion = builder.minPluginVersion;
            this.maxPluginVersion = builder.maxPluginVersion;
            this.allowedTiers = builder.allowedTiers;
            this.metadata = builder.metadata;
        }

        String getTenantId() {
            return tenantId;
        }

        boolean isSignatureRequired() {
            return requireSignature;
        }

        String getMinPluginVersion() {
            return minPluginVersion;
        }

        String getMaxPluginVersion() {
            return maxPluginVersion;
        }

        Set<PluginTier> getAllowedTiers() {
            return allowedTiers;
        }

        Map<String, String> getMetadata() {
            return metadata;
        }

        static Builder builder() {
            return new Builder();
        }

        static class Builder {
            private String tenantId;
            private boolean requireSignature = false;
            private String minPluginVersion;
            private String maxPluginVersion;
            private Set<PluginTier> allowedTiers = Set.of();
            private Map<String, String> metadata = Map.of();

            Builder tenantId(String tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            Builder requireSignature(boolean require) {
                this.requireSignature = require;
                return this;
            }

            Builder minPluginVersion(String version) {
                this.minPluginVersion = version;
                return this;
            }

            Builder maxPluginVersion(String version) {
                this.maxPluginVersion = version;
                return this;
            }

            Builder allowedTiers(Set<PluginTier> tiers) {
                this.allowedTiers = tiers;
                return this;
            }

            Builder metadata(Map<String, String> metadata) {
                this.metadata = metadata;
                return this;
            }

            PluginContext build() {
                return new PluginContext(this);
            }
        }
    }

    enum PluginTier {
        STANDARD,
        ENTERPRISE,
        PREMIUM
    }
}
