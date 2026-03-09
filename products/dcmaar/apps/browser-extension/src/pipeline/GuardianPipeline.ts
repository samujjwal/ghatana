/**
 * @fileoverview Guardian Pipeline Factory
 * 
 * Factory function to create and configure the Guardian event pipeline.
 * Wires together sources, processors, and sinks based on configuration.
 * 
 * @module pipeline/GuardianPipeline
 */

import { EventPipeline } from '@ghatana/dcmaar-browser-extension-core';
import { TabActivitySource } from './sources/TabActivitySource';
import { ContentScriptSource } from './sources/ContentScriptSource';
import { CategoryEnrichmentProcessor } from './processors/CategoryEnrichmentProcessor';
import { PolicyEvaluationProcessor } from './processors/PolicyEvaluationProcessor';
import { LocalStorageSink } from './sinks/LocalStorageSink';
import { RealTimeSyncSink } from './sinks/RealTimeSyncSink';
import type { WebsiteBlocker } from '../blocker/WebsiteBlocker';
import type { GuardianPipelineConfig } from './types';

/**
 * Default pipeline configuration
 */
const DEFAULT_CONFIG: Required<GuardianPipelineConfig> = {
    name: 'guardian',
    continueOnError: true,
    enableTabSource: true,
    enableContentScript: false,
    enableRealTimeSync: false,
    realTimeSyncUrl: '',
    deviceId: '',
    retentionDays: 7,
};

/**
 * Guardian Pipeline instance with typed components
 */
export interface GuardianPipelineInstance {
    /** The event pipeline */
    pipeline: EventPipeline;
    /** Tab activity source */
    tabSource: TabActivitySource;
    /** Content script source (if enabled) */
    contentSource?: ContentScriptSource;
    /** Category enrichment processor */
    categoryProcessor: CategoryEnrichmentProcessor;
    /** Policy evaluation processor */
    policyProcessor: PolicyEvaluationProcessor;
    /** Local storage sink */
    storageSink: LocalStorageSink;
    /** Real-time sync sink (if enabled) */
    syncSink?: RealTimeSyncSink;
    /** Start the pipeline */
    start: () => Promise<void>;
    /** Stop the pipeline */
    stop: () => Promise<void>;
    /** Get pipeline statistics */
    getStats: () => {
        pipeline: ReturnType<EventPipeline['getStats']>;
        storage: ReturnType<LocalStorageSink['getStats']>;
        sync?: ReturnType<RealTimeSyncSink['getStats']>;
    };
}

/**
 * Create and configure the Guardian event pipeline
 * 
 * This factory function creates a fully configured pipeline with:
 * - TabActivitySource: Captures tab navigation and switches
 * - ContentScriptSource: Receives rich page metrics (optional)
 * - CategoryEnrichmentProcessor: Adds website category information
 * - PolicyEvaluationProcessor: Evaluates blocking policies
 * - LocalStorageSink: Persists events to browser storage
 * - RealTimeSyncSink: Streams events to parent dashboard (optional)
 * 
 * @example
 * ```typescript
 * const blocker = new WebsiteBlocker();
 * await blocker.initialize();
 * 
 * const { pipeline, start, stop } = createGuardianPipeline(blocker, {
 *   enableContentScript: true,
 *   enableRealTimeSync: true,
 *   realTimeSyncUrl: 'wss://guardian.example.com/ws',
 *   deviceId: 'device-123',
 * });
 * 
 * await start();
 * // Pipeline is now running...
 * 
 * await stop();
 * ```
 */
export function createGuardianPipeline(
    blocker: WebsiteBlocker,
    config: GuardianPipelineConfig = {}
): GuardianPipelineInstance {
    const mergedConfig: Required<GuardianPipelineConfig> = {
        ...DEFAULT_CONFIG,
        ...config,
    };

    // Create pipeline
    const pipeline = new EventPipeline({
        name: mergedConfig.name,
        continueOnError: mergedConfig.continueOnError,
        onError: (error, event) => {
            console.error(`[${mergedConfig.name}] Pipeline error:`, error, event);
        },
    });

    // Create sources
    const tabSource = new TabActivitySource({
        minSessionDuration: 1000,
        trackSwitches: true,
        trackNavigations: true,
        trackCloses: true,
    });
    if (mergedConfig.enableTabSource) {
        pipeline.registerSource(tabSource);
    }

    let contentSource: ContentScriptSource | undefined;
    if (mergedConfig.enableContentScript) {
        contentSource = new ContentScriptSource({
            enabledMessageTypes: ['PAGE_METADATA', 'PAGE_INTERACTION', 'MEDIA_EVENT', 'DOM_ANALYSIS', 'PAGE_UNLOAD'],
            validateOrigin: true,
        });
        pipeline.registerSource(contentSource);
    }

    // Create processors
    const categoryProcessor = new CategoryEnrichmentProcessor({
        enableExternalApi: false,
        defaultCategory: 'other' as any, // WebsiteCategory.OTHER
    });
    pipeline.registerProcessor(categoryProcessor);

    const policyProcessor = new PolicyEvaluationProcessor(blocker, {
        enforceBlocking: true,
        blockPageUrl: 'src/pages/blocked.html',
        logBlockEvents: true,
    });
    pipeline.registerProcessor(policyProcessor);

    // Create sinks
    const storageSink = new LocalStorageSink({
        storageKeyPrefix: 'guardian',
        retentionDays: mergedConfig.retentionDays,
        maxRawEvents: 1000,
        aggregateByDomain: true,
        trackHourlyActivity: true,
    });
    pipeline.registerSink(storageSink);

    let syncSink: RealTimeSyncSink | undefined;
    if (mergedConfig.enableRealTimeSync && mergedConfig.realTimeSyncUrl && mergedConfig.deviceId) {
        syncSink = new RealTimeSyncSink({
            serverUrl: mergedConfig.realTimeSyncUrl,
            deviceId: mergedConfig.deviceId,
            reconnectInterval: 5000,
            maxReconnectAttempts: 10,
            heartbeatInterval: 30000,
            bufferWhenDisconnected: true,
            maxBufferSize: 100,
            syncEventTypes: ['tab_activity', 'page_view', 'block_event'],
        });
        pipeline.registerSink(syncSink);
    }

    // Create instance
    const instance: GuardianPipelineInstance = {
        pipeline,
        tabSource,
        contentSource,
        categoryProcessor,
        policyProcessor,
        storageSink,
        syncSink,

        start: async () => {
            console.log('[GuardianPipeline] Starting...');
            await pipeline.start();
            console.log('[GuardianPipeline] Started');
        },

        stop: async () => {
            console.log('[GuardianPipeline] Stopping...');
            await pipeline.stop();
            console.log('[GuardianPipeline] Stopped');
        },

        getStats: () => ({
            pipeline: pipeline.getStats(),
            storage: storageSink.getStats(),
            sync: syncSink?.getStats(),
        }),
    };

    return instance;
}

/**
 * Type guard to check if an object is a GuardianPipelineInstance
 */
export function isGuardianPipelineInstance(obj: unknown): obj is GuardianPipelineInstance {
    if (!obj || typeof obj !== 'object') return false;
    const instance = obj as Record<string, unknown>;
    return (
        'pipeline' in instance &&
        'tabSource' in instance &&
        'categoryProcessor' in instance &&
        'policyProcessor' in instance &&
        'storageSink' in instance &&
        typeof instance.start === 'function' &&
        typeof instance.stop === 'function'
    );
}
