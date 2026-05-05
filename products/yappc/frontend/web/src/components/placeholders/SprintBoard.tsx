/**
 * SprintBoard Component
 *
 * Kanban-style sprint board rendering stories grouped by column (status).
 * Reads sprint and story data from Jotai atoms, with optional filtering.
 *
 * @doc.type component
 * @doc.purpose Sprint board with columnar story cards
 * @doc.layer product
 * @doc.pattern UI Component
 */

import React from 'react';
import { useAtomValue, type Atom } from 'jotai';
import { Plus, Circle } from 'lucide-react';
import { activeSprintAtom, sprintStoriesAtom } from '../../state/atoms';

// =============================================================================
// Types
// =============================================================================

interface SprintBoardProps {
  sprintId?: string;
  projectId?: string;
  filters?: {
    assignees: string[];
    types: string[];
    priorities: string[];
    labels: string[];
    search: string;
  };
  onStoryClick?: (storyId: string) => void;
  onStoryMove?: (storyId: string, fromColumn: string, toColumn: string) => void;
  onCreateStory?: (columnId: string) => void;
  className?: string;
}

// =============================================================================
// Column Config
// =============================================================================

const COLUMNS: Array<{
  id: string;
  label: string;
  dotColor: string;
  headerColor: string;
}> = [
  {
    id: 'todo',
    label: 'To Do',
    dotColor: 'bg-surface-muted',
    headerColor: 'text-fg-muted',
  },
  {
    id: 'in-progress',
    label: 'In Progress',
    dotColor: 'bg-info-bg',
    headerColor: 'text-info-color',
  },
  {
    id: 'review',
    label: 'In Review',
    dotColor: 'bg-warning-bg',
    headerColor: 'text-warning-color',
  },
  {
    id: 'done',
    label: 'Done',
    dotColor: 'bg-emerald-400',
    headerColor: 'text-emerald-400',
  },
];

const PRIORITY_STYLES: Record<string, string> = {
  critical: 'bg-destructive-bg/20 text-destructive',
  high: 'bg-warning-bg/20 text-warning-color',
  medium: 'bg-warning-bg/20 text-warning-color',
  low: 'bg-info-bg/20 text-info-color',
};

// =============================================================================
// Story Card
// =============================================================================

interface StoryRecord {
  id: string;
  title?: string;
  status?: string;
  priority?: string;
  type?: string;
  assigneeId?: string;
  labels?: string[];
  storyPoints?: number;
  points?: number;
}

