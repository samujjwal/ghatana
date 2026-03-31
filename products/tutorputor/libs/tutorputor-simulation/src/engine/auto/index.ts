/**
 * Automatic Simulation Generation Service - Compatibility Layer
 * 
 * This module re-exports from auto-retired.ts and provides the AutoSimulationService.
 * The raw preset bag has been extracted to auto-retired.ts for cleaner architecture.
 * 
 * @deprecated Use starter-catalog.ts for new simulation authoring
 * @doc.type module
 * @doc.purpose Backward compatibility and auto-generation service
 * @doc.layer legacy
 */

import type {
  SimulationManifest,
  SimEntityBase,
} from '@tutorputor/contracts/v1/simulation';

import {
  getSimulationStarterByLegacyPresetId,
  listSimulationStarters,
  type SimulationStarter,
} from '../starter-catalog';
import { createSimulationStarterManifest } from '../starter-packaging';

// Import legacy types and presets from extracted module
import {
  type AutoSimulationRequest,
  type AutoSimulationResult,
  type SimulationPreset,
  type LegacyAutoRuntimeStatus,
  type LegacyAutoRuntimePresetSummary,
  type LegacyAutoRuntimeSummary,
  SimulationPresets,
  getSimulationPresetById,
  listLegacyRuntimePresetSummaries,
  getLegacyAutoRuntimeSummary,
} from '../auto-retired';

type LegacyTemplate = AutoSimulationResult['template'];
type LegacyTemplateParameter = LegacyTemplate['parameters'][number];

// Re-export all legacy types and functions
export {
  type AutoSimulationRequest,
  type AutoSimulationResult,
  type SimulationPreset,
  type LegacyAutoRuntimeStatus,
  type LegacyAutoRuntimePresetSummary,
  type LegacyAutoRuntimeSummary,
  SimulationPresets,
  getSimulationPresetById,
  listLegacyRuntimePresetSummaries,
  getLegacyAutoRuntimeSummary,
} from '../auto-retired';

// =============================================================================
// Helper Functions
// =============================================================================

function toStarterBackedPreset(starter: SimulationStarter): SimulationPreset {
  return {
    id: starter.legacyPresetIds[0] ?? starter.id,
    name: starter.name,
    description: starter.summary,
    domain: toAutoDomainFromStarter(starter.domain),
    manifest: starter.manifest,
    educationalNotes: starter.summary,
  };
}

function toAutoDomainFromStarter(domain: SimulationStarter['domain']): AutoSimulationRequest['domain'] {
  switch (domain) {
    case 'PHYSICS':
      return 'physics';
    case 'CHEMISTRY':
      return 'chemistry';
    case 'BIOLOGY':
      return 'biology';
    case 'MEDICINE':
      return 'medicine';
    case 'CS_DISCRETE':
      return 'cs';
    default:
      return 'math';
  }
}

function dedupePresetList(presets: SimulationPreset[]): SimulationPreset[] {
  const seen = new Set<string>();
  const items: SimulationPreset[] = [];
  for (const preset of presets) {
    if (seen.has(preset.id)) {
      continue;
    }
    seen.add(preset.id);
    items.push(preset);
  }
  return items;
}

function isGovernedStarterAvailable(preset: SimulationPreset): boolean {
  return getSimulationStarterByLegacyPresetId(preset.id) !== null;
}

function listLegacyCompatibilityFallbacks(
  domain?: AutoSimulationRequest['domain'],
): SimulationPreset[] {
  return SimulationPresets.filter((preset) => !isGovernedStarterAvailable(preset)).filter(
    (preset) => (domain ? preset.domain === domain : true),
  );
}

// =============================================================================
// Automatic Simulation Generator
// =============================================================================

export class AutoSimulationService {
  /**
   * Generate simulation from natural language description
   */
  async generateFromDescription(request: AutoSimulationRequest): Promise<AutoSimulationResult> {
    const starter = this.selectStarter(request);
    const preset = starter
      ? toStarterBackedPreset(starter)
      : this.selectLegacyPreset(request);
    const manifest = starter
      ? this.customizeStarterManifest(starter, request)
      : this.customizePreset(preset, request);
    const educational = this.generateEducationalContent(preset, request);
    const confidence = starter
      ? Math.min(0.98, this.calculateConfidence(request, preset) + 0.1)
      : this.calculateConfidence(request, preset);
    
    return {
      manifest,
      template: this.convertToTemplate(manifest),
      explanation: this.generateExplanation(preset, request),
      narration: this.generateNarration(preset, request),
      educational,
      confidence,
    };
  }

