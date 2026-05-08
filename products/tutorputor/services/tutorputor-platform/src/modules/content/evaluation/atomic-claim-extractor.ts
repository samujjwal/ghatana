/**
 * Atomic Claim Extractor
 *
 * Extracts atomic factual claims from generated content for granular validation.
 * Each atomic claim can be independently validated against evidence bundles.
 *
 * @doc.type service
 * @doc.purpose Extract atomic factual claims from content
 * @doc.layer product
 * @doc.pattern Service
 */

import type { Logger } from "pino";

export interface AtomicClaim {
  id: string;
  text: string;
  claimType: "fact" | "definition" | "procedure" | "relationship" | "quantitative";
  confidence: number; // 0.0 - 1.0, how confidently this is a claim
  span: {
    start: number;
    end: number;
  };
}

export interface AtomicClaimExtractionResult {
  claims: AtomicClaim[];
  totalClaims: number;
  extractionConfidence: number;
}

/**
 * Extracts atomic factual claims from content using heuristics and NLP patterns.
 * In production, this would use an LLM or specialized NLP model for better accuracy.
 */
export class AtomicClaimExtractor {
  constructor(private readonly logger: Logger) {}

  async extractFromContent(content: string): Promise<AtomicClaimExtractionResult> {
    const claims: AtomicClaim[] = [];
    const sentences = this.splitIntoSentences(content);

    for (let i = 0; i < sentences.length; i++) {
      const sentence = sentences[i];
      if (!sentence) continue;

      const start = content.indexOf(sentence);
      const end = start + sentence.length;

      // Skip very short sentences or questions
      if (sentence.length < 10 || sentence.trim().endsWith("?")) {
        continue;
      }

      const claimType = this.classifyClaimType(sentence);
      const confidence = this.calculateClaimConfidence(sentence, claimType);

      if (confidence > 0.5) {
        claims.push({
          id: `claim-${i}-${Date.now()}`,
          text: sentence.trim(),
          claimType,
          confidence,
          span: { start, end },
        });
      }
    }

    const extractionConfidence = this.calculateExtractionConfidence(claims, sentences.length);

    this.logger.info(
      {
        totalSentences: sentences.length,
        extractedClaims: claims.length,
        extractionConfidence,
      },
      "Atomic claim extraction completed",
    );

    return {
      claims,
      totalClaims: claims.length,
      extractionConfidence,
    };
  }

  private splitIntoSentences(content: string): string[] {
    // Simple sentence splitting - in production use NLP library
    return content
      .split(/[.!?]+/)
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
  }

  private classifyClaimType(sentence: string): AtomicClaim["claimType"] {
    const lower = sentence.toLowerCase();

    // Quantitative claims contain numbers or measurements
    if (/\d+/.test(sentence) || /equal|greater|less|than|percent|ratio|rate/.test(lower)) {
      return "quantitative";
    }

    // Procedure claims describe steps or processes
    if (/step|process|method|procedure|first|then|next|finally/.test(lower)) {
      return "procedure";
    }

    // Relationship claims describe connections between concepts
    if (/because|therefore|thus|consequently|leads to|causes|results in/.test(lower)) {
      return "relationship";
    }

    // Definition claims define terms
    if (/is defined as|means|refers to|is a|is an/.test(lower)) {
      return "definition";
    }

    // Default to factual claim
    return "fact";
  }

  private calculateClaimConfidence(
    sentence: string,
    claimType: AtomicClaim["claimType"],
  ): number {
    let confidence = 0.5;

    // Increase confidence for well-structured sentences
    if (/^[A-Z]/.test(sentence) && /[.!?]$/.test(sentence.trim())) {
      confidence += 0.2;
    }

    // Increase confidence for claim types that are easier to validate
    if (claimType === "quantitative" || claimType === "definition") {
      confidence += 0.15;
    }

    // Decrease confidence for subjective language
    if (/might|could|possibly|perhaps|maybe|seems/.test(sentence.toLowerCase())) {
      confidence -= 0.2;
    }

    return Math.max(0, Math.min(1, confidence));
  }

  private calculateExtractionConfidence(
    claims: AtomicClaim[],
    totalSentences: number,
  ): number {
    if (totalSentences === 0) return 0;

    const claimRatio = claims.length / totalSentences;
    const avgClaimConfidence =
      claims.reduce((sum, claim) => sum + claim.confidence, 0) / claims.length;

    // Extraction confidence is based on claim ratio and average claim confidence
    return Math.max(0, Math.min(1, claimRatio * avgClaimConfidence));
  }
}
