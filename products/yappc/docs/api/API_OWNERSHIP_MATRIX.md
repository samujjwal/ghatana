# API Ownership Matrix

**Date**: January 29, 2026  
**Status**: Active  
**Version**: 1.0

---

## Architecture Overview

```
Frontend (Port 7001)
        ↓
   Single Entry Point
        ↓
API Gateway (Port 7002) ← Frontend talks to ONLY this port
        ↓
   ┌────┴────┐
   ↓         ↓
Node.js    Java Backend
Backend    (Port 7003)
(Internal)
```

**Key Principle**: Frontend NEVER talks directly to Java backend. All requests go through the API Gateway on port 7002.

---

## API Ownership by Service

### Node.js Backend (Port 7002 - Internal Handlers)

**Owner**: @nodejs-team  
**Technology**: Fastify, Prisma, PostgreSQL  
**Responsibility**: Core business entities and real-time features

#### Endpoints Owned

| Endpoint Pattern                                 | Description          | Status      | Priority |
| ------------------------------------------------ | -------------------- | ----------- | -------- |
| **Workspaces**                                   |                      |             |          |
| `GET /api/workspaces`                            | List workspaces      | ✅ Complete | P0       |
| `POST /api/workspaces`                           | Create workspace     | ✅ Complete | P0       |
| `GET /api/workspaces/:id`                        | Get workspace        | ✅ Complete | P0       |
| `PUT /api/workspaces/:id`                        | Update workspace     | ✅ Complete | P0       |
| `DELETE /api/workspaces/:id`                     | Delete workspace     | ✅ Complete | P0       |
| `GET /api/workspaces/:id/members`                | List members         | ✅ Complete | P1       |
| `POST /api/workspaces/:id/members`               | Add member           | ✅ Complete | P1       |
| `GET /api/workspaces/:id/analytics`              | Analytics            | ✅ Complete | P2       |
| **Projects**                                     |                      |             |          |
| `GET /api/projects`                              | List projects        | ✅ Complete | P0       |
| `POST /api/projects`                             | Create project       | ✅ Complete | P0       |
| `GET /api/projects/:id`                          | Get project          | ✅ Complete | P0       |
| `PUT /api/projects/:id`                          | Update project       | ✅ Complete | P0       |
| `DELETE /api/projects/:id`                       | Delete project       | ✅ Complete | P0       |
| `POST /api/projects/include`                     | Include in workspace | ✅ Complete | P1       |
| `GET /api/projects/:id/analytics`                | Analytics            | ✅ Complete | P2       |
| `GET /api/projects/:id/health`                   | Health score         | ✅ Complete | P2       |
| **Canvas**                                       |                      |             |          |
| `GET /api/projects/:projectId/canvas`            | Load canvas          | ✅ Complete | P0       |
| `PUT /api/projects/:projectId/canvas`            | Save canvas          | ✅ Complete | P0       |
| `POST /api/canvas/:projectId/:canvasId/versions` | Create version       | ✅ Complete | P1       |
| `GET /api/canvas/:projectId/:canvasId/versions`  | List versions        | ✅ Complete | P1       |
| `POST /api/canvas/:projectId/:canvasId/restore`  | Restore version      | ✅ Complete | P1       |
| `POST /api/canvas/ai/suggest-components`         | AI suggestions       | ❌ Missing  | P1       |
| `POST /api/canvas/ai/validate`                   | AI validation        | ❌ Missing  | P1       |
| `POST /api/canvas/ai/generate-code`              | Code generation      | ❌ Missing  | P1       |
| **Lifecycle**                                    |                      |             |          |
| `GET /api/lifecycle/phases`                      | List phases          | ❌ Missing  | P0       |
| `GET /api/lifecycle/projects/:id/current`        | Current phase        | ❌ Missing  | P0       |
| `POST /api/lifecycle/projects/:id/transition`    | Transition phase     | ❌ Missing  | P0       |
| `GET /api/lifecycle/artifacts`                   | List artifacts       | ❌ Missing  | P0       |
| `POST /api/lifecycle/artifacts`                  | Create artifact      | ❌ Missing  | P0       |
| `GET /api/lifecycle/artifacts/:id`               | Get artifact         | ❌ Missing  | P0       |
| `PUT /api/lifecycle/artifacts/:id`               | Update artifact      | ❌ Missing  | P0       |
| `POST /api/lifecycle/gates/validate`             | Validate gate        | ❌ Missing  | P0       |
| `GET /api/lifecycle/gates/:stage/status`         | Gate status          | ❌ Missing  | P1       |
| **DevSecOps**                                    |                      |             |          |
| `POST /api/devsecops/scan/code`                  | Code scan            | ❌ Missing  | P1       |
| `POST /api/devsecops/scan/dependencies`          | Dependency scan      | ❌ Missing  | P1       |
| `GET /api/devsecops/scan/:scanId`                | Scan results         | ❌ Missing  | P1       |
| `POST /api/devsecops/compliance/check`           | Compliance check     | ❌ Missing  | P1       |
| `GET /api/devsecops/compliance/reports`          | Reports              | ❌ Missing  | P2       |
| **GraphQL**                                      |                      |             |          |
| `POST /graphql`                                  | GraphQL API          | ✅ Complete | P1       |

