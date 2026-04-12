/**
 * @fileoverview Tests for AI visibility contracts and policy types.
 */

import { describe, it, expect } from 'vitest';
import {
  AUTONOMY_LEVELS,
  APPROVAL_STATES,
  AI_CHANGE_KINDS,
  isValidAutonomyLevel,
  isValidApprovalState,
  isValidAIChangeKind,
  createAIVisibilityContract,
} from '../ai/types';
import {
  AUTONOMY_EXECUTION_MODES,
  isValidAutonomyExecutionMode,
  createAutonomyModeChangedEvent,
  createAutonomyModeViolationEvent,
  createDefaultKillSwitch,
  isEmergencyKillSwitchActive,
} from '../ai/policy';
import { createCorrelationId, createSessionId } from '../events/base';

describe('AI Types', () => {
  describe('Autonomy Levels', () => {
    it('should have all 4 autonomy levels', () => {
      expect(AUTONOMY_LEVELS).toHaveLength(4);
      expect(AUTONOMY_LEVELS).toContain('AUTONOMOUS');
      expect(AUTONOMY_LEVELS).toContain('ASSISTED');
      expect(AUTONOMY_LEVELS).toContain('SUPERVISED');
      expect(AUTONOMY_LEVELS).toContain('MANUAL');
    });

    it('should validate autonomy levels correctly', () => {
      expect(isValidAutonomyLevel('AUTONOMOUS')).toBe(true);
      expect(isValidAutonomyLevel('ASSISTED')).toBe(true);
      expect(isValidAutonomyLevel('SUPERVISED')).toBe(true);
      expect(isValidAutonomyLevel('MANUAL')).toBe(true);
      expect(isValidAutonomyLevel('INVALID')).toBe(false);
    });
  });

  describe('Approval States', () => {
    it('should have all 4 approval states', () => {
      expect(APPROVAL_STATES).toHaveLength(4);
      expect(APPROVAL_STATES).toContain('PENDING');
      expect(APPROVAL_STATES).toContain('APPROVED');
      expect(APPROVAL_STATES).toContain('REJECTED');
      expect(APPROVAL_STATES).toContain('BYPASSED');
    });

    it('should validate approval states correctly', () => {
      expect(isValidApprovalState('PENDING')).toBe(true);
      expect(isValidApprovalState('APPROVED')).toBe(true);
      expect(isValidApprovalState('REJECTED')).toBe(true);
      expect(isValidApprovalState('BYPASSED')).toBe(true);
      expect(isValidApprovalState('INVALID')).toBe(false);
    });
  });

  describe('AI Change Kinds', () => {
    it('should have all 5 change kinds', () => {
      expect(AI_CHANGE_KINDS).toHaveLength(5);
      expect(AI_CHANGE_KINDS).toContain('insert');
      expect(AI_CHANGE_KINDS).toContain('update');
      expect(AI_CHANGE_KINDS).toContain('delete');
      expect(AI_CHANGE_KINDS).toContain('reorder');
      expect(AI_CHANGE_KINDS).toContain('suggest');
    });

    it('should validate change kinds correctly', () => {
      expect(isValidAIChangeKind('insert')).toBe(true);
      expect(isValidAIChangeKind('update')).toBe(true);
      expect(isValidAIChangeKind('delete')).toBe(true);
      expect(isValidAIChangeKind('reorder')).toBe(true);
      expect(isValidAIChangeKind('suggest')).toBe(true);
      expect(isValidAIChangeKind('INVALID')).toBe(false);
    });
  });

  describe('AI Visibility Contract', () => {
    it('should create a default visibility contract', () => {
      const correlationId = createCorrelationId('test-correlation-123');
      const contract = createAIVisibilityContract(
        'Test Operation',
        correlationId
      );

      expect(contract.operationState).toBe('idle');
      expect(contract.operationLabel).toBe('Test Operation');
      expect(contract.suggestedChanges).toEqual([]);
      expect(contract.appliedChanges).toEqual([]);
      expect(contract.pendingChanges).toEqual([]);
      expect(contract.confidenceBand).toEqual({ low: 0, high: 0 });
      expect(contract.rationale).toBe('');
      expect(contract.evidence).toEqual([]);
      expect(contract.approvalState).toBe('PENDING');
      expect(contract.reviewRequired).toBe(false);
      expect(contract.rollbackAvailable).toBe(true);
      expect(contract.overrideAvailable).toBe(true);
      expect(contract.autonomyLevel).toBe('ASSISTED');
      expect(contract.correlationId).toBe(correlationId);
      expect(contract.triggeredBy).toBe('explicit');
    });

    it('should allow custom options', () => {
      const correlationId = createCorrelationId('test-correlation-123');
      const contract = createAIVisibilityContract('Test Operation', correlationId, {
        operationState: 'running',
        autonomyLevel: 'AUTONOMOUS',
        triggeredBy: 'implicit',
      });

      expect(contract.operationState).toBe('running');
      expect(contract.autonomyLevel).toBe('AUTONOMOUS');
      expect(contract.triggeredBy).toBe('implicit');
    });
  });
});

