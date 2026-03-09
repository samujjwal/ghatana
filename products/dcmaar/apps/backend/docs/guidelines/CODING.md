# Guardian – Backend API – Coding Guidelines

## 1. Scope

These guidelines apply to `@guardian/backend`.

## 2. Layering & Dependencies

- Separate concerns into routing, handlers, domain logic, and persistence.
- Use Fastify plugins for cross-cutting concerns (auth, logging, security, rate limiting).

## 3. Error Handling & Observability

- Return structured errors; integrate with OpenTelemetry and Sentry for tracing and error tracking.

This document is self-contained and defines how to structure code in the Guardian Backend API.
