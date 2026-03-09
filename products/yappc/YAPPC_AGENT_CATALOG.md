# YAPPC Agent Catalog

> Pluggable agent catalog for YAPPC — AI-Native Product Development Platform.
> Catalog ID: `yappc` | Extends: `["platform"]`

---

## Relationship to Existing Config

**Important:** YAPPC already has a fully operational agent system defined under `config/agents/` with **142 agents** across 13 phases (phase0-meta through phase12-scaling). This catalog document describes the **extension plan** for new agents that are not yet covered by the existing registry, following the exact same conventions already established in `config/`.

### Existing Agent System (`config/agents/`)

| File | Purpose |
|------|---------|
| `registry.yaml` | Master catalog — 142 agents in 3-level hierarchy, 13 phases |
| `capabilities.yaml` | 9 capability categories: generation, testing, analysis, documentation, debugging, deployment, data, research, ai |
| `mappings.yaml` | Task-to-agent dispatch rules, 26 canonical domains, fallback rules |
| `definitions/` | 142 agent `.yaml` files (Strategic L1 / Expert L2 / Worker L3) |
| `instances/` | Tenant-specific runtime overrides |

### Existing Lifecycle Stages (`config/lifecycle/stages.yaml`)

The authoritative YAPPC lifecycle has **8 stages**:

```
intent → context → plan → execute → verify → observe → learn → institutionalize
```

Not `Shape/Generate/Run/Evolve` — those are **not** the YAPPC stage names. All agent-to-phase mappings below use the correct stage names from `stages.yaml`.

---

## Overview

YAPPC agents follow the 3-level hierarchy defined in `config/agents/registry.yaml`:

1. **Level 1 — Strategic** (L1): Product vision, architecture decisions, orchestration of entire domains
2. **Level 2 — Domain Expert** (L2): Deep domain expertise; own a subdomain, can delegate to workers
3. **Level 3 — Worker** (L3): Single, focused task execution — the true atomic units

Orchestration is done by **Orchestrator agents** (L2 or L1) that use `delegation.can_delegate_to` to dispatch subtasks to worker agents. This is the pattern established by existing agents like `incident-management-orchestrator.yaml`, `code-review-orchestrator.yaml`, and `documentation-orchestrator.yaml`.

All agent definitions use the format defined in `config/agents/README.md`:

```yaml
id: {agent-name}
name: Human Readable Name
version: 1.0.0
description: ...

metadata:
  level: 1|2|3
  domain: ...
  tags: [...]

generator:
  type: PIPELINE | LLM | TEMPLATE | RULE_BASED | SERVICE_CALL
  steps: [...]

memory:
  episodic: {enabled, retention_days, categories}
  semantic: {enabled, categories}
  procedural: {enabled, categories}

tools: [...]
capabilities: [...]
routing:
  input_types: [...]
  output_types: [...]
delegation:
  can_delegate_to: [...]
  escalates_to: ...
governance:
  requires_approval: true|false
  can_block: true|false
  audit_trail: standard|comprehensive
performance:
  expected_latency_ms: ...
  max_latency_ms: ...
  timeout_ms: ...
```

---

## Where New Agents Live

New agents defined in this catalog belong in `config/agents/definitions/` alongside the existing 142, using the **same YAML format** as every existing agent file (e.g., `sentinel.yaml`, `incident-response-agent.yaml`). They are then registered in `config/agents/registry.yaml`.

```
config/agents/
├── registry.yaml                          ← ADD new agents here (new phases or existing)
├── definitions/
│   ├── phase9-operations/                 ← existing: 9 agents
│   │   ├── incident-response-agent.yaml   ← existing
│   │   ├── security-operations-agent.yaml ← existing
│   │   ├── cost-optimization-agent.yaml   ← existing
│   │   └── ...
│   ├── phase13-devsecops/                 ← NEW: security/compliance/cloud workers
│   │   ├── finding-triage-agent.yaml
│   │   ├── vulnerability-scoring-agent.yaml
│   │   ├── dependency-license-agent.yaml
│   │   ├── cloud-resource-risk-agent.yaml
│   │   ├── compliance-control-agent.yaml
│   │   └── ...
│   └── orchestrators/                     ← NEW: top-level orchestrators
│       ├── security-posture-orchestrator.yaml
│       ├── cloud-security-audit-orchestrator.yaml
│       ├── compliance-audit-orchestrator.yaml
│       └── full-lifecycle-orchestrator.yaml
```

**Capability extensions** go into `config/agents/capabilities.yaml` (add new `id` entries under `capabilities:`).

---

## Existing Coverage vs. Gaps

Before defining new agents, here is what already exists in `registry.yaml` that covers YAPPC needs:

| Need | Existing Agent | Phase | Level |
|------|---------------|-------|-------|
| Security scanning | `sentinel`, `vuln-scanner`, `security-scanner-agent` | phase6-build | L2/L3 |
| Incident response | `incident-response-agent`, `incident-management-orchestrator` | phase9-operations | L2 |
| Security operations | `security-operations-agent` | phase9-operations | L2 |
| Cost optimization | `cost-optimization-agent` | phase9-operations | L3 |
| Deployment / run | `cloud-pilot`, `ci-cd-agent`, `build-orchestrator-agent` | phase6-build/phase8-release | L2/L3 |
| Code generation | `java-expert`, `java-class-writer`, `code-generator-agent`, `react-expert` | phase6-build | L1/L2/L3 |
| Testing | `test-strategist`, `unit-test-writer`, `integration-test-writer`, `e2e-test-runner` | phase7-testing | L2/L3 |
| Dependency mgmt | `dependency-auditor`, `dependency-update-agent` | phase6-build | L3 |
| Documentation | `documentation-orchestrator`, `documentation-writer`, `api-doc-generator` | phase6-build | L2/L3 |
| Observability | `monitoring-agent`, `capacity-monitor-agent`, `slo-manager-agent` | phase9-operations | L3 |
| Architecture | `systems-architect`, `architecture-diagram-agent` | phase4-architecture | L1 |
| Product planning | `products-officer`, `business-analyst` | phase1-ideation/phase3-req | L1/L2 |

**Gaps** (agents NOT yet in registry): compliance framework assessment, cloud resource discovery/risk scoring, project onboarding, lifecycle stage routing (context/plan/execute), full-lifecycle orchestration.

---

## New Agents to Add

The following agents extend the existing registry. All use the **exact same format** as existing definitions.

---

### New Worker Agents (Level 3)

These are the atomic, single-task agents to be added to `config/agents/definitions/`.

#### 1. `cloud-resource-discovery-agent`

```yaml
id: cloud-resource-discovery-agent
name: Cloud Resource Discovery Agent
version: 1.0.0
description: Discovers and inventories cloud resources across AWS/GCP/Azure accounts.

metadata:
  level: 3
  domain: cloud-security
  tags: [cloud, discovery, inventory, aws, gcp, azure]

generator:
  type: PIPELINE
  steps:
    - name: resource_scan
      type: SERVICE_CALL
      service: cloud-service
      endpoint: /resources/discover
    - name: inventory_analysis
      type: LLM
      provider: ANTHROPIC
      model: claude-3-5-sonnet-20241022
      temperature: 0.1
      max_tokens: 2000
      prompt: |
        Analyze discovered cloud resources:
        Resources: {{resources}}
        Previous Inventory: {{previous}}
        Classify resources, flag new/changed/deleted, assess exposure.
        Output as JSON: {inventory, changes, public_resources, risk_indicators}

memory:
  episodic:
    enabled: true
    retention_days: 365
    categories: [resource-scans, inventory-changes]
  semantic:
    enabled: true
    categories: [cloud-patterns, resource-types]
  procedural:
    enabled: true
    categories: [discovery-rules, classification-rules]

tools: [cloud_connector, resource_classifier]

capabilities:
  - cloud-resource-discovery
  - inventory-management

routing:
  input_types: [cloud-account-id, discovery-request]
  output_types: [resource-inventory, inventory-delta]

delegation:
  can_delegate_to: []
  escalates_to: cloud-security-orchestrator

governance:
  requires_approval: false
  can_block: false
  audit_trail: standard

performance:
  expected_latency_ms: 10000
  max_latency_ms: 60000
  timeout_ms: 120000
```

---

#### 2. `cloud-resource-risk-agent`

```yaml
id: cloud-resource-risk-agent
name: Cloud Resource Risk Scoring Agent
version: 1.0.0
description: Scores cloud resources for security risk based on configuration, exposure, and threat intel.

metadata:
  level: 3
  domain: cloud-security
  tags: [cloud, risk-scoring, security, exposure]

generator:
  type: PIPELINE
  steps:
    - name: config_check
      type: RULE_BASED
      rules:
        - Check public accessibility (S3, RDS, EC2)
        - Check encryption at rest / in transit
        - Check IAM overpermissions
        - Check open security groups
    - name: risk_scoring
      type: LLM
      provider: ANTHROPIC
      model: claude-3-5-sonnet-20241022
      temperature: 0.1
      max_tokens: 1500
      prompt: |
        Score cloud resource risk:
        Resource: {{resource}}
        Config Findings: {{findings}}
        Threat Intel: {{intel}}
        Assign risk score 0-100 with breakdown by category.
        Output as JSON: {risk_score, breakdown, critical_issues, remediation_priority}

memory:
  episodic:
    enabled: true
    retention_days: 365
    categories: [risk-assessments]
  semantic:
    enabled: true
    categories: [risk-patterns, cloud-misconfigurations]
  procedural:
    enabled: true
    categories: [scoring-rules, risk-thresholds]

tools: [cloud_config_analyzer, threat_intel_feed]

capabilities:
  - cloud-risk-scoring
  - misconfiguration-detection
  - exposure-analysis

routing:
  input_types: [cloud-resource, risk-assessment-request]
  output_types: [risk-score, risk-report]

delegation:
  can_delegate_to: []
  escalates_to: cloud-security-orchestrator

governance:
  requires_approval: false
  can_block: false
  audit_trail: standard

performance:
  expected_latency_ms: 3000
  max_latency_ms: 10000
  timeout_ms: 20000
```

