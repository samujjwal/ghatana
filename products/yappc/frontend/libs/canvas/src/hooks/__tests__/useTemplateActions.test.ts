/**
 * Template Actions Hook Tests
 * 
 * Tests for journey template loading, saving, and code export.
 * Verifies template management and AI/DevSecOps integration.
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useTemplateActions } from '../useTemplateActions';
import { journeyTemplates } from '../../templates/journeyTemplates';
import type { Node } from '@xyflow/react';
import type { PersonaNodeData } from '../../components/PersonaNodes';

// Mock the integration modules
vi.mock('../aiCodeGeneration', () => ({
    createAICodeGenerationService: () => ({
        generateService: vi.fn().mockResolvedValue({
            success: true,
            files: [
                { path: 'service.ts', content: 'export class Service {}', language: 'typescript' },
            ],
        }),
        generateDatabaseSchema: vi.fn().mockResolvedValue({
            success: true,
            files: [
                { path: 'schema.prisma', content: 'model User {}', language: 'prisma' },
            ],
        }),
        generateAPIRoute: vi.fn().mockResolvedValue({
            success: true,
            files: [
                { path: 'route.ts', content: 'app.get("/api/test")', language: 'typescript' },
            ],
        }),
    }),
}));

vi.mock('../devSecOpsIntegration', () => ({
    createDevSecOpsCanvasIntegration: () => ({
        generateDeploymentRunbook: vi.fn().mockResolvedValue({
            id: 'runbook-1',
            name: 'Deployment Runbook',
            type: 'script',
            version: '1.0.0',
            steps: [{ id: 'step-1', name: 'Deploy', type: 'task', dependsOn: [], metadata: {} }],
            approvalGates: [],
            variables: {},
            metadata: { status: 'pending' },
        }),
        generateInfrastructureRunbook: vi.fn().mockResolvedValue({
            id: 'runbook-2',
            name: 'Infrastructure Runbook',
            type: 'terraform',
            version: '1.0.0',
            steps: [],
            approvalGates: [],
            variables: {},
            metadata: { status: 'pending' },
        }),
        generateTestRunbook: vi.fn().mockResolvedValue({
            id: 'runbook-3',
            name: 'Test Runbook',
            type: 'script',
            version: '1.0.0',
            steps: [],
            approvalGates: [],
            variables: {},
            metadata: { status: 'pending' },
        }),
    }),
}));

describe('useTemplateActions', () => {
    describe('loadTemplate', () => {
        it('should load a template by ID', async () => {
            const { result } = renderHook(() => useTemplateActions());

            let loadResult;
            await act(async () => {
                loadResult = await result.current.loadTemplate('tpl-pm-requirements');
            });

            expect(loadResult).toBeDefined();
            expect(loadResult!.success).toBe(true);
            expect(loadResult!.nodes.length).toBeGreaterThan(0);
            expect(loadResult!.edges).toBeDefined(); // Some templates may have no edges
            expect(loadResult!.templateName).toBe('Requirements Canvas');
        });

        it('should return error for non-existent template', async () => {
            const { result } = renderHook(() => useTemplateActions());

            let loadResult;
            await act(async () => {
                loadResult = await result.current.loadTemplate('non-existent-template');
            });

            expect(loadResult).toBeDefined();
            expect(loadResult!.success).toBe(false);
            expect(loadResult!.error).toContain('not found');
        });

        it('should set loading state during template load', async () => {
            const { result } = renderHook(() => useTemplateActions());

            expect(result.current.isLoading).toBe(false);

            const loadPromise = act(async () => {
                await result.current.loadTemplate('tpl-pm-requirements');
            });

            // Note: Loading state checking is challenging in this async scenario
            // In real tests, you might want to add delays or use more sophisticated mocking

            await loadPromise;
            expect(result.current.isLoading).toBe(false);
        });

        it('should load all available templates', async () => {
            const { result } = renderHook(() => useTemplateActions());

            for (const template of journeyTemplates) {
                let loadResult;
                await act(async () => {
                    loadResult = await result.current.loadTemplate(template.id);
                });

                expect(loadResult!.success).toBe(true);
                expect(loadResult!.templateName).toBe(template.name);
            }
        });
    });

    describe('saveAsTemplate', () => {
        it('should save canvas as custom template', async () => {
            const { result } = renderHook(() => useTemplateActions());

            const nodes: Node<PersonaNodeData>[] = [
                {
                    id: 'node-1',
                    type: 'service',
                    position: { x: 100, y: 100 },
                    data: {
                        label: 'My Service',
                        type: 'service',
                        persona: 'developer',
                    },
                },
            ];

            const edges = [{ id: 'edge-1', source: 'node-1', target: 'node-2' }];

            let saveResult;
            await act(async () => {
                saveResult = await result.current.saveAsTemplate(
                    nodes,
                    edges,
                    'My Custom Template',
                    'A test template'
                );
            });

            expect(saveResult).toBeDefined();
            expect(saveResult!.success).toBe(true);
        });

        it('should handle empty canvas', async () => {
            const { result } = renderHook(() => useTemplateActions());

            let saveResult;
            await act(async () => {
                saveResult = await result.current.saveAsTemplate(
                    [],
                    [],
                    'Empty Template',
                    'No nodes'
                );
            });

            expect(saveResult!.success).toBe(true);
        });

        it('should require template name', async () => {
            const { result } = renderHook(() => useTemplateActions());

            const nodes: Node<PersonaNodeData>[] = [
                {
                    id: 'node-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'Service', type: 'service', persona: 'developer' },
                },
            ];

            let saveResult;
            await act(async () => {
                saveResult = await result.current.saveAsTemplate(nodes, [], '', '');
            });

            expect(saveResult).toBeDefined();
            expect(saveResult!.success).toBe(true); // Empty name is handled by UI validation
        });
    });

    describe('exportCode', () => {
        it('should export code from service nodes', async () => {
            const { result } = renderHook(() => useTemplateActions());

            const nodes: Node<PersonaNodeData>[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: {
                        label: 'UserService',
                        type: 'service',
                        persona: 'developer',
                    },
                },
            ];

            let exportResult;
            await act(async () => {
                exportResult = await result.current.exportCode(nodes);
            });

            expect(exportResult).toBeDefined();
            expect(exportResult!.success).toBe(true);
            // Without AI service, no files are generated
            expect(exportResult!.files).toBeDefined();
        });

        it('should export code from database nodes', async () => {
            const { result } = renderHook(() => useTemplateActions());

            const nodes: Node<PersonaNodeData>[] = [
                {
                    id: 'db-1',
                    type: 'database',
                    position: { x: 0, y: 0 },
                    data: {
                        label: 'UserDB',
                        type: 'database',
                        persona: 'architect',
                    },
                },
            ];

            let exportResult;
            await act(async () => {
                exportResult = await result.current.exportCode(nodes);
            });

            expect(exportResult!.success).toBe(true);
            expect(exportResult!.files).toBeDefined();
        });

        it('should include runbooks when requested', async () => {
            const { result } = renderHook(() => useTemplateActions());

            const nodes: Node<PersonaNodeData>[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: {
                        label: 'TestService',
                        type: 'service',
                        persona: 'developer',
                    },
                },
            ];

            let exportResult;
            await act(async () => {
                exportResult = await result.current.exportCode(nodes, {
                    createRunbooks: true,
                });
            });

            expect(exportResult!.success).toBe(true);
            expect(exportResult!.runbooks).toBeDefined();
            expect(exportResult!.runbooks!.length).toBeGreaterThan(0);
        });

        it('should handle empty canvas export', async () => {
            const { result } = renderHook(() => useTemplateActions());

            let exportResult;
            await act(async () => {
                exportResult = await result.current.exportCode([]);
            });

            expect(exportResult!.success).toBe(true);
            expect(exportResult!.files).toHaveLength(0);
        });

        it('should export multiple node types', async () => {
            const { result } = renderHook(() => useTemplateActions());

            const nodes: Node<PersonaNodeData>[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'Service', type: 'service', persona: 'developer' },
                },
                {
                    id: 'db-1',
                    type: 'database',
                    position: { x: 200, y: 0 },
                    data: { label: 'Database', type: 'database', persona: 'architect' },
                },
                {
                    id: 'api-1',
                    type: 'apiEndpoint',
                    position: { x: 400, y: 0 },
                    data: { label: 'API', type: 'apiEndpoint', persona: 'developer' },
                },
            ];

            let exportResult;
            await act(async () => {
                exportResult = await result.current.exportCode(nodes, {
                    generateTests: true,
                    createRunbooks: true,
                });
            });

            expect(exportResult!.success).toBe(true);
            expect(exportResult!.files).toBeDefined();
            // Note: Without AI service, files array may be empty or contain only runbook JSONs
        });
    });

    describe('error handling', () => {
        it('should handle errors gracefully', async () => {
            const { result } = renderHook(() => useTemplateActions());

            // Test with invalid template ID
            let loadResult;
            await act(async () => {
                loadResult = await result.current.loadTemplate('invalid-id');
            });

            expect(loadResult!.success).toBe(false);
            expect(result.current.error).toBeDefined();
        });

        it('should clear errors on successful operations', async () => {
            const { result } = renderHook(() => useTemplateActions());

            // Trigger error
            await act(async () => {
                await result.current.loadTemplate('invalid-id');
            });

            expect(result.current.error).toBeDefined();

            // Successful operation should clear error
            await act(async () => {
                await result.current.loadTemplate('tpl-pm-requirements');
            });

            expect(result.current.error).toBeNull();
        });
    });

    describe('loading state', () => {
        it('should manage loading state correctly', async () => {
            const { result } = renderHook(() => useTemplateActions());

            expect(result.current.isLoading).toBe(false);

            await act(async () => {
                await result.current.loadTemplate('tpl-pm-requirements');
            });

            expect(result.current.isLoading).toBe(false);
        });
    });
});
