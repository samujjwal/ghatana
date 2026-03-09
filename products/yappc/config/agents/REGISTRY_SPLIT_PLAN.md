# Agent Registry Split Plan

## Overview
Split the monolithic agent registry (194 agents, 663 lines) into domain-specific catalogs
for better maintainability, scalability, and discoverability.

## Current State
- **Single File:** `config/agents/registry.yaml` (663 lines)
- **Total Agents:** 194
- **Organization:** By phases (phase0-meta through phase13-devsecops)
- **Issues:**
  - Difficult to navigate
  - Merge conflicts with multiple contributors
  - Hard to understand domain boundaries
  - No clear ownership

## Proposed Structure

### Domain-Specific Catalogs

```
config/agents/
├── _index.yaml              # Aggregated registry index
├── catalog-schema.yaml      # Schema definitions
├── platform-catalog.yaml    # Platform-level agents (14)
├── devsecops-catalog.yaml   # Security/DevOps agents (39)
├── lifecycle-catalog.yaml   # SDLC agents (45)
├── compliance-catalog.yaml  # Compliance agents (24)
├── cloud-catalog.yaml       # Cloud/infrastructure agents (32)
├── integration-catalog.yaml # Integration/notification agents (20)
└── governance-catalog.yaml  # Governance/guardrail agents (20)
```

### Catalog Details

#### platform-catalog.yaml (14 agents)
**Purpose:** Core platform orchestration and management
**Agents:**
- full-lifecycle-orchestrator
- security-posture-orchestrator
- platform-health-monitor
- agent-dispatcher
- event-coordinator
- (9 more platform agents)

#### devsecops-catalog.yaml (39 agents)
**Purpose:** Security scanning, DevOps operations, deployment
**Agents:**
- sbom-signer-agent
- supply-chain-verifier-agent
- canary-analysis-agent
- rollback-coordinator-agent
- cloud-resource-discovery-agent
- vulnerability-scoring-agent
- (33 more devsecops agents)

#### lifecycle-catalog.yaml (45 agents)
**Purpose:** SDLC phase-specific agents
**Agents organized by phase:**
- Phase 0 (Intent): context-gathering-agent, requirements-analyzer
- Phase 1 (Shape): architecture-design-agent, component-planner
- Phase 2 (Validate): validation-agent, risk-assessment-agent
- Phases 3-8: (39 more agents across generate, run, observe, improve)

#### compliance-catalog.yaml (24 agents)
**Purpose:** Compliance frameworks and auditing
**Agents:**
- compliance-control-evaluation-agent
- compliance-gap-analysis-agent
- soc2-auditor
- gdpr-compliance-agent
- (20 more compliance agents)

#### cloud-catalog.yaml (32 agents)
**Purpose:** Cloud infrastructure management
**Agents:**
- cloud-resource-discovery-agent
- cloud-resource-risk-agent
- multi-cloud-orchestrator
- cost-optimization-agent
- (28 more cloud agents)

#### integration-catalog.yaml (20 agents)
**Purpose:** Third-party integrations and notifications
**Agents:**
- feature-flag-integration-agent
- notification-agent
- slack-integration-agent
- github-integration-agent
- (16 more integration agents)

#### governance-catalog.yaml (20 agents)
**Purpose:** Governance, guardrails, and approval workflows
**Agents:**
- release-governance-agent
- budget-gate-agent
- dependency-gate-agent
- policy-enforcement-agent
- (16 more governance agents)

## Implementation

### Aggregation Pattern
The `_index.yaml` file aggregates all catalogs:

```yaml
apiVersion: ghatana.yappc/v2
kind: AgentRegistryIndex
metadata:
  version: 3.0.0
  description: "Aggregated agent registry index"
  
spec:
  catalogs:
    - name: platform
      file: platform-catalog.yaml
      priority: 1
      
    - name: devsecops
      file: devsecops-catalog.yaml
      priority: 2
      
    - name: lifecycle
      file: lifecycle-catalog.yaml
      priority: 3
      
    - name: compliance
      file: compliance-catalog.yaml
      priority: 4
      
    - name: cloud
      file: cloud-catalog.yaml
      priority: 5
      
    - name: integration
      file: integration-catalog.yaml
      priority: 6
      
    - name: governance
      file: governance-catalog.yaml
      priority: 7
      
  aggregation:
    mode: merge
    deduplicate: true
    validate: true
```

### Java Loader Updates

```java
// New CatalogRegistry class
public interface DomainCatalog {
    String getName();
    List<AgentDefinition> getAgents();
    int getPriority();
}

public class AggregatedAgentRegistry {
    private final Map<String, DomainCatalog> catalogs;
    
    public AgentDefinition findAgent(String id) {
        // Search across all catalogs
        for (DomainCatalog catalog : catalogs.values()) {
            AgentDefinition agent = catalog.findAgent(id);
            if (agent != null) return agent;
        }
        return null;
    }
}
```

## Migration Timeline

### Phase 1: Foundation (Week 1)
- Create catalog schema v2
- Implement aggregation loader
- Create `_index.yaml`

### Phase 2: Catalog Migration (Weeks 2-4)
- Week 2: platform-catalog.yaml, devsecops-catalog.yaml
- Week 3: lifecycle-catalog.yaml, compliance-catalog.yaml
- Week 4: cloud-catalog.yaml, integration-catalog.yaml, governance-catalog.yaml

### Phase 3: Validation (Week 5)
- Run validation on split registry
- Update all tests
- Performance testing

### Phase 4: Cleanup (Week 6)
- Remove monolithic registry.yaml
- Update documentation
- Announce to teams

## Benefits

1. **Maintainability:** Smaller, focused files
2. **Team Ownership:** Clear domain ownership
3. **Parallel Development:** Teams can modify their catalogs independently
4. **Discoverability:** Easier to find agents by domain
5. **Scalability:** Add new domains without growing existing files

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Breaking changes | Maintain backward compatibility during migration |
| Search complexity | Implement efficient cross-catalog search |
| Validation complexity | Update validateAgentCatalog task for multi-file |
| Documentation gaps | Update all docs to reference new structure |

## Success Criteria

- [ ] All 194 agents distributed across 7 catalogs
- [ ] `_index.yaml` aggregates correctly
- [ ] Validation passes on split structure
- [ ] Zero breaking changes for consumers
- [ ] Load time improved or maintained
- [ ] Team documentation updated
