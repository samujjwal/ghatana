/**
 * @fileoverview Guardian Shared Types
 *
 * Central type definitions for domain/page usage, blocked events, content analysis,
 * and domain policies. These types are shared across Popup, Dashboard, and Settings.
 */

// ============================================================================
// Domain & Page Usage Types
// ============================================================================

/**
 * Access status for a domain or page
 */
export type AccessStatus = 'allowed' | 'blocked' | 'limited';

/**
 * Content risk level for domains/pages with chat/messaging/posts
 */
export type ContentRiskLevel = 'none' | 'some' | 'high';

/**
 * Domain-level usage summary (last 7 days)
 */
export interface DomainUsageSummary {
    domain: string;
    timeLast7DaysMinutes: number;
    visitsLast7Days: number;
    blockedAttempts: number;
    contentRisk: ContentRiskLevel;
    status: AccessStatus;
    lastVisited?: number;
}

/**
 * Page-level usage summary (for a selected domain)
 */
export interface PageUsageSummary {
    domain: string;
    url: string;
    title?: string;
    timeLast7DaysMinutes: number;
    lastVisited: number;
    blockedAttempts: number;
    hasChatsOrPosts: boolean;
    contentRisk: ContentRiskLevel;
}

/**
 * Today's usage for a specific domain (used in Popup)
 */
export interface DomainUsageToday {
    domain: string;
    timeMinutes: number;
    visits: number;
}

// ============================================================================
// Blocked Events Types
// ============================================================================

/**
 * A single blocked event record
 */
export interface BlockedEvent {
    id: string;
    timestamp: number;
    domain: string;
    url: string;
    title?: string;
    reason: string;
    policyId?: string;
    category?: string;
}

/**
 * Last blocked event for a specific URL (used in Popup)
 */
export interface LastBlockedEvent {
    timestamp: number;
    reason: string;
    policyId?: string;
}

// ============================================================================
// Content Analysis Types
// ============================================================================

/**
 * Aggregated content analysis summary per domain
 */
export interface ContentSummaryByDomain {
    domain: string;
    messagesAnalyzed: number;
    flaggedByType: Record<string, number>;
    risk: ContentRiskLevel;
    lastFlaggedTime?: number;
}

// ============================================================================
// Domain Policy Types
// ============================================================================

/**
 * Domain policy status
 */
export type DomainPolicyStatus = 'blocked' | 'allowed' | 'default';

/**
 * A single domain policy entry (for Settings block/allow list)
 */
export interface DomainPolicy {
    domain: string;
    status: DomainPolicyStatus;
    addedAt?: number;
}

// ============================================================================
// Policy Evaluation Types
// ============================================================================

/**
 * Result of evaluating a URL against policies
 */
export interface PolicyEvaluationResult {
    status: AccessStatus;
    reason?: string;
    policyId?: string;
    category?: string;
}

// ============================================================================
// Settings Types
// ============================================================================

/**
 * Minimal Guardian settings (for the simplified UX)
 */
export interface MinimalGuardianSettings {
    monitoringEnabled: boolean;
    dataRetentionDays: 7 | 30 | 90;
    alertsEnabled: boolean;
    domainPolicies: DomainPolicy[];
}

// ============================================================================
// Analytics Response Types (for GET_ANALYTICS extensions)
// ============================================================================

/**
 * Extended analytics response with all data needed for Dashboard
 */
export interface ExtendedAnalyticsSummary {
    domains: DomainUsageSummary[];
    pages: PageUsageSummary[];
    blockedEvents: BlockedEvent[];
    contentSummary: ContentSummaryByDomain[];
    // Legacy fields for backward compatibility
    totalUsageRecords: number;
    totalEvents: number;
    webUsage: {
        last24h: number;
        last7d: number;
        allTime: number;
    };
    timeSpent: {
        last24h: number;
        last7d: number;
        allTime: number;
    };
    state: {
        metricsCollecting: boolean;
        eventsCapturing: boolean;
    };
    lastUpdated: number;
}

// ============================================================================
// Message Payload Types
// ============================================================================

/**
 * Payload for UPDATE_DOMAIN_POLICY message
 */
export interface UpdateDomainPolicyPayload {
    domain: string;
    action: 'block' | 'allow' | 'default' | 'temp-allow';
    durationMinutes?: number; // For temp-allow
}

/**
 * Payload for GET_DOMAIN_USAGE_TODAY message
 */
export interface GetDomainUsageTodayPayload {
    domain: string;
}

/**
 * Payload for GET_BLOCKED_EVENTS message
 */
export interface GetBlockedEventsPayload {
    range?: '24h' | '7d' | 'all';
    domain?: string;
    limit?: number;
}

/**
 * Payload for GET_LAST_BLOCKED_EVENT message
 */
export interface GetLastBlockedEventPayload {
    url: string;
}

// ============================================================================
// Utility Types
// ============================================================================

/**
 * Generic message response wrapper
 */
export interface MessageResponse<T = unknown> {
    success: boolean;
    data?: T;
    error?: string;
}

/**
 * Helper to format duration in minutes to human-readable string
 */
export function formatDuration(minutes: number): string {
    if (minutes < 1) return '<1m';
    if (minutes < 60) return `${Math.round(minutes)}m`;
    const hours = Math.floor(minutes / 60);
    const mins = Math.round(minutes % 60);
    return mins > 0 ? `${hours}h ${mins}m` : `${hours}h`;
}

/**
 * Helper to format timestamp to relative time string
 */
export function formatRelativeTime(timestamp: number): string {
    const now = Date.now();
    const diff = now - timestamp;

    if (diff < 60000) return 'just now';
    if (diff < 3600000) return `${Math.round(diff / 60000)}m ago`;
    if (diff < 86400000) return `${Math.round(diff / 3600000)}h ago`;
    return `${Math.round(diff / 86400000)}d ago`;
}

/**
 * Helper to extract domain from URL
 */
export function extractDomain(url: string): string {
    try {
        const urlObj = new URL(url);
        return urlObj.hostname.replace(/^www\./, '');
    } catch {
        return 'unknown';
    }
}
