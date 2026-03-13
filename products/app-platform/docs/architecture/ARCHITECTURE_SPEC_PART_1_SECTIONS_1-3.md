# Architecture & Design Documentation Suite for Project Siddhanta

## Part 1: Sections 1-3

**Document Version:** 2.0  
**Date:** March 5, 2026  
**Status:** Implementation-Ready (Post-ARB Remediation)  
**Change Log:** v2.0 aligns with ARB findings (K-17/K-18/K-19, D-13/D-14, R-02, T-02), adds jurisdiction-neutral core principle, dual-calendar mandate, and content/plugin taxonomy (T1/T2/T3)

> **Stack authority**: [../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md) defines the canonical Siddhanta stack. Any older technology labels in this document are illustrative unless they align to ADR-011.
>
> **Platform Reuse**: See [Finance-Ghatana Integration Plan](../../finance-ghatana-integration-plan.md) for detailed guidance on reusing Ghatana platform components.

---

## Table of Contents - Part 1 (Sections 1-3)

1. [Executive Summary](#1-executive-summary)
2. [Layered Architecture](#2-layered-architecture)
3. [Event-Driven & CQRS Architecture](#3-event-driven--cqrs-architecture)

---

## 1. Executive Summary

### 1.1 Project Vision

**Project Siddhanta** is a **jurisdiction-neutral, multi-domain operating system** designed to serve the complete lifecycle of operations across multiple industries. The platform integrates domain-specific functionality with a generic kernel, providing a unified, scalable, and compliant solution for capital markets, banking, healthcare, insurance, and other domains.

**Capital Markets (Siddhanta) is the first instantiation — not the architectural boundary.** Every piece of domain-specific logic (business rules, workflows, integrations, UI components) is externalized into versioned, signed **Domain Packs**, making it possible to add new domains by publishing new domain packs — zero core-code changes required.

### 1.2 Strategic Objectives

1. **Multi-Domain Platform**: Provide a generic kernel supporting multiple domains (capital markets, banking, healthcare, insurance)
2. **Domain-Neutral Core**: Generic kernel with all domain-specific logic externalized to signed domain packs
3. **Jurisdiction-Neutral**: Support multiple regulatory frameworks via T1/T2/T3 content packs
4. **Regulatory Compliance**: Built-in compliance framework supporting SEBON, NRB (Nepal primary), SEBI, RBI (India), and international regulations (MiFID II, Dodd-Frank)
5. **Scalability**: Support from small operations to large institutional operations with horizontal auto-scaling
6. **Real-time Operations**: Sub-millisecond processing and real-time data distribution
7. **AI-Driven Intelligence**: Embedded AI for risk assessment, compliance monitoring, anomaly detection, and decision support — with full governance, explainability, and drift detection
8. **Multi-Asset Support**: Equities, derivatives, commodities, currencies, fixed income, and future-ready for digital assets and tokenized securities
9. **Cloud-Native**: Kubernetes-based deployment with multi-cloud abstraction (AWS/Azure/GCP/on-premise)
10. **Dual-Calendar Native**: Bikram Sambat and Gregorian calendars at the data layer — every timestamp, every event, every audit record
11. **Domain Pack Extensibility**: All domain behaviour extensible via domain packs without modifying platform kernel
12. **Plugin Extensibility**: All jurisdiction behaviour extensible via the T1/T2/T3 plugin taxonomy

### 1.3 Core Capabilities

#### Domain-Agnostic Kernel Capabilities

- **Identity & Access Management**: Multi-tenant IAM with RBAC/ABAC, MFA, beneficial ownership graph
- **Configuration Engine**: Hierarchical configuration with hot reload, maker-checker, air-gap support
- **Rules Engine**: Declarative policy evaluation with sandboxed execution (OPA/Rego)
- **Plugin Runtime**: Plugin lifecycle management with tier-aware sandbox isolation
- **Event Bus**: Append-only immutable event store with saga orchestration
- **Audit Framework**: Immutable hash-chained audit records with external timestamp anchoring
- **Data Governance**: Data lineage, classification, quality, and compliance management
- **AI Governance**: AI model registry, monitoring, and compliance management
- **Observability**: Unified logging/tracing/metrics with SLO framework
- **Deployment**: Multi-topology deployment with GitOps and infrastructure as code
- **API Gateway**: Unified API gateway with rate limiting, WAF, and domain routing
- **Platform SDK**: Multi-language SDK for all kernel clients
- **Admin Portal**: Unified admin console with schema-driven UI
- **Secrets Management**: Vault abstraction with rotation and HSM support
- **Dual-Calendar Service**: Dual-calendar support with BS/Gregorian conversion
- **Ledger Framework**: Double-entry accounting with immutable ledger
- **Distributed Transactions**: Distributed transaction coordination with saga pattern
- **Resilience Patterns**: Circuit breakers, bulkheads, and retry patterns
- **DLQ Management**: Dead letter queue management with replay

#### Domain-Specific Capabilities (via Domain Packs)

##### Capital Markets (Siddhanta)

- Multi-exchange connectivity via pluggable adapters (NEPSE primary; NSE, BSE, MCX, NCDEX via jurisdiction packs)
- Smart order routing with best execution algorithms
- Pre-trade and post-trade risk management
- Direct Market Access (DMA) and algorithmic trading
- FIX protocol support (FIX 4.2, 4.4, 5.0) — exchange adapter is a T3 plugin
- Market halt detection and circuit-breaker handling
- Real-time position tracking across asset classes
- Multi-currency portfolio valuation
- Performance attribution and analytics
- Margin calculation and monitoring
- Corporate actions processing (dividends, splits, rights, buybacks)
- Comprehensive corporate actions lifecycle management
- Shareholder registry integration
- Dividend processing and distribution
- Rights issue and IPO management
- Regulatory filing automation
- Real-time and historical market data
- Level 1, Level 2, and Level 3 market depth
- Custom analytics and derived data
- Market surveillance and monitoring
- Backtesting infrastructure
- Real-time compliance monitoring
- Automated regulatory reporting (SEBON/NRB primary, SEBI/RBI via packs)
- Risk limit enforcement
- Audit trail and transaction reconstruction
- AML/KYC integration and sanctions screening (OFAC, UN, EU, Nepal NRB lists)
- Client money reconciliation and segregation validation

##### Banking (Domain Pack Template)

- Account management (savings, current, fixed deposit, loan accounts)
- Payment processing (ACH, wire transfers, card payments)
- Loan origination and servicing
- Treasury management (cash management, investments)
- Risk assessment and credit scoring
- Compliance reporting (AML, KYC, regulatory reporting)
- Customer relationship management
- Mobile banking and digital services

##### Healthcare (Domain Pack Template)

- Patient management and records (EHR/EMR)
- Clinical workflows and processes
- Medical billing and claims processing
- Research data management
- Telemedicine and remote monitoring
- Pharmacy management
- Laboratory information systems

##### Insurance (Domain Pack Template)

- Policy management and administration
- Claims processing and settlement
- Underwriting and risk assessment
- Actuarial analysis and pricing
- Reinsurance management
- Compliance and regulatory reporting

### 1.4 Technology Stack Overview

| Layer                | Technologies                                                                          |
| -------------------- | ------------------------------------------------------------------------------------- |
| **Frontend**         | React 18, TypeScript, TailwindCSS, Jotai, TanStack Query, WebSocket                   |
| **API Gateway**      | Envoy/Istio ingress, K-11 gateway control plane, OpenAPI validation                   |
| **Backend Services** | Java 21 + ActiveJ, Node.js LTS + TypeScript + Fastify + Prisma, Python 3.11 + FastAPI |
| **Event Streaming**  | Apache Kafka aligned to Ghatana AEP/Event Cloud                                       |
| **Databases**        | PostgreSQL, TimescaleDB, Redis, OpenSearch, S3/Ceph, pgvector                     |
| **Search**           | OpenSearch                                                                         |
| **Workflow**         | Ghatana workflow runtime + AEP orchestration                                          |
| **Orchestration**    | Kubernetes, Helm                                                                      |
| **Observability**    | Prometheus, Grafana, Jaeger, OpenSearch Stack                                                |
| **AI/ML**            | TensorFlow, PyTorch, MLflow                                                           |
| **Security**         | OAuth 2.0, JWT, HashiCorp Vault, mTLS                                                 |

### 1.5 Architectural Principles

#### Non-Negotiable Invariants

1. **Zero Hardcoding of Jurisdiction Logic**: All country-specific rules externalized to T1/T2/T3 content packs
2. **Event-Sourced, Immutable State**: Every state change captured as an immutable event — no deletes, no silent mutations
3. **CQRS Separation**: Write and read models strictly separated; optimized query projections
4. **Dual-Calendar Native**: Bikram Sambat and Gregorian timestamps at the data layer — every event, every record
5. **AI as Substrate**: AI embedded across all workflows with full governance, explainability, and human-in-the-loop override
6. **Plugin Taxonomy Enforcement**: T1 (Config-only), T2 (Declarative rules), T3 (Signed executable code) — strictly enforced
7. **Generic Core Purity**: Nepal is first instantiation, not architectural boundary

#### Design Principles

8. **Domain-Driven Design (DDD)**: Clear bounded contexts for each business domain
9. **Microservices**: Independent deployment and scaling with clear API contracts
10. **API-First**: Well-defined contracts, versioning, and backward compatibility
11. **Security by Design**: Zero-trust architecture, assume breach, verify everything
12. **Observability**: Comprehensive monitoring, distributed tracing, and centralized logging
13. **Resilience**: Circuit breakers, retries, bulkheads, graceful degradation, and chaos-tested
14. **Maker-Checker**: Approval workflows for all critical state-changing operations

### 1.6 Key Metrics & SLAs

| Metric                             | Target             | Critical Path           |
| ---------------------------------- | ------------------ | ----------------------- |
| **Order Latency**                  | < 1ms (p99)        | Order entry to exchange |
| **Market Data Latency**            | < 100μs            | Exchange to application |
| **System Availability**            | 99.99%             | Trading hours           |
| **Data Consistency**               | 100%               | Financial transactions  |
| **Recovery Time Objective (RTO)**  | < 15 minutes       | Disaster recovery       |
| **Recovery Point Objective (RPO)** | < 1 second         | Data loss tolerance     |
| **Throughput**                     | 100,000 orders/sec | Peak load               |
| **Concurrent Users**               | 50,000+            | Platform-wide           |

---

## 2. Layered Architecture

### 2.1 Architecture Overview

Project Siddhanta employs a **multi-layered architecture** that separates concerns, enables independent scaling, and facilitates maintainability. The architecture consists of seven primary layers:

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│  (Web UI, Mobile Apps, Trading Terminals, APIs)             │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    API Gateway Layer                         │
│  (Envoy/Istio ingress, K-11 routing, Rate Limiting, Auth)   │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                  Application Services Layer                  │
│  (Business Logic, Orchestration, Workflows)                 │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Domain Services Layer                     │
│  (Core Business Domains, Bounded Contexts)                  │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                  Integration & Messaging Layer               │
│  (Kafka, Event Cloud/AEP, FIX Gateway, Exchange Adapters)   │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                      Data Persistence Layer                  │
│  (PostgreSQL, TimescaleDB, Redis, Elasticsearch, Object Store) │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                   Infrastructure & Platform Layer            │
│  (Kubernetes, Service Mesh, Observability, Security)        │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Layer Descriptions

#### 2.2.1 Presentation Layer

**Purpose**: User interaction and data visualization

**Components**:

- **Web Application**: React-based responsive UI for traders, operations, and compliance
- **Mobile Apps**: iOS/Android apps for on-the-go access
- **Trading Terminals**: High-performance desktop applications for professional traders
- **API Documentation**: Interactive API docs (Swagger/OpenAPI)
- **Admin Console**: System administration and configuration

**Technologies**:

- React 18 with TypeScript
- TailwindCSS for styling
- WebSocket for real-time updates
- Chart.js/D3.js for visualizations
- Electron for desktop apps

**Key Patterns**:

- Component-based architecture
- State management with Jotai + TanStack Query
- Real-time data streaming via WebSocket
- Progressive Web App (PWA) capabilities
- Responsive design for multi-device support

#### 2.2.2 API Gateway Layer

**Purpose**: Single entry point for all client requests, handling cross-cutting concerns

**Components**:

- **Envoy/Istio Ingress**: Primary ingress and routing plane
- **K-11 Gateway Control Plane**: Route registration, policy distribution, schema validation
- **Rate Limiter**: Request throttling and quota management
- **Authentication Service**: OAuth 2.0, JWT validation
- **API Versioning**: Support for multiple API versions
- **Request Router**: Intelligent routing based on request attributes

**Technologies**:

- Envoy Proxy
- Istio Ingress Gateway
- Redis for rate limiting
- OAuth 2.0 / OpenID Connect
- JWT for token-based auth
- OpenAPI for external contract validation
- gRPC/protobuf for internal routing metadata where required

**Responsibilities**:

- Request routing and load balancing
- Authentication and authorization
- Rate limiting and throttling
- Request/response transformation
- API versioning and deprecation
- SSL/TLS termination
- Request logging and monitoring
- CORS handling

**Configuration Example**:

```yaml
# Istio VirtualService + K-11 managed route metadata
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: orders-api
spec:
  hosts:
    - api.siddhanta.local
  gateways:
    - public-gateway
  http:
    - match:
        - uri:
            prefix: /api/v1/orders
      route:
        - destination:
            host: oms-service
            port:
              number: 8080
      headers:
        request:
          set:
            x-service-name: oms-service
```

#### 2.2.3 Application Services Layer

**Purpose**: Orchestrate business workflows and coordinate domain services

**Components**:

- **Order Orchestration Service**: Manages order lifecycle workflows
- **Portfolio Aggregation Service**: Consolidates portfolio data
- **Corporate Actions Workflow Service**: Orchestrates corporate actions processing
- **Reporting Service**: Generates reports across domains
- **Notification Service**: Multi-channel notifications
- **Workflow Orchestration**: Ghatana workflow runtime and saga orchestration

**Technologies**:

- Java 21 + ActiveJ for core orchestration services
- Node.js LTS + TypeScript + Fastify + Prisma for portal-facing and control-plane CRUD services
- Ghatana `platform/java:workflow` + `products/aep/platform` for workflow and orchestration
- gRPC/protobuf for internal service communication
- REST/OpenAPI for external service exposure

**Key Patterns**:

- Saga pattern for distributed transactions
- Orchestration over choreography for complex workflows
- Circuit breaker for resilience
- Bulkhead pattern for resource isolation

**Example: Order Placement Workflow**

```typescript
// Order Orchestration Service
class OrderOrchestrationService {
  async placeOrder(orderRequest: OrderRequest): Promise<OrderResponse> {
    const correlationId = generateCorrelationId();

    try {
      // Step 1: Validate order
      const validationResult = await this.orderValidationService.validate(
        orderRequest,
        correlationId,
      );

      if (!validationResult.isValid) {
        return { status: "REJECTED", reason: validationResult.errors };
      }

      // Step 2: Check risk limits
      const riskCheck = await this.riskService.checkLimits(
        orderRequest,
        correlationId,
      );

      if (!riskCheck.approved) {
        return { status: "REJECTED", reason: "Risk limit exceeded" };
      }

      // Step 3: Reserve margin
      await this.marginService.reserveMargin(orderRequest, correlationId);

      // Step 4: Submit to exchange
      const exchangeResponse = await this.executionService.submitOrder(
        orderRequest,
        correlationId,
      );

      // Step 5: Update positions
      await this.positionService.updatePendingPosition(
        orderRequest,
        correlationId,
      );

      // Step 6: Publish order event
      await this.eventPublisher.publish({
        type: "ORDER_PLACED",
        orderId: exchangeResponse.orderId,
        correlationId,
      });

      return {
        status: "ACCEPTED",
        orderId: exchangeResponse.orderId,
        exchangeOrderId: exchangeResponse.exchangeOrderId,
      };
    } catch (error) {
      // Compensating transaction
      await this.compensate(orderRequest, correlationId, error);
      throw error;
    }
  }

  private async compensate(
    orderRequest: OrderRequest,
    correlationId: string,
    error: Error,
  ): Promise<void> {
    // Release reserved margin
    await this.marginService.releaseMargin(orderRequest, correlationId);

    // Log compensation
    logger.error("Order placement failed, compensation executed", {
      correlationId,
      error: error.message,
    });
  }
}
```

#### 2.2.4 Domain Services Layer

**Purpose**: Implement core business logic within bounded contexts

**Bounded Contexts**:

1. **Order Management Context**
   - Order Service
   - Order Validation Service
   - Order Routing Service
   - Order Book Service

2. **Execution Management Context**
   - Execution Service
   - Exchange Adapter Service
   - FIX Gateway Service
   - Execution Analytics Service

3. **Portfolio Management Context**
   - Position Service
   - Portfolio Service
   - Valuation Service
   - Performance Service

4. **Market Data Context**
   - Market Data Service
   - Market Data Normalization Service
   - Market Data Distribution Service
   - Historical Data Service

5. **Corporate Actions Context**
   - Corporate Actions Service
   - Dividend Service
   - Rights Issue Service
   - Shareholder Registry Service

6. **Risk Management Context**
   - Risk Service
   - Margin Service
   - Limit Service
   - Exposure Service

7. **Compliance Context**
   - Compliance Service
   - Regulatory Reporting Service
   - Audit Service
   - Surveillance Service

8. **Client Management Context**
   - Client Service
   - KYC Service
   - Account Service
   - Relationship Service

**Domain Model Example: Order Aggregate**

```typescript
// Order Aggregate Root
class Order {
  private id: OrderId;
  private clientId: ClientId;
  private instrument: Instrument;
  private side: OrderSide;
  private quantity: Quantity;
  private price: Price;
  private orderType: OrderType;
  private status: OrderStatus;
  private events: DomainEvent[] = [];

  static create(command: CreateOrderCommand): Order {
    const order = new Order();
    order.apply(
      new OrderCreatedEvent({
        orderId: OrderId.generate(),
        clientId: command.clientId,
        instrument: command.instrument,
        side: command.side,
        quantity: command.quantity,
        price: command.price,
        orderType: command.orderType,
        timestamp: new Date(),
      }),
    );
    return order;
  }

  submit(): void {
    if (this.status !== OrderStatus.PENDING_VALIDATION) {
      throw new InvalidOrderStateError(
        "Order must be validated before submission",
      );
    }

    this.apply(
      new OrderSubmittedEvent({
        orderId: this.id,
        timestamp: new Date(),
      }),
    );
  }

  fill(fillQuantity: Quantity, fillPrice: Price): void {
    if (
      this.status !== OrderStatus.SUBMITTED &&
      this.status !== OrderStatus.PARTIALLY_FILLED
    ) {
      throw new InvalidOrderStateError("Order must be submitted to be filled");
    }

    const remainingQuantity = this.quantity.subtract(fillQuantity);
    const newStatus = remainingQuantity.isZero()
      ? OrderStatus.FILLED
      : OrderStatus.PARTIALLY_FILLED;

    this.apply(
      new OrderFilledEvent({
        orderId: this.id,
        fillQuantity,
        fillPrice,
        remainingQuantity,
        newStatus,
        timestamp: new Date(),
      }),
    );
  }

  cancel(): void {
    if (
      this.status === OrderStatus.FILLED ||
      this.status === OrderStatus.CANCELLED
    ) {
      throw new InvalidOrderStateError(
        "Cannot cancel filled or already cancelled order",
      );
    }

    this.apply(
      new OrderCancelledEvent({
        orderId: this.id,
        timestamp: new Date(),
      }),
    );
  }

  private apply(event: DomainEvent): void {
    this.events.push(event);
    this.mutate(event);
  }

  private mutate(event: DomainEvent): void {
    if (event instanceof OrderCreatedEvent) {
      this.id = event.orderId;
      this.clientId = event.clientId;
      this.instrument = event.instrument;
      this.side = event.side;
      this.quantity = event.quantity;
      this.price = event.price;
      this.orderType = event.orderType;
      this.status = OrderStatus.PENDING_VALIDATION;
    } else if (event instanceof OrderSubmittedEvent) {
      this.status = OrderStatus.SUBMITTED;
    } else if (event instanceof OrderFilledEvent) {
      this.status = event.newStatus;
      this.quantity = event.remainingQuantity;
    } else if (event instanceof OrderCancelledEvent) {
      this.status = OrderStatus.CANCELLED;
    }
  }

  getUncommittedEvents(): DomainEvent[] {
    return [...this.events];
  }

  clearEvents(): void {
    this.events = [];
  }
}
```

#### 2.2.5 Integration & Messaging Layer

**Purpose**: Enable asynchronous communication and external system integration

**Components**:

- **Event Bus**: Kafka-based event streaming
- **Event Runtime**: Ghatana Event Cloud / AEP-aligned processing
- **FIX Gateway**: FIX protocol connectivity to exchanges
- **Exchange Adapters**: Custom adapters for NSE, BSE, MCX, NCDEX
- **Market Data Gateway**: Real-time market data ingestion
- **Payment Gateway Integration**: Payment processing
- **Regulatory Reporting Gateway**: Automated regulatory submissions

**Technologies**:

- Apache Kafka for event streaming
- Ghatana `products/aep/platform` and `platform/java:event-cloud` for event processing abstractions
- QuickFIX/J for FIX protocol
- WebSocket for real-time data
- gRPC for high-performance RPC

**Event Schema Example**:

```typescript
// Event Schema
interface OrderPlacedEvent {
  eventId: string;
  eventType: "ORDER_PLACED";
  aggregateId: string; // Order ID
  aggregateType: "Order";
  version: number;
  timestamp: Date;
  correlationId: string;
  causationId: string;
  userId: string;

  payload: {
    orderId: string;
    clientId: string;
    instrumentId: string;
    side: "BUY" | "SELL";
    quantity: number;
    price: number;
    orderType: "MARKET" | "LIMIT" | "STOP" | "STOP_LIMIT";
    timeInForce: "DAY" | "GTC" | "IOC" | "FOK";
    exchangeOrderId?: string;
  };

  metadata: {
    source: string;
    environment: string;
    schemaVersion: string;
  };
}
```

**Kafka Topic Strategy**:

```yaml
# Topic Naming Convention: <domain>.<entity>.<event-type>
topics:
  - name: trading.order.placed
    partitions: 12
    replication: 3
    retention: 7d

  - name: trading.order.filled
    partitions: 12
    replication: 3
    retention: 7d

  - name: trading.order.cancelled
    partitions: 12
    replication: 3
    retention: 7d

  - name: market-data.tick
    partitions: 24
    replication: 3
    retention: 1d

  - name: corporate-actions.dividend.announced
    partitions: 6
    replication: 3
    retention: 90d

  - name: compliance.alert
    partitions: 6
    replication: 3
    retention: 365d
```

#### 2.2.6 Data Persistence Layer

**Purpose**: Durable storage of application state and data

**Database Strategy**:

| Database          | Use Case           | Data Types                                             |
| ----------------- | ------------------ | ------------------------------------------------------ |
| **PostgreSQL**    | Transactional data | Orders, clients, accounts, portfolios                  |
| **TimescaleDB**   | Time-series data   | Market data, trades, positions history                 |
| **Redis**         | Caching & sessions | Session data, real-time positions, rate limits         |
| **Elasticsearch** | Search & analytics | Full-text search, log analytics, audit trails          |
| **S3 / MinIO**    | Object storage     | Reports, exports, evidence bundles, pack artifacts     |
| **pgvector**      | Semantic retrieval | AI retrieval indexes, log search, resolution knowledge |

**Implementation Note**: Shared storage abstractions, lifecycle management, and lineage integrations should align to Ghatana Data Cloud rather than introducing a separate Siddhanta-only storage platform.

**Data Partitioning Strategy**:

```sql
-- PostgreSQL: Partition orders by date
CREATE TABLE orders (
    order_id UUID PRIMARY KEY,
    client_id UUID NOT NULL,
    instrument_id VARCHAR(50) NOT NULL,
    order_date DATE NOT NULL,
    side VARCHAR(4) NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    price DECIMAL(18,4),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
) PARTITION BY RANGE (order_date);

CREATE TABLE orders_2024_q1 PARTITION OF orders
    FOR VALUES FROM ('2024-01-01') TO ('2024-04-01');

CREATE TABLE orders_2024_q2 PARTITION OF orders
    FOR VALUES FROM ('2024-04-01') TO ('2024-07-01');

-- Index strategy
CREATE INDEX idx_orders_client_date ON orders (client_id, order_date DESC);
CREATE INDEX idx_orders_status ON orders (status) WHERE status IN ('PENDING', 'SUBMITTED');
CREATE INDEX idx_orders_instrument ON orders (instrument_id, order_date DESC);
```

**Event Store Schema**:

```sql
-- Event Sourcing: Event Store
CREATE TABLE event_store (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE (aggregate_id, event_version)
);

CREATE INDEX idx_event_store_aggregate ON event_store (aggregate_id, event_version);
CREATE INDEX idx_event_store_type ON event_store (event_type, created_at);
CREATE INDEX idx_event_store_created ON event_store (created_at DESC);

-- Snapshot table for performance
CREATE TABLE aggregate_snapshots (
    aggregate_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    snapshot_version INTEGER NOT NULL,
    snapshot_data JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```

#### 2.2.7 Infrastructure & Platform Layer

**Purpose**: Provide foundational platform capabilities

**Components**:

- **Kubernetes Cluster**: Container orchestration
- **Service Mesh (Istio)**: Service-to-service communication, security, observability
- **Monitoring Stack**: Prometheus, Grafana, Alertmanager
- **Logging Stack**: ELK (Elasticsearch, Logstash, Kibana)
- **Tracing**: Jaeger for distributed tracing
- **Secret Management**: HashiCorp Vault
- **CI/CD Pipeline**: GitHub Actions, ArgoCD
- **Container Registry**: Harbor

**Kubernetes Deployment Example**:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: trading
  labels:
    app: order-service
    version: v1
spec:
  replicas: 3
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
        version: v1
    spec:
      containers:
        - name: order-service
          image: harbor.siddhanta.io/trading/order-service:1.0.0
          ports:
            - containerPort: 8080
              name: http
            - containerPort: 9090
              name: metrics
          env:
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: order-service-secrets
                  key: database-url
            - name: KAFKA_BROKERS
              value: "kafka-0.kafka-headless:9092,kafka-1.kafka-headless:9092"
          resources:
            requests:
              memory: "512Mi"
              cpu: "500m"
            limits:
              memory: "1Gi"
              cpu: "1000m"
          livenessProbe:
            httpGet:
              path: /health/live
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /health/ready
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: order-service
  namespace: trading
spec:
  selector:
    app: order-service
  ports:
    - name: http
      port: 80
      targetPort: 8080
    - name: metrics
      port: 9090
      targetPort: 9090
  type: ClusterIP
```

### 2.3 Cross-Cutting Concerns

#### 2.3.1 Security

- mTLS between services (via Istio)
- JWT-based authentication
- RBAC for authorization
- Secrets management via Vault
- Network policies for pod-to-pod communication

#### 2.3.2 Observability

- Distributed tracing with Jaeger
- Metrics collection with Prometheus
- Centralized logging with ELK
- Custom dashboards in Grafana
- Alerting via Alertmanager

#### 2.3.3 Resilience

- Circuit breakers (via Istio/Resilience4j)
- Retry policies with exponential backoff
- Bulkhead pattern for resource isolation
- Rate limiting at API gateway
- Graceful degradation

#### 2.3.4 Configuration Management

- Environment-specific configurations
- Feature flags for gradual rollouts
- Dynamic configuration updates
- Configuration validation at startup

---

## 3. Event-Driven & CQRS Architecture

### 3.1 Event-Driven Architecture Overview

Project Siddhanta employs an **event-driven architecture (EDA)** to achieve:

- **Loose coupling** between services
- **Scalability** through asynchronous processing
- **Auditability** via complete event history
- **Temporal queries** and state reconstruction
- **Real-time responsiveness** to business events

### 3.2 Event Sourcing

**Principle**: Store all changes to application state as a sequence of events rather than just the current state.

**Benefits**:

- Complete audit trail
- Temporal queries (state at any point in time)
- Event replay for debugging and testing
- Support for complex business analytics
- Natural fit for regulatory compliance

**Event Store Design**:

```typescript
// Event Store Interface
interface EventStore {
  append(aggregateId: string, events: DomainEvent[]): Promise<void>;
  getEvents(aggregateId: string, fromVersion?: number): Promise<DomainEvent[]>;
  getEventsByType(
    eventType: string,
    from: Date,
    to: Date,
  ): Promise<DomainEvent[]>;
  getAllEvents(from: Date, to: Date): Promise<DomainEvent[]>;
  subscribe(eventType: string, handler: EventHandler): void;
}

// Event Store Implementation
class PostgresEventStore implements EventStore {
  constructor(private db: Database) {}

  async append(aggregateId: string, events: DomainEvent[]): Promise<void> {
    const client = await this.db.connect();

    try {
      await client.query("BEGIN");

      for (const event of events) {
        await client.query(
          `
          INSERT INTO event_store (
            event_id, aggregate_id, aggregate_type, event_type,
            event_version, event_data, metadata, created_at
          ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
        `,
          [
            event.eventId,
            aggregateId,
            event.aggregateType,
            event.eventType,
            event.version,
            JSON.stringify(event.payload),
            JSON.stringify(event.metadata),
            event.timestamp,
          ],
        );
      }

      await client.query("COMMIT");

      // Publish events to Kafka
      await this.publishEvents(events);
    } catch (error) {
      await client.query("ROLLBACK");
      throw error;
    } finally {
      client.release();
    }
  }

  async getEvents(
    aggregateId: string,
    fromVersion = 0,
  ): Promise<DomainEvent[]> {
    const result = await this.db.query(
      `
      SELECT event_id, aggregate_id, aggregate_type, event_type,
             event_version, event_data, metadata, created_at
      FROM event_store
      WHERE aggregate_id = $1 AND event_version >= $2
      ORDER BY event_version ASC
    `,
      [aggregateId, fromVersion],
    );

    return result.rows.map((row) => this.deserializeEvent(row));
  }

  private async publishEvents(events: DomainEvent[]): Promise<void> {
    for (const event of events) {
      await this.kafkaProducer.send({
        topic: this.getTopicForEvent(event.eventType),
        messages: [
          {
            key: event.aggregateId,
            value: JSON.stringify(event),
            headers: {
              "event-type": event.eventType,
              "correlation-id": event.correlationId,
            },
          },
        ],
      });
    }
  }
}
```

**Snapshot Strategy**:
To optimize performance, snapshots are created periodically:

```typescript
class SnapshotStore {
  private readonly SNAPSHOT_FREQUENCY = 100; // Create snapshot every 100 events

  async saveSnapshot(
    aggregateId: string,
    snapshot: AggregateSnapshot,
  ): Promise<void> {
    await this.db.query(
      `
      INSERT INTO aggregate_snapshots (
        aggregate_id, aggregate_type, snapshot_version, snapshot_data, created_at
      ) VALUES ($1, $2, $3, $4, $5)
      ON CONFLICT (aggregate_id) DO UPDATE
      SET snapshot_version = $3, snapshot_data = $4, created_at = $5
    `,
      [
        aggregateId,
        snapshot.aggregateType,
        snapshot.version,
        JSON.stringify(snapshot.data),
        new Date(),
      ],
    );
  }

  async getSnapshot(aggregateId: string): Promise<AggregateSnapshot | null> {
    const result = await this.db.query(
      `
      SELECT aggregate_id, aggregate_type, snapshot_version, snapshot_data, created_at
      FROM aggregate_snapshots
      WHERE aggregate_id = $1
    `,
      [aggregateId],
    );

    if (result.rows.length === 0) return null;

    const row = result.rows[0];
    return {
      aggregateId: row.aggregate_id,
      aggregateType: row.aggregate_type,
      version: row.snapshot_version,
      data: JSON.parse(row.snapshot_data),
      createdAt: row.created_at,
    };
  }
}

// Aggregate Reconstruction with Snapshot
class OrderRepository {
  async getById(orderId: string): Promise<Order> {
    // Try to load from snapshot
    const snapshot = await this.snapshotStore.getSnapshot(orderId);

    let order: Order;
    let fromVersion = 0;

    if (snapshot) {
      order = Order.fromSnapshot(snapshot);
      fromVersion = snapshot.version + 1;
    } else {
      order = new Order();
    }

    // Load events after snapshot
    const events = await this.eventStore.getEvents(orderId, fromVersion);

    // Replay events
    for (const event of events) {
      order.applyEvent(event);
    }

    return order;
  }

  async save(order: Order): Promise<void> {
    const events = order.getUncommittedEvents();

    if (events.length === 0) return;

    await this.eventStore.append(order.id, events);

    // Check if snapshot needed
    if (order.version % this.SNAPSHOT_FREQUENCY === 0) {
      const snapshot = order.createSnapshot();
      await this.snapshotStore.saveSnapshot(order.id, snapshot);
    }

    order.clearEvents();
  }
}
```

### 3.3 CQRS (Command Query Responsibility Segregation)

**Principle**: Separate read and write operations into different models.

**Write Model (Command Side)**:

- Handles commands (CreateOrder, CancelOrder, etc.)
- Enforces business rules and invariants
- Generates domain events
- Optimized for consistency and transactional integrity

**Read Model (Query Side)**:

- Handles queries (GetOrderById, GetPortfolio, etc.)
- Denormalized views optimized for specific queries
- Eventually consistent with write model
- Optimized for performance and scalability

**Architecture Diagram**:

```
Commands                                      Queries
   ↓                                             ↑
Command Handler                           Query Handler
   ↓                                             ↑
Domain Model                              Read Model
   ↓                                             ↑
Event Store  ──→  Events  ──→  Projections  ──→  Read DB
   ↓
Write DB
```

**Implementation Example**:

```typescript
// Command Side
interface CreateOrderCommand {
  commandId: string;
  clientId: string;
  instrumentId: string;
  side: "BUY" | "SELL";
  quantity: number;
  price: number;
  orderType: "MARKET" | "LIMIT";
}

class CreateOrderCommandHandler {
  constructor(
    private orderRepository: OrderRepository,
    private riskService: RiskService,
    private eventBus: EventBus,
  ) {}

  async handle(command: CreateOrderCommand): Promise<string> {
    // Validate command
    this.validate(command);

    // Check risk limits
    await this.riskService.checkLimits(command.clientId, command);

    // Create order aggregate
    const order = Order.create({
      clientId: command.clientId,
      instrument: await this.getInstrument(command.instrumentId),
      side: command.side,
      quantity: new Quantity(command.quantity),
      price: new Price(command.price),
      orderType: command.orderType,
    });

    // Save order (persists events)
    await this.orderRepository.save(order);

    return order.id;
  }
}

// Query Side
interface OrderSummaryView {
  orderId: string;
  clientId: string;
  clientName: string;
  instrumentId: string;
  instrumentName: string;
  side: string;
  quantity: number;
  price: number;
  status: string;
  filledQuantity: number;
  averageFillPrice: number;
  createdAt: Date;
  updatedAt: Date;
}

class OrderQueryService {
  constructor(private readDb: Database) {}

  async getOrderById(orderId: string): Promise<OrderSummaryView> {
    const result = await this.readDb.query(
      `
      SELECT 
        o.order_id,
        o.client_id,
        c.client_name,
        o.instrument_id,
        i.instrument_name,
        o.side,
        o.quantity,
        o.price,
        o.status,
        o.filled_quantity,
        o.average_fill_price,
        o.created_at,
        o.updated_at
      FROM order_summary_view o
      JOIN clients c ON o.client_id = c.client_id
      JOIN instruments i ON o.instrument_id = i.instrument_id
      WHERE o.order_id = $1
    `,
      [orderId],
    );

    if (result.rows.length === 0) {
      throw new OrderNotFoundError(orderId);
    }

    return result.rows[0];
  }

  async getOrdersByClient(
    clientId: string,
    filters: OrderFilters,
  ): Promise<OrderSummaryView[]> {
    const result = await this.readDb.query(
      `
      SELECT * FROM order_summary_view
      WHERE client_id = $1
        AND ($2::date IS NULL OR order_date >= $2)
        AND ($3::date IS NULL OR order_date <= $3)
        AND ($4::varchar IS NULL OR status = $4)
      ORDER BY created_at DESC
      LIMIT $5 OFFSET $6
    `,
      [
        clientId,
        filters.fromDate,
        filters.toDate,
        filters.status,
        filters.limit || 100,
        filters.offset || 0,
      ],
    );

    return result.rows;
  }
}

// Projection: Update read model from events
class OrderProjection {
  constructor(private readDb: Database) {}

  async onOrderPlaced(event: OrderPlacedEvent): Promise<void> {
    await this.readDb.query(
      `
      INSERT INTO order_summary_view (
        order_id, client_id, instrument_id, side, quantity,
        price, order_type, status, filled_quantity, average_fill_price,
        order_date, created_at, updated_at
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
    `,
      [
        event.payload.orderId,
        event.payload.clientId,
        event.payload.instrumentId,
        event.payload.side,
        event.payload.quantity,
        event.payload.price,
        event.payload.orderType,
        "PENDING",
        0,
        0,
        new Date(event.timestamp).toISOString().split("T")[0],
        event.timestamp,
        event.timestamp,
      ],
    );
  }

  async onOrderFilled(event: OrderFilledEvent): Promise<void> {
    await this.readDb.query(
      `
      UPDATE order_summary_view
      SET 
        filled_quantity = filled_quantity + $2,
        average_fill_price = (
          (average_fill_price * filled_quantity + $3 * $2) / 
          (filled_quantity + $2)
        ),
        status = $4,
        updated_at = $5
      WHERE order_id = $1
    `,
      [
        event.payload.orderId,
        event.payload.fillQuantity,
        event.payload.fillPrice,
        event.payload.newStatus,
        event.timestamp,
      ],
    );
  }

  async onOrderCancelled(event: OrderCancelledEvent): Promise<void> {
    await this.readDb.query(
      `
      UPDATE order_summary_view
      SET status = 'CANCELLED', updated_at = $2
      WHERE order_id = $1
    `,
      [event.payload.orderId, event.timestamp],
    );
  }
}
```

### 3.4 Event Processing Patterns

#### 3.4.1 Event Choreography

Services react to events independently without central coordination.

**Example: Order Fill Processing**

```
OrderService ──→ OrderFilledEvent ──→ Kafka
                       ↓
        ┌──────────────┼──────────────┐
        ↓              ↓              ↓
PositionService  MarginService  NotificationService
        ↓              ↓              ↓
UpdatePosition  ReleaseMargin  SendNotification
```

#### 3.4.2 Event Orchestration

Central orchestrator coordinates event processing.

**Example: Corporate Action Processing**

```typescript
class DividendProcessingOrchestrator {
  async processDividend(dividendId: string): Promise<void> {
    const dividend = await this.dividendService.getById(dividendId);

    // Step 1: Validate dividend
    await this.dividendService.validate(dividend);

    // Step 2: Calculate entitlements
    const entitlements =
      await this.entitlementService.calculateEntitlements(dividend);

    // Step 3: Process payments
    for (const entitlement of entitlements) {
      await this.paymentService.processPayment(entitlement);
    }

    // Step 4: Update shareholder accounts
    await this.shareholderService.updateAccounts(entitlements);

    // Step 5: Generate reports
    await this.reportingService.generateDividendReport(dividend);

    // Step 6: Submit regulatory filing
    await this.regulatoryService.submitFiling(dividend);
  }
}
```

### 3.5 Event Schema Registry

**Purpose**: Centralized management of event schemas for versioning and compatibility.

**Schema Registry**: Confluent Schema Registry with Avro schemas

**Example Schema**:

```json
{
  "type": "record",
  "name": "OrderPlacedEvent",
  "namespace": "com.siddhanta.trading.events",
  "fields": [
    { "name": "eventId", "type": "string" },
    { "name": "eventType", "type": "string", "default": "ORDER_PLACED" },
    { "name": "aggregateId", "type": "string" },
    { "name": "aggregateType", "type": "string", "default": "Order" },
    { "name": "version", "type": "int" },
    { "name": "timestamp", "type": "long", "logicalType": "timestamp-millis" },
    { "name": "correlationId", "type": "string" },
    { "name": "causationId", "type": "string" },
    { "name": "userId", "type": "string" },
    {
      "name": "payload",
      "type": {
        "type": "record",
        "name": "OrderPlacedPayload",
        "fields": [
          { "name": "orderId", "type": "string" },
          { "name": "clientId", "type": "string" },
          { "name": "instrumentId", "type": "string" },
          {
            "name": "side",
            "type": {
              "type": "enum",
              "name": "OrderSide",
              "symbols": ["BUY", "SELL"]
            }
          },
          { "name": "quantity", "type": "double" },
          { "name": "price", "type": ["null", "double"], "default": null },
          {
            "name": "orderType",
            "type": {
              "type": "enum",
              "name": "OrderType",
              "symbols": ["MARKET", "LIMIT", "STOP", "STOP_LIMIT"]
            }
          },
          {
            "name": "timeInForce",
            "type": {
              "type": "enum",
              "name": "TimeInForce",
              "symbols": ["DAY", "GTC", "IOC", "FOK"]
            }
          },
          {
            "name": "exchangeOrderId",
            "type": ["null", "string"],
            "default": null
          }
        ]
      }
    }
  ]
}
```

### 3.6 Event Versioning Strategy

**Backward Compatibility**: New versions must be able to read old events

**Strategies**:

1. **Additive Changes**: Add optional fields with defaults
2. **Event Upcasting**: Transform old events to new format on read
3. **Multiple Event Versions**: Support multiple versions simultaneously

**Example: Event Upcasting**

```typescript
class EventUpcaster {
  upcast(event: DomainEvent): DomainEvent {
    const upcasters = this.getUpcastersForEvent(event.eventType);

    let upcastedEvent = event;
    for (const upcaster of upcasters) {
      if (upcaster.canUpcast(upcastedEvent)) {
        upcastedEvent = upcaster.upcast(upcastedEvent);
      }
    }

    return upcastedEvent;
  }
}

class OrderPlacedEventV1ToV2Upcaster implements EventUpcaster {
  canUpcast(event: DomainEvent): boolean {
    return (
      event.eventType === "ORDER_PLACED" &&
      event.metadata.schemaVersion === "1.0"
    );
  }

  upcast(event: DomainEvent): DomainEvent {
    // V1 didn't have timeInForce, default to DAY
    return {
      ...event,
      payload: {
        ...event.payload,
        timeInForce: "DAY",
      },
      metadata: {
        ...event.metadata,
        schemaVersion: "2.0",
        upcastedFrom: "1.0",
      },
    };
  }
}
```

### 3.7 Idempotency & Exactly-Once Processing

**Challenge**: Ensure events are processed exactly once despite retries and failures.

**Solution**: Idempotency keys and deduplication

```typescript
class IdempotentEventHandler {
  constructor(
    private processedEventsStore: ProcessedEventsStore,
    private handler: EventHandler,
  ) {}

  async handle(event: DomainEvent): Promise<void> {
    // Check if already processed
    const isProcessed = await this.processedEventsStore.isProcessed(
      event.eventId,
    );

    if (isProcessed) {
      logger.info("Event already processed, skipping", {
        eventId: event.eventId,
        eventType: event.eventType,
      });
      return;
    }

    try {
      // Process event
      await this.handler.handle(event);

      // Mark as processed
      await this.processedEventsStore.markProcessed(event.eventId);
    } catch (error) {
      logger.error("Event processing failed", {
        eventId: event.eventId,
        eventType: event.eventType,
        error: error.message,
      });
      throw error;
    }
  }
}

// Processed Events Store
class RedisProcessedEventsStore implements ProcessedEventsStore {
  private readonly TTL = 7 * 24 * 60 * 60; // 7 days

  async isProcessed(eventId: string): Promise<boolean> {
    const result = await this.redis.get(`processed:${eventId}`);
    return result !== null;
  }

  async markProcessed(eventId: string): Promise<void> {
    await this.redis.setex(`processed:${eventId}`, this.TTL, "1");
  }
}
```

---

## Summary

This document (Part 1, Sections 1-3) covers:

1. **Executive Summary**: Vision, objectives, capabilities, technology stack, architectural principles, and key SLAs for Project Siddhanta.

2. **Layered Architecture**: Seven-layer architecture including Presentation, API Gateway, Application Services, Domain Services, Integration & Messaging, Data Persistence, and Infrastructure layers with detailed component descriptions and implementation examples.

3. **Event-Driven & CQRS Architecture**: Event sourcing implementation, CQRS pattern with separate read/write models, event processing patterns, schema registry, versioning strategies, and idempotency handling.

**Next Steps**: Continue with Part 1, Sections 4-5 (Configuration Resolution & Plugin Runtime Architecture).
