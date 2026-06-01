/**
 * Runtime Capability Service
 *
 * WS1: Typed runtime-surface store consuming backend /api/v1/surfaces truth.
 * Provides typed surface lookup, navigation filtering, and action availability.
 *
 * @doc.type module
 * @doc.purpose Runtime-truth-derived typed surface store
 * @doc.layer frontend
 * @doc.pattern Service
 */

import {
  fetchSurfaceRegistry,
  getSurfaceSignal,
  type SurfaceSignal,
} from "../../api/surfaces.service";

export type RuntimeCapabilityServiceState =
  | "loading"
  | "ready"
  | "unavailable"
  | "failed";

export interface RuntimeCapabilitySnapshot {
  readonly state: RuntimeCapabilityServiceState;
  readonly surfaces: readonly SurfaceSignal[];
  readonly error?: Error;
}

function isEnabledSurface(signal: SurfaceSignal | undefined): boolean {
  if (!signal) {
    return false;
  }
  return (
    signal.status === "LIVE" ||
    signal.status === "DEGRADED" ||
    signal.status === "PREVIEW"
  );
}

export class RuntimeCapabilityService {
  private static instance: RuntimeCapabilityService;
  private surfaces: readonly SurfaceSignal[] = [];
  private state: RuntimeCapabilityServiceState = "unavailable";
  private error: Error | undefined;
  private initPromise: Promise<void> | null = null;

  private constructor() {}

  static getInstance(): RuntimeCapabilityService {
    if (!RuntimeCapabilityService.instance) {
      RuntimeCapabilityService.instance = new RuntimeCapabilityService();
    }
    return RuntimeCapabilityService.instance;
  }

  /**
   * Initialize the service from canonical runtime truth.
   */
  async initialize(): Promise<void> {
    if (this.state === "ready") {
      return;
    }

    if (this.initPromise) {
      return this.initPromise;
    }

    this.state = "loading";
    this.error = undefined;
    this.initPromise = fetchSurfaceRegistry()
      .then((snapshot) => {
        this.surfaces = snapshot.surfaces;
        this.state = "ready";
      })
      .catch((error: unknown) => {
        this.surfaces = [];
        this.error =
          error instanceof Error
            ? error
            : new Error("Runtime surface registry is unavailable");
        this.state = "failed";
        console.warn(
          "[RuntimeCapabilityService] Failed to load /api/v1/surfaces runtime truth:",
          this.error,
        );
      })
      .finally(() => {
        this.initPromise = null;
      });

    return this.initPromise;
  }

  getState(): RuntimeCapabilityServiceState {
    return this.state;
  }

  getSnapshot(): RuntimeCapabilitySnapshot {
    return {
      state: this.state,
      surfaces: this.surfaces,
      error: this.error,
    };
  }

  /**
   * Check whether a surface alias is enabled by runtime truth.
   */
  isCapabilityEnabled(capability: string): boolean {
    if (this.state !== "ready") {
      return false;
    }
    return isEnabledSurface(getSurfaceSignal(this.surfaces, [capability]));
  }

  /**
   * WS1: Get surface by path.
   */
  getSurfaceByPath(path: string): SurfaceSignal | undefined {
    if (this.state !== "ready") {
      return undefined;
    }
    return this.surfaces.find((surface) => surface.path === path);
  }

  /**
   * WS1: Get surface by alias (surfaceId or any known alias).
   */
  getSurfaceByAlias(alias: string): SurfaceSignal | undefined {
    if (this.state !== "ready") {
      return undefined;
    }
    return getSurfaceSignal(this.surfaces, [alias]);
  }

  /**
   * WS1: Get navigation surfaces sorted by route group and sort order.
   * Returns discoverable surfaces with primary or contextual navigation flags.
   */
  getNavigationSurfaces(): readonly SurfaceSignal[] {
    if (this.state !== "ready") {
      return [];
    }
    return this.surfaces
      .filter((surface) => 
        surface.discoverable && (surface.primaryNavigation || surface.contextualNavigation)
      )
      .sort((a, b) => {
        // Sort by route group first, then by sort order
        const groupA = a.routeGroup ?? "";
        const groupB = b.routeGroup ?? "";
        if (groupA !== groupB) {
          return groupA.localeCompare(groupB);
        }
        const orderA = a.sortOrder ?? 0;
        const orderB = b.sortOrder ?? 0;
        return orderA - orderB;
      });
  }

  /**
   * WS1: Get action availability for a specific surface.
   * Returns the list of actions allowed for the surface if it's available.
   */
  getActionAvailability(surfaceId: string): readonly string[] {
    if (this.state !== "ready") {
      return [];
    }
    const surface = getSurfaceSignal(this.surfaces, [surfaceId]);
    if (!surface || !isEnabledSurface(surface)) {
      return [];
    }
    return surface.actionsAllowed;
  }

  /**
   * Get all runtime surface enablement flags.
   */
  getAllCapabilities(): Record<string, boolean> {
    return Object.fromEntries(
      this.surfaces.map((surface) => [surface.key, isEnabledSurface(surface)]),
    );
  }

  /**
   * Reset the service for tests.
   */
  reset(): void {
    this.surfaces = [];
    this.state = "unavailable";
    this.error = undefined;
    this.initPromise = null;
  }
}

export const runtimeCapabilityService = RuntimeCapabilityService.getInstance();
