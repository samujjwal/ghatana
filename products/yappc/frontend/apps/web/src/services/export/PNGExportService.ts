/**
 * PNG Export Service - Canvas to PNG conversion
 * Simplified implementation without html-to-image dependency
 */

import { logger } from '../../utils/Logger';
import { palette } from '@ghatana/yappc-ui';

import type { ExportOptions, ExportResult } from './types';
import type { CanvasState } from '../../components/canvas/workspace/canvasAtoms';

/**
 *
 */
class PNGExportServiceClass {
  /**
   * Export canvas to PNG using Canvas API
   */
  async exportToPNG(
    canvasState: CanvasState,
    options: ExportOptions,
  ): Promise<ExportResult> {
    try {
      const canvas = document.createElement('canvas');
      const ctx = canvas.getContext('2d');

      if (!ctx) {
        return {
          success: false,
          error: 'Could not get canvas context',
        };
      }

      // Set canvas dimensions
      const width = 1200;
      const height = 800;
      const scale = options.scale || 1;

      canvas.width = width * scale;
      canvas.height = height * scale;

      // Scale context for high DPI
      ctx.scale(scale, scale);

      // Background
      if (options.includeBackground) {
        // Canvas background: Using standard white for PNG export compatibility
        // eslint-disable-next-line yappc-design-system/no-hardcoded-colors
        ctx.fillStyle = options.backgroundColor || '#ffffff';
        ctx.fillRect(0, 0, width, height);
      }

      // Draw elements (simplified rendering)
      await this.renderElements(ctx, canvasState, width, height);

      // Convert to blob
      return new Promise((resolve) => {
        canvas.toBlob(
          (blob) => {
            if (blob) {
              const filename = options.filename || `canvas-${Date.now()}.png`;
              resolve({
                success: true,
                data: blob,
                filename,
              });
            } else {
              resolve({
                success: false,
                error: 'Failed to create PNG blob',
              });
            }
          },
          'image/png',
          options.quality || 0.9,
        );
      });
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : 'PNG export failed',
      };
    }
  }

  /**
   *
   */
  private async renderElements(
    ctx: CanvasRenderingContext2D,
    canvasState: CanvasState,
    width: number,
    height: number,
  ): Promise<void> {
    const elements = canvasState.elements || [];

    for (const element of elements) {
      try {
        await this.renderElement(ctx, element);
      } catch (error) {
        logger.warn('Failed to render element', 'png-export', {
          elementId: element.id,
          error: error instanceof Error ? error.message : String(error)
        });
      }
    }
  }

  /**
   *
   */
  private async renderElement(ctx: CanvasRenderingContext2D, element: unknown): Promise<void> {
    const { position, size, data, style } = element;

    if (!position) return;

    ctx.save();

    // Apply transforms
    ctx.translate(position.x, position.y);

    // Set styles
    if (style?.color) {
      ctx.fillStyle = style.color;
      ctx.strokeStyle = style.color;
    }

    switch (element.kind) {
      case 'node':
      case 'component':
        await this.renderNode(ctx, element);
        break;
      case 'shape':
        await this.renderShape(ctx, element);
        break;
      case 'stroke':
        await this.renderStroke(ctx, element);
        break;
      default:
        // Fallback: render as rectangle
        await this.renderRectangle(ctx, size?.width || 100, size?.height || 60);
    }

    ctx.restore();
  }

  /**
   *
   */
  private async renderNode(ctx: CanvasRenderingContext2D, element: unknown): Promise<void> {
    const width = element.style?.width || 150;
    const height = element.size?.height || 80;

    // Node background
    // Node background: Using white as default fallback for user-created nodes
    // eslint-disable-next-line yappc-design-system/no-hardcoded-colors
    ctx.fillStyle = element.style?.backgroundColor || '#ffffff';
    ctx.fillRect(0, 0, width, height);

    // Node border
    ctx.strokeStyle = element.style?.borderColor || palette.neutral[300];
    ctx.lineWidth = 2;
    ctx.strokeRect(0, 0, width, height);

    // Node label
    if (element.data?.label) {
      ctx.fillStyle = element.style?.color || palette.neutral[900];
      ctx.font = '14px Arial';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(element.data.label, width / 2, height / 2);
    }
  }

  /**
   *
   */
  private async renderShape(ctx: CanvasRenderingContext2D, element: unknown): Promise<void> {
    const { shapeType, width = 100, height = 100 } = element.data || {};

    ctx.fillStyle = element.style?.fill || 'rgba(0, 0, 0, 0.1)';
    ctx.strokeStyle = element.style?.stroke || palette.neutral[900];
    ctx.lineWidth = element.style?.strokeWidth || 2;

    switch (shapeType) {
      case 'rectangle':
        ctx.fillRect(0, 0, width, height);
        ctx.strokeRect(0, 0, width, height);
        break;
      case 'ellipse':
        ctx.beginPath();
        ctx.ellipse(width / 2, height / 2, width / 2, height / 2, 0, 0, 2 * Math.PI);
        ctx.fill();
        ctx.stroke();
        break;
      default:
        await this.renderRectangle(ctx, width, height);
    }
  }

  /**
   *
   */
  private async renderStroke(ctx: CanvasRenderingContext2D, element: unknown): Promise<void> {
    const points = element.data?.points || [];

    if (points.length < 2) return;

    // Stroke default: Black is standard for user-drawn strokes when not specified
    // eslint-disable-next-line yappc-design-system/no-hardcoded-colors
    ctx.strokeStyle = element.style?.stroke || '#000000';
    ctx.lineWidth = element.style?.strokeWidth || 2;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    ctx.beginPath();
    ctx.moveTo(points[0].x, points[0].y);

    for (let i = 1; i < points.length; i++) {
      ctx.lineTo(points[i].x, points[i].y);
    }

    ctx.stroke();
  }

  /**
   *
   */
  private async renderRectangle(ctx: CanvasRenderingContext2D, width: number, height: number): Promise<void> {
    ctx.fillStyle = 'rgba(200, 200, 200, 0.5)';
    ctx.fillRect(0, 0, width, height);
    ctx.strokeStyle = palette.neutral[500];
    ctx.lineWidth = 1;
    ctx.strokeRect(0, 0, width, height);
  }
}

// Singleton instance
export const PNGExportService = new PNGExportServiceClass();
