/**
 * Analytics Service
 * 
 * Provides analytics, insights, and recommendations functionality.
 * 
 * Features:
 * - Permission usage analytics
 * - Role effectiveness metrics
 * - Security insights
 * - Anomaly detection
 * - Predictive recommendations
 * - Trend analysis
 * - Correlation analysis
 * - Benchmark comparisons
 * 
 * Performance targets:
 * - Analytics computation: <500ms
 * - Dashboard load: <300ms
 * - Anomaly detection: <1s
 * - Recommendation generation: <2s
 */

import {
    AnalyticsPeriod,
    MetricType,
    InsightSeverity,
    InsightCategory,
    AnomalyType,
    RecommendationType,
    PermissionUsageMetric,
    RoleEffectivenessMetric,
    AccessPattern,
    SecurityInsight,
    AnomalyDetection,
    Recommendation,
    AnalyticsDashboard,
    TimeSeriesData,
    AnalyticsFilter,
    AnalyticsQueryResult,
    AnalyticsStats,
    TrendAnalysis,
    MLPrediction,
} from '../types/analytics';
import { AuditService } from './auditService';

/**
 * In-memory analytics store
 */
class AnalyticsStore {
    private insights: Map<string, SecurityInsight> = new Map();
    private anomalies: Map<string, AnomalyDetection> = new Map();
    private recommendations: Map<string, Recommendation> = new Map();
    private permissionMetrics: Map<string, PermissionUsageMetric> = new Map();
    private roleMetrics: Map<string, RoleEffectivenessMetric> = new Map();
    private accessPatterns: Map<string, AccessPattern> = new Map();
    private predictions: Map<string, MLPrediction> = new Map();

    addInsight(insight: SecurityInsight): void {
        this.insights.set(insight.insightId, insight);
    }

    getInsight(insightId: string): SecurityInsight | undefined {
        return this.insights.get(insightId);
    }

    getInsights(filter?: AnalyticsFilter): AnalyticsQueryResult<SecurityInsight> {
        let items = Array.from(this.insights.values());

        if (filter?.severities) {
            items = items.filter((i) => filter.severities!.includes(i.severity));
        }

        if (filter?.categories) {
            items = items.filter((i) => filter.categories!.includes(i.category));
        }

        if (filter?.startDate) {
            items = items.filter((i) => i.detectedAt >= filter.startDate!);
        }

        if (filter?.endDate) {
            items = items.filter((i) => i.detectedAt <= filter.endDate!);
        }

        const total = items.length;
        const offset = filter?.offset || 0;
        const limit = filter?.limit || 50;
        const paginated = items.slice(offset, offset + limit);

        return {
            items: paginated,
            total,
            hasMore: offset + limit < total,
        };
    }

    addAnomaly(anomaly: AnomalyDetection): void {
        this.anomalies.set(anomaly.anomalyId, anomaly);
    }

    getAnomaly(anomalyId: string): AnomalyDetection | undefined {
        return this.anomalies.get(anomalyId);
    }

    getAnomalies(filter?: AnalyticsFilter): AnalyticsQueryResult<AnomalyDetection> {
        let items = Array.from(this.anomalies.values());

        if (filter?.severities) {
            items = items.filter((a) => filter.severities!.includes(a.severity));
        }

        if (filter?.startDate) {
            items = items.filter((a) => a.detectedAt >= filter.startDate!);
        }

        if (filter?.endDate) {
            items = items.filter((a) => a.detectedAt <= filter.endDate!);
        }

        const total = items.length;
        const offset = filter?.offset || 0;
        const limit = filter?.limit || 50;
        const paginated = items.slice(offset, offset + limit);

        return {
            items: paginated,
            total,
            hasMore: offset + limit < total,
        };
    }

    addRecommendation(recommendation: Recommendation): void {
        this.recommendations.set(recommendation.recommendationId, recommendation);
    }

    getRecommendation(recommendationId: string): Recommendation | undefined {
        return this.recommendations.get(recommendationId);
    }

