export interface AIAgentClientConfig {
    baseUrl: string;
    timeout: number;
    apiKey?: string;
    debug?: boolean;
}

export interface CopilotInput {
    sessionId?: string;
    message: string;
    context?: Record<string, unknown>;
}

export interface QueryParserInput {
    query: string;
    schema?: unknown;
}

export interface PredictionInput {
    data: unknown[];
    target?: string;
}

export interface AnomalyInput {
    data: unknown[];
    sensitivity?: number;
}

export class AIAgentClientFactory {
    constructor(config: AIAgentClientConfig) { }

    createCopilotClient() {
        return {
            processMessage: async (input: CopilotInput) => ({
                message: 'Stub response',
                sessionId: input.sessionId || 'stub-session',
                confidence: 1.0,
                data: {},
                success: true,
                metrics: { tokensUsed: 0 },
                error: null as { message: string } | null
            }),
            healthCheck: async () => ({ status: 'ok' }),
            execute: async (input: CopilotInput, context: unknown) => ({
                message: 'Stub response',
                sessionId: input.sessionId || 'stub-session',
                confidence: 1.0,
                data: {},
                success: true,
                metrics: { tokensUsed: 0 },
                error: null as { message: string } | null
            }),
        };
    }

    createQueryParserClient() {
        return {
            parseQuery: async (input: QueryParserInput) => ({
                intent: 'stub-intent',
                entities: {},
                data: {},
                success: true,
                metrics: { tokensUsed: 0 },
                error: null as { message: string } | null
            }),
            healthCheck: async () => ({ status: 'ok' }),
            execute: async (input: QueryParserInput, context: unknown) => ({
                intent: 'stub-intent',
                entities: {},
                data: {},
                success: true,
                metrics: { tokensUsed: 0 },
                error: null as { message: string } | null
            }),
        };
    }

    createPredictionClient() {
        return {
            predict: async (input: PredictionInput) => ({
                predictions: [],
                accuracy: 0.9,
                data: {},
                success: true,
                metrics: { tokensUsed: 0 },
                error: null as { message: string } | null
            }),
            healthCheck: async () => ({ status: 'ok' }),
            execute: async (input: PredictionInput, context: unknown) => ({
                predictions: [],
                accuracy: 0.9,
                data: {},
                success: true,
                metrics: { tokensUsed: 0 },
                error: null as { message: string } | null
            }),
        };
    }

    createAnomalyDetectorClient() {
        return {
            detectAnomalies: async (input: AnomalyInput) => ({
                anomalies: [],
                score: 0.0,
                data: {},
                success: true,
                metrics: { tokensUsed: 0 },
                error: null as { message: string } | null
            }),
            healthCheck: async () => ({ status: 'ok' }),
            execute: async (input: AnomalyInput, context: unknown) => ({
                anomalies: [],
                score: 0.0,
                data: {},
                success: true,
                metrics: { tokensUsed: 0 },
                error: null as { message: string } | null
            }),
        };
    }
}
