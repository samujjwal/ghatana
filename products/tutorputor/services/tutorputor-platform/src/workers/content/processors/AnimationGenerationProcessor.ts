/**
 * Animation Generation Processor - Generates animation specs for claims.
 *
 * @doc.type class
 * @doc.purpose Process animation generation jobs
 * @doc.layer backend-worker
 * @doc.pattern JobProcessor
 */

import { Job } from 'bullmq';
import { PrismaClient } from '@ghatana/tutorputor-db';
import { Logger } from 'pino';
import { RealContentGenerationClient } from '../grpc/RealContentGenerationClient';
import * as crypto from 'crypto';

export interface AnimationGenerationJobData {
    experienceId: string;
    tenantId: string;
    claimRef: string;
    claimText: string;
    animationType: string;
    durationSeconds: number;
    complexity?: string;
}

export class AnimationGenerationProcessor {
    constructor(
        private grpcClient: RealContentGenerationClient,
        private prisma: PrismaClient,
        private logger: Logger
    ) { }

    async process(job: Job<AnimationGenerationJobData>): Promise<void> {
        const {
            experienceId,
            tenantId,
            claimRef,
            claimText,
            animationType,
            durationSeconds,
            complexity,
        } = job.data;

        this.logger.info(
            { jobId: job.id, experienceId, claimRef },
            'Processing animation generation job'
        );

        try {
            const requestId = crypto.randomUUID();
            const response = await this.grpcClient.generateAnimation({
                requestId,
                tenantId,
                claimText,
                claimRef,
                animationType,
                durationSeconds,
            });

            const animation = response?.animation;
            if (!animation) {
                throw new Error('No animation specification returned');
            }

            const persistedDuration = this.resolveDuration(animation, durationSeconds);
            const persistedType = this.mapAnimationType(animation.type ?? animationType);
            const persistedTitle = String(animation.title || `Animation for ${claimRef}`);
            const persistedDescription = String(animation.description || '');
            const persistedConfig = {
                animationId: animation.animation_id ?? animation.animationId ?? null,
                complexity: complexity ?? 'medium',
                keyframes: Array.isArray(animation.keyframes) ? animation.keyframes : [],
                metadata: response?.validation ?? null,
                raw: animation,
            };

            await this.prisma.claimAnimation.upsert({
                where: {
                    experienceId_claimRef: {
                        experienceId,
                        claimRef,
                    },
                },
                create: {
                    experienceId,
                    claimRef,
                    title: persistedTitle,
                    description: persistedDescription,
                    type: persistedType,
                    duration: persistedDuration,
                    config: persistedConfig as any,
                },
                update: {
                    title: persistedTitle,
                    description: persistedDescription,
                    type: persistedType,
                    duration: persistedDuration,
                    config: persistedConfig as any,
                },
            });

            this.logger.info(
                { jobId: job.id, experienceId, claimRef, type: persistedType },
                'Animation generation job completed'
            );
        } catch (error: any) {
            this.logger.error(
                { jobId: job.id, experienceId, claimRef, error: error?.message },
                'Animation generation job failed'
            );
            throw error;
        }
    }

    private resolveDuration(animation: any, fallback: number): number {
        const raw = animation.duration_seconds ?? animation.durationSeconds ?? fallback;
        const parsed = Number(raw);
        return Number.isFinite(parsed) && parsed > 0 ? Math.round(parsed) : Math.max(1, fallback);
    }

    private mapAnimationType(rawType: string): string {
        const normalized = String(rawType || '').trim().toUpperCase();
        switch (normalized) {
            case '3D':
            case 'THREE_D':
                return '3d';
            case 'TIMELINE':
                return 'timeline';
            case '2D':
            case 'TWO_D':
            default:
                return '2d';
        }
    }
}
