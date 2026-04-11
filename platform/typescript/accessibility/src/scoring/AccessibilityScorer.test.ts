/**
 * Unit tests for AccessibilityScorer
 */

import { describe, it, expect, beforeEach } from 'vitest';

import { AccessibilityScorer, calculateAccessibilityScore } from './AccessibilityScorer';
import { mockFindings, mockAccessibilityReport, createMinimalFinding } from '../test/fixtures';

import type { Finding, IndustryBenchmark } from '../types';

describe('AccessibilityScorer', () => {
  let scorer: AccessibilityScorer;

  beforeEach(() => {
    scorer = AccessibilityScorer.getInstance();
  });

  describe('getInstance', () => {
    it('should return singleton instance', () => {
      const instance1 = AccessibilityScorer.getInstance();
      const instance2 = AccessibilityScorer.getInstance();
      expect(instance1).toBe(instance2);
    });
  });

  describe('calculateScore', () => {
    it('should calculate score with perfect compliance', () => {
      const findings: Finding[] = [];
      const score = scorer.calculateScore(findings, 'AA');

      expect(score.overall).toBe(100);
      expect(score.grade).toBe('A+');
      expect(score.complianceLevel).toBeDefined();
    });

    it('should calculate score with violations', () => {
      const score = scorer.calculateScore(mockFindings, 'AA');

      expect(score.overall).toBeLessThan(100);
      expect(score.overall).toBeGreaterThan(0);
      expect(score.grade).toMatch(/^[A-F][+-]?$/);
    });

    it('should penalize critical violations more than minor ones', () => {
      const criticalFinding = createMinimalFinding({ severity: 'critical' });
      const minorFinding = createMinimalFinding({ severity: 'minor' });

      const criticalScore = scorer.calculateScore([criticalFinding], 'AA');
      const minorScore = scorer.calculateScore([minorFinding], 'AA');

      expect(minorScore.overall).toBeGreaterThan(criticalScore.overall);
    });

    it('should include all dimension scores', () => {
      const score = scorer.calculateScore(mockFindings, 'AA');

      expect(score.dimensions).toHaveProperty('wcagCompliance');
      expect(score.dimensions).toHaveProperty('keyboardAccessibility');
      expect(score.dimensions).toHaveProperty('semanticStructure');
      expect(score.dimensions).toHaveProperty('visualAccessibility');
      expect(score.dimensions).toHaveProperty('formAccessibility');
      expect(score.dimensions).toHaveProperty('mediaAccessibility');
      expect(score.dimensions).toHaveProperty('ariaImplementation');
      expect(score.dimensions).toHaveProperty('focusManagement');
    });

    it('should include correct weights', () => {
      const score = scorer.calculateScore(mockFindings, 'AA');

      // Weights are stored as integers (percentages)
      expect(score.dimensions.wcagCompliance.weight).toBe(25);
      expect(score.dimensions.keyboardAccessibility.weight).toBe(20);
      expect(score.dimensions.semanticStructure.weight).toBe(15);
      expect(score.dimensions.visualAccessibility.weight).toBe(15);
    });

    it('should handle WCAG level AAA', () => {
      const score = scorer.calculateScore(mockFindings, 'AAA');
      // WCAG level is used for scoring but compliance level is what's returned
      expect(score.complianceLevel).toBeDefined();
    });
  });

  describe('assignGrade', () => {
    it('should assign A+ for score 95-100', () => {
      const score = { overall: 98 } as any;
      const grade = scorer['getGrade'](score.overall);
      expect(grade).toBe('A+');
    });

    it('should assign A for score 93-96', () => {
      const score = { overall: 94 } as any;
      const grade = scorer['getGrade'](score.overall);
      expect(grade).toBe('A');
    });

    it('should assign B for score 83-86', () => {
      const score = { overall: 85 } as any;
      const grade = scorer['getGrade'](score.overall);
      expect(grade).toBe('B');
    });

    it('should assign F for score below 60', () => {
      const score = { overall: 45 } as any;
      const grade = scorer['getGrade'](score.overall);
      expect(grade).toBe('F');
    });
  });

  describe('generateRecommendations', () => {
    it('should generate recommendations for findings', () => {
      const recommendations = scorer.generateRecommendations(mockFindings, mockAccessibilityReport.score);

      expect(recommendations).toHaveProperty('immediate');
      expect(recommendations).toHaveProperty('shortTerm');
      expect(recommendations).toHaveProperty('longTerm');
      expect(Array.isArray(recommendations.immediate)).toBe(true);
      expect(Array.isArray(recommendations.shortTerm)).toBe(true);
      expect(Array.isArray(recommendations.longTerm)).toBe(true);
    });

    it('should prioritize critical issues as immediate', () => {
      const criticalFinding = createMinimalFinding({ 
        severity: 'critical',
        description: 'Critical issue',
      });
      const recommendations = scorer.generateRecommendations([criticalFinding], mockAccessibilityReport.score);

      expect(recommendations.immediate.length).toBeGreaterThan(0);
      expect(recommendations.immediate[0]).toContain('critical');
    });

    it('should suggest improvements for low scores', () => {
      const lowScore = { ...mockAccessibilityReport.score, overall: 45 };
      const recommendations = scorer.generateRecommendations(mockFindings, lowScore);

      expect(recommendations.shortTerm.length).toBeGreaterThan(0);
    });

    it('should generate urgent recommendation for score < 70', () => {
      const lowScore = { ...mockAccessibilityReport.score, overall: 65 };
      const recommendations = scorer.generateRecommendations(mockFindings, lowScore);
      
      const hasUrgentMessage = recommendations.immediate.some(rec => 
        rec.toLowerCase().includes('urgent') || rec.toLowerCase().includes('severely')
      );
      expect(hasUrgentMessage).toBe(true);
    });

    it('should recommend WCAG AA compliance for score 70-80', () => {
      const mediumScore = { ...mockAccessibilityReport.score, overall: 75 };
      const recommendations = scorer.generateRecommendations([], mediumScore);
      
      const hasWCAGMessage = [...recommendations.immediate, ...recommendations.shortTerm, ...recommendations.longTerm]
        .some(rec => rec.toLowerCase().includes('wcag aa'));
      expect(hasWCAGMessage).toBe(true);
    });

    it('should encourage progress for score 80-90', () => {
      const goodScore = { ...mockAccessibilityReport.score, overall: 85 };
      const recommendations = scorer.generateRecommendations([], goodScore);
      
      const hasProgressMessage = [...recommendations.immediate, ...recommendations.shortTerm, ...recommendations.longTerm]
        .some(rec => rec.toLowerCase().includes('progress') || rec.toLowerCase().includes('good'));
      expect(hasProgressMessage).toBe(true);
    });

    it('should praise excellent scores >= 90', () => {
      const excellentScore = { ...mockAccessibilityReport.score, overall: 92 };
      const recommendations = scorer.generateRecommendations([], excellentScore);
      
      const hasExcellentMessage = [...recommendations.immediate, ...recommendations.shortTerm, ...recommendations.longTerm]
        .some(rec => rec.toLowerCase().includes('excellent'));
      expect(hasExcellentMessage).toBe(true);
    });

    it('should count critical issues and include in recommendations', () => {
      const criticalFindings = [
        createMinimalFinding({ severity: 'critical', id: 'crit-1' }),
        createMinimalFinding({ severity: 'critical', id: 'crit-2' }),
      ];
      const recommendations = scorer.generateRecommendations(criticalFindings, mockAccessibilityReport.score);
      
      const hasCriticalCount = recommendations.immediate.some(rec => 
        rec.includes('2') && rec.toLowerCase().includes('critical')
      );
      expect(hasCriticalCount).toBe(true);
    });

    it('should count serious issues and include in recommendations', () => {
      const seriousFindings = [
        createMinimalFinding({ severity: 'serious', id: 'ser-1' }),
        createMinimalFinding({ severity: 'serious', id: 'ser-2' }),
        createMinimalFinding({ severity: 'serious', id: 'ser-3' }),
      ];
      const recommendations = scorer.generateRecommendations(seriousFindings, mockAccessibilityReport.score);
      
      const hasSeriousCount = recommendations.shortTerm.some(rec => 
        rec.includes('3') && rec.toLowerCase().includes('serious')
      );
      expect(hasSeriousCount).toBe(true);
    });

    it('should provide fallback message for perfect score with no findings', () => {
      const perfectScore = { ...mockAccessibilityReport.score, overall: 97 };
      const recommendations = scorer.generateRecommendations([], perfectScore);
      
      // With no findings and high score, should get a fallback message
      const hasAnyRecommendation = 
        recommendations.immediate.length > 0 ||
        recommendations.shortTerm.length > 0 ||
        recommendations.longTerm.length > 0;
      expect(hasAnyRecommendation).toBe(true);
    });

    it('should provide fallback message for good score < 95 with no findings', () => {
      const goodScore = { ...mockAccessibilityReport.score, overall: 85 };
      const recommendations = scorer.generateRecommendations([], goodScore);
      
      // Should get "Good progress!" message in shortTerm (score 80-90 range)
      const hasProgressMessage = recommendations.shortTerm.some(rec => 
        rec.toLowerCase().includes('good progress') || rec.toLowerCase().includes('continue improving')
      );
      expect(hasProgressMessage).toBe(true);
    });
  });

  describe('compareWithBenchmark', () => {
    it('should compare score with e-commerce benchmark', () => {
      const benchmark = scorer.compareWithBenchmark(mockAccessibilityReport.score, 'e-commerce');

      expect(benchmark).toHaveProperty('industry');
      expect(benchmark).toHaveProperty('averageScore');
      expect(benchmark).toHaveProperty('topQuartile');
      expect(benchmark).toHaveProperty('yourScore');
      expect(benchmark).toHaveProperty('gap');
      expect(benchmark.industry).toBe('e-commerce');
    });

    it('should calculate positive gap for above-average scores', () => {
      const highScore = { ...mockAccessibilityReport.score, overall: 95 };
      const benchmark = scorer.compareWithBenchmark(highScore, 'e-commerce');

      expect(benchmark.gap).toBeGreaterThan(0);
    });

    it('should work with all industry types', () => {
      const industries: IndustryBenchmark['industry'][] = [
        'e-commerce',
        'saas',
        'government',
        'healthcare',
        'education',
        'media',
        'finance',
      ];

      industries.forEach((industry) => {
        const benchmark = scorer.compareWithBenchmark(mockAccessibilityReport.score, industry);
        expect(benchmark.industry).toBe(industry);
        expect(typeof benchmark.averageScore).toBe('number');
      });
    });
  });

  describe('analyzeTrend', () => {
    it('should detect improving trend', () => {
      const current = mockAccessibilityReport;
      const previous = { ...current, score: { ...current.score, overall: 70 } };

      const trend = scorer.analyzeTrend(current, previous);

      expect(trend.direction).toBe('improving');
      expect(trend.change).toBeGreaterThan(0);
    });

    it('should detect declining trend', () => {
      const current = { ...mockAccessibilityReport, score: { ...mockAccessibilityReport.score, overall: 70 } };
      const previous = mockAccessibilityReport;

      const trend = scorer.analyzeTrend(current, previous);

      expect(trend.direction).toBe('declining');
      expect(trend.change).toBeLessThan(0);
    });

    it('should detect stable trend', () => {
      const current = mockAccessibilityReport;
      const previous = { ...current };

      const trend = scorer.analyzeTrend(current, previous);

      expect(trend.direction).toBe('stable');
      expect(trend.change).toBe(0);
    });

    it('should include previous score', () => {
      const current = mockAccessibilityReport;
      const previous = { ...current, score: { ...current.score, overall: 70 } };

      const trend = scorer.analyzeTrend(current, previous);

      expect(trend.previousScore).toBe(70);
    });
  });

  describe('formatScoreReport', () => {
    it('should format complete score report', () => {
      const report = scorer.formatScoreReport(mockAccessibilityReport.score);

      expect(report).toContain('Overall Score');
      expect(report).toContain(mockAccessibilityReport.score.grade);
      expect(report).toContain('WCAG Compliance');
      expect(report).toContain('Keyboard Accessibility');
    });

    it('should include visual progress bars', () => {
      const report = scorer.formatScoreReport(mockAccessibilityReport.score);

      expect(report).toContain('█'); // Progress bar character
      expect(report).toContain('░'); // Empty bar character
    });

    it('should show all dimension scores', () => {
      const report = scorer.formatScoreReport(mockAccessibilityReport.score);

      expect(report).toContain('WCAG Compliance');
      expect(report).toContain('Keyboard Accessibility');
      expect(report).toContain('Semantic Structure');
      expect(report).toContain('Visual Accessibility');
      expect(report).toContain('Form Accessibility');
      expect(report).toContain('Media Accessibility');
      expect(report).toContain('ARIA Implementation');
      expect(report).toContain('Focus Management');
    });
  });

  describe('edge cases', () => {
    it('should handle empty findings array', () => {
      const score = scorer.calculateScore([], 'AA');
      expect(score.overall).toBe(100);
      expect(score.grade).toBe('A+');
    });

    it('should handle findings without WCAG tags', () => {
      const finding = createMinimalFinding({ tags: [] });
      const score = scorer.calculateScore([finding], 'AA');
      expect(score.overall).toBeGreaterThanOrEqual(0);
      expect(score.overall).toBeLessThanOrEqual(100);
    });

    it('should cap score at 100', () => {
      const score = scorer.calculateScore([], 'AA');
      expect(score.overall).toBeLessThanOrEqual(100);
    });

    it('should floor score at 0', () => {
      const manyFindings = Array(100).fill(createMinimalFinding({ severity: 'critical' }));
      const score = scorer.calculateScore(manyFindings, 'AA');
      expect(score.overall).toBeGreaterThanOrEqual(0);
    });

    it('should handle mixed severity findings', () => {
      const findings = [
        createMinimalFinding({ severity: 'critical', tags: ['wcag2aa'] }),
        createMinimalFinding({ severity: 'serious', tags: ['wcag2aa'] }),
        createMinimalFinding({ severity: 'moderate', tags: ['wcag2a'] }),
        createMinimalFinding({ severity: 'minor', tags: ['best-practice'] }),
      ];
      const score = scorer.calculateScore(findings, 'AA');
      expect(score.overall).toBeGreaterThan(0);
      expect(score.overall).toBeLessThan(100);
    });

    it('should handle different WCAG levels in tags', () => {
      const findings = [
        createMinimalFinding({ tags: ['wcag2a', 'wcag2aa'] }),
        createMinimalFinding({ tags: ['wcag2aaa'] }),
        createMinimalFinding({ tags: ['wcag21aa'] }),
      ];
      const score = scorer.calculateScore(findings, 'AAA');
      expect(score.complianceLevel).toBeDefined();
    });
  });

  describe('additional coverage', () => {
    it('should handle various grade boundaries', () => {
      expect(scorer['getGrade'](97)).toBe('A+');
      expect(scorer['getGrade'](95)).toBe('A');
      expect(scorer['getGrade'](92)).toBe('A-');
      expect(scorer['getGrade'](87)).toBe('B+');
      expect(scorer['getGrade'](85)).toBe('B');
      expect(scorer['getGrade'](80)).toBe('B-');
      expect(scorer['getGrade'](77)).toBe('C+');
      expect(scorer['getGrade'](75)).toBe('C');
      expect(scorer['getGrade'](70)).toBe('C-');
      expect(scorer['getGrade'](60)).toBe('D');
      expect(scorer['getGrade'](30)).toBe('F');
    });

    it('should generate recommendations with different severity counts', () => {
      const findings = [
        createMinimalFinding({ severity: 'critical' }),
        createMinimalFinding({ severity: 'critical' }),
        createMinimalFinding({ severity: 'serious' }),
      ];
      
      const score = scorer.calculateScore(findings, 'AA');
      const recommendations = scorer.generateRecommendations(findings, score);
      
      expect(recommendations.immediate.length).toBeGreaterThan(0);
    });

    it('should handle compareWithBenchmark with different industry types', () => {
      const score = scorer.calculateScore(mockFindings, 'AA');
      
      const industries: Array<'e-commerce' | 'saas' | 'content' | 'enterprise' | 'government'> = [
        'e-commerce', 'saas', 'content', 'enterprise', 'government'
      ];
      
      industries.forEach(industry => {
        const benchmark = scorer.compareWithBenchmark(score, industry);
        expect(benchmark).toBeDefined();
        expect(benchmark.industry).toBe(industry);
      });
    });

    it('should format score report with dimensions', () => {
      const score = scorer.calculateScore(mockFindings, 'AA');
      const report = scorer.formatScoreReport(score);
      
      expect(report).toContain('WCAG Compliance');
      expect(report).toContain('Keyboard Accessibility');
      expect(report).toContain('Semantic Structure');
    });

    it('should identify common issue patterns and add recommendations', () => {
      // Create findings with the same rule repeated 3+ times to trigger identifyCommonIssues
      const findings = [
        createMinimalFinding({ id: 'color-contrast', help: 'Elements must have sufficient color contrast' }),
        createMinimalFinding({ id: 'color-contrast', help: 'Elements must have sufficient color contrast' }),
        createMinimalFinding({ id: 'color-contrast', help: 'Elements must have sufficient color contrast' }),
        createMinimalFinding({ id: 'color-contrast', help: 'Elements must have sufficient color contrast' }),
      ];
      
      const score = scorer.calculateScore(findings, 'AA');
      const recommendations = scorer.generateRecommendations(findings, score);
      
      // Should include pattern detection recommendation
      const allRecommendations = [...recommendations.immediate, ...recommendations.shortTerm, ...recommendations.longTerm];
      const hasPatternRec = allRecommendations.some(rec => rec.includes('Pattern detected') || rec.includes('appears') || rec.includes('systematic'));
      expect(hasPatternRec).toBe(true);
    });

    it('should generate score range specific recommendations for 70-79', () => {
      // Create findings that result in ~75 score
      const findings = [
        createMinimalFinding({ severity: 'serious' }),
        createMinimalFinding({ severity: 'moderate' }),
      ];
      
      const score = scorer.calculateScore(findings, 'AA');
      const recommendations = scorer.generateRecommendations(findings, score);
      
      const allRecs = [...recommendations.immediate, ...recommendations.shortTerm, ...recommendations.longTerm];
      expect(allRecs.length).toBeGreaterThan(0);
    });

    it('should generate score range specific recommendations for 80-89', () => {
      // Create findings that result in ~85 score
      const findings = [
        createMinimalFinding({ severity: 'moderate' }),
      ];
      
      const score = scorer.calculateScore(findings, 'AA');
      const recommendations = scorer.generateRecommendations(findings, score);
      
      const allRecs = [...recommendations.immediate, ...recommendations.shortTerm, ...recommendations.longTerm];
      expect(allRecs.length).toBeGreaterThan(0);
    });

    it('should generate score range specific recommendations for 90+', () => {
      // Create empty findings for high score
      const findings: Finding[] = [];
      
      const score = scorer.calculateScore(findings, 'AA');
      const recommendations = scorer.generateRecommendations(findings, score);
      
      const allRecs = [...recommendations.immediate, ...recommendations.shortTerm, ...recommendations.longTerm];
      const hasExcellence = allRecs.some(rec => rec.toLowerCase().includes('excellent') || rec.toLowerCase().includes('maintain'));
      expect(hasExcellence).toBe(true);
    });

    it('should format score report with trend data', () => {
      const score = scorer.calculateScore(mockFindings, 'AA');
      const scoreWithTrend = { 
        ...score, 
        trend: {
          direction: 'improving' as const,
          changePercentage: 5,
          previousScore: 80,
          projectedScore: 90
        }
      };
      
      const report = scorer.formatScoreReport(scoreWithTrend);
      
      expect(report).toContain('Trend:');
      expect(report).toContain('↗');
      expect(report).toContain('IMPROVING');
      expect(report).toContain('+5%');
    });

    it('should format score report with degrading trend', () => {
      const score = scorer.calculateScore(mockFindings, 'AA');
      const scoreWithTrend = { 
        ...score, 
        trend: {
          direction: 'degrading' as const,
          changePercentage: -5,
          previousScore: 90,
          projectedScore: 85
        }
      };
      
      const report = scorer.formatScoreReport(scoreWithTrend);
      
      expect(report).toContain('↘');
      expect(report).toContain('DEGRADING');
    });

    it('should format score report with benchmark data', () => {
      const score = scorer.calculateScore(mockFindings, 'AA');
      const scoreWithBenchmark = { 
        ...score, 
        benchmark: {
          industry: 'e-commerce',
          averageScore: 78,
          top10PercentScore: 92,
          percentile: 65
        }
      };
      
      const report = scorer.formatScoreReport(scoreWithBenchmark);
      
      expect(report).toContain('Industry:');
      expect(report).toContain('e-commerce');
      expect(report).toContain('Industry Average:');
      expect(report).toContain('Top 10%:');
      expect(report).toContain('Your Percentile:');
    });

    it('should format score report with dimensions showing critical issues', () => {
      const findings = [
        createMinimalFinding({ severity: 'critical' }),
        createMinimalFinding({ severity: 'serious' }),
      ];
      const score = scorer.calculateScore(findings, 'AA');
      
      const report = scorer.formatScoreReport(score);
      
      // Should show issue counts in dimension output
      expect(report).toContain('critical');
      expect(report).toContain('serious');
    });

    it('should identify patterns by wcag.criterion when id not found', () => {
      // Create findings where all have same id to trigger pattern matching (3+ occurrences)
      const findings = [
        createMinimalFinding({ id: 'color-contrast-repeated', help: 'Contrast issue repeated' }),
        createMinimalFinding({ id: 'color-contrast-repeated', help: 'Contrast issue repeated' }),
        createMinimalFinding({ id: 'color-contrast-repeated', help: 'Contrast issue repeated' }),
        createMinimalFinding({ id: 'color-contrast-repeated', help: 'Contrast issue repeated' }),
      ];
      
      const score = scorer.calculateScore(findings, 'AA');
      const recommendations = scorer.generateRecommendations(findings, score);
      
      const allRecs = [...recommendations.immediate, ...recommendations.shortTerm, ...recommendations.longTerm];
      const hasPatternRec = allRecs.some(rec => rec.includes('Pattern') || rec.includes('appears') || rec.includes('systematic'));
      expect(hasPatternRec).toBe(true);
    });

    it('should test createProgressBar with different scores', () => {
      const score100 = scorer.calculateScore([], 'AA');
      const score50 = scorer.calculateScore(
        Array.from({ length: 50 }, () => createMinimalFinding({ severity: 'critical' })), 
        'AA'
      );
      const score0 = scorer.calculateScore(
        Array.from({ length: 200 }, () => createMinimalFinding({ severity: 'critical' })), 
        'AA'
      );
      
      const report100 = scorer.formatScoreReport(score100);
      const report50 = scorer.formatScoreReport(score50);
      const report0 = scorer.formatScoreReport(score0);
      
      // Progress bars should be different for different scores
      expect(report100).toContain('[');
      expect(report50).toContain('[');
      expect(report0).toContain('[');
    });
  });

  describe('convenience function exports', () => {
    it('should export calculateAccessibilityScore function', () => {
      const score = calculateAccessibilityScore(mockFindings, 'AA');
      
      expect(score).toHaveProperty('overall');
      expect(score).toHaveProperty('grade');
      expect(score).toHaveProperty('dimensions');
    });
  });

  describe('internal methods coverage', () => {
    it('should call generateRecommendationsInternal with score < 70', () => {
      const findings = [
        createMinimalFinding({ severity: 'critical', id: 'c1' }),
        createMinimalFinding({ severity: 'serious', id: 's1' }),
      ];
      // Access private method via type assertion for testing
      const recommendations = (scorer as any).generateRecommendationsInternal(findings, 65);
      
      expect(recommendations).toBeInstanceOf(Array);
      expect(recommendations.some((r: string) => r.toLowerCase().includes('urgent'))).toBe(true);
    });

    it('should call generateRecommendationsInternal with score 70-80', () => {
      const recommendations = (scorer as any).generateRecommendationsInternal([], 75);
      
      expect(recommendations.some((r: string) => r.toLowerCase().includes('wcag aa'))).toBe(true);
    });

    it('should call generateRecommendationsInternal with score 80-90', () => {
      const recommendations = (scorer as any).generateRecommendationsInternal([], 85);
      
      expect(recommendations.some((r: string) => r.toLowerCase().includes('progress'))).toBe(true);
    });

    it('should call generateRecommendationsInternal with score >= 90', () => {
      const recommendations = (scorer as any).generateRecommendationsInternal([], 92);
      
      expect(recommendations.some((r: string) => r.toLowerCase().includes('excellent'))).toBe(true);
    });

    it('should count critical and serious issues in generateRecommendationsInternal', () => {
      const findings = [
        createMinimalFinding({ severity: 'critical', id: 'c1' }),
        createMinimalFinding({ severity: 'critical', id: 'c2' }),
        createMinimalFinding({ severity: 'serious', id: 's1' }),
        createMinimalFinding({ severity: 'serious', id: 's2' }),
        createMinimalFinding({ severity: 'serious', id: 's3' }),
      ];
      const recommendations = (scorer as any).generateRecommendationsInternal(findings, 75);
      
      expect(recommendations.some((r: string) => r.includes('2') && r.includes('critical'))).toBe(true);
      expect(recommendations.some((r: string) => r.includes('3') && r.includes('serious'))).toBe(true);
    });
  });
});

