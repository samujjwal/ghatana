# Virtual-Org – Java VirtualOrg-Agent Module – Testing Guidelines

## 1. Goals

- Ensure that agent abstractions and implementations behave deterministically and are safe for reuse.

## 2. Unit Tests

- Cover:
  - Agent lifecycle behavior.
  - Role- and capability-based decisions.
  - Event emission in response to inputs.
- Use in-memory fakes for dependencies; avoid external IO.

## 3. Integration Tests

- When agents integrate with the event runtime or workflows, include small integration tests to validate event shapes and transitions.

## 4. Coverage

- Aim for high coverage (≥80%) over public agent APIs and key implementations.

This document is self-contained and explains how to test the Java `virtualorg-agent` module.
