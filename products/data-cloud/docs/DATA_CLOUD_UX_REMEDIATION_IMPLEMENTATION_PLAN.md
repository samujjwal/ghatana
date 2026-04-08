# Data-Cloud UX Remediation Implementation Plan

> Document ID: `DATA_CLOUD_UX_REMEDIATION_IMPLEMENTATION_PLAN`
> Version: `1.0.0`
> Status: `DRAFT`
> Date: `2026-04-07`
> Scope: `products/data-cloud/ui/**`
> Primary Goal: Transform Data-Cloud UI from feature-complete to production-grade with dashboard-first UX, pervasive AI/ML, and comprehensive state handling

---

## 1. Executive Summary

The Data-Cloud UI audit (2026-04-07) revealed a strong technical foundation with React 19, TypeScript strict mode, TanStack Query, and proper API client architecture. However, critical UX gaps prevent production readiness:

**Overall UI/UX Maturity Score:** 6.5/10  
**Dashboard-First Effectiveness Score:** 5/10  
**AI-Native UX Score:** 5/10  
**Release Readiness Verdict:** Conditionally Ready After Fixes (6-8 weeks)

### Critical Blockers

1. **Dashboard is decorative, not operational** - IntelligentHub homepage cannot approve/reject/escalate or take meaningful actions
2. **AI/ML is bolted on, not native** - AI exists as explicit features but is not embedded implicitly throughout workflows
3. **Missing comprehensive error state handling** - No handling for rate limit, conflict, offline degradation, retry states
4. **"Continue Working" section is empty** - Broken promise to users with no user-activity API wired

### Implementation Approach

This plan follows Ghatana repo guidelines strictly:
- **Reuse before creating** - Leverage existing `@ghatana/*` packages, platform modules, and Data-Cloud patterns
- **No new dependencies** - Use existing TanStack Query, Jotai, Zod, @ghatana/design-system
- **Type safety at implementation time** - No `any` types, full TypeScript strict compliance
- **Tests as part of change** - Co-located `__tests__/` with Vitest/React Testing Library
- **Observability built-in** - Structured logs, metrics for critical flows

---

## 2. Objectives

### 2.1 Primary Objectives

- Transform IntelligentHub from portal to operational dashboard with actionable cards
- Embed AI/ML implicitly throughout workflows (not as explicit features)
- Add comprehensive error state coverage (rate limit, offline, conflict, retry)
- Implement bulk operations for entity and workflow management
- Reduce navigation cognitive load from 16 items to 8-10 items
- Add runtime validation with Zod schemas for all API boundaries

### 2.2 Secondary Objectives

- Add semantic search via existing platform infrastructure
- Implement natural language query (NLQ) for QuickQueryConsole
- Add inline anomaly detection in entity rows
- Improve accessibility to WCAG 2.1 AA compliance
- Standardize empty state patterns across all pages

### 2.3 Non-Goals

- Replacing the existing React 19 + TanStack Query stack
- Changing the hexagonal backend architecture
- Redesigning the visual design system (use existing @ghatana/design-system)
- Adding new third-party dependencies without clear justification
- Creating duplicate voice/NLP infrastructure when @audio-video/ui already exists

---

## 3. Foundational Infrastructure (Security, Privacy, Voice/NLP)

### 3.1 Security and Privacy as First-Class Citizens

**Guiding Principle:** Every feature must be secure and privacy-compliant by design, not as an afterthought.

**Reuse Existing Infrastructure:**
- **TokenStorage** (`lib/auth/tokenStorage.ts`): Memory-first token storage with sessionStorage fallback
- **TrustCenter** (`pages/TrustCenter.tsx`): Existing governance/privacy dashboard
- **TenantContextFilter** (platform/java): Tenant isolation already implemented
- **RBAC patterns** (copilot-instructions.md section 23): Existing role-based access control

**Privacy Requirements (GDPR/CCPA Compliant):**
- Explicit consent capture for voice data processing
- Data retention limits with automatic purging
- Right to deletion (voice logs, user activity, AI suggestions)
- Audit trails for all privacy-sensitive operations
- Privacy-preserving AI (no raw audio storage, only transcripts)

**Security Requirements:**
- All new endpoints require authentication (reuse JwtAuthFilter)
- Tenant isolation enforced at API boundary
- Input validation with Zod at all boundaries
- CSRF protection for state-changing operations
- Rate limiting on voice endpoints (prevent abuse)

### 3.2 Voice and NLP as First-Class Interface

**Guiding Principle:** Voice and natural language are primary interaction modes, not secondary features.

**Reuse Existing Infrastructure:**
- **@audio-video/ui** (`products/audio-video/libs/audio-video-ui`): Shared voice hooks
  - `useSpeechRecognition` - STT with browser + platform fallback
  - `useSpeechSynthesis` - TTS with browser + platform fallback
- **VoiceCommandBar** (`components/voice/VoiceCommandBar.tsx`): Existing floating voice interface
- **Platform Audio-Video module** (`platform/java/audio-video`): Java STT/TTS engines

**Voice Integration Strategy:**
1. **Every input has voice alternative** - All text inputs support voice dictation
2. **Natural language commands** - Voice commands for all major actions
3. **Voice feedback** - TTS for confirmations and critical alerts
4. **Accessibility-first** - Voice features primarily serve accessibility needs

**NLP Integration Strategy:**
1. **Natural Language Query (NLQ)** everywhere - "Ask Anything" on every page
2. **Intent-based navigation** - Voice commands navigate directly to features
3. **Semantic search** - Find entities/collections by natural language description
4. **Conversational AI** - Contextual help and guidance via voice/text chat

### 3.3 Reusable Component Strategy

**Components to Extract for Cross-Product Use:**

