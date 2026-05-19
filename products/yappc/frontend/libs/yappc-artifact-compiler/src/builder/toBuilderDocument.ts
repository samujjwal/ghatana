/**
 * @fileoverview Converts a `SemanticProductModel` produced by the
 * `SynthesisEngine` into a `BuilderDocument` consumable by the
 * `@ghatana/ui-builder` canvas.
 *
 * Design constraints:
 * - One `PageModel` becomes the document root. If multiple pages exist,
 *   the first page (alphabetically by routePath) is used as root; all others
 *   are surfaced as residual metadata in the document's description.
 * - Each `ComponentModel` referenced by a page becomes a `ComponentInstance`
 *   in the document's node map.
 * - Components not referenced by any page are appended as orphan nodes and
 *   reported in the `lossPoints` of the round-trip fidelity record.
 * - Token / theme / style / data / API / state models are NOT added as
 *   ComponentInstance nodes — they surface in the `designSystem` field or
 *   as metadata only.
 *
 * @doc.type module
 * @doc.purpose SemanticProductModel → BuilderDocument converter
 * @doc.layer product
 * @doc.pattern Adapter
 */

import type {
  BuilderDocument,
  ComponentInstance,
  DesignSystemModel,
  DocumentMetadata,
  InstanceMetadata,
  NodeId,
  DocumentId,
  LossPoint,
  RoundTripFidelity,
} from '@ghatana/ui-builder';
import { attachBuilderDocumentCompatibility, createBuilderDocument } from '@ghatana/ui-builder';
import { ComponentContractSchema } from '@ghatana/ds-schema';
import type { ComponentContract } from '@ghatana/ds-schema';
import type { SemanticProductModel, ComponentModel, PageModel, TokenModel, ThemeModel, PropSchema } from '../model/types';

// ============================================================================
// Public API
// ============================================================================

/** Result of the conversion — the BuilderDocument plus diagnostic info. */
export interface ToBuilderDocumentResult {
  readonly document: BuilderDocument;
  /** Pages that were not used as the root page (converted to metadata). */
  readonly additionalPages: readonly PageModel[];
  /** Round-trip fidelity info for the full document. */
  readonly roundTripFidelity: RoundTripFidelity;
}

/**
 * Converts a `SemanticProductModel` into a `BuilderDocument`.
 *
 * @throws {Error} if the model has no pages and no components (empty document).
 */
