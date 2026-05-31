import { describe, expect, it } from "vitest";

import { getNavigationSectionsForShellRole } from "../../layouts/DefaultLayout";
import { getDiscoverableRouteSurfaces } from "../../lib/routing/RouteSurfaceRegistry";

const NAV_SURFACE_PATHS = new Set([
  "/",
  "/data",
  "/events",
  "/pipelines",
  "/query",
  "/trust",
  "/operations",
]);

function getSectionPaths(
  role: "primary-user" | "operator" | "admin",
): string[] {
  const sections = getNavigationSectionsForShellRole(role);
  return sections
    .flatMap((section) => section.items.map((item) => item.to))
    .sort();
}

function getExpectedPathsFromRegistry(
  role: "primary-user" | "operator" | "admin",
): string[] {
  return getDiscoverableRouteSurfaces(role)
    .map((route) => route.path)
    .filter((path) => NAV_SURFACE_PATHS.has(path))
    .sort();
}

describe("DefaultLayout navigation progressive disclosure", () => {
  it("matches registry discoverability for primary-user shell", () => {
    const paths = getSectionPaths("primary-user");

    expect(paths).toEqual(getExpectedPathsFromRegistry("primary-user"));
    expect(paths).not.toContain("/operations");
  });

  it("matches registry discoverability for operator shell", () => {
    const paths = getSectionPaths("operator");

    expect(paths).toEqual(getExpectedPathsFromRegistry("operator"));
    expect(paths).toContain("/trust");
    expect(paths).not.toContain("/connectors");
    expect(paths).not.toContain("/operations");
  });

  it("matches registry discoverability for admin shell", () => {
    const paths = getSectionPaths("admin");

    expect(paths).toEqual(getExpectedPathsFromRegistry("admin"));
    expect(paths).toContain("/operations");
    expect(paths).not.toContain("/settings");
  });

  it("keeps advanced and preview surfaces out of primary navigation", () => {
    const paths = getSectionPaths("admin");

    const advancedHiddenPaths = [
      "/connectors",
      "/alerts",
      "/memory",
      "/entities",
      "/context",
      "/fabric",
      "/agents",
      "/media/artifacts",
      "/plugins",
      "/operations/jobs",
      "/operations/release-truth",
      "/settings",
    ];

    for (const hiddenPath of advancedHiddenPaths) {
      expect(paths).not.toContain(hiddenPath);
    }
  });
});
