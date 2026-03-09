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
export declare const PRESET_AGENT_ONLY: AgentConnectorConfig;
/**
 * Preset 2: Extension Bridge
 *
 * Agent receives telemetry from browser extension via bridge protocol
 * and routes it through the ingest pipeline, while also collecting
 * and sending its own metrics.
 *
 * Use case: Agent + Browser Extension deployment
 */
export declare const PRESET_EXTENSION_BRIDGE: AgentConnectorConfig;
/**
 * Preset 3: Desktop Bridge
 *
 * Agent receives telemetry from desktop app via bridge protocol
 * and routes it through the ingest pipeline, while also collecting
 * and sending its own metrics.
 *
 * Use case: Agent + Desktop App deployment
 */
export declare const PRESET_DESKTOP_BRIDGE: AgentConnectorConfig;
/**
 * Preset 4: Full Platform (Agent + Extension + Desktop)
 *
 * Agent receives telemetry from both browser extension and desktop app,
 * processes through ingest pipeline, and sends to server. Also collects
 * and sends its own metrics.
 *
 * Use case: Complete platform deployment with all components
 */
export declare const PRESET_FULL_PLATFORM: AgentConnectorConfig;
/**
 * Preset 5: Development Mode
 *
 * All sources enabled with debug logging and console output.
 * Useful for development, testing, and debugging.
 *
 * Use case: Local development and troubleshooting
 */
export declare const PRESET_DEVELOPMENT: AgentConnectorConfig;
/**
 * Preset 6: High Throughput
 *
 * Optimized for high-volume telemetry processing.
 * Larger batch sizes, shorter intervals, multiple sinks.
 *
 * Use case: Production deployments with high event volume
 */
export declare const PRESET_HIGH_THROUGHPUT: AgentConnectorConfig;
/**
 * All available presets mapped by ID
 */
export declare const PRESETS: {
    readonly 'agent-only': AgentConnectorConfig;
    readonly 'extension-bridge': AgentConnectorConfig;
    readonly 'desktop-bridge': AgentConnectorConfig;
    readonly 'full-platform': AgentConnectorConfig;
    readonly development: AgentConnectorConfig;
    readonly 'high-throughput': AgentConnectorConfig;
};
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
export declare function getPreset(id: PresetId): AgentConnectorConfig;
/**
 * Get all available preset IDs
 *
 * @returns Array of preset IDs
 */
export declare function getPresetIds(): PresetId[];
/**
 * Validate a preset configuration
 *
 * @param config - Configuration to validate
 * @returns Validation result with errors if any
 */
export declare function validatePreset(config: AgentConnectorConfig): {
    valid: boolean;
    errors: string[];
};
//# sourceMappingURL=presets.d.ts.map