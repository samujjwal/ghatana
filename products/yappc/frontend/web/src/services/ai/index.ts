/**
 * AI Services Index
 *
 * @doc.type module
 * @doc.purpose Export all AI services and types
 * @doc.layer product
 * @doc.pattern Barrel Export
 */

export {
    generateArtifactSuggestions,
    suggestArtifactContent,
    getRecommendedArtifacts,
} from './ArtifactSuggestionService';

export type {
    ArtifactSuggestion,
    SuggestionContext,
} from './ArtifactSuggestionService';

export {
    generatePhaseAISuggestions,
    getPhasePrompt,
    getNextAction,
    isPhaseReady,
} from './PhaseAIPromptService';

export type {
    PhasePrompt,
    PhaseAIContext,
    PhaseAISuggestion,
} from './PhaseAIPromptService';

// AIService exports
export { AIService, getAIService, resetAIService } from './AIService';
export type {
    AIServiceConfig,
    GenerateOptions,
    GenerateResult,
} from './AIService';

// Types exports
export type {
    ModelProvider,
    FallbackStrategy,
    LLMRequest,
    LLMResponse,
    AIError,
    AIErrorCode,
    QualityMetric,
    QualitySummary,
    ConfidenceScore,
    RequestMetadata,
} from './types';
