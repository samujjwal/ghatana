import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ContentWorkerTelemetryPublisher } from '../generation-telemetry';

describe('ContentWorkerTelemetryPublisher', () => {
    let prisma: {
        generationJob: {
            findUnique: ReturnType<typeof vi.fn>;
            update: ReturnType<typeof vi.fn>;
        };
    };
    let logger: {
        warn: ReturnType<typeof vi.fn>;
    };
    let redis: {
        publish: ReturnType<typeof vi.fn>;
    };

    beforeEach(() => {
        prisma = {
            generationJob: {
                findUnique: vi.fn().mockResolvedValue({
                    id: 'gen-job-1',
                    requestId: 'req-1',
                    jobType: 'ANIMATION',
                    progress: 0,
                    diagnostics: null,
                    startedAt: null,
                }),
                update: vi.fn().mockResolvedValue(undefined),
            },
        };
        logger = {
            warn: vi.fn(),
        };
        redis = {
            publish: vi.fn().mockResolvedValue(1),
        };
    });

    it('persists correlated telemetry into generationJob diagnostics and Redis stream', async () => {
        const publisher = new ContentWorkerTelemetryPublisher(
            prisma as any,
            logger as any,
            redis as any,
        );

        await publisher.publishForJob(
            {
                id: 'queue-job-1',
                name: 'generate-animation',
                attemptsMade: 1,
                data: {
                    generationJobId: 'gen-job-1',
                    generationRequestId: 'req-1',
                },
            } as any,
            {
                stage: 'grpc_response_received',
                message: 'Animation response received',
                progressPercent: 60,
                status: 'running',
                cost: {
                    actualTokens: 850,
                    actualCostUsd: 0.0017,
                },
                diagnostics: {
                    animationId: 'anim-1',
                },
            },
        );

        expect(prisma.generationJob.update).toHaveBeenCalledWith(
            expect.objectContaining({
                where: { id: 'gen-job-1' },
                data: expect.objectContaining({
                    progress: 60,
                    diagnostics: expect.objectContaining({
                        workerTelemetry: expect.objectContaining({
                            requestId: 'req-1',
                            jobId: 'gen-job-1',
                            stage: 'grpc_response_received',
                        }),
                    }),
                }),
            }),
        );

        expect(redis.publish).toHaveBeenCalledWith(
            'tutorputor:generation-execution:req-1',
            expect.stringContaining('"kind":"telemetry"'),
        );
    });

    it('extracts rough token cost from generation metadata', () => {
        expect(
            ContentWorkerTelemetryPublisher.extractCostFromMetadata({
                model_name: 'gpt-test',
                tokens_used: 1500,
                generation_time_ms: 4200,
            }),
        ).toEqual({
            model: 'gpt-test',
            actualTokens: 1500,
            actualCostUsd: 0.003,
            generationTimeMs: 4200,
        });
    });
});
