# Virtual-Org – Integration Library – Coding Guidelines

## 1. Scope

These guidelines apply to the Virtual‑Org Integration library (`libs/integration`), which connects Virtual‑Org to the operator framework, AEP, and shared domain models.

## 2. Dependencies & Layering

- This module is an **integration layer**:
  - It may depend on Virtual‑Org, AEP libs, operator framework, and domain models.
  - It must not re‑implement core domain logic for those modules.
- Keep integration code focused on wiring and translation.

## 3. Adapter Design

- Encapsulate type mappings and protocol conversions in small, reusable functions or classes.
- Make contracts explicit:
  - What Virtual‑Org types map to which operator/AEP types.
  - What assumptions exist about IDs, timestamps, and schemas.

## 4. Async & Error Handling

- Use ActiveJ promises and platform patterns for async behavior.
- Propagate errors in a way that calling components (AEP, operators, services) can handle consistently.

## 5. Configuration & Environment

- Avoid embedding environment‑specific values (hosts, credentials); read them from configuration at the service layer.
- Keep integration utilities parameterized so they can be reused in multiple services.

## 6. Documentation

- Document key adapters and integration flows in this docs tree so they can be understood without external references.

This document is self-contained and defines how to structure code in the Virtual‑Org Integration library.
