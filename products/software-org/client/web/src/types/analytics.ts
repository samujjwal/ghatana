/**
 * Type definitions for analytics and insights
 * 
 * Provides types for:
 * - Permission usage analytics
 * - Role effectiveness metrics
 * - Security insights
 * - Anomaly detection
 * - Predictive recommendations
 * - Time series data
 * - Chart configurations
 */

/**
 * Time period for analytics
 */
export enum AnalyticsPeriod {
    LAST_24_HOURS = 'LAST_24_HOURS',
    LAST_7_DAYS = 'LAST_7_DAYS',
    LAST_30_DAYS = 'LAST_30_DAYS',
    LAST_90_DAYS = 'LAST_90_DAYS',
    LAST_YEAR = 'LAST_YEAR',
    CUSTOM = 'CUSTOM',
}

/**
 * Metric type
 */
export enum MetricType {
    PERMISSION_USAGE = 'PERMISSION_USAGE',
    ROLE_EFFECTIVENESS = 'ROLE_EFFECTIVENESS',
    ACCESS_PATTERNS = 'ACCESS_PATTERNS',
    SECURITY_SCORE = 'SECURITY_SCORE',
    COMPLIANCE_SCORE = 'COMPLIANCE_SCORE',
    USER_ACTIVITY = 'USER_ACTIVITY',
}

/**
 * Insight severity
 */
export enum InsightSeverity {
    CRITICAL = 'CRITICAL',
    WARNING = 'WARNING',
    INFO = 'INFO',
    POSITIVE = 'POSITIVE',
}

/**
 * Insight category
 */
export enum InsightCategory {
    SECURITY = 'SECURITY',
    COMPLIANCE = 'COMPLIANCE',
    PERFORMANCE = 'PERFORMANCE',
    USAGE = 'USAGE',
    COST = 'COST',
    OPTIMIZATION = 'OPTIMIZATION',
}

/**
 * Anomaly type
 */
export enum AnomalyType {
    UNUSUAL_ACCESS = 'UNUSUAL_ACCESS',
    PRIVILEGE_ESCALATION = 'PRIVILEGE_ESCALATION',
    BULK_CHANGES = 'BULK_CHANGES',
    OFF_HOURS_ACTIVITY = 'OFF_HOURS_ACTIVITY',
    GEOGRAPHIC_ANOMALY = 'GEOGRAPHIC_ANOMALY',
    PERMISSION_CREEP = 'PERMISSION_CREEP',
}

/**
 * Recommendation type
 */
export enum RecommendationType {
    PERMISSION_OPTIMIZATION = 'PERMISSION_OPTIMIZATION',
    ROLE_CONSOLIDATION = 'ROLE_CONSOLIDATION',
    ACCESS_REVIEW = 'ACCESS_REVIEW',
    SECURITY_HARDENING = 'SECURITY_HARDENING',
    COMPLIANCE_IMPROVEMENT = 'COMPLIANCE_IMPROVEMENT',
    AUTOMATION = 'AUTOMATION',
}

/**
 * Chart type
 */
export enum ChartType {
    LINE = 'LINE',
    BAR = 'BAR',
    PIE = 'PIE',
    AREA = 'AREA',
    SCATTER = 'SCATTER',
    HEATMAP = 'HEATMAP',
    RADAR = 'RADAR',
}

/**
 * Time series data point
 */
export interface TimeSeriesDataPoint {
    timestamp: string;
    value: number;
    label?: string;
    metadata?: Record<string, any>;
}

/**
 * Time series data
 */
export interface TimeSeriesData {
    series: {
        name: string;
        data: TimeSeriesDataPoint[];
        color?: string;
    }[];
    period: AnalyticsPeriod;
    startDate: string;
    endDate: string;
    aggregation?: 'hour' | 'day' | 'week' | 'month';
}

/**
 * Permission usage metric
 */
