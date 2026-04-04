# AEP Product Vision Document

**Product:** Agentic Event Processor (AEP)  
**Status:** Active Development  
**Analysis Date:** 2026-04-04  
**Evidence Base:** Code, tests, docs, UI, configuration, runtime structure

## Executive Summary

AEP is the **event-driven agent orchestration runtime** for the Ghatana platform. It serves as the central nervous system for processing events, executing agent pipelines, and managing the lifecycle of agentic workflows across multiple products. The product demonstrates strong architectural ambition but suffers from fragmentation and drift between its various components.

**Observed in code**: AEP has a comprehensive Java backend with ActiveJ framework, a React-based UI, and extensive documentation describing its capabilities.

**Inferred**: The product aims to be the platform-wide event intake and routing runtime, but implementation reality shows multiple overlapping subsystems that need consolidation.

## Problem Statement

Organizations building sophisticated agent-based systems need a centralized, reliable runtime that can:

- Process high-volume event streams with low latency
- Execute complex agent pipelines with deterministic and probabilistic behaviors
- Provide multi-tenant isolation and governance
- Support human-in-the-loop workflows and learning loops
- Maintain observability and compliance across all operations

**Gap**: Current implementation shows fragmentation between server, gateway, orchestrator, and registry components that creates operational complexity.

## Product Vision

AEP will become the **agentic event operating system** for Ghatana - a cleanly composable platform that provides:

1. **Event Cloud as the data plane** - Four-tier storage (Memory, Redis, Postgres, Iceberg/S3)
2. **AEP Server as the control-plane API** - Unified HTTP/gRPC surface
3. **Orchestrator as the execution plane** - Pipeline and workflow execution
4. **Registry as the control-plane source of truth** - Agent and pipeline metadata
5. **Learning loop as first-class system** - HITL, consolidation, policy promotion
6. **UI as operator cockpit** - Outcome-oriented navigation (Operate/Build/Learn/Govern/Catalog)

**Observed in docs**: This vision aligns with ADR-001 (typed agents) and ADR-003 (four-tier event cloud).

## Target Users and Personas

### Primary Operators
- **Platform Engineers** - Deploy, monitor, and maintain AEP infrastructure
- **DevOps Engineers** - Manage pipelines, deployments, and scaling
- **System Integrators** - Connect external systems via APIs and connectors

### Secondary Users  
- **Agent Developers** - Create and register new agent operators
- **Data Scientists** - Design patterns and analyze event flows
- **Compliance Officers** - Review audit trails and governance reports

### Tertiary Users
- **Product Managers** - Monitor agent performance and business metrics
- **Support Engineers** - Troubleshoot failed pipelines and agent issues

**Observed in UI**: The React UI provides distinct interfaces for each persona group with appropriate capability exposure.

## Value Proposition

### For Platform Teams
- **Unified event processing** - Single runtime for all product event needs
- **Multi-tenant isolation** - Secure separation between customers/contexts
- **Scalable execution** - Horizontal scaling with ActiveJ event loops
- **Observability** - Built-in metrics, tracing, and health monitoring

### For Agent Developers  
- **Extensible operator framework** - Plugin architecture for custom agents
- **Pattern studio** - Visual tools for event pattern design
- **Testing infrastructure** - Comprehensive test harnesses and validation
- **Version management** - Pipeline and agent versioning with rollback

### For Business Users
- **Human-in-the-loop** - Review and approve low-confidence agent decisions
- **Learning system** - Continuous improvement from episodic memory
- **Compliance reporting** - Automated audit trails and governance reports
- **Real-time monitoring** - Live dashboards for agent performance

**Observed in code**: Server modules provide distinct capabilities for each value proposition area.

## Product Scope

### In Scope (Implemented)
- **Event Processing Core** - AepEngine with ActiveJ Promise-based async
- **Pipeline Execution** - YAML-configurable operator chains
- **Pattern Management** - Event pattern registration and detection
- **Agent Registry** - Central catalog of available operators
- **HTTP/gRPC APIs** - RESTful and streaming interfaces
- **Multi-tenant Support** - Tenant-scoped execution and data
- **Analytics Engine** - Real-time metrics and forecasting
- **Compliance Framework** - SOC2 controls and audit logging
- **Learning Loop** - HITL queue and consolidation scheduler
- **React UI** - Operator cockpit with outcome-oriented navigation

### Partially Implemented
- **Event Cloud Integration** - Bridge exists but not fully wired
- **Operator Discovery** - ServiceLoader mechanism but limited catalog
- **Disaster Recovery** - Backup service but not automated
- **Advanced Analytics** - Forecasting engine but naive implementation

### Out of Scope
- **Event Storage** - Delegated to Data-Cloud product
- **Agent Implementation** - Owned by product teams
- **Identity Management** - Delegated to platform identity services
- **Infrastructure Provisioning** - Delegated to platform ops

**Observed in build.gradle.kts**: Dependencies show clear boundaries for in-scope vs delegated capabilities.

## Non-Goals

