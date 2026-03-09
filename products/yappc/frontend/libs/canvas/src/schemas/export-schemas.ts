import { z } from 'zod';

// Export Format Schemas - Phase 7 Implementation
export const ExportFormatSchema = z.enum([
  'png',
  'svg',
  'pdf',
  'jsx',
  'json',
  'html',
]);

export const ImageExportOptionsSchema = z.object({
  format: z.enum(['png', 'svg']),
  width: z.number().positive().optional(),
  height: z.number().positive().optional(),
  quality: z.number().min(0.1).max(1).default(0.9),
  backgroundColor: z.string().default('#ffffff'),
  includeLabels: z.boolean().default(true),
  scale: z.number().positive().default(1),
  padding: z.number().nonnegative().default(10),
});

export const PdfExportOptionsSchema = z.object({
  format: z.literal('pdf'),
  pageSize: z.enum(['A4', 'A3', 'Letter', 'Legal']).default('A4'),
  orientation: z.enum(['portrait', 'landscape']).default('portrait'),
  includeMetadata: z.boolean().default(true),
  watermark: z.string().optional(),
  margins: z
    .object({
      top: z.number().nonnegative().default(20),
      right: z.number().nonnegative().default(20),
      bottom: z.number().nonnegative().default(20),
      left: z.number().nonnegative().default(20),
    })
    .default(() => ({}) as unknown),
});

export const CodeExportOptionsSchema = z.object({
  format: z.enum(['jsx', 'html']),
  includeStyles: z.boolean().default(true),
  minify: z.boolean().default(false),
  typescript: z.boolean().default(true),
  componentName: z.string().optional(),
  exportType: z.enum(['component', 'page', 'module']).default('component'),
  dependencies: z.array(z.string()).default([]),
});

export const JsonExportOptionsSchema = z.object({
  format: z.literal('json'),
  includeMetadata: z.boolean().default(true),
  includePositions: z.boolean().default(true),
  minify: z.boolean().default(false),
  version: z.string().default('1.0'),
});

export const ExportOptionsSchema = z.discriminatedUnion('format', [
  ImageExportOptionsSchema,
  PdfExportOptionsSchema,
  CodeExportOptionsSchema,
  JsonExportOptionsSchema,
]);

export const ExportRequestSchema = z.object({
  canvasId: z.string(),
  options: ExportOptionsSchema,
  async: z.boolean().default(false),
  callbackUrl: z.string().url().optional(),
});

export const ExportResultSchema = z.object({
  id: z.string(),
  status: z.enum(['pending', 'processing', 'completed', 'failed']),
  format: ExportFormatSchema,
  url: z.string().url().optional(),
  filename: z.string().optional(),
  size: z.number().nonnegative().optional(),
  createdAt: z.string().datetime(),
  completedAt: z.string().datetime().optional(),
  expiresAt: z.string().datetime().optional(),
  error: z.string().optional(),
  metadata: z.record(z.string(), z.unknown()).optional(),
});

// Sanitization schemas
export const SanitizationConfigSchema = z.object({
  allowedTags: z
    .array(z.string())
    .default([
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
      'i',
      'b',
      'br',
      'hr',
      'ul',
      'ol',
      'li',
      'table',
      'tr',
      'td',
      'th',
      'svg',
      'path',
      'circle',
      'rect',
      'line',
      'text',
    ]),
  allowedAttributes: z.record(z.string(), z.array(z.string())).default({
    '*': ['class', 'id', 'style'],
    svg: ['width', 'height', 'viewBox', 'xmlns'],
    path: ['d', 'fill', 'stroke', 'stroke-width'],
    circle: ['cx', 'cy', 'r', 'fill', 'stroke'],
    rect: ['x', 'y', 'width', 'height', 'fill', 'stroke'],
    text: ['x', 'y', 'font-family', 'font-size', 'fill'],
  }),
  allowedStyles: z
    .array(z.string())
    .default([
      'color',
      'background-color',
      'font-size',
      'font-family',
      'font-weight',
      'text-align',
      'margin',
      'padding',
      'border',
      'border-radius',
      'width',
      'height',
      'position',
      'top',
      'left',
      'right',
      'bottom',
      'transform',
      'opacity',
      'z-index',
    ]),
  disallowedTags: z
    .array(z.string())
    .default([
      'script',
      'style',
      'iframe',
      'object',
      'embed',
      'form',
      'input',
      'button',
      'link',
      'meta',
      'base',
    ]),
  stripIgnoreTag: z.boolean().default(true),
  stripIgnoreTagBody: z.array(z.string()).default(['script', 'style']),
});

export const SanitizeRequestSchema = z.object({
  content: z.string(),
  config: SanitizationConfigSchema.optional(),
  contentType: z.enum(['html', 'svg', 'css']).default('html'),
});

export const SanitizeResultSchema = z.object({
  sanitized: z.string(),
  removed: z
    .array(
      z.object({
        type: z.enum(['tag', 'attribute', 'style']),
        element: z.string(),
        reason: z.string(),
      })
    )
    .default([]),
  safe: z.boolean(),
});