**Total Endpoints**: 47  
**Complete**: 22 (47%)  
**Missing**: 25 (53%)

---

### Java Backend (Port 7003 - Proxied via Gateway)

**Owner**: @java-team  
**Technology**: ActiveJ HTTP, High-performance async  
**Responsibility**: AI/ML, Complex analytics, High-throughput processing

#### Endpoints Owned

| Endpoint Pattern                             | Description         | Status      | Priority |
| -------------------------------------------- | ------------------- | ----------- | -------- |
| **Left Rail**                                |                     |             |          |
| `GET /api/rail/components`                   | Component list      | ✅ Complete | P1       |
| `GET /api/rail/infrastructure`               | Infrastructure      | ✅ Complete | P1       |
| `GET /api/rail/files`                        | File explorer       | ✅ Complete | P1       |
| `GET /api/rail/data-sources`                 | Data sources        | ✅ Complete | P1       |
| `GET /api/rail/history`                      | Recent items        | ✅ Complete | P2       |
| `GET /api/rail/favorites`                    | Favorites           | ✅ Complete | P2       |
| `POST /api/rail/suggestions`                 | AI suggestions      | ✅ Complete | P1       |
| **Agents**                                   |                     |             |          |
| `GET /api/agents`                            | List agents         | ✅ Complete | P0       |
| `GET /api/agents/:name`                      | Get agent           | ✅ Complete | P0       |
| `POST /api/agents/:name/execute`             | Execute agent       | ✅ Complete | P0       |
| `GET /api/agents/health`                     | Health check        | ✅ Complete | P1       |
| `GET /api/agents/capabilities`               | Capabilities        | ✅ Complete | P1       |
| **Requirements**                             |                     |             |          |
| `GET /api/requirements`                      | List requirements   | ✅ Complete | P0       |
| `POST /api/requirements`                     | Create requirement  | ✅ Complete | P0       |
| `GET /api/requirements/:id`                  | Get requirement     | ✅ Complete | P0       |
| `PUT /api/requirements/:id`                  | Update requirement  | ✅ Complete | P0       |
| `DELETE /api/requirements/:id`               | Delete requirement  | ✅ Complete | P0       |
| `POST /api/requirements/:id/approve`         | Approve             | ✅ Complete | P1       |
| `GET /api/requirements/funnel`               | Analytics           | ✅ Complete | P2       |
| `GET /api/requirements/domains`              | Domains             | ✅ Complete | P1       |
| **AI Suggestions**                           |                     |             |          |
| `POST /api/ai/suggestions/generate`          | Generate            | ✅ Complete | P1       |
| `GET /api/ai/suggestions`                    | List                | ✅ Complete | P1       |
| `GET /api/ai/suggestions/inbox`              | Inbox               | ✅ Complete | P1       |
| `GET /api/ai/suggestions/:id`                | Get                 | ✅ Complete | P1       |
| `POST /api/ai/suggestions/:id/accept`        | Accept              | ✅ Complete | P1       |
| `POST /api/ai/suggestions/:id/reject`        | Reject              | ✅ Complete | P1       |
| **Architecture**                             |                     |             |          |
| `POST /api/architecture/analyze`             | Analyze             | ✅ Complete | P1       |
| `GET /api/architecture/impact/:id`           | Impact analysis     | ✅ Complete | P1       |
| **Audit**                                    |                     |             |          |
| `POST /api/audit/record`                     | Record event        | ✅ Complete | P1       |
| `GET /api/audit/events`                      | Query events        | ✅ Complete | P1       |
| `GET /api/audit/events/:id`                  | Get event           | ✅ Complete | P1       |
| **Version Control**                          |                     |             |          |
| `POST /api/version/create`                   | Create version      | ✅ Complete | P1       |
| `GET /api/version/history/:entityId`         | History             | ✅ Complete | P1       |
| `POST /api/version/:entityId/rollback`       | Rollback            | ✅ Complete | P1       |
| **Authorization**                            |                     |             |          |
| `POST /api/auth/check-permission`            | Check permission    | ✅ Complete | P0       |
| `GET /api/auth/user/permissions`             | User permissions    | ✅ Complete | P0       |
| `GET /api/auth/persona/:persona/permissions` | Persona permissions | ✅ Complete | P1       |
| **Approvals**                                |                     |             |          |
| `GET /api/approvals`                         | List approvals      | ✅ Complete | P1       |
| `POST /api/approvals`                        | Create approval     | ✅ Complete | P1       |
| `PUT /api/approvals/:id`                     | Update approval     | ✅ Complete | P1       |
| **Config**                                   |                     |             |          |
| `GET /api/config/domains`                    | Get domains         | ✅ Complete | P1       |
| `GET /api/config/personas`                   | Get personas        | ✅ Complete | P1       |
| **Dashboard**                                |                     |             |          |
| `GET /api/dashboard/metrics`                 | Metrics             | ✅ Complete | P2       |
| `GET /api/dashboard/insights`                | Insights            | ✅ Complete | P2       |
| **Workflow Agents**                          |                     |             |          |
| `GET /api/workflow-agents`                   | List agents         | ✅ Complete | P1       |
| `POST /api/workflow-agents/:id/execute`      | Execute             | ✅ Complete | P1       |

