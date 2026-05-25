/**
 * Capability Schema Reader
 *
 * Provides type-safe access to the unified capability schema.
 * This is the single source of truth for all capability-based feature gates.
 *
 * @doc.type utility
 * @doc.purpose Type-safe capability schema access for UI feature gates
 * @doc.layer frontend
 */

/**
 * Feature flags interface (must match the store definition)
 */
import type { GeneratedFeatureGateId } from "@/lib/generated/feature-gates.generated";
import { emitDataCloudDiagnostic } from "../diagnostics";

export type FeatureFlags = Record<GeneratedFeatureGateId, boolean>;

/**
 * Capability status from the unified schema
 */
export type CapabilityStatus = 'stable' | 'preview' | 'deprecated' | 'experimental';

/**
 * Capability definition from the unified schema
 */
export interface Capability {
  id: string;
  name: string;
  type: string;
  status: CapabilityStatus;
  products: string[];
  description: string;
  spi_interface?: string;
  ui_gate?: string;
  metadata?: Record<string, string>;
}

/**
 * UI feature gate definition from the unified schema
 */
export interface FeatureGate {
  id: string;
  name: string;
  description: string;
  capability_dependency?: string;
  default_value: boolean;
  products: string[];
}

/**
 * Capability schema structure
 */
export interface CapabilitySchema {
  version: string;
  metadata: {
    description: string;
    last_updated: string;
    generators: string[];
  };
  kernel_capabilities: Capability[];
  data_cloud_capabilities: Capability[];
  aep_capabilities: Capability[];
  ui_feature_gates: FeatureGate[];
  status_definitions: Record<
    CapabilityStatus,
    {
      description: string;
      ui_indicator: string;
      allowed_in_production: boolean;
    }
  >;
}

/**
 * In-memory cache of the capability schema
 * In production, this would be fetched from the backend API
 */
let cachedSchema: CapabilitySchema | null = null;

const RUNTIME_SCHEMA_ENDPOINTS = [
  '/api/v1/surfaces/schema',
] as const;

/**
 * Mock schema for development (should be replaced with API fetch in production)
 */
