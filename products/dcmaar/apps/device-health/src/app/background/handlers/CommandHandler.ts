/**
 * @fileoverview Command Handler
 *
 * Processes CommandMessage to execute commands in the extension.
 * Routes commands to appropriate executors and manages command lifecycle
 * including timeout handling and response management.
 *
 * Responsibilities:
 * - Validate command configuration
 * - Route commands to appropriate executors
 * - Manage command execution with timeout
 * - Handle command responses
 * - Handle errors gracefully
 *
 * @example
 * ```typescript
 * const handler = new CommandHandler();
 * const result = await handler.handle(commandMessage);
 * if (result.success) {
 *   console.log('Command executed:', result.data);
 * } else {
 *   console.error('Command failed:', result.error);
 * }
 * ```
 */

import browser from 'webextension-polyfill';

import ADMIN_CONFIG from '@shared/config/addon-admin';
import { devLog } from '@shared/utils/dev-logger';

import type { HandlerResult, ICommandHandler } from '../contracts/handlers';
import type { CommandMessage } from '../contracts/messages';
import { isAdminSession, setAdminSession } from '../types';

// Legacy addon/receive manager stubs - functionality moved to ExtensionController
const addonManager = {
  listAddons: async () => [],
  getEnabledAddons: async () => [],
  enableAddon: async (_id: string) => {},
  disableAddon: async (_id: string) => {},
  installAddon: async (
    _manifest: unknown,
    _files: unknown,
    _signature?: string,
    _keyId?: string
  ) => {},
  verifySignature: async (_content: ArrayBuffer, _signature: string, _keyId: string) => false,
  listAuditEntries: async () => [],
  clearAddons: async () => {},
  getAddonCode: async (_id: string, _entry: string) => null,
};

const receiveManager = {
  setConfig: async (_config: unknown) => {},
  getReceiveConfig: async () => ({
    enabled: false,
    url: '',
    pollIntervalMinutes: 10,
    autoEnable: false,
  }),
};

/**
 * Command executor function
 */
type CommandExecutor = (args?: Record<string, unknown>) => Promise<unknown>;

/**
 * Command execution result
 */
interface CommandExecutionResult {
  commandType: string;
  executed: boolean;
  result?: unknown;
  error?: string;
  duration: number;
}

/**
 * Command Handler
 *
 * Handles command messages and manages command execution.
 * Supports registering custom command executors and executing
 * commands with timeout and error handling.
 *
 * Built-in commands:
 * - ping: Check if extension is alive
 * - status: Get extension status
 * - version: Get extension version
 */
export class CommandHandler implements ICommandHandler {
  private readonly contextName = 'CommandHandler';
  private commandExecutors: Map<string, CommandExecutor> = new Map();
  private readonly defaultTimeout = 30000; // 30 seconds

