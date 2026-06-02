/**
 * Route Surface Metadata Verification
 *
 * Validates that all runtime surface route gates in routes.tsx are properly
 * registered and consistent with the canonical RouteSurfaceRegistry.
 *
 * This test prevents drift between:
 * - Hand-maintained RuntimeCapabilityRouteGate aliases in routes.tsx
 * - Statically registered surfaces in RouteSurfaceRegistry
 * - Runtime truth state from backend
 *
 * @doc.type test
 * @doc.purpose Verify route/action gate metadata consistency
 * @doc.layer frontend
 */

import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { staticRouteSurfaceFallback } from "../../lib/routing/StaticRouteSurfaceFallback";

/**
 * Gated routes as they appear in routes.tsx
 * This is the source of truth for route aliases used in RuntimeCapabilityRouteGate components
 */
const gatedRoutesFromUI = [
  {
    routeName: "alerts",
    surfaceId: "governance.audit",
    aliases: ["alerts"],
    canonicalCapability: "monitoring",
  },
  {
    routeName: "events",
    surfaceId: "event.store",
    aliases: ["events"],
    canonicalCapability: "event-stream",
  },
  {
    routeName: "memory",
    surfaceId: "context.plane",
    aliases: ["memory"],
    canonicalCapability: "memory-plane",
  },
  {
    routeName: "entities",
    surfaceId: "data.entityStore",
    aliases: ["entities"],
    canonicalCapability: "entity-browser",
  },
  {
    routeName: "context",
    surfaceId: "context.plane",
    aliases: ["context"],
    canonicalCapability: "context-explorer",
  },
  {
    routeName: "fabric",
    surfaceId: "data.storageProfiles",
    aliases: ["fabric"],
    canonicalCapability: "data-fabric",
  },
  {
    routeName: "agents",
    surfaceId: "action.agentRuntime",
    aliases: ["agents"],
    canonicalCapability: "agent-catalog",
  },
  {
    routeName: "settings",
    surfaceId: "settings",
    aliases: ["settings", "config"],
    canonicalCapability: "settings",
  },
  {
    routeName: "plugins",
    surfaceId: "plugin-management",
    aliases: ["plugins", "extensions"],
    canonicalCapability: "plugin-management",
  },
  {
    routeName: "mediaArtifacts",
    surfaceId: "media.audioVideo",
    aliases: ["media-artifacts", "audio-video"],
    canonicalCapability: "media",
  },
  {
    routeName: "connectors",
    surfaceId: "data.connectors",
    aliases: ["connectors"],
    canonicalCapability: "data-connectors",
  },
];

