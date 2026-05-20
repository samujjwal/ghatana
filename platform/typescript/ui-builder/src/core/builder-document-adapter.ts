/**
 * @fileoverview Builder Document Adapter for DTO Conversion
 *
 * Provides adapters for converting BuilderDocument between different formats:
 * - Internal canonical format
 * - API DTO format (for REST/GraphQL APIs)
 * - Storage format (for database persistence)
 * - Legacy format (for backward compatibility)
 *
 * @doc.type module
 * @doc.purpose Builder document DTO conversion
 * @doc.layer platform
 * @doc.pattern AdapterPattern
 */

import type { ComponentInstance, NodeId, DocumentId, Binding } from './types.js';
import { validateBuilderDocument, type BuilderDocument } from './builder-document.js';

// ============================================================================
// API DTO FORMAT
// ============================================================================

/**
 * BuilderDocument format for API responses.
 * Optimized for network transfer with minimal nesting.
 */
export interface BuilderDocumentAPIResponse {
  documentId: string;
  schemaVersion: string;
  owner: string;
  designSystemId?: string;
  designSystemName?: string;
  nodes: Array<{
    id: string;
    contractName: string;
    props: Record<string, unknown>;
    slots: Record<string, string[]>;
    bindings: Array<{
      id: string;
      type: string;
      source: string;
      target: string;
      transform?: string;
    }>;
    metadata: {
      name?: string;
      position?: { x: number; y: number };
      size?: { width: number; height: number };
    };
  }>;
  layout: {
    type: string;
    rootId: string;
    nodes: Record<string, { type: string; children: string[] }>;
  };
  metadata: {
    createdAt: string;
    updatedAt: string;
    author?: string;
    description?: string;
    tags?: string[];
  };
}

// ============================================================================
// STORAGE DTO FORMAT
// ============================================================================

/**
 * BuilderDocument format for database storage.
 * Optimized for storage with indexed fields.
 */
export interface BuilderDocumentStorageDTO {
  _id: string;
  documentId: string;
  schemaVersion: string;
  owner: string;
  designSystemId?: string;
  document: BuilderDocument;
  indexedFields: {
    author: string;
    createdAt: Date;
    updatedAt: Date;
    tags: string[];
    nodeCount: number;
    componentTypes: string[];
  };
}

// ============================================================================
// ADAPTER IMPLEMENTATION
// ============================================================================

/**
 * Adapter for converting BuilderDocument to/from API format.
 */
export class BuilderDocumentAdapter {
  /**
   * Convert canonical BuilderDocument to API response format.
   */
  toAPIResponse(document: BuilderDocument): BuilderDocumentAPIResponse {
    // Extra fields (designSystemId/Name) are not in the canonical type but may
    // be present on documents that pre-date the current schema.
    const extra = document as unknown as Record<string, unknown>;
    return {
      documentId: document.documentId,
      schemaVersion: document.schemaVersion,
      owner: document.owner,
      designSystemId: extra['designSystemId'] as string | undefined,
      designSystemName: extra['designSystemName'] as string | undefined,
      nodes: Object.entries(document.nodes).map(([id, instance]) => ({
        id,
        contractName: instance.contractName,
        props: instance.props,
        slots: instance.slots,
        bindings: instance.bindings,
        metadata: {
          name: instance.metadata.name,
          position: instance.metadata.position,
          size: instance.metadata.size,
        },
      })),
      layout: {
        type: document.layout.type,
        rootId: document.layout.rootId,
        nodes: document.layout.nodes as BuilderDocumentAPIResponse['layout']['nodes'],
      },
      metadata: {
        createdAt: document.metadata.createdAt,
        updatedAt: document.metadata.updatedAt,
        author: document.metadata.author,
        description: document.metadata.description,
        tags: document.metadata.tags ? [...document.metadata.tags] : undefined,
      },
    };
  }

  /**
   * Convert API response format to canonical BuilderDocument.
   */
  fromAPIResponse(api: BuilderDocumentAPIResponse): BuilderDocument {
    const nodes: Record<NodeId, ComponentInstance> = {};
    
    for (const node of api.nodes) {
      nodes[node.id as NodeId] = {
        id: node.id as NodeId,
        contractName: node.contractName,
        props: node.props,
        slots: node.slots as Record<string, NodeId[]>,
        bindings: node.bindings as Binding[],
        metadata: {
          name: node.metadata.name,
          position: node.metadata.position,
          size: node.metadata.size,
        },
      };
    }

    return {
      documentId: api.documentId as DocumentId,
      schemaVersion: api.schemaVersion as '1.0.0',
      owner: api.owner,
      root: api.layout.rootId as NodeId,
      nodes,
      bindings: [],
      layout: {
        type: api.layout.type as BuilderDocument['layout']['type'],
        rootId: api.layout.rootId as NodeId,
        nodes: api.layout.nodes as BuilderDocument['layout']['nodes'],
      },
      metadata: {
        createdAt: api.metadata.createdAt,
        updatedAt: api.metadata.updatedAt,
        author: api.metadata.author,
        description: api.metadata.description,
        tags: api.metadata.tags,
      },
    };
  }

