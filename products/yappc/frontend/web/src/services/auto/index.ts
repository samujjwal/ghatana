/**
 * Auto Services Exports
 *
 * @doc.type module
 * @doc.purpose Auto service exports
 * @doc.layer product
 */

export {
  calculateNextBestActions,
  getTopRecommendedAction,
  getActionById,
} from './NextBestActionService';

export type {
  ActionContext,
  Blocker,
  Activity,
  SuggestedAction,
  ActionRankingResult,
} from './NextBestActionService';

export {
  autoApplyImprovements,
  rollbackImprovement,
  getImprovementSuggestions,
  calculateImprovementConfidence,
  batchApplyImprovements,
  batchRollbackImprovements,
} from './AutoApplyImprovements';

export type {
  Improvement,
  ImprovementApplication,
  AutoApplyOptions,
} from './AutoApplyImprovements';
