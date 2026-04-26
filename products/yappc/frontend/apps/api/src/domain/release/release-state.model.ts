/**
 * Release/Deploy State Models
 *
 * Defines the release lifecycle states and deployment tracking.
 *
 * @doc.type module
 * @doc.purpose Release and deployment state management
 * @doc.layer domain
 * @doc.pattern State Machine
 */

// ============================================================================
// Release States
// ============================================================================

export type ReleaseState =
  | 'draft'
  | 'building'
  | 'testing'
  | 'staging'
  | 'ready'
  | 'deploying'
  | 'deployed'
  | 'rolled_back'
  | 'failed';

export interface ReleaseStateDefinition {
  state: ReleaseState;
  label: string;
  description: string;
  allowedTransitions: ReleaseState[];
  requiresApproval: boolean;
  autoDeploy: boolean;
}

export const RELEASE_STATE_DEFINITIONS: Record<ReleaseState, ReleaseStateDefinition> = {
  draft: {
    state: 'draft',
    label: 'Draft',
    description: 'Release is being prepared',
    allowedTransitions: ['building', 'testing', 'ready'],
    requiresApproval: false,
    autoDeploy: false,
  },
  building: {
    state: 'building',
    label: 'Building',
    description: 'Artifacts are being built',
    allowedTransitions: ['testing', 'failed'],
    requiresApproval: false,
    autoDeploy: false,
  },
  testing: {
    state: 'testing',
    label: 'Testing',
    description: 'Automated tests are running',
    allowedTransitions: ['staging', 'failed', 'draft'],
    requiresApproval: false,
    autoDeploy: false,
  },
  staging: {
    state: 'staging',
    label: 'Staging',
    description: 'Deployed to staging environment',
    allowedTransitions: ['ready', 'failed', 'rolled_back'],
    requiresApproval: false,
    autoDeploy: false,
  },
  ready: {
    state: 'ready',
    label: 'Ready',
    description: 'Approved for production deployment',
    allowedTransitions: ['deploying', 'rolled_back'],
    requiresApproval: true,
    autoDeploy: false,
  },
  deploying: {
    state: 'deploying',
    label: 'Deploying',
    description: 'Currently deploying to production',
    allowedTransitions: ['deployed', 'failed', 'rolled_back'],
    requiresApproval: false,
    autoDeploy: false,
  },
  deployed: {
    state: 'deployed',
    label: 'Deployed',
    description: 'Successfully deployed to production',
    allowedTransitions: ['rolled_back'],
    requiresApproval: false,
    autoDeploy: false,
  },
  rolled_back: {
    state: 'rolled_back',
    label: 'Rolled Back',
    description: 'Deployment was rolled back',
    allowedTransitions: ['draft'],
    requiresApproval: true,
    autoDeploy: false,
  },
  failed: {
    state: 'failed',
    label: 'Failed',
    description: 'Deployment or build failed',
    allowedTransitions: ['draft', 'building'],
    requiresApproval: false,
    autoDeploy: false,
  },
};

// ============================================================================
// Deployment Environment
// ============================================================================

export type DeploymentEnvironment = 'development' | 'staging' | 'production' | 'canary';

export interface DeploymentTarget {
  id: string;
  environment: DeploymentEnvironment;
  region?: string;
  cluster?: string;
  url: string;
}

// ============================================================================
// Release Model
// ============================================================================

export interface Release {
  id: string;
  projectId: string;
  version: string;
  name: string;
  description?: string;

  // State
  state: ReleaseState;
  previousState?: ReleaseState;

  // Artifacts
  artifacts: ReleaseArtifact[];
  manifest: ReleaseManifest;

  // Deployment tracking
  deployments: DeploymentRecord[];

  // Approval
  approvedBy?: string;
  approvedAt?: Date;
  approvalNotes?: string;

  // Metadata
  createdBy: string;
  createdAt: Date;
  updatedAt: Date;
  releasedAt?: Date;

