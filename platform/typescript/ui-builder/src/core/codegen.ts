/**
 * @fileoverview React code generation from BuilderDocument.
 */

import type { BuilderComponentManifest, ComponentContract } from '@ghatana/ds-schema';
import type {
  BuilderDocument,
  NodeId,
  ComponentInstance,
  CodeProjection,
  CodeFile,
  CodeRegionOwnership,
  RoundTripFidelity,
  LossPoint,
} from './types';
import {
  projectDocumentToPlatformPlan,
  type BuilderPlatformNodePlan,
} from './platform-plan';

// ============================================================================
// Code Generation
// ============================================================================

export interface GenerateOptions {
  readonly format: 'functional' | 'class';
  readonly typescript: boolean;
  readonly importPath: string;
  readonly componentName: string;
  readonly manifests?: ReadonlyMap<string, BuilderComponentManifest> | readonly BuilderComponentManifest[];
}

export function generateReactCode(
  document: BuilderDocument,
  contracts: ReadonlyMap<string, ComponentContract>,
  options: GenerateOptions,
): CodeProjection {
  const lossPoints: LossPoint[] = [];

  // Contract-aware round-trip loss analysis across all nodes in the document.
  for (const [nodeId, instance] of document.nodes) {
    // 1. Missing contract — generated code will fall back to heuristic tag names.
    if (!contracts.has(instance.contractName)) {
      lossPoints.push({
        type: 'unsupported-pattern',
        location: nodeId,
        description: `Node "${instance.contractName}" (${nodeId}) has no registered contract — generated code may be incomplete`,
      });
    }

    // 2. Node ownership: user-authored or manual-merge-required content cannot
    //    be fully recovered from static JSX output on re-import.
    const nodeOwnership = instance.metadata.ownership;
    if (nodeOwnership === 'user-authored' || nodeOwnership === 'manual-merge-required') {
      lossPoints.push({
        type: 'custom-code',
        location: nodeId,
        description: `Node "${instance.contractName}" (${nodeId}) has user-authored code — manual reconciliation required on re-import`,
      });
    } else if (nodeOwnership === 'protected') {
      lossPoints.push({
        type: 'custom-code',
        location: nodeId,
        description: `Node "${instance.contractName}" (${nodeId}) is protected — changes require explicit unlock before re-import`,
      });
    }

    // 3. Active data bindings are dynamic and cannot be encoded in static JSX.
    if (instance.bindings.length > 0) {
      lossPoints.push({
        type: 'unsupported-pattern',
        location: nodeId,
        description: `Node "${instance.contractName}" (${nodeId}) has ${instance.bindings.length} data binding(s) — dynamic bindings are not represented in generated static code`,
      });
    }

    // 4. State variants are interaction-driven and not emitted into static code.
    if (instance.metadata.stateVariants && instance.metadata.stateVariants.length > 0) {
      lossPoints.push({
        type: 'unsupported-pattern',
        location: nodeId,
        description: `Node "${instance.contractName}" (${nodeId}) has state variants that are not encoded in generated code`,
      });
    }

    // 5. Responsive variants may require media-query wrappers not yet emitted.
    if (instance.metadata.responsiveVariants && instance.metadata.responsiveVariants.length > 0) {
      lossPoints.push({
        type: 'unsupported-pattern',
        location: nodeId,
        description: `Node "${instance.contractName}" (${nodeId}) has responsive variants that may not be fully represented in generated code`,
      });
    }

    // 6. Heuristic: local/custom import paths signal components whose source is
    //    not managed by the design system registry.
    const contract = contracts.get(instance.contractName);
    if (contract?.builder?.codegen?.importPath.includes('custom')) {
      lossPoints.push({
        type: 'custom-code',
        location: nodeId,
        description: `Custom code in ${instance.contractName} may not round-trip`,
      });
    }
  }

  const { file, nodeOwnership } = generateComponentFile(document, contracts, options);

  // Confidence decays per loss point, weighted by severity.
  const confidence = Math.max(
    0,
    lossPoints.reduce((acc, lp) => {
      if (lp.type === 'custom-code') return acc - 0.2;
      if (lp.type === 'unsupported-pattern') return acc - 0.15;
      return acc - 0.1;
    }, 1.0),
  );

  const roundTripFidelity: RoundTripFidelity = {
    canRoundTrip: lossPoints.length === 0,
    lossPoints,
    confidence,
  };

  return {
    language: options.typescript ? 'tsx' : 'jsx',
    files: [file],
    ownership: nodeOwnership,
    roundTripFidelity,
  };
}

/**
 * Collects all node IDs in the subtree rooted at `nodeId`, in depth-first
 * order. Guards against circular references via the `visited` set.
 */
