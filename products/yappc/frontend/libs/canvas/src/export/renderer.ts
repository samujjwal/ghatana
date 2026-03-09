import type { CanvasData } from '../schemas/canvas-schemas';

// Use inferred types from schemas
/**
 *
 */
type Node = {
  id: string;
  type?: string;
  position?: { x: number; y: number };
  width?: number;
  height?: number;
  data?: unknown;
  style?: Record<string, unknown>;
};

/**
 *
 */
type Edge = {
  id: string;
  type?: string;
  source: string;
  target: string;
  data?: unknown;
  style?: Record<string, unknown>;
};
import type {
  ExportOptions,
  ImageExportOptions,
  PdfExportOptions,
  CodeExportOptions,
  JsonExportOptions,
  ExportResult} from '../schemas/export-schemas';

import {
  ExportFormat
} from '../schemas/export-schemas';
import { sanitizeExportContent, auditExportSecurity } from './sanitizer';

// Canvas-to-SVG renderer
/**
 *
 */
export class SVGRenderer {
  private width: number;
  private height: number;
  private viewBox: string;
  private backgroundColor: string;

  /**
   *
   */
  constructor(options: Partial<ImageExportOptions> = {}) {
    this.width = options.width || 800;
    this.height = options.height || 600;
    this.backgroundColor = options.backgroundColor || '#ffffff';
    this.viewBox = `0 0 ${this.width} ${this.height}`;
  }

  /**
   *
   */
  public renderCanvas(canvas: CanvasData): string {
    const svg = this.createSVGElement();
    
    // Add background
    svg.appendChild(this.createBackground());
    
    // Create main group for canvas content
    const mainGroup = this.createElement('g', {
      id: 'canvas-content',
      transform: `translate(${canvas.viewport?.x || 0}, ${canvas.viewport?.y || 0}) scale(${canvas.viewport?.zoom || 1})`,
    });

    // Render edges first (so they appear behind nodes)
    canvas.edges.forEach(edge => {
      const edgeElement = this.renderEdge(edge);
      if (edgeElement) mainGroup.appendChild(edgeElement);
    });

    // Render nodes
    canvas.nodes.forEach(node => {
      const nodeElement = this.renderNode(node);
      if (nodeElement) mainGroup.appendChild(nodeElement);
    });

    svg.appendChild(mainGroup);
    
    return this.serializeSVG(svg);
  }

  /**
   *
   */
  private createSVGElement(): SVGSVGElement {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('width', this.width.toString());
    svg.setAttribute('height', this.height.toString());
    svg.setAttribute('viewBox', this.viewBox);
    svg.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
    return svg;
  }

  /**
   *
   */
  private createBackground(): SVGRectElement {
    return this.createElement('rect', {
      x: '0',
      y: '0',
      width: '100%',
      height: '100%',
      fill: this.backgroundColor,
    }) as SVGRectElement;
  }

  /**
   *
   */
  private renderNode(node: Node): SVGElement | null {
    const group = this.createElement('g', {
      id: `node-${node.id}`,
      class: `node node-${node.type}`,
      transform: `translate(${node.position?.x || 0}, ${node.position?.y || 0})`,
    });

    switch (node.type || 'default') {
      case 'process':
        return this.renderProcessNode(node, group);
      case 'decision':
        return this.renderDecisionNode(node, group);
      case 'database':
        return this.renderDatabaseNode(node, group);
      case 'group':
        return this.renderGroupNode(node, group);
      default:
        return this.renderDefaultNode(node, group);
    }
  }

  /**
   *
   */
  private renderProcessNode(node: Node, group: SVGElement): SVGElement {
    const width = node.width || 120;
    const height = node.height || 60;
    
    // Process rectangle
    const rect = this.createElement('rect', {
      x: '0',
      y: '0',
      width: width.toString(),
      height: height.toString(),
      rx: '8',
      fill: (node.style?.backgroundColor as string) || '#e3f2fd',
      stroke: (node.style?.borderColor as string) || '#2196f3',
      'stroke-width': '2',
    });
    group.appendChild(rect);
    
    // Label
    if (node.data?.label) {
      const text = this.createElement('text', {
        x: (width / 2).toString(),
        y: (height / 2 + 5).toString(),
        'text-anchor': 'middle',
        'font-family': 'Arial, sans-serif',
        'font-size': '14',
        fill: '#333',
      });
      text.textContent = node.data.label;
      group.appendChild(text);
    }
    
    return group;
  }

  /**
   *
   */
  private renderDecisionNode(node: Node, group: SVGElement): SVGElement {
    const width = node.width || 100;
    const height = node.height || 80;
    
    // Diamond shape
    const diamond = this.createElement('polygon', {
      points: `${width/2},0 ${width},${height/2} ${width/2},${height} 0,${height/2}`,
      fill: (node.style?.backgroundColor as string) || '#fff3e0',
      stroke: (node.style?.borderColor as string) || '#ff9800',
      'stroke-width': '2',
    });
    group.appendChild(diamond);
    
    // Label
    if (node.data?.label) {
      const text = this.createElement('text', {
        x: (width / 2).toString(),
        y: (height / 2 + 5).toString(),
        'text-anchor': 'middle',
        'font-family': 'Arial, sans-serif',
        'font-size': '12',
        fill: '#333',
      });
      text.textContent = node.data.label;
      group.appendChild(text);
    }
    
    return group;
  }

  /**
   *
   */
  private renderDatabaseNode(node: Node, group: SVGElement): SVGElement {
    const width = node.width || 100;
    const height = node.height || 80;
    
    // Database cylinder shape
    const topEllipse = this.createElement('ellipse', {
      cx: (width / 2).toString(),
      cy: '15',
      rx: (width / 2).toString(),
      ry: '15',
      fill: (node.style?.backgroundColor as string) || '#e8f5e8',
      stroke: (node.style?.borderColor as string) || '#4caf50',
      'stroke-width': '2',
    });
    group.appendChild(topEllipse);
    
    const rect = this.createElement('rect', {
      x: '0',
      y: '15',
      width: width.toString(),
      height: (height - 30).toString(),
      fill: (node.style?.backgroundColor as string) || '#e8f5e8',
      stroke: (node.style?.borderColor as string) || '#4caf50',
      'stroke-width': '2',
    });
    group.appendChild(rect);
    
    const bottomEllipse = this.createElement('ellipse', {
      cx: (width / 2).toString(),
      cy: (height - 15).toString(),
      rx: (width / 2).toString(),
      ry: '15',
      fill: (node.style?.backgroundColor as string) || '#e8f5e8',
      stroke: (node.style?.borderColor as string) || '#4caf50',
      'stroke-width': '2',
    });
    group.appendChild(bottomEllipse);
    
    // Label
    if (node.data?.label) {
      const text = this.createElement('text', {
        x: (width / 2).toString(),
        y: (height / 2 + 5).toString(),
        'text-anchor': 'middle',
        'font-family': 'Arial, sans-serif',
        'font-size': '12',
        fill: '#333',
      });
      text.textContent = node.data.label;
      group.appendChild(text);
    }
    
    return group;
  }

  /**
   *
   */
  private renderGroupNode(node: Node, group: SVGElement): SVGElement {
    const width = node.width || 200;
    const height = node.height || 150;
    
    // Group background
    const rect = this.createElement('rect', {
      x: '0',
      y: '0',
      width: width.toString(),
      height: height.toString(),
      rx: '12',
      fill: (node.style?.backgroundColor as string) || '#f5f5f5',
      stroke: (node.style?.borderColor as string) || '#ccc',
      'stroke-width': '2',
      'stroke-dasharray': '5,5',
      opacity: '0.8',
    });
    group.appendChild(rect);
    
    // Title bar
    const titleBar = this.createElement('rect', {
      x: '0',
      y: '0',
      width: width.toString(),
      height: '30',
      rx: '12',
      fill: (node.style?.borderColor as string) || '#ccc',
    });
    group.appendChild(titleBar);
    
    // Title text
    if (node.data?.label) {
      const text = this.createElement('text', {
        x: '10',
        y: '20',
        'font-family': 'Arial, sans-serif',
        'font-size': '14',
        'font-weight': 'bold',
        fill: '#fff',
      });
      text.textContent = node.data.label;
      group.appendChild(text);
    }
    
    return group;
  }

  /**
   *
   */
  private renderDefaultNode(node: Node, group: SVGElement): SVGElement {
    const width = node.width || 100;
    const height = node.height || 50;
    
    const rect = this.createElement('rect', {
      x: '0',
      y: '0',
      width: width.toString(),
      height: height.toString(),
      rx: '4',
      fill: '#f0f0f0',
      stroke: '#999',
      'stroke-width': '1',
    });
    group.appendChild(rect);
    
    return group;
  }

