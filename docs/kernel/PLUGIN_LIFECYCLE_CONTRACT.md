# Plugin Lifecycle Contract

## Overview

Kernel plugins are platform or product-level extensions that hook into product lifecycle events. Plugins remain product-neutral and are registered in the kernel plugin registry.

## Plugin Registry

Plugins are registered in `config/kernel-plugin-registry.json` with the following schema:

```json
{
  "version": "1.0.0",
  "plugins": {
    "plugin-id": {
      "kind": "platform-plugin" | "product-plugin",
      "capabilities": ["capability1", "capability2"],
      "requiredRuntimeServices": ["service1", "service2"],
      "lifecycleHooks": ["onProductBuildCompleted", "onProductDeployed"]
    }
  }
}
```

## Lifecycle Hooks

The following lifecycle hooks are available for plugin subscription:

- `onProductRegistered` - Triggered when a product is registered in the canonical registry
- `onProductBootstrapped` - Triggered when product scaffolding completes
- `onProductDevStarted` - Triggered when dev environment starts
- `onProductValidated` - Triggered when validation phase completes
- `onProductTested` - Triggered when test phase completes
- `onProductBuildStarted` - Triggered when build phase starts
- `onProductBuildCompleted` - Triggered when build phase completes
- `onProductPackaged` - Triggered when packaging completes
- `onProductDeployStarted` - Triggered when deployment starts
- `onProductDeployed` - Triggered when deployment completes
- `onProductVerified` - Triggered when verification completes
- `onProductPromoted` - Triggered when promotion between environments completes
- `onProductRolledBack` - Triggered when rollback completes
- `onProductRetired` - Triggered when product is retired

## Product Plugin Bindings

Products can declare plugin bindings in their manifests:

```json
{
  "pluginBindings": {
    "plugin-audit-trail": {
      "requiredHooks": ["onProductBuildCompleted", "onProductDeployed"],
      "runtimeRequired": true
    }
  }
}
```

## Runtime Services

Plugins can request the following runtime services:

- `tenant-context` - Tenant context for multi-tenancy
- `correlation-id` - Request correlation ID for tracing
- `product-config` - Product configuration access
- `artifact-store` - Artifact storage access
- `deployment-targets` - Deployment target information

## Conformance

Product plugin bindings are validated against the plugin registry:
- Referenced plugins must exist in the registry
- Required hooks must be supported by the plugin
- Runtime service dependencies must be satisfied

Missing plugin lifecycle bindings fail conformance checks.
