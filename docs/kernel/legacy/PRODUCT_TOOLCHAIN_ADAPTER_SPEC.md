# Product Toolchain Adapter Specification

**Version:** 1.0.0
**Status:** Implementation-Ready
**Last Updated:** 2026-05-12

## Purpose

This specification defines the contract for toolchain adapters that abstract build, test, package, and deployment tools (Gradle, pnpm, Vite, Docker, Compose, Kubernetes, Helm, Terraform, Vitest, Playwright, etc.) from the Kernel lifecycle engine.

## Design Principles

1. **Adapters own tool execution**: Adapters know how to invoke specific tools and interpret their results.
2. **Kernel owns orchestration**: Kernel decides which adapters to use, in which order, with which gates.
3. **Products declare intent**: Products declare which adapter to use for each surface through their manifest.
4. **Fail closed**: Adapters must validate their preconditions and fail explicitly if they cannot execute.
5. **No shell injection**: Adapters must use explicit argument arrays, never shell strings.

## Adapter Interface

### TypeScript Interface

```typescript
/**
 * A toolchain adapter abstracts a specific tool (Gradle, pnpm, Docker, etc.)
 * for executing lifecycle phases on product surfaces.
 */
export interface ToolchainAdapter {
  /**
   * Unique identifier for this adapter (e.g., "gradle-java-service").
   */
  readonly id: string;

  /**
   * Lifecycle phases this adapter supports (e.g., ["dev", "validate", "test", "build", "package"]).
   */
  readonly supportedPhases: ProductLifecyclePhase[];

  /**
   * Surface types this adapter supports (e.g., ["backend-api", "worker", "operator"]).
   */
  readonly supportedSurfaceTypes: ProductSurfaceType[];

  /**
   * Generate an execution plan for the given context without executing.
   * This is used for dry-run and planning.
   */
  plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]>;

  /**
   * Execute the planned steps and return the result.
   */
  execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult>;

  /**
   * Validate that the expected outputs were produced by execution.
   */
  validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult>;
}
```

### Adapter Context

```typescript
/**
 * Context provided to an adapter for planning and execution.
 */
export interface ToolchainAdapterContext {
  /**
   * Product identifier.
   */
  productId: string;

  /**
   * Lifecycle phase being executed.
   */
  phase: ProductLifecyclePhase;

  /**
   * Surface being processed.
   */
  surface: ProductSurface;

  /**
   * Environment (for deploy/verify/promote/rollback phases).
   */
  environment?: string;

  /**
   * Source reference (git branch, commit, etc.).
   */
  sourceRef?: string;

  /**
   * Output directory for artifacts and results.
   */
  outputDirectory: string;

  /**
   * Dry-run mode: plan only, do not execute.
   */
  dryRun: boolean;

  /**
   * Surface-specific configuration from the product manifest.
   */
  surfaceConfig: Record<string, unknown>;

  /**
   * Phase-specific configuration from the lifecycle profile.
   */
  phaseConfig: Record<string, unknown>;

  /**
   * Logger for structured output.
   */
  logger: ExecutionLogger;
}
```

### Plan Step

```typescript
/**
 * A single step in an adapter's execution plan.
 */
export interface ToolchainPlanStep {
  /**
   * Step identifier (unique within the plan).
   */
  id: string;

  /**
   * Human-readable description.
   */
  description: string;

  /**
   * Command to execute (as an argument array, never a shell string).
   */
  command: string[];

  /**
   * Working directory for the command.
   */
  workingDirectory: string;

  /**
   * Environment variables for the command.
   */
  env?: Record<string, string>;

  /**
   * Expected outputs from this step.
   */
  expectedOutputs?: string[];

  /**
   * Whether this step can be executed in parallel with others.
   */
  parallelizable?: boolean;

  /**
   * Dependencies on other steps (by step ID).
   */
  dependsOn?: string[];
}
```

### Execution Result

