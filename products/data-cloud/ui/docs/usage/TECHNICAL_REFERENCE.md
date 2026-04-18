# Data Cloud UI – Technical Reference

## 1. Overview

This reference summarizes key technical aspects of the Data Cloud UI module.

## 2. Canonical Route Model

- Primary-user routes: `/`, `/data`, `/data/new`, `/data/:id`, `/data/:id/edit`, `/pipelines`, `/pipelines/new`, `/query`
- Operator disclosure: `/insights`, `/trust`, `/events`, `/entities`, `/memory`, `/context`, `/agents`, `/plugins`
- Admin disclosure: `/settings`
- Operator-only direct links: `/alerts` is a launcher-backed operator triage surface with a shared unsupported fallback for older deployments, and `/fabric` remains preview-only
- Compatibility aliases such as `/dashboard`, `/collections`, `/workflows`, `/sql`, and `/governance` remain available for deep-link continuity but are no longer the canonical discovery paths.
- Compatibility route handoffs: `/lineage` redirects to `/data?view=lineage`, `/quality` redirects to `/data?view=quality`, and `/governance` maps to the same Trust Center surface as `/trust`.

## 3. Session Bootstrap

- `ui/src/lib/auth/session.ts` is the central source for tenant bootstrap, auth presence, API base URL, and shell role.
- The shell role is persisted in session storage and drives sidebar and global-search disclosure.
- Missing tenant context is treated as a runtime boundary instead of silently falling back to `default` or `default-tenant`.

## 4. Core Concepts (Conceptual)

- Views and flows for tenants, collections, entities, and relationships.

This technical reference is self-contained and describes the primary surfaces of the Data Cloud UI module.
