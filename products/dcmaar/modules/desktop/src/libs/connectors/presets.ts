/**
 * @fileoverview Out-of-Box (OOB) Connector Presets
 *
 * Pre-configured setups for common scenarios:
 * - Agent as source/sink
 * - Extension as source/sink
 * - Third-party services
 * - Development/testing configurations
 *
 * @module libs/connectors/presets
 */

import type { ConnectorConfig, DesktopConnectorConfig } from './DesktopConnectorManager';

/**
 * Preset types
 */
export type PresetType =
  | 'agent-only'
  | 'extension-only'
  | 'agent-and-extension'
  | 'multi-agent'
  | 'development'
  | 'custom';

/**
 * Preset metadata
 */
export interface ConnectorPreset {
  id: PresetType;
  name: string;
  description: string;
  config: Omit<DesktopConnectorConfig, 'workspaceId'>;
  tags: string[];
  recommended?: boolean;
}

/**
 * Agent connection options
 */
export interface AgentConnectionOptions {
  /** Agent host (default: localhost) */
  host?: string;
  /** Agent port (default: 9773) */
  port?: number;
  /** Use TLS */
  secure?: boolean;
  /** Authentication token */
  authToken?: string;
  /** Agent ID for identification */
  agentId?: string;
}

/**
 * Extension connection options
 */
export interface ExtensionConnectionOptions {
  /** Extension bridge port (default: 9774) */
  port?: number;
  /** Extension ID */
  extensionId?: string;
  /** Browser type */
  browser?: 'chrome' | 'firefox' | 'edge' | 'brave';
}

/**
 * Create agent source connector config
 */
export const createAgentSourceConfig = (options: AgentConnectionOptions = {}): ConnectorConfig => {
  const { host = 'localhost', port = 9773, secure = false, authToken, agentId } = options;
  const protocol = secure ? 'wss' : 'ws';

  return {
    id: `agent-source-${agentId || 'default'}`,
    name: `Agent Source${agentId ? ` (${agentId})` : ''}`,
    type: 'agent',
    enabled: true,
    priority: 1,
    tags: ['agent', 'source', 'telemetry'],
    options: {
      transportOptions: {
        url: `${protocol}://${host}:${port}`,
        reconnect: true,
        reconnectInterval: 5000,
        maxReconnectAttempts: 10,
      },
      workspaceId: 'default',
      metadata: {
        clientType: 'desktop',
        clientVersion: '1.0.0',
        agentId,
      },
      ...(authToken && {
        transportOptions: {
          headers: { Authorization: `Bearer ${authToken}` },
        },
      }),
    },
  };
};

/**
 * Create agent sink connector config
 */
export const createAgentSinkConfig = (options: AgentConnectionOptions = {}): ConnectorConfig => {
  const { host = 'localhost', port = 9773, secure = false, authToken, agentId } = options;
  const protocol = secure ? 'wss' : 'ws';

  return {
    id: `agent-sink-${agentId || 'default'}`,
    name: `Agent Sink${agentId ? ` (${agentId})` : ''}`,
    type: 'agent',
    enabled: true,
    priority: 1,
    tags: ['agent', 'sink', 'commands'],
    options: {
      client: {
        transportOptions: {
          url: `${protocol}://${host}:${port}`,
          reconnect: true,
          reconnectInterval: 5000,
        },
        workspaceId: 'default',
        metadata: {
          clientType: 'desktop',
          clientVersion: '1.0.0',
          agentId,
        },
      },
      maxRetries: 3,
      retryDelayMs: 1000,
      ...(authToken && {
        client: {
          transportOptions: {
            headers: { Authorization: `Bearer ${authToken}` },
          },
        },
      }),
    },
  };
};

/**
 * Create extension source connector config
 */
export const createExtensionSourceConfig = (
  options: ExtensionConnectionOptions = {}
): ConnectorConfig => {
  const { port = 9774, extensionId, browser = 'chrome' } = options;

  return {
    id: `extension-source-${extensionId || 'default'}`,
    name: `Extension Source (${browser})${extensionId ? ` - ${extensionId}` : ''}`,
    type: 'extension',
    enabled: true,
    priority: 2,
    tags: ['extension', 'source', 'browser-metrics'],
    options: {
      transportOptions: {
        url: `ws://localhost:${port}`,
        reconnect: true,
        reconnectInterval: 3000,
        maxReconnectAttempts: 5,
      },
      workspaceId: 'default',
      metadata: {
        clientType: 'desktop',
        browser,
        extensionId,
      },
    },
  };
};

/**
 * Create extension sink connector config
 */
export const createExtensionSinkConfig = (
  options: ExtensionConnectionOptions = {}
): ConnectorConfig => {
  const { port = 9774, extensionId, browser = 'chrome' } = options;

  return {
    id: `extension-sink-${extensionId || 'default'}`,
    name: `Extension Sink (${browser})${extensionId ? ` - ${extensionId}` : ''}`,
    type: 'extension',
    enabled: true,
    priority: 2,
    tags: ['extension', 'sink', 'browser-commands'],
    options: {
      client: {
        transportOptions: {
          url: `ws://localhost:${port}`,
          reconnect: true,
        },
        workspaceId: 'default',
        metadata: {
          clientType: 'desktop',
          browser,
          extensionId,
        },
      },
      maxRetries: 2,
      retryDelayMs: 500,
    },
  };
};

/**
 * Preset: Agent Only
 * Desktop connects to a single agent for telemetry and commands
 */
export const AGENT_ONLY_PRESET: ConnectorPreset = {
  id: 'agent-only',
  name: 'Agent Only',
  description:
    'Connect to a single agent instance. Agent provides telemetry and receives commands.',
  recommended: true,
  tags: ['simple', 'agent', 'production'],
  config: {
    sources: [createAgentSourceConfig()],
    sinks: [createAgentSinkConfig()],
    autoStart: true,
    logging: {
      level: 'info',
      enabled: true,
    },
    healthCheckInterval: 30000,
  },
};