```typescript
/**
 * Result of executing an adapter's plan.
 */
export interface ToolchainExecutionResult {
  /**
   * Overall status.
   */
  status: 'succeeded' | 'failed' | 'skipped';

  /**
   * Step-level results.
   */
  steps: ToolchainStepResult[];

  /**
   * Artifacts produced (paths relative to output directory).
   */
  artifacts: string[];

  /**
   * Test results (if applicable).
   */
  testResults?: ToolchainTestResults;

  /**
   * Coverage results (if applicable).
   */
  coverageResults?: ToolchainCoverageResults;

  /**
   * Execution duration in milliseconds.
   */
  durationMs: number;

  /**
   * Failure information if status is 'failed'.
   */
  failure?: {
    stepId: string;
    message: string;
    cause?: string;
  };
}

/**
 * Result of executing a single step.
 */
export interface ToolchainStepResult {
  /**
   * Step identifier.
   */
  stepId: string;

  /**
   * Execution status.
   */
  status: 'succeeded' | 'failed' | 'skipped';

  /**
   * Exit code (0 for success).
   */
  exitCode?: number;

  /**
   * Standard output (truncated if large).
   */
  stdout?: string;

  /**
   * Standard error (truncated if large).
   */
  stderr?: string;

  /**
   * Execution duration in milliseconds.
   */
  durationMs: number;
}
```

### Output Validation

```typescript
/**
 * Result of validating adapter outputs.
 */
export interface ToolchainOutputValidationResult {
  /**
   * Overall validation status.
   */
  status: 'valid' | 'invalid' | 'partial';

  /**
   * Validation errors (if any).
   */
  errors: ValidationError[];

  /**
   * Missing expected artifacts (if any).
   */
  missingArtifacts: string[];

  /**
   * Unexpected artifacts found (if any).
   */
  unexpectedArtifacts: string[];
}
```

## Adapter Registry

Adapters are registered in `config/toolchain-adapter-registry.json`:

```json
{
  "version": "1.0.0",
  "adapters": {
    "gradle-java-service": {
      "kind": "build-tool",
      "supportedSurfaceTypes": ["backend-api", "worker", "operator"],
      "supportedPhases": ["dev", "validate", "test", "build", "package"],
      "requires": ["gradleModules"],
      "outputs": ["jvm-classes", "test-report", "coverage-report", "jar"]
    },
    "pnpm-vite-react": {
      "kind": "build-tool",
      "supportedSurfaceTypes": ["web"],
      "supportedPhases": ["dev", "validate", "test", "build", "package"],
      "requires": ["packagePath"],
      "outputs": ["static-web-bundle", "test-report", "typecheck-report"]
    },
    "compose-local": {
      "kind": "deployment-tool",
      "supportedSurfaceTypes": ["backend-api", "web", "worker"],
      "supportedPhases": ["deploy", "verify", "rollback"],
      "requires": ["deployment.local.composeFile"],
      "outputs": ["deployment-manifest", "health-check-report"]
    }
  }
}
```

## Built-in Adapters

### GradleJavaServiceAdapter

**Purpose:** Execute Gradle builds for Java service surfaces.

**Supported Phases:** dev, validate, test, build, package

**Supported Surface Types:** backend-api, worker, operator

**Required Surface Config:**
- `gradleModule`: Gradle module path (e.g., `:products:digital-marketing:dm-api`)
- `devTask?`: Task for dev phase (e.g., `runDmosApiServer`)
- `buildTask`: Task for build phase (e.g., `build`)
- `testTask`: Task for test phase (e.g., `test`)
- `validateTask`: Task for validate phase (e.g., `check`)

**Phase Mapping:**
- `dev` → `runDmosApiServer` or `bootRun` (if configured)
- `validate` → `check`
- `test` → `test`
- `build` → `build`
- `package` → `assemble` or `installDist`

**Outputs:**
- JVM classes
- JAR file
- Test reports (JUnit XML)
- Coverage reports (Jacoco)

**Safety Rules:**
- Validate Gradle module exists before execution
- Use explicit task names, never generic `gradle` without task
- Collect and parse test reports for structured results
- Validate JAR file exists after build

