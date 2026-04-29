# @aep/ui

## Purpose

`products/aep/ui` is the AEP Pipeline Builder — a React 19 / Vite single-page application for visual pipeline design. It owns:

- Visual pipeline canvas (drag-and-drop stage composition using `@ghatana/canvas`)
- Pipeline and agent management UI (create, edit, deploy, monitor)
- Real-time execution status via WebSocket (through the `gateway`)
- Canvas plugin integration (`canvas-plugin/`) for AEP-specific stage widgets

## Boundaries

- **Technology:** React 19, TypeScript, Vite, `@ghatana/design-system`, `@ghatana/canvas`
- **Backend communication:** exclusively through `gateway` — never directly to the Java `server`
- **Does not own:** state persistence — that is managed by the backend; auth — that is managed by `gateway`

## Usage

```bash
# Development (requires gateway running on localhost:3000)
pnpm dev

# Production build
pnpm build
```

## Key directories

| Directory | Contents |
|---|---|
| `src/components/` | Pipeline, agent, and stage UI components |
| `src/canvas-plugin/` | AEP-specific `@ghatana/canvas` stage plugins |
| `src/hooks/` | React hooks for pipeline state, WebSocket, and API calls |
| `src/api/` | Typed API clients (generated from `contracts/openapi.yaml`) |
| `src/generated/` | Auto-generated API client — do not edit manually |

## Verification

```bash
pnpm test
pnpm typecheck
```
