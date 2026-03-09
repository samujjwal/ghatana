/**
 * @fileoverview Guardian Pipeline Module
 * 
 * Exports all pipeline components for the Guardian extension.
 * Implements the Source → Processor → Sink architecture using
 * @ghatana/dcmaar-browser-extension-core framework.
 * 
 * @module pipeline
 */

// Sources
export { TabActivitySource } from './sources/TabActivitySource';
export type { TabActivitySourceConfig } from './sources/TabActivitySource';
export { ContentScriptSource, sendToBackground } from './sources/ContentScriptSource';
export type { ContentScriptSourceConfig, ContentScriptMessage, ContentScriptMessageType } from './sources/ContentScriptSource';

// Processors
export { CategoryEnrichmentProcessor } from './processors/CategoryEnrichmentProcessor';
export type { CategoryEnrichmentConfig } from './processors/CategoryEnrichmentProcessor';
export { PolicyEvaluationProcessor } from './processors/PolicyEvaluationProcessor';
export type { PolicyEvaluationConfig } from './processors/PolicyEvaluationProcessor';

// Sinks
export { LocalStorageSink } from './sinks/LocalStorageSink';
export type { LocalStorageSinkConfig } from './sinks/LocalStorageSink';
export { RealTimeSyncSink } from './sinks/RealTimeSyncSink';
export type {
    RealTimeSyncSinkConfig,
    SyncMessage,
    SyncMessageType,
    ConnectionState,
    DownstreamHandler,
} from './sinks/RealTimeSyncSink';

// Types
export type {
    GuardianEvent,
    GuardianEventType,
    TabActivityEvent,
    ContentScriptEvent,
    PageMetadata,
    InteractionData,
    MediaTrackingData,
    DOMAnalysisData,
    EnrichedEvent,
    PolicyEvaluatedEvent,
    GuardianPipelineConfig,
    DailyUsage,
    DomainUsage,
} from './types';

// Pipeline factory
export { createGuardianPipeline, isGuardianPipelineInstance } from './GuardianPipeline';
export type { GuardianPipelineInstance } from './GuardianPipeline';