  // Rollback
  canRollback: boolean;
  rollbackReleaseId?: string;
}

export interface ReleaseArtifact {
  id: string;
  type: 'container' | 'binary' | 'package' | 'chart' | 'config';
  name: string;
  version: string;
  location: string;
  checksum: string;
  size: number;
  metadata?: Record<string, unknown>;
}

export interface ReleaseManifest {
  version: string;
  services: ServiceDefinition[];
  dependencies: DependencyDefinition[];
  configMap?: Record<string, string>;
  secrets?: string[]; // References only, not actual values
}

export interface ServiceDefinition {
  name: string;
  image: string;
  tag: string;
  replicas: number;
  resources: {
    cpu: string;
    memory: string;
  };
  ports: Array<{
    name: string;
    port: number;
    protocol: 'TCP' | 'UDP';
  }>;
  healthCheck?: {
    path: string;
    port: number;
  };
}

export interface DependencyDefinition {
  name: string;
  version: string;
  required: boolean;
  type: 'service' | 'library' | 'database';
}

// ============================================================================
// Deployment Record
// ============================================================================

export interface DeploymentRecord {
  id: string;
  releaseId: string;
  environment: DeploymentEnvironment;
  target: DeploymentTarget;

  // Status
  status: 'pending' | 'in_progress' | 'completed' | 'failed' | 'cancelled';
  state: ReleaseState;

  // Timing
  startedAt: Date;
  completedAt?: Date;
  duration?: number; // milliseconds

  // Details
  steps: DeploymentStep[];
  logs?: string[];
  error?: string;

  // Actor
  triggeredBy: string;
  triggeredByUser: string;
}

export interface DeploymentStep {
  id: string;
  name: string;
  status: 'pending' | 'running' | 'completed' | 'failed' | 'skipped';
  startedAt?: Date;
  completedAt?: Date;
  logs?: string[];
  error?: string;
}

// ============================================================================
// State Transitions
// ============================================================================

export function canTransition(
  fromState: ReleaseState,
  toState: ReleaseState
): boolean {
  const definition = RELEASE_STATE_DEFINITIONS[fromState];
  return definition.allowedTransitions.includes(toState);
}

export function getAllowedTransitions(state: ReleaseState): ReleaseState[] {
  return RELEASE_STATE_DEFINITIONS[state].allowedTransitions;
}

export function requiresApprovalForTransition(
  fromState: ReleaseState,
  toState: ReleaseState
): boolean {
  const definition = RELEASE_STATE_DEFINITIONS[fromState];
  if (!definition.allowedTransitions.includes(toState)) {
    return false;
  }
  return definition.requiresApproval || RELEASE_STATE_DEFINITIONS[toState].requiresApproval;
}

// ============================================================================
// Factory Functions
// ============================================================================

export function createRelease(
  params: Omit<Release, 'id' | 'createdAt' | 'updatedAt' | 'state' | 'deployments'>
): Release {
  const now = new Date();
  return {
    id: `release-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    state: 'draft',
    deployments: [],
    createdAt: now,
    updatedAt: now,
    canRollback: false,
    ...params,
  };
}

export function createDeploymentRecord(
  params: Omit<DeploymentRecord, 'id' | 'startedAt' | 'steps'>
): DeploymentRecord {
  return {
    id: `deploy-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    startedAt: new Date(),
    steps: [],
    ...params,
  };
}

export function transitionReleaseState(
  release: Release,
  newState: ReleaseState,
  actor: string
): { success: boolean; release?: Release; error?: string } {
  if (!canTransition(release.state, newState)) {
    return {
      success: false,
      error: `Cannot transition from ${release.state} to ${newState}`,
    };
  }

  const updated: Release = {
    ...release,
    previousState: release.state,
    state: newState,
    updatedAt: new Date(),
  };

  if (newState === 'deployed') {
    updated.releasedAt = new Date();
    updated.canRollback = true;
  }

  return { success: true, release: updated };
}