---

#### 3. `compliance-control-evaluation-agent`

```yaml
id: compliance-control-evaluation-agent
name: Compliance Control Evaluation Agent
version: 1.0.0
description: Evaluates a workspace against individual controls within a compliance framework (SOC2, GDPR, ISO27001, PCI-DSS).

metadata:
  level: 3
  domain: compliance
  tags: [compliance, soc2, gdpr, iso27001, pci-dss, controls]

generator:
  type: PIPELINE
  steps:
    - name: evidence_collection
      type: SERVICE_CALL
      service: compliance-service
      endpoint: /evidence/collect
    - name: control_evaluation
      type: LLM
      provider: OPENAI
      model: gpt-4
      temperature: 0.1
      max_tokens: 2000
      prompt: |
        Evaluate compliance control:
        Framework: {{framework}}
        Control: {{control}}
        Evidence: {{evidence}}
        Workspace Config: {{config}}
        Determine: PASS / FAIL / NOT_APPLICABLE with justification.
        Output as JSON: {status, score, evidence_summary, gaps, remediation_steps}

memory:
  episodic:
    enabled: true
    retention_days: 1095
    categories: [control-evaluations, audit-history]
  semantic:
    enabled: true
    categories: [control-requirements, framework-mappings]
  procedural:
    enabled: true
    categories: [evaluation-rules, evidence-criteria]

tools: [compliance_checker, evidence_collector]

capabilities:
  - compliance-control-evaluation
  - evidence-analysis
  - gap-identification

routing:
  input_types: [compliance-framework, control-id, workspace-id]
  output_types: [control-result, compliance-gap]

delegation:
  can_delegate_to: []
  escalates_to: compliance-audit-orchestrator

governance:
  requires_approval: false
  can_block: false
  audit_trail: comprehensive

performance:
  expected_latency_ms: 5000
  max_latency_ms: 15000
  timeout_ms: 30000
```

---

#### 4. `compliance-gap-analysis-agent`

```yaml
id: compliance-gap-analysis-agent
name: Compliance Gap Analysis Agent
version: 1.0.0
description: Aggregates control evaluation results and produces a prioritized gap analysis report for a given framework.

metadata:
  level: 3
  domain: compliance
  tags: [compliance, gap-analysis, remediation-planning]

generator:
  type: PIPELINE
  steps:
    - name: gap_aggregation
      type: RULE_BASED
      rules:
        - Aggregate FAIL and partial-PASS controls
        - Weight by control criticality
        - Map gaps to risk categories
    - name: gap_analysis
      type: LLM
      provider: OPENAI
      model: gpt-4
      temperature: 0.2
      max_tokens: 3000
      prompt: |
        Analyze compliance gaps:
        Framework: {{framework}}
        Failed Controls: {{failed}}
        Business Context: {{context}}
        Produce prioritized remediation roadmap with effort estimates.
        Output as JSON: {total_gaps, critical_gaps, remediation_roadmap, estimated_effort, risk_exposure}

memory:
  episodic:
    enabled: true
    retention_days: 730
    categories: [gap-reports, remediation-tracking]
  semantic:
    enabled: true
    categories: [gap-patterns, remediation-strategies]
  procedural:
    enabled: true
    categories: [prioritization-rules]

tools: [gap_aggregator, remediation_planner]

capabilities:
  - compliance-gap-analysis
  - remediation-planning
  - risk-prioritization

routing:
  input_types: [framework-assessment, control-results]
  output_types: [gap-report, remediation-roadmap]

delegation:
  can_delegate_to: []
  escalates_to: compliance-audit-orchestrator

governance:
  requires_approval: false
  can_block: false
  audit_trail: comprehensive

performance:
  expected_latency_ms: 5000
  max_latency_ms: 20000
  timeout_ms: 30000
```

---

#### 5. `vulnerability-scoring-agent`

> **Note:** `sentinel` and `security-scanner-agent` already cover scanning and triage. This agent is additive — it applies CVSS scoring and business-context risk weighting to findings already triaged by `sentinel`.