  /**
   * Convert canonical BuilderDocument to storage format.
   */
  toStorageDTO(document: BuilderDocument): BuilderDocumentStorageDTO {
    const componentTypes = new Set<string>();
    for (const instance of Object.values(document.nodes)) {
      componentTypes.add(instance.contractName);
    }
    const extra = document as unknown as Record<string, unknown>;

    return {
      _id: document.documentId,
      documentId: document.documentId,
      schemaVersion: document.schemaVersion,
      owner: document.owner,
      designSystemId: extra['designSystemId'] as string | undefined,
      document: document,
      indexedFields: {
        author: document.metadata.author ?? document.owner,
        createdAt: new Date(document.metadata.createdAt),
        updatedAt: new Date(document.metadata.updatedAt),
        tags: document.metadata.tags ? [...document.metadata.tags] : [],
        nodeCount: Object.keys(document.nodes).length,
        componentTypes: Array.from(componentTypes),
      },
    };
  }

  /**
   * Convert storage format to canonical BuilderDocument.
   */
  fromStorageDTO(dto: BuilderDocumentStorageDTO): BuilderDocument {
    const document = dto.document;
    const validation = validateBuilderDocument(document);
    
    if (!validation.valid) {
      throw new Error(
        `Invalid document in storage: ${validation.errors.map(e => e.message).join(', ')}`,
      );
    }

    return document;
  }

  /**
   * Convert legacy format to canonical BuilderDocument.
   * Handles migration from older document formats.
   */
  fromLegacyFormat(legacy: Record<string, unknown>): BuilderDocument {
    // Handle legacy format without schemaVersion
    if (!legacy.schemaVersion) {
      return this.migrateLegacyDocument(legacy);
    }

    // Handle legacy schema versions
    switch (legacy.schemaVersion) {
      case '0.9.0':
        return this.migrateFrom09(legacy);
      default:
        // Assume current format
        return legacy as BuilderDocument;
    }
  }

  /**
   * Migrate document without schema version (pre-0.9.0).
   */
  private migrateLegacyDocument(legacy: Record<string, unknown>): BuilderDocument {
    const legacyMeta = (legacy['metadata'] ?? {}) as Record<string, unknown>;
    const defaultLayout: BuilderDocument['layout'] = {
      type: 'flex',
      rootId: 'root' as NodeId,
      nodes: {
        root: { id: 'root' as NodeId, type: 'root', children: [] },
      },
    };
    return {
      documentId: ((legacy['documentId'] as string | undefined) ?? crypto.randomUUID()) as DocumentId,
      schemaVersion: '1.0.0',
      owner: (legacy['owner'] as string | undefined) ?? 'system',
      root: 'root' as NodeId,
      nodes: (legacy['nodes'] as Record<NodeId, ComponentInstance> | undefined) ?? {},
      bindings: (legacy['bindings'] as Binding[] | undefined) ?? [],
      layout: (legacy['layout'] as BuilderDocument['layout'] | undefined) ?? defaultLayout,
      metadata: {
        createdAt: (legacyMeta['createdAt'] as string | undefined) ?? new Date().toISOString(),
        updatedAt: (legacyMeta['updatedAt'] as string | undefined) ?? new Date().toISOString(),
        author: legacyMeta['author'] as string | undefined,
        description: legacyMeta['description'] as string | undefined,
        tags: legacyMeta['tags'] as string[] | undefined,
      },
    };
  }

  /**
   * Migrate from schema version 0.9.0 to 1.0.0.
   */
  private migrateFrom09(legacy: Record<string, unknown>): BuilderDocument {
    const rawNodes = (legacy['nodes'] ?? {}) as Record<string, ComponentInstance>;
    
    // Ensure each node has required metadata fields (non-mutating)
    const migratedNodes: Record<NodeId, ComponentInstance> = {};
    for (const [nodeId, node] of Object.entries(rawNodes)) {
      migratedNodes[nodeId as NodeId] = {
        ...node,
        metadata: {
          locked: false,
          hidden: false,
          ...node.metadata,
        },
      };
    }

    return {
      ...legacy,
      nodes: migratedNodes,
      schemaVersion: '1.0.0',
    } as BuilderDocument;
  }

  /**
   * Validate that a document can be converted to the target format.
   */
  validateConversion(
    document: BuilderDocument,
    targetFormat: 'api' | 'storage',
  ): { valid: boolean; errors: string[] } {
    const errors: string[] = [];
    const validation = validateBuilderDocument(document);

    if (!validation.valid) {
      errors.push(...validation.errors.map(e => e.message));
    }

    // Format-specific validation
    if (targetFormat === 'api') {
      // Check for circular references that can't be serialized
      try {
        JSON.stringify(this.toAPIResponse(document));
      } catch (err) {
        errors.push(`Document cannot be serialized to API format: ${err instanceof Error ? err.message : String(err)}`);
      }
    }

    if (targetFormat === 'storage') {
      // Check for indexed field requirements
      if (!document.metadata.author) {
        errors.push('Document must have author metadata for storage indexing');
      }
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }

  /**
   * Create a shallow copy of a document for editing.
   * Useful for optimistic updates.
   */
  createEditableCopy(document: BuilderDocument): BuilderDocument {
    return JSON.parse(JSON.stringify(document)) as BuilderDocument;
  }

  /**
   * Merge changes from an edited copy back to the original.
   * Only applies changes that were actually modified.
   */
  mergeChanges(
    original: BuilderDocument,
    edited: BuilderDocument,
  ): BuilderDocument {
    // Simple merge - in production, you'd want more sophisticated conflict resolution
    return {
      ...original,
      nodes: { ...edited.nodes },
      layout: { ...edited.layout },
      metadata: {
        ...edited.metadata,
        updatedAt: new Date().toISOString(),
      },
    };
  }
}

// ============================================================================
// SINGLETON INSTANCE
// ============================================================================

export const builderDocumentAdapter = new BuilderDocumentAdapter();
