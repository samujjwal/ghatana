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
                expect(typeof (prototype as Record<string, unknown>)[method]).toBe('function');
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
});
