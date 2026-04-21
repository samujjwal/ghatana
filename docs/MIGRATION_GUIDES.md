# Product Migration Guides

Step-by-step migration guides for each product's library consolidation.

---

## Platform Kernel Migration

The platform kernel extraction migration status is tracked in a single canonical location:

**Canonical Source:** [`platform-kernel/MIGRATION_STATUS.md`](../platform-kernel/MIGRATION_STATUS.md)

This document tracks the migration of kernel modules from `platform/java/` to the new `platform-kernel/` composite build structure.

### Quick Reference

- **Migration Date:** 2026-04-05
- **Status:** Complete
- **New Structure:** `platform-kernel/kernel-core`, `platform-kernel/kernel-plugin`, `platform-kernel/kernel-persistence`
- **Archived Modules:** `platform/java/.archived/kernel`, `platform/java/.archived/plugin`, `platform/java/.archived/kernel-persistence`

For detailed migration information, refer to the canonical [`platform-kernel/MIGRATION_STATUS.md`](../platform-kernel/MIGRATION_STATUS.md).

---

## YAPPC Migration Guide

### What Changed (15 → 4 libraries)

| Before | After |
|--------|-------|
| `@yappc/auth` | `@yappc/core` (auth module) |
| `@yappc/chat` | `@yappc/core` (chat module) |
| `@yappc/security` | `@yappc/core` (security module) |
| `@yappc/testing` | `@yappc/core` (testing module) |
| `@yappc/canvas` | Removed — use `@ghatana/canvas` directly |
| `@yappc/ui` (token exports) | Use `@ghatana/tokens` directly |
| YAPPC-local string/date utils | Use `@ghatana/platform-utils` |
| YAPPC-local state management | Use `@ghatana/state` |
| YAPPC-local auth state | Use `@ghatana/state` with `createPersistentAtom` |

### Migration Steps

**Step 1: Replace `@yappc/auth`, `@yappc/chat`, `@yappc/security`, `@yappc/testing` imports**

```ts
// Before
import { AuthService } from "@yappc/auth";
import { ChatManager } from "@yappc/chat";

// After
import { AuthService } from "@yappc/core";
import { ChatManager } from "@yappc/core";
```

**Step 2: Replace `@yappc/canvas` with `@ghatana/canvas`**

```ts
// Before
import { FlowCanvas } from "@yappc/canvas";

// After
import { FlowCanvas } from "@ghatana/canvas";
```

**Step 3: Replace token re-exports from `@yappc/ui`**

```ts
// Before
import { colorTokens, spacingTokens } from "@yappc/ui";

// After
import { colorTokens, spacingTokens } from "@ghatana/tokens";
```

**Step 4: Replace utility functions with platform-utils**

```ts
// Before
import { truncate, capitalize } from "@yappc/core/utils";

// After
import { truncate, capitalize } from "@ghatana/platform-utils";
```

---

## Audio-Video Migration Guide

### What Changed

Audio-Video UI (`@audio-video/ui`) no longer contains duplicate UI primitive implementations.

| Before | After |
|--------|-------|
| Local `Button`, `Input`, `Card`, `Modal`, etc. | `@ghatana/design-system` re-exports |
| Local token imports | `@ghatana/tokens` re-exports |
| Audio-video hooks | Still in `@audio-video/ui` (kept) |

### Migration Steps

**Step 1: Update `@audio-video/ui` imports (no change needed for consumers)**

Consumers of `@audio-video/ui` continue to import `Button`, `Input`, `Card` from it — the package now re-exports them from `@ghatana/design-system`. No import path changes are required for consumers.

**Step 2: For direct platform imports (preferred)**

```ts
// Preferred for new code
import { Button, Card } from "@ghatana/design-system";
import { useSpeechSynthesis } from "@audio-video/ui"; // audio-video-specific hooks
```

---

## Data-Cloud Migration Guide

### What Changed

Data-Cloud UI split into application (`@data-cloud/ui`) and library (`@data-cloud/ui-components`).

| Before | After |
|--------|-------|
| Reusable components co-located in app | `@data-cloud/ui-components` library |
| App-level component exports | Internal to `@data-cloud/ui` app |

### Migration Steps

**Step 1: Replace direct component imports from the Data-Cloud app**

