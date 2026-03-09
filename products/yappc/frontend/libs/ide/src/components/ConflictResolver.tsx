/**
 * @ghatana/yappc-ide - Conflict Resolver Component
 * 
 * Interface for resolving collaboration conflicts with merge tools
 * and visual diff display.
 * 
 * @doc.type component
 * @doc.purpose Conflict resolution interface for collaborative editing
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback } from 'react';

import type { Conflict } from '../hooks/useCollaborativeEditing';

/**
 * Conflict resolution options
 */
export type ConflictResolution = 'accept-theirs' | 'accept-mine' | 'merge' | 'postpone';

/**
 * Conflict resolver props
 */
export interface ConflictResolverProps {
  conflicts: Conflict[];
  onResolve: (conflictId: string, resolution: ConflictResolution) => void;
  className?: string;
}

/**
 * Diff view component for conflict visualization
 */
interface DiffViewProps {
  originalContent: string;
  theirContent: string;
  myContent: string;
  onResolution: (resolution: ConflictResolution) => void;
}

const DiffView: React.FC<DiffViewProps> = ({
  originalContent,
  theirContent,
  myContent,
  onResolution,
}) => {
  const [selectedResolution, setSelectedResolution] = useState<ConflictResolution>('postpone');

  const handleResolve = useCallback(() => {
    if (selectedResolution !== 'postpone') {
      onResolution(selectedResolution);
    }
  }, [selectedResolution, onResolution]);

  return (
    <div className="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden">
      {/* Resolution options */}
      <div className="flex items-center gap-2 p-3 bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          Resolution:
        </span>
        <div className="flex gap-2">
          <button
            onClick={() => setSelectedResolution('accept-theirs')}
            className={`
              px-3 py-1 text-xs rounded transition-colors
              ${selectedResolution === 'accept-theirs'
                ? 'bg-blue-500 text-white'
                : 'bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-600'
              }
            `}
          >
            Accept Theirs
          </button>
          <button
            onClick={() => setSelectedResolution('accept-mine')}
            className={`
              px-3 py-1 text-xs rounded transition-colors
              ${selectedResolution === 'accept-mine'
                ? 'bg-blue-500 text-white'
                : 'bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-600'
              }
            `}
          >
            Accept Mine
          </button>
          <button
            onClick={() => setSelectedResolution('merge')}
            className={`
              px-3 py-1 text-xs rounded transition-colors
              ${selectedResolution === 'merge'
                ? 'bg-blue-500 text-white'
                : 'bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-600'
              }
            `}
          >
            Merge
          </button>
        </div>
        <div className="flex-1" />
        <button
          onClick={handleResolve}
          disabled={selectedResolution === 'postpone'}
          className={`
            px-3 py-1 text-xs rounded transition-colors
            ${selectedResolution === 'postpone'
              ? 'bg-gray-100 dark:bg-gray-800 text-gray-400 dark:text-gray-600 cursor-not-allowed'
              : 'bg-green-500 text-white hover:bg-green-600'
            }
          `}
        >
          Apply Resolution
        </button>
      </div>

      {/* Content comparison */}
      <div className="grid grid-cols-3 divide-x divide-gray-200 dark:divide-gray-700">
        {/* Original */}
        <div className="p-3">
          <div className="text-xs font-medium text-gray-500 dark:text-gray-400 mb-2">
            Original
          </div>
          <pre className="text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap font-mono bg-gray-50 dark:bg-gray-900 p-2 rounded">
            {originalContent}
          </pre>
        </div>

        {/* Their version */}
        <div className="p-3">
          <div className="text-xs font-medium text-red-500 dark:text-red-400 mb-2">
            Their Version
          </div>
          <pre className="text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap font-mono bg-red-50 dark:bg-red-900/20 p-2 rounded border border-red-200 dark:border-red-800">
            {theirContent}
          </pre>
        </div>

        {/* My version */}
        <div className="p-3">
          <div className="text-xs font-medium text-blue-500 dark:text-blue-400 mb-2">
            My Version
          </div>
          <pre className="text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap font-mono bg-blue-50 dark:bg-blue-900/20 p-2 rounded border border-blue-200 dark:border-blue-800">
            {myContent}
          </pre>
        </div>
      </div>
    </div>
  );
};

