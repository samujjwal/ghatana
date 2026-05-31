/**
 * DC-P1-006: Tests for CapabilityGated component.
 *
 * Verifies that the component correctly renders/suppresses its children based
 * on the live runtime surface registry state, covering active, degraded, and
 * unavailable paths as well as loading state.
 */

import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type {
  SurfaceRegistrySnapshot,
  SurfaceSignal,
} from "../../../api/surfaces.service";

// ---------------------------------------------------------------------------
// Hoist mocks before module resolution
// ---------------------------------------------------------------------------

const { mockUseCapabilityGate, mockUseSurfaceRegistry } = vi.hoisted(() => ({
  mockUseCapabilityGate:
    vi.fn<
      (
        capabilities: string[],
        mode?: import("../../../hooks/useCapabilityGate").GateMode,
      ) => boolean
    >(),
  mockUseSurfaceRegistry:
    vi.fn<
      () => { data: SurfaceRegistrySnapshot | undefined; isLoading: boolean }
    >(),
}));

vi.mock("../../../hooks/useCapabilityGate", () => ({
  useCapabilityGate: mockUseCapabilityGate,
}));

vi.mock("../../../api/surfaces.service", () => ({
  useSurfaceRegistry: mockUseSurfaceRegistry,
}));

import { CapabilityGated } from "../CapabilityGated";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeRegistry(
  status: SurfaceSignal["status"],
): SurfaceRegistrySnapshot {
  return {
    surfaces: [
      {
        key: "test-cap",
        label: "Test Capability",
        status,
        summary: status,
        rawValue: status,
      },
    ],
    generatedAt: "2026-01-01T00:00:00Z",
    requestId: "req-test",
    tenantId: "tenant-test",
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("CapabilityGated", () => {
  beforeEach(() => vi.clearAllMocks());

  describe("when the capability is active", () => {
    it("renders children", () => {
      mockUseSurfaceRegistry.mockReturnValue({
        data: makeRegistry("LIVE"),
        isLoading: false,
      });
      mockUseCapabilityGate.mockReturnValue(true);

      render(
        <CapabilityGated aliases={["test-cap"]}>
          <div>Protected Feature</div>
        </CapabilityGated>,
      );

      expect(screen.getByText("Protected Feature")).toBeInTheDocument();
    });
  });

  describe("when the capability is unavailable", () => {
    it("renders null when no fallback is provided", () => {
      mockUseSurfaceRegistry.mockReturnValue({
        data: makeRegistry("UNAVAILABLE"),
        isLoading: false,
      });
      mockUseCapabilityGate.mockReturnValue(false);

      const { container } = render(
        <CapabilityGated aliases={["test-cap"]}>
          <div>Protected Feature</div>
        </CapabilityGated>,
      );

      expect(screen.queryByText("Protected Feature")).not.toBeInTheDocument();
      expect(container.firstChild).toBeNull();
    });

    it("renders the fallback when provided", () => {
      mockUseSurfaceRegistry.mockReturnValue({
        data: makeRegistry("UNAVAILABLE"),
        isLoading: false,
      });
      mockUseCapabilityGate.mockReturnValue(false);

      render(
        <CapabilityGated
          aliases={["test-cap"]}
          fallback={<div>Feature Unavailable</div>}
        >
          <div>Protected Feature</div>
        </CapabilityGated>,
      );

      expect(screen.getByText("Feature Unavailable")).toBeInTheDocument();
      expect(screen.queryByText("Protected Feature")).not.toBeInTheDocument();
    });
  });

  describe("when the capability is degraded with mode=active", () => {
    it("renders fallback because active mode rejects degraded", () => {
      mockUseSurfaceRegistry.mockReturnValue({
        data: makeRegistry("DEGRADED"),
        isLoading: false,
      });
      mockUseCapabilityGate.mockReturnValue(false);

      render(
        <CapabilityGated
          aliases={["test-cap"]}
          mode="active"
          fallback={<div>Capability Degraded</div>}
        >
          <div>Protected Feature</div>
        </CapabilityGated>,
      );

      expect(screen.getByText("Capability Degraded")).toBeInTheDocument();
      expect(screen.queryByText("Protected Feature")).not.toBeInTheDocument();
    });
  });

  describe("when the capability is degraded with mode=activeOrDegraded", () => {
    it("renders children because degraded is permitted", () => {
      mockUseSurfaceRegistry.mockReturnValue({
        data: makeRegistry("DEGRADED"),
        isLoading: false,
      });
      mockUseCapabilityGate.mockReturnValue(true);

      render(
        <CapabilityGated aliases={["test-cap"]} mode="activeOrDegraded">
          <div>Degraded Feature</div>
        </CapabilityGated>,
      );

      expect(screen.getByText("Degraded Feature")).toBeInTheDocument();
    });
  });

  describe("while the registry is loading", () => {
    it("renders the loadingFallback when provided", () => {
      mockUseSurfaceRegistry.mockReturnValue({
        data: undefined,
        isLoading: true,
      });
      mockUseCapabilityGate.mockReturnValue(true);

      render(
        <CapabilityGated
          aliases={["test-cap"]}
          loadingFallback={<div>Checking runtime surface...</div>}
        >
          <div>Protected Feature</div>
        </CapabilityGated>,
      );

      expect(
        screen.getByText("Checking runtime surface..."),
      ).toBeInTheDocument();
    });

    it("renders the default spinner when no loadingFallback is provided", () => {
      mockUseSurfaceRegistry.mockReturnValue({
        data: undefined,
        isLoading: true,
      });
      mockUseCapabilityGate.mockReturnValue(true);

      render(
        <CapabilityGated aliases={["test-cap"]}>
          <div>Protected Feature</div>
        </CapabilityGated>,
      );

      // Default spinner renders text "Checking runtime surface..."
      expect(screen.getByText(/checking runtime surface/i)).toBeInTheDocument();
    });
  });
});
