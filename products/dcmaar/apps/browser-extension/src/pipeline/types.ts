/**
 * @fileoverview Guardian Pipeline Types
 * 
 * Shared type definitions for the Guardian event pipeline.
 * These types flow through Source → Processor → Sink.
 * 
 * @module pipeline/types
 */

import type { WebsiteCategory } from '../blocker/WebsiteBlocker';

/**
 * Guardian event types for the pipeline
 */
export type GuardianEventType =
    | 'tab_activity'
    | 'page_view'
    | 'page_interaction'
    | 'media_event'
    | 'dom_analysis'
    | 'block_event'
    | 'policy_update';

/**
 * Base event interface for all Guardian events
 */
export interface GuardianEvent {
    /** Unique event ID */
    id: string;
    /** Event type discriminator */
    type: GuardianEventType;
    /** Unix timestamp in milliseconds */
    timestamp: number;
    /** Source that generated this event */
    source: string;
    /** Optional metadata added by processors */
    metadata?: Record<string, unknown>;
}

/**
 * Tab activity event from TabActivitySource
 */
export interface TabActivityEvent extends GuardianEvent {
    type: 'tab_activity';
    /** Tab ID from Chrome API */
    tabId: number;
    /** Full URL of the page */
    url: string;
    /** Extracted domain */
    domain: string;
    /** Page title */
    title: string;
    /** Time spent on this tab in milliseconds */
    duration?: number;
    /** Whether this is a tab switch or navigation */
    action: 'navigate' | 'activate' | 'close';
}

/**
 * Page metadata extracted by content script
 */
export interface PageMetadata {
    /** Page title */
    title: string;
    /** Meta description */
    description?: string;
    /** OpenGraph title */
    ogTitle?: string;
    /** OpenGraph description */
    ogDescription?: string;
    /** OpenGraph image */
    ogImage?: string;
    /** Canonical URL */
    canonicalUrl?: string;
    /** Keywords from meta tags */
    keywords?: string[];
    /** Language of the page */
    language?: string;
}

/**
 * User interaction data from content script
 */
export interface InteractionData {
    /** Scroll depth percentage (0-100) */
    scrollDepth: number;
    /** Number of clicks on the page */
    clickCount: number;
    /** Number of form interactions */
    formInteractions: number;
    /** Time spent actively interacting (not idle) */
    activeTime: number;
    /** Whether user has scrolled */
    hasScrolled: boolean;
}

/**
 * Media tracking data (YouTube, Netflix, etc.)
 */
export interface MediaTrackingData {
    /** Type of media */
    mediaType: 'video' | 'audio';
    /** Platform (youtube, netflix, spotify, etc.) */
    platform: string;
    /** Media title if available */
    title?: string;
    /** Duration watched/listened in seconds */
    duration: number;
    /** Current playback state */
    state: 'playing' | 'paused' | 'ended';
    /** Video ID if available */
    videoId?: string;
}

/**
 * DOM analysis data for safety scanning
 */
export interface DOMAnalysisData {
    /** Detected keywords (client-side only) */
    detectedKeywords: string[];
    /** Content safety score (0-100, higher is safer) */
    safetyScore: number;
    /** Whether page contains forms */
    hasForms: boolean;
    /** Whether page contains login form */
    hasLoginForm: boolean;
    /** External link count */
    externalLinkCount: number;
}

/**
 * Content script event with rich page data
 */
export interface ContentScriptEvent extends GuardianEvent {
    type: 'page_view' | 'page_interaction' | 'media_event' | 'dom_analysis';
    /** Tab ID */
    tabId: number;
    /** Page URL */
    url: string;
    /** Extracted domain */
    domain: string;
    /** Page metadata */
    pageMetadata?: PageMetadata;
    /** Interaction data */
    interactions?: InteractionData;
    /** Media tracking data */
    media?: MediaTrackingData;
    /** DOM analysis results */
    domAnalysis?: DOMAnalysisData;
}

/**
 * Enriched event with category information
 */
export interface EnrichedEvent extends GuardianEvent {
    /** Website category */
    category?: WebsiteCategory;
    /** Category confidence score (0-1) */
    categoryConfidence?: number;
    /** Whether this domain is in a known list */
    isKnownDomain?: boolean;
}

/**
 * Policy evaluation result attached to events
 */
export interface PolicyEvaluatedEvent extends EnrichedEvent {
    /** Policy decision */
    policyDecision: 'allow' | 'block' | 'warn';
    /** Policy ID that matched */
    policyId?: string;
    /** Reason for the decision */
    policyReason?: string;
    /** Whether action was taken (e.g., redirect to block page) */
    actionTaken?: boolean;
}

/**
 * Configuration for the Guardian pipeline
 */
export interface GuardianPipelineConfig {
    /** Pipeline name for logging */
    name?: string;
    /** Whether to continue on processor errors */
    continueOnError?: boolean;
    /** Enable tab activity source */
    enableTabSource?: boolean;
    /** Enable content script source */
    enableContentScript?: boolean;
    /** Enable real-time sync sink */
    enableRealTimeSync?: boolean;
    /** Real-time sync WebSocket URL */
    realTimeSyncUrl?: string;
    /** Device ID for sync */
    deviceId?: string;
    /** Data retention in days */
    retentionDays?: number;
}

/**
 * Storage schema for daily usage
 */
export interface DailyUsage {
    /** Date in YYYY-MM-DD format */
    date: string;
    /** Total time in milliseconds */
    totalTime: number;
    /** Time by category */
    categories: Record<string, number>;
    /** Usage by domain */
    domains: Record<string, DomainUsage>;
    /** Hourly activity (0-23 array) */
    hourlyActivity: number[];
}

/**
 * Domain-level usage statistics
 */
export interface DomainUsage {
    /** Time spent in milliseconds */
    time: number;
    /** Number of visits */
    visits: number;
    /** Website category */
    category: string;
    /** Favicon URL */
    icon?: string;
    /** Last visit timestamp */
    lastVisit?: number;
}
