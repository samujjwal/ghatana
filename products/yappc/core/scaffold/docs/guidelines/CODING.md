# YAPPC Core – Scaffold – Coding Guidelines

## 1. Scope

These guidelines apply to the Scaffold multi-module project (core, cli, adapters, packs, schemas).

## 2. Responsibilities & Boundaries

- Scaffold is **infrastructure/tooling**, not product business logic.
- Generated artifacts must follow:
  - Layered architecture.
  - Reuse-first dependency rules (use `libs/java/*` and shared TS libs).
  - Testing and documentation standards defined in platform plans.

## 3. Template Design

- Prefer simple, composable templates over monolithic ones.
- Keep configuration external (e.g., JSON/YAML schema + CLI flags) rather than embedded constants.
- Use clear placeholders and comments to guide downstream users.

## 4. CLI & Core Separation

- Core scaffold logic should be reusable from CLI, tests, and other tooling.
- CLI modules act as thin adapters around the core engine.

## 5. Documentation

- Document new packs and adapters within this module’s docs tree when added.

This document is self-contained and defines how to structure and evolve the Scaffold module.
