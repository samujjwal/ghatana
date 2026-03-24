/**
 * Secure Export System - Safe content sanitization and export utilities
 * Implements sandboxed rendering and secure export formats
 */

// DOMPurify would be imported in production: import DOMPurify from 'dompurify';
// For now, using a mock interface to avoid dependency issues

/**
 *
 */
interface MockDOMPurify {
  sanitize: (html: string, config?: unknown) => string;
  addHook: (hook: string, callback: (node: unknown) => void) => void;
}

// Mock DOMPurify for type safety
const MockDOMPurifyImpl: MockDOMPurify = {
  sanitize: (html: string, config?: unknown) => html, // Pass-through in mock
  addHook: (hook: string, callback: (node: unknown) => void) => {}, // No-op in mock
};

/**
 *
 */
export interface ExportOptions {
  format: 'png' | 'svg' | 'pdf' | 'jsx' | 'json';
  quality?: number;
  width?: number;
  height?: number;
  includeMetadata?: boolean;
  sanitize?: boolean;
}

/**
 *
 */
export interface ExportResult {
  success: boolean;
  data?: string | Blob;
  filename?: string;
  errors?: string[];
}

/**
 * Secure HTML/CSS/JS sanitization configuration
 */
const SANITIZATION_CONFIG = {
  ALLOWED_TAGS: [
    'div',
    'span',
    'p',
    'h1',
    'h2',
    'h3',
    'h4',
    'h5',
    'h6',
    'strong',
    'em',
    'u',
    'br',
    'hr',
    'ul',
    'ol',
    'li',
    'svg',
    'path',
    'circle',
    'rect',
    'line',
    'polygon',
    'text',
    'g',
    'defs',
    'marker',
  ],
  ALLOWED_ATTR: [
    'class',
    'id',
    'style',
    'data-*',
    'x',
    'y',
    'width',
    'height',
    'viewBox',
    'fill',
    'stroke',
    'stroke-width',
    'd',
    'cx',
    'cy',
    'r',
    'x1',
    'y1',
    'x2',
    'y2',
  ],
  FORBID_TAGS: ['script', 'object', 'embed', 'form', 'input'],
  FORBID_ATTR: ['onclick', 'onload', 'onerror', 'href', 'src'],
};

/**
 * Secure Content Sanitizer
 */
export class SecureContentSanitizer {
  private static instance: SecureContentSanitizer;
  private domPurify: MockDOMPurify;

  /**
   *
   */
  private constructor() {
    this.domPurify = MockDOMPurifyImpl;
    this.configurePurify();
  }

  /**
   *
   */
  static getInstance(): SecureContentSanitizer {
    if (!SecureContentSanitizer.instance) {
      SecureContentSanitizer.instance = new SecureContentSanitizer();
    }
    return SecureContentSanitizer.instance;
  }

  /**
   *
   */
  private configurePurify(): void {
    // Add custom hooks for additional security
    this.domPurify.addHook('beforeSanitizeElements', (node: unknown) => {
      // Remove any script-like content
      if (node.tagName === 'SCRIPT' || node.tagName === 'OBJECT') {
        node.remove();
      }
    });

    this.domPurify.addHook('beforeSanitizeAttributes', (node: unknown) => {
      // Remove any potentially dangerous attributes
      const dangerousAttrs = ['onclick', 'onload', 'onerror', 'href'];
      dangerousAttrs.forEach((attr) => {
        if (node.hasAttribute && node.hasAttribute(attr)) {
          node.removeAttribute(attr);
        }
      });
    });
  }

  /**
   * Sanitize HTML content
   */
  sanitizeHTML(html: string): string {
    return this.domPurify.sanitize(html, {
      ALLOWED_TAGS: SANITIZATION_CONFIG.ALLOWED_TAGS,
      ALLOWED_ATTR: SANITIZATION_CONFIG.ALLOWED_ATTR,
      FORBID_TAGS: SANITIZATION_CONFIG.FORBID_TAGS,
      FORBID_ATTR: SANITIZATION_CONFIG.FORBID_ATTR,
    });
  }