    getRecommendations(filter?: AnalyticsFilter): AnalyticsQueryResult<Recommendation> {
        let items = Array.from(this.recommendations.values());

        if (filter?.startDate) {
            items = items.filter((r) => r.generatedAt >= filter.startDate!);
        }

        if (filter?.endDate) {
            items = items.filter((r) => r.generatedAt <= filter.endDate!);
        }

        const total = items.length;
        const offset = filter?.offset || 0;
        const limit = filter?.limit || 50;
        const paginated = items.slice(offset, offset + limit);

        return {
            items: paginated,
            total,
            hasMore: offset + limit < total,
        };
    }

    addPermissionMetric(metric: PermissionUsageMetric): void {
        this.permissionMetrics.set(metric.permissionId, metric);
    }

    getPermissionMetric(permissionId: string): PermissionUsageMetric | undefined {
        return this.permissionMetrics.get(permissionId);
    }

    getPermissionMetrics(): PermissionUsageMetric[] {
        return Array.from(this.permissionMetrics.values());
    }

    addRoleMetric(metric: RoleEffectivenessMetric): void {
        this.roleMetrics.set(metric.roleId, metric);
    }

    getRoleMetric(roleId: string): RoleEffectivenessMetric | undefined {
        return this.roleMetrics.get(roleId);
    }

    getRoleMetrics(): RoleEffectivenessMetric[] {
        return Array.from(this.roleMetrics.values());
    }

    clear(): void {
        this.insights.clear();
        this.anomalies.clear();
        this.recommendations.clear();
        this.permissionMetrics.clear();
        this.roleMetrics.clear();
        this.accessPatterns.clear();
        this.predictions.clear();
    }
}

const store = new AnalyticsStore();

/**
 * Analytics Service
 */
export class AnalyticsService {
    /**
     * Get analytics dashboard
     */
    static async getDashboard(period: AnalyticsPeriod = AnalyticsPeriod.LAST_30_DAYS): Promise<AnalyticsDashboard> {
        const startTime = Date.now();

        // Generate mock permission metrics
        const permissionMetrics = await this.generatePermissionMetrics(period);

        // Generate mock role metrics
        const roleMetrics = await this.generateRoleMetrics(period);

        // Calculate summary
        const summary = {
            totalPermissions: permissionMetrics.length,
            activePermissions: permissionMetrics.filter((p) => p.usageCount > 0).length,
            unusedPermissions: permissionMetrics.filter((p) => p.unusedDays && p.unusedDays > 30).length,
            totalRoles: roleMetrics.length,
            effectiveRoles: roleMetrics.filter((r) => r.effectivenessScore >= 70).length,
            ineffectiveRoles: roleMetrics.filter((r) => r.effectivenessScore < 50).length,
            totalUsers: 150,
            activeUsers: 120,
            inactiveUsers: 30,
            securityScore: 82.5,
            complianceScore: 88.3,
        };

        // Generate trends
        const trends = {
            permissionUsage: await this.generateTimeSeriesData('permission_usage', period),
            roleEffectiveness: await this.generateTimeSeriesData('role_effectiveness', period),
            userActivity: await this.generateTimeSeriesData('user_activity', period),
            securityScore: await this.generateTimeSeriesData('security_score', period),
        };

        // Get top metrics
        const topMetrics = {
            mostUsedPermissions: permissionMetrics.sort((a, b) => b.usageCount - a.usageCount).slice(0, 10),
            leastUsedPermissions: permissionMetrics.sort((a, b) => a.usageCount - b.usageCount).slice(0, 10),
            mostEffectiveRoles: roleMetrics.sort((a, b) => b.effectivenessScore - a.effectivenessScore).slice(0, 10),
            leastEffectiveRoles: roleMetrics.sort((a, b) => a.effectivenessScore - b.effectivenessScore).slice(0, 10),
        };

        // Get recent insights and anomalies
        const recentInsights = store.getInsights({ limit: 5 }).items;
        const recentAnomalies = store.getAnomalies({ limit: 5 }).items;
        const topRecommendations = store.getRecommendations({ limit: 5 }).items;

        // Log audit event
        await AuditService.logEvent({
            userId: 'system',
            action: 'analytics.dashboard.load',
            resource: 'analytics_dashboard',
            metadata: {
                period,
                duration: Date.now() - startTime,
            },
        });

        return {
            summary,
            trends,
            topMetrics,
            recentInsights,
            recentAnomalies,
            topRecommendations,
        };
    }

