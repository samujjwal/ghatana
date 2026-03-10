/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Example Plugin — Integration Test
 */
package com.ghatana.yappc.examples.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.yappc.framework.api.domain.BuildSystemType;
import com.ghatana.yappc.framework.api.domain.ProjectDescriptor;
import com.ghatana.yappc.framework.api.plugin.BuildGeneratorPlugin;
import com.ghatana.yappc.framework.core.plugin.PlatformVersion;
import com.ghatana.yappc.framework.core.plugin.sandbox.IsolatingPluginSandbox;
import com.ghatana.yappc.framework.core.plugin.sandbox.PermissionSet;
import com.ghatana.yappc.framework.core.plugin.sandbox.PluginDescriptor;
import com.ghatana.yappc.framework.core.plugin.sandbox.PluginLoadException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration test for the sample Maven POM generator plugin (plan sections 10.5.4, 10.5.5).
 *
 * <p>Verifies:
 * <ul>
 *   <li>The plugin runs correctly inside {@link IsolatingPluginSandbox}.</li>
 *   <li>The generated {@code pom.xml} is structurally valid and contains the correct field values.</li>
 *   <li>The plugin honours the {@link BuildGeneratorPlugin} contract (enabled, priority, capabilities).</li>
 * </ul>
 *
 * <p>Since the entry-point class is on the test classpath an empty classpath list is used,
 * which causes {@code URLClassLoader} to delegate to the parent class loader.
 *
 * @doc.type class
 * @doc.purpose Integration test: MavenPomGeneratorPlugin runs in sandbox, produces valid POM (10.5.5)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("MavenPomGeneratorPlugin Integration Tests (10.5.5)")
class MavenPomGeneratorPluginIntegrationTest {

    private IsolatingPluginSandbox sandbox;
    private PluginDescriptor descriptor;

