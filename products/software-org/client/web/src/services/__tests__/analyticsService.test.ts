import { describe, it, expect, beforeEach, vi } from 'vitest';
import { AnalyticsService } from '../analyticsService';
import {
    AnalyticsPeriod,
    MetricType,
    InsightSeverity,
    InsightCategory,
    AnomalyType,
    RecommendationType,
    type AnalyticsDashboard,
    type PermissionUsageMetric,
    type RoleEffectivenessMetric,
    type SecurityInsight,
    type AnomalyDetection,
    type Recommendation,
    type TrendAnalysis,
} from '../../types/analytics';

describe('AnalyticsService', () => {
    beforeEach(() => {
        // Clear the store before each test
        AnalyticsService['store'].clear();
        vi.clearAllMocks();
    });

    describe('Dashboard Generation', () => {
        it('should generate dashboard with all metrics', async () => {
            const dashboard = await AnalyticsService.getDashboard(AnalyticsPeriod.LAST_30_DAYS);

            expect(dashboard).toBeDefined();
            expect(dashboard.summary).toBeDefined();
            expect(dashboard.trends).toBeDefined();
            expect(dashboard.topMetrics).toBeDefined();
            expect(dashboard.recentInsights).toBeDefined();
            expect(dashboard.recentAnomalies).toBeDefined();
            expect(dashboard.topRecommendations).toBeDefined();
        });

        it('should calculate summary statistics', async () => {
            const dashboard = await AnalyticsService.getDashboard(AnalyticsPeriod.LAST_7_DAYS);

            expect(dashboard.summary.totalPermissions).toBeGreaterThan(0);
            expect(dashboard.summary.totalRoles).toBeGreaterThan(0);
            expect(dashboard.summary.totalUsers).toBeGreaterThan(0);
            expect(dashboard.summary.securityScore).toBeGreaterThanOrEqual(0);
            expect(dashboard.summary.securityScore).toBeLessThanOrEqual(100);
            expect(dashboard.summary.complianceScore).toBeGreaterThanOrEqual(0);
            expect(dashboard.summary.complianceScore).toBeLessThanOrEqual(100);
        });

        it('should generate trends for all metric types', async () => {
            const dashboard = await AnalyticsService.getDashboard(AnalyticsPeriod.LAST_30_DAYS);

            expect(dashboard.trends.permissionUsage).toBeDefined();
            expect(dashboard.trends.roleEffectiveness).toBeDefined();
            expect(dashboard.trends.userActivity).toBeDefined();
            expect(dashboard.trends.securityScore).toBeDefined();

            expect(dashboard.trends.permissionUsage.series.length).toBeGreaterThan(0);
            expect(dashboard.trends.roleEffectiveness.series.length).toBeGreaterThan(0);
        });

        it('should include top metrics', async () => {
            const dashboard = await AnalyticsService.getDashboard(AnalyticsPeriod.LAST_30_DAYS);

            expect(dashboard.topMetrics.mostUsedPermissions.length).toBe(5);
            expect(dashboard.topMetrics.leastUsedPermissions.length).toBe(5);
            expect(dashboard.topMetrics.mostEffectiveRoles.length).toBe(5);
            expect(dashboard.topMetrics.leastEffectiveRoles.length).toBe(5);
        });

        it('should include recent insights', async () => {
            const dashboard = await AnalyticsService.getDashboard(AnalyticsPeriod.LAST_7_DAYS);

            expect(Array.isArray(dashboard.recentInsights)).toBe(true);
            expect(dashboard.recentInsights.length).toBeLessThanOrEqual(10);
        });

        it('should include recent anomalies', async () => {
            const dashboard = await AnalyticsService.getDashboard(AnalyticsPeriod.LAST_7_DAYS);

            expect(Array.isArray(dashboard.recentAnomalies)).toBe(true);
            expect(dashboard.recentAnomalies.length).toBeLessThanOrEqual(5);
        });

        it('should include top recommendations', async () => {
            const dashboard = await AnalyticsService.getDashboard(AnalyticsPeriod.LAST_30_DAYS);

            expect(Array.isArray(dashboard.topRecommendations)).toBe(true);
            expect(dashboard.topRecommendations.length).toBeLessThanOrEqual(6);
        });

        it('should handle different time periods', async () => {
            const last24h = await AnalyticsService.getDashboard(AnalyticsPeriod.LAST_24_HOURS);
            const last7d = await AnalyticsService.getDashboard(AnalyticsPeriod.LAST_7_DAYS);
            const last30d = await AnalyticsService.getDashboard(AnalyticsPeriod.LAST_30_DAYS);

            expect(last24h.trends.permissionUsage.period).toBe(AnalyticsPeriod.LAST_24_HOURS);
            expect(last7d.trends.permissionUsage.period).toBe(AnalyticsPeriod.LAST_7_DAYS);
            expect(last30d.trends.permissionUsage.period).toBe(AnalyticsPeriod.LAST_30_DAYS);
        });
    });

    describe('Permission Usage Analysis', () => {
        it('should analyze permission usage', async () => {
            const metric = await AnalyticsService.analyzePermissionUsage(
                'perm1',
                AnalyticsPeriod.LAST_30_DAYS
            );

            expect(metric).toBeDefined();
            expect(metric.permissionId).toBe('perm1');
            expect(metric.permissionName).toBeDefined();
            expect(metric.usageCount).toBeGreaterThanOrEqual(0);
            expect(metric.uniqueUsers).toBeGreaterThanOrEqual(0);
        });

        it('should calculate usage trend', async () => {
            const metric = await AnalyticsService.analyzePermissionUsage(
                'perm1',
                AnalyticsPeriod.LAST_7_DAYS
            );

            expect(metric.trend).toMatch(/increasing|decreasing|stable/);
            expect(typeof metric.trendPercentage).toBe('number');
        });

        it('should calculate average usage per day', async () => {
            const metric = await AnalyticsService.analyzePermissionUsage(
                'perm1',
                AnalyticsPeriod.LAST_30_DAYS
            );

            expect(metric.averageUsagePerDay).toBeGreaterThanOrEqual(0);
        });

        it('should identify peak usage time', async () => {
            const metric = await AnalyticsService.analyzePermissionUsage(
                'perm1',
                AnalyticsPeriod.LAST_7_DAYS
            );

            if (metric.peakUsageTime) {
                expect(typeof metric.peakUsageTime).toBe('string');
            }
        });

        it('should track unused days', async () => {
            const metric = await AnalyticsService.analyzePermissionUsage(
                'perm1',
                AnalyticsPeriod.LAST_30_DAYS
            );

            if (metric.unusedDays !== undefined) {
                expect(metric.unusedDays).toBeGreaterThanOrEqual(0);
            }
        });
    });

    describe('Role Effectiveness Analysis', () => {
        it('should analyze role effectiveness', async () => {
            const metric = await AnalyticsService.analyzeRoleEffectiveness(
                'role1',
                AnalyticsPeriod.LAST_30_DAYS
            );

            expect(metric).toBeDefined();
            expect(metric.roleId).toBe('role1');
            expect(metric.roleName).toBeDefined();
            expect(metric.assignedUsers).toBeGreaterThanOrEqual(0);
            expect(metric.activeUsers).toBeGreaterThanOrEqual(0);
        });

        it('should calculate utilization rate', async () => {
            const metric = await AnalyticsService.analyzeRoleEffectiveness(
                'role1',
                AnalyticsPeriod.LAST_30_DAYS
            );

            expect(metric.utilizationRate).toBeGreaterThanOrEqual(0);
            expect(metric.utilizationRate).toBeLessThanOrEqual(100);
        });

        it('should calculate effectiveness score', async () => {
            const metric = await AnalyticsService.analyzeRoleEffectiveness(
                'role1',
                AnalyticsPeriod.LAST_7_DAYS
            );

            expect(metric.effectivenessScore).toBeGreaterThanOrEqual(0);
            expect(metric.effectivenessScore).toBeLessThanOrEqual(100);
        });

        it('should identify unused permissions', async () => {
            const metric = await AnalyticsService.analyzeRoleEffectiveness(
                'role1',
                AnalyticsPeriod.LAST_30_DAYS
            );

            expect(metric.unusedPermissions).toBeGreaterThanOrEqual(0);
            expect(metric.unusedPermissions).toBeLessThanOrEqual(metric.permissionsCount);
        });

        it('should identify overused permissions', async () => {
            const metric = await AnalyticsService.analyzeRoleEffectiveness(
                'role1',
                AnalyticsPeriod.LAST_30_DAYS
            );

            expect(metric.overusedPermissions).toBeGreaterThanOrEqual(0);
        });

        it('should generate recommendations', async () => {
            const metric = await AnalyticsService.analyzeRoleEffectiveness(
                'role1',
                AnalyticsPeriod.LAST_30_DAYS
            );

            expect(Array.isArray(metric.recommendations)).toBe(true);
        });
    });

    describe('Anomaly Detection', () => {
        it('should detect anomalies', async () => {
            const anomalies = await AnalyticsService.detectAnomalies();

            expect(Array.isArray(anomalies)).toBe(true);
            anomalies.forEach(anomaly => {
                expect(anomaly.anomalyId).toBeDefined();
                expect(anomaly.type).toBeDefined();
                expect(anomaly.severity).toBeDefined();
            });
        });

        it('should calculate confidence scores', async () => {
            const anomalies = await AnalyticsService.detectAnomalies();

            anomalies.forEach(anomaly => {
                expect(anomaly.confidenceScore).toBeGreaterThanOrEqual(0);
                expect(anomaly.confidenceScore).toBeLessThanOrEqual(100);
            });
        });

        it('should calculate deviation percentage', async () => {
            const anomalies = await AnalyticsService.detectAnomalies();

            anomalies.forEach(anomaly => {
                expect(typeof anomaly.deviationPercentage).toBe('number');
                expect(anomaly.baselineValue).toBeDefined();
                expect(anomaly.observedValue).toBeDefined();
            });
        });

        it('should filter by severity', async () => {
            const anomalies = await AnalyticsService.detectAnomalies({
                severity: InsightSeverity.CRITICAL,
            });

            anomalies.forEach(anomaly => {
                expect(anomaly.severity).toBe(InsightSeverity.CRITICAL);
            });
        });

        it('should filter by type', async () => {
            const anomalies = await AnalyticsService.detectAnomalies({
                type: AnomalyType.UNUSUAL_ACCESS,
            });

            anomalies.forEach(anomaly => {
                expect(anomaly.type).toBe(AnomalyType.UNUSUAL_ACCESS);
            });
        });

        it('should include related events', async () => {
            const anomalies = await AnalyticsService.detectAnomalies();

            anomalies.forEach(anomaly => {
                expect(Array.isArray(anomaly.relatedEvents)).toBe(true);
            });
        });

        it('should track confirmation status', async () => {
            const anomalies = await AnalyticsService.detectAnomalies();

            anomalies.forEach(anomaly => {
                expect(typeof anomaly.isConfirmed).toBe('boolean');
                expect(typeof anomaly.isFalsePositive).toBe('boolean');
            });
        });
    });

    describe('Recommendation Generation', () => {
        it('should generate recommendations', async () => {
            const recommendations = await AnalyticsService.generateRecommendations();

            expect(Array.isArray(recommendations)).toBe(true);
            recommendations.forEach(rec => {
                expect(rec.recommendationId).toBeDefined();
                expect(rec.type).toBeDefined();
                expect(rec.priority).toMatch(/high|medium|low/);
            });
        });

        it('should include expected benefits', async () => {
            const recommendations = await AnalyticsService.generateRecommendations();

            recommendations.forEach(rec => {
                expect(rec.expectedBenefits).toBeDefined();
            });
        });

        it('should estimate effort', async () => {
            const recommendations = await AnalyticsService.generateRecommendations();

            recommendations.forEach(rec => {
                expect(rec.estimatedEffort).toMatch(/low|medium|high/);
            });
        });

        it('should include implementation steps', async () => {
            const recommendations = await AnalyticsService.generateRecommendations();

            recommendations.forEach(rec => {
                if (rec.implementationSteps) {
                    expect(Array.isArray(rec.implementationSteps)).toBe(true);
                }
            });
        });

        it('should filter by priority', async () => {
            const recommendations = await AnalyticsService.generateRecommendations({
                priority: 'high',
            });

            recommendations.forEach(rec => {
                expect(rec.priority).toBe('high');
            });
        });

        it('should filter by type', async () => {
            const recommendations = await AnalyticsService.generateRecommendations({
                type: RecommendationType.PERMISSION_OPTIMIZATION,
            });

            recommendations.forEach(rec => {
                expect(rec.type).toBe(RecommendationType.PERMISSION_OPTIMIZATION);
            });
        });
    });

    describe('Security Insights', () => {
        it('should generate security insights', async () => {
            const insights = await AnalyticsService.generateInsights();

            expect(Array.isArray(insights)).toBe(true);
            insights.forEach(insight => {
                expect(insight.insightId).toBeDefined();
                expect(insight.category).toBeDefined();
                expect(insight.severity).toBeDefined();
            });
        });

        it('should categorize insights', async () => {
            const insights = await AnalyticsService.generateInsights();

            insights.forEach(insight => {
                expect(Object.values(InsightCategory)).toContain(insight.category);
            });
        });

        it('should assign severity levels', async () => {
            const insights = await AnalyticsService.generateInsights();

            insights.forEach(insight => {
                expect(Object.values(InsightSeverity)).toContain(insight.severity);
            });
        });

        it('should identify actionable insights', async () => {
            const insights = await AnalyticsService.generateInsights();

            insights.forEach(insight => {
                expect(typeof insight.actionable).toBe('boolean');
                expect(typeof insight.autoFixAvailable).toBe('boolean');
            });
        });

        it('should include affected resources', async () => {
            const insights = await AnalyticsService.generateInsights();

            insights.forEach(insight => {
                expect(Array.isArray(insight.affectedResources)).toBe(true);
            });
        });

        it('should estimate impact', async () => {
            const insights = await AnalyticsService.generateInsights();

            insights.forEach(insight => {
                if (insight.estimatedImpact) {
                    expect(typeof insight.estimatedImpact).toBe('object');
                }
            });
        });

        it('should filter by category', async () => {
            const insights = await AnalyticsService.generateInsights({
                category: InsightCategory.SECURITY,
            });

            insights.forEach(insight => {
                expect(insight.category).toBe(InsightCategory.SECURITY);
            });
        });

        it('should filter by severity', async () => {
            const insights = await AnalyticsService.generateInsights({
                severity: InsightSeverity.CRITICAL,
            });

            insights.forEach(insight => {
                expect(insight.severity).toBe(InsightSeverity.CRITICAL);
            });
        });
    });

    describe('Trend Analysis', () => {
        it('should analyze trends', async () => {
            const trend = await AnalyticsService.analyzeTrend(
                MetricType.PERMISSION_USAGE,
                AnalyticsPeriod.LAST_30_DAYS
            );

            expect(trend).toBeDefined();
            expect(trend.metric).toBe(MetricType.PERMISSION_USAGE);
            expect(trend.period).toBe(AnalyticsPeriod.LAST_30_DAYS);
        });

        it('should identify trend direction', async () => {
            const trend = await AnalyticsService.analyzeTrend(
                MetricType.SECURITY_SCORE,
                AnalyticsPeriod.LAST_7_DAYS
            );

            expect(trend.direction).toMatch(/increasing|decreasing|stable|volatile/);
        });

        it('should calculate magnitude', async () => {
            const trend = await AnalyticsService.analyzeTrend(
                MetricType.USER_ACTIVITY,
                AnalyticsPeriod.LAST_30_DAYS
            );

            expect(typeof trend.magnitude).toBe('number');
        });

        it('should assess significance', async () => {
            const trend = await AnalyticsService.analyzeTrend(
                MetricType.ROLE_EFFECTIVENESS,
                AnalyticsPeriod.LAST_30_DAYS
            );

            expect(trend.significance).toMatch(/high|medium|low/);
        });

        it('should include forecast', async () => {
            const trend = await AnalyticsService.analyzeTrend(
                MetricType.PERMISSION_USAGE,
                AnalyticsPeriod.LAST_30_DAYS
            );

            if (trend.forecast) {
                expect(trend.forecast.nextValue).toBeDefined();
                expect(trend.forecast.confidence).toBeGreaterThanOrEqual(0);
                expect(trend.forecast.confidence).toBeLessThanOrEqual(100);
                expect(trend.forecast.range).toBeDefined();
                expect(trend.forecast.range.min).toBeDefined();
                expect(trend.forecast.range.max).toBeDefined();
            }
        });

        it('should detect seasonality', async () => {
            const trend = await AnalyticsService.analyzeTrend(
                MetricType.USER_ACTIVITY,
                AnalyticsPeriod.LAST_90_DAYS
            );

            if (trend.seasonality) {
                expect(typeof trend.seasonality.detected).toBe('boolean');
                if (trend.seasonality.detected) {
                    expect(trend.seasonality.period).toMatch(/daily|weekly|monthly/);
                }
            }
        });
    });

    describe('Statistics', () => {
        it('should generate comprehensive statistics', async () => {
            const stats = await AnalyticsService.getStats();

            expect(stats).toBeDefined();
            expect(stats.totalInsights).toBeGreaterThanOrEqual(0);
            expect(stats.totalAnomalies).toBeGreaterThanOrEqual(0);
            expect(stats.totalRecommendations).toBeGreaterThanOrEqual(0);
        });

        it('should group insights by category', async () => {
            const stats = await AnalyticsService.getStats();

            expect(stats.insightsByCategory).toBeDefined();
            expect(typeof stats.insightsByCategory).toBe('object');
        });

        it('should group insights by severity', async () => {
            const stats = await AnalyticsService.getStats();

            expect(stats.insightsBySeverity).toBeDefined();
            expect(typeof stats.insightsBySeverity).toBe('object');
        });

        it('should group anomalies by type', async () => {
            const stats = await AnalyticsService.getStats();

            expect(stats.anomaliesByType).toBeDefined();
            expect(typeof stats.anomaliesByType).toBe('object');
        });

        it('should group recommendations by priority', async () => {
            const stats = await AnalyticsService.getStats();

            expect(stats.recommendationsByPriority).toBeDefined();
            expect(typeof stats.recommendationsByPriority).toBe('object');
        });

        it('should calculate average confidence score', async () => {
            const stats = await AnalyticsService.getStats();

            if (stats.averageConfidenceScore !== undefined) {
                expect(stats.averageConfidenceScore).toBeGreaterThanOrEqual(0);
                expect(stats.averageConfidenceScore).toBeLessThanOrEqual(100);
            }
        });
    });

    describe('Performance', () => {
        it('should generate dashboard within 300ms', async () => {
            const start = Date.now();
            await AnalyticsService.getDashboard(AnalyticsPeriod.LAST_30_DAYS);
            const duration = Date.now() - start;

            expect(duration).toBeLessThan(300);
        });

        it('should analyze permission usage within 500ms', async () => {
            const start = Date.now();
            await AnalyticsService.analyzePermissionUsage('perm1', AnalyticsPeriod.LAST_30_DAYS);
            const duration = Date.now() - start;

            expect(duration).toBeLessThan(500);
        });

        it('should detect anomalies within 1000ms', async () => {
            const start = Date.now();
            await AnalyticsService.detectAnomalies();
            const duration = Date.now() - start;

            expect(duration).toBeLessThan(1000);
        });

        it('should generate recommendations within 2000ms', async () => {
            const start = Date.now();
            await AnalyticsService.generateRecommendations();
            const duration = Date.now() - start;

            expect(duration).toBeLessThan(2000);
        });

        it('should generate insights within 500ms', async () => {
            const start = Date.now();
            await AnalyticsService.generateInsights();
            const duration = Date.now() - start;

            expect(duration).toBeLessThan(500);
        });
    });

    describe('Error Handling', () => {
        it('should handle invalid permission id', async () => {
            const metric = await AnalyticsService.analyzePermissionUsage(
                'invalid-id',
                AnalyticsPeriod.LAST_30_DAYS
            );

            expect(metric).toBeDefined();
            expect(metric.permissionId).toBe('invalid-id');
        });

        it('should handle invalid role id', async () => {
            const metric = await AnalyticsService.analyzeRoleEffectiveness(
                'invalid-id',
                AnalyticsPeriod.LAST_30_DAYS
            );

            expect(metric).toBeDefined();
            expect(metric.roleId).toBe('invalid-id');
        });

        it('should handle empty filter results', async () => {
            const insights = await AnalyticsService.generateInsights({
                category: InsightCategory.SECURITY,
                severity: InsightSeverity.CRITICAL,
            });

            expect(Array.isArray(insights)).toBe(true);
        });
    });
});