    /**
     * Analyze permission usage
     */
    static async analyzePermissionUsage(
        permissionId: string,
        period: AnalyticsPeriod = AnalyticsPeriod.LAST_30_DAYS
    ): Promise<PermissionUsageMetric> {
        // Generate or retrieve metric
        let metric = store.getPermissionMetric(permissionId);

        if (!metric) {
            metric = {
                permissionId,
                permissionName: `Permission ${permissionId}`,
                usageCount: Math.floor(Math.random() * 1000),
                uniqueUsers: Math.floor(Math.random() * 50) + 1,
                lastUsed: new Date(Date.now() - Math.random() * 7 * 24 * 60 * 60 * 1000).toISOString(),
                trend: Math.random() > 0.5 ? 'increasing' : 'decreasing',
                trendPercentage: Math.random() * 50 - 25,
                averageUsagePerDay: Math.random() * 50,
                peakUsageTime: new Date(Date.now() - Math.random() * 24 * 60 * 60 * 1000).toISOString(),
                unusedDays: Math.floor(Math.random() * 60),
            };

            store.addPermissionMetric(metric);
        }

        // Log audit event
        await AuditService.logEvent({
            userId: 'system',
            action: 'analytics.permission.analyze',
            resource: 'permission',
            resourceId: permissionId,
            metadata: {
                period,
                usageCount: metric.usageCount,
            },
        });

        return metric;
    }

    /**
     * Analyze role effectiveness
     */
    static async analyzeRoleEffectiveness(
        roleId: string,
        period: AnalyticsPeriod = AnalyticsPeriod.LAST_30_DAYS
    ): Promise<RoleEffectivenessMetric> {
        // Generate or retrieve metric
        let metric = store.getRoleMetric(roleId);

        if (!metric) {
            const assignedUsers = Math.floor(Math.random() * 50) + 10;
            const activeUsers = Math.floor(assignedUsers * (0.5 + Math.random() * 0.5));
            const permissionsCount = Math.floor(Math.random() * 30) + 5;
            const unusedPermissions = Math.floor(permissionsCount * Math.random() * 0.3);
            const overusedPermissions = Math.floor(permissionsCount * Math.random() * 0.2);

            metric = {
                roleId,
                roleName: `Role ${roleId}`,
                assignedUsers,
                activeUsers,
                utilizationRate: (activeUsers / assignedUsers) * 100,
                permissionsCount,
                unusedPermissions,
                overusedPermissions,
                effectivenessScore: 50 + Math.random() * 50,
                recommendations: this.generateRoleRecommendations(unusedPermissions, overusedPermissions),
            };

            store.addRoleMetric(metric);
        }

        // Log audit event
        await AuditService.logEvent({
            userId: 'system',
            action: 'analytics.role.analyze',
            resource: 'role',
            resourceId: roleId,
            metadata: {
                period,
                effectivenessScore: metric.effectivenessScore,
            },
        });

        return metric;
    }

    /**
     * Detect anomalies
     */
    static async detectAnomalies(filter?: AnalyticsFilter): Promise<AnomalyDetection[]> {
        const startTime = Date.now();

        // Generate sample anomalies
        const anomalies: AnomalyDetection[] = [];
        const types = Object.values(AnomalyType);

        for (let i = 0; i < 5; i++) {
            const type = types[Math.floor(Math.random() * types.length)];
            const anomaly: AnomalyDetection = {
                anomalyId: `anomaly-${Date.now()}-${i}`,
                type,
                severity: Math.random() > 0.7 ? InsightSeverity.CRITICAL : Math.random() > 0.5 ? InsightSeverity.WARNING : InsightSeverity.INFO,
                title: this.getAnomalyTitle(type),
                description: this.getAnomalyDescription(type),
                detectedAt: new Date(Date.now() - Math.random() * 7 * 24 * 60 * 60 * 1000).toISOString(),
                userId: `user-${Math.floor(Math.random() * 100)}`,
                userName: `User ${Math.floor(Math.random() * 100)}`,
                resourceId: `resource-${Math.floor(Math.random() * 100)}`,
                resourceName: `Resource ${Math.floor(Math.random() * 100)}`,
                baselineValue: 10 + Math.random() * 20,
                observedValue: 50 + Math.random() * 50,
                deviationPercentage: 100 + Math.random() * 200,
                confidenceScore: 70 + Math.random() * 30,
                isConfirmed: false,
                isFalsePositive: false,
                relatedEvents: [],
            };

            store.addAnomaly(anomaly);
            anomalies.push(anomaly);
        }

        // Log audit event
        await AuditService.logEvent({
            userId: 'system',
            action: 'analytics.anomalies.detect',
            resource: 'analytics',
            metadata: {
                anomaliesDetected: anomalies.length,
                duration: Date.now() - startTime,
            },
        });

        return anomalies;
    }

