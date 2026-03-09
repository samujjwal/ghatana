# Software-Org Configuration Analysis & Virtual-Org Enhancement Plan

## Executive Summary

This document analyzes the configuration files in `libs/java/software-org/src/main/resources/` and provides a plan to integrate them with the virtual-org UI for complete CRUD management.

---

## 1. Configuration Structure Overview

### 1.1 Directory Structure

```
resources/
├── config/
│   └── departments/           # 5 department configs (agents, workflows, KPIs)
│       ├── devops.yaml
│       ├── engineering.yaml
│       ├── executive.yaml
│       ├── product.yaml
│       └── qa.yaml
├── devsecops/
│   ├── mappings/              # 3 mapping files
│   │   ├── persona_registry.yaml      # 27 personas
│   │   ├── phase_personas.yaml        # 9 phases with persona assignments
│   │   └── stage_phase_mapping.yaml   # 16 stage-to-phase mappings
│   ├── plan/agents/           # Planning phase agents
│   └── specs/agents/          # 420 legacy agent specs (deprecated)
│   └── specs2/                # v2 pipeline specs
│       ├── pipelines/
│       └── stages/
├── operations/
│   ├── OPERATOR_INDEX.yaml    # Master operator catalog
│   ├── cross-cutting/         # 14 cross-cutting operators
│   └── domain/                # Domain operators by area
│       ├── planning/          # 5 operators
│       ├── design/            # 6 operators
│       ├── operate/           # 2 operators
│       └── release/           # 2 operators
└── workflows/
    └── v3/
        ├── pipeline.yaml      # Master pipeline definition
        ├── stages/            # 18 stage definitions
        └── tests/
```

### 1.2 Entity Types Identified

| Entity Type                 | Location                                      | Count | Status      |
| --------------------------- | --------------------------------------------- | ----- | ----------- |
| **Departments**             | `config/departments/`                         | 5     | ✅ Complete |
| **Agents (per dept)**       | `config/departments/*.yaml`                   | ~15   | ✅ Complete |
| **Personas**                | `devsecops/mappings/persona_registry.yaml`    | 27    | ✅ Complete |
| **Phases**                  | `devsecops/mappings/phase_personas.yaml`      | 9     | ✅ Complete |
| **Stages**                  | `devsecops/mappings/stage_phase_mapping.yaml` | 16    | ✅ Complete |
| **Domain Operators**        | `operations/domain/`                          | 15    | ✅ Complete |
| **Cross-cutting Operators** | `operations/cross-cutting/`                   | 14    | ✅ Complete |
| **Workflow Stages**         | `workflows/v3/stages/`                        | 18    | ✅ Complete |
| **KPIs**                    | `config/departments/*.yaml`                   | ~20   | ✅ Complete |
| **Tools**                   | `config/departments/*.yaml`                   | ~10   | ✅ Complete |

---

## 2. Gap Analysis

### 2.1 Missing Configuration Files

| Gap                     | Description                               | Priority | Recommendation                                    |
| ----------------------- | ----------------------------------------- | -------- | ------------------------------------------------- |
| **Services Config**     | No dedicated service definitions          | HIGH     | Create `config/services/` with service YAML files |
| **Integrations Config** | No integration definitions                | HIGH     | Create `config/integrations/` with tool configs   |
| **Org Metadata**        | No root org config                        | MEDIUM   | Create `config/org.yaml` with org-level settings  |
| **Persona Bindings**    | Personas exist but no permission bindings | MEDIUM   | Enhance `persona_registry.yaml` with permissions  |
| **DevSecOps Flows**     | No persona-specific flow definitions      | MEDIUM   | Create `config/flows/` with flow configs          |
| **Environment Config**  | No environment tier definitions           | LOW      | Create `config/environments.yaml`                 |

### 2.2 Missing Content in Existing Files

#### `persona_registry.yaml` - Missing Fields

```yaml
# Current structure:
personas:
  - id: product_manager
    display_name: "Product Manager / Product Owner"
    tags: [product, strategy, leadership]

# Recommended additions:
personas:
  - id: product_manager
    display_name: "Product Manager / Product Owner"
    tags: [product, strategy, leadership]
    # NEW FIELDS:
    description: "Owns product vision and roadmap"
    permissions: [read:all, write:requirements, approve:release]
    default_phases: [PRODUCT_LIFECYCLE_PHASE_PROBLEM_DISCOVERY, PRODUCT_LIFECYCLE_PHASE_IDEATION]
    quick_actions:
      - id: qa-roadmap
        label: "Roadmap"
        icon: "📋"
        href: "/roadmap"
```

#### `phase_personas.yaml` - Missing Fields

