import { canonicalRouteSurfaceRegistry } from "@/lib/routing/RouteSurfaceRegistry";
import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const routesSource = readFileSync(
  path.resolve(__dirname, "../../routes.tsx"),
  "utf8",
);

const compatibilityAliasPaths = [
  "dashboard",
  "hub",
  "collections",
  "collections/new",
  "collections/:id",
  "collections/:id/edit",
  "datasets",
  "lineage",
  "quality",
  "workflows",
  "workflows/new",
  "workflows/:id",
  "sql",
  "governance",
  "brain",
  "dashboards",
  "cost",
] as const;

describe("route surface classification", () => {
  it("keeps preview surfaces hidden from discoverable navigation", () => {
    const previewKeys = [
      "alerts",
      "memory",
      "entities",
      "context",
      "fabric",
      "agents",
      "mediaArtifacts",
    ] as const;

    for (const key of previewKeys) {
      const route = canonicalRouteSurfaceRegistry[key];
      expect(route, `Missing preview route '${key}'`).toBeDefined();
      expect(route.lifecycle).toBe("preview");
      expect(route.discoverable).toBe(false);
      expect(route.minimumShellRole).toBe("operator");
    }
  });

  it("keeps boundary surfaces hidden from discoverable navigation", () => {
    const settingsRoute = canonicalRouteSurfaceRegistry.settings;
    expect(settingsRoute.lifecycle).toBe("boundary");
    expect(settingsRoute.discoverable).toBe(false);
    expect(settingsRoute.minimumShellRole).toBe("admin");
  });

  it("treats non-primary active operator/admin surfaces according to registry discoverability", () => {
    const discoverableActiveKeys = ["events", "operations"] as const;

    for (const key of discoverableActiveKeys) {
      const route = canonicalRouteSurfaceRegistry[key];
      expect(route, `Missing discoverable active route '${key}'`).toBeDefined();
      expect(route.lifecycle).toBe("active");
      expect(route.discoverable).toBe(true);
    }
  });

  it("keeps operations sub-routes hidden from main navigation (P5.2)", () => {
    const hiddenActiveKeys = [
      "connectors",
      "insights",
      "operationsJobs",
      "plugins",
    ] as const;

    for (const key of hiddenActiveKeys) {
      const route = canonicalRouteSurfaceRegistry[key];
      expect(route, `Missing hidden operations route '${key}'`).toBeDefined();
      expect(route.lifecycle).toBe("active");
      expect(route.discoverable).toBe(false);
    }

    const releaseTruthRoute =
      canonicalRouteSurfaceRegistry.operationsReleaseTruth;
    expect(releaseTruthRoute.lifecycle).toBe("boundary");
    expect(releaseTruthRoute.discoverable).toBe(false);
  });

  it("classifies compatibility aliases outside the canonical route surface registry", () => {
    const canonicalPaths = new Set(
      Object.values(canonicalRouteSurfaceRegistry).map((route) =>
        route.path.replace(/^\//, ""),
      ),
    );

    for (const aliasPath of compatibilityAliasPaths) {
      expect(
        canonicalPaths.has(aliasPath),
        `Compatibility alias '${aliasPath}' should not be promoted to canonical route surface`,
      ).toBe(false);
      expect(routesSource).toContain(`path: "${aliasPath}"`);
    }
  });

  it("keeps compatibility redirect aliases explicitly redirected to canonical views", () => {
    expect(routesSource).toContain('path: "lineage"');
    expect(routesSource).toContain('Navigate to="/data?view=lineage" replace');

    expect(routesSource).toContain('path: "quality"');
    expect(routesSource).toContain('Navigate to="/data?view=quality" replace');
  });
});
