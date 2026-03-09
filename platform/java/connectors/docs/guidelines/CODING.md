# Shared Java Connectors – Coding Guidelines

## 1. Scope

These guidelines apply to `libs/java/connectors`.

## 2. Design Principles

- Keep connectors generic and product-agnostic.
- Encapsulate protocol and client details behind clear interfaces.
- Use shared patterns for retries, backoff, and error mapping.

This document is self-contained and defines how to structure code in the Shared Java Connectors library.