```yaml
# Current structure has phases with persona IDs
# Recommended additions per phase:
phases:
  - id: PRODUCT_LIFECYCLE_PHASE_PROBLEM_DISCOVERY
    display_name: "Phase 1 – Problem Discovery & Ideation"
    personas: [...]
    # NEW FIELDS:
    description: "Identify customer problems and ideate solutions"
    entry_criteria:
      - "Sponsorship identified"
      - "Initial stakeholders available"
    exit_criteria:
      - "Problem statement validated"
      - "Initial hypotheses documented"
    kpis:
      - id: discovery-cycle-time
        target: "< 2 weeks"
```

#### Department Configs - Missing Fields

```yaml
# Current structure has agents, workflows, KPIs
# Recommended additions:
department:
  id: engineering
  # NEW FIELDS:
  parent_id: null # For nested org structure
  service_ids: [svc-api-gateway, svc-auth]
  integration_ids: [int-github, int-jira]
  budget_allocation: 0.35 # 35% of org budget
  headcount: 25
  location: "Remote-first"
```

### 2.3 Schema Inconsistencies

| Issue                          | Location              | Fix                                   |
| ------------------------------ | --------------------- | ------------------------------------- |
| Agent IDs not standardized     | `config/departments/` | Use pattern: `{dept}-{role}-{number}` |
| Missing `@doc.*` tags          | Some YAML files       | Add documentation tags                |
| Inconsistent capability naming | Across departments    | Create capability taxonomy            |
| No version field               | Most configs          | Add `version: "1.0.0"` to all         |

---

## 3. Recommended New Configuration Files

### 3.1 `config/org.yaml` - Organization Root Config

```yaml
# Organization Root Configuration
# @doc.type config
# @doc.purpose Organization-level settings

organization:
  id: software-org
  name: "Virtual Software Organization"
  description: "AI-powered software development organization"
  version: "1.0.0"

  settings:
    default_timezone: "UTC"
    work_hours:
      start: "09:00"
      end: "18:00"
    sprint_duration_days: 14

  departments:
    - ref: config/departments/engineering.yaml
    - ref: config/departments/devops.yaml
    - ref: config/departments/product.yaml
    - ref: config/departments/qa.yaml
    - ref: config/departments/executive.yaml

  integrations:
    - ref: config/integrations/github.yaml
    - ref: config/integrations/jira.yaml
    - ref: config/integrations/datadog.yaml
```

### 3.2 `config/services/` - Service Definitions

```yaml
# config/services/api-gateway.yaml
service:
  id: svc-api-gateway
  name: "API Gateway"
  description: "Central API gateway for all external traffic"
  department_id: dept-platform
  tier: tier-0
  risk_level: critical

  slo:
    availability: 99.99
    latency_p95_ms: 50
    error_rate_threshold: 0.1

  dependencies: []
  dependents: [svc-web-app, svc-mobile-bff]

  environments:
    - development
    - staging
    - production

  integration_ids: [int-datadog, int-pagerduty]
  tags: [gateway, critical-path]

  repository:
    url: "https://github.com/org/api-gateway"
    branch: main

  documentation:
    url: "https://docs.example.com/api-gateway"
```

### 3.3 `config/integrations/` - Integration Definitions

```yaml
# config/integrations/github.yaml
integration:
  id: int-github
  name: "GitHub"
  type: source-control
  description: "Source code management and CI/CD"
  enabled: true

  department_ids: [dept-platform, dept-product, dept-data]
  service_ids: [svc-api-gateway, svc-auth, svc-web-app]
  managed_by_personas: [admin, lead]

  config:
    org: "example-org"
    default_branch: main
    require_reviews: true

  external_url: "https://github.com"
  icon: "🐙"
  status: healthy
```

### 3.4 `config/flows/` - DevSecOps Flow Definitions

```yaml
# config/flows/engineer.yaml
flow:
  id: flow-engineer
  name: "Engineer Flow"
  persona_id: engineer
  description: "End-to-end development flow for engineers"

  phases:
    - intake
    - plan
    - build
    - verify
    - review
    - staging
    - deploy
    - operate
    - learn

  steps:
    - step_id: eng-1
      phase_id: intake
      label: "View Story"
      route: "/work-items/:storyId"
      permissions: [read:stories]

    - step_id: eng-2
      phase_id: plan
      label: "Plan Implementation"
      route: "/work-items/:storyId/plan"
      permissions: [write:plans]
```

---

## 4. Implementation Plan

### Phase 1: Backend Config Loader Service (Week 1)

#### 4.1 Create Config Loader Service

**File:** `apps/backend/src/services/config-loader.service.ts`

