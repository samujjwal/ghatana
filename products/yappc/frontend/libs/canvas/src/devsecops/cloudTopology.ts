/**
 * Cloud Topology & Infrastructure as Code (IaC) Import
 * 
 * Parses Terraform and CloudFormation configurations to visualize
 * cloud infrastructure topology, detect drift, and estimate costs.
 * 
 * @module devsecops/cloudTopology
 */

import type { CanvasDocument, CanvasNode, CanvasEdge } from '../types/canvas-document';

/**
 * Supported IaC platforms
 */
export type IaCPlatform = 'terraform' | 'cloudformation' | 'pulumi' | 'cdk';

/**
 * Cloud providers
 */
export type CloudProvider = 'aws' | 'azure' | 'gcp' | 'kubernetes' | 'other';

/**
 * Resource types
 */
export type ResourceType =
  | 'compute'
  | 'storage'
  | 'network'
  | 'database'
  | 'security'
  | 'monitoring'
  | 'container'
  | 'serverless'
  | 'other';

/**
 * Drift status
 */
export type DriftStatus = 'in-sync' | 'drifted' | 'unknown' | 'missing' | 'extra';

/**
 * Cloud resource definition
 */
export interface CloudResource {
  id: string;
  name: string;
  type: ResourceType;
  provider: CloudProvider;
  resourceType: string; // e.g., "aws_instance", "azurerm_virtual_machine"
  dependsOn: string[];
  properties: Record<string, unknown>;
  tags?: Record<string, string>;
  metadata: {
    region?: string;
    zone?: string;
    cost?: CostEstimate;
    drift?: DriftInfo;
    [key: string]: unknown;
  };
}

/**
 * Cost estimation
 */
export interface CostEstimate {
  monthly: number;
  hourly: number;
  currency: string;
  breakdown?: Array<{
    category: string;
    amount: number;
  }>;
}

/**
 * Drift detection information
 */
export interface DriftInfo {
  status: DriftStatus;
  detectedAt: Date;
  changes?: Array<{
    property: string;
    expected: unknown;
    actual: unknown;
  }>;
}

/**
 * Cloud topology (parsed from IaC)
 */
export interface CloudTopology {
  platform: IaCPlatform;
  provider: CloudProvider;
  name: string;
  resources: CloudResource[];
  modules: Module[];
  outputs: Output[];
  variables: Variable[];
  metadata: {
    version?: string;
    description?: string;
    tags?: Record<string, string>;
    totalCost?: CostEstimate;
    [key: string]: unknown;
  };
}

/**
 * IaC module/stack
 */
export interface Module {
  id: string;
  name: string;
  source: string;
  resources: string[]; // resource IDs
  variables?: Record<string, unknown>;
}

/**
 * Output variable
 */
export interface Output {
  name: string;
  value?: unknown;
  description?: string;
  sensitive?: boolean;
}

/**
 * Input variable
 */
export interface Variable {
  name: string;
  type?: string;
  defaultValue?: unknown;
  description?: string;
  required?: boolean;
}

/**
 * Topology visualization configuration
 */
export interface TopologyConfig {
  layout?: 'hierarchical' | 'radial' | 'force-directed';
  groupBy?: 'provider' | 'type' | 'module' | 'region';
  showCosts?: boolean;
  showDrift?: boolean;
  showDependencies?: boolean;
  resourceSpacing?: { x: number; y: number };
  groupSpacing?: number;
}

/**
 * Create default topology configuration
 */
export function createTopologyConfig(
  overrides?: Partial<TopologyConfig>
): TopologyConfig {
  return {
    layout: 'hierarchical',
    groupBy: 'module',
    showCosts: true,
    showDrift: true,
    showDependencies: true,
    resourceSpacing: { x: 200, y: 150 },
    groupSpacing: 300,
    ...overrides,
  };
}

/**
 * Parse Terraform configuration
 * 
 * @param hcl - Terraform HCL configuration string
 * @returns Parsed cloud topology
 * 
 * @example
 * ```typescript
 * const topology = parseTerraform(tfConfig);
 * console.log(topology.resources.length);
 * ```
 */
