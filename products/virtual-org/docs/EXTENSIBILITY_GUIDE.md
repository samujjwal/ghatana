# Virtual-Org & Software-Org Extensibility Guide

**Version:** 1.0.0  
**Last Updated:** November 26, 2025  
**Status:** Living Document  
**Audience:** Plugin Developers, Platform Engineers, Integrators

---

## Executive Summary

This guide provides comprehensive instructions for extending both **Virtual-Org** (the base framework) and **Software-Org** (the reference plugin). Both are designed with **extensibility as a core principle**, allowing customization without modifying source code.

### Extensibility Philosophy

```
┌─────────────────────────────────────────────────────────────────────┐
│                    EXTENSIBILITY PHILOSOPHY                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  "EXTEND, DON'T MODIFY"                                            │
│                                                                     │
│  1. Framework code is IMMUTABLE (no direct modifications)          │
│  2. Extension points allow ALL customization needs                 │
│  3. Plugins can extend other plugins (layered extensibility)       │
│  4. Configuration > Code when possible                             │
│  5. Backwards compatibility is guaranteed                          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Table of Contents

1. [Extension Model Overview](#1-extension-model-overview)
2. [Virtual-Org Extension Points](#2-virtual-org-extension-points)
3. [Software-Org Extension Points](#3-software-org-extension-points)
4. [Creating a New Plugin](#4-creating-a-new-plugin)
5. [Extending Software-Org](#5-extending-software-org)
6. [Integration Development](#6-integration-development)
7. [Frontend Extensibility](#7-frontend-extensibility)
8. [Testing Extensions](#8-testing-extensions)
9. [Best Practices](#9-best-practices)
10. [Examples & Templates](#10-examples--templates)

---

## 1. Extension Model Overview

### 1.1 Layered Extension Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        LAYERED EXTENSION ARCHITECTURE                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  LAYER 4: Custom Extensions                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Your Custom Components                                              │   │
│  │  • Custom departments for Software-Org                              │   │
│  │  • Custom agents for Software-Org                                   │   │
│  │  • Custom integrations                                              │   │
│  │  • Custom UI widgets                                                │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │ extends                                 │
│                                   ▼                                         │
│  LAYER 3: Domain Plugin (e.g., Software-Org)                               │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Software-Org Plugin                                                 │   │
│  │  • 10 Departments (extensible)                                      │   │
│  │  • 11 Agents (extensible)                                           │   │
│  │  • AI Decision Engine (configurable)                                │   │
│  │  • Security Gates (extensible)                                      │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │ extends                                 │
│                                   ▼                                         │
│  LAYER 2: Virtual-Org Framework                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Virtual-Org Framework (IMMUTABLE)                                   │   │
│  │  • AbstractOrganization                                             │   │
│  │  • Department                                                       │   │
│  │  • BaseOrganizationalAgent                                          │   │
│  │  • WorkflowEngine                                                   │   │
│  │  • EventPublisher                                                   │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │ depends on                              │
│                                   ▼                                         │
│  LAYER 1: Platform Services                                                │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  AEP │ Observability │ Auth │ State │ AI Services                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Extension Types

| Type | Where | What You Extend | Example |
|------|-------|-----------------|---------|
| **Plugin** | Virtual-Org | Create new organization type | Healthcare-Org |
| **Component** | Software-Org | Add to existing plugin | Legal Department |
| **Integration** | Any layer | Connect external systems | GitHub, Jira |
| **Widget** | Frontend | Add UI components | Custom Dashboard |
| **Configuration** | Any layer | Change behavior via config | Security policies |

### 1.3 When to Use Each Layer

| Scenario | Layer to Extend | Example |
|----------|-----------------|---------|
| New industry/domain | Layer 2 (Virtual-Org) | Healthcare-Org plugin |
| New department for software org | Layer 3 (Software-Org) | Legal department |
| Custom agent for existing dept | Layer 3 (Software-Org) | Security Engineer agent |
| Connect external tool | Layer 3/4 | Slack integration |
| Custom dashboard widget | Layer 4 | Custom KPI chart |
| Behavior change | Configuration | Security gate threshold |

---

## 2. Virtual-Org Extension Points

### 2.1 Extension Point Summary

| Extension Point | Base Class/Interface | Purpose |
|-----------------|---------------------|---------|
| `AbstractOrganization` | Abstract class | New organization types |
| `Department` | Abstract class | New department types |
| `BaseOrganizationalAgent` | Abstract class | New agent roles |
| `OrganizationalAgent` | Interface | Custom agent contracts |
| `WorkflowDefinition` | Builder | Custom workflows |
| `EventPublisher` | Interface | Custom event routing |
| `OrganizationEvent` | Abstract class | Custom event types |
| `KpiDefinition` | Value object | Custom metrics |
| `IntegrationAdapter` | Interface | External system connections |
| `AgentToolProvider` | Interface | Agent capabilities |

### 2.2 Creating a New Organization Type

```java
/**
 * Step 1: Extend AbstractOrganization
 */
