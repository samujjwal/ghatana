# Platform TypeScript Libraries — Index

> **Owner:** Platform Team | **Status:** Active | **Last Updated:** 2026-04-14

Reference for all canonical `@ghatana/*` TypeScript platform libraries.

**Dependency direction:** `tokens` / `theme` → `platform-utils` → `design-system` / `canvas` / `realtime` / `events` → domain packages. Products must never depend on each other through `@ghatana/*` libraries.

---

## Library Catalogue

| Package | Location | Purpose | Key Exports |
|---------|----------|---------|-------------|
| `@ghatana/tokens` | `platform/typescript/tokens` | Framework-agnostic design tokens | Color, spacing, typography, animation tokens |
| `@ghatana/theme` | `platform/typescript/theme` | Theme provider | `ThemeProvider`, `useTheme`, CSS variable injection |
| `@ghatana/design-system` | `platform/typescript/design-system` | WCAG AA UI component library | `Button`, `Input`, `Card`, `Modal`, 40+ components |
| `@ghatana/platform-utils` | `platform/typescript/foundation/platform-utils` | Cross-cutting utilities | `cn`, `truncate`, `formatDate`, `debounce`, `formatBytes`, a11y helpers |
| `@ghatana/api` | `platform/typescript/api` | HTTP client | `ApiClient`, request interceptors, retry logic |
| `@ghatana/realtime` | `platform/typescript/realtime` | Real-time comms | WebSocket client, SSE client, React hooks |
| `@ghatana/events` | `platform/typescript/events` | Typed event bus | `EventDispatcher`, `EventPayload`, typed events |
| `@ghatana/browser-events` | `platform/typescript/browser-events` | Browser event handlers | Mouse, keyboard, clipboard, focus event handlers |
| `@ghatana/state` | `platform/typescript/state` | State management | `AsyncState<T>`, atoms, persistence, React hooks |
| `@ghatana/config` | `platform/typescript/config` | Configuration + feature flags | `loadEnv`, `createConfig`, `createFeatureFlags` |
| `@ghatana/canvas` | `platform/typescript/canvas` | Canvas and flow UI | Flow canvas, node/edge types, viewport |
| `@ghatana/charts` | `platform/typescript/charts` | Chart components | Declarative chart wrappers built on Recharts |
| `@ghatana/i18n` | `platform/typescript/i18n` | Internationalisation | Translation loading, `useTranslation` |
| `@ghatana/eslint-plugin` | `platform/typescript/eslint-plugin` | Architecture lint rules | Boundary, duplication, migration rules |
| `@ghatana/code-editor` | `platform/typescript/code-editor` | Monaco editor component | Lazily-loaded, syntax highlighting |
| `@ghatana/platform-shell` | `platform/typescript/platform-shell` | Platform chrome | `NavBar`, `TenantSelector`, `NotificationCenter` |
| `@ghatana/sso-client` | `platform/typescript/sso-client` | SSO / JWT auth | Token management, login flows |
| `@ghatana/platform-testing` | `platform/typescript/testing` | Shared test helpers | Accessibility, WCAG, performance testing utilities |
| `@ghatana/ui-integration` | `platform/typescript/ui-integration` | AI + collaboration layer | AI features, multi-user integration |
| `@ghatana/accessibility-audit` | `platform/typescript/accessibility-audit` | Automated WCAG audit | axe-core integration, CI audit helpers |

Each library has a detailed spec in `LIBRARY_<name>.md` in this directory.

---

## Quick-Start Patterns

### Environment validation
```ts
import { z } from "zod";
import { loadEnv, BaseEnvSchema } from "@ghatana/config";

const Env = BaseEnvSchema.extend({ DATABASE_URL: z.string().url() });
export const env = loadEnv(Env);
```

### HTTP client
```ts
import { createApiClient } from "@ghatana/api";
const client = createApiClient({ baseUrl: env.API_BASE_URL });
const data = await client.get<User[]>("/users");
```

### UI components
```ts
import { Button, Card, Input } from "@ghatana/design-system";
import { cn } from "@ghatana/platform-utils";
```

