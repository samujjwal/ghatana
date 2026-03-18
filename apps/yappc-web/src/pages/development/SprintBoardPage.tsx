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

// =============================================================================
// Filter Panel Component
// =============================================================================

const FilterPanel: React.FC<{
  filters: FilterState;
  onFiltersChange: (filters: FilterState) => void;
  onClose: () => void;
}> = ({ filters, onFiltersChange, onClose }) => {
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
      className="absolute right-0 top-full mt-2 w-80 bg-zinc-900 border border-zinc-700 rounded-xl shadow-xl z-50"
    >
      <div className="flex items-center justify-between p-4 border-b border-zinc-800">
        <span className="font-medium text-white">Filters</span>
        <div className="flex items-center gap-2">
          {hasActiveFilters && (
            <button
              onClick={clearFilters}
              className="text-xs text-violet-400 hover:text-violet-300"
            >
              Clear all
            </button>
          )}
          <button onClick={onClose} className="p-1 hover:bg-zinc-800 rounded">
            <X className="w-4 h-4 text-zinc-400" />
          </button>
        </div>
      </div>

      <div className="p-4 space-y-4 max-h-96 overflow-y-auto">
        {/* Assignee */}
        <div>
          <label className="text-xs font-medium text-zinc-400 uppercase tracking-wider">
            Assignee
          </label>
          <div className="mt-2 flex flex-wrap gap-2">
            {assignees.map((name) => (
              <button
                key={name}
                onClick={() => toggleFilter('assignee', name!)}
                className={cn(
                  'px-3 py-1 rounded-full text-sm transition-colors',
                  filters.assignee.length === 0 || filters.assignee.includes(name!)
                    ? 'bg-violet-500 text-white'
                    : 'bg-zinc-800 text-zinc-400 hover:text-white'
                )}
              >
                {name}
              </button>
            ))}
          </div>
        </div>

        {/* Type */}
        <div>
          <label className="text-xs font-medium text-zinc-400 uppercase tracking-wider">
            Type
          </label>
          <div className="mt-2 flex flex-wrap gap-2">
            {types.map((type) => (
              <button
                key={type}
                onClick={() => toggleFilter('type', type)}
                className={cn(
                  'px-3 py-1 rounded-full text-sm capitalize transition-colors',
                  filters.type.includes(type)
                    ? 'bg-violet-500 text-white'
                    : 'bg-zinc-800 text-zinc-400 hover:text-white'
                )}
              >
                {type}
              </button>
            ))}
          </div>
        </div>

        {/* Priority */}
        <div>
          <label className="text-xs font-medium text-zinc-400 uppercase tracking-wider">
            Priority
          </label>
          <div className="mt-2 flex flex-wrap gap-2">
            {priorities.map((priority) => (
              <button
                key={priority}
                onClick={() => toggleFilter('priority', priority)}
                className={cn(
                  'px-3 py-1 rounded-full text-sm capitalize transition-colors',
                  filters.priority.includes(priority)
                    ? 'bg-violet-500 text-white'
                    : 'bg-zinc-800 text-zinc-400 hover:text-white'
                )}
              >
                {priority}
              </button>
            ))}
          </div>
        </div>

        {/* Labels */}
        {labels.length > 0 && (
          <div>
            <label className="text-xs font-medium text-zinc-400 uppercase tracking-wider">
              Labels
            </label>
            <div className="mt-2 flex flex-wrap gap-2">
              {labels.map((label) => (
                <button
                  key={label}
                  onClick={() => toggleFilter('labels', label)}
                  className={cn(
                    'px-3 py-1 rounded-full text-sm transition-colors',
                    filters.labels.includes(label)
                      ? 'bg-violet-500 text-white'
                      : 'bg-zinc-800 text-zinc-400 hover:text-white'
                  )}
                >
                  {label}
                </button>
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
      <button
        onClick={() => setOpen(!open)}
        className={cn(
          'flex items-center gap-2 px-4 py-2 rounded-lg',
          'bg-zinc-800 border border-zinc-700 text-white',
          'hover:border-zinc-600 transition-colors'
        )}
      >
        <Calendar className="w-4 h-4 text-violet-400" />
        <span>{currentSprint?.name || 'Select Sprint'}</span>
        <ChevronDown className="w-4 h-4 text-zinc-400" />
      </button>

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
              className="absolute left-0 top-full mt-2 w-64 bg-zinc-900 border border-zinc-700 rounded-xl shadow-xl z-20"
            >
              <div className="p-2 space-y-1 max-h-64 overflow-y-auto">
                {sprints.map((sprint) => (
                  <button
                    key={sprint.id}
                    onClick={() => {
                      onSelect(sprint.id);
                      setOpen(false);
                    }}
                    className={cn(
                      'w-full flex items-center justify-between p-3 rounded-lg text-left transition-colors',
                      sprint.id === currentSprint?.id
                        ? 'bg-violet-500/20 text-violet-400'
                        : 'hover:bg-zinc-800 text-zinc-300'
                    )}
                  >
                    <div>
                      <div className="font-medium">{sprint.name}</div>
                      <div className="text-xs text-zinc-500">
                        {sprint.startDate} - {sprint.endDate}
                      </div>
                    </div>
                    {sprint.status === 'active' && (
                      <span className="px-2 py-0.5 rounded text-xs bg-emerald-500/20 text-emerald-400">
                        Active
                      </span>
                    )}
                  </button>
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
      <div className="flex-shrink-0 border-b border-zinc-800 bg-zinc-900/50">
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
                  <span className="text-zinc-500">Days left:</span>
                  <span className="text-white font-medium">
                    {activeSprint.daysRemaining || 0}
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-zinc-500">Progress:</span>
                  <div className="w-32 h-2 bg-zinc-800 rounded-full overflow-hidden">
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
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-500" />
              <input
                type="text"
                placeholder="Search stories..."
                value={filters.search}
                onChange={(e) => setFilters({ ...filters, search: e.target.value })}
                className={cn(
                  'w-48 pl-9 pr-4 py-2 rounded-lg',
                  'bg-zinc-800 border border-zinc-700 text-white placeholder-zinc-500',
                  'focus:outline-none focus:ring-2 focus:ring-violet-500 focus:border-transparent',
                  'text-sm'
                )}
              />
            </div>

            {/* Filters */}
            <div className="relative">
              <button
                onClick={() => setShowFilters(!showFilters)}
                className={cn(
                  'flex items-center gap-2 px-3 py-2 rounded-lg text-sm transition-colors',
                  showFilters || activeFiltersCount > 0
                    ? 'bg-violet-500/20 text-violet-400 border border-violet-500/30'
                    : 'bg-zinc-800 text-zinc-400 hover:text-white border border-zinc-700'
                )}
              >
                <Filter className="w-4 h-4" />
                <span>Filters</span>
                {activeFiltersCount > 0 && (
                  <span className="px-1.5 py-0.5 rounded-full bg-violet-500 text-white text-xs">
                    {activeFiltersCount}
                  </span>
                )}
              </button>

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
            <button
              className={cn(
                'p-2 rounded-lg transition-colors',
                'bg-zinc-800 text-zinc-400 hover:text-white border border-zinc-700'
              )}
              title="Board Settings"
            >
              <SlidersHorizontal className="w-4 h-4" />
            </button>

            {/* Refresh */}
            <button
              className={cn(
                'p-2 rounded-lg transition-colors',
                'bg-zinc-800 text-zinc-400 hover:text-white border border-zinc-700'
              )}
              title="Refresh"
            >
              <RefreshCw className="w-4 h-4" />
            </button>

            {/* Velocity */}
            <button
              className={cn(
                'p-2 rounded-lg transition-colors',
                'bg-zinc-800 text-zinc-400 hover:text-white border border-zinc-700'
              )}
              title="View Metrics"
            >
              <BarChart3 className="w-4 h-4" />
            </button>

            {/* Add Story */}
            <button
              className={cn(
                'flex items-center gap-2 px-4 py-2 rounded-lg font-medium text-sm',
                'bg-violet-500 text-white hover:bg-violet-600 transition-colors'
              )}
            >
              <Plus className="w-4 h-4" />
              Add Story
            </button>
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