package com.example.healthcareorg;

import com.ghatana.virtualorg.framework.AbstractOrganization;
import com.ghatana.core.types.TenantId;

/**
 * Healthcare Organization Plugin
 *
 * @doc.type class
 * @doc.purpose Healthcare organization implementation
 * @doc.layer product
 * @doc.pattern Plugin
 */
public class HealthcareOrganization extends AbstractOrganization {
    
    public HealthcareOrganization(TenantId tenantId, String name) {
        super(tenantId, name);
    }
    
    @Override
    protected void initializeDepartments() {
        // Register healthcare-specific departments
        registerDepartment(new EmergencyDepartment(this));
        registerDepartment(new SurgeryDepartment(this));
        registerDepartment(new PharmacyDepartment(this));
        registerDepartment(new NursingDepartment(this));
        registerDepartment(new MedicalRecordsDepartment(this));
        registerDepartment(new AdministrationDepartment(this));
        registerDepartment(new ComplianceDepartment(this));
    }
    
    // Healthcare-specific methods
    public PatientFlow createPatientFlow(String patientId) {
        // ...
    }
}
```

### 2.3 Creating a New Department

```java
/**
 * Step 2: Extend Department for domain-specific departments
 */
package com.example.healthcareorg.departments;

import com.ghatana.virtualorg.framework.Department;
import com.ghatana.virtualorg.framework.DepartmentType;
import com.ghatana.virtualorg.framework.task.*;
import com.ghatana.virtualorg.framework.kpi.KpiDefinition;
import io.activej.promise.Promise;
import java.util.List;

public class EmergencyDepartment extends Department {
    
    public EmergencyDepartment(HealthcareOrganization organization) {
        super(organization, "Emergency", DepartmentType.custom("EMERGENCY"));
        
        // Register emergency department agents
        registerAgent(new ERDoctorAgent());
        registerAgent(new TriageNurseAgent());
        registerAgent(new EMTAgent());
    }
    
    @Override
    public Promise<TaskResult> processTask(Task task) {
        return switch (task.getType()) {
            case "patient_triage" -> handleTriage(task);
            case "emergency_treatment" -> handleTreatment(task);
            case "patient_admission" -> handleAdmission(task);
            case "patient_discharge" -> handleDischarge(task);
            default -> Promise.ofException(
                new UnsupportedTaskException(task.getType())
            );
        };
    }
    
    @Override
    public List<KpiDefinition> getKpis() {
        return List.of(
            KpiDefinition.of("triage_wait_time", "Triage Wait Time (min)", KpiType.DURATION),
            KpiDefinition.of("treatment_time", "Treatment Time (min)", KpiType.DURATION),
            KpiDefinition.of("patient_satisfaction", "Patient Satisfaction", KpiType.PERCENTAGE),
            KpiDefinition.of("bed_occupancy", "Bed Occupancy Rate", KpiType.PERCENTAGE),
            KpiDefinition.of("readmission_rate", "30-Day Readmission Rate", KpiType.PERCENTAGE)
        );
    }
}
```

### 2.4 Creating a New Agent

```java
/**
 * Step 3: Extend BaseOrganizationalAgent for domain-specific agents
 */
package com.example.healthcareorg.agents;

import com.ghatana.virtualorg.framework.agent.BaseOrganizationalAgent;
import com.ghatana.virtualorg.framework.hierarchy.*;
import com.ghatana.core.domain.event.Event;
import java.util.*;

