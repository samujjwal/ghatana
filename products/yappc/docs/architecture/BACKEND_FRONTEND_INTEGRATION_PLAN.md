# YAPPC Backend-Frontend Integration Plan

**Date**: January 29, 2026  
**Status**: Integration Analysis & Detailed Plan  
**Version**: 1.0

---

## Executive Summary

This document provides a comprehensive plan to integrate YAPPC's backend and frontend systems, ensuring all APIs required for end-to-end functionality are defined, exposed, and properly consumed.

### Current Architecture

```
┌──────────────────────────────────────────────────────────┐
│         Frontend (React/Vite) - Port 7001                │
│         - Tanstack Query for data fetching               │
│         - Jotai for state management                     │
└────────────────────┬─────────────────────────────────────┘
                     │ All requests → /api
                     │
┌────────────────────▼─────────────────────────────────────┐
│      API Gateway (Node.js/Fastify) - Port 7002          │
│      - Handles local routes (workspace, projects, etc)   │
│      - Proxies to Java backend for specific routes      │
└──────┬───────────────────────────────────────────┬───────┘
       │                                           │
       │ Local Routes                              │ Proxied Routes
       │                                           │
┌──────▼──────────────────────┐    ┌──────────────▼────────────────┐
│  Node.js Backend Services   │    │   Java Backend (ActiveJ)      │
│  - Port 7002 (internal)     │    │   - Port 7003                 │
│  - PostgreSQL + Prisma      │    │   - Multi-service             │
│  - GraphQL                  │    │   - High-performance          │
└─────────────────────────────┘    └───────────────────────────────┘
```

---

## Part 1: Current State Analysis

### 1.1 Backend Services Inventory

#### **Java Backend (Port 7003)** - ActiveJ HTTP Server

**Location**: `/backend/api/src/main/java/com/ghatana/yappc/api/`

**Controllers & Endpoints**:

1. **HealthController** - Health checks
   - `GET /health` - Basic health
   - `GET /health/ready` - Readiness probe
   - `GET /health/live` - Liveness probe
   - `GET /health/startup` - Startup probe
   - `GET /health/detailed` - Detailed health info

2. **MetricsController** - Prometheus metrics
   - `GET /metrics` - Prometheus metrics endpoint

3. **WorkspaceController** - Workspace management
   - `GET /api/workspaces` - List workspaces
   - `POST /api/workspaces` - Create workspace
   - `GET /api/workspaces/:id` - Get workspace
   - `PUT /api/workspaces/:id` - Update workspace
   - `DELETE /api/workspaces/:id` - Delete workspace
   - `GET /api/workspaces/:id/members` - List members
   - `POST /api/workspaces/:id/members` - Add member
   - `PUT /api/workspaces/:workspaceId/members/:userId` - Update member
   - `DELETE /api/workspaces/:workspaceId/members/:userId` - Remove member
   - `GET /api/workspaces/:id/settings` - Get settings
   - `PUT /api/workspaces/:id/settings` - Update settings

4. **RequirementsController** - Requirements CRUD
   - `POST /api/requirements` - Create requirement
   - `GET /api/requirements` - Query requirements
   - `GET /api/requirements/domains` - Get available domains
   - `GET /api/requirements/funnel` - Funnel analytics
   - `GET /api/requirements/:id` - Get requirement
   - `PUT /api/requirements/:id` - Update requirement
   - `DELETE /api/requirements/:id` - Delete requirement
   - `POST /api/requirements/:id/approve` - Approve requirement
   - `POST /api/requirements/:id/quality` - Calculate quality score

5. **RailController** - Left Rail Components
   - `GET /api/rail/components` - Get components (with filters)
   - `GET /api/rail/infrastructure` - Get infrastructure
   - `GET /api/rail/files` - Get files
   - `GET /api/rail/data-sources` - Get data sources
   - `GET /api/rail/history` - Get history
   - `GET /api/rail/favorites` - Get favorites
   - `POST /api/rail/suggestions` - Get AI suggestions

6. **AISuggestionsController** - AI suggestions
   - `POST /api/ai/suggestions/generate` - Generate suggestion
   - `GET /api/ai/suggestions` - Query suggestions
   - `GET /api/ai/suggestions/inbox` - Get inbox
   - `GET /api/ai/suggestions/:id` - Get suggestion
   - `POST /api/ai/suggestions/:id/accept` - Accept suggestion
   - `POST /api/ai/suggestions/:id/reject` - Reject suggestion

7. **ArchitectureController** - Architecture analysis
   - Various architecture analysis endpoints

8. **AuditController** - Audit logging
   - `POST /api/audit/record` - Record audit event
   - `GET /api/audit/events` - Query audit events
   - `GET /api/audit/events/:eventId` - Get specific event

