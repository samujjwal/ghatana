# Tutorputor DB Service – Coding Guidelines

## 1. Scope

These guidelines apply to `products/tutorputor/services/tutorputor-db`.

## 2. Design Principles

- Keep DB concerns here; other Tutorputor services should depend on shared patterns.
- Centralize schema and migration logic to avoid drift.
- Reuse `libs/java/database` abstractions wherever possible.

This document is self-contained and defines how to structure code in the Tutorputor DB service/module.
