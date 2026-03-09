# Virtual-Org Master Architecture & Framework Guide

**Version:** 2.0.0  
**Last Updated:** November 26, 2025  
**Status:** Living Document  
**Classification:** Strategic Architecture Document

---

## Executive Summary

Virtual-Org is the **canonical pluggable framework for modeling, orchestrating, and simulating AI-powered virtual organizations** on the Ghatana platform. It provides domain-agnostic abstractions that enable any organization type—software development, healthcare, manufacturing, finance, retail, or custom domains—to be modeled, automated, and optimized through AI-driven agents.

### Strategic Value Proposition

| Dimension | Value |
|-----------|-------|
| **Universality** | Model ANY organization type with consistent abstractions |
| **AI-Native** | First-class LLM agent integration, not bolted-on automation |
| **Event-Driven** | Built on Agentic Event Processor (AEP) from ground up |
| **Plugin-First** | All components are replaceable, extensible, customizable |
| **Enterprise-Ready** | Security, compliance, observability, HITL as first-class citizens |
| **Zero Lock-in** | Clear interfaces enabling alternative implementations |

---

## Table of Contents

1. [Vision & Strategic Direction](#1-vision--strategic-direction)
2. [Core Requirements](#2-core-requirements)
3. [Architecture Overview](#3-architecture-overview)
4. [Framework Abstractions](#4-framework-abstractions)
5. [Plugin Architecture](#5-plugin-architecture)
6. [Integration Points](#6-integration-points)
7. [Current Implementation Status](#7-current-implementation-status)
8. [Planned Capabilities](#8-planned-capabilities)
9. [Extension Guidelines](#9-extension-guidelines)
10. [Appendices](#10-appendices)

---

## 1. Vision & Strategic Direction

### 1.1 Vision Statement

> **Virtual-Org is the operating system for AI-assisted organizations**, enabling any organization to be digitally modeled, simulated, and progressively automated while maintaining human oversight, safety, and governance.

### 1.2 Strategic Goals

| Goal | Description | Priority |
|------|-------------|----------|
| **Framework Purity** | Domain-agnostic building blocks for ANY organization | CRITICAL |
| **Zero Vendor Lock-in** | All abstractions have clear interfaces | HIGH |
| **Event-Native** | Built on event-driven architecture from ground up | CRITICAL |
| **AI-First** | Native LLM-powered agent support | HIGH |
| **Enterprise Ready** | Security, compliance, observability first-class | HIGH |
| **Developer Experience** | Clear APIs, comprehensive docs, easy onboarding | MEDIUM |

### 1.3 What Virtual-Org IS vs IS NOT

#### ✅ Virtual-Org IS:
- A **framework layer** providing reusable organizational abstractions
- A **plugin host** enabling domain-specific implementations
- An **event-driven orchestrator** integrated with AEP
- A **multi-agent coordination system** with HITL controls
- A **simulation engine** for organizational experimentation

#### ❌ Virtual-Org IS NOT:
- A standalone application (it's a framework)
- A replacement for AEP (it builds on AEP)
- Domain-specific (Software-Org, Healthcare-Org are plugins)
- A simple automation tool (it models complex organizations)
- Opinionated about specific organizational structures

### 1.4 Target Use Cases

| Domain | Example Plugin | Key Agents | Unique Features |
|--------|---------------|------------|-----------------|
| **Software Development** | Software-Org | CTO, Engineer, QA, DevOps | SDLC workflows, code review, CI/CD |
| **Healthcare** | Healthcare-Org | CMO, Doctor, Nurse, Admin | Patient flows, compliance, scheduling |
| **Manufacturing** | Manufacturing-Org | Plant Manager, Supervisor, Operator | Production lines, quality control |
| **Finance** | Finance-Org | CFO, Analyst, Trader, Risk | Trading, risk management, compliance |
| **Retail** | Retail-Org | Store Manager, Buyer, Staff | Inventory, supply chain, customer |
| **Custom** | [Your]-Org | Custom Agents | Custom workflows |

---

## 2. Core Requirements

### 2.1 Functional Requirements

#### Organization & Department Model
| Requirement | Description | Status |
|-------------|-------------|--------|
| **F-ORG-001** | Model organizations with lifecycle management | ✅ Implemented |
| **F-ORG-002** | Support hierarchical department structures | ✅ Implemented |
| **F-ORG-003** | Tenant-scoped organization contexts | ✅ Implemented |
| **F-ORG-004** | Department-level KPI tracking | ✅ Implemented |
| **F-ORG-005** | Cross-department event routing | ✅ Implemented |

#### Agent System
| Requirement | Description | Status |
|-------------|-------------|--------|
| **F-AGT-001** | Base agent interfaces with role hierarchy | ✅ Implemented |
| **F-AGT-002** | Authority-based decision scoping | ✅ Implemented |
| **F-AGT-003** | Automatic escalation paths | ✅ Implemented |
| **F-AGT-004** | Event subscription/publication | ✅ Implemented |
| **F-AGT-005** | Agent metrics and observability | ✅ Implemented |
| **F-AGT-006** | Memory and context management | 🔄 Planned |
| **F-AGT-007** | Tool execution framework | 🔄 Planned |

#### Workflow & Task System
| Requirement | Description | Status |
|-------------|-------------|--------|
| **F-WFL-001** | Declarative workflow definitions | ✅ Implemented |
| **F-WFL-002** | Multi-step workflow orchestration | ✅ Implemented |
| **F-WFL-003** | Parallel and sequential execution | ✅ Implemented |
| **F-WFL-004** | HITL pause/approval points | ✅ Implemented |
| **F-WFL-005** | Quality gates and checkpoints | ✅ Implemented |
| **F-WFL-006** | SLA tracking and alerts | 🔄 Planned |

#### Event System
| Requirement | Description | Status |
|-------------|-------------|--------|
| **F-EVT-001** | Unified organization event model | ✅ Implemented |
| **F-EVT-002** | AEP integration (publish/subscribe) | ✅ Implemented |
| **F-EVT-003** | Event correlation and tracing | ✅ Implemented |
| **F-EVT-004** | Backwards-compatible event schemas | ✅ Implemented |

### 2.2 Non-Functional Requirements

#### Performance & Scalability
| Requirement | Target | Status |
|-------------|--------|--------|
| **NFR-PERF-001** | Support 1000+ concurrent agents | ✅ Met |
| **NFR-PERF-002** | Event processing <100ms P99 | ✅ Met |
| **NFR-PERF-003** | Workflow step latency <500ms P99 | ✅ Met |
| **NFR-PERF-004** | Multi-tenant isolation | ✅ Met |

#### Security & Compliance
| Requirement | Target | Status |
|-------------|--------|--------|
| **NFR-SEC-001** | Zero-trust by default | ✅ Met |
| **NFR-SEC-002** | Audit trails for all actions | ✅ Met |
| **NFR-SEC-003** | HITL for high-risk operations | ✅ Met |
| **NFR-SEC-004** | Role-based access control | ✅ Met |

#### Reliability & Testability
| Requirement | Target | Status |
|-------------|--------|--------|
| **NFR-REL-001** | 99.9% framework availability | ✅ Met |
| **NFR-REL-002** | Deterministic event handling | ✅ Met |
| **NFR-REL-003** | Comprehensive test coverage | ✅ Met |

---

## 3. Architecture Overview

### 3.1 Layered Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           DOMAIN PLUGINS                                     │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐  ┌───────────────────┐  │
│  │ Software-Org│  │ Healthcare-Org│  │ Finance-Org│  │ [Your]-Org Plugin │  │
│  │   Plugin    │  │    Plugin     │  │   Plugin   │  │                   │  │
│  └──────┬──────┘  └───────┬──────┘  └──────┬─────┘  └─────────┬─────────┘  │
│         │                 │                │                  │            │
│         └─────────────────┼────────────────┼──────────────────┘            │
│                           │         extends/uses                            │
│                           ▼                                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                    VIRTUAL-ORG FRAMEWORK LAYER                              │
│                                                                             │
│  ┌─────────────────┐ ┌───────────────┐ ┌──────────────┐ ┌───────────────┐  │
│  │  Organization   │ │  Department   │ │    Agent     │ │   Workflow    │  │
│  │  Abstractions   │ │  Abstractions │ │  Framework   │ │    Engine     │  │
│  │                 │ │               │ │              │ │               │  │
│  │ • AbstractOrg   │ │ • Department  │ │ • Agent      │ │ • Definition  │  │
│  │ • OrgContext    │ │ • DeptType    │ │ • BaseAgent  │ │ • Engine      │  │
│  │ • Lifecycle     │ │ • KpiTracker  │ │ • OrgAgent   │ │ • Steps       │  │
│  └─────────────────┘ └───────────────┘ └──────────────┘ └───────────────┘  │
│                                                                             │
│  ┌─────────────────┐ ┌───────────────┐ ┌──────────────┐ ┌───────────────┐  │
│  │   Task System   │ │  Event Model  │ │    HITL      │ │   Hierarchy   │  │
│  │                 │ │               │ │   Controls   │ │   System      │  │
│  │ • Task          │ │ • OrgEvent    │ │              │ │               │  │
│  │ • TaskResult    │ │ • Publisher   │ │ • Approval   │ │ • Role        │  │
│  │ • Priority      │ │ • AepAdapter  │ │ • QualityGate│ │ • Authority   │  │
│  └─────────────────┘ └───────────────┘ └──────────────┘ └───────────────┘  │
│                                                                             │
│                           │ depends on                                      │
│                           ▼                                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                      SHARED LIBRARIES                                       │
│                                                                             │
│  libs/java/*                              @ghatana/*                        │
│  ┌─────────────┐ ┌─────────────┐         ┌─────────────┐ ┌─────────────┐   │
│  │ http-server │ │ observability│         │ @ghatana/ui │ │@ghatana/state│  │
│  │ database    │ │ common-utils │         │ charts      │ │ realtime    │   │
│  │ activej-test│ │ ai-integration│        │ tokens      │ │ theme       │   │
│  └─────────────┘ └─────────────┘         └─────────────┘ └─────────────┘   │
│                                                                             │
│                           │ depends on                                      │
│                           ▼                                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                      PLATFORM SERVICES                                      │
│                                                                             │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐               │
│  │ Agentic Event   │ │  Observability  │ │   Auth/AuthZ    │               │
│  │ Processor (AEP) │ │   (Metrics,     │ │                 │               │
│  │                 │ │   Logs, Traces) │ │   Security      │               │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Module Structure

```
products/virtual-org/
├── libs/java/
│   ├── framework/                  # ⭐ CORE FRAMEWORK (DO NOT MODIFY)
│   │   └── src/main/java/com/ghatana/virtualorg/framework/
│   │       ├── AbstractOrganization.java    # Base organization (262 LOC)
│   │       ├── Department.java              # Department abstraction (166 LOC)
│   │       ├── DepartmentType.java          # Extensible enum
│   │       ├── OrganizationContext.java     # Tenant-scoped context
│   │       │
│   │       ├── agent/                       # Agent Framework
│   │       │   ├── Agent.java               # Base interface
│   │       │   ├── BaseOrganizationalAgent.java  # Template impl (364 LOC)
│   │       │   └── OrganizationalAgent.java      # Event contract
│   │       │
│   │       ├── event/                       # Event System
│   │       │   ├── EventPublisher.java      # Publishing interface
│   │       │   ├── OrganizationEventPublisher.java
│   │       │   └── AepEventPublisherAdapter.java  # AEP bridge
│   │       │
│   │       ├── hierarchy/                   # Organizational Hierarchy
│   │       │   ├── Role.java                # Role definition
│   │       │   ├── Authority.java           # Decision authority
│   │       │   └── EscalationPath.java      # Escalation chains
│   │       │
│   │       ├── kpi/                         # KPI Tracking
│   │       │   └── DepartmentKpiTracker.java
│   │       │
│   │       ├── task/                        # Task System
│   │       │   ├── Task.java
│   │       │   └── TaskPriority.java
│   │       │
│   │       ├── workflow/                    # Workflow Engine
│   │       │   ├── WorkflowDefinition.java
│   │       │   ├── WorkflowEngine.java      # Orchestration (277 LOC)
│   │       │   └── WorkflowStep.java
│   │       │
│   │       ├── memory/                      # Agent Memory (🔄 Planned)
│   │       │
│   │       └── integration/                 # External Integrations
│   │           ├── AgentOperatorRegistry.java
│   │           └── WorkflowPipelineAdapter.java
│   │
│   ├── virtualorg-agent/           # Lightweight agent impl (192 LOC)
│   ├── org-events/                 # Event definitions
│   ├── workflows/                  # Reusable workflows
│   ├── operator-adapter/           # AEP operator adapters
│   └── agent-implementations/      # Reference implementations
│
├── contracts/proto/                # Protobuf API contracts
├── apps/virtual-org-service/       # Standalone service (optional)
└── docs/                           # Documentation (this folder)
```

### 3.3 Dependency Rules

```
┌─────────────────────────────────────────────────────────────────────┐
│                    STRICT DEPENDENCY RULES                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ✅ ALLOWED:                                                        │
│     Plugin → Virtual-Org → Shared Libs → Platform                  │
│                                                                     │
│  ❌ FORBIDDEN:                                                      │
│     Virtual-Org → Plugin                                           │
│     Virtual-Org → Product-Specific Code                            │
│     Shared Libs → Virtual-Org                                      │
│     Platform → Virtual-Org                                         │
│                                                                     │
│  RULE: Dependencies flow DOWNWARD only. No upward dependencies.    │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 4. Framework Abstractions

### 4.1 Organization Model

#### AbstractOrganization

The root abstraction for all virtual organizations.

```java
/**
 * Abstract base class for all virtual organizations.
 *
 * EXTENSION POINTS:
 * - Override initializeDepartments() to configure domain-specific departments
 * - Override createContext() to provide custom organization context
 * - Override getEventPublisher() to customize event routing
 *
 * LIFECYCLE:
 * 1. Construction → Departments registered
 * 2. initialize() → Async initialization (DB, connections)
 * 3. start() → Begin event processing
 * 4. shutdown() → Graceful shutdown
 *
 * EVENTS EMITTED:
 * - OrganizationCreatedEvent
 * - DepartmentRegisteredEvent
 * - OrganizationActivatedEvent
 * - OrganizationDeactivatedEvent
 */
public abstract class AbstractOrganization {
    
    // Core identity
    private final Identifier id;
    private final TenantId tenantId;
    private final String name;
    
    // Department registry (thread-safe)
    private final Map<String, Department> departments;
    
    // Context and observability
    private final OrganizationContext context;
    private final DepartmentKpiTracker kpiTracker;
    private final EventPublisher eventPublisher;
    
    // TEMPLATE METHOD: Subclasses define departments
    protected abstract void initializeDepartments();
    
    // Department management
    public void registerDepartment(Department dept);
    public Optional<Department> getDepartment(String name);
    public Collection<Department> getAllDepartments();
    
    // Lifecycle
    public Promise<Void> initialize();
    public Promise<Void> start();
    public Promise<Void> shutdown();
}
```

#### Creating a Custom Organization

```java
// Example: Healthcare Organization
public class HealthcareOrganization extends AbstractOrganization {
    
    public HealthcareOrganization(TenantId tenantId, String name) {
        super(tenantId, name);
    }
    
    @Override
    protected void initializeDepartments() {
        registerDepartment(new EmergencyDepartment(this));
        registerDepartment(new SurgeryDepartment(this));
        registerDepartment(new PharmacyDepartment(this));
        registerDepartment(new NursingDepartment(this));
        registerDepartment(new AdministrationDepartment(this));
        registerDepartment(new ComplianceDepartment(this));
    }
}
```

### 4.2 Department Model

#### Department

Represents a functional unit within an organization.

```java
/**
 * Represents a department within a virtual organization.
 *
 * EXTENSION POINTS:
 * - Override processTask() to handle domain-specific tasks
 * - Override getKpis() to define department-specific KPIs
 * - Override handleEvent() for event-driven behavior
 *
 * THREAD SAFETY:
 * Thread-safe via CopyOnWriteArrayList for agents.
 */
public abstract class Department {
    
    private final Identifier id;
    private final String name;
    private final DepartmentType type;
    private final List<Agent> agents;
    private AbstractOrganization organization;
    
    // ABSTRACT: Must implement
    public abstract Promise<TaskResult> processTask(Task task);
    public abstract List<KpiDefinition> getKpis();
    
    // EXTENSION: Can override
    protected void handleEvent(Event event) { }
    protected void onAgentRegistered(Agent agent) { }
    
    // Agent management
    public void registerAgent(Agent agent);
    public List<Agent> getAgents();
}
```

#### DepartmentType Registry

```java
/**
 * Extensible department type system.
 *
 * BUILT-IN TYPES (11):
 * EXECUTIVE, ENGINEERING, QA, DEVOPS, SUPPORT,
 * PRODUCT, SALES, MARKETING, FINANCE, HR, COMPLIANCE
 *
 * CUSTOM TYPES:
 * Use DepartmentType.custom("MY_DEPT") for domain-specific departments
 */
public enum DepartmentType {
    EXECUTIVE,
    ENGINEERING,
    QA,
    DEVOPS,
    SUPPORT,
    PRODUCT,
    SALES,
    MARKETING,
    FINANCE,
    HR,
    COMPLIANCE,
    CUSTOM;
    
    // Extension method for custom types
    public static DepartmentType custom(String name) {
        return CUSTOM.withName(name);
    }
}
```

### 4.3 Agent Model

#### Agent Hierarchy

```
┌────────────────────────────────────────────────────────────────┐
│                      AGENT ABSTRACTION LAYERS                   │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  ┌────────────────────────────────────────────────────────┐   │
│  │                    Agent (Interface)                    │   │
│  │  • Basic contract: getId(), getName(), getCapabilities()│   │
│  └─────────────────────────┬──────────────────────────────┘   │
│                            │ extends                          │
│  ┌─────────────────────────▼──────────────────────────────┐   │
│  │             OrganizationalAgent (Interface)             │   │
│  │  • Event contract: handle(Event), getRole()             │   │
│  │  • Authority: getAuthority(), getEscalationPath()       │   │
│  └─────────────────────────┬──────────────────────────────┘   │
│                            │ implements                       │
│  ┌─────────────────────────▼──────────────────────────────┐   │
│  │          BaseOrganizationalAgent (Abstract Class)       │   │
│  │  • Default implementations with metrics                 │   │
│  │  • Template method: override doHandle()                 │   │
│  │  • Built-in: authority checking, escalation, metrics    │   │
│  └─────────────────────────┬──────────────────────────────┘   │
│                            │ extends                          │
│  ┌─────────────────────────▼──────────────────────────────┐   │
│  │          Domain-Specific Agents (Your Implementations)  │   │
│  │  • CTOAgent, EngineerAgent, DoctorAgent, etc.          │   │
│  │  • Product-specific behavior in doHandle()              │   │
│  └────────────────────────────────────────────────────────┘   │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

#### BaseOrganizationalAgent

```java
/**
 * Base implementation of OrganizationalAgent.
 *
 * FEATURES:
 * - Role-based event routing
 * - Authority-scoped decision making
 * - Automatic escalation for out-of-scope decisions
 * - Built-in metrics (processed, escalated, errors)
 *
 * TEMPLATE METHOD:
 * Subclasses implement doHandle() for role-specific logic
 */
public abstract class BaseOrganizationalAgent implements OrganizationalAgent {
    
    private final String id;
    private final String version;
    private final Role role;
    private final Authority authority;
    private final EscalationPath escalationPath;
    private final Set<String> supportedEventTypes;
    private final Set<String> outputEventTypes;
    
    // Metrics
    private final AtomicLong processedCount;
    private final AtomicLong escalatedCount;
    private final AtomicLong errorCount;
    
    // TEMPLATE METHOD: Subclasses implement
    protected abstract List<Event> doHandle(Event event, AgentExecutionContext context);
    
    // Built-in behavior
    protected boolean hasAuthority(String decisionType);
    protected List<Event> escalateEvent(Event event, AgentExecutionContext context);
}
```

### 4.4 Workflow Engine

```java
/**
 * Declarative workflow orchestration engine.
 *
 * FEATURES:
 * - Multi-step processes with dependencies
 * - Parallel and sequential execution
 * - HITL pause points
 * - Quality gates
 * - SLA tracking
 */
public class WorkflowDefinition {
    private final String id;
    private final String name;
    private final String version;
    private final List<WorkflowStep> steps;
    
    public static Builder builder() { ... }
}

// Example: Incident Response Workflow
WorkflowDefinition incidentResponse = WorkflowDefinition.builder()
    .id("incident-response")
    .name("Incident Response Workflow")
    .version("1.0.0")
    .addStep(WorkflowStep.of("triage", "Triage Incident", "OnCallEngineer"))
    .addStep(WorkflowStep.of("diagnose", "Diagnose Root Cause", "SRE")
        .dependsOn("triage"))
    .addStep(WorkflowStep.of("fix", "Apply Fix", "Engineer")
        .dependsOn("diagnose"))
    .addStep(WorkflowStep.of("verify", "Verify Resolution", "QA")
        .dependsOn("fix"))
    .addStep(WorkflowStep.of("postmortem", "Post-Incident Review", "TechLead")
        .dependsOn("verify")
        .requiresHumanApproval())
    .build();
```

### 4.5 Event Model

#### Organization Event Hierarchy

```
OrganizationEvent (Base)
├── tenantId, correlationId, timestamp, schemaVersion
│
├── OrganizationLifecycleEvent
│   ├── OrganizationCreatedEvent
│   ├── OrganizationActivatedEvent
│   └── OrganizationDeactivatedEvent
│
├── DepartmentEvent
│   ├── DepartmentRegisteredEvent
│   ├── DepartmentKpiUpdatedEvent
│   └── DepartmentStatusChangedEvent
│
├── AgentEvent
│   ├── AgentDecisionEvent
│   ├── AgentEscalationEvent
│   └── AgentActionCompletedEvent
│
├── TaskEvent
│   ├── TaskCreatedEvent
│   ├── TaskAssignedEvent
│   ├── TaskProgressEvent
│   ├── TaskCompletedEvent
│   └── TaskEscalatedEvent
│
├── WorkflowEvent
│   ├── WorkflowStartedEvent
│   ├── WorkflowStepCompletedEvent
│   └── WorkflowCompletedEvent
│
└── HITLEvent
    ├── HumanApprovalRequestedEvent
    ├── HumanApprovalGrantedEvent
    └── QualityGateTriggeredEvent
```

---

## 5. Plugin Architecture

### 5.1 Extension Points

| Extension Point | Interface/Base Class | Purpose |
|-----------------|---------------------|---------|
| **Organization Type** | `AbstractOrganization` | Define new organization types |
| **Department Type** | `Department` | Create domain-specific departments |
| **Agent Role** | `BaseOrganizationalAgent` | Implement role-specific agents |
| **Workflow Type** | `WorkflowDefinition` | Define reusable workflows |
| **Event Type** | `OrganizationEvent` | Create custom event types |
| **KPI Type** | `KpiDefinition` | Define new metrics |
| **Integration Adapter** | `IntegrationAdapter` | Connect external systems |
| **Tool Provider** | `AgentToolProvider` | Add agent capabilities |

### 5.2 Plugin Registration

```java
/**
 * Plugin manifest for registering Virtual-Org extensions.
 */
public class VirtualOrgPlugin {
    
    public static VirtualOrgPlugin create() {
        return VirtualOrgPlugin.builder()
            // Register organization type
            .organizationType(HealthcareOrganization.class)
            
            // Register departments
            .department("emergency", EmergencyDepartment.class)
            .department("surgery", SurgeryDepartment.class)
            
            // Register agents
            .agent("doctor", DoctorAgent.class)
            .agent("nurse", NurseAgent.class)
            
            // Register workflows
            .workflow("patient-admission", PatientAdmissionWorkflow.class)
            .workflow("surgery-scheduling", SurgerySchedulingWorkflow.class)
            
            // Register integrations
            .integration("ehr", EHRIntegration.class)  // Electronic Health Records
            .integration("scheduling", SchedulingIntegration.class)
            
            .build();
    }
}
```

### 5.3 Plugin Lifecycle

```
DISCOVER → VALIDATE → LOAD → ACTIVATE → RUNTIME → PAUSE → SHUTDOWN

┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│ DISCOVER │───▶│ VALIDATE │───▶│   LOAD   │───▶│ ACTIVATE │
└──────────┘    └──────────┘    └──────────┘    └──────────┘
     │               │               │               │
     ▼               ▼               ▼               ▼
 Scan for       Check deps     Register        Start event
 plugins        & versions     components      subscriptions

┌──────────┐    ┌──────────┐    ┌──────────┐
│ RUNTIME  │◀──▶│  PAUSE   │───▶│ SHUTDOWN │
└──────────┘    └──────────┘    └──────────┘
     │                               │
     ▼                               ▼
 Handle events                   Cleanup &
 Execute workflows               Unregister
```

---

## 6. Integration Points

### 6.1 AEP Integration

Virtual-Org integrates with Agentic Event Processor for:
- Event emission (OrganizationEvents → AEP pipelines)
- Event consumption (AEP pipelines → Agent handlers)
- Pipeline registration (Workflow steps as operators)

```java
public class AepEventPublisherAdapter implements EventPublisher {
    
    private final AepClient aepClient;
    
    @Override
    public void publish(OrganizationEvent event) {
        GEvent gEvent = toGEvent(event);
        aepClient.publish(gEvent);
    }
}

// Register Virtual-Org operators with AEP
AgentOperatorRegistry.register(
    new AgentStreamOperator("virtual-org-agent-processor")
);
```

### 6.2 Observability Integration

```java
public class OrganizationMetrics {
    
    // Agent metrics
    public static final Counter AGENT_DECISIONS = Counter.build()
        .name("virtualorg_agent_decisions_total")
        .help("Total agent decisions made")
        .labelNames("agent_type", "decision_type", "outcome")
        .register();
    
    // Workflow metrics
    public static final Histogram WORKFLOW_DURATION = Histogram.build()
        .name("virtualorg_workflow_duration_seconds")
        .help("Workflow execution duration")
        .labelNames("workflow_id", "outcome")
        .register();
    
    // Department KPIs
    public static final Gauge DEPARTMENT_KPI = Gauge.build()
        .name("virtualorg_department_kpi")
        .help("Department KPI value")
        .labelNames("department", "kpi_name")
        .register();
}
```

### 6.3 Human-in-the-Loop (HITL)

| Pattern | Description | Use Case |
|---------|-------------|----------|
| **Approval Gate** | Pause workflow for human approval | High-risk actions |
| **Review Step** | Agent recommends, human decides | Complex decisions |
| **Override Mode** | Human can override agent actions | Governance |
| **Audit Mode** | Async human review | Compliance |
| **Confidence Threshold** | Below threshold → human | Risk-based automation |

```java
// Request human approval
HumanApprovalRequest request = HumanApprovalRequest.builder()
    .action("deploy_to_production")
    .requester(agentId)
    .context(deploymentContext)
    .urgency(Urgency.HIGH)
    .slaSeconds(3600)
    .build();

HITLService.requestApproval(request);
```

---

## 7. Current Implementation Status

### 7.1 Completed Components

| Component | Status | LOC | Tests |
|-----------|--------|-----|-------|
| AbstractOrganization | ✅ Complete | 262 | 25+ |
| Department | ✅ Complete | 166 | 20+ |
| DepartmentType | ✅ Complete | 45 | 10+ |
| OrganizationContext | ✅ Complete | 120 | 15+ |
| Agent Interface | ✅ Complete | 35 | N/A |
| BaseOrganizationalAgent | ✅ Complete | 364 | 40+ |
| OrganizationalAgent | ✅ Complete | 50 | N/A |
| WorkflowEngine | ✅ Complete | 277 | 30+ |
| WorkflowDefinition | ✅ Complete | 150 | 20+ |
| WorkflowStep | ✅ Complete | 85 | 15+ |
| EventPublisher | ✅ Complete | 45 | 10+ |
| AepEventPublisherAdapter | ✅ Complete | 120 | 15+ |
| VirtualOrgAgent | ✅ Complete | 192 | 25+ |
| Role | ✅ Complete | 60 | 10+ |
| Authority | ✅ Complete | 90 | 15+ |
| EscalationPath | ✅ Complete | 75 | 10+ |

### 7.2 Quality Metrics

| Metric | Target | Actual |
|--------|--------|--------|
| Test Coverage | 80% | 85% |
| Test Pass Rate | 100% | 100% |
| Documentation Coverage | 100% | 100% |
| Critical Bugs | 0 | 0 |

---

## 8. Planned Capabilities

### 8.1 Near-Term (Q1 2026)

| Capability | Priority | Description |
|------------|----------|-------------|
| **Advanced Memory System** | HIGH | Per-agent and shared memory with persistence |
| **Tool Execution Framework** | HIGH | Standardized agent tool interface |
| **Enhanced HITL Controls** | MEDIUM | More granular approval policies |
| **Simulation Mode** | MEDIUM | Run organizations with synthetic events |

### 8.2 Mid-Term (Q2 2026)

| Capability | Priority | Description |
|------------|----------|-------------|
| **Replay Engine** | MEDIUM | Replay historical scenarios |
| **Visual Workflow Builder** | LOW | Drag-and-drop workflow design |
| **Agent Marketplace** | LOW | Discover and install pre-built agents |
| **Cross-Org Communication** | MEDIUM | Organizations interacting with each other |

### 8.3 Long-Term (2026+)

| Capability | Priority | Description |
|------------|----------|-------------|
| **Autonomous Mode** | LOW | Self-improving organizations |
| **Federated Organizations** | LOW | Multi-tenant organization networks |
| **Industry Templates** | MEDIUM | Pre-built org templates by industry |

---

## 9. Extension Guidelines

### 9.1 Rules for Plugin Development

| Rule | Description |
|------|-------------|
| **No Framework Modification** | Never modify `products/virtual-org/libs/java/framework` |
| **Extend, Don't Copy** | Extend framework classes, don't duplicate them |
| **Use EventPublisher** | All cross-department communication via events |
| **Test with EventloopTestBase** | All async tests must use the standard test base |
| **Document with @doc Tags** | Every public class needs JavaDoc + @doc tags |

### 9.2 When to Use Virtual-Org

✅ **USE** Virtual-Org when:
- Modeling an organization with departments and roles
- Building AI-powered agent workflows
- Need event-driven organizational simulation
- Require HITL controls for safety
- Building on the Ghatana platform

❌ **DON'T USE** Virtual-Org when:
- Building simple automation (use AEP directly)
- No organizational structure needed
- Standalone application without platform integration

### 9.3 Best Practices

1. **Extend, Don't Modify** - Always extend base classes
2. **Event-First** - Emit events for all significant actions
3. **Idempotent Handlers** - Event handlers must be idempotent
4. **Clear Boundaries** - Keep product code in product modules
5. **Test with Eventloop** - Use `EventloopTestBase` for async tests
6. **Document Contracts** - Use `@doc.*` tags on all public APIs

---

## 10. Appendices

### 10.1 Glossary

| Term | Definition |
|------|------------|
| **Organization** | Top-level entity representing a company/org |
| **Department** | Functional unit within an organization |
| **Agent** | AI-powered actor with a specific role |
| **Role** | Agent's position in organizational hierarchy |
| **Authority** | Decision-making permissions for an agent |
| **Task** | Unit of work assigned to departments/agents |
| **Workflow** | Multi-step process coordinating agents |
| **HITL** | Human-in-the-Loop control point |
| **KPI** | Key Performance Indicator tracked by department |
| **AEP** | Agentic Event Processor (platform event system) |
| **Plugin** | Domain-specific Virtual-Org implementation |

### 10.2 Related Documents

| Document | Location | Purpose |
|----------|----------|---------|
| Framework Specification | `docs/VIRTUAL_ORG_FRAMEWORK_SPECIFICATION.md` | Detailed API specs |
| Plugin Development Guide | `docs/PLUGIN_DEVELOPMENT_GUIDE.md` | How to create plugins |
| Design Architecture | `docs/DESIGN_ARCHITECTURE.md` | Technical design |
| Vision & Requirements | `docs/vision-and-requirements/VIRTUAL_ORG_VISION_AND_REQUIREMENTS.md` | Strategic vision |

### 10.3 Reference Implementations

| Plugin | Status | Description |
|--------|--------|-------------|
| **Software-Org** | ✅ Production | Software development organizations |
| Healthcare-Org | 📋 Planned | Healthcare organizations |
| Manufacturing-Org | 📋 Planned | Manufacturing organizations |
| Finance-Org | 📋 Planned | Financial organizations |

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 2.0.0 | 2025-11-26 | Platform Team | Master architecture document |
| 1.0.0 | 2025-11-26 | Platform Team | Initial specification |
