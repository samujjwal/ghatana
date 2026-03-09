/**
 * Template Service - Manage canvas templates
 */

import { logger } from '../../utils/Logger';
import type { Template } from './types';
import type { CanvasState } from '../../components/canvas/workspace/canvasAtoms';

/**
 *
 */
class TemplateServiceClass {
  private templates: Map<string, Template> = new Map();
  private storageKey = 'yappc:canvas:templates';

  /**
   *
   */
  constructor() {
    this.loadFromStorage();
  }

  /**
   * Save a canvas as a template
   */
  save(
    name: string,
    description: string,
    canvasState: CanvasState,
    category: string = 'Custom',
    tags: string[] = [],
  ): Template {
    const template: Template = {
      id: `template-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
      name,
      description,
      category,
      tags,
      canvasState: JSON.parse(JSON.stringify(canvasState)), // Deep clone
      metadata: {
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
    };

    this.templates.set(template.id, template);
    this.saveToStorage();

    return template;
  }

  /**
   * Load a template
   */
  load(id: string): Template | null {
    return this.templates.get(id) || null;
  }

  /**
   * List all templates
   */
  list(category?: string): Template[] {
    const templates = Array.from(this.templates.values());

    if (category) {
      return templates.filter((t) => t.category === category);
    }

    return templates;
  }

  /**
   * Search templates
   */
  search(query: string): Template[] {
    const lowerQuery = query.toLowerCase();

    return Array.from(this.templates.values()).filter(
      (template) =>
        template.name.toLowerCase().includes(lowerQuery) ||
        template.description.toLowerCase().includes(lowerQuery) ||
        template.tags.some((tag) => tag.toLowerCase().includes(lowerQuery)),
    );
  }

  /**
   * Update template
   */
  update(id: string, updates: Partial<Omit<Template, 'id' | 'metadata'>>): boolean {
    const template = this.templates.get(id);

    if (!template) {
      return false;
    }

    const updated: Template = {
      ...template,
      ...updates,
      metadata: {
        ...template.metadata,
        updatedAt: new Date().toISOString(),
      },
    };

    this.templates.set(id, updated);
    this.saveToStorage();

    return true;
  }

  /**
   * Delete template
   */
  delete(id: string): boolean {
    const deleted = this.templates.delete(id);
    if (deleted) {
      this.saveToStorage();
    }
    return deleted;
  }

  /**
   * List categories
   */
  listCategories(): string[] {
    const categories = new Set<string>();
    this.templates.forEach((template) => categories.add(template.category));
    return Array.from(categories).sort();
  }

  /**
   * Export template to JSON
   */
  exportTemplate(id: string): string | null {
    const template = this.templates.get(id);
    if (!template) return null;

    return JSON.stringify(template, null, 2);
  }

  /**
   * Import template from JSON
   */
  importTemplate(jsonString: string): { success: boolean; template?: Template; error?: string } {
    try {
      const template = JSON.parse(jsonString) as Template;

      // Validate structure
      if (!template.name || !template.canvasState) {
        return {
          success: false,
          error: 'Invalid template format',
        };
      }

      // Generate new ID to avoid conflicts
      template.id = `template-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
      template.metadata.updatedAt = new Date().toISOString();

      this.templates.set(template.id, template);
      this.saveToStorage();

      return {
        success: true,
        template,
      };
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Import failed',
      };
    }
  }

  /**
   * Clear all templates
   */
  clear(): void {
    this.templates.clear();
    this.saveToStorage();
  }

  /**
   *
   */
  private loadFromStorage(): void {
    if (typeof window === 'undefined') return;

    try {
      const stored = localStorage.getItem(this.storageKey);
      if (stored) {
        const templates = JSON.parse(stored) as Template[];
        templates.forEach((template) => {
          this.templates.set(template.id, template);
        });
      }
    } catch (error) {
      logger.error('Failed to load templates from storage', 'template-service', {
        error: error instanceof Error ? error.message : String(error)
      });
    }
  }

  /**
   *
   */
  private saveToStorage(): void {
    if (typeof window === 'undefined') return;

    try {
      const templates = Array.from(this.templates.values());
      localStorage.setItem(this.storageKey, JSON.stringify(templates));
    } catch (error) {
      logger.error('Failed to save templates to storage', 'template-service', {
        error: error instanceof Error ? error.message : String(error)
      });
    }
  }
}

// Singleton instance
export const TemplateService = new TemplateServiceClass();
