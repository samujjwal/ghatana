# Product Power User Extension Guide

**Version:** 1.0.0
**Status:** Implementation-Ready
**Last Updated:** 2026-05-12

## Purpose

This guide explains how power users can extend Kernel's lifecycle platform by registering custom toolchain adapters, lifecycle profiles, deployment targets, and gates while still respecting Kernel's contracts, boundaries, and validation.

## Design Principles

1. **Extension, not bypass**: Power users can extend capabilities but cannot bypass Kernel's contracts.
2. **Explicit registration**: All extensions must be registered in Kernel's registries.
3. **Contract compliance**: Custom adapters must implement the ToolchainAdapter interface.
4. **Validation enforced**: Kernel validates all extensions before use.
5. **Observable**: All extensions must emit structured logs and metrics.

## Extension Points

### 1. Custom Toolchain Adapters

Power users can register custom toolchain adapters for tools not provided by Kernel (e.g., Bazel, Buck, custom build systems).

#### Register Adapter

Add to `config/toolchain-adapter-registry.json`:

```json
{
  "version": "1.0.0",
  "adapters": {
    "bazel-java-service": {
      "kind": "build-tool",
      "supportedSurfaceTypes": ["backend-api", "worker"],
      "supportedPhases": ["dev", "validate", "test", "build", "package"],
      "requires": ["bazelWorkspace", "bazelTarget"],
      "outputs": ["jvm-classes", "test-report", "coverage-report", "jar"],
      "implementation": "platform/typescript/kernel-toolchains/src/adapters/BazelJavaServiceAdapter.ts"
    }
  }
}
```

#### Implement Adapter

Create adapter in `platform/typescript/kernel-toolchains/src/adapters/`:

```typescript
import {
  ToolchainAdapter,
  ToolchainAdapterContext,
  ToolchainPlanStep,
  ToolchainExecutionResult,
  ToolchainOutputValidationResult,
  ProductLifecyclePhase,
  ProductSurfaceType
} from '@ghatana/kernel-lifecycle';

export class BazelJavaServiceAdapter implements ToolchainAdapter {
  readonly id = 'bazel-java-service';
  readonly supportedPhases: ProductLifecyclePhase[] = ['dev', 'validate', 'test', 'build', 'package'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['backend-api', 'worker'];

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const { phase, surfaceConfig } = context;
    const bazelWorkspace = surfaceConfig.bazelWorkspace as string;
    const bazelTarget = surfaceConfig.bazelTarget as string;

    if (!bazelWorkspace || !bazelTarget) {
      throw new Error('bazelWorkspace and bazelTarget are required for BazelJavaServiceAdapter');
    }

    const command = this.mapPhaseToBazelCommand(phase, bazelTarget);

    return [{
      id: `bazel-${phase}`,
      description: `Run Bazel ${command} for ${bazelTarget}`,
      command: ['bazel', command, bazelTarget],
      workingDirectory: bazelWorkspace,
    }];
  }

  async execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult> {
    const steps = await this.plan(context);
    const results: ToolchainStepResult[] = [];
    const artifacts: string[] = [];

    for (const step of steps) {
      context.logger.info(`Executing: ${step.command.join(' ')}`);
      const result = await this.executeStep(step);
      results.push(result);

      if (result.status === 'failed') {
        return {
          status: 'failed',
          steps: results,
          artifacts,
          durationMs: results.reduce((sum, r) => sum + r.durationMs, 0),
          failure: {
            stepId: step.id,
            message: result.stderr || 'Command failed',
          }
        };
      }

      // Collect artifacts based on phase
      artifacts.push(...this.collectArtifacts(context.phase, context.surfaceConfig));
    }

    return {
      status: 'succeeded',
      steps: results,
      artifacts,
      durationMs: results.reduce((sum, r) => sum + r.durationMs, 0),
    };
  }

  async validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult> {
    const expectedArtifacts = this.getExpectedArtifacts(context.phase, context.surfaceConfig);
    const missing: string[] = [];
    const errors: ValidationError[] = [];

    for (const artifact of expectedArtifacts) {
      if (!await this.fileExists(artifact.path)) {
        missing.push(artifact.path);
        errors.push({
          path: artifact.path,
          message: `Expected artifact not found: ${artifact.path}`,
        });
      }
    }

    return {
      status: missing.length > 0 ? 'invalid' : 'valid',
      errors,
      missingArtifacts: missing,
      unexpectedArtifacts: [],
    };
  }

  private mapPhaseToBazelCommand(phase: ProductLifecyclePhase, target: string): string {
    const phaseToCommand: Record<ProductLifecyclePhase, string> = {
      dev: 'run',
      validate: 'test',
      test: 'test',
      build: 'build',
      package: 'build',
      release: 'build',
      deploy: 'build',
      verify: 'test',
      promote: 'build',
      rollback: 'build',
      operate: 'test',
      retire: 'build',
      create: 'build',
      bootstrap: 'build',
    };
    return phaseToCommand[phase];
  }

  private async executeStep(step: ToolchainPlanStep): Promise<ToolchainStepResult> {
    // Execute command using child_process
    // ...
  }

  private collectArtifacts(phase: ProductLifecyclePhase, surfaceConfig: Record<string, unknown>): string[] {
    // Collect artifacts based on phase
    // ...
  }

  private getExpectedArtifacts(phase: ProductLifecyclePhase, surfaceConfig: Record<string, unknown>): Array<{path: string}> {
    // Return expected artifacts for the phase
    // ...
  }

  private async fileExists(path: string): Promise<boolean> {
    // Check if file exists
    // ...
  }
}
```

