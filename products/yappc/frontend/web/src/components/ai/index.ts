/**
 * AI Components
 *
 * Central AI command interface components.
 *
 * @doc.type module
 * @doc.purpose AI component exports
 * @doc.layer product
 * @doc.pattern Barrel Export
 */

export { CommandInput } from './CommandInput';
export type { CommandInputProps } from './CommandInput';

export { AIResponseCard } from './AIResponseCard';
export type { AIResponseCardProps } from './AIResponseCard';

export { RecentProjectsStrip } from './RecentProjectsStrip';
export type { RecentProjectsStripProps } from './RecentProjectsStrip';

export { AISourceChip } from './AISourceChip';
export type { AISourceChipProps, AISource } from './AISourceChip';

export { LiveStatusBanner } from './LiveStatusBanner';
export type { LiveStatusBannerProps, LiveStatus } from './LiveStatusBanner';

export { RunLineage } from './RunLineage';
export type { RunLineageProps, RunLineageData, RunLineageNode } from './RunLineage';

export { AICommandBar } from './AICommandBar';
export type { AICommandBarProps, AISubmitOptions } from './AICommandBar';

export { InsightPanel } from './InsightPanel';
export type { InsightPanelProps } from './InsightPanel';

export { ConfidenceBadge, ConfidenceDot } from './ConfidenceBadge';
export type { ConfidenceBadgeProps, ConfidenceLevel } from './ConfidenceBadge';

export { AILabelOverlay, AISectionHeader } from './AILabelOverlay';
export type { AILabelOverlayProps, AILabelSize, AILabelVariant } from './AILabelOverlay';

export { CostTile } from './CostTile';
export type { CostTileProps } from './CostTile';

export { RefactorSuggestionPanel } from './RefactorSuggestionPanel';
export type { RefactorSuggestionPanelProps } from './RefactorSuggestionPanel';

export { GeneratedCodeQualityGate } from './GeneratedCodeQualityGate';
export type { GeneratedCodeQualityGateProps } from './GeneratedCodeQualityGate';

export { WebSocketDegradedBanner } from './WebSocketDegradedBanner';
export type { WebSocketDegradedBannerProps } from './WebSocketDegradedBanner';

export { LiveProgressNarrative } from './LiveProgressNarrative';
export type { LiveProgressNarrativeProps, NarrativeEvent, NarrativeStreamStatus } from './LiveProgressNarrative';

export { AIAssistLabel } from './AIAssistLabel';
export type { AIAssistLabelProps, AIAssistSource } from './AIAssistLabel';

export { CostOptimisationPanel } from './CostOptimisationPanel';
export type {
  CostOptimisationPanelProps,
  CostAnalysisData,
  ModelCostBreakdown,
  CheaperAlternative,
} from './CostOptimisationPanel';
export { TestGenerationPanel } from './TestGenerationPanel';
export type { TestGenerationPanelProps, TestGenerationResult, GeneratedTestCase } from './TestGenerationPanel';
