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
      expect(isValidAutonomyLevel('manual')).toBe(true);
      expect(isValidAutonomyLevel('assisted')).toBe(true);
      expect(isValidAutonomyLevel('autonomous')).toBe(true);
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
      expect(isValidApprovalState('pending')).toBe(true);
      expect(isValidApprovalState('approved')).toBe(true);
      expect(isValidApprovalState('rejected')).toBe(true);
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
      expect(isValidAIChangeKind('create')).toBe(true);
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
      expect(isValidAutonomyExecutionMode('immediate')).toBe(true);
      expect(isValidAutonomyExecutionMode('deferred')).toBe(true);
      expect(isValidAutonomyExecutionMode('invalid')).toBe(false);
    });

    it('should create autonomy mode changed events', () => {
      const event = createAutonomyModeChangedEvent('manual', 'assisted', 'test-reason');
      expect(event).toBeDefined();
      expect(event.fromMode).toBe('manual');
      expect(event.toMode).toBe('assisted');
    });

    it('should create autonomy mode violation events', () => {
      const event = createAutonomyModeViolationEvent('assisted', 'test-reason');
      expect(event).toBeDefined();
      expect(event.currentMode).toBe('assisted');
    });
  });

  describe('Security Trust Levels', () => {
    it('should define valid trust levels', () => {
      expect(TRUST_LEVELS).toBeDefined();
      expect(Array.isArray(TRUST_LEVELS)).toBe(true);
      expect(TRUST_LEVELS.length).toBeGreaterThan(0);
    });

    it('should validate trust levels', () => {
      expect(isValidTrustLevel('trusted')).toBe(true);
      expect(isValidTrustLevel('untrusted')).toBe(true);
      expect(isValidTrustLevel('invalid')).toBe(false);
    });

    it('should create default security policy', () => {
      const policy = createDefaultSecurityPolicy();
      expect(policy).toBeDefined();
      expect(policy.trustLevel).toBeDefined();
    });
  });

  describe('Privacy Data Classifications', () => {
    it('should define valid data classifications', () => {
      expect(DATA_CLASSIFICATIONS).toBeDefined();
      expect(Array.isArray(DATA_CLASSIFICATIONS)).toBe(true);
      expect(DATA_CLASSIFICATIONS.length).toBeGreaterThan(0);
    });

    it('should validate data classifications', () => {
      expect(isValidDataClassification('public')).toBe(true);
      expect(isValidDataClassification('private')).toBe(true);
      expect(isValidDataClassification('confidential')).toBe(true);
      expect(isValidDataClassification('invalid')).toBe(false);
    });

    it('should create default privacy policy', () => {
      const policy = createDefaultPrivacyPolicy();
      expect(policy).toBeDefined();
      expect(policy.dataClassification).toBeDefined();
    });
  });

  describe('Visibility Contracts', () => {
    it('should create provenance records', () => {
      const record = createProvenanceRecord('test-source', 'test-operation');
      expect(record).toBeDefined();
      expect(record.source).toBe('test-source');
      expect(record.operation).toBe('test-operation');
    });

    it('should create operation records', () => {
      const record = createOperationRecord('test-operation', 'test-context');
      expect(record).toBeDefined();
      expect(record.operation).toBe('test-operation');
    });

    it('should create default sync status', () => {
      const status = createDefaultSyncStatus();
      expect(status).toBeDefined();
      expect(status.state).toBeDefined();
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
      const record = createAuditRecord('test-action', 'test-actor');
      expect(record).toBeDefined();
      expect(record.action).toBe('test-action');
      expect(record.actor).toBe('test-actor');
    });
  });
});
