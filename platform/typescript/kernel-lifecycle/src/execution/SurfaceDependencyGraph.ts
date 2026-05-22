/**
 * Surface dependency graph for affected surface analysis.
 *
 * Builds a dependency graph of product surfaces and determines which surfaces
 * are affected by changes to other surfaces using transitive dependency analysis.
 *
 * @doc.type class
 * @doc.purpose Build and analyze surface dependency graphs for affected surface execution
 * @doc.layer kernel-lifecycle
 * @doc.pattern GraphAlgorithm
 */

export interface SurfaceDependency {
  readonly surfaceId: string;
  readonly dependsOn: readonly string[];
}

export interface AffectedSurfaceResult {
  readonly affectedSurfaces: readonly string[];
  readonly transitiveDependencies: Map<string, readonly string[]>;
  readonly changeRoot: string;
}

/**
 * Analyzes surface dependencies to determine affected surfaces.
 */
export class SurfaceDependencyGraph {
  private readonly adjacencyList: Map<string, Set<string>> = new Map();
  private readonly reverseAdjacencyList: Map<string, Set<string>> = new Map();

  /**
   * Adds a surface to the dependency graph.
   *
   * @param surface the surface dependency information
   */
  addSurface(surface: SurfaceDependency): void {
    const deps = new Set(surface.dependsOn);
    this.adjacencyList.set(surface.surfaceId, deps);

    // Build reverse adjacency for affected surface computation
    for (const dep of surface.dependsOn) {
      if (!this.reverseAdjacencyList.has(dep)) {
        this.reverseAdjacencyList.set(dep, new Set());
      }
      this.reverseAdjacencyList.get(dep)!.add(surface.surfaceId);
    }

    // Ensure all surfaces have entries in reverse adjacency
    if (!this.reverseAdjacencyList.has(surface.surfaceId)) {
      this.reverseAdjacencyList.set(surface.surfaceId, new Set());
    }
  }

  /**
   * Computes all surfaces affected by a change to the given surface.
   *
   * @param changedSurface the surface that changed
   * @returns the affected surface result
   */
  computeAffectedSurfaces(changedSurface: string): AffectedSurfaceResult {
    const affected = new Set<string>();
    const visited = new Set<string>();
    const transitiveDeps = new Map<string, readonly string[]>();

    // BFS through reverse adjacency to find all dependents
    const queue: string[] = [changedSurface];
    const paths = new Map<string, string[]>([ [changedSurface, []] ]);

    while (queue.length > 0) {
      const current = queue.shift()!;
      if (visited.has(current)) continue;
      visited.add(current);

      const dependents = this.reverseAdjacencyList.get(current) ?? new Set();
      for (const dependent of dependents) {
        if (!visited.has(dependent)) {
          affected.add(dependent);
          queue.push(dependent);
          const currentPath = paths.get(current) ?? [];
          paths.set(dependent, [...currentPath, current]);
        }
      }
    }

    // Build transitive dependency map
    for (const surface of visited) {
      const path = paths.get(surface) ?? [];
      transitiveDeps.set(surface, path);
    }

    // Include the changed surface itself
    affected.add(changedSurface);

    return {
      affectedSurfaces: Array.from(affected),
      transitiveDependencies: transitiveDeps,
      changeRoot: changedSurface,
    };
  }

  /**
   * Computes all surfaces affected by changes to multiple surfaces.
   *
   * @param changedSurfaces the surfaces that changed
   * @returns the affected surface result
   */
  computeAffectedSurfacesFromMultiple(changedSurfaces: readonly string[]): AffectedSurfaceResult {
    const allAffected = new Set<string>();
    const allTransitiveDeps = new Map<string, readonly string[]>();
    const changeRoots = new Set(changedSurfaces);

    for (const changedSurface of changedSurfaces) {
      const result = this.computeAffectedSurfaces(changedSurface);
      result.affectedSurfaces.forEach((s) => allAffected.add(s));
      
      for (const [surface, deps] of result.transitiveDependencies) {
        const existing = allTransitiveDeps.get(surface) ?? [];
        allTransitiveDeps.set(surface, [...existing, ...deps]);
      }
    }

    return {
      affectedSurfaces: Array.from(allAffected),
      transitiveDependencies: allTransitiveDeps,
      changeRoot: Array.from(changeRoots).join(", "),
    };
  }

  /**
   * Validates the dependency graph for cycles.
   *
   * @returns true if the graph has no cycles
   */
  validateNoCycles(): boolean {
    const visited = new Set<string>();
    const recursionStack = new Set<string>();

    const hasCycle = (surface: string): boolean => {
      visited.add(surface);
      recursionStack.add(surface);

      const dependencies = this.adjacencyList.get(surface) ?? new Set();
      for (const dep of dependencies) {
        if (!visited.has(dep)) {
          if (hasCycle(dep)) return true;
        } else if (recursionStack.has(dep)) {
          return true;
        }
      }

      recursionStack.delete(surface);
      return false;
    };

    for (const surface of this.adjacencyList.keys()) {
      if (!visited.has(surface)) {
        if (hasCycle(surface)) return false;
      }
    }

    return true;
  }
}
