# YAPPC Domain – Service Module – Coding Guidelines

## 1. Scope

These guidelines apply to the YAPPC Domain Service module, which implements domain services and related utilities.

## 2. Layering & Dependencies

- Treat this module as part of the **domain layer**:
  - No direct HTTP or UI code.
  - No direct database or external client code.
- Depend on repositories, gateways, and other IO abstractions via interfaces provided by higher layers or shared infrastructure.

## 3. Domain Service Design

- Make services explicit about:
  - Inputs (commands or value objects).
  - Outputs (results or domain events).
  - Invariants they enforce.
- Keep services small and focused; prefer composition over large, multi-purpose classes.

## 4. Error Handling

- Represent domain errors using well-typed results (exceptions or result types) that calling layers can translate into HTTP or UI behavior.

## 5. Documentation

- Document key services with concise comments explaining their purpose, expected usage, and important invariants.

This document is self-contained and defines how to structure code in the YAPPC Domain Service module.
