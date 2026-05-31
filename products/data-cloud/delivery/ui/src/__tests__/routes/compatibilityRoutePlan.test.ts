import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const routesSource = readFileSync(
  path.resolve(__dirname, "../../routes.tsx"),
  "utf8",
);
const deprecationPlan = readFileSync(
  path.resolve(
    __dirname,
    "../../../docs/COMPATIBILITY_ROUTE_DEPRECATION_PLAN.md",
  ),
  "utf8",
);

function findPathIndex(routePath: string): number {
  const doubleQuoted = routesSource.indexOf(`path: "${routePath}"`);
  if (doubleQuoted >= 0) {
    return doubleQuoted;
  }
  return routesSource.indexOf(`path: '${routePath}'`);
}

function routeSourceWindow(routePath: string, windowSize = 700): string {
  const pathIndex = findPathIndex(routePath);
  expect(pathIndex, `Route '${routePath}' must be declared`).toBeGreaterThan(
    -1,
  );
  return routesSource.slice(pathIndex, pathIndex + windowSize);
}

function routeSourceBlock(routePath: string): string {
  const pathIndex = findPathIndex(routePath);
  expect(pathIndex, `Route '${routePath}' must be declared`).toBeGreaterThan(
    -1,
  );
  const blockEnd = routesSource.indexOf("\n      },", pathIndex);
  expect(
    blockEnd,
    `Route '${routePath}' must close its object block`,
  ).toBeGreaterThan(pathIndex);
  return routesSource.slice(pathIndex, blockEnd);
}

describe("compatibility route deprecation plan", () => {
  it("keeps canonical alias inventory aligned with declared compatibility routes", () => {
    const expectedAliases = [
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

    for (const alias of expectedAliases) {
      expect(findPathIndex(alias)).toBeGreaterThan(-1);
      expect(deprecationPlan).toContain(`/${alias}`);
    }
  });

  it("documents measurable retirement gates before alias removal", () => {
    expect(deprecationPlan).toContain("Retirement Gates");
    expect(deprecationPlan).toContain("rolling 30-day window");
    expect(deprecationPlan).toContain("near-zero");
    expect(deprecationPlan).toContain("explicit 410 responses");
  });

  it("wraps every non-redirect compatibility alias with RoleProtectedRoute", () => {
    // Aliases that are Navigate redirects — no RoleProtectedRoute needed.
    const navigateAliases = ["lineage", "quality"];

    // All other compat aliases that render page components must be wrapped.
    const protectedAliases = [
      "dashboard",
      "hub",
      "collections",
      "collections/new",
      "collections/:id",
      "collections/:id/edit",
      "datasets",
      "workflows",
      "workflows/new",
      "workflows/:id",
      "sql",
      "governance",
      "brain",
      "dashboards",
      "cost",
    ] as const;

    for (const alias of navigateAliases) {
      // Navigate aliases should NOT use RoleProtectedRoute — they just redirect.
      const routeBlock = routeSourceBlock(alias);
      expect(routeBlock).toContain("<Navigate");
      expect(routeBlock).not.toContain("RoleProtectedRoute");
    }

    for (const alias of protectedAliases) {
      const routeWindow = routeSourceWindow(alias);
      expect(
        routeWindow,
        `Compatibility alias '${alias}' missing RoleProtectedRoute — add routePath matching the canonical route`,
      ).toContain("RoleProtectedRoute");
    }
  });

  it("routes every page alias to a canonical routePath", () => {
    // Verify each class of aliases resolves to the expected canonical path.
    const canonicalMappings: Array<{ alias: string; canonicalPath: string }> = [
      { alias: "dashboard", canonicalPath: 'routePath="/"' },
      { alias: "hub", canonicalPath: 'routePath="/"' },
      { alias: "collections", canonicalPath: 'routePath="/data"' },
      { alias: "datasets", canonicalPath: 'routePath="/data"' },
      { alias: "workflows", canonicalPath: 'routePath="/pipelines"' },
      { alias: "sql", canonicalPath: 'routePath="/query"' },
      { alias: "governance", canonicalPath: 'routePath="/trust"' },
      { alias: "brain", canonicalPath: 'routePath="/insights"' },
      { alias: "cost", canonicalPath: 'routePath="/insights"' },
    ];

    for (const { alias, canonicalPath } of canonicalMappings) {
      const routeWindow = routeSourceWindow(alias);
      expect(
        routeWindow,
        `Alias '${alias}' must declare ${canonicalPath}`,
      ).toContain(canonicalPath);
    }
  });
});
