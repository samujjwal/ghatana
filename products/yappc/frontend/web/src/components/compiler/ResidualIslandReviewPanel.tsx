/**
 * Residual Island Review Panel Component
 *
 * Displays residual islands from decompilation with severity levels
 * and required actions for each island.
 *
 * @doc.type component
 * @doc.purpose Residual island review UI
 * @doc.layer product
 * @doc.pattern Review Panel
 */

import React from 'react';

import { Alert, Button, Card, CardContent, TextArea } from '@ghatana/design-system';

export type ResidualIslandSeverity = 'critical' | 'high' | 'medium' | 'low' | 'info';

export type RequiredAction = 'block' | 'review' | 'accept' | 'ignore';

export interface ResidualIsland {
  /** Island ID */
  id: string;
  /** Island type */
  type: string;
  /** Description of the island */
  description: string;
  /** Code snippet */
  code: string;
  /** Severity level */
  severity: ResidualIslandSeverity;
  /** Required action */
  requiredAction: RequiredAction;
  /** Line number */
  lineNumber?: number;
  /** File path */
  filePath?: string;
  /** Review notes */
  reviewNotes?: string;
}

export interface ResidualIslandReviewPanelProps {
  /** Residual islands to review */
  islands: ResidualIsland[];
  /** On island accept */
  onAccept: (islandId: string) => void;
  /** On island reject */
  onReject: (islandId: string) => void;
  /** On island review */
  onReview: (islandId: string, notes: string) => void;
  /** Filter by severity */
  filterSeverity?: ResidualIslandSeverity | 'all';
  /** Filter by action */
  filterAction?: RequiredAction | 'all';
  /** Show code snippets */
  showCode?: boolean;
  /** Compact mode */
  compact?: boolean;
  /** Additional class name */
  className?: string;
}

/**
 * Get severity color class
 */
function getSeverityColor(severity: ResidualIslandSeverity): string {
  switch (severity) {
    case 'critical':
      return 'bg-red-100 text-red-800 border-red-300 dark:bg-red-900/30 dark:text-red-300 dark:border-red-700';
    case 'high':
      return 'bg-orange-100 text-orange-800 border-orange-300 dark:bg-orange-900/30 dark:text-orange-300 dark:border-orange-700';
    case 'medium':
      return 'bg-yellow-100 text-yellow-800 border-yellow-300 dark:bg-yellow-900/30 dark:text-yellow-300 dark:border-yellow-700';
    case 'low':
      return 'bg-blue-100 text-blue-800 border-blue-300 dark:bg-blue-900/30 dark:text-blue-300 dark:border-blue-700';
    case 'info':
      return 'bg-gray-100 text-gray-800 border-gray-300 dark:bg-gray-800 dark:text-gray-300 dark:border-gray-600';
    default:
      return 'bg-gray-100 text-gray-800 border-gray-300';
  }
}

/**
 * Get action icon
 */
function getActionIcon(action: RequiredAction): string {
  switch (action) {
    case 'block':
      return '🚫';
    case 'review':
      return '👁️';
    case 'accept':
      return '✅';
    case 'ignore':
      return '⏭️';
    default:
      return '❓';
  }
}

/**
 * Residual Island Review Panel Component
 */
