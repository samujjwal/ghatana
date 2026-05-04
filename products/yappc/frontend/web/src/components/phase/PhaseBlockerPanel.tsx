/**
 * Phase Blocker Panel Component
 *
 * Displays current blockers preventing phase progression.
 * Blockers explain exactly what to do to move forward.
 *
 * @doc.type component
 * @doc.purpose Blocker display panel for phase cockpits
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React from 'react';

export interface Blocker {
  id: string;
  title: string;
  description: string;
  severity: 'critical' | 'high' | 'medium' | 'low';
  actionLabel?: string;
  onAction?: () => void;
}

export interface PhaseBlockerPanelProps {
  /** List of blockers */
  blockers: Blocker[];
  /** Custom className */
  className?: string;
}

/**
 * Severity color mapping
 */
const SEVERITY_COLORS: Record<Blocker['severity'], string> = {
  critical: 'bg-red-50 border-red-200 text-red-700 dark:bg-red-900/20 dark:border-red-800 dark:text-red-300',
  high: 'bg-orange-50 border-orange-200 text-orange-700 dark:bg-orange-900/20 dark:border-orange-800 dark:text-orange-300',
  medium: 'bg-yellow-50 border-yellow-200 text-yellow-700 dark:bg-yellow-900/20 dark:border-yellow-800 dark:text-yellow-300',
  low: 'bg-blue-50 border-blue-200 text-blue-700 dark:bg-blue-900/20 dark:border-blue-800 dark:text-blue-300',
};

/**
 * Phase Blocker Panel Component
 *
 * Displays blockers with:
 * - Severity-based color coding
 * - Clear title and description
 * - Actionable CTA when available
 * - Empty state when no blockers
 */
export const PhaseBlockerPanel: React.FC<PhaseBlockerPanelProps> = ({
  blockers,
  className = '',
}) => {
  if (blockers.length === 0) {
    return (
      <div className={`phase-blocker-panel ${className}`}>
        <div className="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-lg p-4 text-center">
          <p className="text-green-700 dark:text-green-300 text-sm font-medium">
            ✓ No blockers - ready to proceed
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className={`phase-blocker-panel ${className}`}>
      <h3 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">
        Blockers ({blockers.length})
      </h3>
      <div className="space-y-3">
        {blockers.map((blocker) => (
          <div
            key={blocker.id}
            className={`border rounded-lg p-4 ${SEVERITY_COLORS[blocker.severity]}`}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex-1">
                <h4 className="font-medium mb-1">{blocker.title}</h4>
                <p className="text-sm opacity-90">{blocker.description}</p>
              </div>
              {blocker.actionLabel && blocker.onAction && (
                <button
                  onClick={blocker.onAction}
                  className="flex-shrink-0 px-3 py-1.5 text-sm font-medium bg-white dark:bg-gray-800 rounded border border-current opacity-90 hover:opacity-100 transition-opacity"
                >
                  {blocker.actionLabel}
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default PhaseBlockerPanel;