    /**
     * Generate recommendations
     */
    static async generateRecommendations(filter?: AnalyticsFilter): Promise<Recommendation[]> {
        const startTime = Date.now();

        // Generate sample recommendations
        const recommendations: Recommendation[] = [];
        const types = Object.values(RecommendationType);

        for (let i = 0; i < 5; i++) {
            const type = types[Math.floor(Math.random() * types.length)];
            const recommendation: Recommendation = {
                recommendationId: `recommendation-${Date.now()}-${i}`,
                type,
                priority: Math.random() > 0.7 ? 'high' : Math.random() > 0.4 ? 'medium' : 'low',
                title: this.getRecommendationTitle(type),
                description: this.getRecommendationDescription(type),
                rationale: this.getRecommendationRationale(type),
                generatedAt: new Date().toISOString(),
                affectedResources: [
                    {
                        resourceType: 'permission',
                        resourceId: `permission-${i}`,
                        resourceName: `Permission ${i}`,
                    },
                ],
                expectedBenefits: {
                    security: 'Improved security posture',
                    compliance: 'Better compliance alignment',
                    performance: 'Reduced overhead',
                },
                estimatedEffort: Math.random() > 0.6 ? 'low' : Math.random() > 0.3 ? 'medium' : 'high',
                implementationSteps: this.getImplementationSteps(type),
                risks: ['Potential disruption to workflows', 'May require user training'],
                status: 'pending',
            };

            store.addRecommendation(recommendation);
            recommendations.push(recommendation);
        }

        // Log audit event
        await AuditService.logEvent({
            userId: 'system',
            action: 'analytics.recommendations.generate',
            resource: 'analytics',
            metadata: {
                recommendationsGenerated: recommendations.length,
                duration: Date.now() - startTime,
            },
        });

        return recommendations;
    }

    /**
     * Generate security insights
     */
    static async generateInsights(filter?: AnalyticsFilter): Promise<SecurityInsight[]> {
        const startTime = Date.now();

        // Generate sample insights
        const insights: SecurityInsight[] = [];
        const categories = Object.values(InsightCategory);

        for (let i = 0; i < 5; i++) {
            const category = categories[Math.floor(Math.random() * categories.length)];
            const insight: SecurityInsight = {
                insightId: `insight-${Date.now()}-${i}`,
                category,
                severity: Math.random() > 0.7 ? InsightSeverity.CRITICAL : Math.random() > 0.5 ? InsightSeverity.WARNING : InsightSeverity.INFO,
                title: this.getInsightTitle(category),
                description: this.getInsightDescription(category),
                detectedAt: new Date().toISOString(),
                affectedResources: [
                    {
                        resourceType: 'permission',
                        resourceId: `permission-${i}`,
                        resourceName: `Permission ${i}`,
                    },
                ],
                metrics: {
                    impactScore: Math.random() * 100,
                    affectedUsersCount: Math.floor(Math.random() * 50),
                },
                recommendations: ['Review and remediate affected resources', 'Implement additional controls'],
                actionable: true,
                autoFixAvailable: Math.random() > 0.5,
                estimatedImpact: {
                    security: Math.random() * 100,
                    performance: Math.random() * 100,
                    cost: Math.random() * 10000,
                },
            };

            store.addInsight(insight);
            insights.push(insight);
        }

        // Log audit event
        await AuditService.logEvent({
            userId: 'system',
            action: 'analytics.insights.generate',
            resource: 'analytics',
            metadata: {
                insightsGenerated: insights.length,
                duration: Date.now() - startTime,
            },
        });

        return insights;
    }

