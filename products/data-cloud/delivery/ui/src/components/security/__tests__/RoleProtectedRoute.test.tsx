import { render, screen } from "@testing-library/react";
import React from "react";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { SurfaceRegistrySnapshot } from "../../../api/surfaces.service";
import type { ShellRole } from "../../../lib/auth/session";
import { RoleProtectedRoute } from "../RoleProtectedRoute";

const {
  mockBootstrap,
  mockUseSurfaceRegistry,
} = vi.hoisted(() => ({
  mockBootstrap: vi.fn<() => { shellRole: ShellRole }>(),
  mockUseSurfaceRegistry:
    vi.fn<
      () => {
        data: SurfaceRegistrySnapshot | undefined;
        isLoading: boolean;
      }
    >(),
}));

vi.mock("../../../lib/auth/session", () => ({
  __esModule: true,
  default: {
    bootstrap: mockBootstrap,
  },
}));

vi.mock("../../../api/surfaces.service", () => ({
  useSurfaceRegistry: mockUseSurfaceRegistry,
}));

function makeSurface(
  path: string,
  minimumShellRole: ShellRole,
  options?: { targetOnly?: boolean },
) {
  return {
    key: path === "/" ? "home" : path.slice(1),
    label: path,
    status: "LIVE" as const,
    summary: "LIVE",
    ownerPlane: "data",
    requiredDependencies: [],
    dependencyProbes: [],
    tenantScope: "tenant",
    runtimeProfile: "test",
    limitations: "",
    actionsAllowed: [],
    rawValue: {},
    path,
    minimumShellRole,
    targetOnly: options?.targetOnly ?? false,
  };
}

function renderProtected(initialPath: string, routePath?: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route
          path="*"
          element={
            <RoleProtectedRoute routePath={routePath}>
              <span>protected-content</span>
            </RoleProtectedRoute>
          }
        />
        <Route path="/" element={<span>home-redirected</span>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("RoleProtectedRoute", () => {
  beforeEach(() => {
    mockBootstrap.mockReturnValue({ shellRole: "primary-user" });
    mockUseSurfaceRegistry.mockReturnValue({
      isLoading: false,
      data: {
        generatedAt: "2026-01-01",
        requestId: "r-1",
        tenantId: "t-1",
        surfaces: [
          makeSurface("/", "primary-user"),
          makeSurface("/data", "primary-user"),
          makeSurface("/trust", "operator"),
          makeSurface("/operations", "admin"),
          makeSurface("/context", "operator", { targetOnly: true }),
        ],
      },
    });
  });

  it("shows loading state while backend surface truth is loading", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      isLoading: true,
      data: undefined,
    });

    renderProtected("/trust", "/trust");

    expect(screen.getByRole("status")).toHaveTextContent(
      "Loading route policy...",
    );
  });

  it("allows route when shell role is sufficient", () => {
    mockBootstrap.mockReturnValue({ shellRole: "operator" });

    renderProtected("/trust", "/trust");

    expect(screen.getByText("protected-content")).toBeInTheDocument();
  });

  it("denies route when shell role is insufficient", () => {
    mockBootstrap.mockReturnValue({ shellRole: "primary-user" });

    renderProtected("/trust", "/trust");

    expect(screen.getByText("home-redirected")).toBeInTheDocument();
    expect(screen.queryByText("protected-content")).not.toBeInTheDocument();
  });

  it("fails closed for non-primary routes when backend surface truth is unavailable", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      isLoading: false,
      data: {
        generatedAt: "2026-01-01",
        requestId: "r-1",
        tenantId: "t-1",
        surfaces: [],
      },
    });

    renderProtected("/trust", "/trust");

    expect(screen.getByText("home-redirected")).toBeInTheDocument();
  });

  it("denies route when surface path is not found in backend truth", () => {
    renderProtected("/unknown", "/unknown");

    expect(screen.getByText("home-redirected")).toBeInTheDocument();
    expect(screen.queryByText("protected-content")).not.toBeInTheDocument();
  });

  it("denies target-only routes even when role is sufficient", () => {
    mockBootstrap.mockReturnValue({ shellRole: "operator" });

    renderProtected("/context", "/context");

    expect(screen.getByText("home-redirected")).toBeInTheDocument();
    expect(screen.queryByText("protected-content")).not.toBeInTheDocument();
  });
});
