/**
 * AI State Types
 *
 * Type definitions for AI-related features including predictions, recommendations,
 * anomaly detection, and intelligent suggestions.
 *
 * @module types/ai
 * @doc.type module
 * @doc.purpose AI feature type definitions
 * @doc.layer product
 * @doc.pattern Value Object
 */

// ============================================================================
// Insight Types
// ============================================================================

/**
 * Insight type classification
 */
export type InsightType =
    | 'prediction'
    | 'anomaly'
    | 'recommendation'
    | 'trend'
    | 'risk'
    | 'opportunity';

/**
 * Insight priority levels
 */
export type InsightPriority = 'critical' | 'high' | 'medium' | 'low' | 'info';

/**
 * AI-generated insight
 */
export interface AIInsight {
    id: string;
    type: InsightType;
    priority: InsightPriority;
    title: string;
    description: string;
    confidence: number;
    createdAt: Date;
    expiresAt?: Date;
    source: string;
    acknowledged?: boolean;
    metadata: Record<string, unknown>;
    actions?: InsightAction[];
}

/**
 * Actionable item from an insight
 */
export interface InsightAction {
    label: string;
    action: string;
    params?: Record<string, unknown>;
}

// ============================================================================
// Prediction Types
// ============================================================================

/**
 * Prediction type classification
 */
export type PredictionType =
    | 'COMPLETION_DATE'
    | 'RISK_SCORE'
    | 'EFFORT_ESTIMATE'
    | 'PRIORITY_SCORE'
    | 'SUCCESS_PROBABILITY'
    | 'BLOCKERS';

/**
 * AI prediction result
 */
export interface Prediction {
    id: string;
    type: PredictionType;
    itemId: string;
    predictedValue: string | number;
    confidence: number;
    range?: {
        low: string | number;
        high: string | number;
    };
    contributingFactors: ContributingFactor[];
    createdAt: Date;
    expiresAt: Date;
    modelVersion: string;
}

/**
 * Factor contributing to a prediction
 */
export interface ContributingFactor {
    name: string;
    impact: number;
    description: string;
}

// ============================================================================
// Copilot Types
// ============================================================================

/**
 * Message role in a conversation
 */
export type MessageRole = 'user' | 'assistant' | 'system';

/**
 * Code snippet in a message
 */
export interface CodeSnippet {
    language: string;
    code: string;
    fileName?: string;
}

/**
 * Single message in a copilot conversation
 */
export interface CopilotMessage {
    id: string;
    role: MessageRole;
    content: string;
    timestamp: Date;
    suggestions?: string[];
    codeSnippets?: CodeSnippet[];
    actionableItems?: string[];
}

/**
 * Copilot conversation session
 */
export interface CopilotSession {
    sessionId: string;
    messages: CopilotMessage[];
    isLoading: boolean;
    error: string | null;
    lastUpdated: Date;
}

// ============================================================================
// Anomaly Types
// ============================================================================

/**
 * Anomaly severity levels
 */
export type AnomalySeverity = 'critical' | 'high' | 'medium' | 'low';

/**
 * Anomaly type classification
 */
export type AnomalyType =
    | 'spike'
    | 'drop'
    | 'trend_change'
    | 'outlier'
    | 'pattern_break'
    | 'threshold_breach';

/**
 * Anomaly alert
 */
export interface AnomalyAlert {
    id: string;
    metricName: string;
    type: AnomalyType;
    severity: AnomalySeverity;
    title: string;
    description: string;
    score: number;
    currentValue: number;
    expectedValue: number;
    deviation: number;
    detectedAt: Date;
    acknowledgedAt?: Date;
    resolvedAt?: Date;
    metadata: Record<string, unknown>;
}

// ============================================================================
// Recommendation Types
// ============================================================================

/**
 * Recommendation type classification
 */
export type RecommendationType =
    | 'ASSIGNEE'
    | 'TAG'
    | 'PRIORITY'
    | 'PHASE'
    | 'SIMILAR_ITEMS'
    | 'NEXT_ACTION'
    | 'WORKFLOW'
    | 'TIME_ESTIMATE'
    | 'LABEL'
    | 'DEPENDENCY';

/**
 * Recommendation source
 */
export type RecommendationSource = 'collaborative' | 'content' | 'hybrid' | 'rule';

/**
 * AI recommendation
 */
export interface Recommendation {
    id: string;
    type: RecommendationType;
    value: string | Record<string, unknown>;
    displayValue: string;
    confidence: number;
    reason: string;
    source: RecommendationSource;
    dismissed?: boolean;
    acceptedAt?: Date;
    metadata?: Record<string, unknown>;
}

// ============================================================================
// Search Types
// ============================================================================

/**
 * Search modes
 */
export type SearchMode = 'SEMANTIC' | 'TEXT' | 'HYBRID';

/**
 * Search result item
 */
export interface SearchResult {
    id: string;
    type: 'item' | 'document' | 'comment' | 'workflow' | 'knowledge';
    title: string;
    snippet: string;
    highlightedSnippet?: string;
    score: number;
    metadata: Record<string, unknown>;
    matchedTerms?: string[];
}

/**
 * Search facet
 */
export interface SearchFacet {
    field: string;
    values: FacetValue[];
}

/**
 * Facet value
 */
export interface FacetValue {
    value: string;
    count: number;
    selected?: boolean;
}

// ============================================================================
// Preferences Types
// ============================================================================

/**
 * AI feature preferences
 */
export interface AIPreferences {
    enableInsights: boolean;
    enablePredictions: boolean;
    enableAnomalyDetection: boolean;
    enableRecommendations: boolean;
    confidenceThreshold: number;
    maxSuggestions: number;
    autoApplySuggestions: boolean;
}

/**
 * Alert notification preferences
 */
export interface AlertNotificationPrefs {
    browser: boolean;
    sound: boolean;
    email: boolean;
    slack: boolean;
}

// ============================================================================
// Dashboard Types
// ============================================================================

/**
 * AI dashboard statistics
 */
export interface AIDashboardStats {
    insightsCount: number;
    unreadInsights: number;
    predictionsCount: number;
    alertsCount: number;
    unacknowledgedAlerts: number;
    recommendationsCount: number;
}

/**
 * AI health status
 */
export type AIHealthStatus = 'healthy' | 'warning' | 'critical';
