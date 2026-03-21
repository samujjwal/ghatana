# TutorPutor Service Consolidation Analysis

**Date:** March 21, 2026  
**Part of:** P1 - Short-Term Execution Plan (1 month)  
**Owner:** Architecture Team

---

## Executive Summary

Current state: **15 services** → Target: **5 consolidated services**

This document provides a detailed analysis of service consolidation candidates to simplify the TutorPutor architecture while maintaining clear boundaries and separation of concerns.

---

## Current Service Inventory

### Backend Services (9)

| Service | Purpose | Status | Consolidation Target |
|---------|---------|--------|---------------------|
| tutorputor-platform | Core business logic | ✅ Active | **CORE** |
| api-gateway | API routing/layer | ✅ Active | **GATEWAY** |
| tutorputor-ai-proxy | AI provider abstraction | ❌ Failing | → platform |
| tutorputor-kernel-registry | Plugin management | ❌ Failing | → platform |
| tutorputor-db | Data access layer | ⚠️ Partial | → platform |
| tutorputor-lti | LTI 1.3 integration | ❌ Failing | → platform |
| tutorputor-payments | Stripe billing | ❌ Failing | → platform |
| tutorputor-content-studio | Content generation | ❌ Failing | → platform |
| tutorputor-sim-author | Simulation authoring | ❌ Failing | → platform |

### Frontend Applications (4)

| Application | Purpose | Status | Consolidation Target |
|-------------|---------|--------|---------------------|
| tutorputor-web | Main learning platform | ❌ Failing | **WEB** |
| tutorputor-admin | Admin dashboard | ❌ Failing | → web |
| tutorputor-explorer | Content discovery | ❌ Failing | → web |
| tutorputor-mobile | Mobile app | ❌ Failing | **MOBILE** |

### Shared Libraries (15+)

| Library | Purpose | Status | Consolidation Target |
|---------|---------|--------|---------------------|
| learning-kernel | Core learning engine | ✅ Active | **CORE** |
| physics-simulation | Simulation runtime | ✅ Active | **SIMULATION** |
| simulation-engine | Authoring tools | ❌ Failing | → physics-simulation |
| sim-renderer | Rendering components | ❌ Failing | → physics-simulation |
| sim-sdk | Simulation SDK | ✅ Active | **SIMULATION** |
| tutorputor-ui-shared | Shared UI components | ✅ Active | **UI** |
| animator | Animation library | ✅ Active | → ui-shared |
| assessments | Assessment engine | ✅ Active | **ASSESSMENT** |
| contracts | API contracts | ✅ Active | **CORE** |

---

## Consolidation Strategy

### Phase 1: Core Platform Consolidation (Weeks 1-2)

**Merge into `tutorputor-platform`:**

1. **tutorputor-ai-proxy** → platform/src/ai/
   - AI provider abstraction layer
   - Cost tracking integration
   - Prompt management

2. **tutorputor-db** → platform/src/db/
   - Prisma client exports
   - Database utilities
   - Migration scripts

3. **tutorputor-kernel-registry** → platform/src/plugins/
   - Plugin registration
   - Lifecycle management

4. **tutorputor-lti** → platform/src/integrations/lti/
   - LTI 1.3 handlers
   - Grade passback

5. **tutorputor-payments** → platform/src/billing/
   - Stripe integration
   - Subscription management

**Rationale:** All these are infrastructure/support services that platform already depends on. Consolidating reduces deployment complexity and improves startup time.

### Phase 2: Content Services Consolidation (Weeks 3-4)

**Merge into new `tutorputor-content` service:**

1. **tutorputor-content-studio** → content/src/generation/
2. **tutorputor-sim-author** → content/src/simulations/
3. **tutorputor-analytics** (if exists) → content/src/analytics/

**Rationale:** Content generation, simulation authoring, and analytics are all content lifecycle operations that share similar patterns and can benefit from unified caching and queuing.

### Phase 3: Frontend Consolidation (Weeks 5-6)

**Merge into `tutorputor-web`:**

1. **tutorputor-admin** → web/src/admin/
   - Route: /admin/*
   - Role-based access control

2. **tutorputor-explorer** → web/src/explorer/
   - Route: /explore/*
   - Public content discovery

**Rationale:** Admin and explorer are essentially different views of the same data. Unified deployment reduces infrastructure costs and simplifies authentication.

### Phase 4: Library Consolidation (Weeks 7-8)

**Merge simulation libraries:**

1. **simulation-engine** → physics-simulation/src/authoring/
2. **sim-renderer** → physics-simulation/src/rendering/

**Result:** Single `@tutorputor/physics-simulation` package with:
- Runtime simulation execution
- Authoring tools
- Rendering components
- SDK exports

---

## Target Architecture (5 Services)

```
┌─────────────────────────────────────────────────────────────┐
│                        FRONTEND LAYER                        │
├─────────────────┬─────────────────┬─────────────────────────┤
│  tutorputor-web │ tutorputor-mobile│  (future: desktop)     │
│  (React/Vite)   │  (React Native)  │                        │
└────────┬────────┴────────┬────────┴─────────────────────────┘
         │                 │
         └─────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────────┐
│                    API GATEWAY LAYER                        │
│              (tutorputor-gateway)                           │
│    - Authentication  - Rate limiting  - Request routing     │
└─────────────────────────────────────────────────────────────┘
         │
    ┌────┴────┬──────────────┬──────────────┐
    │         │              │              │
┌───▼───┐ ┌──▼─────┐   ┌────▼───┐   ┌─────▼────┐
│CORE   │ │CONTENT │   │SIM     │   │ANALYTICS │
│       │ │       │   │        │   │          │
│Platform│ │Studio │   │Runtime │   │Engine    │
│       │ │       │   │        │   │          │
│- User │ │- Gen  │   │- Exec  │   │- Metrics │
│- Learn│ │- Auth │   │- Render│   │- Reports │
│- Bill │ │- Cache│   │- Collab│   │- Insights│
└───────┘ └───────┘   └────────┘   └──────────┘
```

---

## Implementation Priorities

### High Priority (Immediate)

1. **Platform Consolidation**
   - Effort: XL (2-3 weeks)
   - Impact: Critical
   - Blockers: Build issues must be resolved first

2. **Frontend Unification**
   - Effort: L (1-2 weeks)
   - Impact: High
   - Dependencies: Component library stabilization

### Medium Priority (Next Month)

3. **Content Service Merge**
   - Effort: L (1 week)
   - Impact: Medium
   - Dependencies: AI proxy stabilization

4. **Simulation Library Merge**
   - Effort: M (3-4 days)
   - Impact: Medium
   - Dependencies: physics-simulation build fixes

---

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Build failures block consolidation | High | Fix P0 build issues first |
| Service coupling increases | Medium | Maintain clear internal module boundaries |
| Deployment complexity | Low | Use feature flags for gradual migration |
| Team coordination | Medium | Assign clear ownership per consolidation |

---

## Success Metrics

- **Service Count:** 15 → 5 (67% reduction)
- **Build Success:** Current 73% → Target 95%
- **Deployment Time:** Target <5 minutes per service
- **Startup Time:** Target <10 seconds per service

---

## Conclusion

Service consolidation is feasible and will significantly improve operational efficiency. The primary blocker is resolving build issues (P0) before consolidation can proceed safely.

**Recommendation:** Complete P0 build fixes, then execute Phase 1 consolidation immediately to maximize benefits.
