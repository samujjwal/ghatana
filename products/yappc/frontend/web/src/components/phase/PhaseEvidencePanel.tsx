/**
 * Phase Evidence Panel Component
 *
 * Displays evidence supporting the recommended next action.
 * Evidence helps users understand why the system is suggesting a particular action.
 *
 * @doc.type component
 * @doc.purpose Evidence display panel for phase cockpits
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React from 'react';

export interface EvidenceItem {
  id: string;
  type: 'metric' | 'log' | 'artifact' | 'observation' | 'recommendation';
  title: string;
  description: string;
  value?: string | number;
  source?: string;
  timestamp?: string;
}

export interface PhaseEvidencePanelProps {
  /** List of evidence items */
  evidence: EvidenceItem[];
  /** Custom className */
  className?: string;
}

/**
 * Evidence type icon mapping
 */
const EVIDENCE_ICONS: Record<EvidenceItem['type'], string> = {
  metric: '📊',
  log: '📋',
  artifact: '📄',
  observation: '👁️',
  recommendation: '💡',
};

/**
 * Phase Evidence Panel Component
 *
 * Displays evidence with:
 * - Type-based icons
 * - Title, description, and optional value
 * - Source and timestamp for traceability
 * - Empty state when no evidence
 */
export const PhaseEvidencePanel: React.FC<PhaseEvidencePanelProps> = ({
  evidence,
  className = '',
}) => {
  if (evidence.length === 0) {
    return (
      <div className={`phase-evidence-panel ${className}`}>
        <div className="bg-gray-50 dark:bg-gray-900/50 border border-gray-200 dark:border-gray-700 rounded-lg p-4 text-center">
          <p className="text-gray-500 dark:text-gray-400 text-sm">
            No evidence available
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className={`phase-evidence-panel ${className}`}>
      <h3 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">
        Evidence ({evidence.length})
      </h3>
      <div className="space-y-3">
        {evidence.map((item) => (
          <div
            key={item.id}
            className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-4"
          >
            <div className="flex items-start gap-3">
              <span className="text-xl" aria-hidden="true">
                {EVIDENCE_ICONS[item.type]}
              </span>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between gap-2 mb-1">
                  <h4 className="font-medium text-gray-900 dark:text-gray-100">
                    {item.title}
                  </h4>
                  {item.value !== undefined && (
                    <span className="text-sm font-mono text-gray-600 dark:text-gray-400 bg-gray-100 dark:bg-gray-700 px-2 py-0.5 rounded">
                      {item.value}
                    </span>
                  )}
                </div>
                <p className="text-sm text-gray-600 dark:text-gray-400 mb-2">
                  {item.description}
                </p>
                {(item.source || item.timestamp) && (
                  <div className="flex items-center gap-3 text-xs text-gray-500 dark:text-gray-500">
                    {item.source && (
                      <span>Source: {item.source}</span>
                    )}
                    {item.timestamp && (
                      <span>{new Date(item.timestamp).toLocaleString()}</span>
                    )}
                  </div>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default PhaseEvidencePanel;
