/**
 * Modality Validator
 * 
 * Validates that claims have at least one supporting modality before publishing.
 * Enforces the evidence-based learning policy: every claim must have
 * at least one of: example, simulation, or animation.
 * 
 * @doc.type utility
 * @doc.purpose Validate claim modality linkage
 * @doc.layer validation
 * @doc.pattern Utility
 */

import type { PrismaClient } from '@ghatana/tutorputor-db';

export type ModalityType = 'example' | 'simulation' | 'animation';

export interface ModalityCounts {
  examples: number;
  simulations: number;
  animations: number;
}

export interface ModalityValidationResult {
  valid: boolean;
  error?: string;
  modalities: ModalityCounts;
  preferredModality?: ModalityType;
}

export class PublishingError extends Error {
  constructor(
    message: string,
    public readonly details: { claimRef: string; modalities: ModalityCounts }
  ) {
    super(message);
    this.name = 'PublishingError';
  }
}

export class ModalityValidator {
  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Validate that a claim has at least one supporting modality.
   * 
   * @param experienceId - The experience ID
   * @param claimRef - The claim reference (e.g., "C1")
   * @returns Validation result with modality counts and preferred type
   * @throws Never - returns result object with valid flag
   */
  async validateClaimModality(
    experienceId: string,
    claimRef: string
  ): Promise<ModalityValidationResult> {
    const [examples, simulations, animations] = await Promise.all([
      this.prisma.claimExample.count({
        where: { experienceId, claimRef }
      }),
      this.prisma.claimSimulation.count({
        where: { experienceId, claimRef }
      }),
      this.prisma.claimAnimation.count({
        where: { experienceId, claimRef }
      }),
    ]);

    const total = examples + simulations + animations;

    if (total === 0) {
      return {
        valid: false,
        error: `Claim ${claimRef} has no supporting modalities. At least one of: example, simulation, or animation is required.`,
        modalities: { examples, simulations, animations },
      };
    }

    return {
      valid: true,
      preferredModality: this.selectPreferredModality({ examples, simulations, animations }),
      modalities: { examples, simulations, animations },
    };
  }

  /**
   * Validate all claims in an experience before publishing.
   * 
   * @param experienceId - The experience ID to validate
   * @throws PublishingError if any claim lacks modalities
   */
  async validateExperienceForPublishing(experienceId: string): Promise<void> {
    const claims = await this.prisma.learningClaim.findMany({
      where: { experienceId },
      select: { claimRef: true, text: true }
    });

    if (claims.length === 0) {
      throw new PublishingError(
        'Cannot publish experience: No claims found',
        { claimRef: 'N/A', modalities: { examples: 0, simulations: 0, animations: 0 } }
      );
    }

    const failures: Array<{ claimRef: string; error: string }> = [];

    for (const claim of claims) {
      const validation = await this.validateClaimModality(experienceId, claim.claimRef);
      
      if (!validation.valid) {
        failures.push({
          claimRef: claim.claimRef,
          error: validation.error!,
        });
      }
    }

    if (failures.length > 0) {
      const failureList = failures.map(f => `  - ${f.claimRef}: ${f.error}`).join('\n');
      const firstFailure = failures[0]!;
      throw new PublishingError(
        `Cannot publish experience: ${failures.length} claim(s) lack supporting modalities:\n${failureList}`,
        { claimRef: firstFailure.claimRef, modalities: { examples: 0, simulations: 0, animations: 0 } }
      );
    }
  }

  /**
   * Select the preferred modality based on priority order:
   * 1. Simulation (highest priority)
   * 2. Animation (medium priority)
   * 3. Example (lowest priority)
   * 
   * @param counts - Modality counts
   * @returns The preferred modality type
   */
  selectPreferredModality(counts: ModalityCounts): ModalityType {
    // Priority: simulation > animation > example
    if (counts.simulations > 0) return 'simulation';
    if (counts.animations > 0) return 'animation';
    return 'example';
  }

  /**
   * Get detailed modality information for a claim.
   * 
   * @param experienceId - The experience ID
   * @param claimRef - The claim reference
   * @returns Detailed modality information
   */
  async getClaimModalityDetails(
    experienceId: string,
    claimRef: string
  ): Promise<{
    examples: Array<{ id: string; type: string; title: string }>;
    simulations: Array<{ id: string; interactionType: string; goal: string }>;
    animations: Array<{ id: string; type: string; duration: number }>;
    total: number;
  }> {
    const [examples, simulations, animations] = await Promise.all([
      this.prisma.claimExample.findMany({
        where: { experienceId, claimRef },
        select: { id: true, type: true, title: true }
      }),
      this.prisma.claimSimulation.findMany({
        where: { experienceId, claimRef },
        select: { id: true, interactionType: true, goal: true }
      }),
      this.prisma.claimAnimation.findMany({
        where: { experienceId, claimRef },
        select: { id: true, type: true, duration: true }
      }),
    ]);

    return {
      examples,
      simulations,
      animations,
      total: examples.length + simulations.length + animations.length,
    };
  }
}