---

### PnpmViteReactAdapter

**Purpose:** Execute pnpm/Vite builds for React web surfaces.

**Supported Phases:** dev, validate, test, build, package

**Supported Surface Types:** web

**Required Surface Config:**
- `packagePath`: Path to package.json (e.g., `products/digital-marketing/ui/package.json`)
- `devScript`: Script name for dev phase (e.g., `dev`)
- `buildScript`: Script name for build phase (e.g., `build`)
- `testScript`: Script name for test phase (e.g., `test`)
- `validateScript?`: Script name for validate phase (e.g., `lint`)

**Phase Mapping:**
- `dev` → `pnpm --dir <path> dev`
- `validate` → `pnpm --dir <path> lint` + `tsc --noEmit`
- `test` → `pnpm --dir <path> test`
- `build` → `pnpm --dir <path> build`
- `package` → Docker build of static bundle

**Outputs:**
- Static web bundle (dist/)
- Test reports (Vitest JSON)
- Typecheck report

**Safety Rules:**
- Validate package.json exists and contains required scripts
- Use `--dir` flag for workspace monorepos
- Collect and parse Vite build output for bundle analysis
- Validate dist/ directory exists after build

---

### VitestAdapter

**Purpose:** Execute Vitest test suites.

**Supported Phases:** test

**Supported Surface Types:** web, backend-api (if Vitest is used)

**Required Surface Config:**
- `packagePath`: Path to package.json
- `testScript`: Script name for test (e.g., `test`)

**Outputs:**
- Test results (JSON)
- Coverage reports (if configured)

**Safety Rules:**
- Validate Vitest is installed
- Parse JSON output for structured results
- Fail if test exit code is non-zero

---

### PlaywrightAdapter

**Purpose:** Execute Playwright E2E tests.

**Supported Phases:** test, verify

**Supported Surface Types:** web

**Required Surface Config:**
- `packagePath`: Path to package.json
- `e2eScript`: Script name for E2E tests (e.g., `test:e2e`)

**Outputs:**
- Test results (JSON)
- Screenshots (on failure)
- Trace files (on failure)

**Safety Rules:**
- Validate Playwright is installed
- Require deployed environment for execution
- Parse JSON output for structured results
- Collect screenshots and traces for debugging

---

### DockerBuildxAdapter

**Purpose:** Build container images using Docker Buildx.

**Supported Phases:** package

**Supported Surface Types:** backend-api, web, worker

**Required Surface Config:**
- `dockerfile`: Path to Dockerfile
- `context`: Build context path
- `imageTag`: Target image tag
- `platforms?`: Target platforms (default: current)

**Outputs:**
- Container image
- Image digest
- Build metadata

**Safety Rules:**
- Validate Dockerfile exists
- Use BuildKit for better caching and performance
- Emit image digest for reproducibility
- Support multi-platform builds when configured

---

### ComposeDeploymentAdapter

**Purpose:** Deploy services using Docker Compose.

**Supported Phases:** deploy, verify, rollback

**Supported Surface Types:** backend-api, web, worker

**Required Surface Config:**
- `composeFile`: Path to docker-compose.yml
- `envFile`: Path to .env file
- `services`: Services to deploy

**Outputs:**
- Deployment manifest
- Health check results
- Service status

**Safety Rules:**
- Validate compose file syntax before deployment
- Check for port conflicts
- Run health checks after deployment
- Support rollback via `docker compose down` + previous compose file

---

### NoopDocumentationAdapter

**Purpose:** Placeholder adapter for documentation-only surfaces.

**Supported Phases:** validate, build

**Supported Surface Types:** documentation

**Required Surface Config:** None

**Outputs:** None

**Safety Rules:**
- Always succeed in dry-run mode
- Validate documentation files exist in validate phase
- No actual execution in build phase

## Adapter Safety Rules

All adapters must:

