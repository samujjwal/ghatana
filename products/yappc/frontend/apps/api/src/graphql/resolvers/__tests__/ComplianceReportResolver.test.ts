/**
 * Unit Tests for ComplianceReportResolver
 *
 * <p><b>Purpose</b><br>
 * Tests the GraphQL mutation resolvers for compliance reporting including
 * report generation, scheduling, export operations, and remediation tracking.
 *
 * <p><b>Test Coverage</b><br>
 * - Report generation mutations
 * - Report export to multiple formats
 * - Report scheduling and delivery
 * - Remediation workflow mutations
 * - Authorization and validation
 *
 * @doc.type test
 * @doc.purpose Unit tests for compliance report mutations
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { ComplianceReportResolver } from '../ComplianceReportResolver';
import { PrismaClient } from '@prisma/client';

jest.mock('@prisma/client');

describe('ComplianceReportResolver', () => {
  let resolver: ComplianceReportResolver;
  let mockPrisma: jest.Mocked<PrismaClient>;
  let mockReportService: unknown;
  let mockContext: unknown;

  beforeEach(() => {
    mockPrisma = new PrismaClient() as jest.Mocked<PrismaClient>;
    mockReportService = {
      generateReport: jest.fn(),
      exportToPDF: jest.fn(),
      exportToHTML: jest.fn(),
      exportToCSV: jest.fn(),
      scheduleReport: jest.fn(),
    };

    resolver = new ComplianceReportResolver(mockPrisma, mockReportService);
    mockContext = {
      userId: 'user-123',
      tenantId: 'tenant-123',
      user: { id: 'user-123', email: 'user@example.com' },
    };
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('generateReport mutation', () => {
    it('should generate compliance report', async () => {
      // GIVEN: Assessment ID
      const report = {
        id: 'report-123',
        assessmentId: 'assessment-123',
        framework: 'SOC2',
        generatedAt: new Date(),
        metrics: { overallComplianceScore: 85 },
      };

      mockReportService.generateReport.mockResolvedValue(report);

      // WHEN: Generate report
      const result = await resolver.Mutation.generateReport(
        {},
        { assessmentId: 'assessment-123' },
        mockContext
      );

      // THEN: Should create report
      expect(result.id).toBe('report-123');
      expect(result.framework).toBe('SOC2');
      expect(mockReportService.generateReport).toHaveBeenCalled();
    });

    it('should include all report sections', async () => {
      // GIVEN: Report data
      const report = {
        id: 'report-123',
        sections: {
          executive: { title: 'Executive Summary' },
          findings: { title: 'Findings' },
          remediation: { title: 'Remediation Plan' },
          metrics: { title: 'Metrics' },
        },
      };

      mockReportService.generateReport.mockResolvedValue(report);

      // WHEN: Generate report
      const result = await resolver.Mutation.generateReport(
        {},
        { assessmentId: 'assessment-123' },
        mockContext
      );

      // THEN: Should have all sections
      expect(result.sections).toHaveProperty('executive');
      expect(result.sections).toHaveProperty('findings');
      expect(result.sections).toHaveProperty('remediation');
    });

    it('should throw when assessment not found', async () => {
      // GIVEN: Invalid assessment
      mockReportService.generateReport.mockRejectedValue(
        new Error('Assessment not found')
      );

      // WHEN: Generate report
      // THEN: Should throw error
      await expect(
        resolver.Mutation.generateReport(
          {},
          { assessmentId: 'invalid' },
          mockContext
        )
      ).rejects.toThrow('Assessment not found');
    });
  });

  describe('exportReport mutation', () => {
    it('should export report to PDF', async () => {
      // GIVEN: Report and format
      const pdfBuffer = Buffer.from('PDF content');
      mockReportService.exportToPDF.mockResolvedValue(pdfBuffer);

      // WHEN: Export report
      const result = await resolver.Mutation.exportReport(
        {},
        { reportId: 'report-123', format: 'PDF' },
        mockContext
      );

      // THEN: Should return PDF
      expect(result).toBeInstanceOf(Buffer);
      expect(mockReportService.exportToPDF).toHaveBeenCalled();
    });

    it('should export report to HTML', async () => {
      // GIVEN: Report and format
      const html = '<html>...</html>';
      mockReportService.exportToHTML.mockResolvedValue(html);

      // WHEN: Export report
      const result = await resolver.Mutation.exportReport(
        {},
        { reportId: 'report-123', format: 'HTML' },
        mockContext
      );

      // THEN: Should return HTML
      expect(result).toBe(html);
    });

    it('should export report to CSV', async () => {
      // GIVEN: Report and format
      const csv = 'id,framework,score\n123,SOC2,85';
      mockReportService.exportToCSV.mockResolvedValue(csv);

      // WHEN: Export report
      const result = await resolver.Mutation.exportReport(
        {},
        { reportId: 'report-123', format: 'CSV' },
        mockContext
      );

      // THEN: Should return CSV
      expect(result).toBe(csv);
    });

    it('should validate format parameter', async () => {
      // GIVEN: Invalid format
      // WHEN: Export with invalid format
      // THEN: Should throw error
      await expect(
        resolver.Mutation.exportReport(
          {},
          { reportId: 'report-123', format: 'INVALID' },
          mockContext
        )
      ).rejects.toThrow('Invalid export format');
    });
  });

  describe('scheduleReport mutation', () => {
    it('should schedule recurring report', async () => {
      // GIVEN: Schedule config
      const schedule = {
        id: 'schedule-123',
        reportId: 'report-123',
        frequency: 'monthly',
        recipients: ['admin@example.com'],
        nextRun: new Date(),
      };

      mockReportService.scheduleReport.mockResolvedValue(schedule);

      // WHEN: Schedule report
      const result = await resolver.Mutation.scheduleReport(
        {},
        {
          reportId: 'report-123',
          frequency: 'monthly',
          recipients: ['admin@example.com'],
        },
        mockContext
      );

      // THEN: Should create schedule
      expect(result.id).toBe('schedule-123');
      expect(result.frequency).toBe('monthly');
    });

    it('should validate frequency', async () => {
      // GIVEN: Invalid frequency
      // WHEN: Schedule with invalid frequency
      // THEN: Should throw error
      await expect(
        resolver.Mutation.scheduleReport(
          {},
          {
            reportId: 'report-123',
            frequency: 'invalid',
            recipients: [],
          },
          mockContext
        )
      ).rejects.toThrow('Invalid frequency');
    });
  });

  describe('authorization', () => {
    it('should require authentication', async () => {
      // GIVEN: No context
      const unauthContext = {};

      // WHEN: Try mutation
      // THEN: Should throw error
      await expect(
        resolver.Mutation.generateReport(
          {},
          { assessmentId: 'assessment-123' },
          unauthContext
        )
      ).rejects.toThrow('User not authenticated');
    });
  });
});

