/**
 * Development Phase Canvas Nodes
 *
 * Exports all canvas node components for the Development Phase:
 * - StoryNode: User story visualization
 * - SprintNode: Sprint container visualization
 * - PullRequestNode: Code review visualization
 * - DeploymentNode: Deployment pipeline visualization
 *
 * @doc.type module
 * @doc.purpose Development phase canvas node exports
 * @doc.layer product
 */

// Story Node
export { StoryNode } from './StoryNode';
export type {
  StoryNodeData,
  StoryNodeProps,
  StoryType,
  StoryStatus,
  StoryPriority,
  StoryTask,
  StoryAssignee,
} from './StoryNode';

// Sprint Node
export { SprintNode } from './SprintNode';
export type {
  SprintNodeData,
  SprintNodeProps,
  SprintStatus,
  SprintMember,
  SprintBurndown,
} from './SprintNode';

// Pull Request Node
export { PullRequestNode } from './PullRequestNode';
export type {
  PullRequestNodeData,
  PullRequestNodeProps,
  ReviewStatus,
  ChecksStatus,
  GitProvider,
  PRReviewer,
  PRChecks,
} from './PullRequestNode';

// Deployment Node
export { DeploymentNode } from './DeploymentNode';
export type {
  DeploymentNodeData,
  DeploymentNodeProps,
  DeploymentStatus,
  DeploymentEnvironment,
  PipelineStage,
} from './DeploymentNode';

// Node type registry for ReactFlow
export const developmentNodeTypes = {
  story: StoryNode,
  sprint: SprintNode,
  pullRequest: PullRequestNode,
  deployment: DeploymentNode,
} as const;

export type DevelopmentNodeType = keyof typeof developmentNodeTypes;

// Re-export default imports
import StoryNode from './StoryNode';
import SprintNode from './SprintNode';
import PullRequestNode from './PullRequestNode';
import DeploymentNode from './DeploymentNode';

export default {
  StoryNode,
  SprintNode,
  PullRequestNode,
  DeploymentNode,
  developmentNodeTypes,
};
