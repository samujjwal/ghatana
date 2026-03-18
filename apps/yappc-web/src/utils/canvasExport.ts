/**
 * Canvas Export Utilities
 * 
 * Export canvas content as images (PNG/SVG) or PDF.
 * Optional dependencies: html2canvas, jsPDF
 * 
 * @doc.type utility
 * @doc.purpose Canvas export functionality
 * @doc.layer product
 * @doc.pattern Utility
 */

import type { CanvasMode, AbstractionLevel } from '../types/canvas';

export type ExportFormat = 'png' | 'svg' | 'pdf';

export interface ExportOptions {
    format: ExportFormat;
    fileName?: string;
    quality?: number; // 0-1 for PNG
    scale?: number;
    backgroundColor?: string;
    includeMetadata?: boolean;
}

const DEFAULT_OPTIONS: Required<Omit<ExportOptions, 'format'>> = {
    fileName: 'canvas-export',
    quality: 0.95,
    scale: 2,
    backgroundColor: '#ffffff',
    includeMetadata: true,
};

/**
 * Export canvas as PNG using html2canvas
 * 
 * @requires npm install html2canvas
 * @throws Error if html2canvas is not installed
 */
export async function exportAsPNG(
    element: HTMLElement,
    options: Partial<ExportOptions> = {}
): Promise<void> {
    const opts = { ...DEFAULT_OPTIONS, ...options };

    try {
        // Dynamic import to avoid bundling if not used
        // @ts-ignore - html2canvas may not be installed
        const html2canvas = await import('html2canvas');

        const canvas = await html2canvas.default(element, {
            scale: opts.scale,
            backgroundColor: opts.backgroundColor,
            logging: false,
            useCORS: true,
        });

        // Convert canvas to blob
        canvas.toBlob(
            (blob: Blob | null) => {
                if (blob) {
                    downloadBlob(blob, `${opts.fileName}.png`);
                }
            },
            'image/png',
            opts.quality
        );
    } catch (error) {
        console.error('Failed to export as PNG:', error);
        throw new Error('PNG export requires html2canvas library. Install with: npm install html2canvas');
    }
}

/**
 * Export canvas as SVG
 * 
 * Note: Only works for SVG-based canvases (diagrams, graphs)
 */
export function exportAsSVG(
    element: HTMLElement,
    options: Partial<ExportOptions> = {}
): void {
    const opts = { ...DEFAULT_OPTIONS, ...options };

    try {
        // Find SVG element
        const svgElement = element.querySelector('svg');
        if (!svgElement) {
            throw new Error('No SVG element found in canvas');
        }

        // Clone and serialize SVG
        const svgClone = svgElement.cloneNode(true) as SVGElement;
        const svgString = new XMLSerializer().serializeToString(svgClone);

        // Add metadata if requested
        let finalSvg = svgString;
        if (opts.includeMetadata) {
            finalSvg = addSVGMetadata(svgString, opts.fileName);
        }

        // Create blob and download
        const blob = new Blob([finalSvg], { type: 'image/svg+xml' });
        downloadBlob(blob, `${opts.fileName}.svg`);
    } catch (error) {
        console.error('Failed to export as SVG:', error);
        throw error;
    }
}

/**
 * Export canvas as PDF using jsPDF
 * 
 * @requires npm install jspdf html2canvas
 * @throws Error if dependencies not installed
 */
export async function exportAsPDF(
    element: HTMLElement,
    options: Partial<ExportOptions> = {}
): Promise<void> {
    const opts = { ...DEFAULT_OPTIONS, ...options };

    try {
        // Dynamic imports
        // @ts-ignore - jspdf and html2canvas may not be installed
        const [{ default: jsPDF }, html2canvas] = await Promise.all([
            import('jspdf'),
            import('html2canvas'),
        ]);

        // Capture canvas as image
        const canvas = await html2canvas.default(element, {
            scale: opts.scale,
            backgroundColor: opts.backgroundColor,
            logging: false,
            useCORS: true,
        });

        // Calculate PDF dimensions
        const imgData = canvas.toDataURL('image/png', opts.quality);
        const pdf = new jsPDF({
            orientation: canvas.width > canvas.height ? 'landscape' : 'portrait',
            unit: 'px',
            format: [canvas.width, canvas.height],
        });

        // Add image to PDF
        pdf.addImage(imgData, 'PNG', 0, 0, canvas.width, canvas.height);

        // Add metadata if requested
        if (opts.includeMetadata) {
            pdf.setProperties({
                title: opts.fileName,
                subject: 'Canvas Export',
                creator: 'YAPPC Canvas System',
            });
        }

        // Save PDF
        pdf.save(`${opts.fileName}.pdf`);
    } catch (error) {
        console.error('Failed to export as PDF:', error);
        throw new Error('PDF export requires jspdf and html2canvas libraries. Install with: npm install jspdf html2canvas');
    }
}

/**
 * Main export function - routes to appropriate exporter
 */
export async function exportCanvas(
    element: HTMLElement,
    format: ExportFormat,
    options: Partial<ExportOptions> = {}
): Promise<void> {
    const opts = { ...options, format };

    switch (format) {
        case 'png':
            return exportAsPNG(element, opts);
        case 'svg':
            return exportAsSVG(element, opts);
        case 'pdf':
            return exportAsPDF(element, opts);
        default:
            throw new Error(`Unsupported export format: ${format}`);
    }
}

/**
 * React hook for canvas export
 */
export function useCanvasExport(
    mode: CanvasMode,
    level: AbstractionLevel,
    elementRef: React.RefObject<HTMLElement>
) {
    const exportAs = async (format: ExportFormat, customOptions?: Partial<ExportOptions>) => {
        if (!elementRef.current) {
            throw new Error('Canvas element ref not available');
        }

        const fileName = `${mode}-${level}-${Date.now()}`;
        const options: Partial<ExportOptions> = {
            fileName,
            ...customOptions,
        };

        return exportCanvas(elementRef.current, format, options);
    };

    return { exportAs };
}

/**
 * Helper: Download blob as file
 */
function downloadBlob(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
}

/**
 * Helper: Add metadata to SVG
 */
function addSVGMetadata(svgString: string, title: string): string {
    const metadata = `
    <metadata>
      <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description>
          <dc:title xmlns:dc="http://purl.org/dc/elements/1.1/">${title}</dc:title>
          <dc:creator xmlns:dc="http://purl.org/dc/elements/1.1/">YAPPC Canvas System</dc:creator>
          <dc:date xmlns:dc="http://purl.org/dc/elements/1.1/">${new Date().toISOString()}</dc:date>
        </rdf:Description>
      </rdf:RDF>
    </metadata>
  `;

    // Insert metadata after opening <svg> tag
    return svgString.replace(/<svg([^>]*)>/, `<svg$1>${metadata}`);
}
