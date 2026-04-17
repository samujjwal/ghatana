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
  | 'node' // Suggest a new node
  | 'connection' // Suggest a new connection
  | 'pattern' // Suggest applying a pattern
  | 'gap' // Identify missing component
  | 'risk' // Identify potential risk
  | 'optimization'; // Suggest optimization

export interface AISuggestion {
  id: string;
  type: SuggestionType;
  title: string;
  description: string;
  confidence: number; // 0-1
  priority: 'low' | 'medium' | 'high' | 'critical';
  data?: unknown;
}

interface CanvasNodeLike {
  id: string;
  type?: string;
  position?: { x: number; y: number };
  data?: Record<string, unknown>;
}

interface CanvasEdgeLike {
  source: string;
  target: string;
}

interface ServiceValidationIssue {
  id?: string;
  type?: unknown;
  title?: string;
  message?: string;
  description?: string;
  confidence?: number;
  severity?: AISuggestion['priority'];
  data?: Record<string, unknown>;
}

interface ServiceValidationReport {
  issues?: ServiceValidationIssue[];
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
  /** Last request-level error, if any */
  error: string | null;
}

/**
 * AI Assistant Hook
 * Provides real-time AI suggestions and ghost nodes
 */
export function useAIAssistant(
  options: UseAIAssistantOptions
): UseAIAssistantResult {
  const {
    canvasState,
    lifecyclePhase,
    enabled = true,
    autoSuggest = true,
    suggestionDelay = 2000,
  } = options;

  const [suggestions, setSuggestions] = useState<AISuggestion[]>([]);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [dismissedIds, setDismissedIds] = useState<Set<string>>(new Set());
  const abortRef = useRef<AbortController | null>(null);

  const nodes = useMemo(
    () => (canvasState.elements as unknown[]).filter(isCanvasNodeLike),
    [canvasState.elements]
  );

  const edges = useMemo(
    () => (canvasState.connections as unknown[]).filter(isCanvasEdgeLike),
    [canvasState.connections]
  );

  // Build a CanvasState payload compatible with CanvasAIService.validateCanvas
  const buildServicePayload = useCallback(
    () => ({
      canvasState: {
        nodes,
        edges,
        phase: lifecyclePhase,
      } as Parameters<
        Awaited<ReturnType<typeof getCanvasAIService>>['validateCanvas']
      >[0]['canvasState'],
      phase: lifecyclePhase,
      options: { validateRisks: true },
    }),
    [edges, lifecyclePhase, nodes]
  );

  // Map a ValidationReport into our AISuggestion shape
  const mapReportToSuggestions = useCallback(
    (
      report: Awaited<
        ReturnType<
          Awaited<ReturnType<typeof getCanvasAIService>>['validateCanvas']
        >
      >
    ): AISuggestion[] => {
      const mapped: AISuggestion[] = [];
      const typedReport = report as unknown as ServiceValidationReport;
      for (const issue of typedReport.issues ?? []) {
        const candidateType = issue.type;
        const suggestionType: AISuggestion['type'] =
          candidateType === 'node' ||
          candidateType === 'connection' ||
          candidateType === 'pattern' ||
          candidateType === 'gap' ||
          candidateType === 'risk' ||
          candidateType === 'optimization'
            ? candidateType
            : 'gap';

        mapped.push({
          id: issue.id ?? `ai-${Date.now()}-${Math.random().toString(16).slice(2)}`,
          type: suggestionType,
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

  // Core: call server-backed CanvasAIService only
  const generateSuggestions = useCallback(() => {
    if (!enabled) return;

    // Cancel any in-flight request
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    setIsAnalyzing(true);
    setError(null);

    void (async () => {
      try {
        const service = await getCanvasAIService();
        if (!service.isAvailable()) {
          setSuggestions([]);
          setError('Canvas AI service unavailable');
          return;
        }

        const report = await service.validateCanvas(buildServicePayload());
        if (controller.signal.aborted) return;
        const newSuggestions = mapReportToSuggestions(report);
        const filtered = newSuggestions.filter((s) => !dismissedIds.has(s.id));
        setSuggestions(filtered);
      } catch (serviceError) {
        if (controller.signal.aborted) {
          return;
        }

        const message =
          serviceError instanceof Error
            ? serviceError.message
            : 'Canvas AI analysis failed';
        setSuggestions([]);
        setError(message);
      }
    })()
      .finally(() => {
        if (!controller.signal.aborted) setIsAnalyzing(false);
      });
  }, [
    enabled,
    buildServicePayload,
    mapReportToSuggestions,
    dismissedIds,
  ]);

  // Cleanup in-flight requests on unmount
  useEffect(
    () => () => {
      abortRef.current?.abort();
    },
    []
  );

  // Auto-generate suggestions when canvas changes (debounced)
  useEffect(() => {
    if (!autoSuggest || !enabled) return;
    const timer = setTimeout(generateSuggestions, suggestionDelay);
    return () => clearTimeout(timer);
  }, [
    canvasState.elements.length,
    canvasState.connections.length,
    lifecyclePhase,
    autoSuggest,
    enabled,
    suggestionDelay,
    generateSuggestions,
  ]);

  // Generate ghost nodes from node suggestions
  const ghostNodes = useMemo((): GhostNode[] => {
    return suggestions
      .filter((sugg) => sugg.type === 'node')
      .map((sugg) => ({
        id: `ghost-${sugg.id}`,
        suggestionId: sugg.id,
        type: getRecordValue<string>(sugg.data, 'nodeType') ?? 'component',
        position:
          getRecordValue<{ x: number; y: number }>(
            sugg.data,
            'suggestedPosition'
          ) ?? findEmptyPosition(nodes),
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
        source: getRecordValue<string>(sugg.data, 'source') ?? '',
        target: getRecordValue<string>(sugg.data, 'target') ?? '',
        type: getRecordValue<string>(sugg.data, 'connectionType') ?? 'default',
        animated: true,
      }))
      .filter((connection) => connection.source.length > 0 && connection.target.length > 0);
  }, [suggestions]);

  // Accept a suggestion
  const acceptSuggestion = useCallback(
    (suggestionId: string) => {
      const suggestion = suggestions.find((s) => s.id === suggestionId);
      if (!suggestion) return;

      // Remove from suggestions
      setSuggestions((prev) => prev.filter((s) => s.id !== suggestionId));

      // Mark as dismissed to prevent re-suggestion
      setDismissedIds((prev) => new Set(prev).add(suggestionId));

      // In production, this would trigger actual canvas modifications
      console.log('Accepted suggestion:', suggestion);
    },
    [suggestions]
  );

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
    error,
  };
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Find empty position on canvas for new node
 */
function findEmptyPosition(nodes: CanvasNodeLike[]): { x: number; y: number } {
  const positions = nodes
    .map((node) => node.position)
    .filter((position): position is { x: number; y: number } =>
      Boolean(position && Number.isFinite(position.x) && Number.isFinite(position.y))
    );

  // Simple grid-based positioning
  const gridSize = 200;
  // Find first empty grid cell
  for (let row = 0; row < 10; row++) {
    for (let col = 0; col < 10; col++) {
      const candidateX = col * gridSize + 100;
      const candidateY = row * gridSize + 100;

      const isOccupied = positions.some(
        (pos) =>
          Math.abs(pos.x - candidateX) < gridSize / 2 &&
          Math.abs(pos.y - candidateY) < gridSize / 2
      );

      if (!isOccupied) {
        return { x: candidateX, y: candidateY };
      }
    }
  }

  return { x: 100, y: 100 };
}

function isCanvasNodeLike(value: unknown): value is CanvasNodeLike {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const candidate = value as Partial<CanvasNodeLike>;
  return typeof candidate.id === 'string';
}

function isCanvasEdgeLike(value: unknown): value is CanvasEdgeLike {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const candidate = value as Partial<CanvasEdgeLike>;
  return typeof candidate.source === 'string' && typeof candidate.target === 'string';
}

function getRecordValue<T>(
  value: unknown,
  key: string
): T | null {
  if (!value || typeof value !== 'object') {
    return null;
  }

  const record = value as Record<string, unknown>;
  return (record[key] as T | undefined) ?? null;
}
