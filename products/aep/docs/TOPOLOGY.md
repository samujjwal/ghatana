# AEP Network Topology

## Overview

This document defines the canonical network topology for AEP (Agentic Event Processor) development and deployment environments. It establishes clear port allocations, service boundaries, and communication patterns to prevent collisions and confusion.

**Last Updated**: 2026-03-20  
**Version**: 1.0  
**Status**: Active Implementation

---

## Port Allocation

| Service | Port | Protocol | Purpose | Environment |
|---------|------|----------|---------|-------------|
| UI Dev Server | 3001 | HTTP | React development server with HMR | Development |
| API Gateway (BFF) | 3002 | HTTP | TypeScript BFF layer, auth/session handling | Development/Optional |
| Java Server | 8090 | HTTP | Canonical backend API, event processing | All |
| Java Server SSE | 8090 | SSE | Server-Sent Events for live updates | All |
| Java WebSocket | 8090 | WS | WebSocket endpoints (if enabled) | All |

### Port History

- **Port 3000**: Reserved (collides with external services). **DO NOT USE**.
- **Port 3001**: Now dedicated to UI Dev Server (changed from 3000 to avoid collision).
- **Port 3002**: Dedicated to API Gateway/BFF layer.
- **Port 8090**: Unchanged - canonical Java backend.

---

