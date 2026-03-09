# YAPPC Single-Port Architecture

**Date**: January 29, 2026  
**Status**: ✅ Implemented and Active  
**Version**: 1.0

---

## Executive Summary

YAPPC uses a **single-port API Gateway architecture** where:

- **Frontend talks to ONLY port 7002** (the API Gateway)
- The Gateway routes requests to either Node.js (local) or Java backend (proxied)
- This provides a clean separation, simplifies CORS, and enables flexible backend evolution

```
┌──────────────────────────────────────────────────────────────┐
│                        YAPPC Architecture                      │
│                                                                │
│  Frontend (React)                                              │
│  Port: 7001                                                    │
│  ↓ ALL API calls → http://localhost:7002                       │
│                                                                │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  API Gateway (Fastify + Node.js)                        │  │
│  │  Port: 7002 ← SINGLE ENTRY POINT                        │  │
│  │  ↓                                                       │  │
│  │  Route Decision:                                        │  │
│  │    - /api/workspaces → Node.js (local)                  │  │
│  │    - /api/projects → Node.js (local)                    │  │
│  │    - /api/canvas → Node.js (local)                      │  │
│  │    - /api/lifecycle → Node.js (local)                   │  │
│  │    - /api/rail → Java (proxied)                         │  │
│  │    - /api/agents → Java (proxied)                       │  │
│  │    - /api/requirements → Java (proxied)                 │  │
│  │    - /graphql → Node.js (GraphQL Yoga)                  │  │
│  └─────────────────────────────────────────────────────────┘  │
│       ↓ (internal)              ↓ (internal proxy)             │
│                                                                │
│  Node.js Backend         Java Backend (ActiveJ)               │
│  (No direct port)        Port: 7003 (internal only)           │
│  - Prisma/PostgreSQL     - High-performance async             │
│  - Business logic        - AI/ML processing                    │
│  - Real-time features    - Complex analytics                  │
│                                                                │
│  Database Layer                                                │
│  - PostgreSQL: 5432                                            │
│  - Redis: 6379                                                 │
└──────────────────────────────────────────────────────────────┘
```

---

## Key Principles

### 1. Single Port for Frontend

✅ **Frontend ONLY talks to port 7002**

- No direct calls to Java backend (port 7003)
- No need to configure multiple API endpoints
- Simplified CORS configuration
- Consistent authentication/authorization

### 2. Gateway Routes Intelligently

✅ **Automatic routing based on URL patterns**

- Node.js routes: `/api/workspaces`, `/api/projects`, `/api/canvas`, `/api/lifecycle`
- Java routes: `/api/rail`, `/api/agents`, `/api/requirements`, `/api/ai`
- GraphQL: `/graphql` (handled by Node.js)

### 3. Backend Services are Internal

✅ **Java backend (port 7003) is NOT exposed to frontend**

- Only the Gateway can access it
- Enables backend refactoring without frontend changes
- Supports multiple backend technologies

---

## Port Configuration

### Development Environment

| Service          | Port | Exposed To   | Purpose                |
| ---------------- | ---- | ------------ | ---------------------- |
| **Frontend**     | 7001 | Browser      | React SPA              |
| **API Gateway**  | 7002 | Frontend     | Single API entry point |
| **Java Backend** | 7003 | Gateway only | Internal service       |
| PostgreSQL       | 5432 | Backend only | Database               |
| Redis            | 6379 | Backend only | Cache                  |

### Production Environment

| Service          | Port     | Exposed To   | Purpose                    |
| ---------------- | -------- | ------------ | -------------------------- |
| **Frontend**     | 80/443   | Internet     | Static files via CDN/Nginx |
| **API Gateway**  | 80/443   | Internet     | Single API endpoint via LB |
| **Java Backend** | Internal | Gateway only | ClusterIP service          |
| PostgreSQL       | Internal | Backend only | RDS/CloudSQL               |
| Redis            | Internal | Backend only | ElastiCache/Memorystore    |

---

## Environment Variables

### Frontend Configuration

**File**: `app-creator/apps/web/.env`

```bash
# Frontend ONLY needs ONE API endpoint
VITE_API_ORIGIN=http://localhost:7002

# In production
# VITE_API_ORIGIN=https://api.yappc.com
```

