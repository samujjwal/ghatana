# AEP (Agent Execution Platform) UX Remediation Implementation Plan

> Document ID: `AEP_UX_REMEDIATION_IMPLEMENTATION_PLAN`
> Version: `1.0.0`
> Status: `DRAFT`
> Date: `2026-04-07`
> Scope: `products/aep/ui/**`
> Primary Goal: Transform AEP UI from foundation-complete to production-grade with operational dashboard, pervasive AI/ML, comprehensive error handling, and voice/NLP as first-class interfaces

---

## 1. Executive Summary

The AEP UI audit (2026-04-07) revealed a solid architectural foundation with React 19, TypeScript, TanStack Query, Jotai, and good SSE integration for live updates. However, critical gaps prevent production readiness:

**Overall UI/UX Maturity Score:** 6.5/10  
**Dashboard-First Effectiveness Score:** 5/10  
**AI-Native UX Score:** 4/10  
**API Contract Correctness:** 6/10  
**Release Readiness Verdict:** NOT PRODUCTION READY (3-4 weeks for critical fixes)

### Critical Blockers

1. **~30% of UI surface is non-functional placeholders** - RunDetailPage tabs (lineage, decisions, policies) and GovernancePage sections (compliance, tenancy, audit) show "Coming Soon"
2. **Dashboard lacks operational actionability** - MonitoringDashboard shows metrics but requires navigation for HITL approvals; no bulk operations
3. **AI/ML is bolted-on, not native** - AI exists only as explicit HITL queue; no implicit AI (smart prioritization, anomaly detection, auto-suggestions)
4. **Incomplete error state handling** - Generic Error throws; no handling for rate limits (429), conflicts (409), validation errors (422), permission denied (403)
5. **No runtime validation** - API responses cast to TypeScript types without Zod validation

### Implementation Approach

This plan follows Ghatana repo guidelines strictly:
- **Reuse before creating** - Leverage existing `@ghatana/*` packages, `@audio-video/ui` voice hooks, Data Cloud patterns
- **No new dependencies** - Use existing TanStack Query, Jotai, Zod, platform modules
- **Type safety at implementation time** - No `any` types, full TypeScript strict compliance
- **Tests as part of change** - Co-located `__tests__/` with Vitest/React Testing Library
- **Voice/NLP as first-class** - Every input has voice alternative, every action has NLP command
- **Security/privacy by design** - Consent, audit logging, RBAC from day 1

---

## 2. Objectives

### 2.1 Primary Objectives

- Replace placeholder screens with functional features or hide behind feature flags
- Transform MonitoringDashboard from read-only to operational with inline actions
- Embed AI/ML implicitly (smart HITL prioritization, anomaly detection, auto-suggestions)
- Add comprehensive error state coverage with typed errors and recovery flows
- Implement runtime validation with Zod schemas for all API boundaries
- Add bulk operations for runs and HITL items

### 2.2 Secondary Objectives

- Add voice and NLP as first-class interfaces (not bolted-on)
- Implement smart HITL prioritization by confidence × urgency
- Add anomaly detection alerts to monitoring dashboard
- Improve accessibility to WCAG 2.1 AA compliance
- Add keyboard shortcuts (Cmd+K fuzzy finder)
- Add optimistic updates for mutations

### 2.3 Non-Goals

- Replacing the existing React + TanStack Query stack
- Changing the Java backend architecture
- Redesigning the visual design system
- Adding new third-party dependencies
- Creating duplicate voice/NLP infrastructure when `@audio-video/ui` exists

---

## 3. Foundational Infrastructure (Security, Privacy, Voice/NLP)

### 3.1 Security and Privacy as First-Class Citizens

**Guiding Principle:** Every feature must be secure and privacy-compliant by design.

**Reuse Existing Infrastructure:**
- **TokenStorage** (`products/data-cloud/ui/src/lib/auth/tokenStorage.ts`): Memory-first token storage
- **TenantContextFilter** (platform/java): Tenant isolation already implemented
- **RBAC patterns** (copilot-instructions.md section 23): Existing role-based access control
- **TrustCenter patterns** (Data Cloud): Privacy dashboard patterns

**Privacy Requirements (GDPR/CCPA/SOC2 Compliant):**
- Explicit consent capture for voice data processing
- Data retention limits with automatic purging
- Right to deletion (voice logs, HITL decisions, audit trails)
- Audit trails for all privacy-sensitive operations
- Privacy-preserving AI (no raw audio storage, only transcripts)

**Security Requirements:**
- All new endpoints require authentication (reuse JwtAuthFilter)
- Tenant isolation enforced at API boundary
- Input validation with Zod at all boundaries
- Rate limiting on voice endpoints (prevent abuse)
- CSRF protection for state-changing operations

### 3.2 Voice and NLP as First-Class Interface

**Guiding Principle:** Voice and natural language are primary interaction modes, not secondary features.

**Reuse Existing Infrastructure:**
- **@audio-video/ui** (`products/audio-video/libs/audio-video-ui`): Shared voice hooks
  - `useSpeechRecognition` - STT with browser + platform fallback
  - `useSpeechSynthesis` - TTS with browser + platform fallback
- **Platform Audio-Video module** (`platform/java/audio-video`): Java STT/TTS engines

**Voice Integration Strategy:**
1. **Every input has voice alternative** - All text inputs support voice dictation
2. **Natural language commands** - Voice commands for all major actions
3. **Voice feedback** - TTS for confirmations and critical alerts
4. **Accessibility-first** - Voice features primarily serve accessibility needs

**NLP Integration Strategy:**
1. **Natural Language Query (NLQ)** everywhere - "Ask Anything" on every page
2. **Intent-based navigation** - Voice commands navigate directly to features
3. **Smart HITL prioritization** - NLP analysis of review items for urgency scoring
4. **Conversational help** - Contextual guidance via voice/text

### 3.3 Reusable Component Strategy

**Components to Extract for Cross-Product Use:**

| Component | Current Location | Reusable Package | Products to Benefit |
|-----------|-----------------|------------------|-------------------|
| VoiceInput | New | `@ghatana/voice-ui` | Data Cloud, YAPPC, DCMAAR, FlashIt |
| NLQInput | New | `@ghatana/nlp-ui` | All products |
| ConsentManager | New | `@ghatana/privacy-ui` | All products |
| AuditLogViewer | New | `@ghatana/audit-ui` | All products |
| RBACGuard | New | `@ghatana/security-ui` | All products |
| RunTable | `components/monitoring/` | `@ghatana/monitoring-ui` | Data Cloud |
| HITLReviewCard | `components/hitl/` | `@ghatana/hitl-ui` | YAPPC |

**Extraction Process:**
1. Build components in AEP first (production validation)
2. Extract to platform packages after 2+ products confirm need
3. Maintain backward compatibility during migration
4. Document cross-product usage patterns

---

## 4. Current State Summary

### 4.1 What Is Working

- **Strong technical foundation**: React 19, TypeScript, TanStack Query, Jotai
- **Well-structured navigation**: Outcome-based grouping (Operate/Build/Learn/Govern/Catalog)
- **Live updates via SSE**: useLivePipelineRuns, useHitlQueue properly implemented
- **Good type coverage**: Proper TypeScript interfaces for domain types
- **Clean UI design**: Consistent Tailwind usage, good spacing and typography
- **Error boundaries**: Proper error boundary patterns in place

