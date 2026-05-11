/**
 * Sketch Layer Adapter
 * 
 * Stable adapter for integrating sketch layers with the canonical canvas document schema.
 * Provides abstraction for sketch-specific rendering and interaction logic.
 * 
 * @doc.type module
 * @doc.purpose Sketch layer integration adapter
 * @doc.layer product
 * @doc.pattern Adapter
 */

import type { CanvasNode, CanvasPoint } from '@/routes/app/project/canvas/canvasDocumentAdapters';

export interface SketchNode {
  id: string;
  sketchType: 'freehand' | 'shape' | 'annotation' | 'highlight';
  paths: CanvasPath[];
  position: CanvasPoint;
  bounds: { width: number; height: number };
  color: string;
  strokeWidth: number;
}

export interface CanvasPath {
  points: CanvasPoint[];
  closed: boolean;
}

export interface SketchLayerAdapter {
  /**
   * Converts a sketch node to canonical canvas node format
   */
  toCanvasNode(sketchNode: SketchNode): CanvasNode;

  /**
   * Converts a canonical canvas node to sketch node format
   */
  fromCanvasNode(canvasNode: CanvasNode): SketchNode;

  /**
   * Validates sketch-specific data before conversion
   */
  validateSketchNode(sketchNode: SketchNode): { valid: boolean; errors: string[] };
}

export class SketchLayerAdapterImpl implements SketchLayerAdapter {
  toCanvasNode(sketchNode: SketchNode): CanvasNode {
    const now = new Date();
    
    return {
      id: sketchNode.id,
      type: 'node',
      nodeType: 'sketch',
      transform: {
        position: sketchNode.position,
        scale: 1,
        rotation: 0,
      },
      bounds: {
        x: sketchNode.position.x,
        y: sketchNode.position.y,
        width: sketchNode.bounds.width,
        height: sketchNode.bounds.height,
      },
      visible: true,
      locked: false,
      selected: false,
      zIndex: 0,
      metadata: {
        layer: 'sketch',
        tags: ['sketch', sketchNode.sketchType],
        custom: {
          sketchType: sketchNode.sketchType,
          paths: sketchNode.paths,
          color: sketchNode.color,
          strokeWidth: sketchNode.strokeWidth,
        },
      },
      version: '1.0.0',
      createdAt: now,
      updatedAt: now,
      data: {
        label: `${sketchNode.sketchType} sketch`,
        description: `Freehand ${sketchNode.sketchType} sketch`,
        properties: {
          paths: sketchNode.paths,
          color: sketchNode.color,
          strokeWidth: sketchNode.strokeWidth,
        },
      },
      inputs: [],
      outputs: [],
      style: {
        backgroundColor: 'transparent',
        borderColor: sketchNode.color,
        borderWidth: sketchNode.strokeWidth,
        textColor: '#333',
        fontFamily: 'Inter, sans-serif',
        fontSize: 12,
        borderRadius: 0,
        opacity: 0.8,
      },
    };
  }

  fromCanvasNode(canvasNode: CanvasNode): SketchNode {
    if (canvasNode.nodeType !== 'sketch') {
      throw new Error('Canvas node is not a sketch node');
    }

    const custom = canvasNode.metadata.custom as Record<string, unknown> | undefined;
    const sketchType = (custom?.sketchType as string) || 'freehand';
    const paths = (custom?.paths as CanvasPath[]) || [];
    const color = (custom?.color as string) || '#000000';
    const strokeWidth = (custom?.strokeWidth as number) || 2;

    return {
      id: canvasNode.id,
      sketchType: ['freehand', 'shape', 'annotation', 'highlight'].includes(sketchType)
        ? (sketchType as 'freehand' | 'shape' | 'annotation' | 'highlight')
        : 'freehand',
      paths,
      position: canvasNode.transform.position,
      bounds: {
        width: canvasNode.bounds.width,
        height: canvasNode.bounds.height,
      },
      color,
      strokeWidth,
    };
  }

  validateSketchNode(sketchNode: SketchNode): { valid: boolean; errors: string[] } {
    const errors: string[] = [];

    if (!sketchNode.id || sketchNode.id.trim() === '') {
      errors.push('Sketch node ID is required');
    }

    if (!sketchNode.sketchType) {
      errors.push('Sketch type is required');
    }

    const validSketchTypes = ['freehand', 'shape', 'annotation', 'highlight'];
    if (!validSketchTypes.includes(sketchNode.sketchType)) {
      errors.push(`Invalid sketch type: ${sketchNode.sketchType}`);
    }

    if (!sketchNode.paths || sketchNode.paths.length === 0) {
      errors.push('Sketch must have at least one path');
    }

    for (let i = 0; i < sketchNode.paths.length; i++) {
      const path = sketchNode.paths[i];
      if (!path.points || path.points.length < 2) {
        errors.push(`Path ${i} must have at least 2 points`);
      }
    }

    if (sketchNode.bounds.width <= 0 || sketchNode.bounds.height <= 0) {
      errors.push('Sketch bounds must be positive');
    }

    if (!sketchNode.color || sketchNode.color.trim() === '') {
      errors.push('Sketch color is required');
    }

    if (sketchNode.strokeWidth <= 0) {
      errors.push('Stroke width must be positive');
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }
}

export const sketchLayerAdapter = new SketchLayerAdapterImpl();
