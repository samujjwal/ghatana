/**
 * Canvas Components Export
 *
 * @module ui/components/canvas
 */

// Core canvas components
export * from './DraggableCanvas';
export { default as ProjectCanvas } from './ProjectCanvas';

// Comment thread for nodes
export { NodeCommentThread } from './NodeCommentThread';
export type {
  NodeCommentThreadProps,
  Comment,
  CommentAuthor,
  CommentReaction,
} from './NodeCommentThread';

// Approval workflow
export { ApprovalPanel } from './ApprovalPanel';
export type {
  ApprovalPanelProps,
  Approval,
  Approver,
  ApprovalRequirement,
  ApprovalStatus,
} from './ApprovalPanel';

// Generated artifacts
export { ArtifactsList } from './ArtifactsList';
export type {
  ArtifactsListProps,
  Artifact,
  ArtifactType,
  ArtifactStatus,
} from './ArtifactsList';

// Team review
export { TeamReviewPanel } from './TeamReviewPanel';
export type {
  TeamReviewPanelProps,
  Reviewer,
  ReviewerRole,
  ReviewStatus,
  PendingInvite,
} from './TeamReviewPanel';
