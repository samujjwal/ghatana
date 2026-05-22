/**
 * Affected surface execution planner for optimized lifecycle execution.
 *
 * Uses surface hashing and dependency graphs to determine which surfaces need
 * to be executed based on changes, skipping unchanged surfaces for faster builds.
 *
 * @doc.type class
 * @doc.purpose Plan affected surface execution using hash-based change detection
 * @doc.layer kernel-lifecycle
 * @doc.pattern Service
 */

import type { SurfaceHashResult } from "./SurfaceHashComputer.js";
import { SurfaceHashComputer } from "./SurfaceHashComputer.js";
import { SurfaceDependencyGraph, type SurfaceDependency } from "./SurfaceDependencyGraph.js";

export interface SurfaceExecutionPlan {
  readonly phase: string;
  readonly surfacesToExecute: readonly string[];
  readonly surfacesSkipped: readonly string[];
  readonly skipReason: Map<string, string>;
  readonly executionTime: Date;
}

export interface SurfaceHashStore {
  get(surfaceId: string): Promise<SurfaceHashResult | null>;
  set(surfaceId: string, hash: SurfaceHashResult): Promise<void>;
}

/**
 * Plans lifecycle execution by computing affected surfaces based on changes.
 */
export class AffectedSurfaceExecutionPlanner {
  private readonly hashComputer: SurfaceHashComputer;
  private readonly dependencyGraph: SurfaceDependencyGraph;
  private readonly hashStore: SurfaceHashStore;

  constructor(hashStore: SurfaceHashStore) {
    this.hashComputer = new SurfaceHashComputer();
    this.dependencyGraph = new SurfaceDependencyGraph();
    this.hashStore = hashStore;
  }

  /**
   * Sets up surface dependencies for the dependency graph.
   *
   * @param dependencies the surface dependencies
   */
  setDependencies(dependencies: readonly SurfaceDependency[]): void {
    for (const dep of dependencies) {
      this.dependencyGraph.addSurface(dep);
    }
  }

  /**
   * Computes an execution plan for a lifecycle phase.
   *
   * @param phase the lifecycle phase
   * @param surfaces all surfaces that could be executed
   * @param changedSurfaces surfaces that have changed (if known)
   * @returns the execution plan
   */
  async computeExecutionPlan(
    phase: string,
    surfaces: readonly { readonly surfaceId: string; readonly surfacePath: string }[],
    changedSurfaces?: readonly string[]
  ): Promise<SurfaceExecutionPlan> {
    const surfacesToExecute = new Set<string>();
    const surfacesSkipped = new Set<string>();
    const skipReason = new Map<string, string>();

    // Compute current hashes for all surfaces
    const currentHashes = await this.hashComputer.computeHashes(surfaces);

    // Determine which surfaces have changed
    const actuallyChanged: string[] = [];
    for (const hashResult of currentHashes) {
      const previousHash = await this.hashStore.get(hashResult.surfaceId);
      if (!previousHash || this.hashComputer.hasChanged(previousHash, hashResult)) {
        actuallyChanged.push(hashResult.surfaceId);
        // Store new hash
        await this.hashStore.set(hashResult.surfaceId, hashResult);
      }
    }

    // If no changed surfaces provided, use computed changes
    const changes = changedSurfaces ?? actuallyChanged;

    if (changes.length === 0) {
      // No changes - skip all surfaces
      for (const surface of surfaces) {
        surfacesSkipped.add(surface.surfaceId);
        skipReason.set(surface.surfaceId, "No changes detected");
      }
    } else {
      // Compute affected surfaces using dependency graph
      const affectedResult = this.dependencyGraph.computeAffectedSurfacesFromMultiple(changes);
      const affectedSet = new Set(affectedResult.affectedSurfaces);

      for (const surface of surfaces) {
        if (affectedSet.has(surface.surfaceId)) {
          surfacesToExecute.add(surface.surfaceId);
        } else {
          surfacesSkipped.add(surface.surfaceId);
          skipReason.set(surface.surfaceId, "Not affected by changes");
        }
      }
    }

    return {
      phase,
      surfacesToExecute: Array.from(surfacesToExecute),
      surfacesSkipped: Array.from(surfacesSkipped),
      skipReason,
      executionTime: new Date(),
    };
  }

  /**
   * Computes execution plans for multiple phases in parallel.
   *
   * @param phases the lifecycle phases
   * @param surfaces all surfaces that could be executed
   * @param changedSurfaces surfaces that have changed (if known)
   * @returns the execution plans
   */
  async computeExecutionPlans(
    phases: readonly string[],
    surfaces: readonly { readonly surfaceId: string; readonly surfacePath: string }[],
    changedSurfaces?: readonly string[]
  ): Promise<readonly SurfaceExecutionPlan[]> {
    return Promise.all(
      phases.map((phase) => this.computeExecutionPlan(phase, surfaces, changedSurfaces))
    );
  }
}
