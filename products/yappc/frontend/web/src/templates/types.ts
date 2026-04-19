/**
 * Template Types
 *
 * Type definitions for template components.
 *
 * @packageDocumentation
 */

export interface Template {
  id: string;
  name: string;
  description: string;
  category: string;
  config: import('@yappc/config-schema').PageConfig;
  tags: string[];
  previewImage?: string;
  metadata?: {
    version: string;
    author?: string;
    createdAt?: string;
    updatedAt?: string;
  };
}

export interface TemplateLibraryProps {
  onSelectTemplate?: (template: import('@yappc/config-schema').PageConfig) => void;
  onUseTemplate?: (template: import('@yappc/config-schema').PageConfig) => void;
}

export interface TemplateEditorProps {
  template: import('@yappc/config-schema').PageConfig;
  onSave?: (template: import('@yappc/config-schema').PageConfig) => void;
  onCancel?: () => void;
  readOnly?: boolean;
}
