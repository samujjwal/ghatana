/**
 * @fileoverview Human Control Plane - autonomy policy and execution modes.
 */

import type { CorrelationId, SessionId, PlatformEvent } from '../events/base';

/** Node.js process type declaration for isomorphic usage. */
declare const process: {
  env: Record<string, string | undefined>;
} | undefined;

/** Execution modes for human control plane. */
export type AutonomyExecutionMode =
  | 'AUTONOMOUS_ASSISTED'
  | 'HUMAN_REVIEW_REQUIRED'
  | 'HUMAN_ONLY';

/** All valid autonomy execution modes. */
export const AUTONOMY_EXECUTION_MODES: readonly AutonomyExecutionMode[] = [
  'AUTONOMOUS_ASSISTED',
  'HUMAN_REVIEW_REQUIRED',
  'HUMAN_ONLY',
] as const;

/** Validates an autonomy execution mode. */
export function isValidAutonomyExecutionMode(
  mode: string
): mode is AutonomyExecutionMode {
  return AUTONOMY_EXECUTION_MODES.includes(mode as AutonomyExecutionMode);
}

/** Autonomy mode changed event payload. */
export interface AutonomyModeChangedPayload {
  readonly previousMode: AutonomyExecutionMode;
  readonly newMode: AutonomyExecutionMode;
  readonly changedBy: 'user' | 'system' | 'emergency-kill-switch';
  readonly reason?: string;
  readonly scope: 'global' | 'session' | 'surface';
  readonly surfaceId?: string; // canvas, builder, preview, etc.
}

/** Autonomy mode violation event payload. */
export interface AutonomyModeViolationPayload {
  readonly attemptedAction: string;
  readonly requiredMode: AutonomyExecutionMode;
  readonly currentMode: AutonomyExecutionMode;
  readonly blockedBy: 'implicit-ai-disabled' | 'review-required' | 'human-only-policy';
  readonly actor: 'user' | 'ai' | 'system';
  readonly suggestionId?: string;
  readonly reason: string;
}

/** Type alias for autonomy mode changed event. */
export type AutonomyModeChangedEvent = PlatformEvent<AutonomyModeChangedPayload>;

/** Type alias for autonomy mode violation event. */
export type AutonomyModeViolationEvent = PlatformEvent<AutonomyModeViolationPayload>;

/** Event names for autonomy mode changes. */
export const AUTONOMY_EVENT_NAMES = {
  MODE_CHANGED: 'autonomyMode.changed',
  VIOLATION_BLOCKED: 'autonomyMode.violation.blocked',
} as const;

/** Creates an autonomy mode changed event. */
export function createAutonomyModeChangedEvent(
  payload: AutonomyModeChangedPayload,
  correlationId?: CorrelationId
): AutonomyModeChangedEvent {
  const timestamp = new Date().toISOString();
  const correlation = correlationId ?? (generateUUID() as CorrelationId);
  const sessionId = generateSessionId();

  return {
    name: AUTONOMY_EVENT_NAMES.MODE_CHANGED,
    correlationId: correlation,
    sessionId,
    timestamp,
    source: 'platform-events',
    actor: payload.changedBy === 'user' ? 'user' : 'system',
    triggeredBy: payload.changedBy === 'user' ? 'explicit' : 'implicit',
    payload,
  };
}

/** Creates an autonomy mode violation event. */
export function createAutonomyModeViolationEvent(
  payload: AutonomyModeViolationPayload,
  correlationId?: CorrelationId
): AutonomyModeViolationEvent {
  const timestamp = new Date().toISOString();
  const correlation = correlationId ?? (generateUUID() as CorrelationId);
  const sessionId = generateSessionId();

  return {
    name: AUTONOMY_EVENT_NAMES.VIOLATION_BLOCKED,
    correlationId: correlation,
    sessionId,
    timestamp,
    source: 'platform-events',
    actor: payload.actor,
    triggeredBy: 'implicit',
    payload,
  };
}

/** Policy enforcer for autonomy modes. */
export interface AutonomyPolicyEnforcer {
  readonly currentMode: AutonomyExecutionMode;
  readonly scope: 'global' | 'session' | 'surface';
  readonly surfaceId?: string;

  /**
   * Check if an action is allowed under current policy.
   * Returns allowed=true if action can proceed, with optional review requirement.
   */
  checkAction(
    action: string,
    isImplicitAI: boolean,
    riskLevel: 'low' | 'medium' | 'high' | 'critical'
  ): { readonly allowed: boolean; readonly requiresReview: boolean; readonly reason?: string };

  /**
   * Set the autonomy mode.
   * Emits autonomyMode.changed event.
   */
  setMode(
    mode: AutonomyExecutionMode,
    changedBy: 'user' | 'system' | 'emergency-kill-switch',
    reason?: string
  ): void;
}

/** Emergency kill switch configuration. */
export interface EmergencyKillSwitch {
  readonly enabled: boolean;
  readonly triggeredAt?: string;
  readonly triggeredBy?: string;
  readonly reason?: string;
  readonly forcesMode: AutonomyExecutionMode;
}

/** Creates a default emergency kill switch (disabled). */
export function createDefaultKillSwitch(): EmergencyKillSwitch {
  return {
    enabled: false,
    forcesMode: 'HUMAN_ONLY',
  };
}

/** Checks if emergency kill switch is active via environment or runtime config. */
export function isEmergencyKillSwitchActive(): boolean {
  // Check environment variable
  if (typeof process !== 'undefined' && process.env) {
    return process.env.GHATANA_EMERGENCY_KILL_SWITCH === 'true';
  }
  // Check runtime global (for browser environments)
  if (typeof globalThis !== 'undefined') {
    return (globalThis as { __GHATANA_KILL_SWITCH__?: boolean }).__GHATANA_KILL_SWITCH__ === true;
  }
  return false;
}

/** Generates a session ID. */
function generateSessionId(): SessionId {
  return `sess_${Date.now()}_${Math.random().toString(36).substring(2, 9)}` as SessionId;
}

/** Generates a UUID v4. */
function generateUUID(): CorrelationId {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  }) as CorrelationId;
}
