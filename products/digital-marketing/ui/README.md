# @dmos/ui — DMOS Frontend

React + TypeScript UI for the **Digital Marketing Operating System (DMOS)**.

## Routes

| Path | Description |
|---|---|
| `/login` | Authentication entry point |
| `/workspaces/:workspaceId/dashboard` | Workspace overview dashboard (F1-024) |
| `/workspaces/:workspaceId/approvals` | Approval queue — pending items by risk/type (F1-023) |
| `/workspaces/:workspaceId/approvals/:requestId` | Approval detail — review and decide (F1-023) |

## Tech Stack

- React 19 + TypeScript strict
- React Router v7
- TanStack Query v5
- Jotai
- Tailwind CSS v4
- Vitest + React Testing Library
- Playwright (E2E)

## Scripts

```bash
pnpm dev           # Start dev server (port 5174)
pnpm build         # Type check + Vite build
pnpm test          # Vitest unit tests
pnpm type-check    # tsc --noEmit
pnpm lint          # ESLint strict
pnpm test:e2e      # Playwright E2E tests
```

## Key modules

- `src/api/approvals.ts` — typed API client for `dm-api` approval endpoints
- `src/hooks/useApprovalQueue.ts` — pending approval queue hook
- `src/hooks/useApprovalDetail.ts` — approval detail + snapshot hook
- `src/pages/ApprovalQueuePage.tsx` — F1-023 reviewer queue
- `src/pages/ApprovalDetailPage.tsx` — F1-023 approval detail / decide
- `src/pages/DashboardPage.tsx` — F1-024 workspace dashboard
- `src/lib/feature-flags.ts` — flag registry (GA/EXPERIMENTAL)
- `src/context/AuthContext.tsx` — auth token + workspace/tenant context
