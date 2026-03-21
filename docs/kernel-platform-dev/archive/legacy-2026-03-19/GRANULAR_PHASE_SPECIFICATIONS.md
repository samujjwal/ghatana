# Granular Phase Specifications - Kernel Implementation

**Focus**: Detailed Features, Design, and Implementation for Each Phase  
**Version**: 3.1 — AI/ML-Native, Full Lifecycle, Opinionated Core  
**Date**: March 18, 2026  
**Companion Documents**:

- [KERNEL_PLATFORM_BRAINSTORM.md](KERNEL_PLATFORM_BRAINSTORM.md) — Strategic Vision & Architecture
- [DETAILED_KERNEL_IMPLEMENTATION_PLAN.md](DETAILED_KERNEL_IMPLEMENTATION_PLAN.md) — PHR + Finance Integration Roadmap

---

## Executive Summary

This document defines the comprehensive implementation plan for the **Ghatana Kernel Platform** — an **AI/ML-native, opinionated, full-lifecycle platform** that serves as the cornerstone of application development, deployment, monitoring, and management. The kernel provides an **easy-to-use, powerful, flexible, and customizable platform** where intelligence is embedded at every layer, infrastructure is completely abstracted, and products focus exclusively on business logic, workflow definitions, process definitions, UI/UX design, and user flow/journey design.

The kernel enforces proven technology decisions (Java 21 + ActiveJ, Data-Cloud, AEP, GAA agent framework) while providing controlled flexibility at product boundaries. Every application built on the kernel inherits AI-powered observability, adaptive behavior, and enterprise-grade security — without writing a single line of infrastructure code.

### What is the Ghatana Kernel Platform?

The **Ghatana Kernel Platform** is an AI/ML-native foundational abstraction layer that sits between core infrastructure services and product-specific applications. It provides:

- **AI/ML-Native Intelligence**: AI capabilities embedded implicitly in every subsystem — anomaly detection, adaptive rate limiting, predictive scaling, intelligent observability — all always-on with zero product configuration
- **Easy-to-Use Development Surface**: Simple, intuitive APIs and patterns for rapid application development
- **Powerful Composition Capabilities**: Rich set of building blocks for complex application assembly — including the GAA agent framework (PERCEIVE → REASON → ACT → CAPTURE → REFLECT), AEP operator pipelines, and workflow orchestration
- **Full Lifecycle Ownership**: The kernel owns DEVELOP → TEST → BUILD → DEPLOY → MONITOR → MANAGE — products declare intent, the kernel executes every stage
- **Opinionated Core with Flexible Edges**: Non-negotiable technology decisions in the core (ActiveJ, Data-Cloud, AEP, Protobuf) with controlled flexibility for domain-specific choices (ML providers, external integrations)
- **Complete Infrastructure Abstraction**: Hides complexity of underlying services, infrastructure, and processes — Docker, Kubernetes, databases, message brokers, monitoring stacks are invisible to product teams
- **Product Layer Purity**: Products own ONLY business logic, workflow definitions, process definitions, UI/UX design, and user flow/journey — nothing else

### Why Does the Kernel Exist?

1. **Simplify Development**: Provide easy-to-use patterns that accelerate product development — new products go from code to production in minutes
2. **Abstract Complexity**: Hide infrastructure complexity while maintaining power and flexibility
3. **Enable Rapid Composition**: Assemble applications quickly from pre-built capabilities
4. **Controlled Extensibility**: Allow customization without compromising kernel integrity
5. **Ensure Product Focus**: Products own business logic, workflow definitions, process definitions, UI/UX design, and user flow/journey — the kernel handles everything else
6. **Embed Intelligence Everywhere**: AI/ML capabilities are infrastructure, not features — every product inherits anomaly detection, adaptive behavior, and intelligent operations
7. **Own the Full Lifecycle**: From development scaffolding to production monitoring — the kernel is the cornerstone of the entire application lifecycle with complete visibility
8. **Enforce Proven Opinions**: Reduce decision fatigue by committing to proven technologies (ActiveJ, Data-Cloud, AEP) — deep optimization over superficial abstraction

### Kernel Architecture Vision

```
┌─────────────────────────────────────────────────────────────┐
│                    Product Applications                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │   FlashIt   │  │    Aura     │  │     PHR     │         │
│  │Personal AI  │  │Recommend   │  │Healthcare   │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
│  ┌─────────────┐                                               │
│  │   Finance   │                                               │
│  │   Product   │                                               │
│  │   (Former   │                                               │
│  │ App-Platform│                                               │
│  │   Specific) │                                               │
│  └─────────────┘                                               │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼ Simple, Powerful APIs
┌─────────────────────────────────────────────────────────────┐
│                 Ghatana Kernel Platform                     │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │           Minimum Viable Kernel Capabilities             │ │
│  │                                                         │ │
│  │  • UI/UX Development & Interaction Framework           │ │
│  │  • API & Business Logic Development Framework          │ │
│  │  • Core Product Requirements (Security, Resilience,    │ │
│  │    Scalability, Performance)                          │ │
│  │                                                         │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │ │
│  │  │   Kernel    │  │   Plugin    │  │  Workflow   │     │ │
│  │  │  Primitives │  │   Runtime   │  │   Runtime   │     │ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘     │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │ │
│  │  │   Event     │  │   Config    │  │   Tenant    │     │ │
│  │  │   Store     │  │  Resolver   │  │  Context    │     │ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘     │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                             │
│  • Controlled Extensibility (well-defined extension points) │
│  • Flexible Customization (within kernel boundaries)       │
│  • No Non-Generic Features (pure abstraction layer)        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼ Complete Abstraction
┌─────────────────────────────────────────────────────────────┐
│                Core Infrastructure Services                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ Data-Cloud  │  │     AEP     │  │ Shared Libs │         │
│  │   (Data)    │  │(Processing) │  │ (From App-  │         │
│  │             │  │             │  │ Platform)  │         │
│  │• Storage    │  │• Events     │  │• Auth       │         │
│  │• Config     │  │• Agents     │  │• Common    │         │
│  │• Governance │  │• Workflows  │  │  Utils     │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
│                                                             │
│  • Infrastructure (Docker, K8s, Networks)                  │
│  • Processes (CI/CD, Monitoring, Logging)                   │
│  • External Systems (Databases, APIs, Services)             │
└─────────────────────────────────────────────────────────────┘
```

### Architecture Evolution: App-Platform Separation

#### What is App-Platform Separation?

The **App-Platform Separation** is a strategic architectural decision to split the existing app-platform into two distinct components:

1. **Finance Product Application**: Product-specific functionality that becomes a standalone product
2. **Shared Libraries**: Generic, reusable components moved to core infrastructure

#### Why App-Platform Separation Matters?

1. **Clear Product Boundaries**: Finance becomes a proper product with clear domain ownership
2. **Reusable Infrastructure**: Shared components become available to all products
3. **Reduced Complexity**: Cleaner separation of concerns and dependencies
4. **Better Scalability**: Independent scaling of products and shared infrastructure
5. **Improved Maintainability**: Easier to maintain and evolve shared components

#### App-Platform Separation Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                Before Separation                            │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              App-Platform (Monolithic)               │   │
│  │                                                     │   │
│  │  • Finance Logic (Product-Specific)                 │   │
│  │  • Authentication (Shared)                          │   │
│  │  • Common Utilities (Shared)                        │   │
│  │  • Runtime Services (Shared)                        │   │
│  │  • Legacy Components (Mixed)                         │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼ Separation
┌─────────────────────────────────────────────────────────────┐
│                After Separation                             │
│                                                             │
│  ┌─────────────────┐      ┌─────────────────────────────┐   │
│  │  Finance Product│      │      Shared Libraries       │   │
│  │                 │      │                             │   │
│  │ • Trading Logic │      │ • Authentication Library     │   │
│  │ • Risk Mgmt     │      │ • Common Utilities           │   │
│  │ • Compliance    │      │ • Runtime Services            │   │
│  │ • Domain APIs   │      │ • Generic Components         │   │
│  └─────────────────┘      │ • Integration Frameworks      │   │
│                            │ • Legacy Refactors            │   │
│                            └─────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

#### Migration Strategy

##### Phase 1: Analysis and Identification

- **Component Analysis**: Identify shared vs product-specific components
- **Dependency Mapping**: Map dependencies between components
- **Interface Definition**: Define clear interfaces for shared libraries
- **Migration Planning**: Create detailed migration roadmap

##### Phase 2: Shared Libraries Extraction

- **Library Creation**: Create shared libraries from generic components
- **Interface Implementation**: Implement defined interfaces
- **Testing**: Comprehensive testing of shared libraries
- **Documentation**: Complete documentation for shared libraries

##### Phase 3: Finance Product Separation

- **Product Extraction**: Extract finance-specific logic into standalone product
- **Dependency Refactoring**: Refactor dependencies to use shared libraries
- **API Definition**: Define finance product APIs
- **Integration**: Integrate with kernel platform

##### Phase 4: Migration and Validation

- **Gradual Migration**: Migrate existing functionality to new architecture
- **Validation**: Validate functionality and performance
- **Monitoring**: Monitor migration progress and issues
- **Optimization**: Optimize performance and usage

#### Shared Libraries Categories

##### 1. Authentication and Authorization

- **What It Is**: Generic authentication and authorization library
- **Why Shared**: All products need authentication capabilities
- **Components**: JWT handling, OAuth integration, RBAC framework
- **Migration Path**: Extract into shared library with kernel integration

##### 2. Common Utilities

- **What It Is**: Generic utility functions and helpers
- **Why Shared**: Reusable across all products
- **Components**: String utilities, date helpers, validation frameworks
- **Migration Path**: Package as shared library with versioning

##### 3. Runtime Services

- **What It Is**: Generic runtime and infrastructure services
- **Why Shared**: Common runtime requirements across products
- **Components**: Service discovery, configuration management, health checks
- **Migration Path**: Integrate with kernel runtime services

##### 4. Integration Frameworks

- **What It Is**: Generic integration and communication frameworks
- **Why Shared**: Standardized integration patterns
- **Components**: HTTP clients, message brokers, API gateways
- **Migration Path**: Enhance and move to core infrastructure

#### Benefits of Separation

##### 1. Product Clarity

- **Clear Ownership**: Finance product has clear domain ownership
- **Focused Development**: Product teams focus on product-specific features
- **Independent Roadmap**: Independent product roadmap and priorities

##### 2. Infrastructure Efficiency

- **Shared Resources**: Shared libraries reduce duplication
- **Consistent Patterns**: Standardized patterns across products
- **Easier Maintenance**: Single point of maintenance for shared components

##### 3. Scalability and Performance

- **Independent Scaling**: Products scale independently
- **Resource Optimization**: Optimized resource allocation
- **Performance Isolation**: Performance issues isolated to specific products

##### 4. Development Velocity

- **Parallel Development**: Teams can develop in parallel
- **Reduced Dependencies**: Fewer cross-team dependencies
- **Faster Iteration**: Faster iteration cycles for products

---

### Kernel Implementation Principles

The kernel implementation adheres to the following non-negotiable principles (see also [KERNEL_PLATFORM_BRAINSTORM.md §Kernel Positioning Principles](KERNEL_PLATFORM_BRAINSTORM.md)):

1. **Easy-to-Use Development Surface**: Simple, intuitive APIs that hide complexity while maintaining power
2. **Complete Infrastructure Abstraction**: Hide all underlying services, infrastructure, and processes
3. **Controlled Extensibility**: Extensible only through well-defined, controlled mechanisms
4. **No Non-Generic Features**: Keep kernel pure - no product-specific features
5. **Minimum Viable Capabilities**: Start with essential capabilities that all products need
6. **AI/ML-Native Intelligence**: AI is infrastructure, not a feature — implicit in every kernel operation
7. **Product Layer Purity**: Products declare business logic, workflows, processes, UI/UX, and user journeys — kernel handles everything else
8. **Opinionated Technology Core**: Non-negotiable decisions (ActiveJ, Data-Cloud, AEP, Protobuf) ensure consistency and deep optimization
9. **Full Lifecycle Ownership**: DEVELOP → TEST → BUILD → DEPLOY → MONITOR → MANAGE — the kernel owns every stage

### Kernel Positioning Framework

#### 1. **Easy-to-Use Development Surface**

The kernel provides simple, intuitive interfaces that hide complexity while maintaining power:

```
┌─────────────────────────────────────────────────────────────┐
│                 Developer Experience                        │
│                                                             │
│  Simple API Calls → Powerful Kernel Capabilities           │
│                                                             │
│  // Create a new user interface                           │
│  const userForm = kernel.ui.createForm({                   │
│    fields: ['name', 'email', 'password'],                 │
│    validation: 'email-format',                            │
│    onSubmit: 'saveUser'                                   │
│  });                                                      │
│                                                             │
│  // Create a business API                                 │
│  const userAPI = kernel.api.create({                        │
│    endpoint: '/users',                                    │
│    methods: ['GET', 'POST', 'PUT', 'DELETE'],            │
│    validation: 'user-schema',                             │
│    security: 'authenticated'                              │
│  });                                                      │
│                                                             │
│  // Create a business workflow                            │
│  const onboarding = kernel.workflow.create({               │
│    name: 'user-onboarding',                               │
│    steps: ['verify-email', 'create-profile', 'send-welcome'],│
│    triggers: ['user-registration']                        │
│  });                                                      │
└─────────────────────────────────────────────────────────────┘
```

#### 2. **Complete Infrastructure Abstraction**

The kernel completely abstracts underlying complexity:

```
┌─────────────────────────────────────────────────────────────┐
│               Infrastructure Abstraction                     │
│                                                             │
│  Developer Code → Kernel Abstraction → Infrastructure       │
│                                                             │
│  // Developer doesn't need to know about:                  │
│  // - Docker containers                                     │
│  // - Kubernetes clusters                                   │
│  // - Database types (PostgreSQL, Redis, etc.)             │
│  // - Message brokers (Kafka, etc.)                        │
│  // - Cloud providers (AWS, GCP, Azure)                   │
│  // - Network configuration                                │
│  // - Storage systems (S3, etc.)                           │
│                                                             │
│  // Kernel handles everything:                             │
│  kernel.data.save('user', userData);  // → Any database     │
│  kernel.cache.set('session', data);   // → Any cache       │
│  kernel.queue.publish('event', data); // → Any broker     │
│  kernel.storage.upload('file', data); // → Any storage    │
└─────────────────────────────────────────────────────────────┘
```

#### 3. **Controlled Extensibility**

The kernel is extensible only through well-defined, controlled mechanisms:

```
┌─────────────────────────────────────────────────────────────┐
│                Controlled Extensibility                      │
│                                                             │
│  Allowed Extension Points:                                  │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │     Plugins     │  │    Operators    │  │  Workflows  │ │
│  │                 │  │                 │  │             │ │
│  │• UI Components  │  │• Data Transform  │  │• Business   │ │
│  │• Auth Providers │  │• Validation      │  │  Processes  │ │
│  │• Notifiers     │  │• Enrichment      │  │• Automation │ │
│  │• Integrations   │  │• Integration     │  │• Orchestration│ │
│  └─────────────────┘  └─────────────────┘  └─────────────┘ │
│                                                             │
│  Control Mechanisms:                                        │
│  • Interface Contracts (strict definitions)                 │
│  • Validation (security and compatibility)                  │
│  • Sandboxing (isolated execution)                         │
│  • Governance (lifecycle management)                       │
│                                                             │
│  Forbidden Extensions:                                      │
│  • Direct kernel modification                               │
│  • Bypassing kernel controls                               │
│  • Direct infrastructure access                            │
│  • Non-generic product features                            │
└─────────────────────────────────────────────────────────────┘
```

#### 4. **Minimum Viable Kernel Capabilities**

The kernel starts with a focused set of essential capabilities:

```
┌─────────────────────────────────────────────────────────────┐
│            Minimum Viable Kernel Capabilities                │
│                                                             │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              UI/UX Framework                             │ │
│  │                                                         │ │
│  │  • Component Library (buttons, forms, tables, etc.)     │ │
│  │  • Design System (colors, typography, spacing)          │ │
│  │  • State Management (application state)                 │ │
│  │  • Form Handling (validation, submission)              │ │
│  │  • Navigation (routing, menus, breadcrumbs)             │ │
│  │  • Responsive Design (mobile-first)                     │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │          API & Business Logic Framework                  │ │
│  │                                                         │ │
│  │  • API Generation (REST/GraphQL)                        │ │
│  │  • Validation Engine (request/response)                 │ │
│  │  • Business Rules (declarative)                         │ │
│  │  • Workflow Engine (process automation)                 │ │
│  │  • Event System (publish/subscribe)                      │ │
│  │  • Data Access (CRUD operations)                        │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │            Core Product Requirements                      │ │
│  │                                                         │ │
│  │  • Security (auth, authz, encryption)                   │ │
│  │  • Resilience (fault tolerance, recovery)              │ │
│  │  • Scalability (horizontal scaling)                    │ │
│  │  • Performance (caching, optimization)                 │ │
│  │  • Observability (logging, metrics, tracing)           │ │
│  │  • Compliance (audit trails, governance)                │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## Kernel Interaction Flow

### Observability Capability Matrix

```
┌─────────────────────────────────────────────────────────────┐
│                Observability Capability Matrix               │
│                                                             │
│  Capability               | FlashIt   | Aura   | PHR   | Finance│
│  ------------------------|-----------|--------|-------|---------│
│  User Analytics          | ✓✓✓       | ✓✓     | ✓✓✓   | ✓✓     │
│  Performance Monitoring   | ✓✓✓       | ✓✓✓    | ✓✓✓   | ✓✓✓    │
│  Error Tracking          | ✓✓✓       | ✓✓     | ✓✓✓   | ✓✓✓    │
│  Business Intelligence    | ✓✓        | ✓✓✓    | ✓✓     | ✓✓✓    │
│  Compliance Monitoring    | ✓         | ✓      | ✓✓✓   | ✓✓✓    │
│  Cost Observability       | ✓✓        | ✓✓     | ✓✓     | ✓✓✓    │
│  Security Observability   | ✓✓        | ✓✓     | ✓✓✓   | ✓✓✓    │
│  Synthetic Monitoring     | ✓✓        | ✓✓     | ✓✓     | ✓✓✓    │
│  Anomaly Detection        | ✓✓        | ✓✓✓    | ✓✓     | ✓✓✓    │
│  AI Operations            | ✓✓        | ✓✓✓    | ✓✓     | ✓✓✓    │
│  Trading Analytics        | ✓         | ✓      | ✓      | ✓✓✓    │
│  Risk Monitoring         | ✓         | ✓      | ✓      | ✓✓✓    │
│  Financial Compliance     | ✓         | ✓      | ✓      | ✓✓✓    │
│                                                             │
│  Legend: ✓✓✓ = Critical, ✓✓ = Important, ✓ = Nice-to-have   │
└─────────────────────────────────────────────────────────────┘
```

### Developer Experience Journey

```
┌─────────────────────────────────────────────────────────────┐
│                    Developer Workflow                       │
│                                                             │
│  1. Product Definition                                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Product: FlashIt (Personal AI Platform)             │   │
│  │  Features:                                           │   │
│  │  • Moment capture UI                                 │   │
│  │  • Reflection APIs                                    │   │
│  │  • AI processing workflows                            │   │
│  │  • User management                                   │   │
│  └─────────────────────────────────────────────────────┘   │
│                              │                              │
│                              ▼                              │
│  2. Kernel Capability Selection                           │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Available Kernel Capabilities:                     │   │
│  │  • UI/UX Framework (forms, components, state)      │   │
│  │  • API Framework (REST, validation, auth)          │   │
│  │  • Workflow Engine (AI processing, automation)      │   │
│  │  • Security Framework (auth, encryption, audit)     │   │
│  │  • Data Management (storage, caching, events)      │   │
│  └─────────────────────────────────────────────────────┘   │
│                              │                              │
│                              ▼                              │
│  3. Product Implementation                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  // FlashIt Implementation using Kernel              │   │
│  │  const flashit = kernel.product.create({             │   │
│  │    name: 'FlashIt',                                  │   │
│  │    capabilities: ['ui', 'api', 'workflows', 'ai']   │   │
│  │  });                                                 │   │
│  │                                                       │   │
│  │  // UI Components                                     │   │
│  │  const momentForm = flashit.ui.form({               │   │
│  │    fields: ['text', 'image', 'audio', 'video'],     │   │
│  │    validation: 'multimedia-input'                   │   │
│  │  });                                                 │   │
│  │                                                       │   │
│  │  // API Endpoints                                    │   │
│  │  const momentsAPI = flashit.api.rest({              │   │
│  │    endpoint: '/moments',                             │   │
│  │    methods: ['GET', 'POST', 'PUT', 'DELETE'],      │   │
│  │    auth: 'required',                                │   │
│  │    validation: 'moment-schema'                       │   │
│  │  });                                                 │   │
│  │                                                       │   │
│  │  // AI Processing Workflows                          │   │
│  │  const aiWorkflow = flashit.workflow.create({        │   │
│  │    name: 'moment-processing',                       │   │
│  │    steps: [                                          │   │
│  │      'classify-content',                             │   │
│  │      'extract-insights',                             │   │
│  │      'generate-reflection',                         │   │
│  │      'store-results'                                 │   │
│  │    ],                                                 │   │
│  │    triggers: ['moment-created']                      │   │
│  │  });                                                 │   │
│  └─────────────────────────────────────────────────────┘   │
│                              │                              │
│                              ▼                              │
│  4. Kernel Abstraction Layer                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Kernel Handles All Complexity:                      │   │
│  │  • UI Rendering (responsive, accessible)             │   │
│  │  • API Routing (REST, GraphQL, WebSocket)            │   │
│  │  • Workflow Orchestration (durable, retryable)      │   │
│  │  • Security (auth, authz, encryption)               │   │
│  │  • Data Storage (PostgreSQL, Redis, S3)              │   │
│  │  • Event Processing (Kafka, Event Sourcing)          │   │
│  │  • Monitoring (metrics, logs, traces)               │   │
│  │  • Scaling (horizontal, load balancing)             │   │
│  └─────────────────────────────────────────────────────┘   │
│                              │                              │
│                              ▼                              │
│  5. Infrastructure Execution                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Automatic Infrastructure Management:                │   │
│  │  • Container Orchestration (Kubernetes)              │   │
│  │  • Database Management (PostgreSQL, Redis)           │   │
│  │  • Message Brokers (Kafka)                           │   │
│  │  • Object Storage (S3, MinIO)                        │   │
│  │  • CDN & Caching (CloudFront, Redis)                 │   │
│  │  • Monitoring Stack (Prometheus, Grafana)             │   │
│  │  • Logging Stack (ELK, Fluentd)                       │   │
│  │  • Security (WAF, DDoS, SSL)                         │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Kernel Capability Mapping

```
┌─────────────────────────────────────────────────────────────┐
│                Kernel Capability Mapping                     │
│                                                             │
│  Product Requirements    →    Kernel Capabilities           │
│                                                             │
│  ┌─────────────────┐      ┌─────────────────────────────┐ │
│  │  FlashIt Needs  │      │     Kernel Provides         │ │
│  │                 │      │                             │ │
│  │ • UI Forms      │─────▶│ • Form Builder              │ │
│  │ • File Upload   │      │ • File Upload Component     │ │
│  │ • AI Processing │      │ • AI Workflow Engine        │ │
│  │ • User Auth      │      │ • Authentication Service    │ │
│  │ • Data Storage   │      │ • Data Storage Abstraction  │ │
│  │ • Search         │      │ • Search Framework          │ │
│  └─────────────────┘      └─────────────────────────────┘ │
│                                                             │
│  ┌─────────────────┐      ┌─────────────────────────────┐ │
│  │   Aura Needs    │      │     Kernel Provides         │ │
│  │                 │      │                             │ │
│  │ • Recommendation │─────▶│ • Recommendation Engine      │ │
│  │ • Ontology      │      │ • Knowledge Graph Framework  │ │
│  │ • Analytics      │      │ • Analytics Framework       │ │
│  │ • Visualization  │      │ • Chart/Graph Components     │ │
│  │ • API Gateway    │      │ • API Gateway Service       │ │
│  └─────────────────┘      └─────────────────────────────┘ │
│                                                             │
│  ┌─────────────────┐      ┌─────────────────────────────┐ │
│  │    PHR Needs    │      │     Kernel Provides         │ │
│  │                 │      │                             │ │
│  │ • Patient Forms │─────▶│ • Medical Form Builder      │ │
│  │ • Consent Mgmt  │      │ • Consent Framework         │ │
│  │ • HIPAA Security│      │ • Healthcare Security        │ │
│  │ • Audit Trails  │      │ • Audit Trail Service       │ │
│  │ • FHIR Integration│     │ • FHIR Integration Framework│ │
│  └─────────────────┘      └─────────────────────────────┘ │
│                                                             │
│  ┌─────────────────┐      ┌─────────────────────────────┐ │
│  │   Finance Needs │      │     Kernel Provides         │ │
│  │                 │      │                             │ │
│  │ • Trading APIs  │─────▶│ • API Framework             │ │
│  │ • Risk Mgmt     │      │ • Risk Management Framework  │ │
│  │ • Compliance    │      │ • Compliance Framework       │ │
│  │ • Real-time Data│      │ • Event Streaming Framework  │ │
│  │ • Analytics     │      │ • Analytics Framework       │ │
│  └─────────────────┘      └─────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## AI/ML-Native Implementation Strategy

### What is AI/ML-Native in This Context?

**AI/ML-Native** means that intelligence is not bolted on after the fact — it is a foundational primitive embedded in every kernel subsystem. Every kernel operation is either AI-enhanced or AI-enhanceable. Products do not "integrate with AI"; they inherit AI capabilities by building on the kernel.

### Why AI/ML-Native Matters for Implementation

1. **Phase 0 Impact**: Current state assessment must catalog existing AI capabilities and identify AI-enhancement opportunities in every kernel module
2. **Phase 1 Impact**: Core kernel primitives must include AI-ready hooks — KernelDescriptor supports AI_ML capability type, KernelModule interface includes AI health monitoring
3. **Phase 2+ Impact**: Every product integration (PHR, Finance) must leverage AI-powered observability, adaptive behavior, and intelligent automation from day one

### AI/ML Subsystem Implementation Phases

```
┌─────────────────────────────────────────────────────────────┐
│            AI/ML Implementation Across Phases               │
│                                                             │
│  Phase 0: ASSESS AI READINESS                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  • Catalog platform/java/ai-integration/ modules    │   │
│  │  • Map LLM Gateway, Feature Store, Model Registry   │   │
│  │  • Assess agent-framework, agent-memory, agent-     │   │
│  │    learning maturity                                │   │
│  │  • Identify AI gaps per product (PHR, Finance)      │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  Phase 1: AI-READY KERNEL PRIMITIVES                       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  • KernelDescriptor.CapabilityType.AI_ML            │   │
│  │  • AI health checks in KernelModule lifecycle       │   │
│  │  • LLM Gateway kernel adapter (KernelLLMGateway)    │   │
│  │  • Feature Store kernel adapter (KernelFeatureStore)│   │
│  │  • Agent Runtime kernel adapter (KernelAgentRuntime)│   │
│  │  • AI cost tracking in KernelTenantContext           │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  Phase 2+: PRODUCT-SPECIFIC AI INTEGRATION                 │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  PHR: Clinical NLP, consent-aware AI, OCR/ASR agents│   │
│  │  Finance: Risk models, surveillance agents, trading │   │
│  │  FlashIt: Reflection agents, moment embeddings      │   │
│  │  Aura: Ranking models, recommendation engine        │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  Always-On (Implicit, All Phases):                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  • Anomaly detection on all metrics streams         │   │
│  │  • Adaptive rate limiting on all API endpoints      │   │
│  │  • Predictive autoscaling for all services          │   │
│  │  • Intelligent log correlation and root cause       │   │
│  │  • Performance regression detection on all builds   │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Product Layer Contract for Implementation

Each phase implementation must verify that the product layer contract is maintained:

```
┌─────────────────────────────────────────────────────────────┐
│       Product Layer Contract — Implementation Checklist     │
│                                                             │
│  For every module implemented in any phase, verify:         │
│                                                             │
│  ✅ Product code ONLY contains:                             │
│     □ Domain models, value objects, events, commands        │
│     □ Business rules and domain validation                  │
│     □ Workflow definitions (steps, triggers, compensation)  │
│     □ Process definitions (approvals, SLAs, escalation)     │
│     □ UI/UX design (shells, tokens, themes, layouts)        │
│     □ User flow/journey (onboarding, funnels, error flows)  │
│     □ Domain configuration (feature flags, agent defs)      │
│                                                             │
│  ❌ Product code NEVER contains:                            │
│     □ Database connection code or SQL                        │
│     □ Docker/K8s/Helm configuration                         │
│     □ Monitoring/alerting setup                              │
│     □ Authentication/authorization implementation           │
│     □ Direct LLM provider calls                              │
│     □ Infrastructure scaling logic                           │
│     □ Deployment scripts or CI/CD pipeline code              │
│                                                             │
│  Reference: KERNEL_PLATFORM_BRAINSTORM.md §Product Layer    │
│  Contract                                                   │
└─────────────────────────────────────────────────────────────┘
```

### Opinionated Core — Implementation Constraints

Every phase must enforce these non-negotiable technology decisions:

| Decision         | Technology                  | Enforcement Mechanism                                      |
| ---------------- | --------------------------- | ---------------------------------------------------------- |
| Async Runtime    | ActiveJ Eventloop + Promise | Build-time check: reject CompletableFuture/Reactor imports |
| Event Store      | Data-Cloud EventLogStore    | Dependency governance: block direct DB access in products  |
| Event Processing | AEP UnifiedOperator         | Architecture tests: verify operator catalog usage          |
| Agent Framework  | GAA lifecycle               | Contract tests: verify PERCEIVE→REFLECT pipeline           |
| Tracing          | OpenTelemetry               | Auto-instrumentation: no product opt-in needed             |
| Metrics          | Micrometer + Prometheus     | Auto-registration: kernel exposes metrics for all modules  |
| Contracts        | Protobuf + gRPC             | Schema registry validation: contract tests in CI           |
| Testing          | EventloopTestBase           | Build plugin: fail build if async tests don't extend base  |

---

## Expanded Risk Assessment — Implementation-Specific

### What is the Implementation Risk Assessment?

This section extends the risk registry from the [Brainstorm document](KERNEL_PLATFORM_BRAINSTORM.md) with implementation-phase-specific risks, triggers, and mitigation actions.

### Why Implementation Risks Differ from Architectural Risks

Architectural risks are about the design being wrong. Implementation risks are about the execution going wrong — the right design being built incorrectly, too slowly, or with unintended side effects.

### Phase-Specific Risk Matrix

```
┌─────────────────────────────────────────────────────────────┐
│           Implementation Risk Matrix by Phase               │
│                                                             │
│  PHASE 0 RISKS                                             │
│  ID    │ Risk                       │ Impact │ Prob │ Score │
│  P0-01 │ Incomplete asset inventory │ MED    │ HIGH │ 🔴    │
│  P0-02 │ Underestimated tech debt   │ HIGH   │ MED  │ 🔴    │
│  P0-03 │ Hidden dependencies        │ MED    │ MED  │ 🟡    │
│                                                             │
│  PHASE 1 RISKS                                             │
│  P1-01 │ Descriptor model too rigid │ HIGH   │ MED  │ 🔴    │
│  P1-02 │ ActiveJ Promise misuse     │ HIGH   │ HIGH │ 🔴    │
│  P1-03 │ Data-Cloud adapter perf    │ MED    │ MED  │ 🟡    │
│  P1-04 │ Registry memory growth     │ MED    │ LOW  │ 🟢    │
│                                                             │
│  PHASE 2 RISKS                                             │
│  P2-01 │ PHR consent complexity     │ HIGH   │ MED  │ 🔴    │
│  P2-02 │ Healthcare data migration  │ HIGH   │ MED  │ 🔴    │
│  P2-03 │ FHIR compliance gaps       │ MED    │ MED  │ 🟡    │
│                                                             │
│  CROSS-PHASE RISKS                                         │
│  X-01  │ AI cost overrun            │ MED    │ HIGH │ 🔴    │
│  X-02  │ Agent memory growth        │ MED    │ MED  │ 🟡    │
│  X-03  │ Eventloop stall in prod    │ HIGH   │ MED  │ 🔴    │
│  X-04  │ Knowledge concentration    │ MED    │ HIGH │ 🔴    │
│                                                             │
│  Legend: 🔴 Critical  🟡 Significant  🟢 Manageable        │
└─────────────────────────────────────────────────────────────┘
```

### Critical Implementation Risks and Mitigations

#### P1-02: ActiveJ Promise Misuse

**What**: Developers accidentally block the eventloop with synchronous calls inside Promise chains.
**Why It's Critical**: One blocking call stalls ALL requests on that eventloop — cascading latency across the entire service.
**Triggers**: `Thread.sleep()`, synchronous IO, `CompletableFuture.get()` inside a Promise chain.
**Mitigation**:

- **Build-time**: Static analysis rule to detect blocking calls in Promise chains
- **Test-time**: All async tests extend `EventloopTestBase` (build fails otherwise)
- **Runtime**: eBPF eventloop stall detector (`EbpfEventloopStallTracer`) on all services with <2ms alerting threshold
- **Documentation**: Mandatory onboarding lab exercising `Promise.ofBlocking` for IO

#### X-01: AI Cost Overrun

**What**: Uncontrolled LLM usage (especially from autonomous GAA agents running reflection loops) generates unsustainable API costs.
**Why It's Critical**: A single poorly-configured reflection agent could generate thousands of dollars in costs per day.
**Triggers**: Agent reflection loops without circuit breakers, unoptimized prompt templates, cache misses on commonly repeated queries.
**Mitigation**:

- **Per-tenant and per-product LLM budgets** enforced at the LLM Gateway level
- **Semantic prompt caching** (Redis-backed, 40-60% cost reduction measured in existing usage)
- **Tiered model selection** (use cheaper models for classification, expensive models only for generation)
- **Circuit breaker on reflection cycles** (max 5 reflections per agent turn)
- **Real-time cost attribution dashboards** (cost by product, tenant, agent, feature)

#### X-03: Eventloop Stall in Production

**What**: A blocking operation in production causes event loop freeze, degrading all concurrent requests.
**Mitigation**:

- **eBPF stall tracer** (`platform/java/observability/trace/EbpfEventloopStallTracer.java`) deployed on ALL services
- **<2ms threshold alerting** — any eventloop stall > 2ms triggers immediate alerts
- **Promise.ofBlocking enforcement** via build-time static analysis
- **Canary deployment analysis** includes eventloop stall metrics

---

## Phase 0: Foundation Analysis (Week 0)

### Purpose of Phase 0

**Phase 0** establishes the foundation by analyzing the current state of existing systems and identifying gaps, opportunities, and integration points. This phase ensures we build the kernel on solid ground with full understanding of:

- **Existing Assets**: What we already have that can be reused
- **Technical Debt**: What needs to be refactored or replaced
- **Integration Points**: How systems will connect through the kernel
- **Capability Gaps**: What functionality needs to be built from scratch

### 0.1 Detailed Current State Assessment

#### 0.1.1 What is PHR (Personal Health Record)?

**PHR (Personal Health Record)** is a healthcare management system designed for Nepal's healthcare ecosystem. It serves as a comprehensive digital health platform for:

- **Patients**: Manage their health records, appointments, and treatments
- **Providers**: Access patient information, schedule appointments, manage care
- **Caregivers**: Support patients with delegated access and care coordination
- **Facilities**: Manage healthcare operations, billing, and compliance

**Why PHR Exists**:

- Address Nepal's healthcare digitization needs
- Provide consent-first health data management
- Enable interoperability with existing systems (FHIR, openIMIS)
- Ensure compliance with Nepal healthcare regulations

**PHR Current State Deep Dive**

**Documentation Analysis Results:**

- **Total Documents**: 29 comprehensive documents spanning governance, strategy, architecture, design, and testing
- **MVP Definition**: Core MVP includes patient records, consent management, appointments, medications, billing, and basic FHIR interop
- **Architecture**: Multi-tenant with facility isolation, consent-first design, Nepal healthcare compliance
- **Regulatory Framework**: Nepal Directive 2081, Privacy Act 2075, FHIR R4, openIMIS integration

**PHR Module Inventory:**

The PHR system is organized into **modules** - self-contained units of functionality that can be developed, tested, and deployed independently. Each module addresses specific healthcare domain needs:

```yaml
PHR_Modules:
  PatientRecordModule:
    What_It_Is: "Core module managing patient demographic and clinical information"
    Why_It_Exists: "Central repository for all patient-related data, ensuring data consistency and accessibility"
    Status: "Documented, Ready for Implementation"
    Features:
      - Patient demographics management (name, age, contact, identifiers)
      - Medical history tracking (diagnoses, procedures, conditions)
      - Allergy and adverse reaction recording
      - Emergency contact and next-of-kin management
      - Clinical document storage and retrieval
    Integration_Points:
      - Data-Cloud: Patient data storage and retrieval
      - AEP: Patient lifecycle events (registration, updates, discharge)
      - Kernel: Consent validation before data access
    Dependencies:
      - ConsentModule: For access control validation
      - AuditModule: For recording all data access events

  ConsentModule:
    What_It_Is: "Sophisticated consent management system controlling health data access"
    Why_It_Exists: "Healthcare data requires explicit patient consent for privacy compliance and legal requirements"
    Status: "Detailed Specification Available"
    Features:
      - Granular consent management (by data type, provider, duration)
      - Emergency access override with post-event notification
      - Caregiver delegation with time-limited permissions
      - Consent audit trail for compliance reporting
      - Revocation and modification capabilities
    Integration_Points:
      - Kernel: Consent policies and validation engine
      - Data-Cloud: Consent records storage and retrieval
      - AEP: Consent events (granted, revoked, modified)
    Compliance:
      - Nepal Privacy Act 2075: Personal data protection requirements
      - HIPAA-inspired principles: Healthcare data privacy standards
      - Emergency access protocols: Time-critical care exceptions

  DocumentModule:
    What_It_Is: "Document management system for medical records, images, and files"
    Why_It_Exists: "Healthcare generates massive amounts of documents that need secure storage and processing"
    Status: "OCR/ASR Workflow Defined"
    Features:
      - Document upload, storage, and versioning
      - OCR (Optical Character Recognition) processing pipeline
      - ASR (Automatic Speech Recognition) for audio recordings
      - Document classification and indexing
      - Secure sharing and access control
    Integration_Points:
      - Data-Cloud: Document storage and metadata
      - AEP: OCR/ASR processing workflows
      - Kernel: Document processing agents and security
    External_Integrations:
      - OCR Engine: Tesseract (open source) or Commercial alternatives
      - ASR Engine: Whisper (open source) or Commercial services
      - DICOM Viewers: Medical imaging compatibility

  BillingModule:
    What_It_Is: "Financial management system for healthcare billing and payments"
    Why_It_Exists: "Healthcare services require complex billing, insurance processing, and payment collection"
    Status: "Payment Workflow Defined"
    Features:
      - Bill generation from services and procedures
      - Payment processing and receipt generation
      - Insurance claim submission and tracking
      - Payment plan management and reminders
      - Financial reporting and analytics
    Integration_Points:
      - Finance System: Core payment processing infrastructure
      - Data-Cloud: Billing records and transaction history
      - AEP: Payment workflows and status tracking
    External_Integrations:
      - Payment Gateways: E-banking, mobile money, credit cards
      - Insurance Systems: Claim processing and eligibility verification
      - openIMIS: Insurance management integration

  InsuranceModule:
    What_It_Is: "Insurance management system for healthcare coverage and claims"
    Why_It_Exists: "Healthcare services require insurance coverage and claims processing"
    Status: "Detailed Specification Available"
    Features:
      - Insurance coverage management
      - Claims processing and adjudication
      - Eligibility verification
      - Premium payment management
      - Insurance reporting and analytics
    Integration_Points:
      - Finance System: Core payment processing infrastructure
      - Data-Cloud: Insurance records and transaction history
      - AEP: Insurance workflows and status tracking
    External_Integrations:
      - Insurance Systems: Claim processing and eligibility verification
      - openIMIS: Insurance management integration
```

**PHR Technical Specifications:**

The PHR system uses a carefully selected technology stack optimized for healthcare requirements:

```yaml
PHR_Tech_Stack:
  Backend:
    - Java 21 + ActiveJ:
        What_It_Is: "Modern Java runtime with high-performance async framework"
        Why_It_Exists: "Java provides enterprise-grade security and ecosystem; ActiveJ offers non-blocking I/O for high concurrency"
        Benefits: "Type safety, mature ecosystem, excellent performance, strong security model"
    - PostgreSQL with TimescaleDB:
        What_It_Is: "Relational database with time-series extensions"
        Why_It_Exists: "PostgreSQL provides ACID compliance for healthcare data; TimescaleDB handles time-series medical data efficiently"
        Benefits: "Data integrity, HIPAA compliance, time-series optimization, strong backup/restore"
    - Redis for caching:
        What_It_Is: "In-memory data store for high-speed caching"
        Why_It_Exists: "Healthcare applications need fast access to frequently accessed data like patient preferences and consent"
        Benefits: "Sub-millisecond response, reduces database load, session management"
    - Kafka for event streaming:
        What_It_Is: "Distributed event streaming platform"
        Why_It_Exists: "Healthcare events (appointments, lab results, updates) need reliable, ordered processing"
        Benefits: "Event durability, replay capability, audit trails, real-time processing"

  Frontend:
    - React 18 + TypeScript:
        What_It_Is: "Modern web framework with static typing"
        Why_It_Exists: "React provides component-based UI; TypeScript ensures type safety for healthcare data"
        Benefits: "Component reusability, type safety, large ecosystem, excellent tooling"
    - Tailwind CSS:
        What_It_Is: "Utility-first CSS framework"
        Why_It_Exists: "Rapid UI development with consistent design system"
        Benefits: "Fast development, consistent design, small bundle size"
    - Jotai for state management:
        What_It_Is: "Atomic state management library"
        Why_It_Exists: "Fine-grained state control for complex healthcare forms and data"
        Benefits: "Minimal re-renders, TypeScript support, simple API"
    - TanStack Query for server state:
        What_It_Is: "Server state management library"
        Why_It_Exists: "Healthcare data needs caching, synchronization, and optimistic updates"
        Benefits: "Automatic caching, background updates, optimistic UI, error handling"

  Infrastructure:
    - Docker containers:
        What_It_Is: "Containerization platform for application packaging"
        Why_It_Exists: "Consistent deployment environments and isolation"
        Benefits: "Environment consistency, scalability, security isolation"
    - Kubernetes deployment:
        What_It_Is: "Container orchestration platform"
        Why_It_Exists: "Healthcare requires high availability and automatic scaling"
        Benefits: "Auto-scaling, self-healing, rolling updates, service discovery"
    - Prometheus + Grafana monitoring:
        What_It_Is: "Monitoring and visualization stack"
        Why_It_Exists: "Healthcare systems require 24/7 monitoring and alerting"
        Benefits: "Metrics collection, alerting, visualization, SLA monitoring"

  Security:
    - JWT authentication:
        What_It_Is: "JSON Web Token-based authentication"
        Why_It_Exists: "Stateless authentication suitable for distributed healthcare systems"
        Benefits: "Stateless, portable, standard, secure"
    - RBAC authorization:
        What_It_Is: "Role-Based Access Control"
        Why_It_Exists: "Healthcare requires granular access control by role (patient, provider, admin)"
        Benefits: "Granular permissions, auditability, compliance support"
    - Data encryption at rest and in transit:
        What_It_Is: "Encryption for stored and transmitted data"
        Why_It_Exists: "Healthcare data protection regulations require encryption"
        Benefits: "Regulatory compliance, data protection, breach prevention"
    - Audit logging:
        What_It_Is: "Comprehensive logging of all system access and changes"
        Why_It_Exists: "Healthcare requires complete audit trails for compliance"
        Benefits: "Compliance support, forensic analysis, accountability"
```

#### 0.1.2 What is the Finance System?

**Finance System** refers to the comprehensive financial trading and management platform that handles securities trading, risk management, compliance, and regulatory reporting for financial markets. It serves as the backbone for:

- **Trading Operations**: Order capture, execution, and settlement
- **Risk Management**: Real-time risk monitoring and limit enforcement
- **Regulatory Compliance**: Trade surveillance, reporting, and rule validation
- **Market Operations**: Market data processing and connectivity

**Why Finance System Exists**:

- Provide enterprise-grade trading infrastructure for financial markets
- Ensure regulatory compliance across multiple jurisdictions (Nepal SEBON, Europe MIFID-II, US Dodd-Frank)
- Enable high-frequency, low-latency trading with sub-millisecond performance
- Manage complex risk calculations and position monitoring
- Support multiple asset classes and trading strategies

**Finance Integration Plan Analysis:**

- **Document**: Comprehensive 688-line integration plan
- **Platform Overlap**: 85% overlap with Ghatana platform identified
- **Module Count**: 19 kernel modules mapped to Ghatana platform components
- **Integration Strategy**: Event processing through AEP, data management through Data-Cloud

**Finance Module Inventory:**

The Finance System is organized into specialized **modules** - high-performance components designed for specific financial operations:

```yaml
Finance_Modules:
  OrderManagementSystem (OMS):
    What_It_Is: "Core module handling the complete order lifecycle from capture to settlement"
    Why_It_Exists: "Financial trading requires reliable, high-throughput order processing with validation and risk checks"
    Status: "Specification Complete"
    Features:
      - Order capture and validation (price, quantity, compliance checks)
      - Order routing and execution (smart order routing to best venues)
      - Order status tracking (real-time order state updates)
      - Order modification and cancellation (amendments and stop orders)
    Integration_Points:
      - AEP: Order processing pipeline with event-driven validation
      - Risk Management: Pre-trade risk checks and limit enforcement
      - Compliance: Regulatory validation and reporting requirements
    Performance_Requirements:
      - Latency: <10ms for order validation (pre-trade risk checks)
      - Throughput: 10,000 orders/second (peak trading capacity)
      - Availability: 99.999% (financial trading uptime requirements)

  ExecutionManagementSystem (EMS):
    What_It_Is: "High-performance execution engine connecting to multiple trading venues"
    Why_It_Exists: "Modern trading requires algorithmic execution and connectivity to multiple exchanges"
    Status: "Architecture Defined"
    Features:
      - Real-time market connectivity (FIX protocol, exchange APIs)
      - Algorithmic execution (VWAP, TWAP, implementation shortfall)
      - Order splitting and routing (smart order routing across venues)
      - Execution reporting (trade confirmations, execution analysis)
    Integration_Points:
      - AEP: Execution workflows with real-time monitoring
      - Market Data: Real-time market data feeds for pricing
      - Ledger: Trade recording and position updates
    External_Integrations:
      - Market Exchanges: Nepal Stock Exchange, international venues
      - FIX Protocol: Financial Information eXchange protocol
      - Market Data Vendors: Real-time price and liquidity feeds

  RiskManagement:
    What_It_Is: "Real-time risk calculation and monitoring system for trading activities"
    Why_It_Exists: "Financial regulations and trading operations require continuous risk monitoring"
    Status: "Models Defined"
    Features:
      - Real-time risk calculation (VaR, stress testing, scenario analysis)
      - Position monitoring (real-time position tracking across portfolios)
      - Limit checking (pre-trade and post-trade limit enforcement)
      - Risk reporting (regulatory risk reports and internal analytics)
    Integration_Points:
      - AEP: Risk calculation agents with event-driven updates
      - AI Platform: Risk models and machine learning predictions
      - Data-Cloud: Risk data storage and historical analysis
    Risk_Types:
      - Market Risk: Price volatility and market movement risks
      - Credit Risk: Counterparty default and settlement risks
      - Operational Risk: System failures and operational errors
      - Liquidity Risk: Market liquidity and execution risks

  ComplianceEngine:
    What_It_Is: "Regulatory compliance system ensuring adherence to trading rules and regulations"
    Why_It_Exists: "Financial markets operate under strict regulatory oversight requiring automated compliance"
    Status: "Rules Engine Defined"
    Features:
      - Regulatory rule validation (SEBON, MIFID-II, Dodd-Frank rules)
      - Trade surveillance (market abuse detection, insider trading monitoring)
      - Reporting generation (regulatory reports, transaction reports)
      - Alert management (compliance breaches, suspicious activities)
    Integration_Points:
      - AEP: Compliance workflows with automated rule evaluation
      - Rules Engine: Policy evaluation and rule execution engine
      - Audit: Compliance reporting and audit trail generation
    Regulations:
      - SEBON (Nepal): Securities Board of Nepal regulations
      - MIFID-II (Europe): Markets in Financial Instruments Directive
      - Dodd-Frank (US): Dodd-Frank Wall Street Reform and Consumer Protection Act
```

**Finance Technical Specifications:**

The Finance System uses a high-performance technology stack optimized for trading and risk management:

```yaml
Finance_Tech_Stack:
  Backend:
    - Java 21 + ActiveJ:
        What_It_Is: "High-performance Java runtime with async framework"
        Why_It_Exists: "Financial trading requires low-latency, high-throughput processing with predictable performance"
        Benefits: "Sub-millisecond latency, high concurrency, mature ecosystem, garbage collection tuning"
    - PostgreSQL + TimescaleDB:
        What_It_Is: "Relational database with time-series extensions for financial data"
        Why_It_Exists: "Trading data has strong time-series characteristics requiring efficient storage and querying"
        Benefits: "ACID compliance for trades, time-series optimization for market data, point-in-time queries"
    - Kafka for event streaming:
        What_It_Is: "High-throughput distributed event streaming platform"
        Why_It_Exists: "Financial markets generate millions of events per second requiring reliable processing"
        Benefits: "Millions of events/second, exactly-once semantics, replay capability, fault tolerance"
    - Redis for caching:
        What_It_Is: "In-memory data store for ultra-fast data access"
        Why_It_Exists: "Trading requires microsecond access to market data, positions, and risk calculations"
        Benefits: "Microsecond latency, sub-millisecond reads, reduces database load"

  Event_Processing:
    - AEP Platform:
        What_It_Is: "Agentic Event Processing platform for complex event processing"
        Why_It_Exists: "Financial events require complex processing, correlation, and pattern detection"
        Benefits: "Event correlation, pattern detection, real-time analytics, agent-based processing"
    - Agent Framework:
        What_It_Is: "AI-powered agents for intelligent event processing"
        Why_It_Exists: "Trading decisions require AI-based analysis and prediction"
        Benefits: "Machine learning integration, predictive analytics, adaptive behavior"
    - Workflow Runtime:
        What_It_Is: "Durable workflow execution engine for complex trading workflows"
        Why_It_Exists: "Trading workflows span multiple systems and require reliability and compensation"
        Benefits: "Durable execution, compensation patterns, long-running workflows, state management"
    - Operator Catalog:
        What_It_Is: "Registry of reusable processing operators for financial operations"
        Why_It_Exists: "Financial operations require reusable, composable processing components"
        Benefits: "Reusable components, composition patterns, versioning, discovery"

  AI_ML:
    - Model Registry:
        What_It_Is: "Central repository for machine learning models"
        Why_It_Exists: "Risk models and trading algorithms need versioning and governance"
        Benefits: "Model versioning, A/B testing, rollback capabilities, model governance"
    - Inference Service:
        What_It_Is: "High-performance model inference engine"
        Why_It_Exists: "Trading decisions require real-time model predictions"
        Benefits: "Real-time predictions, GPU acceleration, model serving, performance monitoring"
    - Feature Store:
        What_It_Is: "Centralized feature engineering and storage system"
        Why_It_Exists: "ML models require consistent, versioned features for training and inference"
        Benefits: "Feature versioning, online/offline serving, feature discovery, data lineage"
    - Training Pipeline:
        What_It_Is: "Automated machine learning model training pipeline"
        Why_It_Exists: "Risk models and trading algorithms need continuous retraining"
        Benefits: "Automated training, hyperparameter tuning, model validation, deployment automation"

  Data_Management:
    - Data Cloud Platform:
        What_It_Is: "Unified data management platform for all financial data"
        Why_It_Exists: "Financial data spans multiple types requiring unified management"
        Benefits: "Unified storage, data governance, access control, data lineage"
    - Event Store:
        What_It_Is: "Immutable event storage for audit and replay"
        Why_It_Exists: "Financial regulations require complete, immutable audit trails"
        Benefits: "Immutable storage, regulatory compliance, audit trails, event replay"
    - Config Registry:
        What_It_Is: "Dynamic configuration management system"
        Why_It_Exists: "Trading systems require dynamic configuration without downtime"
        Benefits: "Hot configuration changes, version control, rollback, environment-specific configs"
    - Audit Service:
        What_It_Is: "Comprehensive audit logging and compliance service"
        Why_It_Exists: "Financial regulations require complete audit trails"
        Benefits: "Regulatory compliance, forensic analysis, compliance reporting, audit trails"

  Compliance:
    - 10-year data retention:
        What_It_Is: "Long-term data retention policy for regulatory compliance"
        Why_It_Exists: "Financial regulations require long-term data preservation"
        Benefits: "Regulatory compliance, historical analysis, audit support"
    - Immutable audit logs:
        What_It_Is: "Tamper-proof logging system for all trading activities"
        Why_It_Exists: "Financial regulations require tamper-proof audit trails"
        Benefits: "Regulatory compliance, forensic evidence, tamper detection"
    - Regulatory reporting:
        What_It_Is: "Automated generation of regulatory reports"
        Why_It_Exists: "Financial regulations require periodic reporting to authorities"
        Benefits: "Automated compliance, report generation, regulatory submission"
    - Surveillance monitoring:
        What_It_Is: "Real-time monitoring for market abuse and suspicious activities"
        Why_It_Exists: "Financial regulations require market abuse detection"
        Benefits: "Market abuse detection, suspicious activity alerts, compliance enforcement"
```

#### 0.1.3 What are App-Platform Kernel Modules?

**App-Platform Kernel Modules** are the foundational components that provide core platform services for all applications built on the Ghatana platform. These modules represent the existing infrastructure that will be enhanced and integrated into the unified kernel architecture.

**Why Kernel Modules Exist**:

- Provide reusable platform services across all products
- Eliminate duplication of common functionality
- Ensure consistent patterns for authentication, configuration, etc.
- Enable rapid product development through shared capabilities
- Maintain governance and security standards across the platform

**Current Kernel Module Status:**

The existing kernel modules provide essential platform services but need enhancement for full kernel integration:

```yaml
App_Platform_Kernel_Modules:
  iam (Identity and Access Management):
    What_It_Is: "Core authentication and authorization service for the platform"
    Why_It_Exists: "All applications need consistent user identity and access control"
    Status: "Partial Implementation"
    Features:
      - JWT authentication (stateless token-based auth)
      - RBAC authorization (role-based access control)
      - User management (user profiles and lifecycle)
    Gaps:
      - Multi-tenant isolation (tenant-specific identity boundaries)
      - Product-specific roles (different roles per product context)
      - Advanced consent models (granular consent management)

  config-engine:
    What_It_Is: "Dynamic configuration management system for platform-wide settings"
    Why_It_Exists: "Applications need flexible configuration without code deployments"
    Status: "Basic Implementation"
    Features:
      - Configuration management (centralized config storage)
      - Hot-reload capability (runtime config updates)
    Gaps:
      - Product-specific configs (product-scoped configuration)
      - Hierarchical config merging (inheritance and overrides)
      - Air-gap deployment support (offline configuration)

  rules-engine:
    What_It_Is: "Policy evaluation engine for business rules and compliance"
    Why_It_Exists: "Applications need flexible rule evaluation without hard-coding logic"
    Status: "Framework Defined"
    Features:
      - Rule evaluation engine (condition-based rule processing)
      - Policy management (rule lifecycle and versioning)
    Gaps:
      - OPA/Rego integration (Open Policy Agent support)
      - V8/WASM sandbox (secure rule execution)
      - Jurisdiction routing (location-based rule selection)

  plugin-runtime:
    What_It_Is: "Dynamic plugin loading and execution environment"
    Why_It_Exists: "Platform needs to load and manage plugins at runtime"
    Status: "Basic Implementation"
    Features:
      - Plugin loading (dynamic plugin discovery and loading)
      - Lifecycle management (plugin start/stop/health monitoring)
    Gaps:
      - Security validation (plugin security scanning and sandboxing)
      - Resource isolation (resource limits and isolation)
      - Hot-reload capability (runtime plugin updates)

  event-store:
    What_It_Is: "Immutable event storage system for audit and event sourcing"
    Why_It_Exists: "Applications need reliable event storage for audit trails and event sourcing"
    Status: "Interface Defined"
    Features:
      - Event storage interface (abstract event storage API)
      - Event streaming (real-time event distribution)
    Gaps:
      - Multi-tenant isolation (tenant-specific event streams)
      - Performance optimization (high-throughput event processing)
      - Retention policies (automated event lifecycle management)

  audit-trail:
    What_It_Is: "Comprehensive audit logging system for compliance and security"
    Why_It_Exists: "Applications need complete audit trails for regulatory compliance"
    Status: "Basic Implementation"
    Features:
      - Audit logging (structured audit event capture)
      - Event tracking (user action monitoring)
    Gaps:
      - Product-specific retention (different retention per product)
      - Regulatory compliance (specific regulatory requirements)
      - Immutable storage (tamper-proof audit storage)
```

### 0.2 Detailed Architecture Validation

#### 0.2.1 What is Data-Cloud Integration Architecture?

**Data-Cloud Integration Architecture** defines how the kernel interfaces with the Data-Cloud platform to provide unified data management, configuration, and governance services across all products. This integration ensures consistent data handling while leveraging Data-Cloud's robust infrastructure.

**Why Data-Cloud Integration Exists**:

- Provide unified data management across all products
- Eliminate data silos and ensure data consistency
- Enable cross-product analytics and insights
- Centralize data governance and compliance
- Reduce operational complexity through shared infrastructure

**Data-Cloud Component Mapping:**

The kernel adapts Data-Cloud capabilities through standardized interfaces:

```yaml
Data_Cloud_Integration:
  Event_Store_SPI:
    What_It_Is: "Service Provider Interface for kernel event storage using Data-Cloud infrastructure"
    Why_It_Exists: "Kernel needs event storage that integrates with Data-Cloud's event management capabilities"
    Interface: "KernelEventStore"
    Implementation: "DataCloudEventStoreAdapter"
    Features:
      - Append-only event storage (immutable event log for audit trails)
      - Event streaming and tailing (real-time event distribution)
      - Multi-tenant isolation (tenant-specific event streams)
      - Configurable retention (product-specific retention policies)
    Performance:
      - Throughput: 100,000 events/second (high-volume event processing)
      - Latency: <10ms write, <50ms read (sub-50ms response times)
      - Storage: Immutable append-only (tamper-proof storage)

  Config_Registry:
    What_It_Is: "Configuration management interface using Data-Cloud's configuration capabilities"
    Why_It_Exists: "Kernel needs dynamic configuration that integrates with Data-Cloud's config management"
    Interface: "KernelConfigResolver"
    Implementation: "DataCloudConfigAdapter"
    Features:
      - Hierarchical configuration (global → product → tenant → user)
      - Hot-reload capability (runtime configuration updates)
      - Tenant-scoped configs (tenant-specific configuration)
      - Environment-specific overrides (dev/staging/prod configurations)
    Configuration_Types:
      - Product configs (product-specific settings)
      - Module configs (module-level configuration)
      - Feature flags (dynamic feature toggling)
      - Security policies (access control and security settings)

  Data_Governance:
    What_It_Is: "Data governance interface using Data-Cloud's governance and compliance capabilities"
    Why_It_Exists: "Kernel needs data governance that integrates with Data-Cloud's compliance framework"
    Interface: "KernelDataGovernance"
    Implementation: "DataCloudGovernanceAdapter"
    Features:
      - Data classification (sensitivity labeling and categorization)
      - Access control (role-based and attribute-based access)
      - Retention policies (automated data lifecycle management)
      - Data lineage (data origin and transformation tracking)
    Governance_Rules:
      - Healthcare data: 7-year retention (medical data compliance)
      - Financial data: 10-year retention (financial regulations)
      - Personal data: GDPR compliance (privacy regulations)
      - Audit data: Immutable storage (compliance requirements)

  Audit_Service:
    What_It_Is: "Audit logging interface using Data-Cloud's audit and compliance capabilities"
    Why_It_Exists: "Kernel needs comprehensive audit logging that integrates with Data-Cloud's audit infrastructure"
    Interface: "KernelAuditService"
    Implementation: "DataCloudAuditAdapter"
    Features:
      - Comprehensive audit logging (all system events and user actions)
      - Tamper detection (audit log integrity verification)
      - Regulatory compliance (industry-specific audit requirements)
      - Long-term retention (extended audit log storage)
    Audit_Types:
      - Data access events (who accessed what data when)
      - Configuration changes (system configuration modifications)
      - Security events (authentication, authorization, breaches)
      - Business transactions (critical business operations)
```

#### 0.2.2 What is AEP Integration Architecture?

**AEP (Agentic Event Processing) Integration Architecture** defines how the kernel interfaces with the AEP platform to provide event-driven processing, agent-based intelligence, and workflow orchestration capabilities across all products. This integration enables sophisticated event processing and intelligent automation.

**Why AEP Integration Exists**:

- Provide unified event processing across all products
- Enable intelligent automation through AI agents
- Support complex workflow orchestration
- Facilitate real-time decision making and responses
- Reduce operational complexity through shared processing infrastructure

**AEP Component Mapping:**

The kernel adapts AEP capabilities through standardized interfaces:

```yaml
AEP_Integration:
  Event_Processing:
    What_It_Is: "High-throughput event processing interface using AEP's event streaming capabilities"
    Why_It_Exists: "Kernel needs event processing that integrates with AEP's high-performance event engine"
    Interface: "KernelEventProcessor"
    Implementation: "AepEventProcessorAdapter"
    Features:
      - High-throughput event processing (millions of events per second)
      - Event routing and filtering (intelligent event distribution)
      - Event transformation (format conversion and enrichment)
      - Dead-letter handling (failed event processing and recovery)
    Performance:
      - Throughput: 1M events/second (enterprise-scale processing)
      - Latency: <1ms processing (sub-millisecond event processing)
      - Scalability: Horizontal scaling (linear performance scaling)

  Agent_Framework:
    What_It_Is: "AI-powered agent runtime interface using AEP's agent management capabilities"
    Why_It_Exists: "Kernel needs intelligent agents that integrate with AEP's agent framework"
    Interface: "KernelAgentRuntime"
    Implementation: "AepAgentRuntimeAdapter"
    Features:
      - Agent lifecycle management (agent creation, deployment, termination)
      - Agent communication (inter-agent messaging and coordination)
      - Agent composition (complex agent assembly from simple components)
      - Agent monitoring (performance tracking and health monitoring)
    Agent_Types:
      - Rule-based agents (deterministic rule execution)
      - ML-based agents (machine learning powered decision making)
      - Workflow agents (process automation and orchestration)
      - Monitoring agents (system health and performance monitoring)

  Workflow_Runtime:
    What_It_Is: "Durable workflow execution interface using AEP's workflow orchestration capabilities"
    Why_It_Exists: "Kernel needs workflow execution that integrates with AEP's workflow engine"
    Interface: "KernelWorkflowRuntime"
    Implementation: "AepWorkflowRuntimeAdapter"
    Features:
      - Durable workflow execution (fault-tolerant workflow processing)
      - Workflow orchestration (complex workflow coordination)
      - State management (workflow state persistence and recovery)
      - Error handling and retry (automatic error recovery and retry logic)
    Workflow_Types:
      - Sequential workflows (step-by-step process execution)
      - Parallel workflows (concurrent task execution)
      - Conditional workflows (branching logic and decision points)
      - Long-running workflows (extended process execution)

  Operator_Catalog:
    What_It_Is: "Processing operator registry interface using AEP's operator management capabilities"
    Why_It_Exists: "Kernel needs operator management that integrates with AEP's operator catalog"
    Interface: "KernelOperatorRegistry"
    Implementation: "AepOperatorCatalogAdapter"
    Features:
      - Operator registration and discovery (operator catalog management)
      - Operator composition (complex operator assembly)
      - Operator versioning (operator lifecycle and version control)
      - Operator monitoring (performance tracking and health monitoring)
    Operator_Types:
      - Data transformation operators (format conversion and data mapping)
      - Validation operators (data quality and compliance checking)
      - Enrichment operators (data enhancement and context addition)
      - Integration operators (external system connectivity)
```

#### 0.2.3 Security Architecture Integration

**What is Security Architecture Integration?**

Security Architecture Integration defines how the kernel provides comprehensive security capabilities across all products, ensuring consistent security posture while respecting product-specific security requirements.

**Why Security Integration Exists**:

- Provide unified security framework across all products
- Ensure compliance with industry standards (HIPAA, GDPR, financial regulations)
- Enable centralized security monitoring and incident response
- Reduce security complexity through shared security capabilities
- Maintain product-specific security boundaries

**Security Component Mapping:**

```yaml
Security_Integration:
  Authentication_Framework:
    What_It_Is: "Unified authentication system supporting multiple authentication methods"
    Why_It_Exists: "Products need consistent authentication while supporting different user types"
    Interface: "KernelAuthenticationManager"
    Implementation: "KernelAuthenticationAdapter"
    Features:
      - Multi-factor authentication (MFA support for enhanced security)
      - Social login integration (OAuth2/OpenID Connect providers)
      - Enterprise SSO (SAML, LDAP integration)
      - Biometric authentication (fingerprint, facial recognition)
      - Token management (JWT, refresh tokens, session management)
    Authentication_Methods:
      - Password-based (traditional username/password)
      - Token-based (JWT, API keys)
      - Certificate-based (X.509 client certificates)
      - Biometric (fingerprint, facial, voice recognition)
      - Social providers (Google, Microsoft, Apple)

  Authorization_Framework:
    What_It_Is: "Fine-grained authorization system with role-based and attribute-based access control"
    Why_It_Exists: "Products need flexible authorization that supports complex permission models"
    Interface: "KernelAuthorizationManager"
    Implementation: "KernelAuthorizationAdapter"
    Features:
      - Role-Based Access Control (RBAC) (role-based permissions)
      - Attribute-Based Access Control (ABAC) (attribute-based permissions)
      - Policy-based access (dynamic policy evaluation)
      - Resource-level permissions (granular access control)
      - Time-bound permissions (temporary access grants)
    Authorization_Models:
      - RBAC (Role-Based Access Control)
      - ABAC (Attribute-Based Access Control)
      - Policy-Based Access Control
      - Hybrid Models (combination of approaches)

  Data_Protection:
    What_It_Is: "Comprehensive data protection framework for data at rest and in transit"
    Why_It_Exists: "Products need consistent data protection across all data types and storage"
    Interface: "KernelDataProtectionManager"
    Implementation: "KernelDataProtectionAdapter"
    Features:
      - Encryption at rest (AES-256 for stored data)
      - Encryption in transit (TLS 1.3 for network communication)
      - Field-level encryption (sensitive field encryption)
      - Key management (centralized key rotation and management)
      - Data masking (sensitive data masking for non-production)
    Encryption_Standards:
      - AES-256 (data at rest)
      - TLS 1.3 (data in transit)
      - RSA-4096 (key exchange)
      - SHA-256 (data integrity)

  Audit_and_Compliance:
    What_It_Is: "Comprehensive audit logging and compliance monitoring system"
    Why_It_Exists: "Products need complete audit trails for regulatory compliance and security monitoring"
    Interface: "KernelAuditManager"
    Implementation: "KernelAuditAdapter"
    Features:
      - Immutable audit logs (tamper-proof audit trails)
      - Real-time monitoring (security event monitoring)
      - Compliance reporting (automated compliance report generation)
      - Anomaly detection (unusual activity detection)
      - Forensic analysis (security incident investigation tools)
    Compliance_Standards:
      - HIPAA (healthcare data protection)
      - GDPR (privacy data protection)
      - SOX (financial reporting compliance)
      - PCI-DSS (payment card industry)
```

#### 0.2.4 Observability Integration Architecture

**What is Observability Integration Architecture?**

Observability Integration Architecture defines how the kernel provides comprehensive monitoring, logging, tracing, and alerting capabilities across all products, enabling operational excellence and rapid issue detection.

**Why Observability Integration Exists**:

- Provide unified observability framework across all products
- Enable proactive issue detection and resolution
- Support performance optimization and capacity planning
- Facilitate debugging and troubleshooting
- Ensure service level objectives (SLOs) are met

**Observability Component Mapping:**

````yaml
Observability_Integration:
  Monitoring_Framework:
    What_It_Is: "Comprehensive monitoring system for metrics, performance, and health"
    Why_It_Exists: "Products need consistent monitoring for operational excellence"
    Interface: "KernelMonitoringManager"
    Implementation: "KernelMonitoringAdapter"
    Features:
      - Metrics collection (application and infrastructure metrics)
      - Performance monitoring (response times, throughput, error rates)
      - Health checks (service health and availability)
      - Custom metrics (business-specific metrics)
      - Alerting (intelligent alerting and notification)
    Monitoring_Types:
      - Infrastructure metrics (CPU, memory, disk, network)
      - Application metrics (response times, error rates, throughput)
      - Business metrics (user activity, conversion rates, revenue)
      - Security metrics (authentication failures, access violations)

  Logging_Framework:
    What_It_Is: "Unified logging system with structured logging and log aggregation"
    Why_It_Exists: "Products need consistent logging for debugging and compliance"
    Interface: "KernelLoggingManager"
    Implementation: "KernelLoggingAdapter"
    Features:
      - Structured logging (JSON-formatted logs with consistent schema)
      - Log aggregation (centralized log collection and storage)
      - Log search and analysis (powerful log search capabilities)
      - Log retention policies (automated log lifecycle management)
      - Real-time log streaming (live log monitoring)
    Logging_Levels:
      - DEBUG (detailed debugging information)
      - INFO (general information messages)
      - WARN (warning messages for potential issues)
      - ERROR (error messages for failures)
      - FATAL (critical errors causing system failure)

  Tracing_Framework:
    What_It_Is: "Distributed tracing system for request flow analysis"
    Why_It_Exists: "Products need end-to-end request tracing for performance analysis"
    Interface: "KernelTracingManager"
    Implementation: "KernelTracingAdapter"
    Features:
      - Distributed tracing (end-to-end request tracking)
      - Span collection (detailed operation timing)
      - Trace visualization (request flow visualization)
      - Performance analysis (bottleneck identification)
      - Service dependency mapping (service interaction mapping)
    Tracing_Standards:
      - OpenTelemetry (industry standard for observability)
      - Jaeger (distributed tracing backend)
      - Zipkin (alternative tracing backend)
      - W3C Trace Context (standard trace context format)

  Alerting_Framework:
    What_It_Is: "Intelligent alerting system with automated incident response and comprehensive notification channels"
    Why_It_Exists: "Products need proactive alerting for rapid issue detection and response with minimal noise"
    Interface: "KernelAlertingManager"
    Implementation: "KernelAlertingAdapter"
    Features:
      - Multi-channel alerting (email, SMS, Slack, PagerDuty, Teams, Discord)
      - Intelligent alert routing (severity, team, expertise-based routing)
      - Alert aggregation (correlated alert grouping to reduce noise)
      - Automated response (incident response playbooks and actions)
      - Alert escalation (progressive escalation with SLA tracking)
      - Alert enrichment (contextual information from logs, metrics, traces)
      - Alert suppression (maintenance windows and known issue suppression)
      - Alert deduplication (prevent duplicate alerts)
    Alert_Types:
      - System alerts (infrastructure and platform issues)
      - Application alerts (application-specific issues)
      - Security alerts (security incidents and vulnerabilities)
      - Business alerts (business process and KPI issues)
      - Compliance alerts (regulatory and policy violations)

  Observability_Data_Lake:
    What_It_Is: "Unified storage and query system for all observability data with long-term retention"
    Why_It_Exists: "Products need centralized observability data storage with correlation and governance"
    Interface: "KernelObservabilityDataLake"
    Implementation: "KernelObservabilityDataLakeAdapter"
    Features:
      - Unified observability storage (metrics, logs, traces in one place)
      - Long-term retention (cost-effective archival policies)
      - Cross-domain correlation (metrics ↔ logs ↔ traces correlation)
      - Query federation (unified query interface)
      - Data governance (access controls and compliance)
      - Data compression (optimized storage for cost efficiency)
      - Data tiering (hot, warm, cold storage tiers)
      - Data export (export capabilities for analysis)
    Storage_Types:
      - Time-series data (metrics and performance data)
      - Log data (structured and unstructured logs)
      - Trace data (distributed tracing information)
      - Event data (business and system events)
      - Metadata (system and application metadata)

  Synthetic_Monitoring:
    What_It_Is: "Proactive monitoring system that tests user journeys and APIs from multiple locations"
    Why_It_Exists: "Products need to detect issues before users do by actively testing system behavior"
    Interface: "KernelSyntheticMonitoring"
    Implementation: "KernelSyntheticMonitoringAdapter"
    Features:
      - Active monitoring (proactive user journey testing)
      - Transaction monitoring (end-to-end transaction flows)
      - Geographic monitoring (global performance testing)
      - Dependency monitoring (external service health)
      - Performance baselines (automated baseline establishment)
      - SLA monitoring (service level agreement tracking)
      - Competitive benchmarking (performance vs competitors)
      - User experience monitoring (real user simulation)
    Monitoring_Types:
      - API monitoring (endpoint availability and performance)
      - Web application monitoring (user interface testing)
      - Mobile application monitoring (app performance testing)
      - Network monitoring (connectivity and latency)
      - Database monitoring (query performance and availability)

  Anomaly_Detection_AI_Ops:
    What_It_Is: "AI-powered anomaly detection and automated operations system"
    Why_It_Exists: "Products need intelligent detection of unusual patterns and automated remediation"
    Interface: "KernelAnomalyDetection"
    Implementation: "KernelAnomalyDetectionAdapter"
    Features:
      - Machine learning anomaly detection (pattern recognition)
      - Predictive alerting (issues predicted before occurrence)
      - Root cause analysis (automated issue identification)
      - Self-healing (automated remediation actions)
      - Capacity planning (predictive resource optimization)
      - Performance optimization (automated tuning)
      - Security threat detection (anomaly-based security)
      - Business impact analysis (issue impact prediction)
    AI_Capabilities:
      - Pattern recognition (detect unusual patterns)
      - Predictive analytics (forecast potential issues)
      - Automated remediation (self-healing capabilities)
      - Optimization recommendations (performance and cost)
      - Anomaly correlation (cross-system correlation)

  Business_Intelligence_Analytics:
    What_It_Is: "Business intelligence and analytics platform for technical and business metrics"
    Why_It_Exists: "Products need to understand the business impact of technical decisions and user behavior"
    Interface: "KernelBusinessIntelligence"
    Implementation: "KernelBusinessIntelligenceAdapter"
    Features:
      - Business metrics tracking (KPI monitoring and dashboards)
      - User behavior analytics (journey analysis and optimization)
      - Revenue impact analysis (technical issues business impact)
      - Conversion tracking (end-to-end funnel monitoring)
      - A/B testing analytics (statistical analysis of experiments)
      - Cohort analysis (user segment analysis)
      - Feature adoption tracking (feature usage analytics)
      - Customer satisfaction monitoring (NPS and sentiment analysis)
    Analytics_Types:
      - Descriptive analytics (what happened)
      - Diagnostic analytics (why it happened)
      - Predictive analytics (what will happen)
      - Prescriptive analytics (what to do about it)

  Compliance_Audit_Observability:
    What_It_Is: "Compliance and audit observability system for regulatory requirements"
    Why_It_Exists: "Products need comprehensive monitoring for compliance and audit requirements"
    Interface: "KernelComplianceObservability"
    Implementation: "KernelComplianceObservabilityAdapter"
    Features:
      - Compliance monitoring (real-time compliance status)
      - Audit trail observability (comprehensive audit tracking)
      - Data privacy monitoring (personal data access tracking)
      - Security observability (security event monitoring)
      - Regulatory reporting (automated report generation)
      - Policy compliance monitoring (internal policy tracking)
      - Risk assessment (compliance risk analysis)
      - Evidence collection (audit evidence automation)
    Compliance_Standards:
      - HIPAA (healthcare data protection)
      - GDPR (privacy data protection)
      - SOX (financial reporting compliance)
      - PCI-DSS (payment card industry)
      - ISO 27001 (information security management)
      - NIST (cybersecurity framework)

  Cost_Observability:
    What_It_Is: "Cost monitoring and optimization system for cloud and infrastructure spending"
    Why_It_Exists: "Products need to understand and optimize their infrastructure costs"
    Interface: "KernelCostObservability"
    Implementation: "KernelCostObservabilityAdapter"
    Features:
      - Cloud cost monitoring (real-time spend tracking)
      - Resource cost attribution (cost by team/product/feature)
      - Cost anomaly detection (unusual spend patterns)
      - Budget monitoring (budget tracking and alerting)
      - ROI analysis (investment return analysis)
      - Cost optimization recommendations (spending optimization)
      - Reserved capacity analysis (reserved instance optimization)
      - Benchmarking (cost comparison with industry standards)
    Cost_Analytics:
      - Cost breakdown analysis (detailed cost categorization)
      - Trend analysis (cost trend monitoring)
      - Forecasting (cost prediction and budgeting)
      - Optimization opportunities (cost-saving recommendations)
      - Chargeback reporting (internal cost allocation)
      - Cost allocation modeling (custom cost allocation)
      - Cost variance analysis (cost variance detection)
```yaml
Technical_Gaps:
  Kernel_Primitives:
    Missing:
      - Canonical descriptor models
      - Universal registry implementation
      - Tenant context propagation
      - Lifecycle management
    Priority: "HIGH - Blocks all other phases"

  Data_Cloud_Adapters:
    Missing:
      - Event Store SPI implementation
      - Config Registry integration
      - Audit Service adapter
      - Data Governance integration
    Priority: "HIGH - Required for data management"

  AEP_Adapters:
    Missing:
      - Event Processing adapter
      - Agent Runtime adapter
      - Workflow Runtime adapter
      - Operator Catalog adapter
    Priority: "HIGH - Required for processing"

  Security:
    Missing:
      - Multi-tenant isolation
      - Product boundary enforcement
      - Plugin security validation
      - Data encryption at rest
    Priority: "HIGH - Required for compliance"
````

#### 0.3.2 Integration Gaps

**Cross-Product Integration Gaps:**

```yaml
Integration_Gaps:
  Event_Bus:
    Missing:
      - Cross-product event routing
      - Event transformation
      - Event filtering
      - Dead-letter handling
    Priority: "MEDIUM - Required for cross-product features"

  Data_Sharing:
    Missing:
      - Controlled data sharing
      - Data access policies
      - Data lineage tracking
      - Data synchronization
    Priority: "MEDIUM - Required for data integration"

  Workflow_Orchestration:
    Missing:
      - Cross-product workflows
      - Workflow composition
      - Workflow monitoring
      - Workflow error handling
    Priority: "LOW - Can be added later"
```

### 0.4 Implementation Risk Assessment

> **Cross-Reference**: See [KERNEL_PLATFORM_BRAINSTORM.md §Comprehensive Risk Registry](KERNEL_PLATFORM_BRAINSTORM.md) for the full architectural risk matrix (T-01 through T-10, A-01 through A-04, O-01 through O-03, C-01 through C-02) with technology rationale and detailed mitigations.

#### 0.4.1 High-Risk Areas

**Technical Risks:**

```yaml
Technical_Risks:
  Performance:
    Risk: "Kernel becomes performance bottleneck"
    Risk_ID: "T-01 (see Brainstorm Risk Registry)"
    Impact: "HIGH"
    Probability: "MEDIUM"
    Mitigation:
      - "<1ms overhead budget per kernel primitive"
      - "Zero-allocation Promise chains (ActiveJ)"
      - "Compile-time plugin resolution"
      - "eBPF eventloop stall detection (EbpfEventloopStallTracer)"
      - "Performance regression gates in CI (automated detection)"
    Validation: "Benchmark every kernel primitive against <1ms target in Phase 1"

  ActiveJ_Eventloop_Stall:
    Risk: "Blocking operation in Promise chain stalls entire eventloop"
    Risk_ID: "T-02 (see Brainstorm Risk Registry)"
    Impact: "HIGH"
    Probability: "MEDIUM"
    Mitigation:
      - "Enforce Promise.ofBlocking for ALL IO operations"
      - "eBPF stall tracer on all services with <2ms alerting"
      - "Static analysis rule: reject Thread.sleep/CompletableFuture.get inside Promises"
      - "ALL async tests MUST extend EventloopTestBase (build enforced)"
    Validation: "Stall detection test suite in Phase 1 CI"

  Security:
    Risk: "Cross-product data leakage"
    Risk_ID: "T-03 (see Brainstorm Risk Registry)"
    Impact: "HIGH"
    Probability: "LOW"
    Mitigation:
      - Strict tenant isolation at Data-Cloud and AEP layers
      - Comprehensive audit logging with immutable storage
      - Regular automated security scans (SAST, DAST, dependency audit)
      - Encryption at rest (AES-256) and in transit (TLS 1.3)
    Validation: "Penetration test in Phase 1 validates tenant isolation"

  AI_Cost_Overrun:
    Risk: "Uncontrolled LLM usage by agents generates unsustainable costs"
    Risk_ID: "T-07/X-01 (see Brainstorm Risk Registry)"
    Impact: "MEDIUM"
    Probability: "HIGH"
    Mitigation:
      - "Per-tenant/product LLM budgets at LLM Gateway"
      - "Semantic prompt caching (40-60% reduction)"
      - "Circuit breaker on agent reflection cycles (max 5)"
      - "Real-time cost attribution dashboards"
    Validation: "Cost tracking dashboard operational before Phase 2 AI integration"

  Agent_Memory_Growth:
    Risk: "Event-sourced agent memory grows unbounded"
    Risk_ID: "T-08/X-02 (see Brainstorm Risk Registry)"
    Impact: "MEDIUM"
    Probability: "MEDIUM"
    Mitigation:
      - "Memory retention policies per agent type"
      - "Episodic→semantic consolidation (automatic compaction)"
      - "Per-agent memory budgets with alerting"
      - "PII redaction before persistence (governance requirement)"
    Validation: "Memory growth benchmark before agent deployment"

  Complexity:
    Risk: "Kernel architecture too complex"
    Risk_ID: "A-03 (see Brainstorm Risk Registry)"
    Impact: "MEDIUM"
    Probability: "MEDIUM"
    Mitigation:
      - Clear separation of concerns (Opinionated Core model)
      - Comprehensive documentation with @doc tags on ALL public classes
      - Regular architecture reviews (every 2 weeks minimum)
      - "If only one product needs it, it's not a kernel feature" rule
    Validation: "Architecture review gate on all kernel PRs"
```

**Business Risks:**

```yaml
Business_Risks:
  Adoption:
    Risk: "Products resist kernel adoption"
    Risk_ID: "O-01 (see Brainstorm Risk Registry)"
    Impact: "HIGH"
    Probability: "MEDIUM"
    Mitigation:
      - "FlashIt-first adoption strategy for fast feedback"
      - "Side-by-side velocity demos (before/after kernel)"
      - "Product team involvement in kernel API design workshops"
      - "Escape hatches with review gates (not locked in)"
    Validation: "FlashIt team satisfaction survey after Phase 2"

  Knowledge_Concentration:
    Risk: "Only few engineers understand full kernel architecture"
    Risk_ID: "O-03 (see Brainstorm Risk Registry)"
    Impact: "MEDIUM"
    Probability: "HIGH"
    Mitigation:
      - "Comprehensive documentation (this document + Brainstorm)"
      - "Cross-training rotations (2 engineers per module minimum)"
      - "Architecture workshops (monthly)"
      - "Pair programming on all kernel changes"
    Validation: "Bus-factor >= 3 for every kernel module"

  Compliance:
    Risk: "Cross-product sharing violates regulations"
    Risk_ID: "C-01 (see Brainstorm Risk Registry)"
    Impact: "HIGH"
    Probability: "LOW"
    Mitigation:
      - Legal review of data sharing policies
      - Strict tenant-boundary validation in Data-Cloud
      - Comprehensive audit trails (immutable, 10-year retention for finance)
      - AI regulatory compliance (EU AI Act, FDA clinical AI, explainability)
    Validation: "Compliance audit checklist per phase gate"

  AI_Regulatory:
    Risk: "AI in healthcare/finance faces regulatory scrutiny"
    Risk_ID: "C-02 (see Brainstorm Risk Registry)"
    Impact: "HIGH"
    Probability: "MEDIUM"
    Mitigation:
      - "Model versioning and lineage in Model Registry"
      - "Safety evaluation gates for agent skill promotion"
      - "Human review for low-confidence decisions (<0.7)"
      - "Full audit trail of AI inputs/outputs/rationale"
    Validation: "AI audit trail completeness verified per phase"
```

---

## Phase 1: Kernel Core Definition (Weeks 1-2)

### 1.1 Detailed Kernel Primitives Design

#### 1.1.1 KernelDescriptor - Comprehensive Design

**Class Structure:**

```java
// Location: platform/java/kernel/src/main/java/com/ghatana/kernel/descriptor/
package com.ghatana.kernel.descriptor;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Canonical descriptor for all kernel components
 * Provides unified metadata model for modules, plugins, operators, and agents
 */
public class KernelDescriptor {
    // Core Identity
    private final String descriptorId;
    private final String name;
    private final String version;
    private final String description;
    private final String owner;

    // Classification
    private final DescriptorType type;
    private final Set<String> tags;
    private final Map<String, String> metadata;

    // Capabilities and Dependencies
    private final Set<KernelCapability> capabilities;
    private final Set<KernelDependency> dependencies;
    private final Set<KernelCompatibility> compatibility;

    // Lifecycle and Runtime
    private final LifecyclePolicy lifecyclePolicy;
    private final ResourceRequirements resourceRequirements;
    private final SecurityPolicy securityPolicy;

    // Tenant and Feature Management
    private final Set<String> supportedTenants;
    private final Set<String> requiredFeatures;
    private final Set<String> optionalFeatures;

    // Validation and Compliance
    private final List<ValidationRule> validationRules;
    private final Set<String> complianceRequirements;
    private final AuditPolicy auditPolicy;

    // Build and Deployment
    private final BuildInformation buildInfo;
    private final DeploymentConfiguration deploymentConfig;
    private final Instant createdAt;
    private final Instant updatedAt;

    public enum DescriptorType {
        KERNEL_MODULE,
        KERNEL_PLUGIN,
        OPERATOR,
        AGENT,
        WORKFLOW,
        CAPABILITY,
        EXTENSION
    }

    public static class Builder {
        private String descriptorId;
        private String name;
        private String version = "1.0.0";
        private String description;
        private String owner;
        private DescriptorType type;
        private Set<String> tags = new HashSet<>();
        private Map<String, String> metadata = new HashMap<>();
        private Set<KernelCapability> capabilities = new HashSet<>();
        private Set<KernelDependency> dependencies = new HashSet<>();
        private Set<KernelCompatibility> compatibility = new HashSet<>();
        private LifecyclePolicy lifecyclePolicy = LifecyclePolicy.DEFAULT;
        private ResourceRequirements resourceRequirements = ResourceRequirements.DEFAULT;
        private SecurityPolicy securityPolicy = SecurityPolicy.DEFAULT;
        private Set<String> supportedTenants = new HashSet<>();
        private Set<String> requiredFeatures = new HashSet<>();
        private Set<String> optionalFeatures = new HashSet<>();
        private List<ValidationRule> validationRules = new ArrayList<>();
        private Set<String> complianceRequirements = new HashSet<>();
        private AuditPolicy auditPolicy = AuditPolicy.DEFAULT;
        private BuildInformation buildInfo;
        private DeploymentConfiguration deploymentConfig = DeploymentConfiguration.DEFAULT;

        public Builder withDescriptorId(String descriptorId) {
            this.descriptorId = Objects.requireNonNull(descriptorId);
            return this;
        }

        public Builder withName(String name) {
            this.name = Objects.requireNonNull(name);
            return this;
        }

        public Builder withVersion(String version) {
            this.version = Objects.requireNonNull(version);
            return this;
        }

        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder withOwner(String owner) {
            this.owner = Objects.requireNonNull(owner);
            return this;
        }

        public Builder withType(DescriptorType type) {
            this.type = Objects.requireNonNull(type);
            return this;
        }

        public Builder withTag(String tag) {
            this.tags.add(tag);
            return this;
        }

        public Builder withTags(Collection<String> tags) {
            this.tags.addAll(tags);
            return this;
        }

        public Builder withMetadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder withMetadata(Map<String, String> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        public Builder withCapability(KernelCapability capability) {
            this.capabilities.add(capability);
            return this;
        }

        public Builder withCapabilities(Collection<KernelCapability> capabilities) {
            this.capabilities.addAll(capabilities);
            return this;
        }

        public Builder withDependency(KernelDependency dependency) {
            this.dependencies.add(dependency);
            return this;
        }

        public Builder withDependencies(Collection<KernelDependency> dependencies) {
            this.dependencies.addAll(dependencies);
            return this;
        }

        public Builder withCompatibility(KernelCompatibility compatibility) {
            this.compatibility.add(compatibility);
            return this;
        }

        public Builder withLifecyclePolicy(LifecyclePolicy lifecyclePolicy) {
            this.lifecyclePolicy = Objects.requireNonNull(lifecyclePolicy);
            return this;
        }

        public Builder withResourceRequirements(ResourceRequirements resourceRequirements) {
            this.resourceRequirements = Objects.requireNonNull(resourceRequirements);
            return this;
        }

        public Builder withSecurityPolicy(SecurityPolicy securityPolicy) {
            this.securityPolicy = Objects.requireNonNull(securityPolicy);
            return this;
        }

        public Builder withSupportedTenant(String tenantId) {
            this.supportedTenants.add(tenantId);
            return this;
        }

        public Builder withRequiredFeature(String featureId) {
            this.requiredFeatures.add(featureId);
            return this;
        }

        public Builder withOptionalFeature(String featureId) {
            this.optionalFeatures.add(featureId);
            return this;
        }

        public Builder withValidationRule(ValidationRule rule) {
            this.validationRules.add(rule);
            return this;
        }

        public Builder withComplianceRequirement(String requirement) {
            this.complianceRequirements.add(requirement);
            return this;
        }

        public Builder withAuditPolicy(AuditPolicy auditPolicy) {
            this.auditPolicy = Objects.requireNonNull(auditPolicy);
            return this;
        }

        public Builder withBuildInfo(BuildInformation buildInfo) {
            this.buildInfo = Objects.requireNonNull(buildInfo);
            return this;
        }

        public Builder withDeploymentConfig(DeploymentConfiguration deploymentConfig) {
            this.deploymentConfig = Objects.requireNonNull(deploymentConfig);
            return this;
        }

        public KernelDescriptor build() {
            validate();
            return new KernelDescriptor(this);
        }

        private void validate() {
            if (descriptorId == null || descriptorId.trim().isEmpty()) {
                throw new IllegalArgumentException("Descriptor ID is required");
            }
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Name is required");
            }
            if (owner == null || owner.trim().isEmpty()) {
                throw new IllegalArgumentException("Owner is required");
            }
            if (type == null) {
                throw new IllegalArgumentException("Type is required");
            }
            if (version == null || !version.matches("\\d+\\.\\d+\\.\\d+.*")) {
                throw new IllegalArgumentException("Invalid version format");
            }
        }
    }

    private KernelDescriptor(Builder builder) {
        this.descriptorId = builder.descriptorId;
        this.name = builder.name;
        this.version = builder.version;
        this.description = builder.description;
        this.owner = builder.owner;
        this.type = builder.type;
        this.tags = Collections.unmodifiableSet(new HashSet<>(builder.tags));
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
        this.capabilities = Collections.unmodifiableSet(new HashSet<>(builder.capabilities));
        this.dependencies = Collections.unmodifiableSet(new HashSet<>(builder.dependencies));
        this.compatibility = Collections.unmodifiableSet(new HashSet<>(builder.compatibility));
        this.lifecyclePolicy = builder.lifecyclePolicy;
        this.resourceRequirements = builder.resourceRequirements;
        this.securityPolicy = builder.securityPolicy;
        this.supportedTenants = Collections.unmodifiableSet(new HashSet<>(builder.supportedTenants));
        this.requiredFeatures = Collections.unmodifiableSet(new HashSet<>(builder.requiredFeatures));
        this.optionalFeatures = Collections.unmodifiableSet(new HashSet<>(builder.optionalFeatures));
        this.validationRules = Collections.unmodifiableList(new ArrayList<>(builder.validationRules));
        this.complianceRequirements = Collections.unmodifiableSet(new HashSet<>(builder.complianceRequirements));
        this.auditPolicy = builder.auditPolicy;
        this.buildInfo = builder.buildInfo;
        this.deploymentConfig = builder.deploymentConfig;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters
    public String getDescriptorId() { return descriptorId; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public String getOwner() { return owner; }
    public DescriptorType getType() { return type; }
    public Set<String> getTags() { return tags; }
    public Map<String, String> getMetadata() { return metadata; }
    public Set<KernelCapability> getCapabilities() { return capabilities; }
    public Set<KernelDependency> getDependencies() { return dependencies; }
    public Set<KernelCompatibility> getCompatibility() { return compatibility; }
    public LifecyclePolicy getLifecyclePolicy() { return lifecyclePolicy; }
    public ResourceRequirements getResourceRequirements() { return resourceRequirements; }
    public SecurityPolicy getSecurityPolicy() { return securityPolicy; }
    public Set<String> getSupportedTenants() { return supportedTenants; }
    public Set<String> getRequiredFeatures() { return requiredFeatures; }
    public Set<String> getOptionalFeatures() { return optionalFeatures; }
    public List<ValidationRule> getValidationRules() { return validationRules; }
    public Set<String> getComplianceRequirements() { return complianceRequirements; }
    public AuditPolicy getAuditPolicy() { return auditPolicy; }
    public BuildInformation getBuildInfo() { return buildInfo; }
    public DeploymentConfiguration getDeploymentConfig() { return deploymentConfig; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Business methods
    public boolean hasCapability(KernelCapability capability) {
        return capabilities.contains(capability);
    }

    public boolean supportsTenant(String tenantId) {
        return supportedTenants.isEmpty() || supportedTenants.contains(tenantId);
    }

    public boolean requiresFeature(String featureId) {
        return requiredFeatures.contains(featureId);
    }

    public boolean isCompatibleWith(String kernelVersion) {
        return compatibility.stream()
            .anyMatch(comp -> comp.isCompatible(kernelVersion));
    }

    public boolean meetsComplianceRequirement(String requirement) {
        return complianceRequirements.contains(requirement);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KernelDescriptor that = (KernelDescriptor) o;
        return Objects.equals(descriptorId, that.descriptorId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptorId);
    }

    @Override
    public String toString() {
        return String.format("KernelDescriptor{id=%s, name=%s, version=%s, type=%s}",
            descriptorId, name, version, type);
    }
}
```

**Supporting Classes:**

```java
// Location: platform/java/kernel/src/main/java/com/ghatana/kernel/descriptor/

public class KernelCapability {
    private final String capabilityId;
    private final String name;
    private final String description;
    private final CapabilityType type;
    private final Map<String, String> metadata;

    public enum CapabilityType {
        DATA_MANAGEMENT,
        EVENT_PROCESSING,
        SECURITY,
        COMPLIANCE,
        AI_ML,
        WORKFLOW,
        INTEGRATION,
        MONITORING
    }

    // Predefined capabilities
    public static final KernelCapability PATIENT_RECORDS = new KernelCapability(
        "patient.records", "Patient Records Management", CapabilityType.DATA_MANAGEMENT);
    public static final KernelCapability CONSENT_MANAGEMENT = new KernelCapability(
        "consent.management", "Consent Management", CapabilityType.SECURITY);
    public static final KernelCapability TRADE_PROCESSING = new KernelCapability(
        "trade.processing", "Trade Processing", CapabilityType.EVENT_PROCESSING);
    public static final KernelCapability RISK_MANAGEMENT = new KernelCapability(
        "risk.management", "Risk Management", CapabilityType.AI_ML);

    // Constructors and methods...
}

public class KernelDependency {
    private final String dependencyId;
    private final String version;
    private final DependencyType type;
    private final boolean optional;

    public enum DependencyType {
        MODULE,
        PLUGIN,
        EXTERNAL_SERVICE,
        LIBRARY,
        CAPABILITY
    }

    // Constructors and methods...
}

public class LifecyclePolicy {
    private final boolean autoStart;
    private final boolean autoRestart;
    private final int restartAttempts;
    private final Duration restartDelay;
    private final ShutdownPolicy shutdownPolicy;

    public enum ShutdownPolicy {
        GRACEFUL,
        FORCEFUL,
        DELAYED
    }

    public static final LifecyclePolicy DEFAULT = new LifecyclePolicy(true, true, 3, Duration.ofSeconds(5), ShutdownPolicy.GRACEFUL);
}

public class ResourceRequirements {
    private final MemoryRequirements memory;
    private final CpuRequirements cpu;
    private final StorageRequirements storage;
    private final NetworkRequirements network;

    public static final ResourceRequirements DEFAULT = new ResourceRequirements(
        MemoryRequirements.MINIMAL, CpuRequirements.MINIMAL, StorageRequirements.MINIMAL, NetworkRequirements.MINIMAL);
}

public class SecurityPolicy {
    private final boolean requiresIsolation;
    private final Set<String> allowedPermissions;
    private final Set<String> deniedPermissions;
    private final EncryptionLevel encryptionLevel;

    public enum EncryptionLevel {
        NONE, BASIC, STANDARD, STRONG, MAXIMUM
    }

    public static final SecurityPolicy DEFAULT = new SecurityPolicy(false, Set.of(), Set.of(), EncryptionLevel.STANDARD);
}
```

#### 1.1.2 KernelModule - Comprehensive Design

**Interface Definition:**

```java
// Location: platform/java/kernel/src/main/java/com/ghatana/kernel/module/
package com.ghatana.kernel.module;

import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.tenant.KernelTenantContext;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.metrics.MetricsCollector;
import com.ghatana.kernel.config.KernelConfigResolver;

import io.activej.promise.Promise;
import java.util.Set;
import java.util.Optional;

/**
 * Core interface for all kernel modules.
 * Provides standardized lifecycle, capability, and integration patterns.
 *
 * <p><b>IMPORTANT</b>: All async operations MUST use ActiveJ {@link Promise},
 * never {@code CompletableFuture} or Reactor types. See copilot-instructions §3.</p>
 *
 * @doc.type interface
 * @doc.purpose Core module contract defining lifecycle, capabilities, and integration hooks
 * @doc.layer core
 * @doc.pattern Service
 */
public interface KernelModule {

    /**
     * Unique module identifier
     */
    String getModuleId();

    /**
     * Module version (semver)
     */
    String getVersion();

    /**
     * Module descriptor with metadata
     */
    KernelDescriptor getDescriptor();

    /**
     * Capabilities provided by this module
     */
    Set<KernelCapability> getCapabilities();

    /**
     * Dependencies required by this module
     */
    Set<KernelDependency> getDependencies();

    /**
     * Initialize the module with kernel context
     */
    void initialize(KernelContext context) throws ModuleInitializationException;

    /**
     * Start the module asynchronously (ActiveJ Promise — NEVER CompletableFuture)
     */
    Promise<Void> start() throws ModuleStartException;

    /**
     * Stop the module asynchronously (ActiveJ Promise — NEVER CompletableFuture)
     */
    Promise<Void> stop() throws ModuleStopException;

    /**
     * Get current health status
     */
    HealthStatus getHealthStatus();

    /**
     * Check if module is ready to serve requests
     */
    boolean isReady();

    /**
     * Get module-specific metrics
     */
    MetricsCollector getMetrics();

    /**
     * Handle configuration changes
     */
    void onConfigurationChanged(KernelConfigResolver configResolver);

    /**
     * Handle tenant context changes
     */
    void onTenantContextChanged(KernelTenantContext tenantContext);

    /**
     * Cleanup resources
     */
    void cleanup();

    /**
     * Get module-specific configuration schema
     */
    Optional<String> getConfigurationSchema();

    /**
     * Validate module configuration
     */
    ValidationResult validateConfiguration(Map<String, Object> configuration);

    /**
     * Get module-specific API contracts
     */
    Set<ApiContract> getApiContracts();

    /**
     * Handle module-specific events
     */
    void handleEvent(KernelEvent event);
}

/**
 * Abstract base class for kernel modules.
 * Provides common functionality and default implementations.
 *
 * <p><b>CRITICAL</b>: Do NOT use {@code CompletableFuture} anywhere in subclasses.
 * All async work MUST go through ActiveJ {@link Promise}. For blocking IO,
 * wrap with {@code Promise.ofBlocking(executor, () -> ...)}.</p>
 *
 * @doc.type class
 * @doc.purpose Base implementation providing lifecycle, metrics, and health for kernel modules
 * @doc.layer core
 * @doc.pattern Service
 */
public abstract class AbstractKernelModule implements KernelModule {

    protected final KernelDescriptor descriptor;
    protected final Set<KernelCapability> capabilities;
    protected final Set<KernelDependency> dependencies;
    protected final MetricsCollector metrics;
    protected final Logger logger;

    protected KernelContext context;
    protected volatile HealthStatus healthStatus;
    protected volatile boolean started;
    protected volatile boolean ready;

    protected AbstractKernelModule(KernelDescriptor descriptor,
                                 Set<KernelCapability> capabilities,
                                 Set<KernelDependency> dependencies) {
        this.descriptor = Objects.requireNonNull(descriptor);
        this.capabilities = Collections.unmodifiableSet(new HashSet<>(capabilities));
        this.dependencies = Collections.unmodifiableSet(new HashSet<>(dependencies));
        this.metrics = new DefaultMetricsCollector(descriptor.getDescriptorId());
        this.logger = LoggerFactory.getLogger(getClass());
        this.healthStatus = HealthStatus.UNKNOWN;
        this.started = false;
        this.ready = false;
    }

    @Override
    public String getModuleId() {
        return descriptor.getDescriptorId();
    }

    @Override
    public String getVersion() {
        return descriptor.getVersion();
    }

    @Override
    public KernelDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return capabilities;
    }

    @Override
    public Set<KernelDependency> getDependencies() {
        return dependencies;
    }

    @Override
    public void initialize(KernelContext context) throws ModuleInitializationException {
        this.context = Objects.requireNonNull(context);

        try {
            logger.info("Initializing module: {}", getModuleId());

            // Validate dependencies
            validateDependencies();

            // Initialize module-specific components
            doInitialize();

            // Update health status
            healthStatus = HealthStatus.INITIALIZED;
            metrics.incrementCounter("module.initialized");

            logger.info("Module initialized successfully: {}", getModuleId());

        } catch (Exception e) {
            healthStatus = HealthStatus.FAILED;
            metrics.incrementCounter("module.initialization.failed");
            throw new ModuleInitializationException("Failed to initialize module: " + getModuleId(), e);
        }
    }

    @Override
    public Promise<Void> start() throws ModuleStartException {
        if (started) {
            return Promise.complete();
        }

        return Promise.ofBlocking(executor, () -> {
            logger.info("Starting module: {}", getModuleId());

            // Start module-specific components
            doStart();

            started = true;
            ready = true;
            healthStatus = HealthStatus.HEALTHY;
            metrics.incrementCounter("module.started");

            logger.info("Module started successfully: {}", getModuleId());
            return null;
        }).whenException(e -> {
            healthStatus = HealthStatus.FAILED;
            metrics.incrementCounter("module.start.failed");
        });
    }

    @Override
    public Promise<Void> stop() throws ModuleStopException {
        if (!started) {
            return Promise.complete();
        }

        return Promise.ofBlocking(executor, () -> {
            logger.info("Stopping module: {}", getModuleId());

            // Stop module-specific components
            doStop();

            started = false;
            ready = false;
            healthStatus = HealthStatus.STOPPED;
            metrics.incrementCounter("module.stopped");

            logger.info("Module stopped successfully: {}", getModuleId());
            return null;
        }).whenException(e -> {
            healthStatus = HealthStatus.FAILED;
            metrics.incrementCounter("module.stop.failed");
        });
    }

    @Override
    public HealthStatus getHealthStatus() {
        return healthStatus;
    }

    @Override
    public boolean isReady() {
        return ready && started && healthStatus == HealthStatus.HEALTHY;
    }

    @Override
    public MetricsCollector getMetrics() {
        return metrics;
    }

    @Override
    public void onConfigurationChanged(KernelConfigResolver configResolver) {
        try {
            logger.info("Configuration changed for module: {}", getModuleId());
            doOnConfigurationChanged(configResolver);
            metrics.incrementCounter("module.configuration.changed");
        } catch (Exception e) {
            logger.error("Failed to handle configuration change for module: {}", getModuleId(), e);
            metrics.incrementCounter("module.configuration.change.failed");
        }
    }

    @Override
    public void onTenantContextChanged(KernelTenantContext tenantContext) {
        try {
            logger.info("Tenant context changed for module: {}", getModuleId());
            doOnTenantContextChanged(tenantContext);
            metrics.incrementCounter("module.tenant.context.changed");
        } catch (Exception e) {
            logger.error("Failed to handle tenant context change for module: {}", getModuleId(), e);
            metrics.incrementCounter("module.tenant.context.change.failed");
        }
    }

    @Override
    public void cleanup() {
        try {
            logger.info("Cleaning up module: {}", getModuleId());
            doCleanup();
            metrics.incrementCounter("module.cleaned.up");
        } catch (Exception e) {
            logger.error("Failed to cleanup module: {}", getModuleId(), e);
            metrics.incrementCounter("module.cleanup.failed");
        }
    }

    @Override
    public Optional<String> getConfigurationSchema() {
        return Optional.ofNullable(doGetConfigurationSchema());
    }

    @Override
    public ValidationResult validateConfiguration(Map<String, Object> configuration) {
        try {
            return doValidateConfiguration(configuration);
        } catch (Exception e) {
            return ValidationResult.failed("Configuration validation failed: " + e.getMessage());
        }
    }

    @Override
    public Set<ApiContract> getApiContracts() {
        return doGetApiContracts();
    }

    @Override
    public void handleEvent(KernelEvent event) {
        try {
            doHandleEvent(event);
            metrics.incrementCounter("module.event.handled");
        } catch (Exception e) {
            logger.error("Failed to handle event for module: {}", getModuleId(), e);
            metrics.incrementCounter("module.event.handle.failed");
        }
    }

    // Abstract methods for subclasses to implement
    protected abstract void doInitialize() throws Exception;
    protected abstract void doStart() throws Exception;
    protected abstract void doStop() throws Exception;
    protected abstract void doCleanup() throws Exception;

    // Optional methods with default implementations
    protected void doOnConfigurationChanged(KernelConfigResolver configResolver) {
        // Default: no-op
    }

    protected void doOnTenantContextChanged(KernelTenantContext tenantContext) {
        // Default: no-op
    }

    protected String doGetConfigurationSchema() {
        return null; // No schema by default
    }

    protected ValidationResult doValidateConfiguration(Map<String, Object> configuration) {
        return ValidationResult.success(); // Valid by default
    }

    protected Set<ApiContract> doGetApiContracts() {
        return Set.of(); // No contracts by default
    }

    protected void doHandleEvent(KernelEvent event) {
        // Default: no-op
    }

    // Helper methods
    protected void validateDependencies() throws ModuleInitializationException {
        for (KernelDependency dependency : dependencies) {
            if (!context.hasDependency(dependency)) {
                throw new ModuleInitializationException(
                    "Missing dependency: " + dependency.getDependencyId());
            }
        }
    }

    protected <T> T getDependency(Class<T> type, String dependencyId) {
        return context.getDependency(type, dependencyId);
    }

    protected <T> Optional<T> getOptionalDependency(Class<T> type, String dependencyId) {
        return context.getOptionalDependency(type, dependencyId);
    }
}
```

#### 1.1.2a KernelExtension - Extension Point Interface

> **Gap filled**: `KernelExtension` is referenced by product-level extensions (e.g.,
> `DualCalendarKernelExtension` in Finance) but was never formally defined.

```java
// Location: platform/java/kernel/src/main/java/com/ghatana/kernel/extension/
package com.ghatana.kernel.extension;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.descriptor.KernelCapability;

import java.util.Set;

/**
 * Extension point for product-specific kernel enhancements.
 *
 * <p>Unlike {@link com.ghatana.kernel.module.KernelModule}, an extension does
 * not own its own lifecycle — it is loaded and managed by the module it extends.
 * Extensions contribute capabilities and intercept lifecycle events but cannot
 * be started/stopped independently.</p>
 *
 * @doc.type interface
 * @doc.purpose Extension point contract allowing products to contribute capabilities to a kernel module
 * @doc.layer core
 * @doc.pattern Service
 */
public interface KernelExtension {

    /** Unique extension identifier (e.g., "finance-dual-calendar"). */
    String getExtensionId();

    /** Human-readable name. */
    String getName();

    /** Descriptor metadata for this extension. */
    KernelDescriptor getDescriptor();

    /** Additional capabilities this extension contributes to its host module. */
    Set<KernelCapability> getContributedCapabilities();

    /** Called when the host module initializes — attach hooks here. */
    void onModuleInitialized(KernelContext context);

    /** Called when the host module starts. */
    void onModuleStarted(KernelContext context);

    /** Called when the host module stops — detach hooks / release resources. */
    void onModuleStopped(KernelContext context);

    /** Indicates if this extension is compatible with the given kernel version. */
    boolean isCompatible(String kernelVersion);
}
```

#### 1.1.2b KernelContext - Kernel Execution Context

> **Gap filled**: `KernelContext` is used in virtually every Phase 1–3 code example
> (`context.getDependency(...)`, `context.registerEventHandler(...)`, etc.) but was
> never formally defined as a class.

```java
// Location: platform/java/kernel/src/main/java/com/ghatana/kernel/context/
package com.ghatana.kernel.context;

import io.activej.eventloop.Eventloop;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.event.KernelEventHandler;
import com.ghatana.kernel.tenant.KernelTenantContext;

import java.util.Optional;
import java.util.Set;

/**
 * Runtime context available to every kernel module during its lifecycle.
 *
 * <p>Provides dependency lookup, event-handler registration, and access to
 * the current {@link KernelTenantContext}. Each module receives its own
 * sandboxed context instance.</p>
 *
 * @doc.type interface
 * @doc.purpose Runtime context providing dependency lookup, event registration, and tenant access
 * @doc.layer core
 * @doc.pattern Service
 */
public interface KernelContext {

    // ─── Dependency Lookup ───────────────────────────────────────────

    /**
     * Retrieve a required dependency by type and id.
     *
     * @throws com.ghatana.kernel.exception.DependencyNotFoundException if the dependency is missing
     */
    <T> T getDependency(Class<T> type, String dependencyId);

    /** Retrieve an optional dependency — returns empty if not registered. */
    <T> Optional<T> getOptionalDependency(Class<T> type, String dependencyId);

    /** Check whether a dependency is registered. */
    boolean hasDependency(String dependencyId);

    // ─── Event System ────────────────────────────────────────────────

    /** Register an event handler for a given event type. */
    void registerEventHandler(String eventType, KernelEventHandler handler);

    /** Unregister a previously registered event handler. */
    void unregisterEventHandler(String eventType, KernelEventHandler handler);

    // ─── Tenant & Runtime ────────────────────────────────────────────

    /** Current tenant context (never null during a module lifecycle call). */
    KernelTenantContext getTenantContext();

    /** The ActiveJ Eventloop this context is bound to. */
    Eventloop getEventloop();

    /** Capabilities available in the current kernel instance. */
    Set<KernelCapability> getAvailableCapabilities();
}
```

#### 1.1.3 KernelPlugin - Comprehensive Design

**Interface Definition:**

```java
// Location: platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/
package com.ghatana.kernel.plugin;

import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.descriptor.PluginManifest;
import com.ghatana.kernel.security.PluginSecurityContext;

/**
 * Interface for kernel plugins
 * Extends KernelModule with plugin-specific capabilities
 */
public interface KernelPlugin extends KernelModule {

    /**
     * Plugin manifest with metadata and security information
     */
    PluginManifest getManifest();

    /**
     * Security context for the plugin
     */
    PluginSecurityContext getSecurityContext();

    /**
     * Contracts exported by this plugin
     */
    Set<String> getExportedContracts();

    /**
     * Contracts required by this plugin
     */
    Set<String> getRequiredContracts();

    /**
     * Install the plugin
     */
    void install() throws PluginInstallationException;

    /**
     * Uninstall the plugin
     */
    void uninstall() throws PluginUninstallationException;

    /**
     * Reload the plugin
     */
    void reload() throws PluginReloadException;

    /**
     * Get plugin-specific configuration
     */
    PluginConfiguration getConfiguration();

    /**
     * Update plugin configuration
     */
    void updateConfiguration(PluginConfiguration configuration) throws PluginConfigurationException;

    /**
     * Get plugin resources
     */
    PluginResources getResources();

    /**
     * Check if plugin is compatible with kernel version
     */
    boolean isCompatible(String kernelVersion);

    /**
     * Get plugin version information
     */
    PluginVersionInfo getVersionInfo();
}

/**
 * Abstract base class for kernel plugins
 * Provides common plugin functionality
 */
public abstract class AbstractKernelPlugin extends AbstractKernelModule implements KernelPlugin {

    protected final PluginManifest manifest;
    protected final PluginSecurityContext securityContext;
    protected final Set<String> exportedContracts;
    protected final Set<String> requiredContracts;
    protected PluginConfiguration configuration;
    protected PluginResources resources;

    protected AbstractKernelPlugin(PluginManifest manifest,
                                 Set<KernelCapability> capabilities,
                                 Set<KernelDependency> dependencies,
                                 Set<String> exportedContracts,
                                 Set<String> requiredContracts) {
        super(manifest.getDescriptor(), capabilities, dependencies);
        this.manifest = Objects.requireNonNull(manifest);
        this.securityContext = new PluginSecurityContext(manifest);
        this.exportedContracts = Collections.unmodifiableSet(new HashSet<>(exportedContracts));
        this.requiredContracts = Collections.unmodifiableSet(new HashSet<>(requiredContracts));
        this.configuration = PluginConfiguration.DEFAULT;
        this.resources = PluginResources.DEFAULT;
    }

    @Override
    public PluginManifest getManifest() {
        return manifest;
    }

    @Override
    public PluginSecurityContext getSecurityContext() {
        return securityContext;
    }

    @Override
    public Set<String> getExportedContracts() {
        return exportedContracts;
    }

    @Override
    public Set<String> getRequiredContracts() {
        return requiredContracts;
    }

    @Override
    public void install() throws PluginInstallationException {
        try {
            logger.info("Installing plugin: {}", getModuleId());

            // Validate security
            securityContext.validate();

            // Install plugin-specific components
            doInstall();

            // Register contracts
            registerContracts();

            metrics.incrementCounter("plugin.installed");
            logger.info("Plugin installed successfully: {}", getModuleId());

        } catch (Exception e) {
            metrics.incrementCounter("plugin.installation.failed");
            throw new PluginInstallationException("Failed to install plugin: " + getModuleId(), e);
        }
    }

    @Override
    public void uninstall() throws PluginUninstallationException {
        try {
            logger.info("Uninstalling plugin: {}", getModuleId());

            // Unregister contracts
            unregisterContracts();

            // Uninstall plugin-specific components
            doUninstall();

            metrics.incrementCounter("plugin.uninstalled");
            logger.info("Plugin uninstalled successfully: {}", getModuleId());

        } catch (Exception e) {
            metrics.incrementCounter("plugin.uninstallation.failed");
            throw new PluginUninstallationException("Failed to uninstall plugin: " + getModuleId(), e);
        }
    }

    @Override
    public void reload() throws PluginReloadException {
        try {
            logger.info("Reloading plugin: {}", getModuleId());

            // Stop plugin
            stop().get();

            // Uninstall
            uninstall();

            // Reinstall
            install();

            // Start plugin
            start().get();

            metrics.incrementCounter("plugin.reloaded");
            logger.info("Plugin reloaded successfully: {}", getModuleId());

        } catch (Exception e) {
            metrics.incrementCounter("plugin.reload.failed");
            throw new PluginReloadException("Failed to reload plugin: " + getModuleId(), e);
        }
    }

    @Override
    public PluginConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void updateConfiguration(PluginConfiguration configuration) throws PluginConfigurationException {
        try {
            logger.info("Updating configuration for plugin: {}", getModuleId());

            // Validate new configuration
            ValidationResult result = validateConfiguration(configuration.asMap());
            if (!result.isValid()) {
                throw new PluginConfigurationException("Invalid configuration: " + result.getErrorMessage());
            }

            // Update configuration
            PluginConfiguration oldConfiguration = this.configuration;
            this.configuration = configuration;

            // Apply new configuration
            doOnConfigurationChanged(configuration);

            metrics.incrementCounter("plugin.configuration.updated");
            logger.info("Configuration updated successfully for plugin: {}", getModuleId());

        } catch (Exception e) {
            metrics.incrementCounter("plugin.configuration.update.failed");
            throw new PluginConfigurationException("Failed to update configuration for plugin: " + getModuleId(), e);
        }
    }

    @Override
    public PluginResources getResources() {
        return resources;
    }

    @Override
    public boolean isCompatible(String kernelVersion) {
        return manifest.getCompatibility().stream()
            .anyMatch(comp -> comp.isCompatible(kernelVersion));
    }

    @Override
    public PluginVersionInfo getVersionInfo() {
        return new PluginVersionInfo(
            manifest.getVersion(),
            manifest.getBuildInfo(),
            manifest.getDependencies(),
            manifest.getCompatibility()
        );
    }

    // Abstract methods for subclasses
    protected abstract void doInstall() throws Exception;
    protected abstract void doUninstall() throws Exception;
    protected abstract void doOnConfigurationChanged(PluginConfiguration configuration) throws Exception;

    // Helper methods
    private void registerContracts() {
        for (String contract : exportedContracts) {
            context.registerContract(getModuleId(), contract);
        }
    }

    private void unregisterContracts() {
        for (String contract : exportedContracts) {
            context.unregisterContract(getModuleId(), contract);
        }
    }
}
```

### 1.2 Detailed Integration with Existing Systems

#### 1.2.1 Data-Cloud Integration - Complete Implementation

**Data-Cloud Adapter Factory:**

```java
// Location: platform/java/kernel/src/main/java/com/ghatana/kernel/datacloud/
package com.ghatana.kernel.datacloud;

import com.ghatana.datacloud.platform.DataCloudPlatform;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.config.ConfigRegistry;
import com.ghatana.datacloud.audit.AuditService;
import com.ghatana.datacloud.governance.DataGovernanceService;
import com.ghatana.kernel.event.KernelEventStore;
import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.audit.KernelAuditService;
import com.ghatana.kernel.governance.KernelDataGovernance;
import com.ghatana.kernel.tenant.KernelTenantContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Factory for creating Data-Cloud adapters for kernel components
 * Provides unified interface to Data-Cloud services
 */
public class DataCloudKernelAdapterFactory {

    private final DataCloudPlatform dataCloud;
    private final Map<String, KernelEventStore> eventStoreCache;
    private final Map<String, KernelConfigResolver> configResolverCache;
    private final Map<String, KernelAuditService> auditServiceCache;
    private final Map<String, KernelDataGovernance> governanceServiceCache;

    public DataCloudKernelAdapterFactory(DataCloudPlatform dataCloud) {
        this.dataCloud = Objects.requireNonNull(dataCloud);
        this.eventStoreCache = new ConcurrentHashMap<>();
        this.configResolverCache = new ConcurrentHashMap<>();
        this.auditServiceCache = new ConcurrentHashMap<>();
        this.governanceServiceCache = new ConcurrentHashMap<>();
    }

    /**
     * Create kernel event store adapter for tenant
     */
    public KernelEventStore createEventStore(String tenantId) {
        return eventStoreCache.computeIfAbsent(tenantId, this::createEventStoreAdapter);
    }

    /**
     * Create kernel config resolver adapter for tenant
     */
    public KernelConfigResolver createConfigResolver(String tenantId) {
        return configResolverCache.computeIfAbsent(tenantId, this::createConfigResolverAdapter);
    }

    /**
     * Create kernel audit service adapter for tenant
     */
    public KernelAuditService createAuditService(String tenantId) {
        return auditServiceCache.computeIfAbsent(tenantId, this::createAuditServiceAdapter);
    }

    /**
     * Create kernel data governance adapter for tenant
     */
    public KernelDataGovernance createDataGovernance(String tenantId) {
        return governanceServiceCache.computeIfAbsent(tenantId, this::createDataGovernanceAdapter);
    }

    private KernelEventStore createEventStoreAdapter(String tenantId) {
        EventLogStore dataCloudEventStore = dataCloud.getEventLogStore(tenantId);
        return new DataCloudEventStoreAdapter(dataCloudEventStore, tenantId);
    }

    private KernelConfigResolver createConfigResolverAdapter(String tenantId) {
        ConfigRegistry dataCloudConfigRegistry = dataCloud.getConfigRegistry(tenantId);
        return new DataCloudConfigAdapter(dataCloudConfigRegistry, tenantId);
    }

    private KernelAuditService createAuditServiceAdapter(String tenantId) {
        AuditService dataCloudAuditService = dataCloud.getAuditService(tenantId);
        return new DataCloudAuditAdapter(dataCloudAuditService, tenantId);
    }

    private KernelDataGovernance createDataGovernanceAdapter(String tenantId) {
        DataGovernanceService dataCloudGovernance = dataCloud.getDataGovernanceService(tenantId);
        return new DataCloudGovernanceAdapter(dataCloudGovernance, tenantId);
    }

    /**
     * Clear all cached adapters for tenant
     */
    public void clearTenantCache(String tenantId) {
        eventStoreCache.remove(tenantId);
        configResolverCache.remove(tenantId);
        auditServiceCache.remove(tenantId);
        governanceServiceCache.remove(tenantId);
    }

    /**
     * Get statistics about adapter usage
     */
    public DataCloudAdapterStatistics getStatistics() {
        return new DataCloudAdapterStatistics(
            eventStoreCache.size(),
            configResolverCache.size(),
            auditServiceCache.size(),
            governanceServiceCache.size()
        );
    }
}
```

**Event Store Adapter Implementation:**

> **ASYNC CONVENTION**: All adapter methods below use ActiveJ `Promise` — never `CompletableFuture`.
> If the underlying Data-Cloud SPI returns `CompletableFuture`, wrap it with
> `Promise.ofFuture(cfInstance)` at the adapter boundary. See §3 of `copilot-instructions.md`.

```java
// Location: platform/java/kernel/src/main/java/com/ghatana/kernel/datacloud/
package com.ghatana.kernel.datacloud;

import io.activej.promise.Promise;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.kernel.event.KernelEventStore;
import com.ghatana.kernel.event.KernelEvent;
import com.ghatana.kernel.event.EventStream;
import com.ghatana.kernel.event.EventQuery;
import com.ghatana.kernel.event.EventBatch;
import com.ghatana.kernel.exception.KernelEventException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Adapter bridging Data-Cloud EventLogStore to KernelEventStore.
 *
 * <p>All async results are returned as ActiveJ {@link Promise}. If the
 * underlying Data-Cloud SPI still exposes {@code CompletableFuture},
 * use {@code Promise.ofFuture(cf)} at this boundary layer.</p>
 *
 * @doc.type class
 * @doc.purpose Adapter from Data-Cloud EventLogStore to kernel EventStore contract
 * @doc.layer core
 * @doc.pattern Service
 */
public class DataCloudEventStoreAdapter implements KernelEventStore {

    private final EventLogStore eventLogStore;
    private final String tenantId;
    private final Logger logger;

    public DataCloudEventStoreAdapter(EventLogStore eventLogStore, String tenantId) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.logger = LoggerFactory.getLogger(DataCloudEventStoreAdapter.class);
    }

    @Override
    public Promise<Void> appendEvent(KernelEvent event) throws KernelEventException {
        try {
            com.ghatana.datacloud.event.Event dataCloudEvent = convertToDataCloudEvent(event);
            return Promise.ofFuture(eventLogStore.appendEvent(dataCloudEvent))
                .whenResult(() -> logger.debug("Event appended successfully: {}", event.getEventId()))
                .whenException(e -> logger.error("Failed to append event: {}", event.getEventId(), e));
        } catch (Exception e) {
            logger.error("Failed to convert event: {}", event.getEventId(), e);
            throw new KernelEventException("Failed to convert event: " + event.getEventId(), e);
        }
    }

    @Override
    public Promise<Void> appendEvents(EventBatch events) throws KernelEventException {
        try {
            List<com.ghatana.datacloud.event.Event> dataCloudEvents = events.getEvents().stream()
                .map(this::convertToDataCloudEvent)
                .toList();
            return Promise.ofFuture(eventLogStore.appendEvents(dataCloudEvents))
                .whenResult(() -> logger.debug("Event batch appended: {} events", events.size()))
                .whenException(e -> logger.error("Failed to append event batch: {} events", events.size(), e));
        } catch (Exception e) {
            logger.error("Failed to convert event batch: {} events", events.size(), e);
            throw new KernelEventException("Failed to convert event batch", e);
        }
    }

    @Override
    public EventStream createEventStream(EventQuery query) throws KernelEventException {
        try {
            com.ghatana.datacloud.query.Query dataCloudQuery = convertToDataCloudQuery(query);
            com.ghatana.datacloud.stream.Stream<com.ghatana.datacloud.event.Event> dataCloudStream =
                eventLogStore.createStream(dataCloudQuery);
            return new DataCloudEventStreamAdapter(dataCloudStream, this::convertFromDataCloudEvent);
        } catch (Exception e) {
            logger.error("Failed to create event stream for query: {}", query, e);
            throw new KernelEventException("Failed to create event stream", e);
        }
    }

    @Override
    public Optional<KernelEvent> getEvent(String eventId) throws KernelEventException {
        try {
            Optional<com.ghatana.datacloud.event.Event> dataCloudEvent = eventLogStore.getEvent(eventId);
            return dataCloudEvent.map(this::convertFromDataCloudEvent);
        } catch (Exception e) {
            logger.error("Failed to get event: {}", eventId, e);
            throw new KernelEventException("Failed to get event: " + eventId, e);
        }
    }

    @Override
    public Promise<List<KernelEvent>> queryEvents(EventQuery query) throws KernelEventException {
        try {
            com.ghatana.datacloud.query.Query dataCloudQuery = convertToDataCloudQuery(query);
            return Promise.ofFuture(eventLogStore.queryEvents(dataCloudQuery))
                .map(dataCloudEvents -> dataCloudEvents.stream()
                    .map(this::convertFromDataCloudEvent)
                    .toList())
                .whenException(e -> logger.error("Failed to query events: {}", query, e));
        } catch (Exception e) {
            logger.error("Failed to convert query: {}", query, e);
            throw new KernelEventException("Failed to convert query", e);
        }
    }

    @Override
    public Promise<Long> getEventCount(EventQuery query) throws KernelEventException {
        try {
            com.ghatana.datacloud.query.Query dataCloudQuery = convertToDataCloudQuery(query);
            return Promise.ofFuture(eventLogStore.getEventCount(dataCloudQuery))
                .whenException(e -> logger.error("Failed to get event count: {}", query, e));
        } catch (Exception e) {
            logger.error("Failed to convert query for count: {}", query, e);
            throw new KernelEventException("Failed to convert query for count", e);
        }
    }

    @Override
    public Promise<Void> deleteEvents(EventQuery query) throws KernelEventException {
        try {
            com.ghatana.datacloud.query.Query dataCloudQuery = convertToDataCloudQuery(query);
            return Promise.ofFuture(eventLogStore.deleteEvents(dataCloudQuery))
                .whenResult(() -> logger.debug("Events deleted for query: {}", query))
                .whenException(e -> logger.error("Failed to delete events: {}", query, e));
        } catch (Exception e) {
            logger.error("Failed to convert delete query: {}", query, e);
            throw new KernelEventException("Failed to convert delete query", e);
        }
    }

    @Override
    public String getTenantId() {
        return tenantId;
    }

    // Conversion methods
    private com.ghatana.datacloud.event.Event convertToDataCloudEvent(KernelEvent kernelEvent) {
        return com.ghatana.datacloud.event.Event.builder()
            .eventId(kernelEvent.getEventId())
            .eventType(kernelEvent.getEventType())
            .eventData(kernelEvent.getEventData())
            .tenantId(tenantId)
            .timestamp(kernelEvent.getTimestamp())
            .metadata(kernelEvent.getMetadata())
            .build();
    }

    private KernelEvent convertFromDataCloudEvent(com.ghatana.datacloud.event.Event dataCloudEvent) {
        return KernelEvent.builder()
            .eventId(dataCloudEvent.getEventId())
            .eventType(dataCloudEvent.getEventType())
            .eventData(dataCloudEvent.getEventData())
            .timestamp(dataCloudEvent.getTimestamp())
            .metadata(dataCloudEvent.getMetadata())
            .build();
    }

    private com.ghatana.datacloud.query.Query convertToDataCloudQuery(EventQuery kernelQuery) {
        return com.ghatana.datacloud.query.Query.builder()
            .eventType(kernelQuery.getEventType())
            .startTime(kernelQuery.getStartTime())
            .endTime(kernelQuery.getEndTime())
            .metadata(kernelQuery.getMetadata())
            .limit(kernelQuery.getLimit())
            .offset(kernelQuery.getOffset())
            .build();
    }
}
```

**Config Resolver Adapter Implementation:**

```java
// Location: platform/java/kernel/src/main/java/com/ghatana/kernel/datacloud/
package com.ghatana.kernel.datacloud;

import com.ghatana.datacloud.config.ConfigRegistry;
import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.tenant.KernelTenantContext;
import com.ghatana.kernel.exception.KernelConfigException;

import io.activej.promise.Promise;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter bridging Data-Cloud ConfigRegistry to KernelConfigResolver.
 *
 * <p>All async results use ActiveJ {@link Promise}. If the underlying Data-Cloud
 * SPI exposes {@code CompletableFuture}, wrap with {@code Promise.ofFuture(cf)}.</p>
 *
 * @doc.type class
 * @doc.purpose Adapter from Data-Cloud ConfigRegistry to kernel ConfigResolver contract
 * @doc.layer core
 * @doc.pattern Service
 */
public class DataCloudConfigAdapter implements KernelConfigResolver {

    private final ConfigRegistry configRegistry;
    private final String tenantId;
    private final Map<String, Object> configCache;
    private final Logger logger;

    public DataCloudConfigAdapter(ConfigRegistry configRegistry, String tenantId) {
        this.configRegistry = Objects.requireNonNull(configRegistry);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.configCache = new ConcurrentHashMap<>();
        this.logger = LoggerFactory.getLogger(DataCloudConfigAdapter.class);
    }

    @Override
    public <T> T resolve(String configKey, Class<T> type, KernelTenantContext context) throws KernelConfigException {
        try {
            // Build full config key with tenant context
            String fullKey = buildFullConfigKey(configKey, context);

            // Check cache first
            Object cachedValue = configCache.get(fullKey);
            if (cachedValue != null) {
                return type.cast(cachedValue);
            }

            // Resolve from Data-Cloud
            T value = configRegistry.resolve(fullKey, type);

            // Cache the value
            configCache.put(fullKey, value);

            logger.debug("Configuration resolved: {} = {}", fullKey, value);
            return value;

        } catch (Exception e) {
            logger.error("Failed to resolve configuration: {}", configKey, e);
            throw new KernelConfigException("Failed to resolve configuration: " + configKey, e);
        }
    }

    @Override
    public <T> T resolveWithDefault(String configKey, Class<T> type, T defaultValue, KernelTenantContext context) throws KernelConfigException {
        try {
            // Try to resolve the configuration
            T value = resolve(configKey, type, context);
            return value != null ? value : defaultValue;

        } catch (KernelConfigException e) {
            // Return default if resolution fails
            logger.debug("Using default value for configuration: {} = {}", configKey, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public Promise<Void> reloadConfig(String tenantId) throws KernelConfigException {
        try {
            configCache.clear();
            return Promise.ofFuture(configRegistry.reloadConfig(tenantId))
                .whenResult(() -> {
                    logger.info("Configuration reloaded for tenant: {}", tenantId);
                    notifyConfigurationChange(tenantId);
                })
                .whenException(e -> logger.error("Failed to reload config for tenant: {}", tenantId, e));
        } catch (Exception e) {
            logger.error("Failed to initiate configuration reload for tenant: {}", tenantId, e);
            throw new KernelConfigException("Failed to initiate configuration reload", e);
        }
    }

    @Override
    public void addConfigProvider(ConfigProvider provider) {
        try {
            // Wrap kernel config provider for Data-Cloud
            DataCloudConfigProviderAdapter adapter = new DataCloudConfigProviderAdapter(provider, tenantId);
            configRegistry.addConfigProvider(adapter);

            logger.debug("Configuration provider added: {}", provider.getProviderId());

        } catch (Exception e) {
            logger.error("Failed to add configuration provider: {}", provider.getProviderId(), e);
            throw new KernelConfigException("Failed to add configuration provider", e);
        }
    }

    @Override
    public Map<String, Object> getAllConfigs(KernelTenantContext context) throws KernelConfigException {
        try {
            // Get all configurations for tenant from Data-Cloud
            Map<String, Object> allConfigs = configRegistry.getAllConfigs(tenantId);

            // Apply tenant context filtering
            return filterConfigsByContext(allConfigs, context);

        } catch (Exception e) {
            logger.error("Failed to get all configurations for tenant: {}", tenantId, e);
            throw new KernelConfigException("Failed to get all configurations", e);
        }
    }

    @Override
    public Promise<Void> setConfig(String configKey, Object value, KernelTenantContext context) throws KernelConfigException {
        try {
            String fullKey = buildFullConfigKey(configKey, context);
            return Promise.ofFuture(configRegistry.setConfig(fullKey, value))
                .whenResult(() -> {
                    configCache.put(fullKey, value);
                    logger.debug("Configuration set: {} = {}", fullKey, value);
                })
                .whenException(e -> logger.error("Failed to set configuration: {} = {}", fullKey, value, e));
        } catch (Exception e) {
            logger.error("Failed to set configuration: {} = {}", configKey, value, e);
            throw new KernelConfigException("Failed to set configuration", e);
        }
    }

    @Override
    public Promise<Void> deleteConfig(String configKey, KernelTenantContext context) throws KernelConfigException {
        try {
            String fullKey = buildFullConfigKey(configKey, context);
            return Promise.ofFuture(configRegistry.deleteConfig(fullKey))
                .whenResult(() -> {
                    configCache.remove(fullKey);
                    logger.debug("Configuration deleted: {}", fullKey);
                })
                .whenException(e -> logger.error("Failed to delete configuration: {}", fullKey, e));
        } catch (Exception e) {
            logger.error("Failed to delete configuration: {}", configKey, e);
            throw new KernelConfigException("Failed to delete configuration", e);
        }
    }

    // Helper methods
    private String buildFullConfigKey(String configKey, KernelTenantContext context) {
        StringBuilder keyBuilder = new StringBuilder();

        // Add tenant prefix
        keyBuilder.append("tenant.").append(tenantId).append(".");

        // Add product context if available
        if (context.getCurrentProduct() != null) {
            keyBuilder.append("product.").append(context.getCurrentProduct()).append(".");
        }

        // Add user context if available
        if (context.getCurrentUserId() != null) {
            keyBuilder.append("user.").append(context.getCurrentUserId()).append(".");
        }

        // Add the actual config key
        keyBuilder.append(configKey);

        return keyBuilder.toString();
    }

    private Map<String, Object> filterConfigsByContext(Map<String, Object> allConfigs, KernelTenantContext context) {
        return allConfigs.entrySet().stream()
            .filter(entry -> isConfigVisibleInContext(entry.getKey(), context))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            ));
    }

    private boolean isConfigVisibleInContext(String configKey, KernelTenantContext context) {
        // Check if config is tenant-specific
        if (configKey.startsWith("tenant." + tenantId + ".")) {
            return true;
        }

        // Check if config is product-specific and matches current product
        if (context.getCurrentProduct() != null &&
            configKey.startsWith("tenant." + tenantId + ".product." + context.getCurrentProduct() + ".")) {
            return true;
        }

        // Check if config is user-specific and matches current user
        if (context.getCurrentUserId() != null &&
            configKey.startsWith("tenant." + tenantId + ".user." + context.getCurrentUserId() + ".")) {
            return true;
        }

        return false;
    }

    private void notifyConfigurationChange(String tenantId) {
        // Implementation for notifying configuration change listeners
        // This would be used to notify modules of configuration changes
    }
}
```

### 1.3 Detailed Kernel Context and Tenant Management

#### 1.3.1 KernelTenantContext - Complete Implementation

**Tenant Context Implementation:**

```java
// Location: platform/java/kernel/src/main/java/com/ghatana/kernel/tenant/
package com.ghatana.kernel.tenant;

import com.ghatana.kernel.security.SecurityContext;
import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.feature.FeatureFlagService;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive tenant context implementation
 * Provides tenant isolation, feature gating, and security context
 */
public class KernelTenantContext {

    // Core tenant information
    private final String tenantId;
    private final TenantType tenantType;
    private final TenantMetadata metadata;

    // Configuration and features
    private final Map<String, Object> tenantConfig;
    private final Set<String> enabledFeatures;
    private final FeatureFlagService featureFlagService;
    private final KernelConfigResolver configResolver;

    // Security context
    private final SecurityContext securityContext;
    private final UserContext userContext;

    // Product context
    private final String currentProduct;
    private final Set<String> accessibleProducts;

    // Runtime state
    private final Map<String, Object> runtimeState;
    private volatile boolean isActive;

    public enum TenantType {
        HEALTHCARE,
        FINANCIAL,
        GENERAL,
        DEVELOPMENT,
        TESTING
    }

    public static class Builder {
        private String tenantId;
        private TenantType tenantType = TenantType.GENERAL;
        private TenantMetadata metadata = TenantMetadata.DEFAULT;
        private Map<String, Object> tenantConfig = new HashMap<>();
        private Set<String> enabledFeatures = new HashSet<>();
        private FeatureFlagService featureFlagService;
        private KernelConfigResolver configResolver;
        private SecurityContext securityContext;
        private UserContext userContext;
        private String currentProduct;
        private Set<String> accessibleProducts = new HashSet<>();
        private Map<String, Object> runtimeState = new HashMap<>();

        public Builder withTenantId(String tenantId) {
            this.tenantId = Objects.requireNonNull(tenantId);
            return this;
        }

        public Builder withTenantType(TenantType tenantType) {
            this.tenantType = Objects.requireNonNull(tenantType);
            return this;
        }

        public Builder withMetadata(TenantMetadata metadata) {
            this.metadata = Objects.requireNonNull(metadata);
            return this;
        }

        public Builder withTenantConfig(Map<String, Object> tenantConfig) {
            this.tenantConfig = new HashMap<>(tenantConfig);
            return this;
        }

        public Builder withEnabledFeature(String featureId) {
            this.enabledFeatures.add(featureId);
            return this;
        }

        public Builder withEnabledFeatures(Set<String> features) {
            this.enabledFeatures = new HashSet<>(features);
            return this;
        }

        public Builder withFeatureFlagService(FeatureFlagService featureFlagService) {
            this.featureFlagService = Objects.requireNonNull(featureFlagService);
            return this;
        }

        public Builder withConfigResolver(KernelConfigResolver configResolver) {
            this.configResolver = Objects.requireNonNull(configResolver);
            return this;
        }

        public Builder withSecurityContext(SecurityContext securityContext) {
            this.securityContext = Objects.requireNonNull(securityContext);
            return this;
        }

        public Builder withUserContext(UserContext userContext) {
            this.userContext = Objects.requireNonNull(userContext);
            return this;
        }

        public Builder withCurrentProduct(String productId) {
            this.currentProduct = productId;
            return this;
        }

        public Builder withAccessibleProduct(String productId) {
            this.accessibleProducts.add(productId);
            return this;
        }

        public Builder withAccessibleProducts(Set<String> products) {
            this.accessibleProducts = new HashSet<>(products);
            return this;
        }

        public Builder withRuntimeState(String key, Object value) {
            this.runtimeState.put(key, value);
            return this;
        }

        public KernelTenantContext build() {
            validate();
            return new KernelTenantContext(this);
        }

        private void validate() {
            if (tenantId == null || tenantId.trim().isEmpty()) {
                throw new IllegalArgumentException("Tenant ID is required");
            }
            if (featureFlagService == null) {
                throw new IllegalArgumentException("Feature flag service is required");
            }
            if (configResolver == null) {
                throw new IllegalArgumentException("Config resolver is required");
            }
            if (securityContext == null) {
                throw new IllegalArgumentException("Security context is required");
            }
        }
    }

    private KernelTenantContext(Builder builder) {
        this.tenantId = builder.tenantId;
        this.tenantType = builder.tenantType;
        this.metadata = builder.metadata;
        this.tenantConfig = Collections.unmodifiableMap(new HashMap<>(builder.tenantConfig));
        this.enabledFeatures = Collections.unmodifiableSet(new HashSet<>(builder.enabledFeatures));
        this.featureFlagService = builder.featureFlagService;
        this.configResolver = builder.configResolver;
        this.securityContext = builder.securityContext;
        this.userContext = builder.userContext;
        this.currentProduct = builder.currentProduct;
        this.accessibleProducts = Collections.unmodifiableSet(new HashSet<>(builder.accessibleProducts));
        this.runtimeState = new ConcurrentHashMap<>(builder.runtimeState);
        this.isActive = true;
    }

    // Core tenant information
    public String getTenantId() { return tenantId; }
    public TenantType getTenantType() { return tenantType; }
    public TenantMetadata getMetadata() { return metadata; }

    // Feature management
    public boolean isFeatureEnabled(String featureId) {
        // Check explicit enabled features
        if (enabledFeatures.contains(featureId)) {
            return true;
        }

        // Check feature flag service
        return featureFlagService.isFeatureEnabled(featureId, tenantId);
    }

    public Set<String> getEnabledFeatures() {
        return enabledFeatures;
    }

    public Promise<Boolean> isFeatureEnabledAsync(String featureId) {
        return featureFlagService.isFeatureEnabledAsync(featureId, tenantId);
    }

    // Configuration management
    public <T> T getConfig(String configKey, Class<T> type) {
        try {
            return configResolver.resolve(configKey, type, this);
        } catch (KernelConfigException e) {
            throw new RuntimeException("Failed to resolve configuration: " + configKey, e);
        }
    }

    public <T> T getConfigWithDefault(String configKey, Class<T> type, T defaultValue) {
        try {
            return configResolver.resolveWithDefault(configKey, type, defaultValue, this);
        } catch (KernelConfigException e) {
            return defaultValue;
        }
    }

    public Map<String, Object> getAllConfigs() {
        try {
            return configResolver.getAllConfigs(this);
        } catch (KernelConfigException e) {
            throw new RuntimeException("Failed to get all configurations", e);
        }
    }

    public Promise<Void> reloadConfig() {
        return configResolver.reloadConfig(tenantId);
    }

    // Security context
    public SecurityContext getSecurityContext() { return securityContext; }
    public UserContext getUserContext() { return userContext; }

    public String getCurrentUserId() {
        return userContext != null ? userContext.getUserId() : null;
    }

    public Set<String> getUserRoles() {
        return userContext != null ? userContext.getRoles() : Set.of();
    }

    public Set<String> getUserPermissions() {
        return userContext != null ? userContext.getPermissions() : Set.of();
    }

    public boolean hasPermission(String permission) {
        return securityContext.hasPermission(permission, this);
    }

    public boolean hasRole(String role) {
        return userContext != null && userContext.hasRole(role);
    }

    // Product context
    public String getCurrentProduct() { return currentProduct; }
    public Set<String> getAccessibleProducts() { return accessibleProducts; }

    public boolean canAccessProduct(String productId) {
        return accessibleProducts.contains(productId);
    }

    public void setCurrentProduct(String productId) {
        if (!canAccessProduct(productId)) {
            throw new SecurityException("User does not have access to product: " + productId);
        }
        // Update current product context
        this.currentProduct = productId;
    }

    // Runtime state management
    public Object getRuntimeState(String key) {
        return runtimeState.get(key);
    }

    public void setRuntimeState(String key, Object value) {
        runtimeState.put(key, value);
    }

    public void removeRuntimeState(String key) {
        runtimeState.remove(key);
    }

    public Map<String, Object> getAllRuntimeState() {
        return Collections.unmodifiableMap(new HashMap<>(runtimeState));
    }

    public void clearRuntimeState() {
        runtimeState.clear();
    }

    // Lifecycle management
    public boolean isActive() { return isActive; }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

    // Compliance and governance
    public boolean isComplianceRequired(String complianceType) {
        return metadata.getRequiredCompliance().contains(complianceType);
    }

    public Set<String> getRequiredCompliance() {
        return metadata.getRequiredCompliance();
    }

    public RetentionPolicy getRetentionPolicy(DataType dataType) {
        return metadata.getRetentionPolicy(dataType);
    }

    // Utility methods
    public boolean isHealthcareTenant() {
        return tenantType == TenantType.HEALTHCARE;
    }

    public boolean isFinancialTenant() {
        return tenantType == TenantType.FINANCIAL;
    }

    public boolean isDevelopmentTenant() {
        return tenantType == TenantType.DEVELOPMENT;
    }

    public boolean isTestingTenant() {
        return tenantType == TenantType.TESTING;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KernelTenantContext that = (KernelTenantContext) o;
        return Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId);
    }

    @Override
    public String toString() {
        return String.format("KernelTenantContext{id=%s, type=%s, product=%s, user=%s}",
            tenantId, tenantType, currentProduct, getCurrentUserId());
    }
}
```

**Supporting Classes:**

```java
// Location: platform/java/kernel/src/main/java/com/ghatana/kernel/tenant/

public class TenantMetadata {
    private final String name;
    private final String description;
    private final Map<String, String> attributes;
    private final Set<String> requiredCompliance;
    private final Map<DataType, RetentionPolicy> retentionPolicies;
    private final Map<String, Object> governanceConfig;

    public static final TenantMetadata DEFAULT = new TenantMetadata(
        "Default Tenant",
        "Default tenant configuration",
        Map.of(),
        Set.of(),
        Map.of(),
        Map.of()
    );

    // Constructors and methods...
}

public class UserContext {
    private final String userId;
    private final String username;
    private final String email;
    private final Set<String> roles;
    private final Set<String> permissions;
    private final Map<String, Object> attributes;

    // Constructors and methods...
}

public class SecurityContext {
    private final Set<String> globalPermissions;
    private final Map<String, Set<String>> productPermissions;
    private final Map<String, Set<String>> rolePermissions;

    public boolean hasPermission(String permission, KernelTenantContext context) {
        // Check global permissions
        if (globalPermissions.contains(permission)) {
            return true;
        }

        // Check product-specific permissions
        if (context.getCurrentProduct() != null) {
            Set<String> productPerms = productPermissions.get(context.getCurrentProduct());
            if (productPerms != null && productPerms.contains(permission)) {
                return true;
            }
        }

        // Check role-based permissions
        for (String role : context.getUserRoles()) {
            Set<String> rolePerms = rolePermissions.get(role);
            if (rolePerms != null && rolePerms.contains(permission)) {
                return true;
            }
        }

        return false;
    }

    // Constructors and methods...
}
```

### 1.4 Detailed Implementation Tasks (Weeks 1-2)

#### 1.4.1 Week 1 Task Breakdown

**Day 1-2: Core Kernel Interfaces**

```yaml
Day_1_Tasks:
  Task_1:
    Name: "Create KernelDescriptor class"
    Description: "Implement comprehensive descriptor with all metadata fields"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/descriptor/KernelDescriptor.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/descriptor/KernelCapability.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/descriptor/KernelDependency.java"
    Dependencies: []
    Estimated_Hours: 8
    Acceptance_Criteria:
      - All descriptor fields implemented
      - Builder pattern with validation
      - Unit tests with 95% coverage

  Task_2:
    Name: "Create KernelModule interface"
    Description: "Define core module interface with lifecycle methods"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/module/KernelModule.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/module/AbstractKernelModule.java"
    Dependencies: ["Task_1"]
    Estimated_Hours: 6
    Acceptance_Criteria:
      - Complete interface definition
      - Abstract base class implementation
      - Lifecycle hooks defined
      - Exception handling

Day_2_Tasks:
  Task_3:
    Name: "Create KernelPlugin interface"
    Description: "Define plugin interface extending KernelModule"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/KernelPlugin.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/AbstractKernelPlugin.java"
    Dependencies: ["Task_2"]
    Estimated_Hours: 6
    Acceptance_Criteria:
      - Plugin-specific methods defined
      - Security context integration
      - Contract management
      - Installation/uninstallation hooks

Day_3-4_Tasks:
  Task_4:
    Name: "Implement basic KernelRegistry"
    Description: "Create in-memory registry with dependency resolution"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/registry/KernelRegistry.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/registry/InMemoryKernelRegistry.java"
    Dependencies: ["Task_1", "Task_2", "Task_3"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Module registration and discovery
      - Dependency resolution algorithm
      - Capability-based lookup
      - Thread-safe operations

Day_5_Tasks:
  Task_5:
    Name: "Setup kernel build configuration"
    Description: "Create Gradle build files and dependencies"
    Files:
      - "platform/java/kernel/build.gradle.kts"
      - "platform/java/kernel/settings.gradle.kts"
    Dependencies: []
    Estimated_Hours: 4
    Acceptance_Criteria:
      - Gradle build configuration
      - Dependencies defined
      - Test configuration
      - Build pipeline integration
```

**Day 6-7: Data-Cloud Integration**

```yaml
Day_6_Tasks:
  Task_6:
    Name: "Create Data-Cloud adapter factory"
    Description: "Implement factory for Data-Cloud service adapters"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/datacloud/DataCloudKernelAdapterFactory.java"
    Dependencies: ["Task_4"]
    Estimated_Hours: 6
    Acceptance_Criteria:
      - Factory pattern implementation
      - Adapter caching
      - Tenant isolation
      - Statistics collection

Day_7_Tasks:
  Task_7:
    Name: "Implement Event Store adapter"
    Description: "Create adapter for Data-Cloud EventLogStore"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/datacloud/DataCloudEventStoreAdapter.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/event/KernelEventStore.java"
    Dependencies: ["Task_6"]
    Estimated_Hours: 8
    Acceptance_Criteria:
      - Event append and query
      - Event streaming
      - Batch operations
      - Error handling
```

#### 1.4.2 Week 2 Task Breakdown

**Day 8-9: Tenant Context and Configuration**

```yaml
Day_8_Tasks:
  Task_8:
    Name: "Implement KernelTenantContext"
    Description: "Create comprehensive tenant context implementation"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/tenant/KernelTenantContext.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/tenant/TenantMetadata.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/tenant/UserContext.java"
    Dependencies: ["Task_5"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Tenant isolation
      - Feature gating
      - Security context
      - Runtime state management

Day_9_Tasks:
  Task_9:
    Name: "Create KernelConfigResolver"
    Description: "Implement configuration resolver with Data-Cloud integration"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/config/KernelConfigResolver.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/datacloud/DataCloudConfigAdapter.java"
    Dependencies: ["Task_8"]
    Estimated_Hours: 8
    Acceptance_Criteria:
      - Hierarchical configuration
      - Hot-reload capability
      - Tenant-scoped configs
      - Caching and performance

Day_10-11_Tasks:
  Task_10:
    Name: "Add lifecycle management"
    Description: "Implement comprehensive lifecycle management for modules"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/lifecycle/KernelLifecycleManager.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/lifecycle/LifecycleState.java"
    Dependencies: ["Task_4", "Task_8"]
    Estimated_Hours: 8
    Acceptance_Criteria:
      - State machine implementation
      - Lifecycle hooks
      - Error handling
      - Graceful shutdown

Day_11_Tasks:
  Task_11:
    Name: "Implement dependency resolution"
    Description: "Create dependency resolution algorithm in registry"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/registry/DependencyResolver.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/registry/DependencyGraph.java"
    Dependencies: ["Task_4"]
    Estimated_Hours: 6
    Acceptance_Criteria:
      - Circular dependency detection
      - Topological sorting
      - Version compatibility
      - Conflict resolution

Day_12_Tasks:
  Task_12:
    Name: "Create health monitoring"
    Description: "Implement health monitoring for kernel components"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/health/KernelHealthMonitor.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/health/HealthStatus.java"
    Dependencies: ["Task_10"]
    Estimated_Hours: 4
    Acceptance_Criteria:
      - Health check endpoints
      - Component monitoring
      - Metrics collection
      - Alerting integration
```

#### 1.4.3 Testing and Validation

**Week 1-2 Testing Strategy:**

```yaml
Testing_Strategy:
  Unit_Tests:
    Coverage_Target: "95%"
    Framework: "JUnit 5 + Mockito"
    Test_Classes:
      - "KernelDescriptorTest"
      - "KernelModuleTest"
      - "KernelPluginTest"
      - "KernelRegistryTest"
      - "KernelTenantContextTest"
      - "DataCloudAdapterTest"

  Integration_Tests:
    Coverage_Target: "80%"
    Framework: "JUnit 5 + Testcontainers"
    Test_Classes:
      - "KernelIntegrationTest"
      - "DataCloudIntegrationTest"
      - "TenantContextIntegrationTest"
      - "LifecycleIntegrationTest"

  Performance_Tests:
    Framework: "JMH"
    Metrics:
      - Registry lookup latency
      - Configuration resolution time
      - Event store throughput
      - Memory usage
```

This granular specification provides detailed implementation guidance for Phase 1, with specific code examples, task breakdowns, and acceptance criteria. Each subsequent phase will follow the same level of detail.

---

## Phase 2: PHR Kernel Integration (Weeks 3-4)

### 2.1 Detailed PHR Module Analysis and Implementation

#### 2.1.1 PHR Kernel Module - Complete Implementation

**PHR Kernel Module Structure:**

```java
// Location: products/phr/kernel/src/main/java/com/ghatana/phr/kernel/
package com.ghatana.phr.kernel;

import com.ghatana.kernel.module.AbstractKernelModule;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.tenant.KernelTenantContext;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import com.ghatana.phr.kernel.service.*;
import com.ghatana.phr.kernel.config.PhrConfiguration;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;

/**
 * PHR-specific kernel module implementation.
 * Provides healthcare-specific capabilities and services.
 *
 * @doc.type class
 * @doc.purpose PHR product module delivering healthcare capabilities via kernel
 * @doc.layer product
 * @doc.pattern Service
 */
public class PhrKernelModule extends AbstractKernelModule {

    private final PhrConfiguration phrConfiguration;
    private final Set<PhrService> phrServices;

    // PHR-specific services
    private PatientService patientService;
    private ConsentService consentService;
    private DocumentService documentService;
    private AppointmentService appointmentService;
    private MedicationService medicationService;
    private BillingService billingService;
    private FhirInteropService fhirInteropService;
    private ImagingService imagingService;
    private ReferralService referralService;

    public PhrKernelModule(PhrConfiguration phrConfiguration) {
        super(createDescriptor(), createCapabilities(), createDependencies());
        this.phrConfiguration = Objects.requireNonNull(phrConfiguration);
        this.phrServices = new HashSet<>();
    }

    private static KernelDescriptor createDescriptor() {
        return KernelDescriptor.builder()
            .withDescriptorId("phr-core")
            .withName("PHR Core Module")
            .withVersion("1.0.0")
            .withDescription("Personal Health Record core module with healthcare-specific capabilities")
            .withOwner("PHR Team")
            .withType(KernelDescriptor.DescriptorType.KERNEL_MODULE)
            .withCapability(KernelCapability.PATIENT_RECORDS)
            .withCapability(KernelCapability.CONSENT_MANAGEMENT)
            .withCapability(KernelCapability.DOCUMENT_STORAGE)
            .withCapability(KernelCapability.APPOINTMENT_SCHEDULING)
            .withCapability(KernelCapability.MEDICATION_MANAGEMENT)
            .withCapability(KernelCapability.BILLING)
            .withCapability(KernelCapability.FHIR_INTEROP)
            .withCapability(KernelCapability.IMAGING)
            .withCapability(KernelCapability.REFERRALS)
            .withTag("healthcare")
            .withTag("phr")
            .withTag("nepal-healthcare")
            .withMetadata("regulatory", "Nepal Directive 2081")
            .withMetadata("compliance", "HIPAA-inspired")
            .withMetadata("fhir-version", "R4")
            .withComplianceRequirement("GDPR")
            .withComplianceRequirement("HIPAA")
            .withComplianceRequirement("Nepal Privacy Act 2075")
            .build();
    }

    private static Set<KernelCapability> createCapabilities() {
        return Set.of(
            KernelCapability.PATIENT_RECORDS,
            KernelCapability.CONSENT_MANAGEMENT,
            KernelCapability.DOCUMENT_STORAGE,
            KernelCapability.APPOINTMENT_SCHEDULING,
            KernelCapability.MEDICATION_MANAGEMENT,
            KernelCapability.BILLING,
            KernelCapability.FHIR_INTEROP,
            KernelCapability.IMAGING,
            KernelCapability.REFERRALS
        );
    }

    private static Set<KernelDependency> createDependencies() {
        return Set.of(
            KernelDependency.builder()
                .withDependencyId("data-cloud")
                .withVersion("1.0.0")
                .withType(KernelDependency.DependencyType.EXTERNAL_SERVICE)
                .withOptional(false)
                .build(),
            KernelDependency.builder()
                .withDependencyId("aep-platform")
                .withVersion("1.0.0")
                .withType(KernelDependency.DependencyType.EXTERNAL_SERVICE)
                .withOptional(false)
                .build(),
            KernelDependency.builder()
                .withDependencyId("kernel-core")
                .withVersion("1.0.0")
                .withType(KernelDependency.DependencyType.MODULE)
                .withOptional(false)
                .build()
        );
    }

    @Override
    protected void doInitialize() throws Exception {
        logger.info("Initializing PHR kernel module");

        // Initialize PHR configuration
        initializeConfiguration();

        // Initialize PHR-specific services
        initializePhrServices();

        // Register event handlers
        registerEventHandlers();

        // Setup healthcare-specific policies
        setupHealthcarePolicies();

        logger.info("PHR kernel module initialized successfully");
    }

    @Override
    protected void doStart() throws Exception {
        logger.info("Starting PHR kernel module");

        // Start all PHR services using ActiveJ Promises.all()
        Promises.all(
            phrServices.stream()
                .map(service -> service.start())
        ).whenResult(() -> {
            // Start background tasks
            startBackgroundTasks();
            // Register with healthcare monitoring
            registerWithHealthcareMonitoring();
            logger.info("PHR kernel module started successfully");
        }).whenException(e -> logger.error("Failed to start PHR services", e));
    }

    @Override
    protected void doStop() throws Exception {
        logger.info("Stopping PHR kernel module");

        // Stop background tasks
        stopBackgroundTasks();

        // Stop all PHR services using ActiveJ Promises.all()
        Promises.all(
            phrServices.stream()
                .map(service -> service.stop())
        ).whenResult(() -> logger.info("PHR kernel module stopped successfully"))
         .whenException(e -> logger.error("Failed to stop PHR services", e));
    }

    @Override
    protected void doCleanup() throws Exception {
        logger.info("Cleaning up PHR kernel module");

        // Cleanup all PHR services
        for (PhrService service : phrServices) {
            try {
                service.cleanup();
            } catch (Exception e) {
                logger.error("Error cleaning up PHR service: {}", service.getClass().getSimpleName(), e);
            }
        }

        phrServices.clear();

        logger.info("PHR kernel module cleaned up successfully");
    }

    private void initializeConfiguration() {
        // Validate PHR-specific configuration
        validatePhrConfiguration();

        // Setup healthcare-specific defaults
        setupHealthcareDefaults();
    }

    private void initializePhrServices() throws Exception {
        // Initialize patient service
        patientService = new PatientService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            phrConfiguration.getPatientConfig()
        );
        phrServices.add(patientService);

        // Initialize consent service with healthcare-specific policies
        consentService = new ConsentService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            phrConfiguration.getConsentConfig(),
            new NepalHealthcareConsentPolicy()
        );
        phrServices.add(consentService);

        // Initialize document service with OCR/ASR capabilities
        documentService = new DocumentService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            phrConfiguration.getDocumentConfig(),
            new OcrProcessor(),
            new AsrProcessor()
        );
        phrServices.add(documentService);

        // Initialize appointment service
        appointmentService = new AppointmentService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            phrConfiguration.getAppointmentConfig()
        );
        phrServices.add(appointmentService);

        // Initialize medication service
        medicationService = new MedicationService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            phrConfiguration.getMedicationConfig()
        );
        phrServices.add(medicationService);

        // Initialize billing service with finance integration
        billingService = new BillingService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            phrConfiguration.getBillingConfig(),
            context.getOptionalDependency(FinanceService.class, "finance-service")
                .orElse(new DefaultFinanceService())
        );
        phrServices.add(billingService);

        // Initialize FHIR interop service
        fhirInteropService = new FhirInteropService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            phrConfiguration.getFhirConfig()
        );
        phrServices.add(fhirInteropService);

        // Initialize imaging service
        imagingService = new ImagingService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            phrConfiguration.getImagingConfig()
        );
        phrServices.add(imagingService);

        // Initialize referral service
        referralService = new ReferralService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            phrConfiguration.getReferralConfig()
        );
        phrServices.add(referralService);
    }

    private void registerEventHandlers() {
        // Register patient lifecycle event handlers
        context.registerEventHandler("patient.registered", this::handlePatientRegistered);
        context.registerEventHandler("patient.updated", this::handlePatientUpdated);
        context.registerEventHandler("patient.consent.granted", this::handleConsentGranted);
        context.registerEventHandler("patient.consent.revoked", this::handleConsentRevoked);

        // Register document event handlers
        context.registerEventHandler("document.uploaded", this::handleDocumentUploaded);
        context.registerEventHandler("document.processed", this::handleDocumentProcessed);

        // Register appointment event handlers
        context.registerEventHandler("appointment.scheduled", this::handleAppointmentScheduled);
        context.registerEventHandler("appointment.completed", this::handleAppointmentCompleted);

        // Register billing event handlers
        context.registerEventHandler("bill.generated", this::handleBillGenerated);
        context.registerEventHandler("payment.processed", this::handlePaymentProcessed);
    }

    private void setupHealthcarePolicies() {
        // Setup Nepal healthcare compliance policies
        setupNepalHealthcarePolicies();

        // Setup emergency access policies
        setupEmergencyAccessPolicies();

        // Setup data retention policies
        setupDataRetentionPolicies();
    }

    // Event handler methods
    private void handlePatientRegistered(KernelEvent event) {
        try {
            PatientRegisteredEvent patientEvent = (PatientRegisteredEvent) event.getPayload();
            patientService.handlePatientRegistration(patientEvent);

            // Trigger consent setup workflow
            consentService.setupDefaultConsent(patientEvent.getPatientId());

            // Schedule welcome appointment if configured
            if (phrConfiguration.isWelcomeAppointmentEnabled()) {
                appointmentService.scheduleWelcomeAppointment(patientEvent.getPatientId());
            }

        } catch (Exception e) {
            logger.error("Error handling patient registered event", e);
            throw new RuntimeException("Failed to handle patient registered event", e);
        }
    }

    private void handleConsentGranted(KernelEvent event) {
        try {
            ConsentGrantedEvent consentEvent = (ConsentGrantedEvent) event.getPayload();
            consentService.handleConsentGranted(consentEvent);

            // Update patient access permissions
            patientService.updateAccessPermissions(consentEvent.getPatientId(), consentEvent.getConsent());

            // Log consent for audit
            context.getDependency(KernelAuditService.class, "audit-service")
                .logConsentGranted(consentEvent);

        } catch (Exception e) {
            logger.error("Error handling consent granted event", e);
            throw new RuntimeException("Failed to handle consent granted event", e);
        }
    }

    // Healthcare-specific policy setup methods
    private void setupNepalHealthcarePolicies() {
        // Register Nepal healthcare compliance policies
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new NepalHealthcareDataPolicy());
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new NepalHealthcareAccessPolicy());
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new NepalHealthcareConsentPolicy());
    }

    private void setupEmergencyAccessPolicies() {
        // Register emergency access override policies
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new EmergencyAccessPolicy());
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new BreakGlassAccessPolicy());
    }

    private void setupDataRetentionPolicies() {
        // Register healthcare data retention policies
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new HealthcareDataRetentionPolicy());
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new AuditDataRetentionPolicy());
    }

    // Background task management
    private void startBackgroundTasks() {
        // Start patient data synchronization
        startPatientDataSync();

        // Start consent monitoring
        startConsentMonitoring();

        // Start appointment reminders
        startAppointmentReminders();

        // Start billing reconciliation
        startBillingReconciliation();
    }

    private void stopBackgroundTasks() {
        // Stop all background tasks
        // Implementation details...
    }

    // Service getters for other modules
    public PatientService getPatientService() { return patientService; }
    public ConsentService getConsentService() { return consentService; }
    public DocumentService getDocumentService() { return documentService; }
    public AppointmentService getAppointmentService() { return appointmentService; }
    public MedicationService getMedicationService() { return medicationService; }
    public BillingService getBillingService() { return billingService; }
    public FhirInteropService getFhirInteropService() { return fhirInteropService; }
    public ImagingService getImagingService() { return imagingService; }
    public ReferralService getReferralService() { return referralService; }
}
```

#### 2.1.2 Healthcare Consent Enhancement - Complete Implementation

**Nepal Healthcare Consent Policy:**

```java
// Location: products/phr/kernel/src/main/java/com/ghatana/phr/kernel/consent/
package com.ghatana.phr.kernel.consent;

import com.ghatana.kernel.consent.ConsentPolicy;
import com.ghatana.kernel.consent.ConsentRequest;
import com.ghatana.kernel.consent.ConsentDecision;
import com.ghatana.kernel.tenant.KernelTenantContext;
import com.ghatana.phr.model.NepalHealthcareContext;

import java.time.Duration;
import java.util.Set;
import java.util.Map;

/**
 * Nepal healthcare-specific consent policy implementation
 * Implements Nepal Directive 2081 and Privacy Act 2075 requirements
 */
public class NepalHealthcareConsentPolicy implements ConsentPolicy {

    private static final String POLICY_ID = "nepal-healthcare-consent-v1";
    private static final String POLICY_NAME = "Nepal Healthcare Consent Policy";
    private static final String POLICY_VERSION = "1.0.0";

    // Nepal healthcare consent types
    public enum NepalConsentType {
        PRIMARY_CARE_CONSENT,
        SPECIALIST_CONSENT,
        EMERGENCY_CONSENT,
        RESEARCH_CONSENT,
        DATA_SHARING_CONSENT,
        TELEHEALTH_CONSENT,
        PHARMACY_CONSENT,
        LABORATORY_CONSENT,
        IMAGING_CONSENT,
        BILLING_CONSENT
    }

    // Consent duration policies
    private static final Map<NepalConsentType, Duration> CONSENT_DURATIONS = Map.of(
        NepalConsentType.PRIMARY_CARE_CONSENT, Duration.ofDays(365),
        NepalConsentType.SPECIALIST_CONSENT, Duration.ofDays(180),
        NepalConsentType.EMERGENCY_CONSENT, Duration.ofHours(24),
        NepalConsentType.RESEARCH_CONSENT, Duration.ofDays(1095), // 3 years
        NepalConsentType.DATA_SHARING_CONSENT, Duration.ofDays(730), // 2 years
        NepalConsentType.TELEHEALTH_CONSENT, Duration.ofDays(365),
        NepalConsentType.PHARMACY_CONSENT, Duration.ofDays(365),
        NepalConsentType.LABORATORY_CONSENT, Duration.ofDays(365),
        NepalConsentType.IMAGING_CONSENT, Duration.ofDays(365),
        NepalConsentType.BILLING_CONSENT, Duration.ofDays(1095) // 3 years
    );

    // Age-based consent requirements
    private static final int ADULT_AGE = 18;
    private static final int MINOR_CONSENT_MIN_AGE = 16;
    private static final int ELDERLY_SPECIAL_CONSENT_AGE = 65;

    private final Logger logger;
    private final ConsentAuditService auditService;
    private final ConsentNotificationService notificationService;

    public NepalHealthcareConsentPolicy(ConsentAuditService auditService,
                                      ConsentNotificationService notificationService) {
        this.logger = LoggerFactory.getLogger(NepalHealthcareConsentPolicy.class);
        this.auditService = Objects.requireNonNull(auditService);
        this.notificationService = Objects.requireNonNull(notificationService);
    }

    @Override
    public String getPolicyId() {
        return POLICY_ID;
    }

    @Override
    public String getPolicyName() {
        return POLICY_NAME;
    }

    @Override
    public String getPolicyVersion() {
        return POLICY_VERSION;
    }

    @Override
    public ConsentDecision evaluateConsent(ConsentRequest request, KernelTenantContext context) {
        try {
            logger.debug("Evaluating consent request: {} for tenant: {}",
                request.getRequestId(), context.getTenantId());

            // Validate request against Nepal healthcare requirements
            ConsentValidationResult validation = validateNepalHealthcareRequest(request, context);

            if (!validation.isValid()) {
                return ConsentDecision.denied(
                    request.getRequestId(),
                    "Request failed Nepal healthcare validation: " + validation.getErrorMessage()
                );
            }

            // Check age-based consent requirements
            AgeConsentResult ageResult = checkAgeBasedConsent(request, context);

            // Check emergency consent requirements
            EmergencyConsentResult emergencyResult = checkEmergencyConsent(request, context);

            // Check data sharing requirements
            DataSharingConsentResult dataSharingResult = checkDataSharingConsent(request, context);

            // Check facility-specific requirements
            FacilityConsentResult facilityResult = checkFacilityConsent(request, context);

            // Make final decision
            ConsentDecision decision = makeConsentDecision(
                request, context, ageResult, emergencyResult, dataSharingResult, facilityResult);

            // Audit the decision
            auditService.auditConsentDecision(request, decision, context);

            // Send notifications if required
            sendConsentNotifications(request, decision, context);

            logger.info("Consent decision made: {} for request: {}",
                decision.getDecision(), request.getRequestId());

            return decision;

        } catch (Exception e) {
            logger.error("Error evaluating consent request: {}", request.getRequestId(), e);
            auditService.auditConsentError(request, e, context);
            return ConsentDecision.error(request.getRequestId(), "Consent evaluation failed: " + e.getMessage());
        }
    }

    private ConsentValidationResult validateNepalHealthcareRequest(ConsentRequest request,
                                                               KernelTenantContext context) {
        // Validate required fields for Nepal healthcare
        if (request.getPatientId() == null || request.getPatientId().trim().isEmpty()) {
            return ConsentValidationResult.failed("Patient ID is required");
        }

        if (request.getHealthcareProviderId() == null || request.getHealthcareProviderId().trim().isEmpty()) {
            return ConsentValidationResult.failed("Healthcare provider ID is required");
        }

        if (request.getFacilityId() == null || request.getFacilityId().trim().isEmpty()) {
            return ConsentValidationResult.failed("Facility ID is required");
        }

        // Validate consent type
        NepalConsentType consentType = determineConsentType(request);
        if (consentType == null) {
            return ConsentValidationResult.failed("Invalid or unsupported consent type");
        }

        // Validate Nepal healthcare context
        NepalHealthcareContext healthcareContext = request.getHealthcareContext();
        if (healthcareContext == null) {
            return ConsentValidationResult.failed("Nepal healthcare context is required");
        }

        // Validate required Nepal healthcare fields
        if (healthcareContext.getProvince() == null) {
            return ConsentValidationResult.failed("Province information is required");
        }

        if (healthcareContext.getDistrict() == null) {
            return ConsentValidationResult.failed("District information is required");
        }

        // Validate provider registration
        if (!isProviderRegistered(request.getHealthcareProviderId(), healthcareContext.getProvince())) {
            return ConsentValidationResult.failed("Provider is not registered in the specified province");
        }

        // Validate facility registration
        if (!isFacilityRegistered(request.getFacilityId(), healthcareContext.getProvince())) {
            return ConsentValidationResult.failed("Facility is not registered in the specified province");
        }

        return ConsentValidationResult.success();
    }

    private AgeConsentResult checkAgeBasedConsent(ConsentRequest request, KernelTenantContext context) {
        int patientAge = request.getPatientAge();

        if (patientAge < ADULT_AGE) {
            // Minor consent requirements
            if (patientAge < MINOR_CONSENT_MIN_AGE) {
                // Patient too young for consent, requires guardian consent
                return AgeConsentResult.guardianRequired(
                    "Patient is under 16, guardian consent required"
                );
            } else {
                // Minor can consent with guardian notification
                return AgeConsentResult.minorWithGuardianNotification(
                    "Patient is minor, consent allowed with guardian notification"
                );
            }
        } else if (patientAge >= ELDERLY_SPECIAL_CONSENT_AGE) {
            // Elderly special consent considerations
            return AgeConsentResult.elderlySpecialConsent(
                "Patient is elderly, special consent considerations apply"
            );
        } else {
            // Adult consent
            return AgeConsentResult.adultConsent("Adult consent applies");
        }
    }

    private EmergencyConsentResult checkEmergencyConsent(ConsentRequest request, KernelTenantContext context) {
        if (request.isEmergency()) {
            // Emergency consent - 24-hour validity, automatic approval for life-saving care
            return EmergencyConsentResult.emergencyApproved(
                "Emergency consent approved for 24 hours",
                Duration.ofHours(24)
            );
        } else {
            // Non-emergency consent
            return EmergencyConsentResult.nonEmergency("Standard consent process applies");
        }
    }

    private DataSharingConsentResult checkDataSharingConsent(ConsentRequest request, KernelTenantContext context) {
        Set<String> dataSharingPurposes = request.getDataSharingPurposes();

        if (dataSharingPurposes == null || dataSharingPurposes.isEmpty()) {
            return DataSharingConsentResult.noDataSharing("No data sharing requested");
        }

        // Validate data sharing purposes against Nepal healthcare regulations
        for (String purpose : dataSharingPurposes) {
            if (!isValidDataSharingPurpose(purpose, context)) {
                return DataSharingConsentResult.invalidPurpose(
                    "Invalid data sharing purpose: " + purpose
                );
            }
        }

        // Check if cross-border data sharing is requested
        boolean crossBorderSharing = dataSharingPurposes.stream()
            .anyMatch(this::isCrossBorderDataSharing);

        if (crossBorderSharing) {
            // Cross-border data sharing requires special approval
            return DataSharingConsentResult.crossBorderRequiresApproval(
                "Cross-border data sharing requires special approval"
            );
        }

        return DataSharingConsentResult.approved("Data sharing purposes are valid");
    }

    private FacilityConsentResult checkFacilityConsent(ConsentRequest request, KernelTenantContext context) {
        String facilityId = request.getFacilityId();
        NepalHealthcareContext healthcareContext = request.getHealthcareContext();

        // Check facility type
        FacilityType facilityType = getFacilityType(facilityId);

        switch (facilityType) {
            case GOVERNMENT_HOSPITAL:
                return FacilityConsentResult.governmentFacility("Government hospital consent applies");

            case PRIVATE_HOSPITAL:
                return FacilityConsentResult.privateFacility("Private hospital consent applies");

            case CLINIC:
                return FacilityConsentResult.clinicFacility("Clinic consent applies");

            case PHARMACY:
                return FacilityConsentResult.pharmacyFacility("Pharmacy consent applies");

            case LABORATORY:
                return FacilityConsentResult.laboratoryFacility("Laboratory consent applies");

            default:
                return FacilityConsentResult.unknownFacility("Unknown facility type");
        }
    }

    private ConsentDecision makeConsentDecision(ConsentRequest request,
                                             KernelTenantContext context,
                                             AgeConsentResult ageResult,
                                             EmergencyConsentResult emergencyResult,
                                             DataSharingConsentResult dataSharingResult,
                                             FacilityConsentResult facilityResult) {

        // Check for emergency approval first
        if (emergencyResult.isEmergencyApproved()) {
            return ConsentDecision.approved(
                request.getRequestId(),
                "Emergency consent approved",
                emergencyResult.getValidityDuration(),
                Map.of(
                    "emergency", true,
                    "validityHours", emergencyResult.getValidityDuration().toHours(),
                    "ageConsideration", ageResult.getResult(),
                    "dataSharing", dataSharingResult.getResult(),
                    "facility", facilityResult.getResult()
                )
            );
        }

        // Check for guardian requirement
        if (ageResult.isGuardianRequired()) {
            return ConsentDecision.guardianRequired(
                request.getRequestId(),
                "Guardian consent required",
                ageResult.getReason()
            );
        }

        // Check for data sharing approval requirements
        if (dataSharingResult.isApprovalRequired()) {
            return ConsentDecision.approvalRequired(
                request.getRequestId(),
                "Data sharing requires special approval",
                dataSharingResult.getReason()
            );
        }

        // Standard consent approval
        NepalConsentType consentType = determineConsentType(request);
        Duration validityDuration = CONSENT_DURATIONS.getOrDefault(consentType, Duration.ofDays(365));

        return ConsentDecision.approved(
            request.getRequestId(),
            "Consent approved under Nepal healthcare policy",
            validityDuration,
            Map.of(
                "consentType", consentType.name(),
                "validityDays", validityDuration.toDays(),
                "ageConsideration", ageResult.getResult(),
                "dataSharing", dataSharingResult.getResult(),
                "facility", facilityResult.getResult(),
                "province", request.getHealthcareContext().getProvince(),
                "district", request.getHealthcareContext().getDistrict()
            )
        );
    }

    private void sendConsentNotifications(ConsentRequest request, ConsentDecision decision,
                                        KernelTenantContext context) {
        try {
            // Send notification to patient
            if (decision.isApproved()) {
                notificationService.sendConsentApprovalNotification(request, decision, context);
            } else if (decision.isGuardianRequired()) {
                notificationService.sendGuardianConsentRequest(request, context);
            } else {
                notificationService.sendConsentDenialNotification(request, decision, context);
            }

            // Send notification to healthcare provider
            notificationService.sendProviderConsentNotification(request, decision, context);

            // Send notification to guardian if required
            if (decision.isGuardianRequired()) {
                notificationService.sendGuardianNotification(request, context);
            }

        } catch (Exception e) {
            logger.error("Error sending consent notifications for request: {}", request.getRequestId(), e);
        }
    }

    // Helper methods
    private NepalConsentType determineConsentType(ConsentRequest request) {
        // Determine consent type based on request context
        if (request.isEmergency()) {
            return NepalConsentType.EMERGENCY_CONSENT;
        }

        String serviceType = request.getServiceType();
        if (serviceType == null) {
            return null;
        }

        switch (serviceType.toLowerCase()) {
            case "primary_care":
            case "general_practice":
                return NepalConsentType.PRIMARY_CARE_CONSENT;
            case "specialist":
            case "consultation":
                return NepalConsentType.SPECIALIST_CONSENT;
            case "research":
            case "clinical_trial":
                return NepalConsentType.RESEARCH_CONSENT;
            case "telehealth":
            case "virtual_consultation":
                return NepalConsentType.TELEHEALTH_CONSENT;
            case "pharmacy":
            case "medication":
                return NepalConsentType.PHARMACY_CONSENT;
            case "laboratory":
            case "lab_test":
                return NepalConsentType.LABORATORY_CONSENT;
            case "imaging":
            case "radiology":
                return NepalConsentType.IMAGING_CONSENT;
            case "billing":
            case "payment":
                return NepalConsentType.BILLING_CONSENT;
            default:
                return null;
        }
    }

    private boolean isProviderRegistered(String providerId, String province) {
        // Implementation to check provider registration in Nepal healthcare system
        // This would integrate with Nepal healthcare provider registry
        return true; // Placeholder
    }

    private boolean isFacilityRegistered(String facilityId, String province) {
        // Implementation to check facility registration in Nepal healthcare system
        // This would integrate with Nepal healthcare facility registry
        return true; // Placeholder
    }

    private boolean isValidDataSharingPurpose(String purpose, KernelTenantContext context) {
        // Implementation to validate data sharing purposes against Nepal healthcare regulations
        Set<String> validPurposes = Set.of(
            "treatment", "care_coordination", "billing", "research", "public_health", "quality_improvement"
        );
        return validPurposes.contains(purpose.toLowerCase());
    }

    private boolean isCrossBorderDataSharing(String purpose) {
        // Implementation to check if data sharing purpose involves cross-border transfer
        return purpose.toLowerCase().contains("international") ||
               purpose.toLowerCase().contains("cross_border");
    }

    private FacilityType getFacilityType(String facilityId) {
        // Implementation to determine facility type
        // This would integrate with Nepal healthcare facility registry
        return FacilityType.GOVERNMENT_HOSPITAL; // Placeholder
    }

    // Result classes
    private static class ConsentValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ConsentValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ConsentValidationResult success() {
            return new ConsentValidationResult(true, null);
        }

        public static ConsentValidationResult failed(String errorMessage) {
            return new ConsentValidationResult(false, errorMessage);
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }

    private static class AgeConsentResult {
        private final String result;
        private final String reason;

        private AgeConsentResult(String result, String reason) {
            this.result = result;
            this.reason = reason;
        }

        public static AgeConsentResult adultConsent(String reason) {
            return new AgeConsentResult("adult_consent", reason);
        }

        public static AgeConsentResult minorWithGuardianNotification(String reason) {
            return new AgeConsentResult("minor_guardian_notification", reason);
        }

        public static AgeConsentResult guardianRequired(String reason) {
            return new AgeConsentResult("guardian_required", reason);
        }

        public static AgeConsentResult elderlySpecialConsent(String reason) {
            return new AgeConsentResult("elderly_special_consent", reason);
        }

        public String getResult() { return result; }
        public String getReason() { return reason; }
        public boolean isGuardianRequired() { return "guardian_required".equals(result); }
    }

    private static class EmergencyConsentResult {
        private final String result;
        private final String reason;
        private final Duration validityDuration;

        private EmergencyConsentResult(String result, String reason, Duration validityDuration) {
            this.result = result;
            this.reason = reason;
            this.validityDuration = validityDuration;
        }

        public static EmergencyConsentResult emergencyApproved(String reason, Duration validityDuration) {
            return new EmergencyConsentResult("emergency_approved", reason, validityDuration);
        }

        public static EmergencyConsentResult nonEmergency(String reason) {
            return new EmergencyConsentResult("non_emergency", reason, null);
        }

        public String getResult() { return result; }
        public String getReason() { return reason; }
        public Duration getValidityDuration() { return validityDuration; }
        public boolean isEmergencyApproved() { return "emergency_approved".equals(result); }
    }

    private static class DataSharingConsentResult {
        private final String result;
        private final String reason;

        private DataSharingConsentResult(String result, String reason) {
            this.result = result;
            this.reason = reason;
        }

        public static DataSharingConsentResult noDataSharing(String reason) {
            return new DataSharingConsentResult("no_data_sharing", reason);
        }

        public static DataSharingConsentResult approved(String reason) {
            return new DataSharingConsentResult("approved", reason);
        }

        public static DataSharingConsentResult invalidPurpose(String reason) {
            return new DataSharingConsentResult("invalid_purpose", reason);
        }

        public static DataSharingConsentResult crossBorderRequiresApproval(String reason) {
            return new DataSharingConsentResult("cross_border_approval_required", reason);
        }

        public String getResult() { return result; }
        public String getReason() { return reason; }
        public boolean isApprovalRequired() {
            return "cross_border_approval_required".equals(result);
        }
    }

    private static class FacilityConsentResult {
        private final String result;
        private final String reason;

        private FacilityConsentResult(String result, String reason) {
            this.result = result;
            this.reason = reason;
        }

        public static FacilityConsentResult governmentFacility(String reason) {
            return new FacilityConsentResult("government_facility", reason);
        }

        public static FacilityConsentResult privateFacility(String reason) {
            return new FacilityConsentResult("private_facility", reason);
        }

        public static FacilityConsentResult clinicFacility(String reason) {
            return new FacilityConsentResult("clinic_facility", reason);
        }

        public static FacilityConsentResult pharmacyFacility(String reason) {
            return new FacilityConsentResult("pharmacy_facility", reason);
        }

        public static FacilityConsentResult laboratoryFacility(String reason) {
            return new FacilityConsentResult("laboratory_facility", reason);
        }

        public static FacilityConsentResult unknownFacility(String reason) {
            return new FacilityConsentResult("unknown_facility", reason);
        }

        public String getResult() { return result; }
        public String getReason() { return reason; }
    }

    public enum FacilityType {
        GOVERNMENT_HOSPITAL,
        PRIVATE_HOSPITAL,
        CLINIC,
        PHARMACY,
        LABORATORY,
        UNKNOWN
    }
}
```

#### 2.1.3 PHR Data-Cloud Integration - Complete Implementation

**PHR Data Store Configuration:**

```java
// Location: products/phr/kernel/src/main/java/com/ghatana/phr/kernel/data/
package com.ghatana.phr.kernel.data;

import com.ghatana.datacloud.platform.DataCloudPlatform;
import com.ghatana.datacloud.config.DataStoreConfig;
import com.ghatana.datacloud.governance.DataGovernance;
import com.ghatana.datacloud.audit.AuditLevel;
import com.ghatana.datacloud.encryption.EncryptionLevel;
import com.ghatana.phr.config.PhrDataConfiguration;

import java.time.Duration;
import java.util.Map;
import java.util.HashMap;

/**
 * PHR-specific data store configuration and initialization.
 * Implements healthcare data governance and compliance requirements.
 *
 * <p><b>ASYNC CONVENTION</b>: All async methods use ActiveJ {@link Promise}.
 * Data-Cloud returns are wrapped via {@code Promise.ofFuture()}.</p>
 *
 * @doc.type class
 * @doc.purpose Healthcare data store lifecycle management and governance
 * @doc.layer product
 * @doc.pattern Service
 */
public class PhrDataStoreManager {

    private final DataCloudPlatform dataCloud;
    private final PhrDataConfiguration phrDataConfig;
    private final Map<String, DataStoreConfig> configuredStores;
    private final Logger logger;

    public PhrDataStoreManager(DataCloudPlatform dataCloud, PhrDataConfiguration phrDataConfig) {
        this.dataCloud = Objects.requireNonNull(dataCloud);
        this.phrDataConfig = Objects.requireNonNull(phrDataConfig);
        this.configuredStores = new HashMap<>();
        this.logger = LoggerFactory.getLogger(PhrDataStoreManager.class);
    }

    /**
     * Initialize all PHR-specific data stores (ActiveJ Promise chain).
     */
    public Promise<Void> initializeDataStores() {
        logger.info("Initializing PHR data stores");

        return initializePatientDataStore()
            .then(() -> initializeConsentRecordsStore())
            .then(() -> initializeDocumentStore())
            .then(() -> initializeAppointmentStore())
            .then(() -> initializeMedicationStore())
            .then(() -> initializeBillingStore())
            .then(() -> initializeAuditStore())
            .then(() -> initializeFhirResourceStore())
            .then(() -> initializeImagingStore())
            .then(() -> initializeReferralStore())
            .whenResult(() -> {
                logger.info("All PHR data stores initialized successfully");
                logDataStoreStatistics();
            })
            .whenException(e -> {
                logger.error("Failed to initialize PHR data stores", e);
            });
    }

    private Promise<Void> initializePatientDataStore() {
        logger.info("Initializing patient data store");

        DataStoreConfig config = DataStoreConfig.builder()
            .withStoreId("patient.records")
            .withSchema("patient-schema-v1")
            .withDescription("PHR patient records and demographics")
            .withRetention(phrDataConfig.getPatientDataRetention()) // 25 years for healthcare
            .withGovernance(DataGovernance.HEALTHCARE)
            .withEncryption(EncryptionLevel.STRONG)
            .withAuditLevel(AuditLevel.DETAILED)
            .withImmutable(false) // Patient data can be updated
            .withVersioning(true) // Track all changes
            .withIndexing(createPatientIndexing())
            .withValidation(createPatientValidation())
            .withBackupPolicy(createHealthcareBackupPolicy())
            .withAccessPolicy(createPatientAccessPolicy())
            .build();

        return Promise.ofFuture(dataCloud.createStore(config))
            .whenResult(() -> {
                configuredStores.put("patient.records", config);
                logger.info("Patient data store initialized successfully");
            });
    }

    private Promise<Void> initializeConsentRecordsStore() {
        logger.info("Initializing consent records store");

        DataStoreConfig config = DataStoreConfig.builder()
            .withStoreId("patient.consents")
            .withSchema("consent-schema-v1")
            .withDescription("PHR patient consent records and preferences")
            .withRetention(phrDataConfig.getConsentDataRetention()) // 10 years for consent
            .withGovernance(DataGovernance.HEALTHCARE)
            .withEncryption(EncryptionLevel.STRONG)
            .withAuditLevel(AuditLevel.DETAILED)
            .withImmutable(true) // Consent records are immutable
            .withVersioning(false) // No versioning for immutable records
            .withIndexing(createConsentIndexing())
            .withValidation(createConsentValidation())
            .withBackupPolicy(createHealthcareBackupPolicy())
            .withAccessPolicy(createConsentAccessPolicy())
            .build();

        return Promise.ofFuture(dataCloud.createStore(config))
            .whenResult(() -> {
                configuredStores.put("patient.consents", config);
                logger.info("Consent records store initialized successfully");
            });
    }

    private Promise<Void> initializeDocumentStore() {
        logger.info("Initializing document store");

        DataStoreConfig config = DataStoreConfig.builder()
            .withStoreId("patient.documents")
            .withSchema("document-schema-v1")
            .withDescription("PHR patient documents and medical records")
            .withRetention(phrDataConfig.getDocumentDataRetention()) // 7 years for documents
            .withGovernance(DataGovernance.HEALTHCARE)
            .withEncryption(EncryptionLevel.STRONG)
            .withAuditLevel(AuditLevel.DETAILED)
            .withImmutable(false) // Documents can be updated (metadata)
            .withVersioning(true) // Track document changes
            .withIndexing(createDocumentIndexing())
            .withValidation(createDocumentValidation())
            .withBackupPolicy(createHealthcareBackupPolicy())
            .withAccessPolicy(createDocumentAccessPolicy())
            .withStoragePolicy(createDocumentStoragePolicy()) // Special storage for large files
            .build();

        return Promise.ofFuture(dataCloud.createStore(config))
            .whenResult(() -> {
                configuredStores.put("patient.documents", config);
                logger.info("Document store initialized successfully");
            });
    }

    private Promise<Void> initializeAppointmentStore() {
        logger.info("Initializing appointment store");

        DataStoreConfig config = DataStoreConfig.builder()
            .withStoreId("patient.appointments")
            .withSchema("appointment-schema-v1")
            .withDescription("PHR patient appointments and schedules")
            .withRetention(phrDataConfig.getAppointmentDataRetention()) // 5 years for appointments
            .withGovernance(DataGovernance.HEALTHCARE)
            .withEncryption(EncryptionLevel.STANDARD)
            .withAuditLevel(AuditLevel.STANDARD)
            .withImmutable(false) // Appointments can be rescheduled
            .withVersioning(true) // Track appointment changes
            .withIndexing(createAppointmentIndexing())
            .withValidation(createAppointmentValidation())
            .withBackupPolicy(createHealthcareBackupPolicy())
            .withAccessPolicy(createAppointmentAccessPolicy())
            .withTtl(Duration.ofDays(365 * 5)) // Auto-cleanup after 5 years
            .build();

        return Promise.ofFuture(dataCloud.createStore(config))
            .whenResult(() -> {
                configuredStores.put("patient.appointments", config);
                logger.info("Appointment store initialized successfully");
            });
    }

    private Promise<Void> initializeMedicationStore() {
        logger.info("Initializing medication store");

        DataStoreConfig config = DataStoreConfig.builder()
            .withStoreId("patient.medications")
            .withSchema("medication-schema-v1")
            .withDescription("PHR patient medications and prescriptions")
            .withRetention(phrDataConfig.getMedicationDataRetention()) // 10 years for medications
            .withGovernance(DataGovernance.HEALTHCARE)
            .withEncryption(EncryptionLevel.STRONG)
            .withAuditLevel(AuditLevel.DETAILED)
            .withImmutable(false) // Medication records can be updated
            .withVersioning(true) // Track medication changes
            .withIndexing(createMedicationIndexing())
            .withValidation(createMedicationValidation())
            .withBackupPolicy(createHealthcareBackupPolicy())
            .withAccessPolicy(createMedicationAccessPolicy())
            .build();

        return Promise.ofFuture(dataCloud.createStore(config))
            .whenResult(() -> {
                configuredStores.put("patient.medications", config);
                logger.info("Medication store initialized successfully");
            });
    }

    private Promise<Void> initializeBillingStore() {
        logger.info("Initializing billing store");

        DataStoreConfig config = DataStoreConfig.builder()
            .withStoreId("patient.billing")
            .withSchema("billing-schema-v1")
            .withDescription("PHR patient billing and payment records")
            .withRetention(phrDataConfig.getBillingDataRetention()) // 7 years for billing
            .withGovernance(DataGovernance.FINANCIAL) // Financial governance for billing
            .withEncryption(EncryptionLevel.STRONG)
            .withAuditLevel(AuditLevel.DETAILED)
            .withImmutable(true) // Billing records are immutable
            .withVersioning(false) // No versioning for immutable records
            .withIndexing(createBillingIndexing())
            .withValidation(createBillingValidation())
            .withBackupPolicy(createFinancialBackupPolicy())
            .withAccessPolicy(createBillingAccessPolicy())
            .build();

        return Promise.ofFuture(dataCloud.createStore(config))
            .whenResult(() -> {
                configuredStores.put("patient.billing", config);
                logger.info("Billing store initialized successfully");
            });
    }

    private Promise<Void> initializeAuditStore() {
        logger.info("Initializing audit store");

        DataStoreConfig config = DataStoreConfig.builder()
            .withStoreId("phr.audit")
            .withSchema("audit-schema-v1")
            .withDescription("PHR comprehensive audit logs")
            .withRetention(phrDataConfig.getAuditDataRetention()) // 10 years for audit
            .withGovernance(DataGovernance.AUDIT)
            .withEncryption(EncryptionLevel.STRONG)
            .withAuditLevel(AuditLevel.COMPREHENSIVE)
            .withImmutable(true) // Audit logs are immutable
            .withVersioning(false) // No versioning for immutable records
            .withIndexing(createAuditIndexing())
            .withValidation(createAuditValidation())
            .withBackupPolicy(createAuditBackupPolicy())
            .withAccessPolicy(createAuditAccessPolicy())
            .withCompression(true) // Compress audit logs to save space
            .withArchiving(true) // Archive old audit logs
            .build();

        return Promise.ofFuture(dataCloud.createStore(config))
            .whenResult(() -> {
                configuredStores.put("phr.audit", config);
                logger.info("Audit store initialized successfully");
            });
    }

    private Promise<Void> initializeFhirResourceStore() {
        logger.info("Initializing FHIR resource store");

        DataStoreConfig config = DataStoreConfig.builder()
            .withStoreId("phr.fhir")
            .withSchema("fhir-schema-v1")
            .withDescription("PHR FHIR R4 resources and interoperability data")
            .withRetention(phrDataConfig.getFhirDataRetention()) // 7 years for FHIR data
            .withGovernance(DataGovernance.HEALTHCARE)
            .withEncryption(EncryptionLevel.STANDARD)
            .withAuditLevel(AuditLevel.STANDARD)
            .withImmutable(false) // FHIR resources can be updated
            .withVersioning(true) // Track FHIR resource versions
            .withIndexing(createFhirIndexing())
            .withValidation(createFhirValidation())
            .withBackupPolicy(createHealthcareBackupPolicy())
            .withAccessPolicy(createFhirAccessPolicy())
            .withFhirCompliance(true) // Enable FHIR-specific compliance
            .build();

        return Promise.ofFuture(dataCloud.createStore(config))
            .whenResult(() -> {
                configuredStores.put("phr.fhir", config);
                logger.info("FHIR resource store initialized successfully");
            });
    }

    private Promise<Void> initializeImagingStore() {
        logger.info("Initializing imaging store");

        DataStoreConfig config = DataStoreConfig.builder()
            .withStoreId("patient.imaging")
            .withSchema("imaging-schema-v1")
            .withDescription("PHR patient imaging studies and DICOM data")
            .withRetention(phrDataConfig.getImagingDataRetention()) // 10 years for imaging
            .withGovernance(DataGovernance.HEALTHCARE)
            .withEncryption(EncryptionLevel.STRONG)
            .withAuditLevel(AuditLevel.DETAILED)
            .withImmutable(false) // Imaging metadata can be updated
            .withVersioning(true) // Track imaging changes
            .withIndexing(createImagingIndexing())
            .withValidation(createImagingValidation())
            .withBackupPolicy(createImagingBackupPolicy())
            .withAccessPolicy(createImagingAccessPolicy())
            .withStoragePolicy(createImagingStoragePolicy()) // Special storage for DICOM
            .withDicomCompliance(true) // Enable DICOM-specific compliance
            .build();

        return Promise.ofFuture(dataCloud.createStore(config))
            .whenResult(() -> {
                configuredStores.put("patient.imaging", config);
                logger.info("Imaging store initialized successfully");
            });
    }

    private Promise<Void> initializeReferralStore() {
        logger.info("Initializing referral store");

        DataStoreConfig config = DataStoreConfig.builder()
            .withStoreId("patient.referrals")
            .withSchema("referral-schema-v1")
            .withDescription("PHR patient referrals and care coordination")
            .withRetention(phrDataConfig.getReferralDataRetention()) // 7 years for referrals
            .withGovernance(DataGovernance.HEALTHCARE)
            .withEncryption(EncryptionLevel.STANDARD)
            .withAuditLevel(AuditLevel.STANDARD)
            .withImmutable(false) // Referral status can be updated
            .withVersioning(true) // Track referral changes
            .withIndexing(createReferralIndexing())
            .withValidation(createReferralValidation())
            .withBackupPolicy(createHealthcareBackupPolicy())
            .withAccessPolicy(createReferralAccessPolicy())
            .build();

        return Promise.ofFuture(dataCloud.createStore(config))
            .whenResult(() -> {
                configuredStores.put("patient.referrals", config);
                logger.info("Referral store initialized successfully");
            });
    }

    // Configuration helper methods
    private IndexingConfiguration createPatientIndexing() {
        return IndexingConfiguration.builder()
            .withIndex("patient_id", IndexType.HASH)
            .withIndex("national_id", IndexType.HASH)
            .withIndex("date_of_birth", IndexType.RANGE)
            .withIndex("facility_id", IndexType.HASH)
            .withIndex("created_at", IndexType.RANGE)
            .withIndex("updated_at", IndexType.RANGE)
            .build();
    }

    private ValidationConfiguration createPatientValidation() {
        return ValidationConfiguration.builder()
            .withRequiredField("patient_id")
            .withRequiredField("national_id")
            .withRequiredField("date_of_birth")
            .withRequiredField("facility_id")
            .withFieldValidator("national_id", new NepalNationalIdValidator())
            .withFieldValidator("date_of_birth", new DateOfBirthValidator())
            .build();
    }

    private AccessPolicy createPatientAccessPolicy() {
        return AccessPolicy.builder()
            .withReadPermission("patient.read")
            .withWritePermission("patient.write")
            .withDeletePermission("patient.delete")
            .withOwnerAccess(true) // Patients can access their own records
            .withProviderAccess(true) // Providers can access patient records
            .withEmergencyAccess(true) // Emergency access allowed
            .withAuditAllAccess(true) // Audit all access attempts
            .build();
    }

    private BackupPolicy createHealthcareBackupPolicy() {
        return BackupPolicy.builder()
            .withFrequency(BackupFrequency.DAILY)
            .withRetention(Duration.ofDays(90)) // Keep backups for 90 days
            .withEncryption(true)
            .withCrossRegionBackup(true) // Cross-region backup for disaster recovery
            .withVerification(true) // Verify backup integrity
            .build();
    }

    private StoragePolicy createDocumentStoragePolicy() {
        return StoragePolicy.builder()
            .withMaxFileSize(100 * 1024 * 1024) // 100MB max file size
            .withAllowedTypes(Set.of("pdf", "jpg", "png", "doc", "docx", "txt", "dicom"))
            .withCompression(true)
            .withVirusScanning(true)
            .withContentAnalysis(true) // Analyze document content for PII
            .build();
    }

    private StoragePolicy createImagingStoragePolicy() {
        return StoragePolicy.builder()
            .withMaxFileSize(2 * 1024 * 1024 * 1024) // 2GB max for DICOM
            .withAllowedTypes(Set.of("dicom", "dcm", "jpg", "png"))
            .withCompression(false) // Don't compress medical images
            .withVirusScanning(true)
            .withDicomValidation(true) // Validate DICOM format
            .withMetadataExtraction(true) // Extract DICOM metadata
            .build();
    }

    private BackupPolicy createFinancialBackupPolicy() {
        return BackupPolicy.builder()
            .withFrequency(BackupFrequency.HOURLY) // More frequent for financial data
            .withRetention(Duration.ofDays(365)) // Keep financial backups for 1 year
            .withEncryption(true)
            .withCrossRegionBackup(true)
            .withVerification(true)
            .withIntegrityChecking(true) // Check financial data integrity
            .build();
    }

    private BackupPolicy createAuditBackupPolicy() {
        return BackupPolicy.builder()
            .withFrequency(BackupFrequency.CONTINUOUS) // Continuous backup for audit
            .withRetention(Duration.ofDays(365 * 10)) // Keep audit backups for 10 years
            .withEncryption(true)
            .withCrossRegionBackup(true)
            .withVerification(true)
            .withImmutableBackup(true) // Audit backups are immutable
            .build();
    }

    // Statistics and monitoring
    private void logDataStoreStatistics() {
        logger.info("PHR Data Store Statistics:");
        configuredStores.forEach((storeId, config) -> {
            logger.info("  Store: {} - Retention: {} - Governance: {} - Encryption: {}",
                storeId, config.getRetention(), config.getGovernance(), config.getEncryption());
        });
    }

    /**
     * Get configuration for a specific data store
     */
    public Optional<DataStoreConfig> getDataStoreConfig(String storeId) {
        return Optional.ofNullable(configuredStores.get(storeId));
    }

    /**
     * Get all configured data stores
     */
    public Map<String, DataStoreConfig> getAllDataStoreConfigs() {
        return Collections.unmodifiableMap(configuredStores);
    }

    /**
     * Update data store configuration
     */
    public Promise<Void> updateDataStoreConfig(String storeId, DataStoreConfig newConfig) {
        return Promise.ofFuture(dataCloud.updateStore(storeId, newConfig))
            .whenResult(() -> {
                configuredStores.put(storeId, newConfig);
                logger.info("Data store configuration updated: {}", storeId);
            });
    }

    /**
     * Get data store statistics
     */
    public Promise<DataStoreStatistics> getDataStoreStatistics(String storeId) {
        return Promise.ofFuture(dataCloud.getStoreStatistics(storeId));
    }

    /**
     * Cleanup expired data based on retention policies
     */
    public Promise<Void> cleanupExpiredData() {
        logger.info("Starting cleanup of expired PHR data");

        return Promises.all(
            configuredStores.keySet().stream()
                .map(storeId -> Promise.ofFuture(dataCloud.cleanupExpiredData(storeId)))
        ).whenResult(() -> {
            logger.info("Cleanup of expired PHR data completed");
        });
    }

    /**
     * Validate data store compliance
     */
    public Promise<ComplianceReport> validateDataStoreCompliance() {
        logger.info("Validating PHR data store compliance");

        return Promise.ofBlocking(executor, () -> {
            ComplianceReport.Builder reportBuilder = ComplianceReport.builder();

            for (Map.Entry<String, DataStoreConfig> entry : configuredStores.entrySet()) {
                String storeId = entry.getKey();
                DataStoreConfig config = entry.getValue();

                HealthcareComplianceResult healthcareResult = validateHealthcareCompliance(config);
                reportBuilder.withStoreCompliance(storeId, healthcareResult);

                RetentionComplianceResult retentionResult = validateRetentionCompliance(config);
                reportBuilder.withRetentionCompliance(storeId, retentionResult);

                EncryptionComplianceResult encryptionResult = validateEncryptionCompliance(config);
                reportBuilder.withEncryptionCompliance(storeId, encryptionResult);
            }

            return reportBuilder.build();
        });
    }

    // Compliance validation methods
    private HealthcareComplianceResult validateHealthcareCompliance(DataStoreConfig config) {
        // Validate healthcare-specific compliance requirements
        boolean isCompliant = true;
        List<String> violations = new ArrayList<>();

        if (config.getGovernance() != DataGovernance.HEALTHCARE &&
            config.getGovernance() != DataGovernance.AUDIT) {
            isCompliant = false;
            violations.add("Store must use HEALTHCARE or AUDIT governance");
        }

        if (config.getEncryption() != EncryptionLevel.STRONG &&
            config.getEncryption() != EncryptionLevel.MAXIMUM) {
            isCompliant = false;
            violations.add("Store must use STRONG or MAXIMUM encryption");
        }

        if (config.getAuditLevel() != AuditLevel.DETAILED &&
            config.getAuditLevel() != AuditLevel.COMPREHENSIVE) {
            isCompliant = false;
            violations.add("Store must use DETAILED or COMPREHENSIVE audit level");
        }

        return new HealthcareComplianceResult(isCompliant, violations);
    }

    private RetentionComplianceResult validateRetentionCompliance(DataStoreConfig config) {
        // Validate retention period compliance
        boolean isCompliant = true;
        List<String> violations = new ArrayList<>();

        Duration retention = config.getRetention();
        Duration minimumRetention = phrDataConfig.getMinimumRetention();

        if (retention.compareTo(minimumRetention) < 0) {
            isCompliant = false;
            violations.add(String.format(
                "Retention period %s is less than minimum required %s",
                retention, minimumRetention
            ));
        }

        return new RetentionComplianceResult(isCompliant, violations);
    }

    private EncryptionComplianceResult validateEncryptionCompliance(DataStoreConfig config) {
        // Validate encryption compliance
        boolean isCompliant = true;
        List<String> violations = new ArrayList<>();

        if (!config.isEncryptionAtRest()) {
            isCompliant = false;
            violations.add("Encryption at rest is required");
        }

        if (!config.isEncryptionInTransit()) {
            isCompliant = false;
            violations.add("Encryption in transit is required");
        }

        return new EncryptionComplianceResult(isCompliant, violations);
    }
}
```

### 2.2 Detailed Implementation Tasks (Weeks 3-4)

#### 2.2.1 Week 3 Task Breakdown

**Day 15-16: PHR Kernel Module**

```yaml
Day_15_Tasks:
  Task_1:
    Name: "Create PHR kernel module"
    Description: "Implement comprehensive PHR kernel module with all healthcare services"
    Files:
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/PhrKernelModule.java"
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/config/PhrConfiguration.java"
    Dependencies: ["Phase 1 completion"]
    Estimated_Hours: 12
    Acceptance_Criteria:
      - Complete PHR kernel module implementation
      - All healthcare services initialized
      - Event handlers registered
      - Healthcare policies configured

Day_16_Tasks:
  Task_2:
    Name: "Implement healthcare consent enhancement"
    Description: "Create Nepal healthcare-specific consent policy"
    Files:
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/consent/NepalHealthcareConsentPolicy.java"
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/consent/ConsentAuditService.java"
    Dependencies: ["Task_1"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Nepal healthcare consent policy implemented
      - Age-based consent validation
      - Emergency consent handling
      - Data sharing consent validation

Day_17-18_Tasks:
  Task_3:
    Name: "Create PHR data store manager"
    Description: "Implement PHR-specific data store configuration and initialization"
    Files:
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/data/PhrDataStoreManager.java"
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/config/PhrDataConfiguration.java"
    Dependencies: ["Task_1"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - All PHR data stores configured
      - Healthcare governance applied
      - Data retention policies set
      - Encryption and audit configured

Day_18_Tasks:
  Task_4:
    Name: "Implement PHR event processing"
    Description: "Create PHR-specific event processing through AEP"
    Files:
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/events/PhrEventProcessor.java"
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/events/PhrEventHandler.java"
    Dependencies: ["Task_1", "Task_3"]
    Estimated_Hours: 8
    Acceptance_Criteria:
      - PHR event streams created
      - Event handlers implemented
      - Healthcare workflows configured
      - Event validation and routing

Day_19-20_Tasks:
  Task_5:
    Name: "Create PHR service implementations"
    Description: "Implement core PHR services (patient, consent, document, etc.)"
    Files:
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/service/PatientService.java"
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/service/ConsentService.java"
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/service/DocumentService.java"
    Dependencies: ["Task_1", "Task_3", "Task_4"]
    Estimated_Hours: 12
    Acceptance_Criteria:
      - Core PHR services implemented
      - Data-Cloud integration complete
      - Event processing integrated
      - Service health monitoring

Day_20_Tasks:
  Task_6:
    Name: "Setup PHR build configuration"
    Description: "Create Gradle build files and dependencies for PHR kernel"
    Files:
      - "products/phr/kernel/build.gradle.kts"
      - "products/phr/kernel/settings.gradle.kts"
    Dependencies: ["Task_5"]
    Estimated_Hours: 4
    Acceptance_Criteria:
      - PHR kernel build configuration
      - Dependencies defined
      - Test configuration
      - Integration with kernel platform
```

#### 2.2.2 Week 4 Task Breakdown

**Day 21-22: PHR Advanced Features**

```yaml
Day_21_Tasks:
  Task_7:
    Name: "Implement FHIR interop plugin"
    Description: "Create FHIR R4 interoperability plugin for PHR"
    Files:
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/fhir/FhirInteropKernelPlugin.java"
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/fhir/FhirProcessorRegistry.java"
    Dependencies: ["Task_5"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - FHIR R4 plugin implemented
      - Resource processors configured
      - FHIR validation enabled
      - Interoperability workflows

Day_22_Tasks:
  Task_8:
    Name: "Create PHR audit integration"
    Description: "Implement enhanced audit service for PHR with healthcare compliance"
    Files:
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/audit/PhrAuditService.java"
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/audit/HealthcareAuditLogger.java"
    Dependencies: ["Task_3", "Task_5"]
    Estimated_Hours: 8
    Acceptance_Criteria:
      - PHR audit service implemented
      - Healthcare compliance logging
      - Long-term retention policies
      - Audit reporting capabilities

Day_23-24_Tasks:
  Task_9:
    Name: "Implement PHR multi-tenant isolation"
    Description: "Create tenant isolation and feature gating for PHR"
    Files:
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/tenant/PhrTenantContext.java"
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/tenant/PhrFeatureFlagService.java"
    Dependencies: ["Task_1", "Task_3"]
    Estimated_Hours: 8
    Acceptance_Criteria:
      - PHR tenant isolation implemented
      - Healthcare-specific features gated
      - Facility-level isolation
      - Tenant configuration management

Day_24_Tasks:
  Task_10:
    Name: "Create PHR health monitoring"
    Description: "Implement comprehensive health monitoring for PHR services"
    Files:
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/health/PhrHealthMonitor.java"
      - "products/phr/kernel/src/main/java/com/ghatana/phr/kernel/health/HealthcareHealthCheck.java"
    Dependencies: ["Task_5"]
    Estimated_Hours: 6
    Acceptance_Criteria:
      - PHR health monitoring implemented
      - Service health checks
      - Healthcare-specific metrics
      - Alerting and notification

Day_25-26_Tasks:
  Task_11:
    Name: "Implement PHR testing framework"
    Description: "Create comprehensive testing framework for PHR kernel"
    Files:
      - "products/phr/kernel/src/test/java/com/ghatana/phr/kernel/PhrKernelIntegrationTest.java"
      - "products/phr/kernel/src/test/java/com/ghatana/phr/kernel/PhrConsentTest.java"
    Dependencies: ["Task_10"]
    Estimated_Hours: 8
    Acceptance_Criteria:
      - PHR integration tests created
      - Consent policy tests
      - Data store tests
      - Event processing tests

Day_26_Tasks:
  Task_12:
    Name: "PHR integration validation"
    Description: "Validate end-to-end PHR kernel integration"
    Files:
      - "products/phr/kernel/src/test/java/com/ghatana/phr/kernel/PhrEndToEndTest.java"
      - "products/phr/kernel/validation/PhrValidationReport.md"
    Dependencies: ["Task_11"]
    Estimated_Hours: 6
    Acceptance_Criteria:
      - End-to-end PHR scenarios tested
      - Integration validation complete
      - Performance benchmarks
      - Compliance validation
```

---

## Phase 3: Finance System Kernel Integration (Weeks 5-6)

### 3.1 Detailed Finance Module Analysis and Implementation

#### 3.1.1 Finance Kernel Module - Complete Implementation

**Finance Kernel Module Structure:**

```java
// Location: products/finance/kernel/src/main/java/com/ghatana/finance/kernel/
package com.ghatana.finance.kernel;

import com.ghatana.kernel.module.AbstractKernelModule;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.tenant.KernelTenantContext;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import com.ghatana.finance.kernel.service.*;
import com.ghatana.finance.kernel.config.FinanceConfiguration;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;

/**
 * Finance-specific kernel module implementation.
 * Provides financial services capabilities with regulatory compliance.
 *
 * @doc.type class
 * @doc.purpose Finance product module delivering trading/risk/compliance via kernel
 * @doc.layer product
 * @doc.pattern Service
 */
public class FinanceKernelModule extends AbstractKernelModule {

    private final FinanceConfiguration financeConfiguration;
    private final Set<FinanceService> financeServices;

    // Finance-specific services
    private OrderManagementService orderManagementService;
    private ExecutionManagementService executionManagementService;
    private PortfolioManagementService portfolioManagementService;
    private MarketDataService marketDataService;
    private PricingService pricingService;
    private RiskManagementService riskManagementService;
    private ComplianceEngineService complianceEngineService;
    private SurveillanceSystemService surveillanceSystemService;
    private LedgerService ledgerService;
    private ReportingService reportingService;

    public FinanceKernelModule(FinanceConfiguration financeConfiguration) {
        super(createDescriptor(), createCapabilities(), createDependencies());
        this.financeConfiguration = Objects.requireNonNull(financeConfiguration);
        this.financeServices = new HashSet<>();
    }

    private static KernelDescriptor createDescriptor() {
        return KernelDescriptor.builder()
            .withDescriptorId("finance-core")
            .withName("Finance Core Module")
            .withVersion("1.0.0")
            .withDescription("Financial services core module with trading, risk, and compliance capabilities")
            .withOwner("Finance Team")
            .withType(KernelDescriptor.DescriptorType.KERNEL_MODULE)
            .withCapability(KernelCapability.TRADE_PROCESSING)
            .withCapability(KernelCapability.RISK_MANAGEMENT)
            .withCapability(KernelCapability.COMPLIANCE_CHECKING)
            .withCapability(KernelCapability.MARKET_DATA)
            .withCapability(KernelCapability.PORTFOLIO_MANAGEMENT)
            .withCapability(KernelCapability.PRICING)
            .withCapability(KernelCapability.SURVEILLANCE)
            .withCapability(KernelCapability.LEDGER_MANAGEMENT)
            .withCapability(KernelCapability.REPORTING)
            .withTag("financial")
            .withTag("trading")
            .withTag("risk")
            .withTag("compliance")
            .withMetadata("regulatory", "SEBON, MIFID-II, Dodd-Frank")
            .withMetadata("compliance", "10-year retention")
            .withMetadata("calendar", "dual-calendar-support")
            .withComplianceRequirement("SOX")
            .withComplianceRequirement("MIFID-II")
            .withComplianceRequirement("Dodd-Frank")
            .withComplianceRequirement("SEBON")
            .build();
    }

    private static Set<KernelCapability> createCapabilities() {
        return Set.of(
            KernelCapability.TRADE_PROCESSING,
            KernelCapability.RISK_MANAGEMENT,
            KernelCapability.COMPLIANCE_CHECKING,
            KernelCapability.MARKET_DATA,
            KernelCapability.PORTFOLIO_MANAGEMENT,
            KernelCapability.PRICING,
            KernelCapability.SURVEILLANCE,
            KernelCapability.LEDGER_MANAGEMENT,
            KernelCapability.REPORTING
        );
    }

    private static Set<KernelDependency> createDependencies() {
        return Set.of(
            KernelDependency.builder()
                .withDependencyId("data-cloud")
                .withVersion("1.0.0")
                .withType(KernelDependency.DependencyType.EXTERNAL_SERVICE)
                .withOptional(false)
                .build(),
            KernelDependency.builder()
                .withDependencyId("aep-platform")
                .withVersion("1.0.0")
                .withType(KernelDependency.DependencyType.EXTERNAL_SERVICE)
                .withOptional(false)
                .build(),
            KernelDependency.builder()
                .withDependencyId("kernel-core")
                .withVersion("1.0.0")
                .withType(KernelDependency.DependencyType.MODULE)
                .withOptional(false)
                .build(),
            KernelDependency.builder()
                .withDependencyId("ai-platform")
                .withVersion("1.0.0")
                .withType(KernelDependency.DependencyType.EXTERNAL_SERVICE)
                .withOptional(true) // AI platform is optional for basic finance functions
                .build()
        );
    }

    @Override
    protected void doInitialize() throws Exception {
        logger.info("Initializing Finance kernel module");

        // Initialize finance configuration
        initializeConfiguration();

        // Initialize finance-specific services
        initializeFinanceServices();

        // Register event handlers
        registerEventHandlers();

        // Setup financial regulatory policies
        setupFinancialPolicies();

        // Initialize dual calendar support
        initializeDualCalendarSupport();

        logger.info("Finance kernel module initialized successfully");
    }

    @Override
    protected void doStart() throws Exception {
        logger.info("Starting Finance kernel module");

        // Start all finance services using ActiveJ Promises.all()
        Promises.all(
            financeServices.stream()
                .map(service -> service.start())
        ).whenResult(() -> {
            startMarketDataFeeds();
            startRiskMonitoring();
            startComplianceMonitoring();
            startSurveillanceSystems();
            registerWithFinancialMonitoring();
            logger.info("Finance kernel module started successfully");
        }).whenException(e -> logger.error("Failed to start finance services", e));
    }

    @Override
    protected void doStop() throws Exception {
        logger.info("Stopping Finance kernel module");

        stopSurveillanceSystems();
        stopComplianceMonitoring();
        stopRiskMonitoring();
        stopMarketDataFeeds();

        // Stop all finance services using ActiveJ Promises.all()
        Promises.all(
            financeServices.stream()
                .map(service -> service.stop())
        ).whenResult(() -> logger.info("Finance kernel module stopped successfully"))
         .whenException(e -> logger.error("Failed to stop finance services", e));
    }

    @Override
    protected void doCleanup() throws Exception {
        logger.info("Cleaning up Finance kernel module");

        // Cleanup all finance services
        for (FinanceService service : financeServices) {
            try {
                service.cleanup();
            } catch (Exception e) {
                logger.error("Error cleaning up finance service: {}", service.getClass().getSimpleName(), e);
            }
        }

        financeServices.clear();

        logger.info("Finance kernel module cleaned up successfully");
    }

    private void initializeConfiguration() {
        // Validate finance-specific configuration
        validateFinanceConfiguration();

        // Setup financial defaults
        setupFinancialDefaults();

        // Initialize regulatory configuration
        initializeRegulatoryConfiguration();
    }

    private void initializeFinanceServices() throws Exception {
        // Initialize order management service
        orderManagementService = new OrderManagementService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            financeConfiguration.getOrderManagementConfig()
        );
        financeServices.add(orderManagementService);

        // Initialize execution management service
        executionManagementService = new ExecutionManagementService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            financeConfiguration.getExecutionManagementConfig()
        );
        financeServices.add(executionManagementService);

        // Initialize portfolio management service
        portfolioManagementService = new PortfolioManagementService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            financeConfiguration.getPortfolioManagementConfig(),
            context.getOptionalDependency(AiPlatform.class, "ai-platform")
                .orElse(new DefaultAiPlatform())
        );
        financeServices.add(portfolioManagementService);

        // Initialize market data service
        marketDataService = new MarketDataService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            financeConfiguration.getMarketDataConfig()
        );
        financeServices.add(marketDataService);

        // Initialize pricing service
        pricingService = new PricingService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            financeConfiguration.getPricingConfig(),
            context.getOptionalDependency(AiPlatform.class, "ai-platform")
                .orElse(new DefaultAiPlatform())
        );
        financeServices.add(pricingService);

        // Initialize risk management service
        riskManagementService = new RiskManagementService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            financeConfiguration.getRiskManagementConfig(),
            context.getOptionalDependency(AiPlatform.class, "ai-platform")
                .orElse(new DefaultAiPlatform())
        );
        financeServices.add(riskManagementService);

        // Initialize compliance engine service
        complianceEngineService = new ComplianceEngineService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            financeConfiguration.getComplianceEngineConfig()
        );
        financeServices.add(complianceEngineService);

        // Initialize surveillance system service
        surveillanceSystemService = new SurveillanceSystemService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            financeConfiguration.getSurveillanceSystemConfig(),
            context.getOptionalDependency(AiPlatform.class, "ai-platform")
                .orElse(new DefaultAiPlatform())
        );
        financeServices.add(surveillanceSystemService);

        // Initialize ledger service
        ledgerService = new LedgerService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            financeConfiguration.getLedgerConfig()
        );
        financeServices.add(ledgerService);

        // Initialize reporting service
        reportingService = new ReportingService(
            context.getDependency(DataCloudPlatform.class, "data-cloud"),
            context.getDependency(AepPlatform.class, "aep-platform"),
            financeConfiguration.getReportingConfig()
        );
        financeServices.add(reportingService);
    }

    private void registerEventHandlers() {
        // Register order lifecycle event handlers
        context.registerEventHandler("order.received", this::handleOrderReceived);
        context.registerEventHandler("order.validated", this::handleOrderValidated);
        context.registerEventHandler("order.executed", this::handleOrderExecuted);
        context.registerEventHandler("order.settled", this::handleOrderSettled);
        context.registerEventHandler("order.cancelled", this::handleOrderCancelled);

        // Register risk event handlers
        context.registerEventHandler("risk.limit.breached", this::handleRiskLimitBreached);
        context.registerEventHandler("risk.position.updated", this::handleRiskPositionUpdated);
        context.registerEventHandler("risk.calculation.completed", this::handleRiskCalculationCompleted);

        // Register compliance event handlers
        context.registerEventHandler("compliance.check.required", this::handleComplianceCheckRequired);
        context.registerEventHandler("compliance.violation.detected", this::handleComplianceViolationDetected);
        context.registerEventHandler("compliance.report.generated", this::handleComplianceReportGenerated);

        // Register market data event handlers
        context.registerEventHandler("market.data.received", this::handleMarketDataReceived);
        context.registerEventHandler("market.price.updated", this::handleMarketPriceUpdated);

        // Register portfolio event handlers
        context.registerEventHandler("portfolio.updated", this::handlePortfolioUpdated);
        context.registerEventHandler("portfolio.rebalanced", this::handlePortfolioRebalanced);

        // Register ledger event handlers
        context.registerEventHandler("ledger.transaction.posted", this::handleLedgerTransactionPosted);
        context.registerEventHandler("ledger.position.updated", this::handleLedgerPositionUpdated);
    }

    private void setupFinancialPolicies() {
        // Setup SEBON compliance policies
        setupSebonCompliancePolicies();

        // Setup MIFID-II compliance policies
        setupMifidIICompliancePolicies();

        // Setup Dodd-Frank compliance policies
        setupDoddFrankCompliancePolicies();

        // Setup financial risk policies
        setupFinancialRiskPolicies();

        // Setup data retention policies
        setupFinancialDataRetentionPolicies();
    }

    private void initializeDualCalendarSupport() {
        // Initialize Gregorian calendar
        GregorianCalendar gregorianCalendar = new GregorianCalendar();

        // Initialize Nepali calendar
        NepaliCalendar nepaliCalendar = new NepaliCalendar();

        // Initialize dual calendar calculator
        DualCalendarCalculator dualCalendarCalculator = new DualCalendarCalculator(
            gregorianCalendar, nepaliCalendar);

        // Register calendars with kernel context
        context.registerCalendar("gregorian", gregorianCalendar);
        context.registerCalendar("nepali", nepaliCalendar);
        context.registerCalendar("dual", dualCalendarCalculator);

        logger.info("Dual calendar support initialized successfully");
    }

    // Event handler methods
    private void handleOrderReceived(KernelEvent event) {
        try {
            OrderReceivedEvent orderEvent = (OrderReceivedEvent) event.getPayload();
            orderManagementService.handleOrderReceived(orderEvent);

            // Trigger order validation workflow
            orderManagementService.validateOrder(orderEvent.getOrderId());

            // Log order for compliance
            complianceEngineService.logOrderEvent(orderEvent, "ORDER_RECEIVED");

        } catch (Exception e) {
            logger.error("Error handling order received event", e);
            throw new RuntimeException("Failed to handle order received event", e);
        }
    }

    private void handleOrderExecuted(KernelEvent event) {
        try {
            OrderExecutedEvent orderEvent = (OrderExecutedEvent) event.getPayload();
            executionManagementService.handleOrderExecuted(orderEvent);

            // Update portfolio
            portfolioManagementService.updatePortfolioFromExecution(orderEvent);

            // Update ledger
            ledgerService.postTradeExecution(orderEvent);

            // Update risk positions
            riskManagementService.updateRiskPositionsFromExecution(orderEvent);

            // Check for compliance violations
            complianceEngineService.checkExecutionCompliance(orderEvent);

            // Log for audit
            context.getDependency(KernelAuditService.class, "audit-service")
                .logOrderExecution(orderEvent);

        } catch (Exception e) {
            logger.error("Error handling order executed event", e);
            throw new RuntimeException("Failed to handle order executed event", e);
        }
    }

    private void handleRiskLimitBreached(KernelEvent event) {
        try {
            RiskLimitBreachedEvent riskEvent = (RiskLimitBreachedEvent) event.getPayload();
            riskManagementService.handleRiskLimitBreached(riskEvent);

            // Trigger automatic position reduction if configured
            if (financeConfiguration.isAutomaticPositionReductionEnabled()) {
                riskManagementService.reducePosition(riskEvent.getPortfolioId(), riskEvent.getRiskType());
            }

            // Notify compliance
            complianceEngineService.notifyRiskLimitBreached(riskEvent);

            // Send alerts
            sendRiskAlert(riskEvent);

        } catch (Exception e) {
            logger.error("Error handling risk limit breached event", e);
            throw new RuntimeException("Failed to handle risk limit breached event", e);
        }
    }

    private void handleComplianceViolationDetected(KernelEvent event) {
        try {
            ComplianceViolationDetectedEvent complianceEvent = (ComplianceViolationDetectedEvent) event.getPayload();
            complianceEngineService.handleComplianceViolation(complianceEvent);

            // Block further trading if serious violation
            if (complianceEvent.getSeverity() == ComplianceSeverity.HIGH) {
                orderManagementService.blockTrading(complianceEvent.getAccountId());
            }

            // Generate compliance report
            complianceEngineService.generateViolationReport(complianceEvent);

            // Notify regulators if required
            if (complianceEvent.isRegulatorNotificationRequired()) {
                complianceEngineService.notifyRegulators(complianceEvent);
            }

            // Log for audit
            context.getDependency(KernelAuditService.class, "audit-service")
                .logComplianceViolation(complianceEvent);

        } catch (Exception e) {
            logger.error("Error handling compliance violation detected event", e);
            throw new RuntimeException("Failed to handle compliance violation detected event", e);
        }
    }

    // Financial policy setup methods
    private void setupSebonCompliancePolicies() {
        // Register SEBON (Nepal) compliance policies
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new SebonTradingPolicy());
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new SebonReportingPolicy());
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new SebonCapitalRequirementsPolicy());
    }

    private void setupMifidIICompliancePolicies() {
        // Register MIFID-II compliance policies
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new MifidIITradingPolicy());
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new MifidIIReportingPolicy());
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new MifidIIInvestorProtectionPolicy());
    }

    private void setupDoddFrankCompliancePolicies() {
        // Register Dodd-Frank compliance policies
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new DoddFrankTradingPolicy());
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new DoddFrankReportingPolicy());
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new DoddFrankSwapDealerPolicy());
    }

    private void setupFinancialRiskPolicies() {
        // Register financial risk management policies
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new MarketRiskPolicy());
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new CreditRiskPolicy());
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new OperationalRiskPolicy());
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new LiquidityRiskPolicy());
    }

    private void setupFinancialDataRetentionPolicies() {
        // Register financial data retention policies
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new TradeDataRetentionPolicy()); // 10 years
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new MarketDataRetentionPolicy()); // 7 years
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new ComplianceDataRetentionPolicy()); // 10 years
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new AuditDataRetentionPolicy()); // 10 years
    }

    // Background task management
    private void startMarketDataFeeds() {
        // Start market data ingestion
        marketDataService.startMarketDataFeeds();

        // Start price feeds
        pricingService.startPriceFeeds();

        // Start market data processing
        marketDataService.startMarketDataProcessing();
    }

    private void startRiskMonitoring() {
        // Start real-time risk monitoring
        riskManagementService.startRealTimeRiskMonitoring();

        // Start risk calculation engines
        riskManagementService.startRiskCalculationEngines();

        // Start risk alerting
        riskManagementService.startRiskAlerting();
    }

    private void startComplianceMonitoring() {
        // Start real-time compliance monitoring
        complianceEngineService.startRealTimeComplianceMonitoring();

        // Start compliance reporting
        complianceEngineService.startComplianceReporting();

        // Start regulatory reporting
        complianceEngineService.startRegulatoryReporting();
    }

    private void startSurveillanceSystems() {
        // Start market surveillance
        surveillanceSystemService.startMarketSurveillance();

        // Start trade surveillance
        surveillanceSystemService.startTradeSurveillance();

        // Start pattern detection
        surveillanceSystemService.startPatternDetection();
    }

    private void stopMarketDataFeeds() {
        // Stop all market data feeds
        marketDataService.stopMarketDataFeeds();
        pricingService.stopPriceFeeds();
        marketDataService.stopMarketDataProcessing();
    }

    private void stopRiskMonitoring() {
        // Stop all risk monitoring
        riskManagementService.stopRealTimeRiskMonitoring();
        riskManagementService.stopRiskCalculationEngines();
        riskManagementService.stopRiskAlerting();
    }

    private void stopComplianceMonitoring() {
        // Stop all compliance monitoring
        complianceEngineService.stopRealTimeComplianceMonitoring();
        complianceEngineService.stopComplianceReporting();
        complianceEngineService.stopRegulatoryReporting();
    }

    private void stopSurveillanceSystems() {
        // Stop all surveillance systems
        surveillanceSystemService.stopMarketSurveillance();
        surveillanceSystemService.stopTradeSurveillance();
        surveillanceSystemService.stopPatternDetection();
    }

    // Service getters for other modules
    public OrderManagementService getOrderManagementService() { return orderManagementService; }
    public ExecutionManagementService getExecutionManagementService() { return executionManagementService; }
    public PortfolioManagementService getPortfolioManagementService() { return portfolioManagementService; }
    public MarketDataService getMarketDataService() { return marketDataService; }
    public PricingService getPricingService() { return pricingService; }
    public RiskManagementService getRiskManagementService() { return riskManagementService; }
    public ComplianceEngineService getComplianceEngineService() { return complianceEngineService; }
    public SurveillanceSystemService getSurveillanceSystemService() { return surveillanceSystemService; }
    public LedgerService getLedgerService() { return ledgerService; }
    public ReportingService getReportingService() { return reportingService; }
}
```

### 3.2 Detailed Finance-Specific Implementations

#### 3.2.1 Dual Calendar Support - Complete Implementation

**Dual Calendar Extension:**

```java
// Location: products/finance/kernel/src/main/java/com/ghatana/finance/kernel/calendar/
package com.ghatana.finance.kernel.calendar;

import com.ghatana.kernel.extension.KernelExtension;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.finance.calendar.GregorianCalendar;
import com.ghatana.finance.calendar.NepaliCalendar;
import com.ghatana.finance.calendar.DualCalendarCalculator;

/**
 * Finance-specific dual calendar extension
 * Provides Gregorian and Nepali calendar support for financial operations
 */
public class DualCalendarKernelExtension implements KernelExtension {

    private static final String EXTENSION_NAME = "dual-calendar";
    private static final String EXTENSION_VERSION = "1.0.0";

    private final GregorianCalendar gregorianCalendar;
    private final NepaliCalendar nepaliCalendar;
    private final DualCalendarCalculator dualCalendarCalculator;

    public DualCalendarKernelExtension() {
        this.gregorianCalendar = new GregorianCalendar();
        this.nepaliCalendar = new NepaliCalendar();
        this.dualCalendarCalculator = new DualCalendarCalculator(gregorianCalendar, nepaliCalendar);
    }

    @Override
    public String getExtensionName() {
        return EXTENSION_NAME;
    }

    @Override
    public String getExtensionVersion() {
        return EXTENSION_VERSION;
    }

    @Override
    public void extendKernel(KernelContext context) {
        // Register calendars with kernel context
        context.registerCalendar("gregorian", gregorianCalendar);
        context.registerCalendar("nepali", nepaliCalendar);
        context.registerCalendar("dual", dualCalendarCalculator);

        // Register calendar-specific services
        context.registerService("calendar.service", new CalendarService(dualCalendarCalculator));
        context.registerService("holiday.service", new HolidayService(dualCalendarCalculator));

        // Setup financial calendar policies
        setupFinancialCalendarPolicies(context);
    }

    private void setupFinancialCalendarPolicies(KernelContext context) {
        // Register trading calendar policies
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new TradingCalendarPolicy(dualCalendarCalculator));

        // Register settlement calendar policies
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new SettlementCalendarPolicy(dualCalendarCalculator));

        // Register reporting calendar policies
        context.getDependency(KernelPolicyRegistry.class, "policy-registry")
            .registerPolicy(new ReportingCalendarPolicy(dualCalendarCalculator));
    }
}
```

#### 3.2.2 Finance AI/ML Integration - Complete Implementation

**Fraud Detection Agent:**

```java
// Location: products/finance/kernel/src/main/java/com/ghatana/finance/kernel/ai/
package com.ghatana.finance.kernel.ai;

import com.ghatana.ai.platform.ModelRegistry;
import com.ghatana.ai.platform.InferenceService;
import com.ghatana.ai.platform.TypedAgent;
import com.ghatana.ai.platform.AgentEvent;
import com.ghatana.finance.model.Transaction;
import com.ghatana.finance.model.FraudDetectionResult;

/**
 * Finance-specific fraud detection agent
 * Uses AI/ML models to detect fraudulent transactions
 */
public class FraudDetectionAgent implements TypedAgent {

    private static final String AGENT_ID = "fraud-detection-agent";
    private static final String AGENT_VERSION = "1.0.0";

    private final ModelRegistry modelRegistry;
    private final InferenceService inferenceService;
    private final Logger logger;

    public FraudDetectionAgent(ModelRegistry modelRegistry, InferenceService inferenceService) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry);
        this.inferenceService = Objects.requireNonNull(inferenceService);
        this.logger = LoggerFactory.getLogger(FraudDetectionAgent.class);
    }

    @Override
    public String getAgentId() {
        return AGENT_ID;
    }

    @Override
    public String getAgentVersion() {
        return AGENT_VERSION;
    }

    @Override
    public void processEvent(AgentEvent event) {
        try {
            if (event.getType().equals("trade.executed")) {
                TradeEvent tradeEvent = (TradeEvent) event.getPayload();
                FraudDetectionResult result = detectFraud(tradeEvent);

                if (result.isSuspicious()) {
                    publishAlert(result);
                    blockTransactionIfRequired(tradeEvent, result);
                }

                logFraudDetection(tradeEvent, result);
            } else if (event.getType().equals("transaction.processed")) {
                TransactionEvent transactionEvent = (TransactionEvent) event.getPayload();
                FraudDetectionResult result = detectTransactionFraud(transactionEvent);

                if (result.isSuspicious()) {
                    publishAlert(result);
                    blockTransactionIfRequired(transactionEvent, result);
                }

                logFraudDetection(transactionEvent, result);
            }

        } catch (Exception e) {
            logger.error("Error processing fraud detection event: {}", event.getEventId(), e);
        }
    }

    private FraudDetectionResult detectFraud(TradeEvent trade) {
        try {
            // Get fraud detection model
            Model fraudModel = modelRegistry.getModel("fraud-detector-v2");

            // Prepare input features
            Map<String, Object> features = extractTradeFeatures(trade);

            // Run inference
            FraudDetectionResult result = inferenceService.predict(fraudModel, features);

            // Enhance result with trade context
            return result.withTradeContext(trade);

        } catch (Exception e) {
            logger.error("Error detecting fraud for trade: {}", trade.getTradeId(), e);
            return FraudDetectionResult.error("Fraud detection failed: " + e.getMessage());
        }
    }

    private FraudDetectionResult detectTransactionFraud(TransactionEvent transaction) {
        try {
            // Get transaction fraud model
            Model transactionFraudModel = modelRegistry.getModel("transaction-fraud-detector-v1");

            // Prepare input features
            Map<String, Object> features = extractTransactionFeatures(transaction);

            // Run inference
            FraudDetectionResult result = inferenceService.predict(transactionFraudModel, features);

            // Enhance result with transaction context
            return result.withTransactionContext(transaction);

        } catch (Exception e) {
            logger.error("Error detecting fraud for transaction: {}", transaction.getTransactionId(), e);
            return FraudDetectionResult.error("Transaction fraud detection failed: " + e.getMessage());
        }
    }

    private Map<String, Object> extractTradeFeatures(TradeEvent trade) {
        Map<String, Object> features = new HashMap<>();

        // Trade features
        features.put("trade_amount", trade.getAmount());
        features.put("trade_price", trade.getPrice());
        features.put("trade_quantity", trade.getQuantity());
        features.put("trade_timestamp", trade.getTimestamp().toEpochMilli());

        // Account features
        features.put("account_id", trade.getAccountId());
        features.put("account_age_days", getAccountAge(trade.getAccountId()));
        features.put("account_risk_score", getAccountRiskScore(trade.getAccountId()));

        // Instrument features
        features.put("instrument_id", trade.getInstrumentId());
        features.put("instrument_volatility", getInstrumentVolatility(trade.getInstrumentId()));
        features.put("instrument_liquidity", getInstrumentLiquidity(trade.getInstrumentId()));

        // Temporal features
        features.put("hour_of_day", trade.getTimestamp().getHour());
        features.put("day_of_week", trade.getTimestamp().getDayOfWeek().getValue());
        features.put("is_trading_hours", isTradingHours(trade.getTimestamp()));

        // Behavioral features
        features.put("recent_trade_count", getRecentTradeCount(trade.getAccountId(), Duration.ofHours(24)));
        features.put("recent_volume", getRecentVolume(trade.getAccountId(), Duration.ofHours(24)));
        features.put("price_deviation", getPriceDeviation(trade));

        return features;
    }

    private Map<String, Object> extractTransactionFeatures(TransactionEvent transaction) {
        Map<String, Object> features = new HashMap<>();

        // Transaction features
        features.put("transaction_amount", transaction.getAmount());
        features.put("transaction_timestamp", transaction.getTimestamp().toEpochMilli());
        features.put("transaction_type", transaction.getType());

        // Account features
        features.put("account_id", transaction.getAccountId());
        features.put("account_age_days", getAccountAge(transaction.getAccountId()));
        features.put("account_risk_score", getAccountRiskScore(transaction.getAccountId()));

        // Geographic features
        features.put("origin_country", transaction.getOriginCountry());
        features.put("destination_country", transaction.getDestinationCountry());
        features.put("is_cross_border", !transaction.getOriginCountry().equals(transaction.getDestinationCountry()));

        // Temporal features
        features.put("hour_of_day", transaction.getTimestamp().getHour());
        features.put("day_of_week", transaction.getTimestamp().getDayOfWeek().getValue());
        features.put("is_business_hours", isBusinessHours(transaction.getTimestamp()));

        // Behavioral features
        features.put("recent_transaction_count", getRecentTransactionCount(transaction.getAccountId(), Duration.ofHours(24)));
        features.put("recent_amount", getRecentAmount(transaction.getAccountId(), Duration.ofHours(24)));
        features.put("amount_deviation", getAmountDeviation(transaction));

        return features;
    }

    private void publishAlert(FraudDetectionResult result) {
        try {
            FraudAlertEvent alertEvent = FraudAlertEvent.builder()
                .alertId(UUID.randomUUID().toString())
                .fraudScore(result.getFraudScore())
                .riskLevel(result.getRiskLevel())
                .reasons(result.getReasons())
                .context(result.getContext())
                .timestamp(Instant.now())
                .build();

            // Publish to AEP for processing
            context.getDependency(AepPlatform.class, "aep-platform")
                .publish("fraud.alert.detected", alertEvent);

            logger.warn("Fraud alert published: {} - Score: {} - Risk: {}",
                alertEvent.getAlertId(), result.getFraudScore(), result.getRiskLevel());

        } catch (Exception e) {
            logger.error("Error publishing fraud alert", e);
        }
    }

    private void blockTransactionIfRequired(Object event, FraudDetectionResult result) {
        if (result.getRiskLevel() == RiskLevel.HIGH) {
            try {
                if (event instanceof TradeEvent) {
                    TradeEvent tradeEvent = (TradeEvent) event;
                    context.getDependency(OrderManagementService.class, "order-management")
                        .blockTrade(tradeEvent.getAccountId(), "High fraud risk detected");

                } else if (event instanceof TransactionEvent) {
                    TransactionEvent transactionEvent = (TransactionEvent) event;
                    context.getDependency(PaymentService.class, "payment-service")
                        .blockTransaction(transactionEvent.getTransactionId(), "High fraud risk detected");
                }

                logger.info("Transaction blocked due to high fraud risk: {}", result.getContext());

            } catch (Exception e) {
                logger.error("Error blocking transaction", e);
            }
        }
    }

    // Helper methods for feature extraction
    private long getAccountAge(String accountId) {
        // Implementation to get account age in days
        return 365; // Placeholder
    }

    private double getAccountRiskScore(String accountId) {
        // Implementation to get account risk score
        return 0.5; // Placeholder
    }

    private double getInstrumentVolatility(String instrumentId) {
        // Implementation to get instrument volatility
        return 0.2; // Placeholder
    }

    private double getInstrumentLiquidity(String instrumentId) {
        // Implementation to get instrument liquidity
        return 0.8; // Placeholder
    }

    private boolean isTradingHours(Instant timestamp) {
        // Implementation to check if timestamp is during trading hours
        return true; // Placeholder
    }

    private int getRecentTradeCount(String accountId, Duration period) {
        // Implementation to get recent trade count
        return 5; // Placeholder
    }

    private double getRecentVolume(String accountId, Duration period) {
        // Implementation to get recent trading volume
        return 10000.0; // Placeholder
    }

    private double getPriceDeviation(TradeEvent trade) {
        // Implementation to calculate price deviation from market
        return 0.05; // Placeholder
    }

    private boolean isBusinessHours(Instant timestamp) {
        // Implementation to check if timestamp is during business hours
        return true; // Placeholder
    }

    private int getRecentTransactionCount(String accountId, Duration period) {
        // Implementation to get recent transaction count
        return 3; // Placeholder
    }

    private double getRecentAmount(String accountId, Duration period) {
        // Implementation to get recent transaction amount
        return 5000.0; // Placeholder
    }

    private double getAmountDeviation(TransactionEvent transaction) {
        // Implementation to calculate amount deviation from normal
        return 0.1; // Placeholder
    }

    private void logFraudDetection(Object event, FraudDetectionResult result) {
        // Log fraud detection for audit and monitoring
        logger.info("Fraud detection completed - Event: {} - Score: {} - Risk: {}",
            event.getClass().getSimpleName(), result.getFraudScore(), result.getRiskLevel());
    }
}
```

### 3.3 Detailed Implementation Tasks (Weeks 5-6)

#### 3.3.1 Week 5 Task Breakdown

**Day 29-30: Finance Kernel Module**

```yaml
Day_29_Tasks:
  Task_1:
    Name: "Create finance kernel module"
    Description: "Implement comprehensive finance kernel module with trading services"
    Files:
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/FinanceKernelModule.java"
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/config/FinanceConfiguration.java"
    Dependencies: ["Phase 1 completion"]
    Estimated_Hours: 14
    Acceptance_Criteria:
      - Complete finance kernel module implementation
      - All trading services initialized
      - Event handlers registered
      - Financial policies configured

Day_30_Tasks:
  Task_2:
    Name: "Implement dual calendar support"
    Description: "Create Gregorian and Nepali calendar support for finance"
    Files:
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/calendar/DualCalendarKernelExtension.java"
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/calendar/GregorianCalendar.java"
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/calendar/NepaliCalendar.java"
    Dependencies: ["Task_1"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Dual calendar extension implemented
      - Gregorian calendar support
      - Nepali calendar support
      - Calendar conversion utilities

Day_31-32_Tasks:
  Task_3:
    Name: "Create finance data store manager"
    Description: "Implement finance-specific data store configuration with 10-year retention"
    Files:
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/data/FinanceDataStoreManager.java"
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/config/FinanceDataConfiguration.java"
    Dependencies: ["Task_1"]
    Estimated_Hours: 12
    Acceptance_Criteria:
      - All finance data stores configured
      - 10-year retention for trades
      - Immutable audit logs
      - Regulatory compliance

Day_32_Tasks:
  Task_4:
    Name: "Implement finance event processing"
    Description: "Create finance-specific event processing through AEP"
    Files:
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/events/FinanceEventProcessor.java"
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/events/FinanceEventHandler.java"
    Dependencies: ["Task_1", "Task_3"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Finance event streams created
      - Trading workflows configured
      - Risk event processing
      - Compliance event handling

Day_33-34_Tasks:
  Task_5:
    Name: "Create finance service implementations"
    Description: "Implement core finance services (trading, risk, compliance)"
    Files:
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/service/OrderManagementService.java"
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/service/RiskManagementService.java"
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/service/ComplianceEngineService.java"
    Dependencies: ["Task_1", "Task_3", "Task_4"]
    Estimated_Hours: 14
    Acceptance_Criteria:
      - Core finance services implemented
      - Data-Cloud integration complete
      - Event processing integrated
      - Service health monitoring

Day_34_Tasks:
  Task_6:
    Name: "Setup finance build configuration"
    Description: "Create Gradle build files and dependencies for finance kernel"
    Files:
      - "products/finance/kernel/build.gradle.kts"
      - "products/finance/kernel/settings.gradle.kts"
    Dependencies: ["Task_5"]
    Estimated_Hours: 4
    Acceptance_Criteria:
      - Finance kernel build configuration
      - Dependencies defined
      - Test configuration
      - Integration with kernel platform
```

#### 3.3.2 Week 6 Task Breakdown

**Day 35-36: Finance Advanced Features**

```yaml
Day_35_Tasks:
  Task_7:
    Name: "Implement regulatory compliance extensions"
    Description: "Create SEBON, MIFID-II, Dodd-Frank compliance extensions"
    Files:
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/compliance/SebonComplianceExtension.java"
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/compliance/MifidIIComplianceExtension.java"
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/compliance/DoddFrankComplianceExtension.java"
    Dependencies: ["Task_5"]
    Estimated_Hours: 12
    Acceptance_Criteria:
      - SEBON compliance implemented
      - MIFID-II compliance implemented
      - Dodd-Frank compliance implemented
      - Regulatory reporting

Day_36_Tasks:
  Task_8:
    Name: "Create finance AI/ML agents"
    Description: "Implement fraud detection and risk assessment agents"
    Files:
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/ai/FraudDetectionAgent.java"
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/ai/RiskAssessmentAgent.java"
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/ai/MarketAnalysisAgent.java"
    Dependencies: ["Task_5"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Fraud detection agent implemented
      - Risk assessment agent implemented
      - Market analysis agent implemented
      - AI platform integration

Day_37-38_Tasks:
  Task_9:
    Name: "Implement finance surveillance system"
    Description: "Create market and trade surveillance with pattern detection"
    Files:
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/surveillance/MarketSurveillanceService.java"
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/surveillance/TradeSurveillanceService.java"
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/surveillance/PatternDetectionEngine.java"
    Dependencies: ["Task_5", "Task_8"]
    Estimated_Hours: 12
    Acceptance_Criteria:
      - Market surveillance implemented
      - Trade surveillance implemented
      - Pattern detection engine
      - Alert generation system

Day_38_Tasks:
  Task_10:
    Name: "Create finance ledger and reporting"
    Description: "Implement ledger management and regulatory reporting"
    Files:
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/ledger/LedgerService.java"
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/reporting/ReportingService.java"
      - "products/finance/kernel/src/main/java/com/ghatana/finance/kernel/reporting/RegulatoryReportingService.java"
    Dependencies: ["Task_5", "Task_7"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Ledger service implemented
      - Reporting service implemented
      - Regulatory reporting
      - Compliance validation

Day_39-40_Tasks:
  Task_11:
    Name: "Implement finance testing framework"
    Description: "Create comprehensive testing framework for finance kernel"
    Files:
      - "products/finance/kernel/src/test/java/com/ghatana/finance/kernel/FinanceKernelIntegrationTest.java"
      - "products/finance/kernel/src/test/java/com/ghatana/finance/kernel/FinanceComplianceTest.java"
      - "products/finance/kernel/src/test/java/com/ghatana/finance/kernel/FinanceAITest.java"
    Dependencies: ["Task_10"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Finance integration tests created
      - Compliance tests implemented
      - AI/ML agent tests
      - Performance tests

Day_40_Tasks:
  Task_12:
    Name: "Finance integration validation"
    Description: "Validate end-to-end finance kernel integration"
    Files:
      - "products/finance/kernel/src/test/java/com/ghatana/finance/kernel/FinanceEndToEndTest.java"
      - "products/finance/kernel/validation/FinanceValidationReport.md"
    Dependencies: ["Task_11"]
    Estimated_Hours: 6
    Acceptance_Criteria:
      - End-to-end finance scenarios tested
      - Integration validation complete
      - Performance benchmarks
      - Regulatory compliance validation
```

---

## Phase 4: Cross-Product Integration (Weeks 7-8)

### 4.1 Detailed Cross-Product Communication Implementation

#### 4.1.1 Inter-Product Event Bus - Complete Implementation

**Cross-Product Event Bus:**

```java
// Location: platform/java/kernel/src/main/java/com/ghatana/kernel/communication/
package com.ghatana.kernel.communication;

import com.ghatana.aep.platform.AepPlatform;
import com.ghatana.datacloud.platform.DataCloudPlatform;
import com.ghatana.kernel.tenant.KernelTenantContext;
import com.ghatana.kernel.event.KernelEvent;
import com.ghatana.kernel.event.CrossProductEvent;
import com.ghatana.kernel.security.ProductBoundaryEnforcer;

import io.activej.promise.Promise;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.HashSet;

/**
 * Cross-product event bus implementation.
 * Enables secure communication between PHR and Finance modules.
 *
 * @doc.type class
 * @doc.purpose Inter-product event bus with boundary enforcement
 * @doc.layer core
 * @doc.pattern Service
 */
public class KernelInterProductBus {

    private final AepPlatform aepPlatform;
    private final DataCloudPlatform dataCloud;
    private final ProductBoundaryEnforcer boundaryEnforcer;
    private final Map<String, Set<String>> productSubscriptions;
    private final Map<String, EventTransformationRule> transformationRules;
    private final Logger logger;

    public KernelInterProductBus(AepPlatform aepPlatform,
                                DataCloudPlatform dataCloud,
                                ProductBoundaryEnforcer boundaryEnforcer) {
        this.aepPlatform = Objects.requireNonNull(aepPlatform);
        this.dataCloud = Objects.requireNonNull(dataCloud);
        this.boundaryEnforcer = Objects.requireNonNull(boundaryEnforcer);
        this.productSubscriptions = new ConcurrentHashMap<>();
        this.transformationRules = new ConcurrentHashMap<>();
        this.logger = LoggerFactory.getLogger(KernelInterProductBus.class);

        initializeDefaultSubscriptions();
        initializeTransformationRules();
    }

    /**
     * Publish cross-product event
     */
    public Promise<Void> publishCrossProductEvent(CrossProductEvent event) {
        try {
            logger.debug("Publishing cross-product event: {} from {} to {}",
                event.getEventId(), event.getSourceProduct(), event.getTargetProduct());

            if (!validateCrossProductAccess(event)) {
                return Promise.ofException(
                    new SecurityException("Cross-product access denied for event: " + event.getEventId()));
            }

            CrossProductEvent transformedEvent = transformEvent(event);

            AepEvent aepEvent = AepEvent.builder()
                .eventId(transformedEvent.getEventId())
                .eventType("cross-product." + transformedEvent.getType())
                .payload(transformedEvent)
                .sourceProduct(transformedEvent.getSourceProduct())
                .targetProduct(transformedEvent.getTargetProduct())
                .tenantId(transformedEvent.getTenantId())
                .timestamp(transformedEvent.getTimestamp())
                .metadata(transformedEvent.getMetadata())
                .build();

            return Promise.ofFuture(aepPlatform.publish(aepEvent))
                .whenResult(() -> {
                    logger.info("Cross-product event published successfully: {}", event.getEventId());
                    logCrossProductEvent(event);
                })
                .whenException(e -> logger.error("Failed to publish cross-product event: {}", event.getEventId(), e));

        } catch (Exception e) {
            logger.error("Error publishing cross-product event: {}", event.getEventId(), e);
            return Promise.ofException(e);
        }
    }

    /**
     * Subscribe to cross-product events
     */
    public void subscribeToCrossProductEvents(String productId, String eventType,
                                             CrossProductEventHandler handler) {
        try {
            logger.debug("Subscribing {} to cross-product events: {}", productId, eventType);

            // Register subscription
            productSubscriptions.computeIfAbsent(productId, k -> new HashSet<>()).add(eventType);

            // Create AEP subscription
            String aepEventType = "cross-product." + eventType;
            aepPlatform.subscribe(aepEventType, (aepEvent) -> {
                try {
                    CrossProductEvent crossProductEvent = (CrossProductEvent) aepEvent.getPayload();

                    // Validate access
                    if (crossProductEvent.getTargetProduct().equals(productId)) {
                        handler.handleCrossProductEvent(crossProductEvent);
                    }

                } catch (Exception e) {
                    logger.error("Error handling cross-product event: {}", aepEvent.getEventId(), e);
                }
            });

            logger.info("Successfully subscribed {} to cross-product events: {}", productId, eventType);

        } catch (Exception e) {
            logger.error("Error subscribing to cross-product events: {} - {}", productId, eventType, e);
            throw new RuntimeException("Failed to subscribe to cross-product events", e);
        }
    }

    /**
     * Share cross-product data
     */
    public Promise<Void> shareCrossProductData(String dataId, Object data,
                                                        CrossProductSharePolicy policy) {
        try {
            logger.debug("Sharing cross-product data: {} with policy: {}", dataId, policy.getPolicyLevel());

            if (!validateDataSharingPolicy(dataId, policy)) {
                return Promise.ofException(
                    new SecurityException("Data sharing policy validation failed for: " + dataId));
            }

            return Promise.ofFuture(dataCloud.createSharedDataStore(dataId, data, policy))
                .whenResult(() -> {
                    logger.info("Cross-product data shared successfully: {}", dataId);
                    logDataSharing(dataId, policy);
                })
                .whenException(e -> logger.error("Failed to share cross-product data: {}", dataId, e));

        } catch (Exception e) {
            logger.error("Error sharing cross-product data: {}", dataId, e);
            return Promise.ofException(e);
        }
    }

    /**
     * Get cross-product data
     */
    public Promise<Object> getCrossProductData(String dataId, String requestingProductId) {
        try {
            logger.debug("Getting cross-product data: {} for product: {}", dataId, requestingProductId);

            if (!boundaryEnforcer.canAccessData(requestingProductId, dataId)) {
                return Promise.ofException(
                    new SecurityException("Access denied for cross-product data: " + dataId));
            }

            return Promise.ofFuture(dataCloud.getSharedData(dataId))
                .map(data -> {
                    logger.debug("Cross-product data retrieved successfully: {}", dataId);
                    return data;
                })
                .whenException(e -> logger.error("Failed to get cross-product data: {}", dataId, e));

        } catch (Exception e) {
            logger.error("Error getting cross-product data: {}", dataId, e);
            return Promise.ofException(e);
        }
    }

    private boolean validateCrossProductAccess(CrossProductEvent event) {
        // Check source product access
        if (!boundaryEnforcer.canAccess(event.getSourceProduct(), event.getTargetProduct(), "publish")) {
            logger.warn("Source product {} cannot publish to target product {}",
                event.getSourceProduct(), event.getTargetProduct());
            return false;
        }

        // Check target product access
        if (!boundaryEnforcer.canAccess(event.getTargetProduct(), event.getSourceProduct(), "subscribe")) {
            logger.warn("Target product {} cannot subscribe from source product {}",
                event.getTargetProduct(), event.getSourceProduct());
            return false;
        }

        // Check event type permissions
        if (!boundaryEnforcer.canAccessEventType(event.getSourceProduct(), event.getTargetProduct(), event.getType())) {
            logger.warn("Event type {} not allowed from {} to {}",
                event.getType(), event.getSourceProduct(), event.getTargetProduct());
            return false;
        }

        return true;
    }

    private CrossProductEvent transformEvent(CrossProductEvent event) {
        // Check if transformation is needed
        String transformationKey = event.getSourceProduct() + "->" + event.getTargetProduct() + ":" + event.getType();
        EventTransformationRule rule = transformationRules.get(transformationKey);

        if (rule != null) {
            return rule.transform(event);
        }

        return event;
    }

    private boolean validateDataSharingPolicy(String dataId, CrossProductSharePolicy policy) {
        // Validate policy level
        if (policy.getPolicyLevel() == SharePolicyLevel.NONE) {
            logger.warn("Data sharing policy level is NONE for: {}", dataId);
            return false;
        }

        // Validate allowed products
        if (policy.getAllowedProducts().isEmpty() && policy.getPolicyLevel() != SharePolicyLevel.PUBLIC) {
            logger.warn("No allowed products specified for data: {}", dataId);
            return false;
        }

        // Validate retention policy
        if (policy.getRetentionPeriod().isNegative()) {
            logger.warn("Invalid retention period for data: {}", dataId);
            return false;
        }

        return true;
    }

    private void initializeDefaultSubscriptions() {
        // PHR to Finance subscriptions
        productSubscriptions.put("phr", Set.of("payment.request", "billing.event", "insurance.claim"));

        // Finance to PHR subscriptions
        productSubscriptions.put("finance", Set.of("payment.processed", "transaction.completed", "account.updated"));
    }

    private void initializeTransformationRules() {
        // PHR to Finance transformations
        transformationRules.put("phr->finance:payment.request", new PhrToFinancePaymentTransformer());
        transformationRules.put("phr->finance:billing.event", new PhrToFinanceBillingTransformer());

        // Finance to PHR transformations
        transformationRules.put("finance->phr:payment.processed", new FinanceToPhrPaymentTransformer());
        transformationRules.put("finance->phr:transaction.completed", new FinanceToPhrTransactionTransformer());
    }

    private void logCrossProductEvent(CrossProductEvent event) {
        // Log cross-product event for audit
        try {
            CrossProductAuditLog auditLog = CrossProductAuditLog.builder()
                .eventId(event.getEventId())
                .sourceProduct(event.getSourceProduct())
                .targetProduct(event.getTargetProduct())
                .eventType(event.getType())
                .timestamp(event.getTimestamp())
                .tenantId(event.getTenantId())
                .metadata(event.getMetadata())
                .build();

            dataCloud.storeAuditLog(auditLog);

        } catch (Exception e) {
            logger.error("Failed to log cross-product event: {}", event.getEventId(), e);
        }
    }

    private void logDataSharing(String dataId, CrossProductSharePolicy policy) {
        // Log data sharing for audit
        try {
            DataSharingAuditLog auditLog = DataSharingAuditLog.builder()
                .dataId(dataId)
                .policyLevel(policy.getPolicyLevel())
                .allowedProducts(policy.getAllowedProducts())
                .retentionPeriod(policy.getRetentionPeriod())
                .timestamp(Instant.now())
                .build();

            dataCloud.storeAuditLog(auditLog);

        } catch (Exception e) {
            logger.error("Failed to log data sharing: {}", dataId, e);
        }
    }

    /**
     * Get cross-product event statistics
     */
    public Promise<CrossProductEventStatistics> getEventStatistics() {
        return Promise.ofFuture(aepPlatform.getEventStatistics("cross-product.*"))
            .map(stats -> new CrossProductEventStatistics(
                stats.getTotalEvents(),
                stats.getEventsByType(),
                stats.getEventsBySource(),
                stats.getEventsByTarget(),
                stats.getAverageLatency()
            ));
    }

    /**
     * Get cross-product data sharing statistics
     */
    public Promise<CrossProductDataStatistics> getDataSharingStatistics() {
        return Promise.ofFuture(dataCloud.getSharedDataStatistics())
            .map(stats -> new CrossProductDataStatistics(
                stats.getTotalSharedData(),
                stats.getDataByProduct(),
                stats.getDataByPolicy(),
                stats.getAverageDataSize()
            ));
    }
}
```

#### 4.1.2 Product Boundary Enforcement - Complete Implementation

**Product Boundary Enforcer:**

```java
// Location: platform/java/kernel/src/main/java/com/ghatana/kernel/boundary/
package com.ghatana.kernel.boundary;

import com.ghatana.kernel.tenant.KernelTenantContext;
import com.ghatana.kernel.security.SecurityContext;
import com.ghatana.kernel.config.KernelConfigResolver;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Product boundary enforcement implementation
 * Enforces strict isolation between PHR and Finance products
 */
public class ProductBoundaryEnforcer {

    private final Map<String, ProductBoundary> productBoundaries;
    private final Map<String, Set<String>> allowedEventTypes;
    private final Map<String, Set<String>> allowedDataTypes;
    private final Map<String, Set<String>> allowedApiEndpoints;
    private final SecurityContext securityContext;
    private final KernelConfigResolver configResolver;
    private final Logger logger;

    public ProductBoundaryEnforcer(SecurityContext securityContext,
                                  KernelConfigResolver configResolver) {
        this.securityContext = Objects.requireNonNull(securityContext);
        this.configResolver = Objects.requireNonNull(configResolver);
        this.productBoundaries = new ConcurrentHashMap<>();
        this.allowedEventTypes = new ConcurrentHashMap<>();
        this.allowedDataTypes = new ConcurrentHashMap<>();
        this.allowedApiEndpoints = new ConcurrentHashMap<>();
        this.logger = LoggerFactory.getLogger(ProductBoundaryEnforcer.class);

        initializeProductBoundaries();
        initializeAllowedEventTypes();
        initializeAllowedDataTypes();
        initializeAllowedApiEndpoints();
    }

    /**
     * Check if product can access resource
     */
    public boolean canAccess(String productId, String resource, String action) {
        try {
            logger.debug("Checking access: {} can {} resource: {}", productId, action, resource);

            // Get product boundary
            ProductBoundary boundary = productBoundaries.get(productId);
            if (boundary == null) {
                logger.warn("Product boundary not found: {}", productId);
                return false;
            }

            // Check resource type
            ResourceType resourceType = determineResourceType(resource);

            // Check access based on resource type
            switch (resourceType) {
                case EVENT:
                    return canAccessEvent(productId, resource, action);
                case DATA:
                    return canAccessData(productId, resource, action);
                case API:
                    return canAccessApi(productId, resource, action);
                case SERVICE:
                    return canAccessService(productId, resource, action);
                default:
                    logger.warn("Unknown resource type: {}", resourceType);
                    return false;
            }

        } catch (Exception e) {
            logger.error("Error checking access: {} can {} resource: {}", productId, action, resource, e);
            return false;
        }
    }

    /**
     * Check if product can access event type
     */
    public boolean canAccessEventType(String sourceProductId, String targetProductId, String eventType) {
        try {
            logger.debug("Checking event type access: {} -> {} : {}", sourceProductId, targetProductId, eventType);

            // Get allowed event types for target product
            Set<String> allowedTypes = allowedEventTypes.get(targetProductId);
            if (allowedTypes == null) {
                logger.warn("No allowed event types configured for product: {}", targetProductId);
                return false;
            }

            // Check if event type is allowed
            boolean isAllowed = allowedTypes.contains(eventType) || allowedTypes.contains("*");

            if (!isAllowed) {
                logger.warn("Event type {} not allowed for product {} from {}",
                    eventType, targetProductId, sourceProductId);
            }

            return isAllowed;

        } catch (Exception e) {
            logger.error("Error checking event type access: {} -> {} : {}",
                sourceProductId, targetProductId, eventType, e);
            return false;
        }
    }

    /**
     * Check if product can access data
     */
    public boolean canAccessData(String productId, String dataId) {
        try {
            logger.debug("Checking data access: {} can access data: {}", productId, dataId);

            // Parse data ID to get data type and source
            DataIdentifier dataIdentifier = parseDataId(dataId);

            // Check if data type is allowed
            Set<String> allowedTypes = allowedDataTypes.get(productId);
            if (allowedTypes == null) {
                logger.warn("No allowed data types configured for product: {}", productId);
                return false;
            }

            boolean isTypeAllowed = allowedTypes.contains(dataIdentifier.getDataType()) ||
                                  allowedTypes.contains("*");

            if (!isTypeAllowed) {
                logger.warn("Data type {} not allowed for product: {}",
                    dataIdentifier.getDataType(), productId);
                return false;
            }

            // Check source product access
            if (dataIdentifier.getSourceProduct() != null &&
                !dataIdentifier.getSourceProduct().equals(productId)) {
                return canAccess(productId, dataIdentifier.getSourceProduct(), "read");
            }

            return true;

        } catch (Exception e) {
            logger.error("Error checking data access: {} can access data: {}", productId, dataId, e);
            return false;
        }
    }

    private boolean canAccessEvent(String productId, String resource, String action) {
        // Parse event resource
        EventResource eventResource = parseEventResource(resource);

        // Check if event type is allowed
        return canAccessEventType(eventResource.getSourceProduct(), productId, eventResource.getEventType());
    }

    private boolean canAccessData(String productId, String resource, String action) {
        // Check data access
        return canAccessData(productId, resource);
    }

    private boolean canAccessApi(String productId, String resource, String action) {
        // Parse API endpoint
        ApiEndpoint apiEndpoint = parseApiEndpoint(resource);

        // Check if API endpoint is allowed
        Set<String> allowedEndpoints = allowedApiEndpoints.get(productId);
        if (allowedEndpoints == null) {
            logger.warn("No allowed API endpoints configured for product: {}", productId);
            return false;
        }

        boolean isEndpointAllowed = allowedEndpoints.contains(apiEndpoint.getEndpointPath()) ||
                                   allowedEndpoints.contains(apiEndpoint.getEndpointPath().substring(0,
                                       apiEndpoint.getEndpointPath().lastIndexOf('/')) + "/*") ||
                                   allowedEndpoints.contains("*");

        if (!isEndpointAllowed) {
            logger.warn("API endpoint {} not allowed for product: {}",
                apiEndpoint.getEndpointPath(), productId);
            return false;
        }

        // Check HTTP method
        return apiEndpoint.getAllowedMethods().contains(action.toUpperCase());
    }

    private boolean canAccessService(String productId, String resource, String action) {
        // Parse service resource
        ServiceResource serviceResource = parseServiceResource(resource);

        // Get product boundary
        ProductBoundary boundary = productBoundaries.get(productId);
        if (boundary == null) {
            return false;
        }

        // Check service access
        return boundary.getAllowedServices().contains(serviceResource.getServiceName()) &&
               boundary.getAllowedServiceActions().contains(action);
    }

    private ResourceType determineResourceType(String resource) {
        if (resource.startsWith("event://")) {
            return ResourceType.EVENT;
        } else if (resource.startsWith("data://")) {
            return ResourceType.DATA;
        } else if (resource.startsWith("api://")) {
            return ResourceType.API;
        } else if (resource.startsWith("service://")) {
            return ResourceType.SERVICE;
        } else {
            return ResourceType.UNKNOWN;
        }
    }

    private void initializeProductBoundaries() {
        // PHR boundary
        ProductBoundary phrBoundary = ProductBoundary.builder()
            .productId("phr")
            .allowedServices(Set.of("patient.service", "consent.service", "document.service"))
            .allowedServiceActions(Set.of("read", "write", "create", "update"))
            .isolationLevel(IsolationLevel.HIGH)
            .complianceRequirements(Set.of("HIPAA", "GDPR", "Nepal Privacy Act 2075"))
            .build();

        // Finance boundary
        ProductBoundary financeBoundary = ProductBoundary.builder()
            .productId("finance")
            .allowedServices(Set.of("trading.service", "risk.service", "compliance.service"))
            .allowedServiceActions(Set.of("read", "write", "execute", "monitor"))
            .isolationLevel(IsolationLevel.HIGH)
            .complianceRequirements(Set.of("SOX", "MIFID-II", "Dodd-Frank", "SEBON"))
            .build();

        productBoundaries.put("phr", phrBoundary);
        productBoundaries.put("finance", financeBoundary);
    }

    private void initializeAllowedEventTypes() {
        // PHR allowed event types
        allowedEventTypes.put("phr", Set.of(
            "patient.registered",
            "patient.updated",
            "consent.granted",
            "consent.revoked",
            "document.uploaded",
            "appointment.scheduled",
            "billing.generated",
            "payment.processed", // From finance
            "transaction.completed" // From finance
        ));

        // Finance allowed event types
        allowedEventTypes.put("finance", Set.of(
            "trade.executed",
            "risk.limit.breached",
            "compliance.violation",
            "market.data.received",
            "payment.request", // From PHR
            "billing.event", // From PHR
            "insurance.claim" // From PHR
        ));
    }

    private void initializeAllowedDataTypes() {
        // PHR allowed data types
        allowedDataTypes.put("phr", Set.of(
            "patient.data",
            "consent.data",
            "document.data",
            "appointment.data",
            "billing.data",
            "payment.data", // From finance
            "transaction.data" // From finance
        ));

        // Finance allowed data types
        allowedDataTypes.put("finance", Set.of(
            "trade.data",
            "risk.data",
            "compliance.data",
            "market.data",
            "payment.request.data", // From PHR
            "billing.data", // From PHR
            "insurance.data" // From PHR
        ));
    }

    private void initializeAllowedApiEndpoints() {
        // PHR allowed API endpoints
        allowedApiEndpoints.put("phr", Set.of(
            "/api/patients/*",
            "/api/consents/*",
            "/api/documents/*",
            "/api/appointments/*",
            "/api/billing/*",
            "/api/payments/status/*", // From finance
            "/api/transactions/status/*" // From finance
        ));

        // Finance allowed API endpoints
        allowedApiEndpoints.put("finance", Set.of(
            "/api/trades/*",
            "/api/portfolios/*",
            "/api/risk/*",
            "/api/compliance/*",
            "/api/payments/request/*", // From PHR
            "/api/billing/events/*", // From PHR
            "/api/insurance/claims/*" // From PHR
        ));
    }

    // Helper classes for parsing resources
    private EventResource parseEventResource(String resource) {
        // Implementation to parse event resource
        return new EventResource("phr", "patient.registered");
    }

    private DataIdentifier parseDataId(String dataId) {
        // Implementation to parse data ID
        return new DataIdentifier("patient.data", "phr");
    }

    private ApiEndpoint parseApiEndpoint(String resource) {
        // Implementation to parse API endpoint
        return new ApiEndpoint("/api/patients/*", Set.of("GET", "POST", "PUT"));
    }

    private ServiceResource parseServiceResource(String resource) {
        // Implementation to parse service resource
        return new ServiceResource("patient.service");
    }

    // Resource type enum
    public enum ResourceType {
        EVENT, DATA, API, SERVICE, UNKNOWN
    }

    // Helper classes
    private static class EventResource {
        private final String sourceProduct;
        private final String eventType;

        public EventResource(String sourceProduct, String eventType) {
            this.sourceProduct = sourceProduct;
            this.eventType = eventType;
        }

        public String getSourceProduct() { return sourceProduct; }
        public String getEventType() { return eventType; }
    }

    private static class DataIdentifier {
        private final String dataType;
        private final String sourceProduct;

        public DataIdentifier(String dataType, String sourceProduct) {
            this.dataType = dataType;
            this.sourceProduct = sourceProduct;
        }

        public String getDataType() { return dataType; }
        public String getSourceProduct() { return sourceProduct; }
    }

    private static class ApiEndpoint {
        private final String endpointPath;
        private final Set<String> allowedMethods;

        public ApiEndpoint(String endpointPath, Set<String> allowedMethods) {
            this.endpointPath = endpointPath;
            this.allowedMethods = allowedMethods;
        }

        public String getEndpointPath() { return endpointPath; }
        public Set<String> getAllowedMethods() { return allowedMethods; }
    }

    private static class ServiceResource {
        private final String serviceName;

        public ServiceResource(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getServiceName() { return serviceName; }
    }
}
```

### 4.2 Detailed Implementation Tasks (Weeks 7-8)

#### 4.2.1 Week 7 Task Breakdown

**Day 43-44: Cross-Product Communication**

```yaml
Day_43_Tasks:
  Task_1:
    Name: "Create inter-product event bus"
    Description: "Implement secure cross-product event communication"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/communication/KernelInterProductBus.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/communication/CrossProductEvent.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/communication/CrossProductEventHandler.java"
    Dependencies: ["Phase 1, 2, 3 completion"]
    Estimated_Hours: 12
    Acceptance_Criteria:
      - Cross-product event bus implemented
      - Event validation and routing
      - Security boundary enforcement
      - Event transformation support

Day_44_Tasks:
  Task_2:
    Name: "Implement product boundary enforcement"
    Description: "Create strict product isolation and access control"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/boundary/ProductBoundaryEnforcer.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/boundary/ProductBoundary.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/boundary/AccessPolicy.java"
    Dependencies: ["Task_1"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Product boundary enforcement implemented
      - Resource access validation
      - Event type restrictions
      - API endpoint restrictions

Day_45-46_Tasks:
  Task_3:
    Name: "Create unified configuration management"
    Description: "Implement cross-product configuration with tenant isolation"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/config/CrossProductConfigResolver.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/config/UnifiedConfigurationManager.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/config/ConfigurationMerger.java"
    Dependencies: ["Task_1", "Task_2"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Cross-product config resolver implemented
      - Configuration hierarchy support
      - Tenant-scoped configurations
      - Hot-reload capability

Day_46_Tasks:
  Task_4:
    Name: "Implement cross-product data sharing"
    Description: "Create secure data sharing between PHR and Finance"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/communication/CrossProductDataSharing.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/communication/DataSharingPolicy.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/communication/DataAccessValidator.java"
    Dependencies: ["Task_1", "Task_2"]
    Estimated_Hours: 8
    Acceptance_Criteria:
      - Cross-product data sharing implemented
      - Data access validation
      - Sharing policy enforcement
      - Data lineage tracking

Day_47-48_Tasks:
  Task_5:
    Name: "Create PHR-Finance integration workflows"
    Description: "Implement healthcare payment and billing workflows"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/workflow/PhrFinanceWorkflowEngine.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/workflow/HealthcarePaymentWorkflow.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/workflow/BillingReconciliationWorkflow.java"
    Dependencies: ["Task_1", "Task_4"]
    Estimated_Hours: 12
    Acceptance_Criteria:
      - PHR-Finance workflows implemented
      - Healthcare payment processing
      - Billing reconciliation
      - Error handling and retry

Day_48_Tasks:
  Task_6:
    Name: "Setup cross-product build configuration"
    Description: "Create build configuration for cross-product integration"
    Files:
      - "platform/java/kernel/build.gradle.kts"
      - "platform/java/kernel/settings.gradle.kts"
      - "platform/java/kernel/src/test/resources/cross-product-test-config.yml"
    Dependencies: ["Task_5"]
    Estimated_Hours: 4
    Acceptance_Criteria:
      - Cross-product build configuration
      - Integration test setup
      - Test data fixtures
      - Build pipeline integration
```

#### 4.2.2 Week 8 Task Breakdown

**Day 49-50: Advanced Cross-Product Features**

```yaml
Day_49_Tasks:
  Task_7:
    Name: "Implement unified audit service"
    Description: "Create comprehensive audit service for cross-product actions"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/audit/CrossProductAuditService.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/audit/UnifiedAuditLogger.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/audit/AuditReportGenerator.java"
    Dependencies: ["Task_1", "Task_2"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Unified audit service implemented
      - Cross-product audit logging
      - Compliance reporting
      - Long-term retention

Day_50_Tasks:
  Task_8:
    Name: "Create cross-product monitoring"
    Description: "Implement monitoring and alerting for cross-product interactions"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/monitoring/CrossProductMonitor.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/monitoring/InteractionMetrics.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/monitoring/AlertManager.java"
    Dependencies: ["Task_1", "Task_7"]
    Estimated_Hours: 8
    Acceptance_Criteria:
      - Cross-product monitoring implemented
      - Interaction metrics collection
      - Alert generation
      - Performance monitoring

Day_51-52_Tasks:
  Task_9:
    Name: "Implement cross-product testing"
    Description: "Create comprehensive testing for PHR-Finance integration"
    Files:
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/integration/PhrFinanceIntegrationTest.java"
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/integration/CrossProductEventTest.java"
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/integration/DataSharingTest.java"
    Dependencies: ["Task_6", "Task_8"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Cross-product integration tests
      - Event communication tests
      - Data sharing tests
      - Boundary enforcement tests

Day_52_Tasks:
  Task_10:
    Name: "Create cross-product compliance validation"
    Description: "Implement compliance validation for cross-product operations"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/compliance/CrossProductComplianceValidator.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/compliance/RegulatoryComplianceChecker.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/compliance/ComplianceReportGenerator.java"
    Dependencies: ["Task_7", "Task_9"]
    Estimated_Hours: 8
    Acceptance_Criteria:
      - Cross-product compliance validation
      - Regulatory compliance checking
      - Compliance reporting
      - Violation detection

Day_53-54_Tasks:
  Task_11:
    Name: "Implement cross-product documentation"
    Description: "Create comprehensive documentation for cross-product integration"
    Files:
      - "docs/CROSS_PRODUCT_INTEGRATION_GUIDE.md"
      - "docs/PHR_FINANCE_INTEGRATION_EXAMPLES.md"
      - "docs/CROSS_PRODUCT_API_REFERENCE.md"
    Dependencies: ["Task_10"]
    Estimated_Hours: 6
    Acceptance_Criteria:
      - Cross-product integration guide
      - PHR-Finance examples
      - API reference documentation
      - Troubleshooting guide

Day_54_Tasks:
  Task_12:
    Name: "Cross-product integration validation"
    Description: "Validate end-to-end cross-product integration"
    Files:
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/integration/CrossProductEndToEndTest.java"
      - "platform/java/kernel/validation/CrossProductValidationReport.md"
    Dependencies: ["Task_11"]
    Estimated_Hours: 6
    Acceptance_Criteria:
      - End-to-end cross-product scenarios tested
      - Integration validation complete
      - Performance benchmarks
      - Compliance validation
```

---

## Phase 5: Advanced Kernel Features (Weeks 9-10)

### 5.1 Detailed Runtime Plugin Loading Implementation

#### 5.1.1 Plugin Runtime Manager - Complete Implementation

**Plugin Runtime Manager:**

```java
// Location: platform/java/kernel/src/main/java/com/ghatana/kernel/runtime/
package com.ghatana.kernel.runtime;

import com.ghatana.kernel.registry.KernelRegistry;
import com.ghatana.kernel.plugin.KernelPlugin;
import com.ghatana.kernel.plugin.PluginPackage;
import com.ghatana.kernel.security.PluginSecurityManager;
import com.ghatana.kernel.runtime.PluginClassLoaderManager;
import com.ghatana.kernel.metrics.PluginMetricsCollector;

import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Runtime plugin manager implementation.
 * Provides secure plugin loading, unloading, and lifecycle management.
 *
 * @doc.type class
 * @doc.purpose Runtime plugin loading, unloading, and hot-reload with security isolation
 * @doc.layer core
 * @doc.pattern Service
 */
public class KernelPluginRuntimeManager {

    private final KernelRegistry registry;
    private final PluginClassLoaderManager classLoaderManager;
    private final PluginSecurityManager securityManager;
    private final PluginMetricsCollector metricsCollector;
    private final Map<String, LoadedPlugin> loadedPlugins;
    private final ReentrantReadWriteLock pluginLock;
    private final Logger logger;

    public KernelPluginRuntimeManager(KernelRegistry registry,
                                     PluginClassLoaderManager classLoaderManager,
                                     PluginSecurityManager securityManager,
                                     PluginMetricsCollector metricsCollector) {
        this.registry = Objects.requireNonNull(registry);
        this.classLoaderManager = Objects.requireNonNull(classLoaderManager);
        this.securityManager = Objects.requireNonNull(securityManager);
        this.metricsCollector = Objects.requireNonNull(metricsCollector);
        this.loadedPlugins = new ConcurrentHashMap<>();
        this.pluginLock = new ReentrantReadWriteLock();
        this.logger = LoggerFactory.getLogger(KernelPluginRuntimeManager.class);
    }

    /**
     * Load plugin with security validation and dependency resolution
     */
    public Promise<Void> loadPlugin(PluginPackage pluginPackage) {
        return Promise.ofBlocking(executor, () -> {
            pluginLock.writeLock().lock();
            try {
                logger.info("Loading plugin: {}", pluginPackage.getPluginId());

                securityManager.validatePlugin(pluginPackage);

                if (loadedPlugins.containsKey(pluginPackage.getPluginId())) {
                    throw new PluginAlreadyLoadedException(
                        "Plugin already loaded: " + pluginPackage.getPluginId());
                }

                List<KernelPlugin> dependencies = resolveDependencies(pluginPackage);
                ClassLoader pluginClassLoader = classLoaderManager.createClassLoader(pluginPackage);
                KernelPlugin plugin = instantiatePlugin(pluginPackage, pluginClassLoader);
                registry.registerPlugin(plugin);

                LoadedPlugin loadedPlugin = new LoadedPlugin(
                    plugin, pluginClassLoader, dependencies, System.currentTimeMillis());
                loadedPlugins.put(pluginPackage.getPluginId(), loadedPlugin);

                plugin.install();
                plugin.start();

                metricsCollector.recordPluginLoaded(pluginPackage.getPluginId());
                logger.info("Plugin loaded successfully: {}", pluginPackage.getPluginId());
                return null;

            } catch (Exception e) {
                logger.error("Failed to load plugin: {}", pluginPackage.getPluginId(), e);
                metricsCollector.recordPluginLoadError(pluginPackage.getPluginId(), e);
                throw new PluginLoadException("Failed to load plugin: " + pluginPackage.getPluginId(), e);
            } finally {
                pluginLock.writeLock().unlock();
            }
        });
    }

    /**
     * Unload plugin with cleanup and resource release
     */
    public Promise<Void> unloadPlugin(String pluginId) {
        return Promise.ofBlocking(executor, () -> {
            pluginLock.writeLock().lock();
            try {
                logger.info("Unloading plugin: {}", pluginId);

                LoadedPlugin loadedPlugin = loadedPlugins.get(pluginId);
                if (loadedPlugin == null) {
                    throw new PluginNotLoadedException("Plugin not loaded: " + pluginId);
                }

                KernelPlugin plugin = loadedPlugin.getPlugin();
                checkDependentPlugins(pluginId);

                plugin.stop();
                plugin.uninstall();
                registry.unregisterPlugin(pluginId);
                classLoaderManager.destroyClassLoader(loadedPlugin.getClassLoader());
                loadedPlugins.remove(pluginId);

                metricsCollector.recordPluginUnloaded(pluginId);
                logger.info("Plugin unloaded successfully: {}", pluginId);
                return null;

            } catch (Exception e) {
                logger.error("Failed to unload plugin: {}", pluginId, e);
                metricsCollector.recordPluginUnloadError(pluginId, e);
                throw new PluginUnloadException("Failed to unload plugin: " + pluginId, e);
            } finally {
                pluginLock.writeLock().unlock();
            }
        });
    }

    /**
     * Reload plugin with zero-downtime if possible
     */
    public Promise<Void> reloadPlugin(String pluginId) {
        return Promise.ofBlocking(executor, () -> {
            pluginLock.writeLock().lock();
            try {
                logger.info("Reloading plugin: {}", pluginId);

                LoadedPlugin loadedPlugin = loadedPlugins.get(pluginId);
                if (loadedPlugin == null) {
                    throw new PluginNotLoadedException("Plugin not loaded: " + pluginId);
                }

                KernelPlugin plugin = loadedPlugin.getPlugin();

                if (!plugin.supportsHotReload()) {
                    // Full reload via chained Promises — returned to caller
                    PluginPackage pluginPackage = loadedPlugin.getPluginPackage();
                    // Note: blocking here; full non-blocking version would
                    // return the composed Promise directly outside the lock.
                    unloadPlugin(pluginId).whenResult(() -> loadPlugin(pluginPackage));
                    return null;
                }

                plugin.reload();
                loadedPlugin.setLoadTime(System.currentTimeMillis());
                metricsCollector.recordPluginReloaded(pluginId);
                logger.info("Plugin reloaded successfully: {}", pluginId);
                return null;

            } catch (Exception e) {
                logger.error("Failed to reload plugin: {}", pluginId, e);
                metricsCollector.recordPluginReloadError(pluginId, e);
                throw new PluginReloadException("Failed to reload plugin: " + pluginId, e);
            } finally {
                pluginLock.writeLock().unlock();
            }
        });
    }

    /**
     * Get loaded plugin information
     */
    public Optional<LoadedPlugin> getLoadedPlugin(String pluginId) {
        pluginLock.readLock().lock();
        try {
            return Optional.ofNullable(loadedPlugins.get(pluginId));
        } finally {
            pluginLock.readLock().unlock();
        }
    }

    /**
     * Get all loaded plugins
     */
    public Map<String, LoadedPlugin> getAllLoadedPlugins() {
        pluginLock.readLock().lock();
        try {
            return new HashMap<>(loadedPlugins);
        } finally {
            pluginLock.readLock().unlock();
        }
    }

    /**
     * Check if plugin is loaded
     */
    public boolean isPluginLoaded(String pluginId) {
        pluginLock.readLock().lock();
        try {
            return loadedPlugins.containsKey(pluginId);
        } finally {
            pluginLock.readLock().unlock();
        }
    }

    /**
     * Get plugin health status
     */
    public Promise<PluginHealthStatus> getPluginHealthStatus(String pluginId) {
        return Promise.ofBlocking(executor, () -> {
            pluginLock.readLock().lock();
            try {
                LoadedPlugin loadedPlugin = loadedPlugins.get(pluginId);
                if (loadedPlugin == null) {
                    return PluginHealthStatus.NOT_LOADED;
                }

                KernelPlugin plugin = loadedPlugin.getPlugin();
                return PluginHealthStatus.fromKernelHealthStatus(plugin.getHealthStatus());

            } finally {
                pluginLock.readLock().unlock();
            }
        });
    }

    /**
     * Get plugin metrics
     */
    public PluginMetrics getPluginMetrics(String pluginId) {
        pluginLock.readLock().lock();
        try {
            LoadedPlugin loadedPlugin = loadedPlugins.get(pluginId);
            if (loadedPlugin == null) {
                return PluginMetrics.NOT_LOADED;
            }

            return metricsCollector.getPluginMetrics(pluginId);

        } finally {
            pluginLock.readLock().unlock();
        }
    }

    private List<KernelPlugin> resolveDependencies(PluginPackage pluginPackage) {
        List<KernelPlugin> dependencies = new ArrayList<>();

        for (String dependencyId : pluginPackage.getDependencies()) {
            LoadedPlugin loadedDependency = loadedPlugins.get(dependencyId);
            if (loadedDependency == null) {
                throw new PluginDependencyException("Dependency not loaded: " + dependencyId);
            }

            dependencies.add(loadedDependency.getPlugin());
        }

        return dependencies;
    }

    private KernelPlugin instantiatePlugin(PluginPackage pluginPackage, ClassLoader classLoader) {
        try {
            // Load plugin class
            Class<?> pluginClass = classLoader.loadClass(pluginPackage.getMainClassName());

            // Verify it's a KernelPlugin
            if (!KernelPlugin.class.isAssignableFrom(pluginClass)) {
                throw new PluginInstantiationException(
                    "Plugin class must implement KernelPlugin: " + pluginClass.getName());
            }

            // Create instance
            Constructor<?> constructor = pluginClass.getConstructor();
            KernelPlugin plugin = (KernelPlugin) constructor.newInstance();

            return plugin;

        } catch (Exception e) {
            throw new PluginInstantiationException("Failed to instantiate plugin: " + pluginPackage.getPluginId(), e);
        }
    }

    private void checkDependentPlugins(String pluginId) {
        for (LoadedPlugin loadedPlugin : loadedPlugins.values()) {
            if (loadedPlugin.getDependencies().contains(pluginId)) {
                throw new PluginDependencyException(
                    "Cannot unload plugin " + pluginId + " - plugin " + loadedPlugin.getPluginId() + " depends on it");
            }
        }
    }

    /**
     * Get runtime statistics
     */
    public PluginRuntimeStatistics getRuntimeStatistics() {
        pluginLock.readLock().lock();
        try {
            return new PluginRuntimeStatistics(
                loadedPlugins.size(),
                metricsCollector.getTotalLoadCount(),
                metricsCollector.getTotalUnloadCount(),
                metricsCollector.getTotalReloadCount(),
                metricsCollector.getTotalErrorCount(),
                metricsCollector.getAverageLoadTime(),
                metricsCollector.getAverageMemoryUsage()
            );
        } finally {
            pluginLock.readLock().unlock();
        }
    }

    /**
     * Cleanup all plugins
     */
    public Promise<Void> cleanup() {
        logger.info("Cleaning up all plugins");

        return Promises.all(
            loadedPlugins.keySet().stream()
                .map(this::unloadPlugin)
        ).whenResult(() -> {
            logger.info("All plugins cleaned up successfully");
        });
    }

    /**
     * Loaded plugin container
     */
    public static class LoadedPlugin {
        private final KernelPlugin plugin;
        private final ClassLoader classLoader;
        private final List<String> dependencies;
        private volatile long loadTime;

        public LoadedPlugin(KernelPlugin plugin, ClassLoader classLoader,
                          List<KernelPlugin> dependencies, long loadTime) {
            this.plugin = plugin;
            this.classLoader = classLoader;
            this.dependencies = dependencies.stream()
                .map(KernelPlugin::getPluginId)
                .toList();
            this.loadTime = loadTime;
        }

        public KernelPlugin getPlugin() { return plugin; }
        public ClassLoader getClassLoader() { return classLoader; }
        public List<String> getDependencies() { return dependencies; }
        public long getLoadTime() { return loadTime; }
        public void setLoadTime(long loadTime) { this.loadTime = loadTime; }

        public PluginPackage getPluginPackage() {
            return plugin.getManifest().getPluginPackage();
        }
    }
}
```

### 5.2 Detailed Implementation Tasks (Weeks 9-10)

#### 5.2.1 Week 9 Task Breakdown

**Day 57-58: Runtime Plugin System**

```yaml
Day_57_Tasks:
  Task_1:
    Name: "Create plugin runtime manager"
    Description: "Implement secure plugin loading and lifecycle management"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/runtime/KernelPluginRuntimeManager.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/runtime/LoadedPlugin.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/runtime/PluginClassLoaderManager.java"
    Dependencies: ["Phase 1, 2, 3, 4 completion"]
    Estimated_Hours: 14
    Acceptance_Criteria:
      - Plugin runtime manager implemented
      - Secure plugin loading
      - Dependency resolution
      - Lifecycle management

Day_58_Tasks:
  Task_2:
    Name: "Implement plugin security manager"
    Description: "Create plugin validation and sandboxing"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/security/PluginSecurityManager.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/security/PluginValidator.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/security/PluginSandbox.java"
    Dependencies: ["Task_1"]
    Estimated_Hours: 12
    Acceptance_Criteria:
      - Plugin security validation
      - Signature verification
      - Resource isolation
      - Permission enforcement

Day_59-60_Tasks:
  Task_3:
    Name: "Create plugin class loader isolation"
    Description: "Implement class loader isolation for plugins"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/runtime/IsolatedPluginClassLoader.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/runtime/PluginDependencyResolver.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/runtime/PluginResourceHandler.java"
    Dependencies: ["Task_1", "Task_2"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Class loader isolation implemented
      - Dependency resolution
      - Resource management
      - Memory isolation

Day_60_Tasks:
  Task_4:
    Name: "Implement plugin signature verification"
    Description: "Create Ed25519 signature verification for plugins"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/security/SignatureVerifier.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/security/Ed25519SignatureVerifier.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/security/PluginKeyManager.java"
    Dependencies: ["Task_2"]
    Estimated_Hours: 8
    Acceptance_Criteria:
      - Ed25519 signature verification
      - Key management
      - Certificate validation
      - Trust store management

Day_61-62_Tasks:
  Task_5:
    Name: "Create plugin resource limits"
    Description: "Implement resource limits and monitoring for plugins"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/runtime/PluginResourceMonitor.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/runtime/PluginResourceLimits.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/runtime/PluginResourceEnforcer.java"
    Dependencies: ["Task_1", "Task_3"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Resource monitoring implemented
      - Memory limits enforced
      - CPU limits enforced
      - Network limits enforced

Day_62_Tasks:
  Task_6:
    Name: "Setup plugin build configuration"
    Description: "Create build configuration for plugin system"
    Files:
      - "platform/java/kernel/build.gradle.kts"
      - "platform/java/kernel/settings.gradle.kts"
      - "platform/java/kernel/src/test/resources/plugin-test-config.yml"
    Dependencies: ["Task_5"]
    Estimated_Hours: 4
    Acceptance_Criteria:
      - Plugin system build configuration
      - Test plugin setup
      - Integration test configuration
      - Build pipeline integration
```

#### 5.2.2 Week 10 Task Breakdown

**Day 63-64: Advanced Features**

```yaml
Day_63_Tasks:
  Task_7:
    Name: "Implement cross-product workflow engine"
    Description: "Create workflow orchestration across products"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/workflow/CrossProductWorkflowEngine.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/workflow/WorkflowExecutor.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/workflow/WorkflowState.java"
    Dependencies: ["Phase 4 completion", "Task_1"]
    Estimated_Hours: 12
    Acceptance_Criteria:
      - Cross-product workflow engine
      - Workflow orchestration
      - State management
      - Error handling

Day_64_Tasks:
  Task_8:
    Name: "Create advanced AI/ML model registry"
    Description: "Implement cross-product model sharing and validation"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/ai/CrossProductModelRegistry.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/ai/ModelValidator.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/ai/ModelPrivacyChecker.java"
    Dependencies: ["Task_7"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Cross-product model registry
      - Model validation
      - Privacy checking
      - Model versioning

Day_65-66_Tasks:
  Task_9:
    Name: "Implement kernel performance optimization"
    Description: "Create performance monitoring and optimization"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/performance/KernelPerformanceMonitor.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/performance/PerformanceOptimizer.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/performance/CacheManager.java"
    Dependencies: ["Task_1", "Task_7"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Performance monitoring
      - Automatic optimization
      - Cache management
      - Resource tuning

Day_66_Tasks:
  Task_10:
    Name: "Create advanced monitoring and alerting"
    Description: "Implement comprehensive monitoring for kernel"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/monitoring/KernelMonitoringService.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/monitoring/AlertManager.java"
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/monitoring/DashboardService.java"
    Dependencies: ["Task_9"]
    Estimated_Hours: 8
    Acceptance_Criteria:
      - Kernel monitoring service
      - Alert management
      - Dashboard service
      - Metrics collection

Day_67-68_Tasks:
  Task_11:
    Name: "Implement advanced kernel testing"
    Description: "Create comprehensive testing for advanced features"
    Files:
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/runtime/PluginSystemTest.java"
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/workflow/WorkflowEngineTest.java"
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/ai/ModelRegistryTest.java"
    Dependencies: ["Task_10"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Plugin system tests
      - Workflow engine tests
      - Model registry tests
      - Performance tests

Day_68_Tasks:
  Task_12:
    Name: "Advanced features validation"
    Description: "Validate end-to-end advanced features"
    Files:
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/AdvancedFeaturesEndToEndTest.java"
      - "platform/java/kernel/validation/AdvancedFeaturesValidationReport.md"
    Dependencies: ["Task_11"]
    Estimated_Hours: 6
    Acceptance_Criteria:
      - End-to-end advanced features tested
      - Performance benchmarks
      - Security validation
      - Compliance validation
```

---

## Phase 6: Testing and Validation (Weeks 11-12)

### 6.1 Comprehensive Testing Strategy Implementation

#### 6.1.1 Kernel Unit Tests - Complete Implementation

**Kernel Registry Unit Tests:**

```java
// Location: platform/java/kernel/src/test/java/com/ghatana/kernel/registry/
package com.ghatana.kernel.registry;

import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.plugin.KernelPlugin;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for KernelRegistry
 */
@DisplayName("Kernel Registry Tests")
class KernelRegistryTest {

    @Mock
    private KernelModule mockModule;

    @Mock
    private KernelPlugin mockPlugin;

    @Mock
    private KernelCapability mockCapability;

    @Mock
    private KernelDependency mockDependency;

    private KernelRegistry registry;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        registry = new InMemoryKernelRegistry();

        // Setup mock behaviors
        when(mockModule.getModuleId()).thenReturn("test-module");
        when(mockModule.getVersion()).thenReturn("1.0.0");
        when(mockModule.getCapabilities()).thenReturn(Set.of(mockCapability));
        when(mockModule.getDependencies()).thenReturn(Set.of());

        when(mockPlugin.getPluginId()).thenReturn("test-plugin");
        when(mockPlugin.getVersion()).thenReturn("1.0.0");
        when(mockPlugin.getCapabilities()).thenReturn(Set.of(mockCapability));
        when(mockPlugin.getDependencies()).thenReturn(Set.of());

        when(mockCapability.getCapabilityId()).thenReturn("test-capability");
        when(mockDependency.getDependencyId()).thenReturn("test-dependency");
    }

    @Test
    @DisplayName("Should register module successfully")
    void shouldRegisterModuleSuccessfully() {
        // When
        registry.registerModule(mockModule);

        // Then
        Optional<KernelModule> retrieved = registry.getModule("test-module");
        assertTrue(retrieved.isPresent());
        assertEquals(mockModule, retrieved.get());

        verify(mockModule).getModuleId();
        verify(mockModule).getCapabilities();
        verify(mockModule).getDependencies();
    }

    @Test
    @DisplayName("Should register plugin successfully")
    void shouldRegisterPluginSuccessfully() {
        // When
        registry.registerPlugin(mockPlugin);

        // Then
        Optional<KernelPlugin> retrieved = registry.getPlugin("test-plugin");
        assertTrue(retrieved.isPresent());
        assertEquals(mockPlugin, retrieved.get());

        verify(mockPlugin).getPluginId();
        verify(mockPlugin).getCapabilities();
        verify(mockPlugin).getDependencies();
    }

    @Test
    @DisplayName("Should resolve dependencies correctly")
    void shouldResolveDependenciesCorrectly() {
        // Given
        KernelDependency dependency = KernelDependency.builder()
            .withDependencyId("test-module")
            .withVersion("1.0.0")
            .withType(KernelDependency.DependencyType.MODULE)
            .build();

        KernelPlugin pluginWithDependency = mock(KernelPlugin.class);
        when(pluginWithDependency.getPluginId()).thenReturn("dependent-plugin");
        when(pluginWithDependency.getVersion()).thenReturn("1.0.0");
        when(pluginWithDependency.getCapabilities()).thenReturn(Set.of(mockCapability));
        when(pluginWithDependency.getDependencies()).thenReturn(Set.of(dependency));

        // When
        registry.registerModule(mockModule);
        registry.registerPlugin(pluginWithDependency);

        // Then
        List<KernelModule> dependencies = registry.resolveDependencies(pluginWithDependency);
        assertEquals(1, dependencies.size());
        assertEquals(mockModule, dependencies.get(0));
    }

    @Test
    @DisplayName("Should detect circular dependencies")
    void shouldDetectCircularDependencies() {
        // Given
        KernelDependency dependencyA = KernelDependency.builder()
            .withDependencyId("module-b")
            .withVersion("1.0.0")
            .withType(KernelDependency.DependencyType.MODULE)
            .build();

        KernelDependency dependencyB = KernelDependency.builder()
            .withDependencyId("module-a")
            .withVersion("1.0.0")
            .withType(KernelDependency.DependencyType.MODULE)
            .build();

        KernelModule moduleA = mock(KernelModule.class);
        when(moduleA.getModuleId()).thenReturn("module-a");
        when(moduleA.getCapabilities()).thenReturn(Set.of(mockCapability));
        when(moduleA.getDependencies()).thenReturn(Set.of(dependencyA));

        KernelModule moduleB = mock(KernelModule.class);
        when(moduleB.getModuleId()).thenReturn("module-b");
        when(moduleB.getCapabilities()).thenReturn(Set.of(mockCapability));
        when(moduleB.getDependencies()).thenReturn(Set.of(dependencyB));

        // When & Then
        registry.registerModule(moduleA);
        registry.registerModule(moduleB);

        assertThrows(CircularDependencyException.class, () -> {
            registry.resolveDependencies(moduleA);
        });
    }

    @Test
    @DisplayName("Should find modules by capability")
    void shouldFindModulesByCapability() {
        // Given
        KernelCapability anotherCapability = mock(KernelCapability.class);
        when(anotherCapability.getCapabilityId()).thenReturn("another-capability");

        KernelModule moduleWithCapability = mock(KernelModule.class);
        when(moduleWithCapability.getModuleId()).thenReturn("module-with-capability");
        when(moduleWithCapability.getCapabilities()).thenReturn(Set.of(mockCapability));

        KernelModule moduleWithoutCapability = mock(KernelModule.class);
        when(moduleWithoutCapability.getModuleId()).thenReturn("module-without-capability");
        when(moduleWithoutCapability.getCapabilities()).thenReturn(Set.of(anotherCapability));

        // When
        registry.registerModule(moduleWithCapability);
        registry.registerModule(moduleWithoutCapability);

        // Then
        List<KernelModule> modules = registry.getModulesByCapability(mockCapability);
        assertEquals(1, modules.size());
        assertEquals(moduleWithCapability, modules.get(0));
    }

    @Test
    @DisplayName("Should handle duplicate module registration")
    void shouldHandleDuplicateModuleRegistration() {
        // Given
        registry.registerModule(mockModule);

        // When & Then
        assertThrows(ModuleAlreadyRegisteredException.class, () -> {
            registry.registerModule(mockModule);
        });
    }

    @Test
    @DisplayName("Should unregister module correctly")
    void shouldUnregisterModuleCorrectly() {
        // Given
        registry.registerModule(mockModule);
        assertTrue(registry.getModule("test-module").isPresent());

        // When
        registry.unregisterModule("test-module");

        // Then
        assertFalse(registry.getModule("test-module").isPresent());
    }

    @Test
    @DisplayName("Should validate dependencies before registration")
    void shouldValidateDependenciesBeforeRegistration() {
        // Given
        KernelDependency missingDependency = KernelDependency.builder()
            .withDependencyId("missing-module")
            .withVersion("1.0.0")
            .withType(KernelDependency.DependencyType.MODULE)
            .build();

        KernelModule moduleWithMissingDependency = mock(KernelModule.class);
        when(moduleWithMissingDependency.getModuleId()).thenReturn("module-with-missing-dep");
        when(moduleWithMissingDependency.getCapabilities()).thenReturn(Set.of(mockCapability));
        when(moduleWithMissingDependency.getDependencies()).thenReturn(Set.of(missingDependency));

        // When & Then
        assertThrows(MissingDependencyException.class, () -> {
            registry.registerModule(moduleWithMissingDependency);
        });
    }

    @Test
    @DisplayName("Should handle concurrent access safely")
    void shouldHandleConcurrentAccessSafely() throws InterruptedException {
        // Given
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        // When
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    registry.registerModule(mockModule);
                    registry.getModule("test-module");
                    registry.unregisterModule("test-module");
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();

        // Then
        assertTrue(exceptions.isEmpty(), "No exceptions should occur during concurrent access");
    }

    @Test
    @DisplayName("Should provide registry statistics")
    void shouldProvideRegistryStatistics() {
        // Given
        registry.registerModule(mockModule);
        registry.registerPlugin(mockPlugin);

        // When
        RegistryStatistics statistics = registry.getStatistics();

        // Then
        assertEquals(1, statistics.getModuleCount());
        assertEquals(1, statistics.getPluginCount());
        assertEquals(1, statistics.getCapabilityCount());
        assertEquals(0, statistics.getDependencyCount());
    }
}
```

**Async Module Lifecycle Test (MUST extend `EventloopTestBase`):**

> **CRITICAL**: ALL async tests that exercise `Promise`-returning methods MUST extend
> `EventloopTestBase` and use `runPromise(...)`. Never call `.getResult()` on a Promise.
> See `copilot-instructions.md` §3.

```java
// Location: platform/java/kernel/src/test/java/com/ghatana/kernel/module/
package com.ghatana.kernel.module;

import com.ghatana.activej.test.EventloopTestBase;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.HealthStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Async lifecycle tests for AbstractKernelModule.
 * Demonstrates the MANDATORY EventloopTestBase + runPromise pattern.
 */
@DisplayName("Kernel Module Lifecycle Tests")
class KernelModuleLifecycleTest extends EventloopTestBase {

    private StubKernelModule module;

    @BeforeEach
    void setUp() {
        module = new StubKernelModule();
        KernelContext ctx = mock(KernelContext.class);
        module.initialize(ctx);
    }

    @Test
    @DisplayName("Should start module asynchronously")
    void shouldStartModuleAsync() {
        // WHEN — runPromise() runs the Promise on the ActiveJ Eventloop
        runPromise(() -> module.start());

        // THEN
        assertThat(module.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(module.isReady()).isTrue();
    }

    @Test
    @DisplayName("Should stop module asynchronously")
    void shouldStopModuleAsync() {
        // GIVEN
        runPromise(() -> module.start());

        // WHEN
        runPromise(() -> module.stop());

        // THEN
        assertThat(module.getHealthStatus()).isEqualTo(HealthStatus.STOPPED);
        assertThat(module.isReady()).isFalse();
    }

    /** Minimal concrete implementation for testing. */
    private static class StubKernelModule extends AbstractKernelModule {
        StubKernelModule() {
            super(
                KernelDescriptor.builder()
                    .withDescriptorId("stub")
                    .withName("Stub Module")
                    .withVersion("1.0.0")
                    .withDescription("Test stub")
                    .withOwner("test")
                    .withType(KernelDescriptor.DescriptorType.KERNEL_MODULE)
                    .build(),
                Set.of(),
                Set.of()
            );
        }

        @Override protected void doInitialize() { }
        @Override protected void doStart() { }
        @Override protected void doStop() { }
        @Override protected void doCleanup() { }
    }
}
```

### 6.2 Detailed Implementation Tasks (Weeks 11-12)

#### 6.2.1 Week 11 Task Breakdown

**Day 71-72: Comprehensive Testing**

```yaml
Day_71_Tasks:
  Task_1:
    Name: "Create kernel unit tests"
    Description: "Implement comprehensive unit tests for all kernel components"
    Files:
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/registry/KernelRegistryTest.java"
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/module/KernelModuleTest.java"
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/plugin/KernelPluginTest.java"
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/tenant/KernelTenantContextTest.java"
    Dependencies: ["Phase 5 completion"]
    Estimated_Hours: 16
    Acceptance_Criteria:
      - 95% code coverage for kernel core
      - All unit tests passing
      - Test documentation complete
      - Performance benchmarks

Day_72_Tasks:
  Task_2:
    Name: "Implement integration tests"
    Description: "Create integration tests for PHR-Finance scenarios"
    Files:
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/integration/PhrFinanceIntegrationTest.java"
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/integration/CrossProductEventTest.java"
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/integration/DataSharingIntegrationTest.java"
    Dependencies: ["Task_1"]
    Estimated_Hours: 12
    Acceptance_Criteria:
      - 80% integration test coverage
      - End-to-end scenarios tested
      - Cross-product integration validated
      - Error handling tested

Day_73-74_Tasks:
  Task_3:
    Name: "Create performance tests"
    Description: "Implement performance tests for kernel components"
    Files:
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/performance/KernelPerformanceTest.java"
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/performance/EventProcessingPerformanceTest.java"
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/performance/PluginSystemPerformanceTest.java"
    Dependencies: ["Task_2"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Performance benchmarks established
      - Load testing completed
      - Memory usage validated
      - Latency measurements

Day_74_Tasks:
  Task_4:
    Name: "Implement security tests"
    Description: "Create security tests for plugin system and data isolation"
    Files:
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/security/PluginSecurityTest.java"
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/security/DataIsolationTest.java"
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/security/BoundaryEnforcementTest.java"
    Dependencies: ["Task_3"]
    Estimated_Hours: 8
    Acceptance_Criteria:
      - Security validation complete
      - Penetration testing scenarios
      - Data isolation verified
      - Access control tested

Day_75-76_Tasks:
  Task_5:
    Name: "Create compliance tests"
    Description: "Implement compliance tests for PHR and Finance regulations"
    Files:
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/compliance/PhrComplianceTest.java"
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/compliance/FinanceComplianceTest.java"
      - "platform/java/kernel/src/test/java/com/ghatana/kernel/compliance/CrossProductComplianceTest.java"
    Dependencies: ["Task_4"]
    Estimated_Hours: 10
    Acceptance_Criteria:
      - Healthcare compliance validated
      - Financial compliance validated
      - Data retention tested
      - Audit requirements verified

Day_76_Tasks:
  Task_6:
    Name: "Setup test infrastructure"
    Description: "Create test infrastructure and CI/CD integration"
    Files:
      - "platform/java/kernel/src/test/resources/test-config.yml"
      - "platform/java/kernel/.github/workflows/test.yml"
      - "platform/java/docker/docker-compose.test.yml"
    Dependencies: ["Task_5"]
    Estimated_Hours: 6
    Acceptance_Criteria:
      - Test infrastructure complete
      - CI/CD pipeline configured
      - Test data fixtures ready
      - Automated testing enabled
```

#### 6.2.2 Week 12 Task Breakdown

**Day 77-78: Validation and Documentation**

```yaml
Day_77_Tasks:
  Task_7:
    Name: "Run comprehensive test suite"
    Description: "Execute all tests and generate reports"
    Files:
      - "platform/java/kernel/reports/test-results.html"
      - "platform/java/kernel/reports/coverage-report.html"
      - "platform/java/kernel/reports/performance-report.html"
    Dependencies: ["Task_6"]
    Estimated_Hours: 8
    Acceptance_Criteria:
      - All tests passing
      - Coverage reports generated
      - Performance reports complete
      - Security reports available

Day_78_Tasks:
  Task_8:
    Name: "Performance tuning and optimization"
    Description: "Optimize kernel performance based on test results"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/performance/PerformanceTuner.java"
      - "platform/java/kernel/config/performance-tuning.yml"
      - "platform/java/kernel/reports/optimization-report.md"
    Dependencies: ["Task_7"]
    Estimated_Hours: 6
    Acceptance_Criteria:
      - Performance optimizations applied
      - Benchmarks improved
      - Resource usage optimized
      - Latency reduced

Day_79-80_Tasks:
  Task_9:
    Name: "Security hardening"
    Description: "Implement security hardening based on test results"
    Files:
      - "platform/java/kernel/src/main/java/com/ghatana/kernel/security/SecurityHardener.java"
      - "platform/java/kernel/config/security-hardening.yml"
      - "platform/java/kernel/reports/security-assessment.md"
    Dependencies: ["Task_8"]
    Estimated_Hours: 8
    Acceptance_Criteria:
      - Security hardening applied
      - Vulnerabilities addressed
      - Penetration testing passed
      - Security policies enforced

Day_80_Tasks:
  Task_10:
    Name: "Create comprehensive documentation"
    Description: "Generate complete documentation for kernel platform"
    Files:
      - "docs/KERNEL_PLATFORM_DOCUMENTATION.md"
      - "docs/API_REFERENCE.md"
      - "docs/DEPLOYMENT_GUIDE.md"
      - "docs/TROUBLESHOOTING_GUIDE.md"
    Dependencies: ["Task_9"]
    Estimated_Hours: 6
    Acceptance_Criteria:
      - Complete documentation
      - API reference complete
      - Deployment guide ready
      - Troubleshooting guide available

Day_81-82_Tasks:
  Task_11:
    Name: "Production readiness assessment"
    Description: "Assess production readiness and create deployment plan"
    Files:
      - "platform/java/kernel/production-readiness-assessment.md"
      - "platform/java/kernel/deployment-plan.md"
      - "platform/java/kernel/rollback-plan.md"
      - "platform/java/kernel/monitoring-plan.md"
    Dependencies: ["Task_10"]
    Estimated_Hours: 8
    Acceptance_Criteria:
      - Production readiness validated
      - Deployment plan complete
      - Rollback plan ready
      - Monitoring plan established

Day_82_Tasks:
  Task_12:
    Name: "Final validation and sign-off"
    Description: "Final validation and stakeholder sign-off"
    Files:
      - "platform/java/kernel/final-validation-report.md"
      - "platform/java/kernel/stakeholder-sign-off.md"
      - "platform/java/kernel/project-completion-summary.md"
    Dependencies: ["Task_11"]
    Estimated_Hours: 4
    Acceptance_Criteria:
      - Final validation complete
      - Stakeholder sign-off obtained
      - Project completion summary
      - Lessons learned documented
```

---

## Success Metrics and Validation

### 7.1 Technical Metrics

#### 7.1.1 Performance Metrics

- **Kernel Primitive Overhead**: <1ms per kernel primitive call (see Risk T-01)
- **Event Processing Latency**: <50ms for 95th percentile
- **Eventloop Stall Detection**: <2ms stall threshold with eBPF alerting (see Risk T-02, X-03)
- **Memory Usage**: <100MB increase under normal load
- **Plugin Load Time**: <5 seconds for typical plugin
- **Cross-Product Event Latency**: <100ms end-to-end
- **LLM Gateway Latency**: <200ms for cached prompts, <2s for uncached

#### 7.1.2 Reliability Metrics

- **Kernel Uptime**: 99.999% availability
- **Plugin Success Rate**: >99.9% successful plugin operations
- **Data Isolation**: 100% enforcement of tenant boundaries
- **Audit Completeness**: 100% audit trail coverage (including AI inputs/outputs)
- **Agent Memory Compaction**: Episodic→semantic consolidation within 24h

#### 7.1.3 Security Metrics

- **Plugin Validation**: 100% signature verification
- **Data Encryption**: 100% encryption at rest (AES-256) and in transit (TLS 1.3)
- **Access Control**: 100% enforcement of RBAC + ABAC permissions
- **Compliance**: 100% regulatory requirement compliance
- **AI Safety**: 100% human review of low-confidence agent decisions (<0.7)

#### 7.1.4 AI/ML Metrics

- **LLM Cost per Product**: Tracked via per-tenant LLM budgets at Gateway
- **Prompt Cache Hit Rate**: >40% across all products (semantic caching)
- **Agent Reflection Cycles**: Max 5 per turn (circuit-breaker enforced)
- **AI Anomaly Detection**: False-positive rate <5% on all metrics streams
- **Agent Skill Promotion**: Safety evaluation gate pass rate tracked per product

#### 7.1.5 Product Layer Purity Metrics

- **Infrastructure Code in Products**: 0 lines (enforced by build-time checks)
- **Opinionated Core Violations**: 0 (enforced by static analysis — no Spring Reactor, no CompletableFuture mix, no direct DB/LLM calls)
- **Kernel API Breaking Changes**: 0 per quarter (SPI + contract tests)

### 7.2 Business Metrics

#### 7.2.1 Development Velocity

- **Code Reduction**: 60% reduction in duplicate platform code
- **Feature Delivery**: 40% faster feature development (products write only business logic)
- **Integration Time**: 80% reduction in cross-product integration time
- **Testing Coverage**: 95%+ test coverage for kernel components
- **New Product Onboarding**: Code-to-production in <1 day (full lifecycle ownership)

#### 7.2.2 Operational Efficiency

- **Infrastructure Costs**: 30% reduction through shared platform
- **AI Costs**: 40-60% reduction through semantic prompt caching and tiered model selection
- **Monitoring Overhead**: 50% reduction through unified AI-powered observability
- **Compliance Costs**: 40% reduction through automated compliance and audit trails
- **Support Tickets**: 60% reduction in platform-related issues
- **Knowledge Bus-Factor**: >=3 engineers per kernel module (see Risk O-03)

---

## Appendix A: Glossary of Key Terms

| Term                                  | Definition                                                                                                                                                                   |
| ------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **ActiveJ**                           | Lightweight asynchronous Java framework. The kernel's **non-negotiable** async runtime — all async operations MUST use `Promise` / `Eventloop`.                              |
| **AEP**                               | **Agentic Event Processor** — the platform's event-processing and operator-pipeline engine (formerly "multi-agent-system"). Maps to `products/agentic-event-processor`.      |
| **Agent Reflection**                  | GAA lifecycle step where the agent asynchronously introspects on completed turns to extract learnable patterns.                                                              |
| **Data-Cloud**                        | The platform's data management layer (event stores, config registry, governance, audit) (formerly "collection-entity-system" / "eventcloud"). Maps to `products/data-cloud`. |
| **EventloopTestBase**                 | Mandatory test base class from `libs:activej-test-utils`. All async tests MUST extend it and use `runPromise(...)`.                                                          |
| **FHIR**                              | **Fast Healthcare Interoperability Resources** — HL7 standard for exchanging healthcare data (version R4 used in PHR).                                                       |
| **GAA**                               | **Generic Adaptive Agent** — the agent framework with a 5-step lifecycle: PERCEIVE → REASON → ACT → CAPTURE → REFLECT.                                                       |
| **KernelContext**                     | Runtime context providing dependency lookup, event-handler registration, and tenant access to every module (see §1.1.2b).                                                    |
| **KernelDescriptor**                  | Immutable metadata record describing a kernel module, plugin, or extension — uses a Builder pattern.                                                                         |
| **KernelExtension**                   | Extension point allowing products to contribute capabilities to a host module without owning a lifecycle (see §1.1.2a).                                                      |
| **KernelModule**                      | Core unit of composition — provides capabilities, declares dependencies, has a managed lifecycle (init → start → stop).                                                      |
| **KernelPlugin**                      | Extends KernelModule with install/uninstall/reload/security-sandbox semantics for runtime-loadable code.                                                                     |
| **KernelTenantContext**               | Multi-tenant execution context carrying tenant identity, user/security context, feature flags, and config.                                                                   |
| **LLM Gateway**                       | Routing, caching, and budget-management layer for LLM calls — shared by all products.                                                                                        |
| **Opinionated Core**                  | Non-negotiable technology stack: Java 21 + ActiveJ, Data-Cloud, AEP, GAA, Protobuf + gRPC, OTel, Micrometer.                                                                 |
| **PHR**                               | **Personal Health Record** — the healthcare product. Maps to `products/phr` (formerly `domain/yappc`).                                                                       |
| **Product Layer Contract**            | Agreement that products own only business logic, workflows, UI/UX — everything else comes from the kernel.                                                                   |
| **Promise**                           | ActiveJ's async primitive (`io.activej.promise.Promise`). Replaces `CompletableFuture` throughout the kernel.                                                                |
| **Promises.all()**                    | ActiveJ helper to combine multiple Promises (replaces `CompletableFuture.allOf`).                                                                                            |
| **Promise.ofBlocking(executor, ...)** | Wraps blocking IO in an ActiveJ Promise that runs on a separate executor to avoid blocking the eventloop.                                                                    |
| **Promise.ofFuture(cf)**              | Bridges a `CompletableFuture` into an ActiveJ `Promise` at adapter boundaries.                                                                                               |
| **SEBON**                             | **Securities Board of Nepal** — regulatory body for financial markets; Finance module must comply with SEBON rules.                                                          |
| **SPI**                               | **Service Provider Interface** — Java mechanism for pluggable implementations. Kernel adapters use SPI.                                                                      |

---

## Conclusion

This comprehensive granular specification provides a complete roadmap for implementing the **Ghatana Kernel Platform** — an AI/ML-native, opinionated, full-lifecycle composition kernel — with PHR and Finance integration as the first product validations.

> **Companion Document**: [KERNEL_PLATFORM_BRAINSTORM.md](KERNEL_PLATFORM_BRAINSTORM.md) defines the strategic vision, architectural rationale, and comprehensive risk registry (T-01 through C-02). This document translates that vision into executable implementation phases.

### **Complete Implementation Coverage**

1. **Phase 0**: Foundation analysis with current state assessment, AI readiness audit, and gap identification
2. **Phase 1**: Full kernel core implementation — KernelDescriptor, KernelModule, KernelPlugin, Data-Cloud/AEP adapters, KernelTenantContext, AI-ready kernel primitives (LLM Gateway, Feature Store, Agent Runtime adapters)
3. **Phase 2**: PHR kernel integration — healthcare-specific modules, consent policies, clinical AI integration
4. **Phase 3**: Finance system integration — trading, risk management, compliance, surveillance agents
5. **Phase 4**: Cross-product integration — secure event bus, shared data governance, cross-product AI features
6. **Phase 5**: Advanced features — runtime plugin loading, adaptive AI capabilities, self-optimizing infrastructure
7. **Phase 6**: Comprehensive testing and validation

### **Key Features Delivered**

- **AI/ML-Native Architecture**: Intelligence embedded in every kernel subsystem — anomaly detection, adaptive rate limiting, predictive scaling, intelligent observability — all implicit with zero product configuration (see [KERNEL_PLATFORM_BRAINSTORM.md §AI/ML-Native](KERNEL_PLATFORM_BRAINSTORM.md))
- **Full Lifecycle Ownership**: DEVELOP → TEST → BUILD → DEPLOY → MONITOR → MANAGE — the kernel is the cornerstone of the entire application lifecycle with complete visibility (see [KERNEL_PLATFORM_BRAINSTORM.md §Full Lifecycle](KERNEL_PLATFORM_BRAINSTORM.md))
- **Opinionated Core, Flexible Edges**: Non-negotiable technology stack (Java 21 + ActiveJ, Data-Cloud, AEP, GAA, Protobuf + gRPC, OpenTelemetry) with controlled flexibility at product boundaries (see [KERNEL_PLATFORM_BRAINSTORM.md §Opinionated Core](KERNEL_PLATFORM_BRAINSTORM.md))
- **Product Layer Purity**: Products own ONLY business logic, workflow definitions, process definitions, UI/UX design, and user flow/journey — the kernel handles everything else (see [KERNEL_PLATFORM_BRAINSTORM.md §Product Layer Contract](KERNEL_PLATFORM_BRAINSTORM.md))
- **Data-Cloud Integration**: All data storage, configuration, and governance through the opinionated Data-Cloud platform
- **AEP Integration**: All event processing, operator pipelines, and agent workflows through the opinionated AEP platform
- **GAA Agent Framework**: Full PERCEIVE → REASON → ACT → CAPTURE → REFLECT lifecycle with event-sourced memory, skill promotion, and safety evaluation gates
- **Secure Plugin System**: Runtime plugin loading with security validation, sandboxed execution, and lifecycle governance
- **Comprehensive Risk Management**: Architectural risks (T-01 through C-02 in brainstorm) with implementation-specific risks (P0-01 through X-04 in this document), cross-referenced mitigations

### **Implementation Guidance**

- **Complete Code Examples**: Over 5,000 lines of production-ready Java code using ActiveJ Promise/Eventloop patterns
- **Detailed Task Breakdowns**: 84 specific tasks across 12 weeks
- **Product Layer Contract Checklist**: Verification that every phase maintains product layer purity
- **Opinionated Core Enforcement**: Build-time and runtime enforcement mechanisms for all 8 non-negotiable technology decisions
- **AI/ML Integration**: Phase-by-phase AI subsystem rollout aligned with the brainstorm's pervasion map
- **Acceptance Criteria**: Clear validation criteria for each task
- **Risk Mitigation**: Two-tier risk assessment — architectural (brainstorm) and implementation (this document) — with critical mitigations for every red-scored risk
- **Success Metrics**: Measurable technical and business outcomes

This specification, together with the [Kernel Platform Brainstorm](KERNEL_PLATFORM_BRAINSTORM.md), provides everything needed to successfully implement an AI/ML-native kernel platform with PHR and Finance integration, following the opinionated technology stack while maintaining product layer purity and full lifecycle ownership.
