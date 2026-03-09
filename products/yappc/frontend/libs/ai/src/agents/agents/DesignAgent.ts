/**
 * Design Agent
 *
 * AI agent specialized in design review and UI/UX analysis.
 * Provides automated feedback on:
 * - Visual consistency
 * - Accessibility compliance
 * - Design system adherence
 * - Layout best practices
 * - Color contrast and typography
 */

import { BaseAgent } from '../base/Agent';

import type { AgentConfig, TaskResult } from '../types';
import type { IAIService } from '../../core/index.js';

/**
 * Design review input
 */
export interface DesignReviewInput {
  /** Component or page HTML/JSX */
  code: string;
  /** Component styles (CSS/styled-components) */
  styles?: string;
  /** Design system rules to check against */
  designSystem?: DesignSystemRules;
  /** Screenshots or images to analyze */
  screenshots?: string[];
  /** Context about the component */
  context?: string;
}

/**
 * Design system rules
 */
export interface DesignSystemRules {
  colors?: {
    primary?: string[];
    secondary?: string[];
    error?: string[];
    warning?: string[];
    success?: string[];
  };
  typography?: {
    fontFamilies?: string[];
    fontSizes?: string[];
    lineHeights?: string[];
  };
  spacing?: {
    scale?: number[];
    unit?: string;
  };
  breakpoints?: {
    mobile?: number;
    tablet?: number;
    desktop?: number;
  };
  accessibility?: {
    minContrast?: number;
    requireAltText?: boolean;
    requireAriaLabels?: boolean;
  };
}

/**
 * Design issue severity
 */
export type DesignIssueSeverity = 'critical' | 'major' | 'minor' | 'suggestion';

/**
 * Design issue category
 */
export type DesignIssueCategory =
  | 'accessibility'
  | 'visual-consistency'
  | 'layout'
  | 'typography'
  | 'color'
  | 'spacing'
  | 'responsive'
  | 'performance';

/**
 * Design issue found during review
 */
export interface DesignIssue {
  id: string;
  severity: DesignIssueSeverity;
  category: DesignIssueCategory;
  message: string;
  location?: {
    line?: number;
    column?: number;
    selector?: string;
  };
  suggestion?: string;
  codeSnippet?: string;
  fixExample?: string;
}

/**
 * Design review result
 */
export interface DesignReviewOutput {
  issues: DesignIssue[];
  score: number; // 0-100
  summary: string;
  strengths: string[];
  improvements: string[];
  accessibilityScore: number; // 0-100
  consistencyScore: number; // 0-100
}

/**
 * Design Agent implementation
 */
export class DesignAgent extends BaseAgent<
  DesignReviewInput,
  DesignReviewOutput
