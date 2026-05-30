/**
 * Runtime Capability Service
 *
 * Queries the backend for runtime-truth-derived capability flags.
 * This replaces environment-based feature gates with dynamic runtime truth.
 *
 * @doc.type module
 * @doc.purpose Runtime-truth-derived capability resolution
 * @doc.layer frontend
 * @doc.pattern Service
 */

import { z } from 'zod';

const CapabilityResponseSchema = z.object({
  capabilities: z.record(z.string(), z.boolean()),
  version: z.string(),
  timestamp: z.string(),
});

export type CapabilityResponse = z.infer<typeof CapabilityResponseSchema>;

export class RuntimeCapabilityService {
  private static instance: RuntimeCapabilityService;
  private capabilities: Map<string, boolean> = new Map();
  private initialized = false;
  private initPromise: Promise<void> | null = null;

  private constructor() {}

  static getInstance(): RuntimeCapabilityService {
    if (!RuntimeCapabilityService.instance) {
      RuntimeCapabilityService.instance = new RuntimeCapabilityService();
    }
    return RuntimeCapabilityService.instance;
  }

  /**
   * Initialize the capability service by fetching from the backend.
   * Falls back to environment variables if the backend is unavailable.
   */
  async initialize(): Promise<void> {
    if (this.initialized) {
      return;
    }

    if (this.initPromise) {
      return this.initPromise;
    }

    this.initPromise = this.fetchCapabilities()
      .then((response) => {
        this.capabilities = new Map(Object.entries(response.capabilities));
        this.initialized = true;
      })
      .catch((error) => {
        console.warn('[RuntimeCapabilityService] Failed to fetch capabilities, falling back to env vars:', error);
        this.initializeFromEnv();
        this.initialized = true;
      });

    return this.initPromise;
  }

  private async fetchCapabilities(): Promise<CapabilityResponse> {
    const response = await fetch('/api/v1/capabilities');
    if (!response.ok) {
      throw new Error(`Failed to fetch capabilities: ${response.status}`);
    }
    const data = await response.json();
    return CapabilityResponseSchema.parse(data);
  }

  private initializeFromEnv(): void {
    const TRUE_VALUES = new Set(['1', 'true', 'yes', 'on']);
    const FALSE_VALUES = new Set(['0', 'false', 'no', 'off']);

    const env = import.meta.env as Record<string, unknown>;

    const readBooleanEnv = (key: string, defaultValue: boolean): boolean => {
      const raw = env[key];
      if (typeof raw !== 'string') {
        return defaultValue;
      }
      const normalized = raw.trim().toLowerCase();
      if (TRUE_VALUES.has(normalized)) {
        return true;
      }
      if (FALSE_VALUES.has(normalized)) {
        return false;
      }
      return defaultValue;
    };

    // Map capability names to environment variables
    this.capabilities.set('alerts', readBooleanEnv('VITE_FEATURE_ALERTS', true));
    this.capabilities.set('fabric', readBooleanEnv('VITE_FEATURE_FABRIC', false));
    this.capabilities.set('memory', readBooleanEnv('VITE_FEATURE_MEMORY', true));
    this.capabilities.set('entity-browser', readBooleanEnv('VITE_FEATURE_ENTITY_BROWSER', true));
    this.capabilities.set('context-explorer', readBooleanEnv('VITE_FEATURE_CONTEXT_EXPLORER', true));
    this.capabilities.set('agent-catalog', readBooleanEnv('VITE_FEATURE_AGENT_CATALOG', true));
    this.capabilities.set('settings', readBooleanEnv('VITE_FEATURE_SETTINGS', true));
    this.capabilities.set('event-stream', readBooleanEnv('VITE_FEATURE_EVENT_STREAM', true));
    this.capabilities.set('data-connectors', readBooleanEnv('VITE_FEATURE_DATA_CONNECTORS', true));
  }

  /**
   * Check if a capability is enabled.
   * Returns false if the service is not yet initialized.
   */
  isCapabilityEnabled(capability: string): boolean {
    if (!this.initialized) {
      console.warn('[RuntimeCapabilityService] Service not initialized, returning false for', capability);
      return false;
    }
    return this.capabilities.get(capability) ?? false;
  }

  /**
   * Get all capabilities.
   */
  getAllCapabilities(): Record<string, boolean> {
    return Object.fromEntries(this.capabilities);
  }

  /**
   * Reset the service (useful for testing).
   */
  reset(): void {
    this.capabilities.clear();
    this.initialized = false;
    this.initPromise = null;
  }
}

// Export singleton instance
export const runtimeCapabilityService = RuntimeCapabilityService.getInstance();