export function parseTerraform(hcl: string): CloudTopology {
  const topology: CloudTopology = {
    platform: 'terraform',
    provider: 'aws', // Default, will be detected
    name: 'Terraform Configuration',
    resources: [],
    modules: [],
    outputs: [],
    variables: [],
    metadata: {},
  };

  const lines = hcl.split('\n');
  let currentResource: CloudResource | null = null;
  let currentModule: Module | null = null;
  let currentOutput: Output | null = null;
  let currentVariable: Variable | null = null;
  let braceDepth = 0;
  let inResource = false;
  let inModule = false;
  let inOutput = false;
  let inVariable = false;
  let providerDetected = false;
  let inTags = false;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();

    // Skip comments and empty lines
    if (line.startsWith('#') || line.startsWith('//') || !line) {
      continue;
    }

    // Track brace depth
    const openBraces = (line.match(/{/g) || []).length;
    const closeBraces = (line.match(/}/g) || []).length;
    braceDepth += openBraces - closeBraces;

    // Detect provider
    if (!providerDetected && line.startsWith('provider')) {
      const providerMatch = line.match(/provider\s+"([^"]+)"/);
      if (providerMatch) {
        const providerName = providerMatch[1];
        topology.provider = mapProviderName(providerName);
        providerDetected = true;
      }
    }

    // Parse resource blocks
    if (line.startsWith('resource')) {
      const match = line.match(/resource\s+"([^"]+)"\s+"([^"]+)"/);
      if (match) {
        const [, resourceType, resourceName] = match;
        const provider = detectProviderFromResourceType(resourceType);

        currentResource = {
          id: `${resourceType}.${resourceName}`,
          name: resourceName,
          type: categorizeResourceType(resourceType),
          provider,
          resourceType,
          dependsOn: [],
          properties: {},
          metadata: {},
        };
        inResource = true;
        topology.resources.push(currentResource);
      }
    }

    // Parse module blocks
    else if (line.startsWith('module')) {
      const match = line.match(/module\s+"([^"]+)"/);
      if (match) {
        currentModule = {
          id: `module.${match[1]}`,
          name: match[1],
          source: '',
          resources: [],
        };
        inModule = true;
        topology.modules.push(currentModule);
      }
    }

    // Parse output blocks
    else if (line.startsWith('output')) {
      const match = line.match(/output\s+"([^"]+)"/);
      if (match) {
        currentOutput = {
          name: match[1],
        };
        inOutput = true;
        topology.outputs.push(currentOutput);
      }
    }

    // Parse variable blocks
    else if (line.startsWith('variable')) {
      const match = line.match(/variable\s+"([^"]+)"/);
      if (match) {
        currentVariable = {
          name: match[1],
        };
        inVariable = true;
        topology.variables.push(currentVariable);
      }
    }

    // Parse resource properties
    else if (inResource && currentResource && braceDepth > 0) {
      // Parse depends_on
      if (line.includes('depends_on')) {
        const depsMatch = line.match(/depends_on\s*=\s*\[(.*?)\]/);
        if (depsMatch) {
          const deps = depsMatch[1]
            .split(',')
            .map(d => d.trim().replace(/["\s]/g, ''));
          currentResource.dependsOn = deps;
        }
      }
      // Parse tags
      else if (line.includes('tags') && line.includes('=')) {
        if (line.includes('{')) {
          currentResource.tags = {};
          inTags = true;
          // Check if tags close on same line
          if (line.includes('}')) {
            inTags = false;
            // Parse single-line tags
            const tagMatch = line.match(/tags\s*=\s*{([^}]*)}/);
            if (tagMatch) {
              const tagPairs = tagMatch[1].split(',');
              tagPairs.forEach(pair => {
                const [key, value] = pair.split('=').map(s => s.trim().replace(/"/g, ''));
                if (key && value) {
                  currentResource!.tags![key] = value;
                }
              });
            }
          }
        }
      }
      // Parse multi-line tag entries
      else if (inTags && currentResource && line.includes('=')) {
        const tagMatch = line.match(/([A-Za-z_]+)\s*=\s*"([^"]*)"/);
        if (tagMatch) {
          const [, key, value] = tagMatch;
          currentResource.tags![key] = value;
        }
        // Check for closing brace
        if (line.includes('}')) {
          inTags = false;
        }
      }
      // Parse simple key = value properties
      else if (line.includes('=') && !line.includes('{') && !line.includes('[')) {
        const propMatch = line.match(/([a-z_]+)\s*=\s*(.+)/);
        if (propMatch) {
          const [, key, value] = propMatch;
          currentResource.properties[key] = value.replace(/"/g, '').trim();
        }
      }
    }

    // Parse module properties
    else if (inModule && currentModule && braceDepth > 0) {
      if (line.includes('source')) {
        const sourceMatch = line.match(/source\s*=\s*"([^"]+)"/);
        if (sourceMatch) {
          currentModule.source = sourceMatch[1];
        }
      }
    }

    // Reset context when closing block
    if (braceDepth === 0) {
      if (inResource) {
        inResource = false;
        currentResource = null;
      }
      if (inModule) {
        inModule = false;
        currentModule = null;
      }
      if (inOutput) {
        inOutput = false;
        currentOutput = null;
      }
      if (inVariable) {
        inVariable = false;
        currentVariable = null;
      }
    }
  }

  // Add default module if no modules defined
  if (topology.modules.length === 0) {
    topology.modules.push({
      id: 'module.default',
      name: 'default',
      source: 'local',
      resources: topology.resources.map(r => r.id),
    });
  }

  return topology;
}

/**
 * Parse CloudFormation template
 * 
 * @param template - CloudFormation template (JSON or YAML string)
 * @returns Parsed cloud topology
 * 
 * @example
 * ```typescript
 * const topology = parseCloudFormation(cfnTemplate);
 * ```
 */
export function parseCloudFormation(template: string): CloudTopology {
  const topology: CloudTopology = {
    platform: 'cloudformation',
    provider: 'aws',
    name: 'CloudFormation Stack',
    resources: [],
    modules: [],
    outputs: [],
    variables: [],
    metadata: {},
  };

  try {
    // Try parsing as JSON first
    const cfn = JSON.parse(template);
    return parseCloudFormationObject(cfn, topology);
  } catch {
    // If JSON fails, try YAML (simplified parsing)
    return parseCloudFormationYAML(template, topology);
  }
}

/**
 * Parse CloudFormation JSON object
 */
function parseCloudFormationObject(
  cfn: unknown,
  topology: CloudTopology
): CloudTopology {
  // Parse metadata
  if (cfn.Description) {
    topology.metadata.description = cfn.Description;
  }
  if (cfn.AWSTemplateFormatVersion) {
    topology.metadata.version = cfn.AWSTemplateFormatVersion;
  }

  // Parse resources
  if (cfn.Resources) {
    Object.entries(cfn.Resources).forEach(([logicalId, resource]: [string, any]) => {
      const resourceType = resource.Type || '';
      const cloudResource: CloudResource = {
        id: logicalId,
        name: logicalId,
        type: categorizeAWSResourceType(resourceType),
        provider: 'aws',
        resourceType,
        dependsOn: Array.isArray(resource.DependsOn)
          ? resource.DependsOn
          : resource.DependsOn
          ? [resource.DependsOn]
          : [],
        properties: resource.Properties || {},
        tags: extractCFNTags(resource.Properties?.Tags),
        metadata: {},
      };

      topology.resources.push(cloudResource);
    });
  }

  // Parse outputs
  if (cfn.Outputs) {
    Object.entries(cfn.Outputs).forEach(([name, output]: [string, any]) => {
      topology.outputs.push({
        name,
        value: output.Value,
        description: output.Description,
      });
    });
  }

  // Parse parameters (variables)
  if (cfn.Parameters) {
    Object.entries(cfn.Parameters).forEach(([name, param]: [string, any]) => {
      topology.variables.push({
        name,
        type: param.Type,
        defaultValue: param.Default,
        description: param.Description,
      });
    });
  }

  // Add default module
  topology.modules.push({
    id: 'module.cfn-stack',
    name: 'cfn-stack',
    source: 'cloudformation',
    resources: topology.resources.map(r => r.id),
  });

  return topology;
}

/**
 * Parse CloudFormation YAML (simplified)
 */
function parseCloudFormationYAML(
  yaml: string,
  topology: CloudTopology
): CloudTopology {
  const lines = yaml.split('\n');
  let currentResource: CloudResource | null = null;
  let inResources = false;
  let indentLevel = 0;

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;

    const currentIndent = line.search(/\S/);

    if (trimmed === 'Resources:') {
      inResources = true;
      indentLevel = currentIndent;
      continue;
    }

    if (inResources && currentIndent === indentLevel + 2) {
      // Resource logical ID
      const match = trimmed.match(/^([A-Za-z0-9]+):/);
      if (match) {
        currentResource = {
          id: match[1],
          name: match[1],
          type: 'other',
          provider: 'aws',
          resourceType: '',
          dependsOn: [],
          properties: {},
          metadata: {},
        };
        topology.resources.push(currentResource);
      }
    }

    if (currentResource && currentIndent > indentLevel + 2) {
      if (trimmed.startsWith('Type:')) {
        const typeMatch = trimmed.match(/Type:\s*(.+)/);
        if (typeMatch) {
          currentResource.resourceType = typeMatch[1];
          currentResource.type = categorizeAWSResourceType(typeMatch[1]);
        }
      }
    }
  }

  // Add default module
  topology.modules.push({
    id: 'module.cfn-stack',
    name: 'cfn-stack',
    source: 'cloudformation',
    resources: topology.resources.map(r => r.id),
  });

  return topology;
}

