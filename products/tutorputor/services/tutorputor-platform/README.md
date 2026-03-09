# TutorPutor Platform Service

**Status**: 🚧 **IN DEVELOPMENT** (Phase 1 - Foundation)

Consolidated TutorPutor backend service replacing 28 microservices with a modular monolith.

## Architecture

### **Consolidation Strategy**

```
34 Microservices → 3 Core Services

tutorputor-platform (TypeScript/Fastify)
├── Content Module      (7 services consolidated)
├── Learning Module     (4 services consolidated)
├── Collaboration       (1 service consolidated)
├── User Module         (2 services consolidated)
├── Engagement Module   (3 services consolidated)
├── Integration Module  (4 services consolidated)
└── Tenant Module       (1 service consolidated)

tutorputor-ai-service (Java - uses libs/ai-integration)
└── AI agents for content generation & validation

tutorputor-sim-runtime (Java - uses libs/operator)
└── Physics simulation execution
```

### **Shared Infrastructure** ✅

**Reuses existing platform libraries:**

- `libs/ai-integration` - LLM services (OpenAI, LangChain4j)
- `libs/agent-runtime` - BaseAgent framework
- `libs/observability` - Metrics, health checks
- `libs/http-server` - HTTP utilities
- `libs/database` - Database abstractions
- `libs/redis-cache` - Caching
- `libs/auth` - Authentication/authorization
- `libs/event-cloud` - Event streaming

**No duplication of platform functionality!**

## Development

### **Prerequisites**

- Node.js 22+
- pnpm 9+
- PostgreSQL 16+
- Redis 7+

### **Installation**

```bash
# Install dependencies
pnpm install

# Setup environment
cp .env.example .env

# Database migration
cd ../tutorputor-db
pnpm prisma migrate dev
pnpm prisma generate
```

### **Running**

```bash
# Development mode with hot reload
pnpm dev

# Production build
pnpm build
pnpm start

# Run tests
pnpm test

# Type checking
pnpm type-check
```

### **Endpoints**

```
Health:        GET  /health
Liveness:      GET  /health/live
Readiness:     GET  /health/ready
Metrics:       GET  /metrics

API:
  Content:     /api/content/*
  Learning:    /api/learning/*
  Collaboration: /api/collaboration/*
  User:        /api/user/*
  Engagement:  /api/engagement/*
  Integration: /api/integration/*
  Tenant:      /api/tenant/*
```

## Production Features ✅

- **Observability**: Prometheus metrics, health checks
- **Error Tracking**: Sentry integration
- **Rate Limiting**: Redis-based distributed rate limiting
- **Circuit Breakers**: Resilient external service calls
- **Multi-tenancy**: Tenant-aware request handling
- **Security**: Helmet, CORS, JWT authentication

## Consolidation Status

- [x] Foundation services and middleware
- [x] Content, learning, engagement, integration, and tenant modules
- [x] Shared observability and security layers
- [ ] Full production hardening and complete automation coverage

## Directory Structure

```
tutorputor-platform/
├── src/
│   ├── server.ts                    # Main entry point
│   ├── modules/                     # Business logic modules
│   │   ├── content/                 # Content management
│   │   ├── learning/                # Learning engine
│   │   ├── collaboration/           # Real-time features
│   │   ├── user/                    # User management
│   │   ├── engagement/              # Engagement features
│   │   ├── integration/             # External integrations
│   │   └── tenant/                  # Multi-tenancy
│   ├── core/                        # Core utilities
│   │   ├── observability/           # Metrics, error tracking
│   │   └── middleware/              # Middleware stack
│   └── clients/                     # External service clients
│       ├── ai-service.ts            # AI service gRPC client
│       └── sim-runtime.ts           # Sim runtime gRPC client
├── package.json
├── tsconfig.json
└── README.md
```

## References

- [Architecture Consolidation Plan](../../ARCHITECTURE_CONSOLIDATION_PLAN.md)
- [Architecture Analysis](../../TUTORPUTOR_ARCHITECTURE_ANALYSIS.md)
- [Original Architecture Integration Doc](../../ARCHITECTURE_JAVA_TYPESCRIPT_INTEGRATION.md)
