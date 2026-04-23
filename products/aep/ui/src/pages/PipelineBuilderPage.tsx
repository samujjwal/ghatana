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
import { PanelLeftClose, PanelLeftOpen, PanelRightClose, PanelRightOpen } from 'lucide-react';
import { useAtom, useSetAtom, useAtomValue } from 'jotai';
import { useSearchParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
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
import {
  savePipeline,
  validatePipeline,
  runPipeline,
  getPipeline,
  suggestPipelineStages,
} from '@/api/pipelines';
import { exportPipelineSpec } from '@/lib/pipeline-export';
import { tenantIdAtom } from '@/stores/tenant.store';
import type {
  PipelineSpec,
  PipelineStageSpec,
  StageNodeData,
  StageKind,
} from '@/types/pipeline.types';

// ─── Tooltip Helper ─────────────────────────────────────────────────────

function Tooltip({ children, content }: { children: React.ReactNode; content: string }) {
  const [show, setShow] = useState(false);
  return (
    <div className="relative inline-block">
      <div
        onMouseEnter={() => setShow(true)}
        onMouseLeave={() => setShow(false)}
        className="cursor-help"
      >
        {children}
      </div>
      {show && (
        <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-3 py-1.5 text-xs text-white bg-gray-900 rounded shadow-lg whitespace-nowrap z-50">
          {content}
          <div className="absolute top-full left-1/2 -translate-x-1/2 -mt-1 border-4 border-transparent border-t-gray-900" />
        </div>
      )}
    </div>
  );
}

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
  const [searchParams] = useSearchParams();
  const pipelineId = searchParams.get('id');

  // Loading states for async actions
  const [saving, setSaving] = useState(false);
  const [validating, setValidating] = useState(false);
  const [running, setRunning] = useState(false);

  // ── AI Assistance ───────────────────────────────────────────────
  const [aiOpen, setAiOpen] = useState(false);
  const [aiDescription, setAiDescription] = useState('');
  const [aiGoal, setAiGoal] = useState('');
  const [aiLoading, setAiLoading] = useState(false);
  const [aiSuggestions, setAiSuggestions] = useState<
    { label: string; kind: StageKind; description: string }[] | null
  >(null);
  const [aiExplanation, setAiExplanation] = useState('');
  const [aiConfidence, setAiConfidence] = useState(0);

  // ── Responsive panel toggles ────────────────────────────────────
  const [leftPanelOpen, setLeftPanelOpen] = useState(true);
  const [rightPanelOpen, setRightPanelOpen] = useState(true);

  // ── Load existing pipeline when ?id= present ───────────────────
  const {
    isLoading: isLoadingPipeline,
    isError: isLoadError,
    error: loadError,
  } = useQuery({
    queryKey: ['pipeline', pipelineId, tenantId],
    queryFn: async () => {
      if (!pipelineId) return null;
      const data = await getPipeline(pipelineId, tenantId);
      // Hydrate builder store with loaded pipeline
      setPipeline(data);
      // Convert stages to nodes (simplified — real implementation would
      // map stages → nodes + edges based on connectorIds)
      const loadedNodes = (data.stages ?? []).map((stage, index) => ({
        id: `stage-${index}`,
        type: 'stage' as const,
        position: { x: 100 + index * 250, y: 200 },
        data: {
          label: stage.name,
          kind: stage.kind,
          agents: stage.workflow ?? [],
          agentCount: stage.workflow?.length ?? 0,
          connectorIds: stage.connectorIds ?? [],
          connectors: stage.connectors ?? [],
          description: stage.description ?? '',
        } satisfies StageNodeData,
      }));
      setNodes(loadedNodes);
      setEdges([]);
      setHistory([{ nodes: loadedNodes, edges: [] }]);
      setHistoryIndex(0);
      setDirty(false);
      return data;
    },
    enabled: !!pipelineId,
    staleTime: Infinity,
  });

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

  const handleAiSuggest = useCallback(async () => {
    if (!aiDescription.trim()) return;
    setAiLoading(true);
    try {
      const result = await suggestPipelineStages(
        {
          description: aiDescription,
          goal: aiGoal || undefined,
          existingStages: nodes.map((n) => ({
            name: n.data.label,
            kind: n.data.kind,
            description: n.data.description,
          })),
        },
        tenantId,
      );
      setAiSuggestions(
        result.suggestedStages.map((s: { name: string; kind: string; description?: string }) => ({
          label: s.name,
          kind: (s.kind as StageKind) || 'transform',
          description: s.description ?? '',
        })),
      );
      setAiExplanation(result.explanation);
      setAiConfidence(result.confidence);
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      toast.error(`AI suggestion failed: ${msg}`);
    } finally {
      setAiLoading(false);
    }
  }, [aiDescription, aiGoal, nodes, tenantId]);

  const handleApplySuggestions = useCallback(() => {
    if (!aiSuggestions) return;
    const startX = 100 + nodes.length * 250;
    const newNodes = aiSuggestions.map((s, index) => ({
      id: `stage-${nodes.length + index}`,
      type: 'stage' as const,
      position: { x: startX + index * 250, y: 200 },
      data: {
        label: s.label,
        kind: s.kind,
        agents: [],
        agentCount: 0,
        connectorIds: [],
        connectors: [],
        description: s.description,
      } satisfies StageNodeData,
    }));
    const updatedNodes = [...nodes, ...newNodes];
    setNodes(updatedNodes);
    pushHistory(updatedNodes, edges);
    setDirty(true);
    setAiSuggestions(null);
    setAiDescription('');
    setAiGoal('');
    toast.success(`Added ${newNodes.length} suggested stage(s)`);
  }, [aiSuggestions, nodes, edges, setNodes, pushHistory, setDirty]);

  // ── Render ─────────────────────────────────────────────────────

  if (isLoadingPipeline) {
    return (
      <div className="flex items-center justify-center h-screen w-screen bg-white text-gray-500">
        <div className="animate-spin h-6 w-6 border-2 border-indigo-600 border-t-transparent rounded-full mr-3" />
        Loading pipeline…
      </div>
    );
  }

  if (isLoadError) {
    return (
      <div className="flex flex-col items-center justify-center h-screen w-screen bg-white text-gray-500">
        <h2 className="text-lg font-semibold text-gray-900 mb-2">Failed to load pipeline</h2>
        <p className="text-sm mb-4">{loadError instanceof Error ? loadError.message : 'Unknown error'}</p>
        <a href="/build/pipelines" className="text-indigo-600 hover:underline text-sm">
          Back to pipeline list
        </a>
      </div>
    );
  }

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
        {aiOpen && (
          <div className="border-b border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-800 dark:bg-gray-950">
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">
                AI Stage Assistant
              </h3>
              <button
                type="button"
                onClick={() => setAiOpen(false)}
                className="text-xs text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
              >
                Close
              </button>
            </div>
            <div className="flex gap-2 mb-2">
              <input
                type="text"
                value={aiDescription}
                onChange={(e) => setAiDescription(e.target.value)}
                placeholder="Describe what this pipeline should do..."
                className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void handleAiSuggest();
                }}
              />
              <input
                type="text"
                value={aiGoal}
                onChange={(e) => setAiGoal(e.target.value)}
                placeholder="Goal (optional)"
                className="w-40 rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
              />
              <button
                type="button"
                onClick={() => void handleAiSuggest()}
                disabled={aiLoading || !aiDescription.trim()}
                className="rounded-md bg-indigo-600 px-3 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
              >
                {aiLoading ? 'Thinking...' : 'Suggest'}
              </button>
            </div>
            {aiSuggestions && (
              <div className="mt-2">
                <div className="flex items-center justify-between mb-1">
                  <p className="text-xs text-gray-600 dark:text-gray-400">
                    Confidence: {Math.round(aiConfidence * 100)}%
                  </p>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => setAiSuggestions(null)}
                      className="text-xs text-gray-500 hover:text-gray-700 dark:text-gray-400"
                    >
                      Dismiss
                    </button>
                    <button
                      type="button"
                      onClick={handleApplySuggestions}
                      className="rounded-md bg-green-600 px-2 py-1 text-xs font-medium text-white hover:bg-green-700"
                    >
                      Apply Suggestions
                    </button>
                  </div>
                </div>
                <p className="text-xs text-gray-500 dark:text-gray-400 mb-2">{aiExplanation}</p>
                <div className="flex gap-2 overflow-x-auto pb-1">
                  {aiSuggestions.map((s, i) => (
                    <div
                      key={i}
                      className="flex-shrink-0 rounded-md border border-gray-200 bg-white px-3 py-2 text-xs dark:border-gray-700 dark:bg-gray-900"
                    >
                      <p className="font-semibold text-gray-900 dark:text-gray-100">{s.label}</p>
                      <p className="text-gray-500 dark:text-gray-400">{s.kind}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
        <div className="flex flex-1 overflow-hidden">
          {/* Left panel — hidden on small screens when closed, toggleable */}
          {leftPanelOpen && (
            <div className="hidden lg:block w-60 flex-shrink-0 border-r border-gray-200 dark:border-gray-800 bg-gray-50 dark:bg-gray-950 overflow-y-auto">
              <StagePalette />
            </div>
          )}

          {/* Mobile panel toggles + canvas */}
          <div className="flex flex-1 flex-col overflow-hidden">
            {/* Mobile panel toggle bar */}
            <div className="lg:hidden flex items-center gap-2 px-3 py-1.5 border-b border-gray-200 dark:border-gray-800 bg-gray-50 dark:bg-gray-950">
              <button
                type="button"
                onClick={() => setLeftPanelOpen((p) => !p)}
                className="p-1.5 rounded-md text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-800"
                aria-label={leftPanelOpen ? 'Hide stage palette' : 'Show stage palette'}
                aria-pressed={leftPanelOpen}
              >
                {leftPanelOpen ? <PanelLeftClose className="h-4 w-4" /> : <PanelLeftOpen className="h-4 w-4" />}
              </button>
              <span className="flex-1" />
              <button
                type="button"
                onClick={() => setRightPanelOpen((p) => !p)}
                className="p-1.5 rounded-md text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-800"
                aria-label={rightPanelOpen ? 'Hide property panel' : 'Show property panel'}
                aria-pressed={rightPanelOpen}
              >
                {rightPanelOpen ? <PanelRightClose className="h-4 w-4" /> : <PanelRightOpen className="h-4 w-4" />}
              </button>
            </div>

            {/* Mobile slide-over panels */}
            {leftPanelOpen && (
              <div className="lg:hidden fixed inset-y-0 left-0 z-40 w-60 bg-white dark:bg-gray-950 shadow-xl border-r border-gray-200 dark:border-gray-800 overflow-y-auto">
                <div className="flex items-center justify-between p-3 border-b border-gray-200 dark:border-gray-800">
                  <span className="text-sm font-semibold text-gray-900 dark:text-gray-100">Stages</span>
                  <button
                    type="button"
                    onClick={() => setLeftPanelOpen(false)}
                    className="p-1 rounded-md text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800"
                    aria-label="Close stage palette"
                  >
                    <PanelLeftClose className="h-4 w-4" />
                  </button>
                </div>
                <StagePalette />
              </div>
            )}
            {leftPanelOpen && <div className="lg:hidden fixed inset-0 z-30 bg-black/50" onClick={() => setLeftPanelOpen(false)} aria-hidden="true" />}

            <PipelineErrorBoundary>
              <PipelineCanvas />
            </PipelineErrorBoundary>

            {rightPanelOpen && (
              <div className="lg:hidden fixed inset-y-0 right-0 z-40 w-72 bg-white dark:bg-gray-950 shadow-xl border-l border-gray-200 dark:border-gray-800 overflow-y-auto">
                <div className="flex items-center justify-between p-3 border-b border-gray-200 dark:border-gray-800">
                  <span className="text-sm font-semibold text-gray-900 dark:text-gray-100">Properties</span>
                  <button
                    type="button"
                    onClick={() => setRightPanelOpen(false)}
                    className="p-1 rounded-md text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800"
                    aria-label="Close property panel"
                  >
                    <PanelRightClose className="h-4 w-4" />
                  </button>
                </div>
                <PipelinePropertyPanel />
              </div>
            )}
            {rightPanelOpen && <div className="lg:hidden fixed inset-0 z-30 bg-black/50" onClick={() => setRightPanelOpen(false)} aria-hidden="true" />}
          </div>

          {/* Right panel — hidden on small screens when closed */}
          {rightPanelOpen && (
            <div className="hidden lg:block w-72 flex-shrink-0 border-l border-gray-200 dark:border-gray-800 bg-gray-50 dark:bg-gray-950 overflow-y-auto">
              <PipelinePropertyPanel />
            </div>
          )}
        </div>
      </div>
    </>
  );
}
