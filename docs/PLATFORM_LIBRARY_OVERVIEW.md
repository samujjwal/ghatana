# Platform Library Overview

A reference for all canonical platform TypeScript libraries in the Ghatana monorepo.

**Dependency direction**: tokens/theme → platform-utils → design-system/canvas/realtime/events → domain packages

---

## Library Catalogue

| Package | Purpose | Key Exports |
|---------|---------|-------------|
| `@ghatana/tokens` | Design tokens | Color, spacing, typography, animation tokens |
| `@ghatana/theme` | Theme provider | `ThemeProvider`, `useTheme`, CSS variable injection |
| `@ghatana/design-system` | UI component library | `Button`, `Input`, `Card`, `Modal`, 40+ components |
| `@ghatana/platform-utils` | Shared utilities | `cn`, `truncate`, `formatDate`, `debounce`, `formatBytes` |
| `@ghatana/api` | HTTP client | `ApiClient`, request interceptors, retry logic |
| `@ghatana/realtime` | Real-time comms | WebSocket client, SSE client, React hooks |
| `@ghatana/events` | Event bus | `EventDispatcher`, `EventPayload`, typed events |
| `@ghatana/browser-events` | Browser events | Mouse, keyboard, clipboard, focus event handlers |
| `@ghatana/state` | State management | `AsyncState<T>`, atoms, persistence, React hooks |
| `@ghatana/config` | Configuration | `loadEnv`, `createConfig`, `createFeatureFlags` |
| `@ghatana/canvas` | Canvas & flow UI | Flow canvas, node/edge types, viewport                 |
| `@ghatana/charts` | Chart components | Declarative chart wrappers |
| `@ghatana/i18n` | Internationalisation | Translation loading, `useTranslation` |
| `@ghatana/eslint-plugin` | Lint governance | Architecture boundary, duplication, migration rules |

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
import { createAtom, createAsyncAtom, useStateAtom } from "@ghatana/state";

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

1. **Foundation** (`tokens`, `theme`): no dependencies on other Ghatana packages.
2. **Platform base** (`platform-utils`, `config`): may depend on foundation only.
3. **Platform capabilities** (`design-system`, `api`, `realtime`, `events`, `canvas`, `charts`, `state`): may depend on foundation and platform base.
4. **Products** (`@yappc/*`, `@dcmaar/*`, etc.): may depend on any platform layer; must NOT depend on other products.

Enforced by `@ghatana/eslint-plugin` rule `enforce-platform-boundaries`.

---

## Adding a New Platform Library

See [Library Governance Process](./LIBRARY_GOVERNANCE.md) for the RFC + approval flow.

**Checklist before opening an RFC:**
- [ ] The concern is not already covered by an existing platform library
- [ ] The library will be used by 2+ products
- [ ] No product-specific logic leaks into the new library
- [ ] A README, typed exports, and ≥80% test coverage are included from day 1
