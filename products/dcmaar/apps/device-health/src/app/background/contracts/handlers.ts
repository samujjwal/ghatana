/**
 * @fileoverview Message Handlers for Extension Contracts
 *
 * Routes incoming messages to appropriate handlers based on message type.
 * Each handler is responsible for processing a specific message category:
 * - SinkConfigMessage → sink configuration management
 * - CommandMessage → command execution
 * - ProcessMessage → process lifecycle management
 *
 * Handlers are decoupled from message routing to enable independent testing
 * and reusability across different contexts.
 */

import { EventEmitter } from 'eventemitter3';
import { devLog } from '@shared/utils/dev-logger';

import type {
  AnyMessage,
  SinkConfigMessage,
  CommandMessage,
  ProcessMessage,
} from './messages';
import {
  isSinkConfigMessage,
  isCommandMessage,
  isProcessMessage,
  validateMessage,
} from './messages';

/**
 * Handler result for all message types
 */
export interface HandlerResult {
  success: boolean;
  error?: string;
  data?: unknown;
}

/**
 * Sink configuration handler
 *
 * Processes sink configuration messages and updates sink registry.
 * Responsible for:
 * - Validating sink configuration
 * - Creating/updating sinks
 * - Managing sink lifecycle
 */
export interface ISinkConfigHandler {
  handle(message: SinkConfigMessage): Promise<HandlerResult>;
}

/**
 * Command handler
 *
 * Processes command messages and executes commands.
 * Responsible for:
 * - Command validation
 * - Command execution
 * - Response handling
 */
export interface ICommandHandler {
  handle(message: CommandMessage): Promise<HandlerResult>;
}

/**
 * Process handler
 *
 * Processes process lifecycle messages.
 * Responsible for:
 * - Process creation/lifecycle
 * - Process state management
 * - Process cleanup
 */
export interface IProcessHandler {
  handle(message: ProcessMessage): Promise<HandlerResult>;
}

/**
 * Message handler events
 */
type MessageHandlerEvent =
  | 'message-received'
  | 'message-validated'
  | 'message-handled'
  | 'message-error'
  | 'handler-registered'
  | 'handler-error';

/**
 * Message Router
 *
 * Routes messages to appropriate handlers based on message type.
 * Provides a registry for handlers and manages message processing pipeline.
 *
 * @example
 * ```typescript
 * const router = new MessageRouter();
 * router.registerSinkConfigHandler(sinkHandler);
 * router.registerCommandHandler(commandHandler);
 * router.registerProcessHandler(processHandler);
 *
 * router.on('message-handled', (msg) => {
 *   console.log('Message processed:', msg.id);
 * });
 *
 * await router.route(incomingMessage);
 * ```
 */
export class MessageRouter extends EventEmitter<MessageHandlerEvent> {
  private sinkConfigHandler?: ISinkConfigHandler;
  private commandHandler?: ICommandHandler;
  private processHandler?: IProcessHandler;
  private readonly contextName: string;

  constructor(contextName: string = 'MessageRouter') {
    super();
    this.contextName = contextName;
  }

  /**
   * Register sink configuration handler
   */
  registerSinkConfigHandler(handler: ISinkConfigHandler): void {
    this.sinkConfigHandler = handler;
    this.emit('handler-registered', { type: 'sink-config' });
    devLog.debug(`[${this.contextName}] Sink config handler registered`);
  }

  /**
   * Register command handler
   */
  registerCommandHandler(handler: ICommandHandler): void {
    this.commandHandler = handler;
    this.emit('handler-registered', { type: 'command' });
    devLog.debug(`[${this.contextName}] Command handler registered`);
  }

  /**
   * Register process handler
   */
  registerProcessHandler(handler: IProcessHandler): void {
    this.processHandler = handler;
    this.emit('handler-registered', { type: 'process' });
    devLog.debug(`[${this.contextName}] Process handler registered`);
  }

