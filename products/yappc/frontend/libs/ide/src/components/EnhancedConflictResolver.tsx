/**
 * @ghatana/yappc-ide - Enhanced Conflict Resolver Component
 * 
 * Advanced conflict resolution interface with merge visualization,
 * diff views, and interactive resolution tools.
 * 
 * @doc.type component
 * @doc.purpose Enhanced conflict resolution for collaborative editing
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback, useMemo } from 'react';
import { InteractiveButton } from './MicroInteractions';
import type { UserPresence } from '../hooks/useCollaborativeEditing';

/**
 * Conflict types
 */
export type ConflictType = 'edit' | 'delete' | 'rename' | 'permission' | 'format';

/**
 * Conflict severity levels
 */
export type ConflictSeverity = 'low' | 'medium' | 'high' | 'critical';

/**
 * Conflict resolution options
 */
export type ResolutionAction = 'accept-theirs' | 'accept-mine' | 'merge' | 'manual' | 'postpone';

/**
 * File conflict information
 */
export interface FileConflict {
  id: string;
  fileId: string;
  fileName: string;
  filePath: string;
  type: ConflictType;
  severity: ConflictSeverity;
  description: string;
  users: UserPresence[];
  timestamp: number;
  content?: {
    base: string;
    theirs: string;
    mine: string;
    result?: string;
  };
  metadata?: {
    lineNumbers?: { start: number; end: number };
    changeType?: 'insert' | 'delete' | 'modify';
    size?: number;
  };
}

/**
 * Enhanced conflict resolver props
 */
export interface EnhancedConflictResolverProps {
  conflicts: FileConflict[];
  onResolveConflict: (conflictId: string, action: ResolutionAction, resolution?: string) => void;
  onPostponeConflict: (conflictId: string) => void;
  onRequestUserInfo: (userId: string) => void;
  showDiffView?: boolean;
  enableAutoMerge?: boolean;
  enableBatchResolution?: boolean;
  className?: string;
}

/**
 * Diff view component
 */
interface DiffViewProps {
  base: string;
  theirs: string;
  mine: string;
  onMergeComplete: (result: string) => void;
}

