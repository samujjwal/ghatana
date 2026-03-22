/**
 * CanvasExporter - Export canvas to various formats
 * 
 * Supports SVG, PNG, and JSON export
 * 
 * @doc.type class
 * @doc.purpose Canvas export functionality
 * @doc.layer lib
 * @doc.pattern Exporter
 */

import { HierarchicalNode } from './HierarchyManager';
import type { Connection } from './NodeManipulation';
import type { DrawingStroke } from './DrawingManager';

export interface CanvasExportData {
    nodes: HierarchicalNode[];
    connections: Connection[];
    drawings: DrawingStroke[];
    viewport: { x: number; y: number; zoom: number };
    metadata: {
        version: string;
        exportDate: string;
        projectId?: string;
    };
}

export class CanvasExporter {
    /**
     * Export canvas to JSON
     */
    public exportToJSON(
        nodes: HierarchicalNode[],
        connections: Connection[],
        drawings: DrawingStroke[],
        viewport: { x: number; y: number; zoom: number },
        projectId?: string
    ): string {
        const data: CanvasExportData = {
            nodes,
            connections,
            drawings,
            viewport,
            metadata: {
                version: '1.0.0',
                exportDate: new Date().toISOString(),
                projectId
            }
        };

        return JSON.stringify(data, null, 2);
    }

    /**
     * Import canvas from JSON
     */
    public importFromJSON(json: string): CanvasExportData {
        try {
            const data = JSON.parse(json) as CanvasExportData;

            // Validate structure
            if (!data.nodes || !Array.isArray(data.nodes)) {
                throw new Error('Invalid canvas data: missing or invalid nodes');
            }

            return data;
        } catch (error) {
            throw new Error(`Failed to import canvas: ${error}`);
        }
    }

