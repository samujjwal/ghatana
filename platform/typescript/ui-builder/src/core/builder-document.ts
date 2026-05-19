/**
 * @fileoverview Builder Document Model
 *
 * Core document structure for UI Builder with strict schema validation,
 * versioned migrations, and design-system registry integration.
 *
 * @doc.type module
 * @doc.purpose BuilderDocument model and validation
 * @doc.layer platform
 */

import { z } from "zod";
import { createNodeId, createDocumentId } from "./types.js";
import type { NodeId, DocumentId, ComponentInstance, Binding, DocumentMetadata } from "./types.js";
import type { DataClassification } from "@ghatana/platform-events";

// ============================================================================
// SCHEMA VERSION
// ============================================================================

/** Current schema version for BuilderDocument. */
export const CURRENT_SCHEMA_VERSION = "1.0.0";

/** Schema version history. */
export const SCHEMA_VERSIONS = ["1.0.0"] as const;

// ============================================================================
// ZOD SCHEMAS
// ============================================================================

/** Node ID schema with branded type. */
const NodeIdSchema = z.string().transform((s) => s as NodeId);

/** Document ID schema with branded type. */
const DocumentIdSchema = z.string().transform((s) => s as DocumentId);

/** Position schema. */
const PositionSchema = z.object({
  x: z.number(),
  y: z.number(),
});

/** Size schema. */
const SizeSchema = z.object({
  width: z.number().positive(),
  height: z.number().positive(),
});

/** Layout constraints schema. */
const LayoutConstraintsSchema = z.object({
  minWidth: z.union([z.number(), z.string()]).optional(),
  maxWidth: z.union([z.number(), z.string()]).optional(),
  minHeight: z.union([z.number(), z.string()]).optional(),
  maxHeight: z.union([z.number(), z.string()]).optional(),
  aspectRatio: z.number().optional(),
  resizable: z.boolean(),
  positionable: z.boolean(),
  overflow: z.enum(["visible", "hidden", "scroll", "auto"]).optional(),
});

/** Action definition schema. */
const ActionDefinitionSchema = z.object({
  id: z.string(),
  label: z.string().optional(),
  triggerEvent: z.string(),
  targetKind: z.enum([
    "navigate",
    "toggle-state",
    "emit-event",
    "call-api",
    "update-binding",
    "custom",
  ]),
  payload: z.record(z.string(), z.unknown()).optional(),
  condition: z.string().optional(),
});

/** Responsive variant schema. */
const ResponsiveVariantSchema = z.object({
  breakpoint: z.string(),
  minWidth: z.number().optional(),
  maxWidth: z.number().optional(),
  props: z.record(z.string(), z.unknown()).optional(),
  hidden: z.boolean().optional(),
  position: PositionSchema.optional(),
  size: SizeSchema.optional(),
});

/** State variant schema. */
const StateVariantSchema = z.object({
  state: z.enum(["hover", "focus", "active", "disabled", "error", "loading", "selected"]),
  props: z.record(z.string(), z.unknown()).optional(),
});

/** Privacy metadata schema. Matches platform-events PrivacyMetadata structure for compatibility. */
const PrivacyMetadataSchema = z.object({
  dataClassification: z.custom<DataClassification>((val) => {
    const valid: readonly string[] = ['PUBLIC', 'INTERNAL', 'SENSITIVE', 'CREDENTIALS', 'REGULATED'];
    return typeof val === 'string' && valid.includes(val);
  }),
  piiFields: z.array(z.string()),
  containsCredentials: z.boolean(),
  requiresConsent: z.boolean(),
  retentionDays: z.number(),
  dataSubjectType: z.enum(["user", "customer", "employee", "none"]).optional(),
}).optional();

/** AI change record schema. */
const AIChangeRecordSchema = z.object({
  changeId: z.string(),
  timestamp: z.string(),
  descriptor: z.record(z.string(), z.unknown()),
  reviewStatus: z.object({
    status: z.enum(["none", "pending", "approved", "rejected", "requires-manual"]),
    reviewedBy: z.string().optional(),
    reviewedAt: z.string().optional(),
    notes: z.string().optional(),
  }),
});

