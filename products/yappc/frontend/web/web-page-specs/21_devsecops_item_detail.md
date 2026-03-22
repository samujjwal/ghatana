# 21. DevSecOps Item Detail – Deep-Dive Spec

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 4. DevSecOps Area](../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops)

**Code files:**

- `src/routes/devsecops/item/$itemId.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **detail view for a single DevSecOps item**, with tabs for overview, artifacts, plans, code, security, and activity.

**Primary goals:**

- Show full details of a DevSecOps item (e.g., risk, check, task).
- Provide actions like Edit, View Implementation Plan, Open in GitHub.
- Organize content into focused tabs.

**Non-goals:**

- Replace full issue tracking or code hosting UIs; it links to them.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- Engineers and SREs working specific items.
- Security leads reviewing critical issues.

**Key scenarios:**

1. **Investigating a risk**
   - Security engineer opens item detail.
   - Reads context, code links, and activity.

2. **Implementing a plan**
   - Developer reads implementation plan and follows links to code.

---

## 3. Content & Layout Overview

- **Header:**
  - Item title, ID, key metadata (severity, status, owner).
- **Tab strip:**
  - Overview, Artifacts, Plans, Code, Security, Activity.
- **Actions:**
  - Buttons for Edit, View Implementation Plan, Open in GitHub.

Current implementation uses placeholder content for these sections.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Readable overview:**
  - Plain-language description of what the item is and why it matters.
- **Clear severity and status indicators.**

---

## 5. Completeness and Real-World Coverage

Item detail should cover:

1. Business and technical context.
2. Links to evidence (logs, metrics, code).
3. Change history and current owner.

---

## 6. Modern UI/UX Nuances and Features

- **Sticky header** with title and actions.
- **Tabbed navigation** that preserves scroll state per tab (future enhancement).

---

## 7. Coherence and Consistency Across the App

- Severity/status semantics must match Monitor and DevSecOps dashboard.
- Links to App Creator projects and canvas nodes should be consistent.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops`
- Route implementation: `src/routes/devsecops/item/$itemId.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Connect item to specific App Creator project and nodes.
2. Replace placeholder content with real data.
3. Add status transitions and workflow actions.

---

## 10. Mockup / Expected Layout & Content

```text
H1: [Critical] Outdated library in payments-service

[ Overview | Artifacts | Plans | Code | Security | Activity ]

Overview
-------------------------------------------------------------------------------
Description
- Payments-service is using `acme-http-client` v1.2.3 with known CVE‑2025‑1234.

Metadata
- Severity: Critical
- Status: Open
- Owner: alice
- Phase: Build
- Affected services: payments-service, checkout-service

Actions
[ Edit ]   [ View Implementation Plan ]   [ Open in GitHub ]   [ Change Status ]
```
