/**
 * @fileoverview React code generation from BuilderDocument.
 */

import type { ComponentContract } from '@ghatana/ds-schema';
import type {
  BuilderDocument,
  ComponentInstance,
  NodeId,
  CodeProjection,
  CodeFile,
  CodeRegionOwnership,
  RoundTripFidelity,
  LossPoint,
} from './types';

// ============================================================================
// Code Generation
// ============================================================================

export interface GenerateOptions {
  readonly format: 'functional' | 'class';
  readonly typescript: boolean;
  readonly importPath: string;
  readonly componentName: string;
}

export function generateReactCode(
  document: BuilderDocument,
  contracts: ReadonlyMap<string, ComponentContract>,
  options: GenerateOptions,
): CodeProjection {
  const files: CodeFile[] = [];
  const lossPoints: LossPoint[] = [];
  const ownership: CodeRegionOwnership[] = [];

  // Generate main component
  const mainFile = generateComponentFile(document, contracts, options);
  files.push(mainFile);

  // Track ownership
  ownership.push({
    region: 'component',
    type: 'builder-generated',
    lineStart: 1,
    lineEnd: mainFile.content.split('\n').length,
    builderNodeIds: Array.from(document.nodes.keys()),
  });

  // Check for custom code that might be lost
  for (const [nodeId, instance] of document.nodes) {
    const contract = contracts.get(instance.contractName);
    if (contract?.builder?.codegen?.importPath.includes('custom')) {
      lossPoints.push({
        type: 'custom-code',
        location: nodeId,
        description: `Custom code in ${instance.contractName} may not round-trip`,
      });
    }
  }

  const roundTripFidelity: RoundTripFidelity = {
    canRoundTrip: lossPoints.length === 0,
    lossPoints,
    confidence: lossPoints.length === 0 ? 1.0 : 0.8,
  };

  return {
    language: options.typescript ? 'tsx' : 'jsx',
    files,
    ownership,
    roundTripFidelity,
  };
}

function generateComponentFile(
  document: BuilderDocument,
  contracts: ReadonlyMap<string, ComponentContract>,
  options: GenerateOptions,
): CodeFile {
  const lines: string[] = [];

  // Imports
  lines.push("import * as React from 'react';");
  
  // Collect component imports
  const imports = new Set<string>();
  for (const instance of document.nodes.values()) {
    const contract = contracts.get(instance.contractName);
    if (contract?.builder?.codegen?.importPath) {
      imports.add(contract.builder.codegen.importPath);
    }
  }
  
  for (const importPath of imports) {
    lines.push(`import { ${getComponentNameFromPath(importPath)} } from '${importPath}';`);
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

  // Generate JSX for root nodes
  for (const rootId of document.rootNodes) {
    const rootInstance = document.nodes.get(rootId);
    if (rootInstance) {
      const jsx = generateNodeJSX(rootInstance, document, contracts, 2);
      lines.push(...jsx.map((l) => '    ' + l));
    }
  }

  lines.push('  );');
  lines.push('};');
  lines.push('');
  lines.push(`export default ${componentName};`);

  return {
    path: `${componentName}.tsx`,
    content: lines.join('\n'),
    ownership: {
      region: 'file',
      type: 'builder-generated',
      lineStart: 1,
      lineEnd: lines.length,
      builderNodeIds: [],
    },
  };
}

function generateNodeJSX(
  instance: ComponentInstance,
  document: BuilderDocument,
  contracts: ReadonlyMap<string, ComponentContract>,
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
  const componentName = contract?.builder?.codegen?.componentName ?? instance.contractName;
  
  const lines: string[] = [];
  const indentStr = '  '.repeat(indent);
  
  // Mark this node as visited before descending into its children.
  const childVisited = new Set(visited);
  childVisited.add(instance.id);
  
  // Opening tag with props
  const props = generatePropsString(instance, contract);
  
  // Check for children
  const hasChildren = Object.values(instance.slots).some((s) => s.length > 0);
  
  if (hasChildren) {
    lines.push(`${indentStr}<${componentName}${props}>`);
    
    // Render children from slots
    for (const [slotName, childIds] of Object.entries(instance.slots)) {
      if (childIds.length === 0) continue;
      
      // If slot has a wrapper or is default
      if (slotName !== 'default') {
        lines.push(`${indentStr}  {/* ${slotName} slot */}`);
      }
      
      for (const childId of childIds) {
        const child = document.nodes.get(childId);
        if (child) {
          const childJsx = generateNodeJSX(child, document, contracts, indent + 1, childVisited);
          lines.push(...childJsx);
        }
      }
    }
    
    lines.push(`${indentStr}</${componentName}>`);
  } else {
    lines.push(`${indentStr}<${componentName}${props} />`);
  }
  
  return lines;
}

function generatePropsString(
  instance: ComponentInstance,
  contract: ComponentContract | undefined,
): string {
  const props: string[] = [];
  
  for (const [key, value] of Object.entries(instance.props)) {
    if (typeof value === 'string') {
      props.push(`${key}="${value}"`);
    } else if (typeof value === 'number' || typeof value === 'boolean') {
      props.push(`${key}={${value}}`);
    } else {
      props.push(`${key}={${JSON.stringify(value)}}`);
    }
  }
  
  if (props.length === 0) return '';
  return ' ' + props.join(' ');
}

function getComponentNameFromPath(path: string): string {
  const parts = path.split('/');
  return parts[parts.length - 1] ?? 'Component';
}
