/**
 * Cross-Canvas Validation System
 * Ensures consistency across linked canvases and detects broken references
 */

/**
 *
 */
export interface ValidationIssue {
  id: string;
  type: 'error' | 'warning' | 'info';
  severity: 'critical' | 'high' | 'medium' | 'low';
  message: string;
  canvasId: string;
  elementId?: string;
  fixSuggestion?: string;
  autoFixAvailable?: boolean;
}

/**
 *
 */
export interface CanvasReference {
  sourceCanvasId: string;
  sourceElementId: string;
  targetCanvasId: string;
  targetElementId?: string;
  referenceType: 'portal' | 'component' | 'data' | 'flow';
}

/**
 *
 */
export interface ValidationReport {
  canvasId: string;
  timestamp: string;
  status: 'valid' | 'warning' | 'error';
  issues: ValidationIssue[];
  references: CanvasReference[];
  suggestions: string[];
}

/**
 *
 */
export class CrossCanvasValidator {
  private canvasRegistry: Map<string, unknown> = new Map();
  private referenceIndex: Map<string, CanvasReference[]> = new Map();

  /**
   * Register a canvas for validation
   */
  registerCanvas(canvasId: string, canvasData: unknown): void {
    this.canvasRegistry.set(canvasId, canvasData);
    this.updateReferenceIndex(canvasId, canvasData);
  }

  /**
   * Validate all registered canvases
   */
  validateAll(): ValidationReport[] {
    const reports: ValidationReport[] = [];
    
    for (const [canvasId] of this.canvasRegistry) {
      reports.push(this.validateCanvas(canvasId));
    }
    
    return reports;
  }

  /**
   * Validate a specific canvas
   */
  validateCanvas(canvasId: string): ValidationReport {
    const canvasData = this.canvasRegistry.get(canvasId);
    if (!canvasData) {
      return this.createEmptyReport(canvasId, 'error', [{
        id: `missing-canvas-${canvasId}`,
        type: 'error',
        severity: 'critical',
        message: `Canvas ${canvasId} not found`,
        canvasId,
      }]);
    }

    const issues: ValidationIssue[] = [];
    const references = this.referenceIndex.get(canvasId) || [];

    // Validate portal references
    issues.push(...this.validatePortalReferences(canvasId, canvasData));
    
    // Validate component dependencies
    issues.push(...this.validateComponentDependencies(canvasId, canvasData));
    
    // Validate data flow consistency
    issues.push(...this.validateDataFlowConsistency(canvasId, canvasData));
    
    // Check for circular references
    issues.push(...this.detectCircularReferences(canvasId));

    const status = issues.some(i => i.type === 'error') ? 'error' 
                 : issues.some(i => i.type === 'warning') ? 'warning' 
                 : 'valid';

    return {
      canvasId,
      timestamp: new Date().toISOString(),
      status,
      issues,
      references,
      suggestions: this.generateSuggestions(issues),
    };
  }

  /**
   * Validate portal element references
   */
  private validatePortalReferences(canvasId: string, canvasData: unknown): ValidationIssue[] {
    const issues: ValidationIssue[] = [];
    const elements = canvasData.elements || [];

    for (const element of elements) {
      if (element.type === 'portal' && element.data?.targetCanvasId) {
        const targetCanvasId = element.data.targetCanvasId;
        
        // Check if target canvas exists
        if (!this.canvasRegistry.has(targetCanvasId)) {
          issues.push({
            id: `broken-portal-${element.id}`,
            type: 'error',
            severity: 'high',
            message: `Portal "${element.data.label || element.id}" references non-existent canvas "${targetCanvasId}"`,
            canvasId,
            elementId: element.id,
            fixSuggestion: `Create canvas "${targetCanvasId}" or update portal reference`,
            autoFixAvailable: false,
          });
        }

        // Check for valid target element if specified
        if (element.data.targetElementId) {
          const targetCanvas = this.canvasRegistry.get(targetCanvasId);
          const targetExists = targetCanvas?.elements?.some(
            (el: unknown) => el.id === element.data.targetElementId
          );
          
          if (!targetExists) {
            issues.push({
              id: `broken-portal-element-${element.id}`,
              type: 'warning',
              severity: 'medium',
              message: `Portal element "${element.id}" references non-existent target element "${element.data.targetElementId}"`,
              canvasId,
              elementId: element.id,
              fixSuggestion: 'Remove target element reference or create the target element',
              autoFixAvailable: true,
            });
          }
        }
      }
    }

    return issues;
  }

  /**
   * Validate component dependencies
   */
  private validateComponentDependencies(canvasId: string, canvasData: unknown): ValidationIssue[] {
    const issues: ValidationIssue[] = [];
    const elements = canvasData.elements || [];
    const connections = canvasData.connections || [];

    // Check for orphaned connections
    for (const connection of connections) {
      const sourceExists = elements.some((el: unknown) => el.id === connection.source);
      const targetExists = elements.some((el: unknown) => el.id === connection.target);
      
      if (!sourceExists) {
        issues.push({
          id: `orphaned-connection-source-${connection.id}`,
          type: 'error',
          severity: 'medium',
          message: `Connection "${connection.id}" references missing source element "${connection.source}"`,
          canvasId,
          fixSuggestion: 'Remove orphaned connection or restore missing element',
          autoFixAvailable: true,
        });
      }
      
      if (!targetExists) {
        issues.push({
          id: `orphaned-connection-target-${connection.id}`,
          type: 'error',
          severity: 'medium',
          message: `Connection "${connection.id}" references missing target element "${connection.target}"`,
          canvasId,
          fixSuggestion: 'Remove orphaned connection or restore missing element',
          autoFixAvailable: true,
        });
      }
    }

    return issues;
  }

