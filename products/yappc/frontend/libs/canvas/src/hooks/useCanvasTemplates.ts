/**
 * Consolidated Canvas Templates Hook
 * 
 * Replaces: useTemplateActions + template functionality
 * Provides: Template management
 */

import { useCallback, useState } from 'react';

export type TemplateCategory = 'workflow' | 'architecture' | 'ui' | 'data' | 'security';

export interface Template {
  id: string;
  name: string;
  description: string;
  category: TemplateCategory;
  nodes: unknown[];
  edges: unknown[];
  thumbnail?: string;
  tags: string[];
  createdAt: Date;
  updatedAt: Date;
}

export interface UseCanvasTemplatesOptions {
  canvasId: string;
  category?: TemplateCategory;
}

export interface UseCanvasTemplatesReturn {
  templates: Template[];
  applyTemplate: (templateId: string) => Promise<void>;
  saveAsTemplate: (name: string, description: string, category: TemplateCategory) => Promise<Template>;
  deleteTemplate: (templateId: string) => Promise<void>;
  updateTemplate: (templateId: string, updates: Partial<Template>) => Promise<void>;
  isLoading: boolean;
  error: Error | null;
}

export function useCanvasTemplates(
  options: UseCanvasTemplatesOptions
): UseCanvasTemplatesReturn {
  const { canvasId, category } = options;

  const [templates, setTemplates] = useState<Template[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const applyTemplate = useCallback(async (templateId: string): Promise<void> => {
    setIsLoading(true);
    try {
      const template = templates.find(t => t.id === templateId);
      if (!template) throw new Error('Template not found');
      // Apply template nodes/edges to canvas
    } catch (err) {
      setError(err as Error);
      throw err;
    } finally {
      setIsLoading(false);
    }
  }, [templates]);

  const saveAsTemplate = useCallback(
    async (name: string, description: string, cat: TemplateCategory): Promise<Template> => {
      const template: Template = {
        id: `template-${Date.now()}`,
        name,
        description,
        category: cat,
        nodes: [],
        edges: [],
        tags: [],
        createdAt: new Date(),
        updatedAt: new Date(),
      };
      setTemplates(prev => [...prev, template]);
      return template;
    },
    []
  );

  const deleteTemplate = useCallback(async (templateId: string): Promise<void> => {
    setTemplates(prev => prev.filter(t => t.id !== templateId));
  }, []);

  const updateTemplate = useCallback(
    async (templateId: string, updates: Partial<Template>): Promise<void> => {
      setTemplates(prev =>
        prev.map(t => (t.id === templateId ? { ...t, ...updates, updatedAt: new Date() } : t))
      );
    },
    []
  );

  return {
    templates,
    applyTemplate,
    saveAsTemplate,
    deleteTemplate,
    updateTemplate,
    isLoading,
    error,
  };
}
