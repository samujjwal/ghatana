// @ts-nocheck
/**
 * Sprint Board Page
 *
 * @description Kanban-style sprint board for managing stories and tasks.
 * Uses SprintBoard component from the UI library.
 */

import React, { useState, useCallback } from 'react';
import { useParams } from 'react-router';
import { useAtomValue } from 'jotai';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Filter,
  SlidersHorizontal,
  Plus,
  Calendar,
  BarChart3,
  RefreshCw,
  ChevronDown,
  X,
  Search,
} from 'lucide-react';

import { cn } from '../../utils/cn';
import { SprintBoard } from '../../components/placeholders';
import {
  activeSprintAtom,
  sprintStoriesAtom,
} from '../../state/atoms';
import { useTranslation } from '@ghatana/i18n';

// =============================================================================
// Types
// =============================================================================

interface FilterState {
  assignee: string[];
  type: string[];
  priority: string[];
  labels: string[];
  search: string;
}

const NativeButton = React.forwardRef<HTMLButtonElement, React.ButtonHTMLAttributes<HTMLButtonElement>>((props, ref) =>
  React.createElement('button', { ...props, ref }),
);
NativeButton.displayName = 'NativeButton';

const NativeInput = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>((props, ref) =>
  React.createElement('input', { ...props, ref }),
);
NativeInput.displayName = 'NativeInput';

// =============================================================================
// Filter Panel Component
// =============================================================================

const FilterPanel: React.FC<{
  filters: FilterState;
  onFiltersChange: (filters: FilterState) => void;
  onClose: () => void;
}> = ({ filters, onFiltersChange, onClose }) => {
  const { t } = useTranslation('common');
  const stories = useAtomValue(sprintStoriesAtom);

  // Extract unique values from stories
  const assignees = [...new Set(stories.map((s) => s.assigneeId).filter(Boolean))];
  const types = [...new Set(stories.map((s) => s.type))];
  const priorities = ['critical', 'high', 'medium', 'low'];
  const labels = [...new Set(stories.flatMap((s) => s.labels || []))];

  const toggleFilter = (key: keyof FilterState, value: string) => {
    if (key === 'search') return;
    
    const current = filters[key] as string[];
    const updated = current.includes(value)
      ? current.filter((v) => v !== value)
      : [...current, value];
    
    onFiltersChange({ ...filters, [key]: updated });
  };

  const clearFilters = () => {
    onFiltersChange({
      assignee: [],
      type: [],
      priority: [],
      labels: [],
      search: '',
    });
  };

  const hasActiveFilters = Object.values(filters).some(
    (v) => (Array.isArray(v) ? v.length > 0 : v !== '')
  );

  return (
    <motion.div
      initial={{ opacity: 0, y: -10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -10 }}
      className="absolute right-0 top-full mt-2 w-80 bg-surface border border-border rounded-xl shadow-xl z-50"
    >
      <div className="flex items-center justify-between p-4 border-b border-border">
        <span className="font-medium text-white">Filters</span>
        <div className="flex items-center gap-2">
          {hasActiveFilters && (
            <NativeButton
              onClick={clearFilters}
              className="text-xs text-violet-400 hover:text-violet-300"
            >
              Clear all
            </NativeButton>
          )}
          <NativeButton onClick={onClose} className="p-1 hover:bg-surface rounded" aria-label={t('sprintBoard.closeFilters')}>
            <X className="w-4 h-4 text-fg-muted" />
          </NativeButton>
        </div>
      </div>

      <div className="p-4 space-y-4 max-h-96 overflow-y-auto">
        {/* Assignee */}
        <div>
          <label className="text-xs font-medium text-fg-muted uppercase tracking-wider">
            Assignee
          </label>
          <div className="mt-2 flex flex-wrap gap-2">
            {assignees.map((name) => (
              <NativeButton
                key={name}
                onClick={() => toggleFilter('assignee', name!)}
                className={cn(
                  'px-3 py-1 rounded-full text-sm transition-colors',
                  filters.assignee.length === 0 || filters.assignee.includes(name!)
                    ? 'bg-violet-500 text-white'
                    : 'bg-surface text-fg-muted hover:text-white'
                )}
              >
                {name}
              </NativeButton>
            ))}
          </div>
        </div>

        {/* Type */}
        <div>
          <label className="text-xs font-medium text-fg-muted uppercase tracking-wider">
            Type
          </label>
          <div className="mt-2 flex flex-wrap gap-2">
            {types.map((type) => (
              <NativeButton
                key={type}
                onClick={() => toggleFilter('type', type)}
                className={cn(
                  'px-3 py-1 rounded-full text-sm capitalize transition-colors',
                  filters.type.includes(type)
                    ? 'bg-violet-500 text-white'
                    : 'bg-surface text-fg-muted hover:text-white'
                )}
              >
                {type}
              </NativeButton>
            ))}
          </div>
        </div>

        {/* Priority */}
        <div>
          <label className="text-xs font-medium text-fg-muted uppercase tracking-wider">
            Priority
          </label>
          <div className="mt-2 flex flex-wrap gap-2">
            {priorities.map((priority) => (
              <NativeButton
                key={priority}
                onClick={() => toggleFilter('priority', priority)}
                className={cn(
                  'px-3 py-1 rounded-full text-sm capitalize transition-colors',
                  filters.priority.includes(priority)
                    ? 'bg-violet-500 text-white'
                    : 'bg-surface text-fg-muted hover:text-white'
                )}
              >
                {priority}
              </NativeButton>
            ))}
          </div>
        </div>

        {/* Labels */}
        {labels.length > 0 && (
          <div>
            <label className="text-xs font-medium text-fg-muted uppercase tracking-wider">
              Labels
            </label>
            <div className="mt-2 flex flex-wrap gap-2">
              {labels.map((label) => (
                <NativeButton
                  key={label}
                  onClick={() => toggleFilter('labels', label)}
                  className={cn(
                    'px-3 py-1 rounded-full text-sm transition-colors',
                    filters.labels.includes(label)
                      ? 'bg-violet-500 text-white'
                      : 'bg-surface text-fg-muted hover:text-white'
                  )}
                >
                  {label}
                </NativeButton>
              ))}
            </div>
          </div>
        )}
      </div>
    </motion.div>
  );
};