/** Provenance record schema. */
const ProvenanceRecordSchema = z.object({
  createdBy: z.string(),
  createdAt: z.string(),
  modifiedBy: z.string().optional(),
  modifiedAt: z.string().optional(),
  version: z.string().optional(),
});

/** Instance metadata schema. */
const InstanceMetadataSchema = z.object({
  name: z.string().optional(),
  position: PositionSchema.optional(),
  size: SizeSchema.optional(),
  locked: z.boolean().optional(),
  hidden: z.boolean().optional(),
  ownership: z.record(z.string(), z.unknown()).optional(),
  layout: LayoutConstraintsSchema.optional(),
  responsiveVariants: z.array(ResponsiveVariantSchema).optional(),
  stateVariants: z.array(StateVariantSchema).optional(),
  actions: z.array(ActionDefinitionSchema).optional(),
  reviewStatus: z.object({
    status: z.enum(["none", "pending", "approved", "rejected", "requires-manual"]),
    reviewedBy: z.string().optional(),
    reviewedAt: z.string().optional(),
    notes: z.string().optional(),
  }).optional(),
  pendingProps: z.record(z.string(), z.unknown()).optional(),
  privacyMetadata: PrivacyMetadataSchema.optional(),
  dataClassification: z.record(z.string(), z.unknown()).optional(),
  aiLineage: z.array(AIChangeRecordSchema).optional(),
  collaborationId: z.string().optional(),
  provenance: ProvenanceRecordSchema.optional(),
});

/** Component instance schema. */
const ComponentInstanceSchema = z.object({
  id: NodeIdSchema,
  contractName: z.string().min(1),
  props: z.record(z.string(), z.unknown()),
  slots: z.record(z.string(), z.array(NodeIdSchema)),
  bindings: z.array(z.object({
    id: z.string(),
    type: z.enum(["data", "event", "slot", "theme", "computed"]),
    source: z.string(),
    target: z.string(),
    transform: z.string().optional(),
    bidirectional: z.boolean().optional(),
  })),
  metadata: InstanceMetadataSchema,
});

/** Design system model schema. */
const DesignSystemModelSchema = z.object({
  id: z.string(),
  name: z.string().min(1),
  version: z.string(),
  tokenSetIds: z.array(z.string()),
  componentContracts: z.array(z.record(z.string(), z.unknown())),
  themeId: z.string(),
});

/** Document metadata schema. */
const DocumentMetadataSchema = z.object({
  createdAt: z.string(),
  updatedAt: z.string(),
  author: z.string().optional(),
  description: z.string().optional(),
  tags: z.array(z.string()).optional(),
  changeCount: z.number().optional(),
  collaborationVersion: z.number().optional(),
  checkpointId: z.string().optional(),
  dataClassification: z.record(z.string(), z.unknown()).optional(),
  reviewStatus: z.object({
    status: z.enum(["none", "pending", "approved", "rejected", "requires-manual"]),
    reviewedBy: z.string().optional(),
    reviewedAt: z.string().optional(),
    notes: z.string().optional(),
  }).optional(),
  syncStatus: z.record(z.string(), z.unknown()).optional(),
  visibilityContract: z.record(z.string(), z.unknown()).optional(),
  trustLevel: z.record(z.string(), z.unknown()).optional(),
});

/** Layout node schema. */
const LayoutNodeSchema = z.object({
  id: NodeIdSchema,
  type: z.enum(["root", "container", "leaf"]),
  children: z.array(NodeIdSchema).optional(),
  layout: z.enum(["flex", "grid", "absolute", "stack"]).optional(),
  layoutProps: z.record(z.string(), z.unknown()).optional(),
});

/** Layout definition schema. */
const LayoutSchema = z.object({
  type: z.enum(["flex", "grid", "absolute", "stack", "flow"]),
  nodes: z.record(z.string(), LayoutNodeSchema),
  rootId: NodeIdSchema,
});

/** Binding definition schema. */
const BindingSchema = z.object({
  id: z.string(),
  type: z.enum(["data", "event", "slot", "theme", "computed"]),
  source: z.string(),
  target: z.string(),
  transform: z.string().optional(),
  bidirectional: z.boolean().optional(),
});

