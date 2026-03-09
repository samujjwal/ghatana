# Virtual-Org Documentation Index

**Last Updated:** November 26, 2025

---

## Overview

Virtual-Org is the **canonical pluggable framework for AI-powered virtual organizations** on the Ghatana platform. This index provides navigation to all Virtual-Org documentation.

---

## 📚 Documentation Structure

### Core Documentation

| Document | Purpose | Audience |
|----------|---------|----------|
| [VIRTUAL_ORG_MASTER_ARCHITECTURE.md](./VIRTUAL_ORG_MASTER_ARCHITECTURE.md) | **Master architecture guide** - Vision, requirements, implementation status | All |
| [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) | **Detailed implementation plan** - Phased roadmap to production-quality autonomous organization | Engineering |
| [CONFIGURATION_DRIVEN_ARCHITECTURE.md](./CONFIGURATION_DRIVEN_ARCHITECTURE.md) | **YAML-based organization configuration** - Define orgs, departments, agents, workflows via config | All |
| [EXTENSIBILITY_GUIDE.md](./EXTENSIBILITY_GUIDE.md) | **Complete extensibility guide** for both Virtual-Org and Software-Org | Plugin Developers |
| [README.md](./README.md) | Quick overview | New Developers |

### Configuration Types Reference

| Config Type | Java Class | Example File | Purpose |
|------------|-----------|--------------|---------|
| **Organization** | `OrganizationConfig.java` | `organization.yaml` | Main organization definition |
| **Department** | `DepartmentConfig.java` | `departments/*.yaml` | Department structure |
| **Agent** | `AgentConfig.java` | `agents/*.yaml` | Agent definitions |
| **Action** | `ActionConfig.java` | `actions/code-review.yaml` | Atomic units of work |
| **Persona** | `PersonaConfig.java` | `personas/senior-engineer.yaml` | Agent identity & behavior |
| **Task** | `TaskConfig.java` | `tasks/feature-implementation.yaml` | Work unit definitions |
| **Result** | `ResultConfig.java` | `results/code-review-results.yaml` | Output processing rules |
| **Lifecycle** | `AgentLifecycleConfig.java` | `lifecycle/engineering-agent-lifecycle.yaml` | Agent lifecycle management |
| **Interaction** | `InteractionConfig.java` | `interactions/eng-qa-protocol.yaml` | Inter-agent protocols |
| **Workflow** | `WorkflowConfig.java` | `workflows/sprint-planning.yaml` | Multi-step processes |

---

## 🗺️ Quick Navigation

### I want to...

| Goal | Start Here |
|------|-----------|
| Understand Virtual-Org vision and architecture | [VIRTUAL_ORG_MASTER_ARCHITECTURE.md](./VIRTUAL_ORG_MASTER_ARCHITECTURE.md) |
| **See the implementation roadmap** | [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) |
| **Define organizations via YAML config** | [CONFIGURATION_DRIVEN_ARCHITECTURE.md](./CONFIGURATION_DRIVEN_ARCHITECTURE.md) |
| **Define agent actions** | [CONFIGURATION_DRIVEN_ARCHITECTURE.md#7-action-configuration](./CONFIGURATION_DRIVEN_ARCHITECTURE.md#7-action-configuration) |
| **Configure agent personas & behavior** | [CONFIGURATION_DRIVEN_ARCHITECTURE.md#8-persona-configuration](./CONFIGURATION_DRIVEN_ARCHITECTURE.md#8-persona-configuration) |
| **Define task types & SLAs** | [CONFIGURATION_DRIVEN_ARCHITECTURE.md#9-task-configuration](./CONFIGURATION_DRIVEN_ARCHITECTURE.md#9-task-configuration) |
| **Manage agent lifecycle** | [CONFIGURATION_DRIVEN_ARCHITECTURE.md#11-agent-lifecycle-configuration](./CONFIGURATION_DRIVEN_ARCHITECTURE.md#11-agent-lifecycle-configuration) |
| **Use the Configuration Registry** | [CONFIGURATION_DRIVEN_ARCHITECTURE.md#13-configuration-registry](./CONFIGURATION_DRIVEN_ARCHITECTURE.md#13-configuration-registry) |
| Create a new organization plugin | [EXTENSIBILITY_GUIDE.md](./EXTENSIBILITY_GUIDE.md) |
| See example configurations | [config/examples/](../config/examples/) |

---

## 📁 Module Structure

```
products/virtual-org/
├── config/                     # Configuration files
│   ├── schema/                 #   JSON Schema for validation
│   └── examples/               #   Example configurations
│       ├── organization.yaml
│       ├── actions/
│       ├── personas/
│       ├── tasks/
│       ├── results/
│       ├── lifecycle/
│       └── complete-software-org/  # Full software-org example
├── libs/java/
│   ├── framework/              # Core Framework
│   │   └── config/             #   Configuration POJOs
│   ├── virtualorg-agent/       # Lightweight agent
│   ├── org-events/             # Event definitions
│   └── workflows/              # Reusable workflows
├── contracts/proto/            # API contracts
└── docs/                       # Documentation (you are here)
```

---

## 🔗 Related Documentation

| Project | Document |
|---------|----------|
| Software-Org (Reference Plugin) | [SOFTWARE_ORG_PLUGIN_SPECIFICATION.md](../../software-org/docs/SOFTWARE_ORG_PLUGIN_SPECIFICATION.md) |
| Platform Coding Guidelines | [/.github/copilot-instructions.md](../../../.github/copilot-instructions.md) |

---

*Document Version: 2.0.0*
