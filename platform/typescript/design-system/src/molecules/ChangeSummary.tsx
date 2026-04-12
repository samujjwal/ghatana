/**
 * @fileoverview ChangeSummary - Structured diff summary of applied/suggested changes.
 *
 * @doc.type component
 * @doc.purpose Displays a summary of changes with region links and triggeredBy labels.
 * @doc.category molecule
 * @doc.tags ai, visibility, changes, diff
 */

import * as React from 'react';
import type { AIChangeDescriptor } from '@ghatana/platform-events';

export interface ChangeSummaryProps {
  /** List of changes to display */
  readonly changes: readonly AIChangeDescriptor[];
  /** Whether these are suggested or applied changes */
  readonly type: 'suggested' | 'applied' | 'pending';
  /** Optional title override */
  readonly title?: string;
  /** Callback when a change region is clicked */
  readonly onRegionClick?: (region: string) => void;
  /** Additional CSS classes */
  readonly className?: string;
  /** Whether to show the diff for each change */
  readonly showDiff?: boolean;
}

const kindConfig: Record<
  AIChangeDescriptor['kind'],
  { readonly icon: string; readonly label: string; readonly color: string }
> = {
  insert: { icon: '+', label: 'Added', color: 'text-green-600 bg-green-50 border-green-200' },
  update: { icon: '⟳', label: 'Modified', color: 'text-yellow-600 bg-yellow-50 border-yellow-200' },
  delete: { icon: '−', label: 'Removed', color: 'text-red-600 bg-red-50 border-red-200' },
  reorder: { icon: '⇅', label: 'Reordered', color: 'text-blue-600 bg-blue-50 border-blue-200' },
  suggest: { icon: '💡', label: 'Suggested', color: 'text-purple-600 bg-purple-50 border-purple-200' },
};

const defaultKindConfig = { icon: '?', label: 'Unknown', color: 'text-gray-600 bg-gray-50 border-gray-200' };

const typeConfig = {
  suggested: { title: 'Suggested Changes', border: 'border-purple-200' },
  applied: { title: 'Applied Changes', border: 'border-green-200' },
  pending: { title: 'Pending Review', border: 'border-yellow-200' },
};

/**
 * Individual change item component.
 */
export const ChangeItem: React.FC<{
  readonly change: AIChangeDescriptor;
  readonly onRegionClick?: (region: string) => void;
  readonly showDiff?: boolean;
}> = React.memo(({ change, onRegionClick, showDiff }) => {
  const config = kindConfig[change.kind] ?? defaultKindConfig;
  const clickable = Boolean(onRegionClick);

  return (
    <div className={`rounded-md border ${config.color} p-3`}>
      <div className="flex items-start gap-2">
        <span className="flex-shrink-0 font-mono text-sm font-bold w-6">{config.icon}</span>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-xs font-medium uppercase tracking-wide opacity-70">{config.label}</span>
            <button
              onClick={() => onRegionClick?.(change.region)}
              disabled={!clickable}
              className={`text-sm font-medium text-gray-900 ${clickable ? 'hover:underline cursor-pointer' : 'cursor-default'}`}
              type="button"
            >
              {change.region}
            </button>
          </div>
          <p className="text-sm text-gray-700 mt-1">{change.summary}</p>
          {showDiff && change.diff && (
            <pre className="mt-2 text-xs font-mono bg-white/50 rounded p-2 overflow-x-auto">
              {change.diff}
            </pre>
          )}
        </div>
      </div>
    </div>
  );
});

ChangeItem.displayName = 'ChangeItem';

/**
 * ChangeSummary component - displays a list of changes.
 */
export const ChangeSummary: React.FC<ChangeSummaryProps> = React.memo(({
  changes,
  type,
  title,
  onRegionClick,
  className = '',
  showDiff = false,
}) => {
  const config = typeConfig[type];
  const displayTitle = title ?? config.title;

  if (changes.length === 0) {
    return (
      <div className={`rounded-lg border ${config.border} bg-gray-50 p-4 ${className}`}>
        <h4 className="text-sm font-semibold text-gray-700">{displayTitle}</h4>
        <p className="text-sm text-gray-500 mt-1">No changes</p>
      </div>
    );
  }

  return (
    <div className={`rounded-lg border ${config.border} bg-white ${className}`}>
      <div className="px-4 py-3 border-b border-gray-100">
        <h4 className="text-sm font-semibold text-gray-900">
          {displayTitle}
          <span className="ml-2 text-xs font-normal text-gray-500">({changes.length})</span>
        </h4>
      </div>
      <div className="p-4 space-y-2">
        {changes.map((change, index) => (
          <ChangeItem
            key={`${change.region}-${index}`}
            change={change}
            onRegionClick={onRegionClick}
            showDiff={showDiff}
          />
        ))}
      </div>
    </div>
  );
});

ChangeSummary.displayName = 'ChangeSummary';
