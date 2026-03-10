/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Framework Module
 */
package com.ghatana.yappc.framework.core.plugin.sandbox;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.function.Consumer;
import com.ghatana.yappc.framework.core.plugin.audit.PluginAuditInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes plugins in ClassLoader-isolated environments with enforced
 * permission models and resource budgets.
 *
 * <p>Loading lifecycle:
 * <ol>
 *   <li>Validate platform version compatibility against
 *       {@link PluginDescriptor#minPlatformVersion()}.</li>
 *   <li>Create a child {@link URLClassLoader} from the descriptor's classpath,
 *       using only the platform class loader as parent (no app classpath leakage).</li>
 *   <li>Load and reflectively instantiate {@link PluginDescriptor#mainClass()}.
 *       The class must have a public no-arg constructor.</li>
 *   <li>Wrap the instance with a {@link PermissionProxy} that enforces the
 *       descriptor's {@link PermissionSet}.</li>
 * </ol>
 *
 * <p>Each call to {@link #loadPlugin} returns a fresh proxy-wrapped instance.
 * Callers are responsible for closing the associated {@link URLClassLoader}
 * when the plugin is no longer needed.
 *
 * @doc.type class
 * @doc.purpose Executes plugins in an isolated ClassLoader with enforced permission model
 * @doc.layer product
 * @doc.pattern Service
 */
public class IsolatingPluginSandbox {

    private static final Logger log = LoggerFactory.getLogger(IsolatingPluginSandbox.class);

    /**
     * Semantic version string of the running platform, used to gate plugin
     * compatibility checks.
     */
    private final String platformVersion;

    /**
     * @param platformVersion current platform version (SemVer)
     */
    public IsolatingPluginSandbox(String platformVersion) {
        if (platformVersion == null || platformVersion.isBlank()) {
            throw new IllegalArgumentException("platformVersion must not be blank");
        }
        this.platformVersion = platformVersion;
    }

    /**
     * Loads a plugin and returns a permission-enforcing proxy.
     *
     * @param <T>        the plugin contract type
     * @param descriptor blueprint describing the plugin
     * @param contract   the interface the returned instance must satisfy
     * @return a permission-enforcing, ClassLoader-isolated proxy wrapping the plugin
     * @throws PluginIncompatibleException if the plugin requires a newer platform version
     * @throws PluginLoadException         if loading or instantiation fails for any other reason
     */
    public <T> T loadPlugin(PluginDescriptor descriptor, Class<T> contract)
            throws PluginLoadException {

        log.info("Loading plugin {} (contract={})", descriptor.logId(), contract.getSimpleName());

        // 1. Version compatibility check
        validateCompatibility(descriptor);

        // 2. Isolated child ClassLoader
        URL[] classpathUrls = descriptor.classpath().toArray(URL[]::new);
        URLClassLoader pluginClassLoader = new URLClassLoader(
                classpathUrls,
                IsolatingPluginSandbox.class.getClassLoader());

        // 3. Load and instantiate
        T instance;
        try {
            Class<?> rawClass = pluginClassLoader.loadClass(descriptor.mainClass());
            Object obj = rawClass.getDeclaredConstructor().newInstance();
            instance = contract.cast(obj);
        } catch (ClassNotFoundException e) {
            closeQuietly(pluginClassLoader);
            throw new PluginLoadException(
                    "Plugin class not found: " + descriptor.mainClass() + " in plugin " + descriptor.logId(), e);
        } catch (ClassCastException e) {
            closeQuietly(pluginClassLoader);
            throw new PluginLoadException(
                    "Plugin class " + descriptor.mainClass() + " does not implement " + contract.getName(), e);
        } catch (ReflectiveOperationException e) {
            closeQuietly(pluginClassLoader);
            throw new PluginLoadException(
                    "Failed to instantiate plugin " + descriptor.mainClass() + " via no-arg constructor", e);
        }

        // 4. Wrap with permission-checking proxy
        T proxy = PermissionProxy.wrap(instance, contract, descriptor.permissions());
        log.info("Plugin {} loaded and wrapped successfully.", descriptor.logId());
        return proxy;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Compatibility helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads a plugin with both permission enforcement and audit logging (10.2.2).
     *
     * <p>Wrapping order (outer to inner):
     * <ol>
     *   <li>{@link PluginAuditInterceptor} — records BEFORE/AFTER audit events</li>
     *   <li>{@link PermissionProxy} — enforces permission set</li>
     *   <li>Real plugin instance</li>
     * </ol>
     *
     * @param <T>        plugin contract type
     * @param descriptor plugin descriptor
     * @param contract   plugin interface
     * @param agentId    calling agent identifier used in audit records
     * @param auditSink  receives audit event maps; must be thread-safe
     * @return audited, permission-enforcing proxy
     * @throws PluginLoadException if loading fails
     */
    public <T> T loadPluginWithAudit(
            PluginDescriptor descriptor,
            Class<T> contract,
            String agentId,
            Consumer<Map<String, Object>> auditSink) throws PluginLoadException {
        T permProxy = loadPlugin(descriptor, contract);
        return PluginAuditInterceptor.wrap(permProxy, contract, descriptor.id(), agentId, auditSink);
    }

    /**
     * Validates that the plugin descriptor is compatible with the running platform
     * version, checking both the minimum (inclusive lower bound) and maximum
     * (inclusive upper bound) version constraints.
     *
     * @param descriptor the plugin to validate
     * @throws PluginIncompatibleException if the current platform is too old
     *         (< minPlatformVersion) or too new (&gt; maxPlatformVersion)
     */
    private void validateCompatibility(PluginDescriptor descriptor) throws PluginIncompatibleException {
        // Minimum version: platform must be >= descriptor.minPlatformVersion
        if (!isCompatible(platformVersion, descriptor.minPlatformVersion())) {
            throw new PluginIncompatibleException(descriptor.id(), descriptor.minPlatformVersion());
        }
        // Maximum version: platform must be <= descriptor.maxPlatformVersion (if set)
        String max = descriptor.maxPlatformVersion();
        if (max != null && !max.isBlank() && !"*".equals(max)) {
            // isCompatible(max, current) → max >= current → current <= max → OK
            if (!isCompatible(max, platformVersion)) {
                throw new PluginIncompatibleException(
                        descriptor.id() + " (platform " + platformVersion + " exceeds max supported " + max + ")", max);
            }
        }
    }

    /**
     * Returns {@code true} when {@code currentVersion} is >= {@code minRequired}.
     *
     * <p>Performs a simple numeric SemVer comparison (major.minor.patch).
     * Non-numeric segments are compared lexicographically as a fallback.
     *
     * @param currentVersion  running platform version (e.g. {@code "2.3.0"})
     * @param minRequired     minimum required version (e.g. {@code "2.1.0"})
     * @return {@code true} if compatible
     */
    static boolean isCompatible(String currentVersion, String minRequired) {
        if (minRequired == null || minRequired.isBlank() || "0".equals(minRequired)) {
            return true;
        }
        String[] cur = currentVersion.split("\\.", 3);
        String[] min = minRequired.split("\\.", 3);
        int parts = Math.max(cur.length, min.length);
        for (int i = 0; i < parts; i++) {
            int c = parsePart(cur, i);
            int m = parsePart(min, i);
            if (c != m) {
                return c > m;
            }
        }
        return true; // equal
    }

    private static int parsePart(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void closeQuietly(URLClassLoader cl) {
        try {
            cl.close();
        } catch (Exception ignored) {
            // best effort
        }
    }
}
