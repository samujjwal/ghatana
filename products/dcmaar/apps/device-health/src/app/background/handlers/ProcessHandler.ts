/**
 * @fileoverview Process Handler
 *
 * Processes ProcessMessage to manage process lifecycle (start/stop/pause/resume).
 * Manages process registry, tracks process state, and handles process operations.
 *
 * Responsibilities:
 * - Validate process configuration
 * - Create/update processes in registry
 * - Manage process lifecycle (start/stop/pause/resume)
 * - Track process state
 * - Handle errors gracefully
 *
 * @example
 * ```typescript
 * const handler = new ProcessHandler();
 * const result = await handler.handle(processMessage);
 * if (result.success) {
 *   console.log('Process managed:', result.data);
 * } else {
 *   console.error('Process operation failed:', result.error);
 * }
 * ```
 */

import { devLog } from '@shared/utils/dev-logger';

import type { IProcessHandler, HandlerResult } from '../contracts/handlers';
import type { ProcessMessage } from '../contracts/messages';

/**
 * Process state
 */
type ProcessState = 'created' | 'running' | 'paused' | 'stopped' | 'error';

/**
 * Process registry entry
 */
interface ProcessEntry {
  processId: string;
  processType: string;
  state: ProcessState;
  config?: Record<string, unknown>;
  metadata?: Record<string, string>;
  createdAt: number;
  startedAt?: number;
  stoppedAt?: number;
  errorMessage?: string;
}

/**
 * Process Handler
 *
 * Handles process lifecycle messages and manages the process registry.
 * Each process represents a long-running or scheduled operation.
 *
 * Supported process types:
 * - scheduled: Scheduled/recurring process
 * - background: Background process
 * - worker: Worker process
 * - custom: Custom process implementation
 *
 * Supported actions:
 * - start: Start a process
 * - stop: Stop a process
 * - restart: Restart a process
 * - pause: Pause a process
 * - resume: Resume a process
 */
export class ProcessHandler implements IProcessHandler {
  private readonly contextName = 'ProcessHandler';
  private processRegistry: Map<string, ProcessEntry> = new Map();

