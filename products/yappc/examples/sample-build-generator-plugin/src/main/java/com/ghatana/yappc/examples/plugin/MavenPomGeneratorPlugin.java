/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Example Plugin — Maven POM Generator
 */
package com.ghatana.yappc.examples.plugin;

import com.ghatana.yappc.framework.api.domain.BuildSystemType;
import com.ghatana.yappc.framework.api.domain.ProjectDescriptor;
import com.ghatana.yappc.framework.api.plugin.BuildGeneratorPlugin;
import java.util.Set;

/**
 * Example YAPPC plugin that generates a minimal Maven {@code pom.xml} from a
 * {@link ProjectDescriptor}.
 *
 * <p>This plugin is intentionally minimal — it demonstrates the required plugin contract,
 * public no-arg constructor, and XML string generation without external template engines.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox(PlatformVersion.CURRENT);
 * PluginDescriptor desc = PluginDescriptor.restrictedOf(
 *     "maven-pom-generator", "1.0.0", "2.0.0",
 *     MavenPomGeneratorPlugin.class.getName(), List.of());
 * BuildGeneratorPlugin plugin = sandbox.loadPlugin(desc, BuildGeneratorPlugin.class);
 * String pom = ((MavenPomGeneratorPlugin) plugin).generatePom(projectDescriptor);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Example plugin: generates Maven pom.xml from ProjectDescriptor (10.5.3/10.5.4)
 * @doc.layer product
 * @doc.pattern Plugin
 */
public class MavenPomGeneratorPlugin implements BuildGeneratorPlugin {

    private static final String PLUGIN_NAME    = "maven-pom-generator";
    private static final String PLUGIN_VERSION = "1.0.0";
    private static final String DEFAULT_GROUP  = "com.ghatana.generated";
    private static final String DEFAULT_PACKAGING = "jar";
    private static final String PLATFORM_MIN_VERSION = "2.0.0";

    /**
     * Public no-arg constructor required by {@code IsolatingPluginSandbox}.
     */
    public MavenPomGeneratorPlugin() {
        // no-op — required for reflective instantiation
    }

    // ─────────────────────────────────────────────────────────────────────────
    // YappcPlugin
    // ─────────────────────────────────────────────────────────────────────────

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    /** {@inheritDoc} */
    @Override
    public String getVersion() {
        return PLUGIN_VERSION;
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "Generates a minimal Maven pom.xml from a ProjectDescriptor.";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BuildGeneratorPlugin
    // ─────────────────────────────────────────────────────────────────────────

    /** {@inheritDoc} */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * Maven targets are prioritised slightly lower than Gradle (default 0) to allow
     * multi-build-system projects to prefer Gradle when both plugins are loaded.
     *
     * @param buildSystemType the build system being targeted
     * @return priority: {@code 10} for Maven targets, {@code 0} otherwise
     */
    @Override
    public int getPriority(BuildSystemType buildSystemType) {
        return BuildSystemType.MAVEN.equals(buildSystemType) ? 10 : 0;
    }

    /** {@inheritDoc} */
    @Override
    public BuildGeneratorCapabilities getCapabilities() {
        return new BuildGeneratorCapabilities(Set.of("maven-pom-generation", "dependency-management"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Domain logic
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a minimal Maven {@code pom.xml} for the supplied project.
     *
     * <p>The generated POM includes:
     * <ul>
     *   <li>{@code groupId} — from {@code ProjectDescriptor.metadata["groupId"]}, falling back to
     *       {@value #DEFAULT_GROUP}</li>
     *   <li>{@code artifactId} — from {@code ProjectDescriptor.getId()}</li>
     *   <li>{@code version} — from {@code ProjectDescriptor.metadata["version"]}, falling back to
     *       {@code "0.1.0-SNAPSHOT"}</li>
     *   <li>{@code packaging} — from {@code ProjectDescriptor.metadata["packaging"]}, falling back
     *       to {@value #DEFAULT_PACKAGING}</li>
     *   <li>{@code name} — from {@link ProjectDescriptor#getName()}</li>
     * </ul>
     *
     * @param project the project descriptor to convert
     * @return a well-formed Maven {@code pom.xml} string
     * @throws IllegalArgumentException if {@code project} is {@code null}
     */
    public String generatePom(ProjectDescriptor project) {
        if (project == null) {
            throw new IllegalArgumentException("project must not be null");
        }

        String groupId   = metadataString(project, "groupId",   DEFAULT_GROUP);
        String version   = metadataString(project, "version",   "0.1.0-SNAPSHOT");
        String packaging = metadataString(project, "packaging", DEFAULT_PACKAGING);

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0"
                + " https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "  <modelVersion>4.0.0</modelVersion>\n"
                + "\n"
                + "  <groupId>" + escape(groupId) + "</groupId>\n"
                + "  <artifactId>" + escape(project.getId()) + "</artifactId>\n"
                + "  <version>" + escape(version) + "</version>\n"
                + "  <packaging>" + escape(packaging) + "</packaging>\n"
                + "\n"
                + "  <name>" + escape(project.getName()) + "</name>\n"
                + "\n"
                + "  <properties>\n"
                + "    <maven.compiler.source>21</maven.compiler.source>\n"
                + "    <maven.compiler.target>21</maven.compiler.target>\n"
                + "    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n"
                + "  </properties>\n"
                + "</project>\n";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String metadataString(ProjectDescriptor project, String key, String defaultValue) {
        Object value = project.getMetadata().get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        return defaultValue;
    }

    /** Minimal XML entity escaping for POM element content. */
    private static String escape(String raw) {
        if (raw == null) return "";
        return raw.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;");
    }
}