/** i18n schema. */
const I18nSchema = z.object({
  defaultLocale: z.string(),
  locales: z.array(z.string()),
  translations: z.record(z.string(), z.record(z.string(), z.string())),
});

/** a11y schema. */
const A11ySchema = z.object({
  title: z.string().optional(),
  description: z.string().optional(),
  landmarks: z.array(z.object({
    type: z.enum(["main", "navigation", "complementary", "contentinfo", "search", "banner"]),
    nodeId: NodeIdSchema,
  })).optional(),
  skipLinks: z.array(z.object({
    targetId: NodeIdSchema,
    label: z.string(),
  })).optional(),
});

/** Privacy schema. */
const PrivacySchema = z.object({
  classification: z.enum(["public", "internal", "confidential", "restricted"]),
  piiNodes: z.array(NodeIdSchema).optional(),
  dataRetention: z.object({
    days: z.number(),
    autoDelete: z.boolean(),
  }).optional(),
  consentRequired: z.boolean().optional(),
});

/** Validation rule schema. */
const ValidationRuleSchema = z.object({
  id: z.string(),
  type: z.enum(["required", "format", "range", "custom"]),
  target: z.string(),
  message: z.string(),
  params: z.record(z.string(), z.unknown()).optional(),
});

/** Validation schema. */
const ValidationSchema = z.object({
  rules: z.array(ValidationRuleSchema),
  validateOn: z.enum(["change", "blur", "submit"]).optional(),
});

/** Complete BuilderDocument schema. */
export const BuilderDocumentSchema = z.object({
  schemaVersion: z.literal(CURRENT_SCHEMA_VERSION),
  documentId: DocumentIdSchema,
  owner: z.string(),
  root: NodeIdSchema,
  nodes: z.record(z.string(), ComponentInstanceSchema),
  bindings: z.array(BindingSchema),
  layout: LayoutSchema,
  metadata: DocumentMetadataSchema,
  i18n: I18nSchema.optional(),
  a11y: A11ySchema.optional(),
  privacy: PrivacySchema.optional(),
  validation: ValidationSchema.optional(),
});

type BuilderDocumentSchemaShape = z.infer<typeof BuilderDocumentSchema>;

/** Canonical BuilderDocument type backed by the runtime BuilderDocumentSchema. */
export type BuilderDocument = Omit<BuilderDocumentSchemaShape, 'nodes' | 'bindings' | 'metadata'> & {
  readonly nodes: Record<string, ComponentInstance>;
  readonly bindings: Binding[];
  readonly metadata: DocumentMetadata;
};

type NodeRecordWithCompat = Record<string, ComponentInstance> & {
  get?: (nodeId: NodeId) => ComponentInstance | undefined;
  set?: (nodeId: NodeId, node: ComponentInstance) => NodeRecordWithCompat;
  has?: (nodeId: NodeId) => boolean;
  delete?: (nodeId: NodeId) => boolean;
  keys?: () => IterableIterator<NodeId>;
  values?: () => IterableIterator<ComponentInstance>;
  entries?: () => IterableIterator<[NodeId, ComponentInstance]>;
  readonly size?: number;
};

type BuilderDocumentWithCompat = BuilderDocument & {
  id?: DocumentId;
  version?: string;
  name?: string;
  rootNodes?: NodeId[];
};

function ownEnumerableNodeEntries(nodes: Record<string, ComponentInstance>): [NodeId, ComponentInstance][] {
  const entries = Object.entries(nodes)
    .filter(([, value]) => (
      typeof value === 'object'
      && value !== null
      && 'id' in value
      && value.contractName !== 'RootContainer'
    ));
  return entries as [NodeId, ComponentInstance][];
}

function schemaNodeRecord(nodes: Record<string, ComponentInstance>): Record<string, ComponentInstance> {
  return Object.fromEntries(
    Object.entries(nodes).filter(([, value]) => (
      typeof value === 'object'
      && value !== null
      && 'id' in value
    )),
  ) as Record<string, ComponentInstance>;
}