| Component | Current Location | Reusable Package | Products to Benefit |
|-----------|-----------------|------------------|-------------------|
| VoiceCommandBar | `components/voice/` | `@ghatana/voice-ui` | YAPPC, AEP, DCMAAR, FlashIt |
| useNlpQuery hook | New | `@ghatana/nlp-ui` | All products |
| ConsentManager | New | `@ghatana/privacy-ui` | All products |
| AuditLogViewer | `components/governance/` | `@ghatana/audit-ui` | All products |
| RBACGuard | New | `@ghatana/security-ui` | All products |
| VoiceInput | New | `@ghatana/voice-ui` | All products |
| NlqInput | New | `@ghatana/nlp-ui` | All products |

**Extraction Process:**
1. Build components in Data Cloud first (production validation)
2. Extract to platform packages after 2+ products confirm need
3. Maintain backward compatibility during migration
4. Document cross-product usage patterns

---

## 4. Current State Summary

### 4.1 What Is Working

- **Strong technical foundation**: React 19, TypeScript strict mode, TanStack Query, Jotai
- **Well-typed API clients**: `lib/api/client.ts` with proper error handling and timeout
- **AI integration present**: BrainSidebar, AiSuggestionPanel, PipelineAiHintsPanel with graceful fallback
- **Error boundaries**: RouteErrorBoundary, AppErrorBoundary with proper role="alert"
- **Design system usage**: @ghatana/design-system components (Spinner) and @ghatana/theme tokens
- **Test infrastructure**: Vitest, React Testing Library, Playwright configured

### 4.2 Voice/NLP Infrastructure Already Available

- **@audio-video/ui package**: Shared voice hooks already built and tested
  - `useSpeechRecognition` with browser + platform fallback
  - `useSpeechSynthesis` with browser + platform fallback
- **VoiceCommandBar component**: Floating voice interface exists but underutilized
- **Voice intent endpoint**: `POST /api/v1/voice/intent` exists but limited coverage
- **Platform STT/TTS**: Java audio-video module with Whisper, Coqui, Piper engines

**Gap:** Voice is bolted on as a floating button, not integrated into workflows.

### 4.3 Security/Privacy Foundation Exists

- **TokenStorage**: Memory-first secure token management
- **TrustCenter**: Governance dashboard for policies and consent
- **Tenant isolation**: Backend TenantContextFilter enforces data separation
- **RBAC**: Role-based access control patterns established

**Gaps:**
- No consent management for voice data processing
- No audit logging for privacy-sensitive operations
- No data retention controls in UI
- Voice processing lacks explicit user consent flow

### 4.4 What Is Broken or Incomplete

- **Dashboard not operational**: IntelligentHub has empty sections, hardcoded AI recommendations, no actionable cards
- **AI suggestions not actionable**: `onAction` is TODO comment in InsightsPage (line 849)
- **Missing bulk operations**: EntityBrowserPage and WorkflowsPage lack selection state and bulk actions
- **Incomplete error handling**: No RateLimitError, OfflineBanner, ConflictDialog components
- **Navigation cognitive load**: 16 nav items across 4 sections (Core, Intelligence, Operations, Agentic)
- **Type safety gaps**: Some `any` types (line 817 in InsightsPage), no runtime validation with Zod
- **Accessibility gaps**: 29 files have aria/role attributes but not comprehensive
- **Voice/NLP underutilized**: Voice is floating button, not first-class interface
- **Privacy gaps**: No consent management, no audit logs, no retention controls

---

## 5. Guiding Decisions

### 5.1 Dashboard Transformation Approach

**Decision: Make IntelligentHub operational without adding new components**

Reuse existing patterns:
- Use existing `StatCard` component from PageLayout for actionable dashboard cards
- Use existing `AISuggestion` component for AI-powered recommendations
- Use existing TanStack Query hooks for data fetching
- Add new user-activity API endpoint in backend (reuses existing event streaming infrastructure)

**Rationale:**
- Minimizes new code by extending existing components
- Maintains consistency with current design patterns
- Leverages existing backend event infrastructure for user activity tracking

### 5.2 AI/ML Integration Strategy

**Decision: Embed AI implicitly, not as explicit features**

Approach:
- Add AI-assisted schema inference in collection creation (reuses existing schema service)
- Add smart form prefills using existing AI suggestion endpoints
- Add inline AI suggestions in entity editor (extend existing AiSuggestionPanel)
- Make AI actions reviewable but auto-applied by default

**Rationale:**
- Transforms AI from "add-on" to "native layer"
- Reduces manual work without requiring users to "go to AI"
- Uses existing AI endpoints (`/api/v1/analytics/suggest`, `/api/v1/entities/:collection/suggest`)

### 5.3 Error State Coverage Strategy

**Decision: Create shared error state components using existing patterns**

Approach:
- Create `RateLimitError`, `OfflineBanner`, `ConflictDialog` as new components in `components/common/`
- Configure TanStack Query with exponential backoff (extend existing App.tsx configuration)
- Add optimistic updates for mutations using TanStack Query's `optimisticUpdate` option
- Reuse existing `RouteErrorBoundary` and `AppErrorBoundary` patterns

**Rationale:**
- Extends existing error boundary infrastructure
- Uses TanStack Query built-in retry capabilities
- Maintains consistency with current error handling patterns

### 5.4 Navigation Simplification Strategy

**Decision: Consolidate from 4 sections to 3 sections, add Cmd+K fuzzy finder**

Approach:
- Merge "Intelligence" into "Operations" (Trust, Insights become operational features)
- Group "Agentic" under "System" as advanced features
- Add Cmd+K fuzzy finder using existing CommandBar component
- Deprecate legacy routes with HTTP 301 redirects

**Rationale:**
- Reduces cognitive load from 16 to ~8 nav items
- Reuses existing CommandBar infrastructure
- Maintains backward compatibility during transition

### 5.5 Voice/NLP First-Class Strategy

**Decision: Voice and NLP are primary interfaces, not secondary features**

Approach:
- **VoiceInput component**: Reusable voice-enabled text input (wraps existing inputs)
- **NLQ everywhere**: Natural Language Query on every search/input field
- **VoiceCommandBar expansion**: Extend existing component with more intents
- **NLP intent coverage**: Map all UI actions to voice/natural language commands

