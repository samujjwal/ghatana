/**
 * Code Generator
 *
 * Generates React component code from canvas node configurations.
 * Supports component rendering, data bindings, events, and validation.
 *
 * @module canvas/codegen/CodeGenerator
 */

import type { ComponentNodeData } from '../types/CanvasNode';
import type { ComponentSchema } from '../types/ComponentSchema';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export interface CodeGenerationOptions {
  /**
   * Include TypeScript types
   */
  typescript?: boolean;

  /**
   * Include comments
   */
  includeComments?: boolean;

  /**
   * Include imports
   */
  includeImports?: boolean;

  /**
   * Code style
   */
  style?: 'functional' | 'class';

  /**
   * Indentation
   */
  indent?: number;

  /**
   * Include data binding code
   */
  includeDataBinding?: boolean;

  /**
   * Include event handlers
   */
  includeEvents?: boolean;

  /**
   * Include validation
   */
  includeValidation?: boolean;
}

/**
 *
 */
export interface GeneratedCode {
  component: string;
  imports: string[];
  types?: string;
  hooks?: string;
  handlers?: string;
}

// ============================================================================
// Code Generator Implementation
// ============================================================================

/**
 *
 */
export class CodeGenerator {
  private static defaultOptions: CodeGenerationOptions = {
    typescript: true,
    includeComments: true,
    includeImports: true,
    style: 'functional',
    indent: 2,
    includeDataBinding: true,
    includeEvents: true,
    includeValidation: true,
  };

  /**
   * Generate component code from node data
   */
  static generateComponent(
    componentType: string,
    nodeData: ComponentNodeData,
    options: CodeGenerationOptions = {}
  ): GeneratedCode {
    const opts = { ...this.defaultOptions, ...options };
    const indent = ' '.repeat(opts.indent || 2);

    const imports: string[] = [];
    const code: string[] = [];

    // Add component import
    imports.push(`import { ${componentType} } from '@ghatana/yappc-ui';`);

    // Add React import
    if (opts.includeDataBinding || opts.includeEvents) {
      imports.push(`import React from 'react';`);
    }

    // Generate props
    const propsCode = this.generateProps(nodeData, opts, indent);

    // Generate data binding hooks
    let hooksCode = '';
    if (opts.includeDataBinding && nodeData.dataBinding) {
      hooksCode = this.generateDataBindingHook(nodeData.dataBinding, opts, indent);
      imports.push(`import { useDataBinding } from '@ghatana/yappc-shared-ui-core/hooks';`);
    }

    // Generate event handlers
    let handlersCode = '';
    if (opts.includeEvents && nodeData.events) {
      handlersCode = this.generateEventHandlers(nodeData.events, opts, indent);
    }

    // Generate validation
    let validationCode = '';
    if (opts.includeValidation && nodeData.validation) {
      validationCode = this.generateValidation(nodeData.validation, opts, indent);
      imports.push(`import { useForm } from '@ghatana/yappc-shared-ui-core/hooks';`);
    }

    // Build component
    if (opts.style === 'functional') {
      code.push(`export function Generated${componentType}() {`);

      if (hooksCode) {
        code.push(hooksCode);
      }

      if (handlersCode) {
        code.push(handlersCode);
      }

      if (validationCode) {
        code.push(validationCode);
      }

      code.push(`${indent}return (`);
      code.push(`${indent}${indent}<${componentType}`);
      code.push(propsCode);
      code.push(`${indent}${indent}/>`);
      code.push(`${indent});`);
      code.push(`}`);
    }

    return {
      component: code.join('\n'),
      imports,
      hooks: hooksCode,
      handlers: handlersCode,
    };
  }

  /**
   * Generate props string
   */
  private static generateProps(
    nodeData: ComponentNodeData,
    options: CodeGenerationOptions,
    indent: string
  ): string {
    const props: string[] = [];

    // Regular props
    for (const [key, value] of Object.entries(nodeData.props || {})) {
      if (typeof value === 'string') {
        props.push(`${indent}${indent}${indent}${key}="${value}"`);
      } else if (typeof value === 'number' || typeof value === 'boolean') {
        props.push(`${indent}${indent}${indent}${key}={${value}}`);
      } else {
        props.push(`${indent}${indent}${indent}${key}={${JSON.stringify(value)}}`);
      }
    }

    // Data binding props
    if (options.includeDataBinding && nodeData.dataBinding) {
      props.push(`${indent}${indent}${indent}value={boundValue}`);
      if (nodeData.dataBinding.mode === 'two-way') {
        props.push(`${indent}${indent}${indent}onChange={handleChange}`);
      }
    }

    // Event handler props
    if (options.includeEvents && nodeData.events) {
      for (const eventName of Object.keys(nodeData.events)) {
        const handlerName = `handle${eventName.charAt(0).toUpperCase() + eventName.slice(1)}`;
        props.push(`${indent}${indent}${indent}${eventName}={${handlerName}}`);
      }
    }

    return props.join('\n');
  }

