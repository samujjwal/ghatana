/**
 * Canvas Validator
 *
 * Validates canvas configurations for errors and warnings.
 * Checks component config, data bindings, accessibility, and performance.
 *
 * @module canvas/validation/CanvasValidator
 */

import type { ComponentMetadata } from '../registry/ComponentRegistry';
import type { ComponentNodeData } from '../types/CanvasNode';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export type ValidationSeverity = 'error' | 'warning' | 'info';

/**
 *
 */
export interface ValidationIssue {
  id: string;
  severity: ValidationSeverity;
  category: 'component' | 'binding' | 'event' | 'accessibility' | 'performance';
  message: string;
  nodeId?: string;
  suggestion?: string;
  autoFixable?: boolean;
}

/**
 *
 */
export interface ValidationResult {
  valid: boolean;
  issues: ValidationIssue[];
  errorCount: number;
  warningCount: number;
  infoCount: number;
}

/**
 *
 */
export interface ValidationOptions {
  checkAccessibility?: boolean;
  checkPerformance?: boolean;
  checkDataBindings?: boolean;
  checkEvents?: boolean;
  strictMode?: boolean;
}

// ============================================================================
// Canvas Validator Implementation
// ============================================================================

/**
 *
 */
export class CanvasValidator {
  private static issueCounter = 0;

  /**
   * Validate a single node
   */
  static validateNode(
    nodeId: string,
    componentType: string,
    nodeData: ComponentNodeData,
    metadata?: ComponentMetadata,
    options: ValidationOptions = {}
  ): ValidationIssue[] {
    const issues: ValidationIssue[] = [];

    // Component configuration validation
    issues.push(...this.validateComponentConfig(nodeId, nodeData, metadata, options));

    // Data binding validation
    if (options.checkDataBindings !== false && nodeData.dataBinding) {
      issues.push(...this.validateDataBinding(nodeId, nodeData.dataBinding));
    }

    // Event validation
    if (options.checkEvents !== false && nodeData.events) {
      issues.push(...this.validateEvents(nodeId, nodeData.events));
    }

    // Accessibility validation
    if (options.checkAccessibility && metadata) {
      issues.push(...this.validateAccessibility(nodeId, componentType, nodeData, metadata));
    }

    return issues;
  }

  /**
   * Validate component configuration
   */
  private static validateComponentConfig(
    nodeId: string,
    nodeData: ComponentNodeData,
    metadata?: ComponentMetadata,
    options: ValidationOptions = {}
  ): ValidationIssue[] {
    const issues: ValidationIssue[] = [];

    if (!metadata) {
      return issues;
    }

    // Check required props
    if (metadata.propDefinitions) {
      for (const propDef of metadata.propDefinitions) {
        if (propDef.required) {
          const value = nodeData.props?.[propDef.name];
          if (value === undefined || value === null || value === '') {
            issues.push({
              id: this.generateIssueId(),
              severity: 'error',
              category: 'component',
              message: `Required property "${propDef.label}" is missing`,
              nodeId,
              suggestion: `Set the "${propDef.label}" property`,
              autoFixable: false,
            });
          }
        }
      }
    }

    // Check token references
    if (nodeData.tokens) {
      for (const [propName, tokenPath] of Object.entries(nodeData.tokens)) {
        if (!tokenPath.startsWith('$')) {
          issues.push({
            id: this.generateIssueId(),
            severity: 'warning',
            category: 'component',
            message: `Token reference "${tokenPath}" for property "${propName}" should start with $`,
            nodeId,
            suggestion: `Use $${tokenPath} instead`,
            autoFixable: true,
          });
        }
      }
    }

    return issues;
  }

  /**
   * Validate data binding
   */
  private static validateDataBinding(
    nodeId: string,
    binding: NonNullable<ComponentNodeData['dataBinding']>
  ): ValidationIssue[] {
    const issues: ValidationIssue[] = [];

    // Check source
    if (!binding.source || binding.source.trim() === '') {
      issues.push({
        id: this.generateIssueId(),
        severity: 'error',
        category: 'binding',
        message: 'Data binding source is not specified',
        nodeId,
        suggestion: 'Select a data source',
        autoFixable: false,
      });
    }

    // Check path for non-expression modes
    if (binding.mode !== 'expression' && !binding.path) {
      issues.push({
        id: this.generateIssueId(),
        severity: 'warning',
        category: 'binding',
        message: 'Data binding path is not specified',
        nodeId,
        suggestion: 'Specify a field path or use expression mode',
        autoFixable: false,
      });
    }

    return issues;
  }