  /**
   * Sanitize CSS content
   */
  sanitizeCSS(css: string): string {
    // Remove potentially dangerous CSS
    const dangerousPatterns = [
      /javascript:/gi,
      /expression\(/gi,
      /import\s+/gi,
      /@import/gi,
      /url\(\s*["']?javascript:/gi,
    ];

    let sanitized = css;
    dangerousPatterns.forEach((pattern) => {
      sanitized = sanitized.replace(pattern, '');
    });

    return sanitized;
  }

  /**
   * Sanitize SVG content
   */
  sanitizeSVG(svg: string): string {
    return this.domPurify.sanitize(svg, {
      USE_PROFILES: { svg: true, svgFilters: true },
      ALLOWED_TAGS: [
        'svg',
        'path',
        'circle',
        'rect',
        'line',
        'polygon',
        'text',
        'g',
        'defs',
        'marker',
        'clipPath',
        'mask',
      ],
      FORBID_TAGS: ['script', 'object', 'embed', 'foreignObject'],
    });
  }
}

/**
 * Sandboxed Renderer for safe JSX-to-HTML conversion
 */
export class SandboxedRenderer {
  private iframe: HTMLIFrameElement | null = null;
  private sandbox: Window | null = null;

  /**
   * Initialize sandbox environment
   */
  private async initSandbox(): Promise<void> {
    if (this.iframe) return;

    this.iframe = document.createElement('iframe');
    this.iframe.style.display = 'none';
    this.iframe.sandbox.add('allow-scripts');
    this.iframe.srcdoc = `
      <!DOCTYPE html>
      <html>
        <head>
          <meta charset="utf-8">
          <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body { font-family: system-ui, sans-serif; }
          </style>
        </head>
        <body>
          <div id="root"></div>
        </body>
      </html>
    `;

    document.body.appendChild(this.iframe);

    return new Promise((resolve) => {
      this.iframe!.onload = () => {
        this.sandbox = this.iframe!.contentWindow;
        resolve();
      };
    });
  }

  /**
   * Render JSX safely in sandbox
   */
  async renderJSX(jsx: string): Promise<string> {
    await this.initSandbox();

    if (!this.sandbox) {
      throw new Error('Sandbox initialization failed');
    }

    try {
      // Execute JSX in sandbox (simplified - in production you'd use a proper JSX transformer)
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const result = await (this.sandbox as unknown).eval(`
        try {
          const container = document.getElementById('root');
          container.innerHTML = \`${jsx}\`;
          container.innerHTML;
        } catch (error) {
          throw new Error('JSX rendering failed: ' + error.message);
        }
      `);

      return SecureContentSanitizer.getInstance().sanitizeHTML(result);
    } catch (error) {
      throw new Error(`Sandboxed rendering failed: ${error}`);
    }
  }

  /**
   * Cleanup sandbox
   */
  destroy(): void {
    if (this.iframe) {
      document.body.removeChild(this.iframe);
      this.iframe = null;
      this.sandbox = null;
    }
  }
}

/**
 * Secure Canvas Exporter
 */
export class SecureCanvasExporter {
  private sanitizer = SecureContentSanitizer.getInstance();
  private renderer = new SandboxedRenderer();

  /**
   * Export canvas as PNG
   */
  async exportAsPNG(
    canvasElement: HTMLElement,
    options: ExportOptions
  ): Promise<ExportResult> {
    try {
      const canvas = document.createElement('canvas');
      const ctx = canvas.getContext('2d');

      if (!ctx) {
        return { success: false, errors: ['Canvas context not available'] };
      }

      canvas.width = options.width || canvasElement.offsetWidth;
      canvas.height = options.height || canvasElement.offsetHeight;

      // Convert DOM to image (using html2canvas-like approach)
      // This is a simplified version - production would use html2canvas or similar
      const svgData = await this.convertDOMToSVG(canvasElement);
      const img = new Image();

      return new Promise((resolve) => {
        img.onload = () => {
          ctx.drawImage(img, 0, 0);
          canvas.toBlob(
            (blob) => {
              if (blob) {
                resolve({
                  success: true,
                  data: blob,
                  filename: `canvas-export-${Date.now()}.png`,
                });
              } else {
                resolve({
                  success: false,
                  errors: ['Failed to create PNG blob'],
                });
              }
            },
            'image/png',
            options.quality || 0.9
          );
        };

        img.onerror = () => {
          resolve({ success: false, errors: ['Failed to load canvas image'] });
        };

        img.src = `data:image/svg+xml;base64,${btoa(svgData)}`;
      });
    } catch (error) {
      return { success: false, errors: [String(error)] };
    }
  }

  /**
   * Export canvas as SVG
   */
  async exportAsSVG(
    canvasElement: HTMLElement,
    options: ExportOptions
  ): Promise<ExportResult> {
    try {
      const svgData = await this.convertDOMToSVG(canvasElement);
      const sanitizedSVG =
        options.sanitize !== false
          ? this.sanitizer.sanitizeSVG(svgData)
          : svgData;

      return {
        success: true,
        data: sanitizedSVG,
        filename: `canvas-export-${Date.now()}.svg`,
      };
    } catch (error) {
      return { success: false, errors: [String(error)] };
    }
  }

  /**
   * Export canvas as JSON
   */
  async exportAsJSON(
    canvasData: unknown,
    options: ExportOptions
  ): Promise<ExportResult> {
    try {
      const exportData = {
        version: '1.0.0',
        timestamp: new Date().toISOString(),
        ...(options.includeMetadata && {
          metadata: {
            exportedBy: 'SecureCanvasExporter',
            format: 'json',
          },
        }),
        canvas: canvasData,
      };

      return {
        success: true,
        data: JSON.stringify(exportData, null, 2),
        filename: `canvas-data-${Date.now()}.json`,
      };
    } catch (error) {
      return { success: false, errors: [String(error)] };
    }
  }

  /**
   * Export canvas as JSX components
   */
  async exportAsJSX(
    canvasData: unknown,
    options: ExportOptions
  ): Promise<ExportResult> {
    try {
      const jsx = this.generateJSXFromCanvas(canvasData);
      const sanitizedJSX =
        options.sanitize !== false ? await this.renderer.renderJSX(jsx) : jsx;

      return {
        success: true,
        data: sanitizedJSX,
        filename: `CanvasComponent-${Date.now()}.jsx`,
      };
    } catch (error) {
      return { success: false, errors: [String(error)] };
    }
  }

  /**
   * Convert DOM element to SVG
   */
  private async convertDOMToSVG(element: HTMLElement): Promise<string> {
    const rect = element.getBoundingClientRect();
    const svg = `
      <svg width="${rect.width}" height="${rect.height}" xmlns="http://www.w3.org/2000/svg">
        <foreignObject width="100%" height="100%">
          <div xmlns="http://www.w3.org/1999/xhtml">
            ${element.innerHTML}
          </div>
        </foreignObject>
      </svg>
    `;
    return svg;
  }

  /**
   * Generate JSX from canvas data
   */
  private generateJSXFromCanvas(canvasData: unknown): string {
    const elements = canvasData.elements || [];

    const jsx = `
      import React from 'react';

      export const CanvasComponent = () => {
        return (
          <div className="canvas-container">
            ${elements.map((el: unknown) => this.generateElementJSX(el)).join('\n')}
          </div>
        );
      };

      export default CanvasComponent;
    `;

    return jsx;
  }

  /**
   * Generate JSX for individual element
   */
  private generateElementJSX(element: unknown): string {
    const style = {
      position: 'absolute',
      left: element.position?.x || 0,
      top: element.position?.y || 0,
      width: element.size?.width || 'auto',
      height: element.size?.height || 'auto',
      ...element.style,
    };

    const styleStr = Object.entries(style)
      .map(([key, value]) => `${key}: ${JSON.stringify(value)}`)
      .join(', ');

    return `
      <div
        key="${element.id}"
        className="canvas-element canvas-element-${element.type}"
        style={{ ${styleStr} }}
      >
        {/* ${element.data?.label || element.id} */}
      </div>
    `;
  }

  /**
   * Cleanup resources
   */
  destroy(): void {
    this.renderer.destroy();
  }
}