public class ERDoctorAgent extends BaseOrganizationalAgent {
    
    public ERDoctorAgent(String id) {
        super(
            id,
            "1.0.0",
            
            // Role definition
            Role.of("ER Doctor", Layer.SPECIALIST),
            
            // Decision authority
            Authority.builder()
                .addDecision("diagnose_patient")
                .addDecision("prescribe_medication")
                .addDecision("order_tests")
                .addDecision("admit_patient")
                .addDecision("discharge_patient")
                .build(),
            
            // Escalation path
            EscalationPath.of(
                Role.of("Attending Physician", Layer.SPECIALIST),
                Role.of("Chief Medical Officer", Layer.EXECUTIVE)
            ),
            
            // Events subscribed to
            Set.of(
                "patient.triage.completed",
                "test.results.ready",
                "patient.condition.changed"
            ),
            
            // Events produced
            Set.of(
                "diagnosis.completed",
                "treatment.prescribed",
                "patient.admitted",
                "patient.discharged"
            )
        );
    }
    
    @Override
    protected List<Event> doHandle(Event event, AgentExecutionContext context) {
        return switch (event.getType()) {
            case "patient.triage.completed" -> handleTriageComplete(event, context);
            case "test.results.ready" -> handleTestResults(event, context);
            case "patient.condition.changed" -> handleConditionChange(event, context);
            default -> Collections.emptyList();
        };
    }
    
    private List<Event> handleTriageComplete(Event event, AgentExecutionContext context) {
        PatientData patient = extractPatient(event);
        
        // Evaluate patient condition
        DiagnosisResult diagnosis = evaluatePatient(patient);
        
        // If critical, escalate
        if (diagnosis.isCritical() && !hasAuthority("critical_care")) {
            return escalateEvent(event, context);
        }
        
        // Return diagnosis event
        return List.of(
            Event.builder()
                .type("diagnosis.completed")
                .payload(diagnosis)
                .correlationId(event.getCorrelationId())
                .build()
        );
    }
}
```

### 2.5 Plugin Registration

```java
/**
 * Step 4: Create plugin manifest
 */
package com.example.healthcareorg;

import com.ghatana.virtualorg.framework.plugin.VirtualOrgPlugin;

public class HealthcareOrgPlugin {
    
    public static VirtualOrgPlugin create() {
        return VirtualOrgPlugin.builder()
            // Register organization type
            .organizationType(HealthcareOrganization.class)
            
            // Register departments
            .department("emergency", EmergencyDepartment.class)
            .department("surgery", SurgeryDepartment.class)
            .department("pharmacy", PharmacyDepartment.class)
            .department("nursing", NursingDepartment.class)
            .department("medical-records", MedicalRecordsDepartment.class)
            
            // Register agents
            .agent("er-doctor", ERDoctorAgent.class)
            .agent("triage-nurse", TriageNurseAgent.class)
            .agent("surgeon", SurgeonAgent.class)
            .agent("pharmacist", PharmacistAgent.class)
            
            // Register workflows
            .workflow("patient-admission", PatientAdmissionWorkflow.class)
            .workflow("surgery-scheduling", SurgerySchedulingWorkflow.class)
            .workflow("discharge-planning", DischargePlanningWorkflow.class)
            
            // Register integrations
            .integration("ehr", EHRIntegration.class)
            .integration("lab-systems", LabSystemsIntegration.class)
            .integration("scheduling", SchedulingIntegration.class)
            
            .build();
    }
}
```

---

## 3. Software-Org Extension Points

### 3.1 Extension Point Summary

Software-Org provides additional extension points beyond Virtual-Org:

| Extension Point | Interface | Purpose |
|-----------------|-----------|---------|
| `SoftwareOrgDepartment` | Abstract class | Software-specific departments |
| `SoftwareOrgAgent` | Abstract class | Software-specific agents |
| `CrossDepartmentFlow` | Interface | Event flows between departments |
| `SecurityGate` | Interface | Security checkpoints |
| `SecurityPolicy` | Interface | Security policies |
| `AIDecisionEngine` | Configurable | Decision behavior |
| `IntegrationProvider` | Interface | Tool integrations |
| `DashboardWidget` | Interface | UI widgets |
| `KpiDefinition` | Value object | Custom KPIs |

### 3.2 Adding a Department to Software-Org

```java
/**
 * Add a Legal Department to Software-Org
 */
