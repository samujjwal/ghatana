# LOW-LEVEL DESIGN: K-13 ADMIN PORTAL

**Module**: K-13 Admin Portal  
**Layer**: Kernel (Presentation)  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Platform Architecture Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Admin Portal provides a **unified "single pane of glass" web console for platform administrators** with RBAC-governed views, maker-checker UI workflows, dual-calendar date pickers, audit log viewer, and AI insights dashboard.

**Core Responsibilities**:

- Unified authentication via SSO (K-01 integration)
- RBAC-driven views per role (admin, compliance officer, operator, auditor)
- Maker-checker UI workflows for configuration, rule, and plugin changes
- Dual-calendar (BS + Gregorian) date pickers and date displays
- Audit log viewer with search, filter, and export (K-07 integration)
- AI insights dashboard showing model performance and HITL metrics (K-09)
- Service health dashboard (K-06 Observability integration)
- Plugin management console (K-04 integration)
- Configuration management interface (K-02 integration)
- Deployment monitoring (K-10 integration)
- Micro-frontend architecture for modular extensibility
- Schema-driven rendering for dynamic forms, workflow tasks, and value-catalog-backed controls

**Invariants**:

1. All admin actions MUST go through K-01 authentication
2. All views MUST respect RBAC permissions
3. All date displays MUST show dual-calendar (BS + AD)
4. Critical changes MUST use maker-checker workflow
5. All admin actions MUST be audited via K-07

### 1.2 Explicit Non-Goals

- ❌ Trading UI (separate D-01/D-02 trading interfaces)
- ❌ Client-facing portal (separate customer portal)
- ❌ Mobile application (web-only admin portal)

### 1.3 Dependencies

| Dependency           | Purpose                       | Readiness Gate |
| -------------------- | ----------------------------- | -------------- |
| K-01 IAM             | SSO authentication, RBAC      | K-01 stable    |
| K-02 Config Engine   | Configuration management UI   | K-02 stable    |
| K-04 Plugin Runtime  | Plugin management UI          | K-04 stable    |
| K-06 Observability   | Service health dashboard      | K-06 stable    |
| K-07 Audit Framework | Audit log viewer              | K-07 stable    |
| K-09 AI Governance   | AI insights dashboard         | K-09 stable    |
| K-10 Deployment      | Deployment monitoring         | K-10 stable    |
| K-15 Dual-Calendar   | Date conversion, date pickers | K-15 stable    |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 Technology Stack

```typescript
interface AdminPortalStack {
  framework: "React 18";
  stateManagement: "Jotai + TanStack Query";
  styling: "TailwindCSS";
  routing: "React Router v6";
  architecture: "Micro-Frontend (Module Federation)";
  build: "Vite + Webpack Module Federation";
  testing: "Jest + React Testing Library + Playwright (E2E)";
  i18n: "react-intl";
  a11y: "WCAG 2.1 AA";
}
```

### 2.2 Micro-Frontend Architecture

```
Admin Portal Shell (host)
├── @siddhanta/shell-layout       # Navigation, header, footer
├── @siddhanta/mf-config          # Configuration management (K-02)
├── @siddhanta/mf-plugins         # Plugin management (K-04)
├── @siddhanta/mf-observability   # Service health (K-06)
├── @siddhanta/mf-audit           # Audit log viewer (K-07)
├── @siddhanta/mf-ai-insights     # AI dashboard (K-09)
├── @siddhanta/mf-deployments     # Deployment monitoring (K-10)
├── @siddhanta/mf-iam             # User/role management (K-01)
└── @siddhanta/mf-jurisdiction    # Jurisdiction-specific (T3 plugin)
```

### 2.3 Key UI Components