    @BeforeEach
    void setUp() {
        sandbox = new IsolatingPluginSandbox(PlatformVersion.CURRENT);
        // Empty classpath: plugin class loaded via parent ClassLoader (test classpath)
        descriptor = PluginDescriptor.restrictedOf(
                "maven-pom-generator", "1.0.0", "2.0.0",
                MavenPomGeneratorPlugin.class.getName(),
                List.of());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sandbox loading
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Sandbox loading")
    class SandboxLoading {

        @Test
        @DisplayName("plugin loads successfully in sandbox and is enabled")
        void pluginLoadsInSandbox() throws PluginLoadException {
            BuildGeneratorPlugin plugin = sandbox.loadPlugin(descriptor, BuildGeneratorPlugin.class);
            assertThat(plugin.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("plugin name, version, and description are correct")
        void pluginMetadataIsCorrect() throws PluginLoadException {
            BuildGeneratorPlugin plugin = sandbox.loadPlugin(descriptor, BuildGeneratorPlugin.class);
            assertThat(plugin.getName()).isEqualTo("maven-pom-generator");
            assertThat(plugin.getVersion()).isEqualTo("1.0.0");
            assertThat(plugin.getDescription()).contains("Maven").contains("pom.xml");
        }

        @Test
        @DisplayName("plugin has correct priority for Maven build system")
        void priorityIsCorrectForMaven() throws PluginLoadException {
            BuildGeneratorPlugin plugin = sandbox.loadPlugin(descriptor, BuildGeneratorPlugin.class);
            assertThat(plugin.getPriority(BuildSystemType.MAVEN)).isEqualTo(10);
        }

        @Test
        @DisplayName("plugin capabilities include maven-pom-generation")
        void capabilitiesAdvertiseMavenPomGeneration() throws PluginLoadException {
            BuildGeneratorPlugin plugin = sandbox.loadPlugin(descriptor, BuildGeneratorPlugin.class);
            assertThat(plugin.getCapabilities().getSupportedFeatures())
                    .contains("maven-pom-generation");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POM generation — valid output
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POM generation")
    class PomGeneration {

        @Test
        @DisplayName("generates valid pom.xml with correct artifactId and groupId")
        void generatesValidPomXml() {
            MavenPomGeneratorPlugin plugin = new MavenPomGeneratorPlugin();
            ProjectDescriptor project = new ProjectDescriptor(
                    "my-project", "My Project",
                    Path.of("."),
                    Map.of("groupId", "com.example", "version", "1.0.0"));

            String pom = plugin.generatePom(project);

            assertThat(pom)
                    .contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                    .contains("<modelVersion>4.0.0</modelVersion>")
                    .contains("<groupId>com.example</groupId>")
                    .contains("<artifactId>my-project</artifactId>")
                    .contains("<version>1.0.0</version>")
                    .contains("<name>My Project</name>");
        }

        @Test
        @DisplayName("falls back to defaults when metadata is absent")
        void usesDefaultsWhenMetadataAbsent() {
            MavenPomGeneratorPlugin plugin = new MavenPomGeneratorPlugin();
            ProjectDescriptor project = new ProjectDescriptor(
                    "bare-project", "Bare Project", Path.of("."), Map.of());

            String pom = plugin.generatePom(project);

            assertThat(pom)
                    .contains("<groupId>com.ghatana.generated</groupId>")
                    .contains("<version>0.1.0-SNAPSHOT</version>")
                    .contains("<packaging>jar</packaging>");
        }

        @Test
        @DisplayName("escapes XML special characters in project name")
        void escapesXmlSpecialCharacters() {
            MavenPomGeneratorPlugin plugin = new MavenPomGeneratorPlugin();
            ProjectDescriptor project = new ProjectDescriptor(
                    "escape-test", "My <Project> & More",
                    Path.of("."), Map.of());

            String pom = plugin.generatePom(project);

            assertThat(pom)
                    .contains("<name>My &lt;Project&gt; &amp; More</name>")
                    .doesNotContain("<name>My <Project>");
        }

        @Test
        @DisplayName("null project raises IllegalArgumentException")
        void nullProjectRaisesException() {
            MavenPomGeneratorPlugin plugin = new MavenPomGeneratorPlugin();
            assertThatThrownBy(() -> plugin.generatePom(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("generated POM contains Java 21 compiler settings")
        void generatedPomContainsJava21Settings() {
            MavenPomGeneratorPlugin plugin = new MavenPomGeneratorPlugin();
            ProjectDescriptor project = new ProjectDescriptor(
                    "java21-project", "Java 21 Project", Path.of("."), Map.of());

            String pom = plugin.generatePom(project);

            assertThat(pom)
                    .contains("<maven.compiler.source>21</maven.compiler.source>")
                    .contains("<maven.compiler.target>21</maven.compiler.target>");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sandbox integration: runs in sandbox, produces valid POM
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Sandbox + POM generation (10.5.5 — end-to-end)")
    class SandboxPomGeneration {

        @Test
        @DisplayName("plugin loaded in sandbox generates a valid pom.xml")
        void pluginInSandboxGeneratesValidPom() throws PluginLoadException {
            // Load via sandbox (uses PermissionProxy + isolated ClassLoader)
            BuildGeneratorPlugin plugin = sandbox.loadPlugin(descriptor, BuildGeneratorPlugin.class);

            // Cast to concrete type to access generatePom() — permitted because class
            // is on the shared test classpath (no separate URLClassLoader in this test setup)
            MavenPomGeneratorPlugin concretePlugin = (MavenPomGeneratorPlugin) plugin;
            ProjectDescriptor project = new ProjectDescriptor(
                    "sandbox-project", "Sandbox Project",
                    Path.of("."),
                    Map.of("groupId", "com.ghatana.sandbox", "version", "2.5.0"));

            String pom = concretePlugin.generatePom(project);

            assertThat(pom)
                    .startsWith("<?xml")
                    .contains("<groupId>com.ghatana.sandbox</groupId>")
                    .contains("<artifactId>sandbox-project</artifactId>")
                    .contains("<version>2.5.0</version>")
                    .contains("</project>");
        }

        @Test
        @DisplayName("sandbox with unrestricted permissions still generates correct POM")
        void sandboxWithUnrestrictedPermissionsWorks() throws PluginLoadException {
            PluginDescriptor unrestrictedDescriptor = new PluginDescriptor(
                    "maven-pom-generator", "1.0.0", "2.0.0", null,
                    MavenPomGeneratorPlugin.class.getName(),
                    List.of(),
                    PermissionSet.unrestricted());

            BuildGeneratorPlugin plugin = sandbox.loadPlugin(unrestrictedDescriptor, BuildGeneratorPlugin.class);
            assertThat(plugin.isEnabled()).isTrue();
        }
    }
}