package com.example.softwareorg.legal;

import com.ghatana.softwareorg.SoftwareOrgDepartment;
import com.ghatana.softwareorg.SoftwareOrganization;
import com.ghatana.virtualorg.framework.DepartmentType;

public class LegalDepartment extends SoftwareOrgDepartment {
    
    public LegalDepartment(SoftwareOrganization organization) {
        super(organization, "Legal", DepartmentType.custom("LEGAL"));
        
        registerAgent(new LegalCounselAgent());
        registerAgent(new ContractReviewerAgent());
        registerAgent(new IPAttorneyAgent());
    }
    
    @Override
    public Promise<TaskResult> processTask(Task task) {
        return switch (task.getType()) {
            case "contract_review" -> handleContractReview(task);
            case "ip_assessment" -> handleIPAssessment(task);
            case "compliance_review" -> handleComplianceReview(task);
            case "legal_opinion" -> handleLegalOpinion(task);
            default -> super.processTask(task);
        };
    }
    
    @Override
    public List<KpiDefinition> getKpis() {
        return List.of(
            KpiDefinition.of("contract_cycle_time", "Contract Cycle Time (Days)", KpiType.DURATION),
            KpiDefinition.of("ip_filings", "IP Filings (YTD)", KpiType.COUNT),
            KpiDefinition.of("compliance_score", "Compliance Score", KpiType.PERCENTAGE),
            KpiDefinition.of("legal_spend", "Legal Spend ($)", KpiType.CURRENCY)
        );
    }
}
```

### 3.3 Adding a Cross-Department Flow

```java
/**
 * Add Sales → Legal flow for contract review
 */
package com.example.softwareorg.flows;

import com.ghatana.softwareorg.CrossDepartmentFlow;
import com.ghatana.virtualorg.framework.event.EventPublisher;

public class SalesToLegalFlow implements CrossDepartmentFlow {
    
    private final EventPublisher eventPublisher;
    private final AIDecisionEngine decisionEngine;
    
    @Override
    public String getSourceDepartment() {
        return "SALES";
    }
    
    @Override
    public String getTargetDepartment() {
        return "LEGAL";
    }
    
    @Override
    public Set<String> getTriggerEvents() {
        return Set.of(
            "deal.contract.needed",
            "deal.terms.custom",
            "customer.special.agreement"
        );
    }
    
    @Override
    public Promise<FlowResult> process(Event event) {
        // Analyze deal complexity
        AIAnalysis analysis = decisionEngine.analyze(event);
        
        // Determine legal review priority
        TaskPriority priority = analysis.getRiskScore() > 0.7 
            ? TaskPriority.HIGH 
            : TaskPriority.NORMAL;
        
        // Create legal review task
        Task legalTask = Task.builder()
            .type("contract_review")
            .priority(priority)
            .context(event.getPayload())
            .sla(Duration.ofDays(priority == TaskPriority.HIGH ? 1 : 3))
            .build();
        
        // Route to Legal
        eventPublisher.publish(
            Event.builder()
                .type("contract.review.requested")
                .source("sales-legal-flow")
                .correlationId(event.getCorrelationId())
                .payload(legalTask)
                .build()
        );
        
        return Promise.of(FlowResult.success());
    }
}
```

### 3.4 Adding a Security Gate

```java
/**
 * Add a Contract Compliance Security Gate
 */
package com.example.softwareorg.security;

import com.ghatana.softwareorg.SecurityGate;
import com.ghatana.softwareorg.GateContext;
import com.ghatana.softwareorg.GateResult;

public class ContractComplianceGate implements SecurityGate {
    
    private final ComplianceChecker complianceChecker;
    
    @Override
    public String getName() {
        return "Contract Compliance";
    }
    
    @Override
    public String getStage() {
        return "contract_signing";
    }
    
