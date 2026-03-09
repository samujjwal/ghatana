# Configuration-Driven Organization Architecture

> **Version**: 1.0.0  
> **Last Updated**: 2025-01-XX  
> **Status**: Specification - Approved for Implementation

## Executive Summary

This document specifies the **Configuration-Driven Architecture** for Virtual-Org, enabling organizations, departments, agents, and their interactions to be defined declaratively through YAML/JSON configuration files instead of code.

## Table of Contents

1. [Overview & Goals](#1-overview--goals)
2. [Configuration Schema](#2-configuration-schema)
3. [Organization Configuration](#3-organization-configuration)
4. [Department Configuration](#4-department-configuration)
5. [Agent Configuration](#5-agent-configuration)
6. [Interaction & Workflow Configuration](#6-interaction--workflow-configuration)
7. [Action Configuration](#7-action-configuration) *(NEW)*
8. [Persona Configuration](#8-persona-configuration) *(NEW)*
9. [Task Configuration](#9-task-configuration) *(NEW)*
10. [Result Processing Configuration](#10-result-processing-configuration) *(NEW)*
11. [Agent Lifecycle Configuration](#11-agent-lifecycle-configuration) *(NEW)*
12. [Interaction Protocol Configuration](#12-interaction-protocol-configuration) *(NEW)*
13. [Configuration Registry](#13-configuration-registry) *(NEW)*
14. [Java Implementation Design](#14-java-implementation-design)
15. [Configuration Loading & Validation](#15-configuration-loading--validation)
16. [Hot Reload & Runtime Updates](#16-hot-reload--runtime-updates)
17. [Migration Guide](#17-migration-guide)
18. [Examples](#18-examples)

---

## 1. Overview & Goals

### 1.1 Problem Statement

Currently, Virtual-Org requires Java code to:
- Create organizations (`new SoftwareOrganization(...)`)
- Define departments (`new EngineeringDepartment(...)`)
- Configure agents (hardcoded in department classes)
- Define workflows (programmatic builder API)
- Set up cross-department interactions (code-based)

This creates coupling between **organizational structure** (which should be data) and **framework behavior** (which is code).

### 1.2 Goals

| Goal | Description |
|------|-------------|
| **G1** | Define entire organizations via YAML without writing Java code |
| **G2** | Support dynamic agent configuration (roles, capabilities, AI prompts) |
| **G3** | Enable declarative workflow/interaction definitions |
| **G4** | Provide hot-reload for configuration changes without restarts |
| **G5** | Maintain full type-safety through schema validation |
| **G6** | Preserve backward compatibility with code-based approach |

### 1.3 Non-Goals

- Replacing the code-based approach entirely (both will coexist)
- Runtime code generation (configurations map to existing Java types)
- Visual UI configuration builder (future scope)

---

## 2. Configuration Schema

### 2.1 File Structure

```
config/
├── organization.yaml           # Main organization definition
├── departments/
│   ├── engineering.yaml        # Department configs
│   ├── qa.yaml
│   └── ...
├── agents/
│   ├── cto-agent.yaml          # Agent definitions
│   ├── lead-engineer.yaml
│   └── ...
├── workflows/
│   ├── sprint-planning.yaml    # Cross-department workflows
│   ├── incident-response.yaml
│   └── ...
├── interactions/
│   ├── eng-qa-handoff.yaml     # Department interactions
│   └── ...
└── schema/
    └── virtual-org-schema.json # JSON Schema for validation
```

### 2.2 Configuration Precedence

1. **Environment Variables** (highest priority)
2. **System Properties** (`-Dvirtualorg.config.*`)
3. **Profile-Specific Config** (`organization-{profile}.yaml`)
4. **Default Config** (`organization.yaml`)
5. **Code Defaults** (lowest priority)

### 2.3 Variable Interpolation

Configuration files support variable interpolation:

```yaml
organization:
  name: ${ORG_NAME:Acme Software}        # Env var with default
  tenant: ${TENANT_ID}                    # Required env var
  settings:
    max_agents: ${sys:virtualorg.max_agents:100}  # System property
    feature_flags: ${ref:feature-flags.yaml}      # File reference
```

---

## 3. Organization Configuration

### 3.1 Schema Definition

```yaml
# organization.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: Organization
metadata:
  name: acme-software-org
  namespace: ${TENANT_ID}
  labels:
    environment: production
    version: "1.0.0"

spec:
  # Basic Information
  displayName: "Acme Software Inc."
  description: "Full-stack software development organization"
  
  # Organization Structure
  structure:
    type: hierarchical  # hierarchical | flat | matrix
    maxDepth: 3         # Maximum department nesting
    
  # Global Settings
  settings:
    defaultTimezone: "America/New_York"
    workingHours:
      start: "09:00"
      end: "17:00"
      days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
    
    # Event Configuration
    events:
      enabled: true
      publishTo: aep  # aep | kafka | memory
      
    # KPI Tracking
    kpis:
      enabled: true
      aggregationInterval: PT1H  # ISO-8601 duration
      
    # Human-in-the-Loop
    hitl:
      enabled: true
      defaultApprovalTimeout: PT24H
      escalationPolicy: manager-chain
      
  # Department References
  departments:
    - ref: departments/engineering.yaml
    - ref: departments/qa.yaml
    - ref: departments/devops.yaml
    - ref: departments/support.yaml
    - ref: departments/sales.yaml
    - ref: departments/marketing.yaml
    - ref: departments/product.yaml
    - ref: departments/finance.yaml
    - ref: departments/hr.yaml
    - ref: departments/compliance.yaml
    
  # Cross-Department Workflow References
  workflows:
    - ref: workflows/sprint-planning.yaml
    - ref: workflows/incident-response.yaml
    - ref: workflows/release-management.yaml
    
  # Interaction Definitions
  interactions:
    - ref: interactions/eng-qa-handoff.yaml
    - ref: interactions/support-engineering-escalation.yaml
```

### 3.2 Inline vs. Reference Configuration

Both inline and file-reference configurations are supported:

```yaml
# Inline department (for simple cases)
departments:
  - inline:
      name: Engineering
      type: ENGINEERING
      agents:
        - name: Lead Engineer
          role: lead
          
# File reference (for complex cases)
departments:
  - ref: departments/engineering.yaml
```

---

## 4. Department Configuration

### 4.1 Department Schema

```yaml
# departments/engineering.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: Department
metadata:
  name: engineering
  labels:
    category: technical
    
spec:
  # Basic Info
  displayName: "Engineering"
  type: ENGINEERING  # Must match DepartmentType enum
  description: "Software development and architecture"
  
  # Organizational Position
  hierarchy:
    parent: null        # Top-level department
    children: []        # Sub-departments (optional)
    reportingTo: cto    # Agent ID for reporting
    
  # Department Settings
  settings:
    maxAgents: 50
    autoScaleAgents: false
    taskAssignment:
      strategy: round-robin  # round-robin | least-loaded | capability-match | custom
      customStrategyClass: null
      
  # Agent Definitions
  agents:
    - ref: agents/cto-agent.yaml
    - ref: agents/lead-engineer.yaml
    - ref: agents/senior-engineer.yaml
    - ref: agents/engineer.yaml
    
  # Department-Specific Workflows
  workflows:
    - name: code-review
      steps:
        - name: submit-pr
          agent: engineer
          action: create_pull_request
        - name: review
          agent: lead-engineer
          action: review_code
          waitFor: [submit-pr]
        - name: approve
          agent: senior-engineer
          action: approve_merge
          waitFor: [review]
          
  # KPI Definitions
  kpis:
    - name: velocity_story_points
      displayName: "Sprint Velocity"
      type: gauge
      unit: points
      target: 50
      warningThreshold: 40
      criticalThreshold: 30
      
    - name: code_coverage
      displayName: "Code Coverage"
      type: percentage
      target: 80
      warningThreshold: 70
      criticalThreshold: 60
      
    - name: pr_cycle_time
      displayName: "PR Cycle Time"
      type: duration
      unit: hours
      target: 24
      warningThreshold: 48
      criticalThreshold: 72
      
  # Task Types This Department Handles
  taskTypes:
    - name: feature_development
      priority: normal
      slaHours: 40
      requiredCapabilities: [java, react, database]
      
    - name: bug_fix
      priority: high
      slaHours: 8
      requiredCapabilities: [debugging, java]
      
    - name: code_review
      priority: normal
      slaHours: 4
      requiredCapabilities: [code_review]
```

### 4.2 Department Type Extension

For custom department types beyond the enum:

```yaml
# departments/custom-analytics.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: Department
metadata:
  name: analytics

spec:
  displayName: "Analytics"
  type: CUSTOM
  customTypeDefinition:
    code: ANALYTICS
    displayName: "Data Analytics"
    description: "Business intelligence and data analysis"
    capabilities: [data_analysis, reporting, ml_models]
```

---

## 5. Agent Configuration

### 5.1 Agent Schema

```yaml
# agents/cto-agent.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: Agent
metadata:
  name: cto-agent
  labels:
    level: executive
    ai-enabled: "true"

spec:
  # Basic Identity
  displayName: "CTO Agent"
  description: "Chief Technology Officer - Strategic technical leadership"
  
  # Role Configuration
  role:
    name: CTO
    level: executive  # executive | manager | lead | senior | junior | intern
    title: "Chief Technology Officer"
    
  # Department Assignment
  department: engineering
  reportingTo: ceo-agent  # Supervisor agent
  directReports:          # Agents reporting to this one
    - lead-engineer
    - architect
    
  # Authority & Permissions
  authority:
    decisionScope: organization  # organization | department | team | individual
    budgetLimit: 1000000
    canApprove:
      - architecture_decisions
      - technology_adoption
      - team_expansion
      - vendor_selection
    canEscalateTo:
      - ceo-agent
      - board
      
  # Capabilities & Skills
  capabilities:
    primary:
      - technical_strategy
      - architecture_review
      - technology_evaluation
      - team_leadership
    secondary:
      - vendor_management
      - budget_planning
      
  # AI Configuration (for AI-powered agents)
  ai:
    enabled: true
    provider: openai  # openai | anthropic | bedrock | local
    model: gpt-4-turbo
    
    # System Prompt
    systemPrompt: |
      You are a CTO agent for a software organization.
      Your responsibilities include:
      - Making strategic technology decisions
      - Reviewing architecture proposals
      - Evaluating new technologies
      - Guiding technical direction
      
      Decision Framework:
      1. Consider technical merit
      2. Evaluate cost implications
      3. Assess team capabilities
      4. Analyze risk factors
      
    # Response Configuration
    responseConfig:
      maxTokens: 2000
      temperature: 0.7
      topP: 0.9
      
    # Tool Access
    tools:
      - name: search_documentation
        enabled: true
      - name: query_metrics
        enabled: true
      - name: create_architecture_diagram
        enabled: true
        
    # Memory & Context
    memory:
      type: long-term  # short-term | long-term | hybrid
      contextWindow: 50  # Number of past interactions to remember
      
  # Workload Configuration
  workload:
    maxConcurrentTasks: 5
    taskPrioritization: importance-urgency  # fifo | lifo | importance-urgency | deadline
    
  # Availability
  availability:
    schedule:
      type: business-hours  # always | business-hours | custom
    responseTime:
      target: PT1H      # Target response time
      maximum: PT4H     # Maximum acceptable
      
  # Human-in-the-Loop Settings
  hitl:
    requireApprovalFor:
      - decisions_over_100k
      - external_vendor_contracts
      - security_exceptions
    approvalChain:
      - ceo-agent
      - board
      
  # Event Subscriptions
  subscriptions:
    - event: architecture_proposal_submitted
      action: review_and_decide
    - event: technology_evaluation_requested
      action: evaluate_technology
    - event: security_incident_escalated
      action: coordinate_response
```

### 5.2 Agent Templates

Define reusable agent templates:

```yaml
# agents/templates/engineer-template.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: AgentTemplate
metadata:
  name: engineer-template

spec:
  baseConfig:
    department: engineering
    role:
      level: individual
    capabilities:
      primary: [coding, debugging, testing]
    ai:
      enabled: true
      provider: openai
      model: gpt-4-turbo
    workload:
      maxConcurrentTasks: 3
```

```yaml
# agents/senior-engineer.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: Agent
metadata:
  name: senior-engineer

spec:
  template: engineer-template  # Inherit from template
  
  # Override specific fields
  displayName: "Senior Software Engineer"
  role:
    level: senior
    title: "Senior Software Engineer"
  capabilities:
    primary:
      - $inherit  # Include template capabilities
      - code_review
      - mentoring
      - architecture_design
```

---

## 6. Interaction & Workflow Configuration

### 6.1 Department Interaction Schema

```yaml
# interactions/eng-qa-handoff.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: DepartmentInteraction
metadata:
  name: engineering-qa-handoff
  labels:
    flow-type: handoff

spec:
  displayName: "Engineering to QA Handoff"
  description: "Automated handoff of completed features for testing"
  
  # Participating Departments
  participants:
    source:
      department: engineering
      agents: [lead-engineer, senior-engineer]
    target:
      department: qa
      agents: [qa-lead, qa-engineer]
      
  # Trigger Conditions
  triggers:
    - type: event
      event: feature_development_completed
      conditions:
        - field: status
          operator: equals
          value: ready_for_qa
          
    - type: schedule
      cron: "0 17 * * 1-5"  # Daily at 5 PM on weekdays
      
  # Handoff Process
  process:
    steps:
      - name: prepare_handoff
        actor: source.lead-engineer
        action: compile_test_requirements
        outputs:
          - test_plan
          - acceptance_criteria
          
      - name: create_qa_ticket
        actor: system
        action: create_task
        inputs:
          department: qa
          type: feature_testing
          data:
            from: ${steps.prepare_handoff.outputs}
            
      - name: assign_qa_engineer
        actor: target.qa-lead
        action: assign_task
        strategy: capability-match
        
      - name: acknowledge_handoff
        actor: target.qa-engineer
        action: acknowledge
        timeout: PT2H
        onTimeout: escalate_to_qa_lead
        
  # Communication
  notifications:
    - event: handoff_initiated
      channels: [slack, email]
      recipients: [source.agents, target.agents]
      
    - event: handoff_completed
      channels: [slack]
      recipients: [source.lead-engineer]
      
  # SLA Configuration
  sla:
    acknowledgmentTime: PT2H
    completionTime: PT24H
    escalationPolicy:
      - after: PT4H
        escalateTo: qa-lead
      - after: PT8H
        escalateTo: qa-manager
```

### 6.2 Cross-Department Workflow Schema

```yaml
# workflows/sprint-planning.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: Workflow
metadata:
  name: sprint-planning
  labels:
    type: planning
    frequency: bi-weekly

spec:
  displayName: "Sprint Planning Workflow"
  description: "Bi-weekly sprint planning across all departments"
  
  # Trigger
  trigger:
    type: schedule
    cron: "0 9 1,15 * *"  # 1st and 15th of each month at 9 AM
    
  # Global Context
  context:
    sprintDuration: P2W  # 2 weeks
    maxStoryPoints: 100
    
  # Workflow Steps
  steps:
    - id: set-okrs
      name: "Set Sprint OKRs"
      department: product
      agent: cpo-agent
      action: define_sprint_objectives
      inputs:
        quarterly_goals: ${context.quarterly_goals}
        capacity: ${context.team_capacity}
      outputs:
        - sprint_objectives
        - key_results
      timeout: PT4H
      
    - id: prioritize-backlog
      name: "Prioritize Backlog"
      department: product
      agent: product-manager
      action: prioritize_stories
      waitFor: [set-okrs]
      inputs:
        objectives: ${steps.set-okrs.outputs.sprint_objectives}
        backlog: ${context.product_backlog}
      outputs:
        - prioritized_backlog
      timeout: PT2H
      
    - id: technical-review
      name: "Technical Feasibility Review"
      department: engineering
      agent: cto-agent
      action: review_technical_feasibility
      waitFor: [prioritize-backlog]
      inputs:
        stories: ${steps.prioritize-backlog.outputs.prioritized_backlog}
      outputs:
        - feasibility_report
        - effort_estimates
      timeout: PT2H
      
    - id: capacity-planning
      name: "Team Capacity Planning"
      department: engineering
      agent: lead-engineer
      action: plan_capacity
      waitFor: [technical-review]
      parallel: true
      inputs:
        estimates: ${steps.technical-review.outputs.effort_estimates}
        team_availability: ${context.team_availability}
      outputs:
        - sprint_plan
        - assignments
        
    - id: qa-planning
      name: "QA Resource Planning"
      department: qa
      agent: qa-lead
      action: plan_qa_resources
      waitFor: [technical-review]
      parallel: true  # Runs in parallel with capacity-planning
      inputs:
        stories: ${steps.prioritize-backlog.outputs.prioritized_backlog}
      outputs:
        - qa_plan
        - test_estimates
        
    - id: finalize-sprint
      name: "Finalize Sprint"
      department: product
      agent: product-manager
      action: finalize_sprint
      waitFor: [capacity-planning, qa-planning]  # Waits for both parallel steps
      inputs:
        sprint_plan: ${steps.capacity-planning.outputs.sprint_plan}
        qa_plan: ${steps.qa-planning.outputs.qa_plan}
      outputs:
        - final_sprint
        - commitments
        
    - id: notify-stakeholders
      name: "Notify Stakeholders"
      department: product
      agent: system
      action: send_notifications
      waitFor: [finalize-sprint]
      inputs:
        sprint: ${steps.finalize-sprint.outputs.final_sprint}
      notifications:
        - channel: slack
          message: "Sprint ${context.sprint_number} planning complete!"
        - channel: email
          recipients: [all-department-heads]
          template: sprint-summary
          
  # Error Handling
  errorHandling:
    strategy: compensate  # fail-fast | retry | compensate | continue
    maxRetries: 3
    retryDelay: PT5M
    
    compensationSteps:
      - triggerOn: [capacity-planning, qa-planning]
        action: notify_planning_failure
        
  # Human Approval Gates
  approvals:
    - before: finalize-sprint
      approvers: [cto-agent, cpo-agent]
      timeout: PT4H
      onTimeout: auto-approve
      
  # Completion Criteria
  completion:
    successCondition: all_steps_completed
    artifacts:
      - name: sprint_plan
        path: ${steps.finalize-sprint.outputs.final_sprint}
      - name: team_assignments
        path: ${steps.capacity-planning.outputs.assignments}
```

### 6.3 Incident Response Workflow

```yaml
# workflows/incident-response.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: Workflow
metadata:
  name: incident-response
  labels:
    type: operational
    priority: critical

spec:
  displayName: "Incident Response Workflow"
  
  trigger:
    type: event
    event: incident_detected
    conditions:
      - field: severity
        operator: in
        values: [critical, high]
        
  # Dynamic step selection based on incident type
  steps:
    - id: triage
      name: "Incident Triage"
      department: devops
      agent: on-call-engineer
      action: triage_incident
      timeout: PT15M
      outputs:
        - severity_assessment
        - affected_systems
        - initial_diagnosis
        
    - id: notify-stakeholders
      name: "Notify Stakeholders"
      agent: system
      action: broadcast_notification
      waitFor: [triage]
      parallel: true
      notifications:
        - when: ${steps.triage.outputs.severity_assessment} == "critical"
          channels: [pagerduty, slack-critical]
          recipients: [cto-agent, devops-lead, on-call-team]
        - when: ${steps.triage.outputs.severity_assessment} == "high"
          channels: [slack-incidents]
          recipients: [devops-lead, on-call-team]
          
    - id: investigate
      name: "Investigate Root Cause"
      department: engineering
      agent: senior-engineer
      action: investigate_incident
      waitFor: [triage]
      inputs:
        diagnosis: ${steps.triage.outputs.initial_diagnosis}
        systems: ${steps.triage.outputs.affected_systems}
      outputs:
        - root_cause
        - fix_options
      timeout: PT1H
      
    - id: implement-fix
      name: "Implement Fix"
      department: engineering
      agent: senior-engineer
      action: implement_hotfix
      waitFor: [investigate]
      inputs:
        fix_option: ${steps.investigate.outputs.fix_options[0]}
      outputs:
        - hotfix_branch
        - deployment_plan
      hitl:
        required: true
        approvers: [lead-engineer, cto-agent]
        
    - id: deploy-fix
      name: "Deploy Fix"
      department: devops
      agent: devops-engineer
      action: deploy_hotfix
      waitFor: [implement-fix]
      inputs:
        branch: ${steps.implement-fix.outputs.hotfix_branch}
        plan: ${steps.implement-fix.outputs.deployment_plan}
      outputs:
        - deployment_status
        
    - id: verify-resolution
      name: "Verify Resolution"
      department: qa
      agent: qa-engineer
      action: verify_fix
      waitFor: [deploy-fix]
      outputs:
        - verification_result
        
    - id: post-mortem
      name: "Schedule Post-Mortem"
      department: engineering
      agent: system
      action: schedule_meeting
      waitFor: [verify-resolution]
      condition: ${steps.triage.outputs.severity_assessment} in ["critical", "high"]
      
  # Escalation Timeline
  escalation:
    - after: PT30M
      if: step != completed
      action: escalate
      to: devops-lead
    - after: PT1H
      if: step != completed
      action: escalate
      to: cto-agent
    - after: PT2H
      if: step != completed
      action: escalate
      to: ceo-agent
```

---

## 7. Action Configuration

Actions are the atomic units of work that agents can perform. The Action Configuration system enables declarative definition of all actions available in the organization.

### 7.1 Action Schema

```yaml
# config/actions/code-review.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: Action
metadata:
  name: code-review
  labels:
    category: development
    department: engineering

spec:
  # Basic Information
  displayName: "Code Review Action"
  description: "Performs comprehensive code review on pull requests"
  version: "1.0.0"
  
  # Action Type
  type: ai-assisted  # sync | async | ai-assisted | external | composite
  
  # Input/Output Schema
  input:
    schema:
      type: object
      properties:
        pullRequestUrl:
          type: string
          format: uri
          required: true
        reviewType:
          type: string
          enum: [standard, security, performance]
          default: standard
        focusAreas:
          type: array
          items:
            type: string
          default: []
    validation:
      maxSize: 10MB
      timeout: PT5M
      
  output:
    schema:
      type: object
      properties:
        reviewResult:
          type: string
          enum: [approved, changes_requested, needs_discussion]
        comments:
          type: array
          items:
            $ref: "#/definitions/ReviewComment"
        metrics:
          type: object
          properties:
            linesReviewed: { type: integer }
            issuesFound: { type: integer }
            suggestions: { type: integer }
            
  # Execution Configuration
  execution:
    mode: async
    timeout: PT30M
    retryPolicy:
      maxRetries: 3
      backoffStrategy: exponential
      initialDelay: PT1M
      maxDelay: PT10M
    parallelExecution: false
    resourceRequirements:
      memory: 512MB
      cpu: 0.5
      
  # Result Handling
  resultHandling:
    onSuccess:
      - action: emit_event
        event: code_review_completed
      - action: notify
        channel: slack
        recipients: [pr_author]
    onFailure:
      - action: retry
        maxAttempts: 2
      - action: escalate
        to: lead-engineer
    compensation:
      enabled: true
      action: revert_review_state
      
  # Permissions & Audit
  permissions:
    requiredRoles: [engineer, lead-engineer, senior-engineer]
    requiredCapabilities: [code_review, ${spec.input.schema.properties.reviewType}]
  audit:
    enabled: true
    logLevel: detailed
    retentionDays: 90
```

### 7.2 Composite Actions

Define complex actions composed of sub-actions:

```yaml
# config/actions/full-release.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: Action
metadata:
  name: full-release

spec:
  displayName: "Full Release Pipeline"
  type: composite
  
  composition:
    mode: sequential  # sequential | parallel | dag
    steps:
      - id: code-freeze
        action: code-freeze
        timeout: PT1H
        
      - id: run-tests
        action: run-full-test-suite
        waitFor: [code-freeze]
        timeout: PT2H
        
      - id: security-scan
        action: security-vulnerability-scan
        waitFor: [code-freeze]
        parallel: true  # Can run parallel with run-tests
        
      - id: build-release
        action: build-release-artifacts
        waitFor: [run-tests, security-scan]
        
      - id: deploy-staging
        action: deploy-to-environment
        waitFor: [build-release]
        inputs:
          environment: staging
          
      - id: smoke-tests
        action: run-smoke-tests
        waitFor: [deploy-staging]
        
      - id: deploy-production
        action: deploy-to-environment
        waitFor: [smoke-tests]
        inputs:
          environment: production
        hitl:
          required: true
          approvers: [cto-agent, devops-lead]
```

### 7.3 Action Java Implementation

```java
/**
 * Action configuration POJO.
 */
public record ActionConfig(
    String apiVersion,
    String kind,
    ConfigMetadata metadata,
    ActionSpec spec
) {}

public record ActionSpec(
    String displayName,
    String description,
    String version,
    String type,
    ActionIOSchema input,
    ActionIOSchema output,
    ExecutionConfig execution,
    ResultHandling resultHandling,
    ActionPermissions permissions,
    ActionAudit audit
) {}
```

---

## 8. Persona Configuration

Personas define the identity, behavioral patterns, communication style, and knowledge domains of agents. This enables agents to be configured with consistent personalities and expertise areas.

### 8.1 Persona Schema

```yaml
# config/personas/senior-engineer.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: Persona
metadata:
  name: senior-engineer-persona
  labels:
    category: engineering
    level: senior

spec:
  # Identity
  identity:
    name: "Senior Software Engineer"
    title: "Senior Engineer"
    yearsExperience: 8
    specializations: [backend, distributed-systems, databases]
    
  # Role Definition
  role:
    type: specialized  # base | specialized | custom
    level: senior
    permissions:
      - name: code_review
        scope: department
        actions: [approve, reject, comment]
      - name: architecture_decisions
        scope: team
        actions: [propose, review]
      - name: mentoring
        scope: department
        actions: [assign, review, feedback]
    capabilities:
      - java
      - system-design
      - code-review
      - mentoring
      - debugging
    parentRoles:
      - engineer-base
      
  # Behavioral Configuration
  behavior:
    communication:
      style: professional  # casual | professional | formal | technical
      verbosity: moderate  # concise | moderate | detailed
      tone: helpful        # neutral | helpful | directive | supportive
      preferredChannels: [slack, code-comments]
      responseFormat: structured  # freeform | structured | templated
      
    decisionMaking:
      approach: analytical  # intuitive | analytical | collaborative | directive
      riskTolerance: moderate  # conservative | moderate | aggressive
      autonomyLevel: high  # low | medium | high
      escalationThreshold: complex-decisions
      
    collaboration:
      teamPreference: small-team  # solo | pair | small-team | large-team
      feedbackStyle: constructive
      conflictResolution: mediator
      
  # Knowledge Domains
  knowledge:
    domains:
      - name: backend-development
        proficiency: expert
        topics: [java, spring, microservices, rest-apis]
      - name: databases
        proficiency: advanced
        topics: [postgresql, mongodb, redis, query-optimization]
      - name: system-design
        proficiency: advanced
        topics: [scalability, reliability, distributed-systems]
    certifications:
      - AWS Solutions Architect
      - Java Professional
    continuousLearning:
      - current-topics: [kubernetes, rust, ai-coding-assistants]
      
  # Constraints
  constraints:
    workingHours:
      timezone: "America/New_York"
      start: "09:00"
      end: "18:00"
      days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
    maxConcurrentTasks: 5
    focusTimeRequired: true
    meetingLimit: 4  # per day
```

### 8.2 Persona Templates & Inheritance

```yaml
# config/personas/templates/engineer-base.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: PersonaTemplate
metadata:
  name: engineer-base

spec:
  behavior:
    communication:
      style: professional
      preferredChannels: [slack, code-comments]
    decisionMaking:
      approach: analytical
      
  knowledge:
    domains:
      - name: software-development
        proficiency: intermediate
```

```yaml
# Junior engineer inherits from base
apiVersion: virtualorg.ghatana.com/v1
kind: Persona
metadata:
  name: junior-engineer-persona

spec:
  template: engineer-base
  
  identity:
    name: "Junior Software Engineer"
    yearsExperience: 1
    
  role:
    level: junior
    autonomyLevel: low
    
  behavior:
    decisionMaking:
      escalationThreshold: most-decisions
```

### 8.3 Persona Java Implementation

```java
/**
 * Persona configuration POJO.
 */
public record PersonaConfig(
    String apiVersion,
    String kind,
    ConfigMetadata metadata,
    PersonaSpec spec
) {}

public record PersonaSpec(
    String template,
    PersonaIdentity identity,
    PersonaRole role,
    PersonaBehavior behavior,
    PersonaKnowledge knowledge,
    PersonaConstraints constraints
) {}

public record PersonaRole(
    String type,
    String level,
    List<RolePermission> permissions,
    Set<String> capabilities,
    Set<String> parentRoles
) {}
```

---

## 9. Task Configuration

Tasks represent units of work to be completed by agents. The Task Configuration system provides comprehensive definition of task types, execution requirements, SLAs, and assignment strategies.

### 9.1 Task Schema

```yaml
# config/tasks/feature-implementation.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: TaskDefinition
metadata:
  name: feature-implementation
  labels:
    category: development
    priority: normal

spec:
  # Basic Information
  displayName: "Feature Implementation Task"
  description: "Complete implementation of a product feature"
  version: "1.0.0"
  
  # Task Type & Category
  type: development
  category: feature
  priority: normal  # critical | high | normal | low
  
  # Input Requirements
  input:
    schema:
      properties:
        featureSpec:
          type: object
          required: true
          description: "Feature specification document"
        acceptanceCriteria:
          type: array
          items:
            type: string
          minItems: 1
        estimatedPoints:
          type: integer
          minimum: 1
          maximum: 21
        technicalDesign:
          type: object
          required: false
    validation:
      requireAllFields: false
      customValidator: feature-input-validator
      
  # Output Expectations
  output:
    schema:
      properties:
        implementation:
          type: object
          properties:
            pullRequestUrl: { type: string, format: uri }
            branchName: { type: string }
            filesChanged: { type: integer }
            linesAdded: { type: integer }
            linesRemoved: { type: integer }
        documentation:
          type: object
          properties:
            updated: { type: boolean }
            apiDocs: { type: string }
        testCoverage:
          type: number
          minimum: 0
          maximum: 100
    validation:
      required: [implementation.pullRequestUrl, testCoverage]
      minimumTestCoverage: 80
      
  # Execution Configuration
  execution:
    estimatedDuration: PT8H
    maxDuration: PT40H
    checkpoints:
      - at: 25%
        action: progress_update
      - at: 50%
        action: midpoint_review
      - at: 90%
        action: final_review
    parallelizable: false
    interruptible: true
    
  # Dependencies
  dependencies:
    required:
      - type: task
        name: technical-design
        condition: completed
      - type: resource
        name: development-environment
        condition: available
    optional:
      - type: task
        name: architecture-review
        
  # SLA Configuration
  sla:
    targetCompletion: PT24H
    warningThreshold: PT16H
    criticalThreshold: PT20H
    escalation:
      - at: PT16H
        to: lead-engineer
        action: notify
      - at: PT20H
        to: engineering-manager
        action: notify_and_reassign
      - at: PT24H
        to: cto-agent
        action: escalate_critical
        
  # Assignment Rules
  assignment:
    strategy: capability-match  # round-robin | least-loaded | capability-match | manual
    rules:
      - condition: "estimatedPoints > 8"
        assignTo: senior-engineer
      - condition: "category == 'security'"
        requiredCapabilities: [security-expertise]
      - condition: default
        assignTo: engineer-pool
    maxReassignments: 2
    
  # Completion Criteria
  completion:
    criteria:
      - type: all-checkpoints-passed
      - type: output-validated
      - type: approval-received
        from: [lead-engineer]
    artifacts:
      - name: pull-request
        required: true
      - name: test-report
        required: true
      - name: documentation-update
        required: false
```

### 9.2 Task Templates

```yaml
# config/tasks/templates/development-task.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: TaskTemplate
metadata:
  name: development-task-template

spec:
  execution:
    checkpoints:
      - at: 50%
        action: progress_update
      - at: 90%
        action: final_review
        
  sla:
    escalation:
      - at: 80%
        to: lead-engineer
        action: notify
        
  completion:
    criteria:
      - type: output-validated
      - type: approval-received
```

---

## 10. Result Processing Configuration

Result configurations define how agent outputs are processed, transformed, validated, routed, and aggregated across the organization.

### 10.1 Result Processor Schema

```yaml
# config/results/code-review-results.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: ResultProcessor
metadata:
  name: code-review-result-processor
  labels:
    source: code-review-action

spec:
  # Source Definition
  source:
    action: code-review
    department: engineering
    
  # Result Schema Validation
  schema:
    type: object
    properties:
      reviewResult:
        type: string
        enum: [approved, changes_requested, needs_discussion]
      severity:
        type: string
        enum: [critical, major, minor, suggestion]
      comments:
        type: array
        items:
          $ref: "#/definitions/ReviewComment"
      metrics:
        type: object
        
  # Transformers Pipeline
  transformers:
    - name: normalize-severity
      type: mapping
      config:
        field: severity
        mappings:
          blocker: critical
          error: major
          warning: minor
          info: suggestion
          
    - name: enrich-metrics
      type: enrichment
      config:
        source: metrics-service
        fields: [complexity_score, technical_debt_impact]
        
    - name: calculate-priority
      type: computed
      config:
        outputField: priority
        expression: |
          if (severity == 'critical') return 1;
          if (severity == 'major' && comments.length > 5) return 2;
          return 3;
          
  # Routing Configuration
  routing:
    routes:
      - name: critical-findings
        condition:
          field: severity
          operator: equals
          value: critical
        destinations:
          - type: queue
            name: critical-review-queue
          - type: notification
            channel: slack
            target: "#security-alerts"
          - type: agent
            name: security-lead
            action: urgent_review
            
      - name: standard-findings
        condition:
          field: severity
          operator: in
          values: [major, minor]
        destinations:
          - type: queue
            name: review-backlog
          - type: notification
            channel: email
            target: pr-author
            
      - name: approved-reviews
        condition:
          field: reviewResult
          operator: equals
          value: approved
        destinations:
          - type: workflow
            name: merge-pipeline
          - type: metrics
            name: review-completion-time
            
  # Aggregation Rules
  aggregation:
    enabled: true
    rules:
      - name: daily-review-summary
        groupBy: [department, reviewer]
        period: P1D
        metrics:
          - type: count
            field: "*"
            as: total_reviews
          - type: avg
            field: metrics.linesReviewed
            as: avg_lines_reviewed
          - type: distribution
            field: reviewResult
            as: result_distribution
        output:
          destination: analytics-service
          format: json
          
      - name: author-feedback-aggregate
        groupBy: [pr_author]
        period: P1W
        metrics:
          - type: count
            field: comments
            as: total_comments_received
          - type: categorize
            field: comments.category
            as: comment_categories
            
  # Storage Configuration
  storage:
    primary:
      type: database
      table: code_review_results
      retention: P90D
    archive:
      type: s3
      bucket: review-archives
      retention: P2Y
      
  # Validation Rules
  validation:
    rules:
      - name: required-fields
        type: required
        fields: [reviewResult, severity]
      - name: severity-consistency
        type: custom
        validator: severity-review-consistency
        errorMessage: "Critical severity must have changes_requested result"
```

### 10.2 Result Aggregation Patterns

```yaml
# config/results/department-kpi-aggregator.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: ResultAggregator
metadata:
  name: engineering-kpi-aggregator

spec:
  sources:
    - action: "*"
      department: engineering
      
  aggregation:
    schedule: "0 0 * * *"  # Daily at midnight
    rules:
      - name: velocity
        metrics:
          - type: sum
            field: story_points_completed
          - type: avg
            field: cycle_time_hours
          - type: percentile
            field: cycle_time_hours
            percentile: 95
            
  output:
    destinations:
      - type: kpi-dashboard
        metrics: [velocity, cycle_time_avg, cycle_time_p95]
      - type: event
        name: daily_engineering_metrics
```

---

## 11. Agent Lifecycle Configuration

Agent Lifecycle configurations define how agents are initialized, monitored, scaled, recovered, and terminated throughout their operational lifetime.

### 11.1 Lifecycle Schema

```yaml
# config/lifecycle/engineering-agent-lifecycle.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: AgentLifecycle
metadata:
  name: engineering-agent-lifecycle
  labels:
    department: engineering

spec:
  # Initialization Configuration
  initialization:
    order: 100  # Lower numbers start first
    timeout: PT5M
    bootstrap:
      - name: load-context
        action: load_department_context
        timeout: PT1M
        required: true
      - name: connect-tools
        action: initialize_tool_connections
        timeout: PT2M
        required: true
      - name: load-memory
        action: restore_agent_memory
        timeout: PT1M
        required: false
    warmup:
      enabled: true
      tasks:
        - type: self-test
          action: run_capability_check
        - type: cache-warm
          action: preload_common_data
          
  # Health Check Configuration
  health:
    enabled: true
    interval: PT1M
    timeout: PT30S
    endpoints:
      - name: responsiveness
        type: heartbeat
        threshold: 3  # consecutive failures
      - name: task-processing
        type: functional
        action: process_health_check_task
        expectedResult: success
      - name: memory-usage
        type: metric
        metric: memory_utilization
        threshold: 90%
      - name: queue-depth
        type: metric
        metric: pending_tasks
        threshold: 100
    onUnhealthy:
      - action: alert
        severity: warning
        after: 1
      - action: restart
        after: 3
      - action: escalate
        after: 5
        to: devops-team
        
  # Scaling Configuration
  scaling:
    enabled: true
    mode: auto  # manual | auto | scheduled
    minInstances: 1
    maxInstances: 10
    rules:
      - name: scale-up-on-load
        metric: pending_tasks
        threshold: 50
        direction: up
        adjustment: 2
        cooldown: PT5M
      - name: scale-down-on-idle
        metric: utilization
        threshold: 20%
        direction: down
        adjustment: 1
        cooldown: PT15M
      - name: scheduled-scale
        type: scheduled
        schedule: "0 9 * * 1-5"
        targetInstances: 5
        
  # Recovery Configuration
  recovery:
    enabled: true
    strategy: restart-with-state  # restart | restart-with-state | failover | manual
    maxRestarts: 3
    restartWindow: PT1H
    stateRecovery:
      source: checkpoint
      maxAge: PT1H
    actions:
      - trigger: crash
        action: restart_with_state
        notification: ops-team
      - trigger: stuck
        action: force_restart
        timeout: PT5M
      - trigger: repeated_failures
        action: disable_and_alert
        threshold: 3
        
  # Termination Configuration
  termination:
    gracePeriod: PT5M
    hooks:
      - name: save-state
        action: checkpoint_state
        timeout: PT1M
        required: true
      - name: complete-tasks
        action: finish_current_tasks
        timeout: PT3M
        required: false
      - name: notify-handoff
        action: notify_department
        required: true
    forceTermination:
      enabled: true
      after: PT10M
      
  # State Management
  state:
    persistence:
      enabled: true
      type: checkpoint
      interval: PT5M
      storage: redis
    restoration:
      onStartup: true
      maxAge: PT24H
      fallback: clean_start
```

### 11.2 Lifecycle Events

```yaml
# Define custom lifecycle event handlers
apiVersion: virtualorg.ghatana.com/v1
kind: LifecycleEventHandler
metadata:
  name: agent-lifecycle-events

spec:
  handlers:
    - event: agent.starting
      actions:
        - log: "Agent ${agent.id} starting in ${agent.department}"
        - notify: ops-dashboard
        
    - event: agent.ready
      actions:
        - update_registry: available
        - emit_event: agent_ready
        
    - event: agent.unhealthy
      actions:
        - alert:
            severity: warning
            message: "Agent ${agent.id} health check failed"
        - start_recovery: true
        
    - event: agent.terminated
      actions:
        - cleanup_resources: true
        - update_registry: terminated
        - archive_state: true
```

---

## 12. Interaction Protocol Configuration

Interaction configurations define the communication protocols, data formats, SLAs, and escalation policies for inter-department and inter-agent interactions.

### 12.1 Interaction Protocol Schema

```yaml
# config/interactions/eng-qa-protocol.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: InteractionProtocol
metadata:
  name: engineering-qa-protocol
  labels:
    type: handoff
    departments: [engineering, qa]

spec:
  # Participating Parties
  parties:
    initiator:
      department: engineering
      agents: [lead-engineer, senior-engineer, engineer]
      role: requester
    responder:
      department: qa
      agents: [qa-lead, qa-engineer]
      role: handler
      
  # Protocol Definition
  protocol:
    type: request-response  # request-response | event-driven | streaming
    synchronicity: async
    timeout: PT4H
    
    # Message Format
    messageFormat:
      request:
        schema:
          type: object
          required: [featureId, testScope, priority]
          properties:
            featureId: { type: string }
            testScope: { type: string, enum: [unit, integration, e2e, full] }
            priority: { type: string, enum: [critical, high, normal, low] }
            acceptanceCriteria: { type: array, items: { type: string } }
            deploymentInfo: { type: object }
      response:
        schema:
          type: object
          required: [status, testResults]
          properties:
            status: { type: string, enum: [passed, failed, blocked] }
            testResults:
              type: object
              properties:
                total: { type: integer }
                passed: { type: integer }
                failed: { type: integer }
                skipped: { type: integer }
            issues: { type: array }
            recommendations: { type: string }
            
  # SLA Configuration
  sla:
    acknowledgment:
      timeout: PT1H
      required: true
    response:
      critical: PT4H
      high: PT8H
      normal: PT24H
      low: PT48H
    metrics:
      - name: response_time
        target: P95 < 4h
      - name: completion_rate
        target: 98%
        
  # Escalation Policy
  escalation:
    rules:
      - trigger: no_acknowledgment
        after: PT2H
        action:
          - notify: initiator
          - escalate_to: qa-lead
      - trigger: sla_breach_warning
        at: 80%
        action:
          - notify: [qa-lead, engineering-lead]
      - trigger: sla_breach
        at: 100%
        action:
          - notify: [qa-manager, engineering-manager]
          - create_incident: true
          - reassign: true
          
  # Event Triggers
  events:
    triggers:
      - name: handoff-initiated
        on: request_sent
        emit: qa_testing_requested
        data: [featureId, priority, initiator]
        
      - name: testing-started
        on: acknowledged
        emit: qa_testing_started
        
      - name: testing-completed
        on: response_sent
        emit: qa_testing_completed
        data: [featureId, status, testResults]
        
  # Data Handling
  data:
    transformation:
      request:
        - type: enrich
          source: feature-service
          fields: [featureDetails, relatedTests]
      response:
        - type: normalize
          field: testResults
          format: standard-test-report
          
    validation:
      request:
        - field: featureId
          rule: exists_in_system
        - field: acceptanceCriteria
          rule: non_empty_if_present
      response:
        - field: testResults
          rule: totals_match
```

### 12.2 Cross-Department Interaction Patterns

```yaml
# config/interactions/support-escalation.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: InteractionProtocol
metadata:
  name: support-engineering-escalation

spec:
  parties:
    initiator:
      department: support
      agents: [support-engineer, support-lead]
    responder:
      department: engineering
      agents: [on-call-engineer, senior-engineer]
      
  protocol:
    type: escalation
    
    stages:
      - name: initial-triage
        handler: support-engineer
        timeout: PT30M
        
      - name: technical-escalation
        handler: on-call-engineer
        condition: "issue.severity in ['critical', 'high']"
        timeout: PT1H
        
      - name: expert-review
        handler: senior-engineer
        condition: "issue.complexity == 'high'"
        
  routing:
    strategy: capability-match
    fallback: round-robin
    
  collaboration:
    mode: real-time
    channels:
      - type: chat
        platform: slack
        channel: "#incidents-${issue.id}"
      - type: call
        platform: zoom
        auto_create: true
        condition: "severity == 'critical'"
```

---

## 13. Configuration Registry

The Configuration Registry provides a unified interface for loading, managing, and accessing all configuration types with hot-reload support.

### 13.1 Registry Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Configuration Registry                        │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ Organization │  │  Departments │  │    Agents    │          │
│  │    Config    │  │    Configs   │  │   Configs    │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   Actions    │  │   Personas   │  │    Tasks     │          │
│  │   Configs    │  │   Configs    │  │   Configs    │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   Results    │  │  Lifecycle   │  │ Interactions │          │
│  │   Configs    │  │   Configs    │  │   Configs    │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
├─────────────────────────────────────────────────────────────────┤
│  Hot Reload │ Validation │ Variable Resolution │ Schema Check  │
└─────────────────────────────────────────────────────────────────┘
```

### 13.2 Registry Usage

```java
/**
 * Unified Configuration Registry.
 */
public class ConfigRegistry {
    
    private final ConfigParser parser;
    private final ConfigValidator validator;
    private final WatchService watchService;
    
    // Registries for each config type
    private final Map<String, OrganizationConfig> organizations;
    private final Map<String, ActionConfig> actions;
    private final Map<String, PersonaConfig> personas;
    private final Map<String, TaskConfig> tasks;
    private final Map<String, ResultConfig> results;
    private final Map<String, AgentLifecycleConfig> lifecycles;
    private final Map<String, InteractionConfig> interactions;
    
    /**
     * Load all configurations from a directory.
     */
    public Promise<ConfigRegistry> loadAll(Path configDir) {
        return Promise.ofBlocking(executor, () -> {
            loadOrganization(configDir.resolve("organization.yaml"));
            loadDirectory(configDir.resolve("departments"), DepartmentConfig.class);
            loadDirectory(configDir.resolve("agents"), AgentConfig.class);
            loadDirectory(configDir.resolve("actions"), ActionConfig.class);
            loadDirectory(configDir.resolve("personas"), PersonaConfig.class);
            loadDirectory(configDir.resolve("tasks"), TaskConfig.class);
            loadDirectory(configDir.resolve("results"), ResultConfig.class);
            loadDirectory(configDir.resolve("lifecycle"), AgentLifecycleConfig.class);
            loadDirectory(configDir.resolve("interactions"), InteractionConfig.class);
            
            // Start hot-reload watcher
            startWatching(configDir);
            
            return this;
        });
    }
    
    /**
     * Get action configuration by name.
     */
    public Optional<ActionConfig> getAction(String name) {
        return Optional.ofNullable(actions.get(name));
    }
    
    /**
     * Get persona configuration by name.
     */
    public Optional<PersonaConfig> getPersona(String name) {
        return Optional.ofNullable(personas.get(name));
    }
    
    /**
     * Get task configuration by name.
     */
    public Optional<TaskConfig> getTask(String name) {
        return Optional.ofNullable(tasks.get(name));
    }
    
    /**
     * Subscribe to configuration changes.
     */
    public void onConfigChange(ConfigChangeListener listener) {
        changeListeners.add(listener);
    }
    
    /**
     * Trigger hot reload for a specific config type.
     */
    public Promise<Void> hotReload(ConfigType type) {
        return Promise.ofBlocking(executor, () -> {
            // Reload and validate
            // Notify listeners
        });
    }
}
```

### 13.3 Registry Configuration

```yaml
# config/registry.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: ConfigRegistrySettings
metadata:
  name: main-registry

spec:
  # Directories to scan
  directories:
    organization: config/
    departments: config/departments/
    agents: config/agents/
    actions: config/actions/
    personas: config/personas/
    tasks: config/tasks/
    results: config/results/
    lifecycle: config/lifecycle/
    interactions: config/interactions/
    workflows: config/workflows/
    
  # Hot reload settings
  hotReload:
    enabled: true
    debounceMs: 500
    validateBeforeReload: true
    
  # Validation settings
  validation:
    strict: true
    schemaPath: config/schema/
    failOnWarning: false
    
  # Variable resolution
  variables:
    sources:
      - type: environment
        prefix: VORG_
      - type: system-properties
        prefix: virtualorg.
      - type: file
        path: config/variables.yaml
```

---

## 14. Java Implementation Design

### 14.1 Core Classes

```java
/**
 * Configuration loader for Virtual-Org YAML configurations.
 *
 * @doc.type class
 * @doc.purpose Load and parse organization configurations
 * @doc.layer core
 * @doc.pattern Factory
 */
public interface OrganizationConfigLoader {
    
    /**
     * Loads organization from YAML configuration.
     */
    Promise<AbstractOrganization> loadOrganization(Path configPath);
    
    /**
     * Loads organization from multiple config files.
     */
    Promise<AbstractOrganization> loadOrganization(List<Path> configPaths);
    
    /**
     * Validates configuration without creating organization.
     */
    Promise<ConfigValidationResult> validate(Path configPath);
}

/**
 * Configuration-driven organization implementation.
 */
public class ConfigurableOrganization extends AbstractOrganization {
    
    private final OrganizationConfig config;
    private final ConfigReloadWatcher reloadWatcher;
    
    public ConfigurableOrganization(TenantId tenantId, OrganizationConfig config) {
        super(tenantId, config.getDisplayName(), config.getDescription());
        this.config = config;
        this.reloadWatcher = new ConfigReloadWatcher(this::onConfigReload);
        initializeFromConfig();
    }
    
    private void initializeFromConfig() {
        // Create departments from config
        for (DepartmentConfig deptConfig : config.getDepartments()) {
            Department dept = DepartmentFactory.create(this, deptConfig);
            registerDepartment(dept);
        }
        
        // Initialize workflows
        for (WorkflowConfig wfConfig : config.getWorkflows()) {
            WorkflowDefinition workflow = WorkflowFactory.create(wfConfig);
            registerWorkflow(workflow);
        }
        
        // Set up interactions
        for (InteractionConfig intConfig : config.getInteractions()) {
            DepartmentInteraction interaction = InteractionFactory.create(this, intConfig);
            registerInteraction(interaction);
        }
    }
    
    private void onConfigReload(OrganizationConfig newConfig) {
        // Handle hot reload
    }
}
```

### 14.2 Configuration POJOs

```java
/**
 * Root organization configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrganizationConfig(
    String apiVersion,
    String kind,
    ConfigMetadata metadata,
    OrganizationSpec spec
) {
    public String getDisplayName() {
        return spec.displayName();
    }
    
    public String getDescription() {
        return spec.description();
    }
    
    public List<DepartmentConfig> getDepartments() {
        return spec.departments().stream()
            .map(this::resolveDepartmentRef)
            .toList();
    }
}

/**
 * Department configuration.
 */
public record DepartmentConfig(
    String apiVersion,
    String kind,
    ConfigMetadata metadata,
    DepartmentSpec spec
) {}

/**
 * Agent configuration.
 */
public record AgentConfig(
    String apiVersion,
    String kind,
    ConfigMetadata metadata,
    AgentSpec spec
) {}

/**
 * Agent AI configuration.
 */
public record AgentAIConfig(
    boolean enabled,
    String provider,
    String model,
    String systemPrompt,
    ResponseConfig responseConfig,
    List<ToolConfig> tools,
    MemoryConfig memory
) {}
```

### 14.3 Factory Classes

```java
/**
 * Factory for creating departments from configuration.
 */
public class DepartmentFactory {
    
    private static final Map<DepartmentType, Class<? extends Department>> REGISTRY = new HashMap<>();
    
    static {
        REGISTRY.put(DepartmentType.ENGINEERING, ConfigurableDepartment.class);
        REGISTRY.put(DepartmentType.QA, ConfigurableDepartment.class);
        // ... register all types
    }
    
    public static Department create(AbstractOrganization org, DepartmentConfig config) {
        DepartmentType type = DepartmentType.valueOf(config.spec().type().toUpperCase());
        
        ConfigurableDepartment dept = new ConfigurableDepartment(
            org,
            config.spec().displayName(),
            type,
            config
        );
        
        // Create and register agents
        for (AgentConfig agentConfig : config.spec().agents()) {
            Agent agent = AgentFactory.create(dept, agentConfig);
            dept.registerAgent(agent);
        }
        
        return dept;
    }
}

/**
 * Factory for creating agents from configuration.
 */
public class AgentFactory {
    
    public static Agent create(Department department, AgentConfig config) {
        Agent.Builder builder = Agent.builder()
            .id(config.metadata().name())
            .name(config.spec().displayName())
            .department(department.getName())
            .capabilities(config.spec().capabilities().primary().toArray(String[]::new));
            
        if (config.spec().ai() != null && config.spec().ai().enabled()) {
            // Configure AI-powered agent
            builder.aiConfig(createAIConfig(config.spec().ai()));
        }
        
        return builder.build();
    }
    
    private static AgentAIConfig createAIConfig(AgentAIConfigSpec aiSpec) {
        return new AgentAIConfig(
            aiSpec.provider(),
            aiSpec.model(),
            aiSpec.systemPrompt(),
            aiSpec.responseConfig()
        );
    }
}
```

### 14.4 Configuration-Driven Department

```java
/**
 * Department implementation that is fully configuration-driven.
 *
 * @doc.type class
 * @doc.purpose Configuration-driven department implementation
 * @doc.layer core
 * @doc.pattern Strategy
 */
public class ConfigurableDepartment extends Department {
    
    private final DepartmentConfig config;
    private final TaskAssignmentStrategy assignmentStrategy;
    private final Map<String, Object> kpis;
    
    public ConfigurableDepartment(
            AbstractOrganization organization,
            String name,
            DepartmentType type,
            DepartmentConfig config) {
        super(organization, name, type.name());
        this.config = config;
        this.assignmentStrategy = createAssignmentStrategy(config.spec().settings().taskAssignment());
        this.kpis = initializeKpis(config.spec().kpis());
    }
    
    @Override
    protected Promise<Agent> assignTask(Task task) {
        return assignmentStrategy.assign(task, getAgents());
    }
    
    @Override
    public Map<String, Object> getKpis() {
        return Collections.unmodifiableMap(kpis);
    }
    
    private TaskAssignmentStrategy createAssignmentStrategy(TaskAssignmentConfig config) {
        return switch (config.strategy()) {
            case "round-robin" -> new RoundRobinAssignment();
            case "least-loaded" -> new LeastLoadedAssignment();
            case "capability-match" -> new CapabilityMatchAssignment();
            case "custom" -> loadCustomStrategy(config.customStrategyClass());
            default -> new RoundRobinAssignment();
        };
    }
}
```

---

## 15. Configuration Loading & Validation

### 15.1 YAML Parser with Variable Interpolation

```java
/**
 * YAML configuration parser with variable interpolation.
 */
public class ConfigParser {
    
    private final ObjectMapper yamlMapper;
    private final VariableResolver variableResolver;
    
    public ConfigParser() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.variableResolver = new VariableResolver();
    }
    
    public <T> T parse(Path path, Class<T> type) throws ConfigParseException {
        try {
            String content = Files.readString(path);
            String resolved = variableResolver.resolve(content);
            return yamlMapper.readValue(resolved, type);
        } catch (IOException e) {
            throw new ConfigParseException("Failed to parse config: " + path, e);
        }
    }
    
    public <T> T parseWithReferences(Path basePath, Class<T> type) throws ConfigParseException {
        // Parse main config and resolve all $ref references
        T config = parse(basePath, type);
        return resolveReferences(basePath.getParent(), config);
    }
}

/**
 * Resolves variables in configuration strings.
 */
public class VariableResolver {
    
    private static final Pattern ENV_VAR = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?\\}");
    private static final Pattern SYS_PROP = Pattern.compile("\\$\\{sys:([^:}]+)(?::([^}]*))?\\}");
    
    public String resolve(String content) {
        String result = content;
        
        // Resolve environment variables: ${VAR_NAME:default}
        result = resolvePattern(result, ENV_VAR, System::getenv);
        
        // Resolve system properties: ${sys:prop.name:default}
        result = resolvePattern(result, SYS_PROP, System::getProperty);
        
        return result;
    }
    
    private String resolvePattern(String content, Pattern pattern, 
                                  Function<String, String> resolver) {
        Matcher matcher = pattern.matcher(content);
        StringBuilder result = new StringBuilder();
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            String defaultValue = matcher.group(2);
            String value = resolver.apply(varName);
            
            if (value == null) {
                if (defaultValue != null) {
                    value = defaultValue;
                } else {
                    throw new ConfigurationException("Required variable not set: " + varName);
                }
            }
            
            matcher.appendReplacement(result, value);
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
}
```

### 15.2 Schema Validation

```java
/**
 * JSON Schema validator for configuration files.
 */
public class ConfigValidator {
    
    private final JsonSchema organizationSchema;
    private final JsonSchema departmentSchema;
    private final JsonSchema agentSchema;
    private final JsonSchema workflowSchema;
    
    public ConfigValidator() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        this.organizationSchema = factory.getSchema(loadSchema("organization-schema.json"));
        this.departmentSchema = factory.getSchema(loadSchema("department-schema.json"));
        this.agentSchema = factory.getSchema(loadSchema("agent-schema.json"));
        this.workflowSchema = factory.getSchema(loadSchema("workflow-schema.json"));
    }
    
    public ConfigValidationResult validate(OrganizationConfig config) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Validate organization
        errors.addAll(validateAgainst(config, organizationSchema));
        
        // Validate departments
        for (DepartmentConfig dept : config.getDepartments()) {
            errors.addAll(validateAgainst(dept, departmentSchema));
            
            // Validate agents
            for (AgentConfig agent : dept.getAgents()) {
                errors.addAll(validateAgainst(agent, agentSchema));
            }
        }
        
        // Validate workflows
        for (WorkflowConfig workflow : config.getWorkflows()) {
            errors.addAll(validateAgainst(workflow, workflowSchema));
        }
        
        // Semantic validation
        errors.addAll(validateSemantics(config));
        
        return new ConfigValidationResult(errors.isEmpty(), errors);
    }
    
    private List<ValidationError> validateSemantics(OrganizationConfig config) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Validate agent references exist
        Set<String> agentIds = collectAgentIds(config);
        for (WorkflowConfig workflow : config.getWorkflows()) {
            for (WorkflowStepConfig step : workflow.getSteps()) {
                if (!agentIds.contains(step.getAgent()) && !"system".equals(step.getAgent())) {
                    errors.add(new ValidationError(
                        "Unknown agent reference: " + step.getAgent(),
                        "workflows/" + workflow.getName() + "/steps/" + step.getId()
                    ));
                }
            }
        }
        
        // Validate department references
        // Validate workflow step dependencies
        // etc.
        
        return errors;
    }
}
```

---

## 16. Hot Reload & Runtime Updates

### 16.1 Configuration Watcher

```java
/**
 * Watches configuration files for changes and triggers reload.
 */
public class ConfigReloadWatcher implements Closeable {
    
    private final WatchService watchService;
    private final Consumer<OrganizationConfig> onReload;
    private final ScheduledExecutorService debounceExecutor;
    private volatile boolean running = true;
    
    public ConfigReloadWatcher(Path configDir, Consumer<OrganizationConfig> onReload) 
            throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        this.onReload = onReload;
        this.debounceExecutor = Executors.newSingleThreadScheduledExecutor();
        
        registerRecursive(configDir);
        startWatching();
    }
    
    private void startWatching() {
        Thread watchThread = new Thread(() -> {
            while (running) {
                try {
                    WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                    if (key != null) {
                        handleEvents(key);
                        key.reset();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "config-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }
    
    private void handleEvents(WatchKey key) {
        // Debounce rapid changes
        debounceExecutor.schedule(() -> {
            try {
                OrganizationConfig newConfig = loadConfig();
                ConfigValidationResult validation = validator.validate(newConfig);
                
                if (validation.isValid()) {
                    onReload.accept(newConfig);
                    LOG.info("Configuration reloaded successfully");
                } else {
                    LOG.warn("Configuration reload failed validation: {}", 
                             validation.getErrors());
                }
            } catch (Exception e) {
                LOG.error("Failed to reload configuration", e);
            }
        }, 500, TimeUnit.MILLISECONDS);
    }
}
```

### 16.2 Safe Reload Strategy

```java
/**
 * Handles safe configuration reload without disrupting running workflows.
 */
public class SafeConfigReloader {
    
    public Promise<ReloadResult> reload(
            ConfigurableOrganization org,
            OrganizationConfig newConfig) {
        
        return Promise.ofBlocking(Executors.defaultExecutor(), () -> {
            ReloadResult.Builder result = ReloadResult.builder();
            
            // 1. Validate new configuration
            ConfigValidationResult validation = validator.validate(newConfig);
            if (!validation.isValid()) {
                return result.failed(validation.getErrors()).build();
            }
            
            // 2. Identify changes
            ConfigDiff diff = ConfigDiffer.diff(org.getConfig(), newConfig);
            
            // 3. Apply safe changes immediately
            for (Change change : diff.getSafeChanges()) {
                applyChange(org, change);
                result.addApplied(change);
            }
            
            // 4. Queue disruptive changes for graceful transition
            for (Change change : diff.getDisruptiveChanges()) {
                scheduleGracefulChange(org, change);
                result.addScheduled(change);
            }
            
            return result.success().build();
        });
    }
    
    private void applyChange(ConfigurableOrganization org, Change change) {
        switch (change.type()) {
            case AGENT_CAPABILITY_UPDATE -> updateAgentCapabilities(org, change);
            case KPI_THRESHOLD_UPDATE -> updateKpiThresholds(org, change);
            case AI_PROMPT_UPDATE -> updateAIPrompts(org, change);
            // ...
        }
    }
}
```

---

## 17. Migration Guide

### 17.1 From Code-Based to Config-Based

#### Step 1: Export Existing Organization

```java
// Export existing code-based organization to YAML
ConfigExporter exporter = new ConfigExporter();
OrganizationConfig config = exporter.export(existingSoftwareOrg);
exporter.writeYaml(config, Path.of("config/organization.yaml"));
```

#### Step 2: Validate Generated Configuration

```bash
./gradlew :virtual-org:validateConfig --config=config/organization.yaml
```

#### Step 3: Create ConfigurableOrganization

```java
// Before (code-based)
SoftwareOrganization org = new SoftwareOrganization(tenantId, "Acme");

// After (config-based)
OrganizationConfigLoader loader = new YamlOrganizationConfigLoader();
AbstractOrganization org = loader.loadOrganization(
    Path.of("config/organization.yaml")
).getResult();
```

### 17.2 Hybrid Approach

Support both code and config:

```java
public class HybridOrganization extends AbstractOrganization {
    
    public HybridOrganization(TenantId tenantId, String name, Path configPath) {
        super(tenantId, name);
        
        // Load config-based departments
        OrganizationConfig config = loadConfig(configPath);
        for (DepartmentConfig deptConfig : config.getDepartments()) {
            Department dept = DepartmentFactory.create(this, deptConfig);
            registerDepartment(dept);
        }
        
        // Add code-based custom department
        registerDepartment(new CustomAnalyticsDepartment(this));
    }
}
```

---

## 18. Examples

### 18.1 Minimal Organization

```yaml
# config/minimal-org.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: Organization
metadata:
  name: minimal-startup

spec:
  displayName: "Minimal Startup"
  
  departments:
    - inline:
        displayName: "Engineering"
        type: ENGINEERING
        agents:
          - inline:
              displayName: "Developer"
              role:
                level: individual
              capabilities:
                primary: [coding]
```

### 18.2 Complete Software Organization

See the full example at: `products/software-org/config/` (to be created)

### 18.3 Custom Department Type

```yaml
# config/departments/data-science.yaml
apiVersion: virtualorg.ghatana.com/v1
kind: Department
metadata:
  name: data-science

spec:
  displayName: "Data Science"
  type: CUSTOM
  customTypeDefinition:
    code: DATA_SCIENCE
    displayName: "Data Science & ML"
    
  agents:
    - inline:
        displayName: "ML Engineer"
        role:
          level: senior
        capabilities:
          primary: [machine_learning, python, tensorflow]
        ai:
          enabled: true
          systemPrompt: |
            You are an ML Engineer specializing in:
            - Model development and training
            - Feature engineering
            - MLOps and deployment
```

---

## Appendix A: JSON Schema

The complete JSON Schema for configuration validation is available at:
`products/virtual-org/config/schema/virtual-org-schema.json`

## Appendix B: Reference Implementation Timeline

| Phase | Milestone | Target |
|-------|-----------|--------|
| 1 | Configuration POJOs & Parser | Week 1-2 |
| 2 | ConfigurableOrganization & Department | Week 3-4 |
| 3 | Agent Configuration & AI Setup | Week 5-6 |
| 4 | Workflow Configuration | Week 7-8 |
| 5 | Hot Reload & Validation | Week 9-10 |
| 6 | Migration Tools & Documentation | Week 11-12 |

---

*Document Version: 2.0.0*  
*Author: Virtual-Org Architecture Team*  
*Last Updated: November 2025*  
*Status: Approved for Implementation*

## Document Change History

| Version | Date | Changes |
|---------|------|---------|
| 2.0.0 | 2025-11 | Added comprehensive config types: Action, Persona, Task, Result, Lifecycle, Interaction, ConfigRegistry |
| 1.0.0 | 2025-11 | Initial configuration-driven architecture specification |

