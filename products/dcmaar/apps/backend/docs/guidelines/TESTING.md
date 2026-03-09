# Guardian – Backend API – Testing Guidelines

## 1. Goals

- Ensure backend endpoints, auth flows, and data access are correct and resilient.

## 2. Tests

- Use Vitest + Supertest for endpoint and integration tests.
- Use test DB setup scripts (`db:test:setup`, `db:test:cleanup`).

This document is self-contained and explains how to test the Guardian Backend API.