1. **Declare supported phases and surfaces** in the adapter metadata
2. **Support dry-run planning** via the `plan()` method
3. **Provide structured execution results** with step-level details
4. **Validate outputs** after execution
5. **Emit structured logs** for observability
6. **Fail closed** on missing preconditions or invalid inputs
7. **Avoid shell injection** by using explicit argument arrays
8. **Validate tool availability** before execution (e.g., check if Gradle is installed)
9. **Handle timeouts** and cancel gracefully
10. **Clean up temporary resources** on failure

## Adapter Implementation Pattern

```typescript
export class GradleJavaServiceAdapter implements ToolchainAdapter {
  readonly id = 'gradle-java-service';
  readonly supportedPhases: ProductLifecyclePhase[] = ['dev', 'validate', 'test', 'build', 'package'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['backend-api', 'worker', 'operator'];

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const { phase, surfaceConfig } = context;
    const gradleModule = surfaceConfig.gradleModule as string;

    if (!gradleModule) {
      throw new Error('gradleModule is required for GradleJavaServiceAdapter');
    }

    const task = this.mapPhaseToTask(phase, surfaceConfig);
    const command = ['./gradlew', gradleModule + ':' + task];

    return [{
      id: `gradle-${phase}`,
      description: `Run Gradle ${task} for ${gradleModule}`,
      command,
      workingDirectory: this.resolveModulePath(gradleModule),
    }];
  }

  async execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult> {
    const steps = await this.plan(context);
    // Execute steps and collect results
    // ...
  }

  async validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult> {
    // Validate expected artifacts exist
    // ...
  }

  private mapPhaseToTask(phase: ProductLifecyclePhase, surfaceConfig: Record<string, unknown>): string {
    // Map phase to Gradle task
    // ...
  }

  private resolveModulePath(gradleModule: string): string {
    // Resolve Gradle module to filesystem path
    // ...
  }
}
```

## Adapter Testing

### Unit Tests

Unit tests should use a fake command runner, not real tools:

```typescript
describe('GradleJavaServiceAdapter', () => {
  it('should plan build phase correctly', async () => {
    const fakeRunner = new FakeCommandRunner();
    const adapter = new GradleJavaServiceAdapter(fakeRunner);
    const context = createMockContext({
      phase: 'build',
      surfaceConfig: { gradleModule: ':products:digital-marketing:dm-api' }
    });

    const plan = await adapter.plan(context);

    expect(plan).toHaveLength(1);
    expect(plan[0].command).toEqual(['./gradlew', ':products:digital-marketing:dm-api:build']);
  });
});
```

### Integration Tests

Integration tests can run real tools in isolated environments:

```typescript
describe('GradleJavaServiceAdapter (integration)', () => {
  it('should build real Gradle module', async () => {
    const adapter = new GradleJavaServiceAdapter(new RealCommandRunner());
    const context = createRealContext({
      phase: 'build',
      surfaceConfig: { gradleModule: ':products:digital-marketing:dm-api' }
    });

    const result = await adapter.execute(context);

    expect(result.status).toBe('succeeded');
    expect(result.artifacts).toContain('build/libs/dm-api.jar');
  });
});
```

## Adapter Extension

Power users can register custom adapters by:

1. Implementing the `ToolchainAdapter` interface
2. Registering the adapter in `config/toolchain-adapter-registry.json`
3. Declaring the adapter in product manifests

Custom adapters are still subject to:
- Adapter contract validation
- Gate execution
- Artifact validation
- Observability requirements

## Related Contracts

- [Product Lifecycle Contract](PRODUCT_LIFECYCLE_CONTRACT.md)
- [Product Artifact Contract](PRODUCT_ARTIFACT_CONTRACT.md)
- [Product Environment Contract](PRODUCT_ENVIRONMENT_CONTRACT.md)
- [Product Deployment Contract](PRODUCT_DEPLOYMENT_CONTRACT.md)
- [Product Power User Extension Guide](PRODUCT_POWER_USER_EXTENSION_GUIDE.md)