function attachNodeRecordCompatibility(nodes: Record<string, ComponentInstance>): Record<string, ComponentInstance> {
  const compatible = nodes as NodeRecordWithCompat;
  const define = (name: keyof NodeRecordWithCompat, value: unknown): void => {
    Object.defineProperty(compatible, name, {
      value,
      configurable: true,
      enumerable: false,
      writable: true,
    });
  };

  define('get', (nodeId: NodeId): ComponentInstance | undefined => compatible[nodeId]);
  define('set', (nodeId: NodeId, node: ComponentInstance): NodeRecordWithCompat => {
    compatible[nodeId] = node;
    return compatible;
  });
  define('has', (nodeId: NodeId): boolean => Object.prototype.hasOwnProperty.call(compatible, nodeId));
  define('delete', (nodeId: NodeId): boolean => delete compatible[nodeId]);
  define('keys', function* keys(): IterableIterator<NodeId> {
    for (const [nodeId] of ownEnumerableNodeEntries(compatible)) {
      yield nodeId;
    }
  });
  define('values', function* values(): IterableIterator<ComponentInstance> {
    for (const [, node] of ownEnumerableNodeEntries(compatible)) {
      yield node;
    }
  });
  define('entries', function* entries(): IterableIterator<[NodeId, ComponentInstance]> {
    for (const entry of ownEnumerableNodeEntries(compatible)) {
      yield entry;
    }
  });
  Object.defineProperty(compatible, 'size', {
    get: () => ownEnumerableNodeEntries(compatible).length,
    configurable: true,
    enumerable: false,
  });

  return compatible;
}

export function attachBuilderDocumentCompatibility(document: BuilderDocument): BuilderDocument {
  const compatible = (Object.isExtensible(document)
    ? document
    : {
        ...document,
        nodes: { ...document.nodes },
        bindings: [...document.bindings],
        layout: {
          ...document.layout,
          nodes: { ...document.layout.nodes },
        },
        metadata: { ...document.metadata },
      }) as BuilderDocumentWithCompat;
  const mutableCompatible = compatible as BuilderDocumentWithCompat & {
    nodes: Record<string, ComponentInstance>;
  };
  if (!Object.isExtensible(compatible.nodes)) {
    mutableCompatible.nodes = { ...compatible.nodes };
  }
  attachNodeRecordCompatibility(mutableCompatible.nodes);

  Object.defineProperty(compatible, 'id', {
    get: () => compatible.documentId,
    configurable: true,
    enumerable: false,
  });
  Object.defineProperty(compatible, 'version', {
    get: () => compatible.schemaVersion,
    configurable: true,
    enumerable: false,
  });
  Object.defineProperty(compatible, 'name', {
    get: () => compatible.metadata.description ?? compatible.owner,
    configurable: true,
    enumerable: false,
  });
  Object.defineProperty(compatible, 'rootNodes', {
    get: () => [...(compatible.layout.nodes[compatible.layout.rootId]?.children ?? [])],
    set: (nodeIds: NodeId[]) => {
      const rootLayoutNode = compatible.layout.nodes[compatible.layout.rootId];
      if (rootLayoutNode) {
        rootLayoutNode.children = [...nodeIds];
      }
    },
    configurable: true,
    enumerable: false,
  });

  return compatible;
}

export function normalizeBuilderDocument(document: BuilderDocument): BuilderDocument {
  const candidate = document as BuilderDocumentWithCompat;
  if (candidate.layout && candidate.root && candidate.schemaVersion && !(candidate.nodes instanceof Map)) {
    return attachBuilderDocumentCompatibility(document);
  }

  const rawNodes = candidate.nodes;
  const nodes = rawNodes instanceof Map
    ? Object.fromEntries(rawNodes.entries()) as Record<string, ComponentInstance>
    : { ...(rawNodes as Record<string, ComponentInstance>) };
  const rootNodes = candidate.rootNodes ?? [];
  const root = createNodeId('root');
  const now = new Date().toISOString();

  return attachBuilderDocumentCompatibility({
    schemaVersion: CURRENT_SCHEMA_VERSION,
    documentId: candidate.documentId ?? candidate.id ?? createDocumentId(),
    owner: candidate.owner ?? candidate.metadata?.author ?? 'unknown',
    root,
    nodes,
    bindings: candidate.bindings ?? [],
    layout: {
      type: 'flex',
      nodes: {
        [root]: {
          id: root,
          type: 'root',
          children: [...rootNodes],
          layout: 'flex',
          layoutProps: { direction: 'vertical' },
        },
      },
      rootId: root,
    },
    metadata: {
      createdAt: candidate.metadata?.createdAt ?? now,
      updatedAt: candidate.metadata?.updatedAt ?? now,
      author: candidate.metadata?.author ?? candidate.owner,
      description: candidate.metadata?.description ?? candidate.name,
      tags: candidate.metadata?.tags,
      changeCount: candidate.metadata?.changeCount,
      collaborationVersion: candidate.metadata?.collaborationVersion,
      checkpointId: candidate.metadata?.checkpointId,
      dataClassification: candidate.metadata?.dataClassification,
      reviewStatus: candidate.metadata?.reviewStatus,
      syncStatus: candidate.metadata?.syncStatus,
      visibilityContract: candidate.metadata?.visibilityContract,
      trustLevel: candidate.metadata?.trustLevel,
    },
  });
}