```typescript
/**
 * Configuration Loader Service
 *
 * Loads and parses YAML configuration files from the resources directory.
 * Provides typed access to all organization configuration entities.
 *
 * @doc.type service
 * @doc.purpose Load YAML configs from resources
 * @doc.layer infrastructure
 */

import * as fs from 'fs';
import * as path from 'path';
import * as yaml from 'js-yaml';

export interface ConfigLoaderOptions {
  basePath: string;
  watchForChanges?: boolean;
}

export class ConfigLoaderService {
  private basePath: string;
  private cache: Map<string, unknown> = new Map();

  constructor(options: ConfigLoaderOptions) {
    this.basePath = options.basePath;
  }

  // Load all departments
  async loadDepartments(): Promise<DepartmentConfig[]> { ... }

  // Load all personas
  async loadPersonas(): Promise<PersonaConfig[]> { ... }

  // Load all phases
  async loadPhases(): Promise<PhaseConfig[]> { ... }

  // Load all stages
  async loadStages(): Promise<StageConfig[]> { ... }

  // Load all operators
  async loadOperators(): Promise<OperatorConfig[]> { ... }

  // Load all workflows
  async loadWorkflows(): Promise<WorkflowConfig[]> { ... }

  // Load full org config
  async loadOrgConfig(): Promise<OrgConfig> { ... }
}
```

#### 4.2 Create API Routes

**File:** `apps/backend/src/routes/config.ts`

```typescript
// GET /api/v1/config/departments
// GET /api/v1/config/departments/:id
// POST /api/v1/config/departments
// PUT /api/v1/config/departments/:id
// DELETE /api/v1/config/departments/:id

// Similar endpoints for:
// - /api/v1/config/personas
// - /api/v1/config/phases
// - /api/v1/config/stages
// - /api/v1/config/operators
// - /api/v1/config/workflows
// - /api/v1/config/services
// - /api/v1/config/integrations
```

### Phase 2: Frontend API Integration (Week 1-2)

#### 4.3 Enhance virtualOrgApi

Add new endpoints for all config entities:

```typescript
// services/api/configApi.ts
export const configApi = {
  // Departments
  getDepartments: () => ...,
  getDepartment: (id: string) => ...,
  createDepartment: (data) => ...,
  updateDepartment: (id, data) => ...,
  deleteDepartment: (id) => ...,

  // Personas
  getPersonas: () => ...,
  getPersona: (id: string) => ...,
  // ... CRUD operations

  // Phases
  getPhases: () => ...,
  // ... CRUD operations

  // Stages
  getStages: () => ...,
  // ... CRUD operations

  // Operators
  getOperators: () => ...,
  // ... CRUD operations

  // Workflows
  getWorkflows: () => ...,
  // ... CRUD operations
};
```

#### 4.4 Create React Query Hooks

```typescript
// hooks/useConfig.ts
export function useDepartments() { ... }
export function useCreateDepartment() { ... }
export function useUpdateDepartment() { ... }
export function useDeleteDepartment() { ... }

// Similar hooks for all entities
```

### Phase 3: UI Components (Week 2-3)

#### 4.5 Config Management Pages

| Page                     | Route                  | Purpose                         |
| ------------------------ | ---------------------- | ------------------------------- |
| **Config Dashboard**     | `/config`              | Overview of all config entities |
| **Departments Manager**  | `/config/departments`  | CRUD for departments            |
| **Personas Manager**     | `/config/personas`     | CRUD for personas               |
| **Phases Manager**       | `/config/phases`       | CRUD for phases                 |
| **Stages Manager**       | `/config/stages`       | CRUD for stages                 |
| **Operators Manager**    | `/config/operators`    | CRUD for operators              |
| **Workflows Manager**    | `/config/workflows`    | CRUD for workflows              |
| **Services Manager**     | `/config/services`     | CRUD for services               |
| **Integrations Manager** | `/config/integrations` | CRUD for integrations           |

#### 4.6 Shared Components

```
components/config/
├── ConfigEntityList.tsx      # Generic list with search/filter
├── ConfigEntityForm.tsx      # Generic form for CRUD
├── ConfigEntityCard.tsx      # Card display for entities
├── ConfigYamlEditor.tsx      # Monaco YAML editor
├── ConfigValidationPanel.tsx # Show validation errors
├── ConfigImportExport.tsx    # Import/export YAML
└── ConfigDiffViewer.tsx      # Show changes before save
```

### Phase 4: Enhanced Org Builder (Week 3-4)

#### 4.7 Integrate with OrgBuilderPage

- Add tabs for each entity type
- Enable inline editing
- Add drag-and-drop for relationships
- Show validation errors in real-time
- Add import/export functionality

---

## 5. TypeScript Types

