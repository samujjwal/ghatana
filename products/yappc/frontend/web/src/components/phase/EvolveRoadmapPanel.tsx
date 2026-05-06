/**
 * Evolve Roadmap Panel
 *
 * Phase-native panel for the Evolve phase. Displays the project roadmap,
 * backlog, and next-cycle plan derived from lessons and retrospective findings.
 *
 * @doc.type component
 * @doc.purpose Roadmap and next-cycle planning surface for the Evolve phase
 * @doc.layer product
 * @doc.pattern Phase Panel
 */

import React, { useCallback, useState } from 'react';
import { Button, Card, CardContent } from '@ghatana/design-system';

export type RoadmapPhase =
  | 'intent'
  | 'shape'
  | 'validate'
  | 'generate'
  | 'run'
  | 'observe'
  | 'learn'
  | 'evolve';

export type RoadmapItemStatus = 'planned' | 'in-progress' | 'done' | 'deferred' | 'cancelled';

export type RoadmapItemPriority = 'critical' | 'high' | 'medium' | 'low';

export interface RoadmapItem {
  readonly id: string;
  readonly title: string;
  readonly description?: string;
  readonly targetPhase: RoadmapPhase;
  readonly priority: RoadmapItemPriority;
  readonly status: RoadmapItemStatus;
  readonly sourceLessonId?: string;
  readonly targetCycle?: number;
}

export type BacklogCategory =
  | 'feature'
  | 'bug'
  | 'tech-debt'
  | 'performance'
  | 'accessibility'
  | 'security'
  | 'other';

export interface BacklogItem {
  readonly id: string;
  readonly title: string;
  readonly description?: string;
  readonly category: BacklogCategory;
  readonly priority: RoadmapItemPriority;
  readonly votes: number;
  readonly fromLesson: boolean;
}

export interface EvolveRoadmapPanelProps {
  /** Roadmap items scoped to the current or next cycles */
  readonly roadmapItems: readonly RoadmapItem[];
  /** Backlog items awaiting prioritization */
  readonly backlogItems: readonly BacklogItem[];
  /** Current cycle number */
  readonly currentCycle: number;
  /** Whether the evolve phase handoff is approved (cycle completes) */
  readonly handoffApproved: boolean;
  /** Called to approve handoff and start the next cycle */
  readonly onApproveHandoff: () => void;
  /** Called to move a backlog item onto the roadmap */
  readonly onPromoteBacklogItem: (itemId: string, targetPhase: RoadmapPhase, priority: RoadmapItemPriority) => void;
  /** Called to vote on a backlog item */
  readonly onVoteBacklogItem: (itemId: string) => void;
  /** Whether the current user can approve handoff */
  readonly canApproveHandoff?: boolean;
  /** Reason handoff cannot be approved */
  readonly cannotApproveReason?: string;
  /** Custom className */
  readonly className?: string;
}

const PHASE_LABELS: Record<RoadmapPhase, string> = {
  intent: 'Intent',
  shape: 'Shape',
  validate: 'Validate',
  generate: 'Generate',
  run: 'Run',
  observe: 'Observe',
  learn: 'Learn',
  evolve: 'Evolve',
};

const STATUS_STYLE: Record<RoadmapItemStatus, { label: string; className: string }> = {
  planned: { label: 'Planned', className: 'bg-surface-muted text-fg-muted border-border' },
  'in-progress': { label: 'In progress', className: 'bg-info-bg text-info-color border-info-border' },
  done: { label: 'Done', className: 'bg-success-bg text-success-color border-success-border' },
  deferred: { label: 'Deferred', className: 'bg-surface-muted text-fg-muted border-border' },
  cancelled: { label: 'Cancelled', className: 'bg-destructive-bg text-fg-muted border-destructive-border' },
};

const PRIORITY_STYLE: Record<RoadmapItemPriority, string> = {
  critical: 'text-destructive font-semibold',
  high: 'text-warning-color font-medium',
  medium: 'text-fg',
  low: 'text-fg-muted',
};

const CATEGORY_LABELS: Record<BacklogCategory, string> = {
  feature: 'Feature',
  bug: 'Bug',
  'tech-debt': 'Tech debt',
  performance: 'Performance',
  accessibility: 'Accessibility',
  security: 'Security',
  other: 'Other',
};

/**
 * Evolve Roadmap Panel
 *
 * Provides next-cycle planning with:
 * - Roadmap items grouped by target phase and priority
 * - Backlog with votes and category tagging
 * - Backlog-to-roadmap promotion
 * - Cycle handoff gate (approves transition to next cycle)
 */
