/**
 * PipelineToolbar — Top toolbar for pipeline builder actions.
 *
 * Provides save, validate, export, undo/redo, and status display.
 *
 * @doc.type component
 * @doc.purpose Pipeline editor toolbar
 * @doc.layer frontend
 */
import React from 'react';
import { useAtom, useAtomValue } from 'jotai';
import clsx from 'clsx';
import {
  pipelineAtom,
  isDirtyAtom,
  pipelineStatusAtom,
  canUndoAtom,
  canRedoAtom,
  stageCountAtom,
  totalAgentCountAtom,
  validationAtom,
} from '@/stores/pipeline.store';
import type { PipelineStatus } from '@/types/pipeline.types';

// ─── Status badge colors ─────────────────────────────────────────────

const STATUS_STYLES: Record<PipelineStatus, string> = {
  DRAFT: 'bg-gray-200 text-gray-700',
  VALID: 'bg-green-100 text-green-800',
  PUBLISHED: 'bg-blue-100 text-blue-800',
  RUNNING: 'bg-amber-100 text-amber-800',
  FAILED: 'bg-red-100 text-red-800',
  ARCHIVED: 'bg-gray-300 text-gray-600',
};

// ─── Toolbar Props ───────────────────────────────────────────────────

export interface PipelineToolbarProps {
  onSave: () => void;
  onValidate: () => void;
  onExport: () => void;
  onUndo: () => void;
  onRedo: () => void;
  onNew: () => void;
  onRunNow?: () => void;
  saving?: boolean;
  validating?: boolean;
  running?: boolean;
}

export function PipelineToolbar({
  onSave,
  onValidate,
  onExport,
  onUndo,
  onRedo,
  onNew,
  onRunNow,
  saving = false,
  validating = false,
  running = false,
}: PipelineToolbarProps) {
  const [pipeline] = useAtom(pipelineAtom);
  const dirty = useAtomValue(isDirtyAtom);
  const status = useAtomValue(pipelineStatusAtom);
  const canUndo = useAtomValue(canUndoAtom);
  const canRedo = useAtomValue(canRedoAtom);
  const stages = useAtomValue(stageCountAtom);
  const agents = useAtomValue(totalAgentCountAtom);
  const validation = useAtomValue(validationAtom);

  const hasErrors = validation && !validation.isValid;

  return (
    <header
      className="h-12 border-b border-gray-200 bg-white flex items-center px-4 gap-3"
      data-testid="pipeline-toolbar"
    >
      {/* Pipeline name */}
      <h1 className="text-sm font-semibold truncate max-w-[200px]">
        {pipeline.name}
        {dirty && <span className="ml-1 text-amber-500">●</span>}
      </h1>

      {/* Status badge */}
      <span
        className={clsx(
          'px-2 py-0.5 rounded-full text-[10px] font-bold uppercase',
          STATUS_STYLES[status],
        )}
      >
        {status}
      </span>

      {/* Stats */}
      <span className="text-xs text-gray-500 hidden sm:inline">
        {stages} stage{stages !== 1 ? 's' : ''} · {agents} agent{agents !== 1 ? 's' : ''}
      </span>

      {/* Spacer */}
      <div className="flex-1" />

      {/* Undo / Redo */}
      <button
        onClick={onUndo}
        disabled={!canUndo}
        className="p-1.5 rounded hover:bg-gray-100 disabled:opacity-30"
        title="Undo (Ctrl+Z)"
        data-testid="btn-undo"
      >
        ↩
      </button>
      <button
        onClick={onRedo}
        disabled={!canRedo}
        className="p-1.5 rounded hover:bg-gray-100 disabled:opacity-30"
        title="Redo (Ctrl+Y)"
        data-testid="btn-redo"
      >
        ↪
      </button>

      {/* Divider */}
      <div className="w-px h-6 bg-gray-200" />

      {/* Validate */}
      <button
        onClick={onValidate}
        disabled={validating}
        className={clsx(
          'px-3 py-1 text-xs font-medium rounded border transition-colors',
          hasErrors
            ? 'border-red-300 bg-red-50 text-red-700 hover:bg-red-100'
            : 'border-gray-200 hover:bg-gray-100',
          'disabled:opacity-50',
        )}
        data-testid="btn-validate"
      >
        {validating ? 'Validating…' : hasErrors ? `⚠ ${validation.errors.length} errors` : 'Validate'}
      </button>

      {/* Save */}
      <button
        onClick={onSave}
        disabled={!dirty || saving}
        className="px-3 py-1 text-xs font-medium rounded bg-blue-600 text-white
                   hover:bg-blue-700 disabled:opacity-40 transition-colors"
        data-testid="btn-save"
      >
        {saving ? 'Saving…' : 'Save'}
      </button>

      {/* Run Now */}
      {onRunNow && (
        <button
          onClick={onRunNow}
          disabled={running}
          className="px-3 py-1 text-xs font-medium rounded bg-green-600 text-white
                     hover:bg-green-700 disabled:opacity-40 transition-colors"
          data-testid="btn-run-now"
        >
          {running ? 'Running…' : '▶ Run'}
        </button>
      )}

      {/* Export */}
      <button
        onClick={onExport}
        className="px-3 py-1 text-xs font-medium rounded border border-gray-200
                   hover:bg-gray-100 transition-colors"
        data-testid="btn-export"
      >
        Export JSON
      </button>

      {/* New */}
      <button
        onClick={onNew}
        className="px-3 py-1 text-xs font-medium rounded border border-gray-200
                   hover:bg-gray-100 transition-colors"
        data-testid="btn-new"
      >
        + New
      </button>
    </header>
  );
}
