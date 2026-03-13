/**
 * PatternStudioPage — manage AEP event-detection patterns.
 *
 * Features:
 *   - List patterns with type, status, confidence score
 *   - Filter by type (ALL / ANOMALY / THRESHOLD / SEQUENCE / CORRELATION / CUSTOM)
 *   - Add new pattern (type + name + threshold)
 *   - Delete pattern
 *
 * @doc.type page
 * @doc.purpose AEP pattern catalog management
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import Editor from '@monaco-editor/react';
import {
  listPatterns,
  createPattern,
  deletePattern,
  type PatternSummary,
} from '@/api/pipeline.api';
import type { PatternType } from '@/types/pipeline.types';

// ─── Status badge ────────────────────────────────────────────────────

const STATUS_COLORS: Record<string, string> = {
  ENABLED: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
  DISABLED: 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300',
  LEARNING: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
};

function PatternTypeBadge({ type }: { type: PatternType }) {
  const colorMap: Record<PatternType, string> = {
    ANOMALY: 'bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300',
    THRESHOLD: 'bg-orange-50 text-orange-700 dark:bg-orange-950 dark:text-orange-300',
    SEQUENCE: 'bg-violet-50 text-violet-700 dark:bg-violet-950 dark:text-violet-300',
    CORRELATION: 'bg-cyan-50 text-cyan-700 dark:bg-cyan-950 dark:text-cyan-300',
    CUSTOM: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
  };
  return (
    <span
      className={[
        'inline-flex px-2 py-0.5 rounded text-xs font-medium',
        colorMap[type] ?? colorMap.CUSTOM,
      ].join(' ')}
    >
      {type}
    </span>
  );
}

// ─── Create form ─────────────────────────────────────────────────────

function CreatePatternForm({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const tenantId = useAtomValue(tenantIdAtom);
  const [name, setName] = useState('');
  const [type, setType] = useState<PatternType>('THRESHOLD');
  const [threshold, setThreshold] = useState('');
  const [description, setDescription] = useState('');
  const [config, setConfig] = useState('');

  const mut = useMutation({
    mutationFn: () =>
      createPattern(
        { name, type, threshold: threshold ? Number(threshold) : undefined, description, config: config.trim() || undefined },
        tenantId,
      ),
    onSuccess: () => {
      onCreated();
      onClose();
    },
  });

  return (
    <div
      role="dialog"
      aria-label="Create pattern"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
    >
      <div className="bg-white dark:bg-gray-900 rounded-xl shadow-xl p-6 w-full max-w-md">
        <h2 className="text-base font-semibold mb-4 text-gray-900 dark:text-white">New Pattern</h2>

        <div className="space-y-3">
          <label className="block text-sm">
            <span className="text-gray-600 dark:text-gray-400">Name</span>
            <input
              className="mt-1 block w-full rounded border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. high-error-rate"
            />
          </label>

          <label className="block text-sm">
            <span className="text-gray-600 dark:text-gray-400">Type</span>
            <select
              className="mt-1 block w-full rounded border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm"
              value={type}
              onChange={(e) => setType(e.target.value as PatternType)}
            >
              {(['THRESHOLD', 'ANOMALY', 'SEQUENCE', 'CORRELATION', 'CUSTOM'] as const).map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </label>

          {type === 'THRESHOLD' && (
            <label className="block text-sm">
              <span className="text-gray-600 dark:text-gray-400">Threshold value</span>
              <input
                type="number"
                className="mt-1 block w-full rounded border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                value={threshold}
                onChange={(e) => setThreshold(e.target.value)}
                placeholder="0.95"
              />
            </label>
          )}

          <label className="block text-sm">
            <span className="text-gray-600 dark:text-gray-400">Description (optional)</span>
            <textarea
              className="mt-1 block w-full rounded border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </label>

          <div className="block text-sm">
            <span className="text-gray-600 dark:text-gray-400">Config YAML (optional)</span>
            <div className="mt-1 rounded border border-gray-300 dark:border-gray-700 overflow-hidden">
              <Editor
                height={160}
                language="yaml"
                theme="vs-dark"
                value={config}
                onChange={(val: string | undefined) => setConfig(val ?? '')}
                options={{
                  minimap: { enabled: false },
                  lineNumbers: 'off',
                  fontSize: 12,
                  scrollBeyondLastLine: false,
                  wordWrap: 'on',
                  tabSize: 2,
                }}
              />
            </div>
          </div>
        </div>

        <div className="mt-5 flex justify-end gap-3">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm rounded-md border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800"
          >
            Cancel
          </button>
          <button
            onClick={() => mut.mutate()}
            disabled={!name || mut.isPending}
            className="px-4 py-2 text-sm rounded-md bg-indigo-600 hover:bg-indigo-700 text-white disabled:opacity-50"
          >
            {mut.isPending ? 'Creating…' : 'Create'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Page ────────────────────────────────────────────────────────────

const ALL_TYPES: Array<PatternType | 'ALL'> = [
  'ALL', 'ANOMALY', 'THRESHOLD', 'SEQUENCE', 'CORRELATION', 'CUSTOM',
];

export function PatternStudioPage() {
  const tenantId = useAtomValue(tenantIdAtom);
  const queryClient = useQueryClient();
  const [filterType, setFilterType] = useState<PatternType | 'ALL'>('ALL');
  const [showCreate, setShowCreate] = useState(false);

  const { data: patterns = [], isLoading } = useQuery({
    queryKey: ['aep', 'patterns', tenantId],
    queryFn: () => listPatterns(tenantId),
  });

  const deleteMut = useMutation({
    mutationFn: (id: string) => deletePattern(id, tenantId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['aep', 'patterns'] }),
  });

  const filtered =
    filterType === 'ALL' ? patterns : patterns.filter((p) => p.type === filterType);

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Header */}
      <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950 flex items-center gap-4">
        <h1 className="text-lg font-semibold text-gray-900 dark:text-white">Pattern Studio</h1>
        <div className="flex gap-1 ml-2">
          {ALL_TYPES.map((t) => (
            <button
              key={t}
              onClick={() => setFilterType(t)}
              className={[
                'px-3 py-1 rounded-full text-xs font-medium transition-colors',
                filterType === t
                  ? 'bg-indigo-100 dark:bg-indigo-900 text-indigo-700 dark:text-indigo-300'
                  : 'text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800',
              ].join(' ')}
            >
              {t}
            </button>
          ))}
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="ml-auto px-3 py-1.5 text-sm font-medium rounded-md bg-indigo-600 hover:bg-indigo-700 text-white"
        >
          + New pattern
        </button>
      </div>

      {/* List */}
      <div className="flex-1 overflow-auto px-6 py-4">
        {isLoading && <p className="text-center text-gray-400 py-12">Loading patterns…</p>}
        {!isLoading && (
          <div className="space-y-2">
            {filtered.length === 0 && (
              <p className="text-center text-gray-400 italic py-12">No patterns found</p>
            )}
            {filtered.map((pattern) => (
              <div
                key={pattern.id}
                className="flex items-center gap-4 p-4 rounded-lg border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900"
              >
                <div className="flex-1 min-w-0">
                  <p className="font-medium text-gray-900 dark:text-white truncate">{pattern.name}</p>
                  <p className="text-xs text-gray-400 font-mono mt-0.5">{pattern.id}</p>
                </div>
                <PatternTypeBadge type={pattern.type} />
                <span
                  className={[
                    'px-2 py-0.5 rounded-full text-xs font-medium',
                    STATUS_COLORS[pattern.status] ?? STATUS_COLORS.DISABLED,
                  ].join(' ')}
                >
                  {pattern.status}
                </span>
                <button
                  onClick={() => deleteMut.mutate(pattern.id)}
                  aria-label={`Delete pattern ${pattern.name}`}
                  className="text-gray-400 hover:text-red-500 transition-colors text-sm ml-2"
                >
                  🗑
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {showCreate && (
        <CreatePatternForm
          onClose={() => setShowCreate(false)}
          onCreated={() => void queryClient.invalidateQueries({ queryKey: ['aep', 'patterns'] })}
        />
      )}
    </div>
  );
}
