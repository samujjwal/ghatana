/**
 * Out-of-box connector presets for Agent
 * Following Desktop pattern from DesktopConnectorManager presets.ts
 *
 * These presets provide common configurations for different deployment scenarios:
 * 1. agent-only: Agent collects and sends metrics to server (default)
 * 2. extension-bridge: Agent receives telemetry from browser extension
 * 3. desktop-bridge: Agent receives telemetry from desktop app
 * 4. full-platform: Agent receives from both extension and desktop
 * 5. development: All sources enabled with debug logging
 */

import type { AgentConnectorConfig } from './types';

/**
 * Preset 1: Agent Only (Default)
 *
 * Agent collects system metrics and sends to gRPC server.
 * No external sources, just internal metrics collection.
 *
 * Use case: Standalone agent deployment
 */
export const PRESET_AGENT_ONLY: AgentConnectorConfig = {
  version: '1.0.0',
  sources: [
    {
      id: 'agent-metrics-source',
      type: 'internal',
      enabled: true,
      metadata: {
        description: 'Internal metrics from MetricsService',
        collectInterval: 5000, // 5 seconds
      },
    },
  ],
  sinks: [
    {
      id: 'grpc-server-sink',
      type: 'grpc',
      url: 'https://localhost:50051',
      enabled: true,
      maxRetries: 3,
      timeout: 30000,
      metadata: {
        description: 'gRPC server sink for telemetry data',
      },
    },
  ],
  routing: [
    {
      sourceId: 'agent-metrics-source',
      sinkIds: ['grpc-server-sink'],
    },
  ],
};

/**
 * Preset 2: Extension Bridge
 *
 * Agent receives telemetry from browser extension via bridge protocol
 * and routes it through the ingest pipeline, while also collecting
 * and sending its own metrics.
 *
 * Use case: Agent + Browser Extension deployment
 */
export const PRESET_EXTENSION_BRIDGE: AgentConnectorConfig = {
  version: '1.0.0',
  sources: [
    {
      id: 'extension-bridge-source',
      type: 'bridge',
      enabled: true,
      metadata: {
        protocol: 'dcmaar-bridge-v1',
        description: 'Receives events from browser extension',
        port: 9001,
      },
    },
    {
      id: 'agent-metrics-source',
      type: 'internal',
      enabled: true,
      metadata: {
        description: 'Internal agent metrics',
      },
    },
  ],
  sinks: [
    {
      id: 'ingest-sink',
      type: 'ingest',
      enabled: true,
      metadata: {
        description: 'Routes to Rust IngestService',
      },
    },
    {
      id: 'grpc-server-sink',
      type: 'grpc',
      url: 'https://localhost:50051',
      enabled: true,
      maxRetries: 3,
      timeout: 30000,
    },
  ],
  routing: [
    {
      sourceId: 'extension-bridge-source',
      sinkIds: ['ingest-sink', 'grpc-server-sink'],
    },
    {
      sourceId: 'agent-metrics-source',
      sinkIds: ['grpc-server-sink'],
    },
  ],
};

/**
 * Preset 3: Desktop Bridge
 *
 * Agent receives telemetry from desktop app via bridge protocol
 * and routes it through the ingest pipeline, while also collecting
 * and sending its own metrics.
 *
 * Use case: Agent + Desktop App deployment
 */
export const PRESET_DESKTOP_BRIDGE: AgentConnectorConfig = {
  version: '1.0.0',
  sources: [
    {
      id: 'desktop-bridge-source',
      type: 'bridge',
      enabled: true,
      metadata: {
        protocol: 'dcmaar-bridge-v1',
        description: 'Receives events from desktop app',
        port: 9002,
      },
    },
    {
      id: 'agent-metrics-source',
      type: 'internal',
      enabled: true,
      metadata: {
        description: 'Internal agent metrics',
      },
    },
  ],
  sinks: [
    {
      id: 'ingest-sink',
      type: 'ingest',
      enabled: true,
      metadata: {
        description: 'Routes to Rust IngestService',
      },
    },
    {
      id: 'grpc-server-sink',
      type: 'grpc',
      url: 'https://localhost:50051',
      enabled: true,
      maxRetries: 3,
      timeout: 30000,
    },
  ],
  routing: [
    {
      sourceId: 'desktop-bridge-source',
      sinkIds: ['ingest-sink', 'grpc-server-sink'],
    },
    {
      sourceId: 'agent-metrics-source',
      sinkIds: ['grpc-server-sink'],
    },
  ],
};

