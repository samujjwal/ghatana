/**
 * AI Agent API Client
 *
 * Thin API client that delegates AI agent requests to the Java backend.
 * This follows the Hybrid Backend Strategy - Node.js handles User API,
 * Java handles Core Domain (AI agents).
 *
 * @module ai/agents/api-client
 * @doc.type class
 * @doc.purpose Thin API client for Java AI backend
 * @doc.layer product
 * @doc.pattern API Client
 */

import type {
    AgentContext,
    AgentHealth,
    AgentMetadata,
    AgentName,
    AgentResult,
    IAIAgent,
} from './types';

/**
 * Configuration for the AI Agent API client
 */
export interface AIAgentClientConfig {
    /**
     * Base URL for the Java AI backend
     * @default 'http://localhost:8080'
     */
    baseUrl: string;

    /**
     * API timeout in milliseconds
     * @default 30000
     */
    timeout?: number;

    /**
     * API key for authentication
     */
    apiKey?: string;

    /**
     * Enable request/response logging
     * @default false
     */
    debug?: boolean;
}

/**
 * Response from the Java AI backend
 */
interface JavaAgentResponse<T> {
    success: boolean;
    data?: T;
    error?: {
        code: string;
        message: string;
        retryable: boolean;
    };
    metrics: {
        latencyMs: number;
        tokensUsed?: number;
        costUSD?: number;
        model?: string;
    };
    traceRecords: Array<{
        timestamp: number;
        event: string;
        duration?: number;
        metadata?: Record<string, unknown>;
    }>;
}

/**
 * Base API client for AI agents
 * Wraps HTTP calls to the Java backend
 */
export class AIAgentAPIClient<TInput, TOutput> implements IAIAgent<TInput, TOutput> {
    readonly name: AgentName;
    readonly version: string;
    readonly latencySLA: number;

    private config: Required<AIAgentClientConfig>;
    private endpoint: string;

    constructor(
        name: AgentName,
        version: string,
        latencySLA: number,
        config: AIAgentClientConfig
    ) {
        this.name = name;
        this.version = version;
        this.latencySLA = latencySLA;
        this.endpoint = `/api/v1/agents/${this.name.toLowerCase().replace('agent', '')}`;

        this.config = {
            timeout: 30000,
            apiKey: '',
            debug: false,
            ...config,
        };
    }

    /**
     * Execute the agent via Java backend API
     */
    async execute(
        input: TInput,
        context: AgentContext
    ): Promise<AgentResult<TOutput>> {
        const startTime = Date.now();

        try {
            const response = await this.callJavaBackend<TOutput>(
                'POST',
                `${this.endpoint}/execute`,
                { input, context }
            );

            return {
                success: response.success,
                data: response.data as TOutput,
                error: response.error ? {
                    code: response.error.code,
                    message: response.error.message,
                    retryable: response.error.retryable,
                } : undefined,
                metrics: {
                    latencyMs: response.metrics.latencyMs,
                    tokensUsed: response.metrics.tokensUsed,
                    costUSD: response.metrics.costUSD,
                    modelUsed: response.metrics.model,
                },
                trace: {
                    agentName: this.name,
                    requestId: context.requestId,
                    startTime,
                    endTime: Date.now(),
                    records: response.traceRecords.map(r => ({
                        timestamp: r.timestamp,
                        event: r.event,
                        duration: r.duration,
                        metadata: r.metadata,
                    })),
                },
            };
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : 'Unknown error';

            return {
                success: false,
                data: undefined as unknown as TOutput,
                error: {
                    code: 'API_ERROR',
                    message: errorMessage,
                    retryable: true,
                },
                metrics: {
                    latencyMs: Date.now() - startTime,
                },
                trace: {
                    agentName: this.name,
                    requestId: context.requestId,
                    startTime,
                    endTime: Date.now(),
                    records: [{
                        timestamp: Date.now(),
                        event: 'error',
                        metadata: { error: errorMessage },
                    }],
                },
            };
        }
    }

    /**
     * Health check via Java backend
     */
    async healthCheck(): Promise<AgentHealth> {
        try {
            const response = await this.callJavaBackend<AgentHealth>(
                'GET',
                `${this.endpoint}/health`
            );

            return response.data || {
                healthy: false,
                latency: 0,
                lastCheck: new Date(),
                dependencies: {},
                errorMessage: 'No health data returned',
            };
        } catch (error) {
            return {
                healthy: false,
                latency: 0,
                lastCheck: new Date(),
                dependencies: {},
                errorMessage: error instanceof Error ? error.message : 'Health check failed',
            };
        }
    }

    /**
     * Get agent metadata from Java backend
     */
    async getMetadata(): Promise<AgentMetadata> {
        const response = await this.callJavaBackend<AgentMetadata>(
            'GET',
            `${this.endpoint}/metadata`
        );

        return response.data || {
            name: this.name,
            version: this.version,
            description: 'AI Agent',
            capabilities: [],
            supportedModels: [],
            latencySLA: this.latencySLA,
        };
    }

    /**
     * Internal method to call Java backend
     */
    private async callJavaBackend<T>(
        method: 'GET' | 'POST' | 'PUT' | 'DELETE',
        path: string,
        body?: unknown
    ): Promise<JavaAgentResponse<T>> {
        const url = `${this.config.baseUrl}${path}`;

        const headers: Record<string, string> = {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
        };

        if (this.config.apiKey) {
            headers['Authorization'] = `Bearer ${this.config.apiKey}`;
        }

        if (this.config.debug) {
            console.log(`[AIAgentAPIClient] ${method} ${url}`, body);
        }

        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), this.config.timeout);

        try {
            const response = await fetch(url, {
                method,
                headers,
                body: body ? JSON.stringify(body) : undefined,
                signal: controller.signal,
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                const errorBody = await response.text();
                throw new Error(`HTTP ${response.status}: ${errorBody}`);
            }

            const data = await response.json() as JavaAgentResponse<T>;

            if (this.config.debug) {
                console.log(`[AIAgentAPIClient] Response:`, data);
            }

            return data;
        } catch (error) {
            clearTimeout(timeoutId);

            if (error instanceof Error && error.name === 'AbortError') {
                throw new Error(`Request timeout after ${this.config.timeout}ms`);
            }

            throw error;
        }
    }
}

