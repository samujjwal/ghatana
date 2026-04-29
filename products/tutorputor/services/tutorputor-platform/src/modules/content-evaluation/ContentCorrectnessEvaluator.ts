/**
 * Content Correctness Evaluator
 *
 * Automated validation of AI-generated educational claims against curriculum/citation corpus.
 *
 * @doc.type service
 * @doc.purpose Evaluate correctness of AI-generated educational content
 * @doc.layer platform
 * @doc.pattern Service
 */
import type { PrismaClient } from "@tutorputor/core/db";

export interface Claim {
  id: string;
  text: string;
  type: "fact" | "definition" | "example" | "calculation";
  confidence: number;
}

export interface EvaluationResult {
  claimId: string;
  text: string;
  isCorrect: boolean;
  confidence: number;
  matchedSource: {
    type: "curriculum" | "citation";
    id: string;
    title: string;
    excerpt: string;
  } | null;
  issues: string[] | null;
}

export class ContentCorrectnessEvaluator {
  constructor(private prisma: PrismaClient) {}

  /**
   * Evaluate AI-generated content for correctness
   */
  async evaluateContent(content: string, moduleId: string): Promise<EvaluationResult[]> {
    // Extract claims from content
    const claims = await this.extractClaims(content);
    const results: EvaluationResult[] = [];

    for (const claim of claims) {
      const result = await this.evaluateClaim(claim, moduleId);
      results.push(result);
    }

    return results;
  }

  /**
   * Extract claims from content
   */
  private async extractClaims(content: string): Promise<Claim[]> {
    const claims: Claim[] = [];
    const sentences = content.split(/[.!?]+/).filter((s) => s.trim().length > 0);

    for (let i = 0; i < sentences.length; i++) {
      const sentence = sentences[i]?.trim();
      if (!sentence || sentence.length < 20) continue; // Skip very short sentences

      const claimType = this.classifyClaim(sentence);
      const confidence = this.estimateConfidence(sentence, claimType);

      claims.push({
        id: `claim_${i}`,
        text: sentence,
        type: claimType,
        confidence,
      });
    }

    return claims;
  }

  /**
   * Classify claim type
   */
  private classifyClaim(text: string): Claim["type"] {
    const lower = text.toLowerCase();

    // Check for calculations
    if (/\d+\s*[\+\-\*\/]\s*\d+/.test(text) || /\d+\s*percent/.test(lower)) {
      return "calculation";
    }

    // Check for definitions
    if (lower.includes("is defined as") || lower.includes("means that") || lower.includes("refers to")) {
      return "definition";
    }

    // Check for examples
    if (lower.includes("for example") || lower.includes("such as") || lower.includes("including")) {
      return "example";
    }

    return "fact";
  }

  /**
   * Estimate confidence based on claim characteristics
   */
  private estimateConfidence(text: string, type: Claim["type"]): number {
    let confidence = 0.5;

    // Increase confidence for claims with citations
    if (/\[.*?\]/.test(text)) {
      confidence += 0.2;
    }

    // Increase confidence for claims with numbers
    if (/\d+/.test(text)) {
      confidence += 0.1;
    }

    // Decrease confidence for speculative language
    if (/\b(might|could|may|possibly|probably)\b/.test(text.toLowerCase())) {
      confidence -= 0.2;
    }

    // Adjust based on claim type
    if (type === "calculation") {
      confidence += 0.1;
    }

    return Math.min(Math.max(confidence, 0), 1);
  }

  /**
   * Evaluate a single claim against curriculum and citations
   */
  private async evaluateClaim(claim: Claim, moduleId: string): Promise<EvaluationResult> {
    const issues: string[] = [];
    let isCorrect = false;
    let matchedSource: EvaluationResult["matchedSource"] | undefined;

    // Check against curriculum content
    const curriculumMatch = await this.searchCurriculum(claim.text, moduleId);
    if (curriculumMatch) {
      matchedSource = {
        type: "curriculum",
        id: curriculumMatch.id,
        title: curriculumMatch.title,
        excerpt: curriculumMatch.excerpt,
      };
      isCorrect = true;
    } else {
      // Check against citations
      const citationMatch = await this.searchCitations(claim.text, moduleId);
      if (citationMatch) {
        matchedSource = {
          type: "citation",
          id: citationMatch.id,
          title: citationMatch.title,
          excerpt: citationMatch.excerpt,
        };
        isCorrect = true;
      } else {
        issues.push("No matching source found in curriculum or citations");
      }
    }

    // Check for common factual errors
    const factualErrors = this.checkFactualErrors(claim.text);
    if (factualErrors.length > 0) {
      issues.push(...factualErrors);
      isCorrect = false;
    }

    return {
      claimId: claim.id,
      text: claim.text,
      isCorrect,
      confidence: claim.confidence,
      matchedSource: matchedSource || null,
      issues: issues.length > 0 ? issues : null,
    };
  }

