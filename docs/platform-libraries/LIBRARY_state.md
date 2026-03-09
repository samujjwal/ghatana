# Library Spec – @ghatana/state

Lightweight state management helpers on top of `useSyncExternalStore`.

---

## 1. Purpose & Scope

- Offer **simple, predictable state containers** for React apps.
- Provide patterns for global and local state that integrate well with API/realtime layers.

From `package.json`:

- Name: `@ghatana/state`.
- Description: "Lightweight state management helpers built on top of useSyncExternalStore".
- Peer dep: React ^18 or ^19.

---

## 2. Responsibilities & Boundaries

**Responsibilities:**

- Wrap `useSyncExternalStore` in ergonomic helpers.
- Provide basic store patterns (e.g., createStore, selectors) without heavy dependencies.

**Non-responsibilities:**

- No domain-specific stores (pipelines, modules, etc.).
- No direct API or realtime sockets; those should be plugged into stores at the app layer.

---

## 3. Consumers & Typical Usage

- Shared or app-specific stores in AEP UI, App Creator, TutorPutor.
- Hooks that want a **light store abstraction** without bringing in Redux/Zustand.

Conceptual example:

```ts
import { createStore } from "@ghatana/state";

const useCounter = createStore({ count: 0 }, (state, set) => ({
  increment: () => set({ count: state.count + 1 }),
}));

const count = useCounter((s) => s.count);
```

---

## 4. Dependencies & Relationships

- Peer dep: React.
- Sits between pure logic and UI; should not depend on UI/theme/design-system.

---

## 5. Gaps, Duplicates, Reuse Misses

- **Ad-hoc state patterns:**

  - Apps may have local `useSyncExternalStore` wrappers.  
    → Consolidate into `@ghatana/state` where possible.

- **Interplay with realtime and API:**
  - Need clear patterns for connecting `@ghatana/realtime` and `@ghatana/api` updates into state stores.

---

## 6. Enhancement Opportunities

1. **Reference patterns:**

   - Document patterns like "store per domain" vs "global store" and show examples.

2. **Devtools-friendly APIs:**
   - Consider small integration points for debugging (e.g., log store updates in dev).

---

## 7. Usage Guidelines

- Prefer `@ghatana/state` when you need persistent/shared state between components but want to avoid heavy libraries.
- Keep store definitions thin and domain-agnostic; business rules live closer to the app.