**Reuse Strategy:**
- Use `@audio-video/ui` hooks (already built and tested)
- Extend existing `VoiceCommandBar` (don't rebuild)
- Use platform Java audio-video module for backend STT/TTS

**Accessibility Requirement:**
- Voice features must work without visual confirmation (screen reader compatible)
- All voice outputs have text alternatives
- Voice commands have keyboard equivalents

**Privacy Requirement:**
- Explicit consent before voice processing
- Clear indicator when microphone is active
- One-click disable voice features

### 5.6 Security/Privacy by Design Strategy

**Decision: Security and privacy are non-negotiable from day 1**

Approach:
- **ConsentManager component**: Reusable consent capture for voice/AI processing
- **AuditLog integration**: All privacy-sensitive operations logged
- **DataRetention controls**: User-facing retention settings
- **RBAC everywhere**: All new endpoints enforce permissions
- **Input validation**: Zod schemas at all boundaries

**Reuse Strategy:**
- Extend existing `TokenStorage` for secure auth
- Extend existing `TrustCenter` for privacy dashboard
- Use platform `TenantContextFilter` for isolation
- Use copilot-instructions.md RBAC patterns

**Compliance Requirements:**
- GDPR: Consent, right to deletion, data portability
- CCPA: Disclosure, opt-out, deletion
- SOC2: Audit trails, access controls

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
  purpose: z.enum(['voice_processing', 'ai_suggestions', 'data_retention', 'analytics']),
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
  action: z.enum(['voice_command', 'data_access', 'consent_change', 'data_deletion', 'ai_suggestion_apply']),
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
        className="px-4 py-2 bg-primary-600 text-white rounded-lg disabled:opacity-50"
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

### Phase 1: Dashboard Operationalization (Week 2-3)

**Goal:** Transform IntelligentHub from portal to operational dashboard with voice/NLP integration and security baked in.

#### 1.1 Wire User-Activity API with Audit Logging

**Backend Changes:**
- Create user-activity tracking endpoint: `GET /api/v1/user-activity/recent`
- Reuse existing event streaming infrastructure (AEP integration)
- Store recent activity in memory cache (reuse existing caching patterns)
- **Security**: Enforce RBAC - users can only see their own activity (reuse TenantContextFilter)
- **Privacy**: Add audit logging for all user activity access (use auditLogService from Phase 0)

**Frontend Changes:**
- Create `ui/src/api/user-activity.service.ts`
- Use existing `apiClient` from `lib/api/client.ts`
- Add Zod schema for response validation:
```typescript
import { z } from 'zod';

const UserActivityItem = z.object({
  id: z.string(),
  type: z.enum(['create', 'update', 'delete', 'query', 'alert']),
  action: z.string(),
  target: z.string(),
  timestamp: z.string(),
  path: z.string(),
}).strict();

export type UserActivityItem = z.infer<typeof UserActivityItem>;
```

**Tests:**
- `ui/src/api/user-activity.service.ts/__tests__/user-activity.service.test.ts`
- Backend integration test for new endpoint

#### 1.2 Make "Continue Working" Functional

**Frontend Changes:**
- Update `IntelligentHub.tsx` to fetch real user activity:
```typescript
const { data: recentActivity } = useQuery({
  queryKey: ['user-activity', 'recent'],
  queryFn: () => userActivityService.getRecentActivity(),
  staleTime: 60_000,
});
```

- Transform activity items to `ContinueWorkingItem` format
- Render cards using existing `ContinueWorkingCard` component

**Tests:**
- `ui/src/pages/__tests__/IntelligentHub.test.tsx` - verify real data rendering
- Mock service for unit tests

#### 1.3 Add Actionable Dashboard Cards

**Frontend Changes:**
- Create new component: `ui/src/components/dashboard/ActionableCard.tsx`
- Reuse existing `StatCard` as base, add action buttons
- Add cards for:
  - Pending approvals (fetch from `/api/v1/approvals/pending`)
  - High-priority alerts (fetch from `/api/v1/alerts/high-priority`)
  - System health status (fetch from existing health endpoint)

**Tests:**
- `ui/src/components/dashboard/__tests__/ActionableCard.test.tsx`
- Integration test with mock API responses

#### 1.4 Implement Real "Ask Anything" with Voice/NLQ

**Frontend Changes:**
- Replace `AskAnythingInput` with `NLQInput` component from Phase 0
- Wire to existing `/api/v1/analytics/query` endpoint for SQL queries
- Add natural language to SQL translation (reuse existing AI infrastructure)
- Add query history using Jotai atom
- **Voice integration**: Use VoiceInput for hands-free query input
- **TTS feedback**: Speak confirmation when query executes (use useSpeechSynthesis)
- **Privacy**: Log all queries to audit log (use auditLogService)

```typescript
// In IntelligentHub.tsx
import { NLQInput } from '../components/nlp/NLQInput';
import { useSpeechSynthesis } from '@audio-video/ui';
import { auditLogService } from '../services/audit-log';

const handleNLQ = async (query: string, intent?: string) => {
  // Log query for audit
  await auditLogService.log({
    userId: currentUser.id,
    tenantId: currentTenant.id,
    action: 'nlq_query',
    resource: 'analytics',
    status: 'success',
    metadata: { query, intent },
  });
  
  // Execute query...
  
  // TTS confirmation
  speak('Query executed successfully');
};
```

**Tests:**
- `ui/src/pages/__tests__/IntelligentHub.test.tsx` - verify NLQ and voice input
- Test query history persistence
- Test audit logging

#### 1.5 Add Voice Command Integration to Dashboard

**Frontend Changes:**
- Extend existing `VoiceCommandBar` with dashboard-specific intents:
  - "Show my recent activity"
  - "Show pending approvals"
  - "Go to collections"
  - "Run analytics"
- Add voice navigation to all dashboard cards
- **Accessibility**: Ensure all voice actions have keyboard equivalents

**Backend Changes:**
- Add dashboard intents to `/api/v1/voice/intent` endpoint
- Map voice commands to dashboard actions

**Tests:**
- Test voice command execution
- Test navigation via voice commands

---

### Phase 2: AI Suggestion Actions (Week 3-4)

**Goal:** Make all AI suggestions actionable with deep-link routing, voice control, and full audit logging.

#### 2.1 Implement Deep-Link Routing with Voice Navigation

**Frontend Changes:**
- Update `InsightsPage.tsx` line 849 - replace TODO with real routing:
```typescript
const handleSuggestionAction = useCallback((suggestion: AnalyticsAiSuggestion) => {
  if (suggestion.type === 'optimization' && suggestion.reasons.includes('query')) {
    navigate('/query', { state: { query: suggestion.description } });
  } else if (suggestion.type === 'anomaly') {
    navigate('/data', { state: { view: 'quality', filter: suggestion.reasons[0] } });
  }
}, [navigate]);
```

- Update `EntityBrowserPage.tsx` - add similar action handlers
- Add route state type definitions in `routes.tsx`

**Tests:**
- `ui/src/pages/__tests__/InsightsPage.test.tsx` - verify navigation
- `ui/src/pages/__tests__/EntityBrowserPage.test.tsx` - verify entity actions

#### 2.2 Add "Apply" Actions for Suggestions with Security and Voice

**Frontend Changes:**
- Extend AI suggestion types with `actionType` field
- Add mutation hooks for applying suggestions with audit logging:
```typescript
export function useApplySuggestion() {
  const { speak } = useSpeechSynthesis();
  
  return useMutation({
    mutationFn: async (suggestion: AnalyticsAiSuggestion) => {
      // RBAC check before applying
      const hasPermission = await checkPermission('ai:suggestions:apply');
      if (!hasPermission) throw new Error('Permission denied');
      
      return apiClient.post('/api/v1/analytics/suggestions/apply', { suggestion });
    },
    onSuccess: (data, suggestion) => {
      // Log to audit
      auditLogService.log({
        action: 'ai_suggestion_apply',
        resource: suggestion.id,
        status: 'success',
        metadata: { suggestionType: suggestion.type },
      });
      
      // TTS feedback
      speak(`Applied ${suggestion.type} suggestion successfully`);
      
      queryClient.invalidateQueries({ queryKey: ['analytics', 'ai-suggestions'] });
    },
    onError: (error) => {
      auditLogService.log({
        action: 'ai_suggestion_apply',
        status: 'failure',
        metadata: { error: error.message },
      });
    },
  });
}
```

**Backend Changes:**
- Add endpoint: `POST /api/v1/analytics/suggestions/apply`
- **Security**: Enforce RBAC - check `ai:suggestions:apply` permission
- **Privacy**: Log all AI suggestion applications to audit log
- Implement suggestion application logic (query rewrite, schema update, etc.)

**Tests:**
- `ui/src/api/analytics.service.ts/__tests__/apply-suggestion.test.ts`
- Test RBAC enforcement
- Test audit logging
- Backend integration test

#### 2.3 Add Suggestion History and Dismissal

**Frontend Changes:**
- Create Jotai atom for dismissed suggestions: `dismissedSuggestionsAtom`
- Persist to localStorage using existing persistence patterns
- Add "Dismiss" button with history view

**Tests:**
- Test dismissal persistence
- Test history rendering

---

### Phase 3: Navigation Simplification with Voice (Week 4-5)

**Goal:** Reduce cognitive load from 16 nav items to ~8 items with voice-enabled navigation.

#### 3.1 Consolidate Navigation Sections

**Frontend Changes:**
- Update `DefaultLayout.tsx` navSections:
```typescript
const navSections: NavSection[] = [
  {
    title: 'Data',
    items: [
      { to: '/', label: 'Home', icon: <Home className="h-4 w-4" />, exact: true },
      { to: '/data', label: 'Collections', icon: <Database className="h-4 w-4" /> },
      { to: '/pipelines', label: 'Pipelines', icon: <Workflow className="h-4 w-4" /> },
      { to: '/query', label: 'Query', icon: <Terminal className="h-4 w-4" /> },
    ],
  },
  {
    title: 'Operations',
    items: [
      { to: '/trust', label: 'Trust & Governance', icon: <Shield className="h-4 w-4" /> },
      { to: '/insights', label: 'Insights', icon: <Brain className="h-4 w-4" /> },
      { to: '/alerts', label: 'Alerts', icon: <Bell className="h-4 w-4" /> },
    ],
  },
  {
    title: 'System',
    items: [
      { to: '/events', label: 'Events', icon: <Activity className="h-4 w-4" /> },
      { to: '/memory', label: 'Memory', icon: <Box className="h-4 w-4" /> },
      { to: '/entities', label: 'Entities', icon: <Database className="h-4 w-4" /> },
      { to: '/fabric', label: 'Data Fabric', icon: <Network className="h-4 w-4" /> },
      { to: '/agents', label: 'Agents', icon: <Bot className="h-4 w-4" /> },
      { to: '/plugins', label: 'Plugins', icon: <Package className="h-4 w-4" /> },
      { to: '/settings', label: 'Settings', icon: <Settings className="h-4 w-4" /> },
    ],
  },
];
```

**Tests:**
- `ui/src/layouts/__tests__/DefaultLayout.test.tsx` - verify nav structure

#### 3.2 Add Voice-Enabled Cmd+K Fuzzy Finder

**Frontend Changes:**
- Extend existing `CommandBar` component with fuzzy search AND voice input
- Add keyboard shortcut listener (Cmd+K / Ctrl+K)
- Index all routes and actions for search
- **Voice integration**: Add VoiceInput to command palette for hands-free navigation
- **NLQ support**: Users can say "Go to collections" or "Open settings"
- Reuse existing `@ghatana/design-system` components
- Reuse VoiceInput and NLQInput from Phase 0

```typescript
// CommandBar with voice
import { VoiceInput } from '../voice/VoiceInput';
import { useSpeechRecognition } from '@audio-video/ui';

export function CommandBar() {
  const [query, setQuery] = useState('');
  const { start, stop, isListening } = useSpeechRecognition();
  
  // Voice command: "Go to [page]" navigates directly
  const handleVoiceCommand = (transcript: string) => {
    const match = findCommandByVoice(transcript);
    if (match) executeCommand(match);
  };
  
  return (
    <div className="command-palette">
      <VoiceInput
        value={query}
        onChange={setQuery}
        placeholder="Type or say a command..."
      />
      {/* Command results */}
    </div>
  );
}
```

**Tests:**
- `ui/src/components/core/__tests__/CommandBar.test.tsx`
- Test keyboard shortcuts
- Test voice navigation commands

#### 3.3 Deprecate Legacy Routes

**Frontend Changes:**
- Update `routes.tsx` to add redirects:
```typescript
{
  path: 'dashboard',
  element: <Navigate to="/" replace />,
},
{
  path: 'collections',
  element: <Navigate to="/data" replace />,
},
// ... other legacy routes
```

**Backend Changes:**
- Add HTTP 301 redirects in backend router
- Update API documentation

**Tests:**
- Test redirect behavior
- Test backward compatibility during transition

---

### Phase 4: Bulk Operations with Voice and Audit (Week 5-6)

**Goal:** Add bulk selection and actions with voice commands, full audit logging, and RBAC enforcement.

#### 4.1 Add Selection State Pattern

**Frontend Changes:**
- Create shared hook: `ui/src/hooks/useSelection.ts`
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

**Tests:**
- `ui/src/hooks/__tests__/useSelection.test.ts`

#### 4.4 Add Bulk Operations to EntityBrowserPage with Security and Voice

**Frontend Changes:**
- Add checkbox column to table
- Add bulk action toolbar (delete, export, move)
- Wrap with RBACGuard to check permissions
- **Voice integration**: "Delete selected", "Export all", "Select all"
- Implement bulk delete mutation with audit logging:
```typescript
const bulkDeleteMutation = useMutation({
  mutationFn: async (ids: string[]) => {
    // RBAC check
    const hasPermission = await checkPermission('entities:bulk:delete');
    if (!hasPermission) throw new Error('Permission denied');
    
    return Promise.all(ids.map(id => deleteEntity(namespace, id)));
  },
  onSuccess: (data, ids) => {
    // Audit log
    auditLogService.log({
      action: 'bulk_delete',
      resource: `entities:${namespace}`,
      status: 'success',
      metadata: { count: ids.length },
    });
    
    // TTS feedback
    speak(`Deleted ${ids.length} entities`);
    
    qc.invalidateQueries({ queryKey: ['dc', 'entities', namespace] });
  },
  onError: (error, ids) => {
    auditLogService.log({
      action: 'bulk_delete',
      resource: `entities:${namespace}`,
      status: 'failure',
      metadata: { count: ids.length, error: error.message },
    });
  },
});
```

**Backend Changes:**
- Add bulk delete endpoint: `DELETE /api/v1/entities/:namespace/bulk`
- **Security**: Enforce `entities:bulk:delete` permission
- **Privacy**: Log all bulk operations to audit log
- Implement transactional bulk operations

**Tests:**
- `ui/src/pages/__tests__/EntityBrowserPage.test.tsx` - verify bulk operations
- Test RBAC enforcement
- Test audit logging
- Test voice commands
- Backend integration test for bulk endpoint

#### 4.5 Add Bulk Operations to WorkflowsPage with Security and Voice

**Frontend Changes:**
- Add checkbox column to workflow table
- Add bulk actions (pause, run, stop, archive)
- Wrap with RBACGuard to check `workflows:bulk:*` permissions
- **Voice integration**: "Pause selected workflows", "Run all workflows"
- Implement bulk action mutations with audit logging and TTS feedback (same pattern as 4.4)

**Backend Changes:**
- Add bulk workflow endpoints with RBAC enforcement
- Log all bulk workflow operations to audit log

**Tests:**
- `ui/src/pages/__tests__/WorkflowsPage.test.tsx` - verify bulk operations
- Test RBAC enforcement
- Test audit logging
- Test voice commands

---

### Phase 5: Error State Coverage with Voice (Week 6-7)

**Goal:** Add comprehensive error state handling with voice announcements for accessibility.

#### 5.1 Create Shared Error State Components with Voice

**Frontend Changes:**
- Create `ui/src/components/error/RateLimitError.tsx` with TTS support:
```typescript
interface RateLimitErrorProps {
  retryAfter?: number;
  onRetry: () => void;
}

export const RateLimitError: React.FC<RateLimitErrorProps> = ({ retryAfter, onRetry }) => (
  <div role="alert" className="bg-amber-50 border border-amber-200 rounded-lg p-4">
    <p className="text-amber-900 font-medium">Rate limit exceeded</p>
    {retryAfter && <p className="text-amber-700 text-sm">Retry after {retryAfter}s</p>}
    <button onClick={onRetry} className="mt-2 px-4 py-2 bg-amber-600 text-white rounded">
      Retry
    </button>
  </div>
);
```

- Create `ui/src/components/error/OfflineBanner.tsx`
- Create `ui/src/components/error/ConflictDialog.tsx`

**Tests:**
- Co-located `__tests__/` for each component

#### 5.2 Configure TanStack Query Retry Strategy

**Frontend Changes:**
- Update `App.tsx` queryClient configuration:
```typescript
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: CACHE_TIMES.DEFAULT_STALE_MS,
      retry: (failureCount, error) => {
        // Don't retry on 4xx errors except 408, 429
        if (error instanceof ApiError) {
          if (error.status && error.status >= 400 && error.status < 500) {
            return error.status === 408 || error.status === 429 ? 3 : 0;
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

**Tests:**
- Test retry behavior with mock errors

#### 5.3 Add Optimistic Updates

**Frontend Changes:**
- Update entity delete mutation in `EntityBrowserPage.tsx`:
```typescript
const deleteMutation = useMutation({
  mutationFn: (id: string) => deleteEntity(namespace, id),
  onMutate: async (id) => {
    await qc.cancelQueries({ queryKey: ['dc', 'entities', namespace] });
    const previous = qc.getQueryData(['dc', 'entities', namespace]);
    qc.setQueryData(['dc', 'entities', namespace], (old: EntityListResponse | undefined) => ({
      ...old,
      entities: old?.entities.filter(e => e.id !== id) ?? [],
      total: (old?.total ?? 0) - 1,
    }));
    return { previous };
  },
  onError: (err, id, context) => {
    qc.setQueryData(['dc', 'entities', namespace], context?.previous);
  },
  onSettled: () => {
    qc.invalidateQueries({ queryKey: ['dc', 'entities', namespace] });
  },
});
```

**Tests:**
- Test optimistic update and rollback

---

### Phase 6: Runtime Validation (Week 6-7)

**Goal:** Add Zod schemas for all API responses, remove all `any` types

#### 6.1 Add Zod Schemas for API Contracts

**Frontend Changes:**
- Create `ui/src/lib/schemas/api.ts` with all response schemas:
```typescript
import { z } from 'zod';

const Collection = z.object({
  id: z.string(),
  name: z.string().min(1).max(100),
  description: z.string().optional(),
  schemaType: z.enum(['entity', 'event', 'timeseries', 'graph', 'document']),
  status: z.enum(['active', 'draft', 'archived', 'processing']),
  entityCount: z.number().int().nonnegative(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  schema: z.object({
    fields: z.array(z.object({
      name: z.string(),
      type: z.string(),
      required: z.boolean(),
      description: z.string().optional(),
    })).optional(),
  }).optional(),
}).strict();

export type Collection = z.infer<typeof Collection>;

const PaginatedCollectionResponse = z.object({
  items: z.array(Collection),
  total: z.number().int().nonnegative(),
  page: z.number().int().positive(),
  pageSize: z.number().int().positive(),
  hasMore: z.boolean(),
}).strict();

export type PaginatedCollectionResponse = z.infer<typeof PaginatedCollectionResponse>;
```

- Add schemas for Workflow, Entity, AnalyticsResult, etc.

**Tests:**
- `ui/src/lib/schemas/__tests__/api.test.ts` - verify schema validation

#### 6.2 Add Response Validation Middleware

**Frontend Changes:**
- Update `lib/api/client.ts` to add validation:
```typescript
private async request<T>(
  url: string, 
  config: ApiRequestConfig = {},
  schema?: z.ZodSchema<T>
): Promise<T> {
  // ... existing request logic ...
  
  const payload = await response.json() as unknown;
  
  if (schema) {
    const validated = schema.parse(payload);
    return validated as T;
  }
  
  return payload as T;
}
```

- Update all API calls to include schema parameter

**Tests:**
- Test validation with invalid responses
- Test valid responses pass through

#### 6.3 Remove All `any` Types

**Frontend Changes:**
- Replace line 817 in `InsightsPage.tsx`:
```typescript
// Before:
const collectionNames: string[] = (collectionsData as any[] | undefined)
  ?.map((c: any) => c.name ?? c.id ?? '')
  .filter(Boolean) ?? [];

// After:
const collectionNames: string[] = collectionsData
  ?.map((c) => typeof c === 'object' && c !== null ? (c as { name?: string; id?: string }).name ?? (c as { name?: string; id?: string }).id ?? '' : '')
  .filter(Boolean) ?? [];
```

- Use `unknown` with type guards for untyped data
- Add proper type guards where needed

**Tests:**
- TypeScript compilation must pass with no `any` types
- `tsc --noEmit` must pass

---

### Phase 7: AI Integration Depth (Week 7-8)

**Goal:** Embed AI implicitly throughout workflows

#### 7.1 Add AI-Assisted Schema Inference

**Frontend Changes:**
- Update collection creation form to call existing schema suggestion API
- Add `useSchemaSuggestion` hook:
```typescript
export function useSchemaSuggestion(sampleData: Record<string, unknown>[]) {
  return useMutation({
    mutationFn: (data: Record<string, unknown>[]) => 
      apiClient.post('/schema/suggest', { samples: data }),
  });
}
```

- Display suggested schema with accept/edit options

**Backend Changes:**
- Ensure `/schema/suggest` endpoint exists (already documented in data-cloud-api.ts)
- Implement schema inference using existing AI infrastructure

**Tests:**
- Test schema suggestion rendering
- Test schema acceptance flow

#### 7.2 Add Smart Form Prefills

**Frontend Changes:**
- Create `useSmartPrefill` hook that calls AI suggestion endpoints
- Add prefills to entity creation form based on context
- Use existing `/api/v1/entities/:collection/suggest` endpoint

**Tests:**
- Test form prefills
- Test user can override prefills

#### 7.3 Add Inline AI Suggestions in Entity Editor

**Frontend Changes:**
- Extend `EntityDetailPanel` in `EntityBrowserPage.tsx`
- Add inline suggestions for field completion, validation
- Reuse existing `AiSuggestionPanel` component

**Tests:**
- Test inline suggestion rendering
- Test suggestion application

---

## 7. Library Reuse Strategy

### 7.1 Existing @ghatana/* Packages to Use

| Package | Purpose | Usage in This Plan |
|---------|---------|-------------------|
| `@ghatana/design-system` | UI components | Spinner, Button, Card, Modal |
| `@ghatana/theme` | Theme tokens | All styling, color tokens |
| `@ghatana/platform-utils` | Utility functions | Date formatting, string utilities |
| `@ghatana/api` | API client utilities | Already using via lib/api/client.ts |
| `@ghatana/accessibility-audit` | A11y testing | Add to CI for accessibility checks |
| `@audio-video/ui` | Voice hooks | `useSpeechRecognition`, `useSpeechSynthesis` |

### 7.2 Existing Platform Modules to Reuse

| Module | Purpose | Usage in This Plan |
|--------|---------|-------------------|
| `platform/java:database` | Database operations | Backend user-activity storage |
| `platform/java:http` | HTTP utilities | Already using in backend |
| `platform/java:observability` | Metrics/tracing | Add metrics for dashboard actions |
| `platform/java:security` | Auth/security | Already using TokenStorage |
| `platform/java:audio-video` | STT/TTS engines | Backend voice processing |
| `platform/java:ai-integration` | AI/ML utilities | Schema inference, suggestions |

### 7.3 New Reusable Components (Future Platform Packages)

Components built in this plan are designed for extraction to platform packages:

| Component | Built In | Future Package | Products to Benefit |
|-----------|----------|----------------|-------------------|
| `ConsentManager` | Phase 0 | `@ghatana/privacy-ui` | All products |
| `AuditLogService` | Phase 0 | `@ghatana/audit-ui` | All products |
| `VoiceInput` | Phase 0 | `@ghatana/voice-ui` | YAPPC, AEP, FlashIt |
| `NLQInput` | Phase 0 | `@ghatana/nlp-ui` | All products |
| `RBACGuard` | Phase 0 | `@ghatana/security-ui` | All products |

**Extraction Criteria:**
- Component must be used in 2+ products
- Component must have comprehensive tests
- Component must have clear API documentation
- Component must follow platform package conventions

### 7.4 No New Dependencies

**Explicit decision to NOT add:**
- No new UI component libraries (use existing @ghatana/design-system)
- No new state management libraries (use existing TanStack Query + Jotai)
- No new form libraries (use existing React Hook Form patterns if needed)
- No new validation libraries (use existing Zod)
- No new testing libraries (use existing Vitest + React Testing Library + Playwright)
- No new voice/NLP libraries (use existing @audio-video/ui)

---

## 8. Testing Strategy

### 8.1 Test Coverage Requirements

- **Unit tests**: All new hooks, utilities, components
- **Integration tests**: API client with schemas, mutation flows
- **E2E tests**: Critical user journeys (dashboard actions, bulk operations)
- **Contract tests**: API schema validation
- **Accessibility tests**: axe-core for all new components

### 8.2 Test File Placement

Following Ghatana guidelines - co-located `__tests__/`:

```
ui/src/
  components/
    dashboard/
      ActionableCard.tsx
      __tests__/
        ActionableCard.test.tsx
  hooks/
    useSelection.ts
    __tests__/
      useSelection.test.ts
  lib/
    schemas/
      api.ts
      __tests__/
        api.test.ts
```

### 8.3 Quality Gates

- All TypeScript must pass `tsc --noEmit` with strict mode
- ESLint must pass with zero warnings
- Test coverage minimum 80% for new code
- Accessibility audit must pass for new components
- Bundle size must not increase significantly

---

## 9. Observability Plan

### 9.1 Metrics to Add

- Dashboard action metrics (approvals, escalations, dismissals)
- AI suggestion acceptance rate
- Bulk operation success/failure rates
- Error state occurrences (rate limit, offline, conflict)
- Navigation patterns (Cmd+K usage)

### 9.2 Logging

- Structured logs for all dashboard actions
- Log AI suggestion application with confidence scores
- Log bulk operation results with counts
- Log error state occurrences with context

### 9.3 Tracing

- Add OpenTelemetry spans for dashboard card actions
- Trace AI suggestion application flow
- Trace bulk operation execution

---

## 10. Migration Strategy

### 10.1 Backward Compatibility

- Maintain legacy routes during transition with HTTP 301 redirects
- Keep existing AI suggestion endpoints unchanged
- Maintain existing API contracts (extend, don't break)

### 10.2 Rollback Plan

- Each phase is independently revertable
- Feature flags for new features (dashboard cards, bulk operations)
- Database migrations are additive only

### 10.3 Deployment Strategy

- Deploy in phases (each phase = separate release)
- Use canary deployment for dashboard changes
- Monitor metrics after each phase

---

## 11. Success Criteria

### 11.1 Phase 0 Success (Foundation)

- [ ] ConsentManager captures and stores consent for voice/AI processing
- [ ] AuditLog service logs all privacy-sensitive operations
- [ ] VoiceInput component works in all text inputs with consent check
- [ ] NLQInput component parses natural language to intents
- [ ] RBACGuard enforces permissions on all new features
- [ ] All components have co-located tests passing
- [ ] Components designed for cross-product reuse

### 11.2 Phase 1 Success (Dashboard)

- [ ] IntelligentHub shows real user activity in "Continue Working"
- [ ] Dashboard has 3+ actionable cards with working actions
- [ ] "Ask Anything" executes real queries with NLQ support and voice input
- [ ] Voice commands work for dashboard navigation
- [ ] All dashboard actions are audit logged

### 11.3 Phase 2 Success (AI Actions)

- [ ] All AI suggestions have working action handlers
- [ ] Deep-link routing works for all suggestion types
- [ ] Suggestion history and dismissal functional
- [ ] RBAC enforced on AI suggestion application
- [ ] TTS feedback for AI actions

### 11.4 Phase 3 Success (Navigation)

- [ ] Navigation reduced from 16 to ~8 items
- [ ] Cmd+K fuzzy finder functional with voice input
- [ ] Voice navigation commands work ("Go to collections")
- [ ] Legacy routes redirect properly

### 11.5 Phase 4 Success (Bulk Operations)

- [ ] EntityBrowserPage has bulk selection and actions with RBAC
- [ ] WorkflowsPage has bulk selection and actions with RBAC
- [ ] Bulk operations work correctly with audit logging
- [ ] Voice commands work for bulk operations
- [ ] TTS feedback for bulk operation results

### 11.6 Phase 5 Success (Error States)

- [ ] RateLimitError, OfflineBanner, ConflictDialog components exist
- [ ] TanStack Query configured with exponential backoff
- [ ] Optimistic updates working for key mutations
- [ ] TTS announcements for critical errors (accessibility)

### 11.7 Phase 6 Success (Runtime Validation)

- [ ] All API responses have Zod schemas
- [ ] Response validation middleware in place
- [ ] Zero `any` types in codebase
- [ ] All schemas include audit trail fields

### 11.8 Phase 7 Success (AI Integration)

- [ ] Schema inference works in collection creation
- [ ] Smart prefills work in entity creation
- [ ] Inline AI suggestions work in entity editor
- [ ] All AI features have consent management
- [ ] All AI actions are audit logged

---

## 12. Risks and Mitigations

### 12.1 Risk: Backend API Changes Required

**Mitigation:** Coordinate with backend team early, phase backend changes to align with frontend phases

### 12.2 Risk: Performance Impact from New Features

**Mitigation:** Profile dashboard cards, use TanStack Query caching, lazy load AI suggestions

### 12.3 Risk: User Confusion from Navigation Changes

**Mitigation:** Use gradual rollout, maintain redirects, add onboarding tour

### 12.4 Risk: AI Suggestions Not Useful

**Mitigation:** Add feedback mechanism, track acceptance rates, iterate on suggestion quality

### 12.5 Risk: Voice Privacy Concerns

**Risk:** Users may be concerned about voice data privacy and consent.

**Mitigation:**
- Explicit consent management with clear disclosures
- No raw audio storage (only transcripts)
- Clear microphone activity indicators
- One-click disable voice features
- Privacy dashboard in TrustCenter

### 12.6 Risk: NLQ/NLP Accuracy

**Risk:** Natural language queries may not parse correctly, leading to user frustration.

**Mitigation:**
- Graceful fallback to keyword search
- Intent confidence thresholds
- Confirmation dialogs for low-confidence parses
- Continuous model improvement based on user feedback

### 12.7 Risk: Compliance Violations

**Risk:** GDPR/CCPA/SOC2 compliance gaps in audit logging or consent management.

**Mitigation:**
- Legal review of consent flows
- Automated compliance testing
- Regular audit log reviews
- Data retention policy enforcement

---

## 13. Definition of Done

Each phase is complete when:

1. Code follows existing Ghatana conventions (copilot-instructions.md)
2. Existing platform/shared packages checked before new abstractions
3. TypeScript compiles with strict mode, zero `any` types
4. Relevant tests added/updated and pass
5. Formatting, linting, static checks pass
6. Errors and important flows have observability
7. Inputs validated at correct boundaries
8. No repo drift in architecture, naming, or dependencies

---

## 14. References

- Ghatana Copilot Instructions: `.github/copilot-instructions.md`
- Data-Cloud UX Audit Report: `docs/DATA_CLOUD_UX_AUDIT_REPORT.md` (to be created)
- Existing Remediation Plan: `docs/DATA_CLOUD_REMEDIATION_IMPLEMENTATION_PLAN.md`
- API Documentation: `REST_API_DOCUMENTATION.md`
- Architecture Docs: `docs-generated/02-architecture-decisions-design/01-system-architecture.md`

---

## Appendix A: File Changes Summary

### New Files (Phase 0 - Foundation)

- `ui/src/components/privacy/ConsentManager.tsx`
- `ui/src/components/privacy/__tests__/ConsentManager.test.tsx`
- `ui/src/services/audit-log.ts`
- `ui/src/services/__tests__/audit-log.test.ts`
- `ui/src/components/voice/VoiceInput.tsx`
- `ui/src/components/voice/__tests__/VoiceInput.test.tsx`
- `ui/src/components/nlp/NLQInput.tsx`
- `ui/src/components/nlp/__tests__/NLQInput.test.tsx`
- `ui/src/components/security/RBACGuard.tsx`
- `ui/src/components/security/__tests__/RBACGuard.test.tsx`

### New Files (Other Phases)

- `ui/src/api/user-activity.service.ts`
- `ui/src/components/dashboard/ActionableCard.tsx`
- `ui/src/components/error/RateLimitError.tsx`
- `ui/src/components/error/OfflineBanner.tsx`
- `ui/src/components/error/ConflictDialog.tsx`
- `ui/src/hooks/useSelection.ts`
- `ui/src/lib/schemas/api.ts`

### Modified Files

- `ui/src/pages/IntelligentHub.tsx`
- `ui/src/pages/InsightsPage.tsx`
- `ui/src/pages/EntityBrowserPage.tsx`
- `ui/src/pages/WorkflowsPage.tsx`
- `ui/src/layouts/DefaultLayout.tsx`
- `ui/src/routes.tsx`
- `ui/src/App.tsx`
- `ui/src/lib/api/client.ts`

### Backend Files (Coordination Required)

- New endpoint: `POST /api/v1/consent` (Phase 0)
- New endpoint: `POST /api/v1/audit/log` (Phase 0)
- New endpoint: `POST /api/v1/nlp/parse` (Phase 0)
- New endpoint: `POST /api/v1/auth/check-permission` (Phase 0)
- New endpoint: `GET /api/v1/user-activity/recent` (Phase 1)
- New endpoint: `POST /api/v1/analytics/suggestions/apply` (Phase 2)
- New endpoint: `DELETE /api/v1/entities/:namespace/bulk` (Phase 4)
- Update existing: `/schema/suggest` (ensure implementation exists)

---

## Appendix B: Timeline

| Week | Phase | Deliverables |
|------|-------|--------------|
| 1 | Phase 0 | Foundation: ConsentManager, AuditLog, VoiceInput, NLQInput, RBACGuard |
| 2-3 | Phase 1 | Dashboard operationalization with voice/NLQ |
| 3-4 | Phase 2 | AI suggestion actions with security/audit |
| 4-5 | Phase 3 | Navigation simplification with voice commands |
| 5-6 | Phase 4 | Bulk operations with voice/audit/RBAC |
| 6-7 | Phase 5 | Error state coverage with voice announcements |
| 7 | Phase 6 | Runtime validation with Zod schemas |
| 8 | Phase 7 | AI integration depth with consent management |

**Total Duration:** 8 weeks to production-ready UI with security, privacy, and voice/NLP as first-class features
