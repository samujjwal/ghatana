# Action Plane Module Inventory

**Status:** Current as of commit d951b48a6ee8197ec6bb24919e21134844fe30c6  
**Owner:** Data-Cloud/AEP Team  
**Purpose:** Catalog of all Action Plane modules with roles, ownership, and production status

## Module Inventory

| Module | Role | Owner | Release-blocking | Public surface | Allowed dependencies | Required tests |
| ------ | ---- | ----- | ---------------- | -------------- | -------------------- | -------------- |
| action | Core orchestration | AEP | Yes | PatternSpec compiler, runtime adapter | operator-contracts, central-runtime | Unit, integration |
| operator-contracts | Capability contracts | AEP | Yes | EventOperatorCapability, AgentCapability | platform:java:agent-core, platform:java:ai-integration | Unit, contract |
| central-runtime | Runtime coordination | AEP | Yes | Pipeline execution interface | operator-contracts, engine | Unit, integration |
| engine | Pattern execution engine | AEP | Yes | DAG executor, pattern lifecycle | operator-contracts, registry | Unit, integration |
| registry | Pattern/agent registry | AEP | Yes | Pattern storage, versioning | operator-contracts | Unit, integration |
| analytics | Pattern learning | AEP | Advisory | Learning pipeline, pattern scoring | engine, registry | Unit, integration |
| security | Security policies | AEP | Yes | Policy enforcement, auth | operator-contracts, compliance | Unit, security |
| event-bridge | EventCloud bridge | AEP | Yes | AEP EventCloud SPI | operator-contracts, planes:event:store | Unit, integration |
| agent-runtime | Agent execution | AEP | Yes | Agent capability adapter | operator-contracts, agent-catalog | Unit, integration |
| api | REST API | Data-Cloud | Yes | Pattern CRUD, lifecycle | action, registry | Unit, API contract |
| scaling | Auto-scaling | Data-Cloud | Advisory | Scaling policies | engine, observability | Unit, integration |
| observability | Metrics/tracing | AEP | Yes | Metrics, tracing, logging | engine, agent-runtime | Unit, observability |
| orchestrator | Workflow orchestration | Data-Cloud | Advisory | Workflow-to-pipeline adapter | action, engine | Unit, integration |
| server | Deployment server | Data-Cloud | Yes | HTTP server, gRPC | api, gateway | Unit, integration |
| identity | Identity management | Data-Cloud | Yes | Tenant identity, auth | security, gateway | Unit, security |
| compliance | Compliance policies | Data-Cloud | Yes | Audit, retention, redaction | security, governance | Unit, compliance |
| kernel-bridge | Kernel integration | AEP | Advisory | Platform kernel SPI | platform-kernel:kernel-core | Unit, integration |
| agent-catalog | Agent catalog | AEP | Yes | Agent capability registry | platform:java:agent-core | Unit, integration |
| gateway | API gateway | Data-Cloud | Yes | Request routing, rate limiting | api, identity | Unit, integration |

## Module Categories

### Core Runtime (Release-blocking)
- **action**: Core orchestration and PatternSpec compilation
- **operator-contracts**: EventOperatorCapability and agent capability contracts
- **central-runtime**: Runtime coordination and pipeline execution
- **engine**: Pattern execution engine and DAG processing
- **registry**: Pattern and agent versioning registry
- **agent-runtime**: Agent capability execution adapter
- **agent-catalog**: Agent capability registry and metadata

### Infrastructure (Release-blocking)
- **security**: Security policy enforcement
- **event-bridge**: AEP EventCloud persistence bridge
- **observability**: Metrics, tracing, and logging
- **identity**: Tenant identity and authentication
- **compliance**: Audit, retention, and redaction policies
- **server**: HTTP/gRPC deployment server
- **api**: REST API for pattern CRUD and lifecycle
- **gateway**: API gateway with routing and rate limiting

### Advisory (Non-release-blocking)
- **analytics**: Pattern learning and scoring (still experimental)
- **scaling**: Auto-scaling policies (still experimental)
- **orchestrator**: Workflow orchestration (still experimental)
- **kernel-bridge**: Platform kernel integration (migration-only)

## Migration Notes

### Migration-only modules
- **kernel-bridge**: Temporary bridge for platform kernel integration. Will be removed when platform-kernel is fully integrated.

### Advisory modules
- **analytics**: Pattern learning is experimental and not yet production-ready.
- **scaling**: Auto-scaling policies are under development.
- **orchestrator**: Workflow orchestration is in early development.

## Dependency Rules

### Allowed internal dependencies
- Core runtime modules may depend on operator-contracts
- Infrastructure modules may depend on core runtime
- Advisory modules may depend on core runtime but not vice versa
- All modules may depend on platform:java:* shared modules

### Forbidden dependencies
- Non-action planes must not import Action Plane internals
- Delivery UI/API must not import backend internals except through generated clients
- Extensions must depend only on SPI/contracts
- Contracts must not depend on implementation packages

## Production Status

### Production-ready
All release-blocking modules are production-ready with:
- Comprehensive unit and integration tests
- Security and compliance validation
- Observability and monitoring
- Documentation and examples

### Experimental
Advisory modules are experimental and:
- May have incomplete test coverage
- May have unstable APIs
- Are not yet certified for production use
- Should not be used in production deployments

## References

- [Action Plane Architecture](../ARCHITECTURE.md)
- [PatternSpec Specification](../../../aep/docs/specs/PATTERNSPEC.md)
- [EventOperatorCapability Specification](../../../aep/docs/specs/EVENT_OPERATOR_CAPABILITY_SPEC.md)
- [Data-Cloud Architecture](../../ARCHITECTURE.md)
