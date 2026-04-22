/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
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
 * Integration test for the sample Maven POM generator plugin (plan sections 10.5.4, 10.5.5). // GH-90000
 *
 * <p>Verifies:
 * <ul>
 *   <li>The plugin runs correctly inside {@link IsolatingPluginSandbox}.</li>
 *   <li>The generated {@code pom.xml} is structurally valid and contains the correct field values.</li>
 *   <li>The plugin honours the {@link BuildGeneratorPlugin} contract (enabled, priority, capabilities).</li> // GH-90000
 * </ul>
 *
 * <p>Since the entry-point class is on the test classpath an empty classpath list is used,
 * which causes {@code URLClassLoader} to delegate to the parent class loader.
 *
 * @doc.type class
 * @doc.purpose Integration test: MavenPomGeneratorPlugin runs in sandbox, produces valid POM (10.5.5) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("MavenPomGeneratorPlugin Integration Tests (10.5.5) [GH-90000]")
class MavenPomGeneratorPluginIntegrationTest {

    private IsolatingPluginSandbox sandbox;
    private PluginDescriptor descriptor;

    @BeforeEach
    void setUp() { // GH-90000
        sandbox = new IsolatingPluginSandbox(PlatformVersion.CURRENT); // GH-90000
        // Empty classpath: plugin class loaded via parent ClassLoader (test classpath) // GH-90000
        descriptor = PluginDescriptor.restrictedOf( // GH-90000
                "maven-pom-generator", "1.0.0", "2.0.0",
                MavenPomGeneratorPlugin.class.getName(), // GH-90000
                List.of()); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sandbox loading
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Sandbox loading [GH-90000]")
    class SandboxLoading {

        @Test
        @DisplayName("plugin loads successfully in sandbox and is enabled [GH-90000]")
        void pluginLoadsInSandbox() throws PluginLoadException { // GH-90000
            BuildGeneratorPlugin plugin = sandbox.loadPlugin(descriptor, BuildGeneratorPlugin.class); // GH-90000
            assertThat(plugin.isEnabled()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("plugin name, version, and description are correct [GH-90000]")
        void pluginMetadataIsCorrect() throws PluginLoadException { // GH-90000
            BuildGeneratorPlugin plugin = sandbox.loadPlugin(descriptor, BuildGeneratorPlugin.class); // GH-90000
            assertThat(plugin.getName()).isEqualTo("maven-pom-generator [GH-90000]");
            assertThat(plugin.getVersion()).isEqualTo("1.0.0 [GH-90000]");
            assertThat(plugin.getDescription()).contains("Maven [GH-90000]").contains("pom.xml [GH-90000]");
        }

        @Test
        @DisplayName("plugin has correct priority for Maven build system [GH-90000]")
        void priorityIsCorrectForMaven() throws PluginLoadException { // GH-90000
            BuildGeneratorPlugin plugin = sandbox.loadPlugin(descriptor, BuildGeneratorPlugin.class); // GH-90000
            assertThat(plugin.getPriority(BuildSystemType.MAVEN)).isEqualTo(10); // GH-90000
        }

        @Test
        @DisplayName("plugin capabilities include maven-pom-generation [GH-90000]")
        void capabilitiesAdvertiseMavenPomGeneration() throws PluginLoadException { // GH-90000
            BuildGeneratorPlugin plugin = sandbox.loadPlugin(descriptor, BuildGeneratorPlugin.class); // GH-90000
            assertThat(plugin.getCapabilities().getSupportedFeatures()) // GH-90000
                    .contains("maven-pom-generation [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POM generation — valid output
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POM generation [GH-90000]")
    class PomGeneration {

        @Test
        @DisplayName("generates valid pom.xml with correct artifactId and groupId [GH-90000]")
        void generatesValidPomXml() { // GH-90000
            MavenPomGeneratorPlugin plugin = new MavenPomGeneratorPlugin(); // GH-90000
            ProjectDescriptor project = new ProjectDescriptor( // GH-90000
                    "my-project", "My Project",
                    Path.of(". [GH-90000]"),
                    Map.of("groupId", "com.example", "version", "1.0.0")); // GH-90000

            String pom = plugin.generatePom(project); // GH-90000

            assertThat(pom) // GH-90000
                    .contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>") // GH-90000
                    .contains("<modelVersion>4.0.0</modelVersion> [GH-90000]")
                    .contains("<groupId>com.example</groupId> [GH-90000]")
                    .contains("<artifactId>my-project</artifactId> [GH-90000]")
                    .contains("<version>1.0.0</version> [GH-90000]")
                    .contains("<name>My Project</name> [GH-90000]");
        }

        @Test
        @DisplayName("falls back to defaults when metadata is absent [GH-90000]")
        void usesDefaultsWhenMetadataAbsent() { // GH-90000
            MavenPomGeneratorPlugin plugin = new MavenPomGeneratorPlugin(); // GH-90000
            ProjectDescriptor project = new ProjectDescriptor( // GH-90000
                    "bare-project", "Bare Project", Path.of(". [GH-90000]"), Map.of());

            String pom = plugin.generatePom(project); // GH-90000

            assertThat(pom) // GH-90000
                    .contains("<groupId>com.ghatana.generated</groupId> [GH-90000]")
                    .contains("<version>0.1.0-SNAPSHOT</version> [GH-90000]")
                    .contains("<packaging>jar</packaging> [GH-90000]");
        }

        @Test
        @DisplayName("escapes XML special characters in project name [GH-90000]")
        void escapesXmlSpecialCharacters() { // GH-90000
            MavenPomGeneratorPlugin plugin = new MavenPomGeneratorPlugin(); // GH-90000
            ProjectDescriptor project = new ProjectDescriptor( // GH-90000
                    "escape-test", "My <Project> & More",
                    Path.of(". [GH-90000]"), Map.of());

            String pom = plugin.generatePom(project); // GH-90000

            assertThat(pom) // GH-90000
                    .contains("<name>My &lt;Project&gt; &amp; More</name> [GH-90000]")
                    .doesNotContain("<name>My <Project> [GH-90000]");
        }

        @Test
        @DisplayName("null project raises IllegalArgumentException [GH-90000]")
        void nullProjectRaisesException() { // GH-90000
            MavenPomGeneratorPlugin plugin = new MavenPomGeneratorPlugin(); // GH-90000
            assertThatThrownBy(() -> plugin.generatePom(null)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("generated POM contains Java 21 compiler settings [GH-90000]")
        void generatedPomContainsJava21Settings() { // GH-90000
            MavenPomGeneratorPlugin plugin = new MavenPomGeneratorPlugin(); // GH-90000
            ProjectDescriptor project = new ProjectDescriptor( // GH-90000
                    "java21-project", "Java 21 Project", Path.of(". [GH-90000]"), Map.of());

            String pom = plugin.generatePom(project); // GH-90000

            assertThat(pom) // GH-90000
                    .contains("<maven.compiler.source>21</maven.compiler.source> [GH-90000]")
                    .contains("<maven.compiler.target>21</maven.compiler.target> [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sandbox integration: runs in sandbox, produces valid POM
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Sandbox + POM generation (10.5.5 — end-to-end) [GH-90000]")
    class SandboxPomGeneration {

        @Test
        @DisplayName("plugin loaded in sandbox generates a valid pom.xml [GH-90000]")
        void pluginInSandboxGeneratesValidPom() throws PluginLoadException { // GH-90000
            // Load via sandbox (uses PermissionProxy + isolated ClassLoader) // GH-90000
            BuildGeneratorPlugin plugin = sandbox.loadPlugin(descriptor, BuildGeneratorPlugin.class); // GH-90000

            // Cast to concrete type to access generatePom() — permitted because class // GH-90000
            // is on the shared test classpath (no separate URLClassLoader in this test setup) // GH-90000
            MavenPomGeneratorPlugin concretePlugin = (MavenPomGeneratorPlugin) plugin; // GH-90000
            ProjectDescriptor project = new ProjectDescriptor( // GH-90000
                    "sandbox-project", "Sandbox Project",
                    Path.of(". [GH-90000]"),
                    Map.of("groupId", "com.ghatana.sandbox", "version", "2.5.0")); // GH-90000

            String pom = concretePlugin.generatePom(project); // GH-90000

            assertThat(pom) // GH-90000
                    .startsWith("<?xml [GH-90000]")
                    .contains("<groupId>com.ghatana.sandbox</groupId> [GH-90000]")
                    .contains("<artifactId>sandbox-project</artifactId> [GH-90000]")
                    .contains("<version>2.5.0</version> [GH-90000]")
                    .contains("</project> [GH-90000]");
        }

        @Test
        @DisplayName("sandbox with unrestricted permissions still generates correct POM [GH-90000]")
        void sandboxWithUnrestrictedPermissionsWorks() throws PluginLoadException { // GH-90000
            PluginDescriptor unrestrictedDescriptor = new PluginDescriptor( // GH-90000
                    "maven-pom-generator", "1.0.0", "2.0.0", null,
                    MavenPomGeneratorPlugin.class.getName(), // GH-90000
                    List.of(), // GH-90000
                    PermissionSet.unrestricted()); // GH-90000

            BuildGeneratorPlugin plugin = sandbox.loadPlugin(unrestrictedDescriptor, BuildGeneratorPlugin.class); // GH-90000
            assertThat(plugin.isEnabled()).isTrue(); // GH-90000
        }
    }
}