  /**
   * Search curriculum for matching content
   */
  private async searchCurriculum(text: string, moduleId: string): Promise<{
    id: string;
    title: string;
    excerpt: string;
  } | null> {
    try {
      // Search module content blocks for matching content
      const contentBlocks = await this.prisma.moduleContentBlock.findMany({
        where: {
          moduleId,
          deletedAt: null,
        },
        select: {
          id: true,
          title: true,
          content: true,
        },
        take: 10,
      });

      // Search learning objectives for matching content
      const learningObjectives = await this.prisma.moduleLearningObjective.findMany({
        where: {
          moduleId,
        },
        select: {
          id: true,
          label: true,
        },
        take: 10,
      });

      // Combine and search for best match using text similarity
      const candidates = [
        ...contentBlocks.map((block) => ({
          id: block.id,
          title: "Content Block",
          excerpt: typeof block.payload === "string" ? block.payload : JSON.stringify(block.payload),
        })),
        ...learningObjectives.map((obj) => ({
          id: String(obj.id),
          title: "Learning Objective",
          excerpt: obj.label,
        })),
      ];

      // Find best match by text similarity
      let bestMatch: { id: string; title: string; excerpt: string; similarity: number } | null = null;
      const similarityThreshold = 0.3;

      for (const candidate of candidates) {
        const similarity = this.textSimilarity(text, candidate.excerpt);
        if (similarity > similarityThreshold && (!bestMatch || similarity > bestMatch.similarity)) {
          bestMatch = { ...candidate, similarity };
        }
      }

      return bestMatch ? { id: bestMatch.id, title: bestMatch.title, excerpt: bestMatch.excerpt.slice(0, 500) } : null;
    } catch (error) {
      // Log error but don't fail the evaluation
      console.error("Failed to search curriculum:", error);
      return null;
    }
  }

  /**
   * Search citations for matching content
   */
  private async searchCitations(text: string, moduleId: string): Promise<{
    id: string;
    title: string;
    excerpt: string;
  } | null> {
    try {
      // Search for evidence bundles related to the module
      // Note: EvidenceBundleMetadata doesn't have direct moduleId, so we search all
      // and filter by relevance. In a real system, this would use a proper search index.
      const evidenceBundles = await this.prisma.evidenceBundleMetadata.findMany({
        take: 20,
        orderBy: {
          generatedAt: "desc",
        },
      });

      // For each evidence bundle, try to find associated evidence items
      // This is a simplified approach - in production, use proper search index
      const candidates: Array<{ id: string; title: string; excerpt: string }> = [];

      for (const bundle of evidenceBundles) {
        // Search for any evidence items that might contain matching text
        // This would be more efficient with a full-text search index
        const bundleId = bundle.id;
        const similarity = this.textSimilarity(text, JSON.stringify(bundle));

        if (similarity > 0.2) {
          candidates.push({
            id: bundleId,
            title: `Evidence Bundle (${bundle.primarySourceTypes.join(", ")})`,
            excerpt: `Confidence: ${bundle.bundleConfidence.toFixed(2)}, Coverage: ${bundle.coverageScore.toFixed(2)}`,
          });
        }
      }

      // Find best match
      let bestMatch: { id: string; title: string; excerpt: string; similarity: number } | null = null;
      const similarityThreshold = 0.25;

      for (const candidate of candidates) {
        const similarity = this.textSimilarity(text, candidate.excerpt);
        if (similarity > similarityThreshold && (!bestMatch || similarity > bestMatch.similarity)) {
          bestMatch = { ...candidate, similarity };
        }
      }

      return bestMatch ? { id: bestMatch.id, title: bestMatch.title, excerpt: bestMatch.excerpt.slice(0, 500) } : null;
    } catch (error) {
      // Log error but don't fail the evaluation
      console.error("Failed to search citations:", error);
      return null;
    }
  }

  /**
   * Check for common factual errors
   */
  private checkFactualErrors(text: string): string[] {
    const errors: string[] = [];
    const lower = text.toLowerCase();

    // Check for mathematical impossibilities
    if (/(\d+)\s*>\s*\1/.test(text)) {
      errors.push("Mathematical impossibility detected");
    }

    // Check for contradictory statements
    if (/\b(both|either)\b.*\b(neither|nor)\b/.test(lower)) {
      errors.push("Contradictory statement detected");
    }

    // Check for absolute statements that are rarely correct
    if (/\b(always|never|every|all|none)\b/.test(lower) && !/\b(except|unless)\b/.test(lower)) {
      errors.push("Absolute statement may need qualification");
    }

    return errors;
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
   * Get evaluation statistics for a module
   */
  async getModuleEvaluationStats(moduleId: string): Promise<{
    totalClaims: number;
    correctClaims: number;
    incorrectClaims: number;
    accuracy: number;
    averageConfidence: number;
  }> {
    try {
      // Query content evaluations for this module
      const evaluations = await this.prisma.contentEvaluation.findMany({
        where: {
          moduleId,
        },
        select: {
          isCorrect: true,
          confidence: true,
        },
      });

      const totalClaims = evaluations.length;
      const correctClaims = evaluations.filter((e) => e.isCorrect).length;
      const incorrectClaims = totalClaims - correctClaims;
      const accuracy = totalClaims > 0 ? correctClaims / totalClaims : 0;
      const averageConfidence = totalClaims > 0
        ? evaluations.reduce((sum, e) => sum + (e.confidence || 0), 0) / totalClaims
        : 0;

      return {
        totalClaims,
        correctClaims,
        incorrectClaims,
        accuracy,
        averageConfidence,
      };
    } catch (error) {
      console.error("Failed to get module evaluation stats:", error);
      return {
        totalClaims: 0,
        correctClaims: 0,
        incorrectClaims: 0,
        accuracy: 0,
        averageConfidence: 0,
      };
    }
  }
}
