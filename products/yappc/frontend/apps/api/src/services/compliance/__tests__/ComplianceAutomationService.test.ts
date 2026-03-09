/**
 * Unit Tests for ComplianceAutomationService
 *
 * <p><b>Purpose</b><br>
 * Tests the compliance automation service including remediation plan generation,
 * recommendation generation, progress tracking, and report generation.
 *
 * <p><b>Test Coverage</b><br>
 * - Remediation plan generation with priority sorting
 * - Recommendation generation based on findings
 * - Progress tracking and step status updates
 * - Report generation with metrics calculation
 * - Error handling for invalid inputs
 * - Edge cases and boundary conditions
 *
 * @doc.type test
 * @doc.purpose Unit tests for compliance automation
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { ComplianceAutomationService } from '../ComplianceAutomationService';
import { PrismaClient } from '@prisma/client';

// Mock Prisma client
jest.mock('@prisma/client');

describe('ComplianceAutomationService', () => {
  let service: ComplianceAutomationService;
  let mockPrisma: jest.Mocked<PrismaClient>;
  let mockControlRepository: unknown;
  let mockAuditRepository: unknown;

  beforeEach(() => {
    // GIVEN: Fresh mocks for each test
    mockPrisma = new PrismaClient() as jest.Mocked<PrismaClient>;

    mockControlRepository = {
      findAll: jest.fn(),
      findById: jest.fn(),
      findByFramework: jest.fn(),
    };

    mockAuditRepository = {
      create: jest.fn(),
      findByTenant: jest.fn(),
    };

    service = new ComplianceAutomationService(
      mockControlRepository,
      mockAuditRepository
    );
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('generateRemediationPlan', () => {
    it('should generate remediation plan with correct priority order', async () => {
      // GIVEN: Assessment with multiple findings of different severities
      const assessmentId = 'assessment-123';
      const findings = [
        {
          id: 'finding-1',
          controlId: 'SOC2-CC6.1',
          severity: 'medium',
          description: 'Medium severity issue',
          status: 'open',
        },
        {
          id: 'finding-2',
          controlId: 'SOC2-CC6.2',
          severity: 'critical',
          description: 'Critical severity issue',
          status: 'open',
        },
        {
          id: 'finding-3',
          controlId: 'SOC2-CC6.3',
          severity: 'low',
          description: 'Low severity issue',
          status: 'open',
        },
      ];

      (mockPrisma.complianceFinding as unknown) = {
        findMany: jest.fn().mockResolvedValue(findings),
      };

      // WHEN: Generate remediation plan
      const plan = await service.generateRemediationPlan(assessmentId);

      // THEN: Steps should be sorted by priority (critical -> high -> medium -> low)
      expect(plan).toBeDefined();
      expect(plan.steps).toHaveLength(3);
      expect(plan.steps[0].priority).toBe('critical');
      expect(plan.steps[1].priority).toBe('medium');
      expect(plan.steps[2].priority).toBe('low');
    });

    it('should include estimated effort for each step', async () => {
      // GIVEN: Assessment with findings
      const assessmentId = 'assessment-123';
      const findings = [
        {
          id: 'finding-1',
          controlId: 'SOC2-CC6.1',
          severity: 'high',
          description: 'High severity issue',
          status: 'open',
        },
      ];

      (mockPrisma.complianceFinding as unknown) = {
        findMany: jest.fn().mockResolvedValue(findings),
      };

      // WHEN: Generate remediation plan
      const plan = await service.generateRemediationPlan(assessmentId);

      // THEN: Each step should have estimated effort
      expect(plan.steps[0].estimatedEffort).toBeDefined();
      expect(typeof plan.steps[0].estimatedEffort).toBe('string');
      expect(['1-2 days', '3-5 days', '1-2 weeks', '2-4 weeks']).toContain(
        plan.steps[0].estimatedEffort
      );
    });

    it('should set status to pending for new remediation steps', async () => {
      // GIVEN: Assessment with open findings
      const assessmentId = 'assessment-123';
      const findings = [
        {
          id: 'finding-1',
          controlId: 'SOC2-CC6.1',
          severity: 'medium',
          description: 'Issue',
          status: 'open',
        },
      ];

      (mockPrisma.complianceFinding as unknown) = {
        findMany: jest.fn().mockResolvedValue(findings),
      };

      // WHEN: Generate remediation plan
      const plan = await service.generateRemediationPlan(assessmentId);

      // THEN: All steps should have 'pending' status
      plan.steps.forEach((step) => {
        expect(step.status).toBe('pending');
      });
    });

    it('should throw error when assessment not found', async () => {
      // GIVEN: Non-existent assessment
      const assessmentId = 'non-existent';

      (mockPrisma.complianceFinding as unknown) = {
        findMany: jest.fn().mockResolvedValue([]),
      };

      // WHEN: Generate remediation plan
      // THEN: Should throw error
      await expect(
        service.generateRemediationPlan(assessmentId)
      ).rejects.toThrow('No findings found for assessment');
    });
  });

  describe('generateRecommendations', () => {
    it('should generate recommendations based on control gaps', async () => {
      // GIVEN: Assessment with failed controls
      const assessmentId = 'assessment-123';
      const controls = [
        {
          id: 'control-1',
          title: 'Access Control',
          status: 'not_implemented',
          framework: 'SOC2',
        },
        {
          id: 'control-2',
          title: 'Encryption',
          status: 'implemented',
          framework: 'SOC2',
        },
      ];

      mockControlRepository.findByFramework.mockResolvedValue(controls);

      // WHEN: Generate recommendations
      const recommendations = await service.generateRecommendations(
        assessmentId,
        'SOC2'
      );

      // THEN: Should recommend controls that are not implemented
      expect(recommendations).toBeDefined();
      expect(recommendations.length).toBeGreaterThan(0);
      expect(recommendations[0]).toHaveProperty('controlId');
      expect(recommendations[0]).toHaveProperty('recommendation');
      expect(recommendations[0]).toHaveProperty('priority');
    });

    it('should prioritize critical controls in recommendations', async () => {
      // GIVEN: Mix of critical and low priority controls
      const assessmentId = 'assessment-123';
      const controls = [
        {
          id: 'control-1',
          title: 'Low priority',
          status: 'not_implemented',
          severity: 'low',
          framework: 'SOC2',
        },
        {
          id: 'control-2',
          title: 'Critical control',
          status: 'not_implemented',
          severity: 'critical',
          framework: 'SOC2',
        },
      ];

      mockControlRepository.findByFramework.mockResolvedValue(controls);

      // WHEN: Generate recommendations
      const recommendations = await service.generateRecommendations(
        assessmentId,
        'SOC2'
      );

      // THEN: Critical controls should be first
      expect(recommendations[0].priority).toBe('critical');
    });

    it('should return empty array when all controls are implemented', async () => {
      // GIVEN: All controls implemented
      const assessmentId = 'assessment-123';
      const controls = [
        {
          id: 'control-1',
          title: 'Control 1',
          status: 'implemented',
          framework: 'SOC2',
        },
        {
          id: 'control-2',
          title: 'Control 2',
          status: 'implemented',
          framework: 'SOC2',
        },
      ];

      mockControlRepository.findByFramework.mockResolvedValue(controls);

      // WHEN: Generate recommendations
      const recommendations = await service.generateRecommendations(
        assessmentId,
        'SOC2'
      );

      // THEN: Should return empty array
      expect(recommendations).toEqual([]);
    });
  });

  describe('updateRemediationProgress', () => {
    it('should update step status successfully', async () => {
      // GIVEN: Existing remediation step
      const stepId = 'step-123';
      const updates = {
        status: 'in_progress' as const,
        completedAt: new Date(),
      };

      (mockPrisma.remediationStep as unknown) = {
        update: jest.fn().mockResolvedValue({
          id: stepId,
          ...updates,
        }),
      };

      // WHEN: Update step status
      const result = await service.updateRemediationProgress(stepId, updates);

      // THEN: Step should be updated
      expect(result).toBeDefined();
      expect(result.status).toBe('in_progress');
      expect(mockPrisma.remediationStep.update).toHaveBeenCalledWith({
        where: { id: stepId },
        data: updates,
      });
    });

    it('should calculate progress percentage correctly', async () => {
      // GIVEN: Remediation plan with mixed step statuses
      const planId = 'plan-123';
      const steps = [
        { id: 'step-1', status: 'completed' },
        { id: 'step-2', status: 'completed' },
        { id: 'step-3', status: 'in_progress' },
        { id: 'step-4', status: 'pending' },
      ];

      (mockPrisma.remediationStep as unknown) = {
        findMany: jest.fn().mockResolvedValue(steps),
      };

      // WHEN: Calculate progress
      const progress = await service.calculateProgress(planId);

      // THEN: Should be 50% (2 completed out of 4 total)
      expect(progress).toBe(50);
    });

    it('should return 0 progress for plan with no steps', async () => {
      // GIVEN: Empty remediation plan
      const planId = 'plan-empty';

      (mockPrisma.remediationStep as unknown) = {
        findMany: jest.fn().mockResolvedValue([]),
      };

      // WHEN: Calculate progress
      const progress = await service.calculateProgress(planId);

      // THEN: Should return 0
      expect(progress).toBe(0);
    });

    it('should return 100 progress when all steps completed', async () => {
      // GIVEN: All steps completed
      const planId = 'plan-complete';
      const steps = [
        { id: 'step-1', status: 'completed' },
        { id: 'step-2', status: 'completed' },
      ];

      (mockPrisma.remediationStep as unknown) = {
        findMany: jest.fn().mockResolvedValue(steps),
      };

      // WHEN: Calculate progress
      const progress = await service.calculateProgress(planId);

      // THEN: Should return 100
      expect(progress).toBe(100);
    });
  });

  describe('generateReport', () => {
    it('should generate report with correct metrics', async () => {
      // GIVEN: Assessment with controls and findings
      const assessmentId = 'assessment-123';
      const assessment = {
        id: assessmentId,
        framework: 'SOC2',
        status: 'completed',
        createdAt: new Date(),
      };

      const controls = [
        { status: 'implemented' },
        { status: 'implemented' },
        { status: 'not_implemented' },
        { status: 'partially_implemented' },
      ];

      const findings = [
        { severity: 'critical', status: 'open' },
        { severity: 'high', status: 'open' },
      ];

      (mockPrisma.complianceAssessment as unknown) = {
        findUnique: jest.fn().mockResolvedValue(assessment),
      };

      mockControlRepository.findByFramework.mockResolvedValue(controls);

      (mockPrisma.complianceFinding as unknown) = {
        findMany: jest.fn().mockResolvedValue(findings),
      };

      // WHEN: Generate report
      const report = await service.generateReport(assessmentId);

      // THEN: Report should have correct metrics
      expect(report).toBeDefined();
      expect(report.metrics.totalControls).toBe(4);
      expect(report.metrics.implementedControls).toBe(2);
      expect(report.metrics.findingsCount).toBe(2);
      expect(report.metrics.criticalFindings).toBe(1);
      expect(report.metrics.overallComplianceScore).toBe(50); // 2 implemented out of 4
    });

    it('should include executive summary in report', async () => {
      // GIVEN: Completed assessment
      const assessmentId = 'assessment-123';
      const assessment = {
        id: assessmentId,
        framework: 'SOC2',
        status: 'completed',
      };

      (mockPrisma.complianceAssessment as unknown) = {
        findUnique: jest.fn().mockResolvedValue(assessment),
      };

      mockControlRepository.findByFramework.mockResolvedValue([]);
      (mockPrisma.complianceFinding as unknown) = {
        findMany: jest.fn().mockResolvedValue([]),
      };

      // WHEN: Generate report
      const report = await service.generateReport(assessmentId);

      // THEN: Should include executive summary
      expect(report.executiveSummary).toBeDefined();
      expect(report.executiveSummary.title).toBe('Executive Summary');
      expect(report.executiveSummary.content).toBeDefined();
    });

    it('should throw error when assessment not found', async () => {
      // GIVEN: Non-existent assessment
      const assessmentId = 'non-existent';

      (mockPrisma.complianceAssessment as unknown) = {
        findUnique: jest.fn().mockResolvedValue(null),
      };

      // WHEN: Generate report
      // THEN: Should throw error
      await expect(service.generateReport(assessmentId)).rejects.toThrow(
        'Assessment not found'
      );
    });
  });

  describe('Edge Cases', () => {
    it('should handle empty findings gracefully', async () => {
      // GIVEN: Assessment with no findings
      const assessmentId = 'assessment-empty';

      (mockPrisma.complianceFinding as unknown) = {
        findMany: jest.fn().mockResolvedValue([]),
      };

      // WHEN: Generate remediation plan
      // THEN: Should throw appropriate error
      await expect(
        service.generateRemediationPlan(assessmentId)
      ).rejects.toThrow();
    });

    it('should handle null or undefined inputs', async () => {
      // GIVEN: Null inputs
      // WHEN: Call methods with null
      // THEN: Should throw validation errors
      await expect(service.generateRemediationPlan(null as unknown)).rejects.toThrow();
      await expect(service.generateReport(undefined as unknown)).rejects.toThrow();
    });

    it('should handle database connection errors', async () => {
      // GIVEN: Database error
      (mockPrisma.complianceFinding as unknown) = {
        findMany: jest.fn().mockRejectedValue(new Error('Database connection failed')),
      };

      // WHEN: Attempt operation
      // THEN: Should propagate error
      await expect(
        service.generateRemediationPlan('assessment-123')
      ).rejects.toThrow('Database connection failed');
    });
  });
});