If any package was importing reusable components directly from the Data-Cloud app, migrate to the new library:

```ts
// Before (wrong — importing from an app, not a library)
import { StatusBadge } from "@data-cloud/ui/src/components/common/StatusBadge";

// After
import { StatusBadge } from "@data-cloud/ui-components";
```

**Step 2: Add the dependency**

```json
"@data-cloud/ui-components": "workspace:*"
```

---

## Flashit Migration Guide

### What Changed

`@flashit/shared` utilities now delegate to `@ghatana/platform-utils` for generic functions.

| Function | Old location | New canonical location |
|----------|-------------|----------------------|
| `truncate` | `@flashit/shared/utils` | `@ghatana/platform-utils` |
| `capitalize` | `@flashit/shared/utils` | `@ghatana/platform-utils` |
| `formatDate` | `@flashit/shared/utils` | `@ghatana/platform-utils` |
| `getCurrentTimestamp` | `@flashit/shared/utils` | `@ghatana/platform-utils` |
| `toTitleCase` | `@flashit/shared/utils` | `@flashit/shared/utils` (kept, flashit-specific) |
| `randomString` | `@flashit/shared/utils` | `@flashit/shared/utils` (kept, flashit-specific) |
| `slugify` | `@flashit/shared/utils` | `@flashit/shared/utils` (kept, flashit-specific) |
| `formatRelativeTime` | `@flashit/shared/utils` | `@flashit/shared/utils` (kept, uses date-fns) |
| `isValidISODate` | `@flashit/shared/utils` | `@flashit/shared/utils` (kept, flashit-specific) |

### Migration Steps

**Step 1: Re-export compatibility — no immediate action required**

`@flashit/shared/utils` exports all functions. The platform-owned functions are re-exported through it, so existing imports still work.

**Step 2: For new code, prefer platform-utils directly**

```ts
// For truly generic utilities in new code
import { truncate, capitalize, formatDate } from "@ghatana/platform-utils";

// Flashit-specific
import { toTitleCase, randomString, slugify } from "@flashit/shared";
```

---

## General Platform Adoption Guide

### When to use platform libraries

| Concern | Use |
|---------|-----|
| UI primitives (Button, Input, Card) | `@ghatana/design-system` |
| String/date formatting utilities | `@ghatana/platform-utils` |
| HTTP calls to backend APIs | `@ghatana/api` |
| Real-time events (WebSocket/SSE) | `@ghatana/realtime` |
| Cross-component event bus | `@ghatana/events` |
| Browser interaction events | `@ghatana/browser-events` |
| State atoms and persistence | `@ghatana/state` |
| Config/env/feature flags | `@ghatana/config` |
| Visualisation (flow, canvas) | `@ghatana/canvas` |

### When NOT to use platform libraries

- Product-specific domain logic (business rules, entities)
- Product-specific UI patterns that don't generalise
- One-off utilities not shared across multiple products

### Anti-patterns

```ts
// ❌ Don't reimplement platform utilities
export function truncate(s: string, n: number) { /* ... */ }

// ❌ Don't use banned libraries
import _ from "lodash";
import axios from "axios";
import moment from "moment";

// ❌ Don't import between products
import { GuardianService } from "@dcmaar/guardian-core"; // in @yappc/...

// ✅ Do use platform
import { truncate } from "@ghatana/platform-utils";
import { ApiClient } from "@ghatana/api";
```

---

## Troubleshooting

### "Module not found: @ghatana/platform-utils"

1. Add to `dependencies` in `package.json`: `"@ghatana/platform-utils": "workspace:*"`
2. Run `pnpm install --filter <your-package>`

### TypeScript errors after migrating

- Check `tsconfig.json` has `moduleResolution: bundler` or `node16`
- Verify `skipLibCheck: true` for platform packages with complex type exports

### ESLint warnings about duplicate utilities

Rule `ghatana/no-duplicate-utilities` fires when you define a function with the same name as a platform-utils export. Fix: delete the local implementation and import from `@ghatana/platform-utils`.

### ESLint errors about cross-product imports

Rule `ghatana/no-cross-product-imports` fires when importing between product scopes. Fix: extract the shared concern to a platform package or `shared-services`.