/**
 * Preset: Extension Only
 * Desktop connects to browser extension for metrics and control
 */
export const EXTENSION_ONLY_PRESET: ConnectorPreset = {
  id: 'extension-only',
  name: 'Extension Only',
  description:
    'Connect to browser extension. Extension provides browser metrics and receives commands.',
  recommended: true,
  tags: ['simple', 'extension', 'browser'],
  config: {
    sources: [createExtensionSourceConfig()],
    sinks: [createExtensionSinkConfig()],
    autoStart: true,
    logging: {
      level: 'info',
      enabled: true,
    },
    healthCheckInterval: 30000,
  },
};

/**
 * Preset: Agent + Extension
 * Desktop connects to both agent and extension
 */
export const AGENT_AND_EXTENSION_PRESET: ConnectorPreset = {
  id: 'agent-and-extension',
  name: 'Agent + Extension',
  description:
    'Connect to both agent and extension. Receive telemetry from both sources and send commands to both.',
  recommended: true,
  tags: ['full-stack', 'agent', 'extension'],
  config: {
    sources: [createAgentSourceConfig(), createExtensionSourceConfig()],
    sinks: [createAgentSinkConfig(), createExtensionSinkConfig()],
    autoStart: true,
    logging: {
      level: 'info',
      enabled: true,
    },
    healthCheckInterval: 30000,
  },
};

/**
 * Preset: Multi-Agent
 * Desktop connects to multiple agent instances
 */
export const MULTI_AGENT_PRESET: ConnectorPreset = {
  id: 'multi-agent',
  name: 'Multi-Agent',
  description: 'Connect to multiple agent instances. Useful for monitoring distributed systems.',
  tags: ['advanced', 'multi-agent', 'distributed'],
  config: {
    sources: [
      createAgentSourceConfig({ agentId: 'agent-1', port: 9773 }),
      createAgentSourceConfig({ agentId: 'agent-2', port: 9783 }),
      createAgentSourceConfig({ agentId: 'agent-3', port: 9793 }),
    ],
    sinks: [
      createAgentSinkConfig({ agentId: 'agent-1', port: 9773 }),
      createAgentSinkConfig({ agentId: 'agent-2', port: 9783 }),
      createAgentSinkConfig({ agentId: 'agent-3', port: 9793 }),
    ],
    autoStart: false,
    logging: {
      level: 'info',
      enabled: true,
    },
    healthCheckInterval: 60000,
  },
};

/**
 * Preset: Development
 * Mock connectors for development and testing
 */
export const DEVELOPMENT_PRESET: ConnectorPreset = {
  id: 'development',
  name: 'Development (Mock)',
  description: 'Use mock connectors for development and testing. No external dependencies.',
  tags: ['development', 'testing', 'mock'],
  config: {
    sources: [
      {
        id: 'mock-source',
        name: 'Mock Source',
        type: 'mock',
        enabled: true,
        priority: 1,
        tags: ['mock', 'source'],
        options: {
          interval: 5000,
          agentCount: 3,
        },
      },
    ],
    sinks: [
      {
        id: 'mock-sink',
        name: 'Mock Sink',
        type: 'mock',
        enabled: true,
        priority: 1,
        tags: ['mock', 'sink'],
        options: {
          delay: 100,
        },
      },
    ],
    autoStart: true,
    logging: {
      level: 'debug',
      enabled: true,
    },
    healthCheckInterval: 10000,
  },
};

/**
 * All available presets
 */
export const ALL_PRESETS: ConnectorPreset[] = [
  AGENT_ONLY_PRESET,
  EXTENSION_ONLY_PRESET,
  AGENT_AND_EXTENSION_PRESET,
  MULTI_AGENT_PRESET,
  DEVELOPMENT_PRESET,
];

/**
 * Get preset by ID
 */
export const getPreset = (id: PresetType): ConnectorPreset | undefined => {
  return ALL_PRESETS.find(p => p.id === id);
};

/**
 * Get recommended presets
 */
export const getRecommendedPresets = (): ConnectorPreset[] => {
  return ALL_PRESETS.filter(p => p.recommended);
};

/**
 * Create custom configuration from preset with overrides
 */
export const createConfigFromPreset = (
  presetId: PresetType,
  workspaceId: string,
  overrides?: Partial<DesktopConnectorConfig>
): DesktopConnectorConfig => {
  const preset = getPreset(presetId);
  if (!preset) {
    throw new Error(`Preset not found: ${presetId}`);
  }

  return {
    workspaceId,
    ...preset.config,
    ...overrides,
  };
};

/**
 * Validate connector configuration
 */
export const validateConnectorConfig = (
  config: DesktopConnectorConfig
): { valid: boolean; errors: string[] } => {
  const errors: string[] = [];

  if (!config.workspaceId) {
    errors.push('workspaceId is required');
  }

  if (!config.sources || config.sources.length === 0) {
    errors.push('At least one source connector is required');
  }

  // Check for duplicate IDs
  const sourceIds = new Set<string>();
  for (const source of config.sources) {
    if (sourceIds.has(source.id)) {
      errors.push(`Duplicate source ID: ${source.id}`);
    }
    sourceIds.add(source.id);
  }

  const sinkIds = new Set<string>();
  for (const sink of config.sinks) {
    if (sinkIds.has(sink.id)) {
      errors.push(`Duplicate sink ID: ${sink.id}`);
    }
    sinkIds.add(sink.id);
  }

  return {
    valid: errors.length === 0,
    errors,
  };
};
