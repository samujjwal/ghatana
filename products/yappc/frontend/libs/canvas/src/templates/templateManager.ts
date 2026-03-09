/**
 * Template Management System (Feature 2.12)
 *
 * Extends Feature 1.4's basic template CRUD with:
 * - Gallery UI support (categories, grid layout)
 * - Parameter prompts for template variables
 * - Version update notifications
 * - Template usage analytics
 *
 * Part of Feature 2.12: Template System
 */

import type { DocumentTemplate } from '../history/historyManager';

// Re-export Feature 1.4 functions for convenience
export { createTemplate, updateTemplate, filterTemplates } from '../history/historyManager';

/**
 * Template parameter definition for user prompts
 */
export interface TemplateParameter {
  id: string;
  name: string;
  description: string;
  type: 'string' | 'number' | 'boolean' | 'color' | 'select';
  defaultValue?: string | number | boolean;
  required?: boolean;
  options?: string[]; // For select type
  validation?: {
    min?: number;
    max?: number;
    pattern?: string;
    message?: string;
  };
}

/**
 * Template with parameter support
 */
export interface ParameterizedTemplate<T = unknown> extends DocumentTemplate<T> {
  parameters?: TemplateParameter[];
  parameterValues?: Record<string, string | number | boolean>;
}

/**
 * Template gallery category
 */
export interface TemplateCategory {
  id: string;
  name: string;
  description: string;
  icon?: string;
  order: number;
  subcategories?: TemplateCategory[];
}

/**
 * Template with gallery metadata
 */
export interface GalleryTemplate<T = unknown> extends ParameterizedTemplate<T> {
  featured?: boolean;
  usageCount?: number;
  rating?: number;
  lastUsed?: number;
  downloadUrl?: string;
  thumbnailUrl?: string;
}

/**
 * Template version update notification
 */
export interface TemplateUpdate {
  templateId: string;
  templateName: string;
  currentVersion: string;
  latestVersion: string;
  changelog: string[];
  breaking: boolean;
  updateUrl?: string;
}

/**
 * Template gallery state
 */
export interface TemplateGalleryState<T = unknown> {
  templates: Map<string, GalleryTemplate<T>>;
  categories: Map<string, TemplateCategory>;
  featured: string[]; // Template IDs
  recentlyUsed: string[]; // Template IDs
  updates: Map<string, TemplateUpdate>;
}

/**
 * Create initial gallery state
 */
export function createGalleryState<T>(): TemplateGalleryState<T> {
  return {
    templates: new Map(),
    categories: new Map(),
    featured: [],
    recentlyUsed: [],
    updates: new Map(),
  };
}

/**
 * Add template to gallery
 */
export function addToGallery<T>(
  state: TemplateGalleryState<T>,
  template: GalleryTemplate<T>
): TemplateGalleryState<T> {
  const templates = new Map(state.templates);
  templates.set(template.id, {
    usageCount: 0,
    rating: 0,
    ...template,
  });

  return {
    ...state,
    templates,
  };
}

/**
 * Remove template from gallery
 */
export function removeFromGallery<T>(
  state: TemplateGalleryState<T>,
  templateId: string
): TemplateGalleryState<T> {
  const templates = new Map(state.templates);
  templates.delete(templateId);

  return {
    ...state,
    templates,
    featured: state.featured.filter((id) => id !== templateId),
    recentlyUsed: state.recentlyUsed.filter((id) => id !== templateId),
  };
}

/**
 * Add or update category
 */
export function setCategory(
  state: TemplateGalleryState,
  category: TemplateCategory
): TemplateGalleryState {
  const categories = new Map(state.categories);
  categories.set(category.id, category);

  return {
    ...state,
    categories,
  };
}

/**
 * Get templates by category
 */
export function getTemplatesByCategory<T>(
  state: TemplateGalleryState<T>,
  categoryId: string,
  includeSubcategories = true
): GalleryTemplate<T>[] {
  const category = state.categories.get(categoryId);
  if (!category) return [];

  const categoryIds = new Set([categoryId]);

  if (includeSubcategories && category.subcategories) {
    const collectIds = (cat: TemplateCategory) => {
      categoryIds.add(cat.id);
      cat.subcategories?.forEach(collectIds);
    };
    category.subcategories.forEach(collectIds);
  }

  return Array.from(state.templates.values()).filter(
    (template) => template.category && categoryIds.has(template.category)
  );
}

/**
 * Get featured templates
 */
export function getFeaturedTemplates<T>(
  state: TemplateGalleryState<T>
): GalleryTemplate<T>[] {
  return state.featured
    .map((id) => state.templates.get(id))
    .filter((t): t is GalleryTemplate<T> => t !== undefined);
}

/**
 * Mark template as featured
 */
export function setFeatured<T>(
  state: TemplateGalleryState<T>,
  templateId: string,
  featured: boolean
): TemplateGalleryState<T> {
  const template = state.templates.get(templateId);
  if (!template) return state;

  const templates = new Map(state.templates);
  templates.set(templateId, { ...template, featured });

  const featuredList = featured
    ? [...state.featured, templateId]
    : state.featured.filter((id) => id !== templateId);

  return {
    ...state,
    templates,
    featured: featuredList,
  };
}