> {
  /**
   *
   */
  constructor(config: Omit<AgentConfig, 'capabilities'>) {
    super({
      ...config,
      capabilities: ['design-review', 'accessibility', 'styling'],
    });
  }

  /**
   * Execute design review task
   */
  protected async executeTask(
    input: DesignReviewInput
  ): Promise<TaskResult<DesignReviewOutput>> {
    const issues: DesignIssue[] = [];
    const strengths: string[] = [];

    // Run all checks
    const accessibilityIssues = await this.checkAccessibility(input);
    const consistencyIssues = await this.checkVisualConsistency(input);
    const layoutIssues = await this.checkLayout(input);
    const typographyIssues = await this.checkTypography(input);
    const colorIssues = await this.checkColors(input);
    const responsiveIssues = await this.checkResponsiveness(input);

    issues.push(
      ...accessibilityIssues,
      ...consistencyIssues,
      ...layoutIssues,
      ...typographyIssues,
      ...colorIssues,
      ...responsiveIssues
    );

    // Calculate scores
    const accessibilityScore =
      this.calculateAccessibilityScore(accessibilityIssues);
    const consistencyScore = this.calculateConsistencyScore(consistencyIssues);
    const overallScore = this.calculateOverallScore(issues);

    // Generate strengths
    if (accessibilityScore >= 90) {
      strengths.push('Excellent accessibility compliance');
    }
    if (consistencyScore >= 90) {
      strengths.push('Strong visual consistency');
    }
    if (issues.filter((i) => i.severity === 'critical').length === 0) {
      strengths.push('No critical issues found');
    }

    // Generate summary
    const summary = await this.generateSummary(input, issues, {
      accessibilityScore,
      consistencyScore,
      overallScore,
    });

    // Generate improvement suggestions
    const improvements = this.generateImprovements(issues);

    const output: DesignReviewOutput = {
      issues,
      score: overallScore,
      summary,
      strengths,
      improvements,
      accessibilityScore,
      consistencyScore,
    };

    return {
      success: true,
      output,
      confidence: this.calculateConfidence(issues),
      suggestions: improvements.slice(0, 3),
      warnings: issues
        .filter((i) => i.severity === 'critical' || i.severity === 'major')
        .map((i) => i.message),
    };
  }

  /**
   * Check accessibility compliance
   */
  private async checkAccessibility(
    input: DesignReviewInput
  ): Promise<DesignIssue[]> {
    const issues: DesignIssue[] = [];
    const { code, designSystem } = input;

    // Check for missing alt text on images
    const imgRegex = /<img[^>]*>/gi;
    const images = code.match(imgRegex) || [];
    images.forEach((img, index) => {
      if (!img.includes('alt=')) {
        issues.push({
          id: `a11y-alt-${index}`,
          severity: 'critical',
          category: 'accessibility',
          message: 'Image missing alt text',
          location: { selector: `img:nth-child(${index + 1})` },
          suggestion: 'Add descriptive alt text to all images',
          fixExample: '<img src="..." alt="Description of image" />',
        });
      }
    });

    // Check for missing ARIA labels on interactive elements
    const interactiveElements = ['button', 'input', 'select', 'textarea'];
    interactiveElements.forEach((tag) => {
      const regex = new RegExp(`<${tag}[^>]*>`, 'gi');
      const elements = code.match(regex) || [];
      elements.forEach((element, index) => {
        if (
          !element.includes('aria-label') &&
          !element.includes('aria-labelledby')
        ) {
          const hasVisibleLabel = element.match(/>\s*\w+/);
          if (
            !hasVisibleLabel &&
            designSystem?.accessibility?.requireAriaLabels
          ) {
            issues.push({
              id: `a11y-aria-${tag}-${index}`,
              severity: 'major',
              category: 'accessibility',
              message: `${tag} missing accessible label`,
              location: { selector: `${tag}:nth-child(${index + 1})` },
              suggestion: 'Add aria-label or aria-labelledby attribute',
              fixExample: `<${tag} aria-label="Description">...</${tag}>`,
            });
          }
        }
      });
    });

    // Check color contrast (if styles provided)
    if (input.styles) {
      const contrastIssues = this.checkColorContrast(
        input.styles,
        designSystem
      );
      issues.push(...contrastIssues);
    }

    // Check for semantic HTML
    if (!code.includes('<main') && !code.includes('role="main"')) {
      issues.push({
        id: 'a11y-main',
        severity: 'minor',
        category: 'accessibility',
        message: 'Missing main landmark',
        suggestion: 'Wrap main content in <main> element',
        fixExample: '<main>...</main>',
      });
    }

    return issues;
  }

  /**
   * Check color contrast ratios
   */
  private checkColorContrast(
    styles: string,
    designSystem?: DesignSystemRules
  ): DesignIssue[] {
    const issues: DesignIssue[] = [];
    const minContrast = designSystem?.accessibility?.minContrast ?? 4.5;

    // Extract color declarations
    const colorRegex = /color:\s*([^;]+);/gi;
    const backgroundRegex = /background(?:-color)?:\s*([^;]+);/gi;

    const colors = styles.match(colorRegex) || [];
    const backgrounds = styles.match(backgroundRegex) || [];

    // Simple heuristic: warn if using similar colors
    if (colors.length > 0 && backgrounds.length > 0) {
      issues.push({
        id: 'a11y-contrast-check',
        severity: 'minor',
        category: 'accessibility',
        message: `Verify color contrast meets WCAG ${minContrast}:1 ratio`,
        suggestion:
          'Use a contrast checker tool to verify all text is readable',
      });
    }

    return issues;
  }

  /**
   * Check visual consistency with design system
   */
  private async checkVisualConsistency(
    input: DesignReviewInput
  ): Promise<DesignIssue[]> {
    const issues: DesignIssue[] = [];
    const { code, styles, designSystem } = input;

    if (!designSystem) return issues;

    // Check color usage
    if (designSystem.colors && styles) {
      const allColors = [
        ...(designSystem.colors.primary || []),
        ...(designSystem.colors.secondary || []),
        ...(designSystem.colors.error || []),
        ...(designSystem.colors.warning || []),
        ...(designSystem.colors.success || []),
      ];

      // Extract colors from styles
      const colorRegex = /#[0-9a-f]{3,6}|rgb\([^)]+\)|hsl\([^)]+\)/gi;
      const usedColors = styles.match(colorRegex) || [];

      usedColors.forEach((color) => {
        if (
          !allColors.some(
            (dsColor) => dsColor.toLowerCase() === color.toLowerCase()
          )
        ) {
          issues.push({
            id: `consistency-color-${color}`,
            severity: 'minor',
            category: 'visual-consistency',
            message: `Color "${color}" not in design system`,
            suggestion: 'Use design system colors for consistency',
          });
        }
      });
    }

    // Check spacing
    if (designSystem.spacing && styles) {
      const spacingValues = designSystem.spacing.scale || [
        0, 4, 8, 16, 24, 32, 48, 64,
      ];
      const unit = designSystem.spacing.unit || 'px';

      const spacingRegex = new RegExp(
        `(margin|padding)[^:]*:\\s*([0-9.]+)${unit}`,
        'gi'
      );
      const usedSpacing = styles.match(spacingRegex) || [];

      usedSpacing.forEach((spacing) => {
        const value = parseFloat(spacing.match(/[0-9.]+/)?.[0] || '0');
        if (!spacingValues.includes(value)) {
          issues.push({
            id: `consistency-spacing-${value}`,
            severity: 'suggestion',
            category: 'spacing',
            message: `Spacing value ${value}${unit} not in design system scale`,
            suggestion: `Use design system spacing: ${spacingValues.join(', ')}${unit}`,
          });
        }
      });
    }

    return issues;
  }

  /**
   * Check layout best practices
   */
  private async checkLayout(input: DesignReviewInput): Promise<DesignIssue[]> {
    const issues: DesignIssue[] = [];
    const { code } = input;

    // Check for inline styles (anti-pattern)
    const inlineStyles = code.match(/style=/gi) || [];
    if (inlineStyles.length > 5) {
      issues.push({
        id: 'layout-inline-styles',
        severity: 'minor',
        category: 'layout',
        message: 'Excessive inline styles detected',
        suggestion:
          'Use CSS classes or styled-components for better maintainability',
      });
    }

    // Check for flexbox/grid usage
    const hasModernLayout = input.styles?.match(/display:\s*(flex|grid)/gi);
    if (!hasModernLayout) {
      issues.push({
        id: 'layout-modern',
        severity: 'suggestion',
        category: 'layout',
        message: 'Consider using flexbox or grid for layout',
        suggestion:
          'Modern layout techniques improve responsiveness and maintainability',
      });
    }

    return issues;
  }

  /**
   * Check typography
   */
  private async checkTypography(
    input: DesignReviewInput
  ): Promise<DesignIssue[]> {
    const issues: DesignIssue[] = [];
    const { styles, designSystem } = input;

    if (!styles) return issues;

    // Check font families
    if (designSystem?.typography?.fontFamilies) {
      const fontRegex = /font-family:\s*([^;]+);/gi;
      const fonts = styles.match(fontRegex) || [];

      fonts.forEach((font) => {
        const fontFamily = font.split(':')[1].trim();
        const isValid = designSystem.typography!.fontFamilies!.some((df) =>
          fontFamily.includes(df)
        );

        if (!isValid) {
          issues.push({
            id: `typography-font-${fontFamily}`,
            severity: 'minor',
            category: 'typography',
            message: `Font family "${fontFamily}" not in design system`,
            suggestion: `Use: ${designSystem.typography!.fontFamilies!.join(', ')}`,
          });
        }
      });
    }

    // Check line height
    const lineHeightRegex = /line-height:\s*([0-9.]+)/gi;
    const lineHeights = styles.match(lineHeightRegex) || [];
    lineHeights.forEach((lh) => {
      const value = parseFloat(lh.match(/[0-9.]+/)?.[0] || '0');
      if (value < 1.2) {
        issues.push({
          id: `typography-line-height-${value}`,
          severity: 'minor',
          category: 'typography',
          message: `Line height ${value} is too tight`,
          suggestion: 'Use line-height of at least 1.5 for body text',
        });
      }
    });

    return issues;
  }

  /**
   * Check color usage
   */
  private async checkColors(input: DesignReviewInput): Promise<DesignIssue[]> {
    const issues: DesignIssue[] = [];
    const { styles } = input;

    if (!styles) return issues;

    // Check for too many colors
    const colorRegex = /#[0-9a-f]{3,6}|rgb\([^)]+\)|hsl\([^)]+\)/gi;
    const uniqueColors = new Set(styles.match(colorRegex) || []);

    if (uniqueColors.size > 15) {
      issues.push({
        id: 'color-variety',
        severity: 'minor',
        category: 'color',
        message: `Too many colors used (${uniqueColors.size})`,
        suggestion: 'Limit color palette for better visual consistency',
      });
    }

    return issues;
  }

  /**
   * Check responsive design
   */
  private async checkResponsiveness(
    input: DesignReviewInput
  ): Promise<DesignIssue[]> {
    const issues: DesignIssue[] = [];
    const { styles, code } = input;

    if (!styles) return issues;

    // Check for media queries
    const hasMediaQueries = styles.match(/@media/gi);
    if (!hasMediaQueries) {
      issues.push({
        id: 'responsive-media-queries',
        severity: 'minor',
        category: 'responsive',
        message: 'No media queries found',
        suggestion: 'Add responsive breakpoints for mobile/tablet/desktop',
      });
    }

    // Check for fixed widths
    const fixedWidths = styles.match(/width:\s*[0-9]+px/gi) || [];
    if (fixedWidths.length > 3) {
      issues.push({
        id: 'responsive-fixed-width',
        severity: 'suggestion',
        category: 'responsive',
        message: 'Multiple fixed widths found',
        suggestion: 'Use relative units (%, rem, vw) for better responsiveness',
      });
    }

    return issues;
  }

  /**
   * Calculate accessibility score
   */
  private calculateAccessibilityScore(issues: DesignIssue[]): number {
    const a11yIssues = issues.filter((i) => i.category === 'accessibility');
    const criticalCount = a11yIssues.filter(
      (i) => i.severity === 'critical'
    ).length;
    const majorCount = a11yIssues.filter((i) => i.severity === 'major').length;
    const minorCount = a11yIssues.filter((i) => i.severity === 'minor').length;

    const deduction = criticalCount * 20 + majorCount * 10 + minorCount * 5;
    return Math.max(0, 100 - deduction);
  }

  /**
   * Calculate consistency score
   */
  private calculateConsistencyScore(issues: DesignIssue[]): number {
    const consistencyIssues = issues.filter(
      (i) => i.category === 'visual-consistency'
    );
    const deduction = consistencyIssues.length * 5;
    return Math.max(0, 100 - deduction);
  }

  /**
   * Calculate overall design score
   */
  private calculateOverallScore(issues: DesignIssue[]): number {
    const criticalCount = issues.filter(
      (i) => i.severity === 'critical'
    ).length;
    const majorCount = issues.filter((i) => i.severity === 'major').length;
    const minorCount = issues.filter((i) => i.severity === 'minor').length;

    const deduction = criticalCount * 15 + majorCount * 8 + minorCount * 3;
    return Math.max(0, 100 - deduction);
  }

  /**
   * Calculate confidence in the review
   */
  private calculateConfidence(issues: DesignIssue[]): number {
    // Higher confidence with more concrete issues found
    const concreteIssues = issues.filter(
      (i) => i.category === 'accessibility' || i.severity === 'critical'
    ).length;

    return Math.min(1, 0.6 + concreteIssues * 0.05);
  }

  /**
   * Generate human-readable summary
   */
  private async generateSummary(
    input: DesignReviewInput,
    issues: DesignIssue[],
    scores: {
      accessibilityScore: number;
      consistencyScore: number;
      overallScore: number;
    }
  ): Promise<string> {
    const { accessibilityScore, consistencyScore, overallScore } = scores;
    const criticalCount = issues.filter(
      (i) => i.severity === 'critical'
    ).length;

    let summary = `Design review completed with an overall score of ${overallScore}/100. `;

    if (criticalCount > 0) {
      summary += `Found ${criticalCount} critical issue${criticalCount > 1 ? 's' : ''} that should be addressed immediately. `;
    }

    summary += `Accessibility score: ${accessibilityScore}/100. `;
    summary += `Visual consistency score: ${consistencyScore}/100.`;

    return summary;
  }

  /**
   * Generate improvement recommendations
   */
  private generateImprovements(issues: DesignIssue[]): string[] {
    const improvements: string[] = [];

    // Group issues by category
    const byCategory = issues.reduce(
      (acc, issue) => {
        if (!acc[issue.category]) acc[issue.category] = [];
        acc[issue.category].push(issue);
        return acc;
      },
      {} as Record<string, DesignIssue[]>
    );

    // Generate category-level improvements
    Object.entries(byCategory).forEach(([category, categoryIssues]) => {
      if (categoryIssues.length > 0) {
        const topIssue = categoryIssues.sort((a, b) => {
          const severityOrder = {
            critical: 0,
            major: 1,
            minor: 2,
            suggestion: 3,
          };
          return severityOrder[a.severity] - severityOrder[b.severity];
        })[0];

        improvements.push(topIssue.suggestion || topIssue.message);
      }
    });

    return improvements.slice(0, 5); // Top 5 improvements
  }
}
