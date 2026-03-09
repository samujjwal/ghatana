# Virtual-Org Java Framework – Testing Guidelines

## 1. Goals

- Ensure that core framework abstractions behave deterministically and are safe for reuse.

## 2. Unit Tests

- Cover:
  - Organization and department lifecycle behavior.
  - Agent and workflow state transitions.
  - Event emission contracts.
- Avoid external dependencies; use in-memory fakes for event and state backends.

## 3. Integration Tests

- Where the framework integrates with event runtime or state modules, provide integration tests using shared test utilities.

## 4. Backwards Compatibility

- When evolving APIs, add tests to ensure prior contracts remain valid or are clearly deprecated.

This document is self-contained and explains how to test the Virtual-Org Java Framework module.