    @Override
    public Promise<GateResult> evaluate(GateContext context) {
        Contract contract = context.getContract();
        
        // Check compliance
        ComplianceResult result = complianceChecker.check(contract);
        
        if (result.hasCriticalIssues()) {
            return Promise.of(GateResult.blocked(
                "Critical compliance issues found",
                result.getCriticalIssues()
            ));
        }
        
        if (result.hasWarnings()) {
            return Promise.of(GateResult.requiresApproval(
                "Compliance warnings - requires Legal Counsel approval",
                result.getWarnings()
            ));
        }
        
        return Promise.of(GateResult.passed());
    }
}
```

### 3.5 Software-Org Plugin Registration

```java
/**
 * Register custom extensions with Software-Org
 */
package com.example.softwareorg;

import com.ghatana.softwareorg.SoftwareOrgPlugin;
import com.ghatana.softwareorg.SoftwareOrgRegistry;

public class LegalExtension implements SoftwareOrgPlugin {
    
    @Override
    public void register(SoftwareOrgRegistry registry) {
        // Department
        registry.registerDepartment(
            "legal",
            LegalDepartment.class,
            DepartmentConfig.builder()
                .displayName("Legal Department")
                .icon("⚖️")
                .color("#6B7280")
                .build()
        );
        
        // Agents
        registry.registerAgent("legal-counsel", LegalCounselAgent.class);
        registry.registerAgent("contract-reviewer", ContractReviewerAgent.class);
        registry.registerAgent("ip-attorney", IPAttorneyAgent.class);
        
        // Flows
        registry.registerFlow("sales-to-legal", SalesToLegalFlow.class);
        registry.registerFlow("engineering-to-legal", EngineeringToLegalFlow.class);
        
        // Security Gates
        registry.registerSecurityGate("contract-compliance", ContractComplianceGate.class);
        
        // Integrations
        registry.registerIntegration("docusign", DocuSignIntegration.class);
        registry.registerIntegration("contract-management", ContractManagementIntegration.class);
    }
}
```

---

## 4. Creating a New Plugin

### 4.1 Project Structure

```
products/your-org/
├── libs/java/
│   ├── framework/                  # Your plugin framework extensions
│   │   └── src/main/java/com/yourcompany/yourorg/framework/
│   │       ├── YourOrganization.java
│   │       └── YourOrgPlugin.java
│   │
│   ├── departments/                # Your departments
│   │   └── src/main/java/com/yourcompany/yourorg/departments/
│   │       ├── dept1/
│   │       ├── dept2/
│   │       └── ...
│   │
│   └── agents/                     # Your agents
│       └── src/main/java/com/yourcompany/yourorg/agents/
│           ├── Agent1.java
│           ├── Agent2.java
│           └── ...
│
├── apps/
│   ├── backend/                    # Node.js User API (optional)
│   └── web/                        # React Frontend (optional)
│
├── contracts/proto/                # API contracts
├── docs/                           # Documentation
└── build.gradle.kts                # Build configuration
```

### 4.2 Gradle Configuration

```kotlin
// products/your-org/libs/java/framework/build.gradle.kts
plugins {
    id("java-library")
}

dependencies {
    // REQUIRED: Virtual-Org Framework
    implementation(project(":products:virtual-org:libs:java:framework"))
    
    // Optional: Software-Org (if extending)
    implementation(project(":products:software-org:libs:java:software-org"))
    
    // Platform services
    implementation(project(":libs:java:observability"))
    implementation(project(":libs:java:http-server"))
    implementation(project(":libs:java:ai-integration"))
    
    // Testing (REQUIRED for async tests)
    testImplementation(project(":libs:java:activej-test-utils"))
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}
```

### 4.3 Minimal Plugin Example

```java
// products/your-org/libs/java/framework/src/main/java/com/yourcompany/yourorg/YourOrganization.java
package com.yourcompany.yourorg;

import com.ghatana.virtualorg.framework.AbstractOrganization;
import com.ghatana.core.types.TenantId;

public class YourOrganization extends AbstractOrganization {
    
    public YourOrganization(TenantId tenantId, String name) {
        super(tenantId, name);
    }
    
