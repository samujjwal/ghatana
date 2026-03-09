# YAPPC Core – KG CLI Tools – Design & Architecture

## 1. Purpose

The KG CLI Tools module (`cli-tools`) provides **command-line tools for managing the YAPPC Knowledge Graph**. It offers operators and developers a scriptable interface to inspect, migrate, and maintain graph data and schemas.

## 2. Responsibilities

- Expose CLI commands for common KG operations (inspection, exports, migrations, validation).
- Use KG Core abstractions for entities, relationships, and queries.
- Integrate with platform configuration and utilities.

## 3. Architectural Style

- Hexagonal with a focus on **CLI adapters**:
  - Domain: Consolidated `knowledge-graph` types and core operations.
  - Adapters: Command-line interfaces and IO.

## 4. Layers

- **CLI Adapter Layer** – Commands, argument parsing, user interaction.
- **Application Layer** – Orchestration of KG operations invoked by CLI.
- **Domain Layer** – Uses consolidated `knowledge-graph` abstractions.

## 5. Dependencies

Per build configuration, KG CLI Tools depend on:

- `products:yappc:knowledge-graph` for consolidated graph domain models and services.
- Platform libs: `config-runtime`, `common-utils`, `proto`.
- Test utilities: `test-utils`, `test-data`.

## 6. Design Constraints

- Keep all user interaction and IO in the CLI layer; keep domain logic reusable and testable.
- Do not add product-specific coupling; tools should be usable across environments.

This document is self-contained and summarizes the architecture of the KG CLI Tools module.
