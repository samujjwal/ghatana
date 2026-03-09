/**
 * Modality Selector
 * 
 * Selects the best modality for a claim based on priority order:
 * 1. Simulation (highest priority)
 * 2. Animation (medium priority)  
 * 3. Example (lowest priority)
 * 
 * This enforces the evidence-based learning policy for content delivery.
 * 
 * @doc.type utility
 * @doc.purpose Select preferred modality based on priority
 * @doc.layer business-logic
 * @doc.pattern Utility
 */

import type { PrismaClient } from '@ghatana/tutorputor-db';

export type ModalityType = 'example' | 'simulation' | 'animation';

export interface ModalityAvailability {
  hasSimulation: boolean;
  hasAnimation: boolean;
  hasExample: boolean;
  simulations: Array<{
    id: string;
    simulationManifestId: string;
    interactionType: string;
    goal: string;
    simulationManifest: {
      id: string;
      title: string;
      manifest: any;
    } | null;
  }>;
  animations: Array<{
    id: string;
    type: string;
    title: string;
    duration: number;
    config: any;
  }>;
  examples: Array<{
    id: string;
    type: string;
    title: string;
    content: any;
  }>;
}

export interface ModalitySelectionResult {
  selectedModality: ModalityType | null;
  availableModalities: ModalityType[];
  fallbackUsed: boolean;
  details: {
    simulation?: ModalityAvailability['simulations'][0];
    animation?: ModalityAvailability['animations'][0];
    example?: ModalityAvailability['examples'][0];
  };
}

export class ModalitySelector {
  // Priority order: simulation (highest) > animation > example (lowest)
  private readonly priorityOrder: ModalityType[] = ['simulation', 'animation', 'example'];

  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Get all available modalities for a claim.
   * 
   * @param experienceId - The experience ID
   * @param claimRef - The claim reference
   * @returns Availability information for all modalities
   */
  async getAvailableModalities(
    experienceId: string,
    claimRef: string
  ): Promise<ModalityAvailability> {
    const [examples, simulations, animations] = await Promise.all([
      this.prisma.claimExample.findMany({
        where: { experienceId, claimRef },
        select: {
          id: true,
          type: true,
          title: true,
          content: true,
        },
        orderBy: { orderIndex: 'asc' },
      }),
      this.prisma.claimSimulation.findMany({
        where: { experienceId, claimRef },
        include: {
          simulationManifest: {
            select: {
              id: true,
              title: true,
              manifest: true,
            },
          },
        },
        orderBy: { createdAt: 'asc' },
      }),
      this.prisma.claimAnimation.findMany({
        where: { experienceId, claimRef },
        select: {
          id: true,
          type: true,
          title: true,
          duration: true,
          config: true,
        },
        orderBy: { createdAt: 'asc' },
      }),
    ]);

    // Only count simulations that have valid manifests
    const validSimulations = simulations.filter(
      (s: typeof simulations[0]) => s.simulationManifest !== null
    );

    return {
      hasSimulation: validSimulations.length > 0,
      hasAnimation: animations.length > 0,
      hasExample: examples.length > 0,
      simulations: validSimulations,
      animations,
      examples,
    };
  }

  /**
   * Select the best available modality based on priority order.
   * 
   * @param available - Availability information
   * @returns The selected modality type or null if none available
   */
  selectBestModality(available: ModalityAvailability): ModalityType | null {
    for (const modality of this.priorityOrder) {
      const hasKey = `has${this.capitalize(modality)}` as keyof ModalityAvailability;
      if (available[hasKey as keyof Pick<ModalityAvailability, 'hasSimulation' | 'hasAnimation' | 'hasExample'>]) {
        return modality;
      }
    }
    return null;
  }

  /**
   * Get full selection result with details.
   * 
   * @param experienceId - The experience ID
   * @param claimRef - The claim reference
   * @returns Complete selection result
   */
  async selectModalityForClaim(
    experienceId: string,
    claimRef: string
  ): Promise<ModalitySelectionResult> {
    const available = await this.getAvailableModalities(experienceId, claimRef);
    
    const availableModalities = this.priorityOrder.filter(
      m => available[`has${this.capitalize(m)}` as keyof Pick<ModalityAvailability, 'hasSimulation' | 'hasAnimation' | 'hasExample'>]
    );

    const selectedModality = this.selectBestModality(available);
    const fallbackUsed = availableModalities.length > 0 && 
                         selectedModality !== availableModalities[0];

    return {
      selectedModality,
      availableModalities,
      fallbackUsed,
      details: {
        simulation: available.simulations[0],
        animation: available.animations[0],
        example: available.examples[0],
      },
    };
  }

  /**
   * Get all modalities in priority order.
   * 
   * @returns Array of modalities in priority order
   */
  getPriorityOrder(): ModalityType[] {
    return [...this.priorityOrder];
  }

  /**
   * Check if a specific modality is available.
   * 
   * @param available - Availability information
   * @param modality - The modality to check
   * @returns True if available
   */
  isModalityAvailable(
    available: ModalityAvailability,
    modality: ModalityType
  ): boolean {
    const key = `has${this.capitalize(modality)}` as keyof Pick<ModalityAvailability, 'hasSimulation' | 'hasAnimation' | 'hasExample'>;
    return available[key];
  }

  /**
   * Get the priority rank of a modality (lower is higher priority).
   * 
   * @param modality - The modality to rank
   * @returns The priority rank (0 = highest)
   */
  getModalityPriority(modality: ModalityType): number {
    return this.priorityOrder.indexOf(modality);
  }

  /**
   * Compare two modalities by priority.
   * 
   * @param a - First modality
   * @param b - Second modality
   * @returns Negative if a higher priority, positive if b higher priority
   */
  compareModalityPriority(a: ModalityType, b: ModalityType): number {
    return this.getModalityPriority(a) - this.getModalityPriority(b);
  }

  /**
   * Get the next fallback modality if the preferred one is not available.
   * 
   * @param preferred - The preferred modality
   * @param available - Availability information
   * @returns The fallback modality or null
   */
  getFallbackModality(
    preferred: ModalityType,
    available: ModalityAvailability
  ): ModalityType | null {
    const preferredIndex = this.getModalityPriority(preferred);
    
    for (let i = preferredIndex + 1; i < this.priorityOrder.length; i++) {
      const modality = this.priorityOrder[i];
      if (modality && this.isModalityAvailable(available, modality)) {
        return modality;
      }
    }
    
    return null;
  }

  private capitalize(str: string): string {
    return str.charAt(0).toUpperCase() + str.slice(1);
  }
}