export const EvolveRoadmapPanel: React.FC<EvolveRoadmapPanelProps> = ({
  roadmapItems,
  backlogItems,
  currentCycle,
  handoffApproved,
  onApproveHandoff,
  onPromoteBacklogItem,
  onVoteBacklogItem,
  canApproveHandoff = true,
  cannotApproveReason,
  className = '',
}) => {
  const [promotingId, setPromotingId] = useState<string | null>(null);
  const [promotePhase, setPromotePhase] = useState<RoadmapPhase>('intent');
  const [promotePriority, setPromotePriority] = useState<RoadmapItemPriority>('medium');

  const activeRoadmapItems = roadmapItems.filter(
    (i) => i.status !== 'done' && i.status !== 'cancelled',
  );
  const doneRoadmapItems = roadmapItems.filter((i) => i.status === 'done');
  const lessonBacklogItems = backlogItems.filter((i) => i.fromLesson);

  const handleStartPromote = useCallback((itemId: string) => {
    setPromotingId(itemId);
    setPromotePhase('intent');
    setPromotePriority('medium');
  }, []);

  const handleCancelPromote = useCallback(() => {
    setPromotingId(null);
  }, []);

  const handleConfirmPromote = useCallback(() => {
    if (promotingId != null) {
      onPromoteBacklogItem(promotingId, promotePhase, promotePriority);
      setPromotingId(null);
    }
  }, [promotingId, promotePhase, promotePriority, onPromoteBacklogItem]);

  return (
    <section
      className={`evolve-roadmap-panel space-y-6 ${className}`}
      aria-label="Evolve roadmap"
      data-testid="evolve-roadmap-panel"
    >
      {/* Cycle Header */}
      <Card variant="outlined">
        <CardContent className="p-5">
          <div className="flex items-center justify-between gap-4 flex-wrap">
            <div>
              <h3 className="text-base font-semibold text-fg">Cycle {currentCycle} — Evolve</h3>
              <p className="text-sm text-fg-muted mt-0.5">
                {activeRoadmapItems.length} item{activeRoadmapItems.length !== 1 ? 's' : ''} planned for next cycle ·{' '}
                {doneRoadmapItems.length} completed
              </p>
            </div>
            <span
              className={`inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium ${
                handoffApproved
                  ? 'bg-success-bg border-success-border text-success-color'
                  : 'bg-surface-muted border-border text-fg-muted'
              }`}
            >
              {handoffApproved ? 'Handoff approved' : 'Awaiting handoff'}
            </span>
          </div>
        </CardContent>
      </Card>

      {/* Lessons-derived items callout */}
      {lessonBacklogItems.length > 0 && (
        <div className="rounded-lg border border-info-border bg-info-bg p-3">
          <p className="text-sm text-info-color">
            {lessonBacklogItems.length} backlog item{lessonBacklogItems.length !== 1 ? 's' : ''}{' '}
            derived from retrospective lessons — review and promote to roadmap.
          </p>
        </div>
      )}

      {/* Roadmap */}
      <section aria-label="Roadmap items">
        <h4 className="text-sm font-medium text-fg mb-3">
          Roadmap ({activeRoadmapItems.length} active)
        </h4>
        {activeRoadmapItems.length === 0 ? (
          <div className="rounded-lg border border-border bg-surface-muted p-6 text-center">
            <p className="text-sm text-fg-muted">No roadmap items yet.</p>
            <p className="text-xs text-fg-muted mt-1">Promote backlog items to add them to the next cycle.</p>
          </div>
        ) : (
          <div className="space-y-2">
            {activeRoadmapItems.map((item) => {
              const statusStyle = STATUS_STYLE[item.status];
              const priorityClass = PRIORITY_STYLE[item.priority];
              return (
                <Card key={item.id} variant="outlined">
                  <CardContent className="p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <p className={`text-sm font-medium ${priorityClass}`}>{item.title}</p>
                          <span className="text-xs text-fg-muted">
                            → {PHASE_LABELS[item.targetPhase]}
                          </span>
                          {item.targetCycle != null && item.targetCycle !== currentCycle && (
                            <span className="text-xs text-fg-muted">
                              Cycle {item.targetCycle}
                            </span>
                          )}
                        </div>
                        {item.description && (
                          <p className="text-xs text-fg-muted mt-0.5">{item.description}</p>
                        )}
                        {item.sourceLessonId && (
                          <p className="text-xs text-info-color mt-0.5">From lesson</p>
                        )}
                      </div>
                      <span
                        className={`flex-shrink-0 text-xs rounded-full border px-2 py-0.5 ${statusStyle.className}`}
                      >
                        {statusStyle.label}
                      </span>
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        )}
      </section>

      {/* Backlog */}
      {backlogItems.length > 0 && (
        <section aria-label="Backlog">
          <h4 className="text-sm font-medium text-fg mb-3">
            Backlog ({backlogItems.length})
          </h4>
          <div className="space-y-2">
            {[...backlogItems]
              .sort((a, b) => b.votes - a.votes)
              .map((item) => (
                <Card key={item.id} variant="outlined">
                  <CardContent className="p-4">
                    <div className="flex items-start gap-3">
                      <button
                        type="button"
                        className="flex flex-col items-center px-2 py-1 rounded border border-border bg-surface-muted hover:bg-surface focus:outline-none focus-visible:ring-2 focus-visible:ring-ring flex-shrink-0"
                        onClick={() => onVoteBacklogItem(item.id)}
                        aria-label={`Vote for "${item.title}" (${item.votes} votes)`}
                      >
                        <span className="text-xs text-fg-muted" aria-hidden="true">▲</span>
                        <span className="text-xs font-medium text-fg">{item.votes}</span>
                      </button>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <p className={`text-sm font-medium ${PRIORITY_STYLE[item.priority]}`}>
                            {item.title}
                          </p>
                          <span className="text-xs text-fg-muted">
                            {CATEGORY_LABELS[item.category]}
                          </span>
                          {item.fromLesson && (
                            <span className="text-xs bg-info-bg border border-info-border text-info-color rounded px-1.5 py-0.5">
                              From lesson
                            </span>
                          )}
                        </div>
                        {item.description && (
                          <p className="text-xs text-fg-muted mt-0.5">{item.description}</p>
                        )}
                      </div>
                      {promotingId === item.id ? (
                        <div className="flex flex-col gap-2 flex-shrink-0 min-w-[160px]">
                          <div>
                            <label htmlFor={`promote-phase-${item.id}`} className="text-xs text-fg-muted">
                              Target phase
                            </label>
                            <select
                              id={`promote-phase-${item.id}`}
                              value={promotePhase}
                              onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
                                setPromotePhase(e.target.value as RoadmapPhase)
                              }
                              className="w-full rounded-md border border-border bg-surface text-fg text-xs px-2 py-1 focus:outline-none focus:ring-2 focus:ring-ring"
                            >
                              {(Object.keys(PHASE_LABELS) as RoadmapPhase[]).map((p) => (
                                <option key={p} value={p}>
                                  {PHASE_LABELS[p]}
                                </option>
                              ))}
                            </select>
                          </div>
                          <div>
                            <label htmlFor={`promote-priority-${item.id}`} className="text-xs text-fg-muted">
                              Priority
                            </label>
                            <select
                              id={`promote-priority-${item.id}`}
                              value={promotePriority}
                              onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
                                setPromotePriority(e.target.value as RoadmapItemPriority)
                              }
                              className="w-full rounded-md border border-border bg-surface text-fg text-xs px-2 py-1 focus:outline-none focus:ring-2 focus:ring-ring"
                            >
                              {(Object.keys(PRIORITY_STYLE) as RoadmapItemPriority[]).map((p) => (
                                <option key={p} value={p}>
                                  {p.charAt(0).toUpperCase() + p.slice(1)}
                                </option>
                              ))}
                            </select>
                          </div>
                          <div className="flex gap-1">
                            <Button variant="solid" size="sm" onClick={handleConfirmPromote}>
                              Add
                            </Button>
                            <Button variant="outline" size="sm" onClick={handleCancelPromote}>
                              Cancel
                            </Button>
                          </div>
                        </div>
                      ) : (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleStartPromote(item.id)}
                          aria-label={`Add "${item.title}" to roadmap`}
                          className="flex-shrink-0"
                        >
                          Add to roadmap
                        </Button>
                      )}
                    </div>
                  </CardContent>
                </Card>
              ))}
          </div>
        </section>
      )}

      {/* Handoff Gate */}
      {!handoffApproved && (
        <section aria-label="Cycle handoff">
          {!canApproveHandoff && cannotApproveReason && (
            <div className="mb-3 rounded-lg bg-warning-bg border border-warning-border p-3">
              <p className="text-sm text-warning-color">{cannotApproveReason}</p>
            </div>
          )}
          <Button
            variant="solid"
            onClick={onApproveHandoff}
            disabled={!canApproveHandoff}
            aria-label={`Approve handoff and begin cycle ${currentCycle + 1}`}
            className="w-full sm:w-auto"
          >
            Approve handoff → Cycle {currentCycle + 1}
          </Button>
        </section>
      )}
    </section>
  );
};

export default EvolveRoadmapPanel;
