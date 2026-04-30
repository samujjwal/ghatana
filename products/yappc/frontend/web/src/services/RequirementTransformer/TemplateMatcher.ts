/**
 * Template Matcher
 *
 * Matches requirements to pre-built page templates.
 *
 * @packageDocumentation
 */

import type { PageConfig } from 'yappc-config-schema';
import { dashboardTemplate } from './templates/dashboard';
import { formTemplate } from './templates/form';
import { tableTemplate } from './templates/table';

/**
 * @doc.type service
 * @doc.purpose Match requirements to page templates
 * @doc.layer product
 * @doc.pattern Service
 */
export class TemplateMatcher {
  private readonly templates: Map<string, PageConfig> = new Map([
    ['dashboard', dashboardTemplate],
    ['form', formTemplate],
    ['table', tableTemplate],
  ]);

  /**
   * Match requirements to the best template.
   *
   * @param requirements - Array of requirements
   * @returns Best matching template or null
   */
  match(requirements: string[]): { template: PageConfig; matchScore: number } | null {
    let bestMatch: { template: PageConfig; matchScore: number } | null = null;
    let highestScore = 0;

    for (const [name, template] of this.templates.entries()) {
      const score = this.calculateMatchScore(requirements, name);
      if (score > highestScore && score > 0.3) {
        highestScore = score;
        bestMatch = { template, matchScore: score };
      }
    }

    return bestMatch;
  }

  /**
   * Calculate match score between requirements and template.
   */
  private calculateMatchScore(requirements: string[], templateName: string): number {
    const combinedRequirements = requirements.join(' ').toLowerCase();
    let score = 0;

    // Template-specific keywords
    const templateKeywords: Record<string, string[]> = {
      dashboard: ['dashboard', 'chart', 'metric', 'statistic', 'overview', 'summary', 'analytics'],
      form: ['form', 'input', 'submit', 'field', 'validation', 'entry', 'create', 'edit'],
      table: ['table', 'list', 'grid', 'data', 'rows', 'columns', 'items', 'records'],
    };

    const keywords = templateKeywords[templateName] || [];

    for (const keyword of keywords) {
      if (combinedRequirements.includes(keyword)) {
        score += 0.2;
      }
    }

    // Bonus for exact matches
    if (combinedRequirements.includes(templateName)) {
      score += 0.3;
    }

    return Math.min(1, score);
  }

  /**
   * Get all available templates.
   *
   * @returns Array of template names
   */
  getAvailableTemplates(): string[] {
    return Array.from(this.templates.keys());
  }

  /**
   * Get a specific template by name.
   *
   * @param name - Template name
   * @returns Template or null
   */
  getTemplate(name: string): PageConfig | null {
    return this.templates.get(name) || null;
  }
}
