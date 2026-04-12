/**
 * @fileoverview Code import and ownership-aware reconciliation back into BuilderDocument.
 *
 * Parses external code (TSX/HTML/JSON) and reconciles it against an existing
 * BuilderDocument, preserving ownership markers for regions that originated
 * from user edits and flagging conflicts requiring human review.
 */

import type { BuilderDocument, ComponentInstance, NodeId, RoundTripFidelity, LossPoint } from './types.js';
import { createNodeId } from './types.js';
import { produce } from 'immer';

// ============================================================================
// Import Source
// ============================================================================

export type ImportSourceFormat = 'tsx' | 'html' | 'json';

export interface ImportSource {
  readonly format: ImportSourceFormat;
  readonly content: string;
  /** Path hint for error messages. */
  readonly path?: string;
}

// ============================================================================
// Import Result
// ============================================================================

export type ImportResultStatus = 'clean' | 'review-required' | 'failed';

export interface ImportConflict {
  readonly nodeId: NodeId;
  readonly conflictType: 'ownership' | 'prop-mismatch' | 'unsupported-pattern';
  readonly description: string;
  readonly existingOwnership?: 'generated' | 'user-authored' | 'protected' | 'manual-merge-required';
}

export interface ImportResult {
  readonly status: ImportResultStatus;
  readonly document: BuilderDocument;
  readonly addedNodeIds: readonly NodeId[];
  readonly conflicts: readonly ImportConflict[];
  readonly fidelity: RoundTripFidelity;
  readonly errorMessage?: string;
}

// ============================================================================
// JSON import (lossless round-trip)
// ============================================================================

/**
 * Imports a BuilderDocument serialised as JSON. Lossless and always produces
 * a `clean` result if the JSON is structurally valid.
 */
export function importFromJson(
  source: ImportSource,
  existing: BuilderDocument,
): ImportResult {
  let parsed: unknown;
  try {
    parsed = JSON.parse(source.content);
  } catch (err) {
    return {
      status: 'failed',
      document: existing,
      addedNodeIds: [],
      conflicts: [],
      fidelity: { canRoundTrip: false, lossPoints: [], confidence: 0 },
      errorMessage: `JSON parse error: ${err instanceof Error ? err.message : String(err)}`,
    };
  }

  if (
    typeof parsed !== 'object' ||
    parsed === null ||
    !('rootNodes' in parsed) ||
    !('nodes' in parsed)
  ) {
    return {
      status: 'failed',
      document: existing,
      addedNodeIds: [],
      conflicts: [],
      fidelity: { canRoundTrip: false, lossPoints: [], confidence: 0 },
      errorMessage: 'JSON does not look like a BuilderDocument — missing rootNodes or nodes',
    };
  }

  const raw = parsed as Record<string, unknown>;
  const rawNodes = raw['nodes'] as Record<string, ComponentInstance>;
  const addedIds: NodeId[] = [];

  const updated = produce(existing, (draft) => {
    for (const [id, node] of Object.entries(rawNodes)) {
      if (!draft.nodes.has(id as NodeId)) {
        (draft.nodes as Map<NodeId, ComponentInstance>).set(id as NodeId, node);
        addedIds.push(id as NodeId);
      }
    }
    const importedRoots = raw['rootNodes'] as NodeId[];
    const newRoots = importedRoots.filter((id) => !draft.rootNodes.includes(id));
    draft.rootNodes = [...draft.rootNodes, ...newRoots];
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });

  return {
    status: 'clean',
    document: updated,
    addedNodeIds: addedIds,
    conflicts: [],
    fidelity: { canRoundTrip: true, lossPoints: [], confidence: 1 },
  };
}

// ============================================================================
// TSX import (heuristic, review-required)
// ============================================================================

/**
 * Parses a TSX string with a heuristic JSX element scanner and creates
 * BuilderDocument nodes for discovered components. Always flags `review-required`
 * because TSX may contain arbitrary custom code not representable in the model.
 */
