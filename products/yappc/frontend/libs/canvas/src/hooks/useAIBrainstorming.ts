/**
 * useAIBrainstorming Hook
 * 
 * React hook for AI-powered brainstorming that spawns connected nodes.
 * Follows Journey 1.1 (PM - AI Brainstorming) from YAPPC_USER_JOURNEYS.md.
 * 
 * Features:
 * - AI prompt → generate 4-5 connected child nodes
 * - Auto-layout algorithm for spawned nodes
 * - Color-coded nodes by type (purple for UI)
 * - Integration with AICodeGenerationService
 * 
 * @doc.type hook
 * @doc.purpose AI brainstorming with node generation
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback } from 'react';
import { useReactFlow } from '@xyflow/react';
import type { Node, Edge } from '@xyflow/react';
import { AICodeGenerationService } from '../integration/aiCodeGeneration';
import type { IAIService } from '@ghatana/yappc-ai/core';

/**
 * Node type suggestions from AI
 */
export interface AINodeSuggestion {
    /** Node type (uiScreen, apiEndpoint, database, service) */
    type: 'uiScreen' | 'apiEndpoint' | 'database' | 'service' | 'requirement' | 'userFlow';
    /** Node label */
    label: string;
    /** Node description */
    description?: string;
    /** Persona who owns this node */
    persona: 'pm' | 'architect' | 'developer' | 'qa' | 'ux';
    /** Additional metadata */
    metadata?: Record<string, unknown>;
}

/**
 * AI brainstorming result
 */
export interface AIBrainstormResult {
    /** Original prompt */
    prompt: string;
    /** AI response text */
    response: string;
    /** Generated node suggestions */
    suggestions: AINodeSuggestion[];
    /** Token usage */
    tokenUsage?: {
        promptTokens: number;
        completionTokens: number;
        totalTokens: number;
    };
}

/**
 * useAIBrainstorming options
 */
export interface UseAIBrainstormingOptions {
    /** AI service instance */
    aiService?: IAIService;
    /** Auto-spawn nodes after generation */
    autoSpawn?: boolean;
    /** Number of suggestions to generate */
    suggestionCount?: number;
    /** Callback when nodes are spawned */
    onNodesSpawned?: (nodes: Node[], edges: Edge[]) => void;
    /** Callback when brainstorming starts */
    onBrainstormStart?: (prompt: string) => void;
    /** Callback when brainstorming completes */
    onBrainstormComplete?: (result: AIBrainstormResult) => void;
    /** Callback on error */
    onError?: (error: Error) => void;
}

/**
 * useAIBrainstorming result
 */
export interface UseAIBrainstormingResult {
    /** Current AI response */
    response: AIBrainstormResult | null;
    /** Loading state */
    loading: boolean;
    /** Error if any */
    error: Error | null;
    /** Generate ideas from prompt */
    generateIdeas: (prompt: string, sourceNodeId?: string) => Promise<void>;
    /** Spawn nodes from suggestions */
    spawnNodes: (suggestions: AINodeSuggestion[], sourceNodeId?: string) => void;
    /** Clear response */
    clearResponse: () => void;
}

/**
 * Node type to color mapping
 */
const NODE_TYPE_COLORS: Record<string, string> = {
    uiScreen: '#9c27b0', // Purple
    apiEndpoint: '#2196f3', // Blue
    database: '#4caf50', // Green
    service: '#ff9800', // Orange
    requirement: '#e91e63', // Pink
    userFlow: '#00bcd4', // Cyan
};

/**
 * Auto-layout algorithm - circular arrangement
 */
function calculateNodePositions(
    count: number,
    sourcePosition: { x: number; y: number }
): Array<{ x: number; y: number }> {
    const positions: Array<{ x: number; y: number }> = [];
    const radius = 300;
    const angleStep = (2 * Math.PI) / count;
    const startAngle = 0;

    for (let i = 0; i < count; i++) {
        const angle = startAngle + i * angleStep;
        positions.push({
            x: sourcePosition.x + radius * Math.cos(angle),
            y: sourcePosition.y + radius * Math.sin(angle),
        });
    }

    return positions;
}

/**
 * Parse AI response to extract node suggestions
 */
function parseAISuggestions(aiResponse: string, count: number): AINodeSuggestion[] {
    // In a real implementation, this would use structured output from the AI
    // For now, we'll generate mock suggestions based on common patterns
    const suggestions: AINodeSuggestion[] = [];

    // Extract key concepts from the prompt
    const hasAuth = /auth|login|signin|signup/i.test(aiResponse);
    const hasProfile = /profile|user|account|settings/i.test(aiResponse);
    const hasDashboard = /dashboard|overview|home/i.test(aiResponse);
    const hasEdit = /edit|update|modify/i.test(aiResponse);

    if (hasDashboard) {
        suggestions.push({
            type: 'uiScreen',
            label: 'Dashboard',
            description: 'Main dashboard view',
            persona: 'ux',
            metadata: { screenType: 'view' },
        });
    }

    if (hasAuth) {
        suggestions.push({
            type: 'uiScreen',
            label: 'Login',
            description: 'Authentication screen',
            persona: 'ux',
            metadata: { screenType: 'auth' },
        });
    }

    if (hasProfile) {
        suggestions.push({
            type: 'uiScreen',
            label: 'Profile View',
            description: 'User profile display',
            persona: 'ux',
            metadata: { screenType: 'view' },
        });

        suggestions.push({
            type: 'apiEndpoint',
            label: 'GET /api/users/:id',
            description: 'Fetch user profile',
            persona: 'developer',
            metadata: { method: 'GET', path: '/api/users/:id' },
        });

        suggestions.push({
            type: 'database',
            label: 'Users Table',
            description: 'User data storage',
            persona: 'architect',
            metadata: { engine: 'postgres' },
        });
    }

    if (hasEdit) {
        suggestions.push({
            type: 'uiScreen',
            label: 'Edit Mode',
            description: 'Edit user profile',
            persona: 'ux',
            metadata: { screenType: 'edit' },
        });
    }

    // Ensure we have at least the requested count
    while (suggestions.length < Math.min(count, 5)) {
        suggestions.push({
            type: 'requirement',
            label: `Requirement ${suggestions.length + 1}`,
            description: 'Auto-generated requirement',
            persona: 'pm',
        });
    }

    return suggestions.slice(0, count);
}

/**
 * useAIBrainstorming hook
 */
export function useAIBrainstorming(
    options: UseAIBrainstormingOptions = {}
): UseAIBrainstormingResult {
    const {
        aiService,
        autoSpawn = true,
        suggestionCount = 5,
        onNodesSpawned,
        onBrainstormStart,
        onBrainstormComplete,
        onError,
    } = options;

    const { getNodes, setNodes, setEdges, addNodes, addEdges } = useReactFlow();
    const [response, setResponse] = useState<AIBrainstormResult | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    /**
     * Generate ideas from prompt using AI
     */
    const generateIdeas = useCallback(
        async (prompt: string, sourceNodeId?: string) => {
            if (!prompt.trim()) {
                const err = new Error('Prompt cannot be empty');
                setError(err);
                onError?.(err);
                return;
            }

            setLoading(true);
            setError(null);
            onBrainstormStart?.(prompt);

            try {
                let aiResponse = '';
                let tokenUsage = { promptTokens: 0, completionTokens: 0, totalTokens: 0 };

                if (aiService) {
                    // Use real AI service
                    const codeGenService = new AICodeGenerationService(aiService);
                    // In a real implementation, we'd have a dedicated method for brainstorming
                    // For now, we'll simulate with a generic completion
                    const result = await aiService.complete(
                        `Generate ${suggestionCount} feature ideas for: "${prompt}". Include UI screens, API endpoints, and data models.`,
                        {
                            model: 'gpt-4',
                            temperature: 0.8,
                            maxTokens: 1000,
                        }
                    );
                    aiResponse = result.content;
                    tokenUsage = result.usage;
                } else {
                    // Mock response for development
                    await new Promise((resolve) => setTimeout(resolve, 1500));
                    aiResponse = `Based on "${prompt}", here are feature suggestions:\n1. User Dashboard\n2. Profile Management\n3. Authentication Flow\n4. API Endpoints\n5. Database Schema`;
                }

                // Parse suggestions
                const suggestions = parseAISuggestions(aiResponse, suggestionCount);

                const result: AIBrainstormResult = {
                    prompt,
                    response: aiResponse,
                    suggestions,
                    tokenUsage,
                };

                setResponse(result);
                setLoading(false);
                onBrainstormComplete?.(result);

                // Auto-spawn nodes if enabled
                if (autoSpawn && suggestions.length > 0) {
                    spawnNodes(suggestions, sourceNodeId);
                }
            } catch (err) {
                const error = err instanceof Error ? err : new Error('AI brainstorming failed');
                setError(error);
                setLoading(false);
                onError?.(error);
            }
        },
        [aiService, autoSpawn, suggestionCount, onBrainstormStart, onBrainstormComplete, onError]
    );

    /**
     * Spawn nodes from suggestions
     */
    const spawnNodes = useCallback(
        (suggestions: AINodeSuggestion[], sourceNodeId?: string) => {
            const nodes = getNodes();
            const sourceNode = sourceNodeId ? nodes.find((n) => n.id === sourceNodeId) : null;
            const sourcePosition = sourceNode?.position || { x: 400, y: 300 };

            // Calculate positions in circular layout
            const positions = calculateNodePositions(suggestions.length, sourcePosition);

            // Create new nodes
            const newNodes: Node[] = suggestions.map((suggestion, index) => ({
                id: `${suggestion.type}-${Date.now()}-${index}`,
                type: suggestion.type,
                position: positions[index],
                data: {
                    label: suggestion.label,
                    type: suggestion.type,
                    persona: suggestion.persona,
                    description: suggestion.description,
                    aiGenerated: true,
                    lastUpdated: new Date().toISOString(),
                    metadata: suggestion.metadata,
                },
                style: {
                    borderColor: NODE_TYPE_COLORS[suggestion.type] || '#666',
                },
            }));

            // Create edges from source node to new nodes
            const newEdges: Edge[] = sourceNodeId
                ? newNodes.map((node) => ({
                    id: `e-${sourceNodeId}-${node.id}`,
                    source: sourceNodeId,
                    target: node.id,
                    type: 'smoothstep',
                    animated: true,
                    style: { stroke: '#999' },
                }))
                : [];

            // Add nodes and edges to canvas
            addNodes(newNodes);
            if (newEdges.length > 0) {
                addEdges(newEdges);
            }

            onNodesSpawned?.(newNodes, newEdges);
        },
        [getNodes, addNodes, addEdges, onNodesSpawned]
    );

    /**
     * Clear response
     */
    const clearResponse = useCallback(() => {
        setResponse(null);
        setError(null);
    }, []);

    return {
        response,
        loading,
        error,
        generateIdeas,
        spawnNodes,
        clearResponse,
    };
}
