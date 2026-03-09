/**
 * Desktop Connector System - Usage Examples
 * 
 * This file demonstrates how to integrate the connector system
 * with the desktop application.
 */

// ============================================================================
// Example 1: Initialize Desktop Controller with Agent Only Preset
// ============================================================================

import { createDesktopController } from './controllers/DesktopController';
import { createConfigFromPreset } from './libs/connectors';

async function example1_AgentOnly() {
    // Create configuration using preset
    const connectorConfig = createConfigFromPreset('agent-only', 'my-workspace');

    // Initialize controller
    const controller = await createDesktopController({
        workspaceId: 'my-workspace',
        connectorConfig,
        loadFromStorage: false,
    });

    // Subscribe to telemetry updates
    controller.onTelemetryUpdate((snapshot) => {
        console.log('New telemetry:', {
            version: snapshot.version,
            agentsCount: snapshot.agents.length,
            timestamp: snapshot.collectedAt,
        });

        // Update your UI here
        // e.g., updateDashboard(snapshot);
    });

    // Subscribe to state changes
    controller.onStateChange((state) => {
        console.log('Controller state:', state);
    });

    return controller;
}

// ============================================================================
// Example 2: Custom Configuration with Agent and Extension
// ============================================================================

import {
    createAgentSourceConfig,
    createAgentSinkConfig,
    createExtensionSourceConfig,
    createExtensionSinkConfig,
} from './libs/connectors';

async function example2_CustomConfig() {
    const connectorConfig = {
        workspaceId: 'my-workspace',
        sources: [
            createAgentSourceConfig({
                host: 'localhost',
                port: 9773,
                agentId: 'main-agent',
            }),
            createExtensionSourceConfig({
                port: 9774,
                browser: 'chrome',
                extensionId: 'ext-123',
            }),
        ],
        sinks: [
            createAgentSinkConfig({
                host: 'localhost',
                port: 9773,
                agentId: 'main-agent',
            }),
        ],
        autoStart: true,
        logging: {
            level: 'info' as const,
            enabled: true,
        },
        healthCheckInterval: 30000,
    };

    const controller = await createDesktopController({
        workspaceId: 'my-workspace',
        connectorConfig,
    });

    return controller;
}

// ============================================================================
// Example 3: Send Commands to Agent/Extension
// ============================================================================

async function example3_SendCommands(controller: any) {
    // Send command to all sinks
    const acks = await controller.sendCommand({
        id: `cmd-${Date.now()}`,
        category: 'config',
        payload: {
            setting: 'log_level',
            value: 'debug',
        },
        metadata: {
            issuedBy: 'desktop-user',
            issuedAt: new Date().toISOString(),
            priority: 'medium',
        },
    });

    console.log('Command acknowledgements:', acks);

    // Send command to specific sink
    const specificAcks = await controller.sendCommand(
        {
            id: `cmd-${Date.now()}`,
            category: 'action',
            payload: {
                action: 'restart',
            },
            metadata: {
                issuedBy: 'desktop-user',
                issuedAt: new Date().toISOString(),
                priority: 'high',
            },
        },
        'agent-sink-main-agent' // Specific sink ID
    );

    console.log('Specific sink acks:', specificAcks);
}

// ============================================================================
// Example 4: React Integration with UI Component
// ============================================================================

import React, { useEffect, useState } from 'react';
import { ConnectorConfigUI } from './components/ConnectorConfigUI';
import type { DesktopController } from './controllers/DesktopController';

function ConnectorSettingsPage() {
    const [controller, setController] = useState<DesktopController | null>(null);
    const [config, setConfig] = useState(null);
    const [state, setState] = useState(null);

    useEffect(() => {
        // Initialize controller on mount
        const initController = async () => {
            const ctrl = await createDesktopController({
                workspaceId: 'my-workspace',
                loadFromStorage: true,
            });

            setController(ctrl);
            setConfig(ctrl.getConnectorManager()?.getConfig());
            setState(ctrl.getState());

            // Subscribe to state changes
            ctrl.onStateChange((newState) => {
                setState(newState);
            });
        };

        initController();

        return () => {
            // Cleanup on unmount
            controller?.shutdown();
        };
    }, []);

    const handleConfigChange = (newConfig: any) => {
        setConfig(newConfig);
    };

    const handleApplyConfig = async (newConfig: any) => {
        if (controller) {
            await controller.applyConnectorConfig(newConfig);
        }
    };

    const handleToggleConnector = async (
        connectorId: string,
        type: 'source' | 'sink',
        start: boolean
    ) => {
        const manager = controller?.getConnectorManager();
        if (!manager) return;

        if (type === 'source') {
            if (start) {
                await manager.startSource(connectorId);
            } else {
                await manager.stopSource(connectorId);
            }
        }
    };

    return (
        <div>
            <ConnectorConfigUI
                config={config}
                state={state?.connectorState}
                onChange={handleConfigChange}
                onApply={handleApplyConfig}
                onToggleConnector={handleToggleConnector}
                workspaceId="my-workspace"
            />
        </div>
    );
}

// ============================================================================
// Example 5: Using with Jotai Atoms (Desktop App State Management)
// ============================================================================

import { atom, useAtom } from 'jotai';
import type { DesktopConnectorConfig, ConnectorState, TelemetrySnapshot } from './libs/connectors';

