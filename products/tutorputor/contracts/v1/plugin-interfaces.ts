/**
 * Plugin Interface Contracts
 *
 * Defines the core interfaces that all plugins must implement.
 * These contracts enable the "Plugin Kernel" architecture.
 *
 * @doc.type module
 * @doc.purpose Plugin interface definitions for extensibility
 * @doc.layer contracts
 * @doc.pattern Interface
 */

import type { Evidence, LearningUnit } from './learning-unit';

// ============================================================================
// Core Plugin Types
// ============================================================================

export type PluginType =
    | 'ingestor'
    | 'evidence_processor'
    | 'authoring_tool'
    | 'asset_provider'
    | 'notifier';

export type PluginStatus = 'active' | 'inactive' | 'error';

// ============================================================================
// Plugin Metadata
// ============================================================================

/**
 * Base metadata required for all plugins
 */
export interface PluginMetadata {
    /** Unique plugin identifier (e.g., "analytics-bkt") */
    id: string;
    /** Human-readable name */
    name: string;
    /** Semantic version */
    version: string;
    /** Plugin category */
    type: PluginType;
    /** Execution priority (higher = runs first) */
    priority: number;
    /** Plugin description */
    description?: string;
    /** Plugin author/maintainer */
    author?: string;
    /** Tags for filtering/categorization */
    tags?: readonly string[];
    /** Whether this plugin is enabled (default: true) */
    enabled?: boolean;
}

// ============================================================================
// Processing Context (Blackboard Pattern)
// ============================================================================

/**
 * Shared context passed through the processing pipeline.
 * Plugins read and write to this "blackboard" to communicate.
 */
export interface ProcessingContext {
    /** Unique request/event ID */
    requestId: string;
    /** Learner ID (if applicable) */
    learnerId?: string;
    /** Learning Unit ID (if applicable) */
    learningUnitId?: string;
    /** Claim ID being processed (if applicable) */
    claimId?: string;
    /** Timestamp of the event */
    timestamp: Date;
    /** Key-value store for plugin data exchange */
    data: Record<string, unknown>;
    /** Accumulated errors from plugins */
    errors: PluginError[];
    /** Accumulated warnings from plugins */
    warnings: PluginWarning[];
    /** Flag to halt pipeline execution */
    halt: boolean;
    /** Reason for halting (if halt=true) */
    haltReason?: string;
}

export interface PluginError {
    pluginId: string;
    code: string;
    message: string;
    details?: Record<string, unknown>;
}

export interface PluginWarning {
    pluginId: string;
    code: string;
    message: string;
    details?: Record<string, unknown>;
}

// ============================================================================
// Processing Result
// ============================================================================

export type ProcessingResultStatus = 'success' | 'skipped' | 'error';

export interface ProcessingResult {
    /** Plugin that produced this result */
    pluginId: string;
    /** Outcome status */
    status: ProcessingResultStatus;
    /** Data to merge into context */
    data?: Record<string, unknown>;
    /** Error details (if status=error) */
    error?: PluginError;
    /** Processing duration in milliseconds */
    durationMs: number;
}

// ============================================================================
// Evidence Processor Plugin Interface
// ============================================================================

/**
 * Evidence record from telemetry/LRS
 */
export interface EvidenceEvent {
    id: string;
    learnerId: string;
    learningUnitId: string;
    claimId: string;
    evidenceId: string;
    type: string;
    timestamp: Date;
    payload: Record<string, unknown>;
}

/**
 * Interface for plugins that process learner evidence.
 * Examples: BKT Model, IRT Engine, Cheat Detector, LLM Grader
 */
export interface EvidenceProcessor {
    readonly metadata: PluginMetadata;

    /**
     * Check if this processor can handle the given evidence type.
     */
    supports(evidence: EvidenceEvent): boolean;

    /**
     * Process the evidence and update the context.
     * Plugins can read previous results from context.data.
     */
    process(
        context: ProcessingContext,
        evidence: EvidenceEvent
    ): Promise<ProcessingResult>;

    /**
     * Optional: Initialize the plugin (load models, connect to services)
     */
    initialize?(): Promise<void>;