9. **VersionController** - Version control
   - `POST /api/version/create` - Create version
   - `GET /api/version/history/:entityId` - Get version history
   - `GET /api/version/:entityId/versions/:versionNumber` - Get version
   - `GET /api/version/:entityId/diff` - Compare versions
   - `POST /api/version/:entityId/rollback` - Rollback version

10. **AuthorizationController** - RBAC
    - `POST /api/auth/check-permission` - Check permission
    - `GET /api/auth/user/permissions` - Get user permissions
    - `GET /api/auth/persona/:persona/permissions` - Get persona permissions
    - `GET /api/auth/persona/:persona/has-permission/:permission` - Check persona permission

11. **ApprovalController** - Approval workflows
    - Various approval workflow endpoints

12. **ConfigController** - Configuration
    - Configuration management endpoints

13. **DashboardController** - Dashboard data
    - Dashboard data endpoints

14. **WorkflowAgentController** - Workflow agents
    - Agent workflow endpoints

#### **Node.js Backend (Port 7002)** - Fastify Server

**Location**: `/app-creator/apps/api/src/`

**Routes**:

1. **Workspace Routes** - `/api/workspaces`
   - `GET /api/workspaces` - List workspaces
   - `POST /api/workspaces` - Create workspace
   - `GET /api/workspaces/:workspaceId` - Get workspace
   - `PUT /api/workspaces/:workspaceId` - Update workspace
   - `DELETE /api/workspaces/:workspaceId` - Delete workspace
   - `GET /api/workspaces/:workspaceId/analytics` - Workspace analytics

2. **Project Routes** - `/api/projects`
   - `GET /api/projects` - List projects
   - `POST /api/projects` - Create project
   - `GET /api/projects/:projectId` - Get project
   - `PUT /api/projects/:projectId` - Update project
   - `DELETE /api/projects/:projectId` - Delete project
   - `POST /api/projects/include` - Include project in workspace
   - `POST /api/projects/:projectId/remove-from-workspace` - Remove from workspace
   - `GET /api/projects/:projectId/analytics` - Project analytics
   - `GET /api/projects/:projectId/health` - Project health

3. **Canvas Routes** - `/api/canvas` & `/api/projects/:projectId/canvas`
   - `GET /api/canvas/health` - Canvas service health
   - `GET /api/projects/:projectId/canvas` - Load canvas (unified)
   - `PUT /api/projects/:projectId/canvas` - Save canvas (unified)
   - `GET /api/canvas/:projectId/:canvasId?` - Load canvas (legacy)
   - `PUT /api/canvas/:projectId/:canvasId` - Save canvas (legacy)
   - `POST /api/canvas/:projectId/:canvasId/versions` - Create version
   - `GET /api/canvas/:projectId/:canvasId/versions` - List versions
   - `POST /api/canvas/:projectId/:canvasId/restore` - Restore version
   - `GET /api/canvas/:projectId/:canvasId/diff` - Compare versions

4. **Lifecycle Routes** - `/api/lifecycle`
   - Various lifecycle management endpoints

5. **DevSecOps Routes** - `/api/devsecops`
   - Security and compliance endpoints

6. **GraphQL Endpoint** - `/graphql`
   - GraphQL schema and resolvers

7. **WebSocket** - Canvas Collaboration
   - Real-time canvas collaboration via WebSocket

### 1.2 Frontend API Consumption

**Location**: `/app-creator/apps/web/src/`

**Key Files**:

- `hooks/useWorkspaceData.ts` - Workspace & project data fetching
- `services/lifecycle/api.ts` - Lifecycle service layer
- `services/agentService.ts` - Agent service calls
- `services/canvas/api/CanvasAIService.ts` - Canvas AI operations
- Various component-level fetch calls

**API Base Configuration**:

```typescript
const API_BASE = import.meta.env.DEV
  ? `${import.meta.env.VITE_API_ORIGIN ?? "http://localhost:7002"}/api`
  : "/api";
```

---

## Part 2: API Gap Analysis

### 2.1 Missing or Incomplete APIs

#### **High Priority**

1. **Project Management** (Partial Duplication)
   - ❌ **Issue**: Project endpoints exist in both Node.js and Java backends
   - ❌ **Missing**: Clear ownership and consistency
   - ✅ **Action**: Consolidate to single source of truth

2. **Canvas Operations** (Node.js only)
   - ✅ **Exists**: Canvas CRUD in Node.js
   - ⚠️ **Limited**: Canvas AI operations not fully connected
   - ✅ **Action**: Ensure all canvas AI endpoints are exposed

3. **Lifecycle Management** (Partially exposed)
   - ⚠️ **Partial**: Lifecycle routes exist but implementation unclear
   - ❌ **Missing**: Clear lifecycle phase transitions
   - ❌ **Missing**: Artifact management endpoints
   - ❌ **Missing**: Gate validation endpoints
   - ✅ **Action**: Complete lifecycle API implementation