function collectSubtreeNodeIds(
  nodeId: NodeId,
  document: BuilderDocument,
  visited: Set<NodeId> = new Set(),
): NodeId[] {
  if (visited.has(nodeId)) return [];
  visited.add(nodeId);
  const instance = document.nodes.get(nodeId);
  if (!instance) return [];
  const result: NodeId[] = [nodeId];
  for (const slotChildren of Object.values(instance.slots)) {
    for (const childId of slotChildren) {
      result.push(...collectSubtreeNodeIds(childId, document, visited));
    }
  }
  return result;
}

function generateComponentFile(
  document: BuilderDocument,
  contracts: ReadonlyMap<string, ComponentContract>,
  options: GenerateOptions,
): { file: CodeFile; nodeOwnership: CodeRegionOwnership[] } {
  const lines: string[] = [];
  const nodeOwnership: CodeRegionOwnership[] = [];

  // Imports
  lines.push("import * as React from 'react';");
  const platformPlan = projectDocumentToPlatformPlan(document, contracts, options.manifests);
  if (platformPlan.targets.length > 0) {
    lines.push(`// Builder platform targets: ${platformPlan.targets.join(', ')}`);
  }
  
  // Collect component imports — group by importPath, collecting component names from contracts
  type ImportSpec = { names: Set<string>; namedExport: boolean };
  const importMap = new Map<string, ImportSpec>();
  for (const instance of document.nodes.values()) {
    const contract = contracts.get(instance.contractName);
    const codegen = contract?.builder?.codegen;
    if (!codegen?.importPath) continue;
    const spec = importMap.get(codegen.importPath) ?? { names: new Set<string>(), namedExport: codegen.namedExport ?? true };
    spec.names.add(codegen.componentName);
    importMap.set(codegen.importPath, spec);
  }

  for (const [importPath, spec] of importMap) {
    const names = Array.from(spec.names).join(', ');
    if (spec.namedExport) {
      lines.push(`import { ${names} } from '${importPath}';`);
    } else {
      // default export — emit one import per name (each is a separate default export)
      for (const name of spec.names) {
        lines.push(`import ${name} from '${importPath}';`);
      }
    }
  }

  lines.push('');

  // Component definition
  const componentName = options.componentName;
  lines.push(`export interface ${componentName}Props {`);
  lines.push('  readonly className?: string;');
  lines.push('}');
  lines.push('');
  lines.push(`export const ${componentName}: React.FC<${componentName}Props> = ({`);
  lines.push('  className,');
  lines.push('}) => {');
  lines.push('  return (');

  // Generate JSX for root nodes — track per-root-node line ranges for ownership.
  for (const rootId of document.rootNodes) {
    const rootInstance = document.nodes.get(rootId);
    if (rootInstance) {
      // lineStart is 1-based; capture current line count before adding JSX.
      const linesBefore = lines.length + 1;
      const jsx = generateNodeJSX(rootInstance, document, contracts, platformPlan.nodes, 2);
      lines.push(...jsx.map((l) => '    ' + l));
      const linesAfter = lines.length;

      const subtreeIds = collectSubtreeNodeIds(rootId, document);
      nodeOwnership.push({
        region: `node-${rootId}`,
        type: 'builder-generated',
        lineStart: linesBefore,
        lineEnd: linesAfter,
        builderNodeIds: subtreeIds,
      });
    }
  }

  // Fall back to a whole-component region when the document has no root nodes.
  if (nodeOwnership.length === 0) {
    nodeOwnership.push({
      region: 'component',
      type: 'builder-generated',
      lineStart: 1,
      lineEnd: lines.length,
      builderNodeIds: [],
    });
  }

  lines.push('  );');
  lines.push('};');
  lines.push('');
  lines.push(`export default ${componentName};`);

  return {
    file: {
      path: `${componentName}.tsx`,
      content: lines.join('\n'),
      ownership: {
        region: 'file',
        type: 'builder-generated',
        lineStart: 1,
        lineEnd: lines.length,
        builderNodeIds: Array.from(document.nodes.keys()),
      },
    },
    nodeOwnership,
  };
}