```typescript
// Dual-Calendar Date Picker
interface DualCalendarDatePicker {
  value: DualDate;
  onChange: (date: DualDate) => void;
  displayMode: "BS" | "AD" | "BOTH";
  minDate?: DualDate;
  maxDate?: DualDate;
  fiscalYearDisplay?: boolean;
}

// Maker-Checker Workflow Component
interface MakerCheckerForm {
  entityType: string; // "configuration" | "rule" | "plugin"
  entityId: string;
  currentValue: any;
  proposedValue: any;
  onSubmit: (proposal: ChangeProposal) => void;
  onApprove: (proposalId: string, comments: string) => void;
  onReject: (proposalId: string, reason: string) => void;
}

// Audit Log Viewer
interface AuditLogViewer {
  filters: {
    dateRange: { from: DualDate; to: DualDate };
    service?: string;
    action?: string;
    userId?: string;
    severity?: "INFO" | "WARNING" | "CRITICAL";
  };
  onExport: (format: "CSV" | "JSON" | "PDF") => void;
  pageSize: number;
}

// Schema-Driven Form Renderer
interface DynamicFormRenderer {
  schemaId: string;
  schemaVersion: string;
  uiSchema?: Record<string, unknown>;
  valueCatalogRefs?: Array<{
    field: string;
    catalogKey: string;
    version?: string;
  }>;
  initialValue?: Record<string, unknown>;
  onSubmit: (payload: Record<string, unknown>) => void;
}

// Workflow Task Workbench
interface WorkflowTaskWorkbench {
  taskId: string;
  workflowId: string;
  form: DynamicFormRenderer;
  assignment: {
    actorType: "USER" | "ROLE" | "QUEUE";
    assignee: string;
    dueAt: DualDate;
  };
}
```

---

## 3. DATA MODEL

### 3.1 Frontend State Schema

```typescript
interface AdminPortalState {
  auth: {
    user: User;
    permissions: Permission[];
    token: string;
    sessionExpiry: Date;
  };
  preferences: {
    calendarDisplay: "BS" | "AD" | "BOTH";
    timezone: string;
    language: string;
    theme: "light" | "dark";
  };
  navigation: {
    currentModule: string;
    breadcrumbs: Breadcrumb[];
  };
}
```

### 3.2 RBAC View Matrix

| View               | Admin | Compliance Officer | Operator | Auditor |
| ------------------ | ----- | ------------------ | -------- | ------- |
| Configuration Mgmt | ✅ RW | ❌                 | ✅ RO    | ✅ RO   |
| Plugin Management  | ✅ RW | ❌                 | ✅ RO    | ✅ RO   |
| User Management    | ✅ RW | ❌                 | ❌       | ✅ RO   |
| Audit Logs         | ✅ RO | ✅ RO              | ✅ RO    | ✅ RW   |
| AI Insights        | ✅ RO | ✅ RO              | ✅ RO    | ✅ RO   |
| Service Health     | ✅ RW | ❌                 | ✅ RW    | ✅ RO   |
| Deployments        | ✅ RW | ❌                 | ✅ RO    | ✅ RO   |
| Compliance Rules   | ✅ RO | ✅ RW              | ❌       | ✅ RO   |

### 3.3 Schema-Driven UI Metadata Contract

```json
{
  "schema_id": "kyc_review_task",
  "schema_version": "1.0.0",
  "title": "KYC Review",
  "layout": "two-column",
  "fields": [
    {
      "name": "risk_decision",
      "label": "Decision",
      "component": "Select",
      "catalog_ref": {
        "catalog_key": "risk_decision_codes",
        "version": "3.1.0"
      },
      "required": true
    },
    {
      "name": "review_notes",
      "label": "Reviewer Notes",
      "component": "Textarea",
      "required": true,
      "maxLength": 2000
    }
  ],
  "actions": [
    { "id": "save_draft", "label": "Save Draft", "type": "SECONDARY" },
    { "id": "submit", "label": "Submit Review", "type": "PRIMARY" }
  ]
}
```

---

## 4. CONTROL FLOW

### 4.1 Maker-Checker UI Flow

```
1. Maker navigates to configuration/rule change
2. Maker fills in change form with proposed value
3. Maker submits change proposal → status: PENDING_APPROVAL
4. Checker receives notification (in-app + optional email)
5. Checker reviews change (current vs. proposed, diff view)
6. Checker approves/rejects with comments
7. If approved: change applied, event published to K-05
8. Both maker and checker notified of outcome
9. Full action chain logged to K-07
```

### 4.2 Dual-Calendar Date Display

