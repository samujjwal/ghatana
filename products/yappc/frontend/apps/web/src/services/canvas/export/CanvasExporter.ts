/**
 * Canvas Exporter
 * 
 * Exports canvas data to various formats (JSON, PNG, SVG).
 * Handles file downloads and format conversions.
 * 
 * @doc.type service
 * @doc.purpose Canvas export functionality
 * @doc.layer product
 * @doc.pattern Service
 */

import type { CanvasState } from '../../../components/canvas/workspace/canvasAtoms';
import type { CanvasSnapshot } from '../CanvasPersistence';

export interface ExportOptions {
    filename?: string;
    pretty?: boolean;
}

export interface ImageExportOptions extends ExportOptions {
    width?: number;
    height?: number;
    backgroundColor?: string;
    scale?: number;
}

export class CanvasExporter {
    /**
     * Export canvas state to JSON
     */
    public async exportToJSON(
        state: CanvasState,
        options: ExportOptions = {}
    ): Promise<string> {
        const { pretty = true } = options;

        const exportData = {
            version: '1.0',
            timestamp: Date.now(),
            data: state,
        };

        return JSON.stringify(exportData, null, pretty ? 2 : 0);
    }

    /**
     * Export snapshot to JSON
     */
    public async exportSnapshotToJSON(
        snapshot: CanvasSnapshot,
        options: ExportOptions = {}
    ): Promise<string> {
        const { pretty = true } = options;
        return JSON.stringify(snapshot, null, pretty ? 2 : 0);
    }

    /**
     * Export canvas to PNG
     */
    public async exportToPNG(
        canvasElement: HTMLCanvasElement,
        options: ImageExportOptions = {}
    ): Promise<Blob> {
        const {
            width = canvasElement.width,
            height = canvasElement.height,
            backgroundColor = '#ffffff',
            scale = 1,
        } = options;

        // Create temporary canvas for export
        const exportCanvas = document.createElement('canvas');
        exportCanvas.width = width * scale;
        exportCanvas.height = height * scale;

        const ctx = exportCanvas.getContext('2d');
        if (!ctx) {
            throw new Error('Failed to get canvas context');
        }

        // Fill background
        ctx.fillStyle = backgroundColor;
        ctx.fillRect(0, 0, exportCanvas.width, exportCanvas.height);

        // Scale and draw original canvas
        ctx.scale(scale, scale);
        ctx.drawImage(canvasElement, 0, 0, width, height);

        // Convert to blob
        return new Promise((resolve, reject) => {
            exportCanvas.toBlob(
                (blob) => {
                    if (blob) {
                        resolve(blob);
                    } else {
                        reject(new Error('Failed to create PNG blob'));
                    }
                },
                'image/png'
            );
        });
    }