    /**
     * Optional: Cleanup resources
     */
    shutdown?(): Promise<void>;
}

// ============================================================================
// Ingestor Plugin Interface
// ============================================================================

/**
 * Raw event from external source (before normalization)
 */
export interface RawEvent {
    source: string;
    format: 'xapi' | 'caliper' | 'custom';
    payload: unknown;
    receivedAt: Date;
}

/**
 * Interface for plugins that ingest external data.
 * Examples: xAPI Ingestor, Caliper Ingestor, Log Parser
 */
export interface Ingestor {
    readonly metadata: PluginMetadata;

    /**
     * Check if this ingestor can handle the given event format.
     */
    supports(event: RawEvent): boolean;

    /**
     * Transform raw event into normalized EvidenceEvent.
     */
    ingest(event: RawEvent): Promise<EvidenceEvent | null>;
}

// ============================================================================
// Asset Provider Plugin Interface
// ============================================================================

export interface AssetMetadata {
    id: string;
    filename: string;
    mimeType: string;
    size: number;
    duration?: number; // For video/audio
    width?: number;
    height?: number;
    uploadedAt: Date;
    uploadedBy: string;
}

export interface UploadRequest {
    filename: string;
    mimeType: string;
    size: number;
}

export interface UploadResponse {
    assetId: string;
    uploadUrl: string;
    expiresAt: Date;
}

/**
 * Interface for plugins that store/serve assets.
 * Examples: S3 Provider, Vimeo Provider, IPFS Provider
 */
export interface AssetProvider {
    readonly metadata: PluginMetadata;

    /**
     * Check if this provider can handle the given mime type.
     */
    supports(mimeType: string): boolean;

    /**
     * Generate a presigned upload URL.
     */
    getUploadUrl(request: UploadRequest): Promise<UploadResponse>;

    /**
     * Get a public/signed URL for serving the asset.
     */
    getServeUrl(assetId: string): Promise<string>;

    /**
     * Get asset metadata.
     */
    getMetadata(assetId: string): Promise<AssetMetadata | null>;

    /**
     * Delete an asset.
     */
    delete(assetId: string): Promise<void>;
}

// ============================================================================
// Authoring Tool Plugin Interface
// ============================================================================

export interface ValidationIssue {
    field: string;
    severity: 'error' | 'warning' | 'info';
    message: string;
    suggestion?: string;
}

export interface ValidationResult {
    valid: boolean;
    score: number; // 0-100
    issues: ValidationIssue[];
}

/**
 * Interface for plugins that assist in content authoring.
 * Examples: PayloadCMS Adapter, VS Code Extension, Notion Importer
 */
export interface AuthoringTool {
    readonly metadata: PluginMetadata;

    /**
     * Validate a Learning Unit before publishing.
     */
    validate(unit: LearningUnit): Promise<ValidationResult>;

    /**
     * Import content from external source.
     */
    import?(source: string, options?: Record<string, unknown>): Promise<LearningUnit>;

    /**
     * Export content to external format.
     */
    export?(unit: LearningUnit, format: string): Promise<unknown>;
}

// ============================================================================
// Notifier Plugin Interface
// ============================================================================

export type NotificationChannel = 'email' | 'sms' | 'push' | 'webhook' | 'slack';

export interface Notification {
    channel: NotificationChannel;
    recipient: string;
    subject: string;
    body: string;
    metadata?: Record<string, unknown>;
}

/**
 * Interface for plugins that send notifications.
 * Examples: Email Notifier, Slack Bot, Webhook Sender
 */
export interface Notifier {
    readonly metadata: PluginMetadata;

    /**
     * Check if this notifier supports the given channel.
     */
    supports(channel: NotificationChannel): boolean;

    /**
     * Send a notification.
     */
    send(notification: Notification): Promise<void>;
}

// ============================================================================
// Plugin Registry Types
// ============================================================================

export type Plugin =
    | EvidenceProcessor
    | Ingestor
    | AssetProvider
    | AuthoringTool
    | Notifier;

export interface PluginRegistration {
    plugin: Plugin;
    status: PluginStatus;
    registeredAt: Date;
    lastError?: PluginError;
}