  /**
   * Route a message to the appropriate handler
   *
   * @param message - Raw message to route
   * @returns Handler result
   */
  async route(message: unknown): Promise<HandlerResult> {
    try {
      // Emit received event
      this.emit('message-received', { message });

      // Validate message
      const validation = validateMessage(message);
      if (!validation.valid) {
        const error = `Invalid message: ${validation.error}`;
        this.emit('message-error', { error, message });
        devLog.warn(`[${this.contextName}] ${error}`);
        return { success: false, error };
      }

      const validMessage = validation.data!;
      this.emit('message-validated', { message: validMessage });

      // Route to appropriate handler
      let result: HandlerResult;

      if (isSinkConfigMessage(validMessage)) {
        result = await this.handleSinkConfig(validMessage);
      } else if (isCommandMessage(validMessage)) {
        result = await this.handleCommand(validMessage);
      } else if (isProcessMessage(validMessage)) {
        result = await this.handleProcess(validMessage);
      } else {
        const error = `Unknown message type: ${(validMessage as any).type}`;
        this.emit('message-error', { error, message: validMessage });
        return { success: false, error };
      }

      // Emit handled event
      this.emit('message-handled', { message: validMessage, result });

      return result;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      this.emit('message-error', { error: errorMessage, message });
      devLog.error(`[${this.contextName}] Message routing error`, { error: errorMessage });
      return { success: false, error: errorMessage };
    }
  }

  /**
   * Handle sink configuration message
   */
  private async handleSinkConfig(message: SinkConfigMessage): Promise<HandlerResult> {
    if (!this.sinkConfigHandler) {
      const error = 'No sink config handler registered';
      devLog.warn(`[${this.contextName}] ${error}`);
      return { success: false, error };
    }

    try {
      devLog.debug(`[${this.contextName}] Handling sink config message`, {
        sinkId: message.payload.sinkId,
        messageId: message.id,
      });
      return await this.sinkConfigHandler.handle(message);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      this.emit('handler-error', { type: 'sink-config', error: errorMessage });
      devLog.error(`[${this.contextName}] Sink config handler error`, { error: errorMessage });
      return { success: false, error: errorMessage };
    }
  }

  /**
   * Handle command message
   */
  private async handleCommand(message: CommandMessage): Promise<HandlerResult> {
    if (!this.commandHandler) {
      const error = 'No command handler registered';
      devLog.warn(`[${this.contextName}] ${error}`);
      return { success: false, error };
    }

    try {
      devLog.debug(`[${this.contextName}] Handling command message`, {
        commandType: message.payload.commandType,
        messageId: message.id,
      });
      return await this.commandHandler.handle(message);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      this.emit('handler-error', { type: 'command', error: errorMessage });
      devLog.error(`[${this.contextName}] Command handler error`, { error: errorMessage });
      return { success: false, error: errorMessage };
    }
  }

  /**
   * Handle process message
   */
  private async handleProcess(message: ProcessMessage): Promise<HandlerResult> {
    if (!this.processHandler) {
      const error = 'No process handler registered';
      devLog.warn(`[${this.contextName}] ${error}`);
      return { success: false, error };
    }

    try {
      devLog.debug(`[${this.contextName}] Handling process message`, {
        processId: message.payload.processId,
        action: message.payload.action,
        messageId: message.id,
      });
      return await this.processHandler.handle(message);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      this.emit('handler-error', { type: 'process', error: errorMessage });
      devLog.error(`[${this.contextName}] Process handler error`, { error: errorMessage });
      return { success: false, error: errorMessage };
    }
  }

  /**
   * Check if all required handlers are registered
   */
  isReady(): boolean {
    return !!(this.sinkConfigHandler && this.commandHandler && this.processHandler);
  }

  /**
   * Get handler registration status
   */
  getStatus(): {
    sinkConfigHandlerRegistered: boolean;
    commandHandlerRegistered: boolean;
    processHandlerRegistered: boolean;
    ready: boolean;
  } {
    return {
      sinkConfigHandlerRegistered: !!this.sinkConfigHandler,
      commandHandlerRegistered: !!this.commandHandler,
      processHandlerRegistered: !!this.processHandler,
      ready: this.isReady(),
    };
  }
}
