# YAPPC Frontend ESLint Local Rules

Product-local ESLint rules that enforce YAPPC-specific architectural and code-quality constraints.

## Scope
- Custom lint rules used only by the YAPPC frontend workspace.
- Rule metadata, test coverage, and configuration wiring.
- Governance checks that are too product-specific for shared packages.

## Key Areas
- Rule implementations.
- Rule tests and fixtures.
- Workspace-level ESLint integration.

## Audit Notes
- Prefer focused rules tied to concrete product constraints.
- Keep diagnostics actionable and avoid overlapping generic lint behavior.