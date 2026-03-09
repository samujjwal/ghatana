/**
 * SprintColumn Component
 *
 * @description Kanban board column wrapper that groups stories by status.
 * Handles drag-and-drop target functionality and displays column header.
 *
 * @doc.phase 3
 * @doc.component SprintColumn
 */

import React, { useState, useCallback, useMemo } from 'react';
import type { Story, StoryStatus } from './StoryCard';

// ============================================================================
// Types
// ============================================================================

export interface SprintColumnProps {
  id: string;
  title: string;
  status: StoryStatus;
  stories: Story[];
  wipLimit?: number;
  isDropTarget?: boolean;
  onDrop?: (storyId: string, targetStatus: StoryStatus) => void;
  onStoryClick?: (story: Story) => void;
  children: React.ReactNode;
}

// ============================================================================
// Constants
// ============================================================================

const COLUMN_COLORS: Record<StoryStatus, { header: string; bg: string }> = {
  backlog: { header: '#6B7280', bg: '#F3F4F6' },
  todo: { header: '#3B82F6', bg: '#EFF6FF' },
  'in-progress': { header: '#F59E0B', bg: '#FFFBEB' },
  review: { header: '#8B5CF6', bg: '#F5F3FF' },
  done: { header: '#10B981', bg: '#ECFDF5' },
};

// ============================================================================
// Main Component
// ============================================================================

export const SprintColumn: React.FC<SprintColumnProps> = ({
  id,
  title,
  status,
  stories,
  wipLimit,
  isDropTarget = false,
  onDrop,
  children,
}) => {
  const [isDragOver, setIsDragOver] = useState(false);

  const colors = useMemo(() => COLUMN_COLORS[status], [status]);
  const storyCount = stories.length;
  const isOverWipLimit = wipLimit !== undefined && storyCount > wipLimit;
  const totalPoints = useMemo(
    () => stories.reduce((sum, s) => sum + s.storyPoints, 0),
    [stories]
  );

  // Drag and drop handlers
  const handleDragOver = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.dataTransfer.dropEffect = 'move';
      if (!isDragOver) {
        setIsDragOver(true);
      }
    },
    [isDragOver]
  );

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    // Only handle if leaving the column, not child elements
    const relatedTarget = e.relatedTarget as HTMLElement;
    if (!e.currentTarget.contains(relatedTarget)) {
      setIsDragOver(false);
    }
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setIsDragOver(false);

      const storyId = e.dataTransfer.getData('text/plain');
      if (storyId && onDrop) {
        onDrop(storyId, status);
      }
    },
    [onDrop, status]
  );

  return (
    <div
      className={`sprint-column ${isDragOver ? 'sprint-column--drag-over' : ''} ${
        isDropTarget ? 'sprint-column--drop-target' : ''
      } ${isOverWipLimit ? 'sprint-column--over-limit' : ''}`}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      data-status={status}
      role="region"
      aria-label={`${title} column with ${storyCount} stories`}
    >
      {/* Column Header */}
      <div className="column-header" style={{ borderBottomColor: colors.header }}>
        <div className="column-title-row">
          <h3 className="column-title" style={{ color: colors.header }}>
            {title}
          </h3>
          <span
            className={`column-count ${isOverWipLimit ? 'column-count--over' : ''}`}
          >
            {storyCount}
            {wipLimit !== undefined && `/${wipLimit}`}
          </span>
        </div>
        <div className="column-meta">
          <span className="column-points">{totalPoints} pts</span>
        </div>
      </div>

      {/* Column Body */}
      <div
        className="column-body"
        style={{ background: isDragOver ? colors.bg : undefined }}
      >
        {children}
      </div>

      {/* Drop Indicator */}
      {isDragOver && (
        <div className="drop-indicator">
          <span className="drop-indicator-text">Drop here</span>
        </div>
      )}

      {/* CSS-in-JS Styles */}
      <style>{`
        .sprint-column {
          display: flex;
          flex-direction: column;
          min-width: 280px;
          max-width: 320px;
          background: #F9FAFB;
          border-radius: 12px;
          overflow: hidden;
          position: relative;
          transition: all 0.2s ease;
        }

        .sprint-column--drag-over {
          background: #EFF6FF;
          box-shadow: inset 0 0 0 2px #3B82F6;
        }

        .sprint-column--drop-target {
          outline: 2px dashed #3B82F6;
          outline-offset: -2px;
        }

        .sprint-column--over-limit {
          background: #FEF2F2;
        }

        .column-header {
          padding: 1rem;
          background: #fff;
          border-bottom: 3px solid;
        }

        .column-title-row {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 0.5rem;
        }

        .column-title {
          margin: 0;
          font-size: 0.875rem;
          font-weight: 600;
          text-transform: uppercase;
          letter-spacing: 0.05em;
        }

        .column-count {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          min-width: 24px;
          height: 24px;
          padding: 0 0.5rem;
          background: #E5E7EB;
          border-radius: 12px;
          font-size: 0.75rem;
          font-weight: 600;
          color: #374151;
        }

        .column-count--over {
          background: #FEE2E2;
          color: #DC2626;
        }

        .column-meta {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          margin-top: 0.25rem;
        }

        .column-points {
          font-size: 0.75rem;
          color: #6B7280;
        }

        .column-body {
          flex: 1;
          padding: 0.75rem;
          display: flex;
          flex-direction: column;
          gap: 0.75rem;
          overflow-y: auto;
          min-height: 200px;
          transition: background 0.15s ease;
        }

        .drop-indicator {
          position: absolute;
          bottom: 0.75rem;
          left: 0.75rem;
          right: 0.75rem;
          padding: 0.75rem;
          background: #EFF6FF;
          border: 2px dashed #3B82F6;
          border-radius: 8px;
          text-align: center;
          pointer-events: none;
          animation: pulse-border 1s infinite;
        }

        @keyframes pulse-border {
          0%, 100% { border-color: #3B82F6; }
          50% { border-color: #93C5FD; }
        }

        .drop-indicator-text {
          font-size: 0.75rem;
          font-weight: 500;
          color: #3B82F6;
        }

        /* Scrollbar */
        .column-body::-webkit-scrollbar {
          width: 4px;
        }

        .column-body::-webkit-scrollbar-track {
          background: transparent;
        }

        .column-body::-webkit-scrollbar-thumb {
          background: #D1D5DB;
          border-radius: 2px;
        }

        .column-body::-webkit-scrollbar-thumb:hover {
          background: #9CA3AF;
        }
      `}</style>
    </div>
  );
};

SprintColumn.displayName = 'SprintColumn';

export default SprintColumn;
