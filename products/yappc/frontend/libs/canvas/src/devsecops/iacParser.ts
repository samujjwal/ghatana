/**
 * Cloud Topology & IaC Import (Feature 2.24)
 * 
 * Provides Terraform plan parsing, cloud resource topology visualization,
 * drift detection, and secret handling for infrastructure-as-code imports.
 * 
 * Features:
 * - Terraform plan JSON parsing
 * - Cloud resource graph generation
 * - Drift detection and highlighting
 * - Secret handling via Vault integration
 * - Remediation suggestions
 * - Multi-cloud support (AWS, Azure, GCP, etc.)
 * 
 * @module devsecops/iacParser
 */

// ============================================================================
// Types & Interfaces
// ============================================================================

/**
 * Cloud provider types
 */
export type CloudProvider = 'aws' | 'azure' | 'gcp' | 'kubernetes' | 'other';

/**
 * Terraform resource change types
 */
export type ChangeType = 'create' | 'update' | 'delete' | 'replace' | 'no-op' | 'read';

/**
 * Drift status types
 */
export type DriftStatus = 'in-sync' | 'drifted' | 'missing' | 'extra' | 'unknown';

/**
 * Terraform resource definition
 */
export interface TerraformResource {
  address: string;              // e.g., "aws_instance.web"
  type: string;                 // e.g., "aws_instance"
  name: string;                 // e.g., "web"
  provider: CloudProvider;
  module?: string;              // Module path if in a module
  mode: 'managed' | 'data';
  values: Record<string, unknown>;
  dependencies: string[];       // Resource addresses this depends on
  change?: TerraformChange;
}

/**
 * Terraform resource change
 */
export interface TerraformChange {
  actions: ChangeType[];
  before?: Record<string, unknown>;
  after?: Record<string, unknown>;
  afterUnknown?: Record<string, boolean>;
  beforeSensitive?: Record<string, boolean>;
  afterSensitive?: Record<string, boolean>;
  replacePaths?: string[][];
}

/**
 * Terraform plan representation
 */
export interface TerraformPlan {
  formatVersion: string;
  terraformVersion: string;
  variables?: Record<string, unknown>;
  resources: TerraformResource[];
  outputs?: Record<string, unknown>;
  priorState?: {
    resources: TerraformResource[];
  };
}

/**
 * Drift detection result
 */
export interface DriftDetection {
  address: string;
  status: DriftStatus;
  differences: DriftDifference[];
  severity: 'info' | 'warning' | 'error' | 'critical';
  remediationUrl?: string;
}

/**
 * Individual drift difference
 */
export interface DriftDifference {
  path: string;               // JSON path to the difference
  expected: unknown;
  actual: unknown;
  description: string;
}

/**
 * Secret reference for Vault integration
 */
export interface SecretReference {
  path: string;               // Vault path
  key: string;                // Secret key
  field?: string;             // Specific field in secret
}

/**
 * IaC parser configuration
 */
export interface IaCParserConfig {
  vaultUrl?: string;
  vaultToken?: string;
  enableDriftDetection: boolean;
  enableSecretMasking: boolean;
  secretPattern?: RegExp;
  cloudProviderMapping: Record<string, CloudProvider>;
}

/**
 * Canvas node for IaC resource
 */
export interface IaCCanvasNode {
  id: string;
  label: string;
  type: string;
  data: {
    address: string;
    resourceType: string;
    provider: CloudProvider;
    changeType?: ChangeType;
    driftStatus?: DriftStatus;
    values: Record<string, unknown>;
    secrets?: SecretReference[];
    module?: string;
  };
  position: { x: number; y: number };
  style: {
    backgroundColor?: string;
    borderColor?: string;
    borderWidth?: number;
    borderStyle?: string;
    icon?: string;
  };
}

/**
 * Canvas edge for IaC dependency
 */
export interface IaCCanvasEdge {
  id: string;
  source: string;
  target: string;
  label?: string;
  data: {
    type: 'depends_on' | 'data_source' | 'implicit';
  };
  style: {
    stroke?: string;
    strokeWidth?: number;
    strokeDasharray?: string;
  };
}

/**
 * Canvas graph for IaC topology
 */