    /**
     * Export canvas to SVG
     */
    public exportToSVG(
        nodes: HierarchicalNode[],
        connections: Connection[],
        drawings: DrawingStroke[],
        options: {
            width?: number;
            height?: number;
            padding?: number;
            includeBackground?: boolean;
            backgroundColor?: string;
        } = {}
    ): string {
        const {
            padding = 50,
            includeBackground = true,
            backgroundColor = '#ffffff'
        } = options;

        // Calculate bounds
        const bounds = this.calculateBounds(nodes);
        const width = options.width || (bounds.maxX - bounds.minX + padding * 2);
        const height = options.height || (bounds.maxY - bounds.minY + padding * 2);
        const offsetX = -bounds.minX + padding;
        const offsetY = -bounds.minY + padding;

        let svg = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
`;

        // Background
        if (includeBackground) {
            svg += `  <rect width="${width}" height="${height}" fill="${backgroundColor}"/>\n`;
        }

        // Drawings layer
        if (drawings.length > 0) {
            svg += '  <g id="drawings">\n';
            drawings.forEach(stroke => {
                svg += this.drawingToSVGPath(stroke, offsetX, offsetY);
            });
            svg += '  </g>\n';
        }

        // Connections layer
        if (connections.length > 0) {
            svg += '  <g id="connections">\n';
            connections.forEach(conn => {
                const source = nodes.find(n => n.id === conn.source);
                const target = nodes.find(n => n.id === conn.target);
                if (source && target) {
                    svg += this.connectionToSVGLine(source, target, conn, offsetX, offsetY);
                }
            });
            svg += '  </g>\n';
        }

        // Nodes layer
        svg += '  <g id="nodes">\n';
        nodes.forEach(node => {
            svg += this.nodeToSVGRect(node, offsetX, offsetY);
        });
        svg += '  </g>\n';

        svg += '</svg>';
        return svg;
    }

    /**
     * Export canvas to PNG (returns data URL)
     */
    public async exportToPNG(
        canvasElement: HTMLElement,
        options: {
            scale?: number;
            backgroundColor?: string;
        } = {}
    ): Promise<string> {
        const { scale = 2, backgroundColor = '#ffffff' } = options;

        // Use html2canvas if available
        if (typeof window !== 'undefined' && (window as unknown).html2canvas) {
            const canvas = await (window as unknown).html2canvas(canvasElement, {
                scale,
                backgroundColor,
                logging: false,
                useCORS: true
            });
            return canvas.toDataURL('image/png');
        }

        throw new Error('html2canvas not available. Please include the library.');
    }

    /**
     * Download file to user's computer
     */
    public downloadFile(content: string, filename: string, mimeType: string = 'text/plain'): void {
        const blob = new Blob([content], { type: mimeType });
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
     * Download data URL as file
     */
    public downloadDataURL(dataURL: string, filename: string): void {
        const link = document.createElement('a');
        link.href = dataURL;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    }

    // Helper methods

    private calculateBounds(nodes: HierarchicalNode[]): {
        minX: number;
        minY: number;
        maxX: number;
        maxY: number;
    } {
        if (nodes.length === 0) {
            return { minX: 0, minY: 0, maxX: 800, maxY: 600 };
        }

        let minX = Infinity;
        let minY = Infinity;
        let maxX = -Infinity;
        let maxY = -Infinity;

        nodes.forEach(node => {
            minX = Math.min(minX, node.position.x);
            minY = Math.min(minY, node.position.y);
            maxX = Math.max(maxX, node.position.x + node.size.width);
            maxY = Math.max(maxY, node.position.y + node.size.height);
        });

        return { minX, minY, maxX, maxY };
    }

    private nodeToSVGRect(node: HierarchicalNode, offsetX: number, offsetY: number): string {
        const x = node.position.x + offsetX;
        const y = node.position.y + offsetY;
        const { width, height } = node.size;

        const fillColor = this.getNodeColor(node);
        const strokeColor = node.data.borderColor || '#d0d0d0';
        const text = node.data.text || node.data.label || 'Node';

        return `    <g id="node-${node.id}">
      <rect x="${x}" y="${y}" width="${width}" height="${height}" 
            fill="${fillColor}" stroke="${strokeColor}" stroke-width="2" rx="4"/>
      <text x="${x + width / 2}" y="${y + height / 2}" 
            text-anchor="middle" dominant-baseline="middle"
            font-family="sans-serif" font-size="14" fill="#333">
        ${this.escapeXML(text)}
      </text>
    </g>\n`;
    }

    private connectionToSVGLine(
        source: HierarchicalNode,
        target: HierarchicalNode,
        conn: Connection,
        offsetX: number,
        offsetY: number
    ): string {
        const x1 = source.position.x + source.size.width / 2 + offsetX;
        const y1 = source.position.y + source.size.height / 2 + offsetY;
        const x2 = target.position.x + target.size.width / 2 + offsetX;
        const y2 = target.position.y + target.size.height / 2 + offsetY;

        const color = conn.data?.color || '#999';
        const strokeWidth = conn.data?.strokeWidth || 2;

        return `    <line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" 
          stroke="${color}" stroke-width="${strokeWidth}" marker-end="url(#arrowhead)"/>\n`;
    }

    private drawingToSVGPath(stroke: DrawingStroke, offsetX: number, offsetY: number): string {
        if (stroke.points.length < 2) return '';

        const pathData = stroke.points
            .map((point, i) => {
                const x = point.x + offsetX;
                const y = point.y + offsetY;
                return i === 0 ? `M ${x} ${y}` : `L ${x} ${y}`;
            })
            .join(' ');

        return `    <path d="${pathData}" stroke="${stroke.color}" stroke-width="${stroke.size}" 
          fill="none" stroke-linecap="round" stroke-linejoin="round"/>\n`;
    }

    private getNodeColor(node: HierarchicalNode): string {
        if (node.type === 'sticky') return node.data.color || '#fef3c7';
        if (node.type === 'code') return '#1e1e1e';
        if (node.type === 'text') return '#fafafa';
        return node.data.backgroundColor || '#ffffff';
    }

    private escapeXML(text: string): string {
        return text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&apos;');
    }
}
