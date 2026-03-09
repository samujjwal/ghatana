/**
 * Pipeline Runner for the Learning Evidence Platform.
 *
 * Orchestrates the execution of plugins through a priority-based pipeline.
 * Implements the Plugin Kernel architecture where all processing is done
 * by plugins coordinated through a shared ProcessingContext.
 *
 * @doc.type class
 * @doc.purpose Execute plugin pipelines with priority ordering
 * @doc.layer core
 * @doc.pattern Pipeline, Chain of Responsibility
 */

import type {
    EvidenceProcessor,
    ProcessingResult,
    ProcessingContext,
    EvidenceEvent,
} from '@ghatana/tutorputor-contracts/v1/plugin-interfaces';
import type { PluginRegistry, PluginFilter } from '../registry/PluginRegistry';

/**
 * Configuration for pipeline execution.
 */
export interface PipelineConfig {
    /** Stop on first error (default: false) */
    readonly stopOnError?: boolean;
    /** Maximum plugins to execute (default: unlimited) */
    readonly maxPlugins?: number;
    /** Timeout for individual plugin execution in milliseconds */
    readonly pluginTimeoutMs?: number;
    /** Filter to select which plugins to run */
    readonly filter?: Omit<PluginFilter, 'category'>;
}

/**
 * Result of a single plugin execution.
 */
export interface PluginExecutionResult {
    /** Plugin ID */
    readonly pluginId: string;
    /** Whether execution succeeded */
    readonly success: boolean;
    /** Result from the plugin (if successful) */
    readonly result?: ProcessingResult;
    /** Error (if failed) */
    readonly error?: Error;
    /** Execution duration in milliseconds */
    readonly durationMs: number;
    /** Whether the plugin was skipped */
    readonly skipped: boolean;
    /** Reason for skipping (if skipped) */
    readonly skipReason?: string;
}

/**
 * Result of pipeline execution.
 */
export interface PipelineExecutionResult {
    /** Whether the overall pipeline succeeded */
    readonly success: boolean;
    /** Individual plugin results */
    readonly pluginResults: readonly PluginExecutionResult[];
    /** The final processing context */
    readonly context: ProcessingContext;
    /** Total execution duration in milliseconds */
    readonly totalDurationMs: number;
    /** Number of plugins executed */
    readonly pluginsExecuted: number;
    /** Number of plugins that failed */
    readonly pluginsFailed: number;
    /** Number of plugins skipped */
    readonly pluginsSkipped: number;
}

/**
 * Hook called before plugin execution.
 */
export type BeforePluginHook = (
    pluginId: string,
    context: ProcessingContext
) => Promise<boolean> | boolean;

/**
 * Hook called after plugin execution.
 */
export type AfterPluginHook = (
    pluginId: string,
    result: PluginExecutionResult,
    context: ProcessingContext
) => Promise<void> | void;

/**
 * Pipeline runner that orchestrates plugin execution.
 *
 * Features:
 * - Priority-based plugin ordering
 * - Timeout handling at plugin level
 * - Before/after execution hooks
 * - Comprehensive result tracking
 *
 * @example
 * ```typescript
 * const runner = new PipelineRunner(registry);
 *
 * // Run all evidence processors for an event
 * const result = await runner.runEvidenceProcessors(context, evidence);
 *
 * if (result.success) {
 *   console.log('Processed by:', result.pluginsExecuted);
 * }
 * ```
 */
export class PipelineRunner {
    private readonly registry: PluginRegistry;
    private readonly beforeHooks: BeforePluginHook[] = [];
    private readonly afterHooks: AfterPluginHook[] = [];

    constructor(registry: PluginRegistry) {
        this.registry = registry;
    }

    /**
     * Add a hook to run before each plugin.
     *
     * @param hook - The hook function
     * @returns this for chaining
     */
    addBeforeHook(hook: BeforePluginHook): this {
        this.beforeHooks.push(hook);
        return this;
    }

    /**
     * Add a hook to run after each plugin.
     *
     * @param hook - The hook function
     * @returns this for chaining
     */
    addAfterHook(hook: AfterPluginHook): this {
        this.afterHooks.push(hook);
        return this;
    }

    /**
     * Run all evidence processors through the pipeline.
     *
     * @param context - The processing context
     * @param evidence - The evidence event to process
     * @param config - Pipeline configuration
     * @returns Pipeline execution result
     */
    async runEvidenceProcessors(
        context: ProcessingContext,
        evidence: EvidenceEvent,
        config?: PipelineConfig
    ): Promise<PipelineExecutionResult> {
        const processors = this.registry.getEvidenceProcessors(config?.filter);
        return this.executePlugins(processors, context, evidence, config);
    }

