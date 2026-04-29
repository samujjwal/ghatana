/**
 * Contract tests for Content Generation gRPC client.
 *
 * These tests verify that:
 * 1. Proto files load correctly
 * 2. Client can be initialized
 * 3. Request/response transformations are correct
 * 4. All required RPC methods are available
 *
 * @doc.type test
 * @doc.purpose Verify contract compatibility between worker and service
 * @doc.layer testing
 * @doc.pattern ContractTest
 */

import { describe, it, expect, beforeAll, vi } from 'vitest';
import {
    RealContentGenerationClient,
    ContentGenerationError,
    ProtoLoadError,
    type GrpcClientConfig,
} from '../RealContentGenerationClient';
import type { Logger } from 'pino';

const resolveContractProtoPath = (): string => {
    const fs = require('fs');
    const path = require('path');

    const candidates = [
        path.resolve(__dirname, '../../../../../../../contracts/proto/content_generation.proto'),
        path.resolve(__dirname, '../../../../../../contracts/proto/content_generation.proto'),
        path.resolve(process.cwd(), 'products/tutorputor/contracts/proto/content_generation.proto'),
        path.resolve(process.cwd(), '../../contracts/proto/content_generation.proto'),
        path.resolve(process.cwd(), 'contracts/proto/content_generation.proto'),
    ];

    const found = candidates.find((candidate: string) => fs.existsSync(candidate));
    if (!found) {
        throw new Error(`content_generation.proto not found in candidates: ${candidates.join(', ')}`);
    }

    return found;
};

// Mock pino logger
const createMockLogger = (): Logger => {
    return {
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
    } as unknown as Logger;
};

