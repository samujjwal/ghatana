/**
 * Extension Bootstrap Manager (Stub)
 *
 * Legacy connector code - marked for Phase 6 replacement with proper connector integration
 * Maintained for backward compatibility
 */

import { EventEmitter } from 'eventemitter3';

export interface ConfigSourceDefinition {
  id: string;
  type: 'http' | 'websocket' | 'ipc' | 'filesystem';
  config: Record<string, unknown>;
  priority: number;
  responseFormat?: 'json' | 'yaml';
  updateInterval?: number;
}

export interface BootstrapPhaseConfig {
  configSources: ConfigSourceDefinition[];
  cacheSettings?: {
    enabled: boolean;
    ttlMs: number;
    localStorageKey: string;
  };
  resilience?: {
    maxRetries: number;
    retryDelayMs: number;
    timeoutMs: number;
  };
}

export class ExtensionBootstrapManager extends EventEmitter<Record<string, any>> {
  constructor(_bootstrapConfig: BootstrapPhaseConfig) {
    super();
  }

  async initialize(): Promise<void> {
    // Stub - no-op
  }

  async getRuntimeConfig(): Promise<Record<string, unknown>> {
    return {};
  }

  destroy(): void {
    this.removeAllListeners();
  }
}

export type { BootstrapPhaseConfig as default };