export interface IaCCanvasGraph {
  nodes: IaCCanvasNode[];
  edges: IaCCanvasEdge[];
  metadata: {
    provider?: CloudProvider;
    terraformVersion?: string;
    totalResources: number;
    changedResources: number;
    driftedResources: number;
  };
}

// ============================================================================
// Core Functions
// ============================================================================

/**
 * Parse Terraform plan JSON
 */
export function parseTerraformPlan(planJson: string | object): TerraformPlan {
  const plan = typeof planJson === 'string' ? JSON.parse(planJson) : planJson;
  
  const resources: TerraformResource[] = [];
  
  // Parse planned_values (resources to be created/updated)
  if (plan.planned_values?.root_module) {
    extractResources(plan.planned_values.root_module, resources, 'planned');
  }
  
  // Parse resource_changes for change information
  if (plan.resource_changes) {
    plan.resource_changes.forEach((rc: unknown) => {
      const existing = resources.find(r => r.address === rc.address);
      if (existing) {
        existing.change = {
          actions: rc.change.actions,
          before: rc.change.before,
          after: rc.change.after,
          afterUnknown: rc.change.after_unknown,
          beforeSensitive: rc.change.before_sensitive,
          afterSensitive: rc.change.after_sensitive,
          replacePaths: rc.change.replace_paths
        };
      } else {
        // Resource being deleted
        resources.push({
          address: rc.address,
          type: rc.type,
          name: rc.name,
          provider: inferProvider(rc.type),
          mode: rc.mode,
          values: rc.change.before || {},
          dependencies: [],
          change: {
            actions: rc.change.actions,
            before: rc.change.before,
            after: rc.change.after
          }
        });
      }
    });
  }
  
  return {
    formatVersion: plan.format_version,
    terraformVersion: plan.terraform_version,
    variables: plan.variables,
    resources,
    outputs: plan.outputs,
    priorState: plan.prior_state ? {
      resources: extractStateResources(plan.prior_state)
    } : undefined
  };
}

/**
 * Detect drift between planned and actual state
 */
export function detectDrift(
  plan: TerraformPlan,
  actualState?: TerraformPlan
): DriftDetection[] {
  const drifts: DriftDetection[] = [];
  
  if (!actualState || !plan.priorState) {
    return drifts;
  }
  
  const actualResources = new Map(
    actualState.resources.map(r => [r.address, r])
  );
  
  const plannedResources = new Map(
    plan.resources.map(r => [r.address, r])
  );
  
  const priorResources = new Map(
    plan.priorState.resources.map(r => [r.address, r])
  );
  
  // Check for resources in prior state
  priorResources.forEach((priorResource, address) => {
    const actual = actualResources.get(address);
    const planned = plannedResources.get(address);
    
    if (!actual) {
      // Resource missing in actual state
      drifts.push({
        address,
        status: 'missing',
        differences: [{
          path: '$',
          expected: priorResource.values,
          actual: null,
          description: 'Resource exists in plan but missing in actual state'
        }],
        severity: 'error',
        remediationUrl: generateRemediationUrl(priorResource, 'missing')
      });
    } else {
      // Check for drift in existing resources
      const differences = findDifferences(
        priorResource.values,
        actual.values,
        ''
      );
      
      if (differences.length > 0 && planned?.change?.actions.includes('update')) {
        drifts.push({
          address,
          status: 'drifted',
          differences,
          severity: determineSeverity(differences),
          remediationUrl: generateRemediationUrl(priorResource, 'drifted')
        });
      }
    }
  });
  
  // Check for extra resources in actual state
  actualResources.forEach((actual, address) => {
    if (!priorResources.has(address) && !plannedResources.has(address)) {
      drifts.push({
        address,
        status: 'extra',
        differences: [{
          path: '$',
          expected: null,
          actual: actual.values,
          description: 'Resource exists in actual state but not in plan'
        }],
        severity: 'warning',
        remediationUrl: generateRemediationUrl(actual, 'extra')
      });
    }
  });
  
  return drifts;
}

/**
 * Convert Terraform plan to canvas graph
 */
