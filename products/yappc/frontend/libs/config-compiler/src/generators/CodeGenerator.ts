/**
 * Code Generator
 *
 * Wrapper around yappc-ui CodeGenerator for config-compiler integration.
 *
 * @packageDocumentation
 */

import type { PageConfig, ComponentInstance } from 'yappc-config-schema';
import type { GeneratedArtifact } from '../types';

/**
 * Code Generator wrapper that reuses yappc-ui CodeGenerator
 */
export class CodeGenerator {
  /**
   * Generate React code from PageConfig
   */
  async generateFromPageConfig(
    pageConfig: PageConfig,
    options: {
      includeImports?: boolean;
      includeComments?: boolean;
      indent?: number;
    } = {}
  ): Promise<GeneratedArtifact[]> {
    const artifacts: GeneratedArtifact[] = [];

    // Generate code for each component instance
    for (const component of pageConfig.components || []) {
      const code = this.generateComponentCode(component, options);
      artifacts.push({
        type: 'component',
        name: component.id || 'unknown',
        content: code,
        language: 'typescript',
        path: `components/${component.type}/${component.id || 'unknown'}.tsx`,
        metadata: { componentId: component.id || 'unknown' },
      });
    }

    // Generate page file
    const pageCode = this.generatePageCode(pageConfig, options);
    artifacts.push({
      type: 'page',
      name: pageConfig.id,
      content: pageCode,
      language: 'typescript',
      path: `pages/${pageConfig.id}.tsx`,
      metadata: { pageId: pageConfig.id },
    });

    return artifacts;
  }

  /**
   * Generate code for a single component instance
   */
  private generateComponentCode(
    component: ComponentInstance,
    options: {
      includeImports?: boolean;
      includeComments?: boolean;
      indent?: number;
    }
  ): string {
    const indent = ' '.repeat(options.indent || 2);
    const lines: string[] = [];

    if (options.includeComments !== false) {
      lines.push(`/**`);
      lines.push(` * ${component.type} Component`);
      lines.push(` * Generated from config: ${component.id}`);
      lines.push(` */`);
      lines.push('');
    }

    if (options.includeImports !== false) {
      lines.push(`import { ${component.type} } from 'yappc-ui';`);
      lines.push('');
    }

    lines.push(`export function ${component.id}() {`);
    lines.push(`${indent}return (`);
    lines.push(`${indent}${indent}<${component.type}`);

    // Add props
    if (component.props) {
      for (const [key, value] of Object.entries(component.props)) {
        if (typeof value === 'string') {
          lines.push(`${indent}${indent}${indent}${key}="${value}"`);
        } else if (typeof value === 'number' || typeof value === 'boolean') {
          lines.push(`${indent}${indent}${indent}${key}={${value}}`);
        } else {
          lines.push(
            `${indent}${indent}${indent}${key}={${JSON.stringify(value)}}`
          );
        }
      }
    }

    lines.push(`${indent}${indent}/>`);
    lines.push(`${indent});`);
    lines.push(`}`);

    return lines.join('\n');
  }

  /**
   * Generate page code that composes components
   */
  private generatePageCode(
    pageConfig: PageConfig,
    options: {
      includeImports?: boolean;
      includeComments?: boolean;
      indent?: number;
    }
  ): string {
    const indent = ' '.repeat(options.indent || 2);
    const lines: string[] = [];

    if (options.includeComments !== false) {
      lines.push(`/**`);
      lines.push(` * ${pageConfig.title}`);
      lines.push(` * Generated from page config: ${pageConfig.id}`);
      lines.push(` */`);
      lines.push('');
    }

    if (options.includeImports !== false) {
      for (const component of pageConfig.components || []) {
        lines.push(
          `import { ${component.id} } from './components/${component.type}/${component.id}';`
        );
      }
      lines.push('');
    }

    lines.push(`export function ${pageConfig.id}() {`);
    lines.push(`${indent}return (`);
    lines.push(`${indent}${indent}<div className="${pageConfig.id}">`);

    // Add component instances
    for (const component of pageConfig.components || []) {
      lines.push(`${indent}${indent}${indent}<${component.id} />`);
    }

    lines.push(`${indent}${indent}</div>`);
    lines.push(`${indent});`);
    lines.push(`}`);

    return lines.join('\n');
  }

  /**
   * Generate code from interface definition
   */
  async generateFromInterface(
    _interfaceDef: unknown,
    _options: {
      includeImports?: boolean;
      includeComments?: boolean;
      indent?: number;
    } = {}
  ): Promise<GeneratedArtifact> {
    // Interface-based code generation is a planned feature for generating TypeScript interfaces and types.
    // Current implementation returns a placeholder comment.
    // Future enhancement: Generate TypeScript interfaces, types, and validation schemas from interface definitions.
    const code = `// Interface-based code generation is a planned feature.\n// This will generate TypeScript interfaces, types, and validation schemas from interface definitions.`;
    return {
      type: 'component',
      name: 'InterfaceGenerated',
      content: code,
      language: 'typescript',
      path: `types/interface-generated.ts`,
      metadata: {},
    };
  }
}
