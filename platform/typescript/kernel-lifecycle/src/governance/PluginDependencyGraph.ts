/**
 * PluginDependencyGraph - tracks plugin dependencies and detects cycles.
 *
 * This component maintains a dependency graph of plugins and detects circular
 * dependencies that could cause deadlocks or infinite loops during execution.
 *
 * @doc.type class
 * @doc.purpose Track plugin dependencies and detect cycles
 * @doc.layer kernel-lifecycle
 * @doc.pattern Service
 */

/**
 * Dependency edge in the plugin graph.
 */
interface DependencyEdge {
  readonly from: string;
  readonly to: string;
  readonly kind: "requires" | "suggests" | "conflicts";
}

/**
 * Plugin dependency information.
 */
export interface PluginDependencyInfo {
  readonly pluginId: string;
  readonly dependencies: readonly string[];
  readonly dependents: readonly string[];
  readonly depth: number;
}

/**
 * Cycle detection result.
 */
export interface CycleDetectionResult {
  readonly hasCycles: boolean;
  readonly cycles: readonly string[][];
  readonly criticalPath: readonly string[];
}

/**
 * Plugin dependency graph options.
 */
export interface PluginDependencyGraphOptions {
  /**
   * Whether to treat suggestions as hard dependencies.
   */
  readonly treatSuggestionsAsHard?: boolean;
}

/**
 * Plugin dependency graph with cycle detection.
 */
export class PluginDependencyGraph {
  private readonly adjacencyList = new Map<string, Set<string>>();
  private readonly reverseAdjacencyList = new Map<string, Set<string>>();
  private readonly edgeKinds = new Map<string, string>(); // "from->to" -> kind
  private readonly treatSuggestionsAsHard: boolean;

  constructor(options: PluginDependencyGraphOptions = {}) {
    this.treatSuggestionsAsHard = options.treatSuggestionsAsHard ?? false;
  }

  /**
   * Add a plugin to the graph.
   */
  addPlugin(pluginId: string): void {
    if (!this.adjacencyList.has(pluginId)) {
      this.adjacencyList.set(pluginId, new Set());
    }
    if (!this.reverseAdjacencyList.has(pluginId)) {
      this.reverseAdjacencyList.set(pluginId, new Set());
    }
  }

  /**
   * Add a dependency edge between plugins.
   */
  addDependency(
    from: string,
    to: string,
    kind: "requires" | "suggests" | "conflicts" = "requires"
  ): void {
    // Skip suggestions if not treating as hard dependencies
    if (kind === "suggests" && !this.treatSuggestionsAsHard) {
      return;
    }

    // Skip conflicts (they don't create execution dependencies)
    if (kind === "conflicts") {
      return;
    }

    this.addPlugin(from);
    this.addPlugin(to);

    this.adjacencyList.get(from)!.add(to);
    this.reverseAdjacencyList.get(to)!.add(from);
    this.edgeKinds.set(`${from}->${to}`, kind);
  }

  /**
   * Remove a dependency edge.
   */
  removeDependency(from: string, to: string): void {
    this.adjacencyList.get(from)?.delete(to);
    this.reverseAdjacencyList.get(to)?.delete(from);
    this.edgeKinds.delete(`${from}->${to}`);
  }

  /**
   * Get all dependencies for a plugin.
   */
  getDependencies(pluginId: string): readonly string[] {
    return Array.from(this.adjacencyList.get(pluginId) ?? []);
  }

  /**
   * Get all dependents (plugins that depend on this one).
   */
  getDependents(pluginId: string): readonly string[] {
    return Array.from(this.reverseAdjacencyList.get(pluginId) ?? []);
  }

  /**
   * Get dependency information for a plugin.
   */
  getDependencyInfo(pluginId: string): PluginDependencyInfo | null {
    if (!this.adjacencyList.has(pluginId)) {
      return null;
    }

    const dependencies = this.getDependencies(pluginId);
    const dependents = this.getDependents(pluginId);
    const depth = this.calculateDepth(pluginId);

    return {
      pluginId,
      dependencies,
      dependents,
      depth,
    };
  }

  /**
   * Calculate the depth of a plugin in the dependency graph.
   */
  private calculateDepth(pluginId: string, visited = new Set<string>()): number {
    if (visited.has(pluginId)) {
      return 0; // Cycle detected, return 0 to avoid infinite recursion
    }
    visited.add(pluginId);

    const dependencies = this.getDependencies(pluginId);
    if (dependencies.length === 0) {
      return 0;
    }

    let maxDepth = 0;
    for (const dep of dependencies) {
      const depDepth = this.calculateDepth(dep, new Set(visited));
      maxDepth = Math.max(maxDepth, depDepth + 1);
    }

    return maxDepth;
  }

