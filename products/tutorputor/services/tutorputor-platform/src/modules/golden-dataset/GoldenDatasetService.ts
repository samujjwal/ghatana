/**
 * Golden Dataset Regression Tests
 *
 * Baseline generated artifacts and detect regressions in content quality.
 *
 * @doc.type service
 * @doc.purpose Golden dataset regression testing for content quality
 * @doc.layer platform
 * @doc.pattern Service
 */
import type { PrismaClient } from "@tutorputor/core/db";

export interface GoldenDatasetEntry {
  id: string;
  moduleId: string;
  inputType: string; // Prisma stores as string
  input: string;
  expectedOutput: string;
  qualityMetrics: string; // Prisma stores as JSON string
  createdAt: Date;
}

export interface RegressionTestResult {
  entryId: string;
  moduleId: string;
  input: string;
  actualOutput: string;
  expectedOutput: string;
  passed: boolean;
  qualityDiff: string; // JSON string of { clarity, accuracy, completeness }
  regressionDetected: boolean;
  timestamp: Date;
}

interface QualityMetrics {
  clarity: number;
  accuracy: number;
  completeness: number;
}

interface RegressionTestResultRecord {
  entryId: string;
  moduleId: string;
  input: string;
  actualOutput: string;
  expectedOutput: string;
  passed: boolean;
  qualityDiff: string;
  regressionDetected: boolean;
  timestamp: Date;
}

export class GoldenDatasetService {
  constructor(private prisma: PrismaClient) {}

  /**
   * Add a golden dataset entry
   */
  async addGoldenEntry(entry: Omit<GoldenDatasetEntry, "id" | "createdAt">): Promise<GoldenDatasetEntry> {
    const created = await this.prisma.goldenDataset.create({
      data: {
        moduleId: entry.moduleId,
        inputType: entry.inputType,
        input: entry.input,
        expectedOutput: entry.expectedOutput,
        qualityMetrics: entry.qualityMetrics,
      },
    });

    return {
      ...created,
      qualityMetrics: entry.qualityMetrics,
    };
  }

  /**
   * Run regression test for a module
   */
  async runRegressionTest(moduleId: string): Promise<RegressionTestResult[]> {
    const goldenEntries = await this.prisma.goldenDataset.findMany({
      where: { moduleId },
    });

    const results: RegressionTestResult[] = [];

    for (const entry of goldenEntries) {
      const result = await this.testEntry(entry as GoldenDatasetEntry);
      results.push(result);
    }

    return results;
  }

  /**
   * Test a single golden entry
   */
  private async testEntry(entry: GoldenDatasetEntry): Promise<RegressionTestResult> {
    // Generate actual output using AI services
    const actualOutput = await this.generateOutput(entry);

    // Calculate quality metrics
    const actualQuality = await this.calculateQualityMetrics(actualOutput);
    const expectedQuality = JSON.parse(entry.qualityMetrics) as QualityMetrics;

    // Calculate quality differences
    const qualityDiff = {
      clarity: actualQuality.clarity - expectedQuality.clarity,
      accuracy: actualQuality.accuracy - expectedQuality.accuracy,
      completeness: actualQuality.completeness - expectedQuality.completeness,
    };
    const qualityDiffStr = JSON.stringify(qualityDiff);

    // Determine if regression detected (quality degraded by more than 10%)
    const regressionDetected =
      qualityDiff.clarity < -0.1 ||
      qualityDiff.accuracy < -0.1 ||
      qualityDiff.completeness < -0.1;

    // Compare outputs for similarity
    const similarity = this.textSimilarity(actualOutput, entry.expectedOutput);
    const passed = similarity > 0.7 && !regressionDetected;

    return {
      entryId: entry.id,
      moduleId: entry.moduleId,
      input: entry.input,
      actualOutput,
      expectedOutput: entry.expectedOutput,
      passed,
      qualityDiff: qualityDiffStr,
      regressionDetected,
      timestamp: new Date(),
    };
  }

  /**
   * Generate output for a golden entry
   */
  private async generateOutput(entry: GoldenDatasetEntry): Promise<string> {
    throw new Error('GoldenDataset output generation requires AI generation service integration. Implement actual AI generation pipeline for golden dataset evaluation.');
  }

  /**
   * Calculate quality metrics for output
   */
  private async calculateQualityMetrics(output: string): Promise<{
    clarity: number;
    accuracy: number;
    completeness: number;
  }> {
    const clarity = this.calculateClarity(output);
    const accuracy = this.calculateAccuracy(output);
    const completeness = this.calculateCompleteness(output);

    return { clarity, accuracy, completeness };
  }

