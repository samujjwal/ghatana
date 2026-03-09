# Virtual-Org – Integration Library – Design & Architecture

## 1. Purpose

The Virtual‑Org Integration library (`libs/integration`) provides **integration glue between Virtual‑Org and other platform components**, including the operator framework, Agentic Event Processor (AEP), and domain model libraries. It is the place where Virtual‑Org concepts are wired into the broader platform.

## 2. Responsibilities

- Expose integration utilities that bind Virtual‑Org organizations, workflows, and agents to:
  - Operator framework types and operators.
  - AEP pattern and learning libraries.
  - Shared domain models.
- Provide integration‑level helpers and test scaffolding for Virtual‑Org ↔ platform boundaries.

## 3. Architectural Position

From the build configuration:

- Depends on:
  - `:libs:operator` (operator framework).
  - `:products:virtual-org` and `:products:virtual-org:libs:workflow` (Virtual‑Org core).
  - `:products:agentic-event-processor:libs:pattern` and `:products:agentic-event-processor:libs:learning` (AEP pattern and learning libs).
  - `:libs:domain-models` for shared domain types.
  - ActiveJ promises for async support and SLF4J for logging.

This library **sits at the boundary** between Virtual‑Org, AEP, and the operator framework and should not contain core business logic for any one product.

## 4. Layers

- **Adapter Layer** – Adapters that map between Virtual‑Org, operator, AEP, and domain-model types.
- **Integration Layer** – Coordination utilities and test support for cross‑module interactions.

## 5. Design Constraints

- Keep integration logic composable and testable; avoid hard-coded environment details.
- Keep Virtual‑Org domain rules in Virtual‑Org modules and operator/AEP rules in their respective modules; this library should connect them, not redefine them.

This document is self-contained and summarizes the architecture and responsibilities of the Virtual‑Org Integration library.
