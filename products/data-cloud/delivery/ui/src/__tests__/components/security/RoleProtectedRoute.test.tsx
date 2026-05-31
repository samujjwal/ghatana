/**
 * RoleProtectedRoute Component Tests
 *
 * P5-05: Tests for role-protected routes with new lifecycle checks.
 *
 * @doc.type test
 * @doc.purpose Unit tests for RoleProtectedRoute component
 * @doc.layer frontend
 */

import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { RoleProtectedRoute } from "../../../components/security/RoleProtectedRoute";

// Mock SessionBootstrap
vi.mock("../../../lib/auth/session", () => ({
  SessionBootstrap: {
    bootstrap: vi.fn(() => ({
      shellRole: "operator",
      isAuthenticated: true,
      tenantId: "tenant-abc",
    })),
  },
  default: {
    bootstrap: vi.fn(() => ({
      shellRole: "operator",
      isAuthenticated: true,
      tenantId: "tenant-abc",
    })),
  },
}));

describe("RoleProtectedRoute", () => {
  describe("role-based access control", () => {
    it("allows access when shell role meets minimum requirement", () => {
      vi.mocked(
        require("../../lib/auth/session").SessionBootstrap.bootstrap,
      ).mockReturnValue({
        shellRole: "operator",
        isAuthenticated: true,
        tenantId: "tenant-abc",
      });

      render(
        <MemoryRouter initialEntries={["/operations"]}>
          <Routes>
            <Route
              path="/operations"
              element={
                <RoleProtectedRoute routePath="/operations">
                  <div>Operations Content</div>
                </RoleProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.getByText("Operations Content")).toBeInTheDocument();
    });

    it("denies access when shell role is below minimum requirement", () => {
      vi.mocked(
        require("../../lib/auth/session").SessionBootstrap.bootstrap,
      ).mockReturnValue({
        shellRole: "primary-user",
        isAuthenticated: true,
        tenantId: "tenant-abc",
      });

      render(
        <MemoryRouter initialEntries={["/operations"]}>
          <Routes>
            <Route
              path="/operations"
              element={
                <RoleProtectedRoute routePath="/operations">
                  <div>Operations Content</div>
                </RoleProtectedRoute>
              }
            />
            <Route
              path="/"
              element={<div>Home</div>}
            />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.queryByText("Operations Content")).not.toBeInTheDocument();
      expect(screen.getByText("Home")).toBeInTheDocument();
    });

    it("renders custom fallback when access is denied", () => {
      vi.mocked(
        require("../../lib/auth/session").SessionBootstrap.bootstrap,
      ).mockReturnValue({
        shellRole: "primary-user",
        isAuthenticated: true,
        tenantId: "tenant-abc",
      });

      render(
        <MemoryRouter initialEntries={["/operations"]}>
          <Routes>
            <Route
              path="/operations"
              element={
                <RoleProtectedRoute
                  routePath="/operations"
                  fallback={<div>Access Denied</div>}
                >
                  <div>Operations Content</div>
                </RoleProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.getByText("Access Denied")).toBeInTheDocument();
      expect(screen.queryByText("Operations Content")).not.toBeInTheDocument();
    });
  });

  describe("lifecycle-based access control", () => {
    it("allows access to user-ready routes", () => {
      vi.mocked(
        require("../../lib/auth/session").SessionBootstrap.bootstrap,
      ).mockReturnValue({
        shellRole: "primary-user",
        isAuthenticated: true,
        tenantId: "tenant-abc",
      });

      render(
        <MemoryRouter initialEntries={["/data"]}>
          <Routes>
            <Route
              path="/data"
              element={
                <RoleProtectedRoute routePath="/data">
                  <div>Data Content</div>
                </RoleProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.getByText("Data Content")).toBeInTheDocument();
    });

    it("denies access to target-only routes", () => {
      vi.mocked(
        require("../../lib/auth/session").SessionBootstrap.bootstrap,
      ).mockReturnValue({
        shellRole: "admin",
        isAuthenticated: true,
        tenantId: "tenant-abc",
      });

      render(
        <MemoryRouter initialEntries={["/context"]}>
          <Routes>
            <Route
              path="/context"
              element={
                <RoleProtectedRoute routePath="/context">
                  <div>Context Content</div>
                </RoleProtectedRoute>
              }
            />
            <Route
              path="/"
              element={<div>Home</div>}
            />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.queryByText("Context Content")).not.toBeInTheDocument();
      expect(screen.getByText("Home")).toBeInTheDocument();
    });

    it("denies access to disabled routes", () => {
      vi.mocked(
        require("../../lib/auth/session").SessionBootstrap.bootstrap,
      ).mockReturnValue({
        shellRole: "admin",
        isAuthenticated: true,
        tenantId: "tenant-abc",
      });

      render(
        <MemoryRouter initialEntries={["/some-disabled-route"]}>
          <Routes>
            <Route
              path="/some-disabled-route"
              element={
                <RoleProtectedRoute routePath="/some-disabled-route">
                  <div>Disabled Content</div>
                </RoleProtectedRoute>
              }
            />
            <Route
              path="/"
              element={<div>Home</div>}
            />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.queryByText("Disabled Content")).not.toBeInTheDocument();
      expect(screen.getByText("Home")).toBeInTheDocument();
    });

    it("allows operator-preview with allowPreviewAs=operator", () => {
      vi.mocked(
        require("../../lib/auth/session").SessionBootstrap.bootstrap,
      ).mockReturnValue({
        shellRole: "operator",
        isAuthenticated: true,
        tenantId: "tenant-abc",
      });

      render(
        <MemoryRouter initialEntries={["/alerts"]}>
          <Routes>
            <Route
              path="/alerts"
              element={
                <RoleProtectedRoute
                  routePath="/alerts"
                  allowPreviewAs="operator"
                >
                  <div>Alerts Content</div>
                </RoleProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.getByText("Alerts Content")).toBeInTheDocument();
    });

    it("denies operator-preview without allowPreviewAs", () => {
      vi.mocked(
        require("../../lib/auth/session").SessionBootstrap.bootstrap,
      ).mockReturnValue({
        shellRole: "operator",
        isAuthenticated: true,
        tenantId: "tenant-abc",
      });

      render(
        <MemoryRouter initialEntries={["/alerts"]}>
          <Routes>
            <Route
              path="/alerts"
              element={
                <RoleProtectedRoute routePath="/alerts">
                  <div>Alerts Content</div>
                </RoleProtectedRoute>
              }
            />
            <Route
              path="/"
              element={<div>Home</div>}
            />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.queryByText("Alerts Content")).not.toBeInTheDocument();
      expect(screen.getByText("Home")).toBeInTheDocument();
    });

    it("allows internal-preview with allowPreviewAs=admin", () => {
      vi.mocked(
        require("../../lib/auth/session").SessionBootstrap.bootstrap,
      ).mockReturnValue({
        shellRole: "admin",
        isAuthenticated: true,
        tenantId: "tenant-abc",
      });

      render(
        <MemoryRouter initialEntries={["/operations/runtime-truth"]}>
          <Routes>
            <Route
              path="/operations/runtime-truth"
              element={
                <RoleProtectedRoute
                  routePath="/operations/runtime-truth"
                  allowPreviewAs="admin"
                >
                  <div>Runtime Truth Content</div>
                </RoleProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.getByText("Runtime Truth Content")).toBeInTheDocument();
    });

    it("denies internal-preview for operator role", () => {
      vi.mocked(
        require("../../lib/auth/session").SessionBootstrap.bootstrap,
      ).mockReturnValue({
        shellRole: "operator",
        isAuthenticated: true,
        tenantId: "tenant-abc",
      });

      render(
        <MemoryRouter initialEntries={["/operations/runtime-truth"]}>
          <Routes>
            <Route
              path="/operations/runtime-truth"
              element={
                <RoleProtectedRoute
                  routePath="/operations/runtime-truth"
                  allowPreviewAs="operator"
                >
                  <div>Runtime Truth Content</div>
                </RoleProtectedRoute>
              }
            />
            <Route
              path="/"
              element={<div>Home</div>}
            />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.queryByText("Runtime Truth Content")).not.toBeInTheDocument();
      expect(screen.getByText("Home")).toBeInTheDocument();
    });
  });

  describe("parameterized route resolution", () => {
    it("resolves parent route for parameterized paths", () => {
      vi.mocked(
        require("../../lib/auth/session").SessionBootstrap.bootstrap,
      ).mockReturnValue({
        shellRole: "operator",
        isAuthenticated: true,
        tenantId: "tenant-abc",
      });

      render(
        <MemoryRouter initialEntries={["/operations/jobs"]}>
          <Routes>
            <Route
              path="/operations/jobs"
              element={
                <RoleProtectedRoute>
                  <div>Jobs Content</div>
                </RoleProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.getByText("Jobs Content")).toBeInTheDocument();
    });
  });
});
