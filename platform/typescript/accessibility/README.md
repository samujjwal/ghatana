# @ghatana/accessibility

Unified Ghatana accessibility library — WCAG auditing, scoring, reporting, and audit logging.

Replaces the former two separate packages:
- `@ghatana/accessibility-audit` → use `@ghatana/accessibility` instead
- `@ghatana/audit-components` → use `@ghatana/accessibility` instead

## Usage

```ts
import { runQuickAudit, AccessibilityAuditor } from '@ghatana/accessibility';
import type { AccessibilityReport, WCAGLevel } from '@ghatana/accessibility';

// React components
import { AccessibilityAuditTool, AccessibilityReportViewer } from '@ghatana/accessibility';

// Hook
import { useAccessibilityAudit } from '@ghatana/accessibility';

// Audit logging (GDPR-related operations)
import { auditLogService } from '@ghatana/accessibility/audit';
import type { AuditEvent } from '@ghatana/accessibility/audit';

// Test utilities
import { createMinimalReport, mockFindings } from '@ghatana/accessibility/testing';
```

## Sub-paths

| Sub-path | Purpose |
|----------|---------|
| `@ghatana/accessibility` | Main WCAG auditing and components |
| `@ghatana/accessibility/audit` | Audit logging for privacy-sensitive operations |
| `@ghatana/accessibility/testing` | Test fixtures and helpers (test-only) |
