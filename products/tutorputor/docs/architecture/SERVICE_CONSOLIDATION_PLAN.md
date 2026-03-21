# TutorPutor Service Consolidation Plan

## Overview

This document outlines the Phase 1 service consolidation strategy to further reduce operational complexity while maintaining system capabilities.

## Current State (Post-Initial Consolidation)

| Service | Purpose | Status |
|---------|---------|--------|
| tutorputor-platform | Consolidated backend (replaced 28 services) | ✅ Active |
| api-gateway | API entry point | ✅ Active |
| tutorputor-content-generation | Java content generation | ⚠️ Java/ActiveJ |
| tutorputor-ai-agents | gRPC AI agents | ⚠️ Java/Gradle |
| tutorputor-ai-proxy | Node.js AI proxy | ⚠️ Separate service |
| tutorputor-kernel-registry | Kernel management | ⚠️ Can be merged |
| tutorputor-payments | Stripe integration | ⚠️ Low traffic |
| tutorputor-lti | LTI 1.3 integration | ⚠️ Niche use case |
| tutorputor-vr | VR labs | ⚠️ Experimental |

## Target Architecture (Phase 1)

### Core Services (3)

```
┌─────────────────────────────────────────────────────────────┐
│                    TUTORPUTOR CORE SERVICES                  │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────┐      ┌──────────────────┐             │
│  │  web-gateway     │      │  ai-runtime      │             │
│  │  (Node.js)       │──────│  (Java/ActiveJ)  │             │
│  │  • Web API       │      │  • AI generation │             │
│  │  • Auth/Z        │      │  • Content gen   │             │
│  │  • Payments      │      │  • Model routing │             │
│  │  • LTI           │      └──────────────────┘             │
│  └──────────────────┘                                        │
│           │                                                  │
│           │         ┌──────────────────┐                     │
│           └─────────│  kernel-service  │                     │
│                     │  (Node.js)       │                     │
│                     │  • Learning logic│                     │
│                     │  • Assessments   │                     │
│                     │  • Simulations   │                     │
│                     └──────────────────┘                     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Consolidation Strategy

#### 1. Merge tutorputor-payments into web-gateway
**Rationale:** Low traffic, tightly coupled with user management
**Effort:** Medium (2 weeks)
**Changes:**
- Move Stripe integration to web-gateway
- Migrate payment webhooks
- Update frontend to use consolidated endpoint

#### 2. Merge tutorputor-lti into web-gateway
**Rationale:** LTI is authentication/authorization concern
**Effort:** Medium (2 weeks)
**Changes:**
- Integrate LTI 1.3 Advantage into auth flow
- Add LTI middleware to web-gateway
- Maintain separate LTI configuration

#### 3. Merge tutorputor-kernel-registry into kernel-service
**Rationale:** Kernel management should be with learning logic
**Effort:** Low (1 week)
**Changes:**
- Combine kernel registry with learning-kernel
- Consolidate plugin management
- Simplify deployment

#### 4. Merge tutorputor-ai-proxy into web-gateway
**Rationale:** AI proxy is a thin wrapper, adds no value as separate service
**Effort:** Low (1 week)
**Changes:**
- Integrate AI provider abstraction into web-gateway
- Maintain provider routing logic
- Update client SDKs

#### 5. Evaluate tutorputor-vr fate
**Options:**
- Merge into kernel-service (if VR becomes core feature)
- Maintain as experimental (if usage stays low)
- Archive (if not used in production)

**Decision:** Maintain as experimental for now, monitor usage

### Java Services (Keep Separate)

#### tutorputor-content-generation & tutorputor-ai-agents
**Rationale:** 
- Heavy AI processing requires different runtime optimization
- Java/ActiveJ provides better concurrency for CPU-intensive tasks
- gRPC provides efficient communication with Node.js gateway

**Integration:**
- Keep as AI-runtime cluster behind web-gateway
- Use gRPC for efficient Node.js ↔ Java communication
- Implement circuit breakers for resilience

## Implementation Timeline

### Sprint 1 (Weeks 1-2): Payments & LTI Migration
- [ ] Migrate payments endpoints
- [ ] Update webhook handlers
- [ ] Migrate LTI configuration
- [ ] Test authentication flows

### Sprint 2 (Weeks 3-4): AI Proxy & Kernel Registry
- [ ] Merge AI proxy into gateway
- [ ] Consolidate kernel services
- [ ] Update service discovery
- [ ] Performance testing

### Sprint 3 (Weeks 5-6): Testing & Rollout
- [ ] E2E testing of consolidated services
- [ ] Load testing
- [ ] Documentation updates
- [ ] Gradual rollout (canary deployment)

## Risk Mitigation

### Service Coupling Risks
- **Risk:** Increased blast radius from consolidation
- **Mitigation:** Maintain modular code structure, use feature flags

### Performance Risks
- **Risk:** Gateway becomes bottleneck
- **Mitigation:** Horizontal scaling, caching layer, circuit breakers

### Deployment Risks
- **Risk:** Complex deployment of unified service
- **Mitigation:** Feature flags, blue-green deployment

## Benefits

### Operational
- **Reduced Complexity:** 9 services → 3 services
- **Simpler Monitoring:** Fewer dashboards and alerts
- **Lower Infrastructure Cost:** Fewer containers/instances

### Development
- **Easier Testing:** End-to-end testing simpler
- **Faster Onboarding:** New developers understand system quicker
- **Less Context Switching:** Single codebase for core logic

### Reliability
- **Fewer Failure Points:** Less inter-service communication
- **Easier Debugging:** Request tracing simpler
- **Better Caching:** Shared memory for related data

## Migration Checklist

### Pre-Migration
- [ ] Document current API contracts
- [ ] Set up comprehensive monitoring
- [ ] Create rollback plan
- [ ] Prepare feature flags for gradual rollout

### During Migration
- [ ] Deploy consolidated services to staging
- [ ] Run automated test suite
- [ ] Load test with production traffic patterns
- [ ] Monitor error rates and latency

### Post-Migration
- [ ] Decommission old services
- [ ] Update documentation
- [ ] Clean up DNS/service discovery entries
- [ ] Update CI/CD pipelines

## Success Metrics

| Metric | Before | Target | Measurement |
|--------|--------|--------|-------------|
| Services | 9 | 3 | Service count |
| API Latency p99 | Baseline | < +10% | APM metrics |
| Error Rate | Baseline | < +0.1% | Error tracking |
| Deploy Time | Baseline | -30% | CI/CD metrics |
| Cost | Baseline | -20% | Infrastructure billing |

## Post-Consolidation Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    FINAL ARCHITECTURE                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Frontend (React)                                            │
│       │                                                      │
│       ▼                                                      │
│  ┌──────────────────────────────────────┐                   │
│  │        Web Gateway (Node.js)         │                   │
│  │  ┌──────────┐ ┌──────────┐ ┌────────┐ │                   │
│  │  │   API    │ │  Auth/Z  │ │Payment │ │                   │
│  │  │  Routes  │ │  (JWT)   │ │(Stripe)│ │                   │
│  │  └──────────┘ └──────────┘ └────────┘ │                   │
│  │  ┌──────────┐ ┌──────────┐ ┌────────┐ │                   │
│  │  │   LTI    │ │ AI Proxy │ │Caching │ │                   │
│  │  │(LTI 1.3) │ │(Multi)   │ │(Redis) │ │                   │
│  │  └──────────┘ └──────────┘ └────────┘ │                   │
│  └──────────────────────────────────────┘                   │
│       │                    │                                │
│       ▼                    ▼                                │
│  ┌─────────────┐     ┌──────────────────┐                    │
│  │   Kernel    │     │   AI Runtime     │                    │
│  │  Service    │     │   (Java/ActiveJ) │                    │
│  │  (Node.js)  │     │  ┌──────────────┐ │                    │
│  │ • Learning  │     │  │ Content Gen  │ │                    │
│  │ • Assessment│     │  │ AI Agents    │ │                    │
│  │ • Simulation│     │  │ Model Router │ │                    │
│  │ • VR (opt)  │     │  └──────────────┘ │                    │
│  └─────────────┘     └──────────────────┘                    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Conclusion

This consolidation reduces operational complexity while maintaining clear separation of concerns:
- **Web Gateway:** User-facing API, auth, payments, external integrations
- **Kernel Service:** Domain logic (learning, assessment, simulation)
- **AI Runtime:** Heavy AI processing with optimized Java runtime

Total service count: **9 → 3** (67% reduction)
Operational improvement: Significant simplification with maintained scalability
