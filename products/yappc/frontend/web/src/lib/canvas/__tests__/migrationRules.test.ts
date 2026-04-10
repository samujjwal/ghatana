// @ts-nocheck
/**
 * Migration Rules Tests
 * 
 * Tests the content type migration logic including:
 * - Data transformations between ArtifactType stages
 * - Compatibility checking
 * - Edge cases
 * 
 * @doc.type test
 * @doc.purpose Unit tests for migration rules
 * @doc.layer product
 */

import {
    migrateData,
    isCompatibleConversion,
    getCompatibleTypes,
    type CodeArtifactData,
    type DocumentationData,
    type DiagramData,
    type TestData,
} from '../../../lib/canvas/migrationRules';
import { ArtifactType } from '@/types/fow-stages';

describe('Migration Rules', () => {
    const codeData: CodeArtifactData = {
        code: 'function hello() { return "world"; }',
        language: 'javascript',
        filePath: 'src/hello.js',
    };

    describe('REQUIREMENT → ARCHITECTURE_DECISION_RECORD (code to documentation)', () => {
        it('should produce markdown documentation from code', () => {
            const result = migrateData(
                codeData,
                ArtifactType.REQUIREMENT,
                ArtifactType.ARCHITECTURE_DECISION_RECORD,
            );

            expect(result).toBeTruthy();
            expect(result?.content).toContain('```javascript');
            expect(result?.content).toContain(codeData.code);
            expect(result?.format).toBe('markdown');
        });

        it('should handle missing filePath gracefully', () => {
            const dataNoPath: CodeArtifactData = { code: 'const x = 1;', language: 'javascript' };
            const result = migrateData(
                dataNoPath,
                ArtifactType.REQUIREMENT,
                ArtifactType.ARCHITECTURE_DECISION_RECORD,
            );
            expect(result).toBeTruthy();
            expect(result?.content).toContain('N/A');
        });
    });

    describe('REQUIREMENT → RELEASE_PACKET (code to test scaffolding)', () => {
        it('should generate test cases from code', () => {
            const result = migrateData(
                codeData,
                ArtifactType.REQUIREMENT,
                ArtifactType.RELEASE_PACKET,
            );

            expect(result).toBeTruthy();
            expect(result?.testCases).toBeDefined();
            expect(result?.testCases.length).toBeGreaterThan(0);
            expect(result?.framework).toBe('jest');
        });

        it('should use pytest for Python code', () => {
            const pyData: CodeArtifactData = {
                code: 'def hello(): import os; return "world"',
                language: 'python',
            };
            const result = migrateData(
                pyData,
                ArtifactType.REQUIREMENT,
                ArtifactType.RELEASE_PACKET,
            );
            expect(result?.framework).toBe('pytest');
        });
    });

    describe('REQUIREMENT → DELIVERY_EVIDENCE (code to API spec)', () => {
        it('should parse REST annotations from code', () => {
            const apiCode: CodeArtifactData = {
                code: `@GET('/api/users')\npublic getUsers() {}\n@POST('/api/users')\npublic createUser() {}`,
                language: 'java',
            };

            const result = migrateData(
                apiCode,
                ArtifactType.REQUIREMENT,
                ArtifactType.DELIVERY_EVIDENCE,
            );

            expect(result).toBeTruthy();
            expect(result?.endpoints).toBeDefined();
            expect(result?.endpoints.length).toBeGreaterThan(0);
        });

        it('should return empty endpoints for code without REST annotations', () => {
            const result = migrateData(
                codeData,
                ArtifactType.REQUIREMENT,
                ArtifactType.DELIVERY_EVIDENCE,
            );
            expect(result).toBeTruthy();
            expect(Array.isArray(result?.endpoints)).toBe(true);
        });
    });

    describe('ARCHITECTURE_DECISION_RECORD → PLAN (diagram to implementation plan)', () => {
        const diagramData: DiagramData = {
            nodes: [
                { id: '1', label: 'Frontend', type: 'service' },
                { id: '2', label: 'Backend', type: 'service' },
            ],
            edges: [
                { from: '1', to: '2', label: 'API calls' },
            ],
        };

        it('should generate implementation plan from diagram', () => {
            const result = migrateData(
                diagramData,
                ArtifactType.ARCHITECTURE_DECISION_RECORD,
                ArtifactType.PLAN,
            );

            expect(result).toBeTruthy();
            expect(result?.content).toContain('Implementation Plan');
            expect(result?.content).toContain('Frontend');
            expect(result?.content).toContain('Backend');
            expect(result?.format).toBe('markdown');
        });

        it('should include edge descriptions in the plan', () => {
            const result = migrateData(
                diagramData,
                ArtifactType.ARCHITECTURE_DECISION_RECORD,
                ArtifactType.PLAN,
            );
            expect(result?.content).toContain('API calls');
        });
    });

    describe('PLAN → DEVSECOPS_ITEM (documentation to extracted code)', () => {
        it('should extract code blocks from documentation', () => {
            const docData: DocumentationData = {
                content: '# Guide\n\n```javascript\nfunction login() {}\n```\n\nSome text',
                format: 'markdown',
            };

            const result = migrateData(
                docData,
                ArtifactType.PLAN,
                ArtifactType.DEVSECOPS_ITEM,
            );

            expect(result).toBeTruthy();
            expect(result?.content).toContain('function login()');
            expect(result?.language).toBeDefined();
        });

        it('should return fallback comment when no code blocks are present', () => {
            const docNoCode: DocumentationData = {
                content: 'No code here, just text',
                format: 'markdown',
            };

            const result = migrateData(
                docNoCode,
                ArtifactType.PLAN,
                ArtifactType.DEVSECOPS_ITEM,
            );

            expect(result).toBeTruthy();
            expect(result?.content).toContain('No code blocks found');
        });
    });

    describe('RELEASE_PACKET → OPS_BASELINE (test data to ops baseline)', () => {
        const testData: TestData = {
            testCases: [
                {
                    name: 'Test user login',
                    steps: ['Open login page', 'Enter credentials', 'Click submit'],
                    expected: 'User is logged in successfully',
                },
                {
                    name: 'Test invalid credentials',
                    steps: ['Enter invalid credentials'],
                    expected: 'Error message is displayed',
                },
            ],
            framework: 'jest',
        };

        it('should create ops baseline from test cases', () => {
            const result = migrateData(
                testData,
                ArtifactType.RELEASE_PACKET,
                ArtifactType.OPS_BASELINE,
            );

            expect(result).toBeTruthy();
            expect(result?.content).toContain('Operations Baseline');
            expect(result?.content).toContain('Test user login');
            expect(result?.content).toContain('Test invalid credentials');
            expect(result?.format).toBe('markdown');
        });
    });

    describe('Compatibility Checking', () => {
        it('should return true for compatible conversions', () => {
            expect(isCompatibleConversion(ArtifactType.REQUIREMENT, ArtifactType.ARCHITECTURE_DECISION_RECORD)).toBe(true);
            expect(isCompatibleConversion(ArtifactType.REQUIREMENT, ArtifactType.RELEASE_PACKET)).toBe(true);
            expect(isCompatibleConversion(ArtifactType.REQUIREMENT, ArtifactType.DELIVERY_EVIDENCE)).toBe(true);
            expect(isCompatibleConversion(ArtifactType.ARCHITECTURE_DECISION_RECORD, ArtifactType.PLAN)).toBe(true);
            expect(isCompatibleConversion(ArtifactType.PLAN, ArtifactType.DEVSECOPS_ITEM)).toBe(true);
            expect(isCompatibleConversion(ArtifactType.RELEASE_PACKET, ArtifactType.OPS_BASELINE)).toBe(true);
        });

        it('should return false for incompatible conversions', () => {
            expect(isCompatibleConversion(ArtifactType.OPS_BASELINE, ArtifactType.REQUIREMENT)).toBe(false);
            expect(isCompatibleConversion(ArtifactType.WORKSPACE, ArtifactType.PLAN)).toBe(false);
        });

        it('should get list of compatible types', () => {
            const reqCompatible = getCompatibleTypes(ArtifactType.REQUIREMENT);
            expect(reqCompatible).toContain(ArtifactType.ARCHITECTURE_DECISION_RECORD);
            expect(reqCompatible).toContain(ArtifactType.RELEASE_PACKET);
            expect(reqCompatible).toContain(ArtifactType.DELIVERY_EVIDENCE);

            const planCompatible = getCompatibleTypes(ArtifactType.PLAN);
            expect(planCompatible).toContain(ArtifactType.DEVSECOPS_ITEM);
        });

        it('should return empty array for types with no compatible conversions', () => {
            const compatible = getCompatibleTypes(ArtifactType.WORKSPACE);
            expect(Array.isArray(compatible)).toBe(true);
            expect(compatible.length).toBe(0);
        });
    });

    describe('Edge Cases and Error Handling', () => {
        it('should return null for unsupported migrations', () => {
            const result = migrateData(
                { some: 'data' },
                ArtifactType.OPS_BASELINE,
                ArtifactType.IDEA_BRIEF,
            );
            expect(result).toBeNull();
        });

        it('should handle empty code gracefully', () => {
            const emptyCode: CodeArtifactData = { code: '', language: 'javascript' };
            const result = migrateData(
                emptyCode,
                ArtifactType.REQUIREMENT,
                ArtifactType.ARCHITECTURE_DECISION_RECORD,
            );
            expect(result).toBeTruthy();
        });

        it('should handle documentation without code blocks', () => {
            const docWithoutCode: DocumentationData = {
                content: 'Just plain text, no code here',
                format: 'markdown',
            };
            const result = migrateData(
                docWithoutCode,
                ArtifactType.PLAN,
                ArtifactType.DEVSECOPS_ITEM,
            );
            expect(result).toBeTruthy();
            expect(result?.content).toContain('No code blocks found');
        });

        it('should handle malformed data with error recovery', () => {
            // Should not throw, should return null on error
            expect(() => {
                migrateData({ unexpected: 'structure' }, ArtifactType.OPS_BASELINE, ArtifactType.REQUIREMENT);
            }).not.toThrow();
        });

        it('should handle requirements without acceptance criteria', () => {
            const codeNoFile: CodeArtifactData = {
                code: 'const simple = true;',
                language: 'javascript',
            };
            const result = migrateData(
                codeNoFile,
                ArtifactType.REQUIREMENT,
                ArtifactType.RELEASE_PACKET,
            );
            expect(result).toBeTruthy();
            expect(result?.testCases).toBeDefined();
            expect(result?.testCases.length).toBeGreaterThan(0);
        });
    });

    describe('Language Detection in migrations', () => {
        it('should detect JavaScript and include in documentation', () => {
            const jsCode: CodeArtifactData = {
                code: 'const x = 10; function test() {}',
                language: 'javascript',
            };
            const result = migrateData(
                jsCode,
                ArtifactType.REQUIREMENT,
                ArtifactType.ARCHITECTURE_DECISION_RECORD,
            );
            expect(result?.content).toContain('javascript');
        });

        it('should detect Python and use pytest as framework', () => {
            const pyCode: CodeArtifactData = {
                code: 'def hello(): import os',
                language: 'python',
            };
            const result = migrateData(
                pyCode,
                ArtifactType.REQUIREMENT,
                ArtifactType.RELEASE_PACKET,
            );
            expect(result?.framework).toBe('pytest');
        });

        it('should detect Java language in code documentation', () => {
            const javaCode: CodeArtifactData = {
                code: 'public class Test { private int x; }',
                language: 'java',
            };
            const result = migrateData(
                javaCode,
                ArtifactType.REQUIREMENT,
                ArtifactType.ARCHITECTURE_DECISION_RECORD,
            );
            expect(result?.content).toContain('java');
        });
    });

    describe('Complex Migrations', () => {
        it('should preserve node structure in diagram to plan migration', () => {
            const archData: DiagramData = {
                nodes: [
                    { id: '1', label: 'Frontend', type: 'service' },
                    { id: '2', label: 'Backend', type: 'service' },
                ],
                edges: [
                    { from: '1', to: '2', label: 'API calls' },
                ],
            };
            const result = migrateData(
                archData,
                ArtifactType.ARCHITECTURE_DECISION_RECORD,
                ArtifactType.PLAN,
            );
            expect(result).toBeTruthy();
            expect(result?.content).toContain('Frontend');
            expect(result?.content).toContain('Backend');
        });

        it('should extract a code block from documentation with multiple blocks', () => {
            const docWithMultipleBlocks: DocumentationData = {
                content: '# Examples\n```javascript\nfunction a() {}\n```\nText\n```javascript\nfunction b() {}\n```',
                format: 'markdown',
            };
            const result = migrateData(
                docWithMultipleBlocks,
                ArtifactType.PLAN,
                ArtifactType.DEVSECOPS_ITEM,
            );
            expect(result).toBeTruthy();
            expect(result?.content).toContain('function a()');
        });
    });
});