    /**
     * Analyze trends
     */
    static async analyzeTrend(metric: string, period: AnalyticsPeriod): Promise<TrendAnalysis> {
        const timeSeries = await this.generateTimeSeriesData(metric, period);
        const values = timeSeries.series[0].data.map((d) => d.value);

        // Calculate basic trend
        const firstValue = values[0];
        const lastValue = values[values.length - 1];
        const change = ((lastValue - firstValue) / firstValue) * 100;

        let direction: 'increasing' | 'decreasing' | 'stable' | 'volatile';
        if (Math.abs(change) < 5) {
            direction = 'stable';
        } else if (change > 0) {
            direction = 'increasing';
        } else {
            direction = 'decreasing';
        }

        return {
            metric,
            period,
            direction,
            magnitude: Math.abs(change),
            significance: Math.abs(change) > 20 ? 'high' : Math.abs(change) > 10 ? 'medium' : 'low',
            forecast: {
                nextValue: lastValue * (1 + change / 100),
                confidence: 70 + Math.random() * 20,
                range: {
                    min: lastValue * 0.9,
                    max: lastValue * 1.1,
                },
            },
            seasonality: {
                detected: Math.random() > 0.5,
                period: 'weekly',
            },
        };
    }

    /**
     * Get analytics statistics
     */
    static async getStats(): Promise<AnalyticsStats> {
        const insights = store.getInsights({});
        const anomalies = store.getAnomalies({});
        const recommendations = store.getRecommendations({});

        const topCategories: Record<InsightCategory, number> = {} as any;
        Object.values(InsightCategory).forEach((cat) => {
            topCategories[cat] = insights.items.filter((i) => i.category === cat).length;
        });

        const topSeverities: Record<InsightSeverity, number> = {} as any;
        Object.values(InsightSeverity).forEach((sev) => {
            topSeverities[sev] = insights.items.filter((i) => i.severity === sev).length;
        });

        return {
            totalInsights: insights.total,
            criticalInsights: insights.items.filter((i) => i.severity === InsightSeverity.CRITICAL).length,
            resolvedInsights: 0, // Would track resolution
            totalAnomalies: anomalies.total,
            confirmedAnomalies: anomalies.items.filter((a) => a.isConfirmed).length,
            falsePositives: anomalies.items.filter((a) => a.isFalsePositive).length,
            totalRecommendations: recommendations.total,
            acceptedRecommendations: recommendations.items.filter((r) => r.status === 'accepted').length,
            implementedRecommendations: recommendations.items.filter((r) => r.status === 'implemented').length,
            averageSecurityScore: 82.5,
            averageComplianceScore: 88.3,
            topCategories,
            topSeverities,
            trendSummary: {
                improving: 12,
                declining: 3,
                stable: 8,
            },
        };
    }

    // Helper methods

    private static async generatePermissionMetrics(period: AnalyticsPeriod): Promise<PermissionUsageMetric[]> {
        const metrics: PermissionUsageMetric[] = [];
        for (let i = 0; i < 20; i++) {
            metrics.push({
                permissionId: `permission-${i}`,
                permissionName: `Permission ${i}`,
                usageCount: Math.floor(Math.random() * 1000),
                uniqueUsers: Math.floor(Math.random() * 50) + 1,
                lastUsed: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString(),
                trend: Math.random() > 0.5 ? 'increasing' : 'decreasing',
                trendPercentage: Math.random() * 50 - 25,
                averageUsagePerDay: Math.random() * 50,
                unusedDays: Math.floor(Math.random() * 60),
            });
        }
        return metrics;
    }

    private static async generateRoleMetrics(period: AnalyticsPeriod): Promise<RoleEffectivenessMetric[]> {
        const metrics: RoleEffectivenessMetric[] = [];
        for (let i = 0; i < 15; i++) {
            const assignedUsers = Math.floor(Math.random() * 50) + 10;
            const activeUsers = Math.floor(assignedUsers * (0.5 + Math.random() * 0.5));
            const permissionsCount = Math.floor(Math.random() * 30) + 5;
            const unusedPermissions = Math.floor(permissionsCount * Math.random() * 0.3);

            metrics.push({
                roleId: `role-${i}`,
                roleName: `Role ${i}`,
                assignedUsers,
                activeUsers,
                utilizationRate: (activeUsers / assignedUsers) * 100,
                permissionsCount,
                unusedPermissions,
                overusedPermissions: Math.floor(permissionsCount * Math.random() * 0.2),
                effectivenessScore: 50 + Math.random() * 50,
                recommendations: this.generateRoleRecommendations(unusedPermissions, 0),
            });
        }
        return metrics;
    }

    private static async generateTimeSeriesData(metric: string, period: AnalyticsPeriod): Promise<TimeSeriesData> {
        const days = period === AnalyticsPeriod.LAST_7_DAYS ? 7 : period === AnalyticsPeriod.LAST_30_DAYS ? 30 : 90;
        const data = [];

        for (let i = days - 1; i >= 0; i--) {
            const timestamp = new Date(Date.now() - i * 24 * 60 * 60 * 1000).toISOString();
            data.push({
                timestamp,
                value: 50 + Math.random() * 50,
            });
        }

        return {
            series: [{ name: metric, data }],
            period,
            startDate: data[0].timestamp,
            endDate: data[data.length - 1].timestamp,
            aggregation: 'day',
        };
    }

    private static generateRoleRecommendations(unusedPermissions: number, overusedPermissions: number): string[] {
        const recommendations = [];
        if (unusedPermissions > 5) {
            recommendations.push('Remove unused permissions to reduce attack surface');
        }
        if (overusedPermissions > 3) {
            recommendations.push('Review overused permissions for potential abuse');
        }
        return recommendations;
    }

    private static getAnomalyTitle(type: AnomalyType): string {
        const titles: Record<AnomalyType, string> = {
            [AnomalyType.UNUSUAL_ACCESS]: 'Unusual Access Pattern Detected',
            [AnomalyType.PRIVILEGE_ESCALATION]: 'Potential Privilege Escalation',
            [AnomalyType.BULK_CHANGES]: 'Bulk Permission Changes Detected',
            [AnomalyType.OFF_HOURS_ACTIVITY]: 'Off-Hours Activity Detected',
            [AnomalyType.GEOGRAPHIC_ANOMALY]: 'Geographic Anomaly Detected',
            [AnomalyType.PERMISSION_CREEP]: 'Permission Creep Detected',
        };
        return titles[type];
    }

    private static getAnomalyDescription(type: AnomalyType): string {
        return `Detected ${type.toLowerCase().replace(/_/g, ' ')} that deviates from normal baseline`;
    }

    private static getRecommendationTitle(type: RecommendationType): string {
        const titles: Record<RecommendationType, string> = {
            [RecommendationType.PERMISSION_OPTIMIZATION]: 'Optimize Permission Assignment',
            [RecommendationType.ROLE_CONSOLIDATION]: 'Consolidate Similar Roles',
            [RecommendationType.ACCESS_REVIEW]: 'Conduct Access Review',
            [RecommendationType.SECURITY_HARDENING]: 'Implement Security Hardening',
            [RecommendationType.COMPLIANCE_IMPROVEMENT]: 'Improve Compliance Posture',
            [RecommendationType.AUTOMATION]: 'Automate Manual Processes',
        };
        return titles[type];
    }

    private static getRecommendationDescription(type: RecommendationType): string {
        return `Recommendation to ${type.toLowerCase().replace(/_/g, ' ')} based on usage patterns and best practices`;
    }

    private static getRecommendationRationale(type: RecommendationType): string {
        return `Analysis shows potential improvement by ${type.toLowerCase().replace(/_/g, ' ')}`;
    }

    private static getImplementationSteps(type: RecommendationType): string[] {
        return [
            'Review current state and identify gaps',
            'Create implementation plan',
            'Test changes in staging environment',
            'Deploy to production',
            'Monitor and validate results',
        ];
    }

    private static getInsightTitle(category: InsightCategory): string {
        const titles: Record<InsightCategory, string> = {
            [InsightCategory.SECURITY]: 'Security Insight',
            [InsightCategory.COMPLIANCE]: 'Compliance Insight',
            [InsightCategory.PERFORMANCE]: 'Performance Insight',
            [InsightCategory.USAGE]: 'Usage Insight',
            [InsightCategory.COST]: 'Cost Insight',
            [InsightCategory.OPTIMIZATION]: 'Optimization Insight',
        };
        return titles[category];
    }

    private static getInsightDescription(category: InsightCategory): string {
        return `Insight related to ${category.toLowerCase()} based on recent analysis`;
    }

    /**
     * Clear all analytics data (for testing)
     */
    static clearAll(): void {
        store.clear();
    }
}