/**
 * Convert cloud topology to canvas document
 * 
 * @param topology - Cloud topology
 * @param config - Visualization configuration
 * @returns Canvas document
 */
export function topologyToCanvas(
  topology: CloudTopology,
  config: TopologyConfig = createTopologyConfig()
): CanvasDocument {
  const elements: Record<string, CanvasNode | CanvasEdge> = {};
  const elementOrder: string[] = [];

  // Layout constants
  const NODE_WIDTH = 180;
  const NODE_HEIGHT = 100;
  const HORIZONTAL_GAP = config.resourceSpacing?.x || 200;
  const VERTICAL_GAP = config.resourceSpacing?.y || 150;

  // Group resources
  const groups = groupResources(topology, config.groupBy || 'module');

  let groupY = 50;

  groups.forEach((resourceIds, groupName) => {
    let currentX = 50;
    let currentY = groupY;
    let maxHeightInRow = 0;

    resourceIds.forEach((resourceId, index) => {
      const resource = topology.resources.find(r => r.id === resourceId);
      if (!resource) return;

      // Wrap to next row after 4 resources
      if (index > 0 && index % 4 === 0) {
        currentX = 50;
        currentY += maxHeightInRow + VERTICAL_GAP;
        maxHeightInRow = 0;
      }

      const node: CanvasNode = {
        id: resource.id,
        type: 'node',
        nodeType: 'cloud-resource',
        transform: {
          position: { x: currentX, y: currentY },
          scale: 1,
          rotation: 0,
        },
        bounds: {
          x: currentX,
          y: currentY,
          width: NODE_WIDTH,
          height: NODE_HEIGHT,
        },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 1,
        metadata: {
          resourceName: resource.name,
          resourceType: resource.resourceType,
          provider: resource.provider,
          cost: resource.metadata.cost,
          drift: resource.metadata.drift,
          group: groupName,
        },
        version: '1.0.0',
        createdAt: new Date(),
        updatedAt: new Date(),
        data: {
          label: resource.name,
          type: resource.type,
          provider: resource.provider,
          cost: resource.metadata.cost
            ? `$${resource.metadata.cost.monthly}/mo`
            : undefined,
          drift: resource.metadata.drift?.status,
        },
        inputs: [],
        outputs: resource.dependsOn,
        style: getResourceStyle(resource),
      };

      elements[resource.id] = node;
      elementOrder.push(resource.id);

      currentX += NODE_WIDTH + HORIZONTAL_GAP;
      maxHeightInRow = Math.max(maxHeightInRow, NODE_HEIGHT);
    });

    groupY = currentY + maxHeightInRow + (config.groupSpacing || 300);
  });

  // Create edges for dependencies
  if (config.showDependencies) {
    topology.resources.forEach(resource => {
      resource.dependsOn.forEach(depId => {
        const edgeId = `edge-${depId}-${resource.id}`;
        const edge: CanvasEdge = {
          id: edgeId,
          type: 'edge',
          sourceId: depId,
          targetId: resource.id,
          path: [],
          transform: {
            position: { x: 0, y: 0 },
            scale: 1,
            rotation: 0,
          },
          bounds: { x: 0, y: 0, width: 0, height: 0 },
          visible: true,
          locked: false,
          selected: false,
          zIndex: 0,
          metadata: {},
          version: '1.0.0',
          createdAt: new Date(),
          updatedAt: new Date(),
          style: {
            stroke: '#94a3b8',
            strokeWidth: 2,
            strokeDasharray: '5,5',
          },
        };
        elements[edgeId] = edge;
        elementOrder.push(edgeId);
      });
    });
  }

  return {
    id: `topology-${topology.name}`,
    version: '1.0.0',
    title: `Cloud Topology: ${topology.name}`,
    description: `${topology.platform} topology for ${topology.provider}`,
    viewport: {
      center: { x: 400, y: 300 },
      zoom: 1,
    },
    elements,
    elementOrder,
    metadata: {
      platform: topology.platform,
      provider: topology.provider,
      resourceCount: topology.resources.length,
      moduleCount: topology.modules.length,
      totalCost: topology.metadata.totalCost,
    },
    capabilities: {
      canEdit: false,
      canZoom: true,
      canPan: true,
      canSelect: true,
      canUndo: false,
      canRedo: false,
      canExport: true,
      canImport: false,
      canCollaborate: false,
      canPersist: true,
      allowedElementTypes: ['node', 'edge'],
    },
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

/**
 * Detect drift between IaC and actual infrastructure
 * 
 * @param topology - Cloud topology
 * @param actualState - Actual infrastructure state (from cloud provider)
 * @returns Topology with drift information
 */
export function detectDrift(
  topology: CloudTopology,
  actualState: Record<string, unknown>
): CloudTopology {
  const updatedTopology = { ...topology };

  updatedTopology.resources = topology.resources.map(resource => {
    const actual = actualState[resource.id];

    if (!actual) {
      return {
        ...resource,
        metadata: {
          ...resource.metadata,
          drift: {
            status: 'missing' as DriftStatus,
            detectedAt: new Date(),
          },
        },
      };
    }

    const changes: Array<{ property: string; expected: unknown; actual: unknown }> = [];

    // Compare properties
    Object.keys(resource.properties).forEach(key => {
      if (resource.properties[key] !== actual[key]) {
        changes.push({
          property: key,
          expected: resource.properties[key],
          actual: actual[key],
        });
      }
    });

    return {
      ...resource,
      metadata: {
        ...resource.metadata,
        drift: {
          status: changes.length > 0 ? 'drifted' : 'in-sync',
          detectedAt: new Date(),
          changes: changes.length > 0 ? changes : undefined,
        },
      },
    };
  });

  return updatedTopology;
}

/**
 * Estimate costs for cloud resources
 * 
 * @param topology - Cloud topology
 * @param pricing - Pricing data (from cloud provider APIs)
 * @returns Topology with cost estimates
 */
export function estimateCosts(
  topology: CloudTopology,
  pricing: Record<string, CostEstimate>
): CloudTopology {
  const updatedTopology = { ...topology };

  updatedTopology.resources = topology.resources.map(resource => {
    const cost = pricing[resource.resourceType] || pricing[resource.type];

    return {
      ...resource,
      metadata: {
        ...resource.metadata,
        cost: cost || { monthly: 0, hourly: 0, currency: 'USD' },
      },
    };
  });

  // Calculate total cost
  const totalMonthly = updatedTopology.resources.reduce(
    (sum, r) => sum + (r.metadata.cost?.monthly || 0),
    0
  );

  updatedTopology.metadata.totalCost = {
    monthly: totalMonthly,
    hourly: totalMonthly / 730, // Approximate hours per month
    currency: 'USD',
  };

  return updatedTopology;
}

// Helper functions

/**
 *
 */
function mapProviderName(name: string): CloudProvider {
  if (name.includes('aws')) return 'aws';
  if (name.includes('azure') || name.includes('azurerm')) return 'azure';
  if (name.includes('google') || name.includes('gcp')) return 'gcp';
  if (name.includes('kubernetes')) return 'kubernetes';
  return 'other';
}

/**
 *
 */
function detectProviderFromResourceType(resourceType: string): CloudProvider {
  if (resourceType.startsWith('aws_')) return 'aws';
  if (resourceType.startsWith('azurerm_')) return 'azure';
  if (resourceType.startsWith('google_')) return 'gcp';
  if (resourceType.startsWith('kubernetes_')) return 'kubernetes';
  return 'other';
}

/**
 *
 */
function categorizeResourceType(resourceType: string): ResourceType {
  const type = resourceType.toLowerCase();

  if (
    type.includes('instance') ||
    type.includes('vm') ||
    type.includes('compute')
  )
    return 'compute';
  if (
    type.includes('bucket') ||
    type.includes('storage') ||
    type.includes('disk')
  )
    return 'storage';
  if (
    type.includes('vpc') ||
    type.includes('network') ||
    type.includes('subnet') ||
    type.includes('route')
  )
    return 'network';
  if (type.includes('db') || type.includes('database') || type.includes('rds'))
    return 'database';
  if (
    type.includes('security') ||
    type.includes('iam') ||
    type.includes('policy')
  )
    return 'security';
  if (type.includes('cloudwatch') || type.includes('monitor'))
    return 'monitoring';
  if (
    type.includes('container') ||
    type.includes('ecs') ||
    type.includes('aks')
  )
    return 'container';
  if (type.includes('lambda') || type.includes('function'))
    return 'serverless';

  return 'other';
}

/**
 *
 */
function categorizeAWSResourceType(resourceType: string): ResourceType {
  const type = resourceType.toLowerCase();

  // Check specific types first before generic patterns
  if (type.includes('rds') || type.includes('dynamodb') || type.includes('dbinstance')) return 'database';
  if (type.includes('lambda')) return 'serverless';
  if (type.includes('ecs') || type.includes('eks')) return 'container';
  if (type.includes('cloudwatch')) return 'monitoring';
  if (type.includes('iam') || type.includes('securitygroup')) return 'security';
  if (type.includes('vpc') || type.includes('subnet')) return 'network';
  if (type.includes('s3') || type.includes('ebs')) return 'storage';
  if (type.includes('ec2') || type.includes('instance')) return 'compute';

  return 'other';
}

/**
 *
 */
function extractCFNTags(tags: unknown): Record<string, string> | undefined {
  if (!Array.isArray(tags)) return undefined;

  const tagMap: Record<string, string> = {};
  tags.forEach((tag: unknown) => {
    if (tag.Key && tag.Value) {
      tagMap[tag.Key] = tag.Value;
    }
  });

  return Object.keys(tagMap).length > 0 ? tagMap : undefined;
}

/**
 *
 */
function groupResources(
  topology: CloudTopology,
  groupBy: string
): Map<string, string[]> {
  const groups = new Map<string, string[]>();

  topology.resources.forEach(resource => {
    let groupKey = 'default';

    switch (groupBy) {
      case 'provider':
        groupKey = resource.provider;
        break;
      case 'type':
        groupKey = resource.type;
        break;
      case 'module':
        const module = topology.modules.find(m =>
          m.resources.includes(resource.id)
        );
        groupKey = module?.name || 'default';
        break;
      case 'region':
        groupKey = resource.metadata.region || 'default';
        break;
    }

    const resources = groups.get(groupKey) || [];
    resources.push(resource.id);
    groups.set(groupKey, resources);
  });

  return groups;
}

/**
 *
 */
export function getResourceStyle(resource: CloudResource): Record<string, unknown> {
  const baseStyle = {
    borderRadius: 8,
    padding: 16,
    fontSize: 14,
    fontWeight: 500,
  };

  // Drift-based styling
  if (resource.metadata.drift) {
    switch (resource.metadata.drift.status) {
      case 'drifted':
        return {
          ...baseStyle,
          backgroundColor: '#fef2f2',
          borderColor: '#ef4444',
          borderWidth: 2,
          color: '#7f1d1d',
        };
      case 'missing':
        return {
          ...baseStyle,
          backgroundColor: '#fefce8',
          borderColor: '#eab308',
          borderWidth: 2,
          color: '#713f12',
        };
      case 'in-sync':
        return {
          ...baseStyle,
          backgroundColor: '#f0fdf4',
          borderColor: '#22c55e',
          borderWidth: 2,
          color: '#14532d',
        };
    }
  }

  // Type-based styling
  const typeColors: Record<ResourceType, { bg: string; border: string; text: string }> = {
    compute: { bg: '#eff6ff', border: '#3b82f6', text: '#1e3a8a' },
    storage: { bg: '#f0fdf4', border: '#22c55e', text: '#14532d' },
    network: { bg: '#fef3c7', border: '#f59e0b', text: '#78350f' },
    database: { bg: '#fce7f3', border: '#ec4899', text: '#831843' },
    security: { bg: '#fef2f2', border: '#ef4444', text: '#7f1d1d' },
    monitoring: { bg: '#f5f3ff', border: '#a78bfa', text: '#4c1d95' },
    container: { bg: '#ecfeff', border: '#06b6d4', text: '#164e63' },
    serverless: { bg: '#f5f3ff', border: '#8b5cf6', text: '#4c1d95' },
    other: { bg: '#f8fafc', border: '#94a3b8', text: '#334155' },
  };

  const colors = typeColors[resource.type];

  return {
    ...baseStyle,
    backgroundColor: colors.bg,
    borderColor: colors.border,
    borderWidth: 2,
    color: colors.text,
  };
}
