# YAPPC DevSecOps Library

Shared DevSecOps-oriented integrations and helpers used by the YAPPC frontend stack.

## Scope
- Product-local integrations for engineering, security, and delivery tooling.
- Shared types and service adapters for DevSecOps workflows.
- Supporting UI or utility code for security and operational features.

## Key Areas
- Integration clients and adapters.
- Shared DevSecOps models and helpers.
- Product-local service wiring.

## Audit Notes
- Keep external-tool integration isolated from UI presentation logic.
- Make failures and unsupported operations observable to calling code.