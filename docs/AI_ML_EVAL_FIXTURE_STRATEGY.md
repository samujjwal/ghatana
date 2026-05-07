# AI/ML Eval Fixture Strategy

This document closes `B-4` by defining one committed, versioned fixture strategy shared by the current AI/ML modules.

## Scope

Committed fixture roots:

- `platform/java/ai-integration/src/test/resources/eval-fixtures`
- `products/yappc/core/ai/src/test/resources/eval-fixtures`
- `products/data-cloud/planes/action/agent-runtime/src/test/resources/eval-fixtures`

## Format and layout

Each fixture root uses the same structure:

- `manifest.json`: dataset metadata, ownership, and acceptance thresholds.
- `cases/*.jsonl`: deterministic test cases with one JSON object per line.

`manifest.json` must include:

- `suite`: suite identifier
- `version`: semantic fixture version
- `owner`: owning team/module
- `updatedAt`: ISO timestamp
- `thresholds`: baseline gating values
- `caseFiles`: relative file list

Each JSONL case must include:

- `id`: stable case id
- `input`: prompt/request payload
- `expected`: expected output constraints or labels
- `tags`: scenario tags (e.g. `safety`, `routing`, `deterministic`)

## Ownership model

- `platform/java/ai-integration`: owned by platform AI integration maintainers
- `products/yappc/core/ai`: owned by YAPPC AI maintainers
- `products/data-cloud/planes/action/agent-runtime`: owned by AEP runtime maintainers

Ownership is encoded directly in each `manifest.json` and reviewed with fixture changes.

## Change policy

- Fix-forward only: update canonical fixture files in place.
- Any threshold loosening requires explicit reviewer sign-off in PR notes.
- New model capabilities add new case IDs; existing IDs are immutable.

