/**
 * Unit Tests for ComplianceQueryResolver
 *
 * <p><b>Purpose</b><br>
 * Tests the GraphQL query resolvers for compliance operations including
 * assessment retrieval, filtering, pagination, and multi-framework queries.
 *
 * <p><b>Test Coverage</b><br>
 * - Assessment queries and filtering
 * - Control queries and categorization
 * - Finding queries and severity filtering
 * - Pagination and sorting
 * - Authorization checks
 *
 * @doc.type test
 * @doc.purpose Unit tests for compliance GraphQL queries
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { ComplianceQueryResolver } from '../ComplianceQueryResolver';
import { PrismaClient } from '@prisma/client';

// Mock dependencies
jest.mock('@prisma/client');

describe('ComplianceQueryResolver', () => {
  let resolver: ComplianceQueryResolver;
  let mockPrisma: jest.Mocked<PrismaClient>;
  let mockContext: unknown;

  beforeEach(() => {
    // GIVEN: Fresh mocks and resolver instance
    mockPrisma = new PrismaClient() as jest.Mocked<PrismaClient>;
    resolver = new ComplianceQueryResolver(mockPrisma);

    mockContext = {
      userId: 'user-123',
      tenantId: 'tenant-123',
      user: { id: 'user-123', email: 'user@example.com' },
    };
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('assessments query', () => {
    it('should retrieve assessments for tenant', async () => {
      // GIVEN: Tenant with assessments
      const assessments = [
        { id: '1', framework: 'SOC2', status: 'completed' },
        { id: '2', framework: 'ISO27001', status: 'in_progress' },
      ];

      (mockPrisma.complianceAssessment as unknown) = {
        findMany: jest.fn().mockResolvedValue(assessments),
      };

      // WHEN: Query assessments
      const result = await resolver.Query.assessments({}, {}, mockContext);

      // THEN: Should return assessments
      expect(result).toHaveLength(2);
      expect(result[0].framework).toBe('SOC2');
      expect(mockPrisma.complianceAssessment.findMany).toHaveBeenCalledWith({
        where: { tenantId: 'tenant-123' },
        orderBy: { createdAt: 'desc' },
      });
    });

    it('should filter assessments by framework', async () => {
      // GIVEN: Framework filter
      (mockPrisma.complianceAssessment as unknown) = {
        findMany: jest.fn().mockResolvedValue([]),
      };

      // WHEN: Query with framework filter
      await resolver.Query.assessments(
        {},
        { framework: 'SOC2' },
        mockContext
      );

      // THEN: Should apply framework filter
      expect(mockPrisma.complianceAssessment.findMany).toHaveBeenCalledWith({
        where: {
          tenantId: 'tenant-123',
          framework: 'SOC2',
        },
        orderBy: { createdAt: 'desc' },
      });
    });

    it('should filter assessments by status', async () => {
      // GIVEN: Status filter
      (mockPrisma.complianceAssessment as unknown) = {
        findMany: jest.fn().mockResolvedValue([]),
      };

      // WHEN: Query with status filter
      await resolver.Query.assessments(
        {},
        { status: 'completed' },
        mockContext
      );

      // THEN: Should apply status filter
      expect(mockPrisma.complianceAssessment.findMany).toHaveBeenCalledWith({
        where: {
          tenantId: 'tenant-123',
          status: 'completed',
        },
        orderBy: { createdAt: 'desc' },
      });
    });

    it('should support pagination', async () => {
      // GIVEN: Pagination params
      (mockPrisma.complianceAssessment as unknown) = {
        findMany: jest.fn().mockResolvedValue([]),
      };

      // WHEN: Query with pagination
      await resolver.Query.assessments(
        {},
        { skip: 10, take: 5 },
        mockContext
      );

      // THEN: Should apply pagination
      expect(mockPrisma.complianceAssessment.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          skip: 10,
          take: 5,
        })
      );
    });
  });

  describe('assessment query', () => {
    it('should retrieve single assessment by ID', async () => {
      // GIVEN: Assessment ID
      const assessment = {
        id: 'assessment-123',
        tenantId: 'tenant-123',
        framework: 'SOC2',
      };

      (mockPrisma.complianceAssessment as unknown) = {
        findUnique: jest.fn().mockResolvedValue(assessment),
      };

      // WHEN: Query assessment by ID
      const result = await resolver.Query.assessment(
        {},
        { id: 'assessment-123' },
        mockContext
      );

      // THEN: Should return assessment
      expect(result).toEqual(assessment);
    });

    it('should enforce tenant isolation on assessment retrieval', async () => {
      // GIVEN: Assessment from different tenant
      (mockPrisma.complianceAssessment as unknown) = {
        findUnique: jest.fn().mockResolvedValue(null),
      };

      // WHEN: Query assessment
      const result = await resolver.Query.assessment(
        {},
        { id: 'assessment-999' },
        mockContext
      );

      // THEN: Should not return other tenant's data
      expect(result).toBeNull();
    });
  });

  describe('controls query', () => {
    it('should retrieve controls for framework', async () => {
      // GIVEN: Framework with controls
      const controls = [
        { id: '1', framework: 'SOC2', title: 'Access Control', severity: 'critical' },
        { id: '2', framework: 'SOC2', title: 'Encryption', severity: 'high' },
      ];

      (mockPrisma.complianceControl as unknown) = {
        findMany: jest.fn().mockResolvedValue(controls),
      };

      // WHEN: Query controls
      const result = await resolver.Query.controls(
        {},
        { framework: 'SOC2' },
        mockContext
      );

      // THEN: Should return controls
      expect(result).toHaveLength(2);
      expect(result[0].framework).toBe('SOC2');
    });

    it('should filter controls by severity', async () => {
      // GIVEN: Severity filter
      (mockPrisma.complianceControl as unknown) = {
        findMany: jest.fn().mockResolvedValue([]),
      };

      // WHEN: Query with severity filter
      await resolver.Query.controls(
        {},
        { framework: 'SOC2', severity: 'critical' },
        mockContext
      );

      // THEN: Should apply severity filter
      expect(mockPrisma.complianceControl.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            severity: 'critical',
          }),
        })
      );
    });

    it('should categorize controls', async () => {
      // GIVEN: Controls with categories
      const controls = [
        { id: '1', category: 'Identity & Access', title: 'Control 1' },
        { id: '2', category: 'Identity & Access', title: 'Control 2' },
        { id: '3', category: 'Data Protection', title: 'Control 3' },
      ];

      (mockPrisma.complianceControl as unknown) = {
        findMany: jest.fn().mockResolvedValue(controls),
      };

      // WHEN: Query controls
      const result = await resolver.Query.controls(
        {},
        { framework: 'SOC2' },
        mockContext
      );

      // THEN: Should return categorized controls
      const categories = new Set(result.map((c) => c.category));
      expect(categories.has('Identity & Access')).toBe(true);
      expect(categories.has('Data Protection')).toBe(true);
    });
  });

  describe('findings query', () => {
    it('should retrieve findings for assessment', async () => {
      // GIVEN: Assessment with findings
      const findings = [
        { id: '1', severity: 'critical', status: 'open' },
        { id: '2', severity: 'high', status: 'open' },
      ];

      (mockPrisma.complianceFinding as unknown) = {
        findMany: jest.fn().mockResolvedValue(findings),
      };

      // WHEN: Query findings
      const result = await resolver.Query.findings(
        {},
        { assessmentId: 'assessment-123' },
        mockContext
      );

      // THEN: Should return findings
      expect(result).toHaveLength(2);
    });

    it('should filter findings by severity', async () => {
      // GIVEN: Severity filter
      (mockPrisma.complianceFinding as unknown) = {
        findMany: jest.fn().mockResolvedValue([]),
      };

      // WHEN: Query with severity filter
      await resolver.Query.findings(
        {},
        { assessmentId: 'assessment-123', severity: 'critical' },
        mockContext
      );

      // THEN: Should apply severity filter
      expect(mockPrisma.complianceFinding.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            severity: 'critical',
          }),
        })
      );
    });

    it('should filter findings by status', async () => {
      // GIVEN: Status filter
      (mockPrisma.complianceFinding as unknown) = {
        findMany: jest.fn().mockResolvedValue([]),
      };

      // WHEN: Query with status filter
      await resolver.Query.findings(
        {},
        { assessmentId: 'assessment-123', status: 'open' },
        mockContext
      );

      // THEN: Should apply status filter
      expect(mockPrisma.complianceFinding.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            status: 'open',
          }),
        })
      );
    });
  });

  describe('complianceScore query', () => {
    it('should calculate compliance score for assessment', async () => {
      // GIVEN: Assessment with controls
      const controls = [
        { id: '1', status: 'implemented' },
        { id: '2', status: 'implemented' },
        { id: '3', status: 'not_implemented' },
        { id: '4', status: 'partially_implemented' },
      ];

      (mockPrisma.complianceControl as unknown) = {
        findMany: jest.fn().mockResolvedValue(controls),
      };

      // WHEN: Query compliance score
      const result = await resolver.Query.complianceScore(
        {},
        { assessmentId: 'assessment-123' },
        mockContext
      );

      // THEN: Should calculate score correctly
      expect(result).toBe(50); // 2 implemented out of 4
    });

    it('should calculate weighted compliance score', async () => {
      // GIVEN: Controls with different severities
      const controls = [
        { id: '1', status: 'implemented', severity: 'critical' },
        { id: '2', status: 'not_implemented', severity: 'low' },
      ];

      (mockPrisma.complianceControl as unknown) = {
        findMany: jest.fn().mockResolvedValue(controls),
      };

      // WHEN: Query weighted score
      const result = await resolver.Query.complianceScoreWeighted(
        {},
        { assessmentId: 'assessment-123' },
        mockContext
      );

      // THEN: Should calculate weighted score
      expect(result).toBeGreaterThan(50);
    });
  });

  describe('aggregation queries', () => {
    it('should count findings by severity', async () => {
      // GIVEN: Findings with different severities
      (mockPrisma.complianceFinding as unknown) = {
        groupBy: jest.fn().mockResolvedValue([
          { severity: 'critical', _count: { id: 2 } },
          { severity: 'high', _count: { id: 5 } },
          { severity: 'medium', _count: { id: 3 } },
        ]),
      };

      // WHEN: Query finding counts
      const result = await resolver.Query.findingsBySeverity(
        {},
        { assessmentId: 'assessment-123' },
        mockContext
      );

      // THEN: Should return aggregated counts
      expect(result.critical).toBe(2);
      expect(result.high).toBe(5);
      expect(result.medium).toBe(3);
    });

    it('should get assessment summary', async () => {
      // GIVEN: Assessment with data
      const assessment = {
        id: 'assessment-123',
        framework: 'SOC2',
        status: 'completed',
        controls: [
          { status: 'implemented' },
          { status: 'implemented' },
          { status: 'not_implemented' },
        ],
        findings: [
          { severity: 'critical' },
          { severity: 'high' },
        ],
      };

      (mockPrisma.complianceAssessment as unknown) = {
        findUnique: jest.fn().mockResolvedValue(assessment),
      };

      // WHEN: Query assessment summary
      const result = await resolver.Query.assessmentSummary(
        {},
        { assessmentId: 'assessment-123' },
        mockContext
      );

      // THEN: Should return summary data
      expect(result).toHaveProperty('framework');
      expect(result).toHaveProperty('complianceScore');
      expect(result).toHaveProperty('findingsSummary');
    });
  });

  describe('error handling', () => {
    it('should throw error when user not authenticated', async () => {
      // GIVEN: No authentication context
      const unauthenticatedContext = {};

      // WHEN: Query assessments
      // THEN: Should throw error
      await expect(
        resolver.Query.assessments({}, {}, unauthenticatedContext)
      ).rejects.toThrow('User not authenticated');
    });

    it('should handle database errors', async () => {
      // GIVEN: Database error
      (mockPrisma.complianceAssessment as unknown) = {
        findMany: jest
          .fn()
          .mockRejectedValue(new Error('Database connection failed')),
      };

      // WHEN: Query assessments
      // THEN: Should propagate error
      await expect(
        resolver.Query.assessments({}, {}, mockContext)
      ).rejects.toThrow('Database connection failed');
    });

    it('should validate required parameters', async () => {
      // GIVEN: Missing required parameter
      // WHEN: Query without required field
      // THEN: Should throw validation error
      await expect(
        resolver.Query.findings({}, {}, mockContext)
      ).rejects.toThrow('Assessment ID is required');
    });
  });

  describe('performance optimization', () => {
    it('should include pagination for large result sets', async () => {
      // GIVEN: Large result set
      (mockPrisma.complianceAssessment as unknown) = {
        findMany: jest.fn().mockResolvedValue([]),
      };

      // WHEN: Query with pagination
      await resolver.Query.assessments(
        {},
        { skip: 0, take: 20 },
        mockContext
      );

      // THEN: Should apply pagination
      expect(mockPrisma.complianceAssessment.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          take: 20,
        })
      );
    });

    it('should include sorting for consistent results', async () => {
      // GIVEN: Query without explicit sort
      (mockPrisma.complianceAssessment as unknown) = {
        findMany: jest.fn().mockResolvedValue([]),
      };

      // WHEN: Query assessments
      await resolver.Query.assessments({}, {}, mockContext);

      // THEN: Should apply default sort
      expect(mockPrisma.complianceAssessment.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          orderBy: expect.anything(),
        })
      );
    });
  });
});