function generateNodeJSX(
  instance: ComponentInstance,
  document: BuilderDocument,
  contracts: ReadonlyMap<string, ComponentContract>,
  platformPlans: ReadonlyMap<NodeId, BuilderPlatformNodePlan>,
  indent: number,
  visited: Set<NodeId> = new Set(),
): string[] {
  // Cycle guard: if this node is already on the current rendering stack, emit a
  // placeholder comment and bail out rather than looping infinitely.
  if (visited.has(instance.id)) {
    const indentStr = '  '.repeat(indent);
    return [`${indentStr}{/* [WARN] Circular reference detected for node ${instance.id} - skipped */}`];
  }

  const contract = contracts.get(instance.contractName);
  const nodePlan = platformPlans.get(instance.id);
  const componentName = contract?.builder?.codegen?.componentName ?? instance.contractName;
  const lines: string[] = [];
  const indentStr = '  '.repeat(indent);

  // Mark this node as visited before descending into its children.
  const childVisited = new Set(visited);
  childVisited.add(instance.id);

  const namedSlots = (nodePlan?.slots ?? []).filter(
    (slot) => slot.exposure === 'prop' && slot.childIds.length > 0,
  );
  const defaultSlot = nodePlan?.slots.find((slot) => slot.exposure === 'children');
  const defaultSlotChildIds = defaultSlot?.childIds ?? [];
  const bodyLines = generateDefaultSlotBody(
    instance,
    document,
    contracts,
    platformPlans,
    defaultSlotChildIds,
    indent + 1,
    childVisited,
  );
  const inlineProps = buildInlineProps(instance, defaultSlotChildIds.length === 0);

  if (namedSlots.length === 0 && bodyLines.length === 0) {
    const props = inlineProps.length > 0 ? ` ${inlineProps.join(' ')}` : '';
    lines.push(`${indentStr}<${componentName}${props} />`);
    return lines;
  }

  lines.push(`${indentStr}<${componentName}`);
  for (const prop of inlineProps) {
    lines.push(`${indentStr}  ${prop}`);
  }
  for (const slotPlan of namedSlots) {
    lines.push(...generateNamedSlotPropLines(
      slotPlan,
      document,
      contracts,
      platformPlans,
      indent + 1,
      childVisited,
    ));
  }

  if (bodyLines.length === 0) {
    if (namedSlots.length === 0) {
      lines.push(`${indentStr}/>`);
      return lines;
    }

    lines.push(`${indentStr}>`);
    for (const slotPlan of namedSlots) {
      lines.push(`${indentStr}  {/* ${slotPlan.name} slot */}`);
    }
    lines.push(`${indentStr}</${componentName}>`);
    return lines;
  }

  lines.push(`${indentStr}>`);
  lines.push(...bodyLines);
  lines.push(`${indentStr}</${componentName}>`);
  return lines;
}

function buildInlineProps(
  instance: ComponentInstance,
  includeChildrenProp: boolean,
): string[] {
  const props: string[] = [];

  for (const [key, value] of Object.entries(instance.props)) {
    if (key === 'children' && !includeChildrenProp) continue;
    if (typeof value === 'string') {
      props.push(`${key}=${JSON.stringify(value)}`);
    } else if (typeof value === 'number' || typeof value === 'boolean') {
      props.push(`${key}={${value}}`);
    } else {
      props.push(`${key}={${JSON.stringify(value)}}`);
    }
  }

  return props;
}

function generateNamedSlotPropLines(
  slotPlan: BuilderPlatformNodePlan['slots'][number],
  document: BuilderDocument,
  contracts: ReadonlyMap<string, ComponentContract>,
  platformPlans: ReadonlyMap<NodeId, BuilderPlatformNodePlan>,
  indent: number,
  visited: Set<NodeId>,
): string[] {
  const indentStr = '  '.repeat(indent);
  const valueLines = generateSlotValueLines(
    slotPlan.childIds,
    document,
    contracts,
    platformPlans,
    indent + 1,
    visited,
  );

  return [
    `${indentStr}${slotPlan.name}={`,
    ...valueLines,
    `${indentStr}}`,
  ];
}

function generateSlotValueLines(
  childIds: readonly NodeId[],
  document: BuilderDocument,
  contracts: ReadonlyMap<string, ComponentContract>,
  platformPlans: ReadonlyMap<NodeId, BuilderPlatformNodePlan>,
  indent: number,
  visited: Set<NodeId>,
): string[] {
  if (childIds.length === 1) {
    const child = document.nodes.get(childIds[0]);
    return child
      ? generateNodeJSX(child, document, contracts, platformPlans, indent, visited)
      : [`${'  '.repeat(indent)}{null}`];
  }

  const indentStr = '  '.repeat(indent);
  const lines = [`${indentStr}<>`];
  for (const childId of childIds) {
    const child = document.nodes.get(childId);
    if (child) {
      lines.push(...generateNodeJSX(child, document, contracts, platformPlans, indent + 1, visited));
    }
  }
  lines.push(`${indentStr}</>`);
  return lines;
}

function generateDefaultSlotBody(
  instance: ComponentInstance,
  document: BuilderDocument,
  contracts: ReadonlyMap<string, ComponentContract>,
  platformPlans: ReadonlyMap<NodeId, BuilderPlatformNodePlan>,
  childIds: readonly NodeId[],
  indent: number,
  visited: Set<NodeId>,
): string[] {
  const lines: string[] = [];
  const indentStr = '  '.repeat(indent);

  if (childIds.length > 0 && typeof instance.props.children === 'string') {
    lines.push(`${indentStr}{${JSON.stringify(instance.props.children)}}`);
  }

  for (const childId of childIds) {
    const child = document.nodes.get(childId);
    if (child) {
      lines.push(...generateNodeJSX(child, document, contracts, platformPlans, indent, visited));
    }
  }

  return lines;
}
