# YAPPC – Coding Guidelines

## 1. Scope

These guidelines apply to **YAPPC backend services** and the broader **YAPPC product**, complementing product-specific frontend guidelines for the app-creator web app.

## 2. Backend Coding

- Use **core/http-server** abstractions:
  - Controllers should use ResponseBuilder for responses and error handling.
  - Do not construct HttpResponse directly in business logic.
- Use **core/observability** abstractions:
  - Expose metrics through shared observability APIs, not direct Micrometer usage.
- Maintain `@doc.*` coverage:
  - All public classes should have `@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern` tags.

## 3. Shared Libraries & Components

- When extracting reusable components to `@ghatana/*` libraries:
  - Move shared logic out of product-specific packages.
  - Avoid creating new product-only variants when a shared implementation is possible.
- For frontend-adjacent code (e.g., shared state/UI helpers):
  - Prefer shared `@ghatana/*` libs instead of local re-implementations.

## 4. Dependency Rules

- YAPPC should depend on:
  - Shared libs (`@ghatana/*`, `libs/java/*`, etc.).
  - Core platform modules.
- It should not introduce new dependency directions that violate global architecture rules (e.g., making core libs depend on YAPPC).

These guidelines are self-contained and capture the core backend coding constraints described in the YAPPC roadmap.
