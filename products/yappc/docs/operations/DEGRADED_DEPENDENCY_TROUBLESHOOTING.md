# Degraded Dependency Troubleshooting

Use this guide when a phase packet, Run/Observe view, or kernel-health route reports degraded Data Cloud, AEP, or Kernel truth. The goal is to preserve fail-closed behavior while giving operators a short path from symptom to recovery.

## First Response

1. Capture the `correlationId`, `tenantId`, `workspaceId`, `projectId`, lifecycle phase, dependency name, and truth source from the UI or API response.
2. Confirm whether unsafe actions are disabled. If a degraded packet still allows phase advance, treat that as an authorization incident.
3. Check the backend logs for the same `correlationId`.
4. Check whether the degraded dependency is isolated to one tenant/project or affects all requests.
5. After remediation, reload the phase packet or kernel-health detail route and confirm degraded details are gone or replaced with a more specific blocker.

## Data Cloud Truth Degraded

| Signal | What to check | Remediation | Verification |
| --- | --- | --- | --- |
| Phase packet shows degraded evidence, governance, project state, run status, or Kernel truth sourced from Data Cloud. | Query logs for the request `correlationId`; inspect Data Cloud tenant context, collection availability, and repository error classification. | Restore Data Cloud connectivity or collection availability; fix missing tenant context at the caller; retry the read after the repository returns tenant-scoped records. | Run `./gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.services.integration.DataCloudPhasePacketTruthIntegrationTest"` after code/config changes. |
| Data Cloud read failure returns a blocker instead of empty evidence/governance. | Confirm `PhasePacketServiceImpl.queryPhaseEvidence`, `queryGovernanceRecords`, and run-status readers are returning explicit degraded/blocking records. | Keep the fail-closed record; fix the upstream Data Cloud issue rather than hiding the failure. | Phase cockpit should show dependency, reason, truth source, recovery action, and impacted features. |
| Tenant boundary error. | Confirm the request carries a non-default tenant and repositories reject missing/default tenant context. | Repair authentication/tenant propagation before retrying the Data Cloud query. | Run `./gradlew :products:yappc:infrastructure:datacloud:test --tests "com.ghatana.yappc.infrastructure.datacloud.repository.YappcDataCloudRepositoryTenantEnforcementTest"`. |

## AEP Execution Degraded

| Signal | What to check | Remediation | Verification |
| --- | --- | --- | --- |
| Run or Learn panel lacks execution evidence and marks AEP/runtime evidence degraded. | Search for AEP execution events by `runId`, `traceId`, and `correlationId`; confirm `DataCloudPlatformRunStatusWriter` is receiving execution events. | Replay the missing execution event if the event exists upstream; otherwise repair AEP event emission before rerunning the lifecycle action. | Run `./gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.services.phase.DataCloudPlatformRunStatusWriterTest" --tests "com.ghatana.yappc.services.phase.DataCloudPlatformRunStatusServiceTest"`. |
| Platform run status reader reports `DEGRADED_RUNTIME_TRUTH`. | Check Data Cloud query failure details and whether the run status collection is reachable. | Restore the `yappc_platform_runs` read path, then re-query the phase packet. | Run/Observe should show a concrete status with trace and evidence IDs instead of degraded runtime truth. |
| Failed run has no learning evidence. | Confirm `LearningEvidenceService` received the failed run outcome and persisted evidence with tenant/workspace/project provenance. | Reprocess the failed run outcome after AEP/Data Cloud paths are healthy. | Learn panel should list the learning evidence and recommendation tied to the failed run. |

## Kernel Truth Degraded

| Signal | What to check | Remediation | Verification |
| --- | --- | --- | --- |
| kernel-health route shows malformed or missing Kernel lifecycle truth. | Inspect `kernel_lifecycle_truth` records for schema shape, product ID, lifecycle status, gate state, artifact state, and deployment state. | Repair malformed records at the source; do not fall back to local filesystem truth outside dev/test. | Run `./gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.kernelvisibility.DataCloudKernelLifecycleTruthSourceTest"`. |
| Runtime is configured with local/mock/fake Kernel truth in a non-dev profile. | Check `YAPPC_KERNEL_LIFECYCLE_TRUTH_SOURCE` and deployment manifests. | Set the truth source to `data-cloud` and redeploy; local filesystem provider is only for deterministic dev/test fixtures. | Run `node products/yappc/scripts/check-production-truth-sources.mjs`. |
| ProductUnitIntent handoff fails Kernel contract validation. | Check provider, lifecycle profile, surface, product unit kind, and implementation status against imported Kernel public contract values. | Update the generated/imported Kernel contract resource or correct the YAPPC intent values; do not add local constants. | Run `./gradlew :products:yappc:core:scaffold:api:test --tests "com.ghatana.yappc.kernel.ProductUnitKernelContractRegistryTest" --tests "com.ghatana.yappc.kernel.ProductUnitIntentExporterTest"`. |

## Escalation

| Dependency | Escalate to | Include |
| --- | --- | --- |
| Data Cloud | Data Platform on-call | `correlationId`, tenant, collection, query operation, degraded reason, and repository error class. |
| AEP | AEP runtime owner | `runId`, `traceId`, execution event type, missing evidence IDs, and lifecycle phase. |
| Kernel | Kernel owner | Product unit ID, Kernel truth source, malformed record details, gate/artifact/deployment state, and generated ProductUnitIntent ref. |

## Related Checks

- [YAPPC Test Suites](../TEST_SUITES.md)
- [YAPPC-Only Check Guide](../DEVELOPER_GUIDE.md)
- [Kernel Visibility and Control Plane](../architecture/KERNEL_VISIBILITY_AND_CONTROL_PLANE.md)
- [Lifecycle, Kernel, Data Cloud, and AEP Architecture](../architecture/YAPPC_LIFECYCLE_KERNEL_DATA_CLOUD_AEP.md)