AEP explicitly does NOT:
- Store events long-term (delegates to Data-Cloud)
- Implement specific agent logic (provides runtime only)
- Replace general-purpose stream processing (focused on agents)
- Serve as a general-purpose workflow engine (agent-centric)
- Provide low-level message queue infrastructure

**Observed in OWNER.md**: Clear boundary statements about what AEP owns vs delegates.

## Maturity Assessment

### Architecture Maturity: **Moderate (6/10)**
- Strong conceptual foundation with typed agents and event cloud
- Clean separation of concerns in core interfaces
- Fragmentation between server, gateway, orchestrator components
- Some drift between documented vs implemented architecture

### Implementation Maturity: **Good (7/10)**
- Comprehensive Java backend with proper ActiveJ patterns
- Rich React UI with modern stack (React 19, TypeScript, Jotai)
- Extensive test coverage (32+ test files)
- Production-ready features (metrics, compliance, learning)

### Operational Maturity: **Moderate (5/10)**
- Good observability and health monitoring
- Compliance framework present but not fully validated
- Deployment manifests exist (K8s, Helm)
- Limited operational runbooks and procedures

### Product Maturity: **Good (7/10)**
- Clear product vision and user journeys
- Comprehensive UI covering all major capabilities
- Good documentation but some drift from reality
- Strong feature completeness for core use cases

**Risk**: Fragmentation creates operational complexity that may impact reliability and maintainability.

## Strategic Risks

### High Risk
1. **Architectural Fragmentation** - Multiple overlapping subsystems create confusion
2. **Event Cloud Dependency** - Integration with Data-Cloud not fully validated
3. **Learning Loop Complexity** - HITL and consolidation may be over-engineered

### Medium Risk  
1. **Test Coverage Gaps** - UI testing limited, integration tests need expansion
2. **Performance Scaling** - ActiveJ event loops need production validation
3. **Operator Ecosystem** - Limited third-party operator adoption

### Low Risk
1. **Technology Stack** - Java 21 and ActiveJ are solid choices
2. **UI Framework** - React with modern patterns is well-maintained
3. **Compliance** - SOC2 framework provides good foundation

## Known Unknowns

### Technical Unknowns
- **Production Scale Performance** - Real-world throughput and latency characteristics
- **Multi-tenant Limits** - Actual scalability of tenant isolation mechanisms  
- **Learning Loop Effectiveness** - Real-world impact of episodic memory consolidation

### Business Unknowns
- **Operator Adoption** - Will third parties actually build and register operators?
- **Market Fit** - Does the event-driven agent model match customer needs?
- **Competitive Position** - How does AEP compare to commercial alternatives?

### Operational Unknowns
- **Deployment Complexity** - Real-world operational overhead of multi-component system
- **Maintenance Burden** - Ongoing effort required for learning loop and compliance
- **Upgrade Path** - Complexity of upgrading pipelines and agents between versions

## Success Metrics

### Technical Metrics
- **Event Processing Throughput** - >10,000 events/second per instance
- **Pipeline Latency** - <100ms p95 for simple pipelines
- **System Availability** - >99.9% uptime with graceful degradation
- **Test Coverage** - >80% line coverage for critical paths

### Product Metrics  
- **Operator Registry Growth** - >50 registered operators in first year
- **Pipeline Adoption** - >100 active pipelines across tenants
- **HITL Efficiency** - <5% of decisions require human review
- **Learning Loop Impact** - >20% reduction in low-confidence decisions over time

### Business Metrics
- **Platform Adoption** - AEP used by >3 major products
- **Developer Satisfaction** - >4.0/5.0 satisfaction score
- **Operational Efficiency** - <2 hours/week maintenance overhead
- **Compliance Pass Rate** - 100% automated compliance validation

## Evidence Basis

This vision document is based on analysis of:

**Code Evidence:**
- Aep.java (1,393 lines) - Core factory and configuration
- AepEngine.java (446 lines) - Primary interface with full type definitions  
- AepLauncher.java (301 lines) - Standalone deployment entry point
- 33 server module Java files implementing HTTP/gRPC services
- 32 test files covering major functionality areas

**UI Evidence:**
- App.tsx (172 lines) - Complete routing with outcome-oriented navigation
- PipelineBuilderPage.tsx (247 lines) - Full-featured pipeline editor
- 19 page components covering all major user journeys
- Modern React stack with TypeScript, Jotai, TanStack Query

**Configuration Evidence:**
- server/build.gradle.kts (184 lines) - Comprehensive dependency management
- ui/package.json (50 lines) - Modern frontend tooling
- Dockerfile and K8s manifests for deployment

**Documentation Evidence:**
- OWNER.md, README.md with clear product positioning
- 19 analysis documents with detailed architectural thinking
- ADRs defining typed agents and event cloud architecture

**Gap**: Limited evidence of actual production usage or performance characteristics.

## Conclusion

AEP has a strong foundation and clear vision but needs architectural consolidation to reach production excellence. The product demonstrates good understanding of the event-driven agent problem space and provides comprehensive tooling for operators, developers, and business users.

**Recommendation**: Focus on reducing fragmentation between server, gateway, and orchestrator components while maintaining the rich feature set. The learning loop and compliance capabilities provide strong differentiation but should be validated through production usage.