  /**
   * Detect cycles in the dependency graph using DFS.
   */
  detectCycles(): CycleDetectionResult {
    const cycles: string[][] = [];
    const visited = new Set<string>();
    const recursionStack = new Set<string>();

    const dfs = (pluginId: string, path: string[]): void => {
      visited.add(pluginId);
      recursionStack.add(pluginId);
      path.push(pluginId);

      const dependencies = this.getDependencies(pluginId);
      for (const dep of dependencies) {
        if (!visited.has(dep)) {
          dfs(dep, [...path]);
        } else if (recursionStack.has(dep)) {
          // Found a cycle
          const cycleStart = path.indexOf(dep);
          const cycle = path.slice(cycleStart);
          cycle.push(dep); // Close the cycle
          cycles.push(cycle);
        }
      }

      recursionStack.delete(pluginId);
    };

    for (const pluginId of this.adjacencyList.keys()) {
      if (!visited.has(pluginId)) {
        dfs(pluginId, []);
      }
    }

    const criticalPath = this.findCriticalPath();

    return {
      hasCycles: cycles.length > 0,
      cycles,
      criticalPath,
    };
  }

  /**
   * Find the critical path (longest dependency chain) in the graph.
   */
  private findCriticalPath(): string[] {
    let longestPath: string[] = [];
    const visited = new Set<string>();

    const dfs = (pluginId: string, path: string[]): void => {
      if (visited.has(pluginId)) {
        return;
      }
      visited.add(pluginId);
      path.push(pluginId);

      const dependencies = this.getDependencies(pluginId);
      if (dependencies.length === 0) {
        if (path.length > longestPath.length) {
          longestPath = [...path];
        }
      } else {
        for (const dep of dependencies) {
          dfs(dep, [...path]);
        }
      }
    };

    for (const pluginId of this.adjacencyList.keys()) {
      dfs(pluginId, []);
    }

    return longestPath;
  }

  /**
   * Get topological order of plugins (for execution planning).
   * Returns null if cycles are detected.
   */
  getTopologicalOrder(): string[] | null {
    const cycleResult = this.detectCycles();
    if (cycleResult.hasCycles) {
      return null;
    }

    const order: string[] = [];
    const visited = new Set<string>();
    const tempVisited = new Set<string>();

    const visit = (pluginId: string): void => {
      if (tempVisited.has(pluginId)) {
        return; // Should not happen if no cycles
      }
      if (visited.has(pluginId)) {
        return;
      }

      tempVisited.add(pluginId);

      const dependencies = this.getDependencies(pluginId);
      for (const dep of dependencies) {
        visit(dep);
      }

      tempVisited.delete(pluginId);
      visited.add(pluginId);
      order.push(pluginId);
    };

    for (const pluginId of this.adjacencyList.keys()) {
      if (!visited.has(pluginId)) {
        visit(pluginId);
      }
    }

    return order.reverse();
  }

  /**
   * Get all edges in the graph.
   */
  getEdges(): readonly DependencyEdge[] {
    const edges: DependencyEdge[] = [];
    for (const [from, deps] of this.adjacencyList.entries()) {
      for (const to of deps) {
        const kind = this.edgeKinds.get(`${from}->${to}`) ?? "requires";
        edges.push({ from, to, kind: kind as "requires" | "suggests" | "conflicts" });
      }
    }
    return edges;
  }

  /**
   * Clear the graph.
   */
  clear(): void {
    this.adjacencyList.clear();
    this.reverseAdjacencyList.clear();
    this.edgeKinds.clear();
  }

  /**
   * Get graph statistics.
   */
  getStats(): {
    readonly nodeCount: number;
    readonly edgeCount: number;
    readonly averageDegree: number;
    readonly maxDepth: number;
  } {
    const nodeCount = this.adjacencyList.size;
    let edgeCount = 0;
    let maxDepth = 0;

    for (const [pluginId, deps] of this.adjacencyList.entries()) {
      edgeCount += deps.size;
      const depth = this.calculateDepth(pluginId);
      maxDepth = Math.max(maxDepth, depth);
    }

    const averageDegree = nodeCount > 0 ? edgeCount / nodeCount : 0;

    return {
      nodeCount,
      edgeCount,
      averageDegree,
      maxDepth,
    };
  }
}