const mockSchema: CapabilitySchema = {
  version: '1.0.0',
  metadata: {
    description: 'Unified capability schema for Ghatana platform and products',
    last_updated: '2026-05-03',
    generators: ['KernelCapability.java', 'Data Cloud SPI capabilities', 'AEP SPI capabilities', 'Product feature flags'],
  },
  kernel_capabilities: [
    {
      id: 'data.storage',
      name: 'Data Storage',
      type: 'DATA_MANAGEMENT',
      status: 'stable',
      products: ['all'],
      description: 'Unified data storage abstraction with multi-tier support',
    },
    {
      id: 'workflow.engine',
      name: 'Workflow Engine',
      type: 'WORKFLOW',
      status: 'stable',
      products: ['all'],
      description: 'Business workflow orchestration with durability',
    },
    {
      id: 'observability.framework',
      name: 'Observability Framework',
      type: 'OBSERVABILITY',
      status: 'stable',
      products: ['all'],
      description: 'Comprehensive observability stack with metrics, logs, and traces',
    },
  ],
  data_cloud_capabilities: [
    {
      id: 'data.cloud.query',
      name: 'Query Capability',
      type: 'DATA_MANAGEMENT',
      status: 'stable',
      products: ['data-cloud'],
      description: 'Filtered queries, range queries, aggregations, and pagination',
      spi_interface: 'com.ghatana.datacloud.spi.capability.QueryCapability',
      ui_gate: 'enableUnifiedDataExplorer',
    },
    {
      id: 'data.cloud.workflow.execution',
      name: 'Workflow Execution Capability',
      type: 'WORKFLOW',
      status: 'stable',
      products: ['data-cloud'],
      description: 'Durable workflow execution with checkpoint and rollback',
      spi_interface: 'com.ghatana.datacloud.spi.capability.WorkflowExecutionCapability',
      ui_gate: 'enableSmartWorkflowBuilder',
    },
    {
      id: 'data.cloud.analytics',
      name: 'Analytics Capability',
      type: 'ANALYTICS',
      status: 'stable',
      products: ['data-cloud'],
      description: 'Analytics and aggregation operations',
      spi_interface: 'com.ghatana.datacloud.spi.AggregationCapability',
      metadata: {
        cancellation_supported: 'false',
        notes: 'Analytics cancellation is unsupported; UI must reflect this',
      },
    },
    {
      id: 'data.cloud.fabric',
      name: 'Data Fabric',
      type: 'DATA_MANAGEMENT',
      status: 'preview',
      products: ['data-cloud'],
      description: 'Data fabric topology and connector management',
      metadata: {
        production_ready: 'false',
        notes: 'Preview/demo-only with hardcoded demo metrics; needs production capability boundary',
      },
    },
    {
      id: 'data.cloud.ai.recommendation',
      name: 'AI Recommendation Capability',
      type: 'AI_ML',
      status: 'stable',
      products: ['data-cloud'],
      description: 'AI-powered recommendations and suggestions',
      spi_interface: 'com.ghatana.datacloud.spi.ai.RecommendationCapability',
      ui_gate: 'enableAmbientIntelligence',
    },
    {
      id: 'data.cloud.ai.explanation',
      name: 'AI Explanation Capability',
      type: 'AI_ML',
      status: 'stable',
      products: ['data-cloud'],
      description: 'AI model explanations and interpretability',
      spi_interface: 'com.ghatana.datacloud.spi.ai.ExplanationCapability',
      ui_gate: 'enableContextSidebar',
    },
  ],
  aep_capabilities: [
    {
      id: 'aep.eventcloud.durable',
      name: 'Durable EventLog',
      type: 'EVENT_PROCESSING',
      status: 'stable',
      products: ['aep'],
      description: 'Durable event storage and processing with HTTP 503 for unavailable services in production',
      metadata: {
        fail_closed: 'true',
        notes: 'Production requires durable EventLog provider; in-memory only for dev/test with allow flag',
      },
    },
  ],
  ui_feature_gates: [
    {
      id: 'enableIntelligentHub',
      name: 'Intelligent Hub',
      description: 'Enable the new Intelligent Hub (unified home page)',
      capability_dependency: 'data.cloud.query',
      default_value: true,
      products: ['data-cloud'],
    },
    {
      id: 'enableCommandBar',
      name: 'Command Bar',
      description: 'Enable the Command Bar (NL command input)',
      default_value: true,
      products: ['data-cloud', 'aep'],
    },
    {
      id: 'enableAmbientIntelligence',
      name: 'Ambient Intelligence Bar',
      description: 'Enable the Ambient Intelligence Bar (bottom notifications)',
      capability_dependency: 'data.cloud.ai.recommendation',
      default_value: true,
      products: ['data-cloud'],
    },
    {
      id: 'enableContextSidebar',
      name: 'Context Sidebar',
      description: 'Enable the Context Sidebar (always-visible assistance panel)',
      capability_dependency: 'data.cloud.ai.explanation',
      default_value: true,
      products: ['data-cloud', 'aep'],
    },
    {
      id: 'enableUnifiedDataExplorer',
      name: 'Unified Data Explorer',
      description: 'Enable unified Data Explorer (merged pages)',
      capability_dependency: 'data.cloud.query',
      default_value: true,
      products: ['data-cloud'],
    },
    {
      id: 'enableSmartWorkflowBuilder',
      name: 'Smart Workflow Builder',
      description: 'Enable Smart Workflow Builder (intent-based)',
      capability_dependency: 'data.cloud.workflow.execution',
      default_value: true,
      products: ['data-cloud'],
    },
    {
      id: 'legacyPagesEnabled',
      name: 'Legacy Pages',
      description: 'Keep legacy pages accessible for power users',
      default_value: true,
      products: ['data-cloud', 'aep'],
    },
    {
      id: 'enableSimplifiedNav',
      name: 'Simplified Navigation',
      description: 'Enable simplified navigation (5 items vs 12+)',
      default_value: true,
      products: ['data-cloud', 'aep'],
    },
  ],
  status_definitions: {
    stable: {
      description: 'Production-ready with full support',
      ui_indicator: 'green',
      allowed_in_production: true,
    },
    preview: {
      description: 'Preview/demo-only, not production-ready',
      ui_indicator: 'amber',
      allowed_in_production: false,
    },
    deprecated: {
      description: 'Deprecated, will be removed in future version',
      ui_indicator: 'red',
      allowed_in_production: false,
    },
    experimental: {
      description: 'Experimental, may change without notice',
      ui_indicator: 'purple',
      allowed_in_production: false,
    },
  },
};

/**
 * Load the capability schema from backend API
 * P2-CAP-1: Fetch from backend to ensure single source of truth
 */