  constructor() {
    // Register built-in commands
    this.registerCommand('ping', this.handlePing.bind(this));
    this.registerCommand('status', this.handleStatus.bind(this));
    this.registerCommand('version', this.handleVersion.bind(this));

    // Legacy admin/addon/receive commands wired as contract commands
    this.registerCommand('addons.list', async () => addonManager.listAddons());
    this.registerCommand('addons.enabled', async () => addonManager.getEnabledAddons());
    this.registerCommand('addons.enable', async (args) => {
      const id = String(args?.id || '');
      if (!id) throw new Error('missing id');
      if (!isAdminSession()) throw new Error('admin_required');
      await addonManager.enableAddon(id);
      return { ok: true };
    });
    this.registerCommand('addons.disable', async (args) => {
      const id = String(args?.id || '');
      if (!id) throw new Error('missing id');
      if (!isAdminSession()) throw new Error('admin_required');
      await addonManager.disableAddon(id);
      return { ok: true };
    });
    this.registerCommand('addons.install', async (args) => {
      if (!isAdminSession()) throw new Error('admin_required');
      const manifest = (args?.manifest || {}) as Record<string, unknown>;
      const files = (args?.files || {}) as Record<string, string>;
      const signature = args?.signature ? String(args.signature) : undefined;
      const keyId = args?.keyId ? String(args.keyId) : undefined;
      if (!manifest || typeof manifest.id !== 'string' || typeof manifest.entry !== 'string') {
        throw new Error('invalid manifest');
      }
      const m = {
        id: String(manifest.id),
        name: String((manifest as any).name || (manifest as any).id),
        version: String((manifest as any).version || '0.0.0'),
        entry: String(manifest.entry),
        capabilities: Array.isArray((manifest as any).capabilities)
          ? ((manifest as any).capabilities as string[])
          : [],
      };
      await addonManager.installAddon(m, files, signature, keyId);
      return { ok: true };
    });
    this.registerCommand('addons.audit', async () => addonManager.listAuditEntries());
    this.registerCommand('addons.clear', async () => {
      if (!isAdminSession()) throw new Error('admin_required');
      await addonManager.clearAddons();
      return { ok: true };
    });
    this.registerCommand('addons.getCode', async (args) => {
      const id = String(args?.id || '');
      const entry = String(args?.entry || '');
      if (!id || !entry) throw new Error('missing id or entry');
      const code = await addonManager.getAddonCode(id, entry);
      return { ok: !!code, code };
    });

    this.registerCommand('receive.getConfig', async () => receiveManager.getReceiveConfig());
    this.registerCommand('receive.setConfig', async (args) => {
      const cfg = (args?.config || {}) as Record<string, unknown>;
      await receiveManager.setConfig({
        enabled: !!cfg.enabled,
        url: String(cfg.url || ''),
        pollIntervalMinutes: Number(cfg.pollIntervalMinutes || 10),
        autoEnable: !!cfg.autoEnable,
      });
      return { ok: true };
    });

    this.registerCommand('test.getSink', async () => {
      const key = 'dcmaar_test_sink_v1';
      const obj = await browser.storage.local.get(key);
      return { ok: true, entries: (obj as any)[key] || [] };
    });

    this.registerCommand('admin.unlock', async (args) => {
      const secret = typeof args?.secret === 'string' ? args.secret : '';
      if (secret === ADMIN_CONFIG.ADMIN_SECRET) {
        setAdminSession(true);
        setTimeout(() => setAdminSession(false), 60 * 60 * 1000);
        return { ok: true };
      }
      throw new Error('invalid_secret');
    });

    this.registerCommand('ingest.test.enqueue', async (args) => {
      // no-op: background will handle via port path; keep for completeness
      return { ok: true, queued: true, payload: args?.payload ?? {} };
    });
  }

  /**
   * Handle command message
   *
   * Validates the command and executes it with timeout handling.
   *
   * @param message - Command message
   * @returns Handler result with success status and data
   */
  async handle(message: CommandMessage): Promise<HandlerResult> {
    try {
      const { commandType, args, timeout, expectsResponse } = message.payload;
      const startTime = Date.now();

      devLog.debug(`[${this.contextName}] Processing command`, {
        commandType,
        timeout,
        expectsResponse,
        messageId: message.id,
      });

      // Validate command
      const validation = this.validateCommand(message.payload);
      if (!validation.valid) {
        devLog.warn(`[${this.contextName}] Command validation failed`, {
          commandType,
          error: validation.error,
        });
        return {
          success: false,
          error: `Command validation failed: ${validation.error}`,
        };
      }

      // Get executor
      const executor = this.commandExecutors.get(commandType);
      if (!executor) {
        devLog.warn(`[${this.contextName}] Unknown command type`, { commandType });
        return {
          success: false,
          error: `Unknown command type: ${commandType}`,
        };
      }

      // Execute command with timeout
      const commandTimeout = timeout || this.defaultTimeout;
      const result = await this.executeWithTimeout(executor, args, commandTimeout);

      const duration = Date.now() - startTime;

      devLog.info(`[${this.contextName}] Command executed`, {
        commandType,
        duration,
        success: result.executed,
      });

      return {
        success: result.executed,
        error: result.error,
        data: {
          commandType,
          executed: result.executed,
          result: result.result,
          duration,
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
   * Register a command executor
   *
   * @param commandType - Type of command
   * @param executor - Function to execute the command
   */
  registerCommand(commandType: string, executor: CommandExecutor): void {
    this.commandExecutors.set(commandType, executor);
    devLog.debug(`[${this.contextName}] Command registered`, { commandType });
  }

  /**
   * Unregister a command executor
   *
   * @param commandType - Type of command to unregister
   * @returns True if command was unregistered, false if not found
   */
  unregisterCommand(commandType: string): boolean {
    const existed = this.commandExecutors.has(commandType);
    if (existed) {
      this.commandExecutors.delete(commandType);
      devLog.debug(`[${this.contextName}] Command unregistered`, { commandType });
    }
    return existed;
  }

  /**
   * Validate command
   *
   * @param config - Command configuration to validate
   * @returns Validation result
   */
  private validateCommand(config: {
    commandType: string;
    args?: Record<string, unknown>;
    timeout?: number;
    expectsResponse: boolean;
  }): { valid: boolean; error?: string } {
    // Validate commandType
    if (!config.commandType || config.commandType.trim().length === 0) {
      return { valid: false, error: 'commandType is required and cannot be empty' };
    }

    // Validate timeout if provided
    if (config.timeout !== undefined) {
      if (typeof config.timeout !== 'number' || config.timeout <= 0) {
        return { valid: false, error: 'timeout must be a positive number' };
      }
      if (config.timeout > 300000) {
        // 5 minutes max
        return { valid: false, error: 'timeout cannot exceed 5 minutes' };
      }
    }

    // Validate expectsResponse
    if (typeof config.expectsResponse !== 'boolean') {
      return { valid: false, error: 'expectsResponse must be a boolean' };
    }

    return { valid: true };
  }

  /**
   * Execute command with timeout
   *
   * @param executor - Command executor function
   * @param args - Command arguments
   * @param timeout - Timeout in milliseconds
   * @returns Execution result
   */
  private async executeWithTimeout(
    executor: CommandExecutor,
    args: Record<string, unknown> | undefined,
    timeout: number
  ): Promise<CommandExecutionResult> {
    return new Promise((resolve) => {
      let timeoutId: NodeJS.Timeout | null = null;
      let completed = false;

      // Set timeout
      timeoutId = setTimeout(() => {
        if (!completed) {
          completed = true;
          resolve({
            commandType: 'unknown',
            executed: false,
            error: `Command execution timeout after ${timeout}ms`,
            duration: timeout,
          });
        }
      }, timeout);

      // Execute command
      executor(args)
        .then((result) => {
          if (!completed) {
            completed = true;
            if (timeoutId) clearTimeout(timeoutId);
            resolve({
              commandType: 'unknown',
              executed: true,
              result,
              duration: 0,
            });
          }
        })
        .catch((error) => {
          if (!completed) {
            completed = true;
            if (timeoutId) clearTimeout(timeoutId);
            const errorMessage = error instanceof Error ? error.message : String(error);
            resolve({
              commandType: 'unknown',
              executed: false,
              error: errorMessage,
              duration: 0,
            });
          }
        });
    });
  }

  /**
   * Built-in command: ping
   *
   * Simple health check command
   */
  private async handlePing(): Promise<unknown> {
    return { status: 'pong', timestamp: Date.now() };
  }

  /**
   * Built-in command: status
   *
   * Get extension status
   */
  private async handleStatus(): Promise<unknown> {
    return {
      status: 'running',
      timestamp: Date.now(),
      registeredCommands: Array.from(this.commandExecutors.keys()),
    };
  }

  /**
   * Built-in command: version
   *
   * Get extension version
   */
  private async handleVersion(): Promise<unknown> {
    try {
      const version = browser.runtime.getManifest().version;
      return { version };
    } catch {
      return { version: '0.1.0' };
    }
  }

  /**
   * Get list of registered commands
   *
   * @returns Array of command types
   */
  getRegisteredCommands(): string[] {
    return Array.from(this.commandExecutors.keys());
  }

  /**
   * Check if a command is registered
   *
   * @param commandType - Type of command to check
   * @returns True if command is registered
   */
  hasCommand(commandType: string): boolean {
    return this.commandExecutors.has(commandType);
  }

  /**
   * Get command handler status
   *
   * @returns Status object
   */
  getStatus(): {
    registeredCommands: number;
    commands: string[];
  } {
    return {
      registeredCommands: this.commandExecutors.size,
      commands: Array.from(this.commandExecutors.keys()),
    };
  }
}
