/**
 * DCMAAR Agent Connector Integration Layer
 * TypeScript bridge for integrating @ghatana/dcmaar-connectors with Rust Agent
 *
 * This module provides:
 * - AgentConnectorManager: Orchestrates sources and sinks
 * - Adapter Factory: Creates pluggable connectors
 * - Bridge Protocol: Rust ↔ TypeScript communication
 * - Presets: Out-of-box connector configurations
 */

export { AgentConnectorManager } from './connectors/AgentConnectorManager';
export { AgentAdapterFactory } from './adapters/AdapterFactory';
export { Logger } from './utils/logger';

export type {
  AgentConnectorConfig,
  SourceConfig,
  SinkConfig,
  RoutingConfig,
  ProcessorConfig,
  BridgeMessage,
  BridgeResponse,
  ConnectorStatus,
} from './connectors/types';

export type {
  SourceAdapter,
  SinkAdapter,
  AdapterRegistry,
} from './adapters/types';

// Presets
export {
  PRESETS,
  PRESET_AGENT_ONLY,
  PRESET_EXTENSION_BRIDGE,
  PRESET_DESKTOP_BRIDGE,
  PRESET_FULL_PLATFORM,
  PRESET_DEVELOPMENT,
  PRESET_HIGH_THROUGHPUT,
  getPreset,
  getPresetIds,
  validatePreset,
} from './connectors/presets';
export type { PresetId } from './connectors/presets';
