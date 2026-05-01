/**
 * Export Service - Handle canvas exports to various formats
 */

import { ExportFormat } from './types';

import type { ExportOptions, ExportResult } from './types';
import type {
  CanvasConnection,
  CanvasElement,
  CanvasState,
} from '../../components/canvas/workspace/canvasAtoms';

type RenderableData = Record<string, unknown>;

function getRenderableData(data: unknown): RenderableData {
  return typeof data === 'object' && data !== null ? (data as RenderableData) : {};
}

function getNumber(data: RenderableData, key: string, fallback: number): number {
  return typeof data[key] === 'number' ? (data[key] as number) : fallback;
}

function getString(data: RenderableData, key: string, fallback: string): string {
  return typeof data[key] === 'string' ? (data[key] as string) : fallback;
}

/**
 *
 */
class ExportServiceClass {
  /**
   * Export canvas to specified format
   */
  async export(
    canvasState: CanvasState,
    options: ExportOptions,
  ): Promise<ExportResult> {
    try {
      switch (options.format) {
        case 'json':
          return this.exportJSON(canvasState, options);
        case 'jsx':
          return this.exportJSX(canvasState, options);
        case 'png':
          return this.exportPNG(canvasState, options);
        case 'svg':
          return this.exportSVG(canvasState, options);
        default:
          return {
            success: false,
            error: `Unsupported format: ${options.format}`,
          };
      }
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Export failed',
      };
    }
  }

  /**
   * Export as JSON
   */
  private exportJSON(
    canvasState: CanvasState,
    options: ExportOptions,
  ): ExportResult {
    const data = JSON.stringify(canvasState, null, 2);
    const filename = options.filename || `canvas-${Date.now()}.json`;

    return {
      success: true,
      data,
      filename,
    };
  }

  /**
   * Export as JSX (for page designer components)
   */
  private exportJSX(
    canvasState: CanvasState,
    options: ExportOptions,
  ): ExportResult {
    // Filter page designer components
    const components = canvasState.elements.filter(
      (el) => el.kind === 'component' && el.type !== 'stroke',
    );

    if (components.length === 0) {
      return {
        success: false,
        error: 'No components to export',
      };
    }

    // Generate JSX (simplified version)
    let jsx = "import React from 'react';\n\n";
    jsx += 'export const ExportedCanvas: React.FC = () => {\n';
    jsx += '  return (\n';
    jsx += '    <div>\n';
    jsx += '      {/* Canvas components */}\n';
    jsx += '    </div>\n';
    jsx += '  );\n';
    jsx += '};\n';

    const filename = options.filename || `canvas-${Date.now()}.tsx`;

    return {
      success: true,
      data: jsx,
      filename,
    };
  }

  /**
   * Export as PNG
   */
  private async exportPNG(
    canvasState: CanvasState,
    options: ExportOptions,
  ): Promise<ExportResult> {
    try {
      // Create a temporary canvas element for rendering
      const canvas = document.createElement('canvas');
      const ctx = canvas.getContext('2d');

      if (!ctx) {
        throw new Error('Failed to get canvas context');
      }

      // Set canvas dimensions
      const width = options.width || 1200;
      const height = options.height || 800;
      const scale = options.scale || 1;

      canvas.width = width * scale;
      canvas.height = height * scale;

      // Apply background
      if (options.includeBackground) {
        ctx.fillStyle = options.backgroundColor || '#ffffff';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
      }

      // Render canvas elements
      await this.renderCanvasElements(ctx, canvasState, scale);

      // Convert to PNG
      const dataUrl = canvas.toDataURL('image/png');

      // Create download if needed
      if (options.download) {
        const link = document.createElement('a');
        link.download = options.filename || 'canvas-export.png';
        link.href = dataUrl;
        link.click();
      }

      return {
        success: true,
        data: dataUrl,
        format: 'png',
        metadata: {
          width,
          height,
          scale,
          elementCount: canvasState.elements.length,
          timestamp: new Date().toISOString(),
        }
      };
    } catch (error) {
      console.error('[ExportService] PNG export failed:', error);
      return {
        success: false,
        error: `PNG export failed: ${error instanceof Error ? error.message : 'Unknown error'}`,
      };
    }
  }

  /**
   * Render canvas elements to canvas context
   */
  private async renderCanvasElements(
    ctx: CanvasRenderingContext2D,
    canvasState: CanvasState,
    scale: number
  ): Promise<void> {
    // Render connections first (so they appear behind nodes)
    for (const connection of canvasState.connections || []) {
      await this.renderConnection(ctx, connection, canvasState.elements, scale);
    }

    // Render nodes
    for (const element of canvasState.elements || []) {
      await this.renderElement(ctx, element, scale);
    }
  }

  /**
   * Render a connection/edge
   */
  private async renderConnection(
    ctx: CanvasRenderingContext2D,
    connection: CanvasConnection,
    elements: CanvasElement[],
    scale: number
  ): Promise<void> {
    const { source, target } = connection;
    const style = getRenderableData(connection.style);

    // Find source and target elements
    const sourceElement = this.findElementById(elements, source);
    const targetElement = this.findElementById(elements, target);

    if (!sourceElement || !targetElement) return;

    // Calculate positions
    const sourcePos = {
      x: sourceElement.position.x * scale,
      y: sourceElement.position.y * scale + getNumber(getRenderableData(sourceElement.data), 'height', 50) / 2
    };

    const targetPos = {
      x: targetElement.position.x * scale,
      y: targetElement.position.y * scale
    };

    // Draw connection line
    ctx.beginPath();
    ctx.moveTo(sourcePos.x, sourcePos.y);

    if (getString(style, 'type', '') === 'curved') {
      // Draw curved line
      const controlX = (sourcePos.x + targetPos.x) / 2;
      const controlY = Math.min(sourcePos.y, targetPos.y) - 50;
      ctx.quadraticCurveTo(controlX, controlY, targetPos.x, targetPos.y);
    } else {
      // Draw straight line
      ctx.lineTo(targetPos.x, targetPos.y);
    }

    ctx.strokeStyle = getString(style, 'color', '#666');
    ctx.lineWidth = getNumber(style, 'width', 2);
    ctx.stroke();

    // Draw arrowhead
    this.drawArrowhead(ctx, sourcePos, targetPos, getString(style, 'color', '#666'));
  }

  /**
   * Render a canvas element
   */
  private async renderElement(
    ctx: CanvasRenderingContext2D,
    element: CanvasElement,
    scale: number
  ): Promise<void> {
    const { type, position } = element;
    const data = getRenderableData(element.data);
    const x = position.x * scale;
    const y = position.y * scale;

    switch (type) {
      case 'sticky-note':
      case 'sticky':
        this.renderStickyNote(ctx, x, y, data, scale);
        break;
      case 'frame':
        this.renderFrame(ctx, x, y, data, scale);
        break;
      case 'text':
        this.renderText(ctx, x, y, data, scale);
        break;
      case 'rectangle':
      case 'circle':
      case 'diamond':
        this.renderShape(ctx, x, y, type, data, scale);
        break;
      case 'image':
        await this.renderImage(ctx, x, y, data, scale);
        break;
      default:
        // Default rendering
        this.renderDefaultElement(ctx, x, y, type, data, scale);
    }
  }

  /**
   * Render sticky note
   */
  private renderStickyNote(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    data: unknown,
    scale: number
  ): void {
    const renderData = getRenderableData(data);
    const width = getNumber(renderData, 'width', 150) * scale;
    const height = getNumber(renderData, 'height', 100) * scale;
    const color = getString(renderData, 'color', '#fff9c4');

    // Draw sticky note background
    ctx.fillStyle = color;
    ctx.fillRect(x, y, width, height);

    // Draw border
    ctx.strokeStyle = '#fbc02d';
    ctx.lineWidth = 1;
    ctx.strokeRect(x, y, width, height);

    // Draw text
    const text = getString(renderData, 'text', '');
    if (text) {
      ctx.fillStyle = '#333';
      ctx.font = `${14 * scale}px Arial`;
      ctx.fillText(text, x + 10 * scale, y + 20 * scale);
    }
  }

  /**
   * Render frame
   */
  private renderFrame(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    data: unknown,
    scale: number
  ): void {
    const renderData = getRenderableData(data);
    const width = getNumber(renderData, 'width', 400) * scale;
    const height = getNumber(renderData, 'height', 300) * scale;
    const color = getString(renderData, 'color', '#e3f2fd');

    // Draw frame background
    ctx.fillStyle = color;
    ctx.fillRect(x, y, width, height);

    // Draw border
    ctx.strokeStyle = '#2196f3';
    ctx.lineWidth = 2 * scale;
    ctx.strokeRect(x, y, width, height);

    // Draw title
    const title = getString(renderData, 'title', '');
    if (title) {
      ctx.fillStyle = '#1976d2';
      ctx.font = `bold ${16 * scale}px Arial`;
      ctx.fillText(title, x + 15 * scale, y + 25 * scale);
    }
  }

  /**
   * Render text element
   */
  private renderText(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    data: unknown,
    scale: number
  ): void {
    const renderData = getRenderableData(data);
    const fontSize = getNumber(renderData, 'fontSize', 16);
    const color = getString(renderData, 'color', '#333');

    ctx.fillStyle = color;
    ctx.font = `${fontSize * scale}px Arial`;
    ctx.fillText(getString(renderData, 'text', 'Text'), x, y + fontSize * scale);
  }

  /**
   * Render shape
   */
  private renderShape(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    shapeType: string,
    data: unknown,
    scale: number
  ): void {
    const renderData = getRenderableData(data);
    const color = getString(renderData, 'color', '#e3f2fd');
    const label = getString(renderData, 'label', '');

    ctx.fillStyle = color;
    ctx.strokeStyle = '#2196f3';
    ctx.lineWidth = 2 * scale;

    switch (shapeType) {
      case 'rectangle':
        const rectWidth = getNumber(renderData, 'width', 120) * scale;
        const rectHeight = getNumber(renderData, 'height', 80) * scale;
        ctx.fillRect(x, y, rectWidth, rectHeight);
        ctx.strokeRect(x, y, rectWidth, rectHeight);
        break;

      case 'circle':
        const radius = 40 * scale;
        ctx.beginPath();
        ctx.arc(x + radius, y + radius, radius, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();
        break;

      case 'diamond':
        const size = 40 * scale;
        ctx.beginPath();
        ctx.moveTo(x + size, y);
        ctx.lineTo(x + size * 2, y + size);
        ctx.lineTo(x + size, y + size * 2);
        ctx.lineTo(x, y + size);
        ctx.closePath();
        ctx.fill();
        ctx.stroke();
        break;
    }

    // Draw label
    if (label) {
      ctx.fillStyle = '#1976d2';
      ctx.font = `${14 * scale}px Arial`;
      ctx.textAlign = 'center';
      ctx.fillText(
        label,
        x + (getNumber(renderData, 'width', 120) * scale) / 2,
        y + (getNumber(renderData, 'height', 80) * scale) / 2,
      );
    }
  }

  /**
   * Render image element
   */
  private async renderImage(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    data: unknown,
    scale: number
  ): Promise<void> {
    const renderData = getRenderableData(data);
    const width = getNumber(renderData, 'width', 200) * scale;
    const height = getNumber(renderData, 'height', 150) * scale;

    const url = getString(renderData, 'url', '');
    if (url) {
      try {
        const img = new Image();
        await new Promise((resolve, reject) => {
          img.onload = resolve;
          img.onerror = reject;
          img.src = url;
        });
        ctx.drawImage(img, x, y, width, height);
      } catch (error) {
        console.warn('[ExportService] Failed to load image:', error);
        // Draw placeholder
        ctx.fillStyle = '#f5f5f5';
        ctx.fillRect(x, y, width, height);
        ctx.strokeStyle = '#ddd';
        ctx.strokeRect(x, y, width, height);

        ctx.fillStyle = '#999';
        ctx.font = `${14 * scale}px Arial`;
        ctx.textAlign = 'center';
        ctx.fillText(getString(renderData, 'alt', 'Image'), x + width / 2, y + height / 2);
      }
    } else {
      // Draw placeholder
      ctx.fillStyle = '#f5f5f5';
      ctx.fillRect(x, y, width, height);
      ctx.strokeStyle = '#ddd';
      ctx.strokeRect(x, y, width, height);

      ctx.fillStyle = '#999';
      ctx.font = `${14 * scale}px Arial`;
      ctx.textAlign = 'center';
      ctx.fillText('No Image', x + width / 2, y + height / 2);
    }
  }

  /**
   * Render default element
   */
  private renderDefaultElement(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    type: string,
    data: unknown,
    scale: number
  ): void {
    ctx.fillStyle = '#f0f0f0';
    ctx.fillRect(x, y, 100 * scale, 50 * scale);
    ctx.strokeStyle = '#999';
    ctx.strokeRect(x, y, 100 * scale, 50 * scale);

    ctx.fillStyle = '#666';
    ctx.font = `${12 * scale}px Arial`;
    ctx.fillText(type, x + 10 * scale, y + 30 * scale);
  }

  /**
   * Draw arrowhead
   */
  private drawArrowhead(
    ctx: CanvasRenderingContext2D,
    from: { x: number; y: number },
    to: { x: number; y: number },
    color: string
  ): void {
    const headLength = 10;
    const angle = Math.PI / 6;

    const angle1 = Math.atan2(to.y - from.y, to.x - from.x);
    const angle2 = angle1 + angle;
    const angle3 = angle1 - angle;

    ctx.beginPath();
    ctx.moveTo(to.x, to.y);
    ctx.lineTo(
      to.x - headLength * Math.cos(angle2),
      to.y - headLength * Math.sin(angle2)
    );
    ctx.moveTo(to.x, to.y);
    ctx.lineTo(
      to.x - headLength * Math.cos(angle3),
      to.y - headLength * Math.sin(angle3)
    );
    ctx.strokeStyle = color;
    ctx.lineWidth = 2;
    ctx.stroke();
  }

  /**
   * Find element by ID
   */
  private findElementById(elements: CanvasElement[], id: string): CanvasElement | undefined {
    return elements.find((element) => element.id === id);
  }

  /**
   * Export as SVG
   */
  private async exportSVG(
    canvasState: CanvasState,
    options: ExportOptions,
  ): Promise<ExportResult> {
    const width = 1200;
    const height = 800;
    const scale = options.scale || 1;

    let svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width * scale}" height="${height * scale}" viewBox="0 0 ${width} ${height}">`;

    if (options.includeBackground) {
      // SVG export background: Using standard white for compatibility
      // eslint-disable-next-line yappc-design-system/no-hardcoded-colors
      const bgColor = options.backgroundColor || '#ffffff';
      svg += `<rect width="${width}" height="${height}" fill="${bgColor}"/>`;
    }

    // Add elements (simplified)
    svg += '<g id="canvas-elements">';
    svg += '<!-- Canvas elements would be rendered here -->';
    svg += '</g>';

    svg += '</svg>';

    const filename = options.filename || `canvas-${Date.now()}.svg`;

    return {
      success: true,
      data: svg,
      filename,
    };
  }

  /**
   * Download exported data
   */
  download(data: string | Blob, filename: string, mimeType?: string): void {
    const blob =
      data instanceof Blob
        ? data
        : new Blob([data], { type: mimeType || 'text/plain' });

    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  /**
   * Import from JSON
   */
  async importJSON(jsonString: string): Promise<{
    success: boolean;
    data?: CanvasState;
    error?: string;
  }> {
    try {
      const data = JSON.parse(jsonString);

      // Basic validation
      if (!data.elements || !data.connections) {
        return {
          success: false,
          error: 'Invalid canvas state format',
        };
      }

      return {
        success: true,
        data: data as CanvasState,
      };
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Import failed',
      };
    }
  }
}

// Singleton instance
export const ExportService = new ExportServiceClass();
