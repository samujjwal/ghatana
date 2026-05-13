# YAPPC Production-Grade Plan (Canonical)

## Purpose

This is the canonical implementation plan for production-grade hardening of YAPPC.
It aligns with the active execution checklist in `products/yappc/docs/audits/yappc-todos.md` and the live status board in `products/yappc/docs/audits/IMPLEMENTATION_TRACKER.md`.

## Source of Truth

- Plan details and acceptance gates: `products/yappc/docs/audits/yappc-todos.md`
- Execution status by task: `products/yappc/docs/audits/IMPLEMENTATION_TRACKER.md`

## Governance Rules (Non-Negotiable)

- Reuse before creating.
- Keep explicit boundaries.
- No silent failures.
- Full TypeScript typing.
- Tests for every meaningful behavior change.
- Observability for important flows.
- No unsafe defaults.

## Implementation Order

1. Stabilize contracts first.
2. Fix compile, type, and API mismatches.
3. Harden authorization and scope propagation.
4. Harden lifecycle phase packet.
5. Harden generation, diff, review, and rollback.
6. Harden UI, canvas, and page-builder surfaces.
7. Integrate learning and mastery safely through platform contracts.
8. Add focused tests and remove stale paths.

## Current Status

Track progress in:
- `products/yappc/docs/audits/IMPLEMENTATION_TRACKER.md`
- `products/yappc/docs/audits/yappc-todos.md` (Execution Progress section)