export function toBuilderDocument(model: SemanticProductModel): ToBuilderDocumentResult {
  const components = extractComponents(model);
  const pages = extractPages(model);
  const tokens = extractTokens(model);
  const themes = extractThemes(model);

  // Pick root page (first by routePath lexicographic order; fallback to synthetic root)
  const sortedPages = [...pages].sort((a, b) => a.routePath.localeCompare(b.routePath));
  const rootPage = sortedPages[0] ?? null;
  const additionalPages = rootPage ? sortedPages.slice(1) : [];

  // Build the component index for fast lookup
  const componentById = new Map<string, ComponentModel>();
  for (const comp of components) {
    componentById.set(comp.id, comp);
  }

  // Determine which component IDs are referenced by the root page
  const rootPageComponentIds = new Set<string>(rootPage?.componentIds ?? []);

  // Build node map (root page components first, then orphans)
  const nodes: Record<string, ComponentInstance> = {};
  const orphanNodeIds: NodeId[] = [];
  const lossPoints: LossPoint[] = [];

  // Always include all components (root-referenced + orphans)
  for (const comp of components) {
    const instance = componentModelToInstance(comp);
    nodes[instance.id] = instance;
    if (!rootPageComponentIds.has(comp.id)) {
      orphanNodeIds.push(instance.id);
    }
  }

  if (orphanNodeIds.length > 0) {
    lossPoints.push({
      type: 'unsupported-pattern',
      location: orphanNodeIds.join(', '),
      description: `${orphanNodeIds.length} component(s) not referenced by any page — appended as orphan nodes.`,
    });
  }

  if (additionalPages.length > 0) {
    lossPoints.push({
      type: 'unsupported-pattern',
      description: `${additionalPages.length} additional page(s) not mapped to BuilderDocument root: ${additionalPages.map((p) => p.routePath).join(', ')}.`,
    });
  }

  // Root nodes = the page's component IDs that exist in our map, in order
  const rootNodes: NodeId[] = rootPage
    ? rootPage.componentIds.filter((id) => nodes[id]).map((id) => id as NodeId)
    : (components.length > 0 ? [components[0]!.id as NodeId] : []);

  const designSystem = buildDesignSystem(tokens, themes, components);

  const documentName = rootPage
    ? (rootPage.seoMetadata?.title ?? rootPage.routePath)
    : model.repositoryRoot.split('/').pop() ?? 'Unnamed';

  const metadata: DocumentMetadata = {
    createdAt: model.createdAt,
    updatedAt: model.updatedAt,
    description: rootPage
      ? `${documentName}: converted from SemanticProductModel ${model.id}. Route: ${rootPage.routePath}.`
      : `${documentName}: converted from SemanticProductModel ${model.id}. No page found.`,
    tags: rootPage
      ? [rootPage.routePath, `v${model.version}`, ...rootPage.tags]
      : [`v${model.version}`],
  };

  const roundTripFidelity: RoundTripFidelity = {
    canRoundTrip: lossPoints.length === 0,
    lossPoints,
    confidence: lossPoints.length === 0 ? 1 : Math.max(0.3, 1 - lossPoints.length * 0.2),
  };

  const baseDocument = createBuilderDocument(model.repositoryRoot, {
    documentId: model.id as DocumentId,
  });
  const rootLayoutNode = baseDocument.layout.nodes[baseDocument.layout.rootId] ?? {
    id: baseDocument.layout.rootId,
    type: 'root' as const,
    layout: 'flex' as const,
    children: [],
  };

  const document: BuilderDocument = {
    ...baseDocument,
    nodes: {
      ...baseDocument.nodes,
      ...nodes,
    },
    layout: {
      ...baseDocument.layout,
      nodes: {
        ...baseDocument.layout.nodes,
        [baseDocument.layout.rootId]: {
          ...rootLayoutNode,
          children: rootNodes,
        },
      },
    },
    metadata,
  };

  const compatibleDocument = attachBuilderDocumentCompatibility(document);
  defineLegacyCompatProperty(compatibleDocument, 'version', String(model.version));
  defineLegacyCompatProperty(compatibleDocument, 'name', documentName);
  defineLegacyCompatProperty(compatibleDocument, 'designSystem', designSystem);

  return { document: compatibleDocument, additionalPages, roundTripFidelity };
}

// ============================================================================
// Private helpers
// ============================================================================

function extractComponents(model: SemanticProductModel): ComponentModel[] {
  return model.elements.filter((el): el is ComponentModel => el.kind === 'component');
}

function extractPages(model: SemanticProductModel): PageModel[] {
  return model.elements.filter((el): el is PageModel => el.kind === 'page');
}

function extractTokens(model: SemanticProductModel): TokenModel[] {
  return model.elements.filter((el): el is TokenModel => el.kind === 'token');
}

function extractThemes(model: SemanticProductModel): ThemeModel[] {
  return model.elements.filter((el): el is ThemeModel => el.kind === 'theme');
}

function componentModelToInstance(comp: ComponentModel): ComponentInstance {
  // Build props object with default values from the prop schemas
  const props: Record<string, unknown> = {};
  for (const propSchema of comp.props) {
    if (propSchema.defaultValue !== undefined) {
      props[propSchema.name] = propSchema.defaultValue;
    }
  }

  // Build slots map
  const slots: Record<string, NodeId[]> = {};
  for (const slotSchema of comp.slots) {
    slots[slotSchema.name] = [];
  }

  const instanceMetadata: InstanceMetadata = {
    name: comp.name,
    locked: false,
    hidden: false,
  };

  const instance: ComponentInstance = {
    id: comp.id as NodeId,
    contractName: comp.contractName,
    props,
    slots,
    bindings: [],
    metadata: instanceMetadata,
  };

  return instance;
}

