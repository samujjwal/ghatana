import { describe, it, expect, beforeEach } from 'vitest';
import { AccessibilityScorer } from '../scoring/AccessibilityScorer';
import type { Finding } from '../types';

/**
 * Keyboard navigation accessibility tests — validates that keyboard accessibility
 * findings are correctly evaluated and reduce the keyboard dimension score.
 *
 * @doc.type module
 * @doc.purpose Tests for keyboard navigation accessibility scoring
 * @doc.layer platform
 * @doc.pattern Test
 */

describe('keyboard navigation accessibility', () => {
  let scorer: AccessibilityScorer;

  beforeEach(() => {
    scorer = AccessibilityScorer.getInstance();
  });

  const buildKeyboardFinding = (overrides: Partial<Finding> = {}): Finding => ({
    type: 'wcag-violation',
    wcagReference: 'WCAG-2.1.1',
    severity: 'critical',
    description: 'Interactive element not keyboard accessible',
    element: '<div onClick="submit()">',
    location: { selector: '.clickable-div', line: 20, column: 1 },
    recommendation: 'Use a <button> or add keyboard event handlers and role="button"',
    affectedUsers: ['keyboard', 'motor-impaired'],
    wcagLevel: 'A',
    ...overrides,
  });

  describe('keyboard trap detection', () => {
    it('keyboard trap violation critically reduces the score', () => {
      const finding = buildKeyboardFinding({
        wcagReference: 'WCAG-2.1.2',
        description: 'Keyboard trap detected in modal dialog',
        severity: 'critical',
      });

      const score = scorer.calculateScore([finding], 'AA');
      expect(score.overall).toBeLessThan(90);
    });
  });

  describe('focus management', () => {
    it('missing focus indicator reduces score', () => {
      const finding = buildKeyboardFinding({
        wcagReference: 'WCAG-2.4.7',
        description: 'Focus indicator missing',
        severity: 'serious',
      });

      const base = scorer.calculateScore([], 'AA');
      const withFinding = scorer.calculateScore([finding], 'AA');

      expect(withFinding.overall).toBeLessThan(base.overall);
    });
  });

  describe('severity impact on keyboard dimension', () => {
    it('moderate violation produces higher score than critical', () => {
      const withCritical = scorer.calculateScore(
        [buildKeyboardFinding({ severity: 'critical' })],
        'AA',
      );
      const withModerate = scorer.calculateScore(
        [buildKeyboardFinding({ severity: 'moderate' })],
        'AA',
      );

      expect(withModerate.overall).toBeGreaterThanOrEqual(withCritical.overall);
    });
  });

  describe('score without keyboard violations', () => {
    it('no keyboard violations produces perfect score', () => {
      const score = scorer.calculateScore([], 'AA');
      expect(score.overall).toBe(100);
    });
  });
});
