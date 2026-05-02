# ADR-025: Plugin API Ownership — kernel-plugin owns Plugin, PluginManifest, and Lifecycle Types

**Status:** Accepted  
**Date:** 2026-05-01  
**Deciders:** Platform Kernel Team  
**Relates to:** KERNEL_BOUNDARY_HARDENING_PLAN.md Phase 0.3

---

## Context

The platform-kernel composite build contains two modules that each carry plugin-related abstractions:

- **kernel-core** (`com.ghatana.kernel.plugin.*`): Contains `PluginManifest`, `PluginContext`, `KernelPlugin`, `KernelPluginManifest`, and related runtime/discovery types.
- **kernel-plugin** (`com.ghatana.platform.plugin.*`): Contains `Plugin`, `PluginManifest`, `PluginContext`, `PluginMetadata`, and the full lifecycle SPI that platform plugins extend.

This duplication violates the single-responsibility principle and makes it unclear which package platform plugin authors should depend on. Platform plugins (under `platform-plugins/`) consistently import from `com.ghatana.platform.plugin.*`, confirming that `kernel-plugin` is the de-facto API surface.

---

## Decision

**Option A is accepted**: `kernel-plugin` owns the platform plugin API. `kernel-core` owns runtime orchestration and kernel-internal plugin abstractions only.

Specifically:

| Concern | Canonical Home |
|---|---|
| `Plugin` interface (lifecycle SPI) | `com.ghatana.platform.plugin` in `kernel-plugin` |
| `PluginManifest` (platform, with config schema) | `com.ghatana.platform.plugin` in `kernel-plugin` |
| `PluginContext` (platform) | `com.ghatana.platform.plugin` in `kernel-plugin` |
| `PluginMetadata`, `PluginState`, `PluginType` | `com.ghatana.platform.plugin` in `kernel-plugin` |
| `KernelPluginManifest` (kernel-internal descriptor) | `com.ghatana.kernel.plugin` in `kernel-core` |
| `KernelPlugin`, `PluginLifecycleOrchestrator`, `PluginLoader` | `com.ghatana.kernel.plugin` in `kernel-core` |
| Plugin runtime management, injection, security | `com.ghatana.kernel.plugin.runtime` in `kernel-core` |

`com.ghatana.kernel.plugin.PluginManifest` is a kernel-internal descriptor and is distinct from the platform-facing `com.ghatana.platform.plugin.PluginManifest`. Both may coexist with different fields and purposes, provided there is zero ambiguity at usage sites.

---

## Consequences

### Positive

- Platform plugin authors have a single, stable dependency on `kernel-plugin`.
- `kernel-core` remains focused on runtime orchestration and kernel internal concerns.
- The config schema extension (Phase 2.4) is added to `com.ghatana.platform.plugin.PluginManifest` only.
- Architecture tests can enforce that `platform-plugins/` modules do not import from `com.ghatana.kernel.plugin.*`.

### Negative

- Migration effort: any code in `kernel-core` that references `com.ghatana.platform.plugin.*` must route through the declared dependency on `kernel-plugin`, or be moved.

---

## Alternatives Considered

**Option B: kernel-core owns plugin API, kernel-plugin merged/removed.** Rejected because:
- Platform plugins already use `com.ghatana.platform.plugin.*` universally.
- Moving the API into `kernel-core` would couple product plugin authoring to kernel internals.
- `kernel-plugin` provides a meaningful abstraction layer with its own lifecycle management.

---

## Compliance

- All `platform-plugins/*` modules must import plugin API exclusively from `com.ghatana.platform.plugin.*`.
- `kernel-core` must not import from `com.ghatana.platform.plugin.*` in production code.
- Architecture tests enforce this boundary (see `KernelArchitectureBoundaryTest`).
