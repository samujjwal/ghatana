# Tutorputor DB Service – Testing Guidelines

## 1. Goals

- Ensure DB schemas, migrations, and helpers behave correctly and are safe to reuse.

## 2. Tests

- Unit-test DB helpers with in-memory DBs where possible.
- Add integration tests (e.g., with Testcontainers) for migrations and schema changes.

This document is self-contained and explains how to test the Tutorputor DB service/module.
