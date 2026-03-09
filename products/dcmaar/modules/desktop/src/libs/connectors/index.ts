/**
 * @fileoverview Desktop Connectors - Public API
 *
 * Export all connector-related types and functions
 */

export {
  DesktopConnectorManager,
  createDesktopConnectorManager,
  type DesktopConnectorConfig,
  type ConnectorConfig,
  type ConnectorState,
  type TelemetryUpdateListener,
  type StateChangeListener,
  type ErrorListener,
} from './DesktopConnectorManager';

export {
  createAgentSourceConfig,
  createAgentSinkConfig,
  createExtensionSourceConfig,
  createExtensionSinkConfig,
  createConfigFromPreset,
  getPreset,
  getRecommendedPresets,
  validateConnectorConfig,
  AGENT_ONLY_PRESET,
  EXTENSION_ONLY_PRESET,
  AGENT_AND_EXTENSION_PRESET,
  MULTI_AGENT_PRESET,
  DEVELOPMENT_PRESET,
  ALL_PRESETS,
  type ConnectorPreset,
  type PresetType,
  type AgentConnectionOptions,
  type ExtensionConnectionOptions,
} from './presets';

// Re-export types from adapters
export type { TelemetrySnapshot, ControlCommand, SinkAck, HealthStatus } from '../adapters/types';
