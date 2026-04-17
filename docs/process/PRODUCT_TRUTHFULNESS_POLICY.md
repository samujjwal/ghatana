# Product Truthfulness Policy

> **Owner:** Platform Team + Product Owners | **Status:** Active | **Effective:** 2026-04-17

## Purpose

Product code, README files, operator screens, and release documentation must describe runtime truth rather than aspiration. A feature is not "live", "production-ready", or "complete" unless the repository contains evidence appropriate to that claim.

## Required Status Language

Use these terms consistently in product docs and user-facing UI:

| Term | Meaning | Evidence expectation |
| --- | --- | --- |
| `implemented` | Code exists for the capability | Source and targeted tests exist |
| `verified locally` | Developers can run the capability in a local environment | Reproducible local command or test path is documented |
| `verified in integration` | Capability is exercised against real adapters or integration wiring | Integration test or equivalent evidence exists |
| `deployment-validated` | Capability has been proven in a deployment-like environment | Runbook or deployment evidence exists |
| `experimental` | Capability is unstable, partial, or dependency-sensitive | Docs explicitly describe missing proof or constraints |
| `production-ready` | Capability is deployment-validated, observable, and operationally supportable | Evidence links must exist for tests, runbooks, and readiness criteria |

## Disallowed Patterns

Do not ship these patterns in product docs or user-facing UI without evidence:

- hard-coded "live" indicators for unsupported APIs
- fabricated dependency graphs, sample metrics, or generated outputs presented as runtime facts
- benchmark claims without a reproducible benchmark path
- "ready for launch" or "production-ready" language for preview-only or dependency-sensitive capabilities
- placeholder actions that appear to mutate real runtime state when no backend path exists

## UI Rules

When a backend capability is absent or intentionally unsupported:

- show an explicit unavailable or degraded-state message
- explain the current boundary in one or two factual sentences
- offer a real next step only if one exists, such as navigating to a supported page or pointing to documentation
- never render synthetic operational data in product mode

## Documentation Rules

Product READMEs and operational docs must:

- distinguish implemented capability from validated capability
- link major readiness claims to tests, runbooks, or contract checks
- call out optional dependencies and degraded modes explicitly
- prefer capability matrices over broad marketing language

## Evidence Checklist For `production-ready`

A capability may be described as `production-ready` only when all are true:

- required dependencies are explicit and validated
- failure modes are observable through logs, metrics, or health endpoints
- automated tests cover the critical path at the right level
- no placeholder or synthetic runtime data remains in the user-facing path
- deployment and rollback guidance exists for the owning product

## Enforcement Direction

Teams should add focused checks over time for:

- unsupported readiness phrases in scoped product docs
- UI pages that still import mock operational data in production paths
- public contract drift between documented and implemented routes

Until automation is complete, reviewers must enforce this policy during product changes.
