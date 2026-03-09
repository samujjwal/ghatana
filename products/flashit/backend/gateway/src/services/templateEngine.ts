/**
 * Template Engine for Flashit
 * Moment templates and workflows
 *
 * @doc.type service
 * @doc.purpose Template management and rendering
 * @doc.layer product
 * @doc.pattern TemplateEngine
 */

// ============================================================================
// Types & Interfaces
// ============================================================================

export type TemplateCategory = 'journal' | 'gratitude' | 'reflection' | 'goal' | 'custom';
export type FieldType = 'text' | 'textarea' | 'number' | 'date' | 'time' | 'select' | 'multiselect' | 'checkbox' | 'rating' | 'emotion';

export interface TemplateField {
  id: string;
  type: FieldType;
  label: string;
  placeholder?: string;
  required: boolean;
  options?: string[];
  defaultValue?: unknown;
  validation?: FieldValidation;
}

export interface FieldValidation {
  min?: number;
  max?: number;
  minLength?: number;
  maxLength?: number;
  pattern?: string;
  message?: string;
}

export interface Template {
  id: string;
  name: string;
  description: string;
  category: TemplateCategory;
  icon?: string;
  color?: string;
  fields: TemplateField[];
  tags: string[];
  isPublic: boolean;
  createdBy: string;
  createdAt: Date;
  updatedAt: Date;
  usageCount: number;
}

export interface TemplateInstance {
  id: string;
  templateId: string;
  userId: string;
  values: Record<string, unknown>;
  createdAt: Date;
  momentId?: string;
}

export interface TemplateError {
  fieldId: string;
  message: string;
}

// ============================================================================
// Built-in Templates
// ============================================================================

