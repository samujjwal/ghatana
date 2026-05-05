/**
 * Requirement Version Diff Viewer
 *
 * Shows a side-by-side (or inline) diff between two RequirementVersion snapshots.
 * Uses a pure-TypeScript line-level LCS diff — no external diff library needed.
 *
 * @doc.type component
 * @doc.purpose Compare two requirement version snapshots field-by-field
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useMemo, useState } from 'react';
import { Box, Card, CardContent, Chip, Typography } from '@ghatana/design-system';
import { ArrowLeft, ArrowRight, ChevronDown, ChevronUp } from 'lucide-react';

import type { RequirementVersion } from './types';

// ─────────────────────────────────────────────────────────────────────────────
// Diff primitives (no external dependency)
// ─────────────────────────────────────────────────────────────────────────────

type DiffOp = { kind: 'equal' | 'insert' | 'delete'; text: string };

/**
 * Simple O(nm) LCS-based line diff.
 * Returns a list of operations against old and new line arrays.
 */
function computeLineDiff(oldText: string, newText: string): DiffOp[] {
  const oldLines = oldText.split('\n');
  const newLines = newText.split('\n');
  const n = oldLines.length;
  const m = newLines.length;

  // Build LCS table
  const table: number[][] = Array.from({ length: n + 1 }, () => new Array<number>(m + 1).fill(0));
  for (let i = 1; i <= n; i++) {
    for (let j = 1; j <= m; j++) {
      table[i]![j] = oldLines[i - 1] === newLines[j - 1]
        ? (table[i - 1]![j - 1] ?? 0) + 1
        : Math.max(table[i - 1]![j] ?? 0, table[i]![j - 1] ?? 0);
    }
  }

  // Backtrack
  const ops: DiffOp[] = [];
  let i = n;
  let j = m;
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && oldLines[i - 1] === newLines[j - 1]) {
      ops.unshift({ kind: 'equal', text: oldLines[i - 1]! });
      i--;
      j--;
    } else if (j > 0 && (i === 0 || (table[i]![j - 1] ?? 0) >= (table[i - 1]![j] ?? 0))) {
      ops.unshift({ kind: 'insert', text: newLines[j - 1]! });
      j--;
    } else {
      ops.unshift({ kind: 'delete', text: oldLines[i - 1]! });
      i--;
    }
  }
  return ops;
}

function prettyJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

interface DiffLineProps {
  op: DiffOp;
}

const DiffLine: React.FC<DiffLineProps> = ({ op }) => {
  const lineClass =
    op.kind === 'insert'
      ? 'bg-emerald-50 text-emerald-900 dark:bg-emerald-900/20 dark:text-emerald-200 border-l-4 border-emerald-400'
      : op.kind === 'delete'
      ? 'bg-destructive-bg text-destructive dark:bg-destructive-bg/20 dark:text-destructive border-l-4 border-destructive-border line-through opacity-70'
      : 'text-text-primary';

  const prefix = op.kind === 'insert' ? '+ ' : op.kind === 'delete' ? '− ' : '  ';

  return (
    <div className={['px-3 py-0.5 font-mono text-xs whitespace-pre-wrap', lineClass].join(' ')}>
      <span className="select-none opacity-60">{prefix}</span>
      {op.text}
    </div>
  );
};

interface FieldDiffProps {
  fieldName: string;
  oldValue: string;
  newValue: string;
}