// ============================================================================
// DOCUMENT CREATION
// ============================================================================

/**
 * Create a new empty BuilderDocument.
 */
export function createBuilderDocument(
  owner: string,
  options?: {
    documentId?: DocumentId;
    designSystemId?: string;
    designSystemName?: string;
    nodes?: Map<NodeId, ComponentInstance> | Record<string, ComponentInstance>;
    rootNodes?: readonly NodeId[];
    metadata?: Partial<DocumentMetadata>;
    designSystem?: unknown;
  },
): BuilderDocument {
  const documentId = options?.documentId ?? createDocumentId();
  const rootId = createNodeId();
  const now = new Date().toISOString();

  const overrideNodes = options?.nodes instanceof Map
    ? Object.fromEntries(options.nodes.entries()) as Record<string, ComponentInstance>
    : options?.nodes;
  const document = attachBuilderDocumentCompatibility({
    schemaVersion: CURRENT_SCHEMA_VERSION,
    documentId,
    owner,
    root: rootId,
    nodes: {
      [rootId]: {
        id: rootId,
        contractName: "RootContainer",
        props: {},
        slots: {},
        bindings: [],
        metadata: {
          name: "Root",
          locked: false,
          hidden: false,
        },
      },
    },
    bindings: [],
    layout: {
      type: "flex",
      nodes: {
        [rootId]: {
          id: rootId,
          type: "root",
          children: [],
          layout: "flex",
          layoutProps: { direction: "vertical" },
        },
      },
      rootId,
    },
    metadata: {
      createdAt: now,
      updatedAt: now,
      author: owner,
      changeCount: 0,
      ...options?.metadata,
    },
  });
  if (overrideNodes) {
    (document as BuilderDocument & { nodes: Record<string, ComponentInstance> }).nodes = {
      [rootId]: document.nodes[rootId]!,
      ...overrideNodes,
    };
    attachNodeRecordCompatibility(document.nodes);
  }
  if (options?.rootNodes) {
    const rootLayoutNode = document.layout.nodes[document.layout.rootId];
    if (rootLayoutNode) {
      rootLayoutNode.children = [...options.rootNodes];
    }
  }
  if (options?.designSystem) {
    Object.defineProperty(document, 'designSystem', {
      get: () => options.designSystem,
      configurable: true,
      enumerable: false,
    });
  }
  if (options?.designSystemId) {
    Object.defineProperty(document, 'designSystemId', {
      get: () => options.designSystemId,
      configurable: true,
      enumerable: false,
    });
  }
  if (options?.designSystemName) {
    Object.defineProperty(document, 'designSystemName', {
      get: () => options.designSystemName,
      configurable: true,
      enumerable: false,
    });
  }
  return document;
}

// ============================================================================
// COMPATIBILITY ADAPTERS
// ============================================================================

/**
 * Set root nodes in canonical BuilderDocument layout structure.
 * Adapter for legacy code that sets rootNodes array.
 */
export function setRootNodes(document: BuilderDocument, nodeIds: NodeId[]): BuilderDocument {
  return {
    ...document,
    layout: {
      ...document.layout,
      nodes: {
        ...document.layout.nodes,
        [document.layout.rootId]: {
          ...document.layout.nodes[document.layout.rootId],
          children: nodeIds,
        },
      },
    },
  };
}