// =============================================================================
// Sprint Selector Component
// =============================================================================

const SprintSelector: React.FC<{
  currentSprint: unknown;
  sprints: unknown[];
  onSelect: (sprintId: string) => void;
}> = ({ currentSprint, sprints, onSelect }) => {
  const [open, setOpen] = useState(false);

  return (
    <div className="relative">
      <NativeButton
        onClick={() => setOpen(!open)}
        className={cn(
          'flex items-center gap-2 px-4 py-2 rounded-lg',
          'bg-surface border border-border text-white',
          'hover:border-border transition-colors'
        )}
      >
        <Calendar className="w-4 h-4 text-violet-400" />
        <span>{currentSprint?.name || 'Select Sprint'}</span>
        <ChevronDown className="w-4 h-4 text-fg-muted" />
      </NativeButton>

      <AnimatePresence>
        {open && (
          <>
            <div
              className="fixed inset-0 z-10"
              onClick={() => setOpen(false)}
            />
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              className="absolute left-0 top-full mt-2 w-64 bg-surface border border-border rounded-xl shadow-xl z-20"
            >
              <div className="p-2 space-y-1 max-h-64 overflow-y-auto">
                {sprints.map((sprint) => (
                  <NativeButton
                    key={sprint.id}
                    onClick={() => {
                      onSelect(sprint.id);
                      setOpen(false);
                    }}
                    className={cn(
                      'w-full flex items-center justify-between p-3 rounded-lg text-left transition-colors',
                      sprint.id === currentSprint?.id
                        ? 'bg-violet-500/20 text-violet-400'
                        : 'hover:bg-surface text-fg-muted'
                    )}
                  >
                    <div>
                      <div className="font-medium">{sprint.name}</div>
                      <div className="text-xs text-fg-muted">
                        {sprint.startDate} - {sprint.endDate}
                      </div>
                    </div>
                    {sprint.status === 'active' && (
                      <span className="px-2 py-0.5 rounded text-xs bg-emerald-500/20 text-emerald-400">
                        Active
                      </span>
                    )}
                  </NativeButton>
                ))}
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  );
};

// =============================================================================
// Sprint Board Page Component
// =============================================================================

const SprintBoardPage: React.FC = () => {
    const { t } = useTranslation('common');
  const { projectId, sprintId } = useParams<{ projectId: string; sprintId: string }>();
  
  // State
  const [showFilters, setShowFilters] = useState(false);
  const [filters, setFilters] = useState<FilterState>({
    assignee: [],
    type: [],
    priority: [],
    labels: [],
    search: '',
  });

  // Jotai state
  const activeSprint = useAtomValue(activeSprintAtom);

  const handleSprintChange = useCallback(
    (newSprintId: string) => {
      // Note: activeSprintAtom is read-only, would need writable version for this
      console.log('Sprint change requested:', newSprintId);
    },
    []
  );

  const handleStoryClick = useCallback(
    (storyId: string) => {
      // Note: selectedStoryAtom is read-only, would need writable version for this
      console.log('Story clicked:', storyId);
      // Could open a modal or navigate
    },
    []
  );

  const activeFiltersCount = Object.values(filters).reduce(
    (count, v) => count + (Array.isArray(v) ? v.length : v ? 1 : 0),
    0
  );

  return (
    <div className="h-[calc(100vh-64px)] flex flex-col">
      {/* Header */}
      <div className="flex-shrink-0 border-b border-border bg-surface/50">
        <div className="flex items-center justify-between px-6 py-4">
          {/* Left: Sprint selector and info */}
          <div className="flex items-center gap-4">
            <SprintSelector
              currentSprint={activeSprint}
              sprints={[]}
              onSelect={handleSprintChange}
            />

            {activeSprint && (
              <div className="flex items-center gap-6 text-sm">
                <div className="flex items-center gap-2">
                  <span className="text-fg-muted">Days left:</span>
                  <span className="text-white font-medium">
                    {activeSprint.daysRemaining || 0}
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-fg-muted">Progress:</span>
                  <div className="w-32 h-2 bg-surface rounded-full overflow-hidden">
                    <div
                      className="h-full bg-violet-500 rounded-full"
                      style={{
                        width: `${activeSprint.progress || 0}%`,
                      }}
                    />
                  </div>
                  <span className="text-white font-medium">
                    {activeSprint.progress || 0}%
                  </span>
                </div>
              </div>
            )}
          </div>

          {/* Right: Actions */}
          <div className="flex items-center gap-2">
            {/* Search */}
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-fg-muted" />
              <NativeInput
                type="text"
                placeholder={t('sprintBoard.searchPlaceholder')}
                value={filters.search}
                onChange={(e) => setFilters({ ...filters, search: e.target.value })}
                className={cn(
                  'w-48 pl-9 pr-4 py-2 rounded-lg',
                  'bg-surface border border-border text-white placeholder-zinc-500',
                  'focus:outline-none focus:ring-2 focus:ring-violet-500 focus:border-transparent',
                  'text-sm'
                )}
              />
            </div>

            {/* Filters */}
            <div className="relative">
              <NativeButton
                onClick={() => setShowFilters(!showFilters)}
                className={cn(
                  'flex items-center gap-2 px-3 py-2 rounded-lg text-sm transition-colors',
                  showFilters || activeFiltersCount > 0
                    ? 'bg-violet-500/20 text-violet-400 border border-violet-500/30'
                    : 'bg-surface text-fg-muted hover:text-white border border-border'
                )}
              >
                <Filter className="w-4 h-4" />
                <span>{t('sprintBoard.filters')}</span>
                {activeFiltersCount > 0 && (
                  <span className="px-1.5 py-0.5 rounded-full bg-violet-500 text-white text-xs">
                    {activeFiltersCount}
                  </span>
                )}
              </NativeButton>

              <AnimatePresence>
                {showFilters && (
                  <FilterPanel
                    filters={filters}
                    onFiltersChange={setFilters}
                    onClose={() => setShowFilters(false)}
                  />
                )}
              </AnimatePresence>
            </div>

            {/* View Options */}
            <NativeButton
              className={cn(
                'p-2 rounded-lg transition-colors',
                'bg-surface text-fg-muted hover:text-white border border-border'
              )}
              title="Board Settings"
            >
              <SlidersHorizontal className="w-4 h-4" />
            </NativeButton>

            {/* Refresh */}
            <NativeButton
              className={cn(
                'p-2 rounded-lg transition-colors',
                'bg-surface text-fg-muted hover:text-white border border-border'
              )}
              title="Refresh"
            >
              <RefreshCw className="w-4 h-4" />
            </NativeButton>

            {/* Velocity */}
            <NativeButton
              className={cn(
                'p-2 rounded-lg transition-colors',
                'bg-surface text-fg-muted hover:text-white border border-border'
              )}
              title="View Metrics"
            >
              <BarChart3 className="w-4 h-4" />
            </NativeButton>

            {/* Add Story */}
            <NativeButton
              className={cn(
                'flex items-center gap-2 px-4 py-2 rounded-lg font-medium text-sm',
                'bg-violet-500 text-white hover:bg-violet-600 transition-colors'
              )}
            >
              <Plus className="w-4 h-4" />
              Add Story
            </NativeButton>
          </div>
        </div>
      </div>

      {/* Board */}
      <div className="flex-1 overflow-hidden">
        <SprintBoard
          sprintId={sprintId || activeSprint?.id}
          projectId={projectId}
          filters={{
            assignees: filters.assignee,
            types: filters.type,
            priorities: filters.priority,
            labels: filters.labels,
            search: filters.search,
          }}
          onStoryClick={handleStoryClick}
          onStoryMove={(storyId, fromColumn, toColumn) => {
            // Handle story movement - update status
            console.log(`Move ${storyId} from ${fromColumn} to ${toColumn}`);
          }}
          onCreateStory={(columnId: string) => {
            // Open create story modal for this column
            console.log(`Create story in ${columnId}`);
          }}
        />
      </div>
    </div>
  );
};

export default SprintBoardPage;
