# Virtual-Org – Service – Testing Guidelines

## 1. Goals

- Ensure `virtual-org-service` correctly exposes Virtual-Org capabilities and behaves reliably under load.

## 2. Unit Tests

- Cover application-layer use cases and mapping between DTOs and framework/domain types.

## 3. Integration Tests

- HTTP integration tests for key endpoints (organization, agent, workflow operations).
- Integration with Virtual-Org framework modules and event/state backends using shared test utilities.

## 4. Coverage Targets

- Aim for ≥80% coverage on application logic and critical endpoints.

This document is self-contained and explains how to test the `virtual-org-service` application.
