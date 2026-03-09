/**
 * @fileoverview Command Execution Sink
 *
 * Sink that receives GuardianCommands from the sync processor and executes
 * them locally (browser-specific actions like tab blocking, policy updates).
 * Also acknowledges commands back to the backend.
 *
 * Implements the Sprint 5 connector-based local enforcement loop design.
 *
 * @module pipeline/sinks/CommandExecutionSink
 */

import { BaseEventSink } from '@ghatana/dcmaar-browser-extension-core';
import type { GuardianCommand } from '../sources/CommandSyncSource';

/**
 * Command execution result.
 */
export interface CommandResult {
    command_id: string;
    status: 'processed' | 'failed' | 'expired' | 'unsupported';
    error_reason?: string;
    executed_at: string;
}

/**
 * Internal event for command execution lifecycle.
 */
export interface CommandExecutionEvent {
    id: string;
    type: 'command_execution';
    timestamp: number;
    command: GuardianCommand;
    result: CommandResult;
}

/**
 * Configuration for CommandExecutionSink.
 */
export interface CommandExecutionSinkConfig {
    /** Backend API base URL */
    apiBaseUrl: string;
    /** Device ID for acknowledgment */
    deviceId: string;
    /** Auth token getter */
    getAuthToken: () => string | null;
    /** Whether to auto-acknowledge commands after execution */
    autoAcknowledge?: boolean;
    /** Handler for policy update commands */
    onPolicyUpdate?: (command: GuardianCommand) => Promise<void>;
    /** Handler for immediate action commands (lock, etc.) */
    onImmediateAction?: (command: GuardianCommand) => Promise<void>;
    /** Handler for session request commands (extend time, unblock) */
    onSessionRequest?: (command: GuardianCommand) => Promise<void>;
    /** Handler for data request commands (backend requests data sync) */
    onDataRequest?: (command: GuardianCommand) => Promise<void>;
    /** Handler for system commands (force sync, etc.) */
    onSystemCommand?: (command: GuardianCommand) => Promise<void>;
}

const DEFAULT_CONFIG: Omit<Required<CommandExecutionSinkConfig>, 'apiBaseUrl' | 'deviceId' | 'getAuthToken' | 'onPolicyUpdate' | 'onImmediateAction' | 'onSessionRequest' | 'onDataRequest' | 'onSystemCommand'> = {
    autoAcknowledge: true,
};

/**
 * CommandExecutionSink
 *
 * Receives GuardianCommands and executes them locally based on their kind:
 * - `policy_update`: Triggers policy refresh/application.
 * - `immediate_action`: Executes device control (lock, logout, etc.).
 * - `session_request`: Adjusts session timers (extend time, temporary unblock).
 *
 * After execution, acknowledges the command back to the backend via
 * `POST /api/devices/:id/commands/ack`.
 *
 * @example
 * ```typescript
 * const sink = new CommandExecutionSink({
 *   apiBaseUrl: 'https://api.guardian.example.com',
 *   deviceId: 'device-123',
 *   getAuthToken: () => localStorage.getItem('guardian_token'),
 *   onPolicyUpdate: async (cmd) => {
 *     // Refresh local policy cache
 *   },
 *   onImmediateAction: async (cmd) => {
 *     if (cmd.action === 'lock_device') {
 *       // Show lock screen overlay
 *     }
 *   },
 * });
 *
 * await sink.initialize();
 * await sink.executeCommand(command);
 * ```
 */
export class CommandExecutionSink extends BaseEventSink<CommandExecutionEvent> {
    readonly name = 'command-execution';

    private readonly config: Required<CommandExecutionSinkConfig>;
    private executionHistory: Map<string, CommandResult> = new Map();

    constructor(config: CommandExecutionSinkConfig) {
        super();
        this.config = {
            ...DEFAULT_CONFIG,
            onPolicyUpdate: config.onPolicyUpdate ?? (async () => { }),
            onImmediateAction: config.onImmediateAction ?? (async () => { }),
            onSessionRequest: config.onSessionRequest ?? (async () => { }),
            onDataRequest: config.onDataRequest ?? (async () => { }),
            onSystemCommand: config.onSystemCommand ?? (async () => { }),
            ...config,
        };
    }

    /**
     * Initialize the sink.
     */
    async initialize(): Promise<void> {
        console.debug('[CommandExecutionSink] Initialized');
    }

    /**
     * Shutdown the sink.
     */
    async shutdown(): Promise<void> {
        this.executionHistory.clear();
        console.debug('[CommandExecutionSink] Shutdown');
    }

