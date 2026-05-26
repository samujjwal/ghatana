# Action Plane Module Inventory

**Canonical source:** generated Gradle includes plus `scripts/list-data-cloud-active-modules.mjs`.

Data-Cloud's Action Plane is AEP-powered. Data-Cloud owns persisted action metadata, review evidence, policy evidence, audit evidence, and storage integrations. AEP owns the adaptive event-processing semantics used by the Action Plane. AEP semantic modules may remain under `products/data-cloud/planes/action/*` during migration, but that is code-location reality only and does not transfer product ownership to Data-Cloud.

## Inventory

| Gradle Module | Gradle Included? | Ownership Classification | Implementation State | Release Blocking? | Migration Destination |
| --- | --- | --- | --- | --- | --- |
| `:products:data-cloud:planes:action` | yes | Temporary compatibility module | temporary | yes | Split root aggregator after AEP module migration |
| `:products:data-cloud:planes:action:operator-contracts` | yes | AEP-owned semantic module temporarily co-located | active | yes | `products/aep/operator-contracts` |
| `:products:data-cloud:planes:action:central-runtime` | yes | AEP-owned semantic module temporarily co-located | active | yes | `products/aep/central-runtime` |
| `:products:data-cloud:planes:action:engine` | yes | AEP-owned semantic module temporarily co-located | active | yes | `products/aep/engine` |
| `:products:data-cloud:planes:action:registry` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains in Data-Cloud registry surface |
| `:products:data-cloud:planes:action:analytics` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains in Data-Cloud analytics surface |
| `:products:data-cloud:planes:action:security` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains in Data-Cloud security surface |
| `:products:data-cloud:planes:action:event-bridge` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains as Data-Cloud persistence bridge |
| `:products:data-cloud:planes:action:agent-runtime` | yes | AEP-owned semantic module temporarily co-located | active | yes | `products/aep/agent-runtime` |
| `:products:data-cloud:planes:action:api` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains in Data-Cloud API surface |
| `:products:data-cloud:planes:action:scaling` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains in Data-Cloud operations surface |
| `:products:data-cloud:planes:action:observability` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains in Data-Cloud observability surface |
| `:products:data-cloud:planes:action:orchestrator` | yes | AEP-owned semantic module temporarily co-located | active | yes | `products/aep/orchestrator` |
| `:products:data-cloud:planes:action:server` | yes | Temporary compatibility module | temporary | yes | Remove after routes move to delivery/API or AEP |
| `:products:data-cloud:planes:action:identity` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains in Data-Cloud identity surface |
| `:products:data-cloud:planes:action:compliance` | yes | Data-Cloud-owned persistence/metadata/governance module | active | yes | Remains in Data-Cloud compliance surface |
| `:products:data-cloud:planes:action:kernel-bridge` | yes | Migration-only module | temporary | yes | `products/data-cloud/extensions/kernel-bridge` |
| `:products:data-cloud:planes:action:gateway` | no | Migration-only module | planned | no | Not generated; route work belongs in delivery/API |
| `:products:data-cloud:planes:action:k8s:multi-region` | no | Migration-only module | planned | no | Deployment assets under `products/data-cloud/deploy` |
| `:products:data-cloud:planes:action:agent-catalog` | no | Migration-only module | planned | no | `products/data-cloud/extensions/agent-catalog` |

## Ownership Rules

- AEP-owned semantic modules temporarily co-located: PatternSpec/EPL, CEP, EventOperatorCapability runtime behavior, EventCloud semantics, adaptive pattern learning, predictive/recommended pattern behavior.
- Data-Cloud-owned persistence/metadata/governance modules: storage bridges, metadata registries, audit, retention, encryption, identity, security, compliance, observability, and operational persistence.
- Temporary compatibility modules: runtime shims kept only while routes and module ownership are split.
- Migration-only modules: inventory entries retained to document moved, planned, or removed modules; they must not be marked active unless generated Gradle settings include them.

## References

- [Plane Architecture](./PLANE_ARCHITECTURE.md)
- [Action Plane Boundary Evidence](../../../../.kernel/evidence/action-plane-boundaries.json)
