import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type {
  SurfaceRegistrySnapshot,
  SurfaceSignal,
} from "../../../api/surfaces.service";

const { mockUseSurfaceRegistry } = vi.hoisted(() => ({
  mockUseSurfaceRegistry:
    vi.fn<
      () => { data: SurfaceRegistrySnapshot | undefined; isLoading: boolean }
    >(),
}));

vi.mock("../../../api/surfaces.service", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("../../../api/surfaces.service")>();
  return {
    ...actual,
    useSurfaceRegistry: mockUseSurfaceRegistry,
  };
});

import { RuntimeCapabilityRouteGate } from "../RuntimeCapabilityRouteGate";

function makeSignal(status: SurfaceSignal["status"]): SurfaceSignal {
  return {
    key: "data.storageProfiles",
    label: "Data Fabric",
    status,
    summary: status,
    ownerPlane: "data",
    requiredDependencies: ["storage tier registry"],
    dependencyProbes: [],
    tenantScope: "tenant",
    runtimeProfile: "test",
    limitations: status === "DEGRADED" ? "Read-only topology" : "",
    actionsAllowed: ["read"],
    rawValue: { state: status },
  };
}

function makeSnapshot(
  status: SurfaceSignal["status"],
): SurfaceRegistrySnapshot {
  return {
    surfaces: [makeSignal(status)],
    generatedAt: "2026-01-01",
    requestId: "r",
    tenantId: "t",
  };
}

describe("RuntimeCapabilityRouteGate", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders children when the surface is LIVE", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot("LIVE"),
      isLoading: false,
    });

    render(
      <RuntimeCapabilityRouteGate aliases={["data-fabric"]}>
        <div>Fabric Page</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(screen.getByText("Fabric Page")).toBeInTheDocument();
  });

  it("renders a degraded banner and children when the surface is DEGRADED", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot("DEGRADED"),
      isLoading: false,
    });

    render(
      <RuntimeCapabilityRouteGate aliases={["data-fabric"]}>
        <div>Fabric Page</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(screen.getByRole("alert")).toHaveTextContent("Degraded");
    expect(screen.getByText("Fabric Page")).toBeInTheDocument();
  });

  it("renders a preview badge and children when the surface is PREVIEW", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot("PREVIEW"),
      isLoading: false,
    });

    render(
      <RuntimeCapabilityRouteGate aliases={["data-fabric"]} allowPreview>
        <div>Fabric Page</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(screen.getByRole("status")).toHaveTextContent("Preview");
    expect(screen.getByText("Fabric Page")).toBeInTheDocument();
  });

  it("renders fallback when the surface is PREVIEW without explicit preview allowance", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot("PREVIEW"),
      isLoading: false,
    });

    render(
      <RuntimeCapabilityRouteGate
        aliases={["data-fabric"]}
        fallback={<div>Surface Closed</div>}
      >
        <div>Fabric Page</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(screen.getByText("Surface Closed")).toBeInTheDocument();
    expect(screen.queryByText("Fabric Page")).not.toBeInTheDocument();
  });

  for (const status of ["DISABLED", "UNAVAILABLE", "MISCONFIGURED"] as const) {
    it(`renders fallback when the surface is ${status}`, () => {
      mockUseSurfaceRegistry.mockReturnValue({
        data: makeSnapshot(status),
        isLoading: false,
      });

      render(
        <RuntimeCapabilityRouteGate
          aliases={["data-fabric"]}
          fallback={<div>Surface Closed</div>}
        >
          <div>Fabric Page</div>
        </RuntimeCapabilityRouteGate>,
      );

      expect(screen.getByText("Surface Closed")).toBeInTheDocument();
      expect(screen.queryByText("Fabric Page")).not.toBeInTheDocument();
    });
  }

  it("renders fallback for a failed or empty runtime truth response", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: {
        surfaces: [],
        generatedAt: "2026-01-01",
        requestId: "r",
        tenantId: "t",
      },
      isLoading: false,
    });

    render(
      <RuntimeCapabilityRouteGate
        aliases={["data-fabric"]}
        fallback={<div>Surface Closed</div>}
      >
        <div>Fabric Page</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(screen.getByText("Surface Closed")).toBeInTheDocument();
  });

  it("shows loading state while surfaces are loading", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: undefined,
      isLoading: true,
    });

    render(
      <RuntimeCapabilityRouteGate
        aliases={["data-fabric"]}
        fallback={<div>Surface Closed</div>}
      >
        <div>Fabric Page</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(
      screen.getByText(/Checking surface availability/i),
    ).toBeInTheDocument();
    expect(screen.queryByText("Surface Closed")).not.toBeInTheDocument();
  });
});