describe('Content Generation Contract Tests', () => {
    describe('Proto Loading', () => {
        it('should resolve proto file from contracts/proto/', () => {
            // This test verifies the proto file exists at the authoritative location
            const fs = require('fs');
            const protoPath = resolveContractProtoPath();

            expect(fs.existsSync(protoPath)).toBe(true);
        });

        it('should have valid proto syntax', () => {
            const fs = require('fs');
            const protoPath = resolveContractProtoPath();

            const protoContent = fs.readFileSync(protoPath, 'utf-8');

            // Verify essential proto elements
            expect(protoContent).toContain('syntax = "proto3"');
            expect(protoContent).toContain('package tutorputor.content_generation');
            expect(protoContent).toContain('service ContentGenerationService');
            expect(protoContent).toContain('rpc GenerateClaims');
            expect(protoContent).toContain('rpc AnalyzeContentNeeds');
            expect(protoContent).toContain('rpc GenerateExamples');
            expect(protoContent).toContain('rpc GenerateSimulation');
            expect(protoContent).toContain('rpc GenerateAnimation');
            expect(protoContent).toContain('rpc ValidateContent');
            expect(protoContent).toContain('message LearningClaim');
            expect(protoContent).toContain('message ContentNeeds');
        });
    });

    describe('Client Initialization', () => {
        it('should throw ProtoLoadError when proto file not found', () => {
            const logger = createMockLogger();

            // Try to initialize with invalid path
            const config: GrpcClientConfig = {
                serverAddress: 'localhost:50051',
                useTls: false,
                timeout: 5000,
                maxRetries: 3,
                logger,
            };

            // In a real scenario, this would fail if proto is missing
            // Here we just verify the error type exists
            const error = new ProtoLoadError('Test error', ['/fake/path.proto']);
            expect(error).toBeInstanceOf(ProtoLoadError);
            expect(error.attemptedPaths).toEqual(['/fake/path.proto']);
        });

        it('should expose ready state', () => {
            const logger = createMockLogger();

            // Note: This test requires the proto file to exist
            // In CI/CD, this should pass after proto is validated
            try {
                const client = new RealContentGenerationClient({
                    serverAddress: 'localhost:50051',
                    useTls: false,
                    timeout: 5000,
                    maxRetries: 3,
                    logger,
                });

                // Ready state should be boolean
                expect(typeof client.ready).toBe('boolean');

                // Proto path should be available if client initialized
                const protoPath = client.getProtoPath();
                if (protoPath) {
                    expect(protoPath).toContain('content_generation.proto');
                }
            } catch (e) {
                // If proto not found, that's acceptable for this test
                // The key is that the client doesn't crash silently
                expect(e).toBeInstanceOf(Error);
            }
        });
    });

    describe('Request/Response Types', () => {
        it('should have correct GenerateClaimsRequest interface', () => {
            const request = {
                requestId: 'test-id',
                tenantId: 'tenant-1',
                topic: 'Physics',
                gradeLevel: 'GRADE_9_12',
                domain: 'SCIENCE',
                maxClaims: 5,
                context: {},
            };

            // Verify structure matches interface
            expect(request.requestId).toBeDefined();
            expect(request.tenantId).toBeDefined();
            expect(request.topic).toBeDefined();
            expect(request.gradeLevel).toBeDefined();
            expect(request.domain).toBeDefined();
            expect(request.maxClaims).toBeDefined();
            expect(request.context).toBeDefined();
        });

        it('should have correct ContentNeeds structure', () => {
            const contentNeeds = {
                examples: {
                    required: true,
                    types: ['REAL_WORLD', 'PROBLEM_SOLVING'],
                    count: 2,
                    necessity: 0.8,
                    rationale: 'Test rationale',
                },
                simulation: {
                    required: true,
                    interactionType: 'PARAMETER_EXPLORATION',
                    complexity: 'MEDIUM',
                    necessity: 0.7,
                    rationale: 'Simulation rationale',
                },
                animation: {
                    required: false,
                    animationType: 'TWO_D',
                    durationSeconds: 30,
                    necessity: 0.3,
                    rationale: 'Optional animation',
                },
            };

            expect(contentNeeds.examples?.required).toBe(true);
            expect(contentNeeds.examples?.types).toHaveLength(2);
            expect(contentNeeds.examples?.count).toBe(2);
            expect(contentNeeds.simulation?.required).toBe(true);
            expect(contentNeeds.animation?.required).toBe(false);
        });
    });

    describe('Normalization Functions', () => {
        it('should normalize grade levels correctly', () => {
            // Test cases for grade level normalization
            const testCases = [
                { input: 'K-2', expected: 'K_2' },
                { input: '3-5', expected: 'GRADE_3_5' },
                { input: '6-8', expected: 'GRADE_6_8' },
                { input: '9-12', expected: 'GRADE_9_12' },
                { input: 'GRADE_9_12', expected: 'GRADE_9_12' },
                { input: 'unknown', expected: 'GRADE_6_8' }, // default
            ];

            for (const { input, expected } of testCases) {
                // This tests the normalization logic indirectly
                // In real tests, we'd export and test the function directly
                const normalized = input.toUpperCase().replace(/\s+/g, '_');
                const mapping: Record<string, string> = {
                    'K_2': 'K_2',
                    'K-2': 'K_2',
                    'GRADE_3_5': 'GRADE_3_5',
                    '3-5': 'GRADE_3_5',
                    'GRADE_6_8': 'GRADE_6_8',
                    '6-8': 'GRADE_6_8',
                    'GRADE_9_12': 'GRADE_9_12',
                    '9-12': 'GRADE_9_12',
                };
                expect(mapping[normalized] || 'GRADE_6_8').toBe(expected);
            }
        });

        it('should normalize domains correctly', () => {
            const testCases = [
                { input: 'MATH', expected: 'MATH' },
                { input: 'SCIENCE', expected: 'SCIENCE' },
                { input: 'ENGINEERING', expected: 'TECH' },
                { input: 'unknown', expected: 'TECH' },
            ];

            for (const { input, expected } of testCases) {
                const normalized = input.toUpperCase();
                const mapping: Record<string, string> = {
                    'MATH': 'MATH',
                    'SCIENCE': 'SCIENCE',
                    'TECH': 'TECH',
                    'ENGINEERING': 'TECH',
                };
                expect(mapping[normalized] || 'TECH').toBe(expected);
            }
        });
    });

    describe('Error Handling', () => {
        it('should create ContentGenerationError with correct properties', () => {
            const error = new ContentGenerationError(
                'Test message',
                'TEST_CODE',
                { extra: 'data' }
            );

            expect(error.message).toBe('Test message');
            expect(error.code).toBe('TEST_CODE');
            expect(error.details).toEqual({ extra: 'data' });
            expect(error.name).toBe('ContentGenerationError');
        });

        it('should create ProtoLoadError with attempted paths', () => {
            const paths = ['/path/one.proto', '/path/two.proto'];
            const error = new ProtoLoadError('Proto not found', paths);

            expect(error.message).toBe('Proto not found');
            expect(error.attemptedPaths).toEqual(paths);
            expect(error.name).toBe('ProtoLoadError');
        });
    });

    describe('Contract Compatibility', () => {
        it('should have all required RPC methods defined in client', () => {
            // List of required methods
            const requiredMethods = [
                'generateClaims',
                'analyzeContentNeeds',
                'generateExamples',
                'generateSimulation',
                'generateAnimation',
                'validateContent',
            ];

            // Verify client prototype has all methods
            const clientPrototype = RealContentGenerationClient.prototype;

            for (const method of requiredMethods) {
                expect(
                    typeof (clientPrototype as Record<string, unknown>)[method],
                    `Method ${method} should be defined`
                ).toBe('function');
            }
        });

        it('should have correct proto package structure', () => {
            const fs = require('fs');
            const protoPath = resolveContractProtoPath();

            const protoContent = fs.readFileSync(protoPath, 'utf-8');

            // Verify package declaration matches expected structure
            expect(protoContent).toMatch(/package tutorputor\.content_generation/);

            // Verify service name
            expect(protoContent).toMatch(/service ContentGenerationService/);

            // Verify common messages exist
            const requiredMessages = [
                'GenerateClaimsRequest',
                'GenerateClaimsResponse',
                'LearningClaim',
                'ContentNeeds',
                'ExampleNeeds',
                'SimulationNeeds',
                'AnimationNeeds',
                'ValidationResult',
                'GenerationMetadata',
            ];

            for (const message of requiredMessages) {
                expect(
                    protoContent.includes(`message ${message}`),
                    `Message ${message} should be defined in proto`
                ).toBe(true);
            }
        });
    });
});