4. **Agent Orchestration** (Java only)
   - ✅ **Exists**: Agent endpoints in Java
   - ⚠️ **Limited**: Frontend integration incomplete
   - ✅ **Action**: Ensure frontend can access all agent capabilities

5. **Real-time Collaboration** (WebSocket)
   - ✅ **Exists**: Canvas collaboration WebSocket
   - ⚠️ **Limited**: Needs testing and documentation
   - ✅ **Action**: Document WebSocket protocol

#### **Medium Priority**

6. **DevSecOps Integration**
   - ⚠️ **Partial**: Routes exist but implementation unclear
   - ❌ **Missing**: Security scan endpoints
   - ❌ **Missing**: Compliance check endpoints
   - ✅ **Action**: Complete DevSecOps API

7. **Analytics & Metrics**
   - ✅ **Exists**: Workspace and project analytics
   - ⚠️ **Limited**: Canvas analytics missing
   - ✅ **Action**: Add canvas usage analytics

8. **User Management & RBAC**
   - ✅ **Exists**: Authorization controller in Java
   - ⚠️ **Limited**: Frontend not fully integrated
   - ✅ **Action**: Integrate RBAC checks in frontend

#### **Low Priority**

9. **Audit Logging**
   - ✅ **Exists**: Audit controller in Java
   - ⚠️ **Limited**: Frontend not logging all actions
   - ✅ **Action**: Add audit logging to frontend operations

10. **Version Control**
    - ✅ **Exists**: Version controller in Java
    - ⚠️ **Limited**: Only canvas versioning in use
    - ✅ **Action**: Extend to other entities

### 2.2 Endpoint Consistency Issues

| Endpoint               | Java Backend   | Node.js Backend | Status                        |
| ---------------------- | -------------- | --------------- | ----------------------------- |
| `GET /api/workspaces`  | ✅ Implemented | ✅ Implemented  | ⚠️ **Duplicate** - Choose one |
| `GET /api/projects`    | ❌ Not found   | ✅ Implemented  | ✅ Use Node.js                |
| `GET /api/canvas/*`    | ❌ Not found   | ✅ Implemented  | ✅ Use Node.js                |
| `GET /api/rail/*`      | ✅ Implemented | ❌ Not found    | ✅ Use Java                   |
| `GET /api/agents/*`    | ✅ Implemented | ❌ Not found    | ✅ Use Java                   |
| `GET /api/lifecycle/*` | ❌ Not found   | ⚠️ Partial      | ⚠️ **Needs completion**       |

---

## Part 3: Detailed Integration Plan

### Phase 1: API Consolidation & Documentation (Week 1-2)

#### Step 1.1: Define API Ownership

**Duration**: 2 days

**Action Items**:

1. Create API ownership matrix
2. Decide which backend owns which endpoints
3. Document decision in ADR (Architecture Decision Record)

**Recommended Ownership**:

```
Node.js Backend (Port 7002):
  ✓ /api/workspaces/*        - Workspace CRUD (PostgreSQL)
  ✓ /api/projects/*          - Project CRUD (PostgreSQL)
  ✓ /api/canvas/*            - Canvas persistence (PostgreSQL)
  ✓ /api/lifecycle/*         - Lifecycle management
  ✓ /api/devsecops/*         - Security & compliance
  ✓ /graphql                 - GraphQL API

Java Backend (Port 7003):
  ✓ /api/rail/*              - Left rail components
  ✓ /api/agents/*            - AI agents & workflows
  ✓ /api/requirements/*      - Requirements with AI
  ✓ /api/ai/suggestions/*    - AI suggestions
  ✓ /api/architecture/*      - Architecture analysis
  ✓ /api/audit/*             - Audit logging
  ✓ /api/version/*           - Version control
  ✓ /api/auth/*              - Authorization & RBAC
  ✓ /api/approvals/*         - Approval workflows
  ✓ /metrics                 - Prometheus metrics

Shared:
  ✓ /health                  - Health checks (both)
```

#### Step 1.2: Generate OpenAPI Specs

**Duration**: 3 days

**Action Items**:

1. **Java Backend**: Extract OpenAPI spec from controllers

   ```bash
   # Add Swagger/OpenAPI annotations to controllers
   # Generate spec at /openapi.json
   ```

2. **Node.js Backend**: Generate OpenAPI spec from routes

   ```bash
   # Use @fastify/swagger plugin
   # Generate spec at /api-docs/openapi.json
   ```

3. **Merge specs**: Create unified API documentation
   ```bash
   # Create tools/merge-openapi-specs.ts
   # Output: docs/api/yappc-api-spec.yaml
   ```

**Deliverables**:

- `docs/api/yappc-api-spec.yaml` - Unified OpenAPI 3.0 spec
- `docs/api/README.md` - API documentation guide
- `/api-docs` endpoint serving Swagger UI

#### Step 1.3: Update API Gateway Routing

**Duration**: 2 days

