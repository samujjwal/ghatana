````markdown
# 29. Workflows – Workflow Management – Deep-Dive Spec

> **Document Version:** 1.0.0 (2025-12-29)  
> **Status:** New spec – implementation exists but was not previously documented

Related inventory entry: None (new spec)

**Code files:**

- `src/routes/workflows/_layout.tsx` – Workflow shell/layout
- `src/routes/workflows/index.tsx` – Workflow list
- `src/routes/workflows/new.tsx` – New workflow creation
- `src/routes/workflows/$workflowId.tsx` – Workflow detail/editor

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **standalone workflow management area** for creating, viewing, and editing automated/semi-automated process templates that integrate with DevSecOps and project pipelines.

**Primary goals:**

- List available workflows with status and metadata
- Create new workflows with configuration forms
- View and edit existing workflow definitions
- Connect workflows to DevSecOps phases and project pipelines

**Non-goals:**

- Replace the DevSecOps workflows route (`/devsecops/workflows`) which has different context
- Act as a full orchestration engine UI (this is configuration, not monitoring)

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Platform Engineers:** Create and maintain workflow templates
- **DevSecOps Engineers:** Configure security and compliance workflows
- **Tech Leads:** Review and approve workflow definitions

**Key scenarios:**

1. **Creating a new release workflow**
   - Engineer opens `/workflows/new`
   - Fills out workflow name, description, trigger conditions
   - Adds steps and configurations
   - Saves and activates the workflow

2. **Editing an existing workflow**
   - Engineer opens `/workflows/:workflowId`
   - Reviews current configuration
   - Makes adjustments to steps or conditions
   - Saves changes

3. **Browsing available workflows**
   - Lead opens `/workflows`
   - Filters by type or status
   - Reviews workflow details before selecting

---

## 3. Content & Layout Overview

### 3.1 Workflow List (`/workflows`)

- **Header:**
  - Title: "Workflows"
  - Subtitle: "Manage automated and semi-automated process templates"
  - CTA: "New Workflow" button

- **Filter controls:**
  - Status (Active, Draft, Archived)
  - Type (Release, Security, Compliance, Custom)
  - Search by name

- **Workflow cards/table:**
  - Name and description
  - Type badge
  - Status indicator
  - Last modified date
  - Run count or usage metrics

### 3.2 New Workflow (`/workflows/new`)

- **Form sections:**
  - Basic info (name, description, type)
  - Trigger configuration (manual, scheduled, event-based)
  - Steps builder (ordered list of actions)
  - Conditions and guards
  - Notification settings

### 3.3 Workflow Detail (`/workflows/:workflowId`)

- **Header:**
  - Workflow name and status badge
  - Edit / Activate / Archive actions

- **Tabs or sections:**
  - Overview (summary, recent runs)
  - Configuration (steps, triggers, conditions)
  - History (change log)
  - Runs (execution history)

---

## 4. UX Requirements – User-Friendly and Valuable

- **Clear workflow states:**
  - Draft, Active, Paused, Archived with distinct visual indicators

- **Step builder usability:**
  - Drag-and-drop reordering of steps
  - Clear step type icons and descriptions

- **Validation:**
  - Inline validation for required fields
  - Pre-activation checks for workflow completeness

---

## 5. Completeness and Real-World Coverage

Workflows should support:

1. **Multiple trigger types** (manual, cron, webhook, event)
2. **Parameterization** (variables that can be passed at runtime)
3. **Conditional logic** (if/else, guards)
4. **Integration hooks** (to DevSecOps, CI/CD, notifications)

---

## 6. Modern UI/UX Nuances and Features

- **Visual workflow builder:**
  - Graph or sequential view of steps
  - Collapsible step details

- **Template library:**
  - Pre-built workflow templates to start from

- **Dry-run support:**
  - Test workflow without triggering real actions

---

## 7. Coherence and Consistency Across the App

- Workflows should:
  - Link to DevSecOps phases where applicable
  - Be referenced from project pipelines (Build, Deploy)
  - Use the same status semantics as the rest of the app

---

## 8. Links to More Detail & Working Entry Points

**Code entry points:**

- Workflow layout: `src/routes/workflows/_layout.tsx`
- Workflow list: `src/routes/workflows/index.tsx`
- New workflow: `src/routes/workflows/new.tsx`
- Workflow detail: `src/routes/workflows/$workflowId.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. **Document current implementation state** – Review actual code to update this spec
2. **Clarify relationship to DevSecOps workflows** – Are these the same or different?
3. **Add visual workflow builder** if not present
4. **Integration with canvas** – Allow workflows to be modeled visually

---

## 10. Mockup / Expected Layout & Content

```text
H1: Workflows
Subtitle: Manage automated and semi-automated process templates.

[ New Workflow ]

[ Filters: Status ▼ (Active) | Type ▼ (All) | Search 🔍 "release" ]

Workflow Cards
-------------------------------------------------------------------------------
| Release Pipeline v2                        | Security Scan on PR             |
-------------------------------------------------------------------------------
| Type: [Release]  Status: ● Active          | Type: [Security]  Status: ● Active
| Description: Standard release workflow     | Description: Runs SAST/DAST on   |
| with staging gate and prod approval.       | all pull requests to main.       |
| Last modified: 2d ago by alice            | Last modified: 1w ago by bob     |
| Runs: 47 (last 30d)                       | Runs: 312 (last 30d)            |
| [View] [Edit] [⋯]                          | [View] [Edit] [⋯]               |
-------------------------------------------------------------------------------

Workflow Detail View (/workflows/:id)
-------------------------------------------------------------------------------
H1: Release Pipeline v2                    [Edit] [Activate] [Archive]
Status: ● Active | Type: Release | Created by alice on 2025-10-01

[ Overview | Configuration | History | Runs ]

Configuration tab:
- Trigger: Manual with approval gate
- Steps:
  1. Run tests (CI/CD integration)
  2. Deploy to staging
  3. Wait for approval (24h timeout)
  4. Deploy to production
  5. Notify on success/failure
- Conditions:
  - Skip staging if hotfix branch
  - Require 2 approvers for production
```

````