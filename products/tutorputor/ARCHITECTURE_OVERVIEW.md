# Tutorputor Architecture Overview

This document provides a high-level overview of the Tutorputor platform architecture, its components, and how they interact.

## Table of Contents

1. [System Overview](#system-overview)
2. [Architecture Principles](#architecture-principles)
3. [Component Architecture](#component-architecture)
4. [Data Flow](#data-flow)
5. [Technology Stack](#technology-stack)
6. [Deployment Architecture](#deployment-architecture)
7. [Security Architecture](#security-architecture)
8. [Scalability Considerations](#scalability-considerations)

## System Overview

Tutorputor is an AI-native educational platform that provides personalized learning experiences through adaptive content generation, intelligent tutoring, and real-time feedback. The system is designed as a microservices architecture with strong separation of concerns.

### Core Capabilities

- **Adaptive Learning**: AI-powered content generation that adapts to learner profiles
- **Intelligent Tutoring**: Real-time AI tutoring with context-aware responses
- **Assessment Engine**: Automated assessment creation and grading
- **Content Marketplace**: Module marketplace for content creators
- **Collaboration Tools**: Study groups, forums, and peer learning
- **Analytics**: Learning analytics and progress tracking

## Architecture Principles

### 1. Domain-Driven Design

The system is organized around business domains:
- Learning & Content
- User Management
- Payments & Billing
- Collaboration
- AI & Intelligence
- Assessment & Evaluation

### 2. Layered Architecture

```
┌─────────────────────────────────────┐
│         Presentation Layer            │
│  (Web Apps, Admin Dashboard, APIs)   │
├─────────────────────────────────────┤
│         Application Layer            │
│  (Business Logic, Workflows,       │
│   Domain Services)                  │
├─────────────────────────────────────┤
│         Platform Layer               │
│  (Shared Services, Infrastructure,  │
│   Cross-Cutting Concerns)           │
├─────────────────────────────────────┤
│         Data Layer                  │
│  (Databases, Caches, Queues)        │
└─────────────────────────────────────┘
```

### 3. Separation of Concerns

- **Transport**: HTTP, gRPC, WebSocket - handles communication
- **Domain**: Business logic and rules
- **Persistence**: Database operations
- **Infrastructure**: Configuration, logging, monitoring

### 4. Event-Driven Architecture

The system uses events for loose coupling between components:
- Learning events (module completion, assessment submission)
- Payment events (subscription changes, invoice generation)
- Content events (generation requests, updates)

## Component Architecture

### Frontend Applications

#### Tutorputor Web (React)
- **Purpose**: Main learner-facing web application
- **Tech**: React, TypeScript, Vite, TailwindCSS
- **Features**: Module browsing, learning interface, assessments, marketplace

#### Tutorputor Admin (React)
- **Purpose**: Admin dashboard for platform management
- **Tech**: React, TypeScript, Vite, shadcn/ui
- **Features**: User management, content moderation, analytics

### Backend Services

#### Platform Service (Node.js/TypeScript)
- **Purpose**: Core API gateway and business logic
- **Tech**: Fastify, TypeScript, Prisma, Redis
- **Modules**:
  - User Management
  - Content Generation
  - AI Integration
  - Assessment Engine
  - Learning Analytics
  - Payments
  - Collaboration

#### Payment Service (Node.js/TypeScript)
- **Purpose**: Payment processing and subscription management
- **Tech**: Fastify, TypeScript, Stripe
- **Features**: Stripe integration, subscription management, invoicing

#### AI Agents Service (Node.js/TypeScript)
- **Purpose**: AI agent orchestration and execution
- **Tech**: Node.js, TypeScript, LangChain
- **Features**: Agent runtime, tool integration, prompt management

### Platform Services (Java)

#### Kernel Core
- **Purpose**: Core platform infrastructure
- **Tech**: Java 21, ActiveJ
- **Features**: Event processing, plugin system, kernel services

#### Database Service
- **Purpose**: Database abstraction and connection pooling
- **Tech**: Java 21, ActiveJ, PostgreSQL
- **Features**: Connection pooling, query optimization, read replicas

#### HTTP Service
- **Purpose**: HTTP client and server infrastructure
- **Tech**: Java 21, ActiveJ HTTP
- **Features**: HTTP client, server, load balancing

### Shared Libraries

#### Tutorputor Core
- **Purpose**: Shared database models and types
- **Tech**: TypeScript, Prisma
- **Contents**: Prisma schema, shared types, utilities

## Data Flow

### Learning Flow

```
User Request
    ↓
Web App (React)
    ↓
Platform API (Fastify)
    ↓
Content Generation Service
    ↓
AI Service (OpenAI/Custom)
    ↓
Content Cache (Redis)
    ↓
Database (PostgreSQL)
    ↓
Response to User
```

### Payment Flow

```
User Initiates Payment
    ↓
Payment Service
    ↓
Stripe API
    ↓
Webhook (Stripe → Payment Service)
    ↓
Database Update
    ↓
Notification Service
    ↓
User Notification
```

## Technology Stack

### Frontend
- **Framework**: React 18+
- **Build Tool**: Vite
- **Styling**: TailwindCSS, shadcn/ui
- **State Management**: React Context, Zustand
- **HTTP Client**: Axios, Fetch API
- **Testing**: Vitest, Playwright

### Backend
- **Runtime**: Node.js 18+
- **Framework**: Fastify
- **Language**: TypeScript
- **ORM**: Prisma
- **Database**: PostgreSQL
- **Cache**: Redis
- **Queue**: BullMQ
- **Monitoring**: Sentry, Prometheus

### Platform (Java)
- **Runtime**: Java 21
- **Framework**: ActiveJ
- **Build**: Gradle
- **Database**: PostgreSQL
- **Testing**: JUnit 5

### Infrastructure
- **Container**: Docker
- **Orchestration**: Kubernetes
- **CI/CD**: GitHub Actions
- **Monitoring**: Grafana, Prometheus, Loki
- **Logging**: ELK Stack

## Deployment Architecture

### Development Environment

```
Local Development
├── Web App (localhost:5173)
├── Platform Service (localhost:3000)
├── PostgreSQL (localhost:5432)
└── Redis (localhost:6379)
```

### Staging Environment

```
Kubernetes Cluster
├── Web App Deployment (3 replicas)
├── Platform Service Deployment (3 replicas)
├── PostgreSQL StatefulSet (1 primary, 2 replicas)
├── Redis Deployment (3 replicas)
└── Monitoring Stack
```

### Production Environment

```
Multi-Region Kubernetes
├── Region 1 (US-East)
│   ├── Web App (5 replicas)
│   ├── Platform Service (5 replicas)
│   ├── PostgreSQL (Primary + 2 replicas)
│   └── Redis Cluster (6 nodes)
├── Region 2 (US-West)
│   └── Same as Region 1
└── CDN (Cloudflare)
```

## Security Architecture

### Authentication & Authorization

- **Authentication**: JWT-based authentication with refresh tokens
- **Authorization**: Role-Based Access Control (RBAC)
- **Tenant Isolation**: Multi-tenant with data segregation
- **API Security**: API keys, rate limiting, request signing

### Data Protection

- **Encryption at Rest**: Database encryption, TLS for all connections
- **Encryption in Transit**: TLS 1.3 for all network communication
- **PII Protection**: Data masking, access logging, audit trails
- **Compliance**: FERPA, COPPA, GDPR compliant

### Network Security

- **WAF**: Web Application Firewall
- **DDoS Protection**: Cloudflare, rate limiting
- **Network Segmentation**: VPC isolation, security groups
- **Secrets Management**: HashiCorp Vault, AWS Secrets Manager

## Scalability Considerations

### Horizontal Scaling

- **Stateless Services**: Platform service can scale horizontally
- **Database**: Read replicas for read-heavy workloads
- **Cache**: Redis cluster for distributed caching
- **Queue**: BullMQ with multiple workers

### Vertical Scaling

- **Database**: Connection pooling, query optimization
- **Cache**: Memory optimization, eviction policies
- **Application**: CPU profiling, memory optimization

### Performance Optimization

- **Caching**: Multi-level caching (Redis, in-memory, HTTP)
- **Database**: Indexing, query optimization, connection pooling
- **Async Processing**: Message queues for background jobs
- **CDN**: Static asset delivery, API caching

### Monitoring & Observability

- **Metrics**: Prometheus, Grafana dashboards
- **Logging**: Structured logging, log aggregation
- **Tracing**: Distributed tracing (OpenTelemetry)
- **Alerting**: Real-time alerts, anomaly detection

## Key Design Decisions

### Why Microservices?

- **Independence**: Services can be deployed and scaled independently
- **Technology Diversity**: Different services can use different technologies
- **Team Autonomy**: Teams can own and operate their services
- **Resilience**: Failure isolation, graceful degradation

### Why Event-Driven?

- **Loose Coupling**: Services communicate through events
- **Scalability**: Asynchronous processing, event replay
- **Flexibility**: Easy to add new consumers
- **Audit Trail**: Event log provides audit trail

### Why Multi-Tenant?

- **Cost Efficiency**: Shared infrastructure
- **Isolation**: Data and configuration isolation per tenant
- **Customization**: Tenant-specific configurations
- **Compliance**: Easier to implement tenant-specific compliance

## Future Architecture Evolution

### Short Term (Next 6 Months)

- Implement service mesh for better service-to-service communication
- Add circuit breakers and retry logic for resilience
- Implement API gateway for centralized routing and authentication
- Add real-time analytics pipeline

### Long Term (Next 12-18 Months)

- Event sourcing for critical events
- CQRS for complex read/write patterns
- GraphQL API for flexible data fetching
- Serverless components for bursty workloads

## References

- [Detailed Architecture Documentation](docs/ARCHITECTURE.md)
- [Agent System Architecture](docs/agent-system/ARCHITECTURE.md)
- [API Documentation](docs/api/README.md)
- [Deployment Guide](DEPLOYMENT_GUIDE.md)
