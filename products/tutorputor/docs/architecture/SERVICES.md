# Tutorputor Service Documentation

This document provides detailed documentation for all Tutorputor services, including their purpose, APIs, configuration, and operational guidelines.

## Table of Contents

1. [Platform Service](#platform-service)
2. [Payment Service](#payment-service)
3. [AI Agents Service](#ai-agents-service)
4. [Content Generation Service](#content-generation-service)
5. [Kernel Services](#kernel-services)
6. [Service Communication](#service-communication)
7. [Monitoring and Observability](#monitoring-and-observability)

## Platform Service

### Overview

The Platform Service is the core backend API for the Tutorputor platform. It handles user management, content generation, assessments, learning analytics, and more.

### Technology Stack

- **Runtime**: Node.js 18+
- **Framework**: Fastify
- **Language**: TypeScript
- **Database**: PostgreSQL (via Prisma)
- **Cache**: Redis
- **Queue**: BullMQ

### Modules

#### User Module
- **Location**: `src/modules/user/`
- **Purpose**: User authentication, authorization, and profile management
- **Key Features**:
  - JWT-based authentication
  - Role-based access control (RBAC)
  - User profile management
  - Multi-tenant support

#### Content Module
- **Location**: `src/modules/content/`
- **Purpose**: Content generation, management, and delivery
- **Key Features**:
  - AI-powered content generation
  - Module lifecycle management
  - Content caching
  - Version control

#### AI Module
- **Location**: `src/modules/ai/`
- **Purpose**: AI integration and intelligent tutoring
- **Key Features**:
  - OpenAI integration
  - AI response caching
  - Prompt management
  - Cost tracking

#### Assessment Module
- **Location**: `src/modules/assessment/`
- **Purpose**: Assessment creation, delivery, and grading
- **Key Features**:
  - Multiple question types
  - Auto-grading
  - Analytics and reporting

#### Learning Module
- **Location**: `src/modules/learning/`
- **Purpose**: Learning progress tracking and analytics
- **Key Features**:
  - Enrollment management
  - Progress tracking
  - Learning analytics
  - Recommendations

### API Endpoints

#### Authentication
```
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/logout
POST /api/v1/auth/refresh
```

#### Modules
```
GET /api/v1/modules
GET /api/v1/modules/:id
POST /api/v1/modules
PUT /api/v1/modules/:id
DELETE /api/v1/modules/:id
```

#### Assessments
```
GET /api/v1/assessments
GET /api/v1/assessments/:id
POST /api/v1/assessments/:id/attempts
GET /api/v1/assessments/:id/attempts/:attemptId
```

#### Content Generation
```
POST /api/v1/content/generate
GET /api/v1/content/status/:jobId
```

### Configuration

#### Environment Variables

```env
# Database
DATABASE_URL=postgresql://user:password@localhost:5432/tutorputor
DATABASE_POOL_SIZE=10
DATABASE_POOL_TIMEOUT=10
DATABASE_CONNECT_TIMEOUT=5
DATABASE_ACQUIRE_TIMEOUT=30000
DATABASE_IDLE_TIMEOUT=600
DATABASE_MAX_LIFETIME=1800

# Redis
REDIS_URL=redis://localhost:6379

# JWT
JWT_SECRET=your-secret-key
JWT_EXPIRES_IN=15m
JWT_REFRESH_EXPIRES_IN=7d

# AI Services
OPENAI_API_KEY=your-openai-api-key
OPENAI_MODEL=gpt-4

# Content Generation
CONTENT_QUEUE_DISABLED=false
CONTENT_GENERATION_TIMEOUT=300000

# Monitoring
SENTRY_DSN=your-sentry-dsn
SENTRY_ENVIRONMENT=development
```

### Running the Service

#### Development
```bash
cd services/tutorputor-platform
pnpm dev
```

#### Production
```bash
cd services/tutorputor-platform
pnpm build
pnpm start
```

#### Docker
```bash
docker build -t tutorputor-platform .
docker run -p 3000:3000 tutorputor-platform
```

### Health Checks

```
GET /health
GET /health/ready
GET /health/live
```

### Monitoring

The platform service exposes metrics at `/metrics` endpoint in Prometheus format.

## Payment Service

### Overview

The Payment Service handles payment processing, subscription management, and billing for the Tutorputor platform.

### Technology Stack

- **Runtime**: Node.js 18+
- **Framework**: Fastify
- **Language**: TypeScript
- **Payment Provider**: Stripe
- **Database**: PostgreSQL

### Features

- Stripe integration for payments
- Subscription management
- Invoice generation
- Payment webhooks
- Refund processing

### API Endpoints

#### Payments
```
POST /api/v1/payments/create-intent
POST /api/v1/payments/confirm
GET /api/v1/payments/:id
```

#### Subscriptions
```
POST /api/v1/subscriptions/create
POST /api/v1/subscriptions/cancel
PUT /api/v1/subscriptions/:id
GET /api/v1/subscriptions/:id
```

#### Webhooks
```
POST /api/v1/webhooks/stripe
```

### Configuration

```env
# Stripe
STRIPE_SECRET_KEY=sk_test_your-key
STRIPE_WEBHOOK_SECRET=whsec_your-secret
STRIPE_PRICE_ID=price_your-price-id

# Database
DATABASE_URL=postgresql://user:password@localhost:5432/tutorputor-payments
```

### Running the Service

```bash
cd services/tutorputor-payments
pnpm dev
```

## AI Agents Service

### Overview

The AI Agents Service provides agent orchestration and execution capabilities for AI-powered features.

### Technology Stack

- **Runtime**: Node.js 18+
- **Language**: TypeScript
- **Agent Framework**: LangChain
- **Vector Store**: Pinecone/Weaviate

### Features

- Agent runtime and execution
- Tool integration (search, database, APIs)
- Prompt management
- Context management
- Memory and state management

### Agent Types

#### Content Generation Agent
- Generates educational content based on learning objectives
- Uses RAG (Retrieval-Augmented Generation)
- Supports multiple content formats

#### Tutoring Agent
- Provides real-time tutoring
- Adapts to learner profile
- Handles follow-up questions

#### Assessment Agent
- Creates assessment questions
- Grades responses
- Provides feedback

### Configuration

```env
# Vector Store
PINECONE_API_KEY=your-pinecone-key
PINECONE_ENVIRONMENT=us-east-1
PINECONE_INDEX=tutorputor

# AI Models
OPENAI_API_KEY=your-openai-key
ANTHROPIC_API_KEY=your-anthropic-key
```

### Running the Service

```bash
cd services/tutorputor-ai-agents
pnpm dev
```

## Content Generation Service

### Overview

The Content Generation Service handles asynchronous content generation jobs using BullMQ.

### Technology Stack

- **Runtime**: Node.js 18+
- **Queue**: BullMQ
- **Backend**: Redis
- **Workers**: Node.js

### Features

- Asynchronous content generation
- Job prioritization
- Retry logic with exponential backoff
- Dead letter queue
- Progress tracking

### Queue Configuration

```typescript
const queueOptions = {
  connection: {
    host: 'localhost',
    port: 6379,
  },
  defaultJobOptions: {
    removeOnComplete: {
      count: 1000,
      age: 3600,
    },
    removeOnFail: {
      count: 5000,
      age: 86400,
    },
  },
};
```

### Job Priorities

- **urgent**: Priority 10 - Critical content generation
- **high**: Priority 8 - User-facing content
- **normal**: Priority 5 - Standard content
- **low**: Priority 1 - Background tasks

### Running Workers

```bash
cd services/tutorputor-platform
pnpm worker:content
```

## Kernel Services

### Overview

Kernel services provide shared infrastructure and platform capabilities for all services.

### Kernel Core

- **Purpose**: Core platform infrastructure
- **Features**:
  - Event processing
  - Plugin system
  - Kernel services
  - Configuration management

### Database Service

- **Purpose**: Database abstraction and connection pooling
- **Features**:
  - Connection pooling
  - Query optimization
  - Read replica support
  - Transaction management

### HTTP Service

- **Purpose**: HTTP client and server infrastructure
- **Features**:
  - HTTP client
  - Server framework
  - Load balancing
  - Circuit breakers

### Configuration

```env
# Kernel
KERNEL_PLUGIN_DIR=/path/to/plugins
KERNEL_CONFIG_FILE=/path/to/config.yaml

# Database
DB_CONNECTION_URL=postgresql://user:password@localhost:5432/kernel
DB_POOL_SIZE=20
DB_MAX_LIFETIME=1800

# HTTP
HTTP_CLIENT_TIMEOUT=30000
HTTP_CLIENT_MAX_RETRIES=3
```

### Running Kernel Services

```bash
cd platform-kernel/kernel-core
./gradlew bootRun
```

## Service Communication

### Inter-Service Communication

Services communicate through:

1. **HTTP/REST**: Synchronous communication
2. **gRPC**: High-performance RPC (for internal services)
3. **Message Queues**: Asynchronous communication
4. **Events**: Event-driven communication

### Service Discovery

Services use:
- **DNS**: For service discovery in Kubernetes
- **Environment Variables**: For local development
- **Service Registry**: (Future) Consul/Eureka

### API Gateway

The API Gateway (tutorputor-api-gateway) provides:
- Request routing
- Authentication and authorization
- Rate limiting
- Request/response transformation
- Load balancing

### Circuit Breakers

Services implement circuit breakers for resilience:
- Open circuit after N failures
- Half-open state for recovery testing
- Closed circuit when healthy

## Monitoring and Observability

### Metrics

All services expose Prometheus metrics at `/metrics`:

- **Counter**: Monotonically increasing values
- **Gauge**: Values that can go up or down
- **Histogram**: Distribution of values
- **Summary**: Quantiles

### Logging

Services use structured logging with:
- **Format**: JSON
- **Level**: debug, info, warn, error
- **Context**: Request ID, tenant ID, user ID
- **Aggregation**: Centralized log aggregation (Loki/ELK)

### Tracing

Distributed tracing with OpenTelemetry:
- **Trace ID**: Correlates requests across services
- **Span ID**: Individual operation
- **Parent Span ID**: Operation hierarchy
- **Baggage**: Context propagation

### Health Checks

All services implement health checks:
- **Liveness**: Is the service running?
- **Readiness**: Is the service ready to accept traffic?
- **Startup**: Is the service starting up?

### Alerting

Alerts are configured for:
- High error rates
- High latency
- Low success rates
- Resource exhaustion
- Service unavailability

## Service Deployment

### Development

Services run locally with:
- Docker Compose for dependencies
- Local PostgreSQL and Redis
- Mocked external services

### Staging

Services deployed to staging with:
- Kubernetes cluster
- Production-like configuration
- Reduced scale
- Test data

### Production

Services deployed to production with:
- Multi-region Kubernetes
- Auto-scaling
- Load balancing
- CDN for static assets

## Service Maintenance

### Rolling Updates

Services support rolling updates:
- Zero-downtime deployments
- Health check verification
- Automatic rollback on failure

### Database Migrations

Database migrations are handled via:
- Prisma migrations (TypeScript services)
- Flyway (Java services)
- Version-controlled migration scripts

### Configuration Updates

Configuration updates are handled via:
- Environment variables
- ConfigMaps (Kubernetes)
- Hot-reload where supported

### Troubleshooting

Common issues and solutions:

**Service won't start**
- Check environment variables
- Verify database connectivity
- Check service logs

**High latency**
- Check database query performance
- Verify cache hit rates
- Check queue backlog

**High error rate**
- Check service dependencies
- Verify external service availability
- Check rate limits

## Service Dependencies

### Platform Service Dependencies

- PostgreSQL (primary database)
- Redis (cache and queue)
- OpenAI API (AI services)
- Stripe API (payments)

### Payment Service Dependencies

- PostgreSQL (payment database)
- Stripe API (payment processing)

### AI Agents Service Dependencies

- PostgreSQL (agent state)
- Vector Store (Pinecone/Weaviate)
- OpenAI API (AI models)

## Service Security

### Authentication

- JWT-based authentication
- Token refresh mechanism
- Token revocation support

### Authorization

- Role-based access control (RBAC)
- Permission-based access
- Tenant isolation

### Data Protection

- Encryption at rest
- Encryption in transit (TLS)
- PII masking in logs
- Audit logging

### Rate Limiting

- Per-user rate limits
- Per-tenant rate limits
- IP-based rate limits
- API key rate limits

## Service Scaling

### Horizontal Scaling

Services can be scaled horizontally:
- Stateless services (Platform, Payment)
- Stateful services (Database, Redis)
- Queue workers

### Vertical Scaling

Services can be scaled vertically:
- CPU allocation
- Memory allocation
- Database connection pool size

### Auto-scaling

Kubernetes Horizontal Pod Autoscaler:
- Scale based on CPU utilization
- Scale based on memory utilization
- Scale based on custom metrics

## Service Backup and Recovery

### Database Backups

- Daily full backups
- Point-in-time recovery
- Backup retention: 30 days
- Cross-region replication

### Redis Backups

- RDB snapshots
- AOF persistence
- Backup retention: 7 days

### Disaster Recovery

- Multi-region deployment
- Failover procedures
- Recovery time objective (RTO): 4 hours
- Recovery point objective (RPO): 15 minutes

## Service Documentation Updates

When updating services:
1. Update this documentation
2. Update API documentation
3. Update architecture diagrams
4. Communicate changes to the team

## Contact

For questions about services:
- Platform Service: #platform-team
- Payment Service: #payments-team
- AI Agents Service: #ai-team
- Kernel Services: #kernel-team
