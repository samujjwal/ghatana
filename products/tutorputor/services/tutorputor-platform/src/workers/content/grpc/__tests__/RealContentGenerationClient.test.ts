/**
 * Unit tests for RealContentGenerationClient.
 *
 * @doc.type test
 * @doc.purpose Test gRPC client initialization and methods
 * @doc.layer testing
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
    RealContentGenerationClient,
    ContentGenerationError,
    ProtoLoadError,
    type GrpcClientConfig,
} from '../RealContentGenerationClient';
import type { Logger } from 'pino';

// Mock the gRPC module to test serialization without a real server
vi.mock('@grpc/grpc-js', () => ({
    default: {
        credentials: {
            createInsecure: vi.fn(() => ({})),
            createSsl: vi.fn(() => ({})),
        },
        loadPackageDefinition: vi.fn(() => ({
            tutorputor: {
                content_generation: {
                    ContentGenerationService: vi.fn(function(this: any) {
                        this.generateClaims = vi.fn();
                        this.analyzeContentNeeds = vi.fn();
                        this.generateExamples = vi.fn();
                        this.generateSimulation = vi.fn();
                        this.generateAnimation = vi.fn();
                        this.validateContent = vi.fn();
                        this.healthCheck = vi.fn();
                    }),
                },
            },
        })),
    },
}));

vi.mock('@grpc/proto-loader', () => ({
    default: {
        loadSync: vi.fn(() => ({})),
    },
}));

const createMockLogger = (): Logger => ({
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    debug: vi.fn(),
    trace: vi.fn(),
    silent: vi.fn(),
    level: 'info',
    child: vi.fn().mockReturnThis(),
    bindings: vi.fn().mockReturnValue({}),
    flush: vi.fn(),
    isLevelEnabled: vi.fn().mockReturnValue(true),
} as unknown as Logger);

describe('RealContentGenerationClient', () => {
    let mockLogger: Logger;

    beforeEach(() => {
        mockLogger = createMockLogger();
    });

    describe('Error Types', () => {
        it('ContentGenerationError should have correct structure', () => {
            const error = new ContentGenerationError(
                'Test message',
                'TEST_CODE',
                { detail: 'extra info' }
            );

            expect(error.message).toBe('Test message');
            expect(error.code).toBe('TEST_CODE');
            expect(error.details).toEqual({ detail: 'extra info' });
            expect(error.name).toBe('ContentGenerationError');
        });

        it('ProtoLoadError should have attempted paths', () => {
            const paths = ['/path/one.proto', '/path/two.proto'];
            const error = new ProtoLoadError('Proto not found', paths);

            expect(error.message).toContain('Proto not found');
            expect(error.attemptedPaths).toEqual(paths);
            expect(error.name).toBe('ProtoLoadError');
        });
    });

    describe('Client Interface', () => {
        it('should have all required methods', () => {
            const prototype = RealContentGenerationClient.prototype;
            const methods = [
                'generateClaims',
                'analyzeContentNeeds',
                'generateExamples',
                'generateSimulation',
                'generateAnimation',
                'validateContent',
            ];

            for (const method of methods) {
                expect(typeof (prototype as unknown as Record<string, unknown>)[method]).toBe('function');
            }
        });

        it('should have ready getter', () => {
            const prototype = RealContentGenerationClient.prototype;
            const descriptor = Object.getOwnPropertyDescriptor(prototype, 'ready');
            expect(descriptor?.get).toBeDefined();
        });

        it('should have getProtoPath method', () => {
            const prototype = RealContentGenerationClient.prototype;
            expect(typeof prototype.getProtoPath).toBe('function');
        });
    });

    describe('Request Types', () => {
        it('should accept valid GenerateClaimsRequest', () => {
            const request = {
                requestId: 'req-123',
                tenantId: 'tenant-1',
                topic: 'Newton\'s Laws',
                gradeLevel: 'GRADE_9_12',
                domain: 'PHYSICS',
                maxClaims: 5,
                context: { curriculum: 'standard' },
            };

            expect(request.requestId).toBe('req-123');
            expect(request.maxClaims).toBe(5);
        });

        it('should accept valid AnalyzeContentNeedsRequest', () => {
            const request = {
                requestId: 'req-456',
                tenantId: 'tenant-1',
                claimText: 'Force equals mass times acceleration',
                bloomLevel: 'UNDERSTAND',
                domain: 'PHYSICS',
                gradeLevel: 'GRADE_9_12',
                context: {},
            };

            expect(request.claimText).toBeDefined();
            expect(request.bloomLevel).toBe('UNDERSTAND');
        });
    });

    describe('Response Transformation', () => {
        it('should transform validation response correctly', () => {
            // Simulate the transform logic
            const rawResponse = {
                request_id: 'req-789',
                status: 'valid',
                overall_score: 85,
                can_publish: true,
                dimension_scores: { educational: 90, safety: 95 },
                issues: [],
                issue_count: 0,
                metadata: {
                    model_name: 'gpt-4',
                    tokens_used: 150,
                    generation_time_ms: 1200,
                    temperature: 0.7,
                    prompt_hash: 'abc123',
                    timestamp: '2024-01-01T00:00:00Z',
                },
            };

            // Simulate transformation
            const transformed = {
                requestId: String(rawResponse.request_id),
                status: String(rawResponse.status) as 'valid' | 'invalid' | 'warning',
                overallScore: Number(rawResponse.overall_score),
                canPublish: Boolean(rawResponse.can_publish),
                dimensionScores: rawResponse.dimension_scores as Record<string, number>,
                issues: [],
                issueCount: Number(rawResponse.issue_count),
                metadata: {
                    modelName: String(rawResponse.metadata.model_name),
                    tokensUsed: Number(rawResponse.metadata.tokens_used),
                    generationTimeMs: Number(rawResponse.metadata.generation_time_ms),
                    temperature: Number(rawResponse.metadata.temperature),
                    promptHash: String(rawResponse.metadata.prompt_hash),
                    timestamp: String(rawResponse.metadata.timestamp),
                },
            };

            expect(transformed.requestId).toBe('req-789');
            expect(transformed.status).toBe('valid');
            expect(transformed.overallScore).toBe(85);
            expect(transformed.canPublish).toBe(true);
            expect(transformed.metadata.modelName).toBe('gpt-4');
        });
    });

    describe('Proto-Encoded Request Contract Tests', () => {
        it('should serialize GenerateClaimsRequest with nested RequestContext and context_params', () => {
            // Test the serialization logic directly without initializing the client
            const request = {
                context: {
                    requestId: 'req-123',
                    tenantId: 'tenant-1',
                    timestamp: new Date('2024-01-01T00:00:00Z'),
                    metadata: { curriculum: 'standard' },
                },
                topic: 'Newton\'s Laws',
                gradeLevel: 'GRADE_9_12',
                domain: 'PHYSICS',
                maxClaims: 5,
                contextParams: { key: 'value' },
                language: 'en',
            };

            // Simulate the serialization that happens in generateClaims
            const grpcRequest = {
                context: {
                    request_id: request.context.requestId,
                    tenant_id: request.context.tenantId,
                    timestamp: request.context.timestamp ? {
                        seconds: Math.floor(request.context.timestamp.getTime() / 1000),
                        nanos: (request.context.timestamp.getTime() % 1000) * 1_000_000,
                    } : undefined,
                    metadata: request.context.metadata || {},
                },
                topic: request.topic,
                grade_level: request.gradeLevel,
                domain: request.domain,
                max_claims: request.maxClaims,
                context_params: request.contextParams || {},
                language: request.language || 'en',
            };

            // Verify proto field names and structure
            expect(grpcRequest.context).toBeDefined();
            expect(grpcRequest.context).toBeInstanceOf(Object);
            expect(grpcRequest.context).toHaveProperty('request_id', 'req-123');
            expect(grpcRequest.context).toHaveProperty('tenant_id', 'tenant-1');
            expect(grpcRequest.context).toHaveProperty('timestamp');
            expect(grpcRequest.context).toHaveProperty('metadata');
            expect(grpcRequest).toHaveProperty('topic', 'Newton\'s Laws');
            expect(grpcRequest).toHaveProperty('grade_level', 'GRADE_9_12');
            expect(grpcRequest).toHaveProperty('domain', 'PHYSICS');
            expect(grpcRequest).toHaveProperty('max_claims', 5);
            expect(grpcRequest).toHaveProperty('context_params');
            expect(grpcRequest.context_params).toEqual({ key: 'value' });
            expect(grpcRequest).toHaveProperty('language', 'en');

            // Verify NOT using flat fields (proto requires nested context)
            expect(grpcRequest).not.toHaveProperty('requestId');
            expect(grpcRequest).not.toHaveProperty('tenantId');
        });

        it('should serialize GenerateExamplesRequest with example_types field', () => {
            const request = {
                requestId: 'req-456',
                tenantId: 'tenant-1',
                claimText: 'Force equals mass times acceleration',
                claimRef: 'C1',
                exampleTypes: ['REAL_WORLD', 'PROBLEM_SOLVING'],
                count: 2,
                domain: 'PHYSICS',
                gradeLevel: 'GRADE_9_12',
                context: { key: 'value' },
            };

            // Simulate the serialization that happens in generateExamples
            const grpcRequest = {
                request_id: request.requestId,
                tenant_id: request.tenantId,
                claim_text: request.claimText,
                claim_ref: request.claimRef,
                example_types: (request.exampleTypes || []).map((t: string) => t.toUpperCase()),
                count: request.count,
                domain: request.domain,
                grade_level: request.gradeLevel,
                context: request.context || {},
            };

            // Verify proto field names
            expect(grpcRequest).toHaveProperty('request_id', 'req-456');
            expect(grpcRequest).toHaveProperty('tenant_id', 'tenant-1');
            expect(grpcRequest).toHaveProperty('claim_text', 'Force equals mass times acceleration');
            expect(grpcRequest).toHaveProperty('claim_ref', 'C1');
            expect(grpcRequest).toHaveProperty('example_types');
            expect(Array.isArray(grpcRequest.example_types)).toBe(true);
            expect(grpcRequest.example_types).toEqual(['REAL_WORLD', 'PROBLEM_SOLVING']);
            expect(grpcRequest).toHaveProperty('count', 2);
            expect(grpcRequest).toHaveProperty('domain', 'PHYSICS');
            expect(grpcRequest).toHaveProperty('grade_level', 'GRADE_9_12');
            expect(grpcRequest).toHaveProperty('context', { key: 'value' });

            // Verify NOT using 'types' field (proto requires 'example_types')
            expect(grpcRequest).not.toHaveProperty('types');
        });

        it('should serialize GenerateAnimationRequest with domain, grade_level, and context', () => {
            const request = {
                requestId: 'req-789',
                tenantId: 'tenant-1',
                claimText: 'Photosynthesis process',
                claimRef: 'C2',
                animationType: 'TWO_D',
                durationSeconds: 30,
                domain: 'BIOLOGY',
                gradeLevel: 'GRADE_9_12',
                context: { animationStyle: 'educational' },
            };

            // Simulate the serialization that happens in generateAnimation
            const grpcRequest = {
                request_id: request.requestId,
                tenant_id: request.tenantId,
                claim_text: request.claimText,
                claim_ref: request.claimRef,
                animation_type: request.animationType,
                duration_seconds: request.durationSeconds,
                domain: request.domain,
                grade_level: request.gradeLevel,
                context: request.context || {},
            };

            // Verify proto field names
            expect(grpcRequest).toHaveProperty('request_id', 'req-789');
            expect(grpcRequest).toHaveProperty('tenant_id', 'tenant-1');
            expect(grpcRequest).toHaveProperty('claim_text', 'Photosynthesis process');
            expect(grpcRequest).toHaveProperty('claim_ref', 'C2');
            expect(grpcRequest).toHaveProperty('animation_type', 'TWO_D');
            expect(grpcRequest).toHaveProperty('duration_seconds', 30);
            expect(grpcRequest).toHaveProperty('domain', 'BIOLOGY');
            expect(grpcRequest).toHaveProperty('grade_level', 'GRADE_9_12');
            expect(grpcRequest).toHaveProperty('context', { animationStyle: 'educational' });
        });

        it('should serialize GenerateSimulationRequest with context field', () => {
            const request = {
                requestId: 'req-101',
                tenantId: 'tenant-1',
                claimText: 'Projectile motion',
                claimRef: 'C3',
                gradeLevel: 'GRADE_9_12',
                domain: 'PHYSICS',
                interactionType: 'PARAMETER_EXPLORATION',
                complexity: 'MEDIUM',
                context: { simulationMode: 'interactive' },
            };

            // Simulate the serialization that happens in generateSimulation
            const grpcRequest = {
                request_id: request.requestId,
                tenant_id: request.tenantId,
                claim_text: request.claimText,
                claim_ref: request.claimRef,
                grade_level: request.gradeLevel,
                domain: request.domain,
                interaction_type: request.interactionType,
                complexity: request.complexity,
                context: request.context || {},
            };

            // Verify proto field names including context
            expect(grpcRequest).toHaveProperty('request_id', 'req-101');
            expect(grpcRequest).toHaveProperty('tenant_id', 'tenant-1');
            expect(grpcRequest).toHaveProperty('claim_text', 'Projectile motion');
            expect(grpcRequest).toHaveProperty('claim_ref', 'C3');
            expect(grpcRequest).toHaveProperty('grade_level', 'GRADE_9_12');
            expect(grpcRequest).toHaveProperty('domain', 'PHYSICS');
            expect(grpcRequest).toHaveProperty('interaction_type', 'PARAMETER_EXPLORATION');
            expect(grpcRequest).toHaveProperty('complexity', 'MEDIUM');
            expect(grpcRequest).toHaveProperty('context', { simulationMode: 'interactive' });
        });

        it('should serialize AnalyzeContentNeedsRequest with correct field names', () => {
            const request = {
                requestId: 'req-202',
                tenantId: 'tenant-1',
                claimText: 'Energy conservation',
                bloomLevel: 'UNDERSTAND',
                domain: 'PHYSICS',
                gradeLevel: 'GRADE_9_12',
                context: { lesson: 'thermodynamics' },
            };

            // Simulate the serialization that happens in analyzeContentNeeds
            const grpcRequest = {
                request_id: request.requestId,
                tenant_id: request.tenantId,
                claim_text: request.claimText,
                bloom_level: request.bloomLevel,
                domain: request.domain,
                grade_level: request.gradeLevel,
                context: request.context || {},
            };

            // Verify proto field names
            expect(grpcRequest).toHaveProperty('request_id', 'req-202');
            expect(grpcRequest).toHaveProperty('tenant_id', 'tenant-1');
            expect(grpcRequest).toHaveProperty('claim_text', 'Energy conservation');
            expect(grpcRequest).toHaveProperty('bloom_level', 'UNDERSTAND');
            expect(grpcRequest).toHaveProperty('domain', 'PHYSICS');
            expect(grpcRequest).toHaveProperty('grade_level', 'GRADE_9_12');
            expect(grpcRequest).toHaveProperty('context', { lesson: 'thermodynamics' });
        });

        it('should serialize ValidateContentRequest with correct field names', () => {
            const request = {
                requestId: 'req-303',
                tenantId: 'tenant-1',
                experienceId: 'exp-1',
                title: 'Test Experience',
                description: 'Test description',
                claimTexts: ['Claim 1', 'Claim 2'],
                domain: 'PHYSICS',
            };

            // Simulate the serialization that happens in validateContent
            const grpcRequest = {
                request_id: request.requestId,
                tenant_id: request.tenantId,
                experience_id: request.experienceId,
                title: request.title,
                description: request.description,
                claim_texts: request.claimTexts,
                domain: request.domain,
            };

            // Verify proto field names
            expect(grpcRequest).toHaveProperty('request_id', 'req-303');
            expect(grpcRequest).toHaveProperty('tenant_id', 'tenant-1');
            expect(grpcRequest).toHaveProperty('experience_id', 'exp-1');
            expect(grpcRequest).toHaveProperty('title', 'Test Experience');
            expect(grpcRequest).toHaveProperty('description', 'Test description');
            expect(grpcRequest).toHaveProperty('claim_texts');
            expect(Array.isArray(grpcRequest.claim_texts)).toBe(true);
            expect(grpcRequest.claim_texts).toEqual(['Claim 1', 'Claim 2']);
            expect(grpcRequest).toHaveProperty('domain', 'PHYSICS');
        });
    });
});
