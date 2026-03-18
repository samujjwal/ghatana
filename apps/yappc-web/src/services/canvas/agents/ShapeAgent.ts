/**
 * Shape Agent Implementation
 * 
 * Autonomous agent that suggests architectural patterns and connections
 * 
 * @doc.type service
 * @doc.purpose Autonomous agent for SHAPE phase
 * @doc.layer product
 * @doc.pattern Agent
 */

import {
    ShapeAgentContract,
    AgentExecutionContext,
    AgentAction,
} from './AgentContract';
import { agentExecutor } from './AgentExecutor';
import type { AISuggestion } from '../../../hooks/useAIAssistant';

// ============================================================================
// Shape Agent
// ============================================================================

/**
 * Shape Agent Result
 */
export interface ShapeAgentResult {
    suggestions: AISuggestion[];
    patterns: {
        detected: string[];
        suggested: string[];
    };
    gaps: string[];
    connections: {
        source: string;
        target: string;
        reason: string;
    }[];
}

/**
 * Shape Agent
 * Analyzes canvas and suggests architectural improvements
 */
export class ShapeAgent {
    /**
     * Execute shape agent analysis
     */
    async analyze(context: AgentExecutionContext): Promise<ShapeAgentResult> {
        // Execute within contract
        const result = await agentExecutor.executeAgent(
            ShapeAgentContract,
            context,
            async (ctx) => {
                return this.performAnalysis(ctx);
            }
        );

        if (!result.success) {
            throw new Error(`Shape agent failed: ${result.errors?.join(', ')}`);
        }

        return result.artifacts;
    }

    /**
     * Perform the actual analysis
     */
    private async performAnalysis(
        context: AgentExecutionContext
    ): Promise<ShapeAgentResult> {
        const { canvasState } = context;
        const suggestions: AISuggestion[] = [];
        const detectedPatterns: string[] = [];
        const suggestedPatterns: string[] = [];
        const gaps: string[] = [];
        const suggestedConnections: Array<{ source: string; target: string; reason: string }> = [];

        // Analyze existing elements
        const elements = canvasState.elements || [];
        const connections = canvasState.connections || [];

        // Detect patterns
        const hasApi = elements.some((el) => el.type === 'api');
        const hasData = elements.some((el) => el.type === 'data');
        const hasComponent = elements.some((el) => el.type === 'component');
        const hasFlow = elements.some((el) => el.type === 'flow');

        if (hasApi && hasData && hasComponent) {
            detectedPatterns.push('Three-tier architecture');
        }

        if (hasFlow && hasApi) {
            detectedPatterns.push('Event-driven architecture');
        }

        // Identify gaps
        if (hasApi && !hasData) {
            gaps.push('Missing data layer');
            suggestions.push({
                id: `gap-data-${Date.now()}`,
                type: 'gap',
                title: 'Missing Data Layer',
                description: 'Your API needs a data source. Consider adding a database.',
                confidence: 0.9,
                priority: 'high',
                data: { nodeType: 'data' },
            });
            suggestedPatterns.push('Add data layer');
        }

        if (hasComponent && !hasApi) {
            gaps.push('Missing API layer');
            suggestions.push({
                id: `gap-api-${Date.now()}`,
                type: 'gap',
                title: 'Missing API Layer',
                description: 'Your components need an API to fetch data.',
                confidence: 0.85,
                priority: 'medium',
                data: { nodeType: 'api' },
            });
            suggestedPatterns.push('Add API layer');
        }

        // Suggest connections
        for (const apiNode of elements.filter((el) => el.type === 'api')) {
            const connectedToData = connections.some(
                (conn) =>
                    conn.source === apiNode.id &&
                    elements.find((el) => el.id === conn.target && el.type === 'data')
            );

            if (!connectedToData) {
                const dataNodes = elements.filter((el) => el.type === 'data');
                if (dataNodes.length > 0) {
                    suggestedConnections.push({
                        source: apiNode.id,
                        target: dataNodes[0].id,
                        reason: 'API should connect to data source',
                    });

                    suggestions.push({
                        id: `conn-api-data-${Date.now()}`,
                        type: 'connection',
                        title: 'Connect API to Database',
                        description: `Connect ${apiNode.data?.label || 'API'} to ${dataNodes[0].data?.label || 'Database'}`,
                        confidence: 0.8,
                        priority: 'medium',
                        data: {
                            source: apiNode.id,
                            target: dataNodes[0].id,
                        },
                    });
                }
            }
        }

        // Suggest architectural patterns
        if (elements.length >= 3 && !hasFlow) {
            suggestedPatterns.push('Add workflow orchestration');
            suggestions.push({
                id: `pattern-flow-${Date.now()}`,
                type: 'pattern',
                title: 'Add Workflow Orchestration',
                description: 'Your architecture could benefit from a workflow engine to coordinate processes.',
                confidence: 0.7,
                priority: 'medium',
                data: { pattern: 'workflow' },
            });
        }

        // Check for authentication
        const hasAuth = elements.some(
            (el) =>
                el.data?.name?.toLowerCase().includes('auth') ||
                el.data?.label?.toLowerCase().includes('auth')
        );

        if (elements.length >= 3 && !hasAuth) {
            suggestedPatterns.push('Add authentication');
            suggestions.push({
                id: `pattern-auth-${Date.now()}`,
                type: 'pattern',
                title: 'Add Authentication',
                description: 'Consider adding authentication to secure your application.',
                confidence: 0.75,
                priority: 'high',
                data: { pattern: 'authentication' },
            });
        }

        return {
            suggestions,
            patterns: {
                detected: detectedPatterns,
                suggested: suggestedPatterns,
            },
            gaps,
            connections: suggestedConnections,
        };
    }
}

// ============================================================================
// Singleton Instance
// ============================================================================

export const shapeAgent = new ShapeAgent();
