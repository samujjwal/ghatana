# YAPPC Data Cloud Collections

This registry documents the canonical collection names that YAPPC code and docs must use for Kernel truth, platform run truth, and agent execution state. The machine-readable registry lives at `products/yappc/config/datacloud-collections.json`.

| Collection | Owner | Purpose | Primary implementation |
| --- | --- | --- | --- |
| `kernel_lifecycle_truth` | Kernel/Data Cloud boundary | Typed Kernel lifecycle truth records consumed by YAPPC health, ingest, and recommendation services. | `DataCloudKernelLifecycleTruthSource` |
| `yappc_platform_runs` | YAPPC/AEP runtime evidence boundary | Platform run status records written from AEP/Kernel execution events and read by phase packets. | `DataCloudPlatformRunStatusWriter`, `DataCloudPlatformRunStatusService` |
| `agent-executions` | YAPPC agent runtime | Tenant-scoped agent execution state records persisted by agent execution operators. | `AgentStateRepository` |

## Validation

Run the registry check from the repository root:

```bash
node products/yappc/scripts/check-datacloud-collection-names.mjs
```

The check validates registry schema, required collection names, source file existence, source file usage of the canonical name, and configured forbidden aliases.

## Naming Rules

- Use `kernel_lifecycle_truth` for Kernel lifecycle truth records.
- Use `yappc_platform_runs` for platform run status records.
- Use `agent-executions` for agent execution state records.
- Do not introduce local aliases such as `kernel-lifecycle-truth`, `yappc-platform-runs`, or `agent_executions` for these collections.
- Metric names may still use metric naming conventions, such as `agent_executions_total`; this registry governs Data Cloud collection names only.
