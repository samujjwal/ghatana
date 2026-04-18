# Data-Cloud Capability Map

**Document ID:** DC-CAPABILITY-001  
**Version:** 1.1  
**Date:** 2026-04-13  
**Evidence Base:** Current generated Data Cloud documentation set, requirements traceability, and readiness audit

---

## Executive Summary

Data-Cloud provides **32 major capabilities** organized into **8 capability areas**. The documentation shows **broad implementation coverage**, but the product's validation maturity is uneven. This capability map should be read as a **scope and implementation inventory**, not as a blanket readiness declaration.

**Key Findings:**

- **Core Data Management**: Broadly implemented with multi-backend support
- **AI/ML Integration**: Present across the platform surface, but not equally validated across all workflows
- **Real-time Features**: WebSocket and SSE capabilities are documented
- **Governance & Security**: Strong documentation coverage, though some readiness claims require proof reconciliation
- **Developer Experience**: Multiple APIs and SDK-oriented surfaces are documented

---

## Capability Areas Overview

### 1. Core Data Management (Capabilities: 8)

**Implementation Status:** ✅ **Broadly Implemented**

### 2. Event Streaming & Processing (Capabilities: 4)

**Implementation Status:** ✅ **Broadly Implemented**

### 3. Analytics & Intelligence (Capabilities: 6)

**Implementation Status:** ✅ **Broadly Implemented**

### 4. AI/ML Platform (Capabilities: 5)

**Implementation Status:** ✅ **Broadly Implemented**

### 5. Governance & Security (Capabilities: 3)

**Implementation Status:** ✅ **Broadly Implemented**

### 6. Plugin Ecosystem (Capabilities: 2)

**Implementation Status:** ✅ **Broadly Implemented**

### 7. Real-time & Notifications (Capabilities: 2)

**Implementation Status:** ✅ **Broadly Implemented**

### 8. Operations & Deployment (Capabilities: 2)

**Implementation Status:** ✅ **Broadly Implemented**

### Status Interpretation

- `Complete` in the tables below means the capability is represented in the current documented implementation surface.
- `Production Ready` in the table rows is a **historical generated label**, not a validated certification of performance, security, isolation, or external deployment readiness.
- It does **not** mean every aspect is equally tested, load-validated, security-validated, or ready for external production claims.
- Operational proof points should be read from the test inventory, caveat documents, audit report, and readiness scorecard.
- UI discovery truth is narrower than the broad capability inventory: the primary user shell currently centers on `/`, `/data`, `/pipelines`, and `/query`, while alerts now operate as an operator-facing live surface and settings remains an admin-only boundary surface.

---

## Detailed Capability Mapping

### 1. Core Data Management

| Capability                | Sub-Capabilities                                                                             | Implementation  | Evidence Location                                                                                                  | Readiness Label         |
| ------------------------- | -------------------------------------------------------------------------------------------- | --------------- | ------------------------------------------------------------------------------------------------------------------ | ----------------------- |
| **Entity Storage**        | • CRUD operations<br>• Multi-tenant isolation<br>• Schema validation<br>• Versioning         | ✅ **Complete** | `DataCloud.java`, `EntityStore` SPI<br>PostgreSQL JSONB implementation<br>EntityController endpoints               | ✅ **Production Ready** |
| **Query Engine**          | • SQL queries<br>• JSON queries<br>• Full-text search<br>• Aggregations                      | ✅ **Complete** | ClickHouse integration<br>OpenSearch connector<br>AnalyticsHandler endpoints<br>SQL workspace UI                   | ✅ **Production Ready** |
| **Data Modeling**         | • Schema management<br>• Field types<br>• Relationships<br>• Constraints                     | ✅ **Complete** | Schema evolution agents<br>Collection metadata<br>Entity relations table<br>Schema validation service              | ✅ **Production Ready** |
| **Multi-Backend Storage** | • PostgreSQL<br>• ClickHouse<br>• Redis<br>• RocksDB<br>• S3/Glacier<br>• Ceph               | ✅ **Complete** | 9 storage connectors in platform-launcher<br>StorageProvider SPI implementations<br>Configuration-driven selection | ✅ **Production Ready** |
| **Data Lifecycle**        | • Hot/warm/cold tiers<br>• TTL policies<br>• Automatic archival<br>• Data retention          | ✅ **Complete** | Tiered storage implementation<br>TTL enforcement<br>ColdTierArchivePlugin<br>Retention policy service              | ✅ **Production Ready** |
| **Bulk Operations**       | • Batch import<br>• Bulk export<br>• Bulk delete<br>• Bulk updates                           | ✅ **Complete** | BatchResult SPI<br>Bulk operation endpoints<br>Import/export workflows<br>Performance optimizations                | ✅ **Production Ready** |
| **Data Quality**          | • Validation rules<br>• Anomaly detection<br>• Data profiling<br>• Quality metrics           | ⚠️ **Partial** | Quality service<br>Anomaly detection API<br>Collection-centric quality views in `/data`                            | ⚠️ **Shipped, not exhaustive** |
| **Data Lineage**          | • Dependency tracking<br>• Impact analysis<br>• Lineage visualization<br>• Data flow mapping | ⚠️ **Partial** | Lineage service<br>Impact analysis API<br>`/data?view=lineage` preview rather than a standalone explorer           | ⚠️ **Preview / expansion** |