/**
 * Single conflict item
 */
interface ConflictItemProps {
  conflict: Conflict;
  onResolve: (conflictId: string, resolution: ConflictResolution) => void;
}

const ConflictItem: React.FC<ConflictItemProps> = ({ conflict, onResolve }) => {
  const [isExpanded, setIsExpanded] = useState(false);
  const [showDiff, setShowDiff] = useState(false);

  const handleResolve = useCallback((resolution: ConflictResolution) => {
    onResolve(conflict.id, resolution);
    setShowDiff(false);
  }, [conflict.id, onResolve]);

  const getConflictIcon = useCallback(() => {
    switch (conflict.type) {
      case 'concurrent-edit':
        return '⚡';
      case 'selection-overlap':
        return '🎯';
      case 'file-lock':
        return '🔒';
      default:
        return '⚠️';
    }
  }, [conflict.type]);

  const getConflictDescription = useCallback(() => {
    switch (conflict.type) {
      case 'concurrent-edit':
        return 'Multiple users edited the same content simultaneously';
      case 'selection-overlap':
        return 'Users have overlapping selections in this area';
      case 'file-lock':
        return 'File is locked by another user';
      default:
        return 'Unknown conflict type';
    }
  }, [conflict.type]);

  return (
    <div className="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden">
      {/* Conflict header */}
      <div
        className="flex items-center gap-3 p-3 bg-yellow-50 dark:bg-yellow-900/20 cursor-pointer hover:bg-yellow-100 dark:hover:bg-yellow-900/30 transition-colors"
        onClick={() => setIsExpanded(!isExpanded)}
      >
        <div className="text-lg">
          {getConflictIcon()}
        </div>
        <div className="flex-1 min-w-0">
          <div className="text-sm font-medium text-gray-900 dark:text-gray-100">
            {getConflictDescription()}
          </div>
          <div className="text-xs text-gray-500 dark:text-gray-400">
            Line {conflict.position.line + 1}, Column {conflict.position.column + 1} • 
            {conflict.users.length} users involved • 
            {new Date(conflict.timestamp).toLocaleTimeString()}
          </div>
        </div>
        <div className="flex items-center gap-2">
          {!showDiff && (
            <div className="flex gap-1">
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  handleResolve('accept-theirs');
                }}
                className="px-2 py-1 text-xs bg-red-500 text-white rounded hover:bg-red-600 transition-colors"
              >
                Accept Theirs
              </button>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  handleResolve('accept-mine');
                }}
                className="px-2 py-1 text-xs bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors"
              >
                Accept Mine
              </button>
            </div>
          )}
          <button
            onClick={(e) => {
              e.stopPropagation();
              setShowDiff(!showDiff);
            }}
            className="px-2 py-1 text-xs bg-gray-500 text-white rounded hover:bg-gray-600 transition-colors"
          >
            {showDiff ? 'Hide' : 'Show'} Diff
          </button>
          <div className={`
            w-4 h-4 text-gray-400 transition-transform
            ${isExpanded ? 'rotate-90' : ''}
          `}>
            ▶
          </div>
        </div>
      </div>

      {/* Expanded content */}
      {isExpanded && (
        <div className="p-3 border-t border-gray-200 dark:border-gray-700">
          {/* Affected users */}
          <div className="mb-3">
            <div className="text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
              Affected Users:
            </div>
            <div className="flex flex-wrap gap-2">
              {conflict.users.map(userId => (
                <div
                  key={userId}
                  className="px-2 py-1 text-xs bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 rounded"
                >
                  {userId}
                </div>
              ))}
            </div>
          </div>

          {/* Quick actions */}
          <div className="flex gap-2">
            <button
              onClick={() => handleResolve('accept-theirs')}
              className="px-3 py-1 text-xs bg-red-500 text-white rounded hover:bg-red-600 transition-colors"
            >
              Accept Their Version
            </button>
            <button
              onClick={() => handleResolve('accept-mine')}
              className="px-3 py-1 text-xs bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors"
            >
              Accept My Version
            </button>
            <button
              onClick={() => handleResolve('merge')}
              className="px-3 py-1 text-xs bg-green-500 text-white rounded hover:bg-green-600 transition-colors"
            >
              Merge Manually
            </button>
            <button
              onClick={() => handleResolve('postpone')}
              className="px-3 py-1 text-xs bg-gray-500 text-white rounded hover:bg-gray-600 transition-colors"
            >
              Decide Later
            </button>
          </div>
        </div>
      )}

      {/* Diff view */}
      {showDiff && (
        <div className="border-t border-gray-200 dark:border-gray-700">
          <DiffView
            originalContent="// Original content"
            theirContent="// Their version of the content"
            myContent="// My version of the content"
            onResolution={handleResolve}
          />
        </div>
      )}
    </div>
  );
};

