# Flashit Service Consolidation Plan

**Objective**: Consolidate 15 separate services into 5 core services

**Date**: March 17, 2026

---

## Current Service Architecture (15 services)

### API Layer
1. flashit-web-api (gateway) - Main API server
2. flashit-auth-service - Authentication (separate)
3. flashit-billing-service - Stripe integration (separate)
4. flashit-notification-service - Email/push (separate)

### AI/ML Services
5. flashit-classification-agent - Java service for content classification
6. flashit-embedding-agent - Java service for vector embeddings
7. flashit-reflection-agent - Java service for AI reflections
8. flashit-transcription-agent - Java service for audio transcription

### Background Workers
9. flashit-analytics-worker - BullMQ worker for analytics
10. flashit-export-worker - Export processing
11. flashit-deletion-worker - GDPR deletion

### Storage/Cache
12. flashit-s3-service - MinIO/S3 operations
13. flashit-redis-service - Caching layer

### Monitoring
14. flashit-telemetry-service - Metrics collection
15. flashit-logging-service - Log aggregation

---

## Target Architecture (5 services)

### 1. Flashit Core API (Node.js/Fastify)
**Combines**: web-api + auth + billing + notifications

**Responsibilities**:
- User authentication (JWT + refresh tokens)
- Moment CRUD operations
- Billing (Stripe integration)
- Notifications (email via SES/SMTP, push)
- Real-time features (WebSocket)

**Justification**: These are all user-facing APIs that need to share authentication context and database connections. Consolidating reduces latency and simplifies deployment.

**Implementation**:
```typescript
// Single Fastify server with route modules
server.register(authRoutes);
server.register(billingRoutes);
server.register(notificationRoutes);
server.register(momentRoutes);
```

### 2. Flashit AI Engine (Java/ActiveJ)
**Combines**: classification + embedding + reflection + transcription agents

**Responsibilities**:
- Content classification (NSFW, categories)
- Vector embedding generation
- AI reflection generation
- Audio/video transcription

**Justification**: All AI workloads share similar resource requirements (GPU/memory) and can benefit from shared model loading and caching.

**Implementation**:
```java
// Single ActiveJ HTTP server with multiple endpoints
@Provides
AsyncServlet servlet() {
    return RoutingServlet.create()
        .map("/api/classify", classifyHandler)
        .map("/api/embed", embeddingHandler)
        .map("/api/reflect", reflectionHandler)
        .map("/api/transcribe", transcriptionHandler);
}
```

### 3. Flashit Background Processor (Node.js/BullMQ)
**Combines**: analytics + export + deletion workers

**Responsibilities**:
- Analytics event processing
- Data export generation
- GDPR/data deletion
- Periodic cleanup tasks

**Justification**: Background jobs share the same queue infrastructure (Redis/BullMQ) and can run in the same process with different worker threads.

**Implementation**:
```typescript
// Multiple workers in single process
const analyticsWorker = new Worker('analytics', analyticsProcessor);
const exportWorker = new Worker('export', exportProcessor);
const deletionWorker = new Worker('deletion', deletionProcessor);
```

### 4. Flashit Storage Service (MinIO/S3)
**Existing**: No change needed

**Responsibilities**:
- File upload/download
- Presigned URL generation
- Storage tiering

**Justification**: This is already a minimal, focused service. Keep separate for scaling independently.

### 5. Flashit Observability Stack (Prometheus/Grafana)
**Combines**: telemetry + logging

**Responsibilities**:
- Metrics collection (Prometheus)
- Log aggregation (Loki)
- Distributed tracing (Tempo)

**Justification**: Modern observability stacks are designed to work together. Use Grafana stack for unified monitoring.

---

## Migration Plan

### Phase 1: Core API Consolidation (Week 1-2)

**Day 1-3: Merge auth into gateway**
- Move auth routes from `flashit-auth-service` to `flashit-web-api`
- Update JWT middleware
- Test authentication flow

**Day 4-6: Merge billing into gateway**
- Move Stripe integration
- Update webhook handlers
- Test billing flows

**Day 7-10: Merge notifications into gateway**
- Move email service
- Move push notification logic
- Test notification delivery

**Day 11-14: Testing & Rollout**
- Integration testing
- Load testing
- Gradual rollout with feature flags

### Phase 2: AI Engine Consolidation (Week 3-4)

**Day 1-7: Java service merge**
- Create unified AI engine entry point
- Migrate classification endpoint
- Migrate embedding endpoint
- Migrate reflection endpoint
- Migrate transcription endpoint

**Day 8-14: Testing & Optimization**
- Model sharing between tasks
- GPU utilization optimization
- Load testing

### Phase 3: Background Workers (Week 5-6)

**Day 1-7: Worker consolidation**
- Create unified worker process
- Migrate analytics processor
- Migrate export processor
- Migrate deletion processor

**Day 8-14: Testing & Monitoring**
- Queue health monitoring
- Worker scaling configuration

### Phase 4: Observability (Week 7-8)

**Day 1-7: Grafana stack setup**
- Deploy Prometheus
- Deploy Loki
- Deploy Tempo
- Deploy Grafana

**Day 8-14: Migration**
- Migrate metrics
- Migrate logs
- Dashboard creation

---

## Resource Savings

| Resource | Before (15 services) | After (5 services) | Savings |
|----------|----------------------|-------------------|---------|
| Memory | 15 × 256MB = 3.8GB | 5 × 512MB = 2.5GB | -34% |
| CPU Cores | 15 cores | 8 cores | -47% |
| DB Connections | 15 pools | 5 pools | -67% |
| Deployment Time | 15 minutes | 5 minutes | -67% |
| Monthly Cost | ~$800 | ~$400 | -50% |

---

## Risks & Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Service too large | Medium | Keep modular code structure; can split later if needed |
| Different scaling needs | Low | Use Kubernetes HPA per deployment; services can still scale independently |
| Technology mismatch (Java/Node) | None | Keep AI engine separate (Java) from API (Node) |
| Deployment complexity | Low | Use feature flags for gradual rollout |

---

## Success Metrics

- [ ] 5 services running in production
- [ ] P95 latency < 500ms (vs < 800ms before)
- [ ] 99.9% uptime maintained
- [ ] Deployment time < 5 minutes
- [ ] Cost reduction 40-50%

---

## Implementation Script

```bash
#!/bin/bash
# consolidate-services.sh

echo "Starting Flashit service consolidation..."

# Phase 1: Core API
echo "Phase 1: Consolidating Core API..."
cp -r flashit-auth-service/src/routes/* flashit-web-api/src/routes/
cp -r flashit-billing-service/src/routes/* flashit-web-api/src/routes/
cp -r flashit-notification-service/src/routes/* flashit-web-api/src/routes/

# Phase 2: AI Engine
echo "Phase 2: Consolidating AI Engine..."
mkdir -p flashit-ai-engine/src/main/java/com/flashit/ai
# ... Java service consolidation

# Phase 3: Background Workers
echo "Phase 3: Consolidating Background Workers..."
cp -r flashit-analytics-worker/src flashit-background-processor/src/
cp -r flashit-export-worker/src flashit-background-processor/src/
cp -r flashit-deletion-worker/src flashit-background-processor/src/

echo "Consolidation complete. Review changes before deployment."
```

---

**End of Service Consolidation Plan**
