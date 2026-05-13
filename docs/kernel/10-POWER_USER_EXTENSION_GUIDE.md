# Power User Extension Guide

## Overview

This guide is for power users who want to extend Kernel with custom adapters, plugins, or lifecycle profiles.

## Custom Toolchain Adapters

Create a custom adapter by implementing the `ToolchainAdapter` interface:

```typescript
import { ToolchainAdapter, ToolchainAdapterContext, ToolchainPlanStep, ToolchainExecutionResult } from '@ghatana/kernel-toolchains';

export class CustomAdapter implements ToolchainAdapter {
  id = 'custom-adapter';
  supportedPhases = ['build'];
  supportedSurfaceTypes = ['backend-api'];

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    return [
      {
        step: 'custom-build',
        command: 'custom-build-command',
        description: 'Custom build step'
      }
    ];
  }

  async execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult> {
    // Execute custom build
    return {
      success: true,
      outputs: {}
    };
  }

  async validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult> {
    // Validate outputs
    return {
      valid: true,
      errors: []
    };
  }
}
```

Register your adapter in `config/toolchain-adapter-registry.json`:

```json
{
  "adapters": {
    "custom-adapter": {
      "id": "custom-adapter",
      "package": "@ghatana/kernel-toolchains",
      "implementation": "CustomAdapter",
      "supportedPhases": ["build"],
      "supportedSurfaceTypes": ["backend-api"]
    }
  }
}
```

## Custom Lifecycle Profiles

Create a custom lifecycle profile by extending the base profiles:

```yaml
# config/lifecycle/custom-profile.yaml
extends: standard-web-api-product
surfaces:
  backend-api:
    adapter: custom-adapter
  web:
    adapter: pnpm-vite-react
phases:
  dev:
    mode: parallel
  build:
    mode: sequential
    customSteps:
      - name: pre-build
        command: echo "Pre-build step"
      - name: post-build
        command: echo "Post-build step"
```

## Custom Plugins

Create a custom plugin by implementing lifecycle hooks:

```typescript
export class CustomPlugin {
  async onProductBuildCompleted(context: PluginContext) {
    // Execute custom logic after build
    console.log('Build completed for', context.productId);
  }

  async onProductDeployed(context: PluginContext) {
    // Execute custom logic after deployment
    console.log('Deployed', context.productId, 'to', context.environment);
  }
}
```

Register your plugin in `config/kernel-plugin-registry.json`:

```json
{
  "plugins": {
    "custom-plugin": {
      "kind": "platform-plugin",
      "capabilities": ["custom.capability"],
      "requiredRuntimeServices": ["tenant-context"],
      "lifecycleHooks": ["onProductBuildCompleted", "onProductDeployed"]
    }
  }
}
```

## Custom Deployment Targets

Add custom deployment targets in `config/deployment/deployment-targets.json`:

```json
{
  "targets": {
    "custom-env": {
      "type": "custom",
      "adapter": "custom-deployment-adapter",
      "configPath": "deploy/custom-config.yaml"
    }
  }
}
```

## Custom Governance Policies

Add custom governance policies:

```json
{
  "config/lifecycle/custom-promotion-policies.json": {
    "version": "1.0.0",
    "policies": {
      "custom-promotion": {
        "requirements": {
          "custom-check": true
        }
      }
    }
  }
}
```

## Best Practices

1. **Stay Product-Neutral**: Custom adapters and plugins must not import from `products/**`
2. **Follow Interfaces**: Implement the required interfaces correctly
3. **Register Properly**: Register all custom components in the appropriate registries
4. **Test Thoroughly**: Ensure custom components pass conformance checks
5. **Document**: Document custom components for other users
