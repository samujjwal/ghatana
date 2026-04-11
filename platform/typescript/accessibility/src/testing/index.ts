/**
 * @ghatana/accessibility/testing
 *
 * Test utilities and fixtures for accessibility testing.
 * Import in test files only – do not include in production bundles.
 *
 * @doc.type module
 * @doc.purpose Accessibility testing helpers and WCAG test fixtures
 * @doc.layer platform
 * @doc.pattern Testing Support
 */

export {
  mockAxeViolations,
  mockFindings,
  mockAccessibilityScore,
  mockAuditConfig,
  mockAccessibilityReport,
  createMockElement,
  createMinimalReport,
  createMinimalFinding,
} from '../test/fixtures';