/**
 * Get document ID - adapter for legacy code that uses document.id.
 */
export function getDocumentId(document: BuilderDocument): DocumentId {
  return document.documentId;
}

/**
 * Get document version - adapter for legacy code that uses document.version.
 */
export function getDocumentVersion(document: BuilderDocument): string {
  return document.schemaVersion;
}

/**
 * Get nodes as Map - adapter for legacy code that expects Map<NodeId, ComponentInstance>.
 */
export function getNodesAsMap(document: BuilderDocument): Map<NodeId, ComponentInstance> {
  return new Map(Object.entries(document.nodes) as [NodeId, ComponentInstance][]);
}

/**
 * Get nodes from Map and convert to Record - adapter for converting legacy Map to canonical Record.
 */
export function nodesMapToRecord(map: Map<NodeId, ComponentInstance>): Record<string, ComponentInstance> {
  return Object.fromEntries(map.entries()) as Record<string, ComponentInstance>;
}

// ============================================================================
// DOCUMENT VALIDATION
// ============================================================================

/** Validation issue severity. */
export type ValidationIssueSeverity = "error" | "warning" | "info";

/** Validation issue. */
export interface ValidationIssue {
  readonly severity: ValidationIssueSeverity;
  readonly code: string;
  readonly message: string;
  readonly path: string;
  readonly nodeId?: NodeId;
}

/** Validation result. */
export interface DocumentValidationResult {
  readonly valid: boolean;
  readonly schemaValid: boolean;
  readonly issues: readonly ValidationIssue[];
  readonly errors: readonly ValidationIssue[];
  readonly warnings: readonly ValidationIssue[];
}

/**
 * Validate a BuilderDocument against the schema.
 */
