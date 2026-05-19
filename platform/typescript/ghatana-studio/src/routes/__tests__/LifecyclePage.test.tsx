import { describe, expect, it, vi, beforeEach } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { StudioLifecycleDataContextValue } from "../../data/StudioLifecycleDataContext";
import LifecyclePage from "../LifecyclePage";

const useStudioLifecycleDataMock =
  vi.fn<() => StudioLifecycleDataContextValue>();
const useStudioTranslationMock = vi.fn<() => (key: string) => string>();

vi.mock("../../data/StudioLifecycleDataContext", () => ({
  useStudioLifecycleData: () => useStudioLifecycleDataMock(),
}));

vi.mock("../../i18n/studioTranslations", () => ({
  useStudioTranslation: () => useStudioTranslationMock(),
}));

function createContextValue(
  overrides: Partial<StudioLifecycleDataContextValue> = {},
): StudioLifecycleDataContextValue {
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
          metadata: { environments: ["local"] },
          surfaces: [
            { id: "web", type: "web", implementationStatus: "implemented" },
          ],
        },
      ],
      productUnit: {
        schemaVersion: "1.0.0",
        id: "digital-marketing",
        name: "Digital Marketing",
        kind: "business-product",
        registryProviderRef: { providerId: "registry" },
        sourceProviderRef: { providerId: "source" },
        lifecycleStatus: "enabled",
        metadata: { environments: ["local"], lifecycleExecutionAllowed: true },
        surfaces: [
          { id: "web", type: "web", implementationStatus: "implemented" },
        ],
      },
      lifecycleRuns: [
        {
          runId: "run-1",
          correlationId: "corr-1",
          productUnitId: "digital-marketing",
          phase: "build",
          status: "healthy",
        },
      ],
      selectedRun: {
        runId: "run-1",
        correlationId: "corr-1",
        productUnitId: "digital-marketing",
        phase: "build",
        status: "healthy",
      },
      pendingApprovals: [],
      manifestLoadState: {
        gateResultManifest: { status: "missing" },
        artifactManifest: { status: "missing" },
        deploymentManifest: { status: "missing" },
        verifyHealthReport: { status: "missing" },
      },
    },
    selectedProductUnitId: "digital-marketing",
    selectedRunId: "run-1",
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
    submitApprovalDecision: vi.fn().mockResolvedValue(undefined),
    refresh: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
}

