/**
 * Compliance Assessment Tests
 *
 * @jest-environment jsdom
 */

import {
  ComplianceAssessmentManager,
  Assessment,
  AssessmentResult,
  ComplianceGap,
} from '../ComplianceAssessment';

describe('ComplianceAssessmentManager', () => {
  let manager: ComplianceAssessmentManager;

  beforeEach(() => {
    manager = new ComplianceAssessmentManager();
  });

  describe('Assessment Recording', () => {
    it('should record assessment', () => {
      const assessment = manager.recordAssessment(
        'SOC2',
        'CC6.1',
        AssessmentResult.COMPLIANT,
        ['firewall_policy.pdf', 'access_logs.csv'],
        'security-team',
        'All controls verified'
      );

      expect(assessment).toBeDefined();
      expect(assessment.id).toBeDefined();
      expect(assessment.framework).toBe('SOC2');
      expect(assessment.controlId).toBe('CC6.1');
      expect(assessment.result).toBe(AssessmentResult.COMPLIANT);
    });

    it('should get assessment history', () => {
      manager.recordAssessment(
        'SOC2',
        'CC6.1',
        AssessmentResult.COMPLIANT,
        ['evidence1.pdf'],
        'assessor1'
      );

      manager.recordAssessment(
        'SOC2',
        'CC6.1',
        AssessmentResult.COMPLIANT,
        ['evidence2.pdf'],
        'assessor2'
      );

      const history = manager.getAssessmentHistory('SOC2', 'CC6.1');

      expect(history).toHaveLength(2);
      expect(history[0].date.getTime()).toBeGreaterThanOrEqual(history[1].date.getTime());
    });
  });

  describe('Gap Identification', () => {
    it('should identify gaps from non-compliant assessments', () => {
      const assessments: Assessment[] = [
        {
          id: 'a1',
          date: new Date(),
          framework: 'SOC2',
          controlId: 'CC6.1',
          assessor: 'team',
          result: AssessmentResult.NON_COMPLIANT,
          evidence: [],
          notes: '',
          nextAssessmentDate: new Date(),
        },
      ];

      const gaps = manager.identifyGaps('SOC2', assessments);

      expect(gaps.length).toBeGreaterThan(0);
      expect(gaps[0].controlId).toBe('CC6.1');
      expect(gaps[0].severity).toBe('CRITICAL');
    });

    it('should prioritize critical gaps', () => {
      const assessments: Assessment[] = [
        {
          id: 'a1',
          date: new Date(),
          framework: 'SOC2',
          controlId: 'CC6.1',
          assessor: 'team',
          result: AssessmentResult.NON_COMPLIANT,
          evidence: [],
          notes: '',
          nextAssessmentDate: new Date(),
        },
        {
          id: 'a2',
          date: new Date(),
          framework: 'SOC2',
          controlId: 'CC6.2',
          assessor: 'team',
          result: AssessmentResult.PARTIALLY_COMPLIANT,
          evidence: [],
          notes: '',
          nextAssessmentDate: new Date(),
        },
      ];

      const gaps = manager.identifyGaps('SOC2', assessments);

      expect(gaps[0].priority).toBeGreaterThanOrEqual(gaps[1].priority);
    });
  });

  describe('Remediation Planning', () => {
    it('should create remediation plan', () => {
      const gap: ComplianceGap = {
        controlId: 'CC6.1',
        severity: 'CRITICAL',
        description: 'Access controls not implemented',
        currentState: 'Non-compliant',
        requiredState: 'Compliant',
        remediationSteps: ['Step 1', 'Step 2', 'Step 3'],
        estimatedEffort: 40,
        priority: 10,
      };

      const plan = manager.createRemediationPlan(gap, 'assignee@example.com', new Date());

      expect(plan).toBeDefined();
      expect(plan.gapId).toBe('CC6.1');
      expect(plan.status).toBe('OPEN');
      expect(plan.steps).toHaveLength(3);
    });

    it('should update remediation step', () => {
      const gap: ComplianceGap = {
        controlId: 'CC6.1',
        severity: 'CRITICAL',
        description: 'Gap',
        currentState: 'Non-compliant',
        requiredState: 'Compliant',
        remediationSteps: ['Step 1', 'Step 2'],
        estimatedEffort: 40,
        priority: 10,
      };

      const plan = manager.createRemediationPlan(gap, 'assignee@example.com', new Date());

      const updated = manager.updateRemediationStep(plan.id, 1, ['evidence.pdf']);

      expect(updated).toBe(true);
      expect(plan.steps[0].completed).toBe(true);
      expect(plan.steps[0].evidence).toContain('evidence.pdf');
    });

    it('should mark plan complete when all steps done', () => {
      const gap: ComplianceGap = {
        controlId: 'CC6.1',
        severity: 'CRITICAL',
        description: 'Gap',
        currentState: 'Non-compliant',
        requiredState: 'Compliant',
        remediationSteps: ['Step 1'],
        estimatedEffort: 40,
        priority: 10,
      };

      const plan = manager.createRemediationPlan(gap, 'assignee@example.com', new Date());

      manager.updateRemediationStep(plan.id, 1, ['evidence.pdf']);

      expect(plan.status).toBe('COMPLETED');
    });

    it('should get remediation progress', () => {
      const gap: ComplianceGap = {
        controlId: 'CC6.1',
        severity: 'CRITICAL',
        description: 'Gap',
        currentState: 'Non-compliant',
        requiredState: 'Compliant',
        remediationSteps: ['Step 1', 'Step 2', 'Step 3'],
        estimatedEffort: 40,
        priority: 10,
      };

      const plan = manager.createRemediationPlan(gap, 'assignee@example.com', new Date());

      manager.updateRemediationStep(plan.id, 1, ['evidence.pdf']);

      const progress = manager.getRemediationProgress(plan.id);

      expect(progress?.progress).toBe(33);
      expect(progress?.completedSteps).toBe(1);
      expect(progress?.totalSteps).toBe(3);
    });
  });

  describe('Compliance Scoring', () => {
    it('should calculate compliance score from assessments', () => {
      manager.recordAssessment(
        'SOC2',
        'CC6.1',
        AssessmentResult.COMPLIANT,
        [],
        'team'
      );

      manager.recordAssessment(
        'SOC2',
        'CC6.2',
        AssessmentResult.COMPLIANT,
        [],
        'team'
      );

      manager.recordAssessment(
        'SOC2',
        'CC7.1',
        AssessmentResult.NON_COMPLIANT,
        [],
        'team'
      );

      const score = manager.calculateComplianceScore('SOC2');

      expect(score).toBe(67); // 2 compliant out of 3
    });

    it('should handle 100% compliance', () => {
      manager.recordAssessment(
        'SOC2',
        'CC6.1',
        AssessmentResult.COMPLIANT,
        [],
        'team'
      );

      const score = manager.calculateComplianceScore('SOC2');

      expect(score).toBe(100);
    });

    it('should handle 0% compliance', () => {
      manager.recordAssessment(
        'SOC2',
        'CC6.1',
        AssessmentResult.NON_COMPLIANT,
        [],
        'team'
      );

      const score = manager.calculateComplianceScore('SOC2');

      expect(score).toBe(0);
    });
  });

  describe('Assessment Scheduling', () => {
    it('should get assessments due soon', () => {
      // Record assessment with old next date
      manager.recordAssessment(
        'SOC2',
        'CC6.1',
        AssessmentResult.COMPLIANT,
        [],
        'team'
      );

      const due = manager.getAssessmentsDue(365); // Within year

      expect(due.length).toBeGreaterThan(0);
    });
  });

  describe('Open Plans', () => {
    it('should get open remediation plans', () => {
      const gap1: ComplianceGap = {
        controlId: 'CC6.1',
        severity: 'CRITICAL',
        description: 'Gap 1',
        currentState: 'Non-compliant',
        requiredState: 'Compliant',
        remediationSteps: ['Step 1'],
        estimatedEffort: 40,
        priority: 10,
      };

      const gap2: ComplianceGap = {
        controlId: 'CC6.2',
        severity: 'HIGH',
        description: 'Gap 2',
        currentState: 'Non-compliant',
        requiredState: 'Compliant',
        remediationSteps: ['Step 1'],
        estimatedEffort: 20,
        priority: 8,
      };

      manager.createRemediationPlan(gap1, 'assignee@example.com', new Date());
      manager.createRemediationPlan(gap2, 'assignee@example.com', new Date());

      const open = manager.getOpenRemediationPlans();

      expect(open).toHaveLength(2);
      expect(open[0].targetDate.getTime()).toBeLessThanOrEqual(open[1].targetDate.getTime());
    });
  });
});
