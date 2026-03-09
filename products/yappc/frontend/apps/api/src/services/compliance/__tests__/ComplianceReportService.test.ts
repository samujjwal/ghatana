/**
 * Unit Tests for ComplianceReportService
 *
 * <p><b>Purpose</b><br>
 * Tests the compliance report generation service including PDF generation,
 * HTML rendering, CSV export, report scheduling, and metrics calculation.
 *
 * <p><b>Test Coverage</b><br>
 * - Report generation in multiple formats
 * - Section content generation
 * - Metrics calculation and aggregation
 * - Report scheduling and delivery
 * - Error handling and validation
 *
 * @doc.type test
 * @doc.purpose Unit tests for compliance report generation
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { ComplianceReportService } from '../ComplianceReportService';
import { PrismaClient } from '@prisma/client';

// Mock dependencies
jest.mock('@prisma/client');

describe('ComplianceReportService', () => {
  let service: ComplianceReportService;
  let mockPrisma: jest.Mocked<PrismaClient>;
  let mockControlRepository: unknown;
  let mockAssessmentRepository: unknown;

  beforeEach(() => {
    // GIVEN: Fresh mocks for each test
    mockPrisma = new PrismaClient() as jest.Mocked<PrismaClient>;

    mockControlRepository = {
      findByAssessment: jest.fn(),
      findByFramework: jest.fn(),
    };

    mockAssessmentRepository = {
      findById: jest.fn(),
      findByTenant: jest.fn(),
    };

    service = new ComplianceReportService(
      mockPrisma,
      mockControlRepository,
      mockAssessmentRepository
    );
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('generateReport', () => {
    it('should generate report with all sections', async () => {
      // GIVEN: Assessment with controls and findings
      const assessmentId = 'assessment-123';
      const assessment = {
        id: assessmentId,
        framework: 'SOC2',
        status: 'completed',
        createdAt: new Date(),
        completedAt: new Date(),
      };

      const controls = [
        { id: '1', status: 'implemented', severity: 'critical' },
        { id: '2', status: 'not_implemented', severity: 'high' },
        { id: '3', status: 'implemented', severity: 'medium' },
      ];

      const findings = [
        { controlId: '2', severity: 'high', status: 'open' },
      ];

      mockAssessmentRepository.findById.mockResolvedValue(assessment);
      mockControlRepository.findByAssessment.mockResolvedValue(controls);

      (mockPrisma.complianceFinding as unknown) = {
        findMany: jest.fn().mockResolvedValue(findings),
      };

      // WHEN: Generate report
      const report = await service.generateReport(assessmentId);

      // THEN: Should include all sections
      expect(report).toHaveProperty('id');
      expect(report).toHaveProperty('executiveSummary');
      expect(report).toHaveProperty('assessmentDetails');
      expect(report).toHaveProperty('findings');
      expect(report).toHaveProperty('remediationPlan');
      expect(report).toHaveProperty('metrics');
      expect(report.framework).toBe('SOC2');
    });

    it('should calculate compliance score correctly', async () => {
      // GIVEN: Assessment with known implementation rate
      const assessmentId = 'assessment-123';
      const controls = [
        { id: '1', status: 'implemented' },
        { id: '2', status: 'implemented' },
        { id: '3', status: 'not_implemented' },
        { id: '4', status: 'partially_implemented' },
      ];

      mockAssessmentRepository.findById.mockResolvedValue({
        id: assessmentId,
        framework: 'SOC2',
      });
      mockControlRepository.findByAssessment.mockResolvedValue(controls);
      (mockPrisma.complianceFinding as unknown) = {
        findMany: jest.fn().mockResolvedValue([]),
      };

      // WHEN: Generate report
      const report = await service.generateReport(assessmentId);

      // THEN: Score should be 50% (2 implemented out of 4)
      expect(report.metrics.overallComplianceScore).toBe(50);
    });

    it('should include severity-weighted scoring', async () => {
      // GIVEN: Controls with different severities
      const assessmentId = 'assessment-123';
      const controls = [
        { id: '1', status: 'implemented', severity: 'critical' },
        { id: '2', status: 'not_implemented', severity: 'low' },
      ];

      mockAssessmentRepository.findById.mockResolvedValue({
        id: assessmentId,
        framework: 'SOC2',
      });
      mockControlRepository.findByAssessment.mockResolvedValue(controls);
      (mockPrisma.complianceFinding as unknown) = {
        findMany: jest.fn().mockResolvedValue([]),
      };

      // WHEN: Generate report
      const report = await service.generateReport(assessmentId);

      // THEN: Should have weighted score metrics
      expect(report.metrics).toHaveProperty('weightedScore');
      expect(report.metrics.weightedScore).toBeGreaterThan(50);
    });
  });

  describe('exportToPDF', () => {
    it('should generate PDF buffer', async () => {
      // GIVEN: Report data
      const report = {
        id: 'report-123',
        framework: 'SOC2',
        executiveSummary: { title: 'Summary', content: 'Content' },
        metrics: {
          overallComplianceScore: 80,
          totalControls: 10,
          implementedControls: 8,
        },
      };

      // WHEN: Export to PDF
      const pdfBuffer = await service.exportToPDF(report);

      // THEN: Should return buffer
      expect(pdfBuffer).toBeInstanceOf(Buffer);
      expect(pdfBuffer.length).toBeGreaterThan(0);
    });

    it('should include all report sections in PDF', async () => {
      // GIVEN: Complete report
      const report = {
        id: 'report-123',
        framework: 'SOC2',
        executiveSummary: { title: 'Summary', content: 'Summary content' },
        findings: { title: 'Findings', content: 'Findings content' },
        remediationPlan: { title: 'Plan', content: 'Plan content' },
        metrics: { overallComplianceScore: 75 },
      };

      // WHEN: Export to PDF
      const pdfBuffer = await service.exportToPDF(report);

      // THEN: PDF should contain all section titles
      const pdfText = pdfBuffer.toString();
      expect(pdfText).toContain('Summary');
      expect(pdfText).toContain('Findings');
      expect(pdfText).toContain('Plan');
    });
  });

  describe('exportToHTML', () => {
    it('should generate HTML string', async () => {
      // GIVEN: Report data
      const report = {
        id: 'report-123',
        framework: 'SOC2',
        executiveSummary: { title: 'Summary', content: 'Content' },
        metrics: { overallComplianceScore: 85 },
      };

      // WHEN: Export to HTML
      const html = await service.exportToHTML(report);

      // THEN: Should return valid HTML
      expect(html).toContain('<!DOCTYPE html>');
      expect(html).toContain('<html');
      expect(html).toContain('</html>');
      expect(html).toContain('Summary');
      expect(html).toContain('Content');
    });

    it('should include CSS styling in HTML', async () => {
      // GIVEN: Report data
      const report = {
        id: 'report-123',
        framework: 'SOC2',
        metrics: { overallComplianceScore: 90 },
      };

      // WHEN: Export to HTML
      const html = await service.exportToHTML(report);

      // THEN: Should include style tags
      expect(html).toContain('<style>');
      expect(html).toContain('</style>');
    });
  });

  describe('exportToCSV', () => {
    it('should generate CSV with control data', async () => {
      // GIVEN: Report with controls
      const report = {
        id: 'report-123',
        framework: 'SOC2',
        controls: [
          { id: 'C1', title: 'Control 1', status: 'implemented', severity: 'high' },
          { id: 'C2', title: 'Control 2', status: 'not_implemented', severity: 'medium' },
        ],
      };

      // WHEN: Export to CSV
      const csv = await service.exportToCSV(report);

      // THEN: Should return CSV format
      expect(csv).toContain('Control ID,Title,Status,Severity');
      expect(csv).toContain('C1,Control 1,implemented,high');
      expect(csv).toContain('C2,Control 2,not_implemented,medium');
    });

    it('should handle special characters in CSV', async () => {
      // GIVEN: Report with special characters
      const report = {
        id: 'report-123',
        framework: 'SOC2',
        controls: [
          { id: 'C1', title: 'Control with, comma', status: 'implemented' },
          { id: 'C2', title: 'Control with "quotes"', status: 'implemented' },
        ],
      };

      // WHEN: Export to CSV
      const csv = await service.exportToCSV(report);

      // THEN: Should escape special characters
      expect(csv).toContain('"Control with, comma"');
      expect(csv).toContain('"Control with ""quotes"""');
    });
  });

  describe('scheduleReport', () => {
    it('should create recurring report schedule', async () => {
      // GIVEN: Schedule configuration
      const config = {
        assessmentId: 'assessment-123',
        frequency: 'monthly',
        format: 'pdf',
        recipients: ['admin@example.com'],
      };

      (mockPrisma.reportSchedule as unknown) = {
        create: jest.fn().mockResolvedValue({
          id: 'schedule-123',
          ...config,
          nextRun: new Date(),
        }),
      };

      // WHEN: Schedule report
      const schedule = await service.scheduleReport(config);

      // THEN: Should create schedule
      expect(schedule).toHaveProperty('id');
      expect(schedule.frequency).toBe('monthly');
      expect(mockPrisma.reportSchedule.create).toHaveBeenCalled();
    });

    it('should validate frequency options', async () => {
      // GIVEN: Invalid frequency
      const config = {
        assessmentId: 'assessment-123',
        frequency: 'invalid',
        format: 'pdf',
        recipients: [],
      };

      // WHEN: Schedule report
      // THEN: Should throw error
      await expect(service.scheduleReport(config)).rejects.toThrow(
        'Invalid frequency'
      );
    });

    it('should validate recipients list', async () => {
      // GIVEN: Empty recipients
      const config = {
        assessmentId: 'assessment-123',
        frequency: 'weekly',
        format: 'pdf',
        recipients: [],
      };

      // WHEN: Schedule report
      // THEN: Should throw error
      await expect(service.scheduleReport(config)).rejects.toThrow(
        'At least one recipient required'
      );
    });
  });

  describe('generateExecutiveSummary', () => {
    it('should generate summary with key findings', async () => {
      // GIVEN: Assessment data
      const data = {
        framework: 'SOC2',
        metrics: {
          overallComplianceScore: 75,
          totalControls: 20,
          implementedControls: 15,
          findingsCount: 5,
          criticalFindings: 2,
        },
      };

      // WHEN: Generate summary
      const summary = await service.generateExecutiveSummary(data);

      // THEN: Should include key metrics
      expect(summary.content).toContain('75%');
      expect(summary.content).toContain('20');
      expect(summary.content).toContain('5');
      expect(summary.content).toContain('critical');
    });

    it('should highlight critical issues', async () => {
      // GIVEN: Assessment with critical findings
      const data = {
        framework: 'SOC2',
        metrics: {
          criticalFindings: 3,
          highFindings: 5,
        },
      };

      // WHEN: Generate summary
      const summary = await service.generateExecutiveSummary(data);

      // THEN: Should emphasize critical issues
      expect(summary.content.toLowerCase()).toContain('critical');
      expect(summary.content).toContain('3');
    });
  });

  describe('error handling', () => {
    it('should handle assessment not found', async () => {
      // GIVEN: Non-existent assessment
      mockAssessmentRepository.findById.mockResolvedValue(null);

      // WHEN: Generate report
      // THEN: Should throw error
      await expect(
        service.generateReport('non-existent')
      ).rejects.toThrow('Assessment not found');
    });

    it('should handle database errors gracefully', async () => {
      // GIVEN: Database error
      mockAssessmentRepository.findById.mockRejectedValue(
        new Error('Database connection failed')
      );

      // WHEN: Generate report
      // THEN: Should propagate error
      await expect(
        service.generateReport('assessment-123')
      ).rejects.toThrow('Database connection failed');
    });

    it('should handle PDF generation errors', async () => {
      // GIVEN: Invalid report data
      const invalidReport = null;

      // WHEN: Export to PDF
      // THEN: Should throw error
      await expect(service.exportToPDF(invalidReport as unknown)).rejects.toThrow();
    });
  });
});

