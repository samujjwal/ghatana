/**
 * useIntentCanvasPreview Hook
 *
 * React hook for intent-first canvas generation with preview-before-commit.
 * Wraps IntentCanvasGenerator with UI-friendly state management.
 *
 * @doc.type hook
 * @doc.purpose Preview canvas nodes from intent before committing
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback, useRef } from 'react';
import {
  generatePreview,
  type IntentCanvasPreview,
  type IntentCanvasNode,
  type IntentCanvasConnection,
} from '../services/canvas/intent/IntentCanvasGenerator';

export type PreviewStatus = 'idle' | 'parsing' | 'preview' | 'committed' | 'error';

export interface UseIntentCanvasPreviewResult {
  status: PreviewStatus;
  preview: IntentCanvasPreview | null;
  error: string | null;
  generatePreview: (intent: string) => void;
  commitPreview: () => { nodes: IntentCanvasNode[]; connections: IntentCanvasConnection[] } | null;
  rejectPreview: () => void;
  updateNode: (nodeId: string, updates: Partial<IntentCanvasNode>) => void;
  removeNode: (nodeId: string) => void;
  addNode: (node: Omit<IntentCanvasNode, 'id'>) => void;
}

export function useIntentCanvasPreview(): UseIntentCanvasPreviewResult {
  const [status, setStatus] = useState<PreviewStatus>('idle');
  const [preview, setPreview] = useState<IntentCanvasPreview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const committedRef = useRef<{ nodes: IntentCanvasNode[]; connections: IntentCanvasConnection[] } | null>(null);

  const generate = useCallback((intent: string) => {
    if (!intent.trim()) {
      setError('Intent text is required');
      setStatus('error');
      return;
    }
    setStatus('parsing');
    setError(null);
    try {
      const result = generatePreview(intent);
      setPreview(result);
      setStatus('preview');
      committedRef.current = null;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to generate preview');
      setStatus('error');
    }
  }, []);

  const commitPreview = useCallback(() => {
    if (!preview || status !== 'preview') return null;
    committedRef.current = { nodes: preview.nodes, connections: preview.connections };
    setStatus('committed');
    return committedRef.current;
  }, [preview, status]);

  const rejectPreview = useCallback(() => {
    setPreview(null);
    setStatus('idle');
    setError(null);
    committedRef.current = null;
  }, []);

  const updateNode = useCallback((nodeId: string, updates: Partial<IntentCanvasNode>) => {
    setPreview((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        nodes: prev.nodes.map((n) => (n.id === nodeId ? { ...n, ...updates } : n)),
      };
    });
  }, []);

  const removeNode = useCallback((nodeId: string) => {
    setPreview((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        nodes: prev.nodes.filter((n) => n.id !== nodeId),
        connections: prev.connections.filter((c) => c.from !== nodeId && c.to !== nodeId),
      };
    });
  }, []);

  const addNode = useCallback((node: Omit<IntentCanvasNode, 'id'>) => {
    setPreview((prev) => {
      if (!prev) return prev;
      const id = `node-${Date.now()}-${prev.nodes.length}`;
      return { ...prev, nodes: [...prev.nodes, { ...node, id }] };
    });
  }, []);

  return {
    status,
    preview,
    error,
    generatePreview: generate,
    commitPreview,
    rejectPreview,
    updateNode,
    removeNode,
    addNode,
  };
}
