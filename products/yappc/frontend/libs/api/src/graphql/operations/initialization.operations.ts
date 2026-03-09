/**
 * Initialization Phase GraphQL Operations
 *
 * @description GraphQL queries, mutations, and subscriptions for the
 * initialization phase including wizard configuration, infrastructure
 * provisioning, environment setup, and cost management.
 *
 * @doc.type api
 * @doc.purpose Initialization Phase API
 * @doc.layer data-access
 */

import { gql } from '@apollo/client';

// =============================================================================
// FRAGMENTS
// =============================================================================

export const INITIALIZATION_PROJECT_FRAGMENT = gql`
  fragment InitializationProjectFields on InitializationProject {
    id
    name
    description
    createdAt
    updatedAt
    status
    currentStep
    totalSteps
    configuration {
      repository {
        type
        name
        visibility
        defaultBranch
        enableBranchProtection
        enableCodeOwners
      }
      hosting {
        provider
        region
        instanceType
        autoScaling {
          enabled
          minInstances
          maxInstances
          targetCPU
        }
      }
      database {
        type
        version
        instanceClass
        storageGB
        backupRetentionDays
        multiAZ
      }
      cache {
        enabled
        type
        nodeType
        clusterMode
      }
      storage {
        type
        bucketName
        versioning
        encryption
      }
      cicd {
        provider
        buildCommand
        testCommand
        deployCommand
        environments
      }
      monitoring {
        enabled
        provider
        alertChannels
        dashboards
      }
    }
    costEstimate {
      monthly
      yearly
      breakdown {
        category
        service
        amount
        unit
      }
      currency
    }
    team {
      id
      name
      email
      role
      inviteStatus
    }
  }
`;

export const INFRASTRUCTURE_STATUS_FRAGMENT = gql`
  fragment InfrastructureStatusFields on InfrastructureStatus {
    projectId
    overallStatus
    startedAt
    completedAt
    components {
      name
      type
      status
      progress
      message
      startedAt
      completedAt
      resourceId
      resourceUrl
    }
    logs {
      timestamp
      level
      component
      message
      details
    }
  }
`;

export const ENVIRONMENT_FRAGMENT = gql`
  fragment EnvironmentFields on Environment {
    id
    name
    type
    status
    url
    region
    createdAt
    configuration {
      instanceCount
      instanceType
      domain
      sslEnabled
    }
    secrets {
      key
      description
      lastRotated
      source
    }
    deployments {
      id
      version
      status
      deployedAt
      deployedBy
    }
  }
`;

export const WIZARD_PRESET_FRAGMENT = gql`
  fragment WizardPresetFields on WizardPreset {
    id
    name
    description
    category
    icon
    tags
    configuration {
      repository
      hosting
      database
      cache
      storage
      cicd
      monitoring
    }
    estimatedCost {
      monthly
      currency
    }
    suitableFor
    limitations
  }
`;

// =============================================================================
// QUERIES
// =============================================================================

export const GET_INITIALIZATION_PROJECT = gql`
  ${INITIALIZATION_PROJECT_FRAGMENT}
  
  query GetInitializationProject($id: ID!) {
    initializationProject(id: $id) {
      ...InitializationProjectFields
    }
  }
`;

export const GET_INFRASTRUCTURE_STATUS = gql`
  ${INFRASTRUCTURE_STATUS_FRAGMENT}
  
  query GetInfrastructureStatus($projectId: ID!) {
    infrastructureStatus(projectId: $projectId) {
      ...InfrastructureStatusFields
    }
  }
`;

export const LIST_ENVIRONMENTS = gql`
  ${ENVIRONMENT_FRAGMENT}
  
  query ListEnvironments($projectId: ID!) {
    environments(projectId: $projectId) {
      ...EnvironmentFields
    }
  }
`;

export const GET_ENVIRONMENT = gql`
  ${ENVIRONMENT_FRAGMENT}
  
  query GetEnvironment($id: ID!) {
    environment(id: $id) {
      ...EnvironmentFields
    }
  }
`;

export const GET_WIZARD_PRESETS = gql`
  ${WIZARD_PRESET_FRAGMENT}
  
  query GetWizardPresets($category: String, $search: String) {
    wizardPresets(category: $category, search: $search) {
      ...WizardPresetFields
    }
  }
`;

