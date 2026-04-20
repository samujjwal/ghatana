/**
 * Asset Materialization Service
 *
 * Task 2.8: Update Asset Materialization Service
 *
 * @doc.type module
 * @doc.purpose Service for converting manifests to renderable assets
 * @doc.layer service
 * @doc.pattern Materializer
 */

import type { Logger } from 'pino';
import type { Prisma, PrismaClient } from '@tutorputor/core/db';
import type { WorkedExampleManifest } from '../../../../../../contracts/v1/artifact-manifests/worked-example-manifest';
import type { AnimationManifest } from '../../../../../../contracts/v1/artifact-manifests/animation-manifest';

/**
 * Materialization result.
 */
export interface MaterializationResult {
  success: boolean;
  assetId: string;
  assetType: 'example' | 'animation';
  renderedConfig: Record<string, unknown>;
  errors: string[];
}

/**
 * Service for materializing manifests into renderable assets.
 */
export class AssetMaterializer {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: Logger
  ) {}

  /**
   * Materialize a worked example manifest into a renderable asset.
   */
  async materializeWorkedExample(
    manifest: WorkedExampleManifest,
    claimRef: string
  ): Promise<MaterializationResult> {
    try {
      this.logger.info({ claimRef, manifestId: manifest.claimRef }, 'Materializing worked example');

      // Convert manifest to rendered configuration
      const renderedConfig = this.renderWorkedExample(manifest);
      const experienceId = await this.getExperienceId(claimRef);
      const existingAsset = await this.prisma.claimExample.findFirst({
        where: {
          experienceId,
          claimRef,
        },
        select: { id: true },
      });

      // Persist to database
      const asset = existingAsset
        ? await this.prisma.claimExample.update({
            where: { id: existingAsset.id },
            data: {
              manifestVersion: '1.0.0',
              title: manifest.learnerGoal.substring(0, 100),
              description: manifest.pedagogicalIntent,
              content: renderedConfig as Prisma.InputJsonValue,
              exampleFamily: manifest.exampleFamily,
              validationStatus: 'valid',
            },
          })
        : await this.prisma.claimExample.create({
            data: {
              experienceId,
              claimRef,
              manifestId: `manifest-${manifest.claimRef}`,
              manifestVersion: '1.0.0',
              title: manifest.learnerGoal.substring(0, 100),
              description: manifest.pedagogicalIntent,
              content: renderedConfig as Prisma.InputJsonValue,
              exampleFamily: manifest.exampleFamily,
              type: this.mapExampleFamilyToType(manifest.exampleFamily),
              difficulty: 'INTERMEDIATE',
              orderIndex: 1,
              validationStatus: 'valid',
            },
          });

      return {
        success: true,
        assetId: asset.id,
        assetType: 'example',
        renderedConfig,
        errors: [],
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      this.logger.error({ err: error, claimRef }, 'Worked example materialization failed');

      return {
        success: false,
        assetId: '',
        assetType: 'example',
        renderedConfig: {},
        errors: [message],
      };
    }
  }

  /**
   * Materialize an animation manifest into a renderable asset.
   */
  async materializeAnimation(
    manifest: AnimationManifest,
    claimRef: string,
    variantKey: string = 'primary'
  ): Promise<MaterializationResult> {
    try {
      this.logger.info({ claimRef, variantKey }, 'Materializing animation');

      // Convert manifest to rendered configuration
      const renderedConfig = this.renderAnimation(manifest);

      // Get experienceId
      const experienceId = await this.getExperienceId(claimRef);

      // Persist to database
      const asset = await this.prisma.claimAnimation.upsert({
        where: {
          experienceId_claimRef_variantKey: {
            experienceId,
            claimRef,
            variantKey,
          },
        },
        create: {
          experienceId,
          claimRef,
          manifestId: `manifest-${manifest.claimRef}`,
          manifestVersion: '1.0.0',
          variantKey,
          isPrimary: variantKey === 'primary',
          title: manifest.pedagogicalIntent.substring(0, 100),
          description: manifest.pedagogicalIntent,
          type: '2d',
          duration: manifest.totalDurationSeconds,
          config: renderedConfig as Prisma.InputJsonValue,
          validationStatus: 'valid',
        },
        update: {
          manifestVersion: '1.0.0',
          title: manifest.pedagogicalIntent.substring(0, 100),
          description: manifest.pedagogicalIntent,
          duration: manifest.totalDurationSeconds,
          config: renderedConfig as Prisma.InputJsonValue,
          validationStatus: 'valid',
        },
      });

      return {
        success: true,
        assetId: asset.id,
        assetType: 'animation',
        renderedConfig,
        errors: [],
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      this.logger.error({ err: error, claimRef, variantKey }, 'Animation materialization failed');

      return {
        success: false,
        assetId: '',
        assetType: 'animation',
        renderedConfig: {},
        errors: [message],
      };
    }
  }

  /**
   * Render a worked example manifest to a configuration object.
   */
  private renderWorkedExample(manifest: WorkedExampleManifest): Record<string, unknown> {
    return {
      problemStatement: manifest.learnerGoal,
      solution: {
        steps: manifest.reasoningSteps.map((step: WorkedExampleManifest['reasoningSteps'][number]) => ({
          stepNumber: step.stepNumber,
          description: step.description,
          hint: step.hint,
          hasCheckpoint: step.checkpoint,
        })),
        explanations: manifest.explanationSteps.map((explanation: WorkedExampleManifest['explanationSteps'][number]) => ({
          stepNumber: explanation.stepNumber,
          content: explanation.content,
        })),
      },
      keyPoints: manifest.givens.map((given: WorkedExampleManifest['givens'][number]) => given.description),
      realWorldConnection: manifest.transferPrompts[0]?.prompt,
      misconceptions: manifest.misconceptionCheckpoints.map((checkpoint: WorkedExampleManifest['misconceptionCheckpoints'][number]) => ({
        warning: checkpoint.warningSign,
        guidance: checkpoint.correctiveGuidance,
      })),
      metadata: {
        estimatedTimeMinutes: manifest.estimatedTimeMinutes,
        difficulty: manifest.difficultyEstimate,
        family: manifest.exampleFamily,
      },
    };
  }

  /**
   * Render an animation manifest to a configuration object.
   */
  private renderAnimation(manifest: AnimationManifest): Record<string, unknown> {
    return {
      scene: {
        entities: manifest.entities.map((entity: AnimationManifest['entities'][number]) => ({
          id: entity.entityId,
          name: entity.name,
          type: entity.type,
          initialState: entity.initialState,
        })),
        camera: manifest.sceneGraph.find((node: AnimationManifest['sceneGraph'][number]) => node.type === 'camera')?.transform,
      },
      timeline: {
        segments: manifest.segments.map((segment: AnimationManifest['segments'][number]) => ({
          id: segment.segmentId,
          start: segment.startTime,
          end: segment.endTime,
          description: segment.description,
          pausePoints: segment.pausePoints,
        })),
        totalDuration: manifest.totalDurationSeconds,
      },
      cueing: manifest.cueingRules.map((rule: AnimationManifest['cueingRules'][number]) => ({
        trigger: rule.trigger,
        condition: rule.condition,
        effect: rule.effect,
        target: rule.target,
      })),
      controls: manifest.learnerControls
        .filter((control: AnimationManifest['learnerControls'][number]) => control.enabled)
        .map((control: AnimationManifest['learnerControls'][number]) => control.type),
      narration: manifest.narrationScript?.segments.map((segment: NonNullable<AnimationManifest['narrationScript']>['segments'][number]) => ({
        text: segment.text,
        startTime: segment.startTime,
        duration: segment.duration,
      })),
      pacing: manifest.pacingMetadata,
    };
  }

  /**
   * Map example family to legacy type.
   */
  private mapExampleFamilyToType(family: string): string {
    const mapping: Record<string, string> = {
      'real-world': 'REAL_WORLD',
      'analogy': 'ANALOGY',
      'worked-solution': 'PROBLEM_SOLVING',
      'counterexample': 'CASE_STUDY',
      'case-study': 'CASE_STUDY',
    };

    return mapping[family] || 'PROBLEM_SOLVING';
  }

  /**
   * Get experienceId from claimRef.
   */
  private async getExperienceId(claimRef: string): Promise<string> {
    const claim = await this.prisma.learningClaim.findFirst({
      where: { claimRef },
      select: { experienceId: true },
    });

    if (!claim) {
      throw new Error(`Claim not found: ${claimRef}`);
    }

    return claim.experienceId;
  }
}