/**
 * Conflict Resolver Component
 */
export const ConflictResolver: React.FC<ConflictResolverProps> = ({
  conflicts,
  onResolve,
  className = '',
}) => {
  const [filter, setFilter] = useState<'all' | 'unresolved' | 'resolved'>('unresolved');
  const [sortBy, setSortBy] = useState<'time' | 'type' | 'users'>('time');

  const filteredConflicts = conflicts.filter(conflict => {
    switch (filter) {
      case 'unresolved':
        return !conflict.resolved;
      case 'resolved':
        return conflict.resolved;
      default:
        return true;
    }
  });

  const sortedConflicts = [...filteredConflicts].sort((a, b) => {
    switch (sortBy) {
      case 'time':
        return b.timestamp - a.timestamp;
      case 'type':
        return a.type.localeCompare(b.type);
      case 'users':
        return b.users.length - a.users.length;
      default:
        return 0;
    }
  });

  const unresolvedCount = conflicts.filter(c => !c.resolved).length;

  return (
    <div className={`bg-white dark:bg-gray-900 rounded-lg shadow-lg ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-gray-200 dark:border-gray-700">
        <div>
          <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
            Conflicts
          </h3>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            {unresolvedCount} unresolved • {conflicts.length} total
          </p>
        </div>
        <div className="flex items-center gap-2">
          {/* Filter */}
          <select
            value={filter}
            onChange={(e) => setFilter(e.target.value as 'all' | 'unresolved' | 'resolved')}
            className="px-3 py-1 text-sm border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300"
          >
            <option value="all">All</option>
            <option value="unresolved">Unresolved</option>
            <option value="resolved">Resolved</option>
          </select>

          {/* Sort */}
          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value as 'time' | 'type' | 'users')}
            className="px-3 py-1 text-sm border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300"
          >
            <option value="time">Sort by Time</option>
            <option value="type">Sort by Type</option>
            <option value="users">Sort by Users</option>
          </select>
        </div>
      </div>

      {/* Conflict list */}
      <div className="max-h-96 overflow-y-auto">
        {sortedConflicts.length === 0 ? (
          <div className="p-8 text-center text-gray-500 dark:text-gray-400">
            <div className="text-2xl mb-2">✅</div>
            <p>No conflicts found</p>
          </div>
        ) : (
          <div className="divide-y divide-gray-200 dark:divide-gray-700">
            {sortedConflicts.map(conflict => (
              <ConflictItem
                key={conflict.id}
                conflict={conflict}
                onResolve={onResolve}
              />
            ))}
          </div>
        )}
      </div>

      {/* Footer */}
      {unresolvedCount > 0 && (
        <div className="p-4 border-t border-gray-200 dark:border-gray-700 bg-yellow-50 dark:bg-yellow-900/20">
          <div className="flex items-center justify-between">
            <p className="text-sm text-yellow-800 dark:text-yellow-200">
              {unresolvedCount} conflicts need resolution
            </p>
            <button
              onClick={() => {
                // Auto-resolve all conflicts with "accept-mine" strategy
                sortedConflicts
                  .filter(c => !c.resolved)
                  .forEach(conflict => onResolve(conflict.id, 'accept-mine'));
              }}
              className="px-3 py-1 text-sm bg-yellow-500 text-white rounded hover:bg-yellow-600 transition-colors"
            >
              Resolve All (Accept Mine)
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
