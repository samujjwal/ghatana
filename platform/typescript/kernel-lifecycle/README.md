# @ghatana/kernel-lifecycle

Kernel lifecycle engine for product lifecycle orchestration.

## Purpose

Core engine for the Ghatana product lifecycle system. Orchestrates phase execution, gate evaluation, schema validation, toolchain coordination, and truth-writing for registered products. This is the central runtime for the kernel lifecycle system.

## Key Concepts

- **LifecycleEngine** — orchestrates the full product lifecycle from declaration to release
- **PhaseExecutor** — runs the steps for a declared phase in order
- **GateEvaluator** — evaluates pass/fail conditions for lifecycle gate points
- **SchemaValidator** — validates product lifecycle configurations against canonical schemas
- **LifecycleTruthWriter** — persists authoritative lifecycle results to the truth file store

## Usage

```ts
import { LifecycleEngine, SchemaValidator } from "@ghatana/kernel-lifecycle";
```

## Directory Structure

```
src/
  SchemaValidator.ts    # Validates product registry entries and lifecycle configs
  agentic/              # AI-driven lifecycle orchestration helpers
  api/                  # HTTP API handlers for lifecycle operations
  domain/               # Core lifecycle domain types and logic
  events/               # Lifecycle event types and emitters
  execution/            # Phase and step execution engines
  gates/                # Gate definitions and evaluators
  governance/           # Governance rule evaluators
  health/               # Health-check helpers for lifecycle components
```

## Ownership

Platform Kernel Engineering. See [platform/typescript/LIBRARY_GOVERNANCE.md](../LIBRARY_GOVERNANCE.md).