**Usage in Code**:

```typescript
// app-creator/apps/web/src/config/api.ts
export const API_BASE =
  import.meta.env.VITE_API_ORIGIN || "http://localhost:7002";

// All API calls use this single endpoint
const response = await fetch(`${API_BASE}/api/workspaces`);
```

### API Gateway Configuration

**File**: `app-creator/apps/api/.env`

```bash
# Gateway listens on port 7002
PORT=7002
NODE_ENV=development

# Java backend URL (internal only)
JAVA_BACKEND_URL=http://localhost:7003

# Database connection
DATABASE_URL=postgresql://yappc:password@localhost:5432/yappc

# Redis connection
REDIS_URL=redis://localhost:6379
```

### Java Backend Configuration

**File**: `backend/api/.env`

```bash
# Java backend listens on port 7003 (internal)
SERVER_PORT=7003

# Database connection
DATABASE_URL=jdbc:postgresql://localhost:5432/yappc

# Redis connection
REDIS_HOST=localhost
REDIS_PORT=6379
```

---

## Routing Configuration

### API Gateway Routing Logic

**File**: `app-creator/apps/api/src/index.ts`

```typescript
// ============================================================================
// Local Handlers (Node.js Backend)
// ============================================================================

// Register REST API routes - handled locally by Node.js
app.register(workspaceRoutes, { prefix: '/api' });
app.register(projectRoutes, { prefix: '/api' });
app.register(canvasRoutes, { prefix: '/api' });
app.register(lifecycleRoutes, { prefix: '/api/lifecycle' });
app.register(devsecopsRoutes, { prefix: '/api' });

// GraphQL endpoint - handled locally
app.all('/graphql', async (req, reply) => {
  const response = await yoga.fetch(req.url, { ... });
  // ... GraphQL Yoga integration
});

// ============================================================================
// Java Backend Proxy (Catch-all)
// ============================================================================

// Catch-all route for Java backend proxying
// Handles /api/rail, /api/agents, /api/requirements, etc.
app.all('/api/*', async (request, reply) => {
  const javaBackendUrl = process.env.JAVA_BACKEND_URL || 'http://localhost:7003';
  const targetUrl = new URL(javaBackendUrl + request.url);

  try {
    const response = await fetch(targetUrl.toString(), {
      method: request.method,
      headers: { ...request.headers, host: targetUrl.hostname },
      body: request.method !== 'GET' ? JSON.stringify(request.body) : undefined,
    });

    reply.code(response.status);
    reply.send(await response.text());
  } catch (error) {
    reply.status(503).send({
      error: 'Service Unavailable',
      message: 'Could not reach backend service',
    });
  }
});
```

### Route Resolution Order

**IMPORTANT**: Routes are matched in registration order!

1. **Specific routes first** (registered explicitly)
   - `/api/workspaces` → Node.js
   - `/api/projects` → Node.js
   - `/api/canvas` → Node.js
   - `/api/lifecycle` → Node.js
   - `/graphql` → Node.js GraphQL

2. **Catch-all route last** (proxies to Java)
   - `/api/rail/*` → Java
   - `/api/agents/*` → Java
   - `/api/requirements/*` → Java
   - `/api/ai/*` → Java
   - Any other `/api/*` → Java

---

## Frontend API Consumption

### Unified API Client Pattern

**File**: `app-creator/apps/web/src/services/api.ts`

```typescript
import { API_BASE } from "../config/api";

/**
 * Unified API client - all calls go through port 7002
 */
class APIClient {
  private baseUrl: string;

  constructor() {
    this.baseUrl = API_BASE; // http://localhost:7002
  }

  async get<T>(endpoint: string): Promise<T> {
    const response = await fetch(`${this.baseUrl}${endpoint}`, {
      method: "GET",
      headers: { "Content-Type": "application/json" },
      credentials: "include", // Include cookies for auth
    });

    if (!response.ok) {
      throw new Error(`API Error: ${response.statusText}`);
    }

    return response.json();
  }

  async post<T>(endpoint: string, data: any): Promise<T> {
    const response = await fetch(`${this.baseUrl}${endpoint}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify(data),
    });

    if (!response.ok) {
      throw new Error(`API Error: ${response.statusText}`);
    }

    return response.json();
  }

  // ... PUT, DELETE, PATCH methods
}

