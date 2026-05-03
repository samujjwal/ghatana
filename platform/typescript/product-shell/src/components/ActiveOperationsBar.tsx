/**
 * ActiveOperationsBar — in-progress operations indicator.
 *
 * Renders a slim bottom bar when there are active (in-flight) operations.
 * Clicking the bar triggers `onActiveOperationsClick` to open an operations panel.
 *
 * @doc.type component
 * @doc.purpose Active operations indicator bar for product shell
 * @doc.layer platform
 * @doc.pattern Molecule
 */
import React from 'react';
import { Activity } from 'lucide-react';

interface ActiveOperationsBarProps {
  /** Number of active (in-progress) operations. Bar is not rendered when 0. */
  count: number;
  /** Called when the bar is clicked. Should open an operations panel or page. */
  onClick?: () => void;
}

/**
 * Slim bar shown at the bottom of the shell when operations are in progress.
 * Only rendered when `count > 0`.
 */
export function ActiveOperationsBar({ count, onClick }: ActiveOperationsBarProps): React.ReactElement | null {
  if (count <= 0) {
    return null;
  }

  return (
    <div
      role="status"
      aria-live="polite"
      aria-label={`${count} operation${count === 1 ? '' : 's'} in progress`}
      className="fixed bottom-0 left-0 right-0 z-40"
    >
      <button
        type="button"
        onClick={onClick}
        className="flex w-full items-center justify-center gap-2 bg-indigo-600 px-4 py-2 text-xs font-medium text-white transition-colors hover:bg-indigo-700 dark:bg-indigo-700 dark:hover:bg-indigo-600"
      >
        <Activity className="h-3.5 w-3.5 animate-pulse" aria-hidden="true" />
        <span>
          {count} operation{count === 1 ? '' : 's'} in progress — click to view
        </span>
      </button>
    </div>
  );
}
