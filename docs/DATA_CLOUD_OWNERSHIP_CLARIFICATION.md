# Data Cloud Ownership Clarification

**Status:** Final
**Last Updated:** 2026-04-19
**Applies To:** Data Cloud, AEP, Shared Platform

## Purpose

This document clarifies long-term ownership boundaries between Data Cloud, AEP (Agent Execution Platform), and the shared platform. It prevents duplicate effort, ensures clear accountability, and defines escalation paths.

## Ownership Model

### Platform Layer (Shared Ownership)

The following components are owned by the platform team and shared across all products:

| Component | Owner | Escalation Path |
|-----------|-------|-----------------|
| `platform/java/core` | Platform Team | Platform Tech Lead |
| `platform/java/database` | Platform Team | Platform Tech Lead |
| `platform/java/http` | Platform Team | Platform Tech Lead |
| `platform/java/observability` | Platform Team | Platform Tech Lead |
| `platform/java/security` | Platform Team | Platform Tech Lead |
| `platform/java/testing` | Platform Team | Platform Tech Lead |
| `platform/java/agent-core` | Platform Team | Platform Tech Lead |
| `platform/java/ai-integration` | Platform Team | Platform Tech Lead |
| `platform/typescript/*` | Platform Team | Platform Tech Lead |
| `platform/contracts` | Platform Team | Platform Tech Lead |

**Responsibilities:**
- Maintain backward compatibility
- Provide stable APIs
- Handle cross-product bug fixes
- Manage dependency upgrades
- Ensure security compliance

**Change Process:**
1. Platform team proposes change
2. Impact review with product owners
3. 30-day deprecation notice for breaking changes
4. Migration guide provided

### Data Cloud Layer (Product Ownership)

The following components are owned exclusively by the Data Cloud product team:

| Component | Owner | Escalation Path |
|-----------|-------|-----------------|
| `products/data-cloud/platform-launcher` | Data Cloud Team | Data Cloud Tech Lead |
| `products/data-cloud/platform-api` | Data Cloud Team | Data Cloud Tech Lead |
| `products/data-cloud/platform-entity` | Data Cloud Team | Data Cloud Tech Lead |
| `products/data-cloud/launcher` | Data Cloud Team | Data Cloud Tech Lead |
| `products/data-cloud/ui` | Data Cloud Team | Data Cloud Tech Lead |
| `products/data-cloud/agent-registry` | Data Cloud Team | Data Cloud Tech Lead |
| `products/data-cloud/agent-catalog` | Data Cloud Team | Data Cloud Tech Lead |
| `products/data-cloud/integration-tests` | Data Cloud Team | Data Cloud Tech Lead |

**Responsibilities:**
- Implement Data Cloud-specific business logic
- Define product requirements
- Manage product-specific configurations
- Own product roadmap
- Handle customer-facing features

**Change Process:**
1. Data Cloud team proposes change
2. Platform impact review (if touching platform)
3. Product team approval
4. Deployment via Data Cloud CI/CD

### AEP Layer (Product Ownership)

The following components are owned exclusively by the AEP product team:

| Component | Owner | Escalation Path |
|-----------|-------|-----------------|
| `products/aep/*` | AEP Team | AEP Tech Lead |
| AEP-specific agent catalog entries | AEP Team | AEP Tech Lead |
| AEP workflow templates | AEP Team | AEP Tech Lead |

**Responsibilities:**
- Implement AEP-specific business logic
- Define AEP requirements
- Manage AEP-specific configurations
- Own AEP roadmap
- Handle AEP customer features

## Shared Components with Clear Boundaries

### Agent Catalog

- **Definition File:** `products/data-cloud/agent-catalog/agent-catalog.yaml`
- **Owner:** Data Cloud Team
- **Shared By:** AEP may reference Data Cloud agents, but Data Cloud owns the catalog
- **Escalation:** Data Cloud Tech Lead

**Boundary:**
- Data Cloud defines the catalog schema and structure
- AEP can add AEP-specific agents in AEP's own catalog
- Platform provides the agent-core runtime (shared)

