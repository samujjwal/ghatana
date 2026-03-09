/**
 * @ghatana/yappc-ml - Machine Learning and Personalization Library
 *
 * Comprehensive ML features including behavior tracking, recommendations,
 * adaptive UI, and A/B testing.
 *
 * @packageDocumentation
 */

// Behavior Tracking
export {
  BehaviorTracker,
  type BehaviorEvent,
  type BehaviorEventType,
  type UserSession,
  type BehaviorPattern,
  type BehaviorProfile,
  type BehaviorStorageAdapter,
  type BehaviorTrackerConfig,
} from './tracking/BehaviorTracker';

// Recommendations
export {
  RecommendationEngine,
  type RecommendationItem,
  type Recommendation,
  type UserInteraction,
  type RecommendationConfig,
} from './recommendations/RecommendationEngine';

// Adaptive UI
export {
  AdaptiveUI,
  type AdaptationType,
  type AdaptationRule,
  type UserContext,
  type UserPreferences,
  type AdaptationResult,
  type AdaptiveUIConfig,
} from './adaptive/AdaptiveUI';

// A/B Testing
export {
  ABTestFramework,
  type Variant,
  type Experiment,
  type ExperimentMetric,
  type VariantAssignment,
  type MetricEvent,
  type ExperimentResults,
  type VariantResults,
  type StatisticalTest,
  type ABTestStorageAdapter,
} from './testing/ABTestFramework';
