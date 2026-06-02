/**
 * RuntimeCapabilityRouteGate Component Tests
 *
 * P5-05: Tests for route gate with new lifecycle values and audience-aware preview.
 *
 * @doc.type test
 * @doc.purpose Unit tests for RuntimeCapabilityRouteGate component
 * @doc.layer frontend
 */

import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { RuntimeCapabilityRouteGate } from "../../../components/security/RuntimeCapabilityRouteGate";

const { mockUseSurfaceRegistry } = vi.hoisted(() => ({
  mockUseSurfaceRegistry: vi.fn(),
}));

vi.mock("../../../api/surfaces.service", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("../../../api/surfaces.service")>();
  return {
    ...actual,
    useSurfaceRegistry: mockUseSurfaceRegistry,
  };
});

function makeSurface(status: string, extras: Record<string, unknown> = {}) {
  return {
    key: "governance.audit",
    path: "/alerts",
    label: "Alerts",
    status,
    summary: status,
    ownerPlane: "governance",
    requiredDependencies: [],
    dependencyProbes: [],
    tenantScope: "tenant",
    runtimeProfile: "production",
    limitations: "",
    actionsAllowed: [],
    rawValue: {},
    ...extras,
  };
}

describe("RuntimeCapabilityRouteGate", () => {
  it("allows LIVE", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      isLoading: false,
      data: { generatedAt: "now", requestId: "r", tenantId: "t", surfaces: [makeSurface("LIVE")] },
    });

    render(
      <RuntimeCapabilityRouteGate surfaceId="governance.audit">
        <div>Allowed</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(screen.getByText("Allowed")).toBeInTheDocument();
  });

  it("allows DEGRADED with banner", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      isLoading: false,
      data: { generatedAt: "now", requestId: "r", tenantId: "t", surfaces: [makeSurface("DEGRADED", { limitations: "Read-only" })] },
    });

    render(
      <RuntimeCapabilityRouteGate surfaceId="governance.audit">
        <div>Allowed</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByText("Allowed")).toBeInTheDocument();
  });

  it("denies PREVIEW by default", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      isLoading: false,
      data: { generatedAt: "now", requestId: "r", tenantId: "t", surfaces: [makeSurface("PREVIEW", { audience: "operator" })] },
    });

    render(
      <RuntimeCapabilityRouteGate
        surfaceId="governance.audit"
        fallback={<div>Denied</div>}
      >
        <div>Allowed</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(screen.getByText("Denied")).toBeInTheDocument();
  });

  it("allows PREVIEW for matching audience", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      isLoading: false,
      data: { generatedAt: "now", requestId: "r", tenantId: "t", surfaces: [makeSurface("PREVIEW", { audience: "operator" })] },
    });

    render(
      <RuntimeCapabilityRouteGate
        surfaceId="governance.audit"
        allowPreview
        allowPreviewFor="operator"
      >
        <div>Allowed</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(screen.getByText("Allowed")).toBeInTheDocument();
  });

  it("denies PREVIEW for wrong audience", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      isLoading: false,
      data: { generatedAt: "now", requestId: "r", tenantId: "t", surfaces: [makeSurface("PREVIEW", { audience: "internal" })] },
    });

    render(
      <RuntimeCapabilityRouteGate
        surfaceId="governance.audit"
        allowPreview
        allowPreviewFor="operator"
        fallback={<div>Denied</div>}
      >
        <div>Allowed</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(screen.getByText("Denied")).toBeInTheDocument();
  });

  it("denies target-only", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      isLoading: false,
      data: {
        generatedAt: "now",
        requestId: "r",
        tenantId: "t",
        surfaces: [makeSurface("LIVE", { targetOnly: true, readinessClass: "target-only" })],
      },
    });

    render(
      <RuntimeCapabilityRouteGate
        surfaceId="governance.audit"
        fallback={<div>Denied</div>}
      >
        <div>Allowed</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(screen.getByText("Denied")).toBeInTheDocument();
  });

  it("denies when runtime truth is missing", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      isLoading: false,
      data: { generatedAt: "now", requestId: "r", tenantId: "t", surfaces: [] },
    });

    render(
      <RuntimeCapabilityRouteGate
        surfaceId="governance.audit"
        fallback={<div>Denied</div>}
      >
        <div>Allowed</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(screen.getByText("Denied")).toBeInTheDocument();
  });
});