const StoryCard: React.FC<{
  story: StoryRecord;
  onClick?: () => void;
}> = ({ story, onClick }) => {
  const priorityStyle =
    PRIORITY_STYLES[story.priority ?? ''] ?? PRIORITY_STYLES.low;
  const pts = story.storyPoints ?? story.points ?? 0;

  return (
    <div
      onClick={onClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => e.key === 'Enter' && onClick?.()}
      className="group p-3 bg-surface rounded-lg border border-border hover:border-violet-500/70 cursor-pointer transition-all hover:shadow-lg hover:shadow-violet-500/5"
    >
      {/* Type + Priority badges */}
      <div className="flex items-center gap-1.5 mb-2 flex-wrap">
        {story.type && (
          <span className="px-1.5 py-0.5 rounded text-[10px] font-medium bg-surface-muted text-fg-muted uppercase tracking-wide">
            {story.type}
          </span>
        )}
        {story.priority && (
          <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium ${priorityStyle}`}>
            {story.priority}
          </span>
        )}
      </div>

      {/* Title */}
      <p className="text-sm font-medium text-white leading-snug line-clamp-2 mb-2">
        {story.title ?? 'Untitled Story'}
      </p>

      {/* Footer */}
      <div className="flex items-center justify-between text-[10px] text-fg-muted">
        <div className="flex items-center gap-1.5 flex-wrap">
          {story.labels?.map((label) => (
            <span
              key={label}
              className="px-1.5 py-0.5 rounded bg-surface-muted/60 text-fg-muted"
            >
              {label}
            </span>
          ))}
        </div>
        {pts > 0 && (
          <span className="font-semibold text-fg-muted">
            {pts} {pts === 1 ? 'pt' : 'pts'}
          </span>
        )}
      </div>
    </div>
  );
};

// =============================================================================
// SprintBoard Component
// =============================================================================

export const SprintBoard: React.FC<SprintBoardProps> = ({
  filters,
  onStoryClick,
  onCreateStory,
  className = '',
}) => {
  const activeSprint = useAtomValue(activeSprintAtom);
  const allStories = useAtomValue(sprintStoriesAtom as Atom<StoryRecord[]>);

  // Apply filters
  const filteredStories = allStories.filter((story) => {
    if (!filters) return true;

    if (
      filters.search &&
      !story.title?.toLowerCase().includes(filters.search.toLowerCase())
    ) {
      return false;
    }
    if (
      filters.assignees.length > 0 &&
      story.assigneeId &&
      !filters.assignees.includes(story.assigneeId)
    ) {
      return false;
    }
    if (
      filters.types.length > 0 &&
      story.type &&
      !filters.types.includes(story.type)
    ) {
      return false;
    }
    if (
      filters.priorities.length > 0 &&
      story.priority &&
      !filters.priorities.includes(story.priority)
    ) {
      return false;
    }
    if (filters.labels.length > 0) {
      const storyLabels = story.labels ?? [];
      if (!filters.labels.some((l) => storyLabels.includes(l))) return false;
    }

    return true;
  });

  return (
    <div className={`flex flex-col h-full bg-surface ${className}`}>
      {/* Sprint header */}
      {activeSprint && (
        <div className="px-6 py-3 border-b border-border flex-shrink-0">
          <div className="flex items-center gap-3">
            <Circle className="w-2.5 h-2.5 fill-emerald-400 text-emerald-400" />
            <span className="text-sm font-semibold text-white">
              {activeSprint.name}
            </span>
            <span className="text-xs text-fg-muted">
              {activeSprint.startDate} – {activeSprint.endDate}
            </span>
            {activeSprint.daysRemaining !== undefined && (
              <span className="ml-auto text-xs text-fg-muted">
                {activeSprint.daysRemaining}d remaining
              </span>
            )}
          </div>
        </div>
      )}

      {/* Columns */}
      <div className="flex-1 overflow-x-auto">
        <div className="flex gap-4 p-6 h-full min-w-max">
          {COLUMNS.map((col) => {
            const colStories = filteredStories.filter(
              (s) => (s.status ?? 'todo') === col.id
            );

            return (
              <div
                key={col.id}
                className="flex flex-col w-72 bg-surface/60 rounded-xl border border-border"
              >
                {/* Column header */}
                <div className="flex items-center justify-between px-4 py-3 border-b border-border flex-shrink-0">
                  <div className="flex items-center gap-2">
                    <span className={`w-2 h-2 rounded-full ${col.dotColor}`} />
                    <span className={`text-xs font-semibold uppercase tracking-wide ${col.headerColor}`}>
                      {col.label}
                    </span>
                    <span className="text-[10px] font-medium text-fg-muted bg-surface rounded-full px-1.5 py-0.5">
                      {colStories.length}
                    </span>
                  </div>
                  {onCreateStory && (
                    <button
                      onClick={() => onCreateStory(col.id)}
                      title={`Add story to ${col.label}`}
                      className="w-5 h-5 rounded flex items-center justify-center text-fg-muted hover:text-fg-muted hover:bg-surface transition-colors"
                    >
                      <Plus className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>

                {/* Story cards */}
                <div className="flex-1 overflow-y-auto p-3 space-y-2">
                  {colStories.length === 0 ? (
                    <div className="h-16 flex items-center justify-center text-xs text-fg-muted border-2 border-dashed border-border rounded-lg">
                      No stories
                    </div>
                  ) : (
                    colStories.map((story) => (
                      <StoryCard
                        key={story.id}
                        story={story}
                        onClick={() => onStoryClick?.(story.id)}
                      />
                    ))
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};
