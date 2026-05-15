# Toolchain Adapters

## Contract

Toolchain adapters are the only Kernel-owned path from lifecycle plans to local tools. Products declare surfaces and lifecycle config; adapters implement phase-specific planning, execution, output validation, and evidence production behind the shared `ToolchainAdapter` contract.

Adapters must not be bypassed by product-local lifecycle runners. CLI, API, Studio, and agentic routes call Kernel lifecycle services, which then call adapters through the planner/executor.

## Command Execution

`SpawnCommandRunner` runs commands with `shell: false`, explicit cwd/env, timeout handling, output caps, cancellation support, command IDs, timestamps, and redaction. Timeouts return structured command results by default so callers can map failures into lifecycle-safe reason codes.

Raw command names are not accepted in agentic lifecycle request contracts, and unsafe command/stdout/stderr detail must not cross UI or agent boundaries unless explicitly classified safe.

## Canonical Adapters

### GradleJavaServiceAdapter

- ID: `gradle-java-service`
- Surfaces: `backend-api`, `worker`, `operator`
- Phases: `dev`, `validate`, `test`, `build`
- Responsibilities: validate Gradle module paths, map phases to Gradle tasks, supervise dev metadata, validate outputs, extract test/coverage evidence when present, and return structured failures such as `gradle-module-not-found`, `gradle-task-failed`, and `output-validation-failed`.

### PnpmViteReactAdapter

- ID: `pnpm-vite-react`
- Surfaces: `web`
- Phases: `dev`, `validate`, `test`, `build`
- Responsibilities: validate package paths and scripts before execution, prefer `typecheck` for validate when configured, reject unsupported container-image packaging, validate Vite output, capture bundle/test evidence when available, and write dev process metadata.

### DockerBuildxAdapter

- ID: `docker-buildx`
- Surfaces: containerized backend and web artifacts
- Phases: `package`
- Responsibilities: validate Dockerfile/context/image tags, build with Docker Buildx, preserve resolved image digests, redact build arg values, include product/surface/run/correlation labels, and fail required container artifacts when a digest cannot be resolved.

### ComposeLocalAdapter

- ID: `compose-local`
- Surfaces: local deployment and verification
- Phases: `deploy`, `verify`, `rollback`
- Responsibilities: validate compose labels and expected services, classify running/exited/unhealthy/missing services, resolve `${VAR:-default}` health URLs, require configured health checks during verify, produce deployment/health/rollback evidence, and fail when expected services are absent.

## Output Validation

Adapters are responsible for phase-local validation, but manifest fingerprinting belongs to `LifecycleManifestWriter`.

- Build/package outputs must exist when declared required.
- Missing expected outputs produce structured lifecycle failures.
- Container image artifacts must preserve real image digest when available.
- Dev phases may produce supervised process metadata instead of build artifacts.

## Failure Reason Codes

Common adapter failures should be stable and safe for UI/API clients:

- `script-not-found`
- `gradle-module-not-found`
- `gradle-task-failed`
- `output-validation-failed`
- `dockerfile-not-found`
- `docker-context-not-found`
- `container-digest-missing`
- `compose-service-missing`
- `health-check-failed`

Adapters may add more specific reason codes when the surrounding package has tests for them.

## Validation

```bash
pnpm --dir platform/typescript/kernel-toolchains test
pnpm --dir platform/typescript/kernel-toolchains typecheck
pnpm check:digital-marketing-lifecycle-pilot --smoke
```
