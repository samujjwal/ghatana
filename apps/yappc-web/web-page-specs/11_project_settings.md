# 11. Settings – Project Configuration – Deep-Dive Spec

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 2.10 Settings](../APP_CREATOR_PAGE_SPECS.md#210-settings----settingstsx)

**Code files:**

- `src/routes/app/project/settings.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **central place to configure project-level settings** such as access, environments, integrations, and tokens.

**Primary goals:**

- Allow editing core project metadata and configuration.
- Manage collaborators and their permissions.
- Manage API tokens and integration settings.
- Show an audit trail of changes.

**Non-goals:**

- Replace organization-wide identity management.
- Handle all DevSecOps settings (there is a separate DevSecOps Settings page for that scope).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Project Owners / Tech Leads:** Configure who has access and key environment options.
- **DevOps / Platform Engineers:** Set up integrations (GitHub, CI, monitoring).

**Key scenarios:**

1. **Adding a collaborator**
   - Owner opens Settings.
   - Adds a new user with role (e.g., Viewer, Editor, Admin).

2. **Creating an API token**
   - Engineer creates a token for CI integration with scoped permissions.

3. **Reviewing changes**
   - Lead checks audit trail to see who changed settings recently.

---

## 3. Content & Layout Overview

- **Project configuration section:**
  - Name, description, default branch, etc.
- **Access control (RBAC) section:**
  - Table of users/roles.
  - Dialog for adding/editing users.
- **API tokens section:**
  - Table of tokens with scopes and created dates.
  - Dialog for creating/revoking tokens.
- **Audit trail section:**
  - List of recent setting changes.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Clear scopes/roles:**
  - Role descriptions in layman terms.
- **Safety for destructive actions:**
  - Confirmation dialogs for revoking access or deleting tokens.
- **Feedback:**
  - Success toast (already used for E2E) should be clear and non-technical.

---

## 5. Completeness and Real-World Coverage

Settings should cover:

1. **Access control** for individuals and teams.
2. **Tokens/integrations** needed for CI/CD and monitoring.
3. **Environment-level settings** that affect deployments and observability.

---

## 6. Modern UI/UX Nuances and Features

- **Sectioned layout:**
  - Use cards or tabs to organize settings logically.
- **Inline validation:**
  - Validate emails, token names, etc., with clear messages.
- **Audit visibility:**
  - Timestamps and user names for each change.

---

## 7. Coherence and Consistency Across the App

- Role names and permissions must align with DevSecOps Settings & Governance.
- Settings summaries should be consistent with what’s displayed in the project shell header (e.g., environments, key integrations).

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#210-settings----settingstsx`
- Route implementation: `src/routes/app/project/settings.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Align roles and scopes with platform-wide RBAC design.
2. Provide a concise settings summary surface (e.g., in project header).
3. Add filters/search for large collaborator lists.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Settings
Subtitle: Access, environments, and integrations for this project.

[ Project Config ]   [ Access Control ]   [ API Tokens ]   [ Audit Trail ]

Project Config (card)
-------------------------------------------------------------------------------
- Name: "E‑commerce Platform"
- Default branch: `main`
- Environments: Dev, Staging, Prod
- Repo: github.com/acme/ecommerce‑web

Access Control (card)
-------------------------------------------------------------------------------
User          Role        Last activity
-------------------------------------------------------------------------------
alice         Owner       2025‑10‑18 09:12
bob           Editor      2025‑10‑17 17:03
charlie       Viewer      2025‑10‑16 11:45
-------------------------------------------------------------------------------
[ Add collaborator ]  → opens dialog with email + role fields

API Tokens (card)
-------------------------------------------------------------------------------
Name              Scope             Created           Last used
-------------------------------------------------------------------------------
"ci‑deploy"       deployments:*     2025‑10‑10        2025‑10‑18 09:15
"monitor‑agent"   metrics:read      2025‑10‑05        2025‑10‑17 22:01
-------------------------------------------------------------------------------
[ New token ]  [ Revoke ]

Audit Trail (card)
-------------------------------------------------------------------------------
- 2025‑10‑18 09:12 – alice added bob as Editor
- 2025‑10‑17 22:01 – monitor‑agent token used from IP 203.0.113.10
- 2025‑10‑10 08:30 – alice created token "ci‑deploy"
```
