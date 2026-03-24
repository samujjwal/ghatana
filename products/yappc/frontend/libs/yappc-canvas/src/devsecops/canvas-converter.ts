/**
 * Canvas Converter for Runbooks
 * 
 * Converts runbook execution plans into visual canvas documents for interactive visualization.
 * 
 * Note: This is a simplified implementation. For production use, consider:
 * - More sophisticated layout algorithms
 * - Custom node types for different step types
 * - Interactive approval gate visualization
 * - Real-time status updates
 */

import { calculateStepPositions, getStepStyle } from './helpers';

import type { Runbook, RunbookConfig } from './types';
import type { CanvasDocument, CanvasNode, CanvasEdge, CanvasElement } from '../types/canvas-document';

const CANVAS_DOCUMENT_VERSION = '1.0.0';

/**
 * Convert a runbook to a canvas document for visualization
 * 
 * @param runbook - The runbook to convert
 * @param config - Configuration for layout and display
 * @returns Canvas document ready for rendering
 * 
 * @example
 * ```typescript
 * const canvasDoc = runbookToCanvas(runbook, {
 *   layout: 'dag',
 *   showTiming: true,
 *   showApprovals: true,
 * });
 * ```
 */
export function runbookToCanvas(runbook: Runbook, config: RunbookConfig): CanvasDocument {
  const elements: Record<string, CanvasElement> = {};
  const elementIds: string[] = [];

  // Calculate positions for all steps
  const positions = calculateStepPositions(runbook.steps, config);

  // Create nodes for each step
  runbook.steps.forEach(step => {
    const pos = positions.get(step.id) || { x: 0, y: 0 };
    const style = getStepStyle(step, config);

    const node: CanvasNode = {
      id: step.id,
      type: 'node',
      nodeType: 'node',
      transform: {
        position: pos,
        scale: 1,
        rotation: 0,
      },
      bounds: {
        x: pos.x,
        y: pos.y,
        width: 200,
        height: 80,
      },
      visible: true,
      locked: false,
      selected: false,
      zIndex: 1,
      metadata: {
        custom: {
          label: step.name,
          stepType: step.type,
          status: step.metadata.status,
          command: step.command,
        },
      },
      data: {},
      inputs: [],
      outputs: [],
      style: {
        backgroundColor: style.backgroundColor,
        borderColor: style.borderColor,
        borderWidth: style.borderWidth,
        borderRadius: 8,
        fontSize: style.fontSize,
        fontFamily: style.fontFamily,
      },
      version: CANVAS_DOCUMENT_VERSION,
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    elements[step.id] = node;
    elementIds.push(step.id);
  });

  // Create edges for dependencies
  runbook.steps.forEach(step => {
    step.dependsOn.forEach(depId => {
      const edgeId = `edge-${depId}-${step.id}`;
      const sourceNode = elements[depId] as CanvasNode;
      const targetNode = elements[step.id] as CanvasNode;

      if (sourceNode && targetNode) {
        const edge: CanvasEdge = {
          id: edgeId,
          type: 'edge',
          sourceId: depId,
          targetId: step.id,
          sourceHandle: 'output',
          targetHandle: 'input',
          path: [],
          transform: {
            position: { x: 0, y: 0 },
            scale: 1,
            rotation: 0,
          },
          bounds: {
            x: 0,
            y: 0,
            width: 0,
            height: 0,
          },
          visible: true,
          locked: false,
          selected: false,
          zIndex: 0,
          metadata: {
            custom: {
              relationship: 'depends-on',
            },
          },
          style: {
            strokeColor: '#9ca3af',
            strokeWidth: 2,
          },
          version: CANVAS_DOCUMENT_VERSION,
          createdAt: new Date(),
          updatedAt: new Date(),
        };

        elements[edgeId] = edge;
        elementIds.push(edgeId);
      }
    });
  });

  // Create canvas document
  const canvasDoc: CanvasDocument = {
    id: `runbook-${runbook.id}`,
    title: `Runbook: ${runbook.name}`,
    description: runbook.description || '',
    version: CANVAS_DOCUMENT_VERSION,
    viewport: {
      center: { x: 0, y: 0 },
      zoom: 1,
    },
    elements,
    elementOrder: elementIds as readonly string[],
    metadata: {
      type: 'runbook',
      runbookId: runbook.id,
      runbookType: runbook.type,
      executionStatus: runbook.metadata?.status,
      totalSteps: runbook.metadata?.totalSteps || 0,
      completedSteps: runbook.metadata?.completedSteps || 0,
      failedSteps: runbook.metadata?.failedSteps || 0,
      createdBy: runbook.metadata?.author || 'system',
      createdAt: runbook.metadata?.startTime?.toISOString() || new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
    capabilities: {
      canEdit: false,
      canZoom: true,
      canPan: true,
      canSelect: true,
      canUndo: false,
      canRedo: false,
      canExport: true,
      canImport: false,
      canCollaborate: false,
      canPersist: true,
      allowedElementTypes: ['node', 'edge'],
    },
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  return canvasDoc;
}