export const ESTIMATE_COST = gql`
  query EstimateCost($configuration: InitializationConfigInput!) {
    estimateCost(configuration: $configuration) {
      monthly
      yearly
      breakdown {
        category
        service
        amount
        unit
        details
      }
      currency
      assumptions
      optimizationSuggestions {
        category
        suggestion
        potentialSavings
        tradeoffs
      }
    }
  }
`;

export const GET_AVAILABLE_REGIONS = gql`
  query GetAvailableRegions($provider: CloudProvider!) {
    availableRegions(provider: $provider) {
      id
      name
      displayName
      location
      available
      latency
      services
    }
  }
`;

export const GET_AVAILABLE_INSTANCE_TYPES = gql`
  query GetAvailableInstanceTypes(
    $provider: CloudProvider!
    $region: String!
    $category: String
  ) {
    availableInstanceTypes(provider: $provider, region: $region, category: $category) {
      id
      name
      displayName
      vcpus
      memory
      network
      storage
      pricePerHour
      category
      recommended
    }
  }
`;

export const GET_DATABASE_OPTIONS = gql`
  query GetDatabaseOptions($provider: CloudProvider!) {
    databaseOptions(provider: $provider) {
      type
      name
      description
      versions
      instanceClasses {
        id
        name
        vcpus
        memory
        pricePerHour
      }
      features
      useCases
    }
  }
`;

export const VALIDATE_CONFIGURATION = gql`
  query ValidateConfiguration($configuration: InitializationConfigInput!) {
    validateConfiguration(configuration: $configuration) {
      isValid
      errors {
        field
        code
        message
        suggestion
      }
      warnings {
        field
        code
        message
        suggestion
      }
      recommendations {
        category
        message
        impact
        action
      }
    }
  }
`;

export const GET_INITIALIZATION_LOGS = gql`
  query GetInitializationLogs(
    $projectId: ID!
    $level: LogLevel
    $component: String
    $pagination: PaginationInput
  ) {
    initializationLogs(
      projectId: $projectId
      level: $level
      component: $component
      pagination: $pagination
    ) {
      edges {
        cursor
        node {
          id
          timestamp
          level
          component
          message
          details
          correlationId
        }
      }
      pageInfo {
        hasNextPage
        endCursor
      }
    }
  }
`;

// =============================================================================
// MUTATIONS
// =============================================================================

export const CREATE_INITIALIZATION_PROJECT = gql`
  ${INITIALIZATION_PROJECT_FRAGMENT}
  
  mutation CreateInitializationProject($input: CreateInitializationProjectInput!) {
    createInitializationProject(input: $input) {
      project {
        ...InitializationProjectFields
      }
      nextStepUrl
    }
  }
`;

export const UPDATE_PROJECT_CONFIGURATION = gql`
  ${INITIALIZATION_PROJECT_FRAGMENT}
  
  mutation UpdateProjectConfiguration(
    $projectId: ID!
    $configuration: InitializationConfigInput!
  ) {
    updateProjectConfiguration(projectId: $projectId, configuration: $configuration) {
      ...InitializationProjectFields
    }
  }
`;

export const APPLY_WIZARD_PRESET = gql`
  ${INITIALIZATION_PROJECT_FRAGMENT}
  
  mutation ApplyWizardPreset($projectId: ID!, $presetId: ID!) {
    applyWizardPreset(projectId: $projectId, presetId: $presetId) {
      ...InitializationProjectFields
    }
  }
`;

export const ADVANCE_WIZARD_STEP = gql`
  mutation AdvanceWizardStep($projectId: ID!, $stepData: JSON) {
    advanceWizardStep(projectId: $projectId, stepData: $stepData) {
      currentStep
      totalSteps
      stepName
      validationResult {
        isValid
        errors {
          field
          message
        }
      }
    }
  }
`;

export const GO_BACK_WIZARD_STEP = gql`
  mutation GoBackWizardStep($projectId: ID!) {
    goBackWizardStep(projectId: $projectId) {
      currentStep
      stepName
    }
  }
`;

export const START_INFRASTRUCTURE_PROVISIONING = gql`
  ${INFRASTRUCTURE_STATUS_FRAGMENT}
  
  mutation StartInfrastructureProvisioning($projectId: ID!) {
    startInfrastructureProvisioning(projectId: $projectId) {
      ...InfrastructureStatusFields
    }
  }
`;

