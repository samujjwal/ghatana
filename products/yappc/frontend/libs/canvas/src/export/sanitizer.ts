import {
  createDefaultSanitizationConfig,
} from '../schemas/export-schemas';

import type {
  SanitizationConfig,
  SanitizeRequest,
  SanitizeResult} from '../schemas/export-schemas';

// We'll attempt to load the shared test DOMPurify mock only when running tests.
// In production or when the test helper isn't available, a local fallback is used.
/* eslint-disable @typescript-eslint/no-explicit-any */
/**
 *
 */
interface LocalPurify {
  sanitize: (content: string, options?: unknown) => string;
  addHook: (
    hookName: string,
    callback: (node: unknown, data?: unknown) => void
  ) => void;
}

/**
 * createFallbackPurify
 * A safe, local purify implementation used by default in production. Tests should
 * inject a deterministic mock via the ContentSanitizer constructor when needed.
 */
const createFallbackPurify = (): LocalPurify => {
  const hooks = new Map<string, Array<(node: unknown, data?: unknown) => void>>();
  return {
    addHook: (hookName: string, callback: (node: unknown, data?: unknown) => void) => {
      if (!hooks.has(hookName)) hooks.set(hookName, []);
      hooks.get(hookName)!.push(callback);
    },
    sanitize: (content: string, options: Record<string, unknown> = {}) => {
      let sanitized = content;
      sanitized = sanitized.replace(/<script[^>]*>.*?<\/script>/gis, '');
      sanitized = sanitized.replace(/on\w+\s*=\s*["'][^"']*["']/gi, '');
      sanitized = sanitized.replace(/javascript:/gi, '');
      sanitized = sanitized.replace(/vbscript:/gi, '');

      if (options.ALLOWED_TAGS && Array.isArray(options.ALLOWED_TAGS)) {
        const allowedTags = options.ALLOWED_TAGS.map((t: string) =>
          t.toLowerCase()
        );
        sanitized = sanitized.replace(
          /<(\/?)([\w-]+)[^>]*>/g,
          (match, slash, tagName) => {
            if (allowedTags.includes(tagName.toLowerCase())) return match;
            return '';
          }
        );
      }

      return sanitized;
    },
  };
};

/* eslint-enable @typescript-eslint/no-explicit-any */

// Content Security Policy utilities
/**
 *
 */
export interface CSPConfig {
  defaultSrc: string[];
  scriptSrc: string[];
  styleSrc: string[];
  imgSrc: string[];
  fontSrc: string[];
  connectSrc: string[];
  objectSrc: string[];
  mediaSrc: string[];
  frameSrc: string[];
}

export const createStrictCSP = (): CSPConfig => ({
  defaultSrc: ["'self'"],
  scriptSrc: ["'self'", "'unsafe-inline'"], // For SVG animations
  styleSrc: ["'self'", "'unsafe-inline'"],
  imgSrc: ["'self'", 'data:', 'blob:'],
  fontSrc: ["'self'", 'data:'],
  connectSrc: ["'self'"],
  objectSrc: ["'none'"],
  mediaSrc: ["'self'"],
  frameSrc: ["'none'"],
});

export const generateCSPHeader = (config: CSPConfig): string => {
  return Object.entries(config)
    .map(([directive, sources]) => {
      const kebabDirective = directive.replace(/([A-Z])/g, '-$1').toLowerCase();
      return `${kebabDirective} ${sources.join(' ')}`;
    })
    .join('; ');
};

// Advanced sanitization class
/**
 *
 */
export class ContentSanitizer {
  private config: SanitizationConfig;
  private purifyInstance: LocalPurify;

  /**
   * constructor
   * Accepts an optional purifyInstance for dependency injection. Tests should
   * pass a deterministic mock (for example, createTestDOMPurify()) while
   * production code will use the internal fallback purify implementation.
   */
  constructor(
    config?: Partial<SanitizationConfig>,
    purifyInstance?: LocalPurify
  ) {
    this.config = { ...createDefaultSanitizationConfig(), ...config };
    this.purifyInstance = purifyInstance || createFallbackPurify();
    this.configurePurify();
  }