### 4.2 Voice/NLP Infrastructure Already Available

- **@audio-video/ui package**: Shared voice hooks already built
  - `useSpeechRecognition` with browser + platform fallback
  - `useSpeechSynthesis` with browser + platform fallback
- **Platform STT/TTS**: Java audio-video module available
- **Existing endpoint**: `/api/v1/voice/intent` exists but limited coverage

**Gap:** No voice/NLP integration in AEP UI currently.

### 4.3 Security/Privacy Foundation Exists

- **TokenStorage pattern**: Available from Data Cloud (can port)
- **Tenant isolation**: Backend TenantContextFilter enforces data separation
- **RBAC patterns**: Established in copilot-instructions.md

**Gaps:**
- No consent management for voice data processing
- No audit logging for privacy-sensitive operations
- No runtime validation of API responses

### 4.4 What Is Broken or Incomplete

- **Placeholder screens** (~30% of UI): RunDetailPage tabs, GovernancePage sections
- **Non-operational dashboard**: MonitoringDashboard lacks inline actions
- **AI as bolt-on**: Only explicit HITL queue; no implicit AI features
- **Incomplete error handling**: Generic Error throws, no typed errors
- **No runtime validation**: Direct casting without Zod
- **No bulk operations**: HITL and runs are single-item only
- **No voice/NLP integration**: No voice commands, no NLQ
- **Missing accessibility**: No screen reader optimizations, no keyboard shortcuts

---

## 5. Guiding Decisions

### 5.1 Placeholder Screen Strategy

**Decision: Hide placeholder screens behind feature flags until implemented**

Approach:
- Add feature flag system (reuse from Data Cloud if available)
- Hide lineage/decisions/policies tabs in RunDetailPage when not implemented
- Hide compliance/tenancy/audit sections in GovernancePage when not implemented
- Show "Premium/Enterprise" messaging for unimplemented features

**Rationale:**
- "Coming Soon" screens degrade production feel
- Users should not see non-functional UI elements
- Feature flags allow gradual rollout as features complete

### 5.2 Dashboard Operationalization Approach

**Decision: Add inline actions to MonitoringDashboard without new components**

Approach:
- Extend existing RunTable with Approve/Reject buttons for runs needing review
- Add bulk selection checkboxes to RunTable
- Add bulk cancel/retry actions in toolbar
- Add AI-suggested actions panel (anomaly detection, optimization hints)

**Rationale:**
- Uses existing component patterns
- Maintains consistency with current UI
- Minimal new code required

### 5.3 AI/ML Integration Strategy

**Decision: Embed AI implicitly throughout workflows**

Approach:
- Smart HITL prioritization: sort queue by confidence × age × business impact
- Anomaly detection: proactive alerts on pipeline anomalies with suggested fixes
- Auto-suggestions: suggest next stages in pipeline builder based on patterns
- Policy conflict detection: warn when new policy conflicts with existing

**Rationale:**
- Transforms AI from "add-on" to "native layer"
- Reduces manual work without requiring users to "go to AI"
- Uses existing AI endpoints where available

### 5.4 Voice/NLP First-Class Strategy

**Decision: Voice and NLP are primary interfaces, not secondary features**

Approach:
- **VoiceInput component**: Reusable voice-enabled text input
- **Voice commands**: "Approve run [id]", "Show HITL queue", "Cancel pipeline [name]"
- **NLQ everywhere**: Natural language query on search fields
- **TTS feedback**: Voice confirmations for critical actions
- **Accessibility**: All voice actions have keyboard equivalents

**Reuse Strategy:**
- Use `@audio-video/ui` hooks (already built)
- Extend Data Cloud VoiceCommandBar patterns
- Use platform Java audio-video module for backend

**Privacy Requirement:**
- Explicit consent before voice processing
- Clear indicator when microphone is active
- One-click disable voice features

### 5.5 Error State Coverage Strategy

**Decision: Create typed error hierarchy with recovery flows**

Approach:
- Create `ApiError`, `ValidationError`, `RateLimitError`, `PermissionError` classes
- Add error boundary components with retry actions
- Configure TanStack Query retry strategy (exponential backoff)
- Add optimistic updates with rollback on error

**Rationale:**
- Provides actionable error messages to users
- Enables automatic recovery where possible
- Maintains data consistency with optimistic updates

---

## 6. Implementation Phases

### Phase 0: Foundation - Security, Privacy, Voice/NLP Infrastructure (Week 1)

**Goal:** Build foundational reusable components for security, privacy, and voice/NLP that will be used across all subsequent phases.

#### 0.1 ConsentManager Component (Privacy Foundation)

**Frontend Changes:**
- Create `ui/src/components/privacy/ConsentManager.tsx`:

```typescript
/**
 * @doc.type component
 * @doc.purpose Capture and manage user consent for voice/AI processing
 * @doc.layer frontend
 * @doc.pattern Privacy Component
 */

import React from 'react';
import { z } from 'zod';

const ConsentRecord = z.object({
  id: z.string(),
  userId: z.string(),
  purpose: z.enum(['voice_processing', 'ai_suggestions', 'hitl_review', 'audit_logging']),
  granted: z.boolean(),
  timestamp: z.string().datetime(),
  expiresAt: z.string().datetime().optional(),
  metadata: z.record(z.unknown()).optional(),
}).strict();

export type ConsentRecord = z.infer<typeof ConsentRecord>;

interface ConsentManagerProps {
  purpose: ConsentRecord['purpose'];
  onConsentChange: (granted: boolean) => void;
  children: React.ReactNode;
}

export const ConsentManager: React.FC<ConsentManagerProps> = ({
  purpose,
  onConsentChange,
  children,
}) => {
  // Implementation using existing TrustCenter patterns
  // Reuses TokenStorage for secure consent token management
};
```

**Backend Changes:**
- Add `POST /api/v1/consent` - Record consent
- Add `GET /api/v1/consent/:purpose` - Check consent status
- Add `DELETE /api/v1/consent/:purpose` - Revoke consent
- Reuse platform `TenantContextFilter` for user isolation

**Tests:**
- `ui/src/components/privacy/__tests__/ConsentManager.test.tsx`
- Backend integration tests for consent endpoints

**Reusability:** Component designed for extraction to `@ghatana/privacy-ui`

#### 0.2 AuditLog Service (Security Foundation)

**Frontend Changes:**
- Create `ui/src/services/audit-log.ts`:

```typescript
/**
 * Audit logging for privacy-sensitive operations
 * @doc.type service
 * @doc.purpose Log all privacy-sensitive operations for compliance
 * @doc.layer frontend
 */

import { z } from 'zod';

const AuditEvent = z.object({
  id: z.string(),
  timestamp: z.string().datetime(),
  userId: z.string(),
  tenantId: z.string(),
  action: z.enum(['voice_command', 'hitl_approve', 'hitl_reject', 'pipeline_run', 'policy_approve', 'data_access', 'consent_change']),
  resource: z.string(),
  status: z.enum(['success', 'failure', 'denied']),
  metadata: z.record(z.unknown()).optional(),
}).strict();

export type AuditEvent = z.infer<typeof AuditEvent>;

export const auditLogService = {
  log: async (event: Omit<AuditEvent, 'id' | 'timestamp'>): Promise<void> => {
    // Send to backend audit endpoint
  },
  query: async (filters: { userId?: string; action?: string; from?: string; to?: string }): Promise<AuditEvent[]> => {
    // Query audit logs
  },
};
```