```yaml
id: vulnerability-scoring-agent
name: Vulnerability Scoring Agent
version: 1.0.0
description: Applies CVSS scoring and business-context risk weighting to security findings from sentinel/security-scanner-agent.

metadata:
  level: 3
  domain: security
  tags: [vulnerability, cvss, risk-scoring, devsecops]

generator:
  type: PIPELINE
  steps:
    - name: cvss_calculation
      type: RULE_BASED
      rules:
        - Apply CVSS v3.1 base score formula
        - Apply temporal score modifiers
        - Apply environmental score (asset value, exposure)
    - name: business_context_weighting
      type: LLM
      provider: ANTHROPIC
      model: claude-3-5-sonnet-20241022
      temperature: 0.1
      max_tokens: 1000
      prompt: |
        Apply business context to vulnerability:
        Finding: {{finding}}
        CVSS Score: {{cvss}}
        Asset: {{asset}} (criticality: {{criticality}})
        Produce final risk priority (P1-P4) with justification.
        Output as JSON: {cvss_score, business_risk, final_priority, justification, sla_days}

memory:
  episodic:
    enabled: true
    retention_days: 730
    categories: [scoring-history, sla-tracking]
  semantic:
    enabled: true
    categories: [cvss-patterns, asset-criticality]
  procedural:
    enabled: true
    categories: [scoring-rules, sla-policy]

tools: [cvss_calculator, asset_registry]

capabilities:
  - vulnerability-scoring
  - cvss-calculation
  - risk-prioritization

routing:
  input_types: [security-finding, asset-context]
  output_types: [scored-finding, risk-priority]

delegation:
  can_delegate_to: []
  escalates_to: sentinel

governance:
  requires_approval: false
  can_block: false
  audit_trail: comprehensive

performance:
  expected_latency_ms: 1000
  max_latency_ms: 3000
  timeout_ms: 5000
```

---

#### 6. `project-onboarding-agent`

```yaml
id: project-onboarding-agent
name: Project Onboarding Agent
version: 1.0.0
description: Onboards a new project/repository into YAPPC — clones repo, detects language/stack, creates Project record, triggers initial security scan.

metadata:
  level: 3
  domain: project-management
  tags: [onboarding, project, repository, setup]

generator:
  type: PIPELINE
  steps:
    - name: repo_analysis
      type: SERVICE_CALL
      service: project-service
      endpoint: /projects/analyze
    - name: tech_stack_detection
      type: LLM
      provider: ANTHROPIC
      model: claude-3-5-sonnet-20241022
      temperature: 0.1
      max_tokens: 1000
      prompt: |
        Analyze repository:
        Files: {{file_tree}}
        Package Files: {{package_files}}
        Detect: primary language, frameworks, build tool, test framework, CI/CD.
        Output as JSON: {language, frameworks, build_tool, test_framework, cicd, confidence}
    - name: project_creation
      type: SERVICE_CALL
      service: project-service
      endpoint: /projects/create

memory:
  episodic:
    enabled: true
    retention_days: 365
    categories: [onboarding-sessions]
  semantic:
    enabled: true
    categories: [tech-stack-patterns, project-archetypes]
  procedural:
    enabled: true
    categories: [onboarding-checklist]

tools: [repo_analyzer, project_creator, scanner_trigger]

capabilities:
  - project-onboarding
  - tech-stack-detection
  - repository-analysis

routing:
  input_types: [repository-url, workspace-id]
  output_types: [project-record, onboarding-report]

delegation:
  can_delegate_to: []
  escalates_to: products-officer

governance:
  requires_approval: false
  can_block: false
  audit_trail: standard

performance:
  expected_latency_ms: 5000
  max_latency_ms: 30000
  timeout_ms: 60000
```

---

#### 7. `context-gathering-agent`

> Fills the `context` lifecycle stage gap. The existing routing in `registry.yaml` maps `context` → research-agent for general use, but YAPPC needs a dedicated context agent that specifically gathers codebase context for development tasks.

```yaml
id: context-gathering-agent
name: Context Gathering Agent
version: 1.0.0
description: Gathers codebase context for a development task — reads relevant files, dependencies, prior decisions, and related tickets.

metadata:
  level: 3
  domain: lifecycle
  tags: [context, codebase-analysis, research, intent, plan]

generator:
  type: PIPELINE
  steps:
    - name: codebase_exploration
      type: SERVICE_CALL
      service: code-service
      endpoint: /context/gather
    - name: context_synthesis
      type: LLM
      provider: OPENAI
      model: gpt-4
      temperature: 0.3
      max_tokens: 4000
      prompt: |
        Synthesize codebase context for task:
        Task: {{task}}
        Relevant Files: {{files}}
        Dependencies: {{deps}}
        Prior ADRs: {{adrs}}
        Recent Commits: {{commits}}
        Produce ContextSummary with: relevant_components, dependencies, constraints, risks, suggested_approach.
        Output as JSON: {context_summary, relevant_components, constraints, risks, suggested_approach}

memory:
  episodic:
    enabled: true
    retention_days: 90
    categories: [context-sessions, codebase-snapshots]
  semantic:
    enabled: true
    categories: [codebase-patterns, architecture-knowledge]
  procedural:
    enabled: true
    categories: [context-gathering-checklist]

tools: [code_reader, dependency_analyzer, ticket_reader]

capabilities:
  - context-gathering
  - codebase-analysis
  - dependency-mapping

routing:
  input_types: [task-description, repository-context]
  output_types: [context-summary, dependency-map]

delegation:
  can_delegate_to: []
  escalates_to: systems-architect

governance:
  requires_approval: false
  can_block: false
  audit_trail: standard

performance:
  expected_latency_ms: 5000
  max_latency_ms: 15000
  timeout_ms: 30000
```

---

#### 8. `institutionalize-agent`

> Fills the `institutionalize` lifecycle stage — the 8th stage in `stages.yaml`. No existing agent is mapped to this stage in `registry.yaml` routing.

