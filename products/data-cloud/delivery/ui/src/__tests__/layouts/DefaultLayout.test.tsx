import { describe, expect, it } from "vitest";

import { getNavigationSectionsForShellRole } from "../../layouts/DefaultLayout";
import type { SurfaceSignal } from "../../api/surfaces.service";

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
  const sections = getNavigationSectionsForShellRole(role, testSurfaces);
  return sections
    .flatMap((section) => section.items.map((item) => item.to))
    .sort();
}

const testSurfaces: readonly SurfaceSignal[] = [
  {
    key: "home",
    label: "Home",
    status: "LIVE",
    summary: "LIVE",
    ownerPlane: "shell",
    requiredDependencies: [],
    dependencyProbes: [],
    tenantScope: "global",
    runtimeProfile: "local",
    limitations: "",
    actionsAllowed: [],
    rawValue: "LIVE",
    path: "/",
    discoverable: true,
    minimumShellRole: "primary-user",
    sortOrder: 1,
    routeGroup: "core",
  },
  {
    key: "data",
    label: "Data",
    status: "LIVE",
    summary: "LIVE",
    ownerPlane: "data",
    requiredDependencies: [],
    dependencyProbes: [],
    tenantScope: "tenant",
    runtimeProfile: "local",
    limitations: "",
    actionsAllowed: [],
    rawValue: "LIVE",
    path: "/data",
    discoverable: true,
    minimumShellRole: "primary-user",
    sortOrder: 2,
    routeGroup: "core",
  },
  {
    key: "pipelines",
    label: "Pipelines",
    status: "LIVE",
    summary: "LIVE",
    ownerPlane: "action",
    requiredDependencies: [],
    dependencyProbes: [],
    tenantScope: "tenant",
    runtimeProfile: "local",
    limitations: "",
    actionsAllowed: [],
    rawValue: "LIVE",
    path: "/pipelines",
    discoverable: true,
    minimumShellRole: "primary-user",
    sortOrder: 3,
    routeGroup: "core",
  },
  {
    key: "query",
    label: "Query",
    status: "LIVE",
    summary: "LIVE",
    ownerPlane: "data",
    requiredDependencies: [],
    dependencyProbes: [],
    tenantScope: "tenant",
    runtimeProfile: "local",
    limitations: "",
    actionsAllowed: [],
    rawValue: "LIVE",
    path: "/query",
    discoverable: true,
    minimumShellRole: "primary-user",
    sortOrder: 4,
    routeGroup: "core",
  },
  {
    key: "events",
    label: "Events",
    status: "LIVE",
    summary: "LIVE",
    ownerPlane: "event",
    requiredDependencies: [],
    dependencyProbes: [],
    tenantScope: "tenant",
    runtimeProfile: "local",
    limitations: "",
    actionsAllowed: [],
    rawValue: "LIVE",
    path: "/events",
    discoverable: true,
    minimumShellRole: "operator",
    sortOrder: 5,
    routeGroup: "core",
  },
  {
    key: "trust",
    label: "Trust",
    status: "LIVE",
    summary: "LIVE",
    ownerPlane: "governance",
    requiredDependencies: [],
    dependencyProbes: [],
    tenantScope: "tenant",
    runtimeProfile: "local",
    limitations: "",
    actionsAllowed: [],
    rawValue: "LIVE",
    path: "/trust",
    discoverable: true,
    minimumShellRole: "operator",
    sortOrder: 6,
    routeGroup: "core",
  },
  {
    key: "operations",
    label: "Operations",
    status: "LIVE",
    summary: "LIVE",
    ownerPlane: "ops",
    requiredDependencies: [],
    dependencyProbes: [],
    tenantScope: "tenant",
    runtimeProfile: "local",
    limitations: "",
    actionsAllowed: [],
    rawValue: "LIVE",
    path: "/operations",
    discoverable: true,
    minimumShellRole: "admin",
    sortOrder: 7,
    routeGroup: "manage",
  },
  {
    key: "connectors",
    label: "Connectors",
    status: "PREVIEW",
    summary: "PREVIEW",
    ownerPlane: "data",
    requiredDependencies: [],
    dependencyProbes: [],
    tenantScope: "tenant",
    runtimeProfile: "local",
    limitations: "",
    actionsAllowed: [],
    rawValue: "PREVIEW",
    path: "/connectors",
    discoverable: false,
    minimumShellRole: "operator",
    audience: "operator",
    sortOrder: 8,
    routeGroup: "manage",
  },
];

function getExpectedPathsFromRegistry(
  role: "primary-user" | "operator" | "admin",
): string[] {
  const roleValue = role === "primary-user" ? 0 : role === "operator" ? 1 : 2;
  return testSurfaces
    .filter((surface) => {
      if (!surface.discoverable || !surface.path || !surface.minimumShellRole) {
        return false;
      }
      const minValue =
        surface.minimumShellRole === "primary-user"
          ? 0
          : surface.minimumShellRole === "operator"
            ? 1
            : 2;
      return roleValue >= minValue;
    })
    .map((surface) => surface.path as string)
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