  /**
   *
   */
  private configurePurify(): void {
    // Configure mock purify based on our schema
    this.purifyInstance.addHook('beforeSanitizeElements', (node: unknown) => {
      // Log removed elements for security audit
      if (
        node.nodeName &&
        this.config.disallowedTags.includes(node.nodeName.toLowerCase())
      ) {
        console.warn(`Removed disallowed tag: ${node.nodeName}`);
      }
    });

    this.purifyInstance.addHook('beforeSanitizeAttributes', (node: unknown) => {
      // Validate custom attributes
      if (node.hasAttribute && node.hasAttribute('data-user-id')) {
        const userId = node.getAttribute('data-user-id');
        if (!userId || !this.isValidUserId(userId)) {
          node.removeAttribute('data-user-id');
        }
      }
    });
  }

  /**
   *
   */
  private isValidUserId(userId: string): boolean {
    // Basic validation - in production, this would check against your user system
    return (
      /^[a-zA-Z0-9_-]+$/.test(userId) &&
      userId.length >= 3 &&
      userId.length <= 50
    );
  }

  /**
   *
   */
  public sanitizeHTML(content: string): SanitizeResult {
    const removed: Array<{
      type: 'tag' | 'attribute' | 'style';
      element: string;
      reason: string;
    }> = [];

    try {
      // Custom hook to track removed content
      let removedCount = 0;
      this.purifyInstance.addHook(
        'uponSanitizeElement',
        (node: unknown, data: unknown) => {
          if (data?.allowedTags && !data.allowedTags[data.tagName]) {
            removed.push({
              type: 'tag',
              element: data.tagName,
              reason: `Tag '${data.tagName}' is not in allowedTags list`,
            });
            removedCount++;
          }
        }
      );

      this.purifyInstance.addHook(
        'uponSanitizeAttribute',
        (node: unknown, data: unknown) => {
          if (data?.forceKeepAttr) return;

          const tagName = node?.tagName?.toLowerCase() || '*';
          const allowedForTag =
            (this.config.allowedAttributes[tagName] as string[]) || [];
          const allowedGlobal =
            (this.config.allowedAttributes['*'] as string[]) || [];
          const allowed = [...allowedForTag, ...allowedGlobal];

          if (data?.attrName && !allowed.includes(data.attrName)) {
            removed.push({
              type: 'attribute',
              element: `${tagName}[${data.attrName}]`,
              reason: `Attribute '${data.attrName}' not allowed for tag '${tagName}'`,
            });
            removedCount++;
          }
        }
      );

      const sanitized = this.purifyInstance.sanitize(content, {
        ALLOWED_TAGS: this.config.allowedTags,
        ALLOWED_ATTR: this.getAllowedAttributes(),
        FORBID_TAGS: this.config.disallowedTags,
        FORBID_ATTR: [],
        ALLOW_DATA_ATTR: true,
        ALLOW_ARIA_ATTR: true,
        RETURN_DOM: false,
        RETURN_DOM_FRAGMENT: false,
        SANITIZE_DOM: true,
        KEEP_CONTENT: !this.config.stripIgnoreTag,
      });

      return {
        sanitized: sanitized as string,
        removed,
        safe: removedCount === 0,
      };
    } catch (error) {
      console.error('Sanitization failed:', error);
      return {
        sanitized: '',
        removed: [
          { type: 'tag', element: 'all', reason: 'Sanitization failed' },
        ],
        safe: false,
      };
    }
  }