  /**
   * Validate events
   */
  private static validateEvents(
    nodeId: string,
    events: NonNullable<ComponentNodeData['events']>
  ): ValidationIssue[] {
    const issues: ValidationIssue[] = [];

    for (const [eventName, config] of Object.entries(events)) {
      // Check emit target
      if (!config.emit || config.emit.trim() === '') {
        issues.push({
          id: this.generateIssueId(),
          severity: 'error',
          category: 'event',
          message: `Event "${eventName}" has no emit target`,
          nodeId,
          suggestion: 'Specify an event to emit',
          autoFixable: false,
        });
      }

      // Check payload validity
      if (config.payload) {
        try {
          JSON.stringify(config.payload);
        } catch {
          issues.push({
            id: this.generateIssueId(),
            severity: 'error',
            category: 'event',
            message: `Event "${eventName}" has invalid payload`,
            nodeId,
            suggestion: 'Ensure payload is a valid object',
            autoFixable: false,
          });
        }
      }
    }

    return issues;
  }

  /**
   * Validate accessibility
   */
  private static validateAccessibility(
    nodeId: string,
    componentType: string,
    nodeData: ComponentNodeData,
    metadata: ComponentMetadata
  ): ValidationIssue[] {
    const issues: ValidationIssue[] = [];

    // Interactive components need accessible labels
    const interactiveComponents = ['Button', 'TextField', 'Checkbox', 'Radio', 'Select'];
    if (interactiveComponents.includes(componentType)) {
      const hasLabel =
        nodeData.props?.label ||
        nodeData.props?.['aria-label'] ||
        nodeData.props?.['aria-labelledby'];

      if (!hasLabel) {
        issues.push({
          id: this.generateIssueId(),
          severity: 'warning',
          category: 'accessibility',
          message: `${componentType} should have an accessible label`,
          nodeId,
          suggestion: 'Add a label, aria-label, or aria-labelledby property',
          autoFixable: false,
        });
      }
    }

    // Images need alt text
    if (componentType === 'Image' || componentType === 'Avatar') {
      if (!nodeData.props?.alt && !nodeData.props?.['aria-label']) {
        issues.push({
          id: this.generateIssueId(),
          severity: 'error',
          category: 'accessibility',
          message: `${componentType} is missing alt text`,
          nodeId,
          suggestion: 'Add an alt property for screen readers',
          autoFixable: false,
        });
      }
    }

    return issues;
  }

  /**
   * Validate canvas performance
   */
  static validatePerformance(nodeCount: number): ValidationIssue[] {
    const issues: ValidationIssue[] = [];

    if (nodeCount > 100) {
      issues.push({
        id: this.generateIssueId(),
        severity: 'warning',
        category: 'performance',
        message: `Canvas has ${nodeCount} nodes which may impact performance`,
        suggestion: 'Consider breaking into smaller canvases or using virtualization',
        autoFixable: false,
      });
    }

    if (nodeCount > 200) {
      issues.push({
        id: this.generateIssueId(),
        severity: 'error',
        category: 'performance',
        message: `Canvas has ${nodeCount} nodes which will significantly impact performance`,
        suggestion: 'Reduce the number of nodes or split into multiple canvases',
        autoFixable: false,
      });
    }

    return issues;
  }

  /**
   * Validate entire canvas
   */
  static validateCanvas(
    nodes: Array<{
      id: string;
      componentType: string;
      nodeData: ComponentNodeData;
      metadata?: ComponentMetadata;
    }>,
    options: ValidationOptions = {}
  ): ValidationResult {
    const allIssues: ValidationIssue[] = [];

    // Validate each node
    for (const node of nodes) {
      const nodeIssues = this.validateNode(
        node.id,
        node.componentType,
        node.nodeData,
        node.metadata,
        options
      );
      allIssues.push(...nodeIssues);
    }

    // Performance validation
    if (options.checkPerformance !== false) {
      allIssues.push(...this.validatePerformance(nodes.length));
    }

    // Count issues by severity
    const errorCount = allIssues.filter((i) => i.severity === 'error').length;
    const warningCount = allIssues.filter((i) => i.severity === 'warning').length;
    const infoCount = allIssues.filter((i) => i.severity === 'info').length;

    return {
      valid: errorCount === 0,
      issues: allIssues,
      errorCount,
      warningCount,
      infoCount,
    };
  }

  /**
   * Filter issues by severity
   */
  static filterBySeverity(
    issues: ValidationIssue[],
    severity: ValidationSeverity
  ): ValidationIssue[] {
    return issues.filter((i) => i.severity === severity);
  }

  /**
   * Filter issues by category
   */
  static filterByCategory(
    issues: ValidationIssue[],
    category: ValidationIssue['category']
  ): ValidationIssue[] {
    return issues.filter((i) => i.category === category);
  }

  /**
   * Get auto-fixable issues
   */
  static getAutoFixableIssues(issues: ValidationIssue[]): ValidationIssue[] {
    return issues.filter((i) => i.autoFixable);
  }

  /**
   * Generate unique issue ID
   */
  private static generateIssueId(): string {
    return `issue-${Date.now()}-${this.issueCounter++}`;
  }
}