function buildDesignSystem(
  tokens: TokenModel[],
  themes: ThemeModel[],
  components: ComponentModel[],
): DesignSystemModel {
  // Build component contracts from component models using ds-schema's schema
  // so that all required fields and defaults are properly applied.
  const componentContracts: ComponentContract[] = components.map((comp) => {
    return ComponentContractSchema.parse({
      name: comp.contractName,
      version: '1.0.0',
      description: comp.description,
      metadata: {
        category: inferCategory(comp),
        tags: comp.tags,
      },
      props: comp.props.map((p: PropSchema) => ({
        name: p.name,
        type: mapPropType(p.type),
        description: p.description,
        required: p.required,
        defaultValue: p.defaultValue,
      })),
      slots: comp.slots.map((s) => ({
        name: s.name,
        description: s.required ? 'Required slot' : undefined,
        builderMetadata: {
          required: s.required,
        },
      })),
      events: comp.events.map((e) => ({
        name: e.name,
        description: e.description,
      })),
    });
  });

  const primaryTheme = themes[0];
  const themeId = primaryTheme?.id ?? 'default-theme';

  return {
    id: 'ds-' + themeId,
    name: primaryTheme?.name ?? 'Default Design System',
    version: '1.0.0',
    tokenSetIds: tokens.map((t) => t.id),
    componentContracts,
    themeId,
  };
}

function defineLegacyCompatProperty<T>(
  target: BuilderDocument,
  propertyName: string,
  value: T,
): void {
  Object.defineProperty(target, propertyName, {
    get: () => value,
    configurable: true,
    enumerable: false,
  });
}

/** Infer a design-system category from the component's name or tags. */
function inferCategory(comp: ComponentModel): string {
  const lower = comp.contractName.toLowerCase();
  if (lower.includes('button') || lower.includes('input') || lower.includes('select') || lower.includes('field')) {
    return 'input';
  }
  if (lower.includes('card') || lower.includes('list') || lower.includes('table') || lower.includes('grid')) {
    return 'display';
  }
  if (lower.includes('nav') || lower.includes('menu') || lower.includes('header') || lower.includes('sidebar')) {
    return 'navigation';
  }
  if (lower.includes('modal') || lower.includes('dialog') || lower.includes('toast') || lower.includes('alert')) {
    return 'feedback';
  }
  if (lower.includes('layout') || lower.includes('container') || lower.includes('row') || lower.includes('column')) {
    return 'layout';
  }
  return 'display';
}

/**
 * Map a free-form type string from PropSchema to a valid PropType enum value.
 * Falls back to 'string' for unknown types.
 */
function mapPropType(
  type: string,
): 'string' | 'number' | 'boolean' | 'array' | 'object' | 'function' | 'node' | 'element' | 'enum' | 'union' | 'literal' | 'token-ref' | 'component-ref' {
  const normalized = type.toLowerCase();
  if (normalized === 'string') return 'string';
  if (normalized === 'number' || normalized === 'int' || normalized === 'float') return 'number';
  if (normalized === 'boolean' || normalized === 'bool') return 'boolean';
  if (normalized === 'array' || normalized.endsWith('[]')) return 'array';
  if (normalized === 'object' || normalized === 'record') return 'object';
  if (normalized === 'function' || normalized === 'fn') return 'function';
  if (normalized === 'reactnode' || normalized === 'node') return 'node';
  if (normalized === 'reactelement' || normalized === 'element') return 'element';
  if (normalized === 'enum') return 'enum';
  if (normalized === 'union') return 'union';
  if (normalized === 'literal') return 'literal';
  if (normalized.includes('token')) return 'token-ref';
  if (normalized.includes('component')) return 'component-ref';
  return 'string';
}
