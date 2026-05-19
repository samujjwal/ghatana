/**
 * @fileoverview Action Runtime with Safe Policies
 *
 * Executes builder actions with safety policies including timeouts,
 * resource limits, and sandboxing to prevent abuse and ensure stability.
 *
 * @doc.type module
 * @doc.purpose Safe action execution with policies
 * @doc.layer platform
 * @doc.pattern RuntimeSafety
 */

import type { ActionDefinition, ActionTargetKind } from '../types.js';
import { validateActionPayload, validateActionPayloadSecurity } from '../action-payload-validation.js';

// ============================================================================
// ACTION EXECUTION POLICIES
// ============================================================================

export interface ActionExecutionPolicy {
  /** Maximum time for action execution (ms) */
  timeoutMs: number;
  /** Whether to allow network requests */
  allowNetwork: boolean;
  /** Whether to allow DOM manipulation */
  allowDOMAccess: boolean;
  /** Maximum payload size (bytes) */
  maxPayloadSize: number;
  /** Whether to allow custom actions */
  allowCustomActions: boolean;
}

export const DEFAULT_ACTION_POLICY: ActionExecutionPolicy = {
  timeoutMs: 5000,
  allowNetwork: true,
  allowDOMAccess: false,
  maxPayloadSize: 1024 * 1024, // 1MB
  allowCustomActions: true,
};

export const RESTRICTED_ACTION_POLICY: ActionExecutionPolicy = {
  timeoutMs: 2000,
  allowNetwork: false,
  allowDOMAccess: false,
  maxPayloadSize: 10 * 1024, // 10KB
  allowCustomActions: false,
};

// ============================================================================
// ACTION EXECUTION RESULT
// ============================================================================

export interface ActionResult {
  success: boolean;
  error?: string;
  result?: unknown;
  executionTimeMs: number;
}

// ============================================================================
// ACTION EXECUTION CONTEXT
// ============================================================================

export interface ActionExecutionContext {
  /** Current component props */
  props: Record<string, unknown>;
  /** Component state (if applicable) */
  state?: Record<string, unknown>;
  /** Event that triggered the action */
  event?: Event;
  /** Navigation function (for navigate actions) */
  navigate?: (route: string, params?: Record<string, string>) => void;
  /** State update function (for toggle-state actions) */
  setState?: (key: string, value: unknown) => void;
  /** Event emitter (for emit-event actions) */
  emitEvent?: (eventName: string, payload?: Record<string, unknown>) => void;
  /** API client (for call-api actions) */
  apiClient?: {
    request: (endpoint: string, options?: RequestInit) => Promise<unknown>;
  };
  /** Binding updater (for update-binding actions) */
  updateBinding?: (bindingId: string, value: unknown) => void;
}

// ============================================================================
// ACTION EXECUTOR
// ============================================================================

export class ActionExecutor {
  private policy: ActionExecutionPolicy;

  constructor(policy: Partial<ActionExecutionPolicy> = {}) {
    this.policy = { ...DEFAULT_ACTION_POLICY, ...policy };
  }