  /**
   *
   */
  public sanitizeSVG(content: string): SanitizeResult {
    // SVG-specific sanitization
    const svgConfig = {
      ...this.config,
      allowedTags: [
        'svg',
        'g',
        'path',
        'circle',
        'rect',
        'line',
        'polygon',
        'polyline',
        'ellipse',
        'text',
        'tspan',
        'defs',
        'marker',
        'pattern',
        'clipPath',
        'mask',
        'use',
        'symbol',
        'title',
        'desc',
        'metadata',
      ],
      allowedAttributes: {
        '*': ['id', 'class', 'style', 'transform'],
        svg: ['width', 'height', 'viewBox', 'xmlns', 'version'],
        path: ['d', 'fill', 'stroke', 'stroke-width', 'stroke-dasharray'],
        circle: ['cx', 'cy', 'r', 'fill', 'stroke', 'stroke-width'],
        rect: ['x', 'y', 'width', 'height', 'rx', 'ry', 'fill', 'stroke'],
        text: ['x', 'y', 'font-family', 'font-size', 'fill', 'text-anchor'],
        g: ['transform', 'opacity'],
      },
    };

    const tempSanitizer = new ContentSanitizer(svgConfig);
    return tempSanitizer.sanitizeHTML(content);
  }

  /**
   *
   */
  public sanitizeCSS(content: string): SanitizeResult {
    const removed: Array<{
      type: 'tag' | 'attribute' | 'style';
      element: string;
      reason: string;
    }> = [];
    let sanitized = content;

    // Remove dangerous CSS properties
    const dangerousProperties = [
      'expression',
      'behavior',
      'binding',
      'javascript:',
      'vbscript:',
      '@import',
      'url(javascript:',
      'url(data:',
      'url(vbscript:',
    ];

    dangerousProperties.forEach((dangerous) => {
      // Escape regex special chars in the pattern to avoid syntax errors when
      // the dangerous string contains parentheses or other meta-characters.
      const escaped = dangerous.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      const regex = new RegExp(escaped, 'gi');
      if (regex.test(sanitized)) {
        sanitized = sanitized.replace(regex, '');
        removed.push({
          type: 'style',
          element: dangerous,
          reason: `Dangerous CSS property '${dangerous}' removed`,
        });
      }
    });

    // Validate allowed styles
    const styleRegex = /([a-zA-Z-]+)\s*:\s*([^;]+);?/g;
    let match;
    while ((match = styleRegex.exec(content)) !== null) {
      const property = match[1].toLowerCase();
      if (!this.config.allowedStyles.includes(property)) {
        sanitized = sanitized.replace(match[0], '');
        removed.push({
          type: 'style',
          element: property,
          reason: `CSS property '${property}' not in allowedStyles list`,
        });
      }
    }

    return {
      sanitized,
      removed,
      safe: removed.length === 0,
    };
  }

  /**
   *
   */
  public processRequest(request: SanitizeRequest): SanitizeResult {
    const config = request.config || this.config;
    const tempSanitizer = new ContentSanitizer(config);

    switch (request.contentType) {
      case 'html':
        return tempSanitizer.sanitizeHTML(request.content);
      case 'svg':
        return tempSanitizer.sanitizeSVG(request.content);
      case 'css':
        return tempSanitizer.sanitizeCSS(request.content);
      default:
        return tempSanitizer.sanitizeHTML(request.content);
    }
  }

  /**
   *
   */
  private getAllowedAttributes(): string[] {
    const allAttributes = new Set<string>();
    Object.values(this.config.allowedAttributes).forEach((attrs) => {
      if (Array.isArray(attrs)) {
        (attrs as string[]).forEach((attr) => allAttributes.add(attr));
      }
    });
    return Array.from(allAttributes);
  }

