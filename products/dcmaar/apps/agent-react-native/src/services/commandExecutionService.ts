/**
 * Command Execution Service
 *
 * Receives GuardianCommands from the sync service and executes them locally
 * (platform-specific actions). Also acknowledges commands back to the backend.
 *
 * Implements the Sprint 5 connector-based local enforcement loop design.
 *
 * @module services/commandExecutionService
 */

import { create } from 'zustand';
import { subscribeWithSelector } from 'zustand/middleware';
import type { GuardianCommand } from './commandSyncService';

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
 * Command execution service configuration.
 */
export interface CommandExecutionConfig {
    apiBaseUrl: string;
    deviceId: string;
    getAuthToken: () => string | null;
    autoAcknowledge?: boolean;
}

/**
 * Command handlers for platform-specific implementations.
 */
export interface CommandHandlers {
    onPolicyUpdate?: (command: GuardianCommand) => Promise<void>;
    onImmediateAction?: (command: GuardianCommand) => Promise<void>;
    onSessionRequest?: (command: GuardianCommand) => Promise<void>;
}

/**
 * Command execution service state.
 */
interface CommandExecutionState {
    executionHistory: Map<string, CommandResult>;
    pendingCommands: GuardianCommand[];
    isProcessing: boolean;
}

/**
 * Command Execution Service store.
 */
export const useCommandExecutionStore = create<CommandExecutionState>()(
    subscribeWithSelector(() => ({
        executionHistory: new Map<string, CommandResult>(),
        pendingCommands: [] as GuardianCommand[],
        isProcessing: false as boolean,
    }))
);

// Private state
let config: CommandExecutionConfig | null = null;
let handlers: CommandHandlers = {};

/**
 * Initialize the command execution service.
 */
export function initCommandExecution(
    execConfig: CommandExecutionConfig,
    commandHandlers: CommandHandlers = {}
): void {
    config = {
        ...execConfig,
        autoAcknowledge: execConfig.autoAcknowledge ?? true,
    };
    handlers = commandHandlers;
}

/**
 * Execute a single command.
 */
export async function executeCommand(command: GuardianCommand): Promise<CommandResult> {
    if (!config) {
        return createResult(command.command_id, 'failed', 'Service not initialized');
    }

    // Check if already executed (idempotency)
    const history = useCommandExecutionStore.getState().executionHistory;
    const existing = history.get(command.command_id);
    if (existing) {
        console.debug(`[CommandExecutionService] Command ${command.command_id} already executed`);
        return existing;
    }

    // Validate target
    if (command.target.device_id !== config.deviceId) {
        const result = createResult(command.command_id, 'failed', 'Device ID mismatch');
        await handleResult(result);
        return result;
    }

    // Check expiry
    if (command.expires_at) {
        const expiresAt = new Date(command.expires_at).getTime();
        if (Date.now() > expiresAt) {
            const result = createResult(command.command_id, 'expired', 'Command expired before execution');
            await handleResult(result);
            return result;
        }
    }

    // Execute based on kind
    try {
        switch (command.kind) {
            case 'policy_update':
                if (handlers.onPolicyUpdate) {
                    await handlers.onPolicyUpdate(command);
                }
                break;

            case 'immediate_action':
                if (handlers.onImmediateAction) {
                    await handlers.onImmediateAction(command);
                }
                break;

            case 'session_request':
                if (handlers.onSessionRequest) {
                    await handlers.onSessionRequest(command);
                }
                break;

            default:
                const result = createResult(command.command_id, 'unsupported', `Unknown command kind: ${command.kind}`);
                await handleResult(result);
                return result;
        }

        const result = createResult(command.command_id, 'processed');
        await handleResult(result);
        return result;
    } catch (error) {
        const errorMessage = error instanceof Error ? error.message : String(error);
        const result = createResult(command.command_id, 'failed', errorMessage);
        await handleResult(result);
        return result;
    }
}

/**
 * Execute multiple commands.
 */
export async function executeCommands(commands: GuardianCommand[]): Promise<CommandResult[]> {
    useCommandExecutionStore.setState({ isProcessing: true });

    const results: CommandResult[] = [];
    for (const command of commands) {
        const result = await executeCommand(command);
        results.push(result);
    }

    useCommandExecutionStore.setState({ isProcessing: false });
    return results;
}

/**
 * Get execution result for a command.
 */
export function getExecutionResult(commandId: string): CommandResult | undefined {
    return useCommandExecutionStore.getState().executionHistory.get(commandId);
}

/**
 * Create a command result.
 */
function createResult(
    commandId: string,
    status: CommandResult['status'],
    errorReason?: string
): CommandResult {
    return {
        command_id: commandId,
        status,
        error_reason: errorReason,
        executed_at: new Date().toISOString(),
    };
}

/**
 * Handle result: store and optionally acknowledge.
 */
async function handleResult(result: CommandResult): Promise<void> {
    // Store in history
    useCommandExecutionStore.setState((state) => {
        const newHistory = new Map(state.executionHistory);
        newHistory.set(result.command_id, result);
        return { executionHistory: newHistory };
    });

    // Auto-acknowledge if configured
    if (config?.autoAcknowledge) {
        await acknowledgeCommand(result);
    }
}

/**
 * Acknowledge command to backend.
 */
async function acknowledgeCommand(result: CommandResult): Promise<void> {
    if (!config) {
        return;
    }

    const token = config.getAuthToken();
    if (!token) {
        console.warn('[CommandExecutionService] No auth token, skipping acknowledgment');
        return;
    }

    try {
        const url = `${config.apiBaseUrl}/api/devices/${config.deviceId}/commands/ack`;

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
            console.error(`[CommandExecutionService] Ack failed: ${response.status}`);
        } else {
            console.debug(`[CommandExecutionService] Acknowledged command ${result.command_id}`);
        }
    } catch (error) {
        console.error('[CommandExecutionService] Ack error:', error);
    }
}
