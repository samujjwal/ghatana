import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AnimationGenerationProcessor, type AnimationGenerationJobData } from '../processors/AnimationGenerationProcessor';

describe('AnimationGenerationProcessor', () => {
    const makeJob = (overrides: Partial<AnimationGenerationJobData> = {}) =>
        ({
            id: 'job-1',
            data: {
                experienceId: 'exp-1',
                tenantId: 'tenant-1',
                claimRef: 'C1',
                claimText: 'Gravity accelerates objects toward Earth.',
                animationType: 'TWO_D',
                durationSeconds: 30,
                complexity: 'medium',
                ...overrides,
            },
        } as any);

    let grpcClient: { generateAnimation: ReturnType<typeof vi.fn> };
    let prisma: { claimAnimation: { upsert: ReturnType<typeof vi.fn> } };
    let logger: { info: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        grpcClient = {
            generateAnimation: vi.fn(),
        };
        prisma = {
            claimAnimation: {
                upsert: vi.fn().mockResolvedValue(undefined),
            },
        };
        logger = {
            info: vi.fn(),
            error: vi.fn(),
        };
    });

    it('persists generated animation to claimAnimation', async () => {
        grpcClient.generateAnimation.mockResolvedValue({
            animation: {
                animation_id: 'anim-1',
                title: 'Falling Object',
                description: 'Shows motion under gravity',
                type: 'THREE_D',
                duration_seconds: 45,
                keyframes: [{ time_ms: 0, description: 'start', properties: {} }],
            },
            validation: { valid: true },
        });

        const processor = new AnimationGenerationProcessor(grpcClient as any, prisma as any, logger as any);
        await processor.process(makeJob());

        expect(grpcClient.generateAnimation).toHaveBeenCalledWith(
            expect.objectContaining({
                tenantId: 'tenant-1',
                claimRef: 'C1',
                animationType: 'TWO_D',
                durationSeconds: 30,
            }),
        );

        expect(prisma.claimAnimation.upsert).toHaveBeenCalledWith(
            expect.objectContaining({
                where: {
                    experienceId_claimRef: {
                        experienceId: 'exp-1',
                        claimRef: 'C1',
                    },
                },
                create: expect.objectContaining({
                    type: '3d',
                    duration: 45,
                }),
            }),
        );
    });

    it('fails when grpc response has no animation payload', async () => {
        grpcClient.generateAnimation.mockResolvedValue({});
        const processor = new AnimationGenerationProcessor(grpcClient as any, prisma as any, logger as any);

        await expect(processor.process(makeJob())).rejects.toThrow('No animation specification returned');
        expect(prisma.claimAnimation.upsert).not.toHaveBeenCalled();
    });
});
