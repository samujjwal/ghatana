/**
 * Animation Manifest Generator
 *
 * Task 2.7: Implement Animation Manifest Generator
 *
 * @doc.type module
 * @doc.purpose Service for generating declarative animation manifests
 * @doc.layer service
 * @doc.pattern GeneratorService
 */

import type { Logger } from 'pino';
import type { PrismaClient } from '@tutorputor/core/db';
import type {
  AnimationManifest,
  SceneNode,
  AnimatedEntity,
  AnimationSegment,
  CueingRule,
} from '../../../../../contracts/v1/artifact-manifests/animation-manifest';
import type { EvidenceBundle } from '../../knowledge-base/evidence-bundle';
import { ManifestValidator } from '../manifest-validator';

/**
 * Options for animation generation.
 */
export interface AnimationGenerationOptions {
  durationSeconds?: number;
  pacing?: 'slow' | 'medium' | 'fast';
  includeNarration?: boolean;
  complexity?: 'simple' | 'moderate' | 'complex';
  variantKey?: string;
}

/**
 * Result of animation generation.
 */
export interface AnimationGenerationResult {
  manifest: AnimationManifest;
  success: boolean;
  errors: string[];
  warnings: string[];
}

/**
 * Service for generating animation manifests.
 */
export class AnimationManifestGenerator {
  private readonly validator: ManifestValidator;

  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: Logger
  ) {
    this.validator = new ManifestValidator(logger);
  }

  /**
   * Generate an animation manifest for a claim.
   */
  async generate(
    claimRef: string,
    evidenceBundle: EvidenceBundle,
    domain: string,
    gradeBand: string,
    options?: AnimationGenerationOptions
  ): Promise<AnimationGenerationResult> {
    const errors: string[] = [];
    const warnings: string[] = [];

    try {
      // Get claim details
      const claim = await this.prisma.learningClaim.findFirst({
        where: { claimRef },
      });

      if (!claim) {
        return {
          manifest: this.createEmptyManifest(claimRef),
          success: false,
          errors: [`Claim not found: ${claimRef}`],
          warnings: [],
        };
      }

      const duration = options?.durationSeconds ?? 30;
      const pacing = options?.pacing ?? 'medium';
      const complexity = options?.complexity ?? 'moderate';
      const variantKey = options?.variantKey ?? 'primary';

      // Generate scene graph
      const { sceneGraph, entities } = this.generateScene(complexity, domain);

      // Generate animation segments
      const segments = this.generateSegments(duration, pacing, claim.text);

      // Generate cueing rules
      const cueingRules = this.generateCueingRules(segments, entities);

      // Generate pacing metadata
      const pacingMetadata = this.generatePacingMetadata(duration, segments);

      // Generate narration if requested
      const narrationScript = options?.includeNarration !== false
        ? this.generateNarration(segments, claim.text)
        : undefined;

      // Generate claim mapping
      const claimMapping = this.generateClaimMapping(segments, claimRef);

      // Build manifest
      const manifest: AnimationManifest = {
        schemaVersion: '1.0.0',
        manifestType: 'Animation',
        claimRef,
        evidenceRefs: evidenceBundle.evidences.map(e => e.evidenceRef),
        domain,
        gradeBand,
        pedagogicalIntent: `Visualize "${claim.text}" through animation`,
        sceneGraph,
        entities,
        segments,
        totalDurationSeconds: duration,
        cueingRules,
        pacingMetadata,
        narrationScript,
        subtitles: narrationScript ? this.generateSubtitles(narrationScript) : undefined,
        learnerControls: this.generateLearnerControls(),
        claimMapping,
        createdAt: new Date().toISOString(),
        generatedBy: 'AnimationManifestGenerator',
        validationStatus: 'pending',
        variantKey,
      };

      // Validate
      const validation = this.validator.validateAnimation(manifest);
      warnings.push(...validation.warnings.map(w => w.message));

      this.logger.info({ claimRef, duration, variantKey }, 'Animation manifest generated');

      return {
        manifest,
        success: true,
        errors,
        warnings,
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      this.logger.error({ err: error, claimRef }, 'Animation generation failed');

      return {
        manifest: this.createEmptyManifest(claimRef),
        success: false,
        errors: [...errors, message],
        warnings,
      };
    }
  }

  /**
   * Generate scene graph and entities.
   */
  private generateScene(complexity: 'simple' | 'moderate' | 'complex', domain: string): { sceneGraph: SceneNode[]; entities: AnimatedEntity[] } {
    const entityCount = complexity === 'simple' ? 2 : complexity === 'moderate' ? 4 : 6;

    const entities: AnimatedEntity[] = [];
    const sceneGraph: SceneNode[] = [];

    // Create camera
    sceneGraph.push({
      nodeId: 'camera-main',
      type: 'camera',
      transform: {
        position: [0, 0, 10],
        rotation: [0, 0, 0],
        scale: [1, 1, 1],
      },
    });

    // Create entities based on domain
    const entityTypes: Array<'sphere' | 'cube' | 'cylinder' | 'arrow'> = ['sphere', 'cube', 'cylinder', 'arrow'];

    for (let i = 0; i < entityCount; i++) {
      const entityId = `entity-${i + 1}`;
      const type = entityTypes[i % entityTypes.length];

      entities.push({
        entityId,
        name: `${domain} Element ${i + 1}`,
        type,
        initialState: {
          position: [i * 2 - entityCount, 0, 0],
          color: this.getDomainColor(domain, i),
          opacity: 1,
        },
        behavior: {
          movable: complexity !== 'simple',
          interactive: complexity === 'complex',
          physicsEnabled: false,
        },
      });

      sceneGraph.push({
        nodeId: `node-${entityId}`,
        type: 'entity',
        parentId: undefined,
        transform: {
          position: [i * 2 - entityCount, 0, 0],
          rotation: [0, 0, 0],
          scale: [1, 1, 1],
        },
        properties: { entityId },
      });
    }

    return { sceneGraph, entities };
  }

  /**
   * Get a color for a domain.
   */
  private getDomainColor(domain: string, index: number): string {
    const domainColors: Record<string, string[]> = {
      'physics': ['#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4'],
      'chemistry': ['#F38181', '#AA96DA', '#FCBAD3', '#FFFFD2'],
      'biology': ['#2ECC71', '#27AE60', '#16A085', '#1ABC9C'],
      'math': ['#3498DB', '#2980B9', '#9B59B6', '#8E44AD'],
    };

    const colors = domainColors[domain.toLowerCase()] ?? ['#3498DB', '#E74C3C', '#2ECC71', '#F39C12'];
    return colors[index % colors.length];
  }

  /**
   * Generate animation segments.
   */
  private generateSegments(duration: number, pacing: string, claimText: string): AnimationSegment[] {
    const segmentCount = pacing === 'slow' ? 4 : pacing === 'medium' ? 3 : 2;
    const segmentDuration = duration / segmentCount;

    const segments: AnimationSegment[] = [
      {
        segmentId: 'intro',
        startTime: 0,
        endTime: segmentDuration * 0.2,
        description: 'Introduction: Setting up the scenario',
        conceptsIllustrated: ['context', 'setup'],
        pausePoints: [segmentDuration * 0.1],
        speed: 'normal',
      },
      {
        segmentId: 'concept',
        startTime: segmentDuration * 0.2,
        endTime: segmentDuration * 0.7,
        description: `Main concept: ${claimText}`,
        conceptsIllustrated: ['core concept', 'relationships'],
        pausePoints: [segmentDuration * 0.4, segmentDuration * 0.6],
        speed: pacing,
      },
      {
        segmentId: 'conclusion',
        startTime: segmentDuration * 0.7,
        endTime: duration,
        description: 'Conclusion: Key takeaways',
        conceptsIllustrated: ['summary', 'implications'],
        pausePoints: [duration - 2],
        speed: 'normal',
      },
    ];

    return segments.slice(0, segmentCount);
  }

  /**
   * Generate cueing rules.
   */
  private generateCueingRules(segments: AnimationSegment[], entities: AnimatedEntity[]): CueingRule[] {
    const rules: CueingRule[] = [];

    // Highlight entities during concept segment
    const conceptSegment = segments.find(s => s.segmentId === 'concept');
    if (conceptSegment) {
      for (const entity of entities.slice(0, 2)) {
        rules.push({
          trigger: 'time',
          condition: `t >= ${conceptSegment.startTime}`,
          effect: 'highlight',
          target: entity.entityId,
          duration: 2,
        });
      }
    }

    // Focus on key entity at conclusion
    const conclusionSegment = segments.find(s => s.segmentId === 'conclusion');
    if (conclusionSegment && entities.length > 0) {
      rules.push({
        trigger: 'time',
        condition: `t >= ${conclusionSegment.startTime}`,
        effect: 'focus',
        target: entities[0].entityId,
        duration: 3,
      });
    }

    return rules;
  }

  /**
   * Generate pacing metadata.
   */
  private generatePacingMetadata(duration: number, segments: AnimationSegment[]): { introDuration: number; conceptPresentationDuration: number; interactionTime: number; outroDuration: number; recommendedPauses: number[] } {
    const intro = segments.find(s => s.segmentId === 'intro');
    const concept = segments.find(s => s.segmentId === 'concept');
    const conclusion = segments.find(s => s.segmentId === 'conclusion');

    return {
      introDuration: intro ? intro.endTime - intro.startTime : duration * 0.2,
      conceptPresentationDuration: concept ? concept.endTime - concept.startTime : duration * 0.5,
      interactionTime: duration * 0.1,
      outroDuration: conclusion ? conclusion.endTime - conclusion.startTime : duration * 0.2,
      recommendedPauses: segments.flatMap(s => s.pausePoints),
    };
  }

  /**
   * Generate narration script.
   */
  private generateNarration(segments: AnimationSegment[], claimText: string): { segments: Array<{ segmentId: string; text: string; startTime: number; duration: number; tone: 'instructional' | 'conversational' | 'emphatic' }>; language: string } {
    const narrationSegments = segments.map(s => {
      const duration = s.endTime - s.startTime;
      return {
        segmentId: s.segmentId,
        text: this.getNarrationText(s.segmentId, claimText),
        startTime: s.startTime,
        duration: duration * 0.8,
        tone: s.segmentId === 'concept' ? 'emphatic' as const : 'instructional' as const,
      };
    });

    return {
      segments: narrationSegments,
      language: 'en',
    };
  }

  /**
   * Get narration text for a segment.
   */
  private getNarrationText(segmentId: string, claimText: string): string {
    const texts: Record<string, string> = {
      'intro': "Let's explore this concept together. Watch carefully as we set up the scenario.",
      'concept': `Here we see the key concept: ${claimText}. Notice how the elements interact.`,
      'conclusion': "To summarize what we've learned: the relationships shown here demonstrate the core principle.",
    };

    return texts[segmentId] || 'Continue observing the animation.';
  }

  /**
   * Generate subtitles from narration.
   */
  private generateSubtitles(narration: { segments: Array<{ segmentId: string; text: string; startTime: number; duration: number }> }): Array<{ startTime: number; endTime: number; text: string; segmentRef?: string }> {
    return narration.segments.map(s => ({
      startTime: s.startTime,
      endTime: s.startTime + s.duration,
      text: s.text,
      segmentRef: s.segmentId,
    }));
  }

  /**
   * Generate learner controls.
   */
  private generateLearnerControls(): Array<{ type: 'play' | 'pause' | 'rewind' | 'speed' | 'fullscreen' | 'reset'; enabled: boolean; position?: 'bottom' | 'top' | 'overlay' }> {
    return [
      { type: 'play', enabled: true, position: 'bottom' },
      { type: 'pause', enabled: true, position: 'bottom' },
      { type: 'reset', enabled: true, position: 'bottom' },
      { type: 'speed', enabled: true, position: 'bottom' },
      { type: 'fullscreen', enabled: true, position: 'bottom' },
    ];
  }

  /**
   * Generate claim mapping.
   */
  private generateClaimMapping(segments: AnimationSegment[], claimRef: string): Array<{ claimRef: string; segmentIds: string[]; emphasisLevel: 'primary' | 'secondary' | 'tertiary' }> {
    return [
      {
        claimRef,
        segmentIds: segments.map(s => s.segmentId),
        emphasisLevel: 'primary',
      },
    ];
  }

  /**
   * Create an empty manifest for error cases.
   */
  private createEmptyManifest(claimRef: string): AnimationManifest {
    return {
      schemaVersion: '1.0.0',
      manifestType: 'Animation',
      claimRef,
      evidenceRefs: [],
      domain: '',
      gradeBand: '',
      pedagogicalIntent: '',
      sceneGraph: [],
      entities: [],
      segments: [],
      totalDurationSeconds: 30,
      cueingRules: [],
      pacingMetadata: {
        introDuration: 5,
        conceptPresentationDuration: 20,
        interactionTime: 3,
        outroDuration: 5,
        recommendedPauses: [],
      },
      learnerControls: [
        { type: 'play', enabled: true },
        { type: 'pause', enabled: true },
      ],
      claimMapping: [],
      variantKey: 'primary',
    };
  }
}
