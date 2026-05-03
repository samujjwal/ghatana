# ADR-003: Plugin API Ownership - kernel-core Retains PluginManifest

## Status

Accepted (with amendment)

## Context

The Ghatana platform has duplicate PluginManifest classes in two locations:
- `com.ghatana.kernel.plugin.PluginManifest` in kernel-core
- `com.ghatana.platform.plugin.PluginManifest` in kernel-plugin

This duplication creates ambiguity about which package owns the plugin API, complicates dependency management, and makes it difficult to add configuration schema validation in a single canonical location.

## Decision

**Option A (Amended):** kernel-core retains PluginManifest to avoid circular dependency. kernel-plugin owns the extended plugin framework (PluginTier, PluginResourceQuota, configSchema) but cannot move the base PluginManifest without creating a circular dependency (kernel-core → kernel-plugin → kernel-core).

### Rationale

1. **Circular Dependency Avoidance**: KernelPlugin is defined in kernel-core and references PluginManifest. Moving PluginManifest to kernel-plugin would require kernel-core to depend on kernel-plugin, while kernel-plugin already depends on kernel-core.

2. **Separation of Concerns**: The base PluginManifest (pluginId, version, capabilities, dependencies) belongs with KernelPlugin in kernel-core. Extended features (PluginTier, PluginResourceQuota, configSchema) can be added in kernel-plugin as a separate abstraction if needed.

3. **No Breaking Change**: Keeping PluginManifest in kernel-core maintains backward compatibility and avoids a large-scale migration.

## Consequences

### Positive

- Avoids circular dependency between kernel-core and kernel-plugin
- KernelPlugin and PluginManifest remain co-located (logical pairing)
- No breaking changes to existing code

### Negative

- PluginManifest cannot include configSchema without circular dependency
- Configuration schema validation must be handled differently (Phase 2.4 will need alternative approach)

### Migration Steps

1. Keep `com.ghatana.kernel.plugin.PluginManifest` in kernel-core (canonical location)
2. Remove `com.ghatana.platform.plugin.PluginManifest` from kernel-plugin (duplicate)
3. Keep all existing imports using kernel-core PluginManifest
4. For Phase 2.4 (config schema), consider:
   - Adding a separate ConfigSchema interface in kernel-core
   - Or moving config schema to a separate plugin-config module

## References

- Kernel Boundary Hardening Plan Phase 0.3
- Phase 2.4: Plugin API config schema (requires alternative approach)
