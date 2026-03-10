package com.ghatana.yappc.framework.api.plugin;

/**
 * Root contract for all YAPPC plugins.
 *
 * <p>Implement this interface (or a specialised sub-interface such as
 * {@link BuildGeneratorPlugin}) to contribute functionality to the YAPPC platform.
 * Implementations are discovered and loaded by the {@code IsolatingPluginSandbox}
 * from a separate {@link java.net.URLClassLoader}, so they execute in a
 * ClassLoader-isolated environment with an enforced {@code PermissionSet}.
 *
 * <h2>Minimal implementation</h2>
 * <pre>{@code
 * public class MyPlugin implements YappcPlugin {
 *     @Override public String getName()        { return "my-plugin"; }
 *     @Override public String getVersion()     { return "1.0.0"; }
 *     @Override public String getDescription() { return "Does something useful."; }
 * }
 * }</pre>
 *
 * <h2>Loading a plugin via the sandbox</h2>
 * <pre>{@code
 * PluginDescriptor descriptor = PluginDescriptor.restrictedOf(
 *     "my-plugin", "1.0.0", "2.0.0",
 *     "com.example.MyPlugin",
 *     List.of(new File("my-plugin.jar").toURI().toURL()));
 *
 * IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox(PlatformVersion.CURRENT);
 * YappcPlugin plugin = sandbox.loadPlugin(descriptor, YappcPlugin.class);
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Root contract for all YAPPC plugins
 * @doc.layer core
 * @doc.pattern Plugin
 * @see BuildGeneratorPlugin
 */
public interface YappcPlugin {

    /**
     * Returns the stable, human-readable name of this plugin.
     *
     * <p>The name is used for logging, audit records, and the plugin catalog
     * ({@code GET /api/v1/plugins}). It should be short, descriptive, and
     * consistent across versions (e.g. {@code "maven-pom-generator"}).
     *
     * @return plugin name; never {@code null} or blank
     * @doc.purpose Returns a stable, human-readable plugin name
     */
    String getName();

    /**
     * Returns the SemVer version string of this plugin (e.g. {@code "1.2.3"}).
     *
     * <p>This value must match the {@link com.ghatana.yappc.framework.core.plugin.sandbox.PluginDescriptor#version()}
     * used to load the plugin so that the audit trail stays consistent.
     *
     * @return SemVer version; never {@code null} or blank
     * @doc.purpose Returns the plugin's SemVer version string
     */
    String getVersion();

    /**
     * Returns a one-sentence human-readable description of this plugin's purpose.
     *
     * <p>Displayed in the plugin catalog endpoint and developer tooling.
     *
     * @return description; never {@code null}
     * @doc.purpose Returns a human-readable description of the plugin
     */
    String getDescription();
}
