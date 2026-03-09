/**
 * Risk Scoring Service Tests
 *
 * Tests for risk score calculation including:
 * - Digital overuse scoring
 * - Content risk scoring
 * - Schedule compliance scoring
 * - Overall risk assessment
 *
 * @doc.type test
 * @doc.purpose Unit tests for risk scoring logic
 * @doc.layer backend
 * @doc.pattern Service Test
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';

// Mock the database query function
vi.mock('../../db', () => ({
    query: vi.fn(),
}));

import { query } from '../../db';
import {
    getChildRiskAssessment,
    getParentRiskOverview,
} from '../../services/risk-scoring.service';

const mockQuery = vi.mocked(query);

describe('RiskScoringService', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('getChildRiskAssessment', () => {
        it('should return null for non-existent child', async () => {
            // GIVEN: Child does not exist
            mockQuery.mockResolvedValueOnce([]); // Child query returns empty

            // WHEN: Risk assessment is requested
            const result = await getChildRiskAssessment('non-existent-id');

            // THEN: Returns null
            expect(result).toBeNull();
        });

        it('should calculate low risk for minimal usage', async () => {
            // GIVEN: Child with minimal usage
            const childId = 'child-123';
            const birthDate = new Date();
            birthDate.setFullYear(birthDate.getFullYear() - 10); // 10 years old

            // Mock child query
            mockQuery.mockResolvedValueOnce([
                { id: childId, name: 'Test Child', birth_date: birthDate },
            ]);

            // Mock usage stats - minimal usage
            mockQuery.mockResolvedValueOnce([]); // Peak hour query
            mockQuery.mockResolvedValueOnce([
                { total_seconds: '1800', session_count: '2' }, // 30 minutes total
            ]);

            // Mock block stats - no blocks
            mockQuery.mockResolvedValueOnce([
                { total_blocks: '0', unique_domains: '0' },
            ]);
            mockQuery.mockResolvedValueOnce([]); // Category counts

            // Mock schedule compliance - no late night usage
            mockQuery.mockResolvedValueOnce([{ count: '0' }]);

            // WHEN: Risk assessment is calculated
            const result = await getChildRiskAssessment(childId);

            // THEN: Risk is low
            expect(result).not.toBeNull();
            expect(result!.riskBucket).toBe('low');
            expect(result!.overallScore).toBeLessThan(25);
            expect(result!.dimensions.digitalOveruse).toBeLessThan(25);
            expect(result!.dimensions.contentRisk).toBe(0);
        });

        it('should calculate high risk for excessive usage', async () => {
            // GIVEN: Child with excessive usage
            const childId = 'child-456';
            const birthDate = new Date();
            birthDate.setFullYear(birthDate.getFullYear() - 8); // 8 years old

            // Mock child query
            mockQuery.mockResolvedValueOnce([
                { id: childId, name: 'Heavy User', birth_date: birthDate },
            ]);

            // Mock usage stats - 5 hours daily average (way over limit for 8yo)
            mockQuery.mockResolvedValueOnce([
                { total_seconds: '126000', session_count: '20', peak_hour: 22 },
            ]);
            mockQuery.mockResolvedValueOnce([
                { total_seconds: '126000', session_count: '20' }, // 35 hours over 7 days = 5h/day
            ]);

            // Mock block stats - many blocks including adult content
            mockQuery.mockResolvedValueOnce([
                { total_blocks: '50', unique_domains: '15' },
            ]);
            mockQuery.mockResolvedValueOnce([
                { category: 'adult', count: '5' },
                { category: 'social', count: '30' },
                { category: 'gaming', count: '15' },
            ]);

            // Mock schedule compliance - frequent late night usage
            mockQuery.mockResolvedValueOnce([{ count: '15' }]);

            // WHEN: Risk assessment is calculated
            const result = await getChildRiskAssessment(childId);

            // THEN: Risk is high or critical
            expect(result).not.toBeNull();
            expect(['high', 'critical']).toContain(result!.riskBucket);
            expect(result!.overallScore).toBeGreaterThan(50);
            expect(result!.factors.length).toBeGreaterThan(0);
        });

        it('should include content risk factors for adult content blocks', async () => {
            // GIVEN: Child with adult content blocks
            const childId = 'child-789';
            const birthDate = new Date();
            birthDate.setFullYear(birthDate.getFullYear() - 12);

            mockQuery.mockResolvedValueOnce([
                { id: childId, name: 'Content Test', birth_date: birthDate },
            ]);

            // Minimal usage
            mockQuery.mockResolvedValueOnce([]);
            mockQuery.mockResolvedValueOnce([
                { total_seconds: '3600', session_count: '5' },
            ]);

            // Adult content blocks
            mockQuery.mockResolvedValueOnce([
                { total_blocks: '10', unique_domains: '5' },
            ]);
            mockQuery.mockResolvedValueOnce([
                { category: 'adult', count: '8' },
                { category: 'gambling', count: '2' },
            ]);

            // No late night usage
            mockQuery.mockResolvedValueOnce([{ count: '0' }]);

            // WHEN: Risk assessment is calculated
            const result = await getChildRiskAssessment(childId);

            // THEN: Content risk is elevated
            expect(result).not.toBeNull();
            expect(result!.dimensions.contentRisk).toBeGreaterThan(30);

            const contentFactors = result!.factors.filter(f => f.dimension === 'contentRisk');
            expect(contentFactors.length).toBeGreaterThan(0);
            expect(contentFactors.some(f => f.factor.includes('adult'))).toBe(true);
        });

        it('should include schedule compliance factors for late night usage', async () => {
            // GIVEN: Child with late night usage
            const childId = 'child-late';
            const birthDate = new Date();
            birthDate.setFullYear(birthDate.getFullYear() - 14);

            mockQuery.mockResolvedValueOnce([
                { id: childId, name: 'Night Owl', birth_date: birthDate },
            ]);

            // Normal usage
            mockQuery.mockResolvedValueOnce([]);
            mockQuery.mockResolvedValueOnce([
                { total_seconds: '7200', session_count: '10' },
            ]);

            // No blocks
            mockQuery.mockResolvedValueOnce([
                { total_blocks: '0', unique_domains: '0' },
            ]);
            mockQuery.mockResolvedValueOnce([]);

            // Heavy late night usage
            mockQuery.mockResolvedValueOnce([{ count: '12' }]);

            // WHEN: Risk assessment is calculated
            const result = await getChildRiskAssessment(childId);

            // THEN: Schedule compliance risk is elevated
            expect(result).not.toBeNull();
            expect(result!.dimensions.scheduleCompliance).toBeGreaterThan(25);

            const scheduleFactors = result!.factors.filter(f => f.dimension === 'scheduleCompliance');
            expect(scheduleFactors.length).toBeGreaterThan(0);
        });

        it('should generate insights for high risk children', async () => {
            // GIVEN: High risk child
            const childId = 'child-insights';
            const birthDate = new Date();
            birthDate.setFullYear(birthDate.getFullYear() - 10);

            mockQuery.mockResolvedValueOnce([
                { id: childId, name: 'Insights Test', birth_date: birthDate },
            ]);

            // High usage
            mockQuery.mockResolvedValueOnce([]);
            mockQuery.mockResolvedValueOnce([
                { total_seconds: '72000', session_count: '30' }, // ~3h/day
            ]);

            // Some blocks
            mockQuery.mockResolvedValueOnce([
                { total_blocks: '20', unique_domains: '10' },
            ]);
            mockQuery.mockResolvedValueOnce([
                { category: 'social', count: '15' },
            ]);

            // Some late night
            mockQuery.mockResolvedValueOnce([{ count: '8' }]);

            // WHEN: Risk assessment is calculated
            const result = await getChildRiskAssessment(childId);

            // THEN: Insights are generated
            expect(result).not.toBeNull();
            expect(result!.insights.length).toBeGreaterThan(0);
            expect(result!.recommendations.length).toBeGreaterThan(0);
        });

        it('should include data window in assessment', async () => {
            // GIVEN: Valid child
            const childId = 'child-window';
            const birthDate = new Date();
            birthDate.setFullYear(birthDate.getFullYear() - 10);

            mockQuery.mockResolvedValueOnce([
                { id: childId, name: 'Window Test', birth_date: birthDate },
            ]);
            mockQuery.mockResolvedValueOnce([]);
            mockQuery.mockResolvedValueOnce([{ total_seconds: '0', session_count: '0' }]);
            mockQuery.mockResolvedValueOnce([{ total_blocks: '0', unique_domains: '0' }]);
            mockQuery.mockResolvedValueOnce([]);
            mockQuery.mockResolvedValueOnce([{ count: '0' }]);

            // WHEN: Risk assessment is calculated with 14 day window
            const result = await getChildRiskAssessment(childId, 14);

            // THEN: Data window is included
            expect(result).not.toBeNull();
            expect(result!.dataWindow).toBeDefined();
            expect(result!.dataWindow.start).toBeDefined();
            expect(result!.dataWindow.end).toBeDefined();
        });
    });

    describe('getParentRiskOverview', () => {
        it('should return empty array for parent with no children', async () => {
            // GIVEN: Parent with no children
            mockQuery.mockResolvedValueOnce([]);

            // WHEN: Overview is requested
            const result = await getParentRiskOverview('parent-no-kids');

            // THEN: Empty array returned
            expect(result).toEqual([]);
        });

        it('should return assessments sorted by risk score descending', async () => {
            // GIVEN: Parent with multiple children
            const parentId = 'parent-multi';

            // Mock children query
            mockQuery.mockResolvedValueOnce([
                { id: 'child-1' },
                { id: 'child-2' },
            ]);

            // Mock first child - low risk
            const birthDate1 = new Date();
            birthDate1.setFullYear(birthDate1.getFullYear() - 10);
            mockQuery.mockResolvedValueOnce([
                { id: 'child-1', name: 'Low Risk', birth_date: birthDate1 },
            ]);
            mockQuery.mockResolvedValueOnce([]);
            mockQuery.mockResolvedValueOnce([{ total_seconds: '1800', session_count: '2' }]);
            mockQuery.mockResolvedValueOnce([{ total_blocks: '0', unique_domains: '0' }]);
            mockQuery.mockResolvedValueOnce([]);
            mockQuery.mockResolvedValueOnce([{ count: '0' }]);

            // Mock second child - high risk
            const birthDate2 = new Date();
            birthDate2.setFullYear(birthDate2.getFullYear() - 8);
            mockQuery.mockResolvedValueOnce([
                { id: 'child-2', name: 'High Risk', birth_date: birthDate2 },
            ]);
            mockQuery.mockResolvedValueOnce([]);
            mockQuery.mockResolvedValueOnce([{ total_seconds: '100000', session_count: '50' }]);
            mockQuery.mockResolvedValueOnce([{ total_blocks: '30', unique_domains: '10' }]);
            mockQuery.mockResolvedValueOnce([{ category: 'adult', count: '5' }]);
            mockQuery.mockResolvedValueOnce([{ count: '10' }]);

            // WHEN: Overview is requested
            const result = await getParentRiskOverview(parentId);

            // THEN: Results are sorted by risk (highest first)
            expect(result.length).toBe(2);
            expect(result[0].overallScore).toBeGreaterThanOrEqual(result[1].overallScore);
        });
    });

    describe('Risk Bucket Calculation', () => {
        it('should assign correct buckets based on score thresholds', async () => {
            // This tests the internal getBucketFromScore logic through the service
            const childId = 'bucket-test';
            const birthDate = new Date();
            birthDate.setFullYear(birthDate.getFullYear() - 10);

            // Test low bucket (score < 25)
            mockQuery.mockResolvedValueOnce([
                { id: childId, name: 'Bucket Test', birth_date: birthDate },
            ]);
            mockQuery.mockResolvedValueOnce([]);
            mockQuery.mockResolvedValueOnce([{ total_seconds: '0', session_count: '0' }]);
            mockQuery.mockResolvedValueOnce([{ total_blocks: '0', unique_domains: '0' }]);
            mockQuery.mockResolvedValueOnce([]);
            mockQuery.mockResolvedValueOnce([{ count: '0' }]);

            const lowResult = await getChildRiskAssessment(childId);
            expect(lowResult!.riskBucket).toBe('low');
        });
    });
});
