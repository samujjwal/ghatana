# Platform Kernel

Core kernel contracts and runtime primitives for platform modules that need consistent capability registration, module lifecycle management, context propagation, plugin loading, policy enforcement, and observability hooks.

## Purpose

- Provide the canonical kernel-layer abstractions reused across platform and product modules.
- Keep generic contracts in one place so product code depends on stable kernel APIs instead of duplicating lifecycle and registry logic.
- Centralize architectural guardrails around capability mapping, scope boundaries, plugin loading, and security integration.

## Module Contents

The main package root is `com.ghatana.kernel` and currently contains these top-level areas:

- `adapter` — adapter contracts and integration points.
- `ai` — AI-related kernel abstractions.
- `annotation` — kernel annotations and marker metadata.
- `audit` — audit and cross-scope audit support.
- `boundary` — architectural boundary enforcement utilities.
- `capability` — capability identifiers and capability-oriented contracts.
- `communication` — kernel communication and messaging abstractions.
- `config` — kernel configuration resolution and binding.
- `context` — kernel context lifecycle and context propagation.
- `contract` and `contracts` — kernel-facing contracts and compatibility layers.
- `descriptor` — kernel/module descriptors and metadata models.
- `event` — kernel event abstractions.
- `extension` — extension registration and extensibility points.
- `health` — health model integration.
- `loader` — loading/bootstrap mechanics.
- `module` — kernel module lifecycle primitives.
- `observability` — metrics/tracing/logging hooks used by kernel components.
- `plugin` — plugin runtime and plugin metadata support.
- `policy` — policy resolution and enforcement abstractions.
- `registry` — kernel registry APIs and implementations.
- `scope` — scoping and isolation models.
- `security` — security-facing kernel abstractions.
- `util` — shared kernel utilities.
- `workflow` — workflow-facing kernel contracts.

## Related Documentation

- `docs/CANONICAL_CAPABILITY_MAPPING.md` — authoritative capability-to-module mapping and extraction guidance.
- `CHANGELOG.md` — module change history.

## Build And Test

From the repository root:

```bash
./gradlew :platform:java:kernel:test
```

Representative test coverage already exists for:

- descriptor behavior
- registry behavior
- context lifecycle
- end-to-end kernel flows
- architecture and purity validation
- cross-module integration paths

## Conventions

- Keep this module generic and platform-scoped; product/domain logic belongs outside kernel.
- Reuse existing platform libraries first, especially `platform:java:core`, `platform:java:http`, `platform:java:observability`, and `platform:java:security`.
- Maintain capability mappings in `docs/CANONICAL_CAPABILITY_MAPPING.md` when adding new kernel-facing features.
- Preserve the existing ActiveJ-first async model and avoid introducing alternate async frameworks.