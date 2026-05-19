import { describe, expect, it, vi, beforeEach } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import type { StudioLifecycleDataContextValue } from "../../data/StudioLifecycleDataContext";
import OpeningPilotsPage from "../OpeningPilotsPage";

const useStudioLifecycleDataMock =
  vi.fn<() => StudioLifecycleDataContextValue>();
const useStudioTranslationMock = vi.fn<() => (key: string) => string>();

vi.mock("../../data/StudioLifecycleDataContext", () => ({
  useStudioLifecycleData: () => useStudioLifecycleDataMock(),
}));

vi.mock("../../i18n/studioTranslations", () => ({
  useStudioTranslation: () => useStudioTranslationMock(),
}));

function contextValue(): StudioLifecycleDataContextValue {
  return {
    snapshot: {
      status: "ready",
      runtimeMode: "configured",
      availableProductUnits: [
        {
          schemaVersion: "1.0.0",
          id: "digital-marketing",
          name: "Digital Marketing",
          kind: "business-product",
          registryProviderRef: { providerId: "registry" },
          sourceProviderRef: { providerId: "source" },
          lifecycleStatus: "enabled",
          metadata: { lifecycleExecutionAllowed: true },
          surfaces: [],
        },
        {
          schemaVersion: "1.0.0",
          id: "phr",
          name: "PHR",
          kind: "business-product",
          registryProviderRef: { providerId: "registry" },
          sourceProviderRef: { providerId: "source" },
          lifecycleStatus: "enabled",
          metadata: { lifecycleExecutionAllowed: true },
          surfaces: [],
        },
      ],
      productUnit: {
        schemaVersion: "1.0.0",
        id: "phr",
        name: "PHR",
        kind: "business-product",
        registryProviderRef: { providerId: "registry" },
        sourceProviderRef: { providerId: "source" },
        lifecycleStatus: "enabled",
        metadata: { lifecycleExecutionAllowed: true },
        surfaces: [],
      },
      lifecycleRuns: [],
      selectedRun: {
        runId: "phr-run-1",
        correlationId: "corr-phr-1",
        productUnitId: "phr",
        phase: "validate",
        status: "blocked",
        manifestRefs: {
          "artifact-manifest": "artifact://phr-run-1",
          "deployment-manifest": "deployment://phr-run-1",
        },
      },
      pendingApprovals: [
        {
          approvalId: "approval-phr-1",
          productUnitId: "phr",
          runId: "phr-run-1",
          requestedBy: "release-manager",
          reason: "FHIR validation",
          requiredApprovers: ["clinical-owner"],
          expiresAt: "2026-05-19T00:00:00.000Z",
        },
      ],
      manifestLoadState: {
        gateResultManifest: { status: "loaded" },
        artifactManifest: { status: "loaded" },
        deploymentManifest: { status: "loaded" },
        verifyHealthReport: { status: "loaded" },
      },
      gateResultManifest: {
        schemaVersion: "1.0.0",
        productUnitId: "phr",
        runId: "phr-run-1",
        gates: [
          {
            gateId: "fhir-contract-validation",
            status: "failed",
            required: true,
            reason: "fhir-evidence-missing",
            nextAction: "attach-fhir-evidence",
          },
        ],
      },
      artifactManifest: {
        schemaVersion: "1.0.0",
        productId: "phr",
        phase: "build",
        timestamp: "2026-05-18T00:00:00.000Z",
        artifacts: [],
      },
      deploymentManifest: {
        schemaVersion: "1.0.0",
        productId: "phr",
        version: "1.0.0",
        environment: "local",
        environmentSafety: "local",
        deploymentId: "deploy-phr-1",
        deployedAt: "2026-05-18T00:00:00.000Z",
        rollbackPlan: {
          strategy: "previous-artifact",
          targetVersion: "0.9.0",
          reason: "rollback",
          steps: ["restore previous artifact"],
        },
        surfaces: [],
      },
      verifyHealthReport: {
        schemaVersion: "1.0.0",
        productUnitId: "phr",
        runId: "phr-run-1",
        status: "blocked",
        checkedAt: "2026-05-18T00:00:00.000Z",
      },
    },
    selectedProductUnitId: "phr",
    selectedRunId: "phr-run-1",
    selectedEnvironment: "local",
    selectedProviderMode: "bootstrap",
    intentOperation: { status: "idle" },
    authenticatedUserId: "user-123",
    selectProductUnit: vi.fn(),
    selectRun: vi.fn(),
    setEnvironment: vi.fn(),
    setProviderMode: vi.fn(),
    createPlan: vi.fn(),
    executePhase: vi.fn(),
    requestApproval: vi.fn(),
    submitApprovalDecision: vi.fn(),
    refresh: vi.fn(),
  };
}

describe("OpeningPilotsPage", () => {
  beforeEach(() => {
    useStudioTranslationMock.mockReturnValue((key: string) => key);
    useStudioLifecycleDataMock.mockReturnValue(contextValue());
  });

  it("shows Digital Marketing and PHR opening pilot truth", () => {
    render(<OpeningPilotsPage />);

    expect(screen.getByLabelText("Digital Marketing opening pilot")).toBeInTheDocument();
    expect(screen.getByLabelText("PHR opening pilot")).toBeInTheDocument();
    expect(screen.getByText("fhir-contract-validation")).toBeInTheDocument();
    expect(screen.getByText("artifact://phr-run-1")).toBeInTheDocument();
    expect(screen.getByText("deployment://phr-run-1")).toBeInTheDocument();
  });

  it("filters to requires-approval pilots", () => {
    render(<OpeningPilotsPage />);

    fireEvent.change(screen.getByLabelText("studio.route.openingPilots.filterLabel"), {
      target: { value: "requires-approval" },
    });

    expect(screen.queryByLabelText("Digital Marketing opening pilot")).not.toBeInTheDocument();
    expect(screen.getByLabelText("PHR opening pilot")).toBeInTheDocument();
    expect(screen.getByText("Resolve pending approval before promotion.")).toBeInTheDocument();
  });
});
