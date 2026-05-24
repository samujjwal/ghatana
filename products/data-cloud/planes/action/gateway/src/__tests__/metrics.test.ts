/**
 * Tests for GatewayMetrics Action Plane metrics (AEP-006).
 * Validates that Action Plane metrics are properly recorded and exposed.
 */

import { GatewayMetrics, GatewayMetricsSnapshot } from '../metrics';

describe('GatewayMetrics Action Plane Metrics (AEP-006)', () => {
  let metrics: GatewayMetrics;

  beforeEach(() => {
    metrics = new GatewayMetrics();
  });

  describe('Pattern Match Metrics', () => {
    it('records pattern match by type', () => {
      metrics.recordPatternMatch('fraud-detection');
      metrics.recordPatternMatch('fraud-detection');
      metrics.recordPatternMatch('risk-assessment');

      const snapshot = metrics.snapshot();
      expect(snapshot.patternMatchesByType['fraud-detection']).toBe(2);
      expect(snapshot.patternMatchesByType['risk-assessment']).toBe(1);
    });

    it('increments pattern match count for same type', () => {
      metrics.recordPatternMatch('test-pattern');
      metrics.recordPatternMatch('test-pattern');
      metrics.recordPatternMatch('test-pattern');

      const snapshot = metrics.snapshot();
      expect(snapshot.patternMatchesByType['test-pattern']).toBe(3);
    });

    it('resets pattern match metrics on reset', () => {
      metrics.recordPatternMatch('test-pattern');
      metrics.reset();

      const snapshot = metrics.snapshot();
      expect(snapshot.patternMatchesByType['test-pattern']).toBeUndefined();
    });
  });

  describe('Agent Execution Metrics', () => {
    it('records agent execution by status', () => {
      metrics.recordAgentExecution('success');
      metrics.recordAgentExecution('failure');
      metrics.recordAgentExecution('success');

      const snapshot = metrics.snapshot();
      expect(snapshot.agentExecutionsByStatus['success']).toBe(2);
      expect(snapshot.agentExecutionsByStatus['failure']).toBe(1);
    });

    it('increments agent execution count for same status', () => {
      metrics.recordAgentExecution('timeout');
      metrics.recordAgentExecution('timeout');

      const snapshot = metrics.snapshot();
      expect(snapshot.agentExecutionsByStatus['timeout']).toBe(2);
    });

    it('resets agent execution metrics on reset', () => {
      metrics.recordAgentExecution('success');
      metrics.reset();

      const snapshot = metrics.snapshot();
      expect(snapshot.agentExecutionsByStatus['success']).toBeUndefined();
    });
  });

  describe('Evidence Write Metrics', () => {
    it('records evidence write by status', () => {
      metrics.recordEvidenceWrite('success');
      metrics.recordEvidenceWrite('failure');
      metrics.recordEvidenceWrite('success');

      const snapshot = metrics.snapshot();
      expect(snapshot.evidenceWritesByStatus['success']).toBe(2);
      expect(snapshot.evidenceWritesByStatus['failure']).toBe(1);
    });

    it('increments evidence write count for same status', () => {
      metrics.recordEvidenceWrite('retry');
      metrics.recordEvidenceWrite('retry');

      const snapshot = metrics.snapshot();
      expect(snapshot.evidenceWritesByStatus['retry']).toBe(2);
    });

    it('resets evidence write metrics on reset', () => {
      metrics.recordEvidenceWrite('success');
      metrics.reset();

      const snapshot = metrics.snapshot();
      expect(snapshot.evidenceWritesByStatus['success']).toBeUndefined();
    });
  });

  describe('Policy Evaluation Metrics', () => {
    it('records policy evaluation by decision', () => {
      metrics.recordPolicyEvaluation('allow');
      metrics.recordPolicyEvaluation('deny');
      metrics.recordPolicyEvaluation('allow');

      const snapshot = metrics.snapshot();
      expect(snapshot.policyEvaluationsByDecision['allow']).toBe(2);
      expect(snapshot.policyEvaluationsByDecision['deny']).toBe(1);
    });

    it('increments policy evaluation count for same decision', () => {
      metrics.recordPolicyEvaluation('allow');
      metrics.recordPolicyEvaluation('allow');

      const snapshot = metrics.snapshot();
      expect(snapshot.policyEvaluationsByDecision['allow']).toBe(2);
    });

    it('resets policy evaluation metrics on reset', () => {
      metrics.recordPolicyEvaluation('allow');
      metrics.reset();

      const snapshot = metrics.snapshot();
      expect(snapshot.policyEvaluationsByDecision['allow']).toBeUndefined();
    });
  });

  describe('Commit SHA Validation Metrics', () => {
    it('records commit SHA validation by result', () => {
      metrics.recordCommitShaValidation('valid');
      metrics.recordCommitShaValidation('invalid');
      metrics.recordCommitShaValidation('valid');

      const snapshot = metrics.snapshot();
      expect(snapshot.commitShaValidationsByResult['valid']).toBe(2);
      expect(snapshot.commitShaValidationsByResult['invalid']).toBe(1);
    });

    it('increments commit SHA validation count for same result', () => {
      metrics.recordCommitShaValidation('missing');
      metrics.recordCommitShaValidation('missing');

      const snapshot = metrics.snapshot();
      expect(snapshot.commitShaValidationsByResult['missing']).toBe(2);
    });

    it('resets commit SHA validation metrics on reset', () => {
      metrics.recordCommitShaValidation('valid');
      metrics.reset();

      const snapshot = metrics.snapshot();
      expect(snapshot.commitShaValidationsByResult['valid']).toBeUndefined();
    });
  });

  describe('Action Plane Metrics Integration', () => {
    it('records all Action Plane metrics independently', () => {
      metrics.recordPatternMatch('test-pattern');
      metrics.recordAgentExecution('success');
      metrics.recordEvidenceWrite('success');
      metrics.recordPolicyEvaluation('allow');
      metrics.recordCommitShaValidation('valid');

      const snapshot = metrics.snapshot();
      expect(snapshot.patternMatchesByType['test-pattern']).toBe(1);
      expect(snapshot.agentExecutionsByStatus['success']).toBe(1);
      expect(snapshot.evidenceWritesByStatus['success']).toBe(1);
      expect(snapshot.policyEvaluationsByDecision['allow']).toBe(1);
      expect(snapshot.commitShaValidationsByResult['valid']).toBe(1);
    });

    it('preserves existing gateway metrics when recording Action Plane metrics', () => {
      metrics.recordHttpProxyRequest(200);
      metrics.recordAuthFailure('invalid_token');
      metrics.recordPatternMatch('test-pattern');

      const snapshot = metrics.snapshot();
      expect(snapshot.httpProxyRequestsByStatus['200']).toBe(1);
      expect(snapshot.authFailuresByReason['invalid_token']).toBe(1);
      expect(snapshot.patternMatchesByType['test-pattern']).toBe(1);
    });

    it('resets all metrics including Action Plane metrics', () => {
      metrics.recordHttpProxyRequest(200);
      metrics.recordPatternMatch('test-pattern');
      metrics.recordAgentExecution('success');
      metrics.reset();

      const snapshot = metrics.snapshot();
      expect(snapshot.httpProxyRequestsByStatus['200']).toBeUndefined();
      expect(snapshot.patternMatchesByType['test-pattern']).toBeUndefined();
      expect(snapshot.agentExecutionsByStatus['success']).toBeUndefined();
    });
  });
});