### 2. Event Streaming & Processing

| Capability           | Sub-Capabilities                                                                     | Implementation  | Evidence Location                                                                                      | Status                  |
| -------------------- | ------------------------------------------------------------------------------------ | --------------- | ------------------------------------------------------------------------------------------------------ | ----------------------- |
| **Event Sourcing**   | • Append-only log<br>• Immutable events<br>• Event versioning<br>• Replay capability | ✅ **Complete** | EventLogStore SPI<br>Kafka implementation<br>V001\_\_create_events_table.sql<br>Event replay endpoints | ✅ **Production Ready** |
| **Event Streaming**  | • Real-time publishing<br>• Topic management<br>• Partitioning<br>• Consumer groups  | ✅ **Complete** | KafkaStreamingPlugin<br>Event publisher service<br>Event exploration UI<br>Operator diagnostics         | ✅ **Production Ready** |
| **Event Processing** | • Filtering<br>• Transformation<br>• Routing<br>• Aggregation                        | ✅ **Complete** | Event processing pipeline<br>Stream processing engine<br>Event routing rules<br>Aggregation functions  | ✅ **Production Ready** |
| **Event Store**      | • Persistence<br>• Indexing<br>• Querying<br>• Retention                             | ✅ **Complete** | EventStore implementation<br>Event indexing<br>Time-range queries<br>Retention policies                | ✅ **Production Ready** |

### 3. Analytics & Intelligence

| Capability                | Sub-Capabilities                                                                 | Implementation  | Evidence Location                                                                          | Status                  |
| ------------------------- | -------------------------------------------------------------------------------- | --------------- | ------------------------------------------------------------------------------------------ | ----------------------- |
| **Ad-hoc Analytics**      | • SQL queries<br>• Natural language<br>• Visual query builder<br>• Query caching | ⚠️ **Partial** | AnalyticsHandler<br>Canonical `/query` workspace<br>Natural language suggestion path<br>Query cache service | ⚠️ **Shipped with UX gaps** |
| **Reporting**             | • Report generation<br>• Scheduled reports<br>• Distribution<br>• Templates      | ⚠️ **Partial** | Backend reporting capabilities are documented, but no primary end-user reporting workflow is promoted in the current UI | ⚠️ **Documented more broadly than surfaced** |
| **Interactive Analytics** | • Operator insight views<br>• Interactive charts<br>• Summary widgets<br>• Sharing gaps | ⚠️ **Partial** | Operator insights surface<br>Chart components<br>Historical dashboard aliases retained only for compatibility | ⚠️ **Operator-facing, not a full dashboard product** |
| **Data Visualization**    | • Charts<br>• Graphs<br>• Maps<br>• Custom visualizations                        | ✅ **Complete** | Visualization library<br>Chart components<br>Map integration<br>Custom viz framework       | ✅ **Production Ready** |
| **Business Intelligence** | • OLAP queries<br>• Cubes<br>• Dimensions<br>• Measures                          | ✅ **Complete** | OLAP engine<br>Cube definitions<br>Dimension management<br>Measure calculations            | ✅ **Production Ready** |
| **Predictive Analytics**  | • Forecasting<br>• Trend analysis<br>• Anomaly detection<br>• Recommendations    | ✅ **Complete** | Prediction service<br>Trend analysis<br>Anomaly detection<br>Recommendation engine         | ✅ **Production Ready** |

### 4. AI/ML Platform

