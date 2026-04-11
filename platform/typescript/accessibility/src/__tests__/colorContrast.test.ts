import { describe, it, expect, beforeEach } from 'vitest';
import { AccessibilityScorer } from '../scoring/AccessibilityScorer';
import type { Finding } from '../types';

/**
 * Color contrast tests — validates that low-contrast findings are correctly
 * scored and penalized according to WCAG 1.4.3 / 1.4.6 criteria.
 *
 * @doc.type module
 * @doc.purpose Tests for color contrast accessibility detection and scoring
 * @doc.layer platform
 * @doc.pattern Test
 */

describe('color contrast accessibility', () => {
  let scorer: AccessibilityScorer;

  beforeEach(() => {
    scorer = AccessibilityScorer.getInstance();
  });

  const buildContrastFinding = (severity: Finding['severity'] = 'serious'): Finding => ({
    type: 'wcag-violation',
    wcagReference: 'WCAG-1.4.3',
    severity,
    description: 'Text contrast ratio of 2.5:1 does not meet the 4.5:1 minimum',
    element: '<p class="body-text">',
    location: { selector: 'p.body-text', line: 88, column: 5 },
    recommendation: 'Increase text color contrast to at least 4.5:1 for normal text',
    affectedUsers: ['low-vision', 'color-blind'],
    wcagLevel: 'AA',
  });

  describe('contrast ratio violations', () => {
    it('serious contrast violation reduces score below 90', () => {
      const score = scorer.calculateScore([buildContrastFinding('serious')], 'AA');
      expect(score.overall).toBeLessThan(90);
    });

    it('minor contrast violation produces score above serious violation', () => {
      const withSerious = scorer.calculateScore([buildContrastFinding('serious')], 'AA');
      const withMinor = scorer.calculateScore([buildContrastFinding('minor')], 'AA');

      expect(withMinor.overall).toBeGreaterThan(withSerious.overall);
    });

    it('critical contrast violation produces score below 75', () => {
      const score = scorer.calculateScore([buildContrastFinding('critical')], 'AA');
      expect(score.overall).toBeLessThan(75);
    });
  });

  describe('enhanced contrast (WCAG 1.4.6)', () => {
    it('enhanced contrast violation is a recognizable finding type', () => {
      const finding: Finding = {
        ...buildContrastFinding('moderate'),
        wcagReference: 'WCAG-1.4.6',
        description: 'Enhanced contrast (AAA) requirement not met',
        wcagLevel: 'AAA',
      };

      const score = scorer.calculateScore([finding], 'AAA');
      expect(score.overall).toBeLessThan(100);
    });
  });

  describe('multiple contrast violations', () => {
    it('multiple contrast violations cumulatively reduce score', () => {
      const single = scorer.calculateScore([buildContrastFinding('moderate')], 'AA');
      const double = scorer.calculateScore(
        [buildContrastFinding('moderate'), buildContrastFinding('moderate')],
        'AA',
      );

      expect(double.overall).toBeLessThanOrEqual(single.overall);
    });
  });

  describe('clean pass', () => {
    it('no contrast violations yields score of 100', () => {
      const score = scorer.calculateScore([], 'AA');
      expect(score.overall).toBe(100);
    });
  });
});
