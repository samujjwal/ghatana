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
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RuntimeCapabilityRouteGate } from "../../components/security/RuntimeCapabilityRouteGate";

// Mock the surfaces service
vi.mock("../../api/surfaces.service", () => ({
  useSurfaceRegistry: () => ({
    data: {
      surfaces: [
        {
          key: "alert-triage",
          label: "Alerts",
          status: "PREVIEW",
          summary: "Operator alert triage console",
          audience: "operator",
          readinessClass: "operator-preview",
          targetOnly: false,
          ownerPlane: "action",
          requiredDependencies: [],
          dependencyProbes: [],
          tenantScope: "global",
          runtimeProfile: "production",
          limitations: "",
          actionsAllowed: [],
          rawValue: {},
        },
        {
          key: "runtime-truth",
          label: "Runtime Truth",
          status: "PREVIEW",
          summary: "Runtime truth dashboard",
          audience: "internal",
          readinessClass: "internal-preview",
          targetOnly: false,
          ownerPlane: "data",
          requiredDependencies: [],
          dependencyProbes: [],
          tenantScope: "global",
          runtimeProfile: "production",
          limitations: "",
          actionsAllowed: [],
          rawValue: {},
        },
        {
          key: "context-explorer",
          label: "Context Explorer",
          status: "UNAVAILABLE",
          summary: "Context explorer not available",
          audience: undefined,
          readinessClass: "target-only",
          targetOnly: true,
          ownerPlane: "action",
          requiredDependencies: [],
          dependencyProbes: [],
          tenantScope: "global",
          runtimeProfile: "production",
          limitations: "Target-only surface - not yet generally available",
          actionsAllowed: [],
          rawValue: {},
        },
      ],
      generatedAt: "2024-01-15T10:30:00Z",
      requestId: "req-123",
      tenantId: "tenant-abc",
    },
    isLoading: false,
  }),
  isSurfaceAvailable: vi.fn((signal, options) => {
    if (!signal) return false;
    if (signal.status === "LIVE" || signal.status === "DEGRADED") return true;
    if (signal.status === "PREVIEW" && options?.allowPreview) {
      if (options?.previewAudience === "admin") return true;
      return signal.audience === options?.previewAudience;
    }
    if (signal.targetOnly || signal.readinessClass === "target-only") return false;
    return false;
  }),
  getSurfaceSignal: vi.fn((surfaces, aliases) => {
    const normalizedAliases = aliases.map((a) => a.toLowerCase());
    return surfaces.find((s) =>
      normalizedAliases.includes(s.key.toLowerCase()),
    );
  }),
}));

describe("RuntimeCapabilityRouteGate", () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );

  describe("preview access control", () => {
    it("blocks preview when allowPreview is false (default)", () => {
      render(
        <RuntimeCapabilityRouteGate
          aliases={["alert-triage"]}
          allowPreview={false}
        >
          <div>Protected Content</div>
        </RuntimeCapabilityRouteGate>,
        { wrapper },
      );

      expect(screen.queryByText("Protected Content")).not.toBeInTheDocument();
    });

    it("allows preview when allowPreview is true and audience matches", () => {
      render(
        <RuntimeCapabilityRouteGate
          aliases={["alert-triage"]}
          allowPreview={true}
          allowPreviewFor="operator"
        >
          <div>Protected Content</div>
        </RuntimeCapabilityRouteGate>,
        { wrapper },
      );

      expect(screen.getByText("Protected Content")).toBeInTheDocument();
    });

    it("blocks preview when audience does not match", () => {
      render(
        <RuntimeCapabilityRouteGate
          aliases={["alert-triage"]}
          allowPreview={true}
          allowPreviewFor="admin"
        >
          <div>Protected Content</div>
        </RuntimeCapabilityRouteGate>,
        { wrapper },
      );

      expect(screen.queryByText("Protected Content")).not.toBeInTheDocument();
    });

    it("allows admin to access any preview", () => {
      render(
        <RuntimeCapabilityRouteGate
          aliases={["alert-triage"]}
          allowPreview={true}
          allowPreviewFor="admin"
        >
          <div>Protected Content</div>
        </RuntimeCapabilityRouteGate>,
        { wrapper },
      );

      expect(screen.getByText("Protected Content")).toBeInTheDocument();
    });

    it("allows internal preview for admin", () => {
      render(
        <RuntimeCapabilityRouteGate
          aliases={["runtime-truth"]}
          allowPreview={true}
          allowPreviewFor="admin"
        >
          <div>Protected Content</div>
        </RuntimeCapabilityRouteGate>,
        { wrapper },
      );

      expect(screen.getByText("Protected Content")).toBeInTheDocument();
    });
  });

  describe("target-only surfaces", () => {
    it("blocks target-only surfaces regardless of preview settings", () => {
      render(
        <RuntimeCapabilityRouteGate
          aliases={["context-explorer"]}
          allowPreview={true}
          allowPreviewFor="admin"
        >
          <div>Protected Content</div>
        </RuntimeCapabilityRouteGate>,
        { wrapper },
      );

      expect(screen.queryByText("Protected Content")).not.toBeInTheDocument();
    });
  });

  describe("fallback rendering", () => {
    it("renders custom fallback when access is denied", () => {
      render(
        <RuntimeCapabilityRouteGate
          aliases={["alert-triage"]}
          allowPreview={false}
          fallback={<div>Access Denied</div>}
        >
          <div>Protected Content</div>
        </RuntimeCapabilityRouteGate>,
        { wrapper },
      );

      expect(screen.getByText("Access Denied")).toBeInTheDocument();
      expect(screen.queryByText("Protected Content")).not.toBeInTheDocument();
    });
  });

  describe("loading state", () => {
    it("shows loading state when blockWhileLoading is true", () => {
      vi.mocked(
        require("../../api/surfaces.service").useSurfaceRegistry,
      ).mockReturnValue({
        data: undefined,
        isLoading: true,
      } as any);

      render(
        <RuntimeCapabilityRouteGate
          aliases={["alert-triage"]}
          blockWhileLoading={true}
        >
          <div>Protected Content</div>
        </RuntimeCapabilityRouteGate>,
        { wrapper },
      );

      expect(screen.queryByText("Protected Content")).not.toBeInTheDocument();
    });
  });
});
