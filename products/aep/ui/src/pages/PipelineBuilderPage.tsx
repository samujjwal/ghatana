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
import { useSearchParams, useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { toast, Toaster } from 'sonner';
import { useAuth } from '@/context/AuthContext';
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
  exportPipelineSpec,
} from '@/api/pipeline.api';
import { tenantIdAtom } from '@/stores/tenant.store';
import { ConfidenceExplanation } from '@/components/shared/ConfidenceExplanation';
import {
  getAiConfidenceTier,
  getAiRouting,
  getAiRoutingDescription,
  getAiRoutingLabel,
  normalizeAiSources,
  type AiAssistSource,
} from '@/lib/ai-assist';
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
  const { hasAnyRole, isVerifyingAuth } = useAuth();
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
  const routeParams = useParams<{ pipelineId?: string }>();
  const pipelineId = routeParams.pipelineId ?? searchParams.get('id') ?? undefined;

  // Loading states for async actions
  const [saving, setSaving] = useState(false);
  const [validating, setValidating] = useState(false);
  const [running, setRunning] = useState(false);

  // ── Guided Assistance ───────────────────────────────────────────────
  // Suggestions panel is opt-in; guided default mode keeps the canvas clean (TASK-L2)
  const isNewPipeline = !pipelineId;
  const [suggestionsOpen, setSuggestionsOpen] = useState(false);
  const [suggestionDescription, setSuggestionDescription] = useState('');
  const [suggestionGoal, setSuggestionGoal] = useState('');
  const [suggestionsLoading, setSuggestionsLoading] = useState(false);
  const [suggestions, setSuggestions] = useState<
    { label: string; kind: StageKind; description: string }[] | null
  >(null);
  const [suggestionExplanation, setSuggestionExplanation] = useState('');
  const [suggestionConfidence, setSuggestionConfidence] = useState(0);
  const [suggestionSources, setSuggestionSources] = useState<AiAssistSource[]>([]);

  // ── Responsive panel toggles ────────────────────────────────────
  const [leftPanelOpen, setLeftPanelOpen] = useState(true);
  const [rightPanelOpen, setRightPanelOpen] = useState(true);

  // ── Discard-changes confirmation dialog ──────────────────────────
  const [discardDialogOpen, setDiscardDialogOpen] = useState(false);
  const [pendingNewAction, setPendingNewAction] = useState(false);

  // ── Mobile viewport advisory ──────────────────────────────────
  const [mobileAdvisoryDismissed, setMobileAdvisoryDismissed] = useState(false);
  const isMobileViewport = typeof window !== 'undefined' && window.innerWidth < 1024;
  const showMobileAdvisory = isMobileViewport && !mobileAdvisoryDismissed;
  const canManagePipelines = hasAnyRole(['admin', 'operator']);

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
          kind: stage.kind ?? 'custom',
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
    const stageNodes = nodes.filter((n) => n.type === 'stage');
    const hasValidationOrEnrichment = stageNodes.some(
      (n) => (n.data as StageNodeData).kind === 'validation' || (n.data as StageNodeData).kind === 'enrichment',
    );
    if (!hasValidationOrEnrichment) {
      toast.warning(
        'Governance cue: This pipeline has no validation or enrichment stage. Consider adding one before saving.',
        { duration: 8000 },
      );
    }

    setSaving(true);
    try {
      const spec = nodesToSpec(pipeline, nodes);
      const saved = await savePipeline(spec, tenantId);
      setPipeline(saved);
      setDirty(false);
      toast.success('Pipeline saved');
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      toast.error(`Save failed: ${msg}`);
    } finally {
      setSaving(false);
    }
  }, [pipeline, nodes, tenantId, setPipeline, setDirty]);

  // ── Validate ────────────────────────────────────────────────────

  const handleValidate = useCallback(async () => {
    setValidating(true);
    try {
      const spec = nodesToSpec(pipeline, nodes);
      const result = await validatePipeline(spec, tenantId);
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
  }, [pipeline, nodes, tenantId, setValidation, setStatus]);

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

  // ── Keyboard shortcuts ───────────────────────────────────────────

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      const isMeta = e.ctrlKey || e.metaKey;
      if (!isMeta) return;

      // Save: Ctrl/Cmd+S
      if (e.key === 's') {
        e.preventDefault();
        void handleSave();
        return;
      }

      // Validate: Ctrl/Cmd+Shift+V
      if (e.key === 'V' && e.shiftKey) {
        e.preventDefault();
        void handleValidate();
        return;
      }

      // Undo: Ctrl/Cmd+Z
      if (e.key === 'z' && !e.shiftKey) {
        e.preventDefault();
        handleUndo();
        return;
      }

      // Redo: Ctrl/Cmd+Shift+Z or Ctrl/Cmd+Y
      if ((e.key === 'z' && e.shiftKey) || e.key === 'y') {
        e.preventDefault();
        handleRedo();
        return;
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [handleSave, handleValidate, handleUndo, handleRedo]);

  // ── Run Now ─────────────────────────────────────────────────────

  const [pipelineStatus] = useAtom(pipelineStatusAtom);
  const [validationResult] = useAtom(validationAtom);

  const handleRunNow = useCallback(async () => {
    if (!pipeline.id) {
      toast.error('Save the pipeline before running.');
      return;
    }
    if (pipelineStatus !== 'VALID' && !validationResult?.isValid) {
      toast.error('Validate the pipeline before running.');
      return;
    }
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
  }, [pipeline.id, pipelineStatus, validationResult, tenantId]);

  // ── New Pipeline ───────────────────────────────────────────────

  const executeNewPipeline = useCallback(() => {
    setPipeline({ name: 'Untitled Pipeline', stages: [], status: 'DRAFT', version: 1 });
    setNodes([]);
    setEdges([]);
    setHistory([]);
    setHistoryIndex(0);
    setDirty(false);
    setValidation(null);
    setStatus('DRAFT');
  }, [setPipeline, setNodes, setEdges, setHistory, setHistoryIndex, setDirty, setValidation, setStatus]);

  const handleNew = useCallback(() => {
    if (isDirty) {
      setPendingNewAction(true);
      setDiscardDialogOpen(true);
      return;
    }
    executeNewPipeline();
  }, [isDirty, executeNewPipeline]);

  const handleDiscardConfirm = useCallback(() => {
    setDiscardDialogOpen(false);
    setPendingNewAction(false);
    executeNewPipeline();
  }, [executeNewPipeline]);

  const handleDiscardCancel = useCallback(() => {
    setDiscardDialogOpen(false);
    setPendingNewAction(false);
  }, []);

  const requestSuggestions = useCallback(async (description: string, goal?: string) => {
    if (!description.trim()) return;
    setSuggestionsLoading(true);
    try {
      const result = await suggestPipelineStages(
        {
          description,
          goal,
          existingStages: nodes
            .filter((n): n is typeof n & { type: 'stage' } => n.type === 'stage')
            .map((n) => ({
              name: (n.data as StageNodeData).label,
              kind: (n.data as StageNodeData).kind,
              description: (n.data as StageNodeData).description,
            })) satisfies import('@/api/pipeline.api').PipelineStage[],
        },
        tenantId,
      );
      setSuggestions(
        result.suggestedStages.map((s) => ({
          label: s.name,
          kind: (s.kind as StageKind) ?? 'custom',
          description: s.description ?? '',
        })),
      );
      setSuggestionExplanation(result.rationale ?? result.message ?? 'Suggestions prepared from the current tenant context.');
      setSuggestionConfidence(result.confidence);
      setSuggestionSources(normalizeAiSources(result.evidence));
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      toast.error(`Suggestion failed: ${msg}`);
    } finally {
      setSuggestionsLoading(false);
    }
  }, [nodes, tenantId]);

  const handleSuggest = useCallback(async () => {
    await requestSuggestions(suggestionDescription, suggestionGoal || undefined);
  }, [suggestionDescription, suggestionGoal, requestSuggestions]);

  const handleApplySuggestions = useCallback(() => {
    if (!suggestions) return;
    const startX = 100 + nodes.length * 250;
    const newNodes = suggestions.map((s: { label: string; kind: StageKind; description: string }, index: number) => ({
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
    setSuggestions(null);
    setSuggestionDescription('');
    setSuggestionGoal('');
    setSuggestionSources([]);
    toast.success(`Added ${newNodes.length} suggested stage(s)`);
  }, [suggestions, nodes, edges, setNodes, pushHistory, setDirty]);

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

      {/* T-13: Accessible discard-changes confirmation dialog (replaces window.confirm) */}
      {discardDialogOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="discard-dialog-title"
          aria-describedby="discard-dialog-desc"
          className="fixed inset-0 z-[100] flex items-center justify-center"
        >
          <div
            className="absolute inset-0 bg-black/40"
            onClick={handleDiscardCancel}
            aria-hidden="true"
          />
          <div className="relative z-10 w-full max-w-sm rounded-2xl bg-white shadow-xl p-6">
            <h2
              id="discard-dialog-title"
              className="text-base font-semibold text-gray-900 mb-2"
            >
              Discard unsaved changes?
            </h2>
            <p
              id="discard-dialog-desc"
              className="text-sm text-gray-600 mb-5"
            >
              You have unsaved changes. Starting a new pipeline will discard them permanently.
            </p>
            <div className="flex justify-end gap-3">
              <button
                type="button"
                onClick={handleDiscardCancel}
                className="rounded-lg px-4 py-2 text-sm font-medium text-gray-700 border border-gray-300 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              >
                Keep editing
              </button>
              <button
                type="button"
                onClick={handleDiscardConfirm}
                className="rounded-lg px-4 py-2 text-sm font-medium text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-red-500"
                autoFocus
              >
                Discard changes
              </button>
            </div>
          </div>
        </div>
      )}

      {showMobileAdvisory && (
        <div className="lg:hidden fixed bottom-4 left-4 right-4 z-50 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 shadow-lg dark:border-amber-900 dark:bg-amber-950/80 dark:text-amber-100">
          <div className="flex items-start gap-3">
            <div className="flex-1">
              <p className="text-sm font-medium text-amber-900 dark:text-amber-200">
                Mobile authoring is limited
              </p>
              <p className="mt-0.5 text-xs text-amber-700 dark:text-amber-300">
                Drag-and-drop and advanced stage editing work best on a desktop. You can view and make basic edits here.
              </p>
            </div>
            <button
              type="button"
              onClick={() => setMobileAdvisoryDismissed(true)}
              className="text-xs font-medium text-amber-800 dark:text-amber-300 hover:underline flex-shrink-0"
            >
              Dismiss
            </button>
          </div>
        </div>
      )}
      <div className="flex flex-col h-screen w-screen bg-white" data-testid="pipeline-builder">
        {!isVerifyingAuth && !canManagePipelines && (
          <div className="border-b border-amber-200 bg-amber-50 px-4 py-2 text-xs text-amber-900 dark:border-amber-900 dark:bg-amber-950/50 dark:text-amber-200">
            Read-only access: saving and running pipelines requires an operator or admin role.
          </div>
        )}
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
          canPersistChanges={canManagePipelines}
          canRunPipelines={canManagePipelines}
        />
        {suggestionsOpen && (
          <div className="border-b border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-800 dark:bg-gray-950">
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">
                Suggested stages
              </h3>
              <button
                type="button"
                onClick={() => setSuggestionsOpen(false)}
                className="text-xs text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
              >
                Close
              </button>
            </div>
            <div className="flex gap-2 mb-2">
              <input
                type="text"
                value={suggestionDescription}
                onChange={(e) => setSuggestionDescription(e.target.value)}
                placeholder="Describe what this pipeline should do..."
                className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void handleSuggest();
                }}
              />
              <input
                type="text"
                value={suggestionGoal}
                onChange={(e) => setSuggestionGoal(e.target.value)}
                placeholder="Goal (optional)"
                className="w-40 rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
              />
              <button
                type="button"
                onClick={() => void handleSuggest()}
                disabled={suggestionsLoading || !suggestionDescription.trim()}
                className="rounded-md bg-indigo-600 px-3 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
              >
                {suggestionsLoading ? 'Preparing...' : 'Suggest'}
              </button>
            </div>
            <div className="mt-2 flex gap-2 flex-wrap">
              {[
                { label: 'Validate → Enrich → Route', template: 'Validate incoming events, enrich with metadata, then route to downstream systems' },
                { label: 'Error triage pipeline', template: 'Triage error events by severity, correlate with traces, and alert on-call team' },
                { label: 'Data quality gate', template: 'Check data quality rules, reject invalid records, and emit quality metrics' },
                { label: 'Audit log collector', template: 'Collect audit events, normalize schema, and write to durable audit store' },
              ].map((t) => (
                <button
                  key={t.label}
                  type="button"
                  onClick={() => {
                    setSuggestionDescription(t.template);
                    void requestSuggestions(t.template, suggestionGoal || undefined);
                  }}
                  className="rounded-full border border-gray-200 bg-white px-2.5 py-1 text-[11px] text-gray-600 hover:border-indigo-300 hover:bg-indigo-50 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300 dark:hover:border-indigo-700 dark:hover:bg-indigo-950"
                >
                  {t.label}
                </button>
              ))}
            </div>
            {suggestions && (
              <div className="mt-2">
                <div className="flex items-center justify-between mb-1">
                  <p className="text-xs text-gray-600 dark:text-gray-400">
                    {getAiRoutingLabel(getAiRouting(suggestionConfidence))}
                  </p>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => {
                        setSuggestions(null);
                        setSuggestionSources([]);
                      }}
                      className="text-xs text-gray-500 hover:text-gray-700 dark:text-gray-400"
                    >
                      Dismiss
                    </button>
                    <button
                      type="button"
                      onClick={handleApplySuggestions}
                      disabled={getAiRouting(suggestionConfidence) === 'advisory'}
                      className="rounded-md bg-green-600 px-2 py-1 text-xs font-medium text-white hover:bg-green-700"
                    >
                      {getAiRouting(suggestionConfidence) === 'reviewable' ? 'Apply after review' : 'Apply suggestions'}
                    </button>
                  </div>
                </div>
                <ConfidenceExplanation
                  tier={getAiConfidenceTier(suggestionConfidence)}
                  score={suggestionConfidence}
                  reasoning={suggestionExplanation || getAiRoutingDescription(getAiRouting(suggestionConfidence))}
                  evidenceUrl={suggestionSources.find((source) => source.href)?.href}
                  className="mb-2"
                />
                {suggestionSources.length > 0 && (
                  <div className="mb-2 flex flex-wrap gap-2 text-[11px] text-gray-500 dark:text-gray-400">
                    {suggestionSources.map((source) => (
                      source.href ? (
                        <a
                          key={source.label}
                          href={source.href}
                          target="_blank"
                          rel="noreferrer"
                          className="rounded-full border border-gray-200 px-2 py-1 hover:underline dark:border-gray-700"
                        >
                          {source.label}
                        </a>
                      ) : (
                        <span
                          key={source.label}
                          className="rounded-full border border-gray-200 px-2 py-1 dark:border-gray-700"
                        >
                          {source.label}
                        </span>
                      )
                    ))}
                  </div>
                )}
                {getAiRouting(suggestionConfidence) === 'advisory' && (
                  <p className="mb-2 text-xs font-medium text-amber-700 dark:text-amber-300">
                    Advisory only: inspect the suggested stages and evidence before adding them manually.
                  </p>
                )}
                <div className="flex gap-2 overflow-x-auto pb-1">
                  {suggestions.map((s, i) => (
                    <div
                      key={i}
                      className="flex-shrink-0 rounded-md border border-gray-200 bg-white px-3 py-2 text-xs dark:border-gray-700 dark:bg-gray-900"
                    >
                      <p className="font-semibold text-gray-900 dark:text-gray-100">{s.label}</p>
                      <p className="text-gray-500 dark:text-gray-400">{s.kind}</p>
                      {s.description && (
                        <p className="mt-1 max-w-40 text-[11px] text-gray-500 dark:text-gray-400">{s.description}</p>
                      )}
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