export const ResidualIslandReviewPanel: React.FC<ResidualIslandReviewPanelProps> = ({
  islands,
  onAccept,
  onReject,
  onReview,
  filterSeverity = 'all',
  filterAction = 'all',
  showCode = true,
  compact = false,
  className = '',
}) => {
  const [selectedIsland, setSelectedIsland] = React.useState<string | null>(null);
  const [reviewNotes, setReviewNotes] = React.useState<Map<string, string>>(new Map());

  const filteredIslands = islands.filter(island => {
    if (filterSeverity !== 'all' && island.severity !== filterSeverity) return false;
    if (filterAction !== 'all' && island.requiredAction !== filterAction) return false;
    return true;
  });

  const handleReviewSubmit = (islandId: string): void => {
    const notes = reviewNotes.get(islandId) || '';
    onReview(islandId, notes);
  };

  const severityCount = islands.reduce<Map<ResidualIslandSeverity, number>>((acc, island) => {
    const currentCount = acc.get(island.severity) ?? 0;
    acc.set(island.severity, currentCount + 1);
    return acc;
  }, new Map());

  return (
    <Card className={className} variant="outlined">
      <CardContent className="p-0">
      <div className="p-4 border-b border-gray-200 dark:border-gray-700">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
          Residual Islands Review
        </h3>
        <div className="mt-2 flex flex-wrap gap-2">
          {Array.from(severityCount.entries()).map(([severity, count]) => (
            <span
              key={severity}
              className={`px-2 py-1 rounded text-xs font-medium ${getSeverityColor(severity)}`}
            >
              {severity}: {count}
            </span>
          ))}
        </div>
      </div>

      <div className="divide-y divide-gray-200 dark:divide-gray-700 max-h-[600px] overflow-y-auto">
        {filteredIslands.length === 0 ? (
          <div className="p-8 text-center text-gray-500 dark:text-gray-400">
            No residual islands to review
          </div>
        ) : (
          filteredIslands.map(island => (
            <div
              key={island.id}
              className={`p-4 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors ${
                selectedIsland === island.id ? 'bg-blue-50 dark:bg-blue-900/20' : ''
              }`}
            >
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-2">
                    <span className={`px-2 py-1 rounded text-xs font-medium ${getSeverityColor(island.severity)}`}>
                      {island.severity}
                    </span>
                    <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
                      {island.type}
                    </span>
                    <span className="text-gray-500">•</span>
                    <span className="text-sm text-gray-600 dark:text-gray-400">
                      {getActionIcon(island.requiredAction)} {island.requiredAction}
                    </span>
                  </div>
                  <p className="text-sm text-gray-700 dark:text-gray-300 mb-2">
                    {island.description}
                  </p>
                  {island.filePath && (
                    <p className="text-xs text-gray-500 dark:text-gray-400">
                      {island.filePath}:{island.lineNumber}
                    </p>
                  )}
                </div>
                <div className="flex gap-2 ml-4">
                  <Button
                    onClick={() => onAccept(island.id)}
                    variant="solid"
                    size="sm"
                    title="Accept island"
                  >
                    Accept
                  </Button>
                  <Button
                    onClick={() => onReject(island.id)}
                    variant="outline"
                    size="sm"
                    title="Reject island"
                  >
                    Reject
                  </Button>
                  <Button
                    onClick={() => setSelectedIsland(island.id)}
                    variant="ghost"
                    size="sm"
                    title="Review island"
                  >
                    Review
                  </Button>
                </div>
              </div>

              {showCode && !compact && (
                <div className="mt-3">
                  <pre className="bg-gray-100 dark:bg-gray-800 p-3 rounded text-xs overflow-x-auto">
                    <code>{island.code}</code>
                  </pre>
                </div>
              )}

              {selectedIsland === island.id && (
                <div className="mt-3 border-t border-gray-200 dark:border-gray-700 pt-3">
                  <Alert severity="info" variant="outlined" title="Review notes" className="mb-3">
                    Record why this residual should be accepted, rejected, or sent for follow-up review.
                  </Alert>
                  <TextArea
                    value={reviewNotes.get(island.id) || island.reviewNotes || ''}
                    onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => {
                      const nextValue = e.target.value;
                      setReviewNotes((currentNotes) => {
                        const nextNotes = new Map(currentNotes);
                        nextNotes.set(island.id, nextValue);
                        return nextNotes;
                      });
                    }}
                    placeholder="Add review notes..."
                    rows={3}
                  />
                  <div className="mt-2 flex justify-end">
                    <Button
                      onClick={() => handleReviewSubmit(island.id)}
                      variant="solid"
                      size="sm"
                    >
                      Save Notes
                    </Button>
                  </div>
                </div>
              )}
            </div>
          ))
        )}
      </div>
      </CardContent>
    </Card>
  );
};

export default ResidualIslandReviewPanel;