    @Override
    protected void initializeDepartments() {
        registerDepartment(new CoreDepartment(this));
        // Add more departments as needed
    }
}
```

---

## 5. Extending Software-Org

### 5.1 When to Extend Software-Org

Extend Software-Org when you need:
- Additional departments for software organizations
- Additional agents with new roles
- Custom event flows between departments
- Additional security gates
- Custom integrations for dev tools
- Industry-specific software org features

### 5.2 Extension Checklist

- [ ] Identify the extension point (Department, Agent, Flow, Gate)
- [ ] Create extension class extending appropriate base
- [ ] Implement required abstract methods
- [ ] Add JavaDoc with @doc tags
- [ ] Create tests using EventloopTestBase
- [ ] Register extension via SoftwareOrgPlugin
- [ ] Update documentation

---

## 6. Integration Development

### 6.1 Integration Interface

```java
/**
 * Integration adapter for external systems
 */
public interface IntegrationAdapter {
    
    String getId();
    String getName();
    String getDescription();
    
    // Lifecycle
    Promise<Void> initialize(IntegrationConfig config);
    Promise<Void> shutdown();
    
    // Health
    HealthStatus getHealth();
    
    // Operations (specific to integration type)
}
```

### 6.2 Example: GitHub Integration

```java
package com.ghatana.softwareorg.integration.github;

import com.ghatana.softwareorg.IntegrationAdapter;

public class GitHubIntegration implements IntegrationAdapter {
    
    private GitHubClient client;
    
    @Override
    public String getId() {
        return "github";
    }
    
    @Override
    public Promise<Void> initialize(IntegrationConfig config) {
        this.client = GitHubClient.create(
            config.getString("token"),
            config.getString("org")
        );
        return Promise.complete();
    }
    
    // PR events
    public Promise<List<PullRequest>> getPullRequests(String repo) {
        return client.getPullRequests(repo);
    }
    
    // Webhook handler
    public void handleWebhook(WebhookEvent event) {
        switch (event.getType()) {
            case "pull_request" -> handlePREvent(event);
            case "push" -> handlePushEvent(event);
            case "issue" -> handleIssueEvent(event);
        }
    }
    
    private void handlePREvent(WebhookEvent event) {
        // Emit event to Software-Org
        eventPublisher.publish(
            Event.builder()
                .type("github.pr." + event.getAction())
                .source("github-integration")
                .payload(event.getPayload())
                .build()
        );
    }
}
```

---

## 7. Frontend Extensibility

### 7.1 Plugin Slot System

```tsx
// Frontend plugin registration
import { pluginRegistry } from '@ghatana/software-org-ui';

// Register a custom widget
pluginRegistry.register({
    id: 'legal-contracts-widget',
    slot: 'dashboard-widget',
    component: LegalContractsWidget,
    config: {
        title: 'Legal Contracts',
        size: 'medium',
        refreshInterval: 30000,
        department: 'legal',
    }
});

// Register a custom page
pluginRegistry.register({
    id: 'legal-dashboard',
    slot: 'department-page',
    route: '/legal',
    component: LegalDashboard,
    navItem: {
        label: 'Legal',
        icon: ScaleIcon,
        position: 8,
    }
});

// Register a custom sidebar item
pluginRegistry.register({
    id: 'legal-nav',
    slot: 'sidebar-nav',
    component: LegalNavItem,
    config: {
        position: 8,
    }
});
```

### 7.2 Using Plugin Slots

```tsx
// In your dashboard component
import { PluginSlot } from '@ghatana/software-org-ui';

function Dashboard() {
    return (
        <div className="dashboard">
            {/* Core widgets */}
            <DepartmentMetrics />
            
            {/* Plugin widgets - automatically renders registered widgets */}
            <PluginSlot 
                name="dashboard-widget" 
                filter={{ department: currentDepartment }} 
            />
        </div>
    );
}
```

### 7.3 Creating Custom Widgets

```tsx
// Custom widget component
import { DashboardWidget } from '@ghatana/software-org-ui';

