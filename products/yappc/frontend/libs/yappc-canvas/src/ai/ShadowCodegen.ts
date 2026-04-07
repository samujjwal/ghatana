/**
 * Shadow Codegen
 *
 * Background code generation from canvas state.
 * Converts visual designs to code in multiple frameworks.
 *
 * @doc.type service
 * @doc.purpose Code generation from canvas
 * @doc.layer ai
 * @doc.pattern Strategy Pattern
 */

import type {
  UniversalNode,
  UniversalDocument,
  ArtifactContract,
  UniqueId,
} from '../model/contracts';
import { getArtifactRegistry } from '../registry/ArtifactRegistry';

export type CodegenTarget =
  | 'react'
  | 'react-native'
  | 'vue'
  | 'angular'
  | 'svelte'
  | 'html'
  | 'flutter'
  | 'swiftui';

export interface CodegenOptions {
  target: CodegenTarget;
  typescript?: boolean;
  includeStyles?: boolean;
  styleSystem?: 'css' | 'tailwind' | 'styled-components' | 'emotion' | 'inline';
  splitComponents?: boolean;
  includeComments?: boolean;
  componentMapping?: Record<string, string>;
  importMapping?: Record<string, string>;
  indentSize?: number;
  singleQuotes?: boolean;
}

export interface CodegenResult {
  mainCode: string;
  styleCode?: string;
  components?: Record<string, string>;
  imports: string[];
  dependencies: string[];
  warnings: string[];
}

/**
 *
 */
export class ShadowCodegen {
  private static instance: ShadowCodegen | null = null;
  private cache: Map<string, CodegenResult> = new Map();

  /**
   *
   */
  public static getInstance(): ShadowCodegen {
    if (!ShadowCodegen.instance) {
      ShadowCodegen.instance = new ShadowCodegen();
    }
    return ShadowCodegen.instance;
  }

  /**
   *
   */
  public generate(
    document: UniversalDocument,
    options: CodegenOptions
  ): CodegenResult {
    const cacheKey = `${document.id}:${JSON.stringify(options)}`;
    const cached = this.cache.get(cacheKey);
    if (cached) return cached;

    const registry = getArtifactRegistry();
    const warnings: string[] = [];
    const allNodes = Object.values(document.nodes);
    const imports: string[] = ["import React from 'react';"];

    // Generate code from root nodes
    const rootNodes = allNodes.filter((n) => n.parentId === null);
    const nodeCode = rootNodes
      .map((node) => {
        const contract = registry.get(node.kind);
        if (!contract) warnings.push(`No contract: ${node.kind}`);
        return this.generateReactNode(node, options, 1, document.nodes);
      })
      .join('\n');

    const mainCode = this.wrapReactComponent(
      nodeCode,
      this.toComponentName(document.name),
      imports,
      options
    );

    const result: CodegenResult = {
      mainCode,
      imports,
      dependencies: ['react'],
      warnings,
    };

    this.cache.set(cacheKey, result);
    return result;
  }

  /**
   *
   */
  public clearCache(): void {
    this.cache.clear();
  }

  /**
   *
   */
  private generateReactNode(
    node: UniversalNode,
    options: CodegenOptions,
    depth: number,
    nodesMap: Record<UniqueId, UniversalNode>
  ): string {
    const indent = ' '.repeat(depth * (options.indentSize ?? 2));
    const componentName = options.componentMapping?.[node.kind] ?? 'div';

    // Build children code by resolving child IDs to nodes
    const children = node.children
      .map((childId) => nodesMap[childId])
      .filter(Boolean)
      .map((child) =>
        this.generateReactNode(child, options, depth + 1, nodesMap)
      )
      .join('\n');

    const textContent =
      typeof node.content === 'string'
        ? node.content
        : node.props?.text || node.props?.label || '';

    if (children) {
      return `${indent}<${componentName}>\n${children}\n${indent}</${componentName}>`;
    } else if (textContent) {
      return `${indent}<${componentName}>${textContent}</${componentName}>`;
    } else {
      return `${indent}<${componentName} />`;
    }
  }

  /**
   *
   */
  private wrapReactComponent(
    code: string,
    name: string,
    imports: string[],
    options: CodegenOptions
  ): string {
    const ts = options.typescript;
    const propsType = ts ? `interface ${name}Props {}\n\n` : '';
    const propsArg = ts ? `props: ${name}Props` : 'props';

    return `${imports.join('\n')}\n\n${propsType}export const ${name}${ts ? ': React.FC<' + name + 'Props>' : ''} = (${propsArg}) => {\n  return (\n${code}\n  );\n};\n\nexport default ${name};\n`;
  }

  /**
   *
   */
  private toComponentName(name: string): string {
    return name
      .replace(/[^a-zA-Z0-9]/g, ' ')
      .split(' ')
      .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
      .join('');
  }
}

export function getShadowCodegen(): ShadowCodegen {
  return ShadowCodegen.getInstance();
}

export default ShadowCodegen;