| Capability              | Sub-Capabilities                                                                                    | Implementation  | Evidence Location                                                                        | Status                  |
| ----------------------- | --------------------------------------------------------------------------------------------------- | --------------- | ---------------------------------------------------------------------------------------- | ----------------------- |
| **Feature Store**       | • Feature storage<br>• Feature retrieval<br>• Feature versioning<br>• Feature lineage               | ✅ **Complete** | FeatureStoreService<br>Feature cache<br>Feature versioning<br>Feature lineage UI         | ✅ **Production Ready** |
| **Model Registry**      | • Model storage<br>• Model versioning<br>• Model metadata<br>• Model deployment                     | ✅ **Complete** | ModelRegistryService<br>Model versioning<br>Model metadata<br>Model deployment API       | ✅ **Production Ready** |
| **ML Pipelines**        | • Pipeline orchestration<br>• Pipeline versioning<br>• Pipeline monitoring<br>• Pipeline scheduling | ✅ **Complete** | PipelineOrchestrator<br>Pipeline versioning<br>Pipeline monitoring<br>Pipeline scheduler | ✅ **Production Ready** |
| **Experiment Tracking** | • Experiment logging<br>• Parameter tracking<br>• Metric tracking<br>• Result comparison            | ✅ **Complete** | Experiment service<br>Parameter tracking<br>Metric tracking<br>Result comparison UI      | ✅ **Production Ready** |
| **Model Serving**       | • Online inference<br>• Batch inference<br>• A/B testing<br>• Model monitoring                      | ✅ **Complete** | Model serving API<br>Batch inference<br>A/B testing framework<br>Model monitoring        | ✅ **Production Ready** |

### 5. Governance & Security

| Capability                 | Sub-Capabilities                                                                       | Implementation  | Evidence Location                                                                  | Status                  |
| -------------------------- | -------------------------------------------------------------------------------------- | --------------- | ---------------------------------------------------------------------------------- | ----------------------- |
| **Multi-tenant Isolation** | • Tenant separation<br>• Resource quotas<br>• Network isolation<br>• Data isolation    | ✅ **Complete** | TenantContext<br>Resource quotas<br>Network policies<br>Data isolation enforcement | ✅ **Production Ready** |
| **Access Control**         | • Authentication<br>• Authorization<br>• Role-based access<br>• Attribute-based access | ✅ **Complete** | Security framework<br>Role management<br>Permission system<br>Policy enforcement   | ✅ **Production Ready** |
| **Audit & Compliance**     | • Audit logging<br>• Data lineage<br>• Privacy controls<br>• Compliance reporting      | ✅ **Complete** | Audit service<br>Data lineage<br>Privacy controls<br>Compliance reports            | ✅ **Production Ready** |

### 6. Plugin Ecosystem

| Capability           | Sub-Capabilities                                                                         | Implementation  | Evidence Location                                                                | Status                  |
| -------------------- | ---------------------------------------------------------------------------------------- | --------------- | -------------------------------------------------------------------------------- | ----------------------- |
| **Plugin Framework** | • Plugin discovery<br>• Plugin lifecycle<br>• Plugin communication<br>• Plugin isolation | ✅ **Complete** | Plugin framework<br>Plugin discovery<br>Plugin lifecycle<br>Plugin communication | ✅ **Production Ready** |
| **Plugin Store**     | • Bundled plugin inventory<br>• Toggle and upgrade intent<br>• Distribution boundaries<br>• Compatibility signals | ⚠️ **Partial** | Bundled plugin inventory<br>Enable/disable routes<br>Upgrade-intent route<br>Boundary-guarded marketplace helpers | ⚠️ **Operator-facing, not a full marketplace** |

### 7. Real-time & Notifications

| Capability             | Sub-Capabilities                                                                                     | Implementation  | Evidence Location                                                                   | Status                  |
| ---------------------- | ---------------------------------------------------------------------------------------------------- | --------------- | ----------------------------------------------------------------------------------- | ----------------------- |
| **WebSocket API**      | • Real-time updates<br>• Bidirectional communication<br>• Connection management<br>• Message routing | ✅ **Complete** | WebSocket endpoint<br>Real-time updates<br>Connection management<br>Message routing | ✅ **Runtime-backed** |
| **Server-Sent Events** | • Event streaming<br>• Automatic reconnection<br>• Event filtering<br>• Event aggregation            | ✅ **Complete** | SSE implementation<br>Auto-reconnection<br>Event filtering<br>Event aggregation     | ✅ **Runtime-backed** |