  /**
   * Validate data flow consistency
   */
  private validateDataFlowConsistency(canvasId: string, canvasData: unknown): ValidationIssue[] {
    const issues: ValidationIssue[] = [];
    const elements = canvasData.elements || [];
    const connections = canvasData.connections || [];

    // Build adjacency list
    const graph = new Map<string, string[]>();
    for (const connection of connections) {
      if (!graph.has(connection.source)) {
        graph.set(connection.source, []);
      }
      graph.get(connection.source)!.push(connection.target);
    }

    // Check for isolated components
    const connectedNodes = new Set<string>();
    for (const [source, targets] of graph) {
      connectedNodes.add(source);
      targets.forEach(target => connectedNodes.add(target));
    }

    for (const element of elements) {
      if (element.type !== 'portal' && !connectedNodes.has(element.id)) {
        issues.push({
          id: `isolated-element-${element.id}`,
          type: 'info',
          severity: 'low',
          message: `Element "${element.data?.label || element.id}" is not connected to any other elements`,
          canvasId,
          elementId: element.id,
          fixSuggestion: 'Consider connecting to other elements or remove if unused',
        });
      }
    }

    return issues;
  }

  /**
   * Detect circular references in canvas hierarchy
   */
  private detectCircularReferences(canvasId: string): ValidationIssue[] {
    const issues: ValidationIssue[] = [];
    const visited = new Set<string>();
    const recursionStack = new Set<string>();

    const detectCycle = (currentCanvasId: string, path: string[]): boolean => {
      if (recursionStack.has(currentCanvasId)) {
        // Found a cycle
        const cycleStart = path.indexOf(currentCanvasId);
        const cycle = path.slice(cycleStart).concat(currentCanvasId);
        
        issues.push({
          id: `circular-reference-${currentCanvasId}`,
          type: 'error',
          severity: 'critical',
          message: `Circular reference detected in canvas hierarchy: ${cycle.join(' → ')}`,
          canvasId: currentCanvasId,
          fixSuggestion: 'Remove one of the portal references to break the cycle',
        });
        
        return true;
      }

      if (visited.has(currentCanvasId)) {
        return false;
      }

      visited.add(currentCanvasId);
      recursionStack.add(currentCanvasId);

      // Check all portal references from this canvas
      const canvasData = this.canvasRegistry.get(currentCanvasId);
      if (canvasData?.elements) {
        for (const element of canvasData.elements) {
          if (element.type === 'portal' && element.data?.targetCanvasId) {
            const hasCycle = detectCycle(element.data.targetCanvasId, [...path, currentCanvasId]);
            if (hasCycle) return true;
          }
        }
      }

      recursionStack.delete(currentCanvasId);
      return false;
    };

    detectCycle(canvasId, []);
    return issues;
  }

  /**
   * Update reference index for a canvas
   */
  private updateReferenceIndex(canvasId: string, canvasData: unknown): void {
    const references: CanvasReference[] = [];
    const elements = canvasData.elements || [];

    for (const element of elements) {
      if (element.type === 'portal' && element.data?.targetCanvasId) {
        references.push({
          sourceCanvasId: canvasId,
          sourceElementId: element.id,
          targetCanvasId: element.data.targetCanvasId,
          targetElementId: element.data.targetElementId,
          referenceType: 'portal',
        });
      }
    }

    this.referenceIndex.set(canvasId, references);
  }

  /**
   * Generate suggestions based on issues
   */
  private generateSuggestions(issues: ValidationIssue[]): string[] {
    const suggestions: string[] = [];
    const errorCount = issues.filter(i => i.type === 'error').length;
    const warningCount = issues.filter(i => i.type === 'warning').length;

    if (errorCount > 0) {
      suggestions.push(`Fix ${errorCount} critical error${errorCount > 1 ? 's' : ''} to ensure canvas functionality`);
    }

    if (warningCount > 0) {
      suggestions.push(`Review ${warningCount} warning${warningCount > 1 ? 's' : ''} to improve canvas quality`);
    }

    if (issues.some(i => i.autoFixAvailable)) {
      suggestions.push('Some issues can be automatically fixed - click "Auto Fix" where available');
    }

    return suggestions;
  }

  /**
   * Create empty validation report
   */
  private createEmptyReport(
    canvasId: string, 
    status: ValidationReport['status'], 
    issues: ValidationIssue[] = []
  ): ValidationReport {
    return {
      canvasId,
      timestamp: new Date().toISOString(),
      status,
      issues,
      references: [],
      suggestions: [],
    };
  }

  /**
   * Auto-fix issues where possible
   */
  autoFixIssues(canvasId: string, issueIds: string[]): { fixed: string[]; failed: string[] } {
    const fixed: string[] = [];
    const failed: string[] = [];
    
    const canvasData = this.canvasRegistry.get(canvasId);
    if (!canvasData) {
      return { fixed, failed: issueIds };
    }

    for (const issueId of issueIds) {
      try {
        if (issueId.startsWith('orphaned-connection-')) {
          // Remove orphaned connections
          const connectionId = issueId.split('-').pop();
          canvasData.connections = canvasData.connections.filter(
            (conn: unknown) => conn.id !== connectionId
          );
          fixed.push(issueId);
        } else if (issueId.startsWith('broken-portal-element-')) {
          // Remove broken portal element references
          const elementId = issueId.replace('broken-portal-element-', '');
          const element = canvasData.elements.find((el: unknown) => el.id === elementId);
          if (element?.data) {
            delete element.data.targetElementId;
          }
          fixed.push(issueId);
        } else {
          failed.push(issueId);
        }
      } catch (error) {
        failed.push(issueId);
      }
    }

    // Update registry with fixed data
    if (fixed.length > 0) {
      this.registerCanvas(canvasId, canvasData);
    }

    return { fixed, failed };
  }
}