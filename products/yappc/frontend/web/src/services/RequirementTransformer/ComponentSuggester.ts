/**
 * Component Suggester
 *
 * Suggests appropriate components based on requirements.
 *
 * @packageDocumentation
 */

import type { RequirementConfig } from '@yappc/config-schema';

/**
 * @doc.type service
 * @doc.purpose Suggest components based on requirements
 * @doc.layer product
 * @doc.pattern Service
 */
export class ComponentSuggester {
  private readonly componentMappings: Map<string, string[]> = new Map([
    ['input', ['TextField', 'Textarea', 'Select', 'Checkbox', 'RadioGroup']],
    ['display', ['Typography', 'Card', 'List', 'Table', 'Grid']],
    ['action', ['Button', 'IconButton', 'Link']],
    ['navigation', ['Breadcrumb', 'Tabs', 'Stepper']],
    ['layout', ['Container', 'Box', 'Stack', 'Grid']],
    ['data', ['Table', 'Chart', 'DataGrid', 'List']],
    ['form', ['Form', 'TextField', 'Select', 'Checkbox', 'RadioGroup', 'DatePicker']],
    ['feedback', ['Alert', 'Snackbar', 'Progress', 'Skeleton']],
    ['media', ['Image', 'Avatar', 'Icon']],
  ]);

  /**
   * Suggest components for a given requirement.
   *
   * @param requirement - Requirement to analyze
   * @returns Array of suggested component types
   */
  suggestComponents(requirement: RequirementConfig): string[] {
    const text = (requirement.title + ' ' + requirement.description).toLowerCase();
    const suggestions = new Set<string>();

    for (const [keyword, components] of this.componentMappings.entries()) {
      if (text.includes(keyword)) {
        components.forEach((comp) => suggestions.add(comp));
      }
    }

    // Type-specific suggestions
    if (requirement.type === 'user-interface') {
      suggestions.add('Button');
      suggestions.add('TextField');
      suggestions.add('Card');
    }

    if (requirement.type === 'api') {
      suggestions.add('Table');
      suggestions.add('List');
    }

    if (requirement.type === 'data') {
      suggestions.add('Table');
      suggestions.add('Chart');
      suggestions.add('DataGrid');
    }

    return Array.from(suggestions);
  }

  /**
   * Suggest components for multiple requirements.
   *
   * @param requirements - Array of requirements
   * @returns Map of requirement ID to suggested components
   */
  suggestForRequirements(requirements: RequirementConfig[]): Map<string, string[]> {
    const suggestions = new Map<string, string[]>();

    requirements.forEach((req) => {
      suggestions.set(req.id, this.suggestComponents(req));
    });

    return suggestions;
  }

  /**
   * Get component priority based on requirement type and priority.
   *
   * @param component - Component type
   * @param requirement - Requirement
   * @returns Priority score (0-1)
   */
  getComponentPriority(component: string, requirement: RequirementConfig): number {
    let score = 0.5;

    const suggestions = this.suggestComponents(requirement);
    if (suggestions.includes(component)) {
      score += 0.3;
    }

    if (requirement.priority === 'critical') {
      score += 0.1;
    }

    if (requirement.priority === 'high') {
      score += 0.05;
    }

    return Math.min(1, score);
  }

  /**
   * Get all available component types.
   *
   * @returns Array of component types
   */
  getAvailableComponents(): string[] {
    const allComponents = new Set<string>();
    for (const components of this.componentMappings.values()) {
      components.forEach((comp) => allComponents.add(comp));
    }
    return Array.from(allComponents);
  }
}
