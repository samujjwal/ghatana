/**
 * Base AI Agent Implementation
 *
 * Abstract base class that all AI agents extend.
 * Provides common functionality for telemetry, error handling, and health checks.
 *
 * @module ai/agents/BaseAgent
 * @doc.type class
 * @doc.purpose Base AI agent implementation
 * @doc.layer product
 * @doc.pattern Template Method
 */

import { v4 as uuidv4 } from 'uuid';
import type {
    AgentContext,
    AgentHealth,
    AgentMetadata,
    AgentMetrics,
    AgentName,
    AgentResult,
    AgentTrace,
    IAIAgent,
} from './types';
import { AgentError } from './types';

/**
 * Base agent configuration
 */
export interface BaseAgentConfig {
    name: AgentName;
    version: string;
    description: string;
    capabilities: string[];
    supportedModels: string[];
    latencySLA: number;
    defaultTimeout?: number;
    maxRetries?: number;
    enableTelemetry?: boolean;
}

/**
 * Abstract base agent class with common functionality
 */
export abstract class BaseAgent<TInput, TOutput>
    implements IAIAgent<TInput, TOutput> {
    readonly name: AgentName;
    readonly version: string;
    readonly latencySLA: number;

    protected config: BaseAgentConfig;
    protected lastHealthCheck: AgentHealth | null = null;

    constructor(config: BaseAgentConfig) {
        this.config = {
            defaultTimeout: 30000,
            maxRetries: 3,
            enableTelemetry: true,
            ...config,
        };
        this.name = config.name;
        this.version = config.version;
        this.latencySLA = config.latencySLA;
    }

    /**
     * Execute the agent with telemetry and error handling
     */
    async execute(
        input: TInput,
        context: AgentContext
    ): Promise<AgentResult<TOutput>> {
        const startTime = Date.now();
        const requestId = context.requestId || uuidv4();

        try {
            // Validate input
            this.validateInput(input);

            // Check if agent is excluded for this user
            if (context.preferences?.excludedAgents?.includes(this.name)) {
                throw new AgentError(
                    `Agent ${this.name} is excluded for this user`,
                    'AGENT_EXCLUDED',
                    this.name,
                    false
                );
            }

            // Apply timeout
            const timeout = context.timeout || this.config.defaultTimeout;
            const result = await this.withTimeout(
                this.processRequest(input, context),
                timeout!
            );

            const latencyMs = Date.now() - startTime;

            // Log SLA violation
            if (latencyMs > this.latencySLA) {
                console.warn(
                    `[${this.name}] SLA violation: ${latencyMs}ms > ${this.latencySLA}ms`
                );
            }

            return {
                success: true,
                data: result.data,
                metrics: {
                    latencyMs,
                    tokensUsed: result.tokensUsed,
                    modelVersion: result.modelVersion || this.version,
                    confidence: result.confidence,
                },
                trace: this.buildTrace(requestId, context, { success: true }),
            };
        } catch (error) {
            const latencyMs = Date.now() - startTime;
            const agentError = this.wrapError(error);

            return {
                success: false,
                error: agentError,
                metrics: {
                    latencyMs,
                    modelVersion: this.version,
                },
                trace: this.buildTrace(requestId, context, {
                    success: false,
                    error: agentError.message,
                }),
            };
        }
    }

    /**
     * Process the request - must be implemented by subclasses
     */
    protected abstract processRequest(
        input: TInput,
        context: AgentContext
    ): Promise<ProcessResult<TOutput>>;

    /**
     * Validate input - can be overridden by subclasses
     */
    protected validateInput(_input: TInput): void {
        // Default: no validation
    }

    /**
     * Health check
     */
    async healthCheck(): Promise<AgentHealth> {
        const startTime = Date.now();

        try {
            const dependencies = await this.checkDependencies();
            const healthy = Object.values(dependencies).every(
                (status) => status !== 'unhealthy'
            );

            this.lastHealthCheck = {
                healthy,
                latency: Date.now() - startTime,
                lastCheck: new Date(),
                dependencies,
            };

            return this.lastHealthCheck;
        } catch (error) {
            this.lastHealthCheck = {
                healthy: false,
                latency: Date.now() - startTime,
                lastCheck: new Date(),
                dependencies: {},
                errorMessage: error instanceof Error ? error.message : String(error),
            };

            return this.lastHealthCheck;
        }
    }

    /**
     * Check agent dependencies - can be overridden by subclasses
     */
    protected async checkDependencies(): Promise<
        Record<string, 'healthy' | 'degraded' | 'unhealthy'>
    > {
        return { base: 'healthy' };
    }

    /**
     * Get agent metadata
     */
    getMetadata(): AgentMetadata {
        return {
            name: this.name,
            version: this.version,
            description: this.config.description,
            capabilities: this.config.capabilities,
            supportedModels: this.config.supportedModels,
            latencySLA: this.latencySLA,
        };
    }

    /**
     * Wrap timeout around a promise
     */
    protected async withTimeout<T>(
        promise: Promise<T>,
        timeoutMs: number
    ): Promise<T> {
        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => {
                reject(
                    new AgentError(
                        `Agent ${this.name} timed out after ${timeoutMs}ms`,
                        'TIMEOUT',
                        this.name,
                        true
                    )
                );
            }, timeoutMs);

            promise
                .then((result) => {
                    clearTimeout(timer);
                    resolve(result);
                })
                .catch((error) => {
                    clearTimeout(timer);
                    reject(error);
                });
        });
    }

    /**
     * Wrap any error into AgentError
     */
    protected wrapError(error: unknown): AgentError {
        if (error instanceof AgentError) {
            return error;
        }

        if (error instanceof Error) {
            return new AgentError(
                error.message,
                'INTERNAL_ERROR',
                this.name,
                false,
                error
            );
        }

        return new AgentError(String(error), 'UNKNOWN_ERROR', this.name, false);
    }

    /**
     * Build trace for observability
     */
    protected buildTrace(
        requestId: string,
        context: AgentContext,
        additional: Record<string, unknown>
    ): AgentTrace {
        return {
            agentName: this.name,
            requestId,
            timestamp: new Date(),
            metadata: {
                userId: context.userId,
                workspaceId: context.workspaceId,
                ...additional,
            },
        };
    }

    /**
     * Retry with exponential backoff
     */
    protected async retryWithBackoff<T>(
        fn: () => Promise<T>,
        maxRetries: number = this.config.maxRetries!
    ): Promise<T> {
        let lastError: Error | undefined;

        for (let attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return await fn();
            } catch (error) {
                lastError = error instanceof Error ? error : new Error(String(error));

                if (attempt < maxRetries) {
                    const delay = Math.pow(2, attempt) * 100; // 100ms, 200ms, 400ms, etc.
                    await new Promise((resolve) => setTimeout(resolve, delay));
                }
            }
        }

        throw lastError;
    }
}

/**
 * Result from agent processing
 */
export interface ProcessResult<T> {
    data: T;
    tokensUsed?: number;
    modelVersion?: string;
    confidence?: number;
}
