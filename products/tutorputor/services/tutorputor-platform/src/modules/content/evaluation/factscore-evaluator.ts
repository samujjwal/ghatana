/**
 * FActScore Evaluator
 *
 * Task 4.2: Implement FActScore Evaluation
 *
 * Based on FActScore methodology (Min et al., 2023)
 *
 * @doc.type module
 * @doc.purpose FActScore-style fact verification
 * @doc.layer service
 * @doc.pattern Evaluator
 */

import type { Logger } from 'pino';
import type { EvidenceBundle, LearningEvidence } from '../../knowledge-base/evidence-bundle';

/**
 * Atomic fact extracted from content.
 */
export interface AtomicFact {
  factId: string;
  statement: string;
  sourceSpan: [number, number];
  entities: string[];
  claimRef?: string;
}

/**
 * FActScore evaluation result.
 */
export interface FActScoreResult {
  precision: number; // 0-1, % of facts supported by evidence
  numFacts: number;
  supportedFacts: AtomicFact[];
  unsupportedFacts: AtomicFact[];
  contradictingFacts: AtomicFact[];
  coverage: number; // Evidence coverage of facts
}

/**
 * Service for FActScore evaluation.
 */
export class FActScoreEvaluator {
  constructor(private readonly logger: Logger) {}

  /**
   * Evaluate content using FActScore methodology.
   */
  async evaluate(
    content: string,
    evidenceBundle: EvidenceBundle
  ): Promise<FActScoreResult> {
    this.logger.info({ evidenceCount: evidenceBundle.evidences.length }, 'Starting FActScore evaluation');

    // Step 1: Extract atomic facts
    const facts = await this.extractFacts(content);

    // Step 2: Verify each fact against evidence
    const supported: AtomicFact[] = [];
    const unsupported: AtomicFact[] = [];
    const contradicting: AtomicFact[] = [];

    for (const fact of facts) {
      const verification = await this.verifyFact(fact, evidenceBundle);

      if (verification.isSupported) {
        supported.push(fact);
      } else if (verification.isContradicted) {
        contradicting.push(fact);
      } else {
        unsupported.push(fact);
      }
    }

    // Calculate precision
    const numFacts = facts.length;
    const precision = numFacts > 0 ? supported.length / numFacts : 0;

    // Calculate coverage (how many facts have any evidence mention)
    const coveredFacts = facts.filter(f =>
      evidenceBundle.evidences.some(e =>
        this.hasOverlap(f.statement, e.excerpt || '')
      )
    );
    const coverage = numFacts > 0 ? coveredFacts.length / numFacts : 0;

    this.logger.info({ precision, numFacts, supported: supported.length }, 'FActScore evaluation complete');

    return {
      precision: Math.round(precision * 100) / 100,
      numFacts,
      supportedFacts: supported,
      unsupportedFacts: unsupported,
      contradictingFacts: contradicting,
      coverage: Math.round(coverage * 100) / 100,
    };
  }

  /**
   * Extract atomic facts from content.
   * In production, this would use an LLM with structured output.
   */
  private async extractFacts(content: string): Promise<AtomicFact[]> {
    const facts: AtomicFact[] = [];

    // Simple heuristic: split by sentences and treat each as a potential fact
    const sentences = content.match(/[^.!?]+[.!?]+/g) || [content];

    for (let i = 0; i < sentences.length; i++) {
      const sentence = sentences[i].trim();
      if (sentence.length > 20) { // Skip very short fragments
        // Calculate position in original content
        const startPos = content.indexOf(sentence);
        const endPos = startPos + sentence.length;

        facts.push({
          factId: `fact-${i}`,
          statement: sentence,
          sourceSpan: [startPos, endPos],
          entities: this.extractEntities(sentence),
        });
      }
    }

    return facts;
  }

  /**
   * Extract entities from a fact statement.
   */
  private extractEntities(statement: string): string[] {
    // Simple heuristic: capitalized words are likely entities
    const words = statement.match(/\b[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*\b/g) || [];
    return [...new Set(words)]; // Deduplicate
  }

  /**
   * Verify a fact against evidence.
   */
  private async verifyFact(
    fact: AtomicFact,
    evidenceBundle: EvidenceBundle
  ): Promise<{ isSupported: boolean; isContradicted: boolean; confidence: number }> {
    let bestMatch = { score: 0, evidence: null as LearningEvidence | null };

    // Check semantic similarity to each evidence
    for (const evidence of evidenceBundle.evidences) {
      const similarity = this.calculateSimilarity(fact.statement, evidence.excerpt || '');
      if (similarity > bestMatch.score) {
        bestMatch = { score: similarity, evidence };
      }
    }

    // Check for contradiction
    if (bestMatch.evidence?.supportKind === 'CONTRADICTS' && bestMatch.score > 0.5) {
      return { isSupported: false, isContradicted: true, confidence: bestMatch.score };
    }

    // Threshold for support
    const isSupported = bestMatch.score > 0.6;

    return {
      isSupported,
      isContradicted: false,
      confidence: bestMatch.score,
    };
  }

  /**
   * Calculate semantic similarity between two texts.
   * In production, this would use embeddings.
   */
  private calculateSimilarity(text1: string, text2: string): number {
    const words1 = new Set(text1.toLowerCase().split(/\s+/));
    const words2 = new Set(text2.toLowerCase().split(/\s+/));

    const intersection = new Set([...words1].filter(x => words2.has(x)));
    const union = new Set([...words1, ...words2]);

    return intersection.size / union.size;
  }

  /**
   * Check if two texts have word overlap.
   */
  private hasOverlap(text1: string, text2: string): boolean {
    const words1 = new Set(text1.toLowerCase().split(/\s+/));
    const words2 = new Set(text2.toLowerCase().split(/\s+/));

    const intersection = new Set([...words1].filter(x => words2.has(x)));
    return intersection.size > 2; // At least 3 words in common
  }

  /**
   * Get threshold for pass/fail.
   */
  getPassThreshold(): number {
    return 0.8; // 80% precision required
  }

  /**
   * Check if result passes threshold.
   */
  passes(result: FActScoreResult): boolean {
    return result.precision >= this.getPassThreshold();
  }
}
