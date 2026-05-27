# YAPPC Lifecycle, Kernel, Data Cloud, and AEP Architecture

This document captures the current implementation boundaries for YAPPC lifecycle execution, Kernel handoff, Data Cloud truth, AEP execution evidence, and frontend visibility.

## Lifecycle Control Flow

```mermaid
flowchart LR
  User["User"] --> Web["YAPPC Web App\nfrontend/web"]
  Web --> Api["YAPPC Backend APIs\ncore/yappc-services"]
  Api --> PhasePacket["PhasePacketServiceImpl\nreadiness, gates, actions"]
  Api --> Intent["Intent/Shape/Generate/Run/Learn/Evolve Services"]
  Intent --> DataCloud["Data Cloud Repositories\nintent, shape, runs, evidence, proposals"]
  PhasePacket --> DataCloud
  PhasePacket --> Governance["Policy/Governance Records"]
  PhasePacket --> KernelTruth["KernelLifecycleTruthSource"]
  KernelTruth --> DataCloudTruth["DataCloudKernelLifecycleTruthSource\nkernel_lifecycle_truth"]
  KernelTruth --> LocalTruth["LocalKernelManifestTruthSource\ndev/test only"]
  Api --> Metrics["BusinessMetrics and Structured Logs"]
```

## Kernel ProductUnitIntent Handoff

```mermaid
sequenceDiagram
  participant Web as YAPPC Web App
  participant Api as ProductUnitIntent API
  participant Exporter as ProductUnitIntentExporter
  participant Contract as Kernel Product Contract Registry
  participant Kernel as Kernel Public Contract/API
  participant DC as Data Cloud Evidence

  Web->>Api: Request ProductUnitIntent generation
  Api->>Exporter: Build typed ProductUnitIntent DTO
  Exporter->>Contract: Validate provider/profile/surface values
  Contract-->>Exporter: Canonical Kernel contract values
  Exporter-->>Api: YAML/JSON ProductUnitIntent
  Api->>DC: Persist evidence/audit references
  Api-->>Web: Valid Kernel-compatible intent
  Web->>Kernel: Handoff through CLI/API flow
```

## Production Truth Boundary

```mermaid
flowchart TB
  subgraph Production["Production Runtime"]
    Env["YAPPC_KERNEL_LIFECYCLE_TRUTH_SOURCE=data-cloud"] --> Guard["YappcEnvironmentConfig validation"]
    Guard --> DCKernel["DataCloudKernelLifecycleTruthSource"]
    DCKernel --> KLT["kernel_lifecycle_truth records"]
  end

  subgraph DevTest["Development/Test"]
    Fixtures[".kernel/out/products/** fixtures"] --> LocalProvider["Local filesystem provider"]
  end

  LocalProvider -. rejected in production .-> Guard
  DCKernel --> Health["KernelHealthSnapshotService"]
  Health --> Recommendations["KernelActionRecommendationService"]
```

## Runtime Evidence Loop

```mermaid
flowchart LR
  Kernel["Kernel execution events"] --> RunWriter["DataCloudPlatformRunStatusWriter"]
  AEP["AEP execution events"] --> RunWriter
  RunWriter --> Runs["yappc_platform_runs"]
  EvidenceSvc["Evidence adapters"] --> Evidence["phase evidence records"]
  GovernanceSvc["Governance adapters"] --> Gov["governance/policy records"]
  Runs --> Packet["PhasePacket"]
  Evidence --> Packet
  Gov --> Packet
  Packet --> Web["Phase cockpit and lifecycle pages"]
```

## Evidence Links

| Area | Code/Test Evidence |
| --- | --- |
| Kernel contract import/export | `ProductUnitKernelContractRegistryTest`, `ProductUnitIntentExporterTest` |
| Data Cloud Kernel truth | `DataCloudKernelLifecycleTruthSourceTest`, `KernelLifecycleEventIngestServiceTest` |
| Phase packet Data Cloud truth integration | `DataCloudPhasePacketTruthIntegrationTest` |
| Platform run write/read path | `DataCloudPlatformRunStatusWriterTest`, `DataCloudPlatformRunStatusServiceTest` |
| Frontend lifecycle visibility | `phase-cockpit-routes.test.tsx`, `KernelHealthDashboardPage.test.tsx` |
| Release evidence | `generate-yappc-scorecard-evidence.mjs`, `check-yappc-scorecard-evidence.mjs` |