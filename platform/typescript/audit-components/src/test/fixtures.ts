/**
 * Test fixtures and mock data for accessibility audit tests
 */

import type {
  AccessibilityReport,
  Finding,
  AccessibilityScore,
  AuditConfig,
  WCAGLevel,
  Severity,
} from '../types';
import type { Result as AxeResult } from 'axe-core';

/**
 * Mock axe-core violations
 */
export const mockAxeViolations: AxeResult[] = [
  {
    id: 'color-contrast',
    impact: 'serious',
    tags: ['wcag2aa', 'wcag143'],
    description: 'Ensures the contrast between foreground and background colors meets WCAG 2 AA contrast ratio thresholds',
    help: 'Elements must have sufficient color contrast',
    helpUrl: 'https://dequeuniversity.com/rules/axe/4.9/color-contrast',
    nodes: [
      {
        html: '<button class="btn-primary">Click me</button>',
        target: ['button.btn-primary'],
        failureSummary: 'Fix any of the following:\n  Element has insufficient color contrast',
        any: [],
        all: [],
        none: [],
        impact: 'serious',
      },
    ],
  },
  {
    id: 'button-name',
    impact: 'critical',
    tags: ['wcag2a', 'wcag412', 'section508'],
    description: 'Ensures buttons have discernible text',
    help: 'Buttons must have discernible text',
    helpUrl: 'https://dequeuniversity.com/rules/axe/4.9/button-name',
    nodes: [
      {
        html: '<button></button>',
        target: ['div > button'],
        failureSummary: 'Fix any of the following:\n  Element does not have inner text',
        any: [],
        all: [],
        none: [],
        impact: 'critical',
      },
    ],
  },
  {
    id: 'aria-required-attr',
    impact: 'critical',
    tags: ['wcag2a', 'wcag412'],
    description: 'Ensures elements with ARIA roles have all required attributes',
    help: 'Required ARIA attributes must be provided',
    helpUrl: 'https://dequeuniversity.com/rules/axe/4.9/aria-required-attr',
    nodes: [
      {
        html: '<div role="button">Click</div>',
        target: ['div[role="button"]'],
        failureSummary: 'Fix all of the following:\n  Required ARIA attribute not present: aria-label',
        any: [],
        all: [],
        none: [],
        impact: 'critical',
      },
    ],
  },
];

/**
 * Mock findings
 */
export const mockFindings: Finding[] = [
  {
    id: 'color-contrast',
    description: 'Elements must have sufficient color contrast',
    impact: 'serious',
    help: 'Ensures the contrast between foreground and background colors meets WCAG 2 AA contrast ratio thresholds',
    helpUrl: 'https://dequeuniversity.com/rules/axe/4.9/color-contrast',
    tags: ['wcag2aa', 'wcag143'],
    location: {
      selector: 'button.btn-primary',
      xpath: null,
      htmlSnippet: '<button class="btn-primary">Click me</button>',
    },
    wcag: {
      level: 'AA' as WCAGLevel,
      successCriteria: ['1.4.3'],
      version: '2.1',
    },
    severity: 'serious' as Severity,
    violationType: 'visual',
    affectedUsers: ['visual', 'colorblind'],
    remediation: {
      priority: 'high',
      effort: 'low',
      summary: 'Increase color contrast to meet WCAG AA standards',
      steps: [
        'Check current contrast ratio',
        'Adjust foreground or background color',
        'Verify new contrast ratio meets 4.5:1 threshold',
      ],
      codeExample: 'color: #000000; background-color: #ffffff;',
      automatable: false,
    },
  },
  {
    id: 'button-name',
    description: 'Buttons must have discernible text',
    impact: 'critical',
    help: 'Ensures buttons have discernible text',
    helpUrl: 'https://dequeuniversity.com/rules/axe/4.9/button-name',
    tags: ['wcag2a', 'wcag412', 'section508'],
    location: {
      selector: 'div > button',
      xpath: null,
      htmlSnippet: '<button></button>',
    },
    wcag: {
      level: 'A' as WCAGLevel,
      successCriteria: ['4.1.2'],
      version: '2.1',
    },
    severity: 'critical' as Severity,
    violationType: 'keyboard',
    affectedUsers: ['screenreader', 'keyboard', 'motor'],
    remediation: {
      priority: 'critical',
      effort: 'low',
      summary: 'Add accessible text to button',
      steps: [
        'Add visible text inside button',
        'Or add aria-label attribute',
        'Or add aria-labelledby pointing to label element',
      ],
      codeExample: '<button aria-label="Submit form">Submit</button>',
      automatable: false,
    },
  },
];

/**
 * Mock accessibility score
 */
