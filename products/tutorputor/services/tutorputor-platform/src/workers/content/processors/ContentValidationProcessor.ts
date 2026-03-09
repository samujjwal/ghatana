/**
 * Content Validation Processor - Validates content using 4C framework.
 * 
 * @doc.type class
 * @doc.purpose Process content validation jobs
 * @doc.layer backend-worker
 * @doc.pattern JobProcessor
 */

import { Job } from 'bullmq';
import { PrismaClient } from '@ghatana/tutorputor-db';
import { Logger } from 'pino';
import { RealContentGenerationClient } from '../grpc/RealContentGenerationClient';
import * as crypto from 'crypto';

export interface ContentValidationJobData {
    experienceId: string;
    checkCorrectness: boolean;
    checkCompleteness: boolean;
    checkConcreteness: boolean;
    checkConciseness: boolean;
    minConfidenceThreshold: number;
}

export class ContentValidationProcessor {
    constructor(
        private grpcClient: RealContentGenerationClient,
        private prisma: PrismaClient,
        private logger: Logger
    ) { }

    async process(job: Job<ContentValidationJobData>): Promise<void> {
        const { experienceId, checkCorrectness, checkCompleteness, checkConcreteness, checkConciseness, minConfidenceThreshold } = job.data;

        this.logger.info(
            { jobId: job.id, experienceId },
            'Processing content validation job'
        );

        try {
            // Fetch experience with all content
            const experience = await this.prisma.learningExperience.findUnique({
                where: { id: experienceId },
                include: {
                    claims: {
                        include: {
                            examples: true,
                            simulations: {
                                include: {
                                    simulationManifest: true
                                }
                            }
                        },
                    },
                    experienceTasks: true,
                },
            });

            if (!experience) {
                throw new Error(`Experience not found: ${experienceId}`);
            }

            // Build validation request
            const requestId = crypto.randomUUID();

            // Adapt claims - map to what Java side likely expects
            const claimsPayload = (experience.claims as any[]).map((c: any) => ({
                claimRef: c.claimRef,
                text: c.text,
                bloomLevel: c.bloomLevel,
            }));

            const evidencesPayload = (experience.claims as any[]).flatMap((c: any) =>
                (c.examples || []).map((e: any) => ({
                    claimRef: c.claimRef,
                    type: e.type,
                }))
            );

            const tasksPayload = (experience.experienceTasks || []).map((task: any) => ({
                claimRef: task.claimRef,
                type: task.type,
            }));

            // Extract targetGrades safely - schema defines it as Json
            // Assuming it's array of string or object with grade field
            const targetGrades = Array.isArray(experience.targetGrades) ? experience.targetGrades : [];
            const primaryGrade = targetGrades.length > 0 ? (typeof targetGrades[0] === 'string' ? targetGrades[0] : JSON.stringify(targetGrades[0])) : 'GRADE_6_8';

            const response = await this.grpcClient.validateContent({
                requestId,
                experienceId,
                content: {
                    gradeLevel: primaryGrade,
                    domain: experience.domain,
                    claims: claimsPayload,
                    evidences: evidencesPayload,
                    tasks: tasksPayload,
                },
                config: {
                    checkCorrectness,
                    checkCompleteness,
                    checkConcreteness,
                    checkConciseness,
                    minConfidenceThreshold,
                },
            });

            this.logger.info(
                { jobId: job.id, passed: response.report?.passed, score: response.report?.overall_score },
                'Content validated successfully'
            );

            // Store validation results
            // Schema has changed: uses specific scores and ValidationStatus enum
            const overallStatus = response.report?.passed ? 'PASS' : 'FAIL';
            const rawScore = Number(response.report?.overall_score || 0);
            const score = rawScore <= 1 ? Math.round(rawScore * 100) : Math.round(rawScore);

            await this.prisma.validationRecord.create({
                data: {
                    experienceId,
                    overallStatus: overallStatus,

                    authorityScore: score,
                    accuracyScore: score,
                    usefulnessScore: score,
                    harmlessnessScore: score,
                    accessibilityScore: score,
                    gradefitScore: score,

                    issues: response.report?.issues || [],
                    suggestions: response.report?.recommendations || [],
                    validatedAt: new Date(),
                },
            });

            if (response.report?.passed) {
                await this.prisma.learningExperience.update({
                  where: { id: experienceId },
                  data: {
                    status: 'REVIEW',
                  },
                });
            } else {
                this.logger.warn(
                    { jobId: job.id, experienceId, issuesCount: response.report?.issues?.length || 0 },
                    'Experience validation failed - needs improvement'
                );
            }

            this.logger.info(
                { jobId: job.id, experienceId },
                'Content validation job completed'
            );

        } catch (error: any) {
            this.logger.error(
                { jobId: job.id, experienceId, error: error.message },
                'Content validation job failed'
            );
            throw error;
        }
    }
}
