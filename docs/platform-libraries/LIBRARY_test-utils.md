# Library Spec – @ghatana/test-utils

Shared testing utilities for Ghatana TypeScript/React packages.

---

## 1. Purpose & Scope

- Provide **common testing helpers** for component and hook tests:
  - Rendering with providers (theme, router, state).
  - Reusable mocks and fixtures.

From `package.json`:

- Name: `@ghatana/test-utils`.
- Description: "Shared testing utilities for Ghatana TypeScript packages".
- Peer deps: `@testing-library/react`, React.
- Depends on `@ghatana/theme`.

---

## 2. Responsibilities & Boundaries

**Responsibilities:**

- Wrap React Testing Library with platform defaults.
- Avoid repetition of setup logic (e.g., theming, routing) across tests.

**Non-responsibilities:**

- No production code; this is test-only.
- No business logic or domain-specific test behaviors.

---

## 3. Consumers & Typical Usage

- Component libraries (`@ghatana/ui`, `@ghatana/design-system`).
- Apps (AEP UI, App Creator, TutorPutor) for consistent testing patterns.

Conceptual example:

```ts
import { renderWithProviders } from "@ghatana/test-utils";

const { getByText } = renderWithProviders(<MyComponent />);
```

---

## 4. Dependencies & Relationships

- Built on top of React Testing Library and `@ghatana/theme`.
- Complements `@ghatana/storybook` and `@yappc/accessibility-audit` for visual + a11y testing.

---

## 5. Gaps, Duplicates, Reuse Misses

- **Local test helpers:**
  - Some apps may still define their own `renderWithProviders`.  
    → Consolidate these into this lib.

---

## 6. Enhancement Opportunities

1. **Standard fixtures:**

   - Provide reusable mock data for common entities (pipelines, modules, users) to speed up test writing.

2. **Test recipes:**
   - Document typical patterns (testing forms, modals, async flows) using these utils.

---

## 7. Usage Guidelines

- Prefer `@ghatana/test-utils` for shared setup and avoids repeating boilerplate.
- Add new helpers here when you see patterns repeated across multiple test suites.
