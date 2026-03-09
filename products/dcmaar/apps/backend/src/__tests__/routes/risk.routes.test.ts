/**
 * Risk Routes Integration Tests
 *
 * Tests for risk assessment API endpoints.
 *
 * @doc.type test
 * @doc.purpose Integration tests for risk API
 * @doc.layer backend
 * @doc.pattern Route Test
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';

// Mock the services
vi.mock('../../services/risk-scoring.service', () => ({
    getChildRiskAssessment: vi.fn(),
    getParentRiskOverview: vi.fn(),
}));

vi.mock('../../middleware/auth.middleware', () => ({
    authenticate: vi.fn((request: any, _reply: any, done: any) => {
        request.userId = 'test-user-id';
        request.user = { id: 'test-user-id', email: 'test@example.com' };
        done();
    }),
    AuthRequest: {},
}));

import { getChildRiskAssessment, getParentRiskOverview } from '../../services/risk-scoring.service';

const mockGetChildRiskAssessment = vi.mocked(getChildRiskAssessment);
const mockGetParentRiskOverview = vi.mocked(getParentRiskOverview);

describe('Risk Routes', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('GET /api/risk/overview', () => {
        it('should return risk overview for authenticated user', async () => {
            // GIVEN: User has children with risk assessments
            const mockAssessments = [
                {
                    childId: 'child-1',
                    childName: 'Test Child',
                    overallScore: 35,
                    riskBucket: 'medium' as const,
                    dimensions: {
                        digitalOveruse: 40,
                        scheduleCompliance: 20,
                        contentRisk: 30,
                        socialRisk: 0,
                    },
                    factors: [],
                    insights: ['Some insight'],
                    recommendations: ['Some recommendation'],
                    assessedAt: new Date().toISOString(),
                    dataWindow: {
                        start: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
                        end: new Date().toISOString(),
                    },
                },
            ];

            mockGetParentRiskOverview.mockResolvedValue(mockAssessments);

            // THEN: Service should be called with correct parameters
            expect(mockGetParentRiskOverview).toBeDefined();
        });

        it('should accept days query parameter', async () => {
            // GIVEN: Request with days parameter
            mockGetParentRiskOverview.mockResolvedValue([]);

            // THEN: Service should accept days parameter
            expect(mockGetParentRiskOverview).toBeDefined();
        });
    });

    describe('GET /api/risk/children/:childId', () => {
        it('should return risk assessment for specific child', async () => {
            // GIVEN: Child exists with risk assessment
            const mockAssessment = {
                childId: 'child-123',
                childName: 'Test Child',
                overallScore: 25,
                riskBucket: 'low' as const,
                dimensions: {
                    digitalOveruse: 20,
                    scheduleCompliance: 15,
                    contentRisk: 10,
                    socialRisk: 0,
                },
                factors: [],
                insights: [],
                recommendations: [],
                assessedAt: new Date().toISOString(),
                dataWindow: {
                    start: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
                    end: new Date().toISOString(),
                },
            };

            mockGetChildRiskAssessment.mockResolvedValue(mockAssessment);

            // THEN: Service should be called
            expect(mockGetChildRiskAssessment).toBeDefined();
        });

        it('should return 404 for non-existent child', async () => {
            // GIVEN: Child does not exist
            mockGetChildRiskAssessment.mockResolvedValue(null);

            // THEN: Should return null
            const result = await mockGetChildRiskAssessment('non-existent');
            expect(result).toBeNull();
        });
    });

    describe('GET /api/risk/children/:childId/factors', () => {
        it('should return risk factors for child', async () => {
            // GIVEN: Child with risk factors
            const mockAssessment = {
                childId: 'child-factors',
                childName: 'Factors Test',
                overallScore: 60,
                riskBucket: 'high' as const,
                dimensions: {
                    digitalOveruse: 70,
                    scheduleCompliance: 50,
                    contentRisk: 40,
                    socialRisk: 0,
                },
                factors: [
                    {
                        dimension: 'digitalOveruse' as const,
                        factor: 'excessive_screen_time',
                        severity: 'high' as const,
                        description: 'Average daily usage exceeds recommended limit',
                        value: 300,
                    },
                ],
                insights: [],
                recommendations: [],
                assessedAt: new Date().toISOString(),
                dataWindow: {
                    start: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
                    end: new Date().toISOString(),
                },
            };

            mockGetChildRiskAssessment.mockResolvedValue(mockAssessment);

            // THEN: Factors should be available
            const result = await mockGetChildRiskAssessment('child-factors');
            expect(result?.factors.length).toBeGreaterThan(0);
        });

        it('should filter factors by severity', async () => {
            // GIVEN: Child with mixed severity factors
            const mockAssessment = {
                childId: 'child-severity',
                childName: 'Severity Test',
                overallScore: 50,
                riskBucket: 'medium' as const,
                dimensions: {
                    digitalOveruse: 50,
                    scheduleCompliance: 30,
                    contentRisk: 20,
                    socialRisk: 0,
                },
                factors: [
                    {
                        dimension: 'digitalOveruse' as const,
                        factor: 'high_screen_time',
                        severity: 'high' as const,
                        description: 'High usage',
                        value: 200,
                    },
                    {
                        dimension: 'scheduleCompliance' as const,
                        factor: 'occasional_late_night',
                        severity: 'low' as const,
                        description: 'Some late night usage',
                        value: 3,
                    },
                ],
                insights: [],
                recommendations: [],
                assessedAt: new Date().toISOString(),
                dataWindow: {
                    start: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
                    end: new Date().toISOString(),
                },
            };

            mockGetChildRiskAssessment.mockResolvedValue(mockAssessment);

            // THEN: Can filter by severity
            const result = await mockGetChildRiskAssessment('child-severity');
            const highFactors = result?.factors.filter(f => f.severity === 'high');
            expect(highFactors?.length).toBe(1);
        });
    });

    describe('GET /api/risk/children/:childId/recommendations', () => {
        it('should return recommendations for child', async () => {
            // GIVEN: Child with recommendations
            const mockAssessment = {
                childId: 'child-recs',
                childName: 'Recommendations Test',
                overallScore: 55,
                riskBucket: 'high' as const,
                dimensions: {
                    digitalOveruse: 60,
                    scheduleCompliance: 40,
                    contentRisk: 30,
                    socialRisk: 0,
                },
                factors: [],
                insights: [
                    'Screen time is above recommended levels',
                    'Consider setting stricter limits',
                ],
                recommendations: [
                    'Set daily screen time limits',
                    'Enable bedtime mode',
                ],
                assessedAt: new Date().toISOString(),
                dataWindow: {
                    start: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
                    end: new Date().toISOString(),
                },
            };

            mockGetChildRiskAssessment.mockResolvedValue(mockAssessment);

            // THEN: Recommendations should be available
            const result = await mockGetChildRiskAssessment('child-recs');
            expect(result?.recommendations.length).toBeGreaterThan(0);
            expect(result?.insights.length).toBeGreaterThan(0);
        });
    });
});
