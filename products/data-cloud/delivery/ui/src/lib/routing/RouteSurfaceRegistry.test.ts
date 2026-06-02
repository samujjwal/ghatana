/**
 * Route Surface Registry Tests (P5-04)
 *
 * Tests for route surface registry including lifecycle management,
 * discoverability, and preview gating.
 *
 * @doc.type module
 * @doc.purpose Tests for route surface registry lifecycle and discoverability
 * @doc.layer frontend
 * @doc.pattern Test
 */

import { describe, expect, it } from "vitest";
import {
  staticRouteSurfaceFallback,
  getDiscoverableRouteSurfaces,
  getRouteSurfacesByLifecycle,
  getRouteSurfaceByPath,
} from "./StaticRouteSurfaceFallback";

describe("RouteSurfaceRegistry", () => {
  describe("P5-04: target-only routes are never discoverable", () => {
    it("excludes target-only routes from discoverable surfaces", () => {
      const discoverable = getDiscoverableRouteSurfaces("operator");
      const targetOnlyRoutes = Object.values(staticRouteSurfaceFallback).filter(
        (route) => route.lifecycle === "target-only"
      );

      expect(targetOnlyRoutes).toHaveLength(1); // context is target-only
      expect(discoverable).not.toContainEqual(
        expect.objectContaining({ lifecycle: "target-only" })
      );
    });

    it("context route is marked as target-only", () => {
      const contextRoute = staticRouteSurfaceFallback.context;
      expect(contextRoute.lifecycle).toBe("target-only");
      expect(contextRoute.discoverable).toBe(false);
    });
  });

  describe("P5-04: preview routes require explicit preview audience", () => {
    it("operator-preview routes have previewAudience set", () => {
      const operatorPreviewRoutes = Object.values(staticRouteSurfaceFallback).filter(
        (route) => route.lifecycle === "operator-preview"
      );

      expect(operatorPreviewRoutes.length).toBeGreaterThan(0);
      operatorPreviewRoutes.forEach((route) => {
        expect(route.previewAudience).toBe("operator");
        expect(route.discoverable).toBe(false);
      });
    });

    it("memory is marked as operator-preview", () => {
      const memoryRoute = staticRouteSurfaceFallback.memory;
      expect(memoryRoute.lifecycle).toBe("operator-preview");
      expect(memoryRoute.previewAudience).toBe("operator");
    });

    it("agents is marked as operator-preview", () => {
      const agentsRoute = staticRouteSurfaceFallback.agents;
      expect(agentsRoute.lifecycle).toBe("operator-preview");
      expect(agentsRoute.previewAudience).toBe("operator");
    });

    it("mediaArtifacts is marked as operator-preview", () => {
      const mediaArtifactsRoute = staticRouteSurfaceFallback.mediaArtifacts;
      expect(mediaArtifactsRoute.lifecycle).toBe("operator-preview");
      expect(mediaArtifactsRoute.previewAudience).toBe("operator");
    });

    it("fabric is marked as operator-preview", () => {
      const fabricRoute = staticRouteSurfaceFallback.fabric;
      expect(fabricRoute.lifecycle).toBe("operator-preview");
      expect(fabricRoute.previewAudience).toBe("operator");
    });

    it("plugins is marked as operator-preview", () => {
      const pluginsRoute = staticRouteSurfaceFallback.plugins;
      expect(pluginsRoute.lifecycle).toBe("operator-preview");
      expect(pluginsRoute.previewAudience).toBe("operator");
    });
  });

  describe("P5-04: default nav contains only intended outcome-first routes", () => {
    it("primary-user sees only active, discoverable routes", () => {
      const discoverable = getDiscoverableRouteSurfaces("primary-user");
      const expectedPaths = ["/", "/data", "/query", "/pipelines"];

      const actualPaths = discoverable.map((route) => route.path);
      expect(actualPaths).toEqual(expect.arrayContaining(expectedPaths));
      
      // Should not include operator-only routes
      expect(actualPaths).not.toContain("/events");
      expect(actualPaths).not.toContain("/trust");
      expect(actualPaths).not.toContain("/operations");
    });

    it("operator sees active and operator-preview routes", () => {
      const discoverable = getDiscoverableRouteSurfaces("operator");
      const expectedPaths = ["/", "/data", "/query", "/pipelines", "/events", "/trust"];

      const actualPaths = discoverable.map((route) => route.path);
      expect(actualPaths).toEqual(expect.arrayContaining(expectedPaths));
      
      // Should not include target-only routes
      expect(actualPaths).not.toContain("/context");
    });

    it("admin sees all active routes including operations", () => {
      const discoverable = getDiscoverableRouteSurfaces("admin");
      const expectedPaths = ["/", "/data", "/query", "/pipelines", "/events", "/trust", "/operations"];

      const actualPaths = discoverable.map((route) => route.path);
      expect(actualPaths).toEqual(expect.arrayContaining(expectedPaths));
    });
  });

  describe("getRouteSurfacesByLifecycle", () => {
    it("groups routes by lifecycle correctly", () => {
      const byLifecycle = getRouteSurfacesByLifecycle();

      expect(byLifecycle.active).toBeDefined();
      expect(byLifecycle["operator-preview"]).toBeDefined();
      expect(byLifecycle["target-only"]).toBeDefined();
      expect(byLifecycle.disabled).toBeDefined();
    });

    it("P5-01: includes new lifecycle values", () => {
      const byLifecycle = getRouteSurfacesByLifecycle();

      expect(Object.keys(byLifecycle)).toContain("user-ready");
      expect(Object.keys(byLifecycle)).toContain("operator-preview");
      expect(Object.keys(byLifecycle)).toContain("internal-preview");
      expect(Object.keys(byLifecycle)).toContain("target-only");
      expect(Object.keys(byLifecycle)).toContain("disabled");
    });

    it("categorizes routes into correct lifecycle buckets", () => {
      const byLifecycle = getRouteSurfacesByLifecycle();

      // Context should be in target-only
      expect(byLifecycle["target-only"]).toContainEqual(
        expect.objectContaining({ path: "/context" })
      );

      // Memory should be in operator-preview
      expect(byLifecycle["operator-preview"]).toContainEqual(
        expect.objectContaining({ path: "/memory" })
      );

      // Home should be in active
      expect(byLifecycle.active).toContainEqual(
        expect.objectContaining({ path: "/" })
      );
    });
  });

  describe("getRouteSurfaceByPath", () => {
    it("finds route by path", () => {
      const homeRoute = getRouteSurfaceByPath("/");
      expect(homeRoute).toBeDefined();
      expect(homeRoute?.path).toBe("/");
    });

    it("returns undefined for unknown path", () => {
      const unknownRoute = getRouteSurfaceByPath("/unknown-path");
      expect(unknownRoute).toBeUndefined();
    });
  });

});
