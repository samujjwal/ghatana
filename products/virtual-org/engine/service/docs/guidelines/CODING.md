# Virtual-Org – Service – Coding Guidelines

## 1. Scope

These guidelines apply to the `virtual-org-service` application, which exposes Virtual-Org framework capabilities via APIs.

## 2. Layering

- Follow API → Application → Domain → Infrastructure layering:
  - Keep Virtual-Org framework abstractions in the domain layer.
  - Keep HTTP-specific concerns in the API layer.

## 3. HTTP & Error Handling

- Use platform `http-server` abstractions and ResponseBuilder.
- Return problem-details errors for validation and processing errors.

## 4. Observability

- Integrate with `libs/java/observability`:
  - Metrics for request volume, latency, error rates.
  - Traces for cross-service calls.

## 5. Documentation & @doc.\*

- Public API entry points and key use cases should be documented with JavaDoc and `@doc.*` tags where applicable.

These guidelines are self-contained and define how to structure code for the `virtual-org-service` application.
