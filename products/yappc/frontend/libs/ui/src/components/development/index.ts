/**
 * Development Components - Barrel Export
 *
 * @description Exports all Phase 3 Development UI components.
 * Includes sprint board, code review, deployment, and feature flag components.
 *
 * @doc.phase 3
 */

// Sprint/Kanban Components
export { StoryCard } from './StoryCard';
export type {
  Story,
  StoryType,
  StoryStatus,
  StoryPriority,
  StoryAssignee,
  StoryTask,
  StoryPullRequest,
  StoryCardProps,
} from './StoryCard';

export { SprintColumn } from './SprintColumn';
export type {
  SprintColumnProps,
  ColumnStatus,
} from './SprintColumn';

export { StoryDetailPanel } from './StoryDetailPanel';
export type {
  StoryDetailPanelProps,
  AcceptanceCriteria,
  LinkedResource,
  ActivityItem,
  StoryLabel,
} from './StoryDetailPanel';

// Chart Components
export { VelocityChart } from './VelocityChart';
export type {
  SprintVelocityData,
  VelocityChartProps,
} from './VelocityChart';

export { BurndownChart } from './BurndownChart';
export type {
  BurndownDataPoint,
  BurndownChartProps,
} from './BurndownChart';

// Retrospective Components
export { RetroItem } from './RetroItem';
export type {
  RetroItemData,
  RetroItemCategory,
  RetroVoter,
  RetroItemProps,
} from './RetroItem';

export { TeamMoodPicker } from './TeamMoodPicker';
export type {
  TeamMember,
  TeamMoodPickerProps,
} from './TeamMoodPicker';

// Code Review Components
export { CodeReviewCard } from './CodeReviewCard';
export type {
  PullRequest,
  PRStatus,
  CIStatus,
  ReviewDecision,
  PRReviewer,
  PRLabel,
  AIAnalysis,
  CodeReviewCardProps,
} from './CodeReviewCard';

// Feature Flag Components
export { FeatureFlagRow } from './FeatureFlagRow';
export type {
  FeatureFlag,
  FlagType,
  Environment,
  EnvironmentStatus,
  TargetingRule,
  FlagVariant,
  FeatureFlagRowProps,
} from './FeatureFlagRow';

// Deployment Components
export { DeploymentCard } from './DeploymentCard';
export type {
  Deployment,
  DeploymentStatus,
  PipelineStage,
  DeploymentChange,
  DeploymentCardProps,
} from './DeploymentCard';