describe("LifecyclePage approval queue", () => {
  beforeEach(() => {
    useStudioTranslationMock.mockReturnValue((key: string) => key);
  });

  it("renders no pending approvals message", () => {
    useStudioLifecycleDataMock.mockReturnValue(createContextValue());

    render(<LifecyclePage />);

    expect(
      screen.getByText("studio.route.lifecycle.noPendingApprovals"),
    ).toBeInTheDocument();
  });

  it("submits approval decisions from the queue with authenticated user ID", async () => {
    const submitApprovalDecision = vi.fn().mockResolvedValue(undefined);
    const refresh = vi.fn().mockResolvedValue(undefined);
    useStudioLifecycleDataMock.mockReturnValue(
      createContextValue({
        submitApprovalDecision,
        refresh,
        authenticatedUserId: "user-123",
        snapshot: {
          ...createContextValue().snapshot,
          runtimeMode: "configured",
          pendingApprovals: [
            {
              approvalId: "approval-1",
              productUnitId: "digital-marketing",
              runId: "run-1",
              requestedBy: "release-manager",
              reason: "Deploy",
              requiredApprovers: ["alice"],
              expiresAt: "2026-05-16T00:00:00.000Z",
            },
          ],
        },
      }),
    );

    render(<LifecyclePage />);

    fireEvent.click(
      screen.getByRole("button", {
        name: "studio.lifecycle.action.submitApproval",
      }),
    );

    await waitFor(() => {
      expect(submitApprovalDecision).toHaveBeenCalledWith(
        "approval-1",
        expect.objectContaining({
          approvalId: "approval-1",
          approved: true,
          approvedBy: "user-123",
        }),
      );
    });

    fireEvent.click(
      screen.getByRole("button", {
        name: "studio.lifecycle.action.rejectApproval",
      }),
    );

    await waitFor(() => {
      expect(submitApprovalDecision).toHaveBeenCalledWith(
        "approval-1",
        expect.objectContaining({
          approvalId: "approval-1",
          approved: false,
          approvedBy: "user-123",
        }),
      );
      expect(refresh).toHaveBeenCalled();
    });
  });

  it("does not submit approval when authenticatedUserId is missing", async () => {
    const submitApprovalDecision = vi.fn().mockResolvedValue(undefined);
    const refresh = vi.fn().mockResolvedValue(undefined);
    useStudioLifecycleDataMock.mockReturnValue(
      createContextValue({
        submitApprovalDecision,
        refresh,
        authenticatedUserId: undefined,
        snapshot: {
          ...createContextValue().snapshot,
          runtimeMode: "configured",
          pendingApprovals: [
            {
              approvalId: "approval-1",
              productUnitId: "digital-marketing",
              runId: "run-1",
              requestedBy: "release-manager",
              reason: "Deploy",
              requiredApprovers: ["alice"],
              expiresAt: "2026-05-16T00:00:00.000Z",
            },
          ],
        },
      }),
    );

    render(<LifecyclePage />);

    const approveButton = screen.getByRole("button", {
      name: "studio.lifecycle.action.submitApproval",
    });
    const rejectButton = screen.getByRole("button", {
      name: "studio.lifecycle.action.rejectApproval",
    });

    // Buttons should be disabled when authenticatedUserId is missing
    expect(approveButton).toBeDisabled();
    expect(rejectButton).toBeDisabled();

    fireEvent.click(approveButton);
    fireEvent.click(rejectButton);

    await waitFor(() => {
      expect(submitApprovalDecision).not.toHaveBeenCalled();
    });
  });

  it("renders typed manifest evidence summaries", () => {
    useStudioLifecycleDataMock.mockReturnValue(
      createContextValue({
        snapshot: {
          ...createContextValue().snapshot,
          runtimeMode: "configured",
          gateResultManifest: {
            schemaVersion: "1.0.0",
            productUnitId: "digital-marketing",
            runId: "run-1",
            gates: [
              {
                gateId: "security-scan",
                status: "passed",
                required: true,
              },
            ],
          },
          artifactManifest: {
            schemaVersion: "1.0.0",
            productId: "digital-marketing",
            phase: "build",
            timestamp: "2026-05-16T00:00:00.000Z",
            artifacts: [
              {
                id: "web-dist",
                path: "dist",
                metadata: {
                  type: "static-web-bundle",
                  packaging: "static-files",
                  version: "1.0.0",
                  buildNumber: "1",
                  gitCommit: "abc1234",
                  gitBranch: "main",
                  timestamp: "2026-05-16T00:00:00.000Z",
                  sizeBytes: 1024,
                },
                fingerprint: {
                  algorithm: "sha256",
                  hash: "a".repeat(64),
                },
                expected: true,
                found: true,
              },
            ],
          },
          deploymentManifest: {
            schemaVersion: "1.0.0",
            productId: "digital-marketing",
            version: "1.0.0",
            environment: "local",
            environmentSafety: "local",
            deploymentId: "deploy-1",
            deployedAt: "2026-05-16T00:00:00.000Z",
            rollbackPlan: {
              strategy: "previous-artifact",
              targetVersion: "0.9.0",
              reason: "Rollback",
              steps: ["restore previous artifact"],
            },
            surfaces: [],
            target: "compose-local",
          },
          verifyHealthReport: {
            schemaVersion: "1.0.0",
            productUnitId: "digital-marketing",
            runId: "run-1",
            status: "healthy",
            checkedAt: "2026-05-16T00:00:00.000Z",
          },
          manifestLoadState: {
            gateResultManifest: { status: "loaded" },
            artifactManifest: { status: "loaded" },
            deploymentManifest: { status: "loaded" },
            verifyHealthReport: { status: "loaded" },
          },
        },
      }),
    );

    render(<LifecyclePage />);

    expect(screen.getByText("security-scan")).toBeInTheDocument();
    expect(screen.getByText("web-dist")).toBeInTheDocument();
    expect(
      screen.getByText("studio.route.lifecycle.environmentLabel: local"),
    ).toBeInTheDocument();
    expect(
      screen.getAllByText("studio.route.lifecycle.runStatus.healthy").length,
    ).toBeGreaterThan(0);
    expect(
      screen.getAllByText("studio.route.lifecycle.runIdLabel").length,
    ).toBeGreaterThan(0);
    expect(
      screen.getByText("studio.route.lifecycle.failureReasonLabel"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "studio.route.lifecycle.outcome.completedWithoutFailureReason",
      ),
    ).toBeInTheDocument();
  });

  it("renders runtime truth summaries with failure reason next action and redacted private evidence", () => {
    useStudioLifecycleDataMock.mockReturnValue(
      createContextValue({
        snapshot: {
          ...createContextValue().snapshot,
          runtimeMode: "configured",
          selectedRun: {
            runId: "run-1",
            correlationId: "corr-1",
            productUnitId: "digital-marketing",
            phase: "build",
            status: "blocked",
            failureReasonCode: "gate-failed",
            eventsRef: "private://agent-evidence/corr-1",
            manifestRefs: {
              "artifact-manifest": "artifact-manifest://run-1",
              "deployment-manifest": "deployment-manifest://run-1",
            },
          },
          gateResultManifest: {
            schemaVersion: "1.0.0",
            productUnitId: "digital-marketing",
            runId: "run-1",
            gates: [
              {
                gateId: "release-policy",
                status: "failed",
                required: true,
                reason: "security-review-required",
                nextAction: "request-approval",
              },
            ],
          },
          artifactManifest: {
            schemaVersion: "1.0.0",
            productId: "digital-marketing",
            providerMode: "platform",
            phase: "build",
            timestamp: "2026-05-16T00:00:00.000Z",
            artifacts: [
              {
                id: "web-dist",
                path: "dist",
                metadata: {
                  type: "static-web-bundle",
                  packaging: "static-files",
                  version: "1.0.0",
                  buildNumber: "1",
                  gitCommit: "abc123",
                  gitBranch: "main",
                  timestamp: "2026-05-16T00:00:00.000Z",
                  sizeBytes: 1024,
                },
                fingerprint: {
                  algorithm: "sha256",
                  hash: "abc123",
                },
                expected: true,
                found: true,
              },
            ],
          },
          deploymentManifest: {
            schemaVersion: "1.0.0",
            productId: "digital-marketing",
            version: "1.0.0",
            environment: "local",
            environmentSafety: "local",
            deploymentId: "deploy-1",
            deployedAt: "2026-05-16T00:00:00.000Z",
            rollbackPlan: {
              strategy: "previous-artifact",
              targetVersion: "0.9.0",
              reason: "Rollback",
              steps: ["restore previous artifact"],
            },
            surfaces: [],
            target: "compose-local",
          },
          verifyHealthReport: {
            schemaVersion: "1.0.0",
            productUnitId: "digital-marketing",
            runId: "run-1",
            status: "degraded",
            checkedAt: "2026-05-16T00:00:00.000Z",
          },
          manifestLoadState: {
            gateResultManifest: { status: "loaded" },
            artifactManifest: { status: "loaded" },
            deploymentManifest: { status: "loaded" },
            verifyHealthReport: { status: "loaded" },
          },
        },
      }),
    );

    render(<LifecyclePage />);

    expect(screen.getByLabelText("runtime-truth-summary")).toBeInTheDocument();
    expect(screen.getByLabelText("gate-result-view")).toHaveTextContent(
      "release-policy",
    );
    expect(screen.getByLabelText("gate-result-view")).toHaveTextContent(
      "security-review-required",
    );
    expect(screen.getByLabelText("gate-result-view")).toHaveTextContent(
      "request-approval",
    );
    expect(screen.getByLabelText("artifact-manifest-view")).toHaveTextContent(
      "artifact-manifest://run-1",
    );
    expect(screen.getByLabelText("deployment-manifest-view")).toHaveTextContent(
      "sha256:abc123",
    );
    expect(screen.getByLabelText("health-snapshot-panel")).toHaveTextContent(
      "platform",
    );
    expect(
      screen.getByLabelText("agent-action-evidence-panel"),
    ).toHaveTextContent("redacted");
  });

  it("keeps runtime truth cards keyboard focusable", () => {
    useStudioLifecycleDataMock.mockReturnValue(createContextValue());

    render(<LifecyclePage />);

    const lifecycleRunCard = screen.getByLabelText("lifecycle-run-detail");
    lifecycleRunCard.focus();

    expect(lifecycleRunCard).toHaveFocus();
  });

  it("shows blocked reason codes for unsupported phase/environment and platform provider mode", () => {
    useStudioLifecycleDataMock.mockReturnValue(
      createContextValue({
        selectedEnvironment: "staging",
        selectedProviderMode: "platform",
        snapshot: {
          ...createContextValue().snapshot,
          runtimeMode: "configured",
          status: "degraded",
          productUnit: {
            ...createContextValue().snapshot.productUnit!,
            lifecycleStatus: "planned",
            metadata: {
              environments: ["local"],
              phases: ["build"],
              lifecycleExecutionAllowed: false,
              lifecycleReadiness: {
                reasonCodes: ["requires-product-gates"],
                requiredGates: ["security-gate"],
                nextRequiredWork: ["enable-gates-before-execution"],
              },
            },
          },
        },
      }),
    );

    render(<LifecyclePage />);

    fireEvent.click(screen.getByRole("button", { name: "Deploy" }));

    expect(
      screen.getByLabelText("lifecycle-blocked-reasons"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("studio.route.lifecycle.reasonCode.phase-not-supported"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "studio.route.lifecycle.reasonCode.environment-not-supported",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "studio.route.lifecycle.reasonCode.provider-mode-unavailable",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "studio.route.lifecycle.reasonCode.lifecycle-execution-not-allowed",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText("lifecycle-readiness-state"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "studio.route.lifecycle.reasonCode.requires-product-gates",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText("studio.route.lifecycle.reasonCode.security-gate"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "studio.route.lifecycle.reasonCode.enable-gates-before-execution",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", {
        name: "studio.route.lifecycle.executePhaseButton",
      }),
    ).toBeDisabled();
  });

  it("treats PHR as an opening lifecycle pilot", () => {
    useStudioLifecycleDataMock.mockReturnValue(
      createContextValue({
        snapshot: {
          ...createContextValue().snapshot,
          runtimeMode: "configured",
          productUnit: {
            ...createContextValue().snapshot.productUnit!,
            id: "phr",
            name: "PHR",
            lifecycleStatus: "enabled",
            metadata: {
              environments: ["local"],
              lifecycleExecutionAllowed: true,
            },
          },
        },
      }),
    );

    render(<LifecyclePage />);

    expect(
      screen.getByLabelText("lifecycle-pilot-readiness"),
    ).toBeInTheDocument();
    expect(screen.getByText("Current product unit: phr")).toBeInTheDocument();
    expect(
      screen.queryByText(/Non-pilot products remain fail-closed/),
    ).not.toBeInTheDocument();
  });

  it("shows explicit non-pilot messaging for products outside opening lifecycle pilots", () => {
    useStudioLifecycleDataMock.mockReturnValue(
      createContextValue({
        snapshot: {
          ...createContextValue().snapshot,
          runtimeMode: "configured",
          productUnit: {
            ...createContextValue().snapshot.productUnit!,
            id: "finance",
            name: "Finance",
            lifecycleStatus: "planned",
            metadata: {
              environments: ["local"],
              lifecycleExecutionAllowed: false,
            },
          },
        },
      }),
    );

    render(<LifecyclePage />);

    expect(screen.getByText("Current product unit: finance")).toBeInTheDocument();
    expect(
      screen.getByText(
        /Only Digital Marketing and PHR are lifecycle-enabled opening pilots/,
      ),
    ).toBeInTheDocument();
  });

  it("renders manifest remediation guidance for missing manifest states", () => {
    useStudioLifecycleDataMock.mockReturnValue(createContextValue());

    render(<LifecyclePage />);

    expect(
      screen.getAllByLabelText("manifest-status-reason").length,
    ).toBeGreaterThan(0);
    expect(
      screen.getAllByText("studio.route.lifecycle.manifest.remediation.missing")
        .length,
    ).toBeGreaterThan(0);
  });

  it("surfaces execute-phase errors and skips refresh when execution fails", async () => {
    const executePhase = vi
      .fn()
      .mockRejectedValue(new Error("kernel execution failed"));
    const refresh = vi.fn().mockResolvedValue(undefined);

    useStudioLifecycleDataMock.mockReturnValue(
      createContextValue({
        executePhase,
        refresh,
      }),
    );

    render(<LifecyclePage />);

    fireEvent.click(
      screen.getByRole("button", {
        name: "studio.route.lifecycle.executePhaseButton",
      }),
    );

    await waitFor(() => {
      expect(executePhase).toHaveBeenCalledWith("build", {
        dryRun: false,
        environment: "local",
      });
    });

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(
        "kernel execution failed",
      );
    });

    expect(refresh).not.toHaveBeenCalled();
  });
});
