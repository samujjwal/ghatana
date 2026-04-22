/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
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
 * Unit tests for the plugin sandbox components (plan section 10.1.6). // GH-90000
 *
 * <p>Tests are intentionally isolated — no actual JAR loading is needed.
 * The {@link IsolatingPluginSandbox} version-gate, {@link PermissionProxy}
 * enforcement, and {@link ResourceBudget} timeout are each exercised in
 * isolation.
 *
 * @doc.type class
 * @doc.purpose Tests for IsolatingPluginSandbox, PermissionProxy, ResourceBudget (10.1.6) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Plugin Sandbox Tests (10.1.6) [GH-90000]")
class PluginSandboxTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Shared test contracts (declared at top level to avoid name ambiguity) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    /** Test plugin contract used by version-compatibility and permission tests. */
    interface Greeter {
        String greet(String name); // GH-90000
        String callHost(String host); // GH-90000
    }

    /** Test plugin contract for file operations. */
    interface FileReader {
        String read(String path); // GH-90000
    }

    /** Compliant greeter — no side effects. */
    static class TrustworthyGreeter implements Greeter {
        @Override
        public String greet(String name) { // GH-90000
            return "Hello, " + name + "!";
        }

        @Override
        public String callHost(String host) { // GH-90000
            return "called:" + host;
        }
    }

    /** Passes paths through; permission proxy blocks disallowed paths. */
    static class PassthroughFileReader implements FileReader {
        @Override
        public String read(String path) { // GH-90000
            return "content-of:" + path;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10.1.6.a — Version compatibility gate
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Version compatibility [GH-90000]")
    class VersionCompatibility {

        @Test
        @DisplayName("compatible platform version allows loading [GH-90000]")
        void compatibleVersionPasses() { // GH-90000
            assertThat(IsolatingPluginSandbox.isCompatible("2.3.0", "2.1.0")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("equal versions are compatible [GH-90000]")
        void equalVersionPasses() { // GH-90000
            assertThat(IsolatingPluginSandbox.isCompatible("2.3.0", "2.3.0")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("older platform rejects plugin requiring newer version [GH-90000]")
        void incompatibleVersionRejected() { // GH-90000
            assertThat(IsolatingPluginSandbox.isCompatible("1.9.9", "2.0.0")).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("blank minPlatformVersion is always compatible [GH-90000]")
        void blankMinVersionPasses() { // GH-90000
            assertThat(IsolatingPluginSandbox.isCompatible("1.0.0", "")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("sandbox throws PluginIncompatibleException for older platform [GH-90000]")
        void sandboxThrowsOnIncompatibleVersion() { // GH-90000
            IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox("1.0.0 [GH-90000]");
            PluginDescriptor descriptor = new PluginDescriptor( // GH-90000
                    "test-plugin", "1.0.0", "2.0.0", null,
                    "com.example.TestPlugin",
                    List.of(), // GH-90000
                    PermissionSet.empty()); // GH-90000

            assertThatThrownBy(() -> sandbox.loadPlugin(descriptor, Greeter.class)) // GH-90000
                    .isInstanceOf(PluginIncompatibleException.class) // GH-90000
                    .hasMessageContaining("test-plugin [GH-90000]")
                    .hasMessageContaining("2.0.0 [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10.1.6.b — PermissionProxy enforcement
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PermissionProxy enforcement [GH-90000]")
    class PermissionProxyEnforcement {

        @Test
        @DisplayName("10.1.6.b.1 — compliant plugin with unrestricted permissions runs successfully [GH-90000]")
        void compliantPluginRunsSuccessfully() { // GH-90000
            Greeter proxy = PermissionProxy.wrap(new TrustworthyGreeter(), Greeter.class, PermissionSet.unrestricted()); // GH-90000
            assertThat(proxy.greet("world [GH-90000]")).isEqualTo("Hello, world! [GH-90000]");
        }

        @Test
        @DisplayName("10.1.6.b.2 — plugin accessing unauthorized network host → SecurityException [GH-90000]")
        void unauthorizedNetworkHostRejected() { // GH-90000
            Greeter proxy = PermissionProxy.wrap(new TrustworthyGreeter(), Greeter.class, PermissionSet.empty()); // GH-90000

            assertThatThrownBy(() -> proxy.callHost("api.external-service.com [GH-90000]"))
                    .isInstanceOf(SecurityException.class) // GH-90000
                    .hasMessageContaining("api.external-service.com [GH-90000]");
        }

        @Test
        @DisplayName("10.1.6.b.3 — explicitly allowed network host passes through without exception [GH-90000]")
        void allowedNetworkHostPassesThrough() { // GH-90000
            PermissionSet permissions = new PermissionSet( // GH-90000
                    List.of("api.trusted-partner.com [GH-90000]"), List.of(), List.of());
            Greeter proxy = PermissionProxy.wrap(new TrustworthyGreeter(), Greeter.class, permissions); // GH-90000

            assertThat(proxy.callHost("api.trusted-partner.com [GH-90000]")).isEqualTo("called:api.trusted-partner.com [GH-90000]");
        }

        @Test
        @DisplayName("10.1.6.b.4 — plugin accessing disallowed file path → SecurityException [GH-90000]")
        void unauthorizedFilePathRejected() { // GH-90000
            FileReader proxy = PermissionProxy.wrap(new PassthroughFileReader(), FileReader.class, PermissionSet.empty()); // GH-90000

            assertThatThrownBy(() -> proxy.read("/etc/passwd [GH-90000]"))
                    .isInstanceOf(SecurityException.class) // GH-90000
                    .hasMessageContaining("/etc/passwd [GH-90000]");
        }

        @Test
        @DisplayName("10.1.6.b.5 — non-interface contract throws IllegalArgumentException [GH-90000]")
        void nonInterfaceContractThrows() { // GH-90000
            assertThatThrownBy(() -> PermissionProxy.wrap("instance", String.class, PermissionSet.empty())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("interface [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10.1.6.c — ResourceBudget timeout enforcement
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ResourceBudget timeout enforcement [GH-90000]")
    class ResourceBudgetTimeoutEnforcement {

        @Test
        @DisplayName("10.1.6.c.1 — fast callable completes within budget [GH-90000]")
        void fastCallableCompletesSuccessfully() throws Exception { // GH-90000
            ResourceBudget budget = new ResourceBudget("fast-plugin", 2000, ResourceBudget.UNLIMITED); // GH-90000
            String result = budget.execute(() -> "fast-result"); // GH-90000
            assertThat(result).isEqualTo("fast-result [GH-90000]");
        }

        @Test
        @DisplayName("10.1.6.c.2 — slow callable exceeds time budget → PluginTimeoutException [GH-90000]")
        void slowCallableTimesOut() { // GH-90000
            ResourceBudget budget = new ResourceBudget("slow-plugin", 100, ResourceBudget.UNLIMITED); // GH-90000

            assertThatThrownBy(() -> budget.execute(() -> { // GH-90000
                Thread.sleep(5000); // Will be interrupted at 100ms // GH-90000
                return "should-never-return";
            }))
                    .isInstanceOf(PluginTimeoutException.class) // GH-90000
                    .hasMessageContaining("slow-plugin [GH-90000]")
                    .hasMessageContaining("100 [GH-90000]");
        }

        @Test
        @DisplayName("10.1.6.c.3 — unlimited budget never times out for fast call [GH-90000]")
        void unlimitedBudgetNeverTimesOut() throws Exception { // GH-90000
            ResourceBudget budget = ResourceBudget.unlimited("any-plugin [GH-90000]");
            Integer result = budget.execute(() -> 42); // GH-90000
            assertThat(result).isEqualTo(42); // GH-90000
        }

        @Test
        @DisplayName("10.1.6.c.4 — callable exception propagates through budget [GH-90000]")
        void callableExceptionPropagates() { // GH-90000
            ResourceBudget budget = new ResourceBudget("error-plugin", 5000, ResourceBudget.UNLIMITED); // GH-90000

            assertThatThrownBy(() -> budget.execute(() -> { // GH-90000
                throw new IllegalStateException("plugin-error [GH-90000]");
            }))
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessage("plugin-error [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10.1.1–10.1.2 — PluginDescriptor & PermissionSet model tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PluginDescriptor and PermissionSet [GH-90000]")
    class ModelTests {

        @Test
        @DisplayName("PluginDescriptor.restrictedOf creates empty PermissionSet [GH-90000]")
        void restrictedOfHasEmptyPermissions() { // GH-90000
            PluginDescriptor descriptor = PluginDescriptor.restrictedOf( // GH-90000
                    "my-plugin", "1.0.0", "1.0.0", "com.example.Plugin", List.of()); // GH-90000

            assertThat(descriptor.permissions().allowedNetworkHosts()).isEmpty(); // GH-90000
            assertThat(descriptor.permissions().allowedFilePaths()).isEmpty(); // GH-90000
            assertThat(descriptor.permissions().allowedJavaPackages()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("PermissionSet.unrestricted allows any host [GH-90000]")
        void unrestrictedAllowsAnyHost() { // GH-90000
            assertThat(PermissionSet.unrestricted().isNetworkHostAllowed("anything.com [GH-90000]")).isTrue();
        }

        @Test
        @DisplayName("PermissionSet.empty denies all access [GH-90000]")
        void emptyDeniesAll() { // GH-90000
            PermissionSet empty = PermissionSet.empty(); // GH-90000
            assertThat(empty.isNetworkHostAllowed("example.com [GH-90000]")).isFalse();
            assertThat(empty.isFilePathAllowed("/tmp/data [GH-90000]")).isFalse();
            assertThat(empty.isJavaPackageAllowed("java.nio.file [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("PermissionSet.isFilePathAllowed uses prefix matching [GH-90000]")
        void filePathPrefixMatching() { // GH-90000
            PermissionSet perm = new PermissionSet(List.of(), List.of("/var/data/ [GH-90000]"), List.of());
            assertThat(perm.isFilePathAllowed("/var/data/output.json [GH-90000]")).isTrue();
            assertThat(perm.isFilePathAllowed("/etc/secret [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("PluginDescriptor.logId includes id and version [GH-90000]")
        void logIdContainsIdAndVersion() { // GH-90000
            PluginDescriptor d = PluginDescriptor.restrictedOf("my-plugin", "3.2.1", "1.0.0", "X", List.of()); // GH-90000
            assertThat(d.logId()).isEqualTo("my-plugin@3.2.1 [GH-90000]");
        }
    }

}
