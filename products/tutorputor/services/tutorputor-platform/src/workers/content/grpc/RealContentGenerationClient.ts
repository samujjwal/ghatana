/**
 * Production gRPC client for content generation + validation agents.
 */

import * as grpc from '@grpc/grpc-js';
import * as protoLoader from '@grpc/proto-loader';
import type { Logger } from 'pino';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export interface GrpcClientConfig {
    serverAddress: string;
    useTls: boolean;
    timeout: number;
    maxRetries: number;
    logger: Logger;
}

export interface GenerateClaimsRequest {
    requestId: string;
    tenantId: string;
    topic: string;
    gradeLevel: string;
    domain: string;
    maxClaims: number;
    context: Record<string, string>;
}

export interface GenerateExamplesRequest {
    requestId: string;
    tenantId: string;
    claimText: string;
    claimRef: string;
    gradeLevel: string;
    domain: string;
    types: string[];
    count: number;
}

export interface GenerateSimulationRequest {
    requestId: string;
    tenantId: string;
    claimText: string;
    claimRef: string;
    gradeLevel: string;
    domain: string;
    interactionType: string;
    complexity: string;
}

export interface GenerateAnimationRequest {
    requestId: string;
    tenantId: string;
    claimText: string;
    claimRef: string;
    animationType: string;
    durationSeconds: number;
}

export interface ValidateContentRequest {
    requestId: string;
    experienceId: string;
    content: {
        gradeLevel: string;
        domain: string;
        claims: Array<{
            claimRef: string;
            text: string;
            bloomLevel: string;
        }>;
        evidences: Array<{
            claimRef: string;
            type: string;
        }>;
        tasks: Array<{
            claimRef: string;
            type: string;
        }>;
    };
    config: {
        checkCorrectness: boolean;
        checkCompleteness: boolean;
        checkConcreteness: boolean;
        checkConciseness: boolean;
        minConfidenceThreshold: number;
    };
}

function normalizeGradeLevel(input: string): string {
    const normalized = String(input || '').toUpperCase();
    const mapping: Record<string, string> = {
        K_2: 'K_2',
        GRADE_3_5: 'GRADE_3_5',
        GRADE_6_8: 'GRADE_6_8',
        GRADE_9_12: 'GRADE_9_12',
        UNDERGRADUATE: 'UNDERGRADUATE',
        GRADUATE: 'GRADUATE',
        PROFESSIONAL: 'GRADUATE',
    };
    return mapping[normalized] || 'GRADE_6_8';
}

function normalizeDomain(input: string): string {
    const normalized = String(input || '').toUpperCase();
    const mapping: Record<string, string> = {
        MATH: 'MATH',
        SCIENCE: 'SCIENCE',
        TECH: 'TECH',
        ENGINEERING: 'TECH',
        ARTS: 'TECH',
        LANGUAGE: 'TECH',
        GENERAL: 'TECH',
    };
    return mapping[normalized] || 'TECH';
}

function normalizeBloom(input: string): string {
    const normalized = String(input || '').toUpperCase();
    const allowed = ['REMEMBER', 'UNDERSTAND', 'APPLY', 'ANALYZE', 'EVALUATE', 'CREATE'];
    return allowed.includes(normalized) ? normalized : 'UNDERSTAND';
}

function normalizeExampleType(input: string): string {
    const normalized = String(input || '').toUpperCase();
    const mapping: Record<string, string> = {
        REAL_WORLD: 'REAL_WORLD',
        REAL_WORLD_APPLICATION: 'REAL_WORLD',
        PROBLEM_SOLVING: 'PROBLEM_SOLVING',
        ANALOGY: 'ANALOGY',
        CASE_STUDY: 'CASE_STUDY',
        VISUAL_REPRESENTATION: 'ANALOGY',
        STEP_BY_STEP: 'PROBLEM_SOLVING',
        COMPARISON: 'ANALOGY',
    };
    return mapping[normalized] || 'REAL_WORLD';
}

function normalizeInteractionType(input: string): string {
    const normalized = String(input || '').toUpperCase();
    const mapping: Record<string, string> = {
        PARAMETER_EXPLORATION: 'PARAMETER_EXPLORATION',
        PREDICTION: 'PREDICTION',
        CONSTRUCTION: 'CONSTRUCTION',
        GUIDED_DISCOVERY: 'PREDICTION',
        OPEN_ENDED: 'CONSTRUCTION',
        DEMONSTRATION: 'PARAMETER_EXPLORATION',
    };
    return mapping[normalized] || 'PARAMETER_EXPLORATION';
}

