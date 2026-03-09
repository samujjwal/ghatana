/**
 * @doc.type module
 * @doc.purpose Auto-Revision type definitions
 * @doc.layer product
 * @doc.pattern Types
 */

// Re-export types from service for external use
export type {
    DriftSignal,
    RegenerationInsight,
    RegenerationCandidate,
    ABExperiment,
    AutoRevisionConfig,
} from './service';

// Additional types for the module
export interface AutoRevisionModuleConfig {
    enabled: boolean;
    driftMonitoring: {
        enabled: boolean;
        intervalHours: number;
    };
    regeneration: {
        enabled: boolean;
        maxConcurrentJobs: number;
    };
    abTesting: {
        enabled: boolean;
        minSampleSize: number;
        significanceThreshold: number;
    };
}

export interface AutoRevisionMetrics {
    driftSignalsDetected: number;
    regenerationsQueued: number;
    regenerationsCompleted: number;
    abExperimentsCreated: number;
    abExperimentsCompleted: number;
    averageImprovementRate: number;
    lastDriftCheck: Date;
    lastRegeneration: Date;
}

export interface AutoRevisionHealthStatus {
    status: 'healthy' | 'warning' | 'critical';
    driftMonitoring: {
        lastCheck: Date;
        nextCheck: Date;
        status: 'running' | 'stopped' | 'error';
    };
    regenerationQueue: {
        pendingJobs: number;
        processingJobs: number;
        failedJobs: number;
        averageProcessingTime: number;
    };
    abTesting: {
        runningExperiments: number;
        completedExperiments: number;
        averageTestDuration: number;
    };
}