/**
 * Factory for creating agent API clients
 */
export class AIAgentClientFactory {
    private config: AIAgentClientConfig;

    constructor(config: AIAgentClientConfig) {
        this.config = config;
    }

    /**
     * Create a Copilot agent client
     */
    createCopilotClient(): AIAgentAPIClient<CopilotInput, CopilotOutput> {
        return new AIAgentAPIClient(
            'CopilotAgent',
            '1.0.0',
            2000,
            this.config
        );
    }

    /**
     * Create a Query Parser agent client
     */
    createQueryParserClient(): AIAgentAPIClient<QueryParserInput, QueryParserOutput> {
        return new AIAgentAPIClient(
            'QueryParserAgent',
            '1.0.0',
            300,
            this.config
        );
    }

    /**
     * Create a Prediction agent client
     */
    createPredictionClient(): AIAgentAPIClient<PredictionInput, PredictionOutput> {
        return new AIAgentAPIClient(
            'PredictionAgent',
            '1.0.0',
            500,
            this.config
        );
    }

    /**
     * Create an Anomaly Detector agent client
     */
    createAnomalyDetectorClient(): AIAgentAPIClient<AnomalyInput, AnomalyOutput> {
        return new AIAgentAPIClient(
            'AnomalyDetectorAgent',
            '1.0.0',
            200,
            this.config
        );
    }
}

// Re-export input/output types from Java backend
// These match the Java record structures

/**
 * Copilot agent input - matches Java CopilotInput.java
 */
export interface CopilotInput {
    query: string;
    conversationHistory?: Array<{
        role: 'user' | 'assistant' | 'system';
        content: string;
        timestamp?: number;
    }>;
    itemContext?: {
        itemId: string;
        phase?: string;
        priority?: string;
    };
    maxTokens?: number;
    streaming?: boolean;
}

/**
 * Copilot agent output - matches Java CopilotOutput.java
 */
export interface CopilotOutput {
    message: string;
    actions: Array<{
        type: string;
        label: string;
        payload: Record<string, unknown>;
        confidence: number;
    }>;
    suggestions: string[];
    modelUsed: string;
    tokensUsed: number;
}

/**
 * Query Parser agent input - matches Java QueryParserInput.java
 */
export interface QueryParserInput {
    query: string;
    contextualItems?: string[];
    previousQueries?: string[];
    maxSuggestions?: number;
}

/**
 * Query Parser agent output - matches Java QueryParserOutput.java
 */
export interface QueryParserOutput {
    intent: 'SEARCH' | 'FILTER' | 'COMMAND' | 'QUESTION' | 'NAVIGATE';
    confidence: number;
    parameters: Record<string, unknown>;
    suggestions: string[];
    tokens: Array<{
        text: string;
        type: string;
        start: number;
        end: number;
    }>;
    parsedQuery: {
        type: string;
        filters: Record<string, unknown>;
        sortBy?: string;
        limit?: number;
    };
}

/**
 * Prediction agent input - matches Java PredictionInput.java
 */
export interface PredictionInput {
    itemId: string;
    currentPhase?: string;
    historicalData?: Array<{
        date: string;
        phase: string;
        metrics: Record<string, number>;
    }>;
    horizonDays?: number;
    requestedPredictions?: string[];
}

/**
 * Prediction agent output - matches Java PredictionOutput.java
 */
export interface PredictionOutput {
    predictions: Array<{
        targetDate: string;
        predictedPhase: string;
        confidence: number;
        probability: number;
    }>;
    riskScore: number;
    riskFactors: Array<{
        factor: string;
        score: number;
        description: string;
    }>;
    recommendations: Array<{
        priority: 'low' | 'medium' | 'high' | 'critical';
        action: string;
        reason: string;
        impact: string;
    }>;
    modelInfo: {
        ensemble: string[];
        confidence: number;
    };
}

/**
 * Anomaly Detector agent input - matches Java AnomalyInput.java
 */
export interface AnomalyInput {
    itemId?: string;
    workspaceId?: string;
    metricType: 'VELOCITY' | 'QUALITY_METRICS' | 'CYCLE_TIME' | 'THROUGHPUT' | 'ERROR_RATE' | 'CUSTOM';
    dataPoints: Array<{
        timestamp: number;
        value: number;
        labels?: Record<string, string>;
    }>;
    sensitivity?: number;
    lookbackWindow?: number;
}

/**
 * Anomaly Detector agent output - matches Java AnomalyOutput.java
 */
export interface AnomalyOutput {
    anomaliesDetected: Array<{
        timestamp: number;
        value: number;
        expectedValue: number;
        deviation: number;
        severity: 'INFO' | 'WARNING' | 'CRITICAL';
        type: string;
        description: string;
    }>;
    baseline: {
        mean: number;
        stdDev: number;
        min: number;
        max: number;
        trend: 'increasing' | 'decreasing' | 'stable';
    };
    rootCauseAnalysis?: {
        likelyCause: string;
        confidence: number;
        contributingFactors: Array<{
            factor: string;
            contribution: number;
        }>;
    };
    mitigationSteps: Array<{
        step: number;
        action: string;
        priority: 'low' | 'medium' | 'high';
        automated: boolean;
    }>;
}