const DiffView: React.FC<DiffViewProps> = ({ base, theirs, mine, onMergeComplete }) => {
  const [selectedVersion, setSelectedVersion] = useState<'base' | 'theirs' | 'mine'>('base');

  const lines = useMemo(() => {
    const baseLines = base.split('\n');
    const theirsLines = theirs.split('\n');
    const mineLines = mine.split('\n');

    const maxLines = Math.max(baseLines.length, theirsLines.length, mineLines.length);

    return Array.from({ length: maxLines }, (_, i) => ({
      base: baseLines[i] || '',
      theirs: theirsLines[i] || '',
      mine: mineLines[i] || '',
      lineNumber: i + 1,
    }));
  }, [base, theirs, mine]);


  return (
    <div className="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden">
      {/* Version selector */}
      <div className="flex border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800">
        <button
          onClick={() => setSelectedVersion('base')}
          className={`px-4 py-2 text-sm font-medium border-r border-gray-200 dark:border-gray-700 ${selectedVersion === 'base'
            ? 'bg-white dark:bg-gray-900 text-blue-600 dark:text-blue-400'
            : 'text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200'
            }`}
        >
          Base Version
        </button>
        <button
          onClick={() => setSelectedVersion('theirs')}
          className={`px-4 py-2 text-sm font-medium border-r border-gray-200 dark:border-gray-700 ${selectedVersion === 'theirs'
            ? 'bg-white dark:bg-gray-900 text-blue-600 dark:text-blue-400'
            : 'text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200'
            }`}
        >
          Their Version
        </button>
        <button
          onClick={() => setSelectedVersion('mine')}
          className={`px-4 py-2 text-sm font-medium ${selectedVersion === 'mine'
            ? 'bg-white dark:bg-gray-900 text-blue-600 dark:text-blue-400'
            : 'text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200'
            }`}
        >
          My Version
        </button>
      </div>

      {/* Content display */}
      <div className="max-h-96 overflow-y-auto">
        <div className="grid grid-cols-3 gap-0">
          <div className="border-r border-gray-200 dark:border-gray-700">
            <div className="bg-gray-50 dark:bg-gray-800 px-3 py-1 text-xs font-medium text-gray-600 dark:text-gray-400">
              Base
            </div>
            <div className="p-3">
              {lines.map((line, i) => (
                <div key={i} className="flex">
                  <span className="text-xs text-gray-400 mr-3 w-8 text-right">
                    {line.lineNumber}
                  </span>
                  <span className="text-sm font-mono">{line.base || '\u00A0'}</span>
                </div>
              ))}
            </div>
          </div>

          <div className="border-r border-gray-200 dark:border-gray-700">
            <div className="bg-blue-50 dark:bg-blue-900/20 px-3 py-1 text-xs font-medium text-blue-600 dark:text-blue-400">
              Theirs
            </div>
            <div className="p-3">
              {lines.map((line, i) => (
                <div key={i} className="flex">
                  <span className="text-xs text-gray-400 mr-3 w-8 text-right">
                    {line.lineNumber}
                  </span>
                  <span className={`text-sm font-mono ${line.theirs !== line.base ? 'text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/20' : ''
                    }`}>
                    {line.theirs || '\u00A0'}
                  </span>
                </div>
              ))}
            </div>
          </div>

          <div>
            <div className="bg-green-50 dark:bg-green-900/20 px-3 py-1 text-xs font-medium text-green-600 dark:text-green-400">
              Mine
            </div>
            <div className="p-3">
              {lines.map((line, i) => (
                <div key={i} className="flex">
                  <span className="text-xs text-gray-400 mr-3 w-8 text-right">
                    {line.lineNumber}
                  </span>
                  <span className={`text-sm font-mono ${line.mine !== line.base ? 'text-green-600 dark:text-green-400 bg-green-50 dark:bg-green-900/20' : ''
                    }`}>
                    {line.mine || '\u00A0'}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Resolution actions */}
      <div className="border-t border-gray-200 dark:border-gray-700 p-3 bg-gray-50 dark:bg-gray-800">
        <div className="flex justify-between items-center">
          <div className="text-sm text-gray-600 dark:text-gray-400">
            Choose a resolution strategy
          </div>
          <div className="flex gap-2">
            <InteractiveButton
              variant="secondary"
              size="sm"
              onClick={() => onMergeComplete(theirs)}
            >
              Accept Theirs
            </InteractiveButton>
            <InteractiveButton
              variant="secondary"
              size="sm"
              onClick={() => onMergeComplete(mine)}
            >
              Accept Mine
            </InteractiveButton>
            <InteractiveButton
              variant="primary"
              size="sm"
              onClick={() => onMergeComplete(base)}
            >
              Accept Base
            </InteractiveButton>
          </div>
        </div>
      </div>
    </div>
  );
};

/**
 * Single conflict card component
 */
interface ConflictCardProps {
  conflict: FileConflict;
  onResolve: (action: ResolutionAction, resolution?: string) => void;
  onPostpone: () => void;
  onRequestUserInfo: (userId: string) => void;
  showDiff: boolean;
}

const ConflictCard: React.FC<ConflictCardProps> = ({
  conflict,
  onResolve,
  onPostpone,
  onRequestUserInfo,
  showDiff,
}) => {
  const [showDetails, setShowDetails] = useState(false);
  const [showDiffView, setShowDiffView] = useState(false);

  const getSeverityColor = (severity: ConflictSeverity) => {
    switch (severity) {
      case 'critical': return 'text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/20';
      case 'high': return 'text-orange-600 dark:text-orange-400 bg-orange-50 dark:bg-orange-900/20';
      case 'medium': return 'text-yellow-600 dark:text-yellow-400 bg-yellow-50 dark:bg-yellow-900/20';
      case 'low': return 'text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/20';
    }
  };

  const getTypeIcon = (type: ConflictType) => {
    switch (type) {
      case 'edit': return '✏️';
      case 'delete': return '🗑️';
      case 'rename': return '📝';
      case 'permission': return '🔒';
      case 'format': return '🎨';
    }
  };

  return (
    <div className="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden">
      {/* Conflict header */}
      <div className="p-4 bg-white dark:bg-gray-900">
        <div className="flex items-start justify-between">
          <div className="flex-1">
            <div className="flex items-center gap-2 mb-2">
              <span className="text-lg">{getTypeIcon(conflict.type)}</span>
              <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">
                {conflict.fileName}
              </h3>
              <span className={`px-2 py-1 text-xs font-medium rounded ${getSeverityColor(conflict.severity)}`}>
                {conflict.severity.toUpperCase()}
              </span>
            </div>
            <p className="text-sm text-gray-600 dark:text-gray-400 mb-2">
              {conflict.description}
            </p>
            <div className="flex items-center gap-4 text-xs text-gray-500 dark:text-gray-400">
              <span>📁 {conflict.filePath}</span>
              <span>🕐 {new Date(conflict.timestamp).toLocaleString()}</span>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <InteractiveButton
              variant="ghost"
              size="sm"
              onClick={() => setShowDetails(!showDetails)}
            >
              {showDetails ? 'Hide' : 'Details'}
            </InteractiveButton>
            {showDiff && conflict.content && (
              <InteractiveButton
                variant="secondary"
                size="sm"
                onClick={() => setShowDiffView(!showDiffView)}
              >
                {showDiffView ? 'Hide Diff' : 'Show Diff'}
              </InteractiveButton>
            )}
          </div>
        </div>

        {/* Users involved */}
        <div className="mt-3 flex items-center gap-2">
          <span className="text-xs text-gray-500 dark:text-gray-400">Involved:</span>
          {conflict.users.map(user => (
            <button
              key={user.userId}
              onClick={() => onRequestUserInfo(user.userId)}
              className="flex items-center gap-1 px-2 py-1 text-xs bg-gray-100 dark:bg-gray-800 rounded hover:bg-gray-200 dark:hover:bg-gray-700"
            >
              <div
                className="w-4 h-4 rounded-full"
                style={{ backgroundColor: user.userColor }}
              />
              {user.userName}
            </button>
          ))}
        </div>
      </div>

      {/* Detailed information */}
      {showDetails && (
        <div className="border-t border-gray-200 dark:border-gray-700 p-4 bg-gray-50 dark:bg-gray-800">
          <div className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <span className="font-medium text-gray-700 dark:text-gray-300">Type:</span>
              <span className="ml-2 text-gray-600 dark:text-gray-400">{conflict.type}</span>
            </div>
            <div>
              <span className="font-medium text-gray-700 dark:text-gray-300">Severity:</span>
              <span className="ml-2 text-gray-600 dark:text-gray-400">{conflict.severity}</span>
            </div>
            {conflict.metadata?.lineNumbers && (
              <div>
                <span className="font-medium text-gray-700 dark:text-gray-300">Lines:</span>
                <span className="ml-2 text-gray-600 dark:text-gray-400">
                  {conflict.metadata.lineNumbers.start}-{conflict.metadata.lineNumbers.end}
                </span>
              </div>
            )}
            {conflict.metadata?.changeType && (
              <div>
                <span className="font-medium text-gray-700 dark:text-gray-300">Change:</span>
                <span className="ml-2 text-gray-600 dark:text-gray-400">{conflict.metadata.changeType}</span>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Diff view */}
      {showDiffView && conflict.content && (
        <div className="border-t border-gray-200 dark:border-gray-700 p-4">
          <DiffView
            base={conflict.content.base}
            theirs={conflict.content.theirs}
            mine={conflict.content.mine}
            onMergeComplete={(result) => onResolve('manual', result)}
          />
        </div>
      )}

      {/* Resolution actions */}
      <div className="border-t border-gray-200 dark:border-gray-700 p-4 bg-white dark:bg-gray-900">
        <div className="flex justify-between items-center">
          <div className="text-sm text-gray-600 dark:text-gray-400">
            Choose how to resolve this conflict
          </div>
          <div className="flex gap-2">
            <InteractiveButton
              variant="ghost"
              size="sm"
              onClick={onPostpone}
            >
              Postpone
            </InteractiveButton>
            <InteractiveButton
              variant="secondary"
              size="sm"
              onClick={() => onResolve('accept-theirs')}
            >
              Accept Theirs
            </InteractiveButton>
            <InteractiveButton
              variant="secondary"
              size="sm"
              onClick={() => onResolve('accept-mine')}
            >
              Accept Mine
            </InteractiveButton>
            <InteractiveButton
              variant="primary"
              size="sm"
              onClick={() => onResolve('merge')}
            >
              Auto Merge
            </InteractiveButton>
          </div>
        </div>
      </div>
    </div>
  );
};

/**
 * Enhanced Conflict Resolver Component
 */
export const EnhancedConflictResolver: React.FC<EnhancedConflictResolverProps> = ({
  conflicts,
  onResolveConflict,
  onPostponeConflict,
  onRequestUserInfo,
  showDiffView = true,
  enableAutoMerge = true,
  enableBatchResolution = true,
  className = '',
}) => {
  const [selectedConflicts, setSelectedConflicts] = useState<Set<string>>(new Set());
  const [filter, setFilter] = useState<{
    severity?: ConflictSeverity;
    type?: ConflictType;
    resolved?: boolean;
  }>({});

  const filteredConflicts = useMemo(() => {
    return conflicts.filter(conflict => {
      if (filter.severity && conflict.severity !== filter.severity) return false;
      if (filter.type && conflict.type !== filter.type) return false;
      return true;
    });
  }, [conflicts, filter]);

  const handleResolveConflict = useCallback((conflictId: string, action: ResolutionAction, resolution?: string) => {
    onResolveConflict(conflictId, action, resolution);
    setSelectedConflicts(prev => {
      const newSet = new Set(prev);
      newSet.delete(conflictId);
      return newSet;
    });
  }, [onResolveConflict]);

  const handleBatchResolve = useCallback((action: ResolutionAction) => {
    selectedConflicts.forEach(conflictId => {
      onResolveConflict(conflictId, action);
    });
    setSelectedConflicts(new Set());
  }, [selectedConflicts, onResolveConflict]);

  const handleSelectAll = useCallback(() => {
    if (selectedConflicts.size === filteredConflicts.length) {
      setSelectedConflicts(new Set());
    } else {
      setSelectedConflicts(new Set(filteredConflicts.map(c => c.id)));
    }
  }, [selectedConflicts.size, filteredConflicts]);

  const severityCounts = useMemo(() => {
    return conflicts.reduce((acc, conflict) => {
      acc[conflict.severity] = (acc[conflict.severity] || 0) + 1;
      return acc;
    }, {} as Record<ConflictSeverity, number>);
  }, [conflicts]);

  if (conflicts.length === 0) {
    return (
      <div className={`text-center py-8 text-gray-500 dark:text-gray-400 ${className}`}>
        <div className="text-4xl mb-2">✅</div>
        <div className="text-lg font-medium">No conflicts detected</div>
        <div className="text-sm">All files are in sync</div>
      </div>
    );
  }

  return (
    <div className={`space-y-4 ${className}`}>
      {/* Header with stats */}
      <div className="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
              Conflict Resolution ({filteredConflicts.length})
            </h2>
            <div className="flex items-center gap-4 mt-2 text-sm text-gray-600 dark:text-gray-400">
              <span className="flex items-center gap-1">
                <div className="w-2 h-2 bg-red-500 rounded-full" />
                Critical: {severityCounts.critical || 0}
              </span>
              <span className="flex items-center gap-1">
                <div className="w-2 h-2 bg-orange-500 rounded-full" />
                High: {severityCounts.high || 0}
              </span>
              <span className="flex items-center gap-1">
                <div className="w-2 h-2 bg-yellow-500 rounded-full" />
                Medium: {severityCounts.medium || 0}
              </span>
              <span className="flex items-center gap-1">
                <div className="w-2 h-2 bg-blue-500 rounded-full" />
                Low: {severityCounts.low || 0}
              </span>
            </div>
          </div>

          {enableBatchResolution && (
            <div className="flex items-center gap-2">
              {selectedConflicts.size > 0 && (
                <span className="text-sm text-gray-600 dark:text-gray-400">
                  {selectedConflicts.size} selected
                </span>
              )}
              <InteractiveButton
                variant="ghost"
                size="sm"
                onClick={handleSelectAll}
              >
                {selectedConflicts.size === filteredConflicts.length ? 'Deselect All' : 'Select All'}
              </InteractiveButton>
              {selectedConflicts.size > 0 && (
                <>
                  <InteractiveButton
                    variant="secondary"
                    size="sm"
                    onClick={() => handleBatchResolve('accept-theirs')}
                  >
                    Accept Theirs ({selectedConflicts.size})
                  </InteractiveButton>
                  <InteractiveButton
                    variant="secondary"
                    size="sm"
                    onClick={() => handleBatchResolve('accept-mine')}
                  >
                    Accept Mine ({selectedConflicts.size})
                  </InteractiveButton>
                  {enableAutoMerge && (
                    <InteractiveButton
                      variant="primary"
                      size="sm"
                      onClick={() => handleBatchResolve('merge')}
                    >
                      Auto Merge ({selectedConflicts.size})
                    </InteractiveButton>
                  )}
                </>
              )}
            </div>
          )}
        </div>

        {/* Filters */}
        <div className="flex items-center gap-4 mt-4 pt-4 border-t border-gray-200 dark:border-gray-700">
          <select
            value={filter.severity || ''}
            onChange={(e) => setFilter({ ...filter, severity: e.target.value as ConflictSeverity | undefined })}
            className="px-3 py-1 text-sm border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
          >
            <option value="">All Severities</option>
            <option value="critical">Critical</option>
            <option value="high">High</option>
            <option value="medium">Medium</option>
            <option value="low">Low</option>
          </select>

          <select
            value={filter.type || ''}
            onChange={(e) => setFilter({ ...filter, type: e.target.value as ConflictType | undefined })}
            className="px-3 py-1 text-sm border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
          >
            <option value="">All Types</option>
            <option value="edit">Edit</option>
            <option value="delete">Delete</option>
            <option value="rename">Rename</option>
            <option value="permission">Permission</option>
            <option value="format">Format</option>
          </select>
        </div>
      </div>

      {/* Conflict list */}
      <div className="space-y-3">
        {filteredConflicts.map(conflict => (
          <ConflictCard
            key={conflict.id}
            conflict={conflict}
            onResolve={(action, resolution) => handleResolveConflict(conflict.id, action, resolution)}
            onPostpone={() => onPostponeConflict(conflict.id)}
            onRequestUserInfo={onRequestUserInfo}
            showDiff={showDiffView}
          />
        ))}
      </div>
    </div>
  );
};

export default EnhancedConflictResolver;