  /**
   * Generate simulation from learning objective
   */
  async generateForLearning(
    learningObjective: string,
    domain: AutoSimulationRequest['domain'],
    difficulty?: 'beginner' | 'intermediate' | 'advanced'
  ): Promise<AutoSimulationResult> {
    const request: AutoSimulationRequest = {
      description: learningObjective,
      domain,
      learningObjective,
    };
    if (difficulty) {
      request.difficulty = difficulty;
    }
    return this.generateFromDescription(request);
  }

  /**
   * Search presets by query
   */
  searchPresets(query: string): SimulationPreset[] {
    const lower = query.toLowerCase();
    const starterBacked = listSimulationStarters({
      query,
    }).map(toStarterBackedPreset);
    const rawFallbacks = listLegacyCompatibilityFallbacks().filter(
      (p) =>
        p.name.toLowerCase().includes(lower) ||
        p.description.toLowerCase().includes(lower) ||
        p.domain.toLowerCase().includes(lower)
    );
    return dedupePresetList([...starterBacked, ...rawFallbacks]);
  }

  /**
   * Get presets by domain
   */
  getPresetsByDomain(domain: string): SimulationPreset[] {
    const starterBacked = listSimulationStarters().filter(
      (starter) => toAutoDomainFromStarter(starter.domain) === domain,
    ).map(toStarterBackedPreset);
    const rawFallbacks = listLegacyCompatibilityFallbacks(
      domain as AutoSimulationRequest['domain'],
    );
    return dedupePresetList([...starterBacked, ...rawFallbacks]);
  }

  // =============================================================================
  // Private Methods
  // =============================================================================

  private selectStarter(request: AutoSimulationRequest): SimulationStarter | null {
    const starters = listSimulationStarters({
      ...(request.audience ? { audience: request.audience } : {}),
      ...(request.description ? { query: request.description } : {}),
    }).filter((starter) => toAutoDomainFromStarter(starter.domain) === request.domain);

    if (starters.length > 0) {
      return starters[0] ?? null;
    }

    const aliasedStarter = listSimulationStarters().find((starter) =>
      starter.legacyPresetIds.some((legacyId) =>
        this.extractKeywords(request.description ?? '').some((keyword) =>
          legacyId.toLowerCase().includes(keyword) ||
          starter.name.toLowerCase().includes(keyword),
        ),
      ) &&
      toAutoDomainFromStarter(starter.domain) === request.domain,
    );

    return aliasedStarter ?? null;
  }

  private selectLegacyPreset(request: AutoSimulationRequest): SimulationPreset {
    // Filter by domain
    let candidates = listLegacyCompatibilityFallbacks(request.domain);
    if (candidates.length === 0) {
      candidates = SimulationPresets.filter((p) => p.domain === request.domain);
    }
    
    // Filter by difficulty if specified
    const requestedDifficulty = request.difficulty;
    if (requestedDifficulty) {
      candidates = candidates.filter((p) => this.matchesDifficulty(p, requestedDifficulty));
    }
    
    // Match by concepts/keywords in description
    if (request.description) {
      const keywords = this.extractKeywords(request.description);
      const scored = candidates.map((p) => ({
        preset: p,
        score: this.scorePresetByKeywords(p, keywords),
      }));
      scored.sort((a, b) => b.score - a.score);
      
      if (scored.length > 0 && scored[0]!.score > 0) {
        return scored[0]!.preset;
      }
    }
    
    // Return first from domain or default
    return candidates[0] || SimulationPresets[0]!;
  }

  private customizeStarterManifest(
    starter: SimulationStarter,
    request: AutoSimulationRequest,
  ): SimulationManifest {
    const manifest =
      createSimulationStarterManifest({
        starterRef: starter.id,
        title: request.learningObjective ?? starter.name,
      }) ?? structuredClone(starter.manifest as unknown as Record<string, unknown>);

    if (request.description) {
      (manifest as Record<string, unknown>).description = request.description;
    }

    return this.customizeManifestShape(manifest as SimulationManifest, request);
  }

  private customizePreset(
    preset: SimulationPreset,
    request: AutoSimulationRequest
  ): SimulationManifest {
    // Deep clone the manifest
    const manifest = JSON.parse(JSON.stringify(preset.manifest)) as unknown as SimulationManifest;
    return this.customizeManifestShape(manifest, request);
  }

