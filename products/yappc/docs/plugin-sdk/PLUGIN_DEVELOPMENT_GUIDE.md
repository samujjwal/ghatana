# YAPPC Plugin Development Guide

> **Audience:** Developers who want to extend the YAPPC platform by writing custom plugins.  
> **Updated:** 2026-01-19  
> **Module:** `core/framework` — `com.ghatana.yappc.framework.api.plugin`

---

## Table of Contents

1. [What is a YAPPC Plugin?](#1-what-is-a-yappc-plugin)
2. [Plugin Contracts](#2-plugin-contracts)
3. [Platform Version Compatibility](#3-platform-version-compatibility)
4. [Permission Model](#4-permission-model)
5. [Packaging Your Plugin](#5-packaging-your-plugin)
6. [Loading a Plugin](#6-loading-a-plugin)
7. [Audit Logging](#7-audit-logging)
8. [Hot Reload](#8-hot-reload)
9. [Example: Maven POM Generator](#9-example-maven-pom-generator)
10. [Testing Your Plugin](#10-testing-your-plugin)
11. [FAQ & Troubleshooting](#11-faq--troubleshooting)

---

## 1. What is a YAPPC Plugin?

A **YAPPC Plugin** is a Java class, packaged as a JAR, that implements one of the plugin
contracts defined in `com.ghatana.yappc.framework.api.plugin`. Plugins run in an isolated
ClassLoader environment enforced by `IsolatingPluginSandbox` and cannot access resources
outside the permission set granted to them by the deploying tenant.

### Plugin lifecycle at a glance

```
Tenant configures PluginDescriptor (id, classpath, permissions, version bounds)
        ↓
IsolatingPluginSandbox.loadPlugin()
        ↓
  1. validateCompatibility()   → checks minPlatformVersion / maxPlatformVersion
  2. URLClassLoader isolation  → fresh class loader per plugin
  3. reflect.newInstance()     → public no-arg constructor required
  4. PermissionProxy.wrap()    → enforces PermissionSet at runtime
        ↓
Caller receives a permission-enforcing proxy of the plugin contract
```

---

## 2. Plugin Contracts

### `YappcPlugin` (root interface)

Every plugin must implement `YappcPlugin`:

```java
public interface YappcPlugin {
    /** Stable, human-readable plugin name. Used in catalog and audit records. */
    String getName();

    /** SemVer version string, e.g. "1.2.3". Must match PluginDescriptor.version(). */
    String getVersion();

    /** One-sentence description shown in the plugin catalog. */
    String getDescription();
}
```

### `BuildGeneratorPlugin` (specialisation)

Extend `BuildGeneratorPlugin` to participate in build-file generation:

```java
public interface BuildGeneratorPlugin extends YappcPlugin {
    /** Returns false to temporarily disable this plugin without removing it. */
    default boolean isEnabled() { return true; }

    /** Controls ordering when multiple plugins target the same build system. */
    default int getPriority(BuildSystemType buildSystemType) { return 0; }

    /** Advertises optional features this plugin supports. */
    default BuildGeneratorCapabilities getCapabilities() {
        return BuildGeneratorCapabilities.empty();
    }
}
```

---

## 3. Platform Version Compatibility

Declare the platform version range your plugin supports via `PluginDescriptor`:

| Field               | Description                                              | Required |
|---------------------|----------------------------------------------------------|----------|
| `minPlatformVersion`| Minimum YAPPC platform version (inclusive). SemVer.      | Yes      |
| `maxPlatformVersion`| Maximum YAPPC platform version (inclusive). `null`/`"*"` means no upper bound. | No |

When `IsolatingPluginSandbox.loadPlugin()` is called, `validateCompatibility()` checks:

- `platformVersion >= minPlatformVersion` — else throws `PluginIncompatibleException`
- `platformVersion <= maxPlatformVersion` — else throws `PluginIncompatibleException`

The current platform version is available as `PlatformVersion.CURRENT`.

```java
PluginDescriptor descriptor = new PluginDescriptor(
    "my-plugin", "1.0.0",
    /* minPlatformVersion */ "2.0.0",
    /* maxPlatformVersion */ "2.9.9",   // null = unbounded
    "com.example.MyPlugin",
    List.of(jarUrl),
    PermissionSet.empty());
```

---

## 4. Permission Model

All plugin invocations pass through a `PermissionProxy` that enforces a `PermissionSet`.

### `PermissionSet` — allow-list fields

| Field                  | Description                                         |
|------------------------|-----------------------------------------------------|
| `allowedNetworkHosts`  | Hostnames/URLs the plugin may call.                 |
| `allowedFilePaths`     | Absolute path prefixes the plugin may read/write.   |
| `allowedJavaPackages`  | Java package prefixes the plugin may access (informational). |

### Factory methods

```java
PermissionSet.empty()        // No permissions (safe default)
PermissionSet.unrestricted() // All permissions — use only in trusted environments
```

### Custom permissions

```java
PermissionSet perms = new PermissionSet(
    List.of("api.trusted-partner.com"),        // network
    List.of("/data/plugin-workdir/"),           // file paths
    List.of("com.example.sdk")                 // java packages
);
```

When the proxy detects a method argument that looks like a hostname or absolute path, it
checks the allow-list. Violations throw `SecurityException`.

---

## 5. Packaging Your Plugin

Your plugin must publish a JAR with:

1. A public, no-arg constructor on every entry-point class.
2. Dependencies either shaded into the JAR or listed in the classpath URLs of `PluginDescriptor`.
3. A `META-INF/services/com.ghatana.yappc.framework.api.plugin.YappcPlugin` file listing
   your entry-point class (ServiceLoader discovery, optional but recommended).

### Minimal `build.gradle.kts`

```kotlin
plugins { id("java-library") }

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }

dependencies {
    // Only the plugin API — do NOT depend on core/framework internals
    compileOnly(project(":core:framework"))
}

tasks.jar {
    manifest.attributes["Plugin-Id"]      = "my-plugin"
    manifest.attributes["Plugin-Version"] = "1.0.0"
}
```

---

## 6. Loading a Plugin

```java
// 1. Build the sandbox (once per platform lifecycle)
IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox(PlatformVersion.CURRENT);

// 2. Describe the plugin
URL jarUrl = new File("path/to/my-plugin.jar").toURI().toURL();
PluginDescriptor descriptor = PluginDescriptor.restrictedOf(
    "my-plugin", "1.0.0", "2.0.0",
    "com.example.MyPlugin",
    List.of(jarUrl));

// 3. Load — returns a permission-enforcing proxy
MyPluginContract plugin = sandbox.loadPlugin(descriptor, MyPluginContract.class);

// 4. Use normally
plugin.generate(projectDescriptor);
```

### With audit logging

```java
PluginAuditStore auditStore = new PluginAuditStore();
Consumer<Map<String, Object>> sink = auditStore.auditSinkFor("my-plugin", tenantId);

MyPluginContract plugin = sandbox.loadPluginWithAudit(descriptor, MyPluginContract.class, agentId, sink);
```

Audit records are retrievable via `auditStore.getRecords("my-plugin", tenantId)` or the
REST endpoint `GET /api/v1/plugins/{pluginId}/audit`.

### Hot reload

```java
HotReloadPluginRegistry registry = new HotReloadPluginRegistry(sandbox);
registry.register(descriptor, MyPluginContract.class);

// Later — swap to a new version without downtime
registry.reload("my-plugin");  // loads new descriptor atomically under write lock
```

---

## 7. Audit Logging

Every method invocation on a loaded plugin produces two audit records (via `PluginAuditInterceptor`):

| Record  | When                 | Key fields                                   |
|---------|----------------------|----------------------------------------------|
| BEFORE  | Before method call   | agentId, pluginId, action, phase=BEFORE, inputHash, timestamp |
| AFTER   | After method returns | + outputHash, durationMs, status=OK          |
| ERROR   | On exception         | + durationMs, status=ERROR                   |

`action` is derived from the called method name: `init*` → `INIT`, `generate*` → `GENERATE`,
otherwise the method name uppercased.

---

## 8. Hot Reload

The `HotReloadPluginRegistry` supports zero-downtime plugin swaps:

- **Read operations** (`get()`) acquire a shared read lock — highly concurrent.
- **`reload(pluginId)`** loads the new instance _outside_ the lock, then swaps under an
  exclusive write lock. In-flight calls complete before the lock is acquired.
- **File-based trigger:** `PluginJarReloadListener` monitors `plugins/*.jar` path changes
  (via `ConfigWatchService`) and triggers `registry.reload(pluginId)` automatically.

REST trigger (requires `ADMIN` persona):

```
POST /api/v1/plugins/{pluginId}/reload
```

---

## 9. Example: Maven POM Generator

See `products/yappc/examples/sample-build-generator-plugin/` for a complete working plugin.

The example demonstrates:

- Implementing `BuildGeneratorPlugin` with a public no-arg constructor
- Generating a Maven `pom.xml` from a `ProjectDescriptor`
- Declaring `minPlatformVersion` and `maxPlatformVersion` in the descriptor
- Running inside `IsolatingPluginSandbox` in an integration test

---

## 10. Testing Your Plugin

### Unit tests

Test your plugin class directly (no sandbox needed):

```java
MavenPomGeneratorPlugin plugin = new MavenPomGeneratorPlugin();
String pom = plugin.generatePom(new ProjectDescriptor("my-project", "My Project", Path.of("."), Map.of()));
assertThat(pom).contains("<artifactId>my-project</artifactId>");
```

### Sandbox integration test

```java
IsolatingPluginSandbox sandbox = new IsolatingPluginSandbox("2.0.0");
PluginDescriptor desc = PluginDescriptor.restrictedOf(
    "maven-pom-generator", "1.0.0", "2.0.0",
    MavenPomGeneratorPlugin.class.getName(), List.of());
BuildGeneratorPlugin plugin = sandbox.loadPlugin(desc, BuildGeneratorPlugin.class);
assertThat(plugin.isEnabled()).isTrue();
```

---

## 11. FAQ & Troubleshooting

**Q: My plugin throws `PluginIncompatibleException` on load.**  
A: Check that `minPlatformVersion` in your `PluginDescriptor` is <= `PlatformVersion.CURRENT`
and that `maxPlatformVersion` (if set) is >= `PlatformVersion.CURRENT`.

**Q: My plugin throws `SecurityException` when calling a method.**  
A: The `PermissionProxy` is blocking a hostname or file path argument. Add the target to
`PermissionSet.allowedNetworkHosts` or `allowedFilePaths`.

**Q: My plugin call throws `PluginTimeoutException`.**  
A: The call exceeded `ResourceBudget.maxWallMs`. Increase the budget or optimize the plugin.

**Q: `PluginLoadException: Plugin class not found: com.example.MyPlugin`.**  
A: Ensure the JAR URL in `PluginDescriptor.classpath()` points to the correct location
and the entry-point class name is spelled exactly right (case-sensitive).

**Q: Can I use Spring inside a plugin?**  
A: Plugins must have a public no-arg constructor and do not participate in the platform's
dependency injection context. Using Spring Boot inside a plugin is explicitly unsupported.
Use constructor injection or factory methods within your plugin JAR instead.
