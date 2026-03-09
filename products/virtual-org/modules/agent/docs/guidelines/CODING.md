# Virtual-Org – Java VirtualOrg-Agent Module – Coding Guidelines

## 1. Scope

These guidelines apply to the Java `virtualorg-agent` module, which provides agent abstractions and reference implementations for Virtual-Org.

## 2. Dependencies & Layering

- Treat `virtualorg-agent` as part of the **framework layer**:
  - May depend on Virtual-Org framework and platform `libs/java/*` modules.
  - Must not depend on product-specific modules.
- Keep agent-specific business logic here only when it is reusable across products.

## 3. Agent Modeling

- Represent agents with clear interfaces for:
  - Receiving and handling tasks or events.
  - Emitting new events or updating state.
- Use strong types for roles, capabilities, and states instead of ad-hoc strings.

## 4. Events & State

- Emit events aligned with the Virtual-Org event model.
- Keep state transitions explicit and testable.

## 5. Documentation & @doc.\*

- Public agent types should include JavaDoc with `@doc.*` tags reflecting their purpose and layer.

These guidelines are self-contained and define how to structure code in the Java `virtualorg-agent` module.