**Backend Changes:**
- Add `POST /api/v1/audit/log` - Record audit event
- Add `GET /api/v1/audit/query` - Query audit logs (RBAC enforced)
- Store in append-only audit table with tenant isolation

**Tests:**
- `ui/src/services/__tests__/audit-log.test.ts`

**Reusability:** Service designed for use across all products

#### 0.3 VoiceInput Component (Voice Foundation)

**Frontend Changes:**
- Create `ui/src/components/voice/VoiceInput.tsx`:

```typescript
/**
 * Voice-enabled text input component
 * @doc.type component
 * @doc.purpose Provide voice dictation for any text input
 * @doc.layer frontend
 * @doc.pattern Accessibility Component
 */

import React, { useCallback } from 'react';
import { useSpeechRecognition } from '@audio-video/ui';
import { Mic, MicOff } from 'lucide-react';
import { ConsentManager } from '../privacy/ConsentManager';
import { cn } from '../../lib/theme';

interface VoiceInputProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  disabled?: boolean;
}

export const VoiceInput: React.FC<VoiceInputProps> = ({
  value,
  onChange,
  placeholder,
  className,
  disabled,
}) => {
  const { start, stop, isListening, isSupported } = useSpeechRecognition();

  const toggleListening = useCallback(() => {
    if (isListening) {
      stop();
    } else {
      start({
        onTranscript: (text, isFinal) => {
          if (isFinal) onChange(text);
        },
      });
    }
  }, [isListening, start, stop, onChange]);

  return (
    <ConsentManager purpose="voice_processing" onConsentChange={() => {}}>
      <div className={cn('relative', className)}>
        <input
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          disabled={disabled}
          className="w-full pr-10"
          aria-label="Text input with voice option"
        />
        {isSupported && (
          <button
            onClick={toggleListening}
            disabled={disabled}
            className={cn(
              'absolute right-2 top-1/2 -translate-y-1/2',
              'p-1 rounded-full transition-colors',
              isListening ? 'bg-red-100 text-red-600' : 'hover:bg-gray-100'
            )}
            aria-label={isListening ? 'Stop voice input' : 'Start voice input'}
            aria-pressed={isListening}
          >
            {isListening ? <MicOff className="h-4 w-4" /> : <Mic className="h-4 w-4" />}
          </button>
        )}
      </div>
    </ConsentManager>
  );
};
```

**Tests:**
- `ui/src/components/voice/__tests__/VoiceInput.test.tsx`

**Reusability:** Component designed for extraction to `@ghatana/voice-ui`

#### 0.4 NLQInput Component (NLP Foundation)

**Frontend Changes:**
- Create `ui/src/components/nlp/NLQInput.tsx`:

```typescript
/**
 * Natural Language Query input component
 * @doc.type component
 * @doc.purpose Allow users to query using natural language
 * @doc.layer frontend
 * @doc.pattern NLP Component
 */

import React, { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Send } from 'lucide-react';
import { VoiceInput } from '../voice/VoiceInput';
import { cn } from '../../lib/theme';

interface NLQInputProps {
  onQuery: (query: string, intent?: string) => void;
  placeholder?: string;
  className?: string;
}

export const NLQInput: React.FC<NLQInputProps> = ({
  onQuery,
  placeholder = 'Ask anything...',
  className,
}) => {
  const [query, setQuery] = useState('');
  
  const { mutate: parseIntent, isPending } = useMutation({
    mutationFn: async (text: string) => {
      const response = await fetch('/api/v1/nlp/parse', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text }),
      });
      return response.json();
    },
  });

  const handleSubmit = () => {
    if (!query.trim()) return;
    parseIntent(query, {
      onSuccess: (data) => {
        onQuery(query, data.intent);
      },
      onError: () => {
        onQuery(query);
      },
    });
  };

  return (
    <div className={cn('flex gap-2', className)}>
      <VoiceInput
        value={query}
        onChange={setQuery}
        placeholder={placeholder}
        className="flex-1"
      />
      <button
        onClick={handleSubmit}
        disabled={isPending || !query.trim()}
        className="px-4 py-2 bg-indigo-600 text-white rounded-lg disabled:opacity-50"
        aria-label="Submit query"
      >
        {isPending ? '...' : <Send className="h-4 w-4" />}
      </button>
    </div>
  );
};
```

**Backend Changes:**
- Add `POST /api/v1/nlp/parse` - Parse natural language to intent
- Reuse existing voice intent infrastructure
- Add NLQ models to platform AI module

**Tests:**
- `ui/src/components/nlp/__tests__/NLQInput.test.tsx`

**Reusability:** Component designed for extraction to `@ghatana/nlp-ui`

#### 0.5 RBACGuard Component (Security Foundation)

**Frontend Changes:**
- Create `ui/src/components/security/RBACGuard.tsx`:

```typescript
/**
 * Role-Based Access Control guard component
 * @doc.type component
 * @doc.purpose Conditionally render content based on user permissions
 * @doc.layer frontend
 * @doc.pattern Security Component
 */

import React from 'react';
import { useQuery } from '@tanstack/react-query';

interface RBACGuardProps {
  permission: string;
  resource?: string;
  action?: 'read' | 'write' | 'delete' | 'admin';
  fallback?: React.ReactNode;
  children: React.ReactNode;
}

export const RBACGuard: React.FC<RBACGuardProps> = ({
  permission,
  resource,
  action,
  fallback = null,
  children,
}) => {
  const { data: hasPermission, isLoading } = useQuery({
    queryKey: ['rbac', 'permission', permission, resource, action],
    queryFn: async () => {
      const response = await fetch('/api/v1/auth/check-permission', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ permission, resource, action }),
      });
      const data = await response.json();
      return data.granted;
    },
  });

  if (isLoading) return null;
  return hasPermission ? <>{children}</> : <>{fallback}</>;
};
```

**Tests:**
- `ui/src/components/security/__tests__/RBACGuard.test.tsx`

**Reusability:** Component designed for extraction to `@ghatana/security-ui`

#### 0.6 Success Criteria for Phase 0

- [ ] ConsentManager captures and stores consent for voice/AI processing
- [ ] AuditLog service logs all privacy-sensitive operations
- [ ] VoiceInput component works in all text inputs with consent check
- [ ] NLQInput component parses natural language to intents
- [ ] RBACGuard enforces permissions on all new features
- [ ] All components have co-located tests passing
- [ ] Components designed for cross-product reuse

---

### Phase 1: Placeholder Screen Removal (Week 1-2)

**Goal:** Remove or hide placeholder screens to improve production readiness.

#### 1.1 Add Feature Flag System

**Frontend Changes:**
- Create `ui/src/lib/feature-flags.ts`:

