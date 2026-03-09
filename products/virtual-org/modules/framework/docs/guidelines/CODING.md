# Virtual-Org Java Framework – Coding Guidelines

## 1. Dependency Direction

- Framework code may depend on:
  - `libs/java/*` (http-server, observability, event-runtime, state, database, ai-integration).
- Framework code must **not** depend on product-level modules (YAPPC, Software-Org, or other products).

## 2. Layering & Abstractions

- Keep core abstractions stable and minimal:
  - Interfaces and base classes for organizations, departments, agents, and workflows.
- Separate framework contracts from sample or reference implementations.

## 3. Event & State Modeling

- Align framework events with the platform event model (GEvent-aligned envelopes).
- Use shared state abstractions where needed; avoid ad-hoc persistence.

## 4. Observability

- Use `libs/java/observability` for metrics and tracing.
- Tag metrics with tenant and organizational context.

## 5. Documentation & @doc.\*

- Provide comprehensive JavaDoc for all public APIs, including `@doc.*` tags.

These guidelines are self-contained and describe how to implement code inside the Virtual-Org Java Framework module.
