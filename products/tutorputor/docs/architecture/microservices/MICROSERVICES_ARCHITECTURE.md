# Microservices Architecture Documentation

**Last Updated:** 2026-04-17  
**Version:** 1.0

---

## Overview

TutorPutor uses a microservices architecture to separate concerns and enable independent scaling and deployment of different system components.

---

## Service Boundaries

### Core Services

#### 1. AI Service (tutorputor-content-generation)
**Responsibilities:**
- AI-powered content generation
- LLM integration
- Batch generation processing
- Content generation gRPC server

**Dependencies:**
- LLM providers (Ollama, OpenAI)
- Message queue for job processing
- Database for generation state

**Communication:**
- gRPC server for content generation requests
- Message queue consumer for batch jobs

---

#### 2. Content Service (tutorputor-platform/content)
**Responsibilities:**
- Content studio management
- Content validation
- Animation generation
- Simulation generation
- Content lifecycle management

**Dependencies:**
- AI service for content generation
- Database for content storage
- File storage for media

**Communication:**
- REST API for content operations
- gRPC client to AI service

---

#### 3. Analytics Service (tutorputor-platform/analytics)
**Responsibilities:**
- Learning analytics
- Performance metrics
- Predictive analytics
- Data export
- Teacher analytics

**Dependencies:**
- Database for analytics data
- Redis for real-time analytics
- Feature store for ML features

**Communication:**
- REST API for analytics queries
- Event streaming for real-time updates

---

#### 4. Platform Service (tutorputor-platform)
**Responsibilities:**
- Learning module management
- Assessment management
- User management
- Authentication
- Enrollment management

**Dependencies:**
- Database for core data
- Auth service for authentication
- Content service for content

**Communication:**
- REST API for platform operations
- gRPC to AI service

---

#### 5. Payment Service (tutorputor-payments)
**Responsibilities:**
- Payment processing
- Subscription management
- Billing
- Invoice generation

**Dependencies:**
- Payment gateway (Stripe)
- Database for payment records
- Platform service for user data

**Communication:**
- REST API for payment operations
- Webhooks for payment notifications

---

#### 6. VR Service (tutorputor-vr)
**Responsibilities:**
- VR lab management
- VR session tracking
- VR analytics
- VR content delivery

**Dependencies:**
- Database for VR data
- Content service for VR content
- Analytics service for metrics

**Communication:**
- REST API for VR operations
- WebSocket for real-time VR sessions

---

#### 7. LTI Service (tutorputor-lti)
**Responsibilities:**
- LTI integration
- LTI launch handling
- LTI grade passback
- LTI roster sync

**Dependencies:**
- LTI provider (Canvas, Moodle)
- Platform service for user data

**Communication:**
- LTI protocol for integration
- REST API for LTI operations

---

#### 8. Kernel Registry Service (tutorputor-kernel-registry)
**Responsibilities:**
- Kernel plugin management
- Plugin discovery
- Plugin versioning
- Plugin distribution

**Dependencies:**
- Database for plugin metadata
- File storage for plugin artifacts

**Communication:**
- REST API for plugin operations
- gRPC for plugin distribution

---

## Communication Patterns

### Synchronous Communication

**REST API:**
- Used for most service-to-service communication
- HTTP/HTTPS for transport
- JSON for request/response
- JWT for authentication

**gRPC:**
- Used for high-performance communication
- Protocol Buffers for serialization
- Streaming for large data transfers
- Used between AI service and platform service

---

### Asynchronous Communication

**Message Queue:**
- Used for batch operations
- Event-driven architecture
- Decouples services
- Enables retry logic

**Event Streaming:**
- Used for real-time analytics
- Event sourcing pattern
- Enables replay capability
- Used for learning events

---

## Deployment Topology

### Kubernetes Deployment

**Namespace Structure:**
```
tutorputor-production/
  ├─ ai-service/
  ├─ content-service/
  ├─ analytics-service/
  ├─ platform-service/
  ├─ payment-service/
  ├─ vr-service/
  ├─ lti-service/
  └─ kernel-registry/
```

**Service Discovery:**
- Kubernetes DNS for service discovery
- Service mesh for advanced routing (optional)
- Health checks for service availability

**Load Balancing:**
- Kubernetes Service for load balancing
- Ingress controller for external access
- Horizontal Pod Autoscaler for scaling

---

### Independent Deployment

**Each Service:**
- Has its own Dockerfile
- Has its own deployment configuration
- Can be deployed independently
- Has versioned releases
- Has health checks

**Deployment Process:**
1. Build Docker image
2. Push to container registry
3. Update Kubernetes deployment
4. Run health checks
5. Rollback if needed

---

## Data Ownership

### Database Boundaries

**Shared Database (Current):**
- Most services share the main database
- Clear schema boundaries between services
- Service-specific tables

**Future: Database per Service:**
- AI service: generation_requests, generation_results
- Content service: modules, lessons, content
- Analytics service: learning_events, analytics_data
- Platform service: users, enrollments, assessments
- Payment service: payments, subscriptions

---

### Data Flow

**Write Operations:**
- Service owns its data
- Other services read via API
- Event publishing for data changes

**Read Operations:**
- Direct database reads for performance
- API calls for cross-service data
- Caching for frequently accessed data

---

## Observability

### Monitoring

**Metrics:**
- Prometheus for metrics collection
- Custom metrics per service
- Service-specific dashboards
- Alerting on thresholds

**Logging:**
- Structured logging
- Centralized log aggregation
- Correlation IDs for tracing
- Log levels per service

**Tracing:**
- Distributed tracing (optional with service mesh)
- Request tracing across services
- Performance analysis
- Error tracking

---

## Security

### Service-to-Service Authentication

**JWT Tokens:**
- Service-to-service authentication
- Token rotation
- Token validation

**mTLS:**
- Mutual TLS for secure communication
- Certificate management
- Certificate rotation

### Authorization

**Role-Based Access:**
- Service roles and permissions
- API-level authorization
- Resource-level authorization

---

## Best Practices

1. **Clear boundaries** - Each service has a single responsibility
2. **Independent deployment** - Services can be deployed independently
3. **API versioning** - Use versioned APIs for backward compatibility
4. **Circuit breakers** - Implement circuit breakers for resilience
5. **Retry logic** - Implement retry logic for transient failures
6. **Health checks** - Implement health checks for monitoring
7. **Observability** - Add metrics, logging, and tracing
8. **Security** - Use authentication and authorization

---

## Future Enhancements

- Service mesh (Istio or Linkerd)
- Event sourcing for audit trail
- CQRS for read/write separation
- GraphQL API gateway
- API gateway for unified entry point
- Service versioning strategy
- Blue-green deployments
- Canary deployments

---

**Maintained By:** TutorPutor Engineering Team  
**Contact:** See team documentation for ownership
