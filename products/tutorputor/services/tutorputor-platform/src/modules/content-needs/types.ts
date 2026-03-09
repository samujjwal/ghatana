/**
 * @doc.type module
 * @doc.purpose Content Needs Analyzer type definitions
 * @doc.layer product
 * @doc.pattern Types
 */

// Re-export types from service for external use
export type {
    ContentNeeds,
    ContentContext,
    ClaimAnalysis,
    ExampleType,
    InteractionType,
    AnimationType,
} from './service';

// Additional types for the module
export interface ContentNeedsModuleConfig {
    enabled: boolean;
    aiIntegration: {
        enabled: boolean;
        model: string;
        maxTokens: number;
        temperature: number;
    };
    caching: {
        enabled: boolean;
        ttlSeconds: number;
    };
    analytics: {
        enabled: boolean;
        trackUsage: boolean;
    };
}

export interface ContentNeedsMetrics {
    analysesPerformed: number;
    contentGenerated: number;
    averageConfidence: number;
    domainDistribution: Record<string, number>;
    bloomLevelDistribution: Record<string, number>;
    processingTimeMs: number;
    cacheHitRate: number;
}

export interface ContentNeedsHealthStatus {
    status: 'healthy' | 'warning' | 'critical';
    aiService: {
        available: boolean;
        responseTime: number;
        errorRate: number;
    };
    database: {
        connected: boolean;
        queryTime: number;
    };
    cache: {
        connected: boolean;
        hitRate: number;
        memoryUsage: number;
    };
}
