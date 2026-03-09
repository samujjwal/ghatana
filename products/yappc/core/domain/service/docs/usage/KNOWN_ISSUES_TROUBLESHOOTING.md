# YAPPC Domain – Service Module – Known Issues & Troubleshooting

## 1. Known Issues

- **Domain logic spread across layers** – If rules leak into controllers or repositories, domain services become harder to reason about.
- **Tight coupling to infrastructure** – Direct use of DB or HTTP clients inside domain services breaks layering.

## 2. Troubleshooting Scenarios

### 2.1 Inconsistent Business Behavior Across Services

- **Symptom**: The same use case behaves differently in different services.
- **Checks**:
  - Confirm all services share a common domain service implementation.
  - Look for duplicated rules in controllers or repositories.

### 2.2 Difficulties Testing Domain Logic

- **Symptom**: Tests require real databases or HTTP calls to validate domain behavior.
- **Checks**:
  - Inspect domain services for direct IO calls.
  - Refactor to inject interfaces for IO.

This document is self-contained and lists common issues and mitigations for the YAPPC Domain Service module.
