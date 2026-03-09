# YAPPC Domain – Service Module – Testing Guidelines

## 1. Goals

- Ensure domain services behave correctly and deterministically, independent of transport and persistence.

## 2. Unit Tests

- Focus on:
  - Business rules and invariants enforced by each service.
  - Interactions between domain objects (aggregates, value objects, events).
- Use mocks or fakes for repositories and external gateways.

## 3. Test Structure

- Express tests in terms of domain scenarios (GIVEN/WHEN/THEN) rather than implementation details.
- Keep fixtures small and focused on the behavior under test.

## 4. Determinism & Isolation

- Avoid time or environment dependencies; inject clocks and configuration where needed.
- Ensure tests can run in isolation and in any order.

This document is self-contained and explains how to test the YAPPC Domain Service module.