```typescript
/**
 * Feature flag configuration
 * @doc.type config
 * @doc.purpose Control feature visibility via flags
 * @doc.layer frontend
 */

export const featureFlags = {
  // Run detail tabs
  EVENT_LINEAGE: import.meta.env.VITE_FEATURE_EVENT_LINEAGE === 'true',
  AGENT_DECISIONS: import.meta.env.VITE_FEATURE_AGENT_DECISIONS === 'true',
  POLICY_REFERENCES: import.meta.env.VITE_FEATURE_POLICY_REFERENCES === 'true',
  
  // Governance sections
  COMPLIANCE_REPORTS: import.meta.env.VITE_FEATURE_COMPLIANCE_REPORTS === 'true',
  TENANT_MANAGEMENT: import.meta.env.VITE_FEATURE_TENANT_MANAGEMENT === 'true',
  AUDIT_LOG: import.meta.env.VITE_FEATURE_AUDIT_LOG === 'true',
  
  // Voice/NLP
  VOICE_COMMANDS: import.meta.env.VITE_FEATURE_VOICE_COMMANDS === 'true',
  NLQ: import.meta.env.VITE_FEATURE_NLQ === 'true',
} as const;

export type FeatureFlag = keyof typeof featureFlags;

export function isFeatureEnabled(flag: FeatureFlag): boolean {
  return featureFlags[flag];
}
```

**Tests:**
- `ui/src/lib/__tests__/feature-flags.test.ts`

#### 1.2 Hide RunDetailPage Placeholder Tabs

**Frontend Changes:**
- Update `ui/src/pages/RunDetailPage.tsx`:

```typescript
import { isFeatureEnabled } from '../lib/feature-flags';

// In tab rendering:
{activeTab === 'lineage' && isFeatureEnabled('EVENT_LINEAGE') ? (
  <EventLineagePanel runId={runId} />
) : (
  <ComingSoonPanel
    title="Event lineage"
    description="Event lineage tracing is an enterprise feature. Contact your administrator to enable."
  />
)}
```

**Backend Changes:**
- Add environment variable configuration for feature flags
- Document feature flag requirements

#### 1.3 Hide GovernancePage Placeholder Sections

**Frontend Changes:**
- Update `ui/src/pages/GovernancePage.tsx`:

```typescript
import { isFeatureEnabled } from '../lib/feature-flags';

// In section rendering:
{section === 'compliance' && isFeatureEnabled('COMPLIANCE_REPORTS') ? (
  <CompliancePanel tenantId={tenantId} />
) : (
  <ComingSoonSection
    title="Compliance reports"
    description="Compliance reporting is an enterprise feature. Contact your administrator to enable."
  />
)}
```

#### 1.4 Success Criteria for Phase 1

- [ ] Feature flag system implemented and tested
- [ ] All placeholder tabs/sections hidden behind flags
- [ ] "Coming Soon" messaging updated to enterprise/premium
- [ ] Environment variables documented

---

### Phase 2: Dashboard Operationalization (Week 2-3)

**Goal:** Transform MonitoringDashboard from read-only to operational with inline actions.

#### 2.1 Add Inline Approve/Reject to RunTable

**Frontend Changes:**
- Update `ui/src/components/monitoring/RunTable.tsx`:

```typescript
interface RunTableProps {
  runs: PipelineRun[];
  onCancel?: (runId: string) => void;
  onApprove?: (runId: string) => void;
  onReject?: (runId: string, reason: string) => void;
  className?: string;
}

export function RunTable({ runs, onCancel, onApprove, onReject, className = '' }: RunTableProps) {
  // ... existing code ...
  
  {onApprove && run.needsReview && (
    <td className="px-3 py-2 text-right">
      <button
        onClick={() => onApprove(run.id)}
        className="text-xs text-green-600 hover:text-green-700 font-medium"
      >
        Approve
      </button>
    </td>
  )}
  {onReject && run.needsReview && (
    <td className="px-3 py-2 text-right">
      <button
        onClick={() => {
          const reason = prompt('Enter rejection reason:');
          if (reason) onReject(run.id, reason);
        }}
        className="text-xs text-red-500 hover:text-red-700 font-medium"
      >
        Reject
      </button>
    </td>
  )}
}
```

**Tests:**
- `ui/src/components/monitoring/__tests__/RunTable.test.tsx`

#### 2.2 Add Bulk Selection to RunTable

**Frontend Changes:**
- Create `ui/src/hooks/useSelection.ts` (reuse from Data Cloud pattern):

```typescript
interface UseSelectionOptions<T> {
  items: T[];
  keyFn: (item: T) => string;
}

export function useSelection<T>({ items, keyFn }: UseSelectionOptions<T>) {
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const toggle = useCallback((id: string) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  const toggleAll = useCallback(() => {
    setSelectedIds(prev => 
      prev.size === items.length ? new Set() : new Set(items.map(keyFn))
    );
  }, [items, keyFn]);

  const selectedItems = useMemo(() => 
    items.filter(item => selectedIds.has(keyFn(item))),
  [items, selectedIds, keyFn]);

  return { selectedIds, selectedItems, toggle, toggleAll, setSelectedIds };
}
```

- Update RunTable to include checkboxes

**Tests:**
- `ui/src/hooks/__tests__/useSelection.test.ts`

#### 2.3 Add Bulk Cancel/Retry Actions

**Frontend Changes:**
- Add bulk action toolbar to MonitoringDashboardPage:

```typescript
import { useSelection } from '../hooks/useSelection';
import { RBACGuard } from '../components/security/RBACGuard';

const { selectedIds, selectedItems, toggle, toggleAll, setSelectedIds } = useSelection({
  items: runs,
  keyFn: (run) => run.id,
});

const bulkCancelMutation = useMutation({
  mutationFn: async (ids: string[]) => {
    return Promise.all(ids.map(id => cancelRun(id, tenantId)));
  },
  onSuccess: () => {
    setSelectedIds(new Set());
    qc.invalidateQueries({ queryKey: ['pipeline-runs', tenantId] });
  },
});

// Voice integration
const { speak } = useSpeechSynthesis();
const handleBulkCancel = () => {
  bulkCancelMutation.mutate(Array.from(selectedIds));
  speak(`Cancelling ${selectedIds.size} pipeline runs`);
};
```

**Tests:**
- `ui/src/pages/__tests__/MonitoringDashboardPage.test.tsx`

#### 2.4 Add AI-Suggested Actions Panel

**Frontend Changes:**
- Create `ui/src/components/monitoring/AiSuggestionsPanel.tsx`:

```typescript
/**
 * AI-powered suggestions for pipeline operations
 * @doc.type component
 * @doc.purpose Show AI-suggested actions based on anomaly detection
 * @doc.layer frontend
 */

import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, TrendingUp, Zap } from 'lucide-react';

export const AiSuggestionsPanel: React.FC<{ tenantId: string }> = ({ tenantId }) => {
  const { data: suggestions, isLoading } = useQuery({
    queryKey: ['aep', 'ai-suggestions', tenantId],
    queryFn: () => fetch(`/api/v1/ai/suggestions?tenantId=${tenantId}`).then(r => r.json()),
    staleTime: 60_000,
  });

  if (isLoading) return <div>Loading suggestions...</div>;
  if (!suggestions?.length) return null;

  return (
    <div className="bg-indigo-50 dark:bg-indigo-950 border border-indigo-200 dark:border-indigo-800 rounded-lg p-4">
      <h3 className="text-sm font-semibold text-indigo-900 dark:text-indigo-100 mb-3">
        AI Suggestions
      </h3>
      <div className="space-y-2">
        {suggestions.map((suggestion: any) => (
          <div key={suggestion.id} className="flex items-start gap-2 text-sm">
            <AlertTriangle className="h-4 w-4 text-indigo-600 mt-0.5" />
            <div>
              <p className="text-indigo-900 dark:text-indigo-100">{suggestion.message}</p>
              <button className="text-indigo-600 hover:text-indigo-700 text-xs mt-1">
                Apply fix
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
```

