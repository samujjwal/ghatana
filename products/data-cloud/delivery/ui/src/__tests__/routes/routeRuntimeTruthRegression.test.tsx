import { describe, expect, it } from "vitest";
import type { RouteObject } from "react-router";
import { routes } from "../../routes";

function collectPaths(routeNodes: readonly RouteObject[], parent = ""): string[] {
  const paths: string[] = [];

  for (const node of routeNodes) {
    if (node.path) {
      const fullPath = node.path.startsWith("/")
        ? node.path
        : `${parent}/${node.path}`.replace(/\/+/g, "/");
      paths.push(fullPath);
      if (node.children?.length) {
        paths.push(...collectPaths(node.children, fullPath));
      }
      continue;
    }

    if (node.children?.length) {
      paths.push(...collectPaths(node.children, parent));
    }
  }

  return paths;
}

describe("Route runtime-truth regressions", () => {
  it("does not define duplicate route paths", () => {
    const allPaths = collectPaths(routes);
    const duplicates = allPaths.filter(
      (path, index) => allPaths.indexOf(path) !== index,
    );

    expect(duplicates).toEqual([]);
  });

  it("keeps workflow compatibility paths mapped to pipeline routes", () => {
    const allPaths = collectPaths(routes);

    expect(allPaths).toContain("/workflows");
    expect(allPaths).toContain("/workflows/new");
    expect(allPaths).toContain("/workflows/:id");

    expect(allPaths).toContain("/pipelines");
    expect(allPaths).toContain("/pipelines/new");
    expect(allPaths).toContain("/pipelines/:id");
  });
});