### Agent Registry

- **Implementation:** `products/data-cloud/agent-registry`
- **Owner:** Data Cloud Team
- **Shared By:** AEP uses the same registry pattern but with AEP-specific implementations
- **Escalation:** Data Cloud Tech Lead

**Boundary:**
- Platform provides agent-core interfaces
- Data Cloud implements the registry for Data Cloud
- AEP implements AEP-specific registry using platform interfaces

### AI Integration

- **Platform Layer:** `platform/java/ai-integration` (owned by Platform Team)
- **Data Cloud Layer:** Data Cloud-specific AI services in `platform-api`
- **AEP Layer:** AEP-specific AI services in `products/aep`

**Boundary:**
- Platform provides generic AI integration interfaces
- Each product implements product-specific AI services
- No cross-product AI model sharing without explicit agreement

## Dependency Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    Platform Layer (Shared)                    │
│  core, database, http, observability, security, testing    │
│                    agent-core, ai-integration                │
└─────────────────────────────────────────────────────────────┘
                            ▲
                            │ depends on
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
┌───────┴────────┐  ┌────────┴────────┐  ┌────┴──────────┐
│  Data Cloud   │  │      AEP       │  │ Other Products│
│   Product     │  │    Product     │  │   (Future)    │
└────────────────┘  └─────────────────┘  └───────────────┘
```

## Escalation Matrix

| Issue Type | Primary Owner | Secondary Owner | Escalation Path |
|------------|---------------|-----------------|-----------------|
| Platform API breaking change | Platform Team | All Product Teams | Platform Tech Lead → Architecture Review Board |
| Data Cloud bug | Data Cloud Team | Platform Team (if platform involved) | Data Cloud Tech Lead → Product VP |
| AEP bug | AEP Team | Platform Team (if platform involved) | AEP Tech Lead → Product VP |
| Cross-product dependency conflict | Platform Team | All Product Teams | Architecture Review Board |
| Security vulnerability | Platform Team | All Product Teams | CISO → All Product VPs |

## Decision Rights

### Platform Layer Decisions

- **Platform Team:** Approves all platform layer changes
- **Product Teams:** Consulted on breaking changes
- **Architecture Board:** Final arbiter for cross-platform disputes

### Data Cloud Product Decisions

- **Data Cloud Team:** Owns all Data Cloud product decisions
- **Platform Team:** Consulted on platform impact
- **AEP Team:** Consulted on shared agent/AI decisions

### AEP Product Decisions

- **AEP Team:** Owns all AEP product decisions
- **Platform Team:** Consulted on platform impact
- **Data Cloud Team:** Consulted on shared agent/AI decisions

## Communication Channels

### Regular Syncs

- **Platform-Product Sync:** Weekly (Platform Tech Lead + Product Tech Leads)
- **Data Cloud-AEP Sync:** Bi-weekly (Data Cloud Tech Lead + AEP Tech Lead)
- **Architecture Review:** Monthly (All Tech Leads + Architecture Board)

### Issue Tracking

- Platform issues: `platform/*` GitHub projects
- Data Cloud issues: `products/data-cloud` GitHub projects
- AEP issues: `products/aep` GitHub projects
- Cross-product issues: Create in Platform project, tag all relevant teams

## Migration Path

This ownership model is effective immediately. Any existing cross-team ownership should be transitioned:

1. **Week 1:** Identify all shared components with unclear ownership
2. **Week 2:** Assign clear ownership per this document
3. **Week 3:** Update code ownership files (CODEOWNERS)
4. **Week 4:** Update CI/CD permissions and review processes

## Review Cycle

This document will be reviewed quarterly or when:
- A new product is added
- A major platform component is introduced
- Cross-team ownership disputes arise
- Architecture patterns significantly change

## References

- [Platform Architecture Guide](../architecture/)
- [Data Cloud README](../products/data-cloud/README.md)
- [AEP README](../products/aep/README.md)
- [Governance Model](GOVERNANCE.md)
