# Virtual-Org – Core Operator Adapter – Coding Guidelines

## 1. Scope

These guidelines apply to the top-level Operator Adapter library (`products/virtual-org/libs/operator-adapter`).

## 2. Dependencies & Layering

- Depends on platform libraries (`operator`, `domain-models`, `types`, `observability`, `common-utils`) and `virtualorg-agent`.
- Acts as a **core infrastructure layer** for integrating Virtual‑Org agents into the operator framework and observability stack.

## 3. Adapter Design

- Keep adapter classes focused on translation:
  - Map operator inputs to agent calls and protobuf/domain types.
  - Map agent outputs and events back to operator responses.
- Encapsulate serialization concerns; keep contracts clearly defined and versioned.

## 4. Observability & Logging

- Use `observability` and Micrometer/OTel APIs for metrics and traces.
- Use logging libraries for structured logs; avoid noisy or overly verbose logs in hot paths.

## 5. Testing Considerations

- Design adapters for testability (dependency injection, clear contracts).

This document is self-contained and defines how to structure code in the core Operator Adapter library.