export interface PermissionUsageMetric {
    permissionId: string;
    permissionName: string;
    usageCount: number;
    uniqueUsers: number;
    lastUsed: string;
    trend: 'increasing' | 'decreasing' | 'stable';
    trendPercentage: number;
    averageUsagePerDay: number;
    peakUsageTime?: string;
    unusedDays?: number;
}

/**
 * Role effectiveness metric
 */
export interface RoleEffectivenessMetric {
    roleId: string;
    roleName: string;
    assignedUsers: number;
    activeUsers: number;
    utilizationRate: number; // 0-100%
    permissionsCount: number;
    unusedPermissions: number;
    overusedPermissions: number;
    effectivenessScore: number; // 0-100%
    recommendations: string[];
}

/**
 * Access pattern
 */
export interface AccessPattern {
    patternId: string;
    userId: string;
    userName?: string;
    resourceType: string;
    accessCount: number;
    firstAccess: string;
    lastAccess: string;
    averageAccessesPerDay: number;
    peakHours: number[];
    daysOfWeek: number[];
    isAnomaly: boolean;
    confidenceScore: number; // 0-100%
}

/**
 * Security insight
 */
export interface SecurityInsight {
    insightId: string;
    category: InsightCategory;
    severity: InsightSeverity;
    title: string;
    description: string;
    detectedAt: string;
    affectedResources: {
        resourceType: string;
        resourceId: string;
        resourceName?: string;
    }[];
    metrics: Record<string, number>;
    recommendations: string[];
    actionable: boolean;
    autoFixAvailable: boolean;
    estimatedImpact?: {
        security: number; // 0-100%
        performance: number; // 0-100%
        cost: number; // estimated cost impact
    };
}

/**
 * Anomaly detection result
 */
export interface AnomalyDetection {
    anomalyId: string;
    type: AnomalyType;
    severity: InsightSeverity;
    title: string;
    description: string;
    detectedAt: string;
    userId?: string;
    userName?: string;
    resourceId?: string;
    resourceName?: string;
    baselineValue: number;
    observedValue: number;
    deviationPercentage: number;
    confidenceScore: number; // 0-100%
    isConfirmed: boolean;
    isFalsePositive: boolean;
    resolvedAt?: string;
    resolution?: string;
    relatedEvents: string[];
}

/**
 * Recommendation
 */
export interface Recommendation {
    recommendationId: string;
    type: RecommendationType;
    priority: 'high' | 'medium' | 'low';
    title: string;
    description: string;
    rationale: string;
    generatedAt: string;
    affectedResources: {
        resourceType: string;
        resourceId: string;
        resourceName?: string;
    }[];
    expectedBenefits: {
        security?: string;
        compliance?: string;
        performance?: string;
        cost?: string;
    };
    estimatedEffort: 'low' | 'medium' | 'high';
    implementationSteps?: string[];
    risks?: string[];
    status: 'pending' | 'accepted' | 'rejected' | 'implemented';
    acceptedAt?: string;
    implementedAt?: string;
    feedback?: string;
}

/**
 * Analytics dashboard data
 */
export interface AnalyticsDashboard {
    summary: {
        totalPermissions: number;
        activePermissions: number;
        unusedPermissions: number;
        totalRoles: number;
        effectiveRoles: number;
        ineffectiveRoles: number;
        totalUsers: number;
        activeUsers: number;
        inactiveUsers: number;
        securityScore: number; // 0-100%
        complianceScore: number; // 0-100%
    };
    trends: {
        permissionUsage: TimeSeriesData;
        roleEffectiveness: TimeSeriesData;
        userActivity: TimeSeriesData;
        securityScore: TimeSeriesData;
    };
    topMetrics: {
        mostUsedPermissions: PermissionUsageMetric[];
        leastUsedPermissions: PermissionUsageMetric[];
        mostEffectiveRoles: RoleEffectivenessMetric[];
        leastEffectiveRoles: RoleEffectivenessMetric[];
    };
    recentInsights: SecurityInsight[];
    recentAnomalies: AnomalyDetection[];
    topRecommendations: Recommendation[];
}