const BUILTIN_TEMPLATES: Template[] = [
  {
    id: 'daily-journal',
    name: 'Daily Journal',
    description: 'Reflect on your day',
    category: 'journal',
    icon: '📔',
    color: '#3b82f6',
    fields: [
      {
        id: 'date',
        type: 'date',
        label: 'Date',
        required: true,
        defaultValue: new Date().toISOString().split('T')[0],
      },
      {
        id: 'mood',
        type: 'emotion',
        label: 'How are you feeling?',
        required: true,
      },
      {
        id: 'highlight',
        type: 'textarea',
        label: 'Highlight of the day',
        placeholder: 'What was the best part of your day?',
        required: false,
        validation: { maxLength: 500 },
      },
      {
        id: 'challenge',
        type: 'textarea',
        label: 'Challenge faced',
        placeholder: 'What challenged you today?',
        required: false,
        validation: { maxLength: 500 },
      },
      {
        id: 'learned',
        type: 'textarea',
        label: 'What did you learn?',
        placeholder: 'Any insights or learnings?',
        required: false,
        validation: { maxLength: 500 },
      },
      {
        id: 'grateful',
        type: 'textarea',
        label: 'What are you grateful for?',
        placeholder: 'List 3 things you\'re grateful for',
        required: false,
        validation: { maxLength: 500 },
      },
    ],
    tags: ['journal', 'daily', 'reflection'],
    isPublic: true,
    createdBy: 'system',
    createdAt: new Date('2024-01-01'),
    updatedAt: new Date('2024-01-01'),
    usageCount: 0,
  },
  {
    id: 'gratitude',
    name: 'Gratitude Log',
    description: 'Practice gratitude daily',
    category: 'gratitude',
    icon: '🙏',
    color: '#10b981',
    fields: [
      {
        id: 'date',
        type: 'date',
        label: 'Date',
        required: true,
        defaultValue: new Date().toISOString().split('T')[0],
      },
      {
        id: 'item1',
        type: 'text',
        label: 'I am grateful for...',
        required: true,
        validation: { maxLength: 200 },
      },
      {
        id: 'item2',
        type: 'text',
        label: 'I am grateful for...',
        required: true,
        validation: { maxLength: 200 },
      },
      {
        id: 'item3',
        type: 'text',
        label: 'I am grateful for...',
        required: true,
        validation: { maxLength: 200 },
      },
      {
        id: 'why',
        type: 'textarea',
        label: 'Why are these meaningful?',
        placeholder: 'Reflect on why you\'re grateful for these things',
        required: false,
        validation: { maxLength: 500 },
      },
    ],
    tags: ['gratitude', 'daily', 'positivity'],
    isPublic: true,
    createdBy: 'system',
    createdAt: new Date('2024-01-01'),
    updatedAt: new Date('2024-01-01'),
    usageCount: 0,
  },
  {
    id: 'goal-tracker',
    name: 'Goal Tracker',
    description: 'Track progress on your goals',
    category: 'goal',
    icon: '🎯',
    color: '#f59e0b',
    fields: [
      {
        id: 'goal',
        type: 'text',
        label: 'Goal',
        required: true,
        validation: { maxLength: 200 },
      },
      {
        id: 'progress',
        type: 'rating',
        label: 'Progress (1-10)',
        required: true,
        validation: { min: 1, max: 10 },
      },
      {
        id: 'actions',
        type: 'textarea',
        label: 'Actions taken',
        placeholder: 'What did you do to move closer to your goal?',
        required: false,
        validation: { maxLength: 500 },
      },
      {
        id: 'obstacles',
        type: 'textarea',
        label: 'Obstacles',
        placeholder: 'What\'s blocking your progress?',
        required: false,
        validation: { maxLength: 500 },
      },
      {
        id: 'next_steps',
        type: 'textarea',
        label: 'Next steps',
        placeholder: 'What will you do next?',
        required: false,
        validation: { maxLength: 500 },
      },
    ],
    tags: ['goal', 'progress', 'tracking'],
    isPublic: true,
    createdBy: 'system',
    createdAt: new Date('2024-01-01'),
    updatedAt: new Date('2024-01-01'),
    usageCount: 0,
  },
  {
    id: 'weekly-review',
    name: 'Weekly Review',
    description: 'Reflect on your week',
    category: 'reflection',
    icon: '📊',
    color: '#8b5cf6',
    fields: [
      {
        id: 'week',
        type: 'text',
        label: 'Week of',
        required: true,
      },
      {
        id: 'wins',
        type: 'textarea',
        label: 'Wins of the week',
        placeholder: 'What went well this week?',
        required: false,
        validation: { maxLength: 500 },
      },
      {
        id: 'challenges',
        type: 'textarea',
        label: 'Challenges',
        placeholder: 'What was difficult?',
        required: false,
        validation: { maxLength: 500 },
      },
      {
        id: 'lessons',
        type: 'textarea',
        label: 'Lessons learned',
        placeholder: 'What did you learn?',
        required: false,
        validation: { maxLength: 500 },
      },
      {
        id: 'next_week',
        type: 'textarea',
        label: 'Focus for next week',
        placeholder: 'What will you focus on?',
        required: false,
        validation: { maxLength: 500 },
      },
      {
        id: 'rating',
        type: 'rating',
        label: 'Overall week rating (1-10)',
        required: false,
        validation: { min: 1, max: 10 },
      },
    ],
    tags: ['reflection', 'weekly', 'review'],
    isPublic: true,
    createdBy: 'system',
    createdAt: new Date('2024-01-01'),
    updatedAt: new Date('2024-01-01'),
    usageCount: 0,
  },
];

// ============================================================================
// Template Engine
// ============================================================================

/**
 * TemplateEngine manages templates and instances
 */
class TemplateEngine {
  private templates: Map<string, Template> = new Map();

  constructor() {
    // Load built-in templates
    for (const template of BUILTIN_TEMPLATES) {
      this.templates.set(template.id, template);
    }
  }

  /**
   * Get all templates
   */
  getAllTemplates(): Template[] {
    return Array.from(this.templates.values());
  }

  /**
   * Get templates by category
   */
  getTemplatesByCategory(category: TemplateCategory): Template[] {
    return Array.from(this.templates.values()).filter(
      (t) => t.category === category
    );
  }

  /**
   * Get template by ID
   */
  getTemplate(id: string): Template | undefined {
    return this.templates.get(id);
  }

