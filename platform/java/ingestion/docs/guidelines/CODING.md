# Shared Java Ingestion – Coding Guidelines

## 1. Scope

These guidelines apply to `libs/java/ingestion`.

## 2. Design Principles

- Keep ingestion abstractions reusable and product-agnostic.
- Encapsulate batching, retry, and backpressure behavior behind clear APIs.
- Use shared event runtime and observability libraries.

This document is self-contained and defines how to structure code in the Shared Java Ingestion library.