export const CANCEL_INFRASTRUCTURE_PROVISIONING = gql`
  mutation CancelInfrastructureProvisioning($projectId: ID!) {
    cancelInfrastructureProvisioning(projectId: $projectId) {
      success
      message
      rollbackStatus
    }
  }
`;

export const RETRY_FAILED_COMPONENT = gql`
  mutation RetryFailedComponent($projectId: ID!, $componentName: String!) {
    retryFailedComponent(projectId: $projectId, componentName: $componentName) {
      success
      component {
        name
        status
        message
      }
    }
  }
`;

export const CREATE_ENVIRONMENT = gql`
  ${ENVIRONMENT_FRAGMENT}
  
  mutation CreateEnvironment($projectId: ID!, $input: CreateEnvironmentInput!) {
    createEnvironment(projectId: $projectId, input: $input) {
      ...EnvironmentFields
    }
  }
`;

export const UPDATE_ENVIRONMENT = gql`
  ${ENVIRONMENT_FRAGMENT}
  
  mutation UpdateEnvironment($id: ID!, $input: UpdateEnvironmentInput!) {
    updateEnvironment(id: $id, input: $input) {
      ...EnvironmentFields
    }
  }
`;

export const DELETE_ENVIRONMENT = gql`
  mutation DeleteEnvironment($id: ID!) {
    deleteEnvironment(id: $id) {
      success
      message
    }
  }
`;

export const SET_ENVIRONMENT_SECRET = gql`
  mutation SetEnvironmentSecret(
    $environmentId: ID!
    $key: String!
    $value: String!
    $description: String
  ) {
    setEnvironmentSecret(
      environmentId: $environmentId
      key: $key
      value: $value
      description: $description
    ) {
      key
      description
      lastRotated
    }
  }
`;

export const DELETE_ENVIRONMENT_SECRET = gql`
  mutation DeleteEnvironmentSecret($environmentId: ID!, $key: String!) {
    deleteEnvironmentSecret(environmentId: $environmentId, key: $key) {
      success
    }
  }
`;

export const INVITE_TEAM_MEMBER = gql`
  mutation InviteTeamMember($projectId: ID!, $input: InviteTeamMemberInput!) {
    inviteTeamMember(projectId: $projectId, input: $input) {
      id
      name
      email
      role
      inviteStatus
      inviteLink
    }
  }
`;

export const UPDATE_TEAM_MEMBER_ROLE = gql`
  mutation UpdateTeamMemberRole(
    $projectId: ID!
    $memberId: ID!
    $role: TeamRole!
  ) {
    updateTeamMemberRole(projectId: $projectId, memberId: $memberId, role: $role) {
      id
      role
    }
  }
`;

export const REMOVE_TEAM_MEMBER = gql`
  mutation RemoveTeamMember($projectId: ID!, $memberId: ID!) {
    removeTeamMember(projectId: $projectId, memberId: $memberId) {
      success
    }
  }
`;

export const FINALIZE_INITIALIZATION = gql`
  mutation FinalizeInitialization($projectId: ID!) {
    finalizeInitialization(projectId: $projectId) {
      success
      projectId
      dashboardUrl
      nextSteps {
        order
        title
        description
        actionUrl
        optional
      }
    }
  }
`;

export const SAVE_CONFIGURATION_AS_PRESET = gql`
  ${WIZARD_PRESET_FRAGMENT}
  
  mutation SaveConfigurationAsPreset($projectId: ID!, $name: String!, $description: String) {
    saveConfigurationAsPreset(
      projectId: $projectId
      name: $name
      description: $description
    ) {
      ...WizardPresetFields
    }
  }
`;

// =============================================================================
// SUBSCRIPTIONS
// =============================================================================

export const SUBSCRIBE_TO_PROVISIONING_STATUS = gql`
  ${INFRASTRUCTURE_STATUS_FRAGMENT}
  
  subscription OnProvisioningStatusUpdate($projectId: ID!) {
    provisioningStatusUpdated(projectId: $projectId) {
      ...InfrastructureStatusFields
    }
  }
`;