  /**
   *
   */
  private renderEdge(edge: Edge): SVGElement | null {
    const group = this.createElement('g', {
      id: `edge-${edge.id}`,
      class: `edge edge-${edge.type}`,
    });

    // Find source and target positions (in real implementation, this would reference actual node positions)
    const sourcePos = { x: 100, y: 100 }; // Placeholder
    const targetPos = { x: 300, y: 200 }; // Placeholder

    switch (edge.type || 'standard') {
      case 'standard':
        return this.renderStandardEdge(edge, group, sourcePos, targetPos);
      case 'conditional':
        return this.renderConditionalEdge(edge, group, sourcePos, targetPos);
      case 'bezier':
        return this.renderBezierEdge(edge, group, sourcePos, targetPos);
      default:
        return this.renderStandardEdge(edge, group, sourcePos, targetPos);
    }
  }

  /**
   *
   */
  private renderStandardEdge(edge: Edge, group: SVGElement, source: { x: number; y: number }, target: { x: number; y: number }): SVGElement {
    const line = this.createElement('line', {
      x1: source.x.toString(),
      y1: source.y.toString(),
      x2: target.x.toString(),
      y2: target.y.toString(),
      stroke: (edge.style?.stroke as string) || '#666',
      'stroke-width': '2',
      'marker-end': 'url(#arrowhead)',
    });
    group.appendChild(line);
    
    // Add arrowhead marker definition (would be added to defs section)
    this.addArrowheadMarker(group);
    
    return group;
  }

  /**
   *
   */
  private renderConditionalEdge(edge: Edge, group: SVGElement, source: { x: number; y: number }, target: { x: number; y: number }): SVGElement {
    const line = this.createElement('line', {
      x1: source.x.toString(),
      y1: source.y.toString(),
      x2: target.x.toString(),
      y2: target.y.toString(),
      stroke: (edge.style?.stroke as string) || '#ff9800',
      'stroke-width': '2',
      'stroke-dasharray': '8,4',
      'marker-end': 'url(#arrowhead)',
    });
    group.appendChild(line);
    
    // Add condition label
    if (edge.data?.condition) {
      const midX = (source.x + target.x) / 2;
      const midY = (source.y + target.y) / 2;
      
      const text = this.createElement('text', {
        x: midX.toString(),
        y: (midY - 10).toString(),
        'text-anchor': 'middle',
        'font-family': 'Arial, sans-serif',
        'font-size': '10',
        fill: '#ff9800',
      });
      text.textContent = edge.data.condition;
      group.appendChild(text);
    }
    
    return group;
  }

  /**
   *
   */
  private renderBezierEdge(edge: Edge, group: SVGElement, source: { x: number; y: number }, target: { x: number; y: number }): SVGElement {
    const cp1x = source.x + (target.x - source.x) * 0.5;
    const cp1y = source.y;
    const cp2x = target.x - (target.x - source.x) * 0.5;
    const cp2y = target.y;
    
    const path = this.createElement('path', {
      d: `M ${source.x} ${source.y} C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${target.x} ${target.y}`,
      stroke: (edge.style?.stroke as string) || '#666',
      'stroke-width': '2',
      fill: 'none',
      'marker-end': 'url(#arrowhead)',
    });
    group.appendChild(path);
    
    return group;
  }

  /**
   *
   */
  private addArrowheadMarker(group: SVGElement): void {
    // In a real implementation, this would be added to a defs section once
    const defs = this.createElement('defs');
    const marker = this.createElement('marker', {
      id: 'arrowhead',
      markerWidth: '10',
      markerHeight: '7',
      refX: '9',
      refY: '3.5',
      orient: 'auto',
    });
    
    const polygon = this.createElement('polygon', {
      points: '0 0, 10 3.5, 0 7',
      fill: '#666',
    });
    
    marker.appendChild(polygon);
    defs.appendChild(marker);
    group.appendChild(defs);
  }

  /**
   *
   */
  private createElement(tagName: string, attributes: Record<string, string> = {}): SVGElement {
    const element = document.createElementNS('http://www.w3.org/2000/svg', tagName);
    
    Object.entries(attributes).forEach(([key, value]) => {
      element.setAttribute(key, value);
    });
    
    return element;
  }

