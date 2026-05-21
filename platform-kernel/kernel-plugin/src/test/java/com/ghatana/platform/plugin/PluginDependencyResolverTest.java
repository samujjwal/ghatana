package com.ghatana.platform.plugin;

import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.plugin.PluginManifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PluginDependencyResolver")
class PluginDependencyResolverTest {

    private final PluginDependencyResolver resolver = new PluginDependencyResolver();

    @Test
    @DisplayName("accepts acyclic plugin dependency graph")
    void acceptsAcyclicPluginDependencyGraph() {
        Map<String, PluginManifest> plugins = Map.of(
                "audit-plugin", plugin("audit-plugin"),
                "risk-plugin", plugin("risk-plugin", pluginDependency("audit-plugin", "*", false)));

        assertThatCode(() -> resolver.checkCircularDependencies(plugins)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects circular plugin dependency graph with cycle path")
    void rejectsCircularPluginDependencyGraph() {
        Map<String, PluginManifest> plugins = Map.of(
                "audit-plugin", plugin("audit-plugin", pluginDependency("risk-plugin", "*", false)),
                "risk-plugin", plugin("risk-plugin", pluginDependency("approval-plugin", "*", false)),
                "approval-plugin", plugin("approval-plugin", pluginDependency("audit-plugin", "*", false)));

        assertThatThrownBy(() -> resolver.checkCircularDependencies(plugins))
                .isInstanceOf(PluginDependencyException.class)
                .hasMessageContaining("Circular dependency detected")
                .hasMessageContaining("approval-plugin -> audit-plugin -> risk-plugin -> approval-plugin");
    }

    @Test
    @DisplayName("rejects missing required plugin dependency")
    void rejectsMissingRequiredPluginDependency() {
        Map<String, PluginManifest> plugins = Map.of(
                "risk-plugin", plugin("risk-plugin", pluginDependency("audit-plugin", "*", false)));

        assertThatThrownBy(() -> resolver.checkCircularDependencies(plugins))
                .isInstanceOf(PluginDependencyException.class)
                .hasMessageContaining("requires missing plugin dependency 'audit-plugin'");
    }

    @Test
    @DisplayName("allows missing optional plugin dependency")
    void allowsMissingOptionalPluginDependency() {
        Map<String, PluginManifest> plugins = Map.of(
                "risk-plugin", plugin("risk-plugin", pluginDependency("audit-plugin", "*", true)));

        assertThatCode(() -> resolver.checkCircularDependencies(plugins)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects plugin dependency version mismatch")
    void rejectsPluginDependencyVersionMismatch() {
        Map<String, PluginManifest> plugins = Map.of(
                "audit-plugin", plugin("audit-plugin", "2.0.0"),
                "risk-plugin", plugin("risk-plugin", pluginDependency("audit-plugin", "1.0.0", false)));

        assertThatThrownBy(() -> resolver.checkCircularDependencies(plugins))
                .isInstanceOf(PluginDependencyException.class)
                .hasMessageContaining("version '1.0.0' but found '2.0.0'");
    }

    @Test
    @DisplayName("rejects direct self dependency")
    void rejectsDirectSelfDependency() {
        PluginManifest manifest = plugin("risk-plugin", pluginDependency("risk-plugin", "*", false));

        assertThatThrownBy(() -> resolver.resolveDependencies(manifest))
                .isInstanceOf(PluginDependencyException.class)
                .hasMessageContaining("cannot depend on itself");
    }

    private static KernelDependency pluginDependency(String pluginId, String versionConstraint, boolean optional) {
        return new KernelDependency(
                pluginId,
                versionConstraint,
                KernelDependency.DependencyType.PLUGIN,
                optional);
    }

    private static PluginManifest plugin(String pluginId, KernelDependency... dependencies) {
        return plugin(pluginId, "1.0.0", dependencies);
    }

    private static PluginManifest plugin(String pluginId, String version, KernelDependency... dependencies) {
        PluginManifest.Builder builder = PluginManifest.builder()
                .pluginId(pluginId)
                .version(version);
        for (KernelDependency dependency : dependencies) {
            builder.dependency(dependency);
        }
        return builder.build();
    }
}
