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
 * Dedicated tests for plugin versioning and compatibility gate (plan section 10.4.5).
 *
 * <p>Covers both minimum ({@link PluginDescriptor#minPlatformVersion()}) and maximum
 * ({@link PluginDescriptor#maxPlatformVersion()}) platform version constraints, as well
 * as {@link IsolatingPluginSandbox#isCompatible} unit-level semantics.
 *
 * @doc.type class
 * @doc.purpose Tests for plugin platform version compatibility (10.4.5)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Plugin Versioning Tests (10.4.5)")
class PluginVersioningTest {

    // Minimal plugin contract for version-gate tests (no real class loading needed)
    interface MinimalPlugin {
        String ping();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isCompatible – unit-level SemVer comparison
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isCompatible() SemVer semantics")
    class IsCompatibleSemantics {

        @Test
        @DisplayName("platform version equal to min requirement → compatible")
        void equalVersionIsCompatible() {
            assertThat(IsolatingPluginSandbox.isCompatible("2.0.0", "2.0.0")).isTrue();
        }

        @Test
        @DisplayName("platform major version higher → compatible")
        void higherMajorIsCompatible() {
            assertThat(IsolatingPluginSandbox.isCompatible("3.0.0", "2.0.0")).isTrue();
        }

        @Test
        @DisplayName("platform minor version higher → compatible")
        void higherMinorIsCompatible() {
            assertThat(IsolatingPluginSandbox.isCompatible("2.5.0", "2.1.0")).isTrue();
        }

        @Test
        @DisplayName("platform patch version higher → compatible")
        void higherPatchIsCompatible() {
            assertThat(IsolatingPluginSandbox.isCompatible("2.0.7", "2.0.3")).isTrue();
        }

        @Test
        @DisplayName("platform major version lower → incompatible")
        void lowerMajorIsIncompatible() {
            assertThat(IsolatingPluginSandbox.isCompatible("1.9.9", "2.0.0")).isFalse();
        }

        @Test
        @DisplayName("blank min requirement → always compatible")
        void blankMinRequirementIsCompatible() {
            assertThat(IsolatingPluginSandbox.isCompatible("1.0.0", "")).isTrue();
        }

        @Test
        @DisplayName("null min requirement → always compatible")
        void nullMinRequirementIsCompatible() {
            assertThat(IsolatingPluginSandbox.isCompatible("1.0.0", null)).isTrue();
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
        void pluginRequiringCurrentVersionPassesGate() {
            IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox("2.0.0");
            PluginDescriptor descriptor = new PluginDescriptor(
                    "exactly-current-plugin", "1.0.0", "2.0.0", null,
                    "com.example.NonExistent",
                    List.of(),
                    PermissionSet.empty());

            // Version gate passes; ClassNotFoundException follows (no real JAR)
            assertThatThrownBy(() -> sandbox.loadPlugin(descriptor, MinimalPlugin.class))
                    .isNotInstanceOf(PluginIncompatibleException.class)
                    .isInstanceOf(PluginLoadException.class);
        }

        @Test
        @DisplayName("plugin with minPlatformVersion > current platform → PluginIncompatibleException")
        void pluginRequiringNewerVersionThrows() {
            IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox("1.5.0");
            PluginDescriptor descriptor = new PluginDescriptor(
                    "future-plugin", "1.0.0", "2.0.0", null,
                    "com.example.FuturePlugin",
                    List.of(),
                    PermissionSet.empty());

            assertThatThrownBy(() -> sandbox.loadPlugin(descriptor, MinimalPlugin.class))
                    .isInstanceOf(PluginIncompatibleException.class)
                    .hasMessageContaining("future-plugin")
                    .hasMessageContaining("2.0.0");
        }

        @Test
        @DisplayName("plugin with no minPlatformVersion set → loads (version gate passes)")
        void pluginWithNoMinVersionPassesGate() {
            IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox("1.0.0");
            PluginDescriptor descriptor = PluginDescriptor.restrictedOf(
                    "no-min-plugin", "1.0.0", "",
                    "com.example.NoMin", List.of());

            assertThatThrownBy(() -> sandbox.loadPlugin(descriptor, MinimalPlugin.class))
                    .isNotInstanceOf(PluginIncompatibleException.class)
                    .isInstanceOf(PluginLoadException.class);
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
        void pluginMaxEqualToCurrentPassesGate() {
            IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox("2.0.0");
            PluginDescriptor descriptor = new PluginDescriptor(
                    "max-equal-plugin", "1.0.0", "1.0.0", "2.0.0",
                    "com.example.MaxEqual",
                    List.of(),
                    PermissionSet.empty());

            assertThatThrownBy(() -> sandbox.loadPlugin(descriptor, MinimalPlugin.class))
                    .isNotInstanceOf(PluginIncompatibleException.class)
                    .isInstanceOf(PluginLoadException.class);
        }

        @Test
        @DisplayName("plugin with maxPlatformVersion < current platform → PluginIncompatibleException")
        void pluginExceedingMaxVersionThrows() {
            IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox("3.0.0");
            PluginDescriptor descriptor = new PluginDescriptor(
                    "legacy-plugin", "1.0.0", "1.0.0", "2.9.9",
                    "com.example.LegacyPlugin",
                    List.of(),
                    PermissionSet.empty());

            assertThatThrownBy(() -> sandbox.loadPlugin(descriptor, MinimalPlugin.class))
                    .isInstanceOf(PluginIncompatibleException.class)
                    .hasMessageContaining("legacy-plugin");
        }

        @Test
        @DisplayName("plugin with maxPlatformVersion=null → no upper bound applied")
        void nullMaxPlatformVersionIsUnbounded() {
            IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox("99.0.0");
            PluginDescriptor descriptor = new PluginDescriptor(
                    "unbounded-plugin", "1.0.0", "1.0.0", null,
                    "com.example.Unbounded",
                    List.of(),
                    PermissionSet.empty());

            assertThatThrownBy(() -> sandbox.loadPlugin(descriptor, MinimalPlugin.class))
                    .isNotInstanceOf(PluginIncompatibleException.class)
                    .isInstanceOf(PluginLoadException.class);
        }

        @Test
        @DisplayName("plugin with maxPlatformVersion=* → treated as no upper bound")
        void wildcardMaxPlatformVersionIsUnbounded() {
            IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox("99.0.0");
            PluginDescriptor descriptor = new PluginDescriptor(
                    "wildcard-max-plugin", "1.0.0", "1.0.0", "*",
                    "com.example.WildcardMax",
                    List.of(),
                    PermissionSet.empty());

            assertThatThrownBy(() -> sandbox.loadPlugin(descriptor, MinimalPlugin.class))
                    .isNotInstanceOf(PluginIncompatibleException.class)
                    .isInstanceOf(PluginLoadException.class);
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
        void restrictedOfHasNullMaxVersion() {
            PluginDescriptor desc = PluginDescriptor.restrictedOf(
                    "factory-plugin", "1.0.0", "1.0.0", "com.example.Main", List.of());

            assertThat(desc.maxPlatformVersion()).isNull();
        }

        @Test
        @DisplayName("positional constructor preserves maxPlatformVersion")
        void positionalConstructorPreservesMax() {
            PluginDescriptor desc = new PluginDescriptor(
                    "my-plugin", "2.0.0", "1.5.0", "2.9.9",
                    "com.example.Main", List.of(), PermissionSet.empty());

            assertThat(desc.maxPlatformVersion()).isEqualTo("2.9.9");
        }

        @Test
        @DisplayName("logId() returns id@version format")
        void logIdFormat() {
            PluginDescriptor desc = PluginDescriptor.restrictedOf(
                    "log-test", "3.1.4", "1.0.0", "com.example.LogTest", List.of());

            assertThat(desc.logId()).isEqualTo("log-test@3.1.4");
        }
    }
}