export const mockAccessibilityScore: AccessibilityScore = {
  overall: 75.5,
  grade: 'B',
  complianceLevel: 'Partial A',
  calculatedAt: '2024-01-15T10:30:00.000Z',
  dimensions: {
    wcagCompliance: {
      score: 80,
      grade: 'B',
      weight: 0.25,
      contribution: 20,
      issues: { critical: 0, serious: 1, moderate: 0, minor: 0 },
      findings: ['color-contrast'],
      recommendations: ['Improve color contrast ratios'],
    },
    keyboardAccessibility: {
      score: 90,
      grade: 'A',
      weight: 0.20,
      contribution: 18,
      issues: { critical: 0, serious: 0, moderate: 0, minor: 0 },
      findings: [],
      recommendations: [],
    },
    semanticStructure: {
      score: 75,
      grade: 'C',
      weight: 0.15,
      contribution: 11.25,
      issues: { critical: 0, serious: 0, moderate: 1, minor: 0 },
      findings: [],
      recommendations: ['Use semantic HTML elements'],
    },
    visualAccessibility: {
      score: 60,
      grade: 'D',
      weight: 0.15,
      contribution: 9,
      issues: { critical: 0, serious: 1, moderate: 0, minor: 0 },
      findings: ['color-contrast'],
      recommendations: ['Increase text size and contrast'],
    },
    formAccessibility: {
      score: 85,
      grade: 'B+',
      weight: 0.10,
      contribution: 8.5,
      issues: { critical: 0, serious: 0, moderate: 0, minor: 0 },
      findings: [],
      recommendations: [],
    },
    mediaAccessibility: {
      score: 70,
      grade: 'C',
      weight: 0.05,
      contribution: 3.5,
      issues: { critical: 0, serious: 0, moderate: 0, minor: 1 },
      findings: [],
      recommendations: ['Add captions to videos'],
    },
    ariaImplementation: {
      score: 65,
      grade: 'D',
      weight: 0.05,
      contribution: 3.25,
      issues: { critical: 1, serious: 0, moderate: 0, minor: 0 },
      findings: ['button-name', 'aria-required-attr'],
      recommendations: ['Fix ARIA attributes'],
    },
    focusManagement: {
      score: 80,
      grade: 'B',
      weight: 0.05,
      contribution: 4,
      issues: { critical: 0, serious: 0, moderate: 0, minor: 0 },
      findings: [],
      recommendations: [],
    },
  },
  trend: {
    direction: 'improving',
    changePercentage: 5.3,
    previousScore: 70.2,
    projectedScore: 80.8,
  },
};

/**
 * Mock audit config
 */
export const mockAuditConfig: AuditConfig = {
  runOptions: {
    reporter: 'v2',
    resultTypes: ['violations', 'incomplete'],
  },
  mode: 'comprehensive',
  wcagLevel: 'AA',
  includeIncomplete: false,
  includeWarnings: true,
  outputFormat: 'json',
  outputFormats: ['json', 'html'],
  maxIssues: undefined,
  hideSuccess: true,
  includeScreenshots: false,
  viewports: [{ width: 1920, height: 1080 }],
  thresholds: {
    overall: 80,
    wcagCompliance: 90,
    critical: 0,
    serious: 5,
  },
  rules: {},
  additionalChecks: {
    stateAnalysis: false,
    performanceMetrics: false,
    designSystemValidation: false,
  },
};

/**
 * Mock full accessibility report
 */
export const mockAccessibilityReport: AccessibilityReport = {
  metadata: {
    id: 'audit-test-123',
    timestamp: '2024-01-15T10:30:00.000Z',
    toolVersion: '0.8.0',
    config: mockAuditConfig,
    environment: {
      os: 'Test OS',
      browser: 'Test Browser',
      browserVersion: '1.0',
      nodeVersion: '18.0.0',
    },
  },
  target: {
    url: 'https://example.com/app',
    title: 'Test Application',
  },
  score: mockAccessibilityScore,
  findings: mockFindings,
  summary: {
    totalFindings: 2,
    byType: {
      violations: 2,
      warnings: 0,
      suggestions: 0,
      incomplete: 0,
    },
    bySeverity: {
      critical: 1,
      serious: 1,
      moderate: 0,
      minor: 0,
    },
    elementsAnalyzed: 100,
    elementsWithIssues: 2,
    coverage: 98.0,
    duration: 1234,
  },
  recommendations: {
    immediate: [
      'Fix critical button-name violations affecting screen reader users',
    ],
    shortTerm: [
      'Improve color contrast to meet WCAG AA standards',
    ],
    longTerm: [
      'Implement comprehensive accessibility testing in CI/CD',
    ],
  },
};

/**
 * Create mock HTML element for testing
 */
export function createMockElement(html: string): HTMLElement {
  const container = document.createElement('div');
  container.innerHTML = html;
  return container.firstChild as HTMLElement;
}

/**
 * Create minimal mock report for testing
 */
export function createMinimalReport(overrides?: Partial<AccessibilityReport>): AccessibilityReport {
  return {
    ...mockAccessibilityReport,
    ...overrides,
  };
}

/**
 * Create minimal mock finding for testing
 */
export function createMinimalFinding(overrides?: Partial<Finding>): Finding {
  return {
    ...mockFindings[0],
    ...overrides,
  };
}