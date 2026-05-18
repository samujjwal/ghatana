import type { StudioLifecycleSnapshot } from "../data/StudioLifecycleDataContext";

export type StudioRuntimeTruthState =
  | "loading"
  | "ready"
  | "empty"
  | "error"
  | "blocked"
  | "degraded"
  | "unavailable"
  | "redacted";

export interface StudioLifecycleRunSummary {
  readonly runId: string;
  readonly productUnitId: string;
  readonly phase: string;
  readonly status: string;
  readonly correlationId: string;
  readonly state: StudioRuntimeTruthState;
}

export interface StudioGateSummary {
  readonly gateId: string;
  readonly status: string;
  readonly reason: string;
  readonly nextAction: string;
  readonly state: StudioRuntimeTruthState;
}

export interface StudioArtifactSummary {
  readonly manifestRef: string;
  readonly artifactCount: number;
  readonly missingCount: number;
  readonly primaryDigest: string;
  readonly state: StudioRuntimeTruthState;
}

export interface StudioDeploymentSummary {
  readonly manifestRef: string;
  readonly target: string;
  readonly environment: string;
  readonly artifactDigest: string;
  readonly state: StudioRuntimeTruthState;
}

export interface StudioHealthSummary {
  readonly status: string;
  readonly checkedAt: string;
  readonly providerMode: string;
  readonly state: StudioRuntimeTruthState;
}

export interface StudioProviderBridgeHealthSummary {
  readonly status: string;
  readonly reason: string;
  readonly state: StudioRuntimeTruthState;
}

export interface StudioAgentEvidenceSummary {
  readonly evidenceId: string;
  readonly agentId: string;
  readonly policyDecision: string;
  readonly riskLevel: string;
  readonly evidenceRef: string;
  readonly redacted: boolean;
  readonly state: StudioRuntimeTruthState;
}

export interface StudioRuntimeTruthSummary {
  readonly lifecycleRun: StudioLifecycleRunSummary;
  readonly gates: readonly StudioGateSummary[];
  readonly artifact: StudioArtifactSummary;
  readonly deployment: StudioDeploymentSummary;
  readonly health: StudioHealthSummary;
  readonly providerBridgeHealth: StudioProviderBridgeHealthSummary;
  readonly agentEvidence: readonly StudioAgentEvidenceSummary[];
}

const NOT_AVAILABLE = "not reported";

export function createStudioRuntimeTruthSummary(
  snapshot: StudioLifecycleSnapshot,
): StudioRuntimeTruthSummary {
  const selectedRun = snapshot.selectedRun;
  const firstArtifact = snapshot.artifactManifest?.artifacts[0];
  const manifestRefs = selectedRun?.manifestRefs ?? {};
  const failedGates = (snapshot.gateResultManifest?.gates ?? [])
    .filter((gate) => gate.status !== "passed")
    .map((gate) => {
      const record = gate as {
        readonly gateId: string;
        readonly status: string;
        readonly failureReason?: string;
        readonly reason?: string;
        readonly nextAction?: string;
      };
      return {
        gateId: record.gateId,
        status: record.status,
        reason: record.failureReason ?? record.reason ?? "reason-not-reported",
        nextAction: record.nextAction ?? "inspect-failure",
        state: "blocked" as const,
      };
    });

  return {
    lifecycleRun: {
      runId: selectedRun?.runId ?? NOT_AVAILABLE,
      productUnitId:
        selectedRun?.productUnitId ?? snapshot.productUnit?.id ?? NOT_AVAILABLE,
      phase: selectedRun?.phase ?? NOT_AVAILABLE,
      status: selectedRun?.status ?? snapshot.status,
      correlationId: selectedRun?.correlationId ?? NOT_AVAILABLE,
      state: lifecycleRunState(snapshot),
    },
    gates:
      failedGates.length > 0
        ? failedGates
        : [
            {
              gateId: "gate-summary",
              status: snapshot.manifestLoadState.gateResultManifest.status,
              reason:
                snapshot.manifestLoadState.gateResultManifest.message ??
                "no-failed-gates",
              nextAction: "none",
              state:
                snapshot.manifestLoadState.gateResultManifest.status ===
                "loaded"
                  ? "ready"
                  : "empty",
            },
          ],
    artifact: {
      manifestRef: manifestRefs["artifact-manifest"] ?? NOT_AVAILABLE,
      artifactCount: snapshot.artifactManifest?.artifacts.length ?? 0,
      missingCount:
        snapshot.artifactManifest?.artifacts.filter(
          (artifact) => !artifact.found,
        ).length ?? 0,
      primaryDigest:
        firstArtifact === undefined
          ? NOT_AVAILABLE
          : `${firstArtifact.fingerprint.algorithm}:${firstArtifact.fingerprint.hash}`,
      state: manifestState(snapshot.manifestLoadState.artifactManifest.status),
    },
    deployment: {
      manifestRef: manifestRefs["deployment-manifest"] ?? NOT_AVAILABLE,
      target:
        snapshot.deploymentManifest?.target ??
        snapshot.deploymentManifest?.environment ??
        NOT_AVAILABLE,
      environment: snapshot.deploymentManifest?.environment ?? NOT_AVAILABLE,
      artifactDigest:
        firstArtifact === undefined
          ? NOT_AVAILABLE
          : `${firstArtifact.fingerprint.algorithm}:${firstArtifact.fingerprint.hash}`,
      state: manifestState(
        snapshot.manifestLoadState.deploymentManifest.status,
      ),
    },
    health: {
      status: snapshot.verifyHealthReport?.status ?? NOT_AVAILABLE,
      checkedAt: snapshot.verifyHealthReport?.checkedAt ?? NOT_AVAILABLE,
      providerMode: snapshot.artifactManifest?.providerMode ?? NOT_AVAILABLE,
      state: healthState(snapshot),
    },
    providerBridgeHealth: providerBridgeHealth(snapshot),
    agentEvidence: agentEvidence(snapshot),
  };
}

function lifecycleRunState(
  snapshot: StudioLifecycleSnapshot,
): StudioRuntimeTruthState {
  if (snapshot.status === "loading") {
    return "loading";
  }
  if (snapshot.errorMessage !== undefined) {
    return "error";
  }
  if (snapshot.selectedRun === undefined) {
    return "empty";
  }
  if (
    snapshot.selectedRun.failureReasonCode !== undefined ||
    snapshot.selectedRun.status === "blocked"
  ) {
    return "blocked";
  }
  if (
    snapshot.selectedRun.status === "degraded" ||
    snapshot.status === "degraded"
  ) {
    return "degraded";
  }
  return "ready";
}

function manifestState(status: string): StudioRuntimeTruthState {
  if (status === "loaded") {
    return "ready";
  }
  if (status === "missing") {
    return "empty";
  }
  if (status === "unavailable") {
    return "unavailable";
  }
  if (status === "unauthorized") {
    return "redacted";
  }
  return "error";
}

function healthState(
  snapshot: StudioLifecycleSnapshot,
): StudioRuntimeTruthState {
  if (snapshot.verifyHealthReport?.status === "degraded") {
    return "degraded";
  }
  if (
    snapshot.verifyHealthReport?.status === "failed" ||
    snapshot.verifyHealthReport?.status === "blocked"
  ) {
    return "blocked";
  }
  return manifestState(snapshot.manifestLoadState.verifyHealthReport.status);
}

function providerBridgeHealth(
  snapshot: StudioLifecycleSnapshot,
): StudioProviderBridgeHealthSummary {
  const states = Object.values(snapshot.manifestLoadState).map(
    (state) => state.status,
  );
  if (states.includes("unavailable")) {
    return {
      status: "unavailable",
      reason: "provider-unavailable",
      state: "unavailable",
    };
  }
  if (states.includes("missing")) {
    return {
      status: "degraded",
      reason: "provider-evidence-missing",
      state: "degraded",
    };
  }
  if (states.includes("unauthorized")) {
    return {
      status: "redacted",
      reason: "private-evidence",
      state: "redacted",
    };
  }
  if (states.includes("corrupt")) {
    return {
      status: "degraded",
      reason: "manifest-corrupt",
      state: "degraded",
    };
  }
  return {
    status: "healthy",
    reason: "all-public-manifests-loaded",
    state: "ready",
  };
}

function agentEvidence(
  snapshot: StudioLifecycleSnapshot,
): readonly StudioAgentEvidenceSummary[] {
  const eventsRef = snapshot.selectedRun?.eventsRef;
  const approvalEvidence = snapshot.pendingApprovals.flatMap(
    (approval) => approval.evidenceRefs ?? [],
  );
  const refs = [
    ...(eventsRef === undefined ? [] : [eventsRef]),
    ...approvalEvidence,
  ];

  if (refs.length === 0) {
    return [
      {
        evidenceId: "agent-evidence-empty",
        agentId: "not reported",
        policyDecision: "not reported",
        riskLevel: "not reported",
        evidenceRef: NOT_AVAILABLE,
        redacted: false,
        state: "empty",
      },
    ];
  }

  return refs.map((ref, index) => ({
    evidenceId: `agent-evidence-${index + 1}`,
    agentId: "studio-runtime-agent",
    policyDecision: "requires-approval",
    riskLevel: "high",
    evidenceRef:
      ref.includes("private") || ref.includes("restricted") ? "redacted" : ref,
    redacted: ref.includes("private") || ref.includes("restricted"),
    state:
      ref.includes("private") || ref.includes("restricted")
        ? "redacted"
        : "ready",
  }));
}
