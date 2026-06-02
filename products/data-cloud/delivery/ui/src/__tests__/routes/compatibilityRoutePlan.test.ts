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

  it("converts compatibility aliases to redirect-only routes", () => {
    const redirectAliases = [
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

    for (const alias of redirectAliases) {
      const routeBlock = routeSourceBlock(alias);
      expect(routeBlock.includes("Navigate") || routeBlock.includes("Redirect")).toBe(true);
      expect(routeBlock).not.toContain("RoleProtectedRoute");
    }
  });

  it("routes every compatibility alias to a canonical redirect target", () => {
    const canonicalMappings: Array<{ alias: string; canonicalTarget: string }> = [
      { alias: "dashboard", canonicalTarget: 'to="/"' },
      { alias: "hub", canonicalTarget: 'to="/"' },
      { alias: "collections", canonicalTarget: 'to="/data"' },
      { alias: "collections/new", canonicalTarget: 'to="/data/new"' },
      { alias: "datasets", canonicalTarget: 'to="/data"' },
      { alias: "workflows", canonicalTarget: 'to="/pipelines"' },
      { alias: "workflows/new", canonicalTarget: 'to="/pipelines/new"' },
      { alias: "sql", canonicalTarget: 'to="/query"' },
      { alias: "governance", canonicalTarget: 'to="/trust"' },
      { alias: "brain", canonicalTarget: 'to="/insights"' },
      { alias: "cost", canonicalTarget: 'to="/insights"' },
    ];

    for (const { alias, canonicalTarget } of canonicalMappings) {
      const routeWindow = routeSourceWindow(alias);
      expect(
        routeWindow,
        `Alias '${alias}' must declare ${canonicalTarget}`,
      ).toContain(canonicalTarget);
    }
  });
});
