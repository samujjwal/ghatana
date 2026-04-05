/**
 * Bootstrap Canvas Nodes
 *
 * @description Custom ReactFlow nodes for the bootstrapping phase project graph.
 * Each node extends the visual styling pattern from @ghatana/yappc-canvas while providing
 * domain-specific data structures for project ideation.
 *
 * Node Types:
 * - FeatureNode: Features identified during AI conversation
 * - ServiceNode: Backend services and APIs
 * - DatabaseNode: Data stores and databases
 * - IntegrationNode: Third-party integrations
 * - UserNode: User personas and roles
 *
 * @doc.type module
 * @doc.purpose Bootstrap phase canvas nodes
 * @doc.layer product
 */

// Feature Node - project features
export { FeatureNode, default as FeatureNodeDefault } from './FeatureNode';
export type {
  FeatureNodeProps,
  FeatureNodeData,
  FeaturePhase,
  FeaturePriority,
  FeatureStatus,
} from './FeatureNode';

// Service Node - backend services
export { ServiceNode, default as ServiceNodeDefault } from './ServiceNode';
export type {
  ServiceNodeProps,
  ServiceNodeData,
  ServiceType,
  ServiceEndpoint,
} from './ServiceNode';

// Database Node - data stores
export { DatabaseNode, default as DatabaseNodeDefault } from './DatabaseNode';
export type {
  DatabaseNodeProps,
  DatabaseNodeData,
  DatabaseType,
  DatabaseEntity,
} from './DatabaseNode';

// Integration Node - third-party services
export { IntegrationNode, default as IntegrationNodeDefault } from './IntegrationNode';
export type {
  IntegrationNodeProps,
  IntegrationNodeData,
  IntegrationType,
  AuthMethod,
  IntegrationCapability,
} from './IntegrationNode';

// User Node - personas and roles
export { UserNode, default as UserNodeDefault } from './UserNode';
export type {
  UserNodeProps,
  UserNodeData,
  UserType,
  UserPermission,
  UserGoal,
} from './UserNode';

// =============================================================================
// Node Type Registry for ReactFlow
// =============================================================================

import { FeatureNode } from './FeatureNode';
import { ServiceNode } from './ServiceNode';
import { DatabaseNode } from './DatabaseNode';
import { IntegrationNode } from './IntegrationNode';
import { UserNode } from './UserNode';

/**
 * Bootstrap node types map for ReactFlow
 * Use this when configuring nodeTypes in ReactFlow
 *
 * @example
 * ```tsx
 * import { bootstrapNodeTypes } from '@/components/canvas/nodes/bootstrap';
 *
 * <ReactFlow nodeTypes={bootstrapNodeTypes} ... />
 * ```
 */
export const bootstrapNodeTypes = {
  feature: FeatureNode,
  service: ServiceNode,
  database: DatabaseNode,
  integration: IntegrationNode,
  user: UserNode,
} as const;

/**
 * All bootstrap node type keys
 */
export type BootstrapNodeType = keyof typeof bootstrapNodeTypes;

/**
 * Check if a node type is a bootstrap node
 */
export const isBootstrapNodeType = (type: string): type is BootstrapNodeType => {
  return type in bootstrapNodeTypes;
};