**File**: `app-creator/apps/api/src/middleware/BackendGateway.ts`

**Action Items**:

1. Update `JAVA_BACKEND_ROUTES` array with complete list
2. Add logging for proxy decisions
3. Add circuit breaker for Java backend health

```typescript
// Enhanced routing configuration
const JAVA_BACKEND_ROUTES = [
  "/api/rail",
  "/api/agents",
  "/api/requirements",
  "/api/ai/suggestions",
  "/api/architecture",
  "/api/audit",
  "/api/version",
  "/api/auth",
  "/api/approvals",
  "/api/config",
  "/api/dashboard",
  "/api/workflow-agents",
];

const NODE_BACKEND_ROUTES = [
  "/api/workspaces",
  "/api/projects",
  "/api/canvas",
  "/api/lifecycle",
  "/api/devsecops",
];
```

### Phase 2: Complete Missing APIs (Week 3-4)

#### Step 2.1: Lifecycle API Completion

**Duration**: 5 days

**Backend Location**: `app-creator/apps/api/src/routes/lifecycle.ts`

**Required Endpoints**:

```typescript
// Lifecycle Phase Management
GET    /api/lifecycle/phases                    // List all phases
GET    /api/lifecycle/phases/:phase             // Get phase details
POST   /api/lifecycle/projects/:id/transition   // Transition to next phase
GET    /api/lifecycle/projects/:id/current      // Get current phase

// Artifact Management
GET    /api/lifecycle/artifacts                 // List artifacts
POST   /api/lifecycle/artifacts                 // Create artifact
GET    /api/lifecycle/artifacts/:id             // Get artifact
PUT    /api/lifecycle/artifacts/:id             // Update artifact
DELETE /api/lifecycle/artifacts/:id             // Delete artifact
GET    /api/lifecycle/artifacts/by-phase/:phase // Artifacts by phase

// Gate Validation
POST   /api/lifecycle/gates/validate            // Validate gate criteria
GET    /api/lifecycle/gates/:stage/status       // Get gate status
GET    /api/lifecycle/gates/:stage/missing      // Missing artifacts

// Audit Trail
GET    /api/lifecycle/audit/:projectId          // Get lifecycle audit trail
```

**Implementation**:

```typescript
// app-creator/apps/api/src/routes/lifecycle.ts

import { FastifyInstance } from "fastify";
import prisma from "../db";

export default async function lifecycleRoutes(fastify: FastifyInstance) {
  // Phase Management
  fastify.get("/lifecycle/phases", async (request, reply) => {
    return {
      phases: [
        { id: "INTENT", name: "Intent", order: 1 },
        { id: "SHAPE", name: "Shape", order: 2 },
        { id: "VALIDATE", name: "Validate", order: 3 },
        { id: "GENERATE", name: "Generate", order: 4 },
        { id: "RUN", name: "Run", order: 5 },
        { id: "OBSERVE", name: "Observe", order: 6 },
        { id: "IMPROVE", name: "Improve", order: 7 },
      ],
    };
  });

  // Phase transition
  fastify.post<{ Params: { id: string }; Body: { targetPhase: string } }>(
    "/lifecycle/projects/:id/transition",
    async (request, reply) => {
      const { id } = request.params;
      const { targetPhase } = request.body;

      const project = await prisma.project.update({
        where: { id },
        data: { lifecyclePhase: targetPhase },
      });

      return { project };
    },
  );

  // Artifact management
  fastify.get("/lifecycle/artifacts", async (request, reply) => {
    // Implementation
  });

  fastify.post("/lifecycle/artifacts", async (request, reply) => {
    // Implementation
  });

  // Gate validation
  fastify.post("/lifecycle/gates/validate", async (request, reply) => {
    // Implementation
  });
}
```

#### Step 2.2: DevSecOps API Completion

**Duration**: 3 days

**Backend Location**: `app-creator/apps/api/src/routes/devsecops.ts`

**Required Endpoints**:

```typescript
// Security Scanning
POST   /api/devsecops/scan/code                // Trigger code scan
POST   /api/devsecops/scan/dependencies        // Scan dependencies
GET    /api/devsecops/scan/:scanId/status      // Get scan status
GET    /api/devsecops/scan/:scanId/results     // Get scan results

// Compliance Checks
POST   /api/devsecops/compliance/check         // Run compliance check
GET    /api/devsecops/compliance/reports       // List compliance reports
GET    /api/devsecops/compliance/frameworks    // List supported frameworks

// Security Policies
GET    /api/devsecops/policies                 // List security policies
POST   /api/devsecops/policies                 // Create policy
PUT    /api/devsecops/policies/:id             // Update policy
DELETE /api/devsecops/policies/:id             // Delete policy
```

#### Step 2.3: Canvas AI Integration

**Duration**: 3 days

**Backend Location**: `app-creator/apps/api/src/routes/canvas.ts`

**Required Endpoints**:

