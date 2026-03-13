/**
 * PipelineBuilderPage — Full-page layout assembling the pipeline builder.
 *
 * Composes: StagePalette | PipelineCanvas | PipelinePropertyPanel
 * with PipelineToolbar across the top.
 *
 * @doc.type page
 * @doc.purpose Top-level pipeline builder page
 * @doc.layer frontend
 */
import React, { useCallback, useEffect, useState } from 'react';
import { useAtom, useSetAtom, useAtomValue } from 'jotai';
import { toast, Toaster } from 'sonner';
import {
  PipelineCanvas,
  StagePalette,
  PipelineToolbar,
  PipelinePropertyPanel,
  PipelineErrorBoundary,
} from '@/components/pipeline';
import {
  pipelineAtom,
  nodesAtom,
  edgesAtom,
  isDirtyAtom,
  validationAtom,
  pipelineStatusAtom,
  historyAtom,
  historyIndexAtom,
} from '@/stores/pipeline.store';
import { savePipeline, validatePipeline, exportPipelineSpec, runPipeline } from '@/api/pipeline.api';
import { tenantIdAtom } from '@/stores/tenant.store';
import type {
  PipelineSpec,
  PipelineStageSpec,
  StageNodeData,
} from '@/types/pipeline.types';

// ─── Nodes → PipelineSpec conversion ─────────────────────────────────

function nodesToSpec(
  pipeline: PipelineSpec,
  nodes: ReturnType<typeof useAtomValue<typeof nodesAtom>>,
): PipelineSpec {
  const stages: PipelineStageSpec[] = nodes
    .filter((n) => n.type === 'stage')
    .map((n) => {
      const d = n.data as unknown as StageNodeData;
      return {
        name: d.label,
        kind: d.kind,
        workflow: d.agents,
        connectorIds: d.connectorIds,
        connectors: d.connectors,
        description: d.description,
      };
    });
  return { ...pipeline, stages };
}

// ─── Page Component ──────────────────────────────────────────────────

export function PipelineBuilderPage() {
  const [pipeline, setPipeline] = useAtom(pipelineAtom);
  const [nodes, setNodes] = useAtom(nodesAtom);
  const [edges, setEdges] = useAtom(edgesAtom);
  const [isDirty, setDirty] = useAtom(isDirtyAtom);
  const setValidation = useSetAtom(validationAtom);
  const setStatus = useSetAtom(pipelineStatusAtom);
  const [history, setHistory] = useAtom(historyAtom);
  const [historyIndex, setHistoryIndex] = useAtom(historyIndexAtom);

  const tenantId = useAtomValue(tenantIdAtom);

  // Loading states for async actions
  const [saving, setSaving] = useState(false);
  const [validating, setValidating] = useState(false);
  const [running, setRunning] = useState(false);

  // ── Dirty navigation guard ──────────────────────────────────────

  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (isDirty) {
        e.preventDefault();
        e.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [isDirty]);

  // ── History helper ─────────────────────────────────────────────

  const pushHistory = useCallback(
    (newNodes: typeof nodes, newEdges: typeof edges) => {
      const snapshot = { nodes: newNodes, edges: newEdges };
      const truncated = history.slice(0, historyIndex + 1);
      setHistory([...truncated, snapshot]);
      setHistoryIndex(truncated.length);
    },
    [history, historyIndex, setHistory, setHistoryIndex],
  );

  // ── Save ────────────────────────────────────────────────────────

  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      const spec = nodesToSpec(pipeline, nodes);
      const saved = await savePipeline(spec);
      setPipeline(saved);
      setDirty(false);
      toast.success('Pipeline saved');
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      toast.error(`Save failed: ${msg}`);
    } finally {
      setSaving(false);
    }
  }, [pipeline, nodes, setPipeline, setDirty]);

  // ── Validate ────────────────────────────────────────────────────

  const handleValidate = useCallback(async () => {
    setValidating(true);
    try {
      const spec = nodesToSpec(pipeline, nodes);
      const result = await validatePipeline(spec);
      setValidation(result);
      setStatus(result.isValid ? 'VALID' : 'DRAFT');
      if (result.isValid) {
        toast.success('Pipeline is valid');
      } else {
        toast.error(`Validation failed: ${result.errors.length} error(s)`);
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      toast.error(`Validation error: ${msg}`);
    } finally {
      setValidating(false);
    }
  }, [pipeline, nodes, setValidation, setStatus]);

  // ── Export (JSON) ───────────────────────────────────────────────

  const handleExport = useCallback(() => {
    try {
      const spec = nodesToSpec(pipeline, nodes);
      const json = exportPipelineSpec(spec);
      const blob = new Blob([json], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${pipeline.name.replace(/\s+/g, '-').toLowerCase()}.json`;
      a.click();
      URL.revokeObjectURL(url);
      toast.success('Pipeline exported');
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      toast.error(`Export failed: ${msg}`);
    }
  }, [pipeline, nodes]);

  // ── Undo ────────────────────────────────────────────────────────

  const handleUndo = useCallback(() => {
    if (historyIndex <= 0) return;
    const prevIndex = historyIndex - 1;
    const snapshot = history[prevIndex];
    setNodes(snapshot.nodes);
    setEdges(snapshot.edges);
    setHistoryIndex(prevIndex);
    setDirty(true);
  }, [history, historyIndex, setNodes, setEdges, setHistoryIndex, setDirty]);

  // ── Redo ────────────────────────────────────────────────────────

  const handleRedo = useCallback(() => {
    if (historyIndex >= history.length - 1) return;
    const nextIndex = historyIndex + 1;
    const snapshot = history[nextIndex];
    setNodes(snapshot.nodes);
    setEdges(snapshot.edges);
    setHistoryIndex(nextIndex);
    setDirty(true);
  }, [history, historyIndex, setNodes, setEdges, setHistoryIndex, setDirty]);

  // ── Run Now ─────────────────────────────────────────────────────

  const handleRunNow = useCallback(async () => {
    setRunning(true);
    try {
      const result = await runPipeline(pipeline.id, tenantId);
      toast.success(`Pipeline run triggered — ${result.eventId}`);
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      toast.error(`Failed to trigger pipeline run: ${msg}`);
    } finally {
      setRunning(false);
    }
  }, [pipeline.id, tenantId]);

  // ── New Pipeline ───────────────────────────────────────────────

  const handleNew = useCallback(() => {
    if (isDirty && !window.confirm('You have unsaved changes. Discard them?')) return;
    setPipeline({ name: 'Untitled Pipeline', stages: [], status: 'DRAFT', version: 1 });
    setNodes([]);
    setEdges([]);
    setHistory([]);
    setHistoryIndex(0);
    setDirty(false);
    setValidation(null);
    setStatus('DRAFT');
  }, [isDirty, setPipeline, setNodes, setEdges, setHistory, setHistoryIndex, setDirty, setValidation, setStatus]);

  // ── Render ─────────────────────────────────────────────────────

  return (
    <>
      <Toaster richColors position="top-right" />
      <div className="flex flex-col h-screen w-screen bg-white" data-testid="pipeline-builder">
        <PipelineToolbar
          onSave={handleSave}
          onValidate={handleValidate}
          onExport={handleExport}
          onUndo={handleUndo}
          onRedo={handleRedo}
          onNew={handleNew}
          onRunNow={handleRunNow}
          saving={saving}
          validating={validating}
          running={running}
        />
        <div className="flex flex-1 overflow-hidden">
          <StagePalette />
          <PipelineErrorBoundary>
            <PipelineCanvas />
          </PipelineErrorBoundary>
          <PipelinePropertyPanel />
        </div>
      </div>
    </>
  );
}
