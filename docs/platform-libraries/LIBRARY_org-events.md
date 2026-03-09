# Library Spec – @ghatana/org-events

Organization event contracts and DTOs generated from protobuf.

---

## 1. Purpose & Scope

- Provide **typed event contracts** for organization-related events across services.
- Act as the single source of truth for event payload shapes (generated from protobuf).

From `package.json`:

- Name: `@ghatana/org-events`.
- Description: "Organization event contracts and DTOs generated from protobuf".
- Depends on `google-protobuf` and its types.

---

## 2. Responsibilities & Boundaries

**Responsibilities:**

- Expose **generated TypeScript types and classes** for org events.
- Mirror the **protobuf schema** for cross-language consistency.

**Non-responsibilities:**

- No business logic or event processing.
- No transport or subscription logic (handled by API/realtime layers).

---

## 3. Consumers & Typical Usage

- Backend services and frontends that produce/consume org events.
- Higher-level domain libs that implement event handlers.

Conceptual example:

```ts
import { OrganizationCreatedEvent } from "@ghatana/org-events";

function handleEvent(event: OrganizationCreatedEvent) {
  // event has typed fields according to protobuf
}
```

---

## 4. Dependencies & Relationships

- Depends on `google-protobuf` runtime.
- Should remain **pure contracts**; other libs (e.g., AEP UI) should use these types when dealing with org events.

---

## 5. Gaps, Duplicates, Reuse Misses

- **Potential for type drift:**

  - If other code defines parallel TypeScript interfaces for the same events, update them to import from this library instead.

- **Versioning:**
  - Need a clear policy for versioning when protobuf schemas change.

---

## 6. Enhancement Opportunities

1. **Schema docs:**

   - Generate human-readable docs from protobuf (field meanings, examples).

2. **Event taxonomy alignment:**
   - Align naming and structure with event-type registries in AEP.

---

## 7. Usage Guidelines

- Do not hand-write event contracts in apps; import from `@ghatana/org-events` to ensure consistency.
- When updating protobuf schemas, regenerate and bump versions here first.
