# API Gateway Architecture

**Date**: January 27, 2026  
**Status**: ✅ Complete

## Overview

The frontend now **always uses a single API endpoint** and is completely unaware of multiple backend services. All requests go through a unified gateway.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Frontend (port 7001)                 │
│             (React, Vite Dev Server)                    │
└──────────────────────────┬──────────────────────────────┘
                           │
                All requests go to /api
                    localhost:7002
                           │
┌──────────────────────────▼──────────────────────────────┐
│         API Gateway (Node.js, port 7002)                │
│        (Fastify + Backend Gateway Middleware)          │
├──────────────────────────────────────────────────────────┤
│  Routes to Local Services:        Routes to Java:       │
│  ✓ /api/workspaces               ✓ /api/rail           │
│  ✓ /api/projects                 ✓ /api/agents         │
│  ✓ /api/canvas                   ✓ /api/metrics        │
│  ✓ /api/lifecycle                                       │
│  ✓ /api/devsecops                                       │
└───────────────────────────────────────────────────────────┘
        │                                    │
        │ Handles locally                    │ Proxies to
        │                                    │
┌───────▼──────────────────┐    ┌───────────▼─────────────┐
│  Node.js Backend         │    │   Java Backend          │
│  (port 7002 internal)    │    │   (port 7003)           │
│  - PostgreSQL            │    │   - ActiveJ HTTP Server │
│  - Prisma ORM            │    │   - High-perf services  │
│  - GraphQL Resolvers     │    │   - AI/Agent execution  │
└──────────────────────────┘    └─────────────────────────┘
```

## Key Changes

### 1. **Backend Gateway Middleware** (NEW)

**File**: `products/yappc/app-creator/apps/api/src/middleware/BackendGateway.ts`

- Intercepts requests to Java backend routes (`/api/rail`, `/api/agents`, etc.)
- Proxies them to `http://localhost:7003` (Java service)
- All other routes continue to local services
- Completely transparent to the frontend

### 2. **Node.js API Gateway Configuration**

**File**: `products/yappc/app-creator/apps/api/src/index.ts`

- Registers `BackendGateway` middleware BEFORE route handlers
- Ensures clean separation of concerns
- Node.js acts as a pure gateway for Java backend routes

### 3. **Vite Dev Server Proxy**

**File**: `products/yappc/app-creator/apps/web/vite.config.ts`

Simplified from complex multi-target routing to a single gateway:

```typescript
proxy: {
  '/api': {
    target: 'http://localhost:7002',  // Single gateway
    changeOrigin: true,
    secure: false,
  },
}
```

### 4. **Frontend API Client**

**File**: `products/yappc/app-creator/apps/web/src/hooks/useWorkspaceData.ts`

Now uses a single API origin:

```typescript
const API_BASE = import.meta.env.DEV
  ? `${import.meta.env.VITE_API_ORIGIN ?? "http://localhost:7002"}/api`
  : "/api";
```

## Benefits

✅ **Single Gateway Pattern**: Frontend only knows about one API endpoint  
✅ **Clean Separation**: Backend complexity hidden from frontend  
✅ **Scalable Routing**: Easy to add/remove backend services  
✅ **No Frontend Changes Required**: When adding new Java services, just update the gateway  
✅ **Production Ready**: Works seamlessly behind NGINX/API Gateway in production

## Port Allocation

| Port     | Service         | Purpose                              |
| -------- | --------------- | ------------------------------------ |
| **7002** | Node.js API     | Gateway + Local business logic       |
| **7003** | Java Backend    | High-perf domain logic, agents, rail |
| **7001** | Frontend (Vite) | UI Development Server                |

## Request Flow Example

**Frontend** → `/api/workspaces`

- Vite proxies to `http://localhost:7002/api/workspaces`
- Node.js gateway recognizes local route, handles it directly
- Returns workspace data to frontend

**Frontend** → `/api/rail/components?mode=architect`

- Vite proxies to `http://localhost:7002/api/rail/components?mode=architect`
- Node.js gateway recognizes Java route, proxies to `http://localhost:7003/api/rail/components?mode=architect`
- Java backend processes, returns rail data through gateway to frontend

## Environment Variables

- `VITE_API_ORIGIN`: (Frontend) Override API origin (optional, defaults to `localhost:7002`)
- `JAVA_BACKEND_URL`: (Node.js API) Location of Java backend (optional, defaults to `http://localhost:7003`)
- `PORT`: (Node.js API) Gateway port (optional, defaults to `7002`)

## Testing

All three services running:

```bash
# Terminal 1: Frontend (Vite dev server)
cd products/yappc/app-creator/apps/web
npm run dev  # Runs on 7001

# Terminal 2: Node.js Gateway
cd products/yappc/app-creator/apps/api
npm run dev  # Runs on 7002

# Terminal 3: Java Backend
cd products/yappc/backend/api
./gradlew bootRun  # Runs on 7003
```

Frontend accesses everything via `http://localhost:7002/api/*` ✅
