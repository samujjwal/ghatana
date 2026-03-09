# Virtual-Org Framework Dependency Specification

> **Document Version:** 1.0.0  
> **Created:** 2025-12-03  
> **Status:** Living Document  
> **Scope:** Software-Org dependencies on Virtual-Org framework

---

## Executive Summary

This document defines the **dependency relationship** between Software-Org and the Virtual-Org framework. Virtual-Org serves as the **foundational framework** for organization modeling, event-driven simulation, and agent orchestration. Software-Org is a **domain plugin** that implements DevSecOps-specific functionality on top of this framework.

---

## 1. Dependency Architecture

### 1.1 Layered Dependency Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        DEPENDENCY HIERARCHY                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  LAYER 4: Software-Org Product                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  products/software-org/                                              │   │
│  │  ├── apps/web/           # React UI                                 │   │
│  │  ├── apps/backend/       # Node.js API                              │   │
│  │  ├── apps/desktop/       # Tauri desktop                            │   │
│  │  ├── libs/java/          # Java domain services                     │   │
│  │  └── config/             # YAML configurations                      │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │ DEPENDS ON                              │
│                                   ▼                                         │
│  LAYER 3: Virtual-Org Framework                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  products/virtual-org/                                               │   │
│  │  ├── libs/java/framework/         # Core abstractions               │   │
│  │  ├── libs/java/agent-implementations/ # Agent base classes         │   │
│  │  ├── libs/java/org-events/        # Event model                     │   │
│  │  ├── libs/java/workflows/         # Workflow engine                 │   │
│  │  ├── libs/workflow/               # Workflow definitions            │   │
│  │  ├── libs/integration/            # External integrations           │   │
│  │  └── libs/operator-adapter/       # AEP operator adapters           │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │ DEPENDS ON                              │
│                                   ▼                                         │
│  LAYER 2: Platform Libraries (libs/java/*)                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  • auth / auth-platform          # Authentication                   │   │
│  │  • http-server / http-client     # HTTP abstractions                │   │
│  │  • observability                  # Metrics, tracing                 │   │
│  │  • ai-integration                 # LLM/AI services                  │   │
│  │  • config-runtime                 # Configuration management         │   │
│  │  • state                          # State management                 │   │
│  │  • event-runtime                  # Event processing                 │   │
│  │  • database                       # Database abstractions            │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │ DEPENDS ON                              │
│                                   ▼                                         │
│  LAYER 1: Agentic Event Processor (AEP)                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  products/agentic-event-processor/                                   │   │
│  │  • Operator Catalog              # Unified operators                 │   │
│  │  • Pipeline Builder              # Event pipelines                   │   │
│  │  • Event Cloud                   # Event storage & tailing           │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Dependency Flow Rules

1. **Strict Downward Flow:** `Software-Org → Virtual-Org → libs/java → AEP`
2. **No Upward Dependencies:** Virtual-Org MUST NOT depend on Software-Org
3. **No Circular Dependencies:** No module can transitively depend on itself
4. **Framework Immutability:** Virtual-Org code is IMMUTABLE from Software-Org perspective

---

## 2. Virtual-Org Module Dependencies

### 2.1 Core Framework Module

| Module      | Path                                       | Purpose           | Software-Org Usage                   |
| ----------- | ------------------------------------------ | ----------------- | ------------------------------------ |
| `framework` | `products/virtual-org/libs/java/framework` | Core abstractions | All department/agent implementations |

**Key Classes:**

| Class                     | Package                                       | Purpose                | Dependency Type |
| ------------------------- | --------------------------------------------- | ---------------------- | --------------- |
| `AbstractOrganization`    | `com.ghatana.virtualorg.framework.org`        | Organization base      | EXTENDS         |
| `Department`              | `com.ghatana.virtualorg.framework.department` | Department abstraction | EXTENDS         |
| `BaseOrganizationalAgent` | `com.ghatana.virtualorg.framework.agent`      | Agent base class       | EXTENDS         |
| `TaskDefinition`          | `com.ghatana.virtualorg.framework.task`       | Task contracts         | IMPLEMENTS      |
| `TaskInstance`            | `com.ghatana.virtualorg.framework.task`       | Task runtime           | USES            |
| `DepartmentKpiTracker`    | `com.ghatana.virtualorg.framework.kpi`        | KPI tracking           | IMPLEMENTS      |
| `HierarchyNode`           | `com.ghatana.virtualorg.framework.hierarchy`  | Org hierarchy          | USES            |
| `CapabilityDefinition`    | `com.ghatana.virtualorg.framework.capability` | Agent capabilities     | USES            |

**Gradle Dependency:**

```groovy
// products/software-org/libs/java/domain-models/build.gradle.kts
dependencies {
    api(project(":products:virtual-org:libs:java:framework"))
}
```

### 2.2 Agent Implementations Module

| Module                  | Path                                                   | Purpose                    | Software-Org Usage                |
| ----------------------- | ------------------------------------------------------ | -------------------------- | --------------------------------- |
| `agent-implementations` | `products/virtual-org/libs/java/agent-implementations` | Agent base implementations | Extend for domain-specific agents |

**Key Classes:**

| Class                   | Package                                  | Purpose           | Dependency Type |
| ----------------------- | ---------------------------------------- | ----------------- | --------------- |
| `LLMAgentProvider`      | `com.ghatana.virtualorg.agents.spi.impl` | AI-backed agents  | USES            |
| `StandardToolProvider`  | `com.ghatana.virtualorg.agents.spi.impl` | Tool provisioning | USES            |
| `AgentLifecycleManager` | `com.ghatana.virtualorg.agents`          | Agent lifecycle   | USES            |

**Gradle Dependency:**

```groovy
dependencies {
    implementation(project(":products:virtual-org:libs:java:agent-implementations"))
}
```

### 2.3 Organization Events Module

| Module       | Path                                        | Purpose     | Software-Org Usage              |
| ------------ | ------------------------------------------- | ----------- | ------------------------------- |
| `org-events` | `products/virtual-org/libs/java/org-events` | Event model | All event emission/subscription |

**Key Classes:**

| Class               | Package                                    | Purpose            | Dependency Type |
| ------------------- | ------------------------------------------ | ------------------ | --------------- |
| `OrganizationEvent` | `com.ghatana.virtualorg.events`            | Event base         | USES            |
| `EventPublisher`    | `com.ghatana.virtualorg.events`            | Event emission     | USES            |
| `EventSubscriber`   | `com.ghatana.virtualorg.events`            | Event subscription | IMPLEMENTS      |
| `DepartmentEvent`   | `com.ghatana.virtualorg.events.department` | Department events  | USES            |
| `AgentEvent`        | `com.ghatana.virtualorg.events.agent`      | Agent events       | USES            |
| `TaskEvent`         | `com.ghatana.virtualorg.events.task`       | Task events        | USES            |
| `WorkflowEvent`     | `com.ghatana.virtualorg.events.workflow`   | Workflow events    | USES            |

**Event Types Used:**

| Event Type               | Category   | Software-Org Usage   |
| ------------------------ | ---------- | -------------------- |
| `org.dept.created`       | Department | Department creation  |
| `org.dept.updated`       | Department | Department updates   |
| `org.dept.deleted`       | Department | Department deletion  |
| `org.agent.assigned`     | Agent      | Agent assignment     |
| `org.agent.unassigned`   | Agent      | Agent removal        |
| `org.task.created`       | Task       | Work item creation   |
| `org.task.completed`     | Task       | Work item completion |
| `org.workflow.started`   | Workflow   | Workflow initiation  |
| `org.workflow.completed` | Workflow   | Workflow completion  |
| `org.approval.requested` | Approval   | Approval request     |
| `org.approval.completed` | Approval   | Approval decision    |
| `org.hierarchy.changed`  | Hierarchy  | Restructure events   |

**Gradle Dependency:**

```groovy
dependencies {
    api(project(":products:virtual-org:libs:java:org-events"))
}
```

### 2.4 Workflow Module

| Module     | Path                                 | Purpose         | Software-Org Usage     |
| ---------- | ------------------------------------ | --------------- | ---------------------- |
| `workflow` | `products/virtual-org/libs/workflow` | Workflow engine | Cross-department flows |

**Key Classes:**

| Class                | Package                           | Purpose            | Dependency Type |
| -------------------- | --------------------------------- | ------------------ | --------------- |
| `WorkflowEngine`     | `com.ghatana.virtualorg.workflow` | Workflow execution | USES            |
| `WorkflowDefinition` | `com.ghatana.virtualorg.workflow` | Workflow schema    | USES            |
| `WorkflowStep`       | `com.ghatana.virtualorg.workflow` | Step definition    | USES            |
| `WorkflowContext`    | `com.ghatana.virtualorg.workflow` | Execution context  | USES            |
| `WorkflowRegistry`   | `com.ghatana.virtualorg.workflow` | Workflow catalog   | USES            |

**Gradle Dependency:**

```groovy
dependencies {
    implementation(project(":products:virtual-org:libs:workflow"))
}
```

### 2.5 Integration Module

| Module        | Path                                    | Purpose               | Software-Org Usage    |
| ------------- | --------------------------------------- | --------------------- | --------------------- |
| `integration` | `products/virtual-org/libs/integration` | External integrations | AEP, state, messaging |

**Key Classes:**

| Class                        | Package                                        | Purpose       | Dependency Type |
| ---------------------------- | ---------------------------------------------- | ------------- | --------------- |
| `AepEventBusConnector`       | `com.ghatana.virtualorg.integration.aep`       | AEP event bus | USES            |
| `CoreStateStoreAdapter`      | `com.ghatana.virtualorg.integration.state`     | State storage | USES            |
| `EventCloudMessageTransport` | `com.ghatana.virtualorg.integration.messaging` | Messaging     | USES            |

**Gradle Dependency:**

```groovy
dependencies {
    implementation(project(":products:virtual-org:libs:integration"))
}
```

### 2.6 Operator Adapter Module

| Module             | Path                                         | Purpose               | Software-Org Usage      |
| ------------------ | -------------------------------------------- | --------------------- | ----------------------- |
| `operator-adapter` | `products/virtual-org/libs/operator-adapter` | AEP operator adapters | Cross-cutting operators |

**Key Classes:**

| Class                         | Package                           | Purpose           | Dependency Type |
| ----------------------------- | --------------------------------- | ----------------- | --------------- |
| `CrossCuttingOperatorFactory` | `com.ghatana.virtualorg.operator` | Operator creation | USES            |
| `AepOperatorCatalogClient`    | `com.ghatana.virtualorg.operator` | Operator registry | USES            |
| `AepAgentRegistryClient`      | `com.ghatana.virtualorg.operator` | Agent registry    | USES            |

**Gradle Dependency:**

```groovy
dependencies {
    implementation(project(":products:virtual-org:libs:operator-adapter"))
}
```

---

## 3. Configuration Dependencies

### 3.1 Configuration Schema

Software-Org configurations MUST conform to Virtual-Org schema:

```
products/virtual-org/config/schema/virtual-org-schema.json
```

### 3.2 Required Configuration Files

| File                  | Purpose               | Virtual-Org Validation |
| --------------------- | --------------------- | ---------------------- |
| `organization.yaml`   | Org structure         | ✅ Schema validated    |
| `departments/*.yaml`  | Department configs    | ✅ Schema validated    |
| `agents/*.yaml`       | Agent configs         | ✅ Schema validated    |
| `workflows/*.yaml`    | Workflow definitions  | ✅ Schema validated    |
| `integrations/*.yaml` | External integrations | ✅ Schema validated    |

### 3.3 Configuration Example

```yaml
# products/software-org/config/organization.yaml
$schema: "../../../virtual-org/config/schema/virtual-org-schema.json"

organization:
  id: software-org-demo
  name: "Demo Software Organization"
  type: software-company
  version: "1.0.0"

  hierarchy:
    type: hierarchical
    levels:
      - name: organization
        roles: [owner, ceo]
      - name: executive
        roles: [cto, cpo, cfo, coo, ciso]
      - name: management
        roles: [director, team-lead, architect]
      - name: operations
        roles: [admin, security-admin]
      - name: contributor
        roles: [engineer, qa, support, sales]

departments:
  - id: engineering
    name: "Engineering"
    type: development
    capabilities:
      - code-development
      - code-review
      - architecture-design
    kpis:
      - deployment-frequency
      - lead-time
      - mttr

agents:
  - id: senior-engineer
    department: engineering
    role: senior-engineer
    provider: llm-coder
    capabilities:
      - code-review
      - architecture
      - mentoring
```

---

## 4. Extension Contracts

### 4.1 Department Extension

Software-Org departments MUST extend `Department` from Virtual-Org:

```java
package com.ghatana.softwareorg.departments;

import com.ghatana.virtualorg.framework.department.Department;
import com.ghatana.virtualorg.framework.department.DepartmentConfig;

/**
 * @doc.type class
 * @doc.purpose Engineering department implementation
 * @doc.layer product
 * @doc.pattern Department Extension
 */
public class EngineeringDepartment extends Department {

    public EngineeringDepartment(DepartmentConfig config) {
        super(config);
    }

    @Override
    protected void initializeCapabilities() {
        addCapability("code-development");
        addCapability("code-review");
        addCapability("architecture-design");
    }

    @Override
    protected void initializeKpis() {
        addKpi("deployment-frequency");
        addKpi("lead-time");
        addKpi("mttr");
        addKpi("change-failure-rate");
    }
}
```

### 4.2 Agent Extension

Software-Org agents MUST extend `BaseOrganizationalAgent`:

```java
package com.ghatana.softwareorg.agents;

import com.ghatana.virtualorg.framework.agent.BaseOrganizationalAgent;
import com.ghatana.virtualorg.framework.agent.AgentConfig;

/**
 * @doc.type class
 * @doc.purpose Senior engineer agent implementation
 * @doc.layer product
 * @doc.pattern Agent Extension
 */
public class SeniorEngineerAgent extends BaseOrganizationalAgent {

    public SeniorEngineerAgent(AgentConfig config) {
        super(config);
    }

    @Override
    public Promise<TaskResult> executeTask(TaskInstance task) {
        return switch (task.getType()) {
            case "code-review" -> performCodeReview(task);
            case "architecture-review" -> performArchitectureReview(task);
            case "mentoring" -> provideMentoring(task);
            default -> Promise.ofException(new UnsupportedTaskException(task.getType()));
        };
    }
}
```

### 4.3 Cross-Department Flow Extension

Software-Org flows MUST implement `CrossDepartmentEventFlow`:

```java
package com.ghatana.softwareorg.flows;

import com.ghatana.virtualorg.workflow.CrossDepartmentEventFlow;
import com.ghatana.virtualorg.events.OrganizationEvent;

/**
 * @doc.type class
 * @doc.purpose Engineering to QA flow implementation
 * @doc.layer product
 * @doc.pattern Flow Extension
 */
public class EngineeringToQaFlow implements CrossDepartmentEventFlow {

    @Override
    public String getFlowId() {
        return "engineering-to-qa";
    }

    @Override
    public String getSourceDepartment() {
        return "engineering";
    }

    @Override
    public String getTargetDepartment() {
        return "qa";
    }

    @Override
    public Promise<OrganizationEvent> transform(OrganizationEvent sourceEvent) {
        // Transform feature-complete event to test-request event
        return Promise.of(createTestRequestEvent(sourceEvent));
    }
}
```

---

## 5. SPI (Service Provider Interface) Dependencies

### 5.1 Required SPI Implementations

| SPI Interface       | Virtual-Org Default          | Software-Org Override |
| ------------------- | ---------------------------- | --------------------- |
| `AgentProvider`     | `LLMAgentProvider`           | No override needed    |
| `ToolProvider`      | `StandardToolProvider`       | May add custom tools  |
| `MessageTransport`  | `EventCloudMessageTransport` | No override needed    |
| `StateStoreAdapter` | `CoreStateStoreAdapter`      | No override needed    |
| `EventBusConnector` | `AepEventBusConnector`       | No override needed    |

### 5.2 SPI Registration

```java
// META-INF/services/com.ghatana.virtualorg.spi.AgentProvider
com.ghatana.softwareorg.spi.SoftwareOrgAgentProvider

// META-INF/services/com.ghatana.virtualorg.spi.ToolProvider
com.ghatana.softwareorg.spi.DevSecOpsToolProvider
```

---

## 6. Platform Library Dependencies (Transitive)

### 6.1 Through Virtual-Org

| Platform Library           | Used By Virtual-Org For | Software-Org Usage |
| -------------------------- | ----------------------- | ------------------ |
| `libs/java/auth`           | Authentication          | User identity      |
| `libs/java/observability`  | Metrics, tracing        | Monitoring         |
| `libs/java/ai-integration` | LLM/AI services         | Agent AI           |
| `libs/java/config-runtime` | Configuration           | Org config         |
| `libs/java/state`          | State management        | Org state          |
| `libs/java/event-runtime`  | Event processing        | Event handling     |

### 6.2 Direct Dependencies

| Platform Library        | Software-Org Usage | Justification       |
| ----------------------- | ------------------ | ------------------- |
| `libs/java/http-server` | REST API           | Direct API exposure |
| `libs/java/database`    | Data persistence   | Custom data models  |
| `libs/java/validation`  | Input validation   | Custom validators   |

---

## 7. Version Compatibility Matrix

### 7.1 Current Versions

| Component               | Version | Compatibility |
| ----------------------- | ------- | ------------- |
| Virtual-Org Framework   | 1.0.0   | ✅ Compatible |
| Virtual-Org Workflow    | 1.0.0   | ✅ Compatible |
| Virtual-Org Integration | 1.0.0   | ✅ Compatible |
| AEP                     | 2.0.0   | ✅ Compatible |
| Platform libs           | 1.x     | ✅ Compatible |

### 7.2 Upgrade Policy

1. Virtual-Org follows **semantic versioning**
2. **Minor versions** (1.x.0) are backward compatible
3. **Patch versions** (1.0.x) are bug fixes only
4. **Major versions** (x.0.0) may break compatibility

### 7.3 Breaking Change Notification

Virtual-Org breaking changes are communicated via:

- `BREAKING_CHANGES.md` in Virtual-Org docs
- Deprecation warnings in code
- Migration guides for major versions

---

## 8. Testing with Virtual-Org

### 8.1 Test Base Classes

```java
// All Software-Org async tests MUST extend this
import com.ghatana.virtualorg.testing.VirtualOrgTestBase;

class EngineeringDepartmentTest extends VirtualOrgTestBase {

    @Test
    void shouldHandleCodeReviewTask() {
        // GIVEN
        var department = createTestDepartment("engineering");
        var task = createTestTask("code-review");

        // WHEN
        var result = runPromise(() -> department.executeTask(task));

        // THEN
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }
}
```

### 8.2 Mock Factories

```java
import com.ghatana.virtualorg.testing.MockFactories;

var mockDepartment = MockFactories.department("engineering");
var mockAgent = MockFactories.agent("senior-engineer");
var mockEvent = MockFactories.event(OrganizationEventType.TASK_CREATED);
```

---

## 9. Migration Guide

### 9.1 From Standalone to Virtual-Org

If migrating existing code to use Virtual-Org:

| Before                    | After                                                                   |
| ------------------------- | ----------------------------------------------------------------------- |
| Custom `Department` class | Extend `com.ghatana.virtualorg.framework.department.Department`         |
| Custom `Agent` class      | Extend `com.ghatana.virtualorg.framework.agent.BaseOrganizationalAgent` |
| Custom event model        | Use `com.ghatana.virtualorg.events.OrganizationEvent`                   |
| Custom workflow engine    | Use `com.ghatana.virtualorg.workflow.WorkflowEngine`                    |
| Custom configuration      | Use Virtual-Org YAML schema                                             |

### 9.2 Migration Steps

1. Add Virtual-Org dependencies to `build.gradle.kts`
2. Extend Virtual-Org base classes
3. Migrate event model to `OrganizationEvent`
4. Register departments/agents with Virtual-Org registry
5. Convert configurations to Virtual-Org schema
6. Update tests to use `VirtualOrgTestBase`

---

## 10. Appendix

### 10.1 Full Dependency Tree

```
products:software-org
├── products:virtual-org:libs:java:framework
│   ├── libs:java:common-utils
│   ├── libs:java:config-runtime
│   └── libs:java:event-runtime
├── products:virtual-org:libs:java:agent-implementations
│   ├── products:virtual-org:libs:java:framework
│   └── libs:java:ai-integration
├── products:virtual-org:libs:java:org-events
│   ├── products:virtual-org:libs:java:framework
│   └── libs:java:event-cloud
├── products:virtual-org:libs:workflow
│   └── products:virtual-org:libs:java:framework
├── products:virtual-org:libs:integration
│   ├── products:agentic-event-processor:operator-catalog
│   ├── libs:java:state
│   └── libs:java:event-cloud
├── products:virtual-org:libs:operator-adapter
│   └── products:agentic-event-processor:operator-catalog
├── libs:java:http-server (direct)
├── libs:java:database (direct)
├── libs:java:observability (transitive)
└── libs:java:auth (transitive)
```

### 10.2 Gradle Configuration

```groovy
// products/software-org/build.gradle.kts
dependencies {
    // Virtual-Org Framework (core)
    api(project(":products:virtual-org:libs:java:framework"))

    // Virtual-Org Agent Implementations
    implementation(project(":products:virtual-org:libs:java:agent-implementations"))

    // Virtual-Org Events
    api(project(":products:virtual-org:libs:java:org-events"))

    // Virtual-Org Workflow
    implementation(project(":products:virtual-org:libs:workflow"))

    // Virtual-Org Integration
    implementation(project(":products:virtual-org:libs:integration"))

    // Virtual-Org Operator Adapter
    implementation(project(":products:virtual-org:libs:operator-adapter"))

    // Direct platform dependencies
    implementation(project(":libs:java:http-server"))
    implementation(project(":libs:java:database"))
}
```

### 10.3 Quick Reference

| Need                | Virtual-Org Class         | Import                                                           |
| ------------------- | ------------------------- | ---------------------------------------------------------------- |
| Create department   | `Department`              | `com.ghatana.virtualorg.framework.department.Department`         |
| Create agent        | `BaseOrganizationalAgent` | `com.ghatana.virtualorg.framework.agent.BaseOrganizationalAgent` |
| Emit event          | `EventPublisher`          | `com.ghatana.virtualorg.events.EventPublisher`                   |
| Subscribe to events | `EventSubscriber`         | `com.ghatana.virtualorg.events.EventSubscriber`                  |
| Define workflow     | `WorkflowDefinition`      | `com.ghatana.virtualorg.workflow.WorkflowDefinition`             |
| Execute workflow    | `WorkflowEngine`          | `com.ghatana.virtualorg.workflow.WorkflowEngine`                 |
| Connect to AEP      | `AepEventBusConnector`    | `com.ghatana.virtualorg.integration.aep.AepEventBusConnector`    |
| Store state         | `CoreStateStoreAdapter`   | `com.ghatana.virtualorg.integration.state.CoreStateStoreAdapter` |
| Test async          | `VirtualOrgTestBase`      | `com.ghatana.virtualorg.testing.VirtualOrgTestBase`              |