### 5.1 New Types Needed

```typescript
// types/config.ts

export interface PersonaConfig {
  id: string;
  display_name: string;
  description?: string;
  tags: string[];
  permissions: string[];
  default_phases: string[];
  quick_actions: QuickAction[];
}

export interface PhaseConfig {
  id: string;
  display_name: string;
  description?: string;
  personas: string[];
  entry_criteria: string[];
  exit_criteria: string[];
  kpis: KpiConfig[];
}

export interface StageConfig {
  id: string;
  phases: string[];
}

export interface OperatorConfig {
  id: string;
  version: string;
  domain: string;
  category: string;
  description: string;
  modes: OperatorMode[];
  validation: ValidationRules;
  hitl: HitlConfig;
  dependencies: DependencyConfig;
  metrics: MetricConfig[];
}

export interface WorkflowStageConfig {
  name: string;
  description: string;
  domain: string;
  operators: OperatorRef[];
  cross_cutting: CrossCuttingRef[];
  workflow: WorkflowStep[];
  entry_criteria: string[];
  exit_criteria: string[];
}
```

---

## 6. Validation Rules

### 6.1 Cross-Entity Validation

| Rule                                | Description                                                             |
| ----------------------------------- | ----------------------------------------------------------------------- |
| **Persona-Phase Consistency**       | Personas in `phase_personas.yaml` must exist in `persona_registry.yaml` |
| **Department-Agent Consistency**    | Agent department IDs must match parent file                             |
| **Operator-Stage Consistency**      | Operators referenced in stages must exist in `operations/`              |
| **Workflow-Operator Consistency**   | Workflow steps must reference valid operators and modes                 |
| **Service-Department Consistency**  | Service department IDs must exist                                       |
| **Integration-Service Consistency** | Integration service IDs must exist                                      |

### 6.2 Schema Validation

- All entities must have `id` field
- All entities should have `version` field
- All entities should have `@doc.*` tags
- IDs must follow naming conventions

---

## 7. Migration Path

### 7.1 Immediate Actions (No Code Changes)

1. ✅ Create `config/org.yaml` with org metadata
2. ✅ Create `config/services/` directory with service YAMLs
3. ✅ Create `config/integrations/` directory with integration YAMLs
4. ✅ Enhance `persona_registry.yaml` with permissions and actions
5. ✅ Add `version` field to all config files

### 7.2 Backend Changes

1. Create `ConfigLoaderService`
2. Add config API routes
3. Add validation middleware
4. Add file watcher for hot reload

### 7.3 Frontend Changes

1. Enhance `virtualOrgApi` with config endpoints
2. Create React Query hooks
3. Build config management UI
4. Integrate with OrgBuilderPage

---

## 8. Success Metrics

| Metric                           | Target             |
| -------------------------------- | ------------------ |
| Config entities loadable via API | 100%               |
| CRUD operations working          | All entities       |
| Validation coverage              | 100% of rules      |
| UI pages complete                | All 9 entity types |
| Hot reload working               | < 1s refresh       |

---

## Appendix A: Entity Relationship Diagram

```
┌─────────────────┐
│   Organization  │
└────────┬────────┘
         │ has many
         ▼
┌─────────────────┐     ┌─────────────────┐
│   Departments   │────▶│    Services     │
└────────┬────────┘     └────────┬────────┘
         │ has many              │ uses
         ▼                       ▼
┌─────────────────┐     ┌─────────────────┐
│     Agents      │     │  Integrations   │
└─────────────────┘     └─────────────────┘
         │
         │ assigned to
         ▼
┌─────────────────┐     ┌─────────────────┐
│    Personas     │────▶│     Phases      │
└─────────────────┘     └────────┬────────┘
                                 │ maps to
                                 ▼
                        ┌─────────────────┐
                        │     Stages      │
                        └────────┬────────┘
                                 │ uses
                                 ▼
                        ┌─────────────────┐
                        │   Operators     │
                        └────────┬────────┘
                                 │ in
                                 ▼
                        ┌─────────────────┐
                        │   Workflows     │
                        └─────────────────┘
```

---

## Appendix B: File Counts Summary

| Directory                   | Files  | Lines (approx) |
| --------------------------- | ------ | -------------- |
| `config/departments/`       | 5      | ~1,200         |
| `devsecops/mappings/`       | 3      | ~250           |
| `operations/cross-cutting/` | 14     | ~2,800         |
| `operations/domain/`        | 15     | ~6,000         |
| `workflows/v3/stages/`      | 18     | ~4,500         |
| **Total**                   | **55** | **~14,750**    |

---

_Document Version: 1.0.0_
_Last Updated: 2025-01-XX_
_Author: Cascade AI_