```yaml
id: institutionalize-agent
name: Institutionalize Agent
version: 1.0.0
description: Embeds learnings from the learn stage into organizational practices — updates documentation, creates templates, updates standards, and triggers knowledge sharing.

metadata:
  level: 3
  domain: lifecycle
  tags: [institutionalize, knowledge-management, standards, templates]

generator:
  type: PIPELINE
  steps:
    - name: lessons_analysis
      type: LLM
      provider: OPENAI
      model: gpt-4
      temperature: 0.4
      max_tokens: 3000
      prompt: |
        Convert lessons learned into actionable organizational updates:
        Lessons: {{lessons}}
        Current Standards: {{standards}}
        Existing Templates: {{templates}}
        Produce: updated_standards, new_templates, training_materials, automation_candidates.
        Output as JSON: {standards_updates, new_templates, training_materials, automation_candidates, knowledge_articles}
    - name: standards_update
      type: SERVICE_CALL
      service: knowledge-service
      endpoint: /standards/update
    - name: template_creation
      type: SERVICE_CALL
      service: knowledge-service
      endpoint: /templates/create

memory:
  episodic:
    enabled: true
    retention_days: 1095
    categories: [institutionalization-sessions, standards-history]
  semantic:
    enabled: true
    categories: [organizational-patterns, best-practices]
  procedural:
    enabled: true
    categories: [institutionalization-checklist]

tools: [standards_updater, template_creator, knowledge_publisher]

capabilities:
  - knowledge-institutionalization
  - standards-management
  - template-creation

routing:
  input_types: [lessons-learned, improvement-plan]
  output_types: [updated-standards, new-templates, knowledge-articles]

delegation:
  can_delegate_to: []
  escalates_to: products-officer

governance:
  requires_approval: true
  can_block: false
  audit_trail: comprehensive

performance:
  expected_latency_ms: 8000
  max_latency_ms: 30000
  timeout_ms: 60000
```

---

### New Orchestrator Agents (Level 2)

These orchestrate combinations of existing + new worker agents via `delegation.can_delegate_to`.

---

#### O1. `security-posture-orchestrator`

```yaml
id: security-posture-orchestrator
name: Security Posture Orchestrator
version: 1.0.0
description: Orchestrates end-to-end security posture assessment — delegates to sentinel, security-scanner-agent, vulnerability-scoring-agent, dependency-auditor, and security-operations-agent.

metadata:
  level: 2
  domain: security
  tags: [orchestrator, security-posture, devsecops]

generator:
  type: PIPELINE
  steps:
    - name: posture_coordination
      type: RULE_BASED
      rules:
        - Trigger parallel: sentinel scan + dependency-auditor
        - Wait for all scans to complete
        - Apply vulnerability-scoring-agent to findings
        - Aggregate into posture score
    - name: posture_summary
      type: LLM
      provider: OPENAI
      model: gpt-4
      temperature: 0.2
      max_tokens: 3000
      prompt: |
        Summarize security posture:
        Scan Results: {{scan_results}}
        Vulnerability Scores: {{vuln_scores}}
        Dependency Audit: {{dep_audit}}
        Produce overall posture score (0-100), trend vs. last assessment, top priorities.
        Output as JSON: {posture_score, trend, critical_findings, top_priorities, posture_summary}

memory:
  episodic:
    enabled: true
    retention_days: 730
    categories: [posture-assessments, posture-trends]
  semantic:
    enabled: true
    categories: [posture-patterns, benchmark-data]
  procedural:
    enabled: true
    categories: [assessment-workflow, scoring-rules]

tools: [posture_aggregator, trend_analyzer]

capabilities:
  - security-posture-assessment
  - multi-agent-orchestration
  - posture-scoring

routing:
  input_types: [workspace-id, posture-request]
  output_types: [posture-report, posture-score]

delegation:
  can_delegate_to:
    - sentinel
    - security-scanner-agent
    - vulnerability-scoring-agent
    - dependency-auditor
    - security-operations-agent
  escalates_to: systems-architect

governance:
  requires_approval: false
  can_block: false
  audit_trail: comprehensive

performance:
  expected_latency_ms: 30000
  max_latency_ms: 120000
  timeout_ms: 180000
```

---

#### O2. `cloud-security-audit-orchestrator`