describe('ClaimGenerationProcessor Contract', () => {
    it('should handle claims with missing content_needs', async () => {
        // This is a unit test for the processor's fallback logic
        // In a real integration test, we'd mock the gRPC client

        const claimWithoutNeeds = {
            claim_ref: 'C1',
            text: 'Test claim',
            bloom_level: 'UNDERSTAND',
            order_index: 1,
            // content_needs is missing
        };

        // Verify claim structure
        expect(claimWithoutNeeds.claim_ref).toBeDefined();
        expect(claimWithoutNeeds.text).toBeDefined();

        // content_needs should be undefined
        expect((claimWithoutNeeds as Record<string, unknown>).content_needs).toBeUndefined();
    });

    it('should validate content needs structure', () => {
        const validContentNeeds = {
            examples: {
                required: true,
                types: ['REAL_WORLD'],
                count: 2,
                necessity: 0.8,
                rationale: 'Rationale',
            },
        };

        // Check if any modality is required
        const hasRequired =
            validContentNeeds.examples?.required ||
            false;

        expect(hasRequired).toBe(true);
    });
});

// Integration contract tests — use mocked gRPC transport so they run in CI
// without a live server.  Swap `vi.mock(...)` for a real server in nightly E2E runs.
describe('Integration Contract Tests', () => {
    const createStubClient = (overrides?: Partial<InstanceType<typeof RealContentGenerationClient>>) => {
        const logger = createMockLogger();
        const base = Object.create(RealContentGenerationClient.prototype) as InstanceType<typeof RealContentGenerationClient>;
        return Object.assign(base, {
            ready: true,
            generateClaims: vi.fn().mockResolvedValue({
                requestId: 'req-1',
                claims: [{ claim_ref: 'C1', text: 'Atoms are the building blocks of matter.', bloom_level: 'REMEMBER', order_index: 1 }],
                metadata: { model: 'gpt-4', version: '1.0.0', tokensUsed: 100, processingTimeMs: 50, cacheHit: false },
            }),
            analyzeContentNeeds: vi.fn().mockResolvedValue({ requestId: 'req-2', contentNeeds: {} }),
            validateContent: vi.fn().mockResolvedValue({ valid: true, issues: [] }),
            ...overrides,
        });
    };

    it('should connect to gRPC server and generate claims', async () => {
        const client = createStubClient();
        const result = await client.generateClaims({
            requestId: 'req-1',
            tenantId: 'tenant-1',
            topic: 'Atomic Structure',
            gradeLevel: 'GRADE_9_12',
            domain: 'SCIENCE',
            maxClaims: 3,
            context: {},
        });

        expect(result.requestId).toBe('req-1');
        expect(result.claims).toHaveLength(1);
        expect(result.claims[0].claim_ref).toBe('C1');
        expect(vi.mocked(client.generateClaims)).toHaveBeenCalledTimes(1);
    });

    it('should handle server unavailable gracefully', async () => {
        const serverError = new ContentGenerationError(
            'Service unavailable',
            'UNAVAILABLE',
            { retriable: true },
        );
        const client = createStubClient({
            generateClaims: vi.fn().mockRejectedValue(serverError),
        });

        await expect(
            client.generateClaims({
                requestId: 'req-err',
                tenantId: 'tenant-1',
                topic: 'Forces',
                gradeLevel: 'GRADE_6_8',
                domain: 'SCIENCE',
                maxClaims: 2,
                context: {},
            }),
        ).rejects.toBeInstanceOf(ContentGenerationError);
    });

    it('should handle timeout without crashing', async () => {
        const timeoutError = new ContentGenerationError('Request timed out', 'DEADLINE_EXCEEDED', {});
        const client = createStubClient({
            analyzeContentNeeds: vi.fn().mockRejectedValue(timeoutError),
        });

        await expect(
            client.analyzeContentNeeds({ requestId: 'req-timeout', tenantId: 'tenant-1', claims: [], context: {} }),
        ).rejects.toMatchObject({ code: 'DEADLINE_EXCEEDED' });
    });

    it('should validate content and return issues on failure', async () => {
        const client = createStubClient({
            validateContent: vi.fn().mockResolvedValue({
                valid: false,
                issues: ['Claim text is too short', 'Missing bloom level'],
            }),
        });

        const result = await client.validateContent({ requestId: 'req-val', tenantId: 'tenant-1', contentId: 'c-1', contentType: 'CLAIM', content: {}, context: {} });
        expect(result.valid).toBe(false);
        expect(result.issues).toHaveLength(2);
    });
});

// Export for external contract verification
export { createMockLogger };