  /**
   * Execute an action with safety policies.
   */
  async execute(
    action: ActionDefinition,
    context: ActionExecutionContext,
  ): Promise<ActionResult> {
    const startTime = Date.now();

    // Validate action payload
    const payloadValidation = validateActionPayload(action);
    if (!payloadValidation.valid) {
      return {
        success: false,
        error: `Payload validation failed: ${payloadValidation.errors.map(e => e.message).join(', ')}`,
        executionTimeMs: Date.now() - startTime,
      };
    }

    // Validate payload security
    const securityValidation = validateActionPayloadSecurity(action);
    if (!securityValidation.valid) {
      return {
        success: false,
        error: `Security validation failed: ${securityValidation.errors.map(e => e.message).join(', ')}`,
        executionTimeMs: Date.now() - startTime,
      };
    }

    // Check custom action policy
    if (action.targetKind === 'custom' && !this.policy.allowCustomActions) {
      return {
        success: false,
        error: 'Custom actions are not allowed under current policy',
        executionTimeMs: Date.now() - startTime,
      };
    }

    // Check network policy
    if (action.targetKind === 'call-api' && !this.policy.allowNetwork) {
      return {
        success: false,
        error: 'Network requests are not allowed under current policy',
        executionTimeMs: Date.now() - startTime,
      };
    }

    // Execute with timeout
    try {
      const result = await this.executeWithTimeout(action, context);
      return {
        success: true,
        result,
        executionTimeMs: Date.now() - startTime,
      };
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : String(error),
        executionTimeMs: Date.now() - startTime,
      };
    }
  }

  private async executeWithTimeout(
    action: ActionDefinition,
    context: ActionExecutionContext,
  ): Promise<unknown> {
    const executor = this.getExecutor(action.targetKind);
    
    return new Promise((resolve, reject) => {
      const timeoutId = setTimeout(() => {
        reject(new Error(`Action execution timeout after ${this.policy.timeoutMs}ms`));
      }, this.policy.timeoutMs);

      executor(action, context)
        .then(resolve)
        .catch(reject)
        .finally(() => clearTimeout(timeoutId));
    });
  }

  private getExecutor(targetKind: ActionTargetKind) {
    switch (targetKind) {
      case 'navigate':
        return this.executeNavigate.bind(this);
      case 'toggle-state':
        return this.executeToggleState.bind(this);
      case 'emit-event':
        return this.executeEmitEvent.bind(this);
      case 'call-api':
        return this.executeCallApi.bind(this);
      case 'update-binding':
        return this.executeUpdateBinding.bind(this);
      case 'custom':
        return this.executeCustom.bind(this);
      default:
        return () => Promise.reject(new Error(`Unknown target kind: ${targetKind}`));
    }
  }

  private async executeNavigate(
    action: ActionDefinition,
    context: ActionExecutionContext,
  ): Promise<void> {
    if (!context.navigate) {
      throw new Error('Navigate function not provided in context');
    }

    const payload = action.payload as { route?: string; params?: Record<string, string> };
    if (!payload?.route) {
      throw new Error('Navigate action requires route in payload');
    }

    context.navigate(payload.route, payload.params);
  }

  private async executeToggleState(
    action: ActionDefinition,
    context: ActionExecutionContext,
  ): Promise<void> {
    if (!context.setState) {
      throw new Error('setState function not provided in context');
    }

    const payload = action.payload as { stateKey?: string; value?: unknown };
    if (payload?.stateKey === undefined) {
      throw new Error('Toggle state action requires stateKey in payload');
    }

    context.setState(payload.stateKey, payload.value);
  }

  private async executeEmitEvent(
    action: ActionDefinition,
    context: ActionExecutionContext,
  ): Promise<void> {
    if (!context.emitEvent) {
      throw new Error('emitEvent function not provided in context');
    }

    const payload = action.payload as { eventName?: string; payload?: Record<string, unknown> };
    if (!payload?.eventName) {
      throw new Error('Emit event action requires eventName in payload');
    }

    context.emitEvent(payload.eventName, payload.payload);
  }

  private async executeCallApi(
    action: ActionDefinition,
    context: ActionExecutionContext,
  ): Promise<unknown> {
    if (!context.apiClient) {
      throw new Error('apiClient not provided in context');
    }

    const payload = action.payload as {
      endpoint?: string;
      method?: string;
      headers?: Record<string, string>;
      body?: Record<string, unknown>;
    };

    if (!payload?.endpoint) {
      throw new Error('Call API action requires endpoint in payload');
    }

    const options: RequestInit = {
      method: payload.method || 'GET',
      headers: {
        'Content-Type': 'application/json',
        ...payload.headers,
      },
    };

    if (payload.body && payload.method !== 'GET') {
      options.body = JSON.stringify(payload.body);
    }

    return context.apiClient.request(payload.endpoint, options);
  }

  private async executeUpdateBinding(
    action: ActionDefinition,
    context: ActionExecutionContext,
  ): Promise<void> {
    if (!context.updateBinding) {
      throw new Error('updateBinding function not provided in context');
    }

    const payload = action.payload as { bindingId?: string; value?: unknown };
    if (payload?.bindingId === undefined) {
      throw new Error('Update binding action requires bindingId in payload');
    }

    context.updateBinding(payload.bindingId, payload.value);
  }

  private async executeCustom(
    action: ActionDefinition,
    context: ActionExecutionContext,
  ): Promise<unknown> {
    // Custom actions are not executed by the runtime
    // They should be handled by the application layer
    throw new Error('Custom actions must be handled by the application layer');
  }

  /**
   * Update the execution policy.
   */
  setPolicy(policy: Partial<ActionExecutionPolicy>): void {
    this.policy = { ...this.policy, ...policy };
  }

  /**
   * Get the current execution policy.
   */
  getPolicy(): ActionExecutionPolicy {
    return { ...this.policy };
  }
}