### 8. Operations & Deployment

| Capability                     | Sub-Capabilities                                                              | Implementation  | Evidence Location                                                                 | Status                  |
| ------------------------------ | ----------------------------------------------------------------------------- | --------------- | --------------------------------------------------------------------------------- | ----------------------- |
| **Container Deployment**       | • Docker images<br>• Kubernetes manifests<br>• Helm charts<br>• Health checks | ✅ **Complete** | Dockerfile<br>K8s manifests<br>Helm charts<br>Health checks                       | ✅ **Production Ready** |
| **Monitoring & Observability** | • Metrics collection<br>• Distributed tracing<br>• Logging<br>• Operator diagnostics<br>• Live operator alert triage | ⚠️ **Partial** | Metrics collection<br>Distributed tracing<br>Logging framework<br>Insights operator diagnostics<br>Launcher-backed Alerts page | ⚠️ **Metrics, diagnostics, and alert lifecycle are now live; remaining work is alert quality instrumentation and calmer triage UX** |

---

## Implementation Quality Assessment

### High-Quality Implementations

**Entity Storage & Query Engine**

- **Evidence**: Complete CRUD operations, multi-backend support, comprehensive indexing
- **Quality**: Production-ready with proven storage backends
- **Risk**: Low - well-tested and documented

**Event Streaming**

- **Evidence**: Kafka integration, event sourcing, real-time processing
- **Quality**: Enterprise-grade with proper partitioning and retention
- **Risk**: Low - standard Kafka patterns

**AI/ML Integration**

- **Evidence**: Feature store, model registry, ML pipelines
- **Quality**: Deeply embedded, not bolted on
- **Risk**: Medium - depends on ML workload patterns

### Medium-Quality Implementations

**Real-time Features**

- **Evidence**: WebSocket and SSE endpoints
- **Quality**: Functional but needs performance testing
- **Risk**: Medium - scalability under load unknown

**Plugin Ecosystem**

- **Evidence**: Plugin framework and registry
- **Quality**: Framework exists but limited plugin examples
- **Risk**: Medium - depends on ecosystem adoption

### Areas for Improvement

**Performance Optimization**

- Need comprehensive performance testing
- Optimize query patterns for large datasets
- Implement caching strategies

**Documentation**

- Add more usage examples
- Create troubleshooting guides
- Document performance characteristics

---

## Risk & Gap Analysis

### Critical Gaps

**Validation gaps remain in critical areas**:

- Performance characteristics under production load are not yet fully validated.
- Security hardening and tenant-isolation wording are not fully reconciled across the docs.
- Advanced areas such as plugins, ML-support, and some analytics paths have uneven validation coverage.

### Medium Risks

**Performance at Scale**

- **Risk**: Unknown performance characteristics under production load
- **Impact**: Performance issues in production
- **Mitigation**: Load testing and performance optimization

**Plugin Ecosystem Adoption**

- **Risk**: Limited third-party plugin development
- **Impact**: Reduced extensibility
- **Mitigation**: Core plugin development and developer experience

### Low Risks

**Technology Evolution**

- **Risk**: Technology stack evolution (ActiveJ, React 19)
- **Impact**: Future migration requirements
- **Mitigation**: Regular technology assessment

---

## Capability Dependencies

### Core Dependencies

```
Entity Storage ← Query Engine ← Analytics ← AI/ML Platform
     ↓              ↓              ↓           ↓
Multi-tenant ← Access Control ← Audit ← Plugin Framework
     ↓              ↓              ↓           ↓
Event Streaming ← Real-time ← Monitoring ← Deployment
```

### External Dependencies

- **AEP**: Agentic orchestration (consumer)
- **Platform Libraries**: Shared infrastructure
- **Infrastructure**: Kafka, PostgreSQL, ClickHouse, Redis

---

## Evolution Roadmap

### Phase 1: Production Optimization (Next 3 months)

- Performance testing and optimization
- Plugin ecosystem development
- Developer experience improvements

### Phase 2: Intelligence Enhancement (3-6 months)

- Advanced AI/ML capabilities
- Enhanced natural language processing
- Predictive analytics improvements

### Phase 3: Ecosystem Expansion (6-12 months)

- Third-party plugin development
- Integration partnerships
- Community building

---

_This capability map represents the documented capability surface as of April 13, 2026. It should be maintained together with the requirements, test inventory, audit report, and readiness scorecard so scope claims and readiness claims stay aligned._
