# Library Spec – @ghatana/realtime

WebSocket and Server-Sent Events (SSE) helpers for realtime features.

---

## 1. Purpose & Scope

- Provide **client-side abstractions** for connecting to realtime backends:
  - WebSockets.
  - Server-Sent Events (SSE).
- Help apps manage subscriptions, reconnection, and message handling.

From `package.json`:

- Name: `@ghatana/realtime`.
- Description: "WebSocket and SSE helpers for Ghatana realtime clients".
- Peer dep: React ^19.0.0.

---

## 2. Responsibilities & Boundaries

**Responsibilities:**

- Manage lifecycle of realtime connections.
- Provide a **small set of primitives** (e.g., connect, subscribe, send, disconnect) and possibly React hooks.
- Integrate with `@ghatana/state` where appropriate for subscribing to updates.

**Non-responsibilities:**

- Domain-specific event handling (e.g., pipeline events vs tutoring events).  
  Those belong in higher-level modules or apps.

---

## 3. Consumers & Typical Usage

- AEP UI, App Creator, and other apps needing realtime views (alerts, logs, dashboards).
- Domain-specific client libraries that build on top of the generic realtime layer.

Conceptual example:

```ts
import { createRealtimeClient } from "@ghatana/realtime";

const client = createRealtimeClient({ url: "wss://api.ghatana.com/realtime" });
client.subscribe("alerts", (message) => {
  /* update state */
});
```

---

## 4. Dependencies & Relationships

- Sits beside `@ghatana/api` and `@ghatana/state`.
- Should be designed so that **state stores** can easily consume its events.

---

## 5. Gaps, Duplicates, Reuse Misses

- **React peer version alignment:**

  - Uses React ^19.0.0 while some consumers may still be on 18.x.

- **Potential per-app websocket clients:**
  - Where apps have hand-rolled WebSocket logic, they should migrate to `@ghatana/realtime`.

---

## 6. Enhancement Opportunities

1. **React hook wrappers:**

   - Export `useRealtimeSubscription` hooks that integrate cleanly with `@ghatana/state`.

2. **Backoff & resilience policies:**

   - Centralize reconnection strategies and document them.

3. **Observability integration:**
   - Emit structured events for connection lifecycle to feed monitoring.

---

## 7. Usage Guidelines

- Build common realtime patterns (e.g., presence, pub/sub channels) here rather than repeating them per app.
- Keep the core small and predictable, with clear extension points for domain-specific protocols.