export function validateBuilderDocument(
  document: unknown,
): DocumentValidationResult {
  const issues: ValidationIssue[] = [];

  // Auto-migrate: if schemaVersion is missing, assign the current version so the
  // schema validator can proceed (normalisation is a no-op for well-formed docs).
  if (document && typeof document === 'object' && !('schemaVersion' in document)) {
    document = { schemaVersion: CURRENT_SCHEMA_VERSION, ...document };
  }

  // Schema validation
  const documentForSchema = document && typeof document === 'object'
    ? {
        ...(document as BuilderDocument),
        nodes: schemaNodeRecord((document as BuilderDocument).nodes ?? {}),
      }
    : document;
  const schemaResult = BuilderDocumentSchema.safeParse(documentForSchema);

  if (!schemaResult.success) {
    const zodIssues = schemaResult.error.issues;
    for (const issue of zodIssues) {
      issues.push({
        severity: "error",
        code: `SCHEMA_${issue.code}`,
        message: issue.message,
        path: issue.path.join("."),
      });
    }

    return {
      valid: false,
      schemaValid: false,
      issues,
      errors: issues,
      warnings: [],
    };
  }

  const validDocument = schemaResult.data;

  // Semantic validation

  // Check that root node exists
  if (!validDocument.nodes[validDocument.root]) {
    issues.push({
      severity: "error",
      code: "MISSING_ROOT_NODE",
      message: `Root node ${validDocument.root} not found in nodes map`,
      path: "root",
    });
  }

  // Check that layout references valid nodes
  for (const nodeId of Object.keys(validDocument.layout.nodes)) {
    if (!validDocument.nodes[nodeId]) {
      issues.push({
        severity: "error",
        code: "LAYOUT_NODE_MISSING",
        message: `Layout references non-existent node: ${nodeId}`,
        path: `layout.nodes.${nodeId}`,
      });
    }
  }

  // Check that all children in layout exist
  for (const [nodeId, layoutNode] of Object.entries(validDocument.layout.nodes)) {
    if (layoutNode.children) {
      for (const childId of layoutNode.children) {
        if (!validDocument.nodes[childId]) {
          issues.push({
            severity: "error",
            code: "CHILD_NODE_MISSING",
            message: `Node ${nodeId} references non-existent child: ${childId}`,
            path: `layout.nodes.${nodeId}.children`,
            nodeId: childId as NodeId,
          });
        }
      }
    }
  }

  // Check that all binding sources are valid
  for (const [index, binding] of validDocument.bindings.entries()) {
    if (!binding.source || binding.source.trim() === "") {
      issues.push({
        severity: "warning",
        code: "EMPTY_BINDING_SOURCE",
        message: `Binding ${binding.id} has empty source`,
        path: `bindings[${index}].source`,
      });
    }
    if (!binding.target || binding.target.trim() === "") {
      issues.push({
        severity: "error",
        code: "EMPTY_BINDING_TARGET",
        message: `Binding ${binding.id} has empty target`,
        path: `bindings[${index}].target`,
      });
    }
  }

  // Check for orphaned nodes (not in layout)
  const layoutNodeIds = new Set(Object.keys(validDocument.layout.nodes));
  for (const nodeId of Object.keys(validDocument.nodes)) {
    if (!layoutNodeIds.has(nodeId) && nodeId !== validDocument.root) {
      issues.push({
        severity: "warning",
        code: "ORPHANED_NODE",
        message: `Node ${nodeId} exists but is not in layout`,
        path: `nodes.${nodeId}`,
        nodeId: nodeId as NodeId,
      });
    }
  }

  // Check privacy configuration consistency
  if (validDocument.privacy) {
    for (const piiNodeId of validDocument.privacy.piiNodes ?? []) {
      if (!validDocument.nodes[piiNodeId]) {
        issues.push({
          severity: "error",
          code: "PII_NODE_MISSING",
          message: `Privacy references non-existent PII node: ${piiNodeId}`,
          path: "privacy.piiNodes",
          nodeId: piiNodeId,
        });
      }
    }
  }

  // Check accessibility configuration
  if (validDocument.a11y) {
    for (const landmark of validDocument.a11y.landmarks ?? []) {
      if (!validDocument.nodes[landmark.nodeId]) {
        issues.push({
          severity: "error",
          code: "LANDMARK_NODE_MISSING",
          message: `Accessibility landmark references non-existent node: ${landmark.nodeId}`,
          path: "a11y.landmarks",
          nodeId: landmark.nodeId,
        });
      }
    }
    for (const skipLink of validDocument.a11y.skipLinks ?? []) {
      if (!validDocument.nodes[skipLink.targetId]) {
        issues.push({
          severity: "error",
          code: "SKIP_LINK_TARGET_MISSING",
          message: `Skip link references non-existent target: ${skipLink.targetId}`,
          path: "a11y.skipLinks",
          nodeId: skipLink.targetId,
        });
      }
    }
  }

  const errors = issues.filter((i) => i.severity === "error");
  const warnings = issues.filter((i) => i.severity === "warning");

  return {
    valid: errors.length === 0,
    schemaValid: true,
    issues,
    errors,
    warnings,
  };
}

// ============================================================================
// SCHEMA MIGRATION
// ============================================================================

/**
 * Migration function type.
 */
export type MigrationFunction = (document: unknown) => unknown;

/**
 * Registry of migrations from older schema versions.
 *
 * Migration strategy:
 * - Each migration function transforms a document from version X to version X+1
 * - Migrations should be additive and backward-compatible where possible
 * - Always validate the output document against the target schema
 * - Document breaking changes in this comment block
 *
 * Example migration entry (for future use):
 * [
 *   ['0.9.0', (doc) => {
 *     // Transform 0.9.0 to 1.0.0 structure
 *     // e.g., convert rootNodes array to layout.rootId + layout.nodes structure
 *     return transformedDoc;
 *   }],
 * ]
 */
export const MIGRATIONS: ReadonlyMap<string, MigrationFunction> = new Map([
  // Version 1.0.0 is the initial schema version - no migrations needed yet
  // Future migrations will be added here as the schema evolves
]);

/**
 * Detect schema version of a document.
 */
export function detectSchemaVersion(document: unknown): string | null {
  if (typeof document !== "object" || document === null) {
    return null;
  }

  const doc = document as Record<string, unknown>;

  // Check explicit schemaVersion
  if (typeof doc.schemaVersion === "string") {
    return doc.schemaVersion;
  }

  // Check for legacy version field
  if (typeof doc.version === "string") {
    return doc.version;
  }

  return null;
}