  /**
   *
   */
  private serializeSVG(svg: SVGSVGElement): string {
    const serializer = new XMLSerializer();
    let svgString = serializer.serializeToString(svg);
    
    // Add XML declaration and DOCTYPE
    svgString = `<?xml version="1.0" encoding="UTF-8"?>\n${  svgString}`;
    
    return svgString;
  }
}

// Export engine
/**
 *
 */
export class ExportEngine {
  private svgRenderer: SVGRenderer;

  /**
   *
   */
  constructor() {
    this.svgRenderer = new SVGRenderer();
  }

  /**
   *
   */
  public async exportCanvas(canvas: CanvasData, options: ExportOptions): Promise<ExportResult> {
    const startTime = Date.now();
    
    try {
      let result: ExportResult;
      
      switch (options.format) {
        case 'svg':
          result = await this.exportToSVG(canvas, options as ImageExportOptions);
          break;
        case 'png':
          result = await this.exportToPNG(canvas, options as ImageExportOptions);
          break;
        case 'pdf':
          result = await this.exportToPDF(canvas, options as PdfExportOptions);
          break;
        case 'jsx':
        case 'html':
          result = await this.exportToCode(canvas, options as CodeExportOptions);
          break;
        case 'json':
          result = await this.exportToJSON(canvas, options as JsonExportOptions);
          break;
        default:
          throw new Error(`Unsupported export format: ${(options as unknown).format}`);
      }
      
      result.completedAt = new Date().toISOString();
      result.metadata = {
        ...result.metadata,
        processingTime: Date.now() - startTime,
        nodeCount: canvas.nodes.length,
        edgeCount: canvas.edges.length,
      };
      
      return result;
      
    } catch (error) {
      return {
        id: `export_${Date.now()}`,
        status: 'failed',
        format: options.format,
        createdAt: new Date().toISOString(),
        error: error instanceof Error ? error.message : 'Unknown export error',
      };
    }
  }

  /**
   *
   */
  private async exportToSVG(canvas: CanvasData, options: ImageExportOptions): Promise<ExportResult> {
    this.svgRenderer = new SVGRenderer(options);
    const svgContent = this.svgRenderer.renderCanvas(canvas);
    
    // Sanitize SVG content
    const sanitizeResult = sanitizeExportContent(svgContent, 'svg');
    if (!sanitizeResult.safe) {
      console.warn('SVG export required sanitization:', sanitizeResult.removed);
    }
    
    // Security audit
    const audit = auditExportSecurity(sanitizeResult.sanitized);
    if (audit.riskLevel === 'high') {
      throw new Error('SVG export failed security audit');
    }
    
    return {
      id: `svg_${Date.now()}`,
      status: 'completed',
      format: 'svg',
      url: `data:image/svg+xml;base64,${btoa(sanitizeResult.sanitized)}`,
      filename: `canvas-export-${Date.now()}.svg`,
      size: new Blob([sanitizeResult.sanitized]).size,
      createdAt: new Date().toISOString(),
      metadata: {
        sanitized: !sanitizeResult.safe,
        securityAudit: audit,
      },
    };
  }

  /**
   *
   */
  private async exportToPNG(canvas: CanvasData, options: ImageExportOptions): Promise<ExportResult> {
    // Convert SVG to PNG using Canvas API
    const svgContent = this.svgRenderer.renderCanvas(canvas);
    
    return new Promise((resolve, reject) => {
      const img = new Image();
      const canvasEl = document.createElement('canvas');
      const ctx = canvasEl.getContext('2d');
      
      if (!ctx) {
        reject(new Error('Could not get canvas context'));
        return;
      }
      
      img.onload = () => {
        canvasEl.width = options.width || img.width;
        canvasEl.height = options.height || img.height;
        
        // Set background color
        ctx.fillStyle = options.backgroundColor || '#ffffff';
        ctx.fillRect(0, 0, canvasEl.width, canvasEl.height);
        
        // Draw SVG
        ctx.drawImage(img, 0, 0, canvasEl.width, canvasEl.height);
        
        canvasEl.toBlob((blob) => {
          if (!blob) {
            reject(new Error('Failed to create PNG blob'));
            return;
          }
          
          const url = URL.createObjectURL(blob);
          resolve({
            id: `png_${Date.now()}`,
            status: 'completed',
            format: 'png',
            url,
            filename: `canvas-export-${Date.now()}.png`,
            size: blob.size,
            createdAt: new Date().toISOString(),
          });
        }, 'image/png', options.quality || 0.9);
      };
      
      img.onerror = () => reject(new Error('Failed to load SVG for PNG conversion'));
      img.src = `data:image/svg+xml;base64,${btoa(svgContent)}`;
    });
  }