/**
 * Chart configuration
 */
export interface ChartConfig {
    chartId: string;
    type: ChartType;
    title: string;
    description?: string;
    data: any;
    options?: {
        xAxis?: {
            label: string;
            type: 'time' | 'category' | 'number';
        };
        yAxis?: {
            label: string;
            type: 'number' | 'percentage';
            min?: number;
            max?: number;
        };
        legend?: {
            show: boolean;
            position: 'top' | 'bottom' | 'left' | 'right';
        };
        colors?: string[];
        stacked?: boolean;
        showDataLabels?: boolean;
        height?: number;
    };
}

/**
 * Analytics filter
 */
export interface AnalyticsFilter {
    period?: AnalyticsPeriod;
    startDate?: string;
    endDate?: string;
    metricTypes?: MetricType[];
    userIds?: string[];
    roleIds?: string[];
    permissionIds?: string[];
    resourceTypes?: string[];
    severities?: InsightSeverity[];
    categories?: InsightCategory[];
    limit?: number;
    offset?: number;
}

/**
 * Analytics query result
 */
export interface AnalyticsQueryResult<T> {
    items: T[];
    total: number;
    hasMore: boolean;
    aggregations?: Record<string, any>;
}

/**
 * Analytics export options
 */
export interface AnalyticsExportOptions {
    format: 'pdf' | 'csv' | 'json' | 'excel';
    includeCharts: boolean;
    includeTables: boolean;
    includeInsights: boolean;
    includeRecommendations: boolean;
    filter?: AnalyticsFilter;
    sections?: string[];
}

/**
 * ML model prediction
 */
export interface MLPrediction {
    predictionId: string;
    modelName: string;
    modelVersion: string;
    predictionType: 'anomaly' | 'recommendation' | 'forecast' | 'classification';
    input: Record<string, any>;
    output: Record<string, any>;
    confidence: number; // 0-100%
    timestamp: string;
    explanation?: string;
    features?: {
        name: string;
        value: any;
        importance: number; // 0-100%
    }[];
}

/**
 * Trend analysis result
 */
export interface TrendAnalysis {
    metric: string;
    period: AnalyticsPeriod;
    direction: 'increasing' | 'decreasing' | 'stable' | 'volatile';
    magnitude: number; // percentage change
    significance: 'high' | 'medium' | 'low';
    forecast?: {
        nextValue: number;
        confidence: number; // 0-100%
        range: {
            min: number;
            max: number;
        };
    };
    seasonality?: {
        detected: boolean;
        period?: 'daily' | 'weekly' | 'monthly';
    };
}

/**
 * Correlation analysis result
 */
export interface CorrelationAnalysis {
    metric1: string;
    metric2: string;
    correlationCoefficient: number; // -1 to 1
    strength: 'strong' | 'moderate' | 'weak' | 'none';
    significance: number; // p-value
    interpretation: string;
    visualizationData?: TimeSeriesData;
}

/**
 * Benchmark comparison
 */
export interface BenchmarkComparison {
    metric: string;
    currentValue: number;
    benchmarkValue: number;
    industryAverage: number;
    percentile: number; // 0-100
    status: 'above' | 'below' | 'at' | 'unknown';
    gap: number;
    recommendations?: string[];
}

/**
 * Analytics statistics
 */
export interface AnalyticsStats {
    totalInsights: number;
    criticalInsights: number;
    resolvedInsights: number;
    totalAnomalies: number;
    confirmedAnomalies: number;
    falsePositives: number;
    totalRecommendations: number;
    acceptedRecommendations: number;
    implementedRecommendations: number;
    averageSecurityScore: number;
    averageComplianceScore: number;
    topCategories: Record<InsightCategory, number>;
    topSeverities: Record<InsightSeverity, number>;
    trendSummary: {
        improving: number;
        declining: number;
        stable: number;
    };
}
