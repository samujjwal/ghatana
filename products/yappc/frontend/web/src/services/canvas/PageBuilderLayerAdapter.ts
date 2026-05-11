/**
 * Page Builder Layer Adapter
 * 
 * Stable adapter for integrating page-builder with the canonical canvas document schema.
 * Ensures canvas does not directly own page-builder internals.
 * Provides abstraction for page-specific rendering and interaction logic.
 * 
 * @doc.type module
 * @doc.purpose Page builder integration adapter
 * @doc.layer product
 * @doc.pattern Adapter
 */

import type { CanvasNode, CanvasPoint } from '@/routes/app/project/canvas/canvasDocumentAdapters';

// Abstract page-builder types - canvas should not depend on @ghatana/ui-builder directly
export interface PageNode {
  id: string;
  pageType: 'page' | 'template' | 'component';
  document: unknown; // Abstracted BuilderDocument
  position: CanvasPoint;
  bounds: { width: number; height: number };
  expanded: boolean;
  validationSummary?: unknown; // Abstracted ValidationResult
}

export interface PageBuilderLayerAdapter {
  /**
   * Converts a page node to canonical canvas node format
   */
  toCanvasNode(pageNode: PageNode): CanvasNode;

  /**
   * Converts a canonical canvas node to page node format
   */
  fromCanvasNode(canvasNode: CanvasNode): PageNode;

  /**
   * Validates page-specific data before conversion
   */
  validatePageNode(pageNode: PageNode): { valid: boolean; errors: string[] };
}

export class PageBuilderAdapterImpl implements PageBuilderLayerAdapter {
  toCanvasNode(pageNode: PageNode): CanvasNode {
    const now = new Date();
    
    return {
      id: pageNode.id,
      type: 'node',
      nodeType: 'page',
      transform: {
        position: pageNode.position,
        scale: 1,
        rotation: 0,
      },
      bounds: {
        x: pageNode.position.x,
        y: pageNode.position.y,
        width: pageNode.bounds.width,
        height: pageNode.bounds.height,
      },
      visible: true,
      locked: false,
      selected: false,
      zIndex: 0,
      metadata: {
        layer: 'page',
        tags: ['page', pageNode.pageType],
        custom: {
          pageType: pageNode.pageType,
          document: pageNode.document,
          expanded: pageNode.expanded,
          validationSummary: pageNode.validationSummary,
        },
      },
      version: '1.0.0',
      createdAt: now,
      updatedAt: now,
      data: {
        label: `${pageNode.pageType} page`,
        description: 'Page builder document',
        properties: {
          document: pageNode.document,
          expanded: pageNode.expanded,
          validationSummary: pageNode.validationSummary,
        },
      },
      inputs: [],
      outputs: [],
      style: {
        backgroundColor: '#ffffff',
        borderColor: '#e0e0e0',
        borderWidth: 1,
        textColor: '#333',
        fontFamily: 'Inter, sans-serif',
        fontSize: 12,
        borderRadius: 4,
        opacity: 1,
      },
    };
  }

  fromCanvasNode(canvasNode: CanvasNode): PageNode {
    if (canvasNode.nodeType !== 'page') {
      throw new Error('Canvas node is not a page node');
    }

    const custom = canvasNode.metadata.custom as Record<string, unknown> | undefined;
    const pageType = (custom?.pageType as string) || 'page';
    const document = custom?.document;
    const expanded = (custom?.expanded as boolean) || false;
    const validationSummary = custom?.validationSummary;

    return {
      id: canvasNode.id,
      pageType: ['page', 'template', 'component'].includes(pageType)
        ? (pageType as 'page' | 'template' | 'component')
        : 'page',
      document,
      position: canvasNode.transform.position,
      bounds: {
        width: canvasNode.bounds.width,
        height: canvasNode.bounds.height,
      },
      expanded,
      validationSummary,
    };
  }

  validatePageNode(pageNode: PageNode): { valid: boolean; errors: string[] } {
    const errors: string[] = [];

    if (!pageNode.id || pageNode.id.trim() === '') {
      errors.push('Page node ID is required');
    }

    if (!pageNode.pageType) {
      errors.push('Page type is required');
    }

    const validPageTypes = ['page', 'template', 'component'];
    if (!validPageTypes.includes(pageNode.pageType)) {
      errors.push(`Invalid page type: ${pageNode.pageType}`);
    }

    if (pageNode.bounds.width <= 0 || pageNode.bounds.height <= 0) {
      errors.push('Page bounds must be positive');
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }
}

export const pageBuilderLayerAdapter = new PageBuilderAdapterImpl();
