# YAPPC Core – Framework – Coding Guidelines

## 1. Scope

These guidelines apply to all code inside the YAPPC Core Framework module and any consuming YAPPC service that uses its abstractions.

## 2. Layering & Ownership

- Keep the framework focused on **cross-cutting infrastructure**:
  - HTTP configuration, filters, error handling patterns.
  - Observability integration (metrics, logging, tracing hooks).
  - Common configuration and bootstrap helpers.
- Do **not** place product-specific business logic inside the framework; that belongs in individual services.

## 3. HTTP & Error Handling

- Use `libs/java/http-server` exclusively for routing, filters, and response construction.
- Provide reusable exception mappers and problem-details helpers.
- Ensure all framework HTTP helpers are neutral and reusable by multiple services.

## 4. Observability

- Integrate with `libs/java/observability`:
  - Provide helper functions for metrics and tracing instrumentation.
  - Enforce structured logging with correlation ids.

## 5. Configuration & Security

- Centralize configuration patterns (env/config service) without hard-coding secrets.
- Provide hooks for authentication and authorization filters where appropriate, but keep policy details in the product.

## 6. Documentation & @doc.\*

- Public framework classes should include JavaDoc with `@doc.*` tags reflecting their role (infrastructure/framework layer).

These guidelines are self-contained and define how to implement and extend the YAPPC Core Framework module.
