# 27. Login – Authentication Entry – Deep-Dive Spec

> **Document Version:** 2.0.0 (2025-12-29)  
> **Last Updated:** December 29, 2025 – Updated with implementation details

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 6. Login & Page Builder](../APP_CREATOR_PAGE_SPECS.md#6-login--page-builder)

**Code files:**

| File | Purpose |
|------|---------|
| `src/routes/login.tsx` | Login page route |
| `src/routes/_root.tsx` | Root layout with session handling |

**Routes:**

| Route | Purpose |
|-------|---------|
| `/login` | Authentication entry point |

**Authentication Flow:**

1. User arrives at `/login`
2. Enter credentials or click "Demo User"
3. On success, redirect to `/app/workspaces`
4. Session managed via cookies/localStorage

> **Note:** Current implementation is placeholder auth. Production will use OAuth/OIDC.

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **simple login form** for username/password authentication and a demo-user shortcut into the app.

**Primary goals:**

- Let users enter credentials to sign in.
- Show clear error messages on failed login.
- Provide a "Continue as Demo User" option for quick evaluation.

**Non-goals:**

- Implement full production auth stack; current logic is placeholder.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- Any user of the App Creator web app (developers, leads, platform engineers).

**Key scenarios:**

1. **Standard login**
   - User enters username/password.
   - On success (future real auth), redirected to `/app/workspaces`.

2. **Demo access**
   - User clicks "Continue as Demo User".
   - Skips credential entry and lands in demo workspace.

3. **Session expiration**
   - Coming from `/auth/login?reason=session-expired`, shows alert that session expired.

---

## 3. Content & Layout Overview

- **Form fields:**
  - Username.
  - Password.
- **Buttons:**
  - Submit (Login).
  - Continue as Demo User.
- **Errors:**
  - Inline error alert when credentials invalid or session expired.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Clear messaging:**
  - Error messages in plain language ("We couldn't sign you in" etc.).
- **Session-expired explanation:**
  - Simple text if `reason=session-expired` is present.

---

## 5. Completeness and Real-World Coverage

Login should support:

1. Integration with real auth/session management (future).
2. Redirects back to intended target page after login.

---

## 6. Modern UI/UX Nuances and Features

- **Password handling:**
  - Show/hide password toggle (future enhancement).
- **Accessibility:**
  - Proper labels and keyboard focus management.

---

## 7. Coherence and Consistency Across the App

- Post-login landing (workspaces) must match `/app/workspaces` behavior and messaging.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#6-login--page-builder`
- Route implementation: `src/routes/login.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Hook into real auth/session provider.
2. Support redirect to last visited workspace/project.
3. Improve demo-user story and explain its limitations.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Sign in to YAPPC App Creator

[ Alert area (optional) ]
- Example: "Your session expired. Please sign in again." (yellow info banner)

Form
-------------------------------------------------------------------------------
Username: [______________________]
Password: [______________] [👁]

[ Sign in ]    [ Continue as Demo User ]

Footer
-------------------------------------------------------------------------------
"By continuing, you agree to the internal use policy."
```
