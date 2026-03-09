# Virtual-Org – Workflow Library – Design & Architecture

## 1. Purpose

The top-level Virtual‑Org Workflow library (`products/virtual-org/libs/workflow`) provides **workflow abstractions and helpers** that connect Virtual‑Org organizations and agents with the platform operator framework and shared domain models.

## 2. Responsibilities

- Define reusable workflow primitives and patterns for Virtual‑Org scenarios.
- Integrate workflows with the operator framework (`:libs:operator`).
- Use shared domain models where applicable.
- Support async execution via ActiveJ promises.

## 3. Architectural Position

From the build configuration, this module depends on:

- `:libs:operator` – core operator framework.
- `:products:virtual-org` – Virtual‑Org root module.
- `:libs:domain-models` – shared types.
- ActiveJ promises and SLF4J logging.

The Workflow library sits at the **integration-aware workflow layer**, above Virtual‑Org core types and below product-specific services.

## 4. Layers

- **Workflow API layer** – Public workflow primitives, interfaces, and base classes.
- **Integration layer** – Helpers that bind workflows to operator and domain-model types.

## 5. Design Constraints

- Keep core workflow abstractions independent of any single product.
- Use Virtual‑Org and operator framework contracts consistently.

This document is self-contained and summarizes the architecture and role of the Virtual‑Org Workflow library.
