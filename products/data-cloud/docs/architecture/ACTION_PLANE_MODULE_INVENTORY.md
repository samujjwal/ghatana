# Action Plane Module Inventory

**Canonical source:** generated Gradle includes plus `scripts/list-data-cloud-active-modules.mjs`.

Data-Cloud's Action Plane is AEP-powered. Data-Cloud owns persisted action metadata, review evidence, policy evidence, audit evidence, and storage integrations. AEP owns the adaptive event-processing semantics used by the Action Plane. AEP semantic modules are co-located under `products/data-cloud/planes/action/*` as a deliberate architectural decision to maintain tight integration between Data Cloud's data plane and AEP's event processing capabilities. This co-location does not transfer product ownership to Data-Cloud and does not imply AEP semantic/product readiness.

**Boundary Enforcement:** Data/Event/Context/Governance planes have Gradle `validateBoundaryRules` tasks that prevent dependencies on Action internals (engine, orchestrator, agent-runtime, central-runtime, operator-contracts). Action Plane semantics must be accessed through shared SPI contracts only.

## Inventory

| Gradle Module | Gradle Included? | Ownership Classification | Implementation State | Implementation Critical? | Migration Destination | Placement Status | Semantic Readiness | Runtime Readiness | API Readiness | UI Readiness | Test Coverage |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `:products:data-cloud:planes:action` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains as Data-Cloud Action Plane root aggregator | complete | partial | partial | partial | partial | partial |
| `:products:data-cloud:planes:action:operator-contracts` | yes | AEP-owned semantic module co-located | active | yes | Remains co-located for Data Cloud-AEP integration | complete | partial (PatternSpec compiler incomplete) | partial | partial | N/A | partial |
| `:products:data-cloud:planes:action:central-runtime` | yes | AEP-owned semantic module co-located | active | yes | Remains co-located for Data Cloud-AEP integration | complete | partial (replay-safe execution partial) | partial | partial | N/A | partial |
| `:products:data-cloud:planes:action:engine` | yes | AEP-owned semantic module co-located | active | yes | Remains co-located for Data Cloud-AEP integration | complete | partial (learning-to-recommendation incomplete) | partial | partial | N/A | partial |
| `:products:data-cloud:planes:action:registry` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains in Data-Cloud registry surface | complete | active | active | active | partial | partial |
| `:products:data-cloud:planes:action:analytics` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains in Data-Cloud analytics surface | complete | active | active | partial | partial | partial |
| `:products:data-cloud:planes:action:security` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains in Data-Cloud security surface | complete | active | active | active | N/A | partial |
| `:products:data-cloud:planes:action:event-bridge` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains as Data-Cloud persistence bridge | complete | partial (EventCloud SPI incomplete) | partial | partial | N/A | partial |
| `:products:data-cloud:planes:action:agent-runtime` | yes | AEP-owned semantic module co-located | active | yes | Remains co-located for Data Cloud-AEP integration | complete | partial (replay-safe execution partial) | partial | partial | N/A | partial |
| `:products:data-cloud:planes:action:api` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains in Data-Cloud API surface | complete | active | active | active | N/A | partial |
| `:products:data-cloud:planes:action:scaling` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains in Data-Cloud operations surface | complete | active | partial | partial | N/A | partial |
| `:products:data-cloud:planes:action:observability` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains in Data-Cloud observability surface | complete | partial | partial | partial | partial | partial |
| `:products:data-cloud:planes:action:orchestrator` | yes | AEP-owned semantic module co-located | active | yes | Remains co-located for Data Cloud-AEP integration | complete | partial (replay-safe lifecycle partial) | partial | partial | N/A | partial |
| `:products:data-cloud:planes:action:server` | yes | Data-Cloud-owned delivery module | active | yes | Remains in Data-Cloud delivery surface | complete | active | active | active | N/A | partial |
| `:products:data-cloud:planes:action:identity` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains in Data-Cloud identity surface | complete | active | active | active | N/A | partial |
| `:products:data-cloud:planes:action:compliance` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains in Data-Cloud compliance surface | complete | active | active | active | N/A | partial |
| `:products:data-cloud:planes:action:kernel-bridge` | yes | Temporary compatibility module | temporary | yes | `products/data-cloud/extensions/kernel-bridge` (completed) | migrated | N/A | N/A | N/A | N/A | N/A |
| `:products:data-cloud:planes:action:gateway` | no | Migration-only module | temporary | no | Route work moved to delivery/API | removed | N/A | N/A | N/A | N/A | N/A |
| `:products:data-cloud:planes:action:k8s:multi-region` | no | Migration-only module | temporary | no | Deployment assets under `products/data-cloud/deploy` | removed | N/A | N/A | N/A | N/A | N/A |
| `:products:data-cloud:planes:action:agent-catalog` | no | Migration-only module | temporary | no | `products/data-cloud/extensions/agent-catalog` (completed) | migrated | N/A | N/A | N/A | N/A | N/A |

**Status Key:**
- **active**: Fully implemented and operational
- **partial**: Implementation in progress with known gaps
- **N/A**: Not applicable for this module type
- **complete**: Migration or placement complete
- **removed**: Module removed from Action Plane

**Important:** No module should be considered production-ready for lifecycle-proof semantics unless it has: executable PatternSpec compiler, EventCloud SPI integration, pattern lifecycle management, learning-to-recommendation loop, and replay-safe agent execution with side-effect controls.

## Ownership Rules

- **AEP-owned semantic modules permanently co-located**: PatternSpec/EPL, CEP, EventOperatorCapability runtime behavior, EventCloud semantics, adaptive pattern learning, predictive/recommended pattern behavior. These modules remain under Data Cloud's Action Plane to enable tight integration with Data Cloud's data plane while maintaining clear AEP ownership through package structure and documentation.
- **Data-Cloud-owned persistence/metadata/governance modules**: storage bridges, metadata registries, audit, retention, encryption, identity, security, compliance, observability, and operational persistence. These modules are owned by Data Cloud and provide the persistence layer for Action Plane operations.
- **Data-Cloud-owned delivery modules**: HTTP server, API surface, and gateway components that expose Action Plane capabilities to external consumers.
- **Migration-completed modules**: kernel-bridge and agent-catalog have been successfully migrated to the extensions directory and are no longer in the Action Plane module tree.

## Boundary Enforcement

Gradle tasks enforce the following rules:
- **Data plane** (`:products:data-cloud:planes:data:entity`): `validateBoundaryRules` prevents dependencies on Action internals
- **Event plane** (`:products:data-cloud:planes:event:core`): `validateBoundaryRules` prevents dependencies on Action internals
- **Governance plane** (`:products:data-cloud:planes:governance:core`): `validateBoundaryRules` prevents dependencies on Action internals

Forbidden dependencies for Data/Event/Governance planes:
- `:products:data-cloud:planes:action:engine`
- `:products:data-cloud:planes:action:orchestrator`
- `:products:data-cloud:planes:action:agent-runtime`
- `:products:data-cloud:planes:action:central-runtime`
- `:products:data-cloud:planes:action:operator-contracts`

These boundary rules run as part of the `check` task for each plane.

## Architectural Decision

**Decision**: AEP semantic modules (operator-contracts, central-runtime, engine, agent-runtime, orchestrator) will remain permanently co-located under `products/data-cloud/planes/action/*`.

**Rationale**:
1. Data Cloud's data plane requires tight integration with AEP's event processing capabilities
2. Co-location enables efficient data flow between Data Cloud entities and AEP event streams
3. Shared SPI contracts provide the necessary abstraction layer for ownership separation
4. Gradle boundary rules enforce proper dependency direction
5. Package structure and documentation maintain clear AEP ownership

**Module Placement Status**: COMPLETED - no further location migration is required for the co-located Action Plane modules.

**Semantic/Product Readiness Status**: PARTIAL - the placement decision does not complete AEP semantics. Completion still requires executable proof for the unified operator model, PatternSpec compiler, EventCloud SPI, pattern lifecycle, learning-to-recommendation loop, replay-safe agent execution, and targeted checks.

## References

- [Plane Architecture](./PLANE_ARCHITECTURE.md)
- [Action Plane Boundary Evidence](../../../../.kernel/evidence/action-plane-boundaries.json)