/**
 * Preset 4: Full Platform (Agent + Extension + Desktop)
 *
 * Agent receives telemetry from both browser extension and desktop app,
 * processes through ingest pipeline, and sends to server. Also collects
 * and sends its own metrics.
 *
 * Use case: Complete platform deployment with all components
 */
export const PRESET_FULL_PLATFORM: AgentConnectorConfig = {
  version: '1.0.0',
  sources: [
    {
      id: 'extension-bridge-source',
      type: 'bridge',
      enabled: true,
      metadata: {
        protocol: 'dcmaar-bridge-v1',
        description: 'Receives events from browser extension',
        port: 9001,
      },
    },
    {
      id: 'desktop-bridge-source',
      type: 'bridge',
      enabled: true,
      metadata: {
        protocol: 'dcmaar-bridge-v1',
        description: 'Receives events from desktop app',
        port: 9002,
      },
    },
    {
      id: 'agent-metrics-source',
      type: 'internal',
      enabled: true,
      metadata: {
        description: 'Internal agent metrics',
      },
    },
  ],
  sinks: [
    {
      id: 'ingest-sink',
      type: 'ingest',
      enabled: true,
      metadata: {
        description: 'Routes to Rust IngestService',
        batchSize: 100,
        batchInterval: 5000,
      },
    },
    {
      id: 'grpc-server-sink',
      type: 'grpc',
      url: 'https://localhost:50051',
      enabled: true,
      maxRetries: 3,
      timeout: 30000,
      metadata: {
        description: 'Primary telemetry sink',
      },
    },
  ],
  routing: [
    {
      sourceId: 'extension-bridge-source',
      sinkIds: ['ingest-sink', 'grpc-server-sink'],
    },
    {
      sourceId: 'desktop-bridge-source',
      sinkIds: ['ingest-sink', 'grpc-server-sink'],
    },
    {
      sourceId: 'agent-metrics-source',
      sinkIds: ['grpc-server-sink'],
    },
  ],
};

/**
 * Preset 5: Development Mode
 *
 * All sources enabled with debug logging and console output.
 * Useful for development, testing, and debugging.
 *
 * Use case: Local development and troubleshooting
 */
export const PRESET_DEVELOPMENT: AgentConnectorConfig = {
  version: '1.0.0',
  sources: [
    {
      id: 'extension-bridge-source',
      type: 'bridge',
      enabled: true,
      debug: true,
      metadata: {
        protocol: 'dcmaar-bridge-v1',
        description: 'Extension bridge (debug mode)',
        port: 9001,
      },
    },
    {
      id: 'desktop-bridge-source',
      type: 'bridge',
      enabled: true,
      debug: true,
      metadata: {
        protocol: 'dcmaar-bridge-v1',
        description: 'Desktop bridge (debug mode)',
        port: 9002,
      },
    },
    {
      id: 'agent-metrics-source',
      type: 'internal',
      enabled: true,
      debug: true,
      metadata: {
        description: 'Internal metrics (debug mode)',
      },
    },
  ],
  sinks: [
    {
      id: 'console-sink',
      type: 'console',
      enabled: true,
      metadata: {
        description: 'Debug console output',
        pretty: true,
      },
    },
    {
      id: 'ingest-sink',
      type: 'ingest',
      enabled: true,
      debug: true,
      metadata: {
        description: 'Ingest sink (debug mode)',
      },
    },
    {
      id: 'grpc-server-sink',
      type: 'grpc',
      url: 'https://localhost:50051',
      enabled: false, // Disabled in dev mode by default
      maxRetries: 1,
      timeout: 10000,
    },
  ],
  routing: [
    {
      sourceId: 'extension-bridge-source',
      sinkIds: ['console-sink', 'ingest-sink'],
    },
    {
      sourceId: 'desktop-bridge-source',
      sinkIds: ['console-sink', 'ingest-sink'],
    },
    {
      sourceId: 'agent-metrics-source',
      sinkIds: ['console-sink', 'ingest-sink'],
    },
  ],
};

/**
 * Preset 6: High Throughput
 *
 * Optimized for high-volume telemetry processing.
 * Larger batch sizes, shorter intervals, multiple sinks.
 *
 * Use case: Production deployments with high event volume
 */
