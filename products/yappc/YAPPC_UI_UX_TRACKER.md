# YAPPC UI/UX Implementation Tracker

## P0 — Immediate
| ID | Task | Effort | File(s) | Status |
|---|---|---|---|---|
| P0-1 | Fix routes.spec.ts path resolution | S | web/src/__tests__/routes.spec.ts | Done |
| P0-2 | Rewrite e2e/smoke.spec.ts to current routes | M | e2e/smoke.spec.ts | Done |
| P0-3 | Rewrite e2e/accessibility-navigation.spec.ts | M | e2e/accessibility-navigation.spec.ts | Done |
| P0-4 | Remove duplicate canvas-workspace tab/route | S | routes.ts, _shell.tsx, canvas-workspace.tsx | Done |
| P0-5 | Archive legacy router/routes.tsx | S | router/routes.tsx | Done |
| P0-6 | Remove inactive auth routes from tree | S | routes/register.tsx, forgot-password.tsx | Done |
| P0-7 | Remove orphaned IDE route | S | routes/ide.tsx | Done |
| P0-8 | Fix workspace settings action routing | S | routes/app/workspaces.tsx | Done |
| P0-9 | Add confirmation before auto-creating workspace | S | routes/app/workspaces.tsx | Done |
| P0-10 | Fix runtime `require is not defined` | M | vite.config.ts | Done |

## P1 — Short-term
| ID | Task | Effort | File(s) | Status |
|---|---|---|---|---|
| P1-1 | Convert profile to read-only summary | S | routes/profile.tsx | Done |
| P1-2 | Fix not-found support link | S | routes/not-found.tsx | Done |
| P1-3 | Update test-isolation.ts default URL | S | e2e/helpers/test-isolation.ts | Done |

## P2 — Medium-term
| ID | Task | Effort | File(s) | Status |
|---|---|---|---|---|
| P2-1 | Refactor canvas orchestrator | L | routes/app/project/canvas.tsx | Done |
| P2-2 | Reframe lifecycle summary-first | M | routes/app/project/lifecycle.tsx | Done |

## Audit Findings — Completed
| ID | Task | Effort | File(s) | Status |
|---|---|---|---|---|
| YAPPC-F005 | Fix shared testing utility syntax + test-isolation types | M | platform/typescript/design-system/src/utils/testing.ts, e2e/helpers/test-isolation.ts | Done |
| YAPPC-F012 | Dashboard inline workspace health instead of routing to list | S | routes/dashboard.tsx | Done |
| YAPPC-F013 | Dashboard as primary resume surface | S | routes/dashboard.tsx | Done |
| YAPPC-F023 | Replace mock global search with real mounted navigation routes | S | components/search/GlobalSearch.tsx | Done |
| YAPPC-F025 | Deprecate materially outdated generated cross-alignment docs | S | docs-generated/02-inventory-analysis/05-cross-alignment-analysis.md | Done |
| YAPPC-F026 | Fix verification scripts pointing at old apps/web structure | S | scripts/validate-routes.ts | Done |
| YAPPC-F028 | Replace generic help with contextual guidance toggle | S | routes/_shell.tsx | Done |

## Remaining Architectural Items (Deferred to Dedicated Refactor)
| ID | Task | Effort | File(s) | Status |
|---|---|---|---|---|
| YAPPC-F021 | Centralize browser API access and token/session handling | L | lib/http.ts, services/auth/*, many route files | Not started |
| YAPPC-F022 | Consolidate localStorage/sessionStorage keys into governed cache policy | L | ~82 files with direct storage access | Not started |
| YAPPC-F024 | Consolidate CommandPalette and GlobalSearch into one surface | M | components/search/*, components/command-palette/* | Not started |

---
*Generated from YAPPC_UI_UX_AUDIT_2026-04-22.md*