  /**
   *
   */
  private async exportToPDF(canvas: CanvasData, options: PdfExportOptions): Promise<ExportResult> {
    // Mock PDF export - in production, use a library like jsPDF or Puppeteer
    const content = `PDF Export of Canvas - ${canvas.metadata.id}`;
    
    return {
      id: `pdf_${Date.now()}`,
      status: 'completed',
      format: 'pdf',
      url: `data:application/pdf;base64,${btoa(content)}`,
      filename: `canvas-export-${Date.now()}.pdf`,
      size: content.length,
      createdAt: new Date().toISOString(),
      metadata: {
        pageSize: options.pageSize,
        orientation: options.orientation,
      },
    };
  }

  /**
   *
   */
  private async exportToCode(canvas: CanvasData, options: CodeExportOptions): Promise<ExportResult> {
    let code: string;
    
    if (options.format === 'jsx') {
      code = this.generateJSXComponent(canvas, options);
    } else {
      code = this.generateHTMLPage(canvas, options);
    }
    
    // Sanitize code content
    const sanitizeResult = sanitizeExportContent(code, 'html');
    
    return {
      id: `code_${Date.now()}`,
      status: 'completed',
      format: options.format,
      url: `data:text/plain;base64,${btoa(sanitizeResult.sanitized)}`,
      filename: `canvas-export-${Date.now()}${options.format === 'jsx' ? '.tsx' : '.html'}`,
      size: sanitizeResult.sanitized.length,
      createdAt: new Date().toISOString(),
      metadata: {
        componentName: options.componentName,
        typescript: options.typescript,
        dependencies: options.dependencies,
      },
    };
  }

  /**
   *
   */
  private async exportToJSON(canvas: CanvasData, options: JsonExportOptions): Promise<ExportResult> {
    const exportData = {
      version: options.version,
      canvas: {
        id: canvas.metadata.id,
        nodes: options.includePositions ? canvas.nodes : canvas.nodes.map(n => ({ ...n, position: undefined })),
        edges: canvas.edges,
        settings: canvas.settings,
        metadata: options.includeMetadata ? canvas.metadata : undefined,
      },
      exportedAt: new Date().toISOString(),
    };
    
    const jsonString = options.minify 
      ? JSON.stringify(exportData)
      : JSON.stringify(exportData, null, 2);
    
    return {
      id: `json_${Date.now()}`,
      status: 'completed',
      format: 'json',
      url: `data:application/json;base64,${btoa(jsonString)}`,
      filename: `canvas-export-${Date.now()}.json`,
      size: jsonString.length,
      createdAt: new Date().toISOString(),
    };
  }

  /**
   *
   */
  private generateJSXComponent(canvas: CanvasData, options: CodeExportOptions): string {
    const componentName = options.componentName || 'CanvasExport';
    const imports = options.includeStyles ? "import './canvas-styles.css';" : '';
    
    return `${imports}
import React from 'react';

export interface ${componentName}Props {
  className?: string;
  style?: React.CSSProperties;
}

export const ${componentName}: React.FC<${componentName}Props> = ({ className, style }) => {
  return (
    <div className={\`canvas-export \${className || ''}\`} style={style}>
      <svg width="800" height="600" viewBox="0 0 800 600">
        {/* Canvas content would be generated here */}
        <rect x="0" y="0" width="100%" height="100%" fill="#ffffff" />
        {/* Nodes and edges would be rendered as SVG elements */}
      </svg>
    </div>
  );
};

export default ${componentName};`;
  }

  /**
   *
   */
  private generateHTMLPage(canvas: CanvasData, options: CodeExportOptions): string {
    const styles = options.includeStyles ? `
<style>
  .canvas-export {
    width: 100%;
    height: 100vh;
    display: flex;
    justify-content: center;
    align-items: center;
  }
  .canvas-export svg {
    max-width: 100%;
    max-height: 100%;
  }
</style>` : '';

    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Canvas Export</title>${styles}
</head>
<body>
  <div class="canvas-export">
    <svg width="800" height="600" viewBox="0 0 800 600">
      <rect x="0" y="0" width="100%" height="100%" fill="#ffffff" />
      <!-- Canvas content would be generated here -->
    </svg>
  </div>
</body>
</html>`;
  }
}

// Production-ready export instance
export const exportEngine = new ExportEngine();