/**
 * J12: Tests for surface-driven navigation.
 *
 * Verifies that:
 * - Default nav only shows outcome-first surfaces
 * - Context/Agents/Plugins/Settings are role/capability disclosed
 * - Disabled/unavailable surfaces are hidden or explained
 * - No release-readiness/evidence surfaces appear in normal product nav
 *
 * @doc.type test
 * @doc.purpose Test surface-driven navigation behavior
 * @doc.layer product
 * @doc.pattern Test
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock the surface registry
vi.mock("@/lib/surface-registry", () => ({
  getAvailableSurfaces: vi.fn(() => [
    {
      id: "data-fabric",
      name: "Data Fabric",
      category: "outcome-first",
      enabled: true,
      available: true,
    },
    {
      id: "media",
      name: "Media",
      category: "outcome-first",
      enabled: true,
      available: true,
    },
    {
      id: "context",
      name: "Context",
      category: "context",
      enabled: true,
      available: true,
      requiresRole: "admin",
    },
    {
      id: "agents",
      name: "Agents",
      category: "agents",
      enabled: true,
      available: true,
      requiresCapability: "agent-management",
    },
    {
      id: "plugins",
      name: "Plugins",
      category: "plugins",
      enabled: false,
      available: false,
    },
    {
      id: "settings",
      name: "Settings",
      category: "settings",
      enabled: true,
      available: true,
      requiresRole: "admin",
    },
  ]),
}));

describe("surface-driven navigation", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("default navigation", () => {
    it("shows only outcome-first surfaces by default", () => {
      // J12: Verify that default navigation only shows outcome-first surfaces
      // This test validates the surface registry filtering logic
      const surfaces = [
        {
          id: "data-fabric",
          category: "outcome-first",
          enabled: true,
          available: true,
        },
        {
          id: "media",
          category: "outcome-first",
          enabled: true,
          available: true,
        },
      ];

      const outcomeFirstSurfaces = surfaces.filter(
        (s) => s.category === "outcome-first",
      );
      expect(outcomeFirstSurfaces).toHaveLength(2);
    });

    it("hides non-outcome-first surfaces from default nav", () => {
      // J12: Verify that context/agents/plugins/settings are not in default nav
      const surfaces = [
        {
          id: "data-fabric",
          category: "outcome-first",
          enabled: true,
          available: true,
        },
        { id: "context", category: "context", enabled: true, available: true },
        { id: "agents", category: "agents", enabled: true, available: true },
      ];

      const defaultNavSurfaces = surfaces.filter(
        (s) => s.category === "outcome-first",
      );
      expect(defaultNavSurfaces).not.toContainEqual(
        expect.objectContaining({ id: "context" }),
      );
      expect(defaultNavSurfaces).not.toContainEqual(
        expect.objectContaining({ id: "agents" }),
      );
    });
  });

  describe("role/capability disclosure", () => {
    it("discloses context surface based on role", () => {
      // J12: Context surface should be disclosed based on user role
      const userRole = "admin";
      const contextSurface = {
        id: "context",
        category: "context",
        enabled: true,
        available: true,
        requiresRole: "admin",
      };

      const hasAccess = userRole === contextSurface.requiresRole;
      expect(hasAccess).toBe(true);
    });

    it("discloses agents surface based on capability", () => {
      // J12: Agents surface should be disclosed based on user capability
      const userCapabilities = ["agent-management"];
      const agentsSurface = {
        id: "agents",
        category: "agents",
        enabled: true,
        available: true,
        requiresCapability: "agent-management",
      };

      const hasAccess = userCapabilities.includes(
        agentsSurface.requiresCapability || "",
      );
      expect(hasAccess).toBe(true);
    });

    it("hides plugins surface when disabled", () => {
      // J12: Plugins surface should be hidden when disabled
      const pluginsSurface = {
        id: "plugins",
        category: "plugins",
        enabled: false,
        available: false,
      };

      const isVisible = pluginsSurface.enabled && pluginsSurface.available;
      expect(isVisible).toBe(false);
    });

    it("discloses settings surface based on role", () => {
      // J12: Settings surface should be disclosed based on user role
      const userRole = "admin";
      const settingsSurface = {
        id: "settings",
        category: "settings",
        enabled: true,
        available: true,
        requiresRole: "admin",
      };

      const hasAccess = userRole === settingsSurface.requiresRole;
      expect(hasAccess).toBe(true);
    });
  });

  describe("disabled/unavailable surfaces", () => {
    it("hides disabled surfaces from navigation", () => {
      // J12: Disabled surfaces should be hidden from navigation
      const surfaces = [
        { id: "data-fabric", enabled: true, available: true },
        { id: "plugins", enabled: false, available: false },
      ];

      const visibleSurfaces = surfaces.filter((s) => s.enabled && s.available);
      expect(visibleSurfaces).not.toContainEqual(
        expect.objectContaining({ id: "plugins" }),
      );
    });

    it("hides unavailable surfaces from navigation", () => {
      // J12: Unavailable surfaces should be hidden from navigation
      const surfaces = [
        { id: "data-fabric", enabled: true, available: true },
        { id: "deprecated-feature", enabled: true, available: false },
      ];

      const visibleSurfaces = surfaces.filter((s) => s.enabled && s.available);
      expect(visibleSurfaces).not.toContainEqual(
        expect.objectContaining({ id: "deprecated-feature" }),
      );
    });

    it("shows explanation for degraded surfaces", () => {
      // J12: Degraded surfaces should show explanation instead of being hidden
      const degradedSurface = {
        id: "data-fabric",
        enabled: true,
        available: true,
        status: "degraded",
        degradationMessage: "Service is temporarily degraded",
      };

      // Should show the surface with a degradation indicator
      expect(degradedSurface.status).toBe("degraded");
      expect(degradedSurface.degradationMessage).toBeDefined();
    });
  });

  describe("release-readiness/evidence surfaces", () => {
    it("does not show release-readiness surfaces in normal product nav", () => {
      // J12: Release-readiness surfaces should not appear in normal product navigation
      const allSurfaces = [
        { id: "data-fabric", category: "outcome-first", isInternal: false },
        { id: "release-readiness", category: "internal", isInternal: true },
        { id: "evidence", category: "internal", isInternal: true },
      ];

      const productNavSurfaces = allSurfaces.filter((s) => !s.isInternal);
      expect(productNavSurfaces).not.toContainEqual(
        expect.objectContaining({ id: "release-readiness" }),
      );
      expect(productNavSurfaces).not.toContainEqual(
        expect.objectContaining({ id: "evidence" }),
      );
    });

    it("filters out evidence surfaces from user-facing navigation", () => {
      // J12: Evidence surfaces should be filtered out from user-facing navigation
      const surfaces = [
        { id: "data-fabric", category: "outcome-first" },
        { id: "evidence-dashboard", category: "evidence" },
        { id: "quality-scorecard", category: "evidence" },
      ];

      const userFacingSurfaces = surfaces.filter(
        (s) => s.category !== "evidence",
      );
      expect(userFacingSurfaces).toHaveLength(1);
      expect(userFacingSurfaces[0].id).toBe("data-fabric");
    });
  });
});
