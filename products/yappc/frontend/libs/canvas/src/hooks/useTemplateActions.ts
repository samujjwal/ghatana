/**
 * Canvas Template Actions Hook
 * 
 * Provides actions for loading, saving, and exporting journey templates.
 * Integrates with journey templates, AI code generation, and DevSecOps workflows.
 * 
 * @module useTemplateActions
 */

import { useCallback, useState } from 'react';
import type { Node } from '@xyflow/react';
import { journeyTemplates, type JourneyTemplate, type JourneyNode } from '../templates/journeyTemplates';
import type { PersonaNodeData } from '../components/PersonaNodes';
import { createAICodeGenerationService } from '../integration/aiCodeGeneration';
import { createDevSecOpsCanvasIntegration } from '../integration/devSecOpsIntegration';
import type { IAIService } from '@ghatana/yappc-ai/core';

/**
 *
 */
export interface TemplateLoadResult {
    success: boolean;
    nodes: Node<PersonaNodeData>[];
    edges: Array<{ id: string; source: string; target: string }>;
    templateName?: string;
    error?: string;
}

/**
 *
 */
export interface CodeExportResult {
    success: boolean;
    files: Array<{
        path: string;
        content: string;
        language: string;
    }>;
    runbooks?: Array<{
        name: string;
        type: string;
        steps: number;
    }>;
    error?: string;
}

/**
 *
 */
export interface TemplateActions {
    /** Load a journey template by ID */
    loadTemplate: (templateId: string) => Promise<TemplateLoadResult>;
    /** Save current canvas as a custom template */
    saveAsTemplate: (
        nodes: Node<PersonaNodeData>[],
        edges: Array<{ id: string; source: string; target: string }>,
        templateName: string,
        description?: string
    ) => Promise<{ success: boolean; error?: string }>;
    /** Export code and runbooks from canvas nodes */
    exportCode: (
        nodes: Node<PersonaNodeData>[],
        options?: {
            generateTests?: boolean;
            createRunbooks?: boolean;
            aiService?: IAIService;
        }
    ) => Promise<CodeExportResult>;
    /** Loading state */
    isLoading: boolean;
    /** Current error message */
    error: string | null;
}

/**
 * Hook for canvas template actions
 */