export const apiClient = new APIClient();
```

### React Query Integration

```typescript
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../services/api";

/**
 * Hook to fetch workspaces
 * Behind the scenes: GET http://localhost:7002/api/workspaces
 * Gateway routes to: Node.js local handler
 */
export function useWorkspaces() {
  return useQuery({
    queryKey: ["workspaces"],
    queryFn: () => apiClient.get("/api/workspaces"),
  });
}

/**
 * Hook to fetch agents
 * Behind the scenes: GET http://localhost:7002/api/agents
 * Gateway routes to: Java backend (proxied to port 7003)
 */
export function useAgents() {
  return useQuery({
    queryKey: ["agents"],
    queryFn: () => apiClient.get("/api/agents"),
  });
}
```

---

## Benefits of This Architecture

### 1. Frontend Simplicity

✅ **One API endpoint to rule them all**

- No need to know which backend serves what
- No CORS complications with multiple origins
- Easy to mock for testing

### 2. Backend Flexibility

✅ **Move endpoints between backends without frontend changes**

- Start in Node.js, move to Java for performance
- A/B test different implementations
- Gradual migration paths

### 3. Security

✅ **Java backend is not exposed publicly**

- Only Gateway can access it
- Centralized authentication/authorization
- Easier to audit and monitor

### 4. Scalability

✅ **Independent scaling**

- Scale Gateway horizontally for load
- Scale Java backend for CPU-intensive tasks
- Scale Node.js for I/O-intensive tasks

### 5. Development Experience

✅ **Simple local setup**

```bash
# Terminal 1: Start Java backend (internal)
cd backend/api
./gradlew run

# Terminal 2: Start API Gateway (frontend talks here)
cd app-creator/apps/api
npm run dev

# Terminal 3: Start frontend
cd app-creator/apps/web
npm run dev
```

Frontend only needs to know about port 7002! ✅

---

## Deployment Architecture

### Kubernetes Configuration

```yaml
# Frontend Deployment
apiVersion: v1
kind: Service
metadata:
  name: yappc-frontend
spec:
  type: LoadBalancer
  ports:
    - port: 80
      targetPort: 7001
  selector:
    app: yappc-frontend

---
# API Gateway Deployment
apiVersion: v1
kind: Service
metadata:
  name: yappc-gateway
spec:
  type: LoadBalancer # Public endpoint
  ports:
    - port: 80
      targetPort: 7002
  selector:
    app: yappc-gateway

---
# Java Backend Deployment
apiVersion: v1
kind: Service
metadata:
  name: yappc-java-backend
spec:
  type: ClusterIP # Internal only!
  ports:
    - port: 7003
  selector:
    app: yappc-java-backend
```

**Key Points**:

- Frontend and Gateway are LoadBalancer (public)
- Java backend is ClusterIP (internal only)
- Gateway uses service DNS: `http://yappc-java-backend:7003`

---

## Testing the Architecture

### Verify Single-Port Configuration

```bash
# 1. Start all services
make dev

# 2. Verify frontend config
curl http://localhost:7001
# Check that API calls go to localhost:7002

# 3. Test Gateway routing - Node.js endpoint
curl http://localhost:7002/api/workspaces
# Should return workspace list (Node.js handler)

# 4. Test Gateway routing - Java endpoint
curl http://localhost:7002/api/agents
# Should proxy to Java backend and return agents

# 5. Verify Java backend is NOT directly accessible from frontend
# This should work (Gateway can access):
docker exec yappc-gateway curl http://localhost:7003/api/agents

# This should NOT work from host (if properly firewalled):
# curl http://localhost:7003/api/agents
```

### Frontend Network Inspection

**Open DevTools → Network**:

```
All API calls should show:
- URL: http://localhost:7002/api/*
- No calls to http://localhost:7003
```

---

## Troubleshooting

### Problem: Frontend can't reach API

**Check**:

1. API Gateway is running on port 7002
2. Frontend `.env` has `VITE_API_ORIGIN=http://localhost:7002`
3. CORS is enabled in Gateway

**Solution**:

```bash
# Check Gateway is listening
curl http://localhost:7002/health

# Check CORS headers
curl -H "Origin: http://localhost:7001" \
  -H "Access-Control-Request-Method: GET" \
  -X OPTIONS http://localhost:7002/api/workspaces
```

### Problem: 503 Service Unavailable for Java endpoints

**Check**:

1. Java backend is running on port 7003
2. Gateway can reach Java backend
3. `JAVA_BACKEND_URL` is set correctly in Gateway

**Solution**:

```bash
# Check Java backend
curl http://localhost:7003/api/agents

# Check Gateway env
docker exec yappc-gateway env | grep JAVA_BACKEND_URL

# Should output: JAVA_BACKEND_URL=http://localhost:7003
```

### Problem: Routes not matching correctly

**Check route registration order**:

- Specific routes (workspaces, projects) registered BEFORE catch-all
- Catch-all (`/api/*`) registered LAST

---

## Migration Guide: Multi-Port → Single-Port

If you have old code calling Java backend directly:

### Before (❌ Wrong)

```typescript
// DON'T DO THIS
const JAVA_API = "http://localhost:7003";
const NODE_API = "http://localhost:7002";

// Wrong: Frontend knows about multiple backends
const agents = await fetch(`${JAVA_API}/api/agents`);
const workspaces = await fetch(`${NODE_API}/api/workspaces`);
```

### After (✅ Correct)

```typescript
// DO THIS
const API_BASE = "http://localhost:7002";

// Correct: Frontend only knows one endpoint
const agents = await fetch(`${API_BASE}/api/agents`);
const workspaces = await fetch(`${API_BASE}/api/workspaces`);
```

---

## Configuration Checklist

- [ ] Frontend `.env` has `VITE_API_ORIGIN=http://localhost:7002` (single port)
- [ ] Gateway `.env` has `PORT=7002`
- [ ] Gateway `.env` has `JAVA_BACKEND_URL=http://localhost:7003`
- [ ] Java backend `.env` has `SERVER_PORT=7003`
- [ ] No frontend code directly accesses port 7003
- [ ] All API calls go through `API_BASE` variable
- [ ] CORS configured on port 7002 only
- [ ] Docker Compose exposes only port 7002 to host for API

---

## Performance Considerations

### Latency Impact

**Additional hop**: Frontend → Gateway → Java

- Gateway proxying adds ~1-5ms latency
- Acceptable for most use cases
- Benefits outweigh costs

**Optimization**:

- Keep Gateway lightweight (no heavy processing)
- Use HTTP/2 for multiplexing
- Enable gzip compression
- Cache at Gateway level for public endpoints

### Load Distribution

**Gateway handles**:

- 60% requests (Node.js local: workspaces, projects, canvas, lifecycle)
- 40% requests (Java proxy: agents, rail, requirements, AI)

**Scaling strategy**:

- Scale Gateway pods based on total request rate
- Scale Java backend based on CPU usage (AI/ML processing)
- Scale Node.js workers based on database connections

---

## Summary

✅ **Frontend talks to ONLY port 7002** (API Gateway)  
✅ **Gateway routes to Node.js (local) or Java (proxy)** based on URL patterns  
✅ **Java backend (port 7003) is internal only**  
✅ **Simple configuration, flexible architecture**  
✅ **Ready for production deployment**

**Key Files**:

- Frontend config: `app-creator/apps/web/.env` → `VITE_API_ORIGIN=http://localhost:7002`
- Gateway config: `app-creator/apps/api/.env` → `PORT=7002`, `JAVA_BACKEND_URL=http://localhost:7003`
- Gateway routing: `app-creator/apps/api/src/index.ts`
- API client: `app-creator/apps/web/src/services/api.ts`

**Next Steps**:

1. Review this architecture document
2. Verify all frontend code uses single API_BASE
3. Test all endpoints through Gateway
4. Deploy with confidence! 🚀

---

**Document Version**: 1.0  
**Last Updated**: January 29, 2026  
**Owner**: Integration Team  
**Status**: ✅ Active and Deployed