  private customizeManifestShape(
    manifest: SimulationManifest,
    request: AutoSimulationRequest,
  ): SimulationManifest {
    manifest.id = (manifest.id || `auto-${Date.now()}`) as SimulationManifest['id'];
    manifest.version = manifest.version || '1.0.0';
    manifest.createdAt = manifest.createdAt || new Date().toISOString();
    manifest.updatedAt = new Date().toISOString();

    const entities = Array.isArray((manifest as { entities?: SimEntityBase[] }).entities)
      ? ([...(manifest as { entities?: SimEntityBase[] }).entities!] as SimEntityBase[])
      : [];
    const steps = Array.isArray(manifest.steps) ? [...manifest.steps] : [];

    if (request.entityCount) {
      const currentCount = entities.length;
      if (request.entityCount > currentCount) {
        for (let i = currentCount; i < request.entityCount; i++) {
          entities.push(this.createGenericEntity(i));
        }
      } else if (request.entityCount < currentCount) {
        entities.splice(request.entityCount);
      }
    }

    if (request.duration && steps.length > 0) {
      const totalCurrentDuration = steps.reduce((sum, step) => sum + (step.duration || 0), 0);
      const scaleFactor = totalCurrentDuration > 0
        ? (request.duration * 1000) / totalCurrentDuration
        : 1;
      manifest.steps = steps.map((step) => ({
        ...step,
        duration: Math.round((step.duration || 1000) * scaleFactor),
      }));
    } else {
      manifest.steps = steps;
    }

    (manifest as unknown as { entities?: SimEntityBase[] }).entities = entities;
    return manifest;
  }

  private generateEducationalContent(
    preset: SimulationPreset,
    request: AutoSimulationRequest
  ): AutoSimulationResult['educational'] {
    const concepts = this.extractConcepts(preset, request);
    
    return {
      concepts,
      prerequisites: this.inferPrerequisites(concepts),
      followUpQuestions: this.generateQuestions(concepts),
      commonMisconceptions: this.getMisconceptions(concepts),
    };
  }

  private generateExplanation(preset: SimulationPreset, request: AutoSimulationRequest): string {
    return `This simulation demonstrates ${preset.name.toLowerCase()}. ${preset.description} ` +
      `It is designed for ${request.audience || 'general'} learners studying ${request.domain}.`;
  }

  private generateNarration(preset: SimulationPreset, request: AutoSimulationRequest): string {
    const manifest = preset.manifest;
    let narration = `Welcome to ${manifest.title}. `;
    
    const manifestSteps = (manifest as { steps?: Array<{ description?: string }> }).steps;
    if (manifestSteps && manifestSteps.length > 0) {
      narration += `This simulation has ${manifestSteps.length} steps. `;
      narration += `First, ${manifestSteps[0]!.description?.toLowerCase()}. `;
      
      if (manifestSteps.length > 1) {
        narration += `Then, ${manifestSteps[1]!.description?.toLowerCase()}. `;
      }
      
      narration += `Watch how the process unfolds.`;
    }
    
    return narration;
  }

  private calculateConfidence(
    request: AutoSimulationRequest,
    preset: SimulationPreset
  ): number {
    let confidence = 0.6;
    
    if (request.domain === preset.domain) confidence += 0.2;
    if (request.description && this.descriptionMatchesPreset(request.description, preset)) {
      confidence += 0.15;
    }
    if (request.learningObjective) confidence += 0.1;
    
    return Math.min(confidence, 0.95);
  }

  private convertToTemplate(manifest: SimulationManifest): LegacyTemplate {
    const manifestEntities = (manifest as { entities?: Array<{
      id: string;
      type?: string;
      x: number;
      y: number;
      properties?: Record<string, unknown>;
      appearance?: Record<string, unknown>;
    }> }).entities;
    
    return {
      id: manifest.id || `template-${Date.now()}`,
      name: manifest.title || 'Generated Simulation',
      description: manifest.description || 'Auto-generated simulation',
      domain: (manifest as { type?: string }).type ?? 'physics',
      difficulty: 'intermediate',
      entities: (manifestEntities || []).map((e) => ({
        id: e.id,
        type: e.type || 'dynamic-body',
        x: e.x,
        y: e.y,
        properties: e.properties || {},
        appearance: e.appearance || {},
      })),
      config: { gravity: 9.8, timeScale: 1, paused: false },
      parameters: this.inferParameters(manifest),
      learningObjectives: ['Understand core concepts', 'Observe system behavior'],
    };
  }