    /**
     * Execute a single command.
     */
    async executeCommand(command: GuardianCommand): Promise<CommandResult> {
        // Check if already executed (idempotency)
        const existing = this.executionHistory.get(command.command_id);
        if (existing) {
            console.debug(`[CommandExecutionSink] Command ${command.command_id} already executed`);
            return existing;
        }

        // Validate target
        if (command.target.device_id !== this.config.deviceId) {
            const result = this.createResult(command, 'failed', 'Device ID mismatch');
            await this.handleResult(command, result);
            return result;
        }

        // Check expiry
        if (command.expires_at) {
            const expiresAt = new Date(command.expires_at).getTime();
            if (Date.now() > expiresAt) {
                const result = this.createResult(command, 'expired', 'Command expired before execution');
                await this.handleResult(command, result);
                return result;
            }
        }

        // Execute based on kind
        try {
            switch (command.kind) {
                case 'policy_update':
                    await this.config.onPolicyUpdate(command);
                    break;

                case 'immediate_action':
                    await this.config.onImmediateAction(command);
                    break;

                case 'session_request':
                    await this.config.onSessionRequest(command);
                    break;

                case 'data_request':
                    await this.config.onDataRequest(command);
                    break;

                case 'system':
                    await this.config.onSystemCommand(command);
                    break;

                default:
                    const result = this.createResult(command, 'unsupported', `Unknown command kind: ${command.kind}`);
                    await this.handleResult(command, result);
                    return result;
            }

            const result = this.createResult(command, 'processed');
            await this.handleResult(command, result);
            return result;
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            const result = this.createResult(command, 'failed', errorMessage);
            await this.handleResult(command, result);
            return result;
        }
    }

    /**
     * Execute multiple commands (from sync snapshot).
     */
    async executeCommands(commands: GuardianCommand[]): Promise<CommandResult[]> {
        const results: CommandResult[] = [];

        for (const command of commands) {
            const result = await this.executeCommand(command);
            results.push(result);
        }

        return results;
    }

    /**
     * Send event (for pipeline compatibility).
     */
    async send(_event: CommandExecutionEvent): Promise<void> {
        // This sink primarily uses executeCommand, but we support send for pipeline integration
        this.stats.sent++;
    }

    /**
     * Send batch of events (for pipeline compatibility).
     */
    async sendBatch(events: CommandExecutionEvent[]): Promise<void> {
        for (const event of events) {
            await this.send(event);
        }
    }

    /**
     * Get execution history for a command.
     */
    getExecutionResult(commandId: string): CommandResult | undefined {
        return this.executionHistory.get(commandId);
    }

    /**
     * Create a command result.
     */
    private createResult(
        command: GuardianCommand,
        status: CommandResult['status'],
        errorReason?: string
    ): CommandResult {
        return {
            command_id: command.command_id,
            status,
            error_reason: errorReason,
            executed_at: new Date().toISOString(),
        };
    }

    /**
     * Handle result: store, emit event, and optionally acknowledge.
     */
    private async handleResult(command: GuardianCommand, result: CommandResult): Promise<void> {
        // Store in history
        this.executionHistory.set(command.command_id, result);

        // Emit event
        const event: CommandExecutionEvent = {
            id: `exec-${command.command_id}-${Date.now()}`,
            type: 'command_execution',
            timestamp: Date.now(),
            command,
            result,
        };

        // Note: BaseEventSink doesn't have emit, so we track stats
        this.stats.sent++;

        // Auto-acknowledge if configured
        if (this.config.autoAcknowledge) {
            await this.acknowledgeCommand(result);
        }
    }

    /**
     * Acknowledge command to backend.
     */
    private async acknowledgeCommand(result: CommandResult): Promise<void> {
        const token = this.config.getAuthToken();

        if (!token) {
            console.warn('[CommandExecutionSink] No auth token, skipping acknowledgment');
            return;
        }

        try {
            const url = `${this.config.apiBaseUrl}/api/devices/${this.config.deviceId}/commands/ack`;

            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    Authorization: `Bearer ${token}`,
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    command_id: result.command_id,
                    status: result.status,
                    error_reason: result.error_reason,
                }),
            });

            if (!response.ok) {
                console.error(`[CommandExecutionSink] Ack failed: ${response.status}`);
            } else {
                console.debug(`[CommandExecutionSink] Acknowledged command ${result.command_id}`);
            }
        } catch (error) {
            console.error('[CommandExecutionSink] Ack error:', error);
        }
    }
}
