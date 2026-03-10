/**
 * Learning Kernel - Core Plugin Orchestration for Learning Evidence Platform
 *
 * This is the central kernel that coordinates all plugins in the LEP.
 * It implements the Plugin Kernel architecture where:
 * - All features are plugins
 * - Plugins communicate via shared ProcessingContext (Blackboard pattern)
 * - Plugins are executed in priority order via PipelineRunner
 *
 * @doc.type module
 * @doc.purpose Core orchestration kernel for LEP
 * @doc.layer core
 * @doc.pattern Plugin Kernel, Blackboard
 *
 * @example
 * ```typescript
 * import {
 *   PluginRegistry,
 *   PipelineRunner,
 *   ProcessingContext,
 *   globalRegistry,
 * } from '@ghatana/tutorputor-learning-kernel';
 *
 * // Register plugins
 * globalRegistry.registerEvidenceProcessor(new CBMProcessor());
 * globalRegistry.registerEvidenceProcessor(new BKTProcessor());
 *
 * // Create pipeline runner
 * const runner = new PipelineRunner(globalRegistry);
 *
 * // Process an event
 * const result = await runner.runEvidenceProcessors(context, evidence);
 * console.log('Results:', result.pluginResults);
 * ```
 */

// Registry
export {
    PluginRegistry,
    globalRegistry,
    type PluginCategory,
    type PluginFilter,
    type PluginEvent,
    type PluginEventListener,
} from './registry/PluginRegistry';

// Pipeline
export {
    PipelineRunner,
    createPipelineRunner,
    type PipelineConfig,
    type PluginExecutionResult,
    type PipelineExecutionResult,
    type BeforePluginHook,
    type AfterPluginHook,
} from './pipeline/PipelineRunner';

// Built-in Plugins
export {
    CBMProcessor,
    createCBMProcessor,
    type CBMConfig,
    type CBMResult,
    type CBMAggregateMetrics,
    type ConfidenceLevel,
} from './plugins/CBMProcessor';

export {
    XAPIIngestor,
    createXAPIIngestor,
    type XAPIIngestorConfig,
    type XAPIStatement,
} from './plugins/XAPIIngestor';

export {
    LearningUnitValidator,
    createLearningUnitValidator,
    type LearningUnitValidatorConfig,
    type ValidationRule,
    type ValidationCategory,
} from './plugins/LearningUnitValidator';

export {
    ContentStudioValidator,
    createContentStudioValidator,
    type ContentStudioValidatorConfig,
    type ContentStudioValidationRule,
} from './plugins/ContentStudioValidator';

export {
    BKTProcessor,
    createBKTProcessor,
    type BKTConfig,
    type BKTParams,
    type BKTResult,
} from './plugins/BKTProcessor';

export {
    IRTProcessor,
    createIRTProcessor,
    type IRTConfig,
    type IRTItemParams,
    type IRTResult,
} from './plugins/IRTProcessor';

// Re-export core types from contracts for convenience
export type {
    Plugin,
    PluginMetadata,
    PluginStatus,
    PluginType,
    PluginRegistration,
    EvidenceProcessor,
    Ingestor,
    AssetProvider,
    AuthoringTool,
    Notifier,
    ProcessingContext,
    ProcessingResult,
    EvidenceEvent,
    RawEvent,
    ValidationResult,
    ValidationIssue,
} from '@ghatana/tutorputor-contracts/v1/plugin-interfaces';

// Engine
export * from './engine';

// Path
export * from './path';