**Total Endpoints**: 46  
**Complete**: 46 (100%)  
**Missing**: 0 (0%)

---

### Shared Endpoints (Both Backends)

| Endpoint       | Node.js | Java | Status   | Notes             |
| -------------- | ------- | ---- | -------- | ----------------- |
| `GET /health`  | ✅      | ✅   | Complete | Both implement    |
| `GET /metrics` | ✅      | ✅   | Complete | Prometheus format |

---

## Gateway Routing Configuration

**File**: `app-creator/apps/api/src/middleware/BackendGateway.ts`

### Routes Proxied to Java Backend

```typescript
const JAVA_BACKEND_ROUTES = [
  "/api/rail", // Left rail components
  "/api/agents", // AI agents
  "/api/requirements", // Requirements engine
  "/api/ai/suggestions", // AI suggestions
  "/api/architecture", // Architecture analysis
  "/api/audit", // Audit logging
  "/api/version", // Version control
  "/api/auth", // Authorization
  "/api/approvals", // Approval workflows
  "/api/config", // Configuration
  "/api/dashboard", // Dashboard
  "/api/workflow-agents", // Workflow agents
  "/metrics", // Prometheus (Java)
];
```

### Routes Handled Locally (Node.js)

```typescript
const NODE_BACKEND_ROUTES = [
  "/api/workspaces", // Workspace CRUD
  "/api/projects", // Project CRUD
  "/api/canvas", // Canvas operations
  "/api/lifecycle", // Lifecycle management
  "/api/devsecops", // DevSecOps
  "/graphql", // GraphQL API
  "/health", // Health check (Node)
];
```

---

## Priority Assignments

### P0 - Critical (Blocks E2E)

**Must be implemented for basic functionality**

#### Node.js Team

- [ ] `GET /api/lifecycle/phases`
- [ ] `GET /api/lifecycle/projects/:id/current`
- [ ] `POST /api/lifecycle/projects/:id/transition`
- [ ] `GET /api/lifecycle/artifacts`
- [ ] `POST /api/lifecycle/artifacts`
- [ ] `GET /api/lifecycle/artifacts/:id`
- [ ] `PUT /api/lifecycle/artifacts/:id`
- [ ] `POST /api/lifecycle/gates/validate`

#### Java Team

- [x] All P0 endpoints complete ✅

**Total P0**: 8 endpoints  
**Complete**: 0/8 (0%)

### P1 - High Priority (Major Features)

**Should be implemented next**

#### Node.js Team

- [ ] Canvas AI endpoints (3)
- [ ] DevSecOps scanning (3)
- [ ] Additional lifecycle (1)

#### Java Team