export function LegalContractsWidget() {
    const { data, isLoading } = useQuery(['legal-contracts'], fetchContracts);
    
    return (
        <DashboardWidget
            title="Legal Contracts"
            icon={<DocumentIcon />}
            isLoading={isLoading}
        >
            <ContractList contracts={data} />
            <ContractMetrics contracts={data} />
        </DashboardWidget>
    );
}
```

---

## 8. Testing Extensions

### 8.1 Testing Requirements

All extensions MUST follow these testing requirements:

| Requirement | Tool | Coverage Target |
|-------------|------|-----------------|
| Unit tests | JUnit 5 | 80%+ |
| Async tests | EventloopTestBase | 100% async code |
| Integration tests | TestContainers | Critical paths |
| Contract tests | Pact/WireMock | All integrations |

### 8.2 Testing Async Code (MANDATORY)

```java
/**
 * ALL async tests MUST extend EventloopTestBase
 * NEVER use .getResult() on a Promise
 */
package com.example.yourorg.departments;

import com.ghatana.activej.test.EventloopTestBase;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class YourDepartmentTest extends EventloopTestBase {
    
    @Test
    void shouldProcessTask() {
        // GIVEN
        YourDepartment department = new YourDepartment(mockOrganization());
        Task task = Task.create("test-task", "sample_work");
        
        // WHEN - Use runPromise() for async operations
        TaskResult result = runPromise(() -> department.processTask(task));
        
        // THEN
        assertThat(result.isCompleted()).isTrue();
        assertThat(result.getOutput()).contains("expected output");
    }
    
    @Test
    void shouldEmitEvents() {
        // GIVEN
        YourDepartment department = new YourDepartment(mockOrganization());
        EventCaptor captor = EventCaptor.create();
        department.getEventPublisher().subscribe(captor);
        
        // WHEN
        runPromise(() -> department.processTask(sampleTask()));
        
        // THEN
        assertThat(captor.getCapturedEvents())
            .hasSize(1)
            .allMatch(e -> e.getType().equals("expected.event.type"));
    }
}
```

### 8.3 Testing Agents

```java
class YourAgentTest extends EventloopTestBase {
    
    private YourAgent agent;
    private MockAgentExecutionContext context;
    
    @BeforeEach
    void setUp() {
        agent = new YourAgent("test-agent");
        context = MockAgentExecutionContext.create();
    }
    
    @Test
    void shouldHandleExpectedEvent() {
        // GIVEN
        Event event = Event.builder()
            .type("expected.event.type")
            .payload(Map.of("key", "value"))
            .build();
        
        // WHEN
        List<Event> results = runPromise(() -> 
            Promise.of(agent.handle(event, context))
        );
        
        // THEN
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getType()).isEqualTo("response.event.type");
    }
    
    @Test
    void shouldEscalateWhenBeyondAuthority() {
        // GIVEN
        Event event = Event.builder()
            .type("beyond.authority.event")
            .build();
        
        // WHEN
        List<Event> results = runPromise(() -> 
            Promise.of(agent.handle(event, context))
        );
        
        // THEN
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getType()).contains("escalation");
    }
}
```

---

## 9. Best Practices

### 9.1 Extension Development Rules

| Rule | Description | Rationale |
|------|-------------|-----------|
| **Extend, Don't Modify** | Never modify framework code | Maintainability, upgrades |
| **Use EventPublisher** | All cross-component communication | Loose coupling, auditability |
| **Test with EventloopTestBase** | All async tests | Correct async handling |
| **Document with @doc tags** | All public APIs | Discoverability, maintenance |
| **Emit Events** | For all significant actions | Observability, integration |
| **Idempotent Handlers** | Event handlers must be idempotent | Reliability, replay |
| **Clear Boundaries** | Keep product code in product modules | Separation of concerns |
| **Configuration > Code** | Prefer config over hardcoding | Flexibility |

### 9.2 Documentation Requirements

Every public class MUST have:

```java
/**
 * Brief description of the class.
 *
 * <p><b>Purpose</b><br>
 * What this class does and why.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Example code
 * }</pre>
 *
 * @doc.type [class|interface|record|enum]
 * @doc.purpose [One-line description]
 * @doc.layer [core|product|platform]
 * @doc.pattern [Service|Repository|ValueObject|etc]
 */