export function convertTerraformToCanvas(
  plan: TerraformPlan,
  drifts?: DriftDetection[]
): IaCCanvasGraph {
  const nodes: IaCCanvasNode[] = [];
  const edges: IaCCanvasEdge[] = [];
  
  const driftMap = new Map(drifts?.map(d => [d.address, d]));
  
  // Create nodes for each resource
  plan.resources.forEach((resource, index) => {
    const drift = driftMap.get(resource.address);
    const changeType = resource.change?.actions[0];
    
    nodes.push({
      id: resource.address,
      label: `${resource.type}.${resource.name}`,
      type: resource.type,
      data: {
        address: resource.address,
        resourceType: resource.type,
        provider: resource.provider,
        changeType,
        driftStatus: drift?.status,
        values: maskSecrets(resource.values),
        module: resource.module
      },
      position: calculatePosition(index, plan.resources.length),
      style: getResourceStyle(resource, changeType, drift?.status)
    });
    
    // Create edges for dependencies
    resource.dependencies.forEach(depAddress => {
      edges.push({
        id: `${resource.address}-${depAddress}`,
        source: depAddress,
        target: resource.address,
        label: 'depends on',
        data: {
          type: 'depends_on'
        },
        style: {
          stroke: '#666666',
          strokeWidth: 2,
          strokeDasharray: undefined
        }
      });
    });
  });
  
  const changedResources = plan.resources.filter(r => 
    r.change && !r.change.actions.includes('no-op')
  ).length;
  
  const driftedResources = drifts?.filter(d => 
    d.status === 'drifted'
  ).length || 0;
  
  return {
    nodes,
    edges,
    metadata: {
      terraformVersion: plan.terraformVersion,
      totalResources: plan.resources.length,
      changedResources,
      driftedResources
    }
  };
}

/**
 * Mask sensitive values in resource data
 */
export function maskSecrets(
  values: Record<string, unknown>,
  secretPattern: RegExp = /password|secret|token|key|credential/i
): Record<string, unknown> {
  const masked: Record<string, unknown> = {};
  
  Object.entries(values).forEach(([key, value]) => {
    if (secretPattern.test(key)) {
      masked[key] = '***MASKED***';
    } else if (Array.isArray(value)) {
      masked[key] = value;
    } else if (typeof value === 'object' && value !== null) {
      masked[key] = maskSecrets(value, secretPattern);
    } else {
      masked[key] = value;
    }
  });
  
  return masked;
}

/**
 * Extract secrets for Vault retrieval
 */