export function useTemplateActions(): TemplateActions {
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    /**
     * Load a journey template by ID
     */
    const loadTemplate = useCallback(async (templateId: string): Promise<TemplateLoadResult> => {
        setIsLoading(true);
        setError(null);

        try {
            const template = journeyTemplates.find((t) => t.id === templateId);

            if (!template) {
                throw new Error(`Template ${templateId} not found`);
            }

            // Convert template to ReactFlow nodes and edges
            const nodes = template.nodes;

            const edges = template.edges.map((edge) => ({
                id: `${edge.source}-${edge.target}`,
                source: edge.source,
                target: edge.target,
                type: 'smoothstep',
                animated: true,
            }));

            return {
                success: true,
                nodes: nodes as unknown as Node<PersonaNodeData>[],
                edges,
                templateName: template.name,
            };
        } catch (err) {
            const errorMessage = err instanceof Error ? err.message : 'Failed to load template';
            setError(errorMessage);
            return {
                success: false,
                nodes: [],
                edges: [],
                error: errorMessage,
            };
        } finally {
            setIsLoading(false);
        }
    }, []);

    /**
     * Save current canvas as a custom template
     */
    const saveAsTemplate = useCallback(
        async (
            nodes: Node<PersonaNodeData>[],
            edges: Array<{ id: string; source: string; target: string }>,
            templateName: string,
            description?: string
        ): Promise<{ success: boolean; error?: string }> => {
            setIsLoading(true);
            setError(null);

            try {
                // Create template object
                const template: JourneyTemplate = {
                    id: `custom-${Date.now()}`,
                    name: templateName,
                    description: description || `Custom template: ${templateName}`,
                    persona: 'pm', // Default, could be inferred from nodes
                    category: 'requirements',
                    tags: ['custom'],
                    nodes: nodes as JourneyNode[],
                    edges: edges.map((edge) => ({
                        ...edge,
                        id: edge.id,
                        source: edge.source,
                        target: edge.target,
                    })),
                };

                // In a real implementation, this would persist to backend/localStorage
                // For now, we'll just add it to the journeyTemplates array temporarily
                journeyTemplates.push(template);

                // Could trigger a callback or emit an event here
                console.log('Template saved:', template);

                return { success: true };
            } catch (err) {
                const errorMessage = err instanceof Error ? err.message : 'Failed to save template';
                setError(errorMessage);
                return {
                    success: false,
                    error: errorMessage,
                };
            } finally {
                setIsLoading(false);
            }
        },
        []
    );

    /**
     * Export code and runbooks from canvas nodes
     */
    const exportCode = useCallback(
        async (
            nodes: Node<PersonaNodeData>[],
            options?: {
                generateTests?: boolean;
                createRunbooks?: boolean;
                aiService?: IAIService;
            }
        ): Promise<CodeExportResult> => {
            setIsLoading(true);
            setError(null);

            try {
                const files: Array<{ path: string; content: string; language: string }> = [];
                const runbooks: Array<{ name: string; type: string; steps: number }> = [];

                // Initialize services if AI is available
                const aiCodeGen = options?.aiService
                    ? createAICodeGenerationService(options.aiService)
                    : null;
                const devSecOps = createDevSecOpsCanvasIntegration();

                // Process each node
                for (const node of nodes) {
                    const nodeData = node.data;

                    // Generate code based on node type
                    switch (nodeData.type) {
                        case 'service':
                            if (aiCodeGen) {
                                const serviceResult = await aiCodeGen.generateService(nodeData as unknown, {
                                    includeTests: options?.generateTests,
                                });
                                if (serviceResult.success && serviceResult.files) {
                                    files.push(...serviceResult.files.map(f => ({
                                        path: f.path,
                                        content: f.content,
                                        language: f.language,
                                    })));
                                }
                            }

                            // Generate deployment runbook
                            if (options?.createRunbooks) {
                                const deploymentRunbook = await devSecOps.generateDeploymentRunbook(
                                    nodeData as unknown,
                                    {
                                        environment: 'production',
                                        strategy: 'rolling',
                                        autoRollback: true,
                                        requireApproval: true,
                                    }
                                );
                                runbooks.push({
                                    name: deploymentRunbook.name,
                                    type: deploymentRunbook.type,
                                    steps: deploymentRunbook.steps.length,
                                });

                                files.push({
                                    path: `runbooks/${nodeData.label}-deployment.json`,
                                    content: JSON.stringify(deploymentRunbook, null, 2),
                                    language: 'json',
                                });
                            }
                            break;

                        case 'database':
                            if (aiCodeGen) {
                                const schemaResult = await aiCodeGen.generateDatabaseSchema(nodeData as unknown);
                                if (schemaResult.success && schemaResult.files) {
                                    files.push(...schemaResult.files.map(f => ({
                                        path: f.path,
                                        content: f.content,
                                        language: f.language,
                                    })));
                                }
                            }

                            // Generate infrastructure runbook
                            if (options?.createRunbooks) {
                                const infraRunbook = await devSecOps.generateInfrastructureRunbook(
                                    nodeData as unknown
                                );
                                runbooks.push({
                                    name: infraRunbook.name,
                                    type: infraRunbook.type,
                                    steps: infraRunbook.steps.length,
                                });

                                files.push({
                                    path: `runbooks/${nodeData.label}-infrastructure.json`,
                                    content: JSON.stringify(infraRunbook, null, 2),
                                    language: 'json',
                                });
                            }
                            break;

                        case 'apiEndpoint':
                            if (aiCodeGen) {
                                const routeResult = await aiCodeGen.generateAPIRoute(nodeData as unknown);
                                if (routeResult.success && routeResult.files) {
                                    files.push(...routeResult.files.map(f => ({
                                        path: f.path,
                                        content: f.content,
                                        language: f.language,
                                    })));
                                }
                            }
                            break;

                        case 'testSuite':
                            if (options?.createRunbooks) {
                                const testRunbook = await devSecOps.generateTestRunbook(nodeData as unknown);
                                runbooks.push({
                                    name: testRunbook.name,
                                    type: testRunbook.type,
                                    steps: testRunbook.steps.length,
                                });

                                files.push({
                                    path: `runbooks/${nodeData.label}-tests.json`,
                                    content: JSON.stringify(testRunbook, null, 2),
                                    language: 'json',
                                });
                            }
                            break;
                    }
                }

                return {
                    success: true,
                    files,
                    runbooks,
                };
            } catch (err) {
                const errorMessage = err instanceof Error ? err.message : 'Failed to export code';
                setError(errorMessage);
                return {
                    success: false,
                    files: [],
                    error: errorMessage,
                };
            } finally {
                setIsLoading(false);
            }
        },
        []
    );

    return {
        loadTemplate,
        saveAsTemplate,
        exportCode,
        isLoading,
        error,
    };
}
