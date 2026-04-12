/**
 * @fileoverview Design system event taxonomy.
 *
 * Required design-system event families:
 * - ds.token.created, ds.token.updated, ds.token.deprecated
 * - ds.component.registered, ds.component.contract.validated
 * - ds.theme.changed, ds.preset.applied
 * - ds.governance.violation.detected
 * - ds.ai.suggestion.shown, ds.ai.fix.applied
 * - ds.audit.completed
 */

import type { AIVisibilityContract } from '../ai/types';

/** Design system token lifecycle payload. */
export interface TokenLifecyclePayload {
  readonly tokenId: string;
  readonly tokenName: string;
  readonly category: string;
  readonly value: string | number;
  readonly previousValue?: string | number;
  readonly reason?: string;
}

/** Design system component registration payload. */
export interface ComponentRegisteredPayload {
  readonly componentId: string;
  readonly componentName: string;
  readonly category: string;
  readonly version: string;
  readonly contractHash: string;
}

/** Design system contract validation payload. */
export interface ContractValidatedPayload {
  readonly componentId: string;
  readonly componentName: string;
  readonly validationResult: 'pass' | 'fail' | 'warn';
  readonly errors?: readonly string[];
  readonly warnings?: readonly string[];
}

/** Design system theme change payload. */
export interface ThemeChangedPayload {
  readonly themeId: string;
  readonly themeName: string;
  readonly previousThemeId?: string;
  readonly mode: string;
  readonly brand: string;
}

/** Design system preset application payload. */
export interface PresetAppliedPayload {
  readonly presetId: string;
  readonly presetName: string;
  readonly targetThemeId: string;
  readonly affectedTokens: readonly string[];
}

/** Design system governance violation payload. */
export interface GovernanceViolationPayload {
  readonly violationId: string;
  readonly policyName: string;
  readonly severity: 'error' | 'warning' | 'info';
  readonly affectedComponentIds?: readonly string[];
  readonly affectedTokenIds?: readonly string[];
  readonly message: string;
  readonly remediation?: string;
}

/** Design system AI suggestion/fix payload. */
export interface DSAISuggestionPayload {
  readonly suggestionId: string;
  readonly kind: 'duplicate-detection' | 'drift-detection' | 'a11y-fix' | 'token-suggestion';
  readonly affectedTokens?: readonly string[];
  readonly affectedComponents?: readonly string[];
  readonly visibilityContract: AIVisibilityContract;
}

/** Design system audit completion payload. */
export interface AuditCompletedPayload {
  readonly auditId: string;
  readonly auditType: 'a11y' | 'security' | 'privacy' | 'governance' | 'full';
  readonly componentCount: number;
  readonly tokenCount: number;
  readonly issuesFound: number;
  readonly issuesBySeverity: {
    readonly error: number;
    readonly warning: number;
    readonly info: number;
  };
}

/** All design system event payload types. */
export interface DesignSystemEventPayloads {
  'ds.token.created': TokenLifecyclePayload;
  'ds.token.updated': TokenLifecyclePayload;
  'ds.token.deprecated': TokenLifecyclePayload;
  'ds.component.registered': ComponentRegisteredPayload;
  'ds.component.contract.validated': ContractValidatedPayload;
  'ds.theme.changed': ThemeChangedPayload;
  'ds.preset.applied': PresetAppliedPayload;
  'ds.governance.violation.detected': GovernanceViolationPayload;
  'ds.ai.suggestion.shown': DSAISuggestionPayload;
  'ds.ai.fix.applied': DSAISuggestionPayload;
  'ds.audit.completed': AuditCompletedPayload;
}

/** Design system event names as const assertions. */
export const DesignSystemEvents = {
  TOKEN_CREATED: 'ds.token.created',
  TOKEN_UPDATED: 'ds.token.updated',
  TOKEN_DEPRECATED: 'ds.token.deprecated',
  COMPONENT_REGISTERED: 'ds.component.registered',
  COMPONENT_CONTRACT_VALIDATED: 'ds.component.contract.validated',
  THEME_CHANGED: 'ds.theme.changed',
  PRESET_APPLIED: 'ds.preset.applied',
  GOVERNANCE_VIOLATION_DETECTED: 'ds.governance.violation.detected',
  AI_SUGGESTION_SHOWN: 'ds.ai.suggestion.shown',
  AI_FIX_APPLIED: 'ds.ai.fix.applied',
  AUDIT_COMPLETED: 'ds.audit.completed',
} as const;

/** Type for all design system event names. */
export type DesignSystemEventName =
  (typeof DesignSystemEvents)[keyof typeof DesignSystemEvents];

/** All design system event names as an array for validation. */
export const ALL_DESIGN_SYSTEM_EVENT_NAMES: readonly DesignSystemEventName[] =
  Object.values(DesignSystemEvents);
