/**
 * AI Assistant Hook for Canvas
 * 
 * Provides AI-powered suggestions, pattern detection, and ghost nodes
 * 
 * @doc.type hook
 * @doc.purpose AI assistance for canvas operations
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import type { CanvasState } from '@/components/canvas/workspace/canvasAtoms';
import { LifecyclePhase } from '@/types/lifecycle';
import { getCanvasAIService } from '../services/canvas/api/CanvasAIService';

// ============================================================================
// Types
// ============================================================================

export type SuggestionType =
    | 'node'           // Suggest a new node
    | 'connection'     // Suggest a new connection
    | 'pattern'        // Suggest applying a pattern
    | 'gap'            // Identify missing component
    | 'risk'           // Identify potential risk
    | 'optimization';  // Suggest optimization

export interface AISuggestion {
    id: string;
    type: SuggestionType;
    title: string;
    description: string;
    confidence: number; // 0-1
    priority: 'low' | 'medium' | 'high' | 'critical';
    data?: unknown;
}

export interface GhostNode {
    id: string;
    suggestionId: string;
    type: string;
    position: { x: number; y: number };
    data: Record<string, unknown>;
    style?: Record<string, unknown>;
}

export interface GhostConnection {
    id: string;
    suggestionId: string;
    source: string;
    target: string;
    type?: string;
    animated?: boolean;
}

export interface PatternSuggestion {
    id: string;
    name: string;
    description: string;
    nodes: GhostNode[];
    connections: GhostConnection[];
}

// ============================================================================
// AI Assistant Hook
// ============================================================================

export interface UseAIAssistantOptions {
    canvasState: CanvasState;
    lifecyclePhase: LifecyclePhase;
    enabled?: boolean;
    autoSuggest?: boolean;
    suggestionDelay?: number; // ms
}

export interface UseAIAssistantResult {
    /** All active suggestions */
    suggestions: AISuggestion[];
    /** Ghost nodes for visualization */
    ghostNodes: GhostNode[];
    /** Ghost connections for visualization */
    ghostConnections: GhostConnection[];
    /** Accept a suggestion */
    acceptSuggestion: (suggestionId: string) => void;
    /** Dismiss a suggestion */
    dismissSuggestion: (suggestionId: string) => void;
    /** Dismiss all suggestions */
    dismissAll: () => void;
    /** Request AI suggestions manually */
    requestSuggestions: () => void;
    /** Whether AI is currently analyzing */
    isAnalyzing: boolean;
}

/**
 * AI Assistant Hook
 * Provides real-time AI suggestions and ghost nodes
 */
export function useAIAssistant(options: UseAIAssistantOptions): UseAIAssistantResult {
    const {
        canvasState,
        lifecyclePhase,
        enabled = true,
        autoSuggest = true,
        suggestionDelay = 2000,
    } = options;

    const [suggestions, setSuggestions] = useState<AISuggestion[]>([]);
    const [isAnalyzing, setIsAnalyzing] = useState(false);
    const [dismissedIds, setDismissedIds] = useState<Set<string>>(new Set());
    const abortRef = useRef<AbortController | null>(null);

    // Build a CanvasState payload compatible with CanvasAIService.validateCanvas
    const buildServicePayload = useCallback(() => ({
        canvasState: {
            nodes: canvasState.elements ?? [],
            edges: canvasState.connections ?? [],
            phase: lifecyclePhase,
        } as Parameters<Awaited<ReturnType<typeof getCanvasAIService>>['validateCanvas']>[0]['canvasState'],
        phase: lifecyclePhase,
        options: { validateRisks: true },
    }), [canvasState, lifecyclePhase]);

    // Map a ValidationReport into our AISuggestion shape
    const mapReportToSuggestions = useCallback(
        (report: Awaited<ReturnType<Awaited<ReturnType<typeof getCanvasAIService>>['validateCanvas']>>): AISuggestion[] => {
            const mapped: AISuggestion[] = [];
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const anyReport = report as unknown;
            for (const issue of anyReport?.issues ?? []) {
                mapped.push({
                    id: issue.id ?? `ai-${Date.now()}-${Math.random()}`,
                    type: (issue.type as AISuggestion['type']) ?? 'gap',
                    title: issue.title ?? issue.message ?? 'AI suggestion',
                    description: issue.description ?? issue.message ?? '',
                    confidence: issue.confidence ?? 0.8,
                    priority: issue.severity ?? 'medium',
                    data: issue.data,
                });
            }
            return mapped;
        },
        []
    );

    // Local heuristics — used as offline fallback when AI service unavailable
    const buildLocalSuggestions = useCallback((): AISuggestion[] => {
        const local: AISuggestion[] = [];

        if (lifecyclePhase === LifecyclePhase.SHAPE) {
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const standaloneNodes = canvasState.elements.filter((el: unknown) => {
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                const hasConn = canvasState.connections.some((c: unknown) => c.source === el.id || c.target === el.id);
                return !hasConn && el.type === 'api';
            });
            if (standaloneNodes.length > 0) {
                local.push({
                    id: `gap-standalone-${Date.now()}`,
                    type: 'gap',
                    title: 'Unconnected API nodes detected',
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    description: `${standaloneNodes.length} API node(s) have no connections.`,
                    confidence: 0.8,
                    priority: 'medium',
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    data: { nodes: standaloneNodes.map((n: unknown) => n.id) },
                });
            }
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const hasApi = canvasState.elements.some((el: unknown) => el.type === 'api');
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const hasData = canvasState.elements.some((el: unknown) => el.type === 'data');
            if (hasApi && !hasData) {
                local.push({
                    id: `node-database-${Date.now()}`,
                    type: 'node',
                    title: 'Add Database',
                    description: 'Your API likely needs a database.',
                    confidence: 0.9,
                    priority: 'high',
                    data: { nodeType: 'data', suggestedPosition: findEmptyPosition(canvasState) },
                });
            }
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const hasAuth = canvasState.elements.some((el: unknown) =>
                el.data?.name?.toLowerCase().includes('auth') ||
                el.data?.label?.toLowerCase().includes('auth')
            );
            if (canvasState.elements.length >= 3 && !hasAuth) {
                local.push({
                    id: `pattern-auth-${Date.now()}`,
                    type: 'pattern',
                    title: 'Add Authentication',
                    description: 'Consider adding authentication to secure your application.',
                    confidence: 0.7,
                    priority: 'medium',
                    data: { pattern: 'authentication' },
                });
            }
        }

        if (lifecyclePhase === LifecyclePhase.VALIDATE) {
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const criticalNodes = canvasState.elements.filter((el: unknown) => {
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                const conns = canvasState.connections.filter((c: unknown) => c.source === el.id || c.target === el.id);
                return conns.length > 3;
            });
            if (criticalNodes.length > 0) {
                local.push({
                    id: `risk-spof-${Date.now()}`,
                    type: 'risk',
                    title: 'Potential Single Point of Failure',
                    description: `${criticalNodes.length} node(s) have high connectivity.`,
                    confidence: 0.75,
                    priority: 'high',
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    data: { nodes: criticalNodes.map((n: unknown) => n.id) },
                });
            }
        }

        if (lifecyclePhase === LifecyclePhase.IMPROVE) {
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const apiNodes = canvasState.elements.filter((el: unknown) => el.type === 'api');
            if (apiNodes.length >= 3) {
                local.push({
                    id: `optimization-cache-${Date.now()}`,
                    type: 'optimization',
                    title: 'Add Caching Layer',
                    description: 'Multiple API nodes detected. Consider adding a caching layer.',
                    confidence: 0.65,
                    priority: 'medium',
                    data: { suggestedType: 'redis' },
                });
            }
        }

        return local;
    }, [canvasState, lifecyclePhase]);

    // Core: call real CanvasAIService; fall back to local heuristics
    const generateSuggestions = useCallback(() => {
        if (!enabled) return;

        // Cancel any in-flight request
        abortRef.current?.abort();
        const controller = new AbortController();
        abortRef.current = controller;

        setIsAnalyzing(true);

        getCanvasAIService()
            .then(async (service) => {
                if (!service.isAvailable()) {
                    throw new Error('CanvasAIService unavailable');
                }
                const report = await service.validateCanvas(buildServicePayload());
                if (controller.signal.aborted) return;
                return mapReportToSuggestions(report);
            })
            .catch(() => {
                // Service unavailable or error — use local heuristics
                if (controller.signal.aborted) return undefined;
                return buildLocalSuggestions();
            })
            .then((newSuggestions) => {
                if (controller.signal.aborted || !newSuggestions) return;
                const filtered = newSuggestions.filter((s) => !dismissedIds.has(s.id));
                setSuggestions(filtered);
            })
            .finally(() => {
                if (!controller.signal.aborted) setIsAnalyzing(false);
            });
    }, [enabled, buildServicePayload, mapReportToSuggestions, buildLocalSuggestions, dismissedIds]);

    // Cleanup in-flight requests on unmount
    useEffect(() => () => { abortRef.current?.abort(); }, []);

    // Auto-generate suggestions when canvas changes (debounced)
    useEffect(() => {
        if (!autoSuggest || !enabled) return;
        const timer = setTimeout(generateSuggestions, suggestionDelay);
        return () => clearTimeout(timer);
    }, [canvasState.elements.length, canvasState.connections.length, lifecyclePhase, autoSuggest, enabled, suggestionDelay, generateSuggestions]);

    // Generate ghost nodes from node suggestions
    const ghostNodes = useMemo((): GhostNode[] => {
        return suggestions
            .filter((sugg) => sugg.type === 'node')
            .map((sugg) => ({
                id: `ghost-${sugg.id}`,
                suggestionId: sugg.id,
                type: sugg.data?.nodeType || 'component',
                position: sugg.data?.suggestedPosition || { x: 100, y: 100 },
                data: {
                    label: sugg.title,
                    isGhost: true,
                    suggestion: sugg,
                },
                style: {
                    opacity: 0.6,
                    border: '2px dashed #2196f3',
                    backgroundColor: 'rgba(33, 150, 243, 0.1)',
                },
            }));
    }, [suggestions]);

    // Generate ghost connections from connection suggestions
    const ghostConnections = useMemo((): GhostConnection[] => {
        return suggestions
            .filter((sugg) => sugg.type === 'connection')
            .map((sugg) => ({
                id: `ghost-conn-${sugg.id}`,
                suggestionId: sugg.id,
                source: sugg.data?.source,
                target: sugg.data?.target,
                type: sugg.data?.connectionType || 'default',
                animated: true,
            }));
    }, [suggestions]);

    // Accept a suggestion
    const acceptSuggestion = useCallback((suggestionId: string) => {
        const suggestion = suggestions.find((s) => s.id === suggestionId);
        if (!suggestion) return;

        // Remove from suggestions
        setSuggestions((prev) => prev.filter((s) => s.id !== suggestionId));

        // Mark as dismissed to prevent re-suggestion
        setDismissedIds((prev) => new Set(prev).add(suggestionId));

        // In production, this would trigger actual canvas modifications
        console.log('Accepted suggestion:', suggestion);
    }, [suggestions]);

    // Dismiss a suggestion
    const dismissSuggestion = useCallback((suggestionId: string) => {
        setSuggestions((prev) => prev.filter((s) => s.id !== suggestionId));
        setDismissedIds((prev) => new Set(prev).add(suggestionId));
    }, []);

    // Dismiss all suggestions
    const dismissAll = useCallback(() => {
        setSuggestions((prev) => {
            prev.forEach((sugg) => {
                setDismissedIds((ids) => new Set(ids).add(sugg.id));
            });
            return [];
        });
    }, []);

    // Request suggestions manually
    const requestSuggestions = useCallback(() => {
        generateSuggestions();
    }, [generateSuggestions]);

    return {
        suggestions,
        ghostNodes,
        ghostConnections,
        acceptSuggestion,
        dismissSuggestion,
        dismissAll,
        requestSuggestions,
        isAnalyzing,
    };
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Find empty position on canvas for new node
 */
function findEmptyPosition(canvasState: CanvasState): { x: number; y: number } {
    const positions = canvasState.elements.map((el: unknown) => el.position);

    // Simple grid-based positioning
    const gridSize = 200;
    let x = 100;
    let y = 100;

    // Find first empty grid cell
    for (let row = 0; row < 10; row++) {
        for (let col = 0; col < 10; col++) {
            const candidateX = col * gridSize + 100;
            const candidateY = row * gridSize + 100;

            const isOccupied = positions.some((pos: unknown) =>
                Math.abs(pos.x - candidateX) < gridSize / 2 &&
                Math.abs(pos.y - candidateY) < gridSize / 2
            );

            if (!isOccupied) {
                return { x: candidateX, y: candidateY };
            }
        }
    }

    return { x, y };
}
