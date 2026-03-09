/**
 * AI Services Index
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
