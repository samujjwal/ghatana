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

// Mock i18n
vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

import { RuntimeCapabilityRouteGate } from "../RuntimeCapabilityRouteGate";

function makeSignal(
  status: SurfaceSignal["status"],
  audience?: "internal" | "operator" | "admin",
  targetOnly?: boolean
): SurfaceSignal {
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
    audience,
    targetOnly,
  };
}

function makeSnapshot(
  status: SurfaceSignal["status"],
  audience?: "internal" | "operator" | "admin",
  targetOnly?: boolean
): SurfaceRegistrySnapshot {
  return {
    surfaces: [makeSignal(status, audience, targetOnly)],
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

  it("renders a degraded banner with i18n key and children when the surface is DEGRADED", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot("DEGRADED"),
      isLoading: false,
    });

    render(
      <RuntimeCapabilityRouteGate aliases={["data-fabric"]}>
        <div>Fabric Page</div>
      </RuntimeCapabilityRouteGate>,
    );

    // Should use i18n key, not hardcoded "Degraded"
    expect(screen.getByRole("alert")).toHaveTextContent("runtimeGate.degraded");
    expect(screen.getByText("Fabric Page")).toBeInTheDocument();
  });

  it("renders a preview badge with i18n key and children when the surface is PREVIEW with allowPreview", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot("PREVIEW", "operator"),
      isLoading: false,
    });

    render(
      <RuntimeCapabilityRouteGate aliases={["data-fabric"]} allowPreview allowPreviewFor="operator">
        <div>Fabric Page</div>
      </RuntimeCapabilityRouteGate>,
    );

    // Should use i18n key, not hardcoded "Preview"
    expect(screen.getByRole("status")).toHaveTextContent("runtimeGate.preview");
    expect(screen.getByText("Fabric Page")).toBeInTheDocument();
  });

  it("P5-02: PREVIEW does not render by default without allowPreview", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot("PREVIEW", "operator"),
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

  it("P5-02: operator-preview renders only for operator/admin", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot("PREVIEW", "operator"),
      isLoading: false,
    });

    // Operator should be able to access
    render(
      <RuntimeCapabilityRouteGate aliases={["data-fabric"]} allowPreview allowPreviewFor="operator">
        <div>Fabric Page</div>
      </RuntimeCapabilityRouteGate>,
    );
    expect(screen.getByText("Fabric Page")).toBeInTheDocument();

    // Admin should be able to access any preview
    render(
      <RuntimeCapabilityRouteGate aliases={["data-fabric"]} allowPreview allowPreviewFor="admin">
        <div>Fabric Page</div>
      </RuntimeCapabilityRouteGate>,
    );
    expect(screen.getByText("Fabric Page")).toBeInTheDocument();
  });

  it("P5-02: target-only always renders disabled state", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot("UNAVAILABLE", undefined, true),
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

  it("P5-02: loading state does not flash optional surface", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: undefined,
      isLoading: true,
    });

    render(
      <RuntimeCapabilityRouteGate
        aliases={["data-fabric"]}
        blockWhileLoading={true}
        fallback={<div>Surface Closed</div>}
      >
        <div>Fabric Page</div>
      </RuntimeCapabilityRouteGate>,
    );

    // Should use i18n key for loading message
    expect(
      screen.getByText("runtimeGate.loadingMessage"),
    ).toBeInTheDocument();
    expect(screen.queryByText("Fabric Page")).not.toBeInTheDocument();
    expect(screen.queryByText("Surface Closed")).not.toBeInTheDocument();
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

  it("shows loading state with i18n key while surfaces are loading", () => {
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

    // Should use i18n key for loading message
    expect(
      screen.getByText("runtimeGate.loadingMessage"),
    ).toBeInTheDocument();
    expect(screen.queryByText("Surface Closed")).not.toBeInTheDocument();
  });
});