export function extractSecretReferences(
  values: Record<string, unknown>,
  secretPattern: RegExp = /password|secret|token|key|credential/i
): SecretReference[] {
  const secrets: SecretReference[] = [];
  
  Object.entries(values).forEach(([key, value]) => {
    if (secretPattern.test(key) && typeof value === 'string') {
      // Check if value is a Vault reference
      const vaultMatch = value.match(/vault:\/\/([^#]+)#(.+)/);
      if (vaultMatch) {
        secrets.push({
          path: vaultMatch[1],
          key: vaultMatch[2],
          field: key
        });
      }
    } else if (typeof value === 'object' && value !== null) {
      secrets.push(...extractSecretReferences(value, secretPattern));
    }
  });
  
  return secrets;
}

/**
 * Generate remediation suggestions
 */
export function generateRemediationSuggestions(
  drift: DriftDetection
): string[] {
  const suggestions: string[] = [];
  
  switch (drift.status) {
    case 'missing':
      suggestions.push(`Run 'terraform apply' to create the missing resource: ${drift.address}`);
      suggestions.push('Verify the resource was not manually deleted');
      break;
      
    case 'drifted':
      suggestions.push(`Run 'terraform apply' to update the drifted resource: ${drift.address}`);
      suggestions.push('Review the differences to understand what changed');
      drift.differences.forEach(diff => {
        suggestions.push(`  - ${diff.path}: expected ${JSON.stringify(diff.expected)}, got ${JSON.stringify(diff.actual)}`);
      });
      break;
      
    case 'extra':
      suggestions.push(`Resource ${drift.address} exists but is not managed by Terraform`);
      suggestions.push('Consider importing it with: terraform import');
      suggestions.push('Or manually delete it if it\'s no longer needed');
      break;
      
    default:
      suggestions.push('No specific remediation available');
  }
  
  return suggestions;
}

/**
 * Group resources by cloud provider
 */
export function groupResourcesByProvider(
  resources: TerraformResource[]
): Map<CloudProvider, TerraformResource[]> {
  const groups = new Map<CloudProvider, TerraformResource[]>();
  
  resources.forEach(resource => {
    const existing = groups.get(resource.provider) || [];
    existing.push(resource);
    groups.set(resource.provider, existing);
  });
  
  return groups;
}

/**
 * Calculate resource dependencies
 */
export function calculateDependencyDepth(
  resources: TerraformResource[]
): Map<string, number> {
  const depths = new Map<string, number>();
  const inProgress = new Set<string>();
  
  /**
   *
   */
  function calculateDepth(address: string): number {
    // Return cached depth if already calculated
    if (depths.has(address)) {
      return depths.get(address)!;
    }
    
    // Detect circular dependency
    if (inProgress.has(address)) {
      return 0;
    }
    
    inProgress.add(address);
    const resource = resources.find(r => r.address === address);
    
    if (!resource || resource.dependencies.length === 0) {
      depths.set(address, 0);
      inProgress.delete(address);
      return 0;
    }
    
    // Calculate depth based on maximum dependency depth + 1
    let maxDepth = 0;
    resource.dependencies.forEach(depAddress => {
      const depDepth = calculateDepth(depAddress);
      maxDepth = Math.max(maxDepth, depDepth + 1);
    });
    
    depths.set(address, maxDepth);
    inProgress.delete(address);
    return maxDepth;
  }
  
  resources.forEach(resource => {
    if (!depths.has(resource.address)) {
      calculateDepth(resource.address);
    }
  });
  
  return depths;
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Extract resources from Terraform module
 */
function extractResources(
  module: unknown,
  resources: TerraformResource[],
  mode: 'planned' | 'state',
  modulePath?: string
): void {
  if (module.resources) {
    module.resources.forEach((resource: unknown) => {
      resources.push({
        address: resource.address,
        type: resource.type,
        name: resource.name,
        provider: inferProvider(resource.type),
        module: modulePath,
        mode: resource.mode,
        values: resource.values || {},
        dependencies: resource.depends_on || []
      });
    });
  }
  
  if (module.child_modules) {
    module.child_modules.forEach((child: unknown) => {
      const childPath = modulePath 
        ? `${modulePath}.${child.address}` 
        : child.address;
      extractResources(child, resources, mode, childPath);
    });
  }
}

/**
 * Extract resources from state
 */
function extractStateResources(state: unknown): TerraformResource[] {
  const resources: TerraformResource[] = [];
  
  if (state.values?.root_module) {
    extractResources(state.values.root_module, resources, 'state');
  }
  
  return resources;
}

/**
 * Infer cloud provider from resource type
 */
function inferProvider(resourceType: string): CloudProvider {
  if (resourceType.startsWith('aws_')) return 'aws';
  if (resourceType.startsWith('azurerm_')) return 'azure';
  if (resourceType.startsWith('google_')) return 'gcp';
  if (resourceType.startsWith('kubernetes_')) return 'kubernetes';
  return 'other';
}

/**
 * Find differences between two objects
 */
function findDifferences(
  expected: unknown,
  actual: unknown,
  path: string
): DriftDifference[] {
  const differences: DriftDifference[] = [];
  
  if (typeof expected !== typeof actual) {
    differences.push({
      path,
      expected,
      actual,
      description: `Type mismatch at ${path}`
    });
    return differences;
  }
  
  if (typeof expected === 'object' && expected !== null && !Array.isArray(expected)) {
    // Check for missing or changed fields in expected
    Object.keys(expected).forEach(key => {
      const newPath = path ? `${path}.${key}` : key;
      if (!(key in actual)) {
        differences.push({
          path: newPath,
          expected: expected[key],
          actual: undefined,
          description: `Missing field at ${newPath}`
        });
      } else if (typeof expected[key] === 'object' && expected[key] !== null && !Array.isArray(expected[key])) {
        differences.push(...findDifferences(expected[key], actual[key], newPath));
      } else if (expected[key] !== actual[key]) {
        differences.push({
          path: newPath,
          expected: expected[key],
          actual: actual[key],
          description: `Value mismatch at ${newPath}`
        });
      }
    });
    
    // Check for extra fields in actual (additions)
    Object.keys(actual).forEach(key => {
      const newPath = path ? `${path}.${key}` : key;
      if (!(key in expected)) {
        differences.push({
          path: newPath,
          expected: undefined,
          actual: actual[key],
          description: `Extra field at ${newPath}`
        });
      }
    });
  } else if (expected !== actual) {
    differences.push({
      path,
      expected,
      actual,
      description: `Value mismatch at ${path}`
    });
  }
  
  return differences;
}

/**
 * Determine severity from differences
 */
function determineSeverity(differences: DriftDifference[]): 'info' | 'warning' | 'error' | 'critical' {
  const criticalFields = ['security_groups', 'iam_policy', 'encryption'];
  const warningFields = ['tags', 'description'];
  
  for (const diff of differences) {
    if (criticalFields.some(field => diff.path.includes(field))) {
      return 'critical';
    }
  }
  
  for (const diff of differences) {
    if (!warningFields.some(field => diff.path.includes(field))) {
      return 'error';
    }
  }
  
  return 'warning';
}

/**
 * Generate remediation documentation URL
 */
function generateRemediationUrl(resource: TerraformResource, status: string): string {
  const baseUrls: Record<CloudProvider, string> = {
    aws: 'https://registry.terraform.io/providers/hashicorp/aws/latest/docs',
    azure: 'https://registry.terraform.io/providers/hashicorp/azurerm/latest/docs',
    gcp: 'https://registry.terraform.io/providers/hashicorp/google/latest/docs',
    kubernetes: 'https://registry.terraform.io/providers/hashicorp/kubernetes/latest/docs',
    other: 'https://registry.terraform.io'
  };
  
  const baseUrl = baseUrls[resource.provider];
  return `${baseUrl}/resources/${resource.type}`;
}

/**
 * Calculate position for resource in canvas
 */
function calculatePosition(index: number, total: number): { x: number; y: number } {
  const columns = Math.ceil(Math.sqrt(total));
  const row = Math.floor(index / columns);
  const col = index % columns;
  
  return {
    x: col * 300,
    y: row * 200
  };
}

/**
 * Get resource style based on change type and drift status
 */
function getResourceStyle(
  resource: TerraformResource,
  changeType?: ChangeType,
  driftStatus?: DriftStatus
): IaCCanvasNode['style'] {
  const style: IaCCanvasNode['style'] = {
    backgroundColor: '#ffffff',
    borderColor: '#cccccc',
    borderWidth: 2,
    borderStyle: 'solid'
  };
  
  // Color by change type
  if (changeType) {
    switch (changeType) {
      case 'create':
        style.borderColor = '#00AA00';
        style.backgroundColor = '#E8F5E9';
        break;
      case 'update':
        style.borderColor = '#FF9800';
        style.backgroundColor = '#FFF3E0';
        break;
      case 'delete':
        style.borderColor = '#F44336';
        style.backgroundColor = '#FFEBEE';
        break;
      case 'replace':
        style.borderColor = '#9C27B0';
        style.backgroundColor = '#F3E5F5';
        break;
    }
  }
  
  // Highlight drift
  if (driftStatus && driftStatus !== 'in-sync') {
    style.borderWidth = 4;
    style.borderStyle = 'dashed';
    
    switch (driftStatus) {
      case 'drifted':
        style.borderColor = '#FF5722';
        break;
      case 'missing':
        style.borderColor = '#F44336';
        break;
      case 'extra':
        style.borderColor = '#FFC107';
        break;
    }
  }
  
  // Set icon by provider
  const providerIcons: Record<CloudProvider, string> = {
    aws: '☁️',
    azure: '🔷',
    gcp: '🌐',
    kubernetes: '☸️',
    other: '📦'
  };
  style.icon = providerIcons[resource.provider];
  
  return style;
}

/**
 * Create default IaC parser configuration
 */
export function createIaCParserConfig(overrides?: Partial<IaCParserConfig>): IaCParserConfig {
  return {
    enableDriftDetection: true,
    enableSecretMasking: true,
    secretPattern: /password|secret|token|key|credential/i,
    cloudProviderMapping: {
      'aws_': 'aws',
      'azurerm_': 'azure',
      'google_': 'gcp',
      'kubernetes_': 'kubernetes'
    },
    ...overrides
  };
}
