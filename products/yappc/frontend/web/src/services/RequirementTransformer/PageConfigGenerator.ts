/**
 * Page Config Generator
 *
 * Generates PageConfig from requirements and templates.
 *
 * @packageDocumentation
 */

import type { PageConfig, RequirementConfig } from '@yappc/config-schema';

import { TemplateMatcher } from './TemplateMatcher';

/**
 * @doc.type service
 * @doc.purpose Generate PageConfig from requirements and templates
 * @doc.layer product
 * @doc.pattern Service
 */
export class PageConfigGenerator {
  private readonly templateMatcher: TemplateMatcher;

  constructor() {
    this.templateMatcher = new TemplateMatcher();
  }

  /**
   * Generate a PageConfig from requirements.
   *
   * @param requirements - Array of requirements
   * @param pageTitle - Optional page title
   * @returns Generated PageConfig
   */
  generateFromRequirements(requirements: RequirementConfig[], pageTitle?: string): PageConfig {
    const requirementTexts = requirements.map((r) => r.title + ' ' + r.description);
    const templateMatch = this.templateMatcher.match(requirementTexts);

    if (templateMatch) {
      return this.customizeTemplate(templateMatch.template, requirements, pageTitle);
    }

    return this.generateFromScratch(requirements, pageTitle);
  }

  /**
   * Customize a template based on requirements.
   */
  private customizeTemplate(
    template: PageConfig,
    requirements: RequirementConfig[],
    pageTitle?: string
  ): PageConfig {
    const customized = JSON.parse(JSON.stringify(template)) as PageConfig;

    if (pageTitle) {
      customized.title = pageTitle;
    }

    // Add requirement metadata
    customized.metadata = {
      ...customized.metadata,
      generatedFrom: requirements.map((r) => r.id),
      requirementsCount: requirements.length,
    };

    return customized;
  }

  /**
   * Generate a PageConfig from scratch when no template matches.
   */
  private generateFromScratch(requirements: RequirementConfig[], pageTitle?: string): PageConfig {
    const id = `page-${Date.now()}`;
    const title = pageTitle || 'Generated Page';
    const route = `/${title.toLowerCase().replace(/\s+/g, '-')}`;

    const components: PageConfig['components'] = [];

    let yOffset = 0;

    // Add header
    components.push({
      id: 'header',
      type: 'Container',
      props: {
        padding: '24px',
      },
      children: ['title'],
      position: { x: 0, y: yOffset, width: 1200, height: 80 },
    });

    components.push({
      id: 'title',
      type: 'Typography',
      props: {
        variant: 'h4',
        children: title,
      },
      position: { x: 24, y: yOffset + 24, width: 400, height: 40 },
    });

    yOffset += 80;

    // Add requirement sections
    requirements.forEach((req, idx) => {
      components.push({
        id: `req-section-${idx}`,
        type: 'Card',
        props: {
          title: req.title,
        },
        children: [`req-desc-${idx}`],
        position: { x: 0, y: yOffset, width: 1200, height: 100 },
      });

      components.push({
        id: `req-desc-${idx}`,
        type: 'Typography',
        props: {
          variant: 'body2',
          children: req.description,
        },
        position: { x: 24, y: yOffset + 50, width: 1150, height: 40 },
      });

      yOffset += 120;
    });

    return {
      id,
      title,
      route,
      layout: 'canvas',
      components,
      connections: {
        events: [],
        data: [],
        navigation: [],
      },
      metadata: {
        generatedFrom: requirements.map((r) => r.id),
        requirementsCount: requirements.length,
        template: 'custom',
      },
    };
  }

  /**
   * Update an existing PageConfig with new requirements.
   *
   * @param existingConfig - Existing PageConfig
   * @param newRequirements - New requirements to add
   * @returns Updated PageConfig
   */
  updateWithRequirements(existingConfig: PageConfig, newRequirements: RequirementConfig[]): PageConfig {
    const updated = JSON.parse(JSON.stringify(existingConfig)) as PageConfig;

    newRequirements.forEach((req, idx) => {
      const yOffset = (updated.components.length || 0) * 120;

      updated.components.push({
        id: `req-section-${updated.components.length}`,
        type: 'Card',
        props: {
          title: req.title,
        },
        children: [`req-desc-${updated.components.length}`],
        position: { x: 0, y: yOffset, width: 1200, height: 100 },
      });

      updated.components.push({
        id: `req-desc-${updated.components.length}`,
        type: 'Typography',
        props: {
          variant: 'body2',
          children: req.description,
        },
        position: { x: 24, y: yOffset + 50, width: 1150, height: 40 },
      });
    });

    updated.metadata = {
      ...updated.metadata,
      generatedFrom: [...(updated.metadata.generatedFrom || []), ...newRequirements.map((r) => r.id)],
      requirementsCount: (updated.metadata.requirementsCount || 0) + newRequirements.length,
    };

    return updated;
  }
}