// Define atoms
export const connectorConfigAtom = atom<DesktopConnectorConfig | null>(null);
export const connectorStateAtom = atom<ConnectorState | null>(null);
export const telemetrySnapshotAtom = atom<TelemetrySnapshot | null>(null);
export const desktopControllerAtom = atom<DesktopController | null>(null);

// Hook to initialize and manage controller
function useDesktopController() {
    const [controller, setController] = useAtom(desktopControllerAtom);
    const [, setConnectorState] = useAtom(connectorStateAtom);
    const [, setTelemetrySnapshot] = useAtom(telemetrySnapshotAtom);

    useEffect(() => {
        if (controller) return;

        const initController = async () => {
            const ctrl = await createDesktopController({
                workspaceId: 'my-workspace',
                loadFromStorage: true,
            });

            // Sync state to atoms
            ctrl.onStateChange((state) => {
                setConnectorState(state.connectorState || null);
            });

            ctrl.onTelemetryUpdate((snapshot) => {
                setTelemetrySnapshot(snapshot);
            });

            setController(ctrl);
        };

        initController();
    }, [controller, setController, setConnectorState, setTelemetrySnapshot]);

    return controller;
}

// Use in a component
function DashboardPage() {
    const controller = useDesktopController();
    const [telemetrySnapshot] = useAtom(telemetrySnapshotAtom);
    const [connectorState] = useAtom(connectorStateAtom);

    return (
        <div>
            <h1>Dashboard</h1>

            {connectorState && (
                <div>
                    <p>Active Sources: {connectorState.activeSourcesCount}</p>
                    <p>Active Sinks: {connectorState.activeSinksCount}</p>
                    <p>Healthy: {connectorState.healthy ? 'Yes' : 'No'}</p>
                </div>
            )}

            {telemetrySnapshot && (
                <div>
                    <p>Agents: {telemetrySnapshot.agents.length}</p>
                    <p>Last Update: {telemetrySnapshot.collectedAt}</p>
                </div>
            )}
        </div>
    );
}

// ============================================================================
// Example 6: Multi-Agent Setup
// ============================================================================

async function example6_MultiAgent() {
    const connectorConfig = {
        workspaceId: 'distributed-system',
        sources: [
            createAgentSourceConfig({ port: 9773, agentId: 'agent-1' }),
            createAgentSourceConfig({ port: 9783, agentId: 'agent-2' }),
            createAgentSourceConfig({ port: 9793, agentId: 'agent-3' }),
        ],
        sinks: [
            createAgentSinkConfig({ port: 9773, agentId: 'agent-1' }),
            createAgentSinkConfig({ port: 9783, agentId: 'agent-2' }),
            createAgentSinkConfig({ port: 9793, agentId: 'agent-3' }),
        ],
        autoStart: false, // Manual start for multi-agent
        logging: { level: 'info' as const, enabled: true },
        healthCheckInterval: 60000,
    };

    const controller = await createDesktopController({
        workspaceId: 'distributed-system',
        connectorConfig,
    });

    // Manually start specific sources
    const manager = controller.getConnectorManager();
    if (manager) {
        await manager.startSource('agent-source-agent-1');
        await manager.startSource('agent-source-agent-2');
        // agent-3 stays dormant
    }

    return controller;
}

// ============================================================================
// Example 7: Runtime Connector Management
// ============================================================================

async function example7_RuntimeManagement(controller: any) {
    const manager = controller.getConnectorManager();
    if (!manager) return;

    // Add new source at runtime
    await manager.addConnector(
        {
            id: 'new-http-source',
            name: 'HTTP Polling Source',
            type: 'http',
            enabled: true,
            priority: 3,
            options: {
                url: 'https://api.example.com/telemetry',
                interval: 5000,
            },
        },
        'source'
    );

    // Add new sink at runtime
    await manager.addConnector(
        {
            id: 'file-sink',
            name: 'Local File Sink',
            type: 'file',
            enabled: true,
            options: {
                path: './logs/commands.jsonl',
            },
        },
        'sink'
    );

    // Remove a connector
    await manager.removeConnector('agent-source-agent-2', 'source');

    // Get current connector list
    const connectors = manager.getConnectors();
    console.log('Current connectors:', connectors);
}

// ============================================================================
// Example 8: Health Monitoring
// ============================================================================

async function example8_HealthMonitoring(controller: any) {
    const manager = controller.getConnectorManager();
    if (!manager) return;

    // Manual health check
    const health = await manager.healthCheck();
    console.log('Health status:', health);

    // Check specific connector health
    if (health['source:agent-source-main-agent']) {
        const agentHealth = health['source:agent-source-main-agent'];
        console.log('Agent health:', {
            healthy: agentHealth.healthy,
            latency: agentHealth.latencyMs,
            error: agentHealth.error,
        });
    }

    // Automated health monitoring
    setInterval(async () => {
        const currentHealth = await manager.healthCheck();
        const unhealthyConnectors = Object.entries(currentHealth)
            .filter(([_, status]: any) => !status.healthy)
            .map(([id]) => id);

        if (unhealthyConnectors.length > 0) {
            console.warn('Unhealthy connectors:', unhealthyConnectors);
            // Trigger alerts or UI updates
        }
    }, 30000); // Every 30 seconds
}

export {
    example1_AgentOnly,
    example2_CustomConfig,
    example3_SendCommands,
    example6_MultiAgent,
    example7_RuntimeManagement,
    example8_HealthMonitoring,
    ConnectorSettingsPage,
    DashboardPage,
    useDesktopController,
};