```typescript
// AI-powered canvas operations
POST / api / canvas / ai / suggest - components; // AI component suggestions
POST / api / canvas / ai / optimize - layout; // AI layout optimization
POST / api / canvas / ai / validate - architecture; // AI architecture validation
POST / api / canvas / ai / generate - code; // Generate code from canvas
POST / api / canvas / ai / analyze - dependencies; // Dependency analysis
```

### Phase 3: Frontend Integration (Week 5-6)

#### Step 3.1: Create Unified API Client

**Duration**: 3 days

**Location**: `app-creator/apps/web/src/lib/api/client.ts`

```typescript
/**
 * Unified API Client for YAPPC
 * Single source of truth for all API calls
 */

import axios, { AxiosInstance, AxiosError } from "axios";

class YappcApiClient {
  private client: AxiosInstance;

  constructor() {
    this.client = axios.create({
      baseURL: import.meta.env.DEV ? "http://localhost:7002/api" : "/api",
      timeout: 30000,
      headers: {
        "Content-Type": "application/json",
      },
    });

    this.setupInterceptors();
  }

  private setupInterceptors() {
    // Request interceptor
    this.client.interceptors.request.use(
      (config) => {
        // Add auth token
        const token = localStorage.getItem("auth_token");
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        }

        // Add trace ID
        config.headers["X-Trace-ID"] = generateTraceId();

        return config;
      },
      (error) => Promise.reject(error),
    );

    // Response interceptor
    this.client.interceptors.response.use(
      (response) => response,
      (error: AxiosError) => {
        // Handle errors consistently
        if (error.response?.status === 401) {
          // Redirect to login
        }
        return Promise.reject(error);
      },
    );
  }

  // Workspace APIs
  workspaces = {
    list: () => this.client.get("/workspaces"),
    get: (id: string) => this.client.get(`/workspaces/${id}`),
    create: (data: any) => this.client.post("/workspaces", data),
    update: (id: string, data: any) =>
      this.client.put(`/workspaces/${id}`, data),
    delete: (id: string) => this.client.delete(`/workspaces/${id}`),
  };

  // Project APIs
  projects = {
    list: (workspaceId: string) =>
      this.client.get(`/projects?workspaceId=${workspaceId}`),
    get: (id: string) => this.client.get(`/projects/${id}`),
    create: (data: any) => this.client.post("/projects", data),
    update: (id: string, data: any) => this.client.put(`/projects/${id}`, data),
    delete: (id: string) => this.client.delete(`/projects/${id}`),
  };

  // Canvas APIs
  canvas = {
    load: (projectId: string) =>
      this.client.get(`/projects/${projectId}/canvas`),
    save: (projectId: string, data: any) =>
      this.client.put(`/projects/${projectId}/canvas`, data),
    versions: (projectId: string) =>
      this.client.get(`/canvas/${projectId}/unified-canvas/versions`),
  };

  // Lifecycle APIs
  lifecycle = {
    phases: () => this.client.get("/lifecycle/phases"),
    currentPhase: (projectId: string) =>
      this.client.get(`/lifecycle/projects/${projectId}/current`),
    transition: (projectId: string, targetPhase: string) =>
      this.client.post(`/lifecycle/projects/${projectId}/transition`, {
        targetPhase,
      }),
    artifacts: {
      list: (params?: any) =>
        this.client.get("/lifecycle/artifacts", { params }),
      create: (data: any) => this.client.post("/lifecycle/artifacts", data),
    },
  };

  // Agent APIs
  agents = {
    list: () => this.client.get("/agents"),
    execute: (agentName: string, input: any) =>
      this.client.post(`/agents/${agentName}/execute`, input),
  };

  // Rail APIs
  rail = {
    components: (params?: any) =>
      this.client.get("/rail/components", { params }),
    infrastructure: () => this.client.get("/rail/infrastructure"),
    files: (path?: string) =>
      this.client.get("/rail/files", { params: { path } }),
  };
}

export const apiClient = new YappcApiClient();
```

#### Step 3.2: Migrate to React Query Hooks

**Duration**: 4 days

**Location**: `app-creator/apps/web/src/hooks/api/`

```typescript
// hooks/api/useWorkspaces.ts
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api/client";

export function useWorkspaces() {
  const queryClient = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ["workspaces"],
    queryFn: async () => {
      const response = await apiClient.workspaces.list();
      return response.data;
    },
  });

  const createMutation = useMutation({
    mutationFn: (data: any) => apiClient.workspaces.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["workspaces"] });
    },
  });

  return {
    workspaces: data,
    isLoading,
    error,
    createWorkspace: createMutation.mutate,
  };
}

// Similar hooks for:
// - useProjects()
// - useCanvas()
// - useLifecycle()
// - useAgents()
```

#### Step 3.3: Update Components to Use New API Client

**Duration**: 5 days

**Action Items**:

1. Replace direct `fetch` calls with `apiClient`
2. Replace inline queries with custom hooks
3. Add error boundaries
4. Add loading states
5. Add optimistic updates

### Phase 4: Testing & Validation (Week 7)

#### Step 4.1: E2E API Tests

**Duration**: 3 days

**Location**: `tests/e2e/api/`

```typescript
// tests/e2e/api/workspace-flow.spec.ts
import { test, expect } from "@playwright/test";

test.describe("Workspace API E2E", () => {
  test("should create, list, and delete workspace", async ({ request }) => {
    // Create
    const createResponse = await request.post("/api/workspaces", {
      data: { name: "Test Workspace" },
    });
    expect(createResponse.ok()).toBeTruthy();
    const workspace = await createResponse.json();

    // List
    const listResponse = await request.get("/api/workspaces");
    expect(listResponse.ok()).toBeTruthy();
    const workspaces = await listResponse.json();
    expect(workspaces).toContainEqual(
      expect.objectContaining({ id: workspace.id }),
    );

    // Delete
    const deleteResponse = await request.delete(
      `/api/workspaces/${workspace.id}`,
    );
    expect(deleteResponse.ok()).toBeTruthy();
  });
});

// Similar tests for:
// - project-flow.spec.ts
// - canvas-flow.spec.ts
// - lifecycle-flow.spec.ts
```

#### Step 4.2: Integration Tests

**Duration**: 2 days

**Location**: `tests/integration/`

```typescript
// tests/integration/api-gateway.test.ts
import { describe, it, expect } from "vitest";
import { app } from "../app-creator/apps/api/src/index";

describe("API Gateway Routing", () => {
  it("should route workspace requests to Node.js backend", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/api/workspaces",
    });
    expect(response.statusCode).toBe(200);
  });

  it("should proxy rail requests to Java backend", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/api/rail/components",
    });
    // Should proxy to Java backend
    expect(response.statusCode).toBe(200);
  });
});
```

#### Step 4.3: API Contract Tests

**Duration**: 2 days

**Use Pact or similar contract testing framework**

```typescript
// tests/contract/workspace-api.pact.ts
import { pactWith } from "jest-pact";

pactWith({ consumer: "WebUI", provider: "WorkspaceAPI" }, (provider) => {
  it("should get list of workspaces", async () => {
    await provider.addInteraction({
      state: "user has workspaces",
      uponReceiving: "a request for workspaces",
      withRequest: {
        method: "GET",
        path: "/api/workspaces",
      },
      willRespondWith: {
        status: 200,
        body: eachLike({
          id: like("ws-123"),
          name: like("My Workspace"),
        }),
      },
    });

    // Test implementation
  });
});
```

### Phase 5: Documentation & Deployment (Week 8)

#### Step 5.1: API Documentation

**Duration**: 2 days

**Deliverables**:

1. **API Reference** - `docs/api/API_REFERENCE.md`
   - All endpoints documented
   - Request/response examples
   - Error codes and handling

2. **Integration Guide** - `docs/guides/FRONTEND_API_GUIDE.md`
   - How to use the unified API client
   - React Query patterns
   - Error handling best practices
   - Authentication flows

3. **Architecture Diagrams** - `docs/diagrams/api-architecture.md`
   - Updated architecture diagrams
   - Request flow diagrams
   - Service interaction diagrams

#### Step 5.2: Deployment Configuration

**Duration**: 2 days

**Files to Update**:

1. `docker-compose.yml` - Ensure all services are properly configured
2. `k8s/` - Kubernetes manifests
3. `.env.example` - Environment variables documentation
4. `README.md` - Updated quickstart guide

#### Step 5.3: Monitoring & Observability

**Duration**: 2 days

**Action Items**:

1. **API Metrics**:
   - Request count per endpoint
   - Response times
   - Error rates

2. **Distributed Tracing**:
   - OpenTelemetry integration
   - Trace requests across services

3. **Dashboards**:
   - Grafana dashboards for API health
   - Alert rules for SLOs

---

## Part 4: API Endpoint Registry

### Complete API Endpoint List

#### Authentication & Authorization

```
POST   /api/auth/login                    - User login
POST   /api/auth/logout                   - User logout
POST   /api/auth/refresh                  - Refresh token
POST   /api/auth/check-permission         - Check permission
GET    /api/auth/user/permissions         - Get user permissions
GET    /api/auth/persona/:persona/permissions - Get persona permissions
```

#### Workspaces

```
GET    /api/workspaces                    - List workspaces
POST   /api/workspaces                    - Create workspace
GET    /api/workspaces/:id                - Get workspace
PUT    /api/workspaces/:id                - Update workspace
DELETE /api/workspaces/:id                - Delete workspace
GET    /api/workspaces/:id/members        - List members
POST   /api/workspaces/:id/members        - Add member
DELETE /api/workspaces/:id/members/:userId - Remove member
GET    /api/workspaces/:id/analytics      - Workspace analytics
```

#### Projects

```
GET    /api/projects                      - List projects
POST   /api/projects                      - Create project
GET    /api/projects/:id                  - Get project
PUT    /api/projects/:id                  - Update project
DELETE /api/projects/:id                  - Delete project
POST   /api/projects/include              - Include in workspace
POST   /api/projects/:id/remove-from-workspace - Remove from workspace
GET    /api/projects/:id/analytics        - Project analytics
GET    /api/projects/:id/health           - Project health
```

#### Canvas

```
GET    /api/projects/:projectId/canvas    - Load canvas
PUT    /api/projects/:projectId/canvas    - Save canvas
POST   /api/canvas/:projectId/:canvasId/versions - Create version
GET    /api/canvas/:projectId/:canvasId/versions - List versions
POST   /api/canvas/:projectId/:canvasId/restore  - Restore version
POST   /api/canvas/ai/suggest-components  - AI suggestions
POST   /api/canvas/ai/validate            - AI validation
POST   /api/canvas/ai/generate-code       - Generate code
```

#### Lifecycle

```
GET    /api/lifecycle/phases              - List phases
GET    /api/lifecycle/projects/:id/current - Current phase
POST   /api/lifecycle/projects/:id/transition - Transition phase
GET    /api/lifecycle/artifacts           - List artifacts
POST   /api/lifecycle/artifacts           - Create artifact
GET    /api/lifecycle/artifacts/:id       - Get artifact
PUT    /api/lifecycle/artifacts/:id       - Update artifact
DELETE /api/lifecycle/artifacts/:id       - Delete artifact
POST   /api/lifecycle/gates/validate      - Validate gate
GET    /api/lifecycle/gates/:stage/status - Gate status
```

#### Agents

```
GET    /api/agents                        - List agents
GET    /api/agents/:name                  - Get agent
POST   /api/agents/:name/execute          - Execute agent
GET    /api/agents/health                 - Agents health
GET    /api/agents/capabilities           - Agent capabilities
```

#### Left Rail

```
GET    /api/rail/components               - List components
GET    /api/rail/infrastructure           - Infrastructure view
GET    /api/rail/files                    - File explorer
GET    /api/rail/data-sources             - Data sources
GET    /api/rail/history                  - Recent items
GET    /api/rail/favorites                - Favorite items
POST   /api/rail/suggestions              - AI suggestions
```

#### Requirements

```
GET    /api/requirements                  - List requirements
POST   /api/requirements                  - Create requirement
GET    /api/requirements/:id              - Get requirement
PUT    /api/requirements/:id              - Update requirement
DELETE /api/requirements/:id              - Delete requirement
POST   /api/requirements/:id/approve      - Approve requirement
GET    /api/requirements/funnel           - Funnel analytics
GET    /api/requirements/domains          - Available domains
```

#### AI Suggestions

```
POST   /api/ai/suggestions/generate       - Generate suggestion
GET    /api/ai/suggestions                - List suggestions
GET    /api/ai/suggestions/inbox          - Suggestion inbox
GET    /api/ai/suggestions/:id            - Get suggestion
POST   /api/ai/suggestions/:id/accept     - Accept suggestion
POST   /api/ai/suggestions/:id/reject     - Reject suggestion
```

#### DevSecOps

```
POST   /api/devsecops/scan/code           - Code scan
POST   /api/devsecops/scan/dependencies   - Dependency scan
GET    /api/devsecops/scan/:scanId        - Scan results
POST   /api/devsecops/compliance/check    - Compliance check
GET    /api/devsecops/compliance/reports  - Compliance reports
GET    /api/devsecops/policies            - Security policies
```

#### Audit & Version Control

```
POST   /api/audit/record                  - Record event
GET    /api/audit/events                  - Query events
GET    /api/audit/events/:id              - Get event
POST   /api/version/create                - Create version
GET    /api/version/history/:entityId     - Version history
POST   /api/version/:entityId/rollback    - Rollback version
```

#### Health & Metrics

```
GET    /health                            - Basic health
GET    /health/ready                      - Readiness probe
GET    /health/live                       - Liveness probe
GET    /health/startup                    - Startup probe
GET    /metrics                           - Prometheus metrics
```

---

## Part 5: Configuration & Environment

### Environment Variables

#### Node.js Backend (Port 7002)

```bash
# app-creator/.env
PORT=7002
NODE_ENV=development
DATABASE_URL=postgresql://user:pass@localhost:5432/yappc
REDIS_URL=redis://localhost:6379
JAVA_BACKEND_URL=http://localhost:7003
VITE_API_ORIGIN=http://localhost:7002
```

#### Java Backend (Port 7003)

```bash
# backend/api/.env
SERVER_PORT=7003
DATABASE_URL=jdbc:postgresql://localhost:5432/yappc
REDIS_HOST=localhost
REDIS_PORT=6379
```

