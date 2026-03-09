# YAPPC Backend – Compliance Module – Testing Guidelines

## 1. Goals

- Ensure compliance policy and acknowledgment logic is correct, deterministic, and auditable.

## 2. Unit Tests

- Cover:
  - Policy creation, update, and versioning.
  - Status transitions (DRAFT → ACTIVE → DEPRECATED/ARCHIVED).
  - Acknowledgment recording and idempotency.
  - Search, export, and cleanup behavior.
- Use in-memory data structures; avoid external IO.

## 3. Edge Cases

- Multiple acknowledgments for same user and version.
- Very large policy sets (for search and export performance).
- Long retention periods for cleanup.

## 4. Determinism

- Drive time-based behavior (e.g., review cycles) via injectable clocks or clear test hooks where possible.

This document is self-contained and explains how to test the Compliance module.
