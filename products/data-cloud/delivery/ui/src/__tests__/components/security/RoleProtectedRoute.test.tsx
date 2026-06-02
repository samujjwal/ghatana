import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import type { SurfaceSignal } from "../../../api/surfaces.service";
import { RoleProtectedRoute } from "../../../components/security/RoleProtectedRoute";

const { mockBootstrap, mockUseSurfaceRegistry } = vi.hoisted(() => ({
  mockBootstrap: vi.fn(),
  mockUseSurfaceRegistry: vi.fn(),
}));

vi.mock("../../../lib/auth/session", () => ({
  __esModule: true,
  default: {
    bootstrap: mockBootstrap,
  },
}));

vi.mock("../../../api/surfaces.service", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("../../../api/surfaces.service")>();
  return {
    ...actual,
    useSurfaceRegistry: mockUseSurfaceRegistry,
  };
});

function makeSurface(path: string, extras: Partial<SurfaceSignal> = {}): SurfaceSignal {
  return {
    key: path.replace("/", "") || "home",
    path,
    label: path,
    status: "LIVE",
    summary: "LIVE",
    ownerPlane: "data",
    requiredDependencies: [],
    dependencyProbes: [],
    tenantScope: "tenant",
    runtimeProfile: "production",
    limitations: "",
    actionsAllowed: [],
    rawValue: {},
    minimumShellRole: "primary-user",
    discoverable: true,
    ...extras,
  };
}

function renderRoute(path: string): void {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path={path === "/" ? "/" : `${path}`}
          element={
            <RoleProtectedRoute routePath={path}>
              <div>Protected</div>
            </RoleProtectedRoute>
          }
        />
        <Route path="/" element={<div>Home</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("RoleProtectedRoute", () => {
  it("allows when role is sufficient", () => {
    mockBootstrap.mockReturnValue({ shellRole: "admin" });
    mockUseSurfaceRegistry.mockReturnValue({
      isLoading: false,
      data: {
        generatedAt: "now",
        requestId: "r",
        tenantId: "t",
        surfaces: [makeSurface("/operations", { minimumShellRole: "operator" })],
      },
    });

    renderRoute("/operations");

    expect(screen.getByText("Protected")).toBeInTheDocument();
  });

  it("denies when role is insufficient", () => {
    mockBootstrap.mockReturnValue({ shellRole: "primary-user" });
    mockUseSurfaceRegistry.mockReturnValue({
      isLoading: false,
      data: {
        generatedAt: "now",
        requestId: "r",
        tenantId: "t",
        surfaces: [makeSurface("/operations", { minimumShellRole: "operator" })],
      },
    });

    renderRoute("/operations");

    expect(screen.queryByText("Protected")).not.toBeInTheDocument();
    expect(screen.getByText("Home")).toBeInTheDocument();
  });

  it("blocks while backend surface truth is loading", () => {
    mockBootstrap.mockReturnValue({ shellRole: "admin" });
    mockUseSurfaceRegistry.mockReturnValue({ isLoading: true, data: undefined });

    renderRoute("/operations");

    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(screen.queryByText("Protected")).not.toBeInTheDocument();
  });

  it("fails closed for non-primary routes when backend is unavailable", () => {
    mockBootstrap.mockReturnValue({ shellRole: "admin" });
    mockUseSurfaceRegistry.mockReturnValue({
      isLoading: false,
      data: { generatedAt: "now", requestId: "r", tenantId: "t", surfaces: [] },
    });

    renderRoute("/operations");

    expect(screen.queryByText("Protected")).not.toBeInTheDocument();
    expect(screen.getByText("Home")).toBeInTheDocument();
  });

  it("allows /data policy fallback when backend is unavailable", () => {
    mockBootstrap.mockReturnValue({ shellRole: "primary-user" });
    mockUseSurfaceRegistry.mockReturnValue({
      isLoading: false,
      data: { generatedAt: "now", requestId: "r", tenantId: "t", surfaces: [] },
    });

    render(
      <MemoryRouter initialEntries={["/data"]}>
        <Routes>
          <Route
            path="/data"
            element={
              <RoleProtectedRoute routePath="/data">
                <div>Protected</div>
              </RoleProtectedRoute>
            }
          />
          <Route path="/" element={<div>Home</div>} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText("Protected")).toBeInTheDocument();
  });

  it("denies unknown route when backend is available", () => {
    mockBootstrap.mockReturnValue({ shellRole: "admin" });
    mockUseSurfaceRegistry.mockReturnValue({
      isLoading: false,
      data: {
        generatedAt: "now",
        requestId: "r",
        tenantId: "t",
        surfaces: [makeSurface("/operations")],
      },
    });

    renderRoute("/unknown");

    expect(screen.queryByText("Protected")).not.toBeInTheDocument();
    expect(screen.getByText("Home")).toBeInTheDocument();
  });

  it("denies target-only route", () => {
    mockBootstrap.mockReturnValue({ shellRole: "admin" });
    mockUseSurfaceRegistry.mockReturnValue({
      isLoading: false,
      data: {
        generatedAt: "now",
        requestId: "r",
        tenantId: "t",
        surfaces: [
          makeSurface("/context", {
            targetOnly: true,
            readinessClass: "target-only",
          }),
        ],
      },
    });

    renderRoute("/context");

    expect(screen.queryByText("Protected")).not.toBeInTheDocument();
    expect(screen.getByText("Home")).toBeInTheDocument();
  });
});