- [x] All P1 endpoints complete ✅

**Total P1**: 7 endpoints  
**Complete**: 0/7 (0%)

### P2 - Nice to Have (Enhanced Features)

**Can be implemented later**

#### Node.js Team

- Analytics endpoints
- Advanced reporting

#### Java Team

- [x] All P2 endpoints complete ✅

---

## Implementation Priorities

### Week 1-2: Foundation

- [x] Document API ownership (this document)
- [ ] Generate OpenAPI specs
- [ ] Update API Gateway routing
- [ ] Add comprehensive logging

### Week 3-4: Critical APIs (P0)

- [ ] Implement all 8 lifecycle endpoints
- [ ] Test from frontend
- [ ] Write E2E tests

### Week 5-6: High Priority (P1)

- [ ] Canvas AI endpoints
- [ ] DevSecOps scanning
- [ ] Frontend integration

---

## Team Responsibilities

### Node.js Team (@nodejs-team)

**Primary Owner**: Backend API Gateway + Business Logic  
**Tech Stack**: Fastify, Prisma, PostgreSQL, Redis  
**Focus Areas**:

- Core CRUD operations
- Real-time collaboration (WebSocket)
- GraphQL API
- Lifecycle orchestration
- Canvas persistence

**Current Workload**:

- 47 total endpoints
- 22 complete (47%)
- 25 missing (53%)
- 8 P0 endpoints to implement

### Java Team (@java-team)

**Primary Owner**: AI/ML + High-Performance Services  
**Tech Stack**: ActiveJ HTTP, Promise-based async  
**Focus Areas**:

- AI agent orchestration
- Complex analytics
- High-throughput processing
- Audit logging
- RBAC enforcement

**Current Workload**:

- 46 total endpoints
- 46 complete (100%) ✅
- 0 missing
- All P0 complete ✅

---

## Decision Log

### Why This Split?

**Node.js Backend**:

- ✓ Better for CRUD operations with ORM (Prisma)
- ✓ Rich ecosystem for web APIs
- ✓ Easy WebSocket integration
- ✓ Great for rapid iteration

**Java Backend**:

- ✓ Better for CPU-intensive AI/ML tasks
- ✓ ActiveJ provides high throughput
- ✓ Strong typing for complex logic
- ✓ Excellent for parallel processing

### Why Single Gateway?

**Frontend Simplicity**:

- ✓ Only one API endpoint to configure
- ✓ No CORS complications
- ✓ Unified authentication
- ✓ Consistent error handling

**Backend Flexibility**:

- ✓ Can move endpoints between backends
- ✓ Can add new backends easily
- ✓ Load balancing at gateway level
- ✓ A/B testing capabilities

---

## Configuration

### Environment Variables

#### API Gateway (Port 7002)

```bash
PORT=7002
NODE_ENV=development
JAVA_BACKEND_URL=http://localhost:7003
DATABASE_URL=postgresql://user:pass@localhost:5432/yappc
REDIS_URL=redis://localhost:6379
```

#### Java Backend (Port 7003)

```bash
SERVER_PORT=7003
DATABASE_URL=jdbc:postgresql://localhost:5432/yappc
REDIS_HOST=localhost
REDIS_PORT=6379
```

#### Frontend (Port 7001)

```bash
VITE_API_ORIGIN=http://localhost:7002
# Frontend ONLY talks to port 7002
```

---

## Monitoring

### Metrics to Track

**Per Backend**:

- Request count
- Response time (P50, P95, P99)
- Error rate
- Throughput

**Per Endpoint**:

- Usage frequency
- Performance
- Error patterns

### Dashboards

**Grafana Dashboard**: `dashboards/api-overview.json`

Panels:

1. Request rate by backend
2. Response time by endpoint
3. Error rate by service
4. Gateway proxy vs local handler ratio

---

## Change Log

| Date       | Change                   | Author            |
| ---------- | ------------------------ | ----------------- |
| 2026-01-29 | Initial ownership matrix | @integration-team |

---

## Review Schedule

- **Weekly**: Review P0 progress
- **Bi-weekly**: Update ownership as needed
- **Monthly**: Review and optimize routing

**Next Review**: February 5, 2026

---

## Sign-Off

- [ ] Node.js Team Lead
- [ ] Java Team Lead
- [ ] Frontend Team Lead
- [ ] DevOps Lead
- [ ] Product Owner

**Status**: Ready for Implementation ✅
