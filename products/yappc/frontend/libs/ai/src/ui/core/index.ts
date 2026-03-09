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

// Chat and copilot
export { AICopilotPanel } from './AICopilotPanel';
export type { AICopilotPanelProps, CopilotMessage, CopilotPosition } from './AICopilotPanel';

// Predictions display
export { PredictionCard } from './PredictionCard';
export type { PredictionCardProps } from './PredictionCard';

// Anomaly alerts
export { AnomalyBanner } from './AnomalyBanner';
export type { AnomalyBannerProps } from './AnomalyBanner';

// Smart suggestions
export { SmartSuggestions } from './SmartSuggestions';
export type { SmartSuggestionsProps } from './SmartSuggestions';

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
