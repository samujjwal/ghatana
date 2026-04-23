/**
 * PipelineToolbar — Top toolbar for pipeline builder actions.
 *
 * Provides save, validate, export, undo/redo, and status display.
 *
 * @doc.type component
 * @doc.purpose Pipeline editor toolbar
 * @doc.layer frontend
 */
import React, { useState } from 'react';
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
import { Button } from '@ghatana/design-system';

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
      <Tooltip content="Undo last change (Ctrl+Z)">
        <Button
          onClick={onUndo}
          disabled={!canUndo}
          variant="ghost"
          className="p-1.5"
          data-testid="btn-undo"
          aria-label="Undo last change"
          aria-keyshortcuts="Control+z"
        >
          ↩
        </Button>
      </Tooltip>
      <Tooltip content="Redo last change (Ctrl+Y)">
        <Button
          onClick={onRedo}
          disabled={!canRedo}
          variant="ghost"
          className="p-1.5"
          data-testid="btn-redo"
          aria-label="Redo last change"
          aria-keyshortcuts="Control+y"
        >
          ↪
        </Button>
      </Tooltip>

      {/* Divider */}
      <div className="w-px h-6 bg-gray-200" />

      {/* Validate */}
      <Tooltip content="Validate pipeline structure and dependencies">
        <Button
          onClick={onValidate}
          disabled={validating}
          variant="secondary"
          className={clsx(
            'px-3 py-1 text-xs font-medium',
            hasErrors && 'border-red-300 bg-red-50 text-red-700 hover:bg-red-100'
          )}
          data-testid="btn-validate"
        >
          {validating ? 'Validating…' : hasErrors ? `⚠ ${validation.errors.length} errors` : 'Validate'}
        </Button>
      </Tooltip>

      {/* Save */}
      <Tooltip content="Save pipeline to backend">
        <Button
          onClick={onSave}
          disabled={!dirty || saving}
          variant="primary"
          className="px-3 py-1 text-xs font-medium"
          style={{ backgroundColor: '#2563eb' }}
          data-testid="btn-save"
        >
          {saving ? 'Saving…' : 'Save'}
        </Button>
      </Tooltip>

      {/* Run Now */}
      {onRunNow && (
        <Tooltip content="Trigger pipeline execution with test event">
          <Button
            onClick={onRunNow}
            disabled={running}
            variant="primary"
            className="px-3 py-1 text-xs font-medium"
            style={{ backgroundColor: '#16a34a' }}
            data-testid="btn-run-now"
          >
            {running ? 'Running…' : '▶ Run'}
          </Button>
        </Tooltip>
      )}

      {/* Export */}
      <Tooltip content="Export pipeline as JSON file">
        <Button
          onClick={onExport}
          variant="secondary"
          className="px-3 py-1 text-xs font-medium"
          data-testid="btn-export"
        >
          Export JSON
        </Button>
      </Tooltip>

      {/* New */}
      <Tooltip content="Create new pipeline (unsaved changes will be lost)">
        <Button
          onClick={onNew}
          variant="secondary"
          className="px-3 py-1 text-xs font-medium"
          data-testid="btn-new"
        >
          + New
        </Button>
      </Tooltip>
    </header>
  );
}