  /**
   * Generate data binding hook
   */
  private static generateDataBindingHook(
    binding: NonNullable<ComponentNodeData['dataBinding']>,
    options: CodeGenerationOptions,
    indent: string
  ): string {
    const code: string[] = [];

    if (options.includeComments) {
      code.push(`${indent}// Data binding: ${binding.source}.${binding.path || ''}`);
    }

    code.push(`${indent}const { value: boundValue, setValue: setBoundValue } = useDataBinding({`);
    code.push(`${indent}${indent}source: '${binding.source}',`);
    if (binding.path) {
      code.push(`${indent}${indent}path: '${binding.path}',`);
    }
    code.push(`${indent}${indent}mode: '${binding.mode}',`);
    code.push(`${indent}});`);
    code.push('');

    if (binding.mode === 'two-way') {
      code.push(`${indent}const handleChange = (newValue: unknown) => {`);
      code.push(`${indent}${indent}setBoundValue(newValue);`);
      code.push(`${indent}};`);
      code.push('');
    }

    return code.join('\n');
  }

  /**
   * Generate event handlers
   */
  private static generateEventHandlers(
    events: NonNullable<ComponentNodeData['events']>,
    options: CodeGenerationOptions,
    indent: string
  ): string {
    const code: string[] = [];

    if (options.includeComments) {
      code.push(`${indent}// Event handlers`);
    }

    for (const [eventName, config] of Object.entries(events)) {
      const handlerName = `handle${eventName.charAt(0).toUpperCase() + eventName.slice(1)}`;

      code.push(`${indent}const ${handlerName} = (event: unknown) => {`);

      if (options.includeComments) {
        code.push(`${indent}${indent}// Emit: ${config.emit}`);
      }

      code.push(`${indent}${indent}eventBus.emit('${config.emit}', {`);

      if (config.payload) {
        for (const [key, value] of Object.entries(config.payload)) {
          code.push(`${indent}${indent}${indent}${key}: ${JSON.stringify(value)},`);
        }
      }

      code.push(`${indent}${indent}${indent}...event,`);
      code.push(`${indent}${indent}});`);
      code.push(`${indent}};`);
      code.push('');
    }

    return code.join('\n');
  }

  /**
   * Generate validation code
   */
  private static generateValidation(
    validation: NonNullable<ComponentNodeData['validation']>,
    options: CodeGenerationOptions,
    indent: string
  ): string {
    const code: string[] = [];

    if (options.includeComments) {
      code.push(`${indent}// Form validation`);
    }

    code.push(`${indent}const form = useForm({`);
    code.push(`${indent}${indent}validationRules: {`);

    for (const rule of validation.rules) {
      const params = rule.params ? rule.params.map(p => JSON.stringify(p)).join(', ') : '';
      code.push(`${indent}${indent}${indent}${rule.type}: [${params}],`);
    }

    code.push(`${indent}${indent}},`);
    code.push(`${indent}});`);
    code.push('');

    return code.join('\n');
  }

  /**
   * Generate complete file with imports
   */
  static generateFile(
    componentType: string,
    nodeData: ComponentNodeData,
    options: CodeGenerationOptions = {}
  ): string {
    const generated = this.generateComponent(componentType, nodeData, options);
    const parts: string[] = [];

    if (options.includeImports) {
      parts.push(generated.imports.join('\n'));
      parts.push('');
    }

    if (options.includeComments) {
      parts.push('/**');
      parts.push(` * Generated ${componentType} Component`);
      parts.push(' * Auto-generated from canvas configuration');
      parts.push(' */');
    }

    parts.push(generated.component);

    return parts.join('\n');
  }

  /**
   * Generate schema to component mapping
   */
  static generateFromSchema(
    schema: ComponentSchema,
    options: CodeGenerationOptions = {}
  ): GeneratedCode {
    const nodeData: ComponentNodeData = {
      componentType: schema.type,
      props: schema.props || {},
    };

    return this.generateComponent(schema.type, nodeData, options);
  }

  /**
   * Batch generate multiple components
   */
  static generateBatch(
    components: Array<{ type: string; nodeData: ComponentNodeData }>,
    options: CodeGenerationOptions = {}
  ): Record<string, GeneratedCode> {
    const result: Record<string, GeneratedCode> = {};

    for (const { type, nodeData } of components) {
      result[type] = this.generateComponent(type, nodeData, options);
    }

    return result;
  }
}
