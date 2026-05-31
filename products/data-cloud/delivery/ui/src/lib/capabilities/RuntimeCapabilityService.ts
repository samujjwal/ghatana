/**
 * Runtime Capability Service
 *
 * Compatibility service for legacy feature-gate callers. Runtime truth is
 * loaded from the canonical /api/v1/surfaces endpoint only.
 *
 * @doc.type module
 * @doc.purpose Runtime-truth-derived capability resolution
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
