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
 * Dedicated tests for plugin versioning and compatibility gate (plan section 10.4.5). // GH-90000
 *
 * <p>Covers both minimum ({@link PluginDescriptor#minPlatformVersion()}) and maximum // GH-90000
 * ({@link PluginDescriptor#maxPlatformVersion()}) platform version constraints, as well // GH-90000
 * as {@link IsolatingPluginSandbox#isCompatible} unit-level semantics.
 *
 * @doc.type class
 * @doc.purpose Tests for plugin platform version compatibility (10.4.5) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Plugin Versioning Tests (10.4.5)")
class PluginVersioningTest {

    // Minimal plugin contract for version-gate tests (no real class loading needed) // GH-90000
    interface MinimalPlugin {
        String ping(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isCompatible – unit-level SemVer comparison
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isCompatible() SemVer semantics")
    class IsCompatibleSemantics {

        @Test
        @DisplayName("platform version equal to min requirement → compatible")
        void equalVersionIsCompatible() { // GH-90000
            assertThat(IsolatingPluginSandbox.isCompatible("2.0.0", "2.0.0")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("platform major version higher → compatible")
        void higherMajorIsCompatible() { // GH-90000
            assertThat(IsolatingPluginSandbox.isCompatible("3.0.0", "2.0.0")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("platform minor version higher → compatible")
        void higherMinorIsCompatible() { // GH-90000
            assertThat(IsolatingPluginSandbox.isCompatible("2.5.0", "2.1.0")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("platform patch version higher → compatible")
        void higherPatchIsCompatible() { // GH-90000
            assertThat(IsolatingPluginSandbox.isCompatible("2.0.7", "2.0.3")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("platform major version lower → incompatible")
        void lowerMajorIsIncompatible() { // GH-90000
            assertThat(IsolatingPluginSandbox.isCompatible("1.9.9", "2.0.0")).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("blank min requirement → always compatible")
        void blankMinRequirementIsCompatible() { // GH-90000
            assertThat(IsolatingPluginSandbox.isCompatible("1.0.0", "")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("null min requirement → always compatible")
        void nullMinRequirementIsCompatible() { // GH-90000
            assertThat(IsolatingPluginSandbox.isCompatible("1.0.0", null)).isTrue(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Minimum version constraint
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Minimum platform version constraint")
    class MinVersionConstraint {

        @Test
        @DisplayName("plugin with minPlatformVersion == current platform → loads (version gate passes)")
        void pluginRequiringCurrentVersionPassesGate() { // GH-90000
            IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox("2.0.0");
            PluginDescriptor descriptor = new PluginDescriptor( // GH-90000
                    "exactly-current-plugin", "1.0.0", "2.0.0", null,
                    "com.example.NonExistent",
                    List.of(), // GH-90000
                    PermissionSet.empty()); // GH-90000

            // Version gate passes; ClassNotFoundException follows (no real JAR) // GH-90000
            assertThatThrownBy(() -> sandbox.loadPlugin(descriptor, MinimalPlugin.class)) // GH-90000
                    .isNotInstanceOf(PluginIncompatibleException.class) // GH-90000
                    .isInstanceOf(PluginLoadException.class); // GH-90000
        }

        @Test
        @DisplayName("plugin with minPlatformVersion > current platform → PluginIncompatibleException")
        void pluginRequiringNewerVersionThrows() { // GH-90000
            IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox("1.5.0");
            PluginDescriptor descriptor = new PluginDescriptor( // GH-90000
                    "future-plugin", "1.0.0", "2.0.0", null,
                    "com.example.FuturePlugin",
                    List.of(), // GH-90000
                    PermissionSet.empty()); // GH-90000

            assertThatThrownBy(() -> sandbox.loadPlugin(descriptor, MinimalPlugin.class)) // GH-90000
                    .isInstanceOf(PluginIncompatibleException.class) // GH-90000
                    .hasMessageContaining("future-plugin")
                    .hasMessageContaining("2.0.0");
        }

        @Test
        @DisplayName("plugin with no minPlatformVersion set → loads (version gate passes)")
        void pluginWithNoMinVersionPassesGate() { // GH-90000
            IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox("1.0.0");
            PluginDescriptor descriptor = PluginDescriptor.restrictedOf( // GH-90000
                    "no-min-plugin", "1.0.0", "",
                    "com.example.NoMin", List.of()); // GH-90000

            assertThatThrownBy(() -> sandbox.loadPlugin(descriptor, MinimalPlugin.class)) // GH-90000
                    .isNotInstanceOf(PluginIncompatibleException.class) // GH-90000
                    .isInstanceOf(PluginLoadException.class); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Maximum version constraint
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Maximum platform version constraint")
    class MaxVersionConstraint {

        @Test
        @DisplayName("plugin with maxPlatformVersion == current platform → gate passes")
        void pluginMaxEqualToCurrentPassesGate() { // GH-90000
            IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox("2.0.0");
            PluginDescriptor descriptor = new PluginDescriptor( // GH-90000
                    "max-equal-plugin", "1.0.0", "1.0.0", "2.0.0",
                    "com.example.MaxEqual",
                    List.of(), // GH-90000
                    PermissionSet.empty()); // GH-90000

            assertThatThrownBy(() -> sandbox.loadPlugin(descriptor, MinimalPlugin.class)) // GH-90000
                    .isNotInstanceOf(PluginIncompatibleException.class) // GH-90000
                    .isInstanceOf(PluginLoadException.class); // GH-90000
        }

        @Test
        @DisplayName("plugin with maxPlatformVersion < current platform → PluginIncompatibleException")
        void pluginExceedingMaxVersionThrows() { // GH-90000
            IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox("3.0.0");
            PluginDescriptor descriptor = new PluginDescriptor( // GH-90000
                    "legacy-plugin", "1.0.0", "1.0.0", "2.9.9",
                    "com.example.LegacyPlugin",
                    List.of(), // GH-90000
                    PermissionSet.empty()); // GH-90000

            assertThatThrownBy(() -> sandbox.loadPlugin(descriptor, MinimalPlugin.class)) // GH-90000
                    .isInstanceOf(PluginIncompatibleException.class) // GH-90000
                    .hasMessageContaining("legacy-plugin");
        }

        @Test
        @DisplayName("plugin with maxPlatformVersion=null → no upper bound applied")
        void nullMaxPlatformVersionIsUnbounded() { // GH-90000
            IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox("99.0.0");
            PluginDescriptor descriptor = new PluginDescriptor( // GH-90000
                    "unbounded-plugin", "1.0.0", "1.0.0", null,
                    "com.example.Unbounded",
                    List.of(), // GH-90000
                    PermissionSet.empty()); // GH-90000

            assertThatThrownBy(() -> sandbox.loadPlugin(descriptor, MinimalPlugin.class)) // GH-90000
                    .isNotInstanceOf(PluginIncompatibleException.class) // GH-90000
                    .isInstanceOf(PluginLoadException.class); // GH-90000
        }

        @Test
        @DisplayName("plugin with maxPlatformVersion=* → treated as no upper bound")
        void wildcardMaxPlatformVersionIsUnbounded() { // GH-90000
            IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox("99.0.0");
            PluginDescriptor descriptor = new PluginDescriptor( // GH-90000
                    "wildcard-max-plugin", "1.0.0", "1.0.0", "*",
                    "com.example.WildcardMax",
                    List.of(), // GH-90000
                    PermissionSet.empty()); // GH-90000

            assertThatThrownBy(() -> sandbox.loadPlugin(descriptor, MinimalPlugin.class)) // GH-90000
                    .isNotInstanceOf(PluginIncompatibleException.class) // GH-90000
                    .isInstanceOf(PluginLoadException.class); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PluginDescriptor model — factory and accessors
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PluginDescriptor model")
    class PluginDescriptorModel {

        @Test
        @DisplayName("restrictedOf() factory sets maxPlatformVersion to null")
        void restrictedOfHasNullMaxVersion() { // GH-90000
            PluginDescriptor desc = PluginDescriptor.restrictedOf( // GH-90000
                    "factory-plugin", "1.0.0", "1.0.0", "com.example.Main", List.of()); // GH-90000

            assertThat(desc.maxPlatformVersion()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("positional constructor preserves maxPlatformVersion")
        void positionalConstructorPreservesMax() { // GH-90000
            PluginDescriptor desc = new PluginDescriptor( // GH-90000
                    "my-plugin", "2.0.0", "1.5.0", "2.9.9",
                    "com.example.Main", List.of(), PermissionSet.empty()); // GH-90000

            assertThat(desc.maxPlatformVersion()).isEqualTo("2.9.9");
        }

        @Test
        @DisplayName("logId() returns id@version format")
        void logIdFormat() { // GH-90000
            PluginDescriptor desc = PluginDescriptor.restrictedOf( // GH-90000
                    "log-test", "3.1.4", "1.0.0", "com.example.LogTest", List.of()); // GH-90000

            assertThat(desc.logId()).isEqualTo("log-test@3.1.4");
        }
    }
}
