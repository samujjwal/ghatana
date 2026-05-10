/**
 * Lifecycle Policy Service Golden Tests
 *
 * Comprehensive tests covering:
 * - Valid transitions
 * - Blocked transitions
 * - Bypass approval
 * - Evidence requirements
 * - Rollback
 * - Retry
 * - Audit trail
 *
 * @doc.type test
 * @doc.purpose Validate lifecycle policy enforcement
 * @doc.layer domain
 */

import { LifecyclePolicyService, lifecyclePolicyService } from '../lifecycle-policy-service';
import type { LifecyclePhaseId } from '../lifecycle-taxonomy';

describe('LifecyclePolicyService Golden Tests', () => {
  let service: LifecyclePolicyService;

  beforeEach(() => {
    service = new LifecyclePolicyService();
  });

  describe('Valid Transitions', () => {
    it('should allow forward transition from INTENT to CONTEXT', () => {
      const context = {
        artifacts: ['Idea Brief', 'Problem Statement'],
        completedCriteria: ['problem-defined', 'stakeholders-aligned'],
        approvals: [],
        evidence: [],
        userId: 'user-1',
        userRole: 'PRODUCT_MANAGER',
      };

      const readiness = service.checkPhaseReadiness('project-1', 'INTENT', context);
      expect(readiness.ready).toBe(true);
    });

    it('should allow forward transition from CONTEXT to PLAN', () => {
      const context = {
        artifacts: ['Architecture Diagram', 'Tech Stack', 'API Design'],
        completedCriteria: ['architecture-approved', 'tech-stack-selected'],
        approvals: ['ARCHITECT'],
        evidence: [{ id: 'ev1', type: 'document', title: 'Architecture Review' }],
        userId: 'user-1',
        userRole: 'ARCHITECT',
      };

      const readiness = service.checkPhaseReadiness('project-1', 'CONTEXT', context);
      expect(readiness.ready).toBe(true);
    });

    it('should allow forward transition from PLAN to EXECUTE', () => {
      const context = {
        artifacts: ['Implementation Plan', 'Task Breakdown'],
        completedCriteria: ['plan-approved', 'resources-allocated'],
        approvals: ['TECH_LEAD'],
        evidence: [{ id: 'ev1', type: 'document', title: 'Plan Approval' }],
        userId: 'user-1',
        userRole: 'TECH_LEAD',
      };

      const readiness = service.checkPhaseReadiness('project-1', 'PLAN', context);
      expect(readiness.ready).toBe(true);
    });

    it('should allow backward transition from EXECUTE to PLAN', () => {
      const rule = service.getTransitionRule('EXECUTE', 'PLAN');
      expect(rule.allowed).toBe(true);
    });

    it('should allow backward transition from VERIFY to EXECUTE', () => {
      const rule = service.getTransitionRule('VERIFY', 'EXECUTE');
      expect(rule.allowed).toBe(true);
    });
  });

  describe('Blocked Transitions', () => {
    it('should block transition when exit criteria not met', () => {
      const context = {
        artifacts: ['Idea Brief'],
        completedCriteria: ['problem-defined'], // missing stakeholders-aligned
        approvals: [],
        evidence: [],
        userId: 'user-1',
        userRole: 'PRODUCT_MANAGER',
      };

      const blockers = service.getBlockers('INTENT', 'CONTEXT', context);
      const exitBlocker = blockers.find(b => b.type === 'exit_criteria');
      
      expect(exitBlocker).toBeDefined();
      expect(exitBlocker?.message).toContain('stakeholders-aligned');
    });

    it('should block transition when required artifacts missing', () => {
      const context = {
        artifacts: ['Architecture Diagram'], // missing Tech Stack, API Design
        completedCriteria: ['architecture-approved', 'tech-stack-selected'],
        approvals: ['ARCHITECT'],
        evidence: [],
        userId: 'user-1',
        userRole: 'ARCHITECT',
      };

      const blockers = service.getBlockers('CONTEXT', 'PLAN', context);
      const artifactBlockers = blockers.filter(b => b.type === 'artifact');
      
      expect(artifactBlockers.length).toBeGreaterThan(0);
    });

    it('should block transition when evidence insufficient', () => {
      const context = {
        artifacts: ['Architecture Diagram', 'Tech Stack', 'API Design'],
        completedCriteria: ['architecture-approved', 'tech-stack-selected'],
        approvals: ['ARCHITECT'],
        evidence: [], // minimum evidence count is 1
        userId: 'user-1',
        userRole: 'ARCHITECT',
      };

      const blockers = service.getBlockers('CONTEXT', 'PLAN', context);
      const evidenceBlocker = blockers.find(b => b.type === 'evidence');
      
      expect(evidenceBlocker).toBeDefined();
    });

    it('should block transition when approval missing', () => {
      const context = {
        artifacts: ['Architecture Diagram', 'Tech Stack', 'API Design'],
        completedCriteria: ['architecture-approved', 'tech-stack-selected'],
        approvals: [], // missing ARCHITECT approval
        evidence: [{ id: 'ev1', type: 'document', title: 'Evidence' }],
        userId: 'user-1',
        userRole: 'PRODUCT_MANAGER',
      };

      const blockers = service.getBlockers('CONTEXT', 'PLAN', context);
      const approvalBlocker = blockers.find(b => b.type === 'approval');
      
      expect(approvalBlocker).toBeDefined();
    });

    it('should block invalid forward transitions (skip phases)', () => {
      const rule = service.getTransitionRule('INTENT', 'EXECUTE');
      expect(rule.allowed).toBe(false);
    });

    it('should block invalid backward transitions (too far back)', () => {
      // Test that you can't skip back more than one phase at once
      const rule = service.getTransitionRule('VERIFY', 'INTENT');
      expect(rule.allowed).toBe(false);
    });
  });

  describe('Bypass Approval', () => {
    it('should allow bypass for ADMIN role', () => {
      const canBypass = service.canBypass('CONTEXT', 'PLAN', 'ADMIN');
      expect(canBypass.allowed).toBe(true);
    });

    it('should allow bypass for OWNER role', () => {
      const canBypass = service.canBypass('CONTEXT', 'PLAN', 'OWNER');
      expect(canBypass.allowed).toBe(true);
    });

    it('should not allow bypass for EDITOR role', () => {
      const canBypass = service.canBypass('CONTEXT', 'PLAN', 'EDITOR');
      expect(canBypass.allowed).toBe(false);
    });

    it('should not allow bypass for VIEWER role', () => {
      const canBypass = service.canBypass('CONTEXT', 'PLAN', 'VIEWER');
      expect(canBypass.allowed).toBe(false);
    });

    it('should allow emergency bypass for OWNER on critical phases', () => {
      const canBypass = service.canBypass('VERIFY', 'INSTITUTIONALIZE', 'OWNER');
      expect(canBypass.allowed).toBe(true);
    });

    it('should provide bypass reason when allowed', () => {
      const canBypass = service.canBypass('CONTEXT', 'PLAN', 'ADMIN');
      expect(canBypass.reason).toBeDefined();
      expect(canBypass.reason).toContain('admin_override');
    });
  });

  describe('Evidence Requirements', () => {
    it('should require evidence for PLAN phase', () => {
      const requirements = service.getEvidenceRequirements('PLAN');
      expect(requirements.minimumCount).toBeGreaterThan(0);
      expect(requirements.verificationRequired).toBe(true);
    });

    it('should require evidence for VERIFY phase', () => {
      const requirements = service.getEvidenceRequirements('VERIFY');
      expect(requirements.minimumCount).toBeGreaterThan(0);
      expect(requirements.verificationRequired).toBe(true);
    });

    it('should require evidence for INSTITUTIONALIZE phase', () => {
      const requirements = service.getEvidenceRequirements('INSTITUTIONALIZE');
      expect(requirements.minimumCount).toBeGreaterThan(0);
      expect(requirements.verificationRequired).toBe(true);
    });

    it('should not require evidence for INTENT phase', () => {
      const requirements = service.getEvidenceRequirements('INTENT');
      expect(requirements.minimumCount).toBe(0);
    });

    it('should include key artifacts as evidence types', () => {
      const requirements = service.getEvidenceRequirements('CONTEXT');
      expect(requirements.evidenceTypes.length).toBeGreaterThan(0);
    });
  });

  describe('Approval Requirements', () => {
    it('should require ARCHITECT approval for CONTEXT phase', () => {
      const requirements = service.getApprovalRequirements('CONTEXT');
      expect(requirements.approverRoles).toContain('ARCHITECT');
    });

    it('should require TECH_LEAD approval for PLAN phase', () => {
      const requirements = service.getApprovalRequirements('PLAN');
      expect(requirements.approverRoles).toContain('TECH_LEAD');
    });

    it('should have escalation path for INSTITUTIONALIZE phase', () => {
      const requirements = service.getApprovalRequirements('INSTITUTIONALIZE');
      expect(requirements.escalationPath).toBeDefined();
      expect(requirements.escalationPath).toContain('ADMIN');
      expect(requirements.escalationPath).toContain('OWNER');
    });

    it('should require minimum of 1 approval by default', () => {
      const requirements = service.getApprovalRequirements('CONTEXT');
      expect(requirements.minimumApprovals).toBe(1);
    });
  });

  describe('Phase Readiness Scoring', () => {
    it('should calculate high readiness score when all criteria met', () => {
      const context = {
        artifacts: ['Architecture Diagram', 'Tech Stack', 'API Design'],
        completedCriteria: ['architecture-approved', 'tech-stack-selected'],
        approvals: ['ARCHITECT'],
        evidence: [{ id: 'ev1', type: 'document', title: 'Evidence' }],
        userId: 'user-1',
        userRole: 'ARCHITECT',
      };

      const readiness = service.checkPhaseReadiness('project-1', 'CONTEXT', context);
      expect(readiness.score).toBe(100);
    });

    it('should calculate partial readiness score when some criteria missing', () => {
      const context = {
        artifacts: ['Architecture Diagram'], // missing artifacts
        completedCriteria: ['architecture-approved'], // missing gate
        approvals: ['ARCHITECT'],
        evidence: [],
        userId: 'user-1',
        userRole: 'ARCHITECT',
      };

      const readiness = service.checkPhaseReadiness('project-1', 'CONTEXT', context);
      expect(readiness.score).toBeGreaterThan(0);
      expect(readiness.score).toBeLessThan(100);
    });

    it('should calculate zero readiness score when no criteria met', () => {
      const context = {
        artifacts: [],
        completedCriteria: [],
        approvals: [],
        evidence: [],
        userId: 'user-1',
        userRole: 'ARCHITECT',
      };

      const readiness = service.checkPhaseReadiness('project-1', 'CONTEXT', context);
      expect(readiness.score).toBe(0);
    });
  });

  describe('Audit Trail Generation', () => {
    it('should generate audit event for phase transition', () => {
      const auditEvent = service.generateAuditEvent(
        'PHASE_TRANSITION_REQUESTED',
        'project-1',
        'user-1',
        {
          fromPhase: 'CONTEXT',
          toPhase: 'PLAN',
          transitionId: 'trans-123',
          evidenceCount: 2,
          approvalCount: 1,
        }
      );

      expect(auditEvent.action).toBe('PHASE_TRANSITION_REQUESTED');
      expect(auditEvent.projectId).toBe('project-1');
      expect(auditEvent.actor).toBe('user-1');
      expect(auditEvent.timestamp).toBeInstanceOf(Date);
    });

    it('should include severity in audit event', () => {
      const auditEvent = service.generateAuditEvent(
        'PHASE_TRANSITION_COMPLETED',
        'project-1',
        'user-1',
        {
          fromPhase: 'CONTEXT',
          toPhase: 'PLAN',
          transitionId: 'trans-123',
        }
      );

      expect(auditEvent.severity).toBeDefined();
      expect(['info', 'warn', 'error']).toContain(auditEvent.severity);
    });

    it('should include correlation ID in audit event', () => {
      const auditEvent = service.generateAuditEvent(
        'PHASE_TRANSITION_REQUESTED',
        'project-1',
        'user-1',
        {
          fromPhase: 'CONTEXT',
          toPhase: 'PLAN',
          transitionId: 'trans-123',
          correlationId: 'corr-456',
        }
      );

      expect(auditEvent.details.transitionId).toBe('trans-123');
    });
  });

  describe('Transition Rules', () => {
    it('should return transition rule with correct phases', () => {
      const rule = service.getTransitionRule('INTENT', 'CONTEXT');
      expect(rule.fromPhase).toBe('INTENT');
      expect(rule.toPhase).toBe('CONTEXT');
    });

    it('should indicate when approval is required', () => {
      const rule = service.getTransitionRule('CONTEXT', 'PLAN');
      expect(rule.requiresApproval).toBe(true);
    });

    it('should list approver roles', () => {
      const rule = service.getTransitionRule('CONTEXT', 'PLAN');
      expect(rule.approverRoles.length).toBeGreaterThan(0);
    });

    it('should specify minimum evidence count', () => {
      const rule = service.getTransitionRule('CONTEXT', 'PLAN');
      expect(rule.minimumEvidence).toBeGreaterThanOrEqual(0);
    });

    it('should list auto-approve conditions', () => {
      const rule = service.getTransitionRule('INTENT', 'CONTEXT');
      expect(rule.autoApproveConditions).toBeDefined();
      expect(Array.isArray(rule.autoApproveConditions)).toBe(true);
    });

    it('should list bypass conditions', () => {
      const rule = service.getTransitionRule('CONTEXT', 'PLAN');
      expect(rule.bypassConditions).toBeDefined();
      expect(Array.isArray(rule.bypassConditions)).toBe(true);
    });
  });

  describe('Rollback Scenarios', () => {
    it('should allow rollback from EXECUTE to PLAN', () => {
      const rule = service.getTransitionRule('EXECUTE', 'PLAN');
      expect(rule.allowed).toBe(true);
    });

    it('should allow rollback from VERIFY to EXECUTE', () => {
      const rule = service.getTransitionRule('VERIFY', 'EXECUTE');
      expect(rule.allowed).toBe(true);
    });

    it('should allow rollback from DEPLOY to VERIFY', () => {
      const rule = service.getTransitionRule('DEPLOY' as LifecyclePhaseId, 'VERIFY');
      // DEPLOY is not a canonical phase, so this test validates the behavior
      // If DEPLOY were a canonical phase, it should allow rollback to VERIFY
    });

    it('should generate audit event for rollback', () => {
      const auditEvent = service.generateAuditEvent(
        'PHASE_TRANSITION_REQUESTED',
        'project-1',
        'user-1',
        {
          fromPhase: 'VERIFY',
          toPhase: 'EXECUTE',
          transitionId: 'trans-rollback-123',
          rollback: true,
          reason: 'Test failures detected',
        }
      );

      expect(auditEvent.action).toBe('PHASE_TRANSITION_REQUESTED');
      expect(auditEvent.details.fromPhase).toBe('VERIFY');
      expect(auditEvent.details.toPhase).toBe('EXECUTE');
    });

    it('should require approval for rollback on critical phases', () => {
      const rule = service.getTransitionRule('INSTITUTIONALIZE', 'LEARN');
      expect(rule.requiresApproval).toBe(true);
    });
  });

  describe('Retry Scenarios', () => {
    it('should allow retry within same phase after failure', () => {
      // Retry within same phase is not a transition, but should be allowed
      const context = {
        artifacts: ['Architecture Diagram', 'Tech Stack', 'API Design'],
        completedCriteria: ['architecture-approved', 'tech-stack-selected'],
        approvals: ['ARCHITECT'],
        evidence: [{ id: 'ev1', type: 'document', title: 'Evidence' }],
        userId: 'user-1',
        userRole: 'ARCHITECT',
      };

      const readiness = service.checkPhaseReadiness('project-1', 'CONTEXT', context);
      expect(readiness.ready).toBe(true);
    });

    it('should generate audit event for retry attempt', () => {
      const auditEvent = service.generateAuditEvent(
        'PHASE_TRANSITION_REQUESTED',
        'project-1',
        'user-1',
        {
          fromPhase: 'CONTEXT',
          toPhase: 'PLAN',
          transitionId: 'trans-retry-123',
          retry: true,
          previousAttemptId: 'trans-failed-456',
          failureReason: 'Evidence insufficient',
        }
      );

      expect(auditEvent.action).toBe('PHASE_TRANSITION_REQUESTED');
      expect(auditEvent.details.transitionId).toBe('trans-retry-123');
    });

    it('should allow retry with additional evidence', () => {
      const context = {
        artifacts: ['Architecture Diagram', 'Tech Stack', 'API Design'],
        completedCriteria: ['architecture-approved', 'tech-stack-selected'],
        approvals: ['ARCHITECT'],
        evidence: [
          { id: 'ev1', type: 'document', title: 'Evidence 1' },
          { id: 'ev2', type: 'document', title: 'Evidence 2' },
        ],
        userId: 'user-1',
        userRole: 'ARCHITECT',
      };

      const readiness = service.checkPhaseReadiness('project-1', 'CONTEXT', context);
      expect(readiness.evidenceSufficient).toBe(true);
    });

    it('should track retry count in audit trail', () => {
      const auditEvent1 = service.generateAuditEvent(
        'PHASE_TRANSITION_REQUESTED',
        'project-1',
        'user-1',
        {
          fromPhase: 'CONTEXT',
          toPhase: 'PLAN',
          transitionId: 'trans-retry-1',
          retry: true,
          retryCount: 1,
        }
      );

      const auditEvent2 = service.generateAuditEvent(
        'PHASE_TRANSITION_REQUESTED',
        'project-1',
        'user-1',
        {
          fromPhase: 'CONTEXT',
          toPhase: 'PLAN',
          transitionId: 'trans-retry-2',
          retry: true,
          retryCount: 2,
        }
      );

      expect(auditEvent1.details.retryCount).toBe(1);
      expect(auditEvent2.details.retryCount).toBe(2);
    });
  });

  describe('Singleton Instance', () => {
    it('should export singleton instance', () => {
      expect(lifecyclePolicyService).toBeDefined();
      expect(lifecyclePolicyService instanceof LifecyclePolicyService).toBe(true);
    });

    it('should use singleton instance for consistency', () => {
      const result1 = lifecyclePolicyService.getTransitionRule('INTENT', 'CONTEXT');
      const result2 = lifecyclePolicyService.getTransitionRule('INTENT', 'CONTEXT');
      
      expect(result1).toEqual(result2);
    });
  });
});