/**
 * Migrate document to current schema version.
 */
export function migrateBuilderDocument(
  document: unknown,
): { success: boolean; document?: BuilderDocument; errors: string[] } {
  const errors: string[] = [];

  const sourceVersion = detectSchemaVersion(document);
  if (!sourceVersion) {
    errors.push("Could not detect schema version");
    return { success: false, errors };
  }

  if (sourceVersion === CURRENT_SCHEMA_VERSION) {
    // Already current version, validate
    const validation = validateBuilderDocument(document);
    if (!validation.valid) {
      errors.push(...validation.errors.map((e) => e.message));
      return { success: false, errors };
    }
    return { success: true, document: document as BuilderDocument, errors: [] };
  }

  // Check if migration path exists
  const migration = MIGRATIONS.get(sourceVersion);
  if (!migration) {
    errors.push(`No migration available from version ${sourceVersion}`);
    return { success: false, errors };
  }

  try {
    const migrated = migration(document);
    const validation = validateBuilderDocument(migrated);

    if (!validation.valid) {
      errors.push(...validation.errors.map((e) => e.message));
      return { success: false, errors };
    }

    return { success: true, document: migrated as BuilderDocument, errors: [] };
  } catch (error) {
    errors.push(
      `Migration failed: ${error instanceof Error ? error.message : String(error)}`,
    );
    return { success: false, errors };
  }
}

// ============================================================================
// SERIALIZATION
// ============================================================================

/**
 * Serialize BuilderDocument to JSON string.
 */
export function serializeBuilderDocument(document: BuilderDocument): string {
  return JSON.stringify(document, null, 2);
}

/**
 * Deserialize BuilderDocument from JSON string.
 */
export function deserializeBuilderDocument(
  json: string,
): { success: boolean; document?: BuilderDocument; errors: string[] } {
  try {
    const parsed = JSON.parse(json);
    const migration = migrateBuilderDocument(parsed);

    if (!migration.success || !migration.document) {
      return { success: false, errors: migration.errors };
    }

    const migratedDocument = migration.document;
    return { success: true, document: attachBuilderDocumentCompatibility(migratedDocument), errors: [] };
  } catch (error) {
    return {
      success: false,
      errors: [
        `JSON parse error: ${error instanceof Error ? error.message : String(error)}`,
      ],
    };
  }
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

/**
 * Get a node by ID from the document.
 */
export function getNode(
  document: BuilderDocument,
  nodeId: NodeId,
): BuilderDocument['nodes'][string] | undefined {
  return document.nodes[nodeId];
}

/**
 * Get all root-level nodes.
 */
export function getRootNodes(document: BuilderDocument): BuilderDocument['nodes'][string][] {
  const rootLayoutNode = document.layout.nodes[document.root];
  if (!rootLayoutNode?.children) return [];

  return rootLayoutNode.children
    .map((id) => document.nodes[id])
    .filter((n): n is BuilderDocument['nodes'][string] => n !== undefined);
}

/**
 * Get bindings for a specific node.
 */
export function getNodeBindings(
  document: BuilderDocument,
  nodeId: NodeId,
): BuilderDocument['bindings'] {
  return document.bindings.filter((b) => {
    // Check if binding targets this node
    return b.target.startsWith(String(nodeId));
  });
}

/**
 * Check if document has privacy-sensitive data.
 */
export function hasPrivacySensitiveData(document: BuilderDocument): boolean {
  if (document.privacy?.classification === "restricted") return true;
  if (document.privacy?.consentRequired) return true;
  if ((document.privacy?.piiNodes?.length ?? 0) > 0) return true;

  // Check node-level privacy metadata
  for (const node of Object.values(document.nodes)) {
    if (node.metadata.privacyMetadata?.requiresConsent) return true;
    if ((node.metadata.privacyMetadata?.piiFields?.length ?? 0) > 0) return true;
  }

  return false;
}

/**
 * Check if document requires accessibility features.
 */
export function requiresAccessibility(document: BuilderDocument): boolean {
  return document.a11y !== undefined;
}
