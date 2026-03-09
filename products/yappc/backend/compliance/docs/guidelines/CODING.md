# YAPPC Backend – Compliance Module – Coding Guidelines

## 1. Scope

These guidelines apply to the TypeScript Compliance module that models policies, frameworks, assessments, and audit data.

## 2. Domain-First Design

- Keep this module focused on **domain logic**:
  - Policy status and version transitions.
  - Acknowledgment tracking and coverage.
  - Review-cycle calculations and retention rules.
- Avoid coupling to HTTP, databases, or specific UI concerns.

## 3. Types & Immutability

- Use strong types and enums (e.g., `PolicyStatus`) for states and statuses.
- Prefer immutable operations (return new structures) unless performance dictates otherwise.

## 4. Reuse & Composition

- Add helpers and managers (e.g., `CompliancePolicyManager`) that can be reused across services.
- Keep interfaces generic enough for multiple products to consume.

## 5. Documentation

- Key types and functions should have concise inline documentation explaining purpose and expected behavior.

This document is self-contained and defines how to structure code in the Compliance module.