  private matchesDifficulty(preset: SimulationPreset, difficulty: string): boolean {
    // Simple heuristic based on number of entities and steps
    const manifestEntities = (preset.manifest as { entities?: any[] }).entities;
    const manifestSteps = (preset.manifest as { steps?: any[] }).steps;
    const entityCount = manifestEntities?.length || 0;
    const stepCount = manifestSteps?.length || 0;
    
    const complexity = entityCount + stepCount;
    
    if (difficulty === 'beginner') return complexity < 5;
    if (difficulty === 'intermediate') return complexity >= 5 && complexity < 10;
    if (difficulty === 'advanced') return complexity >= 10;
    
    return true;
  }

  private extractKeywords(description: string): string[] {
    const stopWords = ['the', 'a', 'an', 'and', 'or', 'in', 'on', 'at', 'to', 'for', 'of'];
    return description
      .toLowerCase()
      .split(/\s+/)
      .filter((word) => word.length > 3 && !stopWords.includes(word));
  }

  private scorePresetByKeywords(preset: SimulationPreset, keywords: string[]): number {
    const text = `${preset.name} ${preset.description}`.toLowerCase();
    return keywords.filter((kw) => text.includes(kw)).length;
  }

  private createGenericEntity(index: number): SimEntityBase {
    return {
      id: `entity-${index}` as SimEntityBase['id'],
      type: 'dynamic-body',
      x: 100 + Math.random() * 600,
      y: 100 + Math.random() * 400,
      properties: { radius: 20, mass: 1 },
      appearance: { fillColor: '#4ecdc4' },
    } as SimEntityBase;
  }

  private extractConcepts(
    preset: SimulationPreset,
    request: AutoSimulationRequest
  ): string[] {
    const concepts: string[] = [];
    
    // Add from preset
    concepts.push(preset.name);
    
    // Add from domain
    if (request.domain === 'physics') {
      concepts.push('mechanics', 'motion', 'forces');
    } else if (request.domain === 'chemistry') {
      concepts.push('reactions', 'molecules', 'bonding');
    } else if (request.domain === 'biology') {
      concepts.push('cells', 'processes', 'systems');
    } else if (request.domain === 'cs') {
      concepts.push('algorithms', 'data structures', 'complexity');
    }
    
    return [...new Set(concepts)];
  }

  private inferPrerequisites(concepts: string[]): string[] {
    const prerequisites: string[] = [];
    
    concepts.forEach((concept) => {
      const lower = concept.toLowerCase();
      if (lower.includes('newton')) {
        prerequisites.push('basic mechanics', 'vectors');
      } else if (lower.includes('algorithm')) {
        prerequisites.push('programming basics', 'time complexity');
      } else if (lower.includes('reaction')) {
        prerequisites.push('atomic structure', 'chemical bonds');
      }
    });
    
    return [...new Set(prerequisites)];
  }

  private generateQuestions(concepts: string[]): string[] {
    return [
      `What would happen if we changed a parameter in this ${concepts[0]} simulation?`,
      `How does this ${concepts[0]} relate to real-world applications?`,
      `What are the limitations of this model?`,
    ];
  }

  private getMisconceptions(concepts: string[]): string[] {
    const misconceptions: string[] = [];
    
    concepts.forEach((concept) => {
      const lower = concept.toLowerCase();
      if (lower.includes('motion')) {
        misconceptions.push('Objects need constant force to maintain motion');
      } else if (lower.includes('equilibrium')) {
        misconceptions.push('Equilibrium means nothing is happening');
      } else if (lower.includes('energy')) {
        misconceptions.push('Energy can be created or destroyed');
      }
    });
    
    return misconceptions;
  }

  private descriptionMatchesPreset(description: string, preset: SimulationPreset): boolean {
    const keywords = this.extractKeywords(description);
    const presetText = `${preset.name} ${preset.description}`.toLowerCase();
    return keywords.some((kw) => presetText.includes(kw));
  }

  private inferParameters(manifest: SimulationManifest): LegacyTemplateParameter[] {
    const params: LegacyTemplateParameter[] = [];
    const manifestEntities = (manifest as { 
      entities?: Array<{
        id: string;
        properties?: { mass?: number };
      }> 
    }).entities;
    
    // Infer from entity properties
    manifestEntities?.forEach((entity) => {
      if (entity.properties?.mass !== undefined) {
        params.push({
          id: `${entity.id}-mass`,
          name: `${entity.id} Mass`,
          type: 'slider',
          defaultValue: entity.properties.mass,
          min: 0.1,
          max: 10,
          step: 0.1,
          description: `Mass of ${entity.id}`,
        });
      }
    });
    
    return params;
  }
}

// =============================================================================
// Export singleton
// =============================================================================

export const autoSimulationService = new AutoSimulationService();