describe('AI Policy', () => {
  describe('Autonomy Execution Modes', () => {
    it('should have all 3 execution modes', () => {
      expect(AUTONOMY_EXECUTION_MODES).toHaveLength(3);
      expect(AUTONOMY_EXECUTION_MODES).toContain('AUTONOMOUS_ASSISTED');
      expect(AUTONOMY_EXECUTION_MODES).toContain('HUMAN_REVIEW_REQUIRED');
      expect(AUTONOMY_EXECUTION_MODES).toContain('HUMAN_ONLY');
    });

    it('should validate execution modes correctly', () => {
      expect(isValidAutonomyExecutionMode('AUTONOMOUS_ASSISTED')).toBe(true);
      expect(isValidAutonomyExecutionMode('HUMAN_REVIEW_REQUIRED')).toBe(true);
      expect(isValidAutonomyExecutionMode('HUMAN_ONLY')).toBe(true);
      expect(isValidAutonomyExecutionMode('INVALID')).toBe(false);
    });
  });

  describe('Autonomy Mode Events', () => {
    it('should create mode changed events', () => {
      const event = createAutonomyModeChangedEvent({
        previousMode: 'AUTONOMOUS_ASSISTED',
        newMode: 'HUMAN_ONLY',
        changedBy: 'user',
        reason: 'Safety concern',
        scope: 'global',
      });

      expect(event.name).toBe('autonomyMode.changed');
      expect(event.payload.previousMode).toBe('AUTONOMOUS_ASSISTED');
      expect(event.payload.newMode).toBe('HUMAN_ONLY');
      expect(event.payload.changedBy).toBe('user');
      expect(event.payload.reason).toBe('Safety concern');
      expect(event.payload.scope).toBe('global');
      expect(event.actor).toBe('user');
      expect(event.triggeredBy).toBe('explicit');
    });

    it('should create mode violation events', () => {
      const event = createAutonomyModeViolationEvent({
        attemptedAction: 'ai.layout.suggest',
        requiredMode: 'HUMAN_REVIEW_REQUIRED',
        currentMode: 'HUMAN_ONLY',
        blockedBy: 'implicit-ai-disabled',
        actor: 'ai',
        suggestionId: 'suggestion-123',
        reason: 'Implicit AI actions blocked in HUMAN_ONLY mode',
      });

      expect(event.name).toBe('autonomyMode.violation.blocked');
      expect(event.payload.attemptedAction).toBe('ai.layout.suggest');
      expect(event.payload.blockedBy).toBe('implicit-ai-disabled');
      expect(event.actor).toBe('ai');
      expect(event.triggeredBy).toBe('implicit');
    });
  });

  describe('Emergency Kill Switch', () => {
    it('should create disabled kill switch by default', () => {
      const killSwitch = createDefaultKillSwitch();

      expect(killSwitch.enabled).toBe(false);
      expect(killSwitch.forcesMode).toBe('HUMAN_ONLY');
    });

    it('should detect kill switch from environment', () => {
      // Should be false when env var not set
      expect(isEmergencyKillSwitchActive()).toBe(false);

      // Test with env var set
      const originalEnv = process.env.GHATANA_EMERGENCY_KILL_SWITCH;
      process.env.GHATANA_EMERGENCY_KILL_SWITCH = 'true';
      expect(isEmergencyKillSwitchActive()).toBe(true);

      // Cleanup
      process.env.GHATANA_EMERGENCY_KILL_SWITCH = originalEnv;
    });
  });
});