#### Frontend (Port 7001)

```bash
# app-creator/apps/web/.env
VITE_API_ORIGIN=http://localhost:7002
VITE_WS_URL=ws://localhost:7002
```

### Docker Compose Configuration

```yaml
# docker-compose.yml (relevant services)
services:
  # Node.js API Gateway
  nodejs-api:
    build: ./app-creator/apps/api
    ports:
      - "7002:7002"
    environment:
      PORT: 7002
      JAVA_BACKEND_URL: http://java-api:7003
      DATABASE_URL: postgresql://postgres:postgres@postgres:5432/yappc
    depends_on:
      - postgres
      - redis
      - java-api

  # Java Backend
  java-api:
    build: ./backend/api
    ports:
      - "7003:7003"
    environment:
      SERVER_PORT: 7003
      DATABASE_URL: jdbc:postgresql://postgres:5432/yappc
    depends_on:
      - postgres
      - redis

  # Frontend
  frontend:
    build: ./app-creator/apps/web
    ports:
      - "7001:7001"
    environment:
      VITE_API_ORIGIN: http://nodejs-api:7002
    depends_on:
      - nodejs-api
```

---

## Part 6: Success Criteria

### Definition of Done

#### API Completeness

- ✅ All required endpoints are implemented
- ✅ OpenAPI specification is complete and accurate
- ✅ All endpoints have proper error handling
- ✅ All endpoints return consistent response formats

#### Integration

- ✅ API gateway correctly routes all requests
- ✅ Frontend uses unified API client for all calls
- ✅ All React Query hooks are implemented
- ✅ WebSocket collaboration is working

#### Testing

- ✅ All endpoints have unit tests (>80% coverage)
- ✅ E2E tests cover main user flows
- ✅ Contract tests validate API contracts
- ✅ Load tests validate performance

#### Documentation

- ✅ API reference is complete
- ✅ Integration guide is available
- ✅ Architecture diagrams are up to date
- ✅ Deployment guide is complete

#### Observability

- ✅ All APIs emit metrics
- ✅ Distributed tracing is enabled
- ✅ Dashboards are created
- ✅ Alerts are configured

### Performance Targets

- API Response Time (P95): < 200ms
- WebSocket Latency: < 50ms
- API Error Rate: < 0.1%
- API Availability: > 99.9%

---

## Part 7: Risk Mitigation

### Known Risks

1. **Workspace Endpoint Duplication**
   - **Risk**: Confusion and inconsistency
   - **Mitigation**: Choose Node.js as single source, deprecate Java endpoint
   - **Timeline**: Week 1

2. **Backend Service Downtime**
   - **Risk**: Frontend broken if backend is down
   - **Mitigation**: Implement circuit breakers and fallbacks
   - **Timeline**: Week 3

3. **Breaking Changes During Migration**
   - **Risk**: Frontend breaks during API changes
   - **Mitigation**: Use feature flags and gradual rollout
   - **Timeline**: Ongoing

4. **Performance Degradation**
   - **Risk**: API gateway adds latency
   - **Mitigation**: Monitor and optimize, consider caching
   - **Timeline**: Week 7

### Rollback Plan

If critical issues arise:

1. Revert to previous version via Git
2. Roll back database migrations
3. Notify users of temporary degradation
4. Fix issue in separate branch
5. Deploy fix after validation

---

## Part 8: Next Steps

### Immediate Actions (This Week)

1. **Review and approve this plan**
2. **Set up project tracking** (GitHub Projects, Jira, etc.)
3. **Assign ownership** for each phase
4. **Create branches** for development work

### Week 1 Tasks

1. **API Ownership Matrix** (2 days)
2. **OpenAPI Spec Generation** (3 days)
3. **API Gateway Routing Update** (2 days)

### Communication Plan

- **Daily Standups**: Progress updates
- **Weekly Reviews**: Phase completion reviews
- **Documentation**: Keep this plan updated
- **Demos**: Show working features each week

---

## Appendix

### A. Useful Commands

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f nodejs-api
docker-compose logs -f java-api

# Run tests
pnpm test:e2e
pnpm test:integration

# Generate OpenAPI spec
pnpm run generate-openapi

# Build production
pnpm run build
docker-compose -f docker-compose.prod.yml up -d
```

### B. References

- [API Gateway Architecture](./API_GATEWAY_ARCHITECTURE.md)
- [Docker Setup](./README_DOCKER.md)
- [Development Guide](./docs/DEVELOPER_GUIDE.md)
- [Architecture Overview](./docs/ARCHITECTURE.md)

### C. Contact & Support

- **Backend Team**: @backend-team
- **Frontend Team**: @frontend-team
- **DevOps Team**: @devops-team
- **Documentation**: docs/

---

**Document Version**: 1.0  
**Last Updated**: January 29, 2026  
**Next Review**: February 5, 2026