#### Use Custom Adapter

Declare in product manifest:

```yaml
# products/my-product/kernel-product.yaml
productId: my-product
lifecycleProfile: custom-bazel-product

surfaces:
  backend-api:
    adapter: bazel-java-service
    bazelWorkspace: products/my-product
    bazelTarget: //products/my-product:api-service
```

---

### 2. Custom Lifecycle Profiles

Power users can register custom lifecycle profiles for product types not covered by standard profiles.

#### Register Profile

Add to `config/product-lifecycle-profiles.json`:

```json
{
  "version": "1.0.0",
  "profiles": {
    "custom-bazel-product": {
      "description": "A product using Bazel for Java services with custom testing strategy",
      "defaultSurfaces": {
        "dev": ["backend-api", "worker"],
        "validate": ["backend-api", "worker"],
        "test": ["backend-api", "worker"],
        "build": ["backend-api", "worker"],
        "package": ["backend-api", "worker"],
        "deploy": ["backend-api", "worker"]
      },
      "requiredGates": {
        "validate": [
          "registry-validation",
          "manifest-validation",
          "lifecycle-contract-validation",
          "bazel-query-validation"
        ],
        "build": [
          "registry-validation",
          "manifest-validation",
          "bazel-build",
          "bazel-test",
          "conformance"
        ],
        "deploy": [
          "artifact-validation",
          "environment-validation",
          "health-check",
          "observability-check"
        ]
      },
      "defaultAdapters": {
        "backend-api": "bazel-java-service",
        "worker": "bazel-java-service",
        "package.backend-api": "docker-buildx",
        "package.worker": "docker-buildx",
        "deploy.local": "compose-local"
      }
    }
  }
}
```

#### Use Custom Profile

Declare in product manifest:

```yaml
# products/my-product/kernel-product.yaml
productId: my-product
lifecycleProfile: custom-bazel-product
```

---

### 3. Custom Deployment Targets

Power users can register custom deployment targets for infrastructure not covered by standard targets (e.g., AWS ECS, Google Cloud Run, Nomad).

#### Register Deployment Target

Add to `config/deployment/deployment-targets.json`:

```json
{
  "version": "1.0.0",
  "targets": {
    "aws-ecs": {
      "kind": "cluster",
      "adapter": "aws-ecs",
      "supportedEnvironments": ["dev", "staging", "prod"],
      "requires": [
        "deployment.aws.cluster",
        "deployment.aws.taskDefinition",
        "deployment.aws.service"
      ],
      "outputs": ["deployment-manifest", "health-check-report"]
    }
  }
}
```

#### Implement Deployment Adapter

Create adapter in `platform/typescript/kernel-deployment/src/adapters/`:

```typescript
import {
  DeploymentAdapter,
  DeploymentContext,
  DeploymentPlan,
  DeploymentResult,
} from '@ghatana/kernel-deployment';

export class AwsEcsDeploymentAdapter implements DeploymentAdapter {
  readonly id = 'aws-ecs';
  readonly supportedEnvironments = ['dev', 'staging', 'prod'];

  async plan(context: DeploymentContext): Promise<DeploymentPlan> {
    const { environment, surfaceConfig } = context;
    const cluster = surfaceConfig.cluster as string;
    const taskDefinition = surfaceConfig.taskDefinition as string;
    const service = surfaceConfig.service as string;

    return {
      deploymentTarget: 'aws-ecs',
      cluster,
      taskDefinition,
      service,
      steps: [
        {
          action: 'update-task-definition',
          taskDefinition,
        },
        {
          action: 'update-service',
          cluster,
          service,
        },
        {
          action: 'wait-for-stability',
          cluster,
          service,
          timeoutSeconds: 600,
        },
      ],
    };
  }

  async execute(context: DeploymentContext): Promise<DeploymentResult> {
    const plan = await this.plan(context);
    // Execute deployment steps using AWS SDK
    // ...
  }

  async rollback(context: DeploymentContext): Promise<DeploymentResult> {
    // Execute rollback using AWS SDK
    // ...
  }
}
```

#### Use Custom Deployment Target

Declare in product manifest:

```yaml
# products/my-product/kernel-product.yaml
productId: my-product

deployment:
  targets:
    dev:
      deploymentTarget: aws-ecs
      cluster: my-product-dev
      taskDefinition: my-product-task
      service: my-product-service
```

---

### 4. Custom Gates

Power users can register custom gates for validation not covered by standard gates (e.g., custom security scans, compliance checks).

#### Register Gate

Add to `config/security/product-lifecycle-security-gates.json` or appropriate gate config:

```json
{
  "version": "1.0.0",
  "gates": {
    "custom-security-scan": {
      "kind": "security",
      "description": "Custom security scan using internal tooling",
      "implementation": "scripts/gates/custom-security-scan.mjs",
      "requiredFor": ["build", "deploy"],
      "environments": ["staging", "prod"]
    }
  }
}
```

#### Implement Gate

Create gate script:

```javascript
// scripts/gates/custom-security-scan.mjs
import { executeCommand } from '@ghatana/kernel-utils';

export async function runGate(context) {
  const { productId, phase, environment, outputDirectory } = context;

  console.log(`Running custom security scan for ${productId} in ${environment}`);

  const result = await executeCommand(['custom-security-tool', 'scan', '--output', outputDirectory]);

  if (result.exitCode !== 0) {
    return {
      status: 'failed',
      message: 'Custom security scan failed',
      details: result.stderr,
    };
  }

  return {
    status: 'passed',
    message: 'Custom security scan passed',
    details: result.stdout,
  };
}
```

#### Use Custom Gate

Add to lifecycle profile:

```json
{
  "profiles": {
    "standard-web-api-product": {
      "requiredGates": {
        "build": [
          "registry-validation",
          "custom-security-scan",
          "product-build"
        ]
      }
    }
  }
}
```

---

### 5. Custom Environments

Power users can register custom environments for deployment targets not covered by standard environments (e.g., perf, dr).

#### Register Environment

Add to `config/environments/perf.json`:

```json
{
  "schemaVersion": "1.0.0",
  "id": "perf",
  "displayName": "Performance Testing",
  "deploymentTarget": "kubernetes",
  "secretsProvider": "external-secret-store",
  "configProvider": "environment-config-service",
  "approvalRequired": false,
  "requiredGates": [
    "registry-validation",
    "artifact-validation",
    "environment-validation",
    "health-check",
    "performance"
  ],
  "observabilityProfile": "perf-standard",
  "rollbackPolicy": "manual",
  "promotionPolicy": "none"
}
```

#### Use Custom Environment

Deploy to custom environment:

```bash
kernel product deploy my-product --env perf
```

---

## Extension Validation

Kernel validates all extensions before use:

### Adapter Validation

- Adapter implements ToolchainAdapter interface
- Adapter declares supported phases and surface types
- Adapter implements plan(), execute(), and validateOutputs()
- Adapter emits structured logs
- Adapter fails closed on errors