// Share link schemas
export const ShareLinkConfigSchema = z.object({
  permissions: z.object({
    canView: z.boolean().default(true),
    canEdit: z.boolean().default(false),
    canComment: z.boolean().default(false),
    canExport: z.boolean().default(false),
  }),
  expiresAt: z.string().datetime().optional(),
  maxViews: z.number().positive().optional(),
  requireAuth: z.boolean().default(false),
  allowedDomains: z.array(z.string()).default([]),
});

export const CreateShareLinkRequestSchema = z.object({
  canvasId: z.string(),
  config: ShareLinkConfigSchema,
});

export const ShareLinkSchema = z.object({
  id: z.string(),
  token: z.string(),
  canvasId: z.string(),
  url: z.string().url(),
  config: ShareLinkConfigSchema,
  createdBy: z.string(),
  createdAt: z.string().datetime(),
  views: z.number().nonnegative().default(0),
  isActive: z.boolean().default(true),
});

// Security violation schema
export const SecurityViolationSchema = z.object({
  id: z.string(),
  type: z.enum(['high', 'medium', 'low']),
  category: z.enum(['content', 'script', 'style', 'url']),
  rule: z.string(),
  message: z.string(),
  element: z.string().optional(),
  context: z.string().optional(),
  suggestion: z.string().optional(),
  canAutoFix: z.boolean().default(false),
});

// Type inference
/**
 *
 */
export type ExportFormat = z.infer<typeof ExportFormatSchema>;
/**
 *
 */
export type ImageExportOptions = z.infer<typeof ImageExportOptionsSchema>;
/**
 *
 */
export type PdfExportOptions = z.infer<typeof PdfExportOptionsSchema>;
/**
 *
 */
export type CodeExportOptions = z.infer<typeof CodeExportOptionsSchema>;
/**
 *
 */
export type JsonExportOptions = z.infer<typeof JsonExportOptionsSchema>;
/**
 *
 */
export type ExportOptions = z.infer<typeof ExportOptionsSchema>;
/**
 *
 */
export type ExportRequest = z.infer<typeof ExportRequestSchema>;
/**
 *
 */
export type ExportResult = z.infer<typeof ExportResultSchema>;
/**
 *
 */
export type SanitizationConfig = z.infer<typeof SanitizationConfigSchema>;
/**
 *
 */
export type SanitizeRequest = z.infer<typeof SanitizeRequestSchema>;
/**
 *
 */
export type SanitizeResult = z.infer<typeof SanitizeResultSchema>;
/**
 *
 */
export type ShareLinkConfig = z.infer<typeof ShareLinkConfigSchema>;
/**
 *
 */
export type CreateShareLinkRequest = z.infer<
  typeof CreateShareLinkRequestSchema
>;
/**
 *
 */
export type ShareLink = z.infer<typeof ShareLinkSchema>;
/**
 *
 */
export type SecurityViolation = z.infer<typeof SecurityViolationSchema>;

// Export helpers
export const getFileExtension = (format: ExportFormat): string => {
  switch (format) {
    case 'png':
      return '.png';
    case 'svg':
      return '.svg';
    case 'pdf':
      return '.pdf';
    case 'jsx':
      return '.tsx';
    case 'html':
      return '.html';
    case 'json':
      return '.json';
    default:
      return '.txt';
  }
};

export const getMimeType = (format: ExportFormat): string => {
  switch (format) {
    case 'png':
      return 'image/png';
    case 'svg':
      return 'image/svg+xml';
    case 'pdf':
      return 'application/pdf';
    case 'jsx':
    case 'html':
      return 'text/plain';
    case 'json':
      return 'application/json';
    default:
      return 'text/plain';
  }
};

export const createDefaultSanitizationConfig = (): SanitizationConfig => ({
  allowedTags: [
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
    'i',
    'b',
    'br',
    'hr',
    'ul',
    'ol',
    'li',
    'table',
    'tr',
    'td',
    'th',
    'svg',
    'path',
    'circle',
    'rect',
    'line',
    'text',
  ],
  allowedAttributes: {
    '*': ['class', 'id', 'style'],
    svg: ['width', 'height', 'viewBox', 'xmlns'],
    path: ['d', 'fill', 'stroke', 'stroke-width'],
    circle: ['cx', 'cy', 'r', 'fill', 'stroke'],
    rect: ['x', 'y', 'width', 'height', 'fill', 'stroke'],
    text: ['x', 'y', 'font-family', 'font-size', 'fill'],
  },
  allowedStyles: [
    'color',
    'background-color',
    'font-size',
    'font-family',
    'font-weight',
    'text-align',
    'margin',
    'padding',
    'border',
    'border-radius',
    'width',
    'height',
    'position',
    'top',
    'left',
    'right',
    'bottom',
    'transform',
    'opacity',
    'z-index',
  ],
  disallowedTags: [
    'script',
    'style',
    'iframe',
    'object',
    'embed',
    'form',
    'input',
    'button',
    'link',
    'meta',
    'base',
  ],
  stripIgnoreTag: true,
  stripIgnoreTagBody: ['script', 'style'],
});