```yaml
id: cloud-security-audit-orchestrator
name: Cloud Security Audit Orchestrator
version: 1.0.0
description: Orchestrates cloud security audit — delegates to cloud-resource-discovery-agent, cloud-resource-risk-agent, cost-optimization-agent, and security-operations-agent.

metadata:
  level: 2
  domain: cloud-security
  tags: [orchestrator, cloud-audit, devsecops, finops]

generator:
  type: PIPELINE
  steps:
    - name: audit_coordination
      type: RULE_BASED
      rules:
        - Trigger cloud-resource-discovery-agent
        - For each resource: trigger cloud-resource-risk-agent (parallel, batch 50)
        - Trigger cost-optimization-agent for the account
        - Aggregate results
    - name: audit_summary
      type: LLM
      provider: ANTHROPIC
      model: claude-3-5-sonnet-20241022
      temperature: 0.2
      max_tokens: 3000
      prompt: |
        Summarize cloud security audit:
        Resource Inventory: {{inventory}}
        Risk Scores: {{risk_scores}}
        Cost Analysis: {{costs}}
        Produce executive summary with top risks and savings opportunities.
        Output as JSON: {total_resources, high_risk_count, avg_risk_score, cost_waste, top_risks, savings_opportunities}

memory:
  episodic:
    enabled: true
    retention_days: 365
    categories: [cloud-audits, audit-trends]
  semantic:
    enabled: true
    categories: [cloud-patterns, risk-baselines]
  procedural:
    enabled: true
    categories: [audit-workflow, reporting-rules]

tools: [audit_coordinator, cloud_reporter]

capabilities:
  - cloud-security-audit
  - multi-agent-orchestration
  - cloud-risk-aggregation

routing:
  input_types: [cloud-account-id, audit-request]
  output_types: [cloud-audit-report, risk-summary]

delegation:
  can_delegate_to:
    - cloud-resource-discovery-agent
    - cloud-resource-risk-agent
    - cost-optimization-agent
    - security-operations-agent
  escalates_to: head-of-devops

governance:
  requires_approval: false
  can_block: false
  audit_trail: comprehensive

performance:
  expected_latency_ms: 60000
  max_latency_ms: 300000
  timeout_ms: 600000
```

---

#### O3. `compliance-audit-orchestrator`

```yaml
id: compliance-audit-orchestrator
name: Compliance Audit Orchestrator
version: 1.0.0
description: Orchestrates end-to-end compliance audit for a framework — delegates to compliance-control-evaluation-agent and compliance-gap-analysis-agent, produces final report.

metadata:
  level: 2
  domain: compliance
  tags: [orchestrator, compliance, soc2, gdpr, iso27001]

generator:
  type: PIPELINE
  steps:
    - name: audit_coordination
      type: RULE_BASED
      rules:
        - Load all controls for the framework
        - Trigger compliance-control-evaluation-agent per control (parallel, batch 20)
        - Aggregate pass/fail results
        - Trigger compliance-gap-analysis-agent
    - name: audit_report
      type: TEMPLATE
      engine: liquid
      template_path: templates/compliance/audit-report.liquid

memory:
  episodic:
    enabled: true
    retention_days: 1095
    categories: [compliance-audits, audit-history]
  semantic:
    enabled: true
    categories: [framework-requirements, audit-patterns]
  procedural:
    enabled: true
    categories: [audit-workflow, reporting-rules]

tools: [framework_loader, audit_coordinator, report_generator]

capabilities:
  - compliance-audit-orchestration
  - multi-agent-orchestration
  - compliance-reporting

routing:
  input_types: [framework-id, workspace-id, audit-request]
  output_types: [compliance-audit-report, gap-analysis, compliance-score]

delegation:
  can_delegate_to:
    - compliance-control-evaluation-agent
    - compliance-gap-analysis-agent
    - sentinel
  escalates_to: systems-architect

governance:
  requires_approval: true
  can_block: false
  audit_trail: comprehensive

performance:
  expected_latency_ms: 60000
  max_latency_ms: 300000
  timeout_ms: 600000
```

---

#### O4. `full-lifecycle-orchestrator`

> Routes a task through all relevant lifecycle stages (`intent → context → plan → execute → verify → observe → learn → institutionalize`) by delegating to the appropriate existing and new agents at each stage.

```yaml
id: full-lifecycle-orchestrator
name: Full Lifecycle Orchestrator
version: 1.0.0
description: Orchestrates a complete YAPPC lifecycle run for a development task — routes through all 8 stages (intent→context→plan→execute→verify→observe→learn→institutionalize) by delegating to appropriate agents at each stage.

metadata:
  level: 1
  domain: lifecycle
  tags: [orchestrator, lifecycle, sdlc, strategic]

generator:
  type: PIPELINE
  steps:
    - name: stage_routing
      type: RULE_BASED
      rules:
        - Stage intent → delegate to products-officer
        - Stage context → delegate to context-gathering-agent
        - Stage plan → delegate to systems-architect
        - Stage execute → delegate to java-expert or react-expert (by domain)
        - Stage verify → delegate to test-strategist + sentinel (parallel)
        - Stage observe → delegate to monitoring-agent
        - Stage learn → delegate to postmortem-agent (phase10)
        - Stage institutionalize → delegate to institutionalize-agent
    - name: lifecycle_summary
      type: LLM
      provider: OPENAI
      model: gpt-4
      temperature: 0.3
      max_tokens: 2000
      prompt: |
        Summarize lifecycle execution:
        Task: {{task}}
        Stage Outputs: {{stage_outputs}}
        Produce lifecycle completion report with artifacts, metrics, lessons.
        Output as JSON: {artifacts, metrics, stage_durations, lessons, next_cycle_recommendations}

memory:
  episodic:
    enabled: true
    retention_days: 365
    categories: [lifecycle-runs, stage-outputs]
  semantic:
    enabled: true
    categories: [lifecycle-patterns, workflow-optimizations]
  procedural:
    enabled: true
    categories: [stage-routing-rules, transition-criteria]

tools: [lifecycle_coordinator, artifact_manager, metrics_collector]

capabilities:
  - full-lifecycle-orchestration
  - stage-routing
  - multi-agent-orchestration

routing:
  input_types: [task-request, project-id, workspace-id]
  output_types: [lifecycle-result, artifacts, metrics]

delegation:
  can_delegate_to:
    - products-officer
    - context-gathering-agent
    - systems-architect
    - java-expert
    - react-expert
    - test-strategist
    - sentinel
    - monitoring-agent
    - institutionalize-agent
  escalates_to: master-orchestrator-agent

governance:
  requires_approval: false
  can_block: false
  audit_trail: comprehensive

performance:
  expected_latency_ms: 60000
  max_latency_ms: 3600000
  timeout_ms: 7200000
```

