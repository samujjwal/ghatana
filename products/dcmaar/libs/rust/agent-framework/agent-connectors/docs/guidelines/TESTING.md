# DCMaar Agent Connectors – Testing Guidelines

## 1. Goals

- Ensure connectors interact correctly with external systems and handle failures gracefully.

## 2. Unit & Integration Tests

- Use fakes/mocks for remote endpoints.
- Where needed, add integration tests against local or containerized services.

This document is self-contained and explains how to test `agent-connectors`.
