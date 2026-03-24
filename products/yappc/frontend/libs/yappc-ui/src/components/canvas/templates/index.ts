/**
 * Canvas Template System
 *
 * Pre-built templates and template management.
 *
 * @module canvas/templates
 */

// Template definitions
export * from './TemplateDefinition';

// Predefined templates
export * from './predefinedTemplates';

// Template manager
export { TemplateManager } from './TemplateManager';
export type { TemplateManagerProps } from './TemplateManager';

// Template preview
export { TemplatePreview } from './TemplatePreview';
export type { TemplatePreviewProps } from './TemplatePreview';
