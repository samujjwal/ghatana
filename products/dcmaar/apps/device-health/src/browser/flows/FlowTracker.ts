/**
 * @fileoverview Flow Tracker
 *
 * Tracks multi-step user journeys for page usage analytics.
 *
 * @module browser/flows/FlowTracker
 */

import type { FlowEvent } from '@ghatana/dcmaar-browser-extension-core';

export interface FlowDefinition {
  id: string;
  steps: string[];
  timeoutMs?: number;
  metadata?: Record<string, unknown>;
}

interface FlowState {
  id: string;
  startedAt: number;
  definition?: FlowDefinition;
  completedSteps: string[];
  data: Record<string, unknown>;
  tabId?: number;
  timeoutHandle?: ReturnType<typeof setTimeout>;
}

type FlowEventListener = (event: FlowEvent) => void;

/**
 * Flow Tracker for user journey analysis
 */
export class FlowTracker {
  private activeFlows = new Map<string, FlowState>();
  private flowDefinitions = new Map<string, FlowDefinition>();
  private listeners = new Set<FlowEventListener>();
  private autoDetectEnabled = false;

  /**
   * Register a flow definition
   */
  registerFlow(definition: FlowDefinition): void {
    this.flowDefinitions.set(definition.id, { ...definition });
  }

  /**
   * Enable automatic flow detection
   */
  autoDetectFlows(): void {
    this.autoDetectEnabled = true;
  }

  /**
   * Start tracking a flow
   */
  startFlow(flowId: string, definition?: FlowDefinition, context?: { tabId?: number }): void {
    if (!flowId) {
      return;
    }

    if (definition) {
      this.registerFlow(definition);
    }

    const flowDefinition = definition ?? this.flowDefinitions.get(flowId);

    if (this.activeFlows.has(flowId)) {
      this.abandonFlow(flowId, 'restart');
    }

    const state: FlowState = {
      id: flowId,
      definition: flowDefinition,
      startedAt: Date.now(),
      completedSteps: [],
      data: {},
      tabId: context?.tabId,
    };

    // Setup timeout if configured
    if (flowDefinition?.timeoutMs) {
      state.timeoutHandle = setTimeout(() => {
        this.abandonFlow(flowId, 'timeout');
      }, flowDefinition.timeoutMs);
    }

    this.activeFlows.set(flowId, state);
    this.emitFlowEvent({
      type: 'flow',
      flowId,
      action: 'start',
      timestamp: state.startedAt,
      url: undefined,
      tabId: state.tabId,
      data: flowDefinition?.metadata,
    });
  }

  /**
   * Record a flow step
   */
  recordStep(flowId: string, step: string, data?: Record<string, unknown>): void {
    if (!flowId) {
      return;
    }

    const state = this.activeFlows.get(flowId);
    if (!state) {
      if (this.autoDetectEnabled) {
        this.startFlow(flowId);
        this.recordStep(flowId, step, data);
      }
      return;
    }

    if (!state.completedSteps.includes(step)) {
      state.completedSteps.push(step);
    }

    if (data) {
      state.data = { ...state.data, ...data };
    }

    this.emitFlowEvent({
      type: 'flow',
      flowId,
      action: 'step',
      step,
      timestamp: Date.now(),
      url: undefined,
      tabId: state.tabId,
      data,
    });
  }

  /**
   * Complete a flow
   */
  completeFlow(flowId: string, success: boolean, data?: Record<string, unknown>): void {
    const state = this.activeFlows.get(flowId);
    if (!state) {
      return;
    }

    if (state.timeoutHandle) {
      clearTimeout(state.timeoutHandle);
    }

    this.activeFlows.delete(flowId);

    const eventData = {
      ...state.data,
      ...data,
      success,
      durationMs: Date.now() - state.startedAt,
      stepsCompleted: [...state.completedSteps],
    };

    this.emitFlowEvent({
      type: 'flow',
      flowId,
      action: 'complete',
      timestamp: Date.now(),
      url: undefined,
      tabId: state.tabId,
      data: eventData,
    });
  }

  /**
   * Mark flow as abandoned
   */
  abandonFlow(flowId: string, reason: string, data?: Record<string, unknown>): void {
    const state = this.activeFlows.get(flowId);
    if (!state) {
      return;
    }

    if (state.timeoutHandle) {
      clearTimeout(state.timeoutHandle);
    }

    this.activeFlows.delete(flowId);

    this.emitFlowEvent({
      type: 'flow',
      flowId,
      action: reason === 'error' ? 'error' : 'abandon',
      timestamp: Date.now(),
      url: undefined,
      tabId: state.tabId,
      data: {
        ...state.data,
        ...data,
        reason,
        durationMs: Date.now() - state.startedAt,
        stepsCompleted: [...state.completedSteps],
      },
    });
  }

  /**
   * Subscribe to flow events
   */
  onFlowEvent(listener: FlowEventListener): void {
    this.listeners.add(listener);
  }

  /**
   * Unsubscribe from flow events
   */
  offFlowEvent(listener: FlowEventListener): void {
    this.listeners.delete(listener);
  }

  /**
   * Emit event to listeners
   */
  private emitFlowEvent(event: FlowEvent): void {
    this.listeners.forEach((listener) => listener(event));
  }
}