export const PRESET_HIGH_THROUGHPUT: AgentConnectorConfig = {
  version: '1.0.0',
  sources: [
    {
      id: 'extension-bridge-source',
      type: 'bridge',
      enabled: true,
      metadata: {
        protocol: 'dcmaar-bridge-v1',
        port: 9001,
        bufferSize: 10000,
      },
    },
    {
      id: 'desktop-bridge-source',
      type: 'bridge',
      enabled: true,
      metadata: {
        protocol: 'dcmaar-bridge-v1',
        port: 9002,
        bufferSize: 10000,
      },
    },
    {
      id: 'agent-metrics-source',
      type: 'internal',
      enabled: true,
    },
  ],
  sinks: [
    {
      id: 'ingest-sink-primary',
      type: 'ingest',
      enabled: true,
      metadata: {
        batchSize: 500,
        batchInterval: 1000,
        workers: 4,
      },
    },
    {
      id: 'grpc-server-sink-primary',
      type: 'grpc',
      url: 'https://localhost:50051',
      enabled: true,
      maxRetries: 5,
      timeout: 60000,
      metadata: {
        connectionPool: 10,
      },
    },
  ],
  routing: [
    {
      sourceId: 'extension-bridge-source',
      sinkIds: ['ingest-sink-primary', 'grpc-server-sink-primary'],
    },
    {
      sourceId: 'desktop-bridge-source',
      sinkIds: ['ingest-sink-primary', 'grpc-server-sink-primary'],
    },
    {
      sourceId: 'agent-metrics-source',
      sinkIds: ['grpc-server-sink-primary'],
    },
  ],
};

/**
 * All available presets mapped by ID
 */
export const PRESETS = {
  'agent-only': PRESET_AGENT_ONLY,
  'extension-bridge': PRESET_EXTENSION_BRIDGE,
  'desktop-bridge': PRESET_DESKTOP_BRIDGE,
  'full-platform': PRESET_FULL_PLATFORM,
  'development': PRESET_DEVELOPMENT,
  'high-throughput': PRESET_HIGH_THROUGHPUT,
} as const;

/**
 * Preset IDs
 */
export type PresetId = keyof typeof PRESETS;

/**
 * Get a preset configuration by ID
 *
 * @param id - Preset ID
 * @returns Preset configuration
 * @throws Error if preset not found
 *
 * @example
 * ```typescript
 * const config = getPreset('agent-only');
 * const manager = new AgentConnectorManager();
 * await manager.initialize(config);
 * ```
 */
export function getPreset(id: PresetId): AgentConnectorConfig {
  const preset = PRESETS[id];
  if (!preset) {
    throw new Error(`Unknown preset: ${id}. Available presets: ${Object.keys(PRESETS).join(', ')}`);
  }
  return preset;
}

/**
 * Get all available preset IDs
 *
 * @returns Array of preset IDs
 */
export function getPresetIds(): PresetId[] {
  return Object.keys(PRESETS) as PresetId[];
}

/**
 * Validate a preset configuration
 *
 * @param config - Configuration to validate
 * @returns Validation result with errors if any
 */
export function validatePreset(config: AgentConnectorConfig): { valid: boolean; errors: string[] } {
  const errors: string[] = [];

  // Check version
  if (!config.version) {
    errors.push('Missing version');
  }

  // Check sources
  if (!config.sources || config.sources.length === 0) {
    errors.push('At least one source is required');
  }

  // Check sinks
  if (!config.sinks || config.sinks.length === 0) {
    errors.push('At least one sink is required');
  }

  // Check routing
  if (!config.routing || config.routing.length === 0) {
    errors.push('At least one route is required');
  }

  // Validate source IDs are unique
  const sourceIds = new Set<string>();
  for (const source of config.sources || []) {
    if (!source.id) {
      errors.push(`Source missing ID: ${JSON.stringify(source)}`);
    } else if (sourceIds.has(source.id)) {
      errors.push(`Duplicate source ID: ${source.id}`);
    } else {
      sourceIds.add(source.id);
    }
  }

  // Validate sink IDs are unique
  const sinkIds = new Set<string>();
  for (const sink of config.sinks || []) {
    if (!sink.id) {
      errors.push(`Sink missing ID: ${JSON.stringify(sink)}`);
    } else if (sinkIds.has(sink.id)) {
      errors.push(`Duplicate sink ID: ${sink.id}`);
    } else {
      sinkIds.add(sink.id);
    }
  }

  // Validate routing references
  for (const route of config.routing || []) {
    if (!sourceIds.has(route.sourceId)) {
      errors.push(`Route references unknown source: ${route.sourceId}`);
    }
    for (const sinkId of route.sinkIds) {
      if (!sinkIds.has(sinkId)) {
        errors.push(`Route references unknown sink: ${sinkId}`);
      }
    }
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}