export async function loadCapabilitySchema(): Promise<CapabilitySchema> {
  if (cachedSchema) {
    return cachedSchema;
  }

  try {
    let lastError: Error | null = null;

    for (const endpoint of RUNTIME_SCHEMA_ENDPOINTS) {
      try {
        const response = await fetch(endpoint);
        if (!response.ok) {
          throw new Error(`Failed to fetch capability schema from ${endpoint}: ${response.status} ${response.statusText}`);
        }

        const envelope = await response.json() as { data?: CapabilitySchema };
        if (!envelope.data) {
          throw new Error(`Capability schema envelope from ${endpoint} is missing data`);
        }

        if (endpoint !== RUNTIME_SCHEMA_ENDPOINTS[0]) {
          emitDataCloudDiagnostic("Capabilities", "warn", "Capability schema fetched from compatibility endpoint", {
            endpoint,
            preferredEndpoint: RUNTIME_SCHEMA_ENDPOINTS[0],
          });
        }

        cachedSchema = envelope.data;
        return cachedSchema;
      } catch (error) {
        lastError = error instanceof Error ? error : new Error(String(error));
      }
    }

    throw lastError ?? new Error('Failed to fetch capability schema from all configured runtime endpoints');
  } catch (error) {
    emitDataCloudDiagnostic("Capabilities", "error", "Failed to load capability schema from backend, falling back to mock", {
      error,
    });
    // Fallback to mock schema for development
    cachedSchema = mockSchema;
    return cachedSchema;
  }
}

/**
 * Get a capability by ID
 */
export function getCapability(id: string): Capability | undefined {
  if (!cachedSchema) {
    return undefined;
  }

  return [
    ...cachedSchema.kernel_capabilities,
    ...cachedSchema.data_cloud_capabilities,
    ...cachedSchema.aep_capabilities,
  ].find((cap) => cap.id === id);
}

/**
 * Check if a capability is available for a product
 */
export function isCapabilityAvailable(capabilityId: string, product: string = 'data-cloud'): boolean {
  const capability = getCapability(capabilityId);
  if (!capability) {
    return false;
  }

  // Check if capability supports the product
  if (!capability.products.includes('all') && !capability.products.includes(product)) {
    return false;
  }

  // Check if capability is allowed in production based on status
  const statusDef = cachedSchema?.status_definitions[capability.status];
  if (statusDef && !statusDef.allowed_in_production) {
    // In production, preview/deprecated capabilities are not available
    // In dev/test, they might be available
    const isProduction = import.meta.env.MODE === 'production';
    if (isProduction) {
      return false;
    }
  }

  return true;
}

/**
 * Get the status of a capability
 */
export function getCapabilityStatus(capabilityId: string): CapabilityStatus | undefined {
  const capability = getCapability(capabilityId);
  return capability?.status;
}

/**
 * Get a feature gate by ID
 */
export function getFeatureGate(id: string): FeatureGate | undefined {
  if (!cachedSchema) {
    return undefined;
  }

  return cachedSchema.ui_feature_gates.find((gate) => gate.id === id);
}

/**
 * Check if a feature gate should be enabled based on capability dependencies
 */
export async function isFeatureGateEnabled(
  gateId: string,
  product: string = 'data-cloud',
  userOverrides?: Partial<FeatureFlags>
): Promise<boolean> {
  const schema = await loadCapabilitySchema();
  const gate = schema.ui_feature_gates.find((g) => g.id === gateId);

  if (!gate) {
    return false;
  }

  // Check if gate applies to this product
  if (!gate.products.includes('all') && !gate.products.includes(product)) {
    return false;
  }

  // Check user overrides first (from featureFlags.store.ts)
  if (userOverrides && gateId in userOverrides) {
    const value = userOverrides[gateId as keyof FeatureFlags];
    return value !== undefined ? value : gate.default_value;
  }

  // Check capability dependency
  if (gate.capability_dependency) {
    const capabilityAvailable = isCapabilityAvailable(gate.capability_dependency, product);
    if (!capabilityAvailable) {
      return false;
    }
  }

  // Return default value
  return gate.default_value;
}

/**
 * Get all capabilities for a product
 */
export function getProductCapabilities(product: string): Capability[] {
  if (!cachedSchema) {
    return [];
  }

  return [
    ...cachedSchema.kernel_capabilities,
    ...cachedSchema.data_cloud_capabilities,
    ...cachedSchema.aep_capabilities,
  ].filter((cap) => cap.products.includes('all') || cap.products.includes(product));
}

/**
 * Get all feature gates for a product
 */
export function getProductFeatureGates(product: string): FeatureGate[] {
  if (!cachedSchema) {
    return [];
  }

  return cachedSchema.ui_feature_gates.filter((gate) =>
    gate.products.includes('all') || gate.products.includes(product)
  );
}