  /**
   * Calculate clarity score
   */
  private calculateClarity(text: string): number {
    const sentences = text.split(/[.!?]+/).filter((s) => s.trim().length > 0);
    if (sentences.length === 0) return 0;

    let clarityScore = 0.5;

    // Prefer shorter sentences (easier to understand)
    const avgSentenceLength = text.length / sentences.length;
    if (avgSentenceLength < 50) clarityScore += 0.2;
    else if (avgSentenceLength < 100) clarityScore += 0.1;

    // Check for clear structure
    if (/\n/.test(text)) clarityScore += 0.1;

    // Check for bullet points or numbered lists
    if (/^[\s]*[-*•]\s/m.test(text) || /^\s*\d+\.\s/m.test(text)) {
      clarityScore += 0.1;
    }

    return Math.min(clarityScore, 1);
  }

  /**
   * Calculate accuracy score (placeholder)
   */
  private calculateAccuracy(text: string): number {
    // In a real implementation, this would compare against known facts
    // For now, return a default value
    return 0.8;
  }

  /**
   * Calculate completeness score
   */
  private calculateCompleteness(text: string): number {
    let completenessScore = 0.5;

    // Check for introduction
    if (/^(introduction|overview|summary)/i.test(text.substring(0, 100))) {
      completenessScore += 0.1;
    }

    // Check for conclusion
    if (/(conclusion|summary|in conclusion)/i.test(text.substring(text.length - 200))) {
      completenessScore += 0.1;
    }

    // Check for sufficient length
    if (text.length > 200) completenessScore += 0.1;
    if (text.length > 500) completenessScore += 0.1;

    return Math.min(completenessScore, 1);
  }

  /**
   * Calculate text similarity (simple implementation)
   */
  private textSimilarity(text1: string, text2: string): number {
    const words1 = new Set(text1.toLowerCase().split(/\s+/));
    const words2 = new Set(text2.toLowerCase().split(/\s+/));

    const intersection = new Set([...words1].filter((x) => words2.has(x)));
    const union = new Set([...words1, ...words2]);

    return union.size > 0 ? intersection.size / union.size : 0;
  }

  /**
   * Get regression test history
   */
  async getRegressionTestHistory(moduleId: string, limit: number = 50): Promise<RegressionTestResult[]> {
    const history = await this.prisma.regressionTestResult.findMany({
      where: { moduleId },
      orderBy: { timestamp: "desc" },
      take: limit,
    });

    return history.map((record) => this.mapRegressionResult(record));
  }

  /**
   * Get regression statistics
   */
  async getRegressionStats(moduleId: string): Promise<{
    totalTests: number;
    passed: number;
    failed: number;
    regressions: number;
    avgQualityDiff: {
      clarity: number;
      accuracy: number;
      completeness: number;
    };
  }> {
    const results = await this.prisma.regressionTestResult.findMany({
      where: { moduleId },
    });

    if (results.length === 0) {
      return {
        totalTests: 0,
        passed: 0,
        failed: 0,
        regressions: 0,
        avgQualityDiff: { clarity: 0, accuracy: 0, completeness: 0 },
      };
    }

    const regressionResults = results.map((record) => this.mapRegressionResult(record));
    const passed = regressionResults.filter((result) => result.passed).length;
    const regressions = regressionResults.filter((result) => result.regressionDetected).length;

    const qualityDiffs = regressionResults.map((result) => this.parseQualityDiff(result.qualityDiff));
    const avgQualityDiff = {
      clarity:
        qualityDiffs.reduce((sum, d) => sum + (d.clarity || 0), 0) / qualityDiffs.length,
      accuracy:
        qualityDiffs.reduce((sum, d) => sum + (d.accuracy || 0), 0) / qualityDiffs.length,
      completeness:
        qualityDiffs.reduce((sum, d) => sum + (d.completeness || 0), 0) / qualityDiffs.length,
    };

    return {
      totalTests: results.length,
      passed,
      failed: results.length - passed,
      regressions,
      avgQualityDiff,
    };
  }

  private mapRegressionResult(record: RegressionTestResultRecord): RegressionTestResult {
    return {
      entryId: record.entryId,
      moduleId: record.moduleId,
      input: record.input,
      actualOutput: record.actualOutput,
      expectedOutput: record.expectedOutput,
      passed: record.passed,
      qualityDiff: record.qualityDiff,
      regressionDetected: record.regressionDetected,
      timestamp: record.timestamp,
    };
  }

  private parseQualityDiff(qualityDiff: string): QualityMetrics {
    try {
      const parsed = JSON.parse(qualityDiff) as Partial<QualityMetrics>;
      return {
        clarity: typeof parsed.clarity === "number" ? parsed.clarity : 0,
        accuracy: typeof parsed.accuracy === "number" ? parsed.accuracy : 0,
        completeness: typeof parsed.completeness === "number" ? parsed.completeness : 0,
      };
    } catch {
      return { clarity: 0, accuracy: 0, completeness: 0 };
    }
  }
}
