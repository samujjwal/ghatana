/**
 * AI UI Components
 *
 * Central export for all AI-related UI components.
 * These components connect to the AI backend via hooks and state atoms.
 *
 * @doc.type module
 * @doc.purpose AI component exports
 * @doc.layer product
 * @doc.pattern Barrel
 */

// REMOVED: AICopilotPanel (0 consumers, superseded by @ghatana/yappc-ui AIChatInterface)

// Predictions display
export { PredictionCard } from './PredictionCard';
export type { PredictionCardProps } from './PredictionCard';

// Anomaly alerts
export { AnomalyBanner } from './AnomalyBanner';
export type { AnomalyBannerProps } from './AnomalyBanner';

// Recommendation list (renamed from SmartSuggestions to avoid collision with @ghatana/yappc-ui SmartSuggestions)
export { RecommendationList } from './RecommendationList';
export type { RecommendationListProps } from './RecommendationList';

// AI Persona Briefing
export { AIPersonaBriefing } from './AIPersonaBriefing';
export type { AIPersonaBriefingProps } from './AIPersonaBriefing';

// AI Prediction Badge
export { AIPredictionBadge, usePhaseAIPrediction, useItemAIPrediction } from './AIPredictionBadge';
export type { AIPredictionBadgeProps, AIPrediction } from './AIPredictionBadge';

// Workflow wizard
export { AiWorkflowWizard } from './AiWorkflowWizard';
export type { AiWorkflowWizardProps } from './AiWorkflowWizard';

// Workflow step components
export * from './workflow';
