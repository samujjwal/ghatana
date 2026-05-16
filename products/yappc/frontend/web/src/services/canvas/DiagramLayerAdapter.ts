/**
 * Diagram Layer Adapter
 * 
 * Stable adapter for integrating diagram layers with the canonical canvas document schema.
 * Provides abstraction for diagram-specific rendering and interaction logic.
 * 
 * @doc.type module
 * @doc.purpose Diagram layer integration adapter
 * @doc.layer product
 * @doc.pattern Adapter
 */

import type { CanvasNode, CanvasPoint } from '@/routes/app/project/canvas/canvasDocumentAdapters';

export interface DiagramNode {
  id: string;
  diagramType: 'architecture' | 'class' | 'component' | 'infrastructure' | 'sequence';
  mermaidDefinition: string;
  position: CanvasPoint;
  bounds: { width: number; height: number };
}

export interface DiagramLayerAdapter {
  /**
   * Converts a diagram node to canonical canvas node format
   */
  toCanvasNode(diagramNode: DiagramNode): CanvasNode;

  /**
   * Converts a canonical canvas node to diagram node format
   */
  fromCanvasNode(canvasNode: CanvasNode): DiagramNode;

  /**
   * Validates diagram-specific data before conversion
   */
  validateDiagramNode(diagramNode: DiagramNode): { valid: boolean; errors: string[] };
}

export class MermaidDiagramAdapter implements DiagramLayerAdapter {
  toCanvasNode(diagramNode: DiagramNode): CanvasNode {
    const now = new Date();
    
    return {
      id: diagramNode.id,
      type: 'node',
      nodeType: 'diagram',
      transform: {
        position: diagramNode.position,
        scale: 1,
        rotation: 0,
      },
      bounds: {
        x: diagramNode.position.x,
        y: diagramNode.position.y,
        width: diagramNode.bounds.width,
        height: diagramNode.bounds.height,
      },
      visible: true,
      locked: false,
      selected: false,
      zIndex: 0,
      metadata: {
        layer: 'diagram',
        tags: ['diagram', diagramNode.diagramType],
        custom: {
          diagramType: diagramNode.diagramType,
          mermaidDefinition: diagramNode.mermaidDefinition,
        },
      },
      version: '1.0.0',
      createdAt: now,
      updatedAt: now,
      data: {
        label: `${diagramNode.diagramType} diagram`,
        description: `Mermaid ${diagramNode.diagramType} diagram`,
        properties: {
          mermaidDefinition: diagramNode.mermaidDefinition,
        },
      },
      inputs: [],
      outputs: [],
      style: {
        backgroundColor: '#f0f0f0',
        borderColor: '#ccc',
        borderWidth: 1,
        textColor: '#333',
        fontFamily: 'Inter, sans-serif',
        fontSize: 12,
        borderRadius: 4,
        opacity: 1,
      },
    };
  }

  fromCanvasNode(canvasNode: CanvasNode): DiagramNode {
    if (canvasNode.nodeType !== 'diagram') {
      throw new Error('Canvas node is not a diagram node');
    }

    const custom = canvasNode.metadata.custom as Record<string, unknown> | undefined;
    const diagramType = (custom?.diagramType as string) || 'architecture';
    const properties = canvasNode.data.properties as
      | { mermaidDefinition?: string }
      | undefined;
    const mermaidDefinition = properties?.mermaidDefinition ?? '';

    return {
      id: canvasNode.id,
      diagramType: ['architecture', 'class', 'component', 'infrastructure', 'sequence'].includes(diagramType)
        ? (diagramType as 'architecture' | 'class' | 'component' | 'infrastructure' | 'sequence')
        : 'architecture',
      mermaidDefinition,
      position: canvasNode.transform.position,
      bounds: {
        width: canvasNode.bounds.width,
        height: canvasNode.bounds.height,
      },
    };
  }

  validateDiagramNode(diagramNode: DiagramNode): { valid: boolean; errors: string[] } {
    const errors: string[] = [];

    if (!diagramNode.id || diagramNode.id.trim() === '') {
      errors.push('Diagram node ID is required');
    }

    if (!diagramNode.diagramType) {
      errors.push('Diagram type is required');
    }

    const validDiagramTypes = ['architecture', 'class', 'component', 'infrastructure', 'sequence'];
    if (!validDiagramTypes.includes(diagramNode.diagramType)) {
      errors.push(`Invalid diagram type: ${diagramNode.diagramType}`);
    }

    if (!diagramNode.mermaidDefinition || diagramNode.mermaidDefinition.trim() === '') {
      errors.push('Mermaid definition is required');
    }

    if (diagramNode.bounds.width <= 0 || diagramNode.bounds.height <= 0) {
      errors.push('Diagram bounds must be positive');
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }
}

export const diagramLayerAdapter = new MermaidDiagramAdapter();
