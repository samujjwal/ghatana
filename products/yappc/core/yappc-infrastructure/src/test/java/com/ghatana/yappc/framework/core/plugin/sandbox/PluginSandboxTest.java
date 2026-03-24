/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Framework Module
 */
package com.ghatana.yappc.framework.core.plugin.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the plugin sandbox components (plan section 10.1.6).
 *
 * <p>Tests are intentionally isolated — no actual JAR loading is needed.
 * The {@link IsolatingPluginSandbox} version-gate, {@link PermissionProxy}
 * enforcement, and {@link ResourceBudget} timeout are each exercised in
 * isolation.
 *
 * @doc.type class
 * @doc.purpose Tests for IsolatingPluginSandbox, PermissionProxy, ResourceBudget (10.1.6)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Plugin Sandbox Tests (10.1.6)")
class PluginSandboxTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Shared test contracts (declared at top level to avoid name ambiguity)
    // ─────────────────────────────────────────────────────────────────────────

    /** Test plugin contract used by version-compatibility and permission tests. */
    interface Greeter {
        String greet(String name);
        String callHost(String host);
    }

    /** Test plugin contract for file operations. */
    interface FileReader {
        String read(String path);
    }

    /** Compliant greeter — no side effects. */
    static class TrustworthyGreeter implements Greeter {
        @Override
        public String greet(String name) {
            return "Hello, " + name + "!";
        }

        @Override
        public String callHost(String host) {
            return "called:" + host;
        }
    }

    /** Passes paths through; permission proxy blocks disallowed paths. */
    static class PassthroughFileReader implements FileReader {
        @Override
        public String read(String path) {
            return "content-of:" + path;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10.1.6.a — Version compatibility gate
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Version compatibility")
    class VersionCompatibility {

        @Test
        @DisplayName("compatible platform version allows loading")
        void compatibleVersionPasses() {
            assertThat(IsolatingPluginSandbox.isCompatible("2.3.0", "2.1.0")).isTrue();
        }

        @Test
        @DisplayName("equal versions are compatible")
        void equalVersionPasses() {
            assertThat(IsolatingPluginSandbox.isCompatible("2.3.0", "2.3.0")).isTrue();
        }

        @Test
        @DisplayName("older platform rejects plugin requiring newer version")
        void incompatibleVersionRejected() {
            assertThat(IsolatingPluginSandbox.isCompatible("1.9.9", "2.0.0")).isFalse();
        }

        @Test
        @DisplayName("blank minPlatformVersion is always compatible")
        void blankMinVersionPasses() {
            assertThat(IsolatingPluginSandbox.isCompatible("1.0.0", "")).isTrue();
        }

        @Test
        @DisplayName("sandbox throws PluginIncompatibleException for older platform")
        void sandboxThrowsOnIncompatibleVersion() {
            IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox("1.0.0");
            PluginDescriptor descriptor = new PluginDescriptor(
                    "test-plugin", "1.0.0", "2.0.0", null,
                    "com.example.TestPlugin",
                    List.of(),
                    PermissionSet.empty());

            assertThatThrownBy(() -> sandbox.loadPlugin(descriptor, Greeter.class))
                    .isInstanceOf(PluginIncompatibleException.class)
                    .hasMessageContaining("test-plugin")
                    .hasMessageContaining("2.0.0");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10.1.6.b — PermissionProxy enforcement
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PermissionProxy enforcement")
    class PermissionProxyEnforcement {

        @Test
        @DisplayName("10.1.6.b.1 — compliant plugin with unrestricted permissions runs successfully")
        void compliantPluginRunsSuccessfully() {
            Greeter proxy = PermissionProxy.wrap(new TrustworthyGreeter(), Greeter.class, PermissionSet.unrestricted());
            assertThat(proxy.greet("world")).isEqualTo("Hello, world!");
        }

        @Test
        @DisplayName("10.1.6.b.2 — plugin accessing unauthorized network host → SecurityException")
        void unauthorizedNetworkHostRejected() {
            Greeter proxy = PermissionProxy.wrap(new TrustworthyGreeter(), Greeter.class, PermissionSet.empty());

            assertThatThrownBy(() -> proxy.callHost("api.external-service.com"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("api.external-service.com");
        }

        @Test
        @DisplayName("10.1.6.b.3 — explicitly allowed network host passes through without exception")
        void allowedNetworkHostPassesThrough() {
            PermissionSet permissions = new PermissionSet(
                    List.of("api.trusted-partner.com"), List.of(), List.of());
            Greeter proxy = PermissionProxy.wrap(new TrustworthyGreeter(), Greeter.class, permissions);

            assertThat(proxy.callHost("api.trusted-partner.com")).isEqualTo("called:api.trusted-partner.com");
        }

        @Test
        @DisplayName("10.1.6.b.4 — plugin accessing disallowed file path → SecurityException")
        void unauthorizedFilePathRejected() {
            FileReader proxy = PermissionProxy.wrap(new PassthroughFileReader(), FileReader.class, PermissionSet.empty());

            assertThatThrownBy(() -> proxy.read("/etc/passwd"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("/etc/passwd");
        }

        @Test
        @DisplayName("10.1.6.b.5 — non-interface contract throws IllegalArgumentException")
        void nonInterfaceContractThrows() {
            assertThatThrownBy(() -> PermissionProxy.wrap("instance", String.class, PermissionSet.empty()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("interface");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10.1.6.c — ResourceBudget timeout enforcement
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ResourceBudget timeout enforcement")
    class ResourceBudgetTimeoutEnforcement {

        @Test
        @DisplayName("10.1.6.c.1 — fast callable completes within budget")
        void fastCallableCompletesSuccessfully() throws Exception {
            ResourceBudget budget = new ResourceBudget("fast-plugin", 2000, ResourceBudget.UNLIMITED);
            String result = budget.execute(() -> "fast-result");
            assertThat(result).isEqualTo("fast-result");
        }

        @Test
        @DisplayName("10.1.6.c.2 — slow callable exceeds time budget → PluginTimeoutException")
        void slowCallableTimesOut() {
            ResourceBudget budget = new ResourceBudget("slow-plugin", 100, ResourceBudget.UNLIMITED);

            assertThatThrownBy(() -> budget.execute(() -> {
                Thread.sleep(5000); // Will be interrupted at 100ms
                return "should-never-return";
            }))
                    .isInstanceOf(PluginTimeoutException.class)
                    .hasMessageContaining("slow-plugin")
                    .hasMessageContaining("100");
        }

        @Test
        @DisplayName("10.1.6.c.3 — unlimited budget never times out for fast call")
        void unlimitedBudgetNeverTimesOut() throws Exception {
            ResourceBudget budget = ResourceBudget.unlimited("any-plugin");
            Integer result = budget.execute(() -> 42);
            assertThat(result).isEqualTo(42);
        }

        @Test
        @DisplayName("10.1.6.c.4 — callable exception propagates through budget")
        void callableExceptionPropagates() {
            ResourceBudget budget = new ResourceBudget("error-plugin", 5000, ResourceBudget.UNLIMITED);

            assertThatThrownBy(() -> budget.execute(() -> {
                throw new IllegalStateException("plugin-error");
            }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("plugin-error");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10.1.1–10.1.2 — PluginDescriptor & PermissionSet model tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PluginDescriptor and PermissionSet")
    class ModelTests {

        @Test
        @DisplayName("PluginDescriptor.restrictedOf creates empty PermissionSet")
        void restrictedOfHasEmptyPermissions() {
            PluginDescriptor descriptor = PluginDescriptor.restrictedOf(
                    "my-plugin", "1.0.0", "1.0.0", "com.example.Plugin", List.of());

            assertThat(descriptor.permissions().allowedNetworkHosts()).isEmpty();
            assertThat(descriptor.permissions().allowedFilePaths()).isEmpty();
            assertThat(descriptor.permissions().allowedJavaPackages()).isEmpty();
        }

        @Test
        @DisplayName("PermissionSet.unrestricted allows any host")
        void unrestrictedAllowsAnyHost() {
            assertThat(PermissionSet.unrestricted().isNetworkHostAllowed("anything.com")).isTrue();
        }

        @Test
        @DisplayName("PermissionSet.empty denies all access")
        void emptyDeniesAll() {
            PermissionSet empty = PermissionSet.empty();
            assertThat(empty.isNetworkHostAllowed("example.com")).isFalse();
            assertThat(empty.isFilePathAllowed("/tmp/data")).isFalse();
            assertThat(empty.isJavaPackageAllowed("java.nio.file")).isFalse();
        }

        @Test
        @DisplayName("PermissionSet.isFilePathAllowed uses prefix matching")
        void filePathPrefixMatching() {
            PermissionSet perm = new PermissionSet(List.of(), List.of("/var/data/"), List.of());
            assertThat(perm.isFilePathAllowed("/var/data/output.json")).isTrue();
            assertThat(perm.isFilePathAllowed("/etc/secret")).isFalse();
        }

        @Test
        @DisplayName("PluginDescriptor.logId includes id and version")
        void logIdContainsIdAndVersion() {
            PluginDescriptor d = PluginDescriptor.restrictedOf("my-plugin", "3.2.1", "1.0.0", "X", List.of());
            assertThat(d.logId()).isEqualTo("my-plugin@3.2.1");
        }
    }

}