### Profile Validation

- Profile has valid schema
- Profile references valid adapters
- Profile references valid gates
- Profile has default surfaces for all phases

### Deployment Target Validation

- Target has valid schema
- Target references valid adapter
- Target declares required config
- Target declares supported environments

### Gate Validation

- Gate has valid schema
- Gate implementation exists
- Gate declares required phases
- Gate declares required environments

## Extension Best Practices

### 1. Keep Extensions Product-Agnostic

Custom adapters and profiles should be reusable across products, not product-specific.

**Bad:**
```typescript
class DigitalMarketingCustomAdapter { /* ... */ }
```

**Good:**
```typescript
class CustomHttpApiAdapter { /* ... */ }
```

### 2. Fail Closed

All extensions must fail explicitly on errors, not silently continue.

**Bad:**
```typescript
try {
  await execute();
} catch (e) {
  // Ignore error
}
```

**Good:**
```typescript
try {
  await execute();
} catch (e) {
  throw new Error(`Execution failed: ${e.message}`);
}
```

### 3. Emit Structured Logs

All extensions must emit structured logs for observability.

**Bad:**
```typescript
console.log('Building...');
```

**Good:**
```typescript
context.logger.info({
  event: 'build-started',
  productId: context.productId,
  phase: context.phase,
});
```

### 4. Support Dry-Run

All adapters must support dry-run mode via the plan() method.

```typescript
if (context.dryRun) {
  return plan; // Return plan without executing
}
```

### 5. Validate Inputs

All extensions must validate their inputs before execution.

```typescript
if (!surfaceConfig.requiredField) {
  throw new Error('requiredField is required');
}
```

### 6. Clean Up Resources

All extensions must clean up temporary resources on failure.

```typescript
try {
  await execute();
} finally {
  await cleanup();
}
```

## Extension Testing

### Unit Tests

Test extensions with fake dependencies:

```typescript
describe('CustomAdapter', () => {
  it('should plan correctly', async () => {
    const fakeLogger = createFakeLogger();
    const adapter = new CustomAdapter(fakeLogger);
    const context = createMockContext();

    const plan = await adapter.plan(context);

    expect(plan).toHaveLength(1);
    expect(plan[0].command).toEqual(['custom-tool', 'build']);
  });
});
```

### Integration Tests

Test extensions with real tools in isolated environments:

```typescript
describe('CustomAdapter (integration)', () => {
  it('should execute successfully', async () => {
    const adapter = new CustomAdapter(new RealLogger());
    const context = createRealContext();

    const result = await adapter.execute(context);

    expect(result.status).toBe('succeeded');
  });
});
```

## Extension Governance

### Review Process

Custom extensions should be reviewed for:
- Contract compliance
- Security implications
- Performance impact
- Observability coverage
- Reusability across products

### Approval

Custom extensions for production use require approval from:
- Platform team (for adapters, profiles, deployment targets)
- Security team (for security gates)
- Compliance team (for compliance gates)

### Versioning

Custom extensions should be versioned:
- Adapters: semantic versioning
- Profiles: semantic versioning
- Deployment targets: semantic versioning
- Gates: semantic versioning

## Extension Limitations

Power users CANNOT:
- Bypass Kernel's contract validation
- Disable required gates
- Modify Kernel's core lifecycle engine
- Access product-specific data outside their own product
- Deploy without artifact validation
- Deploy without health checks
- Promote without rollback plan

Power users CAN:
- Register custom adapters for their tools
- Register custom profiles for their product types
- Register custom deployment targets for their infrastructure
- Register custom gates for their validation needs
- Register custom environments for their deployment needs
- Extend lifecycle profiles with additional gates
- Override default adapters in product manifests

## Related Contracts

- [Product Lifecycle Contract](PRODUCT_LIFECYCLE_CONTRACT.md)
- [Product Toolchain Adapter Spec](PRODUCT_TOOLCHAIN_ADAPTER_SPEC.md)
- [Product Artifact Contract](PRODUCT_ARTIFACT_CONTRACT.md)
- [Product Environment Contract](PRODUCT_ENVIRONMENT_CONTRACT.md)
- [Product Deployment Contract](PRODUCT_DEPLOYMENT_CONTRACT.md)