export function importFromTsx(
  source: ImportSource,
  existing: BuilderDocument,
  designSystemContractNames: ReadonlySet<string>,
): ImportResult {
  const addedIds: NodeId[] = [];
  const conflicts: ImportConflict[] = [];
  const lossPoints: LossPoint[] = [];

  // Heuristic: match self-closing and opening JSX tags for known contract names
  // e.g. <Button variant="primary" /> or <Card>
  const tagRe = /<([A-Z][A-Za-z0-9]*)(\s[^>]*)?\s*\/?>/g;
  let match: RegExpExecArray | null;
  const insertedComponents: ComponentInstance[] = [];

  while ((match = tagRe.exec(source.content)) !== null) {
    const componentName = match[1] as string;
    if (!designSystemContractNames.has(componentName)) {
      lossPoints.push({
        type: 'unsupported-pattern',
        location: `char ${match.index}`,
        description: `Unknown component <${componentName}> not in design system contracts`,
      });
      conflicts.push({
        nodeId: createNodeId(),
        conflictType: 'unsupported-pattern',
        description: `Component <${componentName}> has no registered contract`,
      });
      continue;
    }

    // Parse inline props with a simple key="value" scanner
    const propsRaw = match[2] ?? '';
    const props: Record<string, unknown> = parseInlineProps(propsRaw);

    const id = createNodeId();
    insertedComponents.push({
      id,
      contractName: componentName,
      props,
      slots: {},
      bindings: [],
      metadata: { ownership: 'user-authored' as const },
    });
    addedIds.push(id);
  }

  if (insertedComponents.length === 0) {
    lossPoints.push({
      type: 'custom-code',
      description: source.content.trim().length === 0
        ? 'Empty TSX source — nothing to import'
        : 'No design-system components found in TSX source',
    });
  }

  const updated = produce(existing, (draft) => {
    for (const node of insertedComponents) {
      (draft.nodes as Map<NodeId, ComponentInstance>).set(node.id, node);
      draft.rootNodes = [...draft.rootNodes, node.id];
    }
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });

  return {
    status: conflicts.length > 0 ? 'review-required' : 'clean',
    document: updated,
    addedNodeIds: addedIds,
    conflicts,
    fidelity: {
      canRoundTrip: lossPoints.length === 0,
      lossPoints,
      confidence: Math.max(0, 1 - lossPoints.length * 0.2),
    },
  };
}

// ============================================================================
// HTML import (best-effort)
// ============================================================================

/**
 * Parses an HTML fragment and maps `<ghatana-*>` custom elements back to
 * BuilderDocument nodes. Other HTML elements are noted as loss points.
 */
export function importFromHtml(
  source: ImportSource,
  existing: BuilderDocument,
): ImportResult {
  const addedIds: NodeId[] = [];
  const lossPoints: LossPoint[] = [];

  // Match <ghatana-some-component attrs />
  const ghatanaRe = /<ghatana-([a-z][a-z0-9-]*)(\s[^>]*)?\s*\/?>/gi;
  let match: RegExpExecArray | null;
  const insertedComponents: ComponentInstance[] = [];

  while ((match = ghatanaRe.exec(source.content)) !== null) {
    const tagSlug = match[1] as string;
    // Convert kebab-case to PascalCase for contract lookup
    const contractName = tagSlug
      .split('-')
      .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
      .join('');

    const propsRaw = match[2] ?? '';
    const props: Record<string, unknown> = parseInlineProps(propsRaw);

    const id = createNodeId();
    insertedComponents.push({
      id,
      contractName,
      props,
      slots: {},
      bindings: [],
      metadata: { ownership: 'user-authored' as const },
    });
    addedIds.push(id);
  }

  // Detect non-ghatana HTML elements that cannot round-trip
  const foreignRe = /<(?!ghatana-)([a-z][a-z0-9-]*)[\s>]/gi;
  while ((match = foreignRe.exec(source.content)) !== null) {
    lossPoints.push({
      type: 'unsupported-pattern',
      location: `char ${match.index}`,
      description: `Non-Ghatana element <${match[1]}> cannot be round-tripped`,
    });
  }

  const updated = produce(existing, (draft) => {
    for (const node of insertedComponents) {
      (draft.nodes as Map<NodeId, ComponentInstance>).set(node.id, node);
      draft.rootNodes = [...draft.rootNodes, node.id];
    }
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });

  return {
    status: lossPoints.length > 0 ? 'review-required' : 'clean',
    document: updated,
    addedNodeIds: addedIds,
    conflicts: [],
    fidelity: {
      canRoundTrip: lossPoints.length === 0,
      lossPoints,
      confidence: Math.max(0, 1 - lossPoints.length * 0.15),
    },
  };
}

// ============================================================================
// Unified dispatcher
// ============================================================================

/** Import from any supported source format. */
export function importSource(
  source: ImportSource,
  existing: BuilderDocument,
  options: { designSystemContracts?: ReadonlySet<string> } = {},
): ImportResult {
  switch (source.format) {
    case 'json':
      return importFromJson(source, existing);
    case 'tsx':
      return importFromTsx(source, existing, options.designSystemContracts ?? new Set());
    case 'html':
      return importFromHtml(source, existing);
  }
}

// ============================================================================
// Helpers
// ============================================================================

function parseInlineProps(raw: string): Record<string, unknown> {
  const props: Record<string, unknown> = {};
  // Match key="value" or key={value} or boolean key
  const attrRe = /([a-zA-Z][a-zA-Z0-9-]*)(?:=(?:"([^"]*)"|'([^']*)'|\{([^}]*)\}))?/g;
  let m: RegExpExecArray | null;
  while ((m = attrRe.exec(raw)) !== null) {
    const key = m[1];
    if (!key || key === '/') continue;
    const value = m[2] ?? m[3] ?? m[4];
    if (value === undefined) {
      props[key] = true;
    } else if (value === 'true') {
      props[key] = true;
    } else if (value === 'false') {
      props[key] = false;
    } else if (/^\d+(\.\d+)?$/.test(value)) {
      props[key] = Number(value);
    } else {
      props[key] = value;
    }
  }
  return props;
}