/**
 * Record template usage
 */
export function recordUsage<T>(
  state: TemplateGalleryState<T>,
  templateId: string
): TemplateGalleryState<T> {
  const template = state.templates.get(templateId);
  if (!template) return state;

  const templates = new Map(state.templates);
  templates.set(templateId, {
    ...template,
    usageCount: (template.usageCount ?? 0) + 1,
    lastUsed: Date.now(),
  });

  // Update recently used list (max 10)
  const recentlyUsed = [
    templateId,
    ...state.recentlyUsed.filter((id) => id !== templateId),
  ].slice(0, 10);

  return {
    ...state,
    templates,
    recentlyUsed,
  };
}

/**
 * Get recently used templates
 */
export function getRecentlyUsed<T>(
  state: TemplateGalleryState<T>
): GalleryTemplate<T>[] {
  return state.recentlyUsed
    .map((id) => state.templates.get(id))
    .filter((t): t is GalleryTemplate<T> => t !== undefined);
}

/**
 * Get popular templates (by usage count)
 */
export function getPopularTemplates<T>(
  state: TemplateGalleryState<T>,
  limit = 10
): GalleryTemplate<T>[] {
  return Array.from(state.templates.values())
    .sort((a, b) => (b.usageCount ?? 0) - (a.usageCount ?? 0))
    .slice(0, limit);
}

/**
 * Get top-rated templates
 */
export function getTopRatedTemplates<T>(
  state: TemplateGalleryState<T>,
  limit = 10
): GalleryTemplate<T>[] {
  return Array.from(state.templates.values())
    .filter((t) => t.rating && t.rating > 0)
    .sort((a, b) => (b.rating ?? 0) - (a.rating ?? 0))
    .slice(0, limit);
}

/**
 * Rate a template
 */
export function rateTemplate<T>(
  state: TemplateGalleryState<T>,
  templateId: string,
  rating: number
): TemplateGalleryState<T> {
  const template = state.templates.get(templateId);
  if (!template) return state;

  const templates = new Map(state.templates);
  templates.set(templateId, {
    ...template,
    rating: Math.max(0, Math.min(5, rating)), // Clamp to 0-5
  });

  return {
    ...state,
    templates,
  };
}

/**
 * Apply parameters to template
 */
export function applyParameters<T>(
  template: ParameterizedTemplate<T>,
  values: Record<string, string | number | boolean>
): ParameterizedTemplate<T> {
  // Validate parameters
  const errors: string[] = [];

  if (template.parameters) {
    for (const param of template.parameters) {
      const value = values[param.id];

      // Check required
      if (param.required && (value === undefined || value === '')) {
        errors.push(`Parameter "${param.name}" is required`);
        continue;
      }

      // Type validation
      if (value !== undefined) {
        if (param.type === 'number' && typeof value !== 'number') {
          errors.push(`Parameter "${param.name}" must be a number`);
        } else if (param.type === 'boolean' && typeof value !== 'boolean') {
          errors.push(`Parameter "${param.name}" must be a boolean`);
        } else if (param.type === 'string' && typeof value !== 'string') {
          errors.push(`Parameter "${param.name}" must be a string`);
        }

        // Range validation
        if (param.type === 'number' && typeof value === 'number') {
          if (param.validation?.min !== undefined && value < param.validation.min) {
            errors.push(
              param.validation.message ||
                `Parameter "${param.name}" must be at least ${param.validation.min}`
            );
          }
          if (param.validation?.max !== undefined && value > param.validation.max) {
            errors.push(
              param.validation.message ||
                `Parameter "${param.name}" must be at most ${param.validation.max}`
            );
          }
        }

        // Pattern validation
        if (
          param.type === 'string' &&
          typeof value === 'string' &&
          param.validation?.pattern
        ) {
          const regex = new RegExp(param.validation.pattern);
          if (!regex.test(value)) {
            errors.push(
              param.validation.message || `Parameter "${param.name}" has invalid format`
            );
          }
        }

        // Select options validation
        if (param.type === 'select' && param.options && !param.options.includes(String(value))) {
          errors.push(
            `Parameter "${param.name}" must be one of: ${param.options.join(', ')}`
          );
        }
      }
    }
  }

  if (errors.length > 0) {
    throw new Error(`Parameter validation failed:\n${errors.join('\n')}`);
  }

  return {
    ...template,
    parameterValues: values,
  };
}

/**
 * Get parameter defaults
 */
export function getParameterDefaults(
  template: ParameterizedTemplate
): Record<string, string | number | boolean> {
  if (!template.parameters) return {};

  return template.parameters.reduce(
    (defaults, param) => {
      if (param.defaultValue !== undefined) {
        defaults[param.id] = param.defaultValue;
      }
      return defaults;
    },
    {} as Record<string, string | number | boolean>
  );
}

/**
 * Check for template updates
 */
