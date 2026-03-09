/**
 * @doc.type module
 * @doc.purpose Knowledge Base Integration type definitions
 * @doc.layer product
 * @doc.pattern Types
 */

// Re-export types from service for external use
export type {
    FactCheckRequest,
    FactCheckResult,
    FactSource,
    KnowledgeBaseEntry,
    CurriculumStandard,
    ValidationRequest,
    ValidationResult,
    ValidationCheck,
} from './service';

// Additional types for the module
export interface KnowledgeBaseModuleConfig {
    enabled: boolean;
    sources: {
        wikipedia: {
            enabled: boolean;
            apiUrl: string;
            rateLimitPerMinute: number;
        };
        openStax: {
            enabled: boolean;
            apiUrl: string;
            rateLimitPerMinute: number;
        };
        khanAcademy: {
            enabled: boolean;
            apiUrl: string;
            rateLimitPerMinute: number;
        };
    };
    caching: {
        enabled: boolean;
        ttlSeconds: number;
        maxEntries: number;
    };
    validation: {
        strictMode: boolean;
        confidenceThreshold: number;
        riskThresholds: {
            low: number;
            medium: number;
            high: number;
        };
    };
}

export interface KnowledgeBaseMetrics {
    factChecksPerformed: number;
    conceptsSearched: number;
    examplesGenerated: number;
    validationsPerformed: number;
    averageConfidence: number;
    sourceReliability: Record<string, number>;
    cacheHitRate: number;
    processingTimeMs: number;
    errorRate: number;
}

export interface KnowledgeBaseHealthStatus {
    status: 'healthy' | 'warning' | 'critical';
    sources: {
        wikipedia: { available: boolean; responseTime: number; errorRate: number };
        openStax: { available: boolean; responseTime: number; errorRate: number };
        khanAcademy: { available: boolean; responseTime: number; errorRate: number };
    };
    cache: {
        connected: boolean;
        hitRate: number;
        memoryUsage: number;
        entryCount: number;
    };
    validation: {
        lastValidation: Date;
        successRate: number;
        averageScore: number;
    };
}