**Backend Changes:**
- Add `GET /api/v1/ai/suggestions` - AI-powered anomaly detection and suggestions
- Integrate with existing learning/reflection infrastructure

**Tests:**
- `ui/src/components/monitoring/__tests__/AiSuggestionsPanel.test.tsx`

#### 2.5 Success Criteria for Phase 2

- [ ] RunTable has inline Approve/Reject buttons
- [ ] RunTable has bulk selection checkboxes
- [ ] Bulk cancel/retry actions work with RBAC enforcement
- [ ] AI suggestions panel shows actionable recommendations
- [ ] Voice commands for bulk actions work
- [ ] Audit logging for all bulk operations

---

### Phase 3: Smart HITL Prioritization (Week 3)

**Goal:** Add AI-powered prioritization to HITL queue.

#### 3.1 Add Urgency Scoring Algorithm

**Backend Changes:**
- Add urgency scoring endpoint: `GET /api/v1/hitl/priority`
- Score formula: `urgency = (1 - confidence) × age × business_impact`
- Business impact factors: pipeline criticality, error rate, SLA proximity

**Frontend Changes:**
- Update `ui/src/hooks/useHitlQueue.ts`:

```typescript
const { data: items, isLoading } = useQuery({
  queryKey: [HITL_QUEUE_QUERY_KEY, tenantId],
  queryFn: async () => {
    const items = await listPendingReviews(tenantId);
    // Sort by urgency score
    return items.sort((a, b) => {
      const urgencyA = calculateUrgency(a);
      const urgencyB = calculateUrgency(b);
      return urgencyB - urgencyA;
    });
  },
  staleTime: 60_000,
});

function calculateUrgency(item: ReviewItem): number {
  const age = Date.now() - new Date(item.createdAt).getTime();
  const confidence = item.confidenceScore ?? 0.5;
  return (1 - confidence) * Math.log(age / (1000 * 60 * 60)); // Age in hours
}
```

#### 3.2 Add Urgency Indicators to HITL List

**Frontend Changes:**
- Update HITL review items to show urgency badges:

```typescript
const getUrgencyLevel = (item: ReviewItem): 'critical' | 'high' | 'medium' | 'low' => {
  const urgency = calculateUrgency(item);
  if (urgency > 0.8) return 'critical';
  if (urgency > 0.5) return 'high';
  if (urgency > 0.2) return 'medium';
  return 'low';
};

const URGENCY_COLORS = {
  critical: 'bg-red-100 text-red-700',
  high: 'bg-orange-100 text-orange-700',
  medium: 'bg-yellow-100 text-yellow-700',
  low: 'bg-gray-100 text-gray-700',
};
```

#### 3.3 Add Bulk Review Actions

**Frontend Changes:**
- Add bulk selection to HITL review queue
- Add bulk approve/reject with RBAC enforcement
- Add voice commands: "Approve all critical items", "Reject low priority items"

**Tests:**
- `ui/src/pages/__tests__/HitlReviewPage.test.tsx`

#### 3.4 Success Criteria for Phase 3

- [ ] HITL queue sorted by urgency
- [ ] Urgency indicators visible on review items
- [ ] Bulk approve/reject actions work
- [ ] Voice commands for bulk review work
- [ ] Audit logging for all review actions

---

### Phase 4: Error State Coverage (Week 3-4)

**Goal:** Add comprehensive error state handling with typed errors.

#### 4.1 Create Typed Error Hierarchy

**Frontend Changes:**
- Create `ui/src/lib/errors.ts`:

```typescript
/**
 * Typed error hierarchy for API responses
 * @doc.type types
 * @doc.purpose Provide structured error types for better error handling
 * @doc.layer frontend
 */

export class ApiError extends Error {
  constructor(
    message: string,
    public status?: number,
    public code?: string,
    public details?: unknown
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export class ValidationError extends ApiError {
  constructor(message: string, details?: unknown) {
    super(message, 422, 'VALIDATION_ERROR', details);
    this.name = 'ValidationError';
  }
}

export class RateLimitError extends ApiError {
  constructor(
    message: string,
    public retryAfter?: number
  ) {
    super(message, 429, 'RATE_LIMIT_ERROR');
    this.name = 'RateLimitError';
  }
}

export class PermissionError extends ApiError {
  constructor(message: string) {
    super(message, 403, 'PERMISSION_DENIED');
    this.name = 'PermissionError';
  }
}

export class ConflictError extends ApiError {
  constructor(message: string, details?: unknown) {
    super(message, 409, 'CONFLICT', details);
    this.name = 'ConflictError';
  }
}
```

**Tests:**
- `ui/src/lib/__tests__/errors.test.ts`

#### 4.2 Update HTTP Client with Error Parsing

**Frontend Changes:**
- Update `ui/src/lib/http-client.ts`:

```typescript
function parseErrorResponse(status: number, data: unknown): Error {
  if (typeof data === 'string') {
    return new ApiError(data, status);
  }
  
  const details = typeof data === 'object' && data !== null ? data : undefined;
  
  if (status === 429) {
    return new RateLimitError(
      details?.message ?? 'Rate limit exceeded',
      details?.retryAfter
    );
  }
  if (status === 422) {
    return new ValidationError(
      details?.message ?? 'Validation failed',
      details
    );
  }
  if (status === 403) {
    return new PermissionError(
      details?.message ?? 'Permission denied'
    );
  }
  if (status === 409) {
    return new ConflictError(
      details?.message ?? 'Resource conflict',
      details
    );
  }
  
  return new ApiError(
    details?.message ?? `HTTP ${status}`,
    status
  );
}
```

#### 4.3 Create Error State Components

**Frontend Changes:**
- Create `ui/src/components/error/RateLimitError.tsx`:

```typescript
/**
 * Rate limit error component with retry
 * @doc.type component
 * @doc.purpose Display rate limit error with retry countdown
 * @doc.layer frontend
 */

import React from 'react';
import { useSpeechSynthesis } from '@audio-video/ui';

interface RateLimitErrorProps {
  retryAfter?: number;
  onRetry: () => void;
}

export const RateLimitError: React.FC<RateLimitErrorProps> = ({ retryAfter, onRetry }) => {
  const { speak } = useSpeechSynthesis();
  
  const handleRetry = () => {
    onRetry();
    speak('Retrying request');
  };

  return (
    <div role="alert" className="bg-amber-50 dark:bg-amber-950 border border-amber-200 dark:border-amber-800 rounded-lg p-4">
      <p className="text-amber-900 dark:text-amber-100 font-medium">Rate limit exceeded</p>
      {retryAfter && <p className="text-amber-700 dark:text-amber-300 text-sm">Retry after {retryAfter}s</p>}
      <button
        onClick={handleRetry}
        className="mt-2 px-4 py-2 bg-amber-600 text-white rounded hover:bg-amber-700 transition-colors"
        aria-label="Retry request"
      >
        Retry
      </button>
    </div>
  );
};
```