export function checkForUpdates<T>(
  state: TemplateGalleryState<T>,
  remoteTemplates: GalleryTemplate<T>[]
): TemplateGalleryState<T> {
  const updates = new Map(state.updates);

  for (const remote of remoteTemplates) {
    const local = state.templates.get(remote.id);
    if (!local) continue;

    // Compare versions (assumes semantic versioning in tags)
    const localVersion = local.tags?.find((t) => t.startsWith('v'))?.slice(1) || '0.0.0';
    const remoteVersion = remote.tags?.find((t) => t.startsWith('v'))?.slice(1) || '0.0.0';

    if (remoteVersion > localVersion) {
      updates.set(remote.id, {
        templateId: remote.id,
        templateName: remote.name,
        currentVersion: localVersion,
        latestVersion: remoteVersion,
        changelog: remote.description ? [remote.description] : [],
        breaking: remoteVersion.split('.')[0] > localVersion.split('.')[0],
        updateUrl: remote.downloadUrl,
      });
    }
  }

  return {
    ...state,
    updates,
  };
}

/**
 * Get available updates
 */
export function getAvailableUpdates(
  state: TemplateGalleryState
): TemplateUpdate[] {
  return Array.from(state.updates.values());
}

/**
 * Clear update notification
 */
export function clearUpdate<T>(
  state: TemplateGalleryState<T>,
  templateId: string
): TemplateGalleryState<T> {
  const updates = new Map(state.updates);
  updates.delete(templateId);

  return {
    ...state,
    updates,
  };
}

/**
 * Search templates with advanced filters
 */
export function searchTemplates<T>(
  state: TemplateGalleryState<T>,
  query: {
    text?: string;
    category?: string;
    tags?: string[];
    author?: string;
    featured?: boolean;
    minRating?: number;
    sortBy?: 'name' | 'usage' | 'rating' | 'recent' | 'updated';
    sortOrder?: 'asc' | 'desc';
  }
): GalleryTemplate<T>[] {
  let results = Array.from(state.templates.values());

  // Text search
  if (query.text) {
    const text = query.text.toLowerCase();
    results = results.filter(
      (t) =>
        t.name.toLowerCase().includes(text) ||
        t.description?.toLowerCase().includes(text) ||
        t.tags?.some((tag) => tag.toLowerCase().includes(text))
    );
  }

  // Category filter
  if (query.category) {
    results = results.filter((t) => t.category === query.category);
  }

  // Tags filter (AND logic)
  if (query.tags && query.tags.length > 0) {
    results = results.filter((t) => query.tags!.every((tag) => t.tags?.includes(tag)));
  }

  // Author filter
  if (query.author) {
    results = results.filter((t) => t.author === query.author);
  }

  // Featured filter
  if (query.featured) {
    results = results.filter((t) => t.featured);
  }

  // Rating filter
  if (query.minRating !== undefined) {
    results = results.filter((t) => (t.rating ?? 0) >= query.minRating!);
  }

  // Sort
  const sortOrder = query.sortOrder === 'desc' ? -1 : 1;
  switch (query.sortBy) {
    case 'name':
      results.sort((a, b) => sortOrder * a.name.localeCompare(b.name));
      break;
    case 'usage':
      results.sort((a, b) => sortOrder * ((a.usageCount ?? 0) - (b.usageCount ?? 0)));
      break;
    case 'rating':
      results.sort((a, b) => sortOrder * ((a.rating ?? 0) - (b.rating ?? 0)));
      break;
    case 'recent':
      results.sort((a, b) => sortOrder * ((a.lastUsed ?? 0) - (b.lastUsed ?? 0)));
      break;
    case 'updated':
      results.sort((a, b) => sortOrder * (a.updatedAt - b.updatedAt));
      break;
  }

  return results;
}

/**
 * Export gallery state to JSON
 */
export function exportGallery<T>(state: TemplateGalleryState<T>): string {
  return JSON.stringify(
    {
      templates: Array.from(state.templates.entries()),
      categories: Array.from(state.categories.entries()),
      featured: state.featured,
      recentlyUsed: state.recentlyUsed,
      updates: Array.from(state.updates.entries()),
    },
    null,
    2
  );
}

/**
 * Import gallery state from JSON
 */
export function importGallery<T>(json: string): TemplateGalleryState<T> {
  const data = JSON.parse(json);
  return {
    templates: new Map(data.templates),
    categories: new Map(data.categories),
    featured: data.featured || [],
    recentlyUsed: data.recentlyUsed || [],
    updates: new Map(data.updates || []),
  };
}

/**
 * Get gallery statistics
 */
export function getGalleryStats<T>(state: TemplateGalleryState<T>) {
  const templates = Array.from(state.templates.values());
  return {
    totalTemplates: templates.length,
    totalCategories: state.categories.size,
    featuredCount: state.featured.length,
    totalUsage: templates.reduce((sum, t) => sum + (t.usageCount ?? 0), 0),
    averageRating:
      templates.filter((t) => t.rating).reduce((sum, t) => sum + (t.rating ?? 0), 0) /
        templates.filter((t) => t.rating).length || 0,
    availableUpdates: state.updates.size,
    categoryCounts: Array.from(state.categories.values()).map((cat) => ({
      id: cat.id,
      name: cat.name,
      count: templates.filter((t) => t.category === cat.id).length,
    })),
  };
}