---

## Registry Updates Required

The following changes must be made to `config/agents/registry.yaml` to register the new agents:

```yaml
# Add to registry.yaml statistics:
statistics:
  total_agents: 153        # was 142, +11 new agents
  level_1_orchestrators: 14  # +1 (full-lifecycle-orchestrator)
  level_2_experts: 60        # +3 (security-posture, cloud-security-audit, compliance-audit orchestrators)
  level_3_workers: 79        # +7 new workers

# Add new phase:
phases:
  phase13-devsecops: "definitions/phase13-devsecops/"   # 7 agents

# Add to hierarchy.level_1_strategic:
  - id: agent.yappc.full-lifecycle-orchestrator
    definition: definitions/orchestrators/full-lifecycle-orchestrator.yaml

# Add to hierarchy.level_2_experts:
  - id: agent.yappc.security-posture-orchestrator
    definition: definitions/orchestrators/security-posture-orchestrator.yaml
    domain: security
    reports_to: [agent.yappc.systems-architect]

  - id: agent.yappc.cloud-security-audit-orchestrator
    definition: definitions/orchestrators/cloud-security-audit-orchestrator.yaml
    domain: cloud-security
    reports_to: [agent.yappc.head-of-devops]

  - id: agent.yappc.compliance-audit-orchestrator
    definition: definitions/orchestrators/compliance-audit-orchestrator.yaml
    domain: compliance
    reports_to: [agent.yappc.systems-architect]

# Add to hierarchy.level_3_workers:
  - id: agent.yappc.cloud-resource-discovery-agent
    definition: definitions/phase13-devsecops/cloud-resource-discovery-agent.yaml
    category: cloud-security
    reports_to: [agent.yappc.cloud-security-audit-orchestrator]

  - id: agent.yappc.cloud-resource-risk-agent
    definition: definitions/phase13-devsecops/cloud-resource-risk-agent.yaml
    category: cloud-security
    reports_to: [agent.yappc.cloud-security-audit-orchestrator]

  - id: agent.yappc.compliance-control-evaluation-agent
    definition: definitions/phase13-devsecops/compliance-control-evaluation-agent.yaml
    category: compliance
    reports_to: [agent.yappc.compliance-audit-orchestrator]

  - id: agent.yappc.compliance-gap-analysis-agent
    definition: definitions/phase13-devsecops/compliance-gap-analysis-agent.yaml
    category: compliance
    reports_to: [agent.yappc.compliance-audit-orchestrator]

  - id: agent.yappc.vulnerability-scoring-agent
    definition: definitions/phase13-devsecops/vulnerability-scoring-agent.yaml
    category: security-analysis
    reports_to: [agent.yappc.sentinel]

  - id: agent.yappc.project-onboarding-agent
    definition: definitions/phase13-devsecops/project-onboarding-agent.yaml
    category: project-management
    reports_to: [agent.yappc.products-officer]

  - id: agent.yappc.context-gathering-agent
    definition: definitions/phase13-devsecops/context-gathering-agent.yaml
    category: lifecycle
    reports_to: [agent.yappc.systems-architect]

  - id: agent.yappc.institutionalize-agent
    definition: definitions/phase13-devsecops/institutionalize-agent.yaml
    category: lifecycle
    reports_to: [agent.yappc.products-officer]

# Update routing to cover missing stages:
routing:
  task_routing:
    intent: agent.yappc.products-officer            # existing
    context: agent.yappc.context-gathering-agent    # NEW
    plan: agent.yappc.systems-architect             # existing
    generate: agent.yappc.java-expert               # existing (renamed from 'generate')
    run: agent.yappc.cloud-pilot                    # existing
    observe: agent.yappc.monitoring-agent           # existing
    secure: agent.yappc.sentinel                    # existing
    institutionalize: agent.yappc.institutionalize-agent  # NEW
```

---

## Capability Extensions (`config/agents/capabilities.yaml`)

New capability entries to append to the existing `capabilities:` list:

```yaml
  # ---------------------------------------------------------------------------
  # Cloud Security Capabilities (new)
  # ---------------------------------------------------------------------------
  - id: cloud-resource-discovery
    name: Cloud Resource Discovery
    description: Discover and inventory cloud resources across providers
    category: analysis
    agent_capability: SecurityAnalysis
    specialization: cloud-discovery

  - id: cloud-risk-scoring
    name: Cloud Risk Scoring
    description: Score cloud resources for security risk
    category: analysis
    agent_capability: SecurityAnalysis
    specialization: cloud-risk

  # ---------------------------------------------------------------------------
  # Compliance Capabilities (new)
  # ---------------------------------------------------------------------------
  - id: compliance-control-evaluation
    name: Compliance Control Evaluation
    description: Evaluate workspace against compliance framework controls
    category: analysis
    agent_capability: SecurityAnalysis
    specialization: compliance

  - id: compliance-gap-analysis
    name: Compliance Gap Analysis
    description: Analyze and prioritize compliance gaps
    category: analysis
    agent_capability: SecurityAnalysis
    specialization: compliance-gap

  # ---------------------------------------------------------------------------
  # Lifecycle Capabilities (new)
  # ---------------------------------------------------------------------------
  - id: context-gathering
    name: Context Gathering
    description: Gather and synthesize codebase context for a task
    category: research
    agent_capability: Research
    specialization: codebase-context

  - id: knowledge-institutionalization
    name: Knowledge Institutionalization
    description: Embed lessons learned into organizational practices
    category: documentation
    agent_capability: Documentation
    specialization: institutionalization
```

---

## Domain Mapping Extensions (`config/agents/mappings.yaml`)

New domain mappings to add:

```yaml
  # D27. Cloud Security (new)
  cloud-security:
    primary_agent: cloud-security-audit-orchestrator
    fallback_agents:
      - security-analyzer
    execution_mode: async

  # D28. Compliance Management (new)
  compliance-management:
    primary_agent: compliance-audit-orchestrator
    fallback_agents:
      - security-analyzer
    execution_mode: async

  # D29. Project Lifecycle (new — fills context stage gap)
  project-lifecycle:
    primary_agent: full-lifecycle-orchestrator
    fallback_agents:
      - ai-assistant
    execution_mode: async
```

---

## Summary

### New Agents Added

| Agent | Level | Domain | Fills Gap |
|-------|:-----:|--------|-----------|
| `cloud-resource-discovery-agent` | L3 | cloud-security | Cloud inventory not covered |
| `cloud-resource-risk-agent` | L3 | cloud-security | Cloud risk scoring not covered |
| `compliance-control-evaluation-agent` | L3 | compliance | No compliance agents existed |
| `compliance-gap-analysis-agent` | L3 | compliance | No compliance agents existed |
| `vulnerability-scoring-agent` | L3 | security | Extends sentinel with CVSS scoring |
| `project-onboarding-agent` | L3 | project-management | No project onboarding agent |
| `context-gathering-agent` | L3 | lifecycle | `context` stage had no dedicated agent |
| `institutionalize-agent` | L3 | lifecycle | `institutionalize` stage not covered in routing |
| `security-posture-orchestrator` | L2 | security | No cross-agent posture assessment |
| `cloud-security-audit-orchestrator` | L2 | cloud-security | No cloud audit orchestration |
| `compliance-audit-orchestrator` | L2 | compliance | No compliance orchestration |
| `full-lifecycle-orchestrator` | L1 | lifecycle | No end-to-end lifecycle coordination |

**Total new agents: 12** (8 workers + 3 L2 orchestrators + 1 L1 strategic)
**Updated registry total: 154 agents** (142 existing + 12 new)

### What Was NOT Added (Already Covered)

| Need | Covered By (existing) |
|------|-----------------------|
| Security scanning | `sentinel`, `security-scanner-agent`, `vuln-scanner` |
| Incident response | `incident-response-agent`, `incident-management-orchestrator` |
| Cost analysis | `cost-optimization-agent` |
| Deployment | `cloud-pilot`, `ci-cd-agent` |
| Code generation | `java-expert`, `java-class-writer`, `react-expert`, `code-generator-agent` |
| Testing | `test-strategist`, `unit-test-writer`, `e2e-test-runner` |
| Dependency auditing | `dependency-auditor`, `dependency-update-agent` |
| Observability | `monitoring-agent`, `slo-manager-agent`, `capacity-monitor-agent` |
| Documentation | `documentation-orchestrator`, `documentation-writer` |

### Lifecycle Stage Coverage (after additions)

| Stage | Routing Agent | Status |
|-------|---------------|--------|
| `intent` | `products-officer` | ✅ existing |
| `context` | `context-gathering-agent` | ✅ new |
| `plan` | `systems-architect` | ✅ existing |
| `execute` | `java-expert` / `react-expert` | ✅ existing |
| `verify` | `test-strategist` + `sentinel` | ✅ existing |
| `observe` | `monitoring-agent` | ✅ existing |
| `learn` | `postmortem-agent` (phase10) | ✅ existing |
| `institutionalize` | `institutionalize-agent` | ✅ new |
