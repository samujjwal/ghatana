/**
 * Product Deployment Contract
 * 
 * Defines the contract for product deployments and their status.
 * Consumed by deployment UI components to display deployment information.
 * 
 * @doc.type module
 * @doc.purpose Product deployment contract for UI components
 * @doc.layer platform
 */

/**
 * Deployment environments
 */
export type DeploymentEnvironment = 'local' | 'dev' | 'staging' | 'prod';

/**
 * Deployment status
 */
export type DeploymentStatus = 'pending' | 'deploying' | 'deployed' | 'failed' | 'rolling-back' | 'rolled-back';

/**
 * Deployment target type
 */
export type DeploymentTargetType = 'compose' | 'kubernetes' | 'helm' | 'terraform';

/**
 * Product deployment
 */
export interface ProductDeployment {
  readonly deploymentId: string;
  readonly productId: string;
  readonly environment: DeploymentEnvironment;
  readonly version: string;
  readonly target: DeploymentTargetType;
  readonly status: DeploymentStatus;
  readonly surfaces: readonly DeploymentSurface[];
  readonly deployedAt?: string;
  readonly deployedBy: string;
  readonly rollbackPlan?: RollbackPlan;
}

/**
 * Surface-specific deployment information
 */
export interface DeploymentSurface {
  readonly surface: string;
  readonly status: DeploymentStatus;
  readonly replicas?: number;
  readonly resources?: DeploymentResources;
}

/**
 * Resource allocation for a surface
 */
export interface DeploymentResources {
  readonly cpu?: string;
  readonly memory?: string;
}

/**
 * Rollback plan for a deployment
 */
export interface RollbackPlan {
  readonly strategy: 'previous-artifact' | 'last-known-good' | 'manual';
  readonly targetVersion?: string;
  readonly reason?: string;
}

/**
 * Promotion between environments
 */
export interface ProductPromotion {
  readonly promotionId: string;
  readonly productId: string;
  readonly sourceEnvironment: DeploymentEnvironment;
  readonly targetEnvironment: DeploymentEnvironment;
  readonly version: string;
  readonly status: DeploymentStatus;
  readonly approvalRequired: boolean;
  readonly approvedBy?: string;
  readonly approvedAt?: string;
  readonly promotedAt?: string;
}
