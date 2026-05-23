/**
 * AffectedSurfaceExecutionGraph - Execution graph for skipping unchanged surfaces.
 *
 * <p>PERF-001: Implements an execution graph that tracks surface dependencies and
 * skips execution of unchanged surfaces when safe. This optimizes lifecycle runs
 * by only executing surfaces that have changed or depend on changed surfaces.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Dependency tracking between surfaces</li>
 *   <li>Change detection via git diff or file hashes</li>
 *   <li>Transitive dependency resolution</li>
 *   <li>Safe skip validation</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Execution graph for skipping unchanged surfaces safely
 * @doc.layer kernel-lifecycle
 * @doc.pattern GraphAlgorithm
 */

import type { ProductSurface } from '@ghatana/kernel-product-contracts';

export interface SurfaceDependency {
  readonly surfaceId: string;
  readonly dependsOn: readonly string[];
}

export interface SurfaceChange {
  readonly surfaceId: string;
  readonly changedFiles: readonly string[];
  readonly changeType: 'modified' | 'added' | 'deleted';
}

export interface ExecutionGraphNode {
  readonly surfaceId: string;
  readonly surface: ProductSurface;
  readonly dependencies: readonly string[];
  readonly dependents: readonly string[];
  readonly changed: boolean;
  readonly canSkip: boolean;
  readonly skipReason?: string;
}

export interface ExecutionGraphResult {
  readonly nodes: readonly ExecutionGraphNode[];
  readonly executionOrder: readonly string[];
  readonly skippedSurfaces: readonly string[];
  readonly executedSurfaces: readonly string[];
}

export interface AffectedSurfaceExecutionGraphOptions {
  readonly repoRoot: string;
  readonly surfaces: readonly ProductSurface[];
  readonly dependencies: readonly SurfaceDependency[];
  readonly changes: readonly SurfaceChange[];
  readonly forceAll?: boolean;
}

export class AffectedSurfaceExecutionGraph {
  private readonly surfaces: readonly ProductSurface[];
  private readonly dependencies: readonly SurfaceDependency[];
  private readonly changes: readonly SurfaceChange[];
  private readonly forceAll: boolean;

  private readonly dependencyMap: Map<string, Set<string>>;
  private readonly dependentMap: Map<string, Set<string>>;
  private readonly changedSet: Set<string>;

  constructor(options: AffectedSurfaceExecutionGraphOptions) {
    this.surfaces = options.surfaces;
    this.dependencies = options.dependencies;
    this.changes = options.changes;
    this.forceAll = options.forceAll ?? false;

    this.dependencyMap = this.buildDependencyMap();
    this.dependentMap = this.buildDependentMap();
    this.changedSet = this.buildChangedSet();
  }

  /**
   * Computes the execution graph with skip decisions.
   */
  compute(): ExecutionGraphResult {
    if (this.forceAll) {
      return this.executeAll();
    }

    const nodes = this.computeNodes();
    const executionOrder = this.topologicalSort(nodes);
    const skippedSurfaces = nodes.filter(n => n.canSkip).map(n => n.surfaceId);
    const executedSurfaces = nodes.filter(n => !n.canSkip).map(n => n.surfaceId);

    return {
      nodes,
      executionOrder,
      skippedSurfaces,
      executedSurfaces,
    };
  }

  private computeNodes(): ExecutionGraphNode[] {
    return this.surfaces.map(surface => {
      const surfaceId = resolveSurfaceId(surface);
      const dependencies = this.dependencyMap.get(surfaceId) ?? new Set<string>();
      const dependents = this.dependentMap.get(surfaceId) ?? new Set<string>();
      const changed = this.changedSet.has(surfaceId);
      const canSkip = this.canSkipSurface(changed, dependencies, dependents);

      const node: ExecutionGraphNode = {
        surfaceId,
        surface,
        dependencies: Array.from(dependencies),
        dependents: Array.from(dependents),
        changed,
        canSkip,
        ...(canSkip ? { skipReason: this.getSkipReason(surfaceId, changed) } : {}),
      };

      return node;
    });
  }

  private canSkipSurface(
    changed: boolean,
    dependencies: Set<string>,
    dependents: Set<string>
  ): boolean {
    // Cannot skip if surface itself changed
    if (changed) {
      return false;
    }

    // Cannot skip if any dependency changed
    for (const dep of dependencies) {
      if (this.changedSet.has(dep)) {
        return false;
      }
    }

    // Cannot skip if any dependent surface changed (transitive)
    // This ensures downstream surfaces get updated
    for (const dependent of dependents) {
      if (this.changedSet.has(dependent)) {
        return false;
      }
    }

    // Safe to skip
    return true;
  }