  /**
   * Create custom template
   */
  createTemplate(template: Omit<Template, 'id' | 'createdAt' | 'updatedAt' | 'usageCount'>): Template {
    const newTemplate: Template = {
      ...template,
      id: `custom-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      createdAt: new Date(),
      updatedAt: new Date(),
      usageCount: 0,
    };

    this.templates.set(newTemplate.id, newTemplate);
    return newTemplate;
  }

  /**
   * Update template
   */
  updateTemplate(id: string, updates: Partial<Template>): Template | null {
    const template = this.templates.get(id);
    if (!template) return null;

    const updated: Template = {
      ...template,
      ...updates,
      updatedAt: new Date(),
    };

    this.templates.set(id, updated);
    return updated;
  }

  /**
   * Delete template
   */
  deleteTemplate(id: string): boolean {
    // Don't allow deletion of built-in templates
    const template = this.templates.get(id);
    if (template?.createdBy === 'system') {
      return false;
    }

    return this.templates.delete(id);
  }

  /**
   * Validate template instance
   */
  validateInstance(templateId: string, values: Record<string, unknown>): TemplateError[] {
    const template = this.templates.get(templateId);
    if (!template) {
      return [{ fieldId: '', message: 'Template not found' }];
    }

    const errors: TemplateError[] = [];

    for (const field of template.fields) {
      const value = values[field.id];

      // Check required fields
      if (field.required && (value === undefined || value === null || value === '')) {
        errors.push({
          fieldId: field.id,
          message: `${field.label} is required`,
        });
        continue;
      }

      // Skip validation if no value
      if (value === undefined || value === null || value === '') {
        continue;
      }

      // Type-specific validation
      if (!this.validateFieldValue(field, value)) {
        errors.push({
          fieldId: field.id,
          message: field.validation?.message || `Invalid value for ${field.label}`,
        });
      }
    }

    return errors;
  }

  /**
   * Validate field value
   */
  private validateFieldValue(field: TemplateField, value: unknown): boolean {
    if (!field.validation) return true;

    switch (field.type) {
      case 'text':
      case 'textarea': {
        const str = String(value);
        if (field.validation.minLength && str.length < field.validation.minLength) {
          return false;
        }
        if (field.validation.maxLength && str.length > field.validation.maxLength) {
          return false;
        }
        if (field.validation.pattern) {
          const regex = new RegExp(field.validation.pattern);
          if (!regex.test(str)) return false;
        }
        break;
      }

      case 'number':
      case 'rating': {
        const num = Number(value);
        if (isNaN(num)) return false;
        if (field.validation.min !== undefined && num < field.validation.min) {
          return false;
        }
        if (field.validation.max !== undefined && num > field.validation.max) {
          return false;
        }
        break;
      }

      case 'select':
        if (field.options && !field.options.includes(String(value))) {
          return false;
        }
        break;

      case 'multiselect':
        if (field.options && Array.isArray(value)) {
          for (const item of value) {
            if (!field.options.includes(String(item))) {
              return false;
            }
          }
        }
        break;
    }

    return true;
  }

  /**
   * Render template with values
   */
  renderTemplate(templateId: string, values: Record<string, unknown>): string {
    const template = this.templates.get(templateId);
    if (!template) return '';

    const lines: string[] = [];
    lines.push(`# ${template.name}`);
    lines.push('');

    for (const field of template.fields) {
      const value = values[field.id];
      if (value === undefined || value === null || value === '') continue;

      lines.push(`## ${field.label}`);

      if (Array.isArray(value)) {
        for (const item of value) {
          lines.push(`- ${item}`);
        }
      } else {
        lines.push(String(value));
      }

      lines.push('');
    }

    return lines.join('\n');
  }

  /**
   * Increment usage count
   */
  incrementUsageCount(templateId: string): void {
    const template = this.templates.get(templateId);
    if (template) {
      template.usageCount++;
      this.templates.set(templateId, template);
    }
  }

  /**
   * Search templates
   */
  searchTemplates(query: string): Template[] {
    const lowercaseQuery = query.toLowerCase();
    return Array.from(this.templates.values()).filter(
      (template) =>
        template.name.toLowerCase().includes(lowercaseQuery) ||
        template.description.toLowerCase().includes(lowercaseQuery) ||
        template.tags.some((tag) => tag.toLowerCase().includes(lowercaseQuery))
    );
  }

  /**
   * Get popular templates
   */
  getPopularTemplates(limit: number = 5): Template[] {
    return Array.from(this.templates.values())
      .sort((a, b) => b.usageCount - a.usageCount)
      .slice(0, limit);
  }

  /**
   * Get recent templates
   */
  getRecentTemplates(limit: number = 5): Template[] {
    return Array.from(this.templates.values())
      .sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime())
      .slice(0, limit);
  }
}

// ============================================================================
// Singleton Instance
// ============================================================================

let templateEngineInstance: TemplateEngine | null = null;

/**
 * Get template engine instance
 */
export function getTemplateEngine(): TemplateEngine {
  if (!templateEngineInstance) {
    templateEngineInstance = new TemplateEngine();
  }
  return templateEngineInstance;
}

export default TemplateEngine;