    /**
     * Export canvas to SVG
     */
    public async exportToSVG(
        state: CanvasState,
        options: ImageExportOptions = {}
    ): Promise<string> {
        const {
            width = 1000,
            height = 1000,
            backgroundColor = '#ffffff',
        } = options;

        // Build SVG markup
        const elements = state.elements.map(el => this.elementToSVG(el)).join('\n');
        const connections = state.connections.map(conn => this.connectionToSVG(conn, state)).join('\n');

        const svg = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}">
    <rect width="${width}" height="${height}" fill="${backgroundColor}" />
    <g id="connections">
        ${connections}
    </g>
    <g id="elements">
        ${elements}
    </g>
</svg>`;

        return svg;
    }

    /**
     * Download file to user's computer
     */
    public async downloadFile(
        content: string | Blob,
        filename: string,
        mimeType: string = 'application/octet-stream'
    ): Promise<void> {
        const blob = content instanceof Blob
            ? content
            : new Blob([content], { type: mimeType });

        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;

        // Trigger download
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);

        // Cleanup
        setTimeout(() => URL.revokeObjectURL(url), 100);
    }

    /**
     * Convert canvas element to SVG markup
     */
    private elementToSVG(element: unknown): string {
        const { position, data, type } = element;
        const x = position.x;
        const y = position.y;
        const label = data?.label || '';

        // Different shapes based on type
        switch (type) {
            case 'component':
                return `<rect x="${x}" y="${y}" width="150" height="60" fill="#1976d2" stroke="#0d47a1" stroke-width="2" rx="5"/>
                        <text x="${x + 75}" y="${y + 35}" text-anchor="middle" fill="white" font-family="Arial" font-size="14">${label}</text>`;

            case 'api':
                return `<rect x="${x}" y="${y}" width="150" height="60" fill="#9c27b0" stroke="#6a1b9a" stroke-width="2" rx="5"/>
                        <text x="${x + 75}" y="${y + 35}" text-anchor="middle" fill="white" font-family="Arial" font-size="14">${label}</text>`;

            case 'data':
                return `<ellipse cx="${x + 75}" cy="${y + 30}" rx="75" ry="30" fill="#4caf50" stroke="#2e7d32" stroke-width="2"/>
                        <text x="${x + 75}" y="${y + 35}" text-anchor="middle" fill="white" font-family="Arial" font-size="14">${label}</text>`;

            default:
                return `<rect x="${x}" y="${y}" width="150" height="60" fill="#666" stroke="#333" stroke-width="2" rx="5"/>
                        <text x="${x + 75}" y="${y + 35}" text-anchor="middle" fill="white" font-family="Arial" font-size="14">${label}</text>`;
        }
    }

    /**
     * Convert connection to SVG line
     */
    private connectionToSVG(connection: unknown, state: CanvasState): string {
        const sourceElement = state.elements.find(el => el.id === connection.source);
        const targetElement = state.elements.find(el => el.id === connection.target);

        if (!sourceElement || !targetElement) return '';

        const x1 = sourceElement.position.x + 75;
        const y1 = sourceElement.position.y + 30;
        const x2 = targetElement.position.x + 75;
        const y2 = targetElement.position.y + 30;

        return `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="#999" stroke-width="2" marker-end="url(#arrowhead)"/>`;
    }
}

/**
 * Canvas Importer
 * 
 * Imports canvas data from various formats.
 */
export class CanvasImporter {
    /**
     * Import canvas from JSON
     */
    public async importFromJSON(json: string): Promise<CanvasState> {
        try {
            const data = JSON.parse(json);

            // Validate structure
            if (!this.isValidCanvasData(data)) {
                throw new Error('Invalid canvas data format');
            }

            return data.data || data;
        } catch (error) {
            throw new Error(`Failed to import JSON: ${error}`);
        }
    }

    /**
     * Import snapshot from JSON
     */
    public async importSnapshotFromJSON(json: string): Promise<CanvasSnapshot> {
        try {
            const snapshot = JSON.parse(json);

            if (!this.isValidSnapshot(snapshot)) {
                throw new Error('Invalid snapshot format');
            }

            return snapshot;
        } catch (error) {
            throw new Error(`Failed to import snapshot: ${error}`);
        }
    }

    /**
     * Read file as text
     */
    public async readFile(file: File): Promise<string> {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result as string);
            reader.onerror = () => reject(reader.error);
            reader.readAsText(file);
        });
    }

    /**
     * Validate canvas data structure
     */
    private isValidCanvasData(data: unknown): boolean {
        if (!data) return false;

        const canvasData = data.data || data;

        return (
            Array.isArray(canvasData.elements) &&
            Array.isArray(canvasData.connections)
        );
    }

    /**
     * Validate snapshot structure
     */
    private isValidSnapshot(snapshot: unknown): boolean {
        return (
            snapshot &&
            typeof snapshot.id === 'string' &&
            typeof snapshot.projectId === 'string' &&
            typeof snapshot.canvasId === 'string' &&
            typeof snapshot.version === 'number' &&
            typeof snapshot.timestamp === 'number' &&
            this.isValidCanvasData(snapshot.data)
        );
    }
}

// Export singleton instances
export const canvasExporter = new CanvasExporter();
export const canvasImporter = new CanvasImporter();
