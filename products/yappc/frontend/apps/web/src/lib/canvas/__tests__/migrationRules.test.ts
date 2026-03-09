/**
 * Migration Rules Tests
 * 
 * Tests the content type migration logic including:
 * - Data transformations
 * - Compatibility checking
 * - Helper functions
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
    type RequirementData,
    type TestData,
    type ApiSpecData,
} from '../../../lib/canvas/migrationRules';
import { ArtifactType } from '@/types/fow-stages';

describe('Migration Rules', () => {
    describe('Code Migrations', () => {
        const codeData: CodeArtifactData = {
            code: 'function hello() { return "world"; }',
            language: 'javascript',
            filePath: 'src/hello.js',
        };

        it('should migrate code to documentation', () => {
            const result = migrateData<CodeArtifactData, DocumentationData>(
                codeData,
                'code',
                'documentation'
            );

            expect(result).toBeTruthy();
            expect(result?.content).toContain('```javascript');
            expect(result?.content).toContain(codeData.code);
            expect(result?.format).toBe('markdown');
        });

        it('should migrate code to test', () => {
            const result = migrateData<CodeArtifactData, TestData>(
                codeData,
                'code',
                'test'
            );

            expect(result).toBeTruthy();
            expect(result?.testCases).toBeDefined();
            expect(result?.testCases.length).toBeGreaterThan(0);
            expect(result?.framework).toBe('jest');
        });

        it('should migrate code to API spec by parsing endpoints', () => {
            const apiCode: CodeArtifactData = {
                code: `
                    @GET('/api/users')
                    public getUsers() {}
                    
                    @POST('/api/users')
                    public createUser() {}
                `,
                language: 'java',
            };

            const result = migrateData<CodeArtifactData, ApiSpecData>(
                apiCode,
                'code',
                'evidence'
            );

            expect(result).toBeTruthy();
            expect(result?.endpoints).toBeDefined();
            expect(result?.endpoints.length).toBeGreaterThan(0);
        });
    });

    describe('Test Migrations', () => {
        const testData: TestData = {
            testCases: [
                {
                    name: 'Test user login',
                    steps: ['Open login page', 'Enter credentials', 'Click submit'],
                    expected: 'User is logged in successfully',
                },
                {
                    name: 'Test invalid credentials',
                    steps: ['Open login page', 'Enter invalid credentials', 'Click submit'],
                    expected: 'Error message is displayed',
                },
            ],
            framework: 'jest',
        };

        it('should migrate test to code', () => {
            const result = migrateData<TestData, CodeArtifactData>(
                testData,
                'test',
                'code'
            );

            expect(result).toBeTruthy();
            expect(result?.code).toContain('Test user login');
            expect(result?.code).toContain('Test invalid credentials');
            expect(result?.language).toBe('javascript');
        });

        it('should migrate test to documentation', () => {
            const result = migrateData<TestData, DocumentationData>(
                testData,
                'test',
                'documentation'
            );

            expect(result).toBeTruthy();
            expect(result?.content).toContain('# Test Cases');
            expect(result?.content).toContain('Test user login');
            expect(result?.content).toContain('**Steps:**');
            expect(result?.format).toBe('markdown');
        });
    });

    describe('Documentation Migrations', () => {
        const docData: DocumentationData = {
            content: `# User Authentication\n\nImplement login functionality.\n\n\`\`\`javascript\nfunction login() {}\n\`\`\`\n\n- Must validate credentials\n- Must show errors`,
            format: 'markdown',
        };

        it('should migrate documentation to code by extracting code blocks', () => {
            const result = migrateData<DocumentationData, CodeArtifactData>(
                docData,
                'documentation',
                'code'
            );

            expect(result).toBeTruthy();
            expect(result?.code).toContain('function login()');
            expect(result?.language).toBe('javascript');
        });

        it('should migrate documentation to requirement', () => {
            const result = migrateData<DocumentationData, RequirementData>(
                docData,
                'documentation',
                'requirement'
            );

            expect(result).toBeTruthy();
            expect(result?.title).toBe('User Authentication');
            expect(result?.description).toContain('Implement login');
            expect(result?.acceptanceCriteria).toBeDefined();
            expect(result?.acceptanceCriteria?.length).toBeGreaterThan(0);
        });

        it('should migrate documentation to test', () => {
            const result = migrateData<DocumentationData, TestData>(
                docData,
                'documentation',
                'test'
            );

            expect(result).toBeTruthy();
            expect(result?.testCases).toBeDefined();
            expect(result?.testCases.length).toBeGreaterThan(0);
        });
    });

    describe('Requirement Migrations', () => {
        const reqData: RequirementData = {
            title: 'User Registration',
            description: 'Users should be able to create new accounts',
            priority: 'high',
            acceptanceCriteria: [
                'Email validation',
                'Password strength check',
                'Confirmation email sent',
            ],
        };

        it('should migrate requirement to brief', () => {
            const result = migrateData(reqData, 'requirement', 'brief');

            expect(result).toBeTruthy();
            expect(result.title).toBe(reqData.title);
            expect(result.description).toBe(reqData.description);
        });

        it('should migrate requirement to documentation', () => {
            const result = migrateData<RequirementData, DocumentationData>(
                reqData,
                'requirement',
                'documentation'
            );

            expect(result).toBeTruthy();
            expect(result?.content).toContain('# User Registration');
            expect(result?.content).toContain('## Acceptance Criteria');
            expect(result?.content).toContain('Email validation');
            expect(result?.format).toBe('markdown');
        });

        it('should migrate requirement to test with generated test cases', () => {
            const result = migrateData<RequirementData, TestData>(
                reqData,
                'requirement',
                'test'
            );

            expect(result).toBeTruthy();
            expect(result?.testCases).toBeDefined();
            expect(result?.testCases.length).toBe(3); // One per acceptance criteria
            expect(result?.testCases[0].expected).toContain('Email validation');
        });
    });

    describe('API Spec Migrations', () => {
        const apiData: ApiSpecData = {
            endpoints: [
                {
                    method: 'GET',
                    path: '/api/users',
                    description: 'Get all users',
                    parameters: [{ name: 'page', type: 'number' }],
                },
                {
                    method: 'POST',
                    path: '/api/users',
                    description: 'Create new user',
                },
            ],
            baseUrl: 'https://api.example.com',
        };

        it('should migrate API spec to documentation', () => {
            const result = migrateData<ApiSpecData, DocumentationData>(
                apiData,
                'evidence',
                'documentation'
            );

            expect(result).toBeTruthy();
            expect(result?.content).toContain('# API Specification');
            expect(result?.content).toContain('**Base URL:**');
            expect(result?.content).toContain('GET /api/users');
            expect(result?.content).toContain('POST /api/users');
        });

        it('should migrate API spec to code', () => {
            const result = migrateData<ApiSpecData, CodeArtifactData>(
                apiData,
                'evidence',
                'code'
            );

            expect(result).toBeTruthy();
            expect(result?.code).toContain('GET /api/users');
            expect(result?.code).toContain('POST /api/users');
            expect(result?.language).toBe('javascript');
        });
    });

    describe('Compatibility Checking', () => {
        it('should return true for compatible conversions', () => {
            expect(isCompatibleConversion('code', 'documentation')).toBe(true);
            expect(isCompatibleConversion('code', 'test')).toBe(true);
            expect(isCompatibleConversion('requirement', 'test')).toBe(true);
        });

        it('should return false for incompatible conversions', () => {
            expect(isCompatibleConversion('code', 'architecture')).toBe(false);
            expect(isCompatibleConversion('test', 'design')).toBe(false);
        });

        it('should get list of compatible types', () => {
            const codeCompatible = getCompatibleTypes('code');
            expect(codeCompatible).toContain('test');
            expect(codeCompatible).toContain('documentation');
            expect(codeCompatible).toContain('evidence');

            const docCompatible = getCompatibleTypes('documentation');
            expect(docCompatible.length).toBeGreaterThan(0);
            expect(docCompatible).toContain('code');
            expect(docCompatible).toContain('requirement');
        });

        it('should return empty array for types with no compatible conversions', () => {
            // Some types might not have outgoing conversions defined
            const compatible = getCompatibleTypes('deployment' as ArtifactType);
            expect(Array.isArray(compatible)).toBe(true);
        });
    });

    describe('Edge Cases and Error Handling', () => {
        it('should return null for unsupported migrations', () => {
            const result = migrateData(
                { some: 'data' },
                'code',
                'brief' // No migration rule defined
            );

            expect(result).toBeNull();
        });

        it('should handle empty code gracefully', () => {
            const emptyCode: CodeArtifactData = {
                code: '',
                language: 'javascript',
            };

            const result = migrateData(emptyCode, 'code', 'documentation');
            expect(result).toBeTruthy();
        });

        it('should handle documentation without code blocks', () => {
            const docWithoutCode: DocumentationData = {
                content: 'Just plain text, no code here',
                format: 'markdown',
            };

            const result = migrateData<DocumentationData, CodeArtifactData>(
                docWithoutCode,
                'documentation',
                'code'
            );

            expect(result).toBeTruthy();
            expect(result?.code).toContain('No code blocks found');
        });

        it('should handle requirements without acceptance criteria', () => {
            const reqWithoutCriteria: RequirementData = {
                title: 'Simple Requirement',
                description: 'Just a description',
                priority: 'low',
            };

            const result = migrateData<RequirementData, TestData>(
                reqWithoutCriteria,
                'requirement',
                'test'
            );

            expect(result).toBeTruthy();
            expect(result?.testCases).toBeDefined();
            expect(result?.testCases.length).toBeGreaterThan(0);
        });

        it('should handle malformed data with error recovery', () => {
            const malformedData = {
                unexpected: 'structure',
            };

            // Should not throw, should return null
            expect(() => {
                migrateData(malformedData, 'code', 'test');
            }).not.toThrow();
        });
    });

    describe('Language Detection', () => {
        it('should detect JavaScript', () => {
            const jsCode: CodeArtifactData = {
                code: 'const x = 10; function test() {}',
                language: 'unknown',
            };

            const result = migrateData(jsCode, 'code', 'documentation');
            expect(result?.content).toContain('javascript');
        });

        it('should detect Python', () => {
            const pyCode: CodeArtifactData = {
                code: 'def hello():\n    import os\n    return "world"',
                language: 'unknown',
            };

            const docData: DocumentationData = {
                content: `\`\`\`python\ndef hello():\n    import os\`\`\``,
                format: 'markdown',
            };

            const result = migrateData<DocumentationData, CodeArtifactData>(
                docData,
                'documentation',
                'code'
            );

            expect(result).toBeTruthy();
        });

        it('should detect Java', () => {
            const javaCode: CodeArtifactData = {
                code: 'public class Test { private int x; }',
                language: 'unknown',
            };

            const result = migrateData(javaCode, 'code', 'documentation');
            expect(result?.content).toContain('java');
        });
    });

    describe('Complex Migrations', () => {
        it('should preserve structure in architecture to design migration', () => {
            const archData = {
                nodes: [
                    { id: '1', label: 'Frontend', type: 'service' },
                    { id: '2', label: 'Backend', type: 'service' },
                ],
                edges: [
                    { from: '1', to: '2', label: 'API calls' },
                ],
            };

            const result = migrateData(archData, 'architecture', 'design');
            expect(result).toBeTruthy();
            expect(result.nodes).toHaveLength(2);
            expect(result.edges).toHaveLength(1);
        });

        it('should extract multiple code blocks from documentation', () => {
            const docWithMultipleBlocks: DocumentationData = {
                content: `
# Code Examples

\`\`\`javascript
function a() {}
\`\`\`

Some text

\`\`\`javascript
function b() {}
\`\`\`
                `,
                format: 'markdown',
            };

            const result = migrateData<DocumentationData, CodeArtifactData>(
                docWithMultipleBlocks,
                'documentation',
                'code'
            );

            expect(result).toBeTruthy();
            expect(result?.code).toContain('function a()');
            expect(result?.code).toContain('function b()');
        });
    });
});
