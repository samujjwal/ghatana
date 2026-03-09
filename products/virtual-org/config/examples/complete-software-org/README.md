# Complete Software Organization Example

This directory contains a **fully configuration-driven software organization** demonstrating how to define an entire company's virtual organization through YAML configuration files.

## Overview

This example shows:
- ✅ Complete organization definition without Java code
- ✅ Multi-department structure (Engineering, QA, DevOps)
- ✅ Agent definitions with AI configurations
- ✅ Task types with SLAs and escalation
- ✅ Cross-department workflows
- ✅ KPIs and metrics
- ✅ Event subscriptions
- ✅ Integration configurations

## Directory Structure

```
complete-software-org/
├── organization.yaml           # Main organization configuration
├── departments/
│   ├── engineering.yaml        # Engineering department
│   ├── qa.yaml                 # QA department
│   └── devops.yaml             # DevOps department
├── workflows/                  # (coming soon)
│   ├── sprint-planning.yaml
│   └── feature-delivery.yaml
├── interactions/               # (coming soon)
│   └── eng-qa-handoff.yaml
└── README.md                   # This file
```

## How to Use

### 1. Set Environment Variables

```bash
export TENANT_ID="your-tenant-id"
export ENV="development"
export AI_PROVIDER="openai"
export AI_MODEL="gpt-4-turbo"
export GITHUB_ORG="your-org"
export SLACK_WORKSPACE="your-workspace"
# ... other required variables
```

### 2. Load Configuration

```java
// Using ConfigRegistry
ConfigRegistry registry = new ConfigRegistry();
registry.loadAll(Path.of("config/examples/complete-software-org"));

// Get organization
OrganizationConfig orgConfig = registry.getOrganization().orElseThrow();

// Build organization from config
AbstractOrganization org = OrganizationFactory.create(tenantId, registry);
```

### 3. Start Organization

```java
// Start the organization
org.start().whenComplete((result, error) -> {
    if (error == null) {
        System.out.println("Organization started successfully!");
    }
});
```

## Configuration Highlights

### Agent AI Configuration

Each agent can have custom AI configuration:

```yaml
ai:
  enabled: true
  provider: openai
  model: gpt-4-turbo
  systemPrompt: |
    You are a Lead Engineer responsible for:
    - Code review and quality assurance
    - Technical design decisions
    ...
```

### Task Types with SLAs

Define task types with automatic escalation:

```yaml
taskTypes:
  - name: bug_fix
    priority: high
    slaHours: 8
    requiredCapabilities: [debugging, java]
    escalation:
      - at: 80%
        to: lead-engineer
      - at: 100%
        to: cto-agent
```

### KPIs

Track department performance:

```yaml
kpis:
  - name: velocity
    displayName: "Sprint Velocity"
    type: gauge
    unit: story_points
    target: 80
    warningThreshold: 60
    criticalThreshold: 40
```

### Department Workflows

Define workflows within departments:

```yaml
workflows:
  - name: code-review-workflow
    steps:
      - name: submit-pr
        agent: engineer
        action: create_pull_request
      - name: review
        agent: senior-engineer
        action: review_code
        waitFor: [submit-pr]
      - name: approve
        agent: lead-engineer
        action: approve_merge
        waitFor: [review]
```

## Extending This Example

### Add a New Department

1. Create `departments/new-department.yaml`
2. Reference it in `organization.yaml`:
   ```yaml
   departments:
     - ref: departments/new-department.yaml
   ```

### Add Custom Actions

1. Create `actions/custom-action.yaml`
2. Reference in organization or department config

### Add Workflows

1. Create `workflows/custom-workflow.yaml`
2. Reference in organization config

## Benefits of Configuration-Driven Approach

| Benefit | Description |
|---------|-------------|
| **No Code Changes** | Modify organization structure without recompiling |
| **Hot Reload** | Changes apply without restart (when enabled) |
| **Version Control** | Track organizational changes in git |
| **Environment Specific** | Use variables for different environments |
| **Validation** | JSON Schema ensures configuration correctness |
| **Reusability** | Share configurations across organizations |

## Related Documentation

- [CONFIGURATION_DRIVEN_ARCHITECTURE.md](../../../docs/CONFIGURATION_DRIVEN_ARCHITECTURE.md)
- [EXTENSIBILITY_GUIDE.md](../../../docs/EXTENSIBILITY_GUIDE.md)
- [ConfigRegistry.java](../../../libs/java/framework/src/main/java/com/ghatana/virtualorg/framework/config/ConfigRegistry.java)