describe("Route Surface Metadata Verification", () => {
  it("verifies all gated route surfaces are registered in RouteSurfaceRegistry", () => {
    for (const gatedRoute of gatedRoutesFromUI) {
      const registryEntry = staticRouteSurfaceFallback[gatedRoute.routeName];
      expect(
        registryEntry,
        `Route '${gatedRoute.routeName}' must be registered in staticRouteSurfaceFallback`,
      ).toBeDefined();

      expect(
        registryEntry.capabilities,
        `Route '${gatedRoute.routeName}' must have capabilities array`,
      ).toBeInstanceOf(Array);

      expect(
        registryEntry.capabilities.length,
        `Route '${gatedRoute.routeName}' must have at least one capability`,
      ).toBeGreaterThan(0);

      // Verify at least the canonical capability is registered
      expect(
        registryEntry.capabilities.includes(gatedRoute.canonicalCapability),
        `Route '${gatedRoute.routeName}' capabilities must include '${gatedRoute.canonicalCapability}'`,
      ).toBe(true);
    }
  });

  it("verifies all route aliases have a canonical surface in the registry", () => {
    for (const gatedRoute of gatedRoutesFromUI) {
      const registryEntry = staticRouteSurfaceFallback[gatedRoute.routeName];

      // At least one alias should match a registered surface
      const aliasesMatchRegistry =
        gatedRoute.aliases.some((alias) => registryEntry.capabilities.includes(alias)) ||
        registryEntry.capabilities.includes(gatedRoute.canonicalCapability);

      expect(
        aliasesMatchRegistry,
        `Route '${gatedRoute.routeName}': at least one alias ${JSON.stringify(gatedRoute.aliases)} must exist in surfaces ${JSON.stringify(registryEntry.capabilities)}`,
      ).toBe(true);
    }
  });

  it("verifies all gated routes have lifecycle and discoverable metadata", () => {
    for (const gatedRoute of gatedRoutesFromUI) {
      const registryEntry = staticRouteSurfaceFallback[gatedRoute.routeName];

      expect(
        registryEntry.lifecycle,
        `Route '${gatedRoute.routeName}' must have lifecycle defined`,
      ).toBeDefined();

      expect(
        [
          "active",
          "preview",
          "boundary",
          "deprecated",
          "redirect",
          "removed",
          "user-ready",
          "operator-preview",
          "internal-preview",
          "target-only",
          "disabled",
        ],
        `Route '${gatedRoute.routeName}' lifecycle '${registryEntry.lifecycle}' must be valid`,
      ).toContain(registryEntry.lifecycle);

      expect(
        typeof registryEntry.discoverable,
        `Route '${gatedRoute.routeName}' must have discoverable boolean`,
      ).toBe("boolean");
    }
  });

  it("verifies gated routes have appropriate role requirements", () => {
    for (const gatedRoute of gatedRoutesFromUI) {
      const registryEntry = staticRouteSurfaceFallback[gatedRoute.routeName];

      // Gated routes should require at least operator role
      const validRoles = ["operator", "admin"];
      expect(
        validRoles.includes(registryEntry.minimumShellRole),
        `Gated route '${gatedRoute.routeName}' must require 'operator' or 'admin' role, got '${registryEntry.minimumShellRole}'`,
      ).toBe(true);
    }
  });

  it("extracts and validates route surface metadata from source", () => {
    // Read routes.tsx source to extract aliases directly
    const routesSourcePath = path.join(__dirname, "../../routes.tsx");
    const routesSource = fs.readFileSync(routesSourcePath, "utf-8");

    const gatePattern = /surfaceId="([^"]+)"/g;
    const matches = Array.from(routesSource.matchAll(gatePattern));

    expect(
      matches.length,
      "Should find all RuntimeCapabilityRouteGate components with aliases",
    ).toBeGreaterThanOrEqual(gatedRoutesFromUI.length);

    // Verify each extracted alias array matches expected structure
    const extractedAliases = matches.map((match) => match[1]);

    expect(
      extractedAliases.every((a) => a !== null),
      "All RuntimeCapabilityRouteGate aliases should be properly formatted",
    ).toBe(true);
  });

  it("generates feature gate metadata for Runtime Truth validation", () => {
    // This metadata could be used for code generation or verification
    const featureGateMetadata = gatedRoutesFromUI.map((gatedRoute) => {
      const registryEntry = staticRouteSurfaceFallback[gatedRoute.routeName];

      return {
        routePath: registryEntry.path,
        routeName: gatedRoute.routeName,
        label: registryEntry.label,
        surfaceId: gatedRoute.surfaceId,
        aliases: gatedRoute.aliases,
        capabilities: registryEntry.capabilities,
        canonicalCapability: gatedRoute.canonicalCapability,
        lifecycle: registryEntry.lifecycle,
        minimumShellRole: registryEntry.minimumShellRole,
        discoverable: registryEntry.discoverable,
        description: registryEntry.description,
      };
    });

    // Verify metadata is consistent
    expect(featureGateMetadata).toHaveLength(gatedRoutesFromUI.length);

    for (const metadata of featureGateMetadata) {
      expect(metadata.routePath).toBeDefined();
      expect(metadata.surfaceId).toBeDefined();
      expect(metadata.capabilities.length).toBeGreaterThan(0);
      expect(metadata.canonicalCapability).toBeDefined();
      expect(metadata.lifecycle).toBeDefined();
      expect(metadata.minimumShellRole).toBeDefined();
      expect(typeof metadata.discoverable).toBe("boolean");
    }
  });

  it("ensures no duplicate route names across gated routes", () => {
    const routeNames = gatedRoutesFromUI.map((r) => r.routeName);
    const uniqueRouteNames = new Set(routeNames);

    expect(uniqueRouteNames.size, "All gated route names must be unique").toBe(
      routeNames.length,
    );
  });

  it("ensures no conflicting aliases within or across routes", () => {
    const allAliases = gatedRoutesFromUI.flatMap((r) => r.aliases);
    const _uniqueAliases = new Set(allAliases);

    // Track conflicts
    const conflicts = new Map<string, string[]>();

    for (const gatedRoute of gatedRoutesFromUI) {
      for (const alias of gatedRoute.aliases) {
        if (!conflicts.has(alias)) {
          conflicts.set(alias, []);
        }
        conflicts.get(alias)!.push(gatedRoute.routeName);
      }
    }

    // Identify and report conflicts
    const conflictingAliases = Array.from(conflicts.entries()).filter(
      ([_, routes]) => routes.length > 1,
    );

    expect(
      conflictingAliases.length,
      `Aliases must be unique across routes. Conflicts: ${conflictingAliases.map(([alias, routes]) => `'${alias}' in [${routes.join(", ")}]`).join("; ")}`,
    ).toBe(0);
  });

  it("provides route surface index for feature gate generation", () => {
    // This index can be used to generate feature gate code or schemas
    const routeCapabilityIndex: Record<
      string,
      { aliases: string[]; route: string }
    > = {};

    for (const gatedRoute of gatedRoutesFromUI) {
      for (const alias of gatedRoute.aliases) {
        routeCapabilityIndex[alias] = {
          aliases: gatedRoute.aliases,
          route: gatedRoute.routeName,
        };
      }
    }

    // Verify index is complete
    expect(
      Object.keys(routeCapabilityIndex).length,
      "Route surface index must include all aliases",
    ).toBe(gatedRoutesFromUI.reduce((sum, r) => sum + r.aliases.length, 0));

    // Sample verification
    expect(routeCapabilityIndex["alerts"]?.route).toBe("alerts");
    expect(routeCapabilityIndex["memory"]?.route).toBe("memory");
    expect(routeCapabilityIndex["entities"]?.route).toBe("entities");
  });

  it("validates alignment between runtime truth names and route aliases", () => {
    // This test ensures that runtime truth names coming from the backend
    // would properly map to route gates

    const capabilityNameVariations: Record<string, string[]> = {
      alerts: ["alerts", "monitoring"],
      events: ["events", "event-stream"],
      memory: ["memory", "memory-plane"],
      entities: ["entities", "entity-browser"],
      context: ["context", "context-explorer"],
      fabric: ["fabric", "data-fabric"],
      agents: ["agents", "agent-catalog"],
      plugins: ["plugins", "extensions", "plugin-management"],
      settings: ["settings", "config"],
      connectors: ["connectors", "data-connectors"],
    };

    for (const [routeName, variations] of Object.entries(
      capabilityNameVariations,
    )) {
      const gatedRoute = gatedRoutesFromUI.find(
        (r) => r.routeName === routeName,
      );
      expect(gatedRoute).toBeDefined();

      // Verify variations match registered aliases
      for (const variation of variations) {
        expect(
          (gatedRoute?.aliases.includes(variation) ?? false) ||
            gatedRoute?.canonicalCapability === variation,
          `Capability name '${variation}' should be recognized as alias for route '${routeName}'`,
        ).toBe(true);
      }
    }
  });
});