export const SUBSCRIBE_TO_PROVISIONING_LOGS = gql`
  subscription OnProvisioningLog($projectId: ID!) {
    provisioningLog(projectId: $projectId) {
      id
      timestamp
      level
      component
      message
      details
      correlationId
    }
  }
`;

export const SUBSCRIBE_TO_COST_UPDATES = gql`
  subscription OnCostUpdate($projectId: ID!) {
    costUpdated(projectId: $projectId) {
      monthly
      yearly
      breakdown {
        category
        service
        amount
        unit
      }
      delta {
        amount
        reason
      }
    }
  }
`;

export const SUBSCRIBE_TO_ENVIRONMENT_STATUS = gql`
  ${ENVIRONMENT_FRAGMENT}
  
  subscription OnEnvironmentStatusChange($projectId: ID!) {
    environmentStatusChanged(projectId: $projectId) {
      ...EnvironmentFields
    }
  }
`;

// =============================================================================
// TYPE DEFINITIONS
// =============================================================================

export interface CreateInitializationProjectInput {
  bootstrapSessionId: string;
  name: string;
  description?: string;
}

export interface InitializationConfigInput {
  repository?: RepositoryConfigInput;
  hosting?: HostingConfigInput;
  database?: DatabaseConfigInput;
  cache?: CacheConfigInput;
  storage?: StorageConfigInput;
  cicd?: CICDConfigInput;
  monitoring?: MonitoringConfigInput;
}

export interface RepositoryConfigInput {
  type: 'github' | 'gitlab' | 'bitbucket' | 'azure-devops';
  name: string;
  visibility: 'public' | 'private' | 'internal';
  defaultBranch?: string;
  enableBranchProtection?: boolean;
  enableCodeOwners?: boolean;
  templateRepository?: string;
}

export interface HostingConfigInput {
  provider: 'aws' | 'gcp' | 'azure' | 'vercel' | 'netlify' | 'railway';
  region: string;
  instanceType: string;
  autoScaling?: {
    enabled: boolean;
    minInstances: number;
    maxInstances: number;
    targetCPU: number;
  };
  customDomain?: string;
}

export interface DatabaseConfigInput {
  type: 'postgresql' | 'mysql' | 'mongodb' | 'redis' | 'dynamodb' | 'none';
  version?: string;
  instanceClass?: string;
  storageGB?: number;
  backupRetentionDays?: number;
  multiAZ?: boolean;
}

export interface CacheConfigInput {
  enabled: boolean;
  type?: 'redis' | 'memcached';
  nodeType?: string;
  clusterMode?: boolean;
}

export interface StorageConfigInput {
  type: 's3' | 'gcs' | 'azure-blob' | 'cloudflare-r2';
  bucketName: string;
  versioning?: boolean;
  encryption?: boolean;
  publicAccess?: boolean;
}

export interface CICDConfigInput {
  provider: 'github-actions' | 'gitlab-ci' | 'circleci' | 'jenkins' | 'azure-pipelines';
  buildCommand: string;
  testCommand: string;
  deployCommand: string;
  environments: string[];
  parallelJobs?: number;
}

export interface MonitoringConfigInput {
  enabled: boolean;
  provider?: 'datadog' | 'newrelic' | 'grafana' | 'cloudwatch' | 'prometheus';
  alertChannels?: Array<{
    type: 'email' | 'slack' | 'pagerduty' | 'webhook';
    target: string;
  }>;
  dashboards?: string[];
}

export interface CreateEnvironmentInput {
  name: string;
  type: 'development' | 'staging' | 'production' | 'preview';
  configuration: {
    instanceCount: number;
    instanceType: string;
    domain?: string;
    sslEnabled?: boolean;
  };
  cloneFrom?: string;
}

export interface UpdateEnvironmentInput {
  name?: string;
  configuration?: {
    instanceCount?: number;
    instanceType?: string;
    domain?: string;
    sslEnabled?: boolean;
  };
}

export interface InviteTeamMemberInput {
  email: string;
  name?: string;
  role: 'admin' | 'developer' | 'viewer';
  message?: string;
}

export type CloudProvider = 'aws' | 'gcp' | 'azure';
export type TeamRole = 'admin' | 'developer' | 'viewer';
export type LogLevel = 'debug' | 'info' | 'warn' | 'error';
