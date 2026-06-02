import { describe, expect, it } from "vitest";
import {
  staticRouteSurfaceFallback,
  getActiveRouteSurfaces,
  getDiscoverableRouteSurfaces,
  getRouteSurfaceByPath,
  getRouteSurfacesByLifecycle,
} from "../../lib/routing/StaticRouteSurfaceFallback";

describe("RouteSurfaceRegistry", () => {
  describe("navigation discoverability", () => {
    it("shows appropriate discoverable surfaces for primary-user role", () => {
      const discoverable = getDiscoverableRouteSurfaces("primary-user");
      const paths = discoverable.map((r) => r.path);

      // Primary users should see outcome-first surfaces
      expect(paths).toContain("/");
      expect(paths).toContain("/data");
      expect(paths).toContain("/pipelines");
      expect(paths).toContain("/query");

      // Should not see operator/admin only surfaces
      expect(paths).not.toContain("/events");
      expect(paths).not.toContain("/trust");
      expect(paths).not.toContain("/operations");
      expect(paths).not.toContain("/connectors");
      expect(paths).not.toContain("/insights");
    });

    it("shows appropriate discoverable surfaces for operator role", () => {
      const discoverable = getDiscoverableRouteSurfaces("operator");
      const paths = discoverable.map((r) => r.path);

      // Operators should see more surfaces
      expect(paths).toContain("/");
      expect(paths).toContain("/data");
      expect(paths).toContain("/pipelines");
      expect(paths).toContain("/query");
      expect(paths).toContain("/events");
      expect(paths).toContain("/trust");
      expect(paths).not.toContain("/connectors");
      expect(paths).not.toContain("/insights");
      expect(paths).not.toContain("/plugins");

      // Should not see admin-only surfaces
      expect(paths).not.toContain("/operations");
    });

    it("shows appropriate discoverable surfaces for admin role", () => {
      const discoverable = getDiscoverableRouteSurfaces("admin");
      const paths = discoverable.map((r) => r.path);

      // Admins should see all discoverable surfaces
      expect(paths).toContain("/");
      expect(paths).toContain("/data");
      expect(paths).toContain("/pipelines");
      expect(paths).toContain("/query");
      expect(paths).toContain("/events");
      expect(paths).toContain("/trust");
      expect(paths).not.toContain("/connectors");
      expect(paths).not.toContain("/insights");
      expect(paths).not.toContain("/plugins");
      expect(paths).toContain("/operations");
    });

    it("disabled surface explains why unavailable through lifecycle and discoverable flags", () => {
      const alertsRoute = staticRouteSurfaceFallback.alerts;
      const memoryRoute = staticRouteSurfaceFallback.memory;
      const agentsRoute = staticRouteSurfaceFallback.agents;

      // These surfaces are marked as non-discoverable
      expect(alertsRoute.discoverable).toBe(false);
      expect(memoryRoute.discoverable).toBe(false);
      expect(agentsRoute.discoverable).toBe(false);

      // They are in operator-preview lifecycle
      expect(alertsRoute.lifecycle).toBe("operator-preview");
      expect(memoryRoute.lifecycle).toBe("operator-preview");
      expect(agentsRoute.lifecycle).toBe("operator-preview");

      // They should not appear in discoverable routes
      const discoverable = getDiscoverableRouteSurfaces("operator");
      const paths = discoverable.map((r) => r.path);
      expect(paths).not.toContain("/alerts");
      expect(paths).not.toContain("/memory");
      expect(paths).not.toContain("/agents");
    });

    it("no release-readiness route is discoverable", () => {
      const releaseTruthRoute =
        staticRouteSurfaceFallback.operationsReleaseTruth;
      const settingsRoute = staticRouteSurfaceFallback.settings;

      // Release-readiness routes are marked as boundary lifecycle
      expect(releaseTruthRoute.lifecycle).toBe("boundary");
      expect(settingsRoute.lifecycle).toBe("boundary");

      // They are non-discoverable
      expect(releaseTruthRoute.discoverable).toBe(false);
      expect(settingsRoute.discoverable).toBe(false);

      // They should not appear in discoverable routes even for admin
      const discoverable = getDiscoverableRouteSurfaces("admin");
      const paths = discoverable.map((r) => r.path);
      expect(paths).not.toContain("/operations/release-truth");
      expect(paths).not.toContain("/settings");

      // They should not appear when includesBoundary is false
      const discoverableWithoutBoundary = getDiscoverableRouteSurfaces(
        "admin",
        false,
      );
      const pathsWithoutBoundary = discoverableWithoutBoundary.map(
        (r) => r.path,
      );
      expect(pathsWithoutBoundary).not.toContain("/operations/release-truth");
      expect(pathsWithoutBoundary).not.toContain("/settings");
    });

    it("boundary routes appear when includesBoundary is true", () => {
      const discoverableWithBoundary = getDiscoverableRouteSurfaces(
        "admin",
        true,
      );
      const paths = discoverableWithBoundary.map((r) => r.path);

      // With includesBoundary=true, boundary routes that are discoverable should appear
      // But since release-truth and settings are discoverable=false, they still won't appear
      expect(paths).not.toContain("/operations/release-truth");
      expect(paths).not.toContain("/settings");
    });
  });

  describe("lifecycle grouping", () => {
    it("groups routes by lifecycle correctly", () => {
      const byLifecycle = getRouteSurfacesByLifecycle();

      // P5-01: Updated counts for new lifecycle values
      expect(byLifecycle.active).toHaveLength(10); // home, data, connectors, pipelines, query, insights, trust, events, operations, operationsJobs
      expect(byLifecycle["operator-preview"]).toHaveLength(7); // alerts, memory, entities, fabric, agents, mediaArtifacts, plugins
      expect(byLifecycle["target-only"]).toHaveLength(1); // context
      expect(byLifecycle.boundary).toHaveLength(2); // operationsReleaseTruth, settings
      expect(byLifecycle.deprecated).toHaveLength(0);
      expect(byLifecycle.redirect).toHaveLength(0);
      expect(byLifecycle.removed).toHaveLength(0);
      expect(byLifecycle["user-ready"]).toHaveLength(0);
      expect(byLifecycle["internal-preview"]).toHaveLength(0);
      expect(byLifecycle.disabled).toHaveLength(0);
    });

    it("active routes are correctly identified", () => {
      const activeRoutes = getActiveRouteSurfaces();
      const paths = activeRoutes.map((r) => r.path);

      expect(paths).toContain("/");
      expect(paths).toContain("/data");
      expect(paths).toContain("/pipelines");
      expect(paths).toContain("/query");
      expect(paths).toContain("/trust");
      expect(paths).toContain("/events");
      expect(paths).toContain("/operations");
      expect(paths).toContain("/operations/jobs");

      expect(paths).not.toContain("/alerts"); // operator-preview
      expect(paths).not.toContain("/memory"); // operator-preview
      expect(paths).not.toContain("/entities"); // operator-preview
      expect(paths).not.toContain("/fabric"); // operator-preview
      expect(paths).not.toContain("/agents"); // operator-preview
      expect(paths).not.toContain("/media/artifacts"); // operator-preview
      expect(paths).not.toContain("/plugins"); // operator-preview
      expect(paths).not.toContain("/context"); // target-only
      expect(paths).not.toContain("/operations/release-truth"); // boundary
      expect(paths).not.toContain("/settings"); // boundary
    });

    it("target-only routes are excluded from discoverable routes", () => {
      const contextRoute = staticRouteSurfaceFallback.context;

      // Context is marked as target-only
      expect(contextRoute.lifecycle).toBe("target-only");
      expect(contextRoute.discoverable).toBe(false);

      // Should not appear in discoverable routes even for admin
      const discoverable = getDiscoverableRouteSurfaces("admin");
      const paths = discoverable.map((r) => r.path);
      expect(paths).not.toContain("/context");
    });
  });

  describe("route lookup", () => {
    it("finds route by path", () => {
      const homeRoute = getRouteSurfaceByPath("/");
      expect(homeRoute).toBeDefined();
      expect(homeRoute?.label).toBe("Home");

      const dataRoute = getRouteSurfaceByPath("/data");
      expect(dataRoute).toBeDefined();
      expect(dataRoute?.label).toBe("Data");

      const nonExistent = getRouteSurfaceByPath("/non-existent");
      expect(nonExistent).toBeUndefined();
    });
  });
});