  /**
   * Handle process message
   *
   * Validates the process configuration and performs the requested action.
   *
   * @param message - Process message
   * @returns Handler result with success status and data
   */
  async handle(message: ProcessMessage): Promise<HandlerResult> {
    try {
      const { processId, processType, action, config, metadata } = message.payload;

      devLog.debug(`[${this.contextName}] Processing process message`, {
        processId,
        processType,
        action,
        messageId: message.id,
      });

      // Validate process configuration
      const validation = this.validateProcess(message.payload);
      if (!validation.valid) {
        devLog.warn(`[${this.contextName}] Process validation failed`, {
          processId,
          error: validation.error,
        });
        return {
          success: false,
          error: `Process validation failed: ${validation.error}`,
        };
      }

      // Get or create process entry
      let processEntry = this.processRegistry.get(processId);
      if (!processEntry) {
        processEntry = {
          processId,
          processType,
          state: 'created',
          config,
          metadata,
          createdAt: Date.now(),
        };
      }

      // Perform action
      const result = await this.performAction(action, processEntry, config, metadata);

      if (result.success) {
        // Update registry
        this.processRegistry.set(processId, processEntry);

        devLog.info(`[${this.contextName}] Process action completed`, {
          processId,
          action,
          newState: processEntry.state,
        });
      } else {
        devLog.warn(`[${this.contextName}] Process action failed`, {
          processId,
          action,
          error: result.error,
        });
      }

      return {
        success: result.success,
        error: result.error,
        data: {
          processId,
          action,
          state: processEntry.state,
          success: result.success,
        },
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      devLog.error(`[${this.contextName}] Handler error`, {
        error: errorMessage,
        messageId: (message as any).id,
      });
      return {
        success: false,
        error: errorMessage,
      };
    }
  }

  /**
   * Perform process action
   *
   * @param action - Action to perform
   * @param processEntry - Process entry to update
   * @param config - Process configuration
   * @param metadata - Process metadata
   * @returns Action result
   */
  private async performAction(
    action: string,
    processEntry: ProcessEntry,
    config?: Record<string, unknown>,
    metadata?: Record<string, string>,
  ): Promise<{ success: boolean; error?: string }> {
    switch (action) {
      case 'start':
        return this.startProcess(processEntry, config, metadata);

      case 'stop':
        return this.stopProcess(processEntry);

      case 'restart':
        return this.restartProcess(processEntry, config, metadata);

      case 'pause':
        return this.pauseProcess(processEntry);

      case 'resume':
        return this.resumeProcess(processEntry);

      default:
        return { success: false, error: `Unknown action: ${action}` };
    }
  }

  /**
   * Start a process
   *
   * @param processEntry - Process entry
   * @param config - Process configuration
   * @param metadata - Process metadata
   * @returns Action result
   */
  private async startProcess(
    processEntry: ProcessEntry,
    config?: Record<string, unknown>,
    metadata?: Record<string, string>,
  ): Promise<{ success: boolean; error?: string }> {
    // Check if already running
    if (processEntry.state === 'running') {
      return { success: false, error: 'Process is already running' };
    }

    // Update process entry
    processEntry.state = 'running';
    processEntry.startedAt = Date.now();
    processEntry.stoppedAt = undefined;
    processEntry.errorMessage = undefined;
    if (config) processEntry.config = config;
    if (metadata) processEntry.metadata = metadata;

    return { success: true };
  }

  /**
   * Stop a process
   *
   * @param processEntry - Process entry
   * @returns Action result
   */
  private async stopProcess(processEntry: ProcessEntry): Promise<{ success: boolean; error?: string }> {
    // Check if already stopped
    if (processEntry.state === 'stopped') {
      return { success: false, error: 'Process is already stopped' };
    }

    // Update process entry
    processEntry.state = 'stopped';
    processEntry.stoppedAt = Date.now();

    return { success: true };
  }

  /**
   * Restart a process
   *
   * @param processEntry - Process entry
   * @param config - Process configuration
   * @param metadata - Process metadata
   * @returns Action result
   */
  private async restartProcess(
    processEntry: ProcessEntry,
    config?: Record<string, unknown>,
    metadata?: Record<string, string>,
  ): Promise<{ success: boolean; error?: string }> {
    // Stop process
    await this.stopProcess(processEntry);

    // Start process
    return this.startProcess(processEntry, config, metadata);
  }

  /**
   * Pause a process
   *
   * @param processEntry - Process entry
   * @returns Action result
   */
  private async pauseProcess(processEntry: ProcessEntry): Promise<{ success: boolean; error?: string }> {
    // Check if running
    if (processEntry.state !== 'running') {
      return { success: false, error: 'Process is not running' };
    }

    // Update process entry
    processEntry.state = 'paused';

    return { success: true };
  }

  /**
   * Resume a process
   *
   * @param processEntry - Process entry
   * @returns Action result
   */
  private async resumeProcess(processEntry: ProcessEntry): Promise<{ success: boolean; error?: string }> {
    // Check if paused
    if (processEntry.state !== 'paused') {
      return { success: false, error: 'Process is not paused' };
    }

    // Update process entry
    processEntry.state = 'running';

    return { success: true };
  }

  /**
   * Validate process configuration
   *
   * @param config - Process configuration to validate
   * @returns Validation result
   */
  private validateProcess(config: {
    processId: string;
    processType: string;
    action: string;
    config?: Record<string, unknown>;
    metadata?: Record<string, string>;
  }): { valid: boolean; error?: string } {
    // Validate processId
    if (!config.processId || config.processId.trim().length === 0) {
      return { valid: false, error: 'processId is required and cannot be empty' };
    }

    // Validate processType
    const validProcessTypes = ['scheduled', 'background', 'worker', 'custom'];
    if (!validProcessTypes.includes(config.processType)) {
      return {
        valid: false,
        error: `processType must be one of: ${validProcessTypes.join(', ')}`,
      };
    }

    // Validate action
    const validActions = ['start', 'stop', 'restart', 'pause', 'resume'];
    if (!validActions.includes(config.action)) {
      return {
        valid: false,
        error: `action must be one of: ${validActions.join(', ')}`,
      };
    }

    return { valid: true };
  }

  /**
   * Get a process from the registry
   *
   * @param processId - ID of process to retrieve
   * @returns Process entry or undefined if not found
   */
  getProcess(processId: string): ProcessEntry | undefined {
    return this.processRegistry.get(processId);
  }

  /**
   * Get all processes from the registry
   *
   * @returns Array of all process entries
   */
  getAllProcesses(): ProcessEntry[] {
    return Array.from(this.processRegistry.values());
  }

  /**
   * Get running processes
   *
   * @returns Array of running process entries
   */
  getRunningProcesses(): ProcessEntry[] {
    return Array.from(this.processRegistry.values()).filter((p) => p.state === 'running');
  }

  /**
   * Remove a process from the registry
   *
   * @param processId - ID of process to remove
   * @returns True if process was removed, false if not found
   */
  removeProcess(processId: string): boolean {
    const existed = this.processRegistry.has(processId);
    if (existed) {
      this.processRegistry.delete(processId);
      devLog.info(`[${this.contextName}] Process removed`, { processId });
    }
    return existed;
  }

  /**
   * Clear all processes from the registry
   */
  clearAllProcesses(): void {
    const count = this.processRegistry.size;
    this.processRegistry.clear();
    devLog.info(`[${this.contextName}] All processes cleared`, { count });
  }

  /**
   * Get process registry status
   *
   * @returns Status object with process counts
   */
  getStatus(): {
    totalProcesses: number;
    runningProcesses: number;
    pausedProcesses: number;
    stoppedProcesses: number;
    processesByType: Record<string, number>;
  } {
    const processes = Array.from(this.processRegistry.values());
    const processesByType: Record<string, number> = {};

    processes.forEach((process) => {
      processesByType[process.processType] = (processesByType[process.processType] || 0) + 1;
    });

    return {
      totalProcesses: processes.length,
      runningProcesses: processes.filter((p) => p.state === 'running').length,
      pausedProcesses: processes.filter((p) => p.state === 'paused').length,
      stoppedProcesses: processes.filter((p) => p.state === 'stopped').length,
      processesByType,
    };
  }
}