function normalizeComplexity(input: string): string {
    const normalized = String(input || '').toUpperCase();
    const mapping: Record<string, string> = {
        LOW: 'LOW',
        BASIC: 'LOW',
        MEDIUM: 'MEDIUM',
        INTERMEDIATE: 'MEDIUM',
        HIGH: 'HIGH',
        ADVANCED: 'HIGH',
    };
    return mapping[normalized] || 'MEDIUM';
}

function normalizeAnimationType(input: string): string {
    const normalized = String(input || '').toUpperCase();
    const mapping: Record<string, string> = {
        TWO_D: 'TWO_D',
        '2D': 'TWO_D',
        THREE_D: 'THREE_D',
        '3D': 'THREE_D',
        TIMELINE: 'TIMELINE',
        PROCESS_VISUALIZATION: 'TIMELINE',
    };
    return mapping[normalized] || 'TWO_D';
}

function resolveProtoPath(file: string): string {
    const candidates = [
        path.resolve(__dirname, '../../../../../tutorputor-ai-agents/src/main/proto', file),
        path.resolve(__dirname, '../../../../../../tutorputor-ai-agents/src/main/proto', file),
        path.resolve(process.cwd(), 'products/tutorputor/services/tutorputor-ai-agents/src/main/proto', file),
        path.resolve(process.cwd(), 'services/tutorputor-ai-agents/src/main/proto', file),
    ];

    const found = candidates.find((candidate) => fs.existsSync(candidate));
    if (!found) {
        throw new Error(`Proto file not found: ${file}. Checked: ${candidates.join(', ')}`);
    }

    return found;
}

export class RealContentGenerationClient {
    private contentClient: any;
    private validationClient: any;
    private logger: Logger;
    private timeout: number;
    private maxRetries: number;
    private isReady = false;

    constructor(config: GrpcClientConfig) {
        this.logger = config.logger;
        this.timeout = config.timeout;
        this.maxRetries = config.maxRetries;
        this.initializeClient(config);
    }

    private initializeClient(config: GrpcClientConfig) {
        try {
            const contentProtoPath = resolveProtoPath('tutorputor_content_generation.proto');
            const validationProtoPath = resolveProtoPath('tutorputor_validation.proto');
            const includeDir = path.dirname(contentProtoPath);

            const contentPackageDefinition = protoLoader.loadSync(contentProtoPath, {
                keepCase: true,
                longs: String,
                enums: String,
                defaults: true,
                oneofs: true,
                includeDirs: [includeDir],
            });

            const validationPackageDefinition = protoLoader.loadSync(validationProtoPath, {
                keepCase: true,
                longs: String,
                enums: String,
                defaults: true,
                oneofs: true,
                includeDirs: [includeDir],
            });

            const contentDescriptor = grpc.loadPackageDefinition(contentPackageDefinition) as any;
            const validationDescriptor = grpc.loadPackageDefinition(validationPackageDefinition) as any;

            const credentials = config.useTls
                ? grpc.credentials.createSsl()
                : grpc.credentials.createInsecure();

            this.contentClient = new contentDescriptor.ghatana.tutorputor.v1.ContentGenerationService(
                config.serverAddress,
                credentials,
            );

            this.validationClient = new validationDescriptor.ghatana.tutorputor.v1.ValidationService(
                config.serverAddress,
                credentials,
            );

            this.isReady = true;
        } catch (e) {
            this.logger.error({ err: e }, 'Failed to initialize gRPC client');
        }
    }

    async generateClaims(request: GenerateClaimsRequest): Promise<any> {
        if (!this.isReady) throw new Error('gRPC client not initialized');

        const grpcRequest = {
            request_id: request.requestId,
            tenant_id: request.tenantId,
            topic: request.topic,
            grade_level: normalizeGradeLevel(request.gradeLevel),
            domain: normalizeDomain(request.domain),
            max_claims: request.maxClaims,
            context: request.context || {},
        };

        return this.makeRequest(
            this.contentClient,
            ['generateClaims', 'GenerateClaims'],
            grpcRequest,
        );
    }

    async generateExamples(request: GenerateExamplesRequest): Promise<any> {
        if (!this.isReady) throw new Error('gRPC client not initialized');

        const grpcRequest = {
            request_id: request.requestId,
            tenant_id: request.tenantId,
            claim_text: request.claimText,
            claim_ref: request.claimRef,
            grade_level: normalizeGradeLevel(request.gradeLevel),
            domain: normalizeDomain(request.domain),
            types: (request.types || []).map(normalizeExampleType),
            count: request.count,
        };

        return this.makeRequest(
            this.contentClient,
            ['generateExamples', 'GenerateExamples'],
            grpcRequest,
        );
    }

    async generateSimulation(request: GenerateSimulationRequest): Promise<any> {
        if (!this.isReady) throw new Error('gRPC client not initialized');

        const grpcRequest = {
            request_id: request.requestId,
            tenant_id: request.tenantId,
            claim_text: request.claimText,
            claim_ref: request.claimRef,
            grade_level: normalizeGradeLevel(request.gradeLevel),
            domain: normalizeDomain(request.domain),
            interaction_type: normalizeInteractionType(request.interactionType),
            complexity: normalizeComplexity(request.complexity),
        };

        return this.makeRequest(
            this.contentClient,
            ['generateSimulation', 'GenerateSimulation'],
            grpcRequest,
        );
    }

    async generateAnimation(request: GenerateAnimationRequest): Promise<any> {
        if (!this.isReady) throw new Error('gRPC client not initialized');

        const grpcRequest = {
            request_id: request.requestId,
            tenant_id: request.tenantId,
            claim_text: request.claimText,
            claim_ref: request.claimRef,
            animation_type: normalizeAnimationType(request.animationType),
            duration_seconds: request.durationSeconds,
        };

        return this.makeRequest(
            this.contentClient,
            ['generateAnimation', 'GenerateAnimation'],
            grpcRequest,
        );
    }

    async validateContent(request: ValidateContentRequest): Promise<any> {
        if (!this.isReady) throw new Error('gRPC client not initialized');

        const grpcRequest = {
            request_id: request.requestId,
            experience_id: request.experienceId,
            content: {
                title: '',
                domain: normalizeDomain(request.content.domain),
                grade_level: normalizeGradeLevel(request.content.gradeLevel),
                claims: (request.content.claims || []).map((claim) => ({
                    claim_ref: claim.claimRef,
                    text: claim.text,
                    bloom_level: normalizeBloom(claim.bloomLevel),
                })),
                evidences: (request.content.evidences || []).map((evidence, index) => ({
                    evidence_ref: `E${index + 1}`,
                    claim_ref: evidence.claimRef,
                    type: evidence.type,
                    description: '',
                })),
                tasks: (request.content.tasks || []).map((task, index) => ({
                    task_ref: `T${index + 1}`,
                    claim_ref: task.claimRef,
                    type: task.type,
                    prompt: '',
                })),
            },
            config: {
                check_correctness: request.config.checkCorrectness,
                check_completeness: request.config.checkCompleteness,
                check_concreteness: request.config.checkConcreteness,
                check_conciseness: request.config.checkConciseness,
                min_confidence_threshold: request.config.minConfidenceThreshold,
            },
        };

        return this.makeRequest(
            this.validationClient,
            ['validateContent', 'ValidateContent'],
            grpcRequest,
        );
    }

    private resolveMethod(client: any, methodNames: string[]): Function {
        for (const methodName of methodNames) {
            if (typeof client[methodName] === 'function') {
                return client[methodName].bind(client);
            }
        }

        throw new Error(`No matching gRPC method found. Tried: ${methodNames.join(', ')}`);
    }

    private async makeRequest(client: any, methodNames: string[], request: any): Promise<any> {
        const method = this.resolveMethod(client, methodNames);

        let attempt = 0;
        while (attempt <= this.maxRetries) {
            try {
                return await new Promise((resolve, reject) => {
                    method(
                        request,
                        { deadline: Date.now() + this.timeout },
                        (err: any, response: any) => {
                            if (err) {
                                reject(err);
                            } else {
                                resolve(response);
                            }
                        },
                    );
                });
            } catch (err) {
                attempt += 1;
                if (attempt > this.maxRetries) {
                    this.logger.error({ err, attempt }, 'gRPC request failed after retries');
                    throw err;
                }
                this.logger.warn({ err, attempt }, 'gRPC request failed, retrying');
            }
        }

        throw new Error('Unreachable gRPC retry state');
    }
}
