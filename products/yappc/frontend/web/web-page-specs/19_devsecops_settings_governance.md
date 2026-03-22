# 19. DevSecOps Settings & Governance – Deep-Dive Spec

> **Document Version:** 2.0.0 (2025-12-29)  
> **Last Updated:** December 29, 2025 – Updated with implementation details

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 4. DevSecOps Area](../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops)

**Code files:**

| File | Purpose |
|------|---------|
| `src/routes/devsecops/settings.tsx` | DevSecOps settings route |

**Routes:**

| Route | Purpose |
|-------|---------|
| `/devsecops/settings` | DevSecOps configuration & governance |

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide **settings and governance controls** for DevSecOps, including roles, integrations, automation, and compliance.

**Primary goals:**

- Configure roles & access in the DevSecOps context.
- Manage integrations (e.g., GitHub, Jira, SonarQube).
- Configure automation and compliance policies.

**Non-goals:**

- Replace platform-wide identity or org-level policy tools.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- Security leads.
- Platform/DevOps engineers.
- Compliance officers.

**Key scenarios:**

1. **Enabling an integration**
   - Engineer enables GitHub integration and configures repository scopes.

2. **Configuring automation**
   - Security lead sets rules for automatic checks and enforcement.

3. **Compliance review**
   - Compliance officer reviews policies and checks coverage.

---

## 3. Content & Layout Overview

- **Tabbed layout:**
  - Roles & Access.
  - Integrations.
  - Automation.
  - Compliance.
- **Roles & Access tab:**
  - Users/roles mapping and controls.
- **Integrations tab:**
  - List of integrations with status and configuration controls.
- **Automation tab:**
  - Rules for triggers and automated actions.
- **Compliance tab:**
  - Overview of policy coverage and status.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Clear status indicators** for each integration (Connected, Error, Disabled).
- **Descriptive copy** for roles/scopes and policies.

---

## 5. Completeness and Real-World Coverage

Settings & Governance must support:

1. Multiple integrations.
2. Granular role & policy configurations.
3. Visibility into the effect of settings on DevSecOps reports and items.

---

## 6. Modern UI/UX Nuances and Features

- **Tab navigation** with clear labels.
- **Inline validation** and error messages for configuration forms.

---

## 7. Coherence and Consistency Across the App

- Roles and policies must match those referenced in project Settings and DevSecOps reports.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops`
- Route implementation: `src/routes/devsecops/settings.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Ensure changes are reflected in project pipelines/settings.
2. Add clear mapping from settings to reports and risk metrics.

---

## 10. Mockup / Expected Layout & Content

```text
H1: DevSecOps Settings & Governance

[ Roles & Access | Integrations | Automation | Compliance ]

Roles & Access (tab)
-------------------------------------------------------------------------------
Role            Description                          Members
-------------------------------------------------------------------------------
DevSecOps Lead  Full control over policies & checks  alice
Security Eng    Manage security rules and templates  bob, carol
Viewer          Read‑only access                     org‑security‑group
-------------------------------------------------------------------------------

Integrations (tab)
-------------------------------------------------------------------------------
Integration   Status       Details
-------------------------------------------------------------------------------
GitHub        Connected    Org: acme‑org • 24 repos in scope  [Configure]
Jira          Not Config.  No project mappings                 [Connect]
SonarQube     Error        Token expired (click for details)  [Fix now]
-------------------------------------------------------------------------------

Automation (tab)
-------------------------------------------------------------------------------
- Rule: "Block deploys on Critical vulns in Prod" – Enabled
- Rule: "Auto‑create Jira issue on P0 incident" – Enabled

Compliance (tab)
-------------------------------------------------------------------------------
- Required checks per repo: SAST, Secrets, License scan
- Coverage: 18 / 22 repos compliant (82%)
```