### State management
```ts
import { createAtom, useStateAtom } from "@ghatana/state";
const counterAtom = createAtom(0);
function Counter() {
  const [count, setCount] = useStateAtom(counterAtom);
  return <button onClick={() => setCount(count + 1)}>{count}</button>;
}
```

### Feature flags
```ts
import { createFeatureFlags } from "@ghatana/config";
const flags = createFeatureFlags({
  newUI: { type: "boolean", enabled: process.env.NODE_ENV !== "production" },
  rollout: { type: "rollout", percentage: 20 },
});
if (flags.isEnabled("newUI")) { ... }
```

---

## Layer Rules

- `@ghatana/tokens` must not import from any other `@ghatana/*` package
- `@ghatana/theme` may only import from `@ghatana/tokens`
- `@ghatana/design-system` may import from `tokens`, `theme`, `platform-utils`
- `@ghatana/canvas` may import from `tokens`, `theme`, `platform-utils`, `design-system`
- No `@ghatana/*` library may import from a product package (`@yappc/*`, `@dcmaar/*`, etc.)

Violations are caught by `scripts/check-platform-package-governance.js` in CI.

---

## Cross-Cutting Notes

- **Accessibility:** Runtime component-level a11y helpers live in `@ghatana/platform-utils`. Deep auditing and CI reporting lives in `@ghatana/accessibility-audit`.
- **`@ghatana/ui`** was removed in V4.1 → replaced by `@ghatana/design-system`. Do not import `@ghatana/ui`.
- **`@ghatana/dcmaar` / `@ghatana/dcmaar-backend-shim`** are `"private": true` workspace entries inside `products/dcmaar/`, not library packages.

---

## Related Documents

- [GOVERNANCE.md](../GOVERNANCE.md) — Package naming rules, ownership, RFC process
- [../ONBOARDING.md](../ONBOARDING.md) — First-build guide

- **Design system layering:**

  - `tokens` → `theme` → `ui` → `design-system` is the intended stack.
  - Apps should generally depend on `design-system` **or** a curated subset, not mix random imports from all four libraries unless necessary.

- **Storybook & test utilities as shared infra:**

  - `@ghatana/storybook` should centralize Storybook config for all component libs.
  - `@ghatana/test-utils` should be the preferred source for React Testing Library setups.

- **State & realtime alignment:**

  - `@ghatana/state` and `@ghatana/realtime` should share patterns for subscriptions and store updates (e.g., consistent hooks/signatures).

- **API consistency:**
  - `@ghatana/api` is the canonical fetch client layer; ad-hoc `fetch` wrappers in apps should be migrated here where possible.

---

## 3. Common Gaps, Duplicates, and Reuse Misses (High-Level)

- **Accessibility duplication risk:**

  - Some a11y helpers (contrast, focus styles, ARIA) live in `@ghatana/platform-utils`.  
    Specs recommend keeping `platform-utils` as the canonical home for generic a11y helpers and using `@yappc/accessibility-audit` only for axe/CI-specific features.

- **Design tokens & theme leakage:**

  - It’s easy for apps or components to hardcode colors/spacings instead of using `tokens`/`theme`.  
    Specs recommend: all visual constants come from `@ghatana/tokens` or `@ghatana/theme`.

- **Multiple React version ranges:**

  - Some libs peer-depend on React 18, others on React 19.2.
  - Specs call this out as a **risk** and recommend consolidating peerDependencies for React across all UI libs.

- **Ad-hoc Storybook configs:**

  - Apps may ship their own `.storybook` rather than layering on `@ghatana/storybook`.

- **Ad-hoc test helpers:**
  - Older apps may have local RTL helpers instead of importing `@ghatana/test-utils`.

Each individual library spec goes into specific gaps and enhancement ideas.

---

## 4. How to Use These Specs

- When adding or refactoring shared code, check the relevant `LIBRARY_*.md` first:
  - Does the change belong in `utils` vs `ui` vs `design-system`?
  - Is there an existing helper or component that should be reused?
- When creating new libraries, use this layering and naming as a guide.
- When updating peer deps or React versions, update the **entire stack** consistently.
