# Virtual-Org – Workflow Library – Coding Guidelines

## 1. Scope

These guidelines apply to the top-level Virtual‑Org Workflow library (`products/virtual-org/libs/workflow`), which provides workflow abstractions and integration with the operator framework and Virtual‑Org core.

## 2. Dependencies & Layering

From the build configuration, this module depends on:

- `:libs:operator` – core operator framework.
- `:products:virtual-org` – root Virtual‑Org module.
- `:libs:domain-models` – shared domain types.
- ActiveJ promise utilities and SLF4J for logging.

Treat this library as an **integration‑aware workflow layer**:

- It may depend on operator and domain models, but should not contain product-specific business rules.

## 3. Workflow Modeling

- Represent workflows and tasks in terms of:
  - Clear states and transitions.
  - Explicit inputs and outputs.
- Use strong types for workflow identifiers, states, and result shapes.

## 4. Integration with Operator & Virtual‑Org

- Keep mapping between workflows and operator types clearly defined.
- Integrate with Virtual‑Org abstractions (organizations, agents, events) via well-documented interfaces.

## 5. Async & Error Handling

- Use ActiveJ promises for async workflows.
- Propagate errors as typed failures that caller code can interpret, not as unstructured exceptions when avoidable.

## 6. Documentation

- Document significant workflow patterns and helpers in this docs tree so they can be understood without external references.

This document is self-contained and defines how to structure code in the Virtual‑Org Workflow library.
