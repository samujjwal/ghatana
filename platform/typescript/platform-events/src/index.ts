/**
 * @fileoverview @ghatana/platform-events - Canonical cross-cutting event taxonomy,
 * AI visibility contracts, security, privacy, and observability types.
 *
 * @doc.type package
 * @doc.purpose Provides shared event schemas, AI visibility contracts, and observability
 *   primitives for canvas, builder, and design-system platforms.
 * @doc.layer platform
 * @doc.dependency None - this is the root of the platform type graph
 */

// Events
export type {
  CorrelationId,
  SessionId,
  EventSource,
  PlatformEvent,
} from './events/base.js';

export {
  createCorrelationId,
  createSessionId,
  isValidCorrelationId,
  isValidSessionId,
} from './events/base.js';

export type { CanvasEventPayloads } from './events/canvas-events.js';
export { CanvasEvents } from './events/canvas-events.js';

export type { BuilderEventPayloads } from './events/builder-events.js';
export { BuilderEvents } from './events/builder-events.js';

export type { DesignSystemEventPayloads } from './events/design-system-events.js';
export { DesignSystemEvents } from './events/design-system-events.js';

// AI
export type {
  AIAutonomyLevel,
  AIApprovalState,
  AIChangeKind,
  AIChangeDescriptor,
  AIVisibilityContract,
  AIPolicy,
  AutonomyThreshold,
  ApprovalRequirement,
  AISuggestion,
  AISuggestionKind,
  AIOperationEvent,
} from './ai/types.js';

export {
  AUTONOMY_LEVELS,
  APPROVAL_STATES,
  AI_CHANGE_KINDS,
  isValidAutonomyLevel,
  isValidApprovalState,
  isValidAIChangeKind,
} from './ai/types.js';

export type {
  AutonomyExecutionMode,
  AutonomyModeChangedEvent,
  AutonomyModeViolationEvent,
} from './ai/policy.js';

export {
  AUTONOMY_EXECUTION_MODES,
  isValidAutonomyExecutionMode,
  createAutonomyModeChangedEvent,
  createAutonomyModeViolationEvent,
} from './ai/policy.js';

// Security
export type {
  TrustLevel,
  SecurityPolicy,
  SecurityMetadata,
  PreviewSecurityProfile,
} from './security/types.js';

export {
  TRUST_LEVELS,
  isValidTrustLevel,
  createDefaultSecurityPolicy,
} from './security/types.js';

// Privacy
export type {
  DataClassification,
  PrivacyPolicy,
  PrivacyMetadata,
} from './privacy/types.js';

export {
  DATA_CLASSIFICATIONS,
  isValidDataClassification,
  createDefaultPrivacyPolicy,
} from './privacy/types.js';

// Visibility
export type {
  VisibilityContract,
  ProvenanceRecord,
  OperationRecord,
  SyncStatus,
  OwnershipRegion,
  CodeOwnership,
} from './visibility/types.js';

export {
  createProvenanceRecord,
  createOperationRecord,
  createDefaultSyncStatus,
} from './visibility/types.js';

// Observability
export type {
  SpanRef,
  MetricSchema,
  AuditRecord,
  TraceContext,
  MetricFamily,
} from './observability/types.js';

export {
  createSpanRef,
  createAuditRecord,
} from './observability/types.js';