```
function formatDualDate(dualDate, displayMode):
  switch displayMode:
    case 'BS':
      return formatBS(dualDate.bs)              // "२०८२-११-२५"
    case 'AD':
      return formatAD(dualDate.gregorian)       // "2026-03-08"
    case 'BOTH':
      return formatBS(dualDate.bs) + " / " + formatAD(dualDate.gregorian)
```

### 4.3 Dynamic Form Resolution Flow

```
User opens workflow task or config editor
  ↓
Portal requests schema metadata from K-02 / workflow API
  ↓
Portal resolves referenced value catalogs for tenant + jurisdiction context
  ↓
Portal selects widgets from component registry using field.component + ui hints
  ↓
User edits form
  ↓
Client-side validation applies schema rules and bounded catalog constraints
  ↓
Submission sent to backend with schema_id + schema_version + payload
  ↓
Backend re-validates against same pinned schema version
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 RBAC View Filtering

```
function getVisibleModules(user):
  permissions = K01.getPermissions(user.id)
  return microFrontends.filter(mf =>
    mf.requiredPermissions.some(p => permissions.includes(p))
  )
```

### 5.2 Widget Selection Policy

```
function resolveWidget(field):
  if field.component specified and componentRegistry.has(field.component):
    return componentRegistry.get(field.component)
  if field.catalog_ref exists:
    return SelectWidget
  if field.type == 'boolean':
    return ToggleWidget
  if field.type == 'number' and field.minimum != null and field.maximum != null:
    return BoundedNumberWidget
  return DefaultTextWidget
```

---

## 6. NFR BUDGETS

| Metric                    | Target    |
| ------------------------- | --------- |
| Initial page load         | <1s (LCP) |
| Module lazy load          | <500ms    |
| Search response           | <200ms    |
| Audit log pagination      | <300ms    |
| Date picker interaction   | <50ms     |
| Concurrent admin sessions | 500+      |

**Availability**: 99.99%  
**Browser Support**: Chrome 90+, Firefox 90+, Safari 15+, Edge 90+

---

## 7. SECURITY DESIGN

- **SSO**: All authentication via K-01 (OAuth 2.0 + OIDC)
- **Session Timeout**: Configurable idle timeout (default 30 min)
- **RBAC Enforcement**: Server-side permission checks on every API call
- **CSP**: Strict Content Security Policy headers
- **XSS Prevention**: React's built-in escaping + CSP
- **CSRF Protection**: SameSite cookies + CSRF tokens
- **Audit Logging**: Every admin action logged to K-07

---

## 8. OBSERVABILITY & AUDIT

### Metrics

- `admin_portal_page_loads_total` — Page loads by module
- `admin_portal_actions_total` — Admin actions by type
- `admin_portal_maker_checker_pending` — Pending approvals count
- `admin_portal_session_duration_seconds` — Session duration

### Real User Monitoring

- Core Web Vitals (LCP, FID, CLS)
- Error rate by module
- Session replay for debugging

---

## 9. EXTENSIBILITY & EVOLUTION

### Extension Points

- Micro-frontend injection points for custom jurisdiction-specific dashboards
- Custom theme/branding via T1 Config Packs
- Widget plugins for additional dashboard panels (T3)
- Custom date format localization (T1)
- Server-provided form schemas and widget hints for metadata-driven admin/task screens
- Dynamic value catalogs and bounded controls resolved from K-02 at render time

---

## 10. TEST PLAN

### Unit Tests

- Component rendering with various RBAC roles
- Dual-calendar date picker interactions
- Maker-checker form validation
- State management (Jotai atoms and TanStack Query cache behavior)

### Integration Tests

- SSO login flow with K-01
- Audit log viewer with K-07 API
- Configuration change with maker-checker workflow
- Plugin management lifecycle
- Schema-driven workflow task rendering with pinned task schema and value catalogs
- Tenant-specific value-catalog preview and bounded control validation

### E2E Tests (Playwright)

- Complete maker-checker workflow (propose → approve → verify)
- Role-based view filtering (admin sees all, auditor sees audit logs)
- Dual-calendar date selection and display

### Accessibility Tests

- WCAG 2.1 AA compliance audit
- Screen reader navigation
- Keyboard-only interaction
- Color contrast verification
