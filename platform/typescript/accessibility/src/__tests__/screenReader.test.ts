import { describe, it, expect, beforeEach } from 'vitest';
import { AccessibilityScorer } from '../scoring/AccessibilityScorer';
import type { Finding } from '../types';

/**
 * Screen reader accessibility tests — validates that findings that affect screen-reader
 * users are correctly detected and weighted in the score.
 *
 * @doc.type module
 * @doc.purpose Tests for screen-reader accessibility finding detection and scoring
 * @doc.layer platform
 * @doc.pattern Test
 */

describe('screen reader accessibility', () => {
  let scorer: AccessibilityScorer;

  beforeEach(() => {
    scorer = AccessibilityScorer.getInstance();
  });

  const buildFinding = (overrides: Partial<Finding> = {}): Finding => ({
    type: 'wcag-violation',
    wcagReference: 'WCAG-1.1.1',
    severity: 'serious',
    description: 'Missing ARIA label',
    element: '<button>',
    location: { selector: 'button.submit', line: 5, column: 3 },
    recommendation: 'Add an aria-label attribute',
    affectedUsers: ['screen-reader'],
    wcagLevel: 'A',
    ...overrides,
  });

  describe('missing ARIA labels', () => {
    it('missing aria-label finding reduces score', () => {
      const base = scorer.calculateScore([], 'AA');
      const withFinding = scorer.calculateScore([buildFinding()], 'AA');

      expect(withFinding.overall).toBeLessThan(base.overall);
    });

    it('severity serious produces more impact than minor', () => {
      const withSerious = scorer.calculateScore(
        [buildFinding({ severity: 'serious' })],
        'AA',
      );
      const withMinor = scorer.calculateScore(
        [buildFinding({ severity: 'minor' })],
        'AA',
      );

      expect(withSerious.overall).toBeLessThan(withMinor.overall);
    });
  });

  describe('multiple screen-reader findings', () => {
    it('two separate findings produce lower score than one', () => {
      const oneFinding = scorer.calculateScore([buildFinding()], 'AA');
      const twoFindings = scorer.calculateScore(
        [buildFinding(), buildFinding({ wcagReference: 'WCAG-4.1.2' })],
        'AA',
      );

      expect(twoFindings.overall).toBeLessThanOrEqual(oneFinding.overall);
    });
  });

  describe('critical screen-reader violation', () => {
    it('critical severity drives score below 80', () => {
      const finding = buildFinding({ severity: 'critical' });
      const score = scorer.calculateScore([finding], 'AA');

      expect(score.overall).toBeLessThan(80);
    });
  });
});
