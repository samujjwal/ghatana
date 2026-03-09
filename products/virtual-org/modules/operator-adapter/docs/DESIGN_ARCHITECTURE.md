# Virtual-Org – Core Operator Adapter – Design & Architecture

## 1. Purpose

The top-level Operator Adapter library bridges **Virtual‑Org agents** and the **unified operator framework** at the platform level. It provides core adapter infrastructure that can be reused across products and services.

## 2. Responsibilities

- Adapt Virtual‑Org agent contracts (from `virtualorg-agent`) to operator framework interfaces (`libs/operator`).
- Integrate with shared observability and utility libraries.
- Handle serialization using protobuf and domain model types.

## 3. Architectural Position

From the build configuration, this module depends on:

- `:libs:operator`, `:libs:domain-models`, `:libs:types`.
- `:libs:observability`, `:libs:common-utils`.
- `:products:virtual-org:libs:virtualorg-agent`.
- ActiveJ (promise/eventloop/common), Micrometer, OpenTelemetry, protobuf, and logging libs.

It sits at the **platform operator adapter layer**, connecting Virtual‑Org to the operator framework and observability stack.

## 4. Layers

- **Adapter layer** – Core adapters that translate between Virtual‑Org agents and operator interfaces.
- **Integration layer** – Observability and serialization helpers supporting adapters.

## 5. Design Constraints

- Keep adapters reusable and independent of any single product.
- Do not embed product-specific business rules in this layer.

This document is self-contained and summarizes the architecture and responsibilities of the top-level Operator Adapter library.
