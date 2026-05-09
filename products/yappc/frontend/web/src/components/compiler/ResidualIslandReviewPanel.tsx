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
import { useI18n } from '../../i18n/I18nProvider';

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
  /** Optional provenance source label */
  provenanceSource?: string;
  /** Optional round-trip fidelity score from 0-1 */
  roundTripFidelity?: number;
  /** Whether this residual blocks generation/release */
  blocking?: boolean;
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
      return 'bg-destructive-bg text-destructive border-destructive-border dark:bg-destructive-bg/30 dark:text-destructive dark:border-destructive-border';
    case 'high':
      return 'bg-warning-bg text-warning-color border-warning-border dark:bg-warning-bg/30 dark:text-warning-color dark:border-warning-border';
    case 'medium':
      return 'bg-warning-bg text-warning-color border-warning-border dark:bg-warning-bg/30 dark:text-warning-color dark:border-warning-border';
    case 'low':
      return 'bg-info-bg text-info-color border-info-border dark:bg-info-bg/30 dark:text-info-color dark:border-info-border';
    case 'info':
      return 'bg-surface-muted text-fg border-border dark:bg-surface dark:text-fg-muted dark:border-border';
    default:
      return 'bg-surface-muted text-fg border-border';
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
  const { t } = useI18n();
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

  const blockingCount = islands.filter((island) => island.blocking || island.requiredAction === 'block').length;

  return (
    <Card className={className} variant="outlined">
      <CardContent className="p-0">
      <div className="p-4 border-b border-border dark:border-border">
        <h3 className="text-lg font-semibold text-fg dark:text-fg-muted">
          Residual Islands Review
        </h3>
        <p className="mt-1 text-sm text-fg-muted dark:text-fg-muted">
          {blockingCount > 0
            ? `${blockingCount} blocking residual ${blockingCount === 1 ? 'requires' : 'require'} resolution before generate or release.`
            : 'No blocking residuals. Review advisory residuals for quality and governance confidence.'}
        </p>
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

      <div className="divide-y divide-border dark:divide-border max-h-[600px] overflow-y-auto">
        {filteredIslands.length === 0 ? (
          <div className="p-8 text-center text-fg-muted dark:text-fg-muted">
            No residual islands to review
          </div>
        ) : (
          filteredIslands.map(island => (
            <div
              key={island.id}
              className={`p-4 hover:bg-surface-muted dark:hover:bg-surface transition-colors ${
                selectedIsland === island.id ? 'bg-info-bg dark:bg-info-bg/20' : ''
              }`}
            >
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-2">
                    <span className={`px-2 py-1 rounded text-xs font-medium ${getSeverityColor(island.severity)}`}>
                      {island.severity}
                    </span>
                    <span
                      className={`px-2 py-1 rounded text-xs font-medium ${
                        island.blocking || island.requiredAction === 'block'
                          ? 'bg-destructive-bg text-destructive border border-destructive-border'
                          : 'bg-info-bg text-info-color border border-info-border'
                      }`}
                    >
                      {island.blocking || island.requiredAction === 'block' ? 'blocking' : 'advisory'}
                    </span>
                    <span className="text-sm font-medium text-fg dark:text-fg-muted">
                      {island.type}
                    </span>
                    <span className="text-fg-muted">•</span>
                    <span className="text-sm text-fg-muted dark:text-fg-muted">
                      {getActionIcon(island.requiredAction)} {island.requiredAction}
                    </span>
                  </div>
                  <p className="text-sm text-fg dark:text-fg-muted mb-2">
                    {island.description}
                  </p>
                  <div className="mb-2 flex flex-wrap items-center gap-2 text-xs">
                    {typeof island.roundTripFidelity === 'number' ? (
                      <span className="rounded border border-info-border bg-info-bg px-2 py-1 text-info-color">
                        Fidelity: {Math.round(island.roundTripFidelity * 100)}%
                      </span>
                    ) : null}
                    {island.provenanceSource ? (
                      <span className="rounded border border-border bg-surface-muted px-2 py-1 text-fg-muted">
                        Provenance: {island.provenanceSource}
                      </span>
                    ) : null}
                  </div>
                  {island.filePath && (
                    <p className="text-xs text-fg-muted dark:text-fg-muted">
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
                  <pre className="bg-surface-muted dark:bg-surface p-3 rounded text-xs overflow-x-auto">
                    <code>{island.code}</code>
                  </pre>
                </div>
              )}

              {selectedIsland === island.id && (
                <div className="mt-3 border-t border-border dark:border-border pt-3">
                  <Alert severity="info" title="Review notes" className="mb-3">
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
                    placeholder={t('residualIsland.reviewNotesPlaceholder')}
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
