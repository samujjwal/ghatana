# Product Toolchain Adapter Specification

This document defines production requirements for toolchain adapters.

## Adapter Contract

Adapters implement the `ToolchainAdapter` contract in `platform/typescript/kernel-toolchains/src/ToolchainAdapter.ts`:

- `plan(context)` returns executable command steps
- `execute(context)` executes steps and returns normalized step/result status
- `validateOutputs(context)` enforces expected outputs

## Registry Contract

All adapters must be declared in `config/toolchain-adapter-registry.json` and validate against `config/toolchain-adapter-registry-schema.json`.

Required registry fields include:

- `safeForDefault`
- `planningImplemented`
- `executionImplemented`
- `outputValidationImplemented`
- `status` and `tests`

Enforcement:

- `status: implemented` requires all three implementation flags to be `true`.
- `status: planned` requires all three implementation flags to be `false`.
- Stable/safe lifecycle profiles may only default to adapters with `safeForDefault: true`.

## Output Validation

Adapters must fail closed on required outputs.

- If `surfaceConfig.expectedOutputs[phase]` is defined in `kernel-product.yaml`, it is authoritative.
- If not defined, adapters may use strict fallback checks.
- Missing expected outputs must produce failed execution results.

## Phase Support

Adapters must reject unsupported phases explicitly.

- No fallback mapping from unsupported phases to build/test is allowed.
- Unsupported phase usage must raise a hard error during planning.

## Side-Effect Rules

Adapters must not perform artifact manifest side effects.

- Artifact manifest generation/writing is not performed in Gradle/pnpm adapter execution paths.
- Adapters only execute tools, collect outputs, and validate outputs.

## Implemented Adapters

- `gradle-java-service`: strict phase support, YAML-driven expected output validation.
- `pnpm-vite-react`: strict phase support, YAML-driven expected output validation.
