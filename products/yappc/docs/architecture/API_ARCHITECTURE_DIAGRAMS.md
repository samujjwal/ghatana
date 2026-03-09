# YAPPC API Architecture & Integration Diagrams

**Date**: January 29, 2026

Visual reference for YAPPC backend-frontend integration architecture.

---

## Overall System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        YAPPC Platform                               │
│                     End-to-End Architecture                         │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                         Frontend Layer                              │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │   React Web App (Vite)           Port 7001                   │ │
│  │   - TanStack Query (data fetching)                           │ │
│  │   - Jotai (state management)                                 │ │
│  │   - React Router (routing)                                   │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                               │                                      │
│                               │ HTTP + WebSocket                     │
│                               ▼                                      │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                      API Gateway Layer                              │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │   Node.js API Gateway (Fastify)  Port 7002                   │ │
│  │                                                               │ │
│  │   ┌─────────────────────────────────────────────────────┐   │ │
│  │   │ Routing Logic                                       │   │ │
│  │   │  • /api/workspaces  → Node.js (Local)              │   │ │
│  │   │  • /api/projects    → Node.js (Local)              │   │ │
│  │   │  • /api/canvas      → Node.js (Local)              │   │ │
│  │   │  • /api/lifecycle   → Node.js (Local)              │   │ │
│  │   │  • /api/rail        → Java (Proxy)                 │   │ │
│  │   │  • /api/agents      → Java (Proxy)                 │   │ │
│  │   │  • /api/ai/*        → Java (Proxy)                 │   │ │
│  │   └─────────────────────────────────────────────────────┘   │ │
│  │                                                               │ │
│  │   Middleware:                                                │ │
│  │   - CORS handling                                            │ │
│  │   - Authentication                                           │ │
│  │   - Request logging                                          │ │
│  │   - Error handling                                           │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                    │                           │                     │
│         Local      │                           │  Proxy              │
│         Handler    │                           │                     │
└─────────────────────────────────────────────────────────────────────┘
                     │                           │
                     ▼                           ▼
┌────────────────────────────────┐  ┌───────────────────────────────┐
│   Node.js Backend Services     │  │   Java Backend Services       │
│   Port 7002 (internal)         │  │   Port 7003                   │
│                                │  │                               │
│   Services:                    │  │   Services:                   │
│   • Workspace Management       │  │   • Left Rail Components      │
│   • Project Management         │  │   • AI Agents                 │
│   • Canvas Persistence         │  │   • Requirements Engine       │
│   • Lifecycle Orchestration    │  │   • AI Suggestions            │
│   • DevSecOps Integration      │  │   • Architecture Analysis     │
│   • WebSocket (Collaboration)  │  │   • Audit Logging             │
│   • GraphQL API                │  │   • Version Control           │
│                                │  │   • RBAC Authorization         │
│   Tech Stack:                  │  │                               │
│   • Fastify                    │  │   Tech Stack:                 │
│   • Prisma ORM                 │  │   • ActiveJ HTTP              │
│   • PostgreSQL                 │  │   • Promise-based async       │
│   • Redis                      │  │   • High-performance          │
└────────────────────────────────┘  └───────────────────────────────┘
                     │                           │
                     │                           │
                     ▼                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      Data & Cache Layer                             │
│                                                                     │
│   ┌──────────────────────┐        ┌────────────────────────┐      │
│   │   PostgreSQL         │        │   Redis Cache          │      │
│   │   Port 5432          │        │   Port 6379            │      │
│   │                      │        │                        │      │
│   │   Tables:            │        │   Caches:              │      │
│   │   • workspaces       │        │   • Session data       │      │
│   │   • projects         │        │   • API responses      │      │
│   │   • canvas_documents │        │   • User preferences   │      │
│   │   • artifacts        │        │   • Rate limits        │      │
│   │   • audit_logs       │        └────────────────────────┘      │
│   └──────────────────────┘                                         │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Request Flow Diagrams

### Workspace Request Flow (Node.js Local)

```
User                Frontend            API Gateway          Node.js Backend      PostgreSQL
 │                     │                     │                     │                  │
 │  GET /workspaces    │                     │                     │                  │
 ├────────────────────>│                     │                     │                  │
 │                     │  GET /api/workspaces│                     │                  │
 │                     ├────────────────────>│                     │                  │
 │                     │                     │ Check auth token    │                  │
 │                     │                     ├──────────┐          │                  │
 │                     │                     │          │          │                  │
 │                     │                     │<─────────┘          │                  │
 │                     │                     │                     │                  │
 │                     │                     │ Route to Node.js    │                  │
 │                     │                     ├────────────────────>│                  │
 │                     │                     │                     │ SELECT * FROM    │
 │                     │                     │                     │   workspaces     │
 │                     │                     │                     ├─────────────────>│
 │                     │                     │                     │                  │
 │                     │                     │                     │<─────────────────┤
 │                     │                     │                     │  Workspaces[]    │
 │                     │                     │  200 OK             │                  │
 │                     │<────────────────────┼─────────────────────┤                  │
 │  Workspaces Data    │                     │                     │                  │
 │<────────────────────┤                     │                     │                  │
 │                     │                     │                     │                  │
```

### Left Rail Request Flow (Java Proxy)

```
User                Frontend            API Gateway          Java Backend         Services
 │                     │                     │                     │                  │
 │  GET /rail/comp...  │                     │                     │                  │
 ├────────────────────>│                     │                     │                  │
 │                     │ GET /api/rail/comp.│                     │                  │
 │                     ├────────────────────>│                     │                  │
 │                     │                     │ Check if Java route │                  │
 │                     │                     ├──────────┐          │                  │
 │                     │                     │          │          │                  │
 │                     │                     │<─────────┘          │                  │
 │                     │                     │  YES - Proxy        │                  │
 │                     │                     │                     │                  │
 │                     │                     │ Proxy to Java:7003  │                  │
 │                     │                     ├────────────────────>│                  │
 │                     │                     │                     │ Query services   │
 │                     │                     │                     ├─────────────────>│
 │                     │                     │                     │                  │
 │                     │                     │                     │<─────────────────┤
 │                     │                     │                     │  Components[]    │
 │                     │                     │  200 OK             │                  │
 │                     │<────────────────────┼─────────────────────┤                  │
 │  Components Data    │                     │                     │                  │
 │<────────────────────┤                     │                     │                  │
```

### Canvas Save with Versioning Flow

```
User              Frontend        API Gateway      Node.js Backend    PostgreSQL
 │                   │                 │                 │                │
 │  Save Canvas      │                 │                 │                │
 ├──────────────────>│                 │                 │                │
 │                   │ PUT /canvas     │                 │                │
 │                   ├────────────────>│                 │                │
 │                   │                 │ Authenticate    │                │
 │                   │                 ├───────┐         │                │
 │                   │                 │       │         │                │
 │                   │                 │<──────┘         │                │
 │                   │                 │                 │                │
 │                   │                 │ Local handler   │                │
 │                   │                 ├────────────────>│                │
 │                   │                 │                 │ BEGIN TX       │
 │                   │                 │                 ├───────────────>│
 │                   │                 │                 │                │
 │                   │                 │                 │ UPDATE canvas  │
 │                   │                 │                 ├───────────────>│
 │                   │                 │                 │                │
 │                   │                 │                 │ INSERT version │
 │                   │                 │                 ├───────────────>│
 │                   │                 │                 │                │
 │                   │                 │                 │ COMMIT TX      │
 │                   │                 │                 ├───────────────>│
 │                   │                 │                 │                │
 │                   │                 │ 200 OK          │                │
 │                   │<────────────────┼─────────────────┤                │
 │  Success          │                 │                 │                │
 │<──────────────────┤                 │                 │                │
```

---

## Service Boundaries

### Node.js Backend Responsibilities

```
┌────────────────────────────────────────────────────────────────┐
│              Node.js Backend Services                          │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  Core Domain:                                                  │
│  • Workspace & Project Management (PostgreSQL)                │
│  • Canvas Persistence & Versioning                            │
│  • Lifecycle Orchestration                                    │
│  • Real-time Collaboration (WebSocket)                        │
│                                                                │
│  Integration Points:                                           │
│  • GraphQL API (federated with Java services)                 │
│  • REST API (local handlers)                                  │
│  • WebSocket (Socket.io)                                      │
│                                                                │
│  Data Access:                                                  │
│  • Prisma ORM → PostgreSQL                                    │
│  • Redis for caching & sessions                               │
│                                                                │
│  Responsibilities:                                             │
│  ✓ CRUD operations on core entities                           │
│  ✓ Real-time collaboration                                    │
│  ✓ GraphQL schema stitching                                   │
│  ✓ API Gateway routing logic                                  │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### Java Backend Responsibilities

```
┌────────────────────────────────────────────────────────────────┐
│               Java Backend Services                            │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  Core Domain:                                                  │
│  • AI Agent Orchestration                                     │
│  • Requirements Analysis & Processing                         │
│  • Architecture Analysis & Validation                         │
│  • Left Rail Component Management                             │
│                                                                │
│  Integration Points:                                           │
│  • REST API (ActiveJ HTTP server)                             │
│  • High-performance async processing                          │
│                                                                │
│  Data Access:                                                  │
│  • Direct JDBC → PostgreSQL (for complex queries)            │
│  • Redis for distributed caching                              │
│                                                                │
│  Responsibilities:                                             │
│  ✓ AI/ML model integration                                    │
│  ✓ Complex business logic                                     │
│  ✓ High-throughput processing                                 │
│  ✓ Advanced analytics                                         │
│  ✓ Audit logging & version control                            │
│  ✓ RBAC enforcement                                           │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

---

## API Gateway Routing Logic

```
┌─────────────────────────────────────────────────────────────────┐
│                    API Gateway Decision Tree                    │
└─────────────────────────────────────────────────────────────────┘

                        Incoming Request
                               │
                               ▼
                    ┌─────────────────────┐
                    │  Extract Request    │
                    │  Method & Path      │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  Authentication?    │
                    │  (If required)      │
                    └──────────┬──────────┘
                               │
                    ┌──────────┴──────────┐
                    │                     │
                ✓ Authenticated      ✗ Not Authenticated
                    │                     │
                    │                     ▼
                    │              Return 401 Unauthorized
                    │
                    ▼
         ┌────────────────────────┐
         │  Check Route Pattern   │
         └────────────────────────┘
                    │
        ┌───────────┴───────────┐
        │                       │
    Matches Java         Matches Node.js
    Backend Pattern      Backend Pattern
        │                       │
        ▼                       ▼
┌──────────────────┐   ┌──────────────────┐
│ Proxy to Java    │   │ Handle Locally   │
│ Backend          │   │ (Node.js)        │
│ Port 7003        │   │                  │
└────┬─────────────┘   └────┬─────────────┘
     │                      │
     │                      │
     ▼                      ▼
┌──────────────────┐   ┌──────────────────┐
│ Java Response    │   │ Node.js Response │
│ (Proxied back)   │   │ (Direct)         │
└────┬─────────────┘   └────┬─────────────┘
     │                      │
     └──────────┬───────────┘
                │
                ▼
       ┌────────────────────┐
       │ Add Response       │
       │ Headers            │
       │ (CORS, etc)        │
       └────────┬───────────┘
                │
                ▼
       ┌────────────────────┐
       │ Log Request/       │
       │ Response           │
       └────────┬───────────┘
                │
                ▼
          Return to Client
```

---

## WebSocket Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│              Real-time Collaboration Architecture               │
└─────────────────────────────────────────────────────────────────┘

Frontend Clients                 Node.js Backend              Redis PubSub
     │                                │                           │
     │  WebSocket Connect             │                           │
     ├───────────────────────────────>│                           │
     │                                │ Store connection          │
     │                                ├──────────────┐            │
     │                                │              │            │
     │                                │<─────────────┘            │
     │                                │                           │
     │  Canvas Update Event           │                           │
     ├───────────────────────────────>│                           │
     │                                │ Validate update           │
     │                                ├──────────────┐            │
     │                                │              │            │
     │                                │<─────────────┘            │
     │                                │                           │
     │                                │ Publish to Redis channel  │
     │                                ├──────────────────────────>│
     │                                │                           │
     │                                │ Subscribe to updates      │
     │                                │<──────────────────────────┤
     │                                │                           │
     │                                │ Broadcast to all clients  │
     │  Canvas Update (broadcast)     │                           │
     │<───────────────────────────────┤                           │
     │                                │                           │
     │                                │                           │

Connected Clients:
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  Client A    │  │  Client B    │  │  Client C    │
│  (Editing)   │  │  (Viewing)   │  │  (Viewing)   │
└──────────────┘  └──────────────┘  └──────────────┘
       │                 │                 │
       └─────────────────┼─────────────────┘
                         │
                    All receive
                  the same updates
```

---

## Data Flow Patterns

### Pattern 1: Simple CRUD (Node.js)

```
Frontend → API Gateway → Node.js Backend → PostgreSQL
                                          ← Response
         ← API Gateway ← Node.js Backend ←
```

### Pattern 2: AI Processing (Java)

```
Frontend → API Gateway → Java Backend → AI Service
                                      ← AI Result
         ← API Gateway ← Java Backend ←
```

### Pattern 3: Complex Flow (Both backends)

```
Frontend → API Gateway → Node.js Backend → PostgreSQL (Save)
                      ↓
                Java Backend → AI Processing
                      ↓
              Store results → PostgreSQL
                      ↓
                Node.js Backend (notify)
                      ↓
              WebSocket → Frontend (update)
```

### Pattern 4: GraphQL Federated Query

```
Frontend → GraphQL Gateway (Node.js)
                ↓
           ┌────┴────┐
           ↓         ↓
    Node.js Backend  Java Backend
           │         │
           ↓         ↓
      PostgreSQL   AI Services
           │         │
           └────┬────┘
                ↓
         Merged Response
                ↓
            Frontend
```

---

## Authentication & Authorization Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                   Auth & RBAC Flow                              │
└─────────────────────────────────────────────────────────────────┘

User               Frontend          API Gateway         Auth Service
 │                    │                    │                    │
 │  Login             │                    │                    │
 ├───────────────────>│                    │                    │
 │                    │ POST /api/auth/login                    │
 │                    ├───────────────────>│                    │
 │                    │                    │ Verify credentials │
 │                    │                    ├───────────────────>│
 │                    │                    │                    │
 │                    │                    │<───────────────────┤
 │                    │                    │  JWT Token         │
 │                    │  200 OK + Token    │                    │
 │                    │<───────────────────┤                    │
 │  Token stored      │                    │                    │
 │<───────────────────┤                    │                    │
 │                    │                    │                    │
 │  API Request       │                    │                    │
 ├───────────────────>│                    │                    │
 │                    │ GET /api/projects  │                    │
 │                    │ Header: Auth Token │                    │
 │                    ├───────────────────>│                    │
 │                    │                    │ Verify token       │
 │                    │                    ├───────────────────>│
 │                    │                    │                    │
 │                    │                    │<───────────────────┤
 │                    │                    │  User + Permissions│
 │                    │                    │                    │
 │                    │                    │ Check RBAC         │
 │                    │                    ├──────────┐         │
 │                    │                    │          │         │
 │                    │                    │<─────────┘         │
 │                    │                    │  Authorized        │
 │                    │                    │                    │
 │                    │                    │ Execute request    │
 │                    │                    ├───────────────┐    │
 │                    │  200 OK + Data     │               │    │
 │                    │<───────────────────┤<──────────────┘    │
 │  Data              │                    │                    │
 │<───────────────────┤                    │                    │
```

---

## Error Handling Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                   Error Handling Architecture                   │
└─────────────────────────────────────────────────────────────────┘

Backend Service           API Gateway            Frontend
       │                       │                      │
       │  Error occurs         │                      │
       ├──────────────────────>│                      │
       │  {                    │                      │
       │    error: "...",      │                      │
       │    code: "ERR_001",   │                      │
       │    details: {...}     │                      │
       │  }                    │                      │
       │                       │ Log error            │
       │                       ├───────────┐          │
       │                       │           │          │
       │                       │<──────────┘          │
       │                       │                      │
       │                       │ Transform to         │
       │                       │ standard format      │
       │                       ├───────────┐          │
       │                       │           │          │
       │                       │<──────────┘          │
       │                       │  {                   │
       │                       │    error: {          │
       │                       │      message: "",    │
       │                       │      code: "",       │
       │                       │      timestamp: ""   │
       │                       │    }                 │
       │                       │  }                   │
       │                       │                      │
       │                       │ 4xx/5xx response     │
       │                       ├─────────────────────>│
       │                       │                      │ Display error
       │                       │                      ├───────────┐
       │                       │                      │           │
       │                       │                      │<──────────┘
       │                       │                      │ Show toast/
       │                       │                      │ modal
```

---

## Monitoring & Observability

```
┌─────────────────────────────────────────────────────────────────┐
│              Observability Architecture                         │
└─────────────────────────────────────────────────────────────────┘

Application Layer            Observability Tools
       │                           │
       │  Metrics                  │
       ├──────────────────────────>│ Prometheus
       │  (request count,          │ (time-series DB)
       │   latency, errors)        │      │
       │                           │      │
       │  Traces                   │      ▼
       ├──────────────────────────>│ Jaeger
       │  (distributed             │ (distributed tracing)
       │   tracing)                │      │
       │                           │      │
       │  Logs                     │      ▼
       ├──────────────────────────>│ Loki / ELK
       │  (structured              │ (log aggregation)
       │   logging)                │      │
       │                           │      │
       │                           │      ▼
       │                           │ Grafana
       │                           │ (visualization)
       │                           │      │
       │                           │      ▼
       │  Alerts                   │ AlertManager
       │<──────────────────────────┤ (alerting)
       │                           │
```

---

## Deployment Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                Production Deployment (Kubernetes)               │
└─────────────────────────────────────────────────────────────────┘

                        ┌──────────────────┐
                        │  Load Balancer   │
                        │  (Ingress)       │
                        └────────┬─────────┘
                                 │
                    ┌────────────┴─────────────┐
                    │                          │
                    ▼                          ▼
           ┌────────────────┐        ┌────────────────┐
           │  Frontend      │        │  API Gateway   │
           │  Pods (x3)     │        │  Pods (x3)     │
           │  Port 7001     │        │  Port 7002     │
           └────────────────┘        └────────┬───────┘
                                               │
                              ┌────────────────┴────────────────┐
                              │                                 │
                              ▼                                 ▼
                    ┌──────────────────┐          ┌──────────────────┐
                    │  Node.js Backend │          │  Java Backend    │
                    │  Pods (x3)       │          │  Pods (x3)       │
                    │  Port 7002       │          │  Port 7003       │
                    └────────┬─────────┘          └────────┬─────────┘
                             │                              │
                             └──────────────┬───────────────┘
                                            │
                              ┌─────────────┴─────────────┐
                              │                           │
                              ▼                           ▼
                    ┌──────────────────┐      ┌──────────────────┐
                    │  PostgreSQL      │      │  Redis           │
                    │  StatefulSet     │      │  StatefulSet     │
                    │  (Primary +      │      │  (Cluster)       │
                    │   Replicas)      │      │                  │
                    └──────────────────┘      └──────────────────┘
```

---

**End of Architecture Diagrams**