    /**
     * Execute a list of plugins through the pipeline.
     */
    private async executePlugins(
        plugins: readonly EvidenceProcessor[],
        context: ProcessingContext,
        evidence: EvidenceEvent,
        config?: PipelineConfig
    ): Promise<PipelineExecutionResult> {
        const startTime = Date.now();
        const pluginResults: PluginExecutionResult[] = [];

        // Limit plugins if configured
        const pluginsToRun = config?.maxPlugins
            ? plugins.slice(0, config.maxPlugins)
            : plugins;

        // Sequential execution
        for (const plugin of pluginsToRun) {
            // Check if context was halted by a plugin
            if (context.halt) {
                break;
            }

            const result = await this.executePlugin(plugin, context, evidence, config?.pluginTimeoutMs);
            pluginResults.push(result);

            // Stop on error if configured
            if (config?.stopOnError && !result.success && !result.skipped) {
                context.halt = true;
                context.haltReason = `Plugin ${plugin.metadata.id} failed`;
                break;
            }
        }

        const totalDurationMs = Date.now() - startTime;
        const pluginsExecuted = pluginResults.filter((r) => !r.skipped).length;
        const pluginsFailed = pluginResults.filter((r) => !r.success && !r.skipped).length;
        const pluginsSkipped = pluginResults.filter((r) => r.skipped).length;

        return {
            success: pluginsFailed === 0,
            pluginResults,
            context,
            totalDurationMs,
            pluginsExecuted,
            pluginsFailed,
            pluginsSkipped,
        };
    }

    /**
     * Execute a single plugin.
     */
    private async executePlugin(
        plugin: EvidenceProcessor,
        context: ProcessingContext,
        evidence: EvidenceEvent,
        timeoutMs?: number
    ): Promise<PluginExecutionResult> {
        const pluginId = plugin.metadata.id;
        const startTime = Date.now();

        // Run before hooks
        for (const hook of this.beforeHooks) {
            const shouldContinue = await hook(pluginId, context);
            if (!shouldContinue) {
                return {
                    pluginId,
                    success: true,
                    skipped: true,
                    skipReason: 'Skipped by before hook',
                    durationMs: Date.now() - startTime,
                };
            }
        }

        // Check if plugin is enabled
        if (plugin.metadata.enabled === false) {
            const result: PluginExecutionResult = {
                pluginId,
                success: true,
                skipped: true,
                skipReason: 'Plugin is disabled',
                durationMs: Date.now() - startTime,
            };
            await this.runAfterHooks(pluginId, result, context);
            return result;
        }

        // Check if plugin supports this evidence
        if (!plugin.supports(evidence)) {
            const result: PluginExecutionResult = {
                pluginId,
                success: true,
                skipped: true,
                skipReason: 'Plugin does not support this evidence type',
                durationMs: Date.now() - startTime,
            };
            await this.runAfterHooks(pluginId, result, context);
            return result;
        }

        try {
            // Execute with optional timeout
            let processingResult: ProcessingResult;

            if (timeoutMs) {
                processingResult = await this.executeWithTimeout(
                    () => plugin.process(context, evidence),
                    timeoutMs,
                    pluginId
                );
            } else {
                processingResult = await plugin.process(context, evidence);
            }

            const durationMs = Date.now() - startTime;

            const result: PluginExecutionResult = {
                pluginId,
                success: processingResult.status !== 'error',
                result: processingResult,
                durationMs,
                skipped: processingResult.status === 'skipped',
                skipReason: processingResult.status === 'skipped' ? 'Plugin returned skipped status' : undefined,
            };

            await this.runAfterHooks(pluginId, result, context);
            return result;
        } catch (error) {
            const durationMs = Date.now() - startTime;
            const errorObj = error instanceof Error ? error : new Error(String(error));

            const result: PluginExecutionResult = {
                pluginId,
                success: false,
                error: errorObj,
                durationMs,
                skipped: false,
            };

            // Add error to context
            context.errors.push({
                pluginId,
                code: 'EXECUTION_ERROR',
                message: errorObj.message,
            });

            await this.runAfterHooks(pluginId, result, context);
            return result;
        }
    }

    /**
     * Execute a function with a timeout.
     */
    private async executeWithTimeout<T>(
        fn: () => Promise<T>,
        timeoutMs: number,
        pluginId: string
    ): Promise<T> {
        return new Promise<T>((resolve, reject) => {
            const timer = setTimeout(() => {
                reject(new Error(`Plugin ${pluginId} execution timed out after ${timeoutMs}ms`));
            }, timeoutMs);

            fn()
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
     * Run all after hooks.
     */
    private async runAfterHooks(
        pluginId: string,
        result: PluginExecutionResult,
        context: ProcessingContext
    ): Promise<void> {
        for (const hook of this.afterHooks) {
            try {
                await hook(pluginId, result, context);
            } catch (error) {
                console.error(`Error in after hook for plugin ${pluginId}:`, error);
            }
        }
    }
}

/**
 * Create a pipeline runner with the given registry.
 *
 * @param registry - The plugin registry
 * @returns New pipeline runner
 */
export function createPipelineRunner(registry: PluginRegistry): PipelineRunner {
    return new PipelineRunner(registry);
}