const FieldDiff: React.FC<FieldDiffProps> = ({ fieldName, oldValue, newValue }) => {
  const [expanded, setExpanded] = useState(true);
  const ops = useMemo(() => computeLineDiff(oldValue, newValue), [oldValue, newValue]);
  const hasChanges = ops.some((op) => op.kind !== 'equal');

  return (
    <Box className="rounded-md border border-divider overflow-hidden" data-testid={`diff-field-${fieldName}`}>
      <button
        type="button"
        onClick={() => setExpanded((prev) => !prev)}
        className="flex w-full items-center justify-between gap-2 px-3 py-2 text-left hover:bg-grey-50 dark:hover:bg-grey-800 transition-colors"
        aria-expanded={expanded}
      >
        <Box className="flex items-center gap-2">
          <Typography className="text-sm font-semibold capitalize">{fieldName}</Typography>
          {!hasChanges && (
            <Chip label="Unchanged" size="sm" className="bg-grey-100 text-grey-600 text-xs" />
          )}
          {hasChanges && (
            <Chip label="Changed" size="sm" className="bg-warning-bg text-warning-color text-xs" />
          )}
        </Box>
        {expanded ? <ChevronUp className="h-4 w-4 text-text-secondary" /> : <ChevronDown className="h-4 w-4 text-text-secondary" />}
      </button>

      {expanded && (
        <Box className="divide-y divide-divider bg-grey-50 dark:bg-grey-900/50">
          {ops.map((op, idx) => (
            // eslint-disable-next-line react/no-array-index-key
            <DiffLine key={idx} op={op} />
          ))}
        </Box>
      )}
    </Box>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// Rich version type used by the diff viewer
// (extends the base RequirementVersion with optional JSON fields from the API)
// ─────────────────────────────────────────────────────────────────────────────

export interface RequirementVersionFull extends RequirementVersion {
  /** Full content snapshot (title + description as JSON) */
  content?: Record<string, unknown> | null;
  /** Acceptance criteria snapshot */
  acceptanceCriteria?: Record<string, unknown> | null;
}

// ─────────────────────────────────────────────────────────────────────────────
// Public component
// ─────────────────────────────────────────────────────────────────────────────

export interface RequirementVersionDiffProps {
  /** Older snapshot */
  fromVersion: RequirementVersionFull;
  /** Newer snapshot */
  toVersion: RequirementVersionFull;
}

/**
 * RequirementVersionDiff
 *
 * Side-by-side header showing which versions are being compared, then
 * collapsible field-level diff sections for: summary, content, acceptanceCriteria.
 *
 * @example
 * ```tsx
 * <RequirementVersionDiff fromVersion={v1} toVersion={v2} />
 * ```
 */
export const RequirementVersionDiff: React.FC<RequirementVersionDiffProps> = ({
  fromVersion,
  toVersion,
}) => {
  const summaryOps = useMemo(
    () => computeLineDiff(fromVersion.summary ?? '', toVersion.summary ?? ''),
    [fromVersion.summary, toVersion.summary]
  );

  const contentOps = useMemo(
    () =>
      computeLineDiff(
        prettyJson(fromVersion.content ?? {}),
        prettyJson(toVersion.content ?? {})
      ),
    [fromVersion.content, toVersion.content]
  );

  const criteriaOps = useMemo(
    () =>
      computeLineDiff(
        prettyJson(fromVersion.acceptanceCriteria ?? {}),
        prettyJson(toVersion.acceptanceCriteria ?? {})
      ),
    [fromVersion.acceptanceCriteria, toVersion.acceptanceCriteria]
  );

  const totalInserts = [...summaryOps, ...contentOps, ...criteriaOps].filter(
    (op) => op.kind === 'insert'
  ).length;
  const totalDeletes = [...summaryOps, ...contentOps, ...criteriaOps].filter(
    (op) => op.kind === 'delete'
  ).length;

  return (
    <Card data-testid="requirement-version-diff">
      <CardContent className="space-y-4 p-4">
        {/* Version header */}
        <Box className="flex items-center gap-3 flex-wrap">
          <Box className="flex items-center gap-2 rounded-md bg-destructive-bg dark:bg-destructive-bg/20 px-3 py-1.5">
            <ArrowLeft className="h-3 w-3 text-destructive" aria-hidden="true" />
            <Typography className="text-sm font-semibold text-destructive dark:text-destructive">
              v{fromVersion.version}
            </Typography>
            <Typography className="text-xs text-destructive dark:text-destructive">
              by {fromVersion.createdBy}
            </Typography>
          </Box>

          <Typography className="text-sm text-text-secondary">→</Typography>

          <Box className="flex items-center gap-2 rounded-md bg-emerald-50 dark:bg-emerald-900/20 px-3 py-1.5">
            <Typography className="text-sm font-semibold text-emerald-700 dark:text-emerald-300">
              v{toVersion.version}
            </Typography>
            <Typography className="text-xs text-emerald-500 dark:text-emerald-400">
              by {toVersion.createdBy}
            </Typography>
            <ArrowRight className="h-3 w-3 text-emerald-600" aria-hidden="true" />
          </Box>

          <Box className="ml-auto flex gap-2">
            {totalInserts > 0 && (
              <Chip
                label={`+${totalInserts}`}
                size="sm"
                className="bg-emerald-100 text-emerald-700 font-mono text-xs"
                aria-label={`${totalInserts} added lines`}
              />
            )}
            {totalDeletes > 0 && (
              <Chip
                label={`-${totalDeletes}`}
                size="sm"
                className="bg-destructive-bg text-destructive font-mono text-xs"
                aria-label={`${totalDeletes} removed lines`}
              />
            )}
            {totalInserts === 0 && totalDeletes === 0 && (
              <Chip label="No changes" size="sm" className="bg-grey-100 text-grey-600 text-xs" />
            )}
          </Box>
        </Box>

        {/* Field diffs */}
        <Box className="space-y-3">
          <FieldDiff
            fieldName="summary"
            oldValue={fromVersion.summary ?? ''}
            newValue={toVersion.summary ?? ''}
          />

          {(fromVersion.content != null || toVersion.content != null) && (
            <FieldDiff
              fieldName="content"
              oldValue={prettyJson(fromVersion.content ?? {})}
              newValue={prettyJson(toVersion.content ?? {})}
            />
          )}

          {(fromVersion.acceptanceCriteria != null || toVersion.acceptanceCriteria != null) && (
            <FieldDiff
              fieldName="acceptance criteria"
              oldValue={prettyJson(fromVersion.acceptanceCriteria ?? {})}
              newValue={prettyJson(toVersion.acceptanceCriteria ?? {})}
            />
          )}
        </Box>

        {/* Timestamps */}
        <Box className="flex justify-between text-xs text-text-secondary">
          <Typography>
            From: {new Date(fromVersion.createdAt).toLocaleString()}
          </Typography>
          <Typography>
            To: {new Date(toVersion.createdAt).toLocaleString()}
          </Typography>
        </Box>
      </CardContent>
    </Card>
  );
};

export default RequirementVersionDiff;
