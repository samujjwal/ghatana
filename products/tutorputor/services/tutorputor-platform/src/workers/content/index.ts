/**
 * Content Worker Entry Point
 * 
 * Configures and starts the background worker for content generation.
 * Handles dispatching jobs to specific processors.
 */

import { Worker, Job, Queue } from 'bullmq';
import { PrismaClient } from '@ghatana/tutorputor-db';
import Redis from 'ioredis';
import { Logger } from 'pino';
import { RealContentGenerationClient } from './grpc/RealContentGenerationClient';
import { ClaimGenerationProcessor } from './processors/ClaimGenerationProcessor';
import { ExampleGenerationProcessor } from './processors/ExampleGenerationProcessor';
import { SimulationGenerationProcessor } from './processors/SimulationGenerationProcessor';
import { AnimationGenerationProcessor } from './processors/AnimationGenerationProcessor';
import { ContentValidationProcessor } from './processors/ContentValidationProcessor';
import { DeadLetterQueueManager, createQueueOptionsWithDLQ } from '../../utils/dead-letter-queue';
import { JobDeduplicator } from '../../utils/job-deduplication';

export interface ContentWorkerConfig {
    redis: {
        host: string;
        port: number;
        password?: string;
        db?: number;
    };
    grpc: {
        serverAddress: string;
        useTls: boolean;
    };
    concurrency?: number;
    logger: Logger;
    prisma?: PrismaClient;
}

export class ContentWorkerService {
    private worker: Worker | null = null;
    private producerQueue: Queue | null = null;
    private redisConnection: Redis | null = null;
    private prisma: PrismaClient;
    private logger: Logger;
    private grpcClient: RealContentGenerationClient;
    private dlqManager: DeadLetterQueueManager;
    private jobDeduplicator: JobDeduplicator;

    // Processors
    private claimProcessor: ClaimGenerationProcessor;
    private exampleProcessor: ExampleGenerationProcessor;
    private simulationProcessor: SimulationGenerationProcessor;
    private animationProcessor: AnimationGenerationProcessor;
    private validationProcessor: ContentValidationProcessor;

    constructor(config: ContentWorkerConfig) {
        this.logger = config.logger;
        this.prisma = config.prisma || new PrismaClient();

        this.grpcClient = new RealContentGenerationClient({
            serverAddress: config.grpc.serverAddress,
            useTls: config.grpc.useTls,
            logger: this.logger,
            timeout: 5000,
            maxRetries: 3,
        });

        this.redisConnection = new Redis({
            host: config.redis.host,
            port: config.redis.port,
            password: config.redis.password,
            db: config.redis.db || 0,
            maxRetriesPerRequest: null,
        });

        // Initialize DLQ manager
        this.dlqManager = new DeadLetterQueueManager(
            {
                name: 'content-generation-dlq',
                redis: config.redis,
            },
            this.logger
        );

        // Initialize job deduplicator
        this.jobDeduplicator = new JobDeduplicator(this.prisma);

        this.producerQueue = new Queue('content-generation', {
            connection: this.redisConnection as any,
            defaultJobOptions: createQueueOptionsWithDLQ(3, 5000),
        });

        this.claimProcessor = new ClaimGenerationProcessor(
            this.grpcClient,
            this.prisma,
            this.producerQueue,
            this.logger
        );
        this.exampleProcessor = new ExampleGenerationProcessor(this.grpcClient, this.prisma, this.logger);
        this.simulationProcessor = new SimulationGenerationProcessor(this.grpcClient, this.prisma, this.logger);
        this.animationProcessor = new AnimationGenerationProcessor(this.grpcClient, this.prisma, this.logger);
        this.validationProcessor = new ContentValidationProcessor(this.grpcClient, this.prisma, this.logger);

        this.worker = new Worker(
            'content-generation',
            async (job: Job) => {
                this.logger.info({ jobId: job.id, name: job.name }, 'Received job');

                try {
                    switch (job.name) {
                        case 'generate-claims':
                            await this.claimProcessor.process(job as any);
                            break;
                        case 'generate-examples':
                            await this.exampleProcessor.process(job as any);
                            break;
                        case 'generate-simulation':
                            await this.simulationProcessor.process(job as any);
                            break;
                        case 'validate-content':
                            await this.validationProcessor.process(job as any);
                            break;
                        case 'generate-animation':
                            await this.animationProcessor.process(job as any);
                            break;
                        default:
                            this.logger.warn({ jobId: job.id, name: job.name }, 'Unknown job name');
                    }
                } catch (error: any) {
                    this.logger.error({ jobId: job.id, err: error }, 'Job processing failed');
                    throw error;
                }
            },
            {
                connection: this.redisConnection as any,
                concurrency: config.concurrency || 5,
                removeOnComplete: { count: 100 },
                removeOnFail: { count: 500 },
            }
        );

        this.setupEventHandlers();
    }

    private setupEventHandlers() {
        if (!this.worker) return;

        this.worker.on('completed', (job) => {
            this.logger.info({ jobId: job.id, name: job.name }, 'Job completed successfully');
        });

        this.worker.on('failed', async (job, err) => {
            this.logger.error({ jobId: job?.id, name: job?.name, err }, 'Job failed');
            
            // Move to DLQ after max retries
            if (job && job.attemptsMade >= 3) {
                await this.dlqManager.moveToDLQ(job, err.message);
            }
        });

        this.worker.on('error', (err) => {
            this.logger.error({ err }, 'Worker error');
        });
    }

    async close() {
        if (this.worker) {
            await this.worker.close();
        }
        if (this.producerQueue) {
            await this.producerQueue.close();
        }
        if (this.dlqManager) {
            await this.dlqManager.close();
        }
        if (this.redisConnection) {
            await this.redisConnection.quit();
        }
        // We do not close prisma here as it might be shared
    }
}
