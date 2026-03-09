# YAPPC Core – Framework – Design & Architecture

## 1. Purpose

The YAPPC Core Framework module provides **shared backend infrastructure and patterns** for YAPPC services (AI Requirements, Refactorer, KG Service, and others). It centralizes HTTP, observability, configuration, and common application patterns so individual services stay thin and consistent.

## 2. Responsibilities

- Provide base configuration for HTTP servers using platform abstractions.
- Expose shared application patterns (error handling, validation, response envelopes).
- Integrate observability (metrics, logging, tracing) in a consistent way.
- Offer reusable utilities for configuration, security hooks, and cross-cutting concerns.

## 3. Architectural Layers

- **API / Framework Surface**
  - Public classes and helpers used by YAPPC services.
- **Application Infrastructure**
  - HTTP configuration, filters, exception mappers, and application bootstrap helpers.
- **Integration Layer**
  - Observability, config, and persistence helpers built on `libs/java/*`.

## 4. Interactions & Dependencies

- Depends on platform modules (`http-server`, `observability`, `database`, and related libs).
- Is consumed by YAPPC core services, which import these abstractions rather than re-implementing them.

## 5. Design Constraints

- Enforces reuse-first: YAPPC services should go through this framework for common concerns instead of using raw libraries.
- Keeps product-specific logic in the consuming services, not inside the framework.

This document is self-contained and describes the role and architecture of the YAPPC Core Framework module.
