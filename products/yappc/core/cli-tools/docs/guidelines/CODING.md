# YAPPC Core – KG CLI Tools – Coding Guidelines

## 1. Scope

These guidelines apply to the KG CLI Tools module, which provides command-line utilities for managing the Knowledge Graph.

## 2. Layering & Responsibilities

- Treat CLI code as an **adapter layer**:
  - Keep domain logic (graph operations) in shared services or helper classes.
  - Keep input/output (arguments, stdout/stderr, file IO) in CLI-specific code.
- Reuse `kg-core` abstractions rather than re-defining graph concepts.

## 3. CLI Design

- Prefer clear, composable commands over over-loaded single commands.
- Use consistent naming and argument patterns across commands.
- Provide helpful usage output and clear error messages.

## 4. Configuration & Safety

- Read configuration via `config-runtime` APIs.
- Avoid hard-coded environment-specific values.
- Add dry-run or confirmation options for potentially destructive commands.

## 5. Documentation

- Document major commands and flags in this docs tree so they can be referenced without external documents.

This document is self-contained and defines how to structure code in the KG CLI Tools module.