- Create similar components for `ValidationError`, `PermissionError`, `ConflictError`

**Tests:**
- Co-located `__tests__/` for each component

#### 4.4 Configure TanStack Query Retry Strategy

**Frontend Changes:**
- Update `ui/src/App.tsx` queryClient configuration:

```typescript
import { ApiError, RateLimitError } from './lib/errors';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: (failureCount, error) => {
        if (error instanceof RateLimitError) {
          // Don't auto-retry rate limit, let user decide
          return 0;
        }
        if (error instanceof ApiError) {
          if (error.status && error.status >= 400 && error.status < 500) {
            return error.status === 408 ? 3 : 0;
          }
        }
        return failureCount < 3;
      },
      retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
      refetchOnWindowFocus: false,
      gcTime: 10 * 60 * 1000,
    },
    mutations: {
      retry: 0,
    },
  },
});
```

#### 4.5 Add Optimistic Updates

**Frontend Changes:**
- Update HITL approve/reject mutations with optimistic updates:

```typescript
const approveMut = useMutation({
  mutationFn: ({ reviewId, note }: { reviewId: string; note?: string }) =>
    approveReview(reviewId, { note, tenantId }),
  onMutate: async ({ reviewId }) => {
    await qc.cancelQueries({ queryKey: [HITL_QUEUE_QUERY_KEY, tenantId] });
    const previous = qc.getQueryData<ReviewItem[]>([HITL_QUEUE_QUERY_KEY, tenantId]);
    qc.setQueryData<ReviewItem[]>([HITL_QUEUE_QUERY_KEY, tenantId], (old = []) =>
      old.filter(item => item.reviewId !== reviewId)
    );
    return { previous };
  },
  onError: (err, { reviewId }, context) => {
    qc.setQueryData([HITL_QUEUE_QUERY_KEY, tenantId], context?.previous);
  },
  onSettled: () => {
    qc.invalidateQueries({ queryKey: [HITL_QUEUE_QUERY_KEY, tenantId] });
  },
});
```

**Tests:**
- Test optimistic update and rollback

#### 4.6 Success Criteria for Phase 4

- [ ] All error types have dedicated components
- [ ] HTTP client parses errors correctly
- [ ] TanStack Query retry strategy configured
- [ ] Optimistic updates work with rollback
- [ ] Error components have TTS feedback

---

### Phase 5: Runtime Validation with Zod (Week 4)

**Goal:** Add Zod schemas for all API responses.

#### 5.1 Add Zod Schemas for API Contracts

**Frontend Changes:**
- Create `ui/src/lib/schemas/api.ts`:

```typescript
/**
 * Zod schemas for API response validation
 * @doc.type schemas
 * @doc.purpose Validate all API responses at runtime
 * @doc.layer frontend
 */

import { z } from 'zod';

// Agent schemas
const AgentRegistration = z.object({
  id: z.string(),
  name: z.string().min(1).max(100),
  tenantId: z.string(),
  version: z.string(),
  status: z.enum(['ACTIVE', 'IDLE', 'ERROR', 'UNKNOWN']),
  capabilities: z.array(z.string()),
  memoryCount: z.number().int().nonnegative(),
  lastSeen: z.string().datetime().optional(),
  registeredAt: z.string().datetime(),
}).strict();

export type AgentRegistration = z.infer<typeof AgentRegistration>;

// Pipeline run schemas
const PipelineRun = z.object({
  id: z.string(),
  pipelineId: z.string(),
  pipelineName: z.string(),
  status: z.enum(['RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED']),
  startedAt: z.string().datetime(),
  finishedAt: z.string().datetime().optional(),
  eventsProcessed: z.number().int().nonnegative(),
  errorsCount: z.number().int().nonnegative(),
}).strict();

export type PipelineRun = z.infer<typeof PipelineRun>;

// Review item schemas
const ReviewItem = z.object({
  reviewId: z.string(),
  tenantId: z.string(),
  skillId: z.string(),
  itemType: z.enum(['POLICY', 'PATTERN', 'AGENT_DECISION']),
  status: z.enum(['PENDING', 'APPROVED', 'REJECTED']),
  proposedVersion: z.record(z.unknown()),
  confidenceScore: z.number().min(0).max(1).optional(),
  createdAt: z.string().datetime(),
  reviewedAt: z.string().datetime().optional(),
  reviewerNote: z.string().optional(),
}).strict();

export type ReviewItem = z.infer<typeof ReviewItem>;

// Learned policy schemas
const LearnedPolicy = z.object({
  id: z.string(),
  tenantId: z.string(),
  skillId: z.string(),
  name: z.string().min(1).max(200),
  description: z.string(),
  status: z.enum(['PENDING_REVIEW', 'APPROVED', 'REJECTED', 'ACTIVE', 'DEPRECATED']),
  confidenceScore: z.number().min(0).max(1),
  episodeCount: z.number().int().nonnegative(),
  version: z.number().int().positive(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
}).strict();

export type LearnedPolicy = z.infer<typeof LearnedPolicy>;

// Response wrapper schemas
const AgentListResponse = z.object({
  agents: z.array(AgentRegistration).optional(),
}).strict();

const PipelineRunsResponse = z.object({
  runs: z.array(PipelineRun).optional(),
}).strict();

const PendingReviewsResponse = z.object({
  items: z.array(ReviewItem).optional(),
}).strict();

const PoliciesResponse = z.object({
  policies: z.array(LearnedPolicy).optional(),
}).strict();
```

**Tests:**
- `ui/src/lib/schemas/__tests__/api.test.ts`

#### 5.2 Add Response Validation Middleware

**Frontend Changes:**
- Update `ui/src/lib/http-client.ts` to add validation:

```typescript
import { z } from 'zod';

async function request<T>(
  method: "GET" | "POST" | "PUT" | "DELETE",
  path: string,
  body?: unknown,
  config: HttpRequestConfig = {},
  schema?: z.ZodSchema<T>
): Promise<HttpResponse<T>> {
  // ... existing request logic ...
  
  const contentType = response.headers.get("content-type") ?? "";
  let data: unknown = null;
  if (response.status !== 204) {
    data = contentType.includes("application/json")
      ? await response.json()
      : await response.text();
  }

  if (!response.ok) {
    throw parseErrorResponse(response.status, data);
  }

  // Validate with Zod if schema provided
  if (schema && contentType.includes("application/json")) {
    const validated = schema.parse(data);
    return {
      data: validated as T,
      status: response.status,
      headers: response.headers,
    };
  }

  return {
    data: data as T,
    status: response.status,
    headers: response.headers,
  };
}
```

- Update all API functions to include schema parameter

#### 5.3 Success Criteria for Phase 5

- [ ] All API responses have Zod schemas
- [ ] HTTP client validates responses
- [ ] Invalid responses throw validation errors
- [ ] All schema tests pass

---

### Phase 6: Voice/NLP Integration (Week 4-5)

**Goal:** Add voice and NLP as first-class interfaces.

#### 6.1 Add VoiceCommandBar Component

**Frontend Changes:**
- Create `ui/src/components/voice/VoiceCommandBar.tsx`:

```typescript
/**
 * Voice command bar for AEP operations
 * @doc.type component
 * @doc.purpose Provide voice commands for common AEP actions
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { useSpeechRecognition, useSpeechSynthesis } from '@audio-video/ui';
import { Mic, MicOff } from 'lucide-react';
import { ConsentManager } from '../privacy/ConsentManager';
import { useNavigate } from 'react-router';

interface VoiceCommand {
  patterns: string[];
  action: () => void;
  description: string;
}

export const VoiceCommandBar: React.FC = () => {
  const { start, stop, isListening, isSupported } = useSpeechRecognition();
  const { speak } = useSpeechSynthesis();
  const navigate = useNavigate();
  const [lastCommand, setLastCommand] = useState<string | null>(null);

  const commands: VoiceCommand[] = [
    {
      patterns: ['show dashboard', 'go to operate', 'show monitoring'],
      action: () => {
        navigate('/operate');
        speak('Showing monitoring dashboard');
      },
      description: 'Navigate to monitoring dashboard',
    },
    {
      patterns: ['show reviews', 'go to hitl', 'show review queue'],
      action: () => {
        navigate('/operate/reviews');
        speak('Showing HITL review queue');
      },
      description: 'Navigate to HITL review queue',
    },
    {
      patterns: ['show pipelines', 'go to build'],
      action: () => {
        navigate('/build');
        speak('Showing pipeline builder');
      },
      description: 'Navigate to pipeline builder',
    },
    {
      patterns: ['show agents', 'go to catalog'],
      action: () => {
        navigate('/catalog/agents');
        speak('Showing agent registry');
      },
      description: 'Navigate to agent registry',
    },
  ];

  const handleTranscript = (text: string, isFinal: boolean) => {
    if (!isFinal) return;
    
    const normalized = text.toLowerCase();
    for (const command of commands) {
      if (command.patterns.some(pattern => normalized.includes(pattern))) {
        setLastCommand(command.description);
        command.action();
        return;
      }
    }
    speak('Command not recognized');
  };

  const toggleListening = () => {
    if (isListening) {
      stop();
    } else {
      start({ onTranscript: handleTranscript });
    }
  };

  if (!isSupported) return null;

  return (
    <ConsentManager purpose="voice_processing" onConsentChange={() => {}}>
      <div className="fixed bottom-4 right-4 z-50">
        <button
          onClick={toggleListening}
          className={cn(
            'p-3 rounded-full shadow-lg transition-colors',
            isListening ? 'bg-red-600 text-white' : 'bg-indigo-600 text-white hover:bg-indigo-700'
          )}
          aria-label={isListening ? 'Stop voice commands' : 'Start voice commands'}
          aria-pressed={isListening}
        >
          {isListening ? <MicOff className="h-6 w-6" /> : <Mic className="h-6 w-6" />}
        </button>
        {lastCommand && (
          <div className="absolute bottom-full right-0 mb-2 bg-gray-900 text-white text-xs px-3 py-1 rounded">
            {lastCommand}
          </div>
        )}
      </div>
    </ConsentManager>
  );
};
```

**Tests:**
- `ui/src/components/voice/__tests__/VoiceCommandBar.test.tsx`

#### 6.2 Add Voice to All Text Inputs

**Frontend Changes:**
- Replace text inputs with VoiceInput in:
  - PipelineListPage search
  - AgentRegistryPage search
  - HitlReviewPage reason input
  - PipelineBuilderPage name/description inputs

#### 6.3 Add NLQ to Search Fields

**Frontend Changes:**
- Replace search inputs with NLQInput in:
  - PipelineListPage
  - AgentRegistryPage
  - MemoryExplorerPage

#### 6.4 Add TTS Feedback for Critical Actions

**Frontend Changes:**
- Add TTS confirmation for:
  - Pipeline run triggered
  - HITL approve/reject
  - Policy approve/reject
  - Bulk operations

**Backend Changes:**
- Add voice intent endpoint for AEP-specific commands
- Map voice commands to API actions

#### 6.5 Success Criteria for Phase 6

- [ ] VoiceCommandBar works with consent
- [ ] All text inputs have voice alternative
- [ ] All search fields support NLQ
- [ ] TTS feedback works for critical actions
- [ ] Voice commands have keyboard equivalents

---

### Phase 7: Keyboard Shortcuts and Accessibility (Week 5)

**Goal:** Add keyboard shortcuts and improve accessibility.

#### 7.1 Add Cmd+K Fuzzy Finder

**Frontend Changes:**
- Create `ui/src/components/core/CommandPalette.tsx`:

```typescript
/**
 * Command palette with fuzzy search and voice
 * @doc.type component
 * @doc.purpose Provide keyboard-driven navigation and actions
 * @doc.layer frontend
 */

import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router';
import { NLQInput } from '../nlp/NLQInput';

interface Command {
  id: string;
  label: string;
  action: () => void;
  keywords: string[];
}

export const CommandPalette: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [query, setQuery] = useState('');
  const navigate = useNavigate();

  const commands: Command[] = [
    { id: 'dashboard', label: 'Go to Monitoring Dashboard', action: () => navigate('/operate'), keywords: ['monitor', 'dashboard', 'operate'] },
    { id: 'reviews', label: 'Go to HITL Reviews', action: () => navigate('/operate/reviews'), keywords: ['hitl', 'review', 'queue'] },
    { id: 'pipelines', label: 'Go to Pipelines', action: () => navigate('/build'), keywords: ['pipeline', 'build'] },
    { id: 'agents', label: 'Go to Agent Registry', action: () => navigate('/catalog/agents'), keywords: ['agent', 'catalog'] },
    { id: 'governance', label: 'Go to Governance', action: () => navigate('/govern'), keywords: ['govern', 'policy'] },
  ];

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setIsOpen(prev => !prev);
      }
      if (e.key === 'Escape') {
        setIsOpen(false);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  if (!isOpen) return null;

  const filteredCommands = commands.filter(cmd =>
    cmd.label.toLowerCase().includes(query.toLowerCase()) ||
    cmd.keywords.some(k => k.includes(query.toLowerCase()))
  );

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50" onClick={() => setIsOpen(false)}>
      <div className="bg-white dark:bg-gray-900 rounded-lg shadow-xl w-full max-w-lg p-4" onClick={e => e.stopPropagation()}>
        <NLQInput
          value={query}
          onChange={setQuery}
          placeholder="Type or say a command..."
          autoFocus
        />
        <div className="mt-4 space-y-1">
          {filteredCommands.map(cmd => (
            <button
              key={cmd.id}
              onClick={() => {
                cmd.action();
                setIsOpen(false);
              }}
              className="w-full text-left px-3 py-2 rounded hover:bg-gray-100 dark:hover:bg-gray-800"
            >
              {cmd.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
};
```

**Tests:**
- `ui/src/components/core/__tests__/CommandPalette.test.tsx`

#### 7.2 Add Breadcrumbs to All Pages

**Frontend Changes:**
- Create `ui/src/components/layout/Breadcrumb.tsx`:

```typescript
/**
 * Breadcrumb navigation component
 * @doc.type component
 * @doc.purpose Show navigation hierarchy
 * @doc.layer frontend
 */

import React from 'react';
import { Link } from 'react-router';

interface BreadcrumbItem {
  label: string;
  to?: string;
}

interface BreadcrumbProps {
  items: BreadcrumbItem[];
}

export const Breadcrumb: React.FC<BreadcrumbProps> = ({ items }) => {
  return (
    <nav aria-label="Breadcrumb" className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
      {items.map((item, index) => (
        <React.Fragment key={index}>
          {index > 0 && <span>/</span>}
          {item.to ? (
            <Link to={item.to} className="hover:text-indigo-600 dark:hover:text-indigo-400">
              {item.label}
            </Link>
          ) : (
            <span className="text-gray-900 dark:text-white">{item.label}</span>
          )}
        </React.Fragment>
      ))}
    </nav>
  );
};
```

- Add breadcrumbs to RunDetailPage, AgentRegistryPage, GovernancePage

#### 7.3 Improve Accessibility

**Frontend Changes:**
- Add ARIA labels to all interactive elements
- Ensure keyboard navigation works for all features
- Add focus indicators
- Test with screen readers

**Tests:**
- Accessibility tests with axe-core
- Keyboard navigation tests

#### 7.4 Success Criteria for Phase 7

- [ ] Cmd+K command palette works
- [ ] All pages have breadcrumbs
- [ ] All features keyboard accessible
- [ ] Screen reader compatible
- [ ] Accessibility tests pass

---

## 7. Testing Strategy

### 7.1 Unit Testing

- All components have co-located `__tests__/` files
- Use Vitest for unit tests
- Test happy path and error states
- Mock API responses

### 7.2 Integration Testing

- Test API client with real schemas
- Test error handling with mock error responses
- Test optimistic updates with rollback
- Test SSE subscription behavior

### 7.3 E2E Testing

- Use Playwright for critical user journeys:
  - Monitor dashboard → approve HITL item
  - Build pipeline → run pipeline → check status
  - Navigate via voice commands
  - Bulk operations

### 7.4 Accessibility Testing

- axe-core automated tests
- Manual screen reader testing
- Keyboard navigation audit

---

## 8. Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Placeholder removal may confuse users | Add clear messaging about feature availability |
| Voice/NLP may not work in all browsers | Graceful degradation to text-only |
| Zod validation may break existing flows | Gradual rollout with feature flags |
| Bulk operations may overload backend | Add rate limiting and progress indicators |
| AI suggestions may be inaccurate | Add confidence thresholds and manual override |

---

## 9. Success Metrics

### 9.1 Quality Metrics

- **Zero placeholder screens** in production build
- **100% API response coverage** with Zod schemas
- **100% error state coverage** with typed errors
- **90%+ accessibility score** (axe-core)
- **All tests passing** (unit, integration, E2E)

### 9.2 UX Metrics

- **Dashboard actionability**: < 2 clicks to complete common tasks
- **HITL queue efficiency**: 50% reduction in review time with prioritization
- **Voice adoption**: 20% of users enable voice features
- **Error recovery**: 90% of errors resolved without page reload

### 9.3 Operational Metrics

- **API error rate**: < 1% for 5xx errors
- **SSE connection reliability**: > 99% uptime
- **Audit log completeness**: 100% of sensitive operations logged

---

## 10. Timeline Summary

| Phase | Duration | Key Deliverables |
|-------|----------|-----------------|
| Phase 0: Foundation | Week 1 | ConsentManager, AuditLog, VoiceInput, NLQInput, RBACGuard |
| Phase 1: Placeholder Removal | Week 1-2 | Feature flags, hidden placeholders |
| Phase 2: Dashboard Ops | Week 2-3 | Inline actions, bulk operations, AI suggestions |
| Phase 3: HITL Prioritization | Week 3 | Urgency scoring, bulk review |
| Phase 4: Error Coverage | Week 3-4 | Typed errors, error components, optimistic updates |
| Phase 5: Runtime Validation | Week 4 | Zod schemas, response validation |
| Phase 6: Voice/NLP | Week 4-5 | VoiceCommandBar, voice inputs, NLQ, TTS |
| Phase 7: Accessibility | Week 5 | Cmd+K, breadcrumbs, accessibility fixes |

**Total Timeline:** 5 weeks for all critical fixes

---

## 11. Appendix

### 11.1 New Files

| File | Purpose |
|------|---------|
| `ui/src/components/privacy/ConsentManager.tsx` | Consent capture and management |
| `ui/src/services/audit-log.ts` | Audit logging service |
| `ui/src/components/voice/VoiceInput.tsx` | Voice-enabled text input |
| `ui/src/components/nlp/NLQInput.tsx` | Natural language query input |
| `ui/src/components/security/RBACGuard.tsx` | Permission guard |
| `ui/src/lib/feature-flags.ts` | Feature flag configuration |
| `ui/src/components/monitoring/AiSuggestionsPanel.tsx` | AI suggestions display |
| `ui/src/lib/errors.ts` | Typed error hierarchy |
| `ui/src/components/error/RateLimitError.tsx` | Rate limit error component |
| `ui/src/components/error/ValidationError.tsx` | Validation error component |
| `ui/src/components/error/PermissionError.tsx` | Permission error component |
| `ui/src/components/error/ConflictError.tsx` | Conflict error component |
| `ui/src/lib/schemas/api.ts` | Zod schemas for API responses |
| `ui/src/components/voice/VoiceCommandBar.tsx` | Voice command bar |
| `ui/src/components/core/CommandPalette.tsx` | Cmd+K command palette |
| `ui/src/components/layout/Breadcrumb.tsx` | Breadcrumb navigation |
| `ui/src/hooks/useSelection.ts` | Bulk selection hook |

### 11.2 Modified Files

| File | Changes |
|------|---------|
| `ui/src/lib/http-client.ts` | Add error parsing, Zod validation |
| `ui/src/App.tsx` | Add CommandPalette, configure retry strategy |
| `ui/src/pages/RunDetailPage.tsx` | Hide tabs behind feature flags |
| `ui/src/pages/GovernancePage.tsx` | Hide sections behind feature flags |
| `ui/src/pages/MonitoringDashboardPage.tsx` | Add inline actions, bulk ops, AI suggestions |
| `ui/src/pages/HitlReviewPage.tsx` | Add urgency sorting, bulk actions |
| `ui/src/components/monitoring/RunTable.tsx` | Add inline actions, checkboxes |
| `ui/src/pages/PipelineListPage.tsx` | Replace search with VoiceInput/NLQInput |
| `ui/src/pages/AgentRegistryPage.tsx` | Replace search with VoiceInput/NLQInput |
| `ui/src/hooks/useHitlQueue.ts` | Add urgency sorting |
| `ui/src/hooks/useLivePipelineRuns.ts` | Add optimistic updates |

### 11.3 Backend API Endpoints Required

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/consent` | POST/GET/DELETE | Consent management |
| `/api/v1/audit/log` | POST | Audit logging |
| `/api/v1/audit/query` | GET | Audit query |
| `/api/v1/nlp/parse` | POST | Natural language parsing |
| `/api/v1/auth/check-permission` | POST | Permission check |
| `/api/v1/ai/suggestions` | GET | AI-powered suggestions |
| `/api/v1/hitl/priority` | GET | HITL priority scoring |

---

*Document follows Ghatana repo guidelines and copilot-instructions.md conventions.*
