/**
 * Template Types
 *
 * Type definitions for template components.
 *
 * @packageDocumentation
 */

import type { PageConfig } from 'yappc-config-schema';

export interface Template {
  id: string;
  name: string;
  description: string;
  category: string;
  config: PageConfig;
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
  onSelectTemplate?: (template: PageConfig) => void;
  onUseTemplate?: (template: PageConfig) => void;
}

export interface TemplateEditorProps {
  template: PageConfig;
  onSave?: (template: PageConfig) => void;
  onCancel?: () => void;
  readOnly?: boolean;
}