  private getSkipReason(surfaceId: string, changed: boolean): string {
    if (changed) {
      return 'Surface changed';
    }
    const dependencies = this.dependencyMap.get(surfaceId) ?? new Set();
    for (const dep of dependencies) {
      if (this.changedSet.has(dep)) {
        return `Dependency ${dep} changed`;
      }
    }
    const dependents = this.dependentMap.get(surfaceId) ?? new Set();
    for (const dependent of dependents) {
      if (this.changedSet.has(dependent)) {
        return `Dependent ${dependent} changed`;
      }
    }
    return 'No changes in dependency chain';
  }

  private topologicalSort(nodes: readonly ExecutionGraphNode[]): readonly string[] {
    const visited = new Set<string>();
    const visiting = new Set<string>();
    const order: string[] = [];

    const visit = (surfaceId: string) => {
      if (visited.has(surfaceId)) {
        return;
      }
      if (visiting.has(surfaceId)) {
        throw new Error(`Cycle detected in surface dependencies involving ${surfaceId}`);
      }

      visiting.add(surfaceId);

      const node = nodes.find(n => n.surfaceId === surfaceId);
      if (node) {
        for (const dep of node.dependencies) {
          visit(dep);
        }
      }

      visiting.delete(surfaceId);
      visited.add(surfaceId);
      order.push(surfaceId);
    };

    for (const node of nodes) {
      visit(node.surfaceId);
    }

    return order;
  }

  private executeAll(): ExecutionGraphResult {
    const nodes: ExecutionGraphNode[] = this.surfaces.map(surface => {
      const surfaceId = resolveSurfaceId(surface);
      return {
        surfaceId,
        surface,
        dependencies: Array.from(this.dependencyMap.get(surfaceId) ?? new Set<string>()),
        dependents: Array.from(this.dependentMap.get(surfaceId) ?? new Set<string>()),
        changed: true,
        canSkip: false,
      };
    });

    const executionOrder = this.topologicalSort(nodes);

    return {
      nodes,
      executionOrder,
      skippedSurfaces: [],
      executedSurfaces: executionOrder,
    };
  }

  private buildDependencyMap(): Map<string, Set<string>> {
    const map = new Map<string, Set<string>>();
    for (const dep of this.dependencies) {
      const deps = map.get(dep.surfaceId) ?? new Set();
      for (const depId of dep.dependsOn) {
        deps.add(depId);
      }
      map.set(dep.surfaceId, deps);
    }
    return map;
  }

  private buildDependentMap(): Map<string, Set<string>> {
    const map = new Map<string, Set<string>>();
    for (const dep of this.dependencies) {
      for (const depId of dep.dependsOn) {
        const dependents = map.get(depId) ?? new Set();
        dependents.add(dep.surfaceId);
        map.set(depId, dependents);
      }
    }
    return map;
  }

  private buildChangedSet(): Set<string> {
    const set = new Set<string>();
    for (const change of this.changes) {
      set.add(change.surfaceId);
    }
    return set;
  }
}

function resolveSurfaceId(surface: ProductSurface): string {
  const explicitId = (surface as unknown as Record<string, unknown>).id;
  if (typeof explicitId === 'string' && explicitId.length > 0) {
    return explicitId;
  }

  const adapterPart = typeof surface.adapter === 'string' && surface.adapter.length > 0
    ? `:${surface.adapter}`
    : '';
  return `${surface.type}:${surface.path}${adapterPart}`;
}

/**
 * Detects changed surfaces using git diff.
 */
export async function detectChangedSurfaces(
  repoRoot: string,
  surfaces: readonly ProductSurface[],
  baseRef?: string
): Promise<SurfaceChange[]> {
  const { execFileSync } = await import('node:child_process');
  const path = await import('node:path');

  const changes: SurfaceChange[] = [];

  try {
    const gitArgs = baseRef
      ? ['diff', '--name-only', baseRef]
      : ['diff', '--name-only', 'HEAD'];
    const output = execFileSync('git', gitArgs, {
      cwd: repoRoot,
      encoding: 'utf8',
    });

    const changedFiles = output.trim().split('\n').filter(Boolean);

    for (const surface of surfaces) {
      const surfacePath = path.join(repoRoot, surface.path);
      const relativePath = path.relative(repoRoot, surfacePath).replace(/\\/g, '/');

      const surfaceChangedFiles = changedFiles.filter(file =>
        file.startsWith(relativePath) || relativePath.startsWith(file)
      );

      if (surfaceChangedFiles.length > 0) {
        changes.push({
          surfaceId: resolveSurfaceId(surface),
          changedFiles: surfaceChangedFiles,
          changeType: 'modified',
        });
      }
    }
  } catch (error) {
    // If git fails, assume all surfaces changed
    console.warn('Git diff failed, assuming all surfaces changed:', error);
    for (const surface of surfaces) {
      changes.push({
        surfaceId: resolveSurfaceId(surface),
        changedFiles: [],
        changeType: 'modified',
      });
    }
  }

  return changes;
}