  // Security audit methods
  /**
   *
   */
  public auditContent(content: string): {
    riskLevel: 'low' | 'medium' | 'high';
    findings: Array<{ type: string; message: string; line?: number }>;
  } {
    const findings: Array<{ type: string; message: string; line?: number }> =
      [];
    let riskLevel: 'low' | 'medium' | 'high' = 'low';

    // Check for suspicious patterns
    const suspiciousPatterns = [
      { pattern: /javascript:/gi, type: 'JavaScript URL', risk: 'high' },
      { pattern: /vbscript:/gi, type: 'VBScript URL', risk: 'high' },
      { pattern: /data:text\/html/gi, type: 'HTML data URL', risk: 'medium' },
      { pattern: /eval\s*\(/gi, type: 'eval() function', risk: 'high' },
      { pattern: /innerHTML/gi, type: 'innerHTML usage', risk: 'medium' },
      {
        pattern: /document\.write/gi,
        type: 'document.write usage',
        risk: 'high',
      },
      { pattern: /<script/gi, type: 'Script tag', risk: 'high' },
      {
        pattern: /on\w+\s*=/gi,
        type: 'Event handler attribute',
        risk: 'medium',
      },
    ];

    const lines = content.split('\n');
    suspiciousPatterns.forEach(({ pattern, type, risk }) => {
      lines.forEach((line, index) => {
        if (pattern.test(line)) {
          findings.push({
            type,
            message: `Potentially dangerous ${type.toLowerCase()} found`,
            line: index + 1,
          });
          if (risk === 'high') riskLevel = 'high';
          else if (risk === 'medium' && riskLevel !== 'high')
            riskLevel = 'medium';
        }
      });
    });

    return { riskLevel, findings };
  }
}

// Production-ready sanitizer instance
export const productionSanitizer = new ContentSanitizer({
  allowedTags: [
    // Basic HTML
    'div',
    'span',
    'p',
    'br',
    'hr',
    // Typography
    'h1',
    'h2',
    'h3',
    'h4',
    'h5',
    'h6',
    'strong',
    'em',
    'u',
    'i',
    'b',
    'small',
    'mark',
    // Lists
    'ul',
    'ol',
    'li',
    // Tables
    'table',
    'thead',
    'tbody',
    'tr',
    'td',
    'th',
    // SVG elements for diagrams
    'svg',
    'g',
    'path',
    'circle',
    'rect',
    'line',
    'text',
    'polygon',
    'polyline',
    'ellipse',
    'defs',
    'marker',
  ],
  disallowedTags: [
    'script',
    'style',
    'iframe',
    'object',
    'embed',
    'applet',
    'form',
    'input',
    'button',
    'textarea',
    'select',
    'option',
    'link',
    'meta',
    'base',
    'title',
    'head',
    'body',
    'html',
    'frame',
    'frameset',
    'noframes',
    'noscript',
  ],
  allowedStyles: [
    'color',
    'background-color',
    'font-size',
    'font-family',
    'font-weight',
    'text-align',
    'text-decoration',
    'line-height',
    'margin',
    'margin-top',
    'margin-right',
    'margin-bottom',
    'margin-left',
    'padding',
    'padding-top',
    'padding-right',
    'padding-bottom',
    'padding-left',
    'border',
    'border-radius',
    'border-width',
    'border-style',
    'border-color',
    'width',
    'height',
    'max-width',
    'max-height',
    'min-width',
    'min-height',
    'display',
    'position',
    'top',
    'right',
    'bottom',
    'left',
    'transform',
    'opacity',
    'z-index',
    'overflow',
    // SVG-specific styles
    'fill',
    'stroke',
    'stroke-width',
    'stroke-dasharray',
    'stroke-linecap',
  ],
});

// Helper functions for common use cases
export const sanitizeUserContent = (content: string): string => {
  const result = productionSanitizer.sanitizeHTML(content);
  if (!result.safe) {
    console.warn('User content required sanitization:', result.removed);
  }
  return result.sanitized;
};

export const sanitizeExportContent = (
  content: string,
  contentType: 'html' | 'svg' | 'css' = 'html'
): SanitizeResult => {
  return productionSanitizer.processRequest({
    content,
    contentType,
  });
};

export const auditExportSecurity = (content: string) => {
  return productionSanitizer.auditContent(content);
};
