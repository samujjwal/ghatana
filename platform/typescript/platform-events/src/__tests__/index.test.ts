/**
 * @ghatana/platform-events test suite
 * Tests for canonical telemetry, AI, security, privacy, visibility, and observability types
 */

import { describe, it, expect } from 'vitest';
import {
  createCorrelationId,
  createSessionId,
  isValidCorrelationId,
  isValidSessionId,
  AUTONOMY_LEVELS,
  APPROVAL_STATES,
  AI_CHANGE_KINDS,
  isValidAutonomyLevel,
  isValidApprovalState,
  isValidAIChangeKind,
  AUTONOMY_EXECUTION_MODES,
  isValidAutonomyExecutionMode,
  createAutonomyModeChangedEvent,
  createAutonomyModeViolationEvent,
  TRUST_LEVELS,
  isValidTrustLevel,
  createDefaultSecurityPolicy,
  DATA_CLASSIFICATIONS,
  isValidDataClassification,
  createDefaultPrivacyPolicy,
  createProvenanceRecord,
  createOperationRecord,
  createDefaultSyncStatus,
  createSpanRef,
  createAuditRecord,
} from '../index';

describe('@ghatana/platform-events', () => {
  describe('CorrelationId', () => {
    it('should create valid correlation IDs', () => {
      const id = createCorrelationId();
      expect(id).toBeDefined();
      expect(typeof id).toBe('string');
      expect(id.length).toBeGreaterThan(0);
    });

    it('should create unique correlation IDs', () => {
      const id1 = createCorrelationId();
      const id2 = createCorrelationId();
      expect(id1).not.toBe(id2);
    });

    it('should validate correlation ID format', () => {
      const id = createCorrelationId();
      expect(isValidCorrelationId(id)).toBe(true);
      expect(isValidCorrelationId('invalid')).toBe(false);
      expect(isValidCorrelationId('')).toBe(false);
    });
  });

  describe('SessionId', () => {
    it('should create valid session IDs', () => {
      const id = createSessionId();
      expect(id).toBeDefined();
      expect(typeof id).toBe('string');
      expect(id.length).toBeGreaterThan(0);
    });

    it('should create unique session IDs', () => {
      const id1 = createSessionId();
      const id2 = createSessionId();
      expect(id1).not.toBe(id2);
    });

    it('should validate session ID format', () => {
      const id = createSessionId();
      expect(isValidSessionId(id)).toBe(true);
      expect(isValidSessionId('invalid')).toBe(false);
      expect(isValidSessionId('')).toBe(false);
    });
  });

  describe('AI Autonomy Levels', () => {
    it('should define valid autonomy levels', () => {
      expect(AUTONOMY_LEVELS).toBeDefined();
      expect(Array.isArray(AUTONOMY_LEVELS)).toBe(true);
      expect(AUTONOMY_LEVELS.length).toBeGreaterThan(0);
    });

    it('should validate autonomy levels', () => {
      expect(isValidAutonomyLevel('MANUAL')).toBe(true);
      expect(isValidAutonomyLevel('ASSISTED')).toBe(true);
      expect(isValidAutonomyLevel('AUTONOMOUS')).toBe(true);
      expect(isValidAutonomyLevel('invalid')).toBe(false);
    });
  });

  describe('AI Approval States', () => {
    it('should define valid approval states', () => {
      expect(APPROVAL_STATES).toBeDefined();
      expect(Array.isArray(APPROVAL_STATES)).toBe(true);
      expect(APPROVAL_STATES.length).toBeGreaterThan(0);
    });

    it('should validate approval states', () => {
      expect(isValidApprovalState('PENDING')).toBe(true);
      expect(isValidApprovalState('APPROVED')).toBe(true);
      expect(isValidApprovalState('REJECTED')).toBe(true);
      expect(isValidApprovalState('invalid')).toBe(false);
    });
  });

  describe('AI Change Kinds', () => {
    it('should define valid change kinds', () => {
      expect(AI_CHANGE_KINDS).toBeDefined();
      expect(Array.isArray(AI_CHANGE_KINDS)).toBe(true);
      expect(AI_CHANGE_KINDS.length).toBeGreaterThan(0);
    });

    it('should validate change kinds', () => {
      expect(isValidAIChangeKind('insert')).toBe(true);
      expect(isValidAIChangeKind('update')).toBe(true);
      expect(isValidAIChangeKind('delete')).toBe(true);
      expect(isValidAIChangeKind('invalid')).toBe(false);
    });
  });

  describe('Autonomy Execution Modes', () => {
    it('should define valid execution modes', () => {
      expect(AUTONOMY_EXECUTION_MODES).toBeDefined();
      expect(Array.isArray(AUTONOMY_EXECUTION_MODES)).toBe(true);
      expect(AUTONOMY_EXECUTION_MODES.length).toBeGreaterThan(0);
    });

    it('should validate execution modes', () => {
      expect(isValidAutonomyExecutionMode('AUTONOMOUS_ASSISTED')).toBe(true);
      expect(isValidAutonomyExecutionMode('HUMAN_REVIEW_REQUIRED')).toBe(true);
      expect(isValidAutonomyExecutionMode('invalid')).toBe(false);
    });

    it('should create autonomy mode changed events', () => {
      const event = createAutonomyModeChangedEvent({
        previousMode: 'HUMAN_ONLY',
        newMode: 'AUTONOMOUS_ASSISTED',
        changedBy: 'user',
        reason: 'test-reason',
        scope: 'global',
      });
      expect(event).toBeDefined();
      expect(event.payload.previousMode).toBe('HUMAN_ONLY');
      expect(event.payload.newMode).toBe('AUTONOMOUS_ASSISTED');
    });

    it('should create autonomy mode violation events', () => {
      const event = createAutonomyModeViolationEvent({
        attemptedAction: 'auto-insert',
        requiredMode: 'HUMAN_ONLY',
        currentMode: 'AUTONOMOUS_ASSISTED',
        blockedBy: 'human-only-policy',
        actor: 'ai',
        reason: 'test-reason',
      });
      expect(event).toBeDefined();
      expect(event.payload.currentMode).toBe('AUTONOMOUS_ASSISTED');
    });
  });

  describe('Security Trust Levels', () => {
    it('should define valid trust levels', () => {
      expect(TRUST_LEVELS).toBeDefined();
      expect(Array.isArray(TRUST_LEVELS)).toBe(true);
      expect(TRUST_LEVELS.length).toBeGreaterThan(0);
    });

    it('should validate trust levels', () => {
      expect(isValidTrustLevel('TRUSTED_WORKSPACE')).toBe(true);
      expect(isValidTrustLevel('UNTRUSTED')).toBe(true);
      expect(isValidTrustLevel('invalid')).toBe(false);
    });

    it('should create default security policy', () => {
      const policy = createDefaultSecurityPolicy();
      expect(policy).toBeDefined();
      expect(policy.sandboxLevel).toBeDefined();
    });
  });

  describe('Privacy Data Classifications', () => {
    it('should define valid data classifications', () => {
      expect(DATA_CLASSIFICATIONS).toBeDefined();
      expect(Array.isArray(DATA_CLASSIFICATIONS)).toBe(true);
      expect(DATA_CLASSIFICATIONS.length).toBeGreaterThan(0);
    });

    it('should validate data classifications', () => {
      expect(isValidDataClassification('PUBLIC')).toBe(true);
      expect(isValidDataClassification('INTERNAL')).toBe(true);
      expect(isValidDataClassification('SENSITIVE')).toBe(true);
      expect(isValidDataClassification('invalid')).toBe(false);
    });

    it('should create default privacy policy', () => {
      const policy = createDefaultPrivacyPolicy();
      expect(policy).toBeDefined();
      expect(typeof policy.dataMinimization).toBe('boolean');
      expect(policy.retentionPeriod).toBeGreaterThan(0);
    });
  });

  describe('Visibility Contracts', () => {
    it('should create provenance records', () => {
      const record = createProvenanceRecord('test-source', 'test-author', '1.0.0');
      expect(record).toBeDefined();
      expect(record.source).toBe('test-source');
      expect(record.author).toBe('test-author');
    });

    it('should create operation records', () => {
      const record = createOperationRecord('test-actor', 'test-trigger', 'low', createCorrelationId());
      expect(record).toBeDefined();
      expect(record.actor).toBe('test-actor');
      expect(record.trigger).toBe('test-trigger');
    });

    it('should create default sync status', () => {
      const status = createDefaultSyncStatus();
      expect(status).toBeDefined();
      expect(typeof status.syncInProgress).toBe('boolean');
    });
  });

  describe('Observability', () => {
    it('should create span references', () => {
      const span = createSpanRef('test-trace', 'test-span');
      expect(span).toBeDefined();
      expect(span.traceId).toBe('test-trace');
      expect(span.spanId).toBe('test-span');
    });

    it('should create audit records', () => {
      const correlationId = createCorrelationId();
      const sessionId = createSessionId();
      const record = createAuditRecord(
        correlationId,
        sessionId,
        { type: 'user', id: 'test-actor' },
        { type: 'test-action', target: 'test-target', targetType: 'document', details: {} },
        { success: true },
      );
      expect(record).toBeDefined();
      expect(record.action.type).toBe('test-action');
      expect(record.actor.id).toBe('test-actor');
    });
  });
});