```

### 9.3 Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Organization | `{Domain}Organization` | `HealthcareOrganization` |
| Department | `{Name}Department` | `EmergencyDepartment` |
| Agent | `{Role}Agent` | `ERDoctorAgent` |
| Flow | `{Source}To{Target}Flow` | `SalesToLegalFlow` |
| Gate | `{Name}Gate` | `ContractComplianceGate` |
| Event | `{noun}.{verb}.{state}` | `contract.review.completed` |

---

## 10. Examples & Templates

### 10.1 Complete Department Template

```java
package com.example.yourorg.departments;

import com.ghatana.virtualorg.framework.AbstractOrganization;
import com.ghatana.virtualorg.framework.Department;
import com.ghatana.virtualorg.framework.DepartmentType;
import com.ghatana.virtualorg.framework.task.*;
import com.ghatana.virtualorg.framework.kpi.KpiDefinition;
import io.activej.promise.Promise;
import java.util.List;

/**
 * Template Department Implementation.
 *
 * @doc.type class
 * @doc.purpose Template for department implementations
 * @doc.layer product
 * @doc.pattern Department Extension
 */
public class TemplateDepartment extends Department {
    
    public TemplateDepartment(AbstractOrganization organization) {
        super(organization, "Template", DepartmentType.custom("TEMPLATE"));
        
        // Register agents
        registerAgent(new TemplateAgent1());
        registerAgent(new TemplateAgent2());
    }
    
    @Override
    public Promise<TaskResult> processTask(Task task) {
        return switch (task.getType()) {
            case "task_type_1" -> handleTask1(task);
            case "task_type_2" -> handleTask2(task);
            default -> Promise.ofException(
                new UnsupportedTaskException(task.getType())
            );
        };
    }
    
    @Override
    public List<KpiDefinition> getKpis() {
        return List.of(
            KpiDefinition.of("kpi_1", "KPI 1 Name", KpiType.COUNT),
            KpiDefinition.of("kpi_2", "KPI 2 Name", KpiType.PERCENTAGE)
        );
    }
    
    private Promise<TaskResult> handleTask1(Task task) {
        // Implementation
        return Promise.of(TaskResult.completed(task.getId(), "Completed"));
    }
    
    private Promise<TaskResult> handleTask2(Task task) {
        // Implementation
        return Promise.of(TaskResult.completed(task.getId(), "Completed"));
    }
}
```

### 10.2 Complete Agent Template

```java
package com.example.yourorg.agents;

import com.ghatana.virtualorg.framework.agent.BaseOrganizationalAgent;
import com.ghatana.virtualorg.framework.hierarchy.*;
import com.ghatana.core.domain.event.Event;
import com.ghatana.core.agent.registry.AgentExecutionContext;
import java.util.*;

/**
 * Template Agent Implementation.
 *
 * @doc.type class
 * @doc.purpose Template for agent implementations
 * @doc.layer product
 * @doc.pattern Agent Extension
 */
public class TemplateAgent extends BaseOrganizationalAgent {
    
    public TemplateAgent(String id) {
        super(
            id,
            "1.0.0",
            Role.of("Template Role", Layer.INDIVIDUAL_CONTRIBUTOR),
            Authority.builder()
                .addDecision("decision_1")
                .addDecision("decision_2")
                .build(),
            EscalationPath.of(
                Role.of("Manager Role", Layer.MANAGEMENT)
            ),
            Set.of("input.event.1", "input.event.2"),
            Set.of("output.event.1", "output.event.2")
        );
    }
    
    @Override
    protected List<Event> doHandle(Event event, AgentExecutionContext context) {
        return switch (event.getType()) {
            case "input.event.1" -> handleEvent1(event, context);
            case "input.event.2" -> handleEvent2(event, context);
            default -> {
                if (!hasAuthority(event.getType())) {
                    yield escalateEvent(event, context);
                }
                yield Collections.emptyList();
            }
        };
    }
    
    private List<Event> handleEvent1(Event event, AgentExecutionContext context) {
        // Implementation
        return List.of(
            Event.builder()
                .type("output.event.1")
                .payload(Map.of("result", "success"))
                .correlationId(event.getCorrelationId())
                .build()
        );
    }
    
    private List<Event> handleEvent2(Event event, AgentExecutionContext context) {
        // Implementation
        return Collections.emptyList();
    }
}
```

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2025-11-26 | Platform Team | Initial extensibility guide |