## Service Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                             │
├─────────────────────────────────────────────────────────────────┤
│  Browser                                                         │
│    ├── React UI (dev:3000 / prod:80/443)                        │
│    └── EventSource/SSE connections                                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         EDGE LAYER                               │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌─────────────────┐                     │
│  │  UI Dev Server  │    │  API Gateway    │                     │
│  │     :3001       │    │     :3002       │                     │
│  │                 │    │  (Optional BFF) │                     │
│  │  Vite + React   │    │  TypeScript     │                     │
│  │  Proxy /api     │    │  Auth/Session   │                     │
│  │  Proxy /admin   │    │  Proxy to :8090 │                     │
│  │  Proxy /events  │    │                 │                     │
│  └─────────────────┘    └─────────────────┘                     │
│           │                      │                              │
│           └──────────┬───────────┘                              │
│                      │                                          │
└──────────────────────┼──────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│                      BACKEND LAYER                               │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐     │
│  │              Java Server (AepHttpServer)               │     │
│  │                         :8090                           │     │
│  │                                                          │     │
│  │  Endpoints:                                              │     │
│  │    ├── /api/v1/*     - REST API (auth required)         │     │
│  │    ├── /events/stream - SSE endpoint                    │     │
│  │    ├── /health       - Health check (public)           │     │
│  │    ├── /ready        - Readiness probe (public)        │     │
│  │    └── /metrics      - Prometheus metrics (public)     │     │
│  │                                                          │     │
│  │  Auth: JWT Bearer tokens via AepAuthFilter              │     │
│  │  Security: OWASP headers via AepSecurityFilter        │     │
│  └─────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────┘
```

---

## Canonical Edge Decision

**Decision**: The Java Server (port 8090) is the **canonical backend API**.

**Rationale**:
1. Single source of truth for AEP functionality
2. Event processing happens in Java layer
3. SSE/WebSocket support built-in
4. JWT auth enforcement via AepAuthFilter
5. Eliminates double-hop for most requests

**API Gateway (port 3002)**: Optional BFF layer for:
- Session management (if needed beyond JWT)
- Request/response transformation
- Protocol bridging (if required)
- Rate limiting at edge (if needed)

**Recommendation**: Start with direct server access. Add API Gateway only if specific BFF needs emerge.

---

## Development Workflow

### Starting Services

```bash
# 1. Start Java Backend
./gradlew :products:aep:server:run
# or
./gradlew :products:aep:server:runDev

# 2. Start UI Dev Server
pnpm --dir products/aep/ui dev
# Server starts on http://localhost:3001
# Proxies /api, /admin, /events to :8090

# 3. (Optional) Start API Gateway
pnpm --dir products/aep/api dev
# Server starts on http://localhost:3002
```

### Environment Variables

**UI Dev Server** (`.env.development`):
```env
VITE_API_URL=http://localhost:8090
VITE_WS_URL=ws://localhost:8090
PORT=3001
```

**Java Server**:
```env
AEP_JWT_SECRET=your-jwt-secret-here  # Required for auth in prod
AEP_CORS_ORIGINS=http://localhost:3000
SERVER_PORT=8090
```

**API Gateway** (`.env`):
```env
PORT=3002
AEP_BACKEND_URL=http://localhost:8090
JWT_SECRET=your-jwt-secret-here
```

---

## API Endpoints

### Public Endpoints (No Auth Required)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/health` | GET | Liveness probe |
| `/ready` | GET | Readiness probe |
| `/live` | GET | Health check |
| `/info` | GET | Service info |
| `/metrics` | GET | Prometheus metrics |
| `/events/stream` | SSE | Event stream (validates JWT on connection) |

### Protected Endpoints (JWT Required)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/pipelines` | GET/POST | Pipeline management |
| `/api/v1/deployments` | GET/POST | Deployment management |
| `/api/v1/events` | POST | Event ingestion |
| `/api/v1/compliance` | GET | Compliance reports |
| `/api/v1/learning` | GET/POST | Learning system |

### Authentication

**Header Format**:
```
Authorization: Bearer <jwt-token>
```

**JWT Claims**:
- `sub`: User ID
- `iss`: Issuer (e.g., "aep")
- `exp`: Expiration timestamp
- `iat`: Issued at timestamp

**Dev Mode**: If `AEP_JWT_SECRET` is not set, auth is disabled with warning log.

---

## Live Update Transport

### Current State

**SSE (Server-Sent Events)**: Primary transport
- Endpoint: `/events/stream`
- Format: `text/event-stream`
- Auth: JWT in query param or header
- Reconnection: Automatic with exponential backoff

**WebSocket**: Available but not primary
- Endpoint: `/ws/*` (various)
- Used for: Real-time bidirectional (if needed)

### Recommendation

**Standardize on SSE** for:
- Pipeline status updates
- Deployment progress
- Event stream
- Notifications

**Use WebSocket only for**:
- Interactive features requiring bidirectional
- Real-time collaboration (future)

---

## CORS Configuration

**Allowed Origins** (configured via `AEP_CORS_ORIGINS`):
- Development: `http://localhost:3000`
- Production: Configured per deployment

**Security Headers** (applied by AepSecurityFilter):
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`
- `Strict-Transport-Security: max-age=31536000`
- `X-Request-Id: <uuid>`

---

## Deployment Topology

### Kubernetes

```yaml
# Services
aep-ui-service:        Port 80 → 3000 (NodePort/ClusterIP)
aep-gateway-service:  Port 80 → 3002 (ClusterIP, optional)
aep-server-service: Port 80 → 8090 (ClusterIP)

# Ingress
aep-ingress:
  - / → aep-ui-service:80
  - /api → aep-server-service:80
  - /events → aep-server-service:80
```

### Docker Compose

```yaml
services:
  aep-server:
    ports:
      - "8090:8090"
  
  aep-ui:
    ports:
      - "3000:3000"
    environment:
      - VITE_API_URL=http://aep-server:8090
  
  # Optional
  aep-gateway:
    ports:
      - "3002:3002"
```

---

## Troubleshooting

### Port Already in Use

```bash
# Find process using port
lsof -i :3000  # or :3002, :8090

# Kill process
kill -9 <PID>
```

### CORS Errors

1. Check `AEP_CORS_ORIGINS` includes UI origin
2. Verify `AepSecurityFilter` is applied
3. Check browser DevTools Network tab for preflight

### Connection Refused

1. Verify service is running: `curl http://localhost:8090/health`
2. Check firewall rules
3. Verify proxy configuration in `vite.config.ts`

---

## Migration Notes

### From Port 3001

If you have existing configurations using port 3001:

1. Update `.env` files to use port 3000 (UI) or 3002 (API)
2. Update proxy configurations
3. Update bookmarks/documentation
4. Clear browser cache

### From Direct Server to Gateway

If migrating server access through gateway:

1. Update UI `VITE_API_URL` to point to gateway
2. Ensure gateway proxies to server
3. Update auth to use gateway tokens

---

## Change Log

| Date | Change | Author |
|------|--------|--------|
| 2026-03-20 | Initial topology definition, port separation | Cascade AI |
| 2026-03-20 | Fixed port collision (3001 → 3000/3002) | Cascade AI |
| 2026-03-20 | Canonical edge decision: server first | Cascade AI |

---

## Related Documents

- `AEP_V2_DEEP_AUDIT_2026-03-19.md` - Audit findings
- `AepSecurityFilter.java` - Security implementation
- `AepAuthFilter.java` - Authentication implementation
- `vite.config.ts` - UI proxy configuration
