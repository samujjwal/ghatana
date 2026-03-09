/**
 * RetroItem Component
 *
 * @description Card component for retrospective items (went well, could improve, action items).
 * Supports voting and comments.
 *
 * @doc.phase 3
 * @doc.component RetroItem
 */

import React, { useState, useCallback } from 'react';

// ============================================================================
// Types
// ============================================================================

export type RetroItemCategory = 'went-well' | 'could-improve' | 'action-item';

export interface RetroVoter {
  id: string;
  name: string;
  avatar?: string;
}

export interface RetroItemData {
  id: string;
  category: RetroItemCategory;
  content: string;
  author: {
    id: string;
    name: string;
    avatar?: string;
  };
  votes: number;
  voters: RetroVoter[];
  createdAt: Date;
  assignee?: {
    id: string;
    name: string;
  };
  dueDate?: Date;
  completed?: boolean;
}

export interface RetroItemProps {
  item: RetroItemData;
  currentUserId?: string;
  onVote?: (itemId: string) => void;
  onEdit?: (itemId: string, content: string) => void;
  onDelete?: (itemId: string) => void;
  onToggleComplete?: (itemId: string) => void;
  readOnly?: boolean;
}

// ============================================================================
// Constants
// ============================================================================

const CATEGORY_CONFIG: Record<
  RetroItemCategory,
  { icon: string; color: string; bgColor: string; label: string }
> = {
  'went-well': {
    icon: '✨',
    color: '#059669',
    bgColor: '#ECFDF5',
    label: 'Went Well',
  },
  'could-improve': {
    icon: '⚠️',
    color: '#D97706',
    bgColor: '#FFFBEB',
    label: 'Could Improve',
  },
  'action-item': {
    icon: '🎯',
    color: '#2563EB',
    bgColor: '#EFF6FF',
    label: 'Action Item',
  },
};

// ============================================================================
// Main Component
// ============================================================================

export const RetroItem: React.FC<RetroItemProps> = ({
  item,
  currentUserId,
  onVote,
  onEdit,
  onDelete,
  onToggleComplete,
  readOnly = false,
}) => {
  const [isEditing, setIsEditing] = useState(false);
  const [editContent, setEditContent] = useState(item.content);

  const config = CATEGORY_CONFIG[item.category];
  const hasVoted =
    currentUserId && item.voters.some((v) => v.id === currentUserId);
  const isAuthor = currentUserId === item.author.id;
  const isActionItem = item.category === 'action-item';

  // Format relative time
  const formatTime = (date: Date): string => {
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return 'Just now';
    if (minutes < 60) return `${minutes}m ago`;
    if (hours < 24) return `${hours}h ago`;
    return `${days}d ago`;
  };

  // Format due date
  const formatDueDate = (date: Date): string => {
    return date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
    });
  };

  // Handlers
  const handleVote = useCallback(() => {
    if (!readOnly && onVote) {
      onVote(item.id);
    }
  }, [readOnly, onVote, item.id]);

  const handleEdit = useCallback(() => {
    setIsEditing(true);
  }, []);

  const handleSaveEdit = useCallback(() => {
    if (onEdit && editContent.trim()) {
      onEdit(item.id, editContent.trim());
    }
    setIsEditing(false);
  }, [onEdit, item.id, editContent]);

  const handleCancelEdit = useCallback(() => {
    setEditContent(item.content);
    setIsEditing(false);
  }, [item.content]);

  const handleDelete = useCallback(() => {
    if (onDelete) {
      onDelete(item.id);
    }
  }, [onDelete, item.id]);

  const handleToggleComplete = useCallback(() => {
    if (onToggleComplete) {
      onToggleComplete(item.id);
    }
  }, [onToggleComplete, item.id]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSaveEdit();
      }
      if (e.key === 'Escape') {
        handleCancelEdit();
      }
    },
    [handleSaveEdit, handleCancelEdit]
  );

  return (
    <div
      className={`retro-item ${item.completed ? 'retro-item--completed' : ''}`}
      style={{ borderLeftColor: config.color }}
    >
      {/* Header */}
      <div className="item-header">
        <span className="item-icon">{config.icon}</span>
        <span className="item-time">{formatTime(item.createdAt)}</span>
        {isAuthor && !readOnly && (
          <div className="item-actions">
            <button
              type="button"
              className="action-btn"
              onClick={handleEdit}
              title="Edit"
              aria-label="Edit item"
            >
              ✏️
            </button>
            <button
              type="button"
              className="action-btn action-btn--danger"
              onClick={handleDelete}
              title="Delete"
              aria-label="Delete item"
            >
              🗑️
            </button>
          </div>
        )}
      </div>

      {/* Content */}
      {isEditing ? (
        <div className="edit-container">
          <textarea
            className="edit-textarea"
            value={editContent}
            onChange={(e) => setEditContent(e.target.value)}
            onKeyDown={handleKeyDown}
            autoFocus
            rows={3}
          />
          <div className="edit-actions">
            <button
              type="button"
              className="edit-btn edit-btn--cancel"
              onClick={handleCancelEdit}
            >
              Cancel
            </button>
            <button
              type="button"
              className="edit-btn edit-btn--save"
              onClick={handleSaveEdit}
            >
              Save
            </button>
          </div>
        </div>
      ) : (
        <p className="item-content">{item.content}</p>
      )}

      {/* Action Item Specific */}
      {isActionItem && (
        <div className="action-item-meta">
          {item.assignee && (
            <span className="assignee">
              👤 {item.assignee.name}
            </span>
          )}
          {item.dueDate && (
            <span className="due-date">
              📅 {formatDueDate(item.dueDate)}
            </span>
          )}
          {!readOnly && (
            <button
              type="button"
              className={`complete-checkbox ${
                item.completed ? 'complete-checkbox--checked' : ''
              }`}
              onClick={handleToggleComplete}
              aria-label={item.completed ? 'Mark incomplete' : 'Mark complete'}
            >
              {item.completed ? '✓' : '○'}
            </button>
          )}
        </div>
      )}

      {/* Footer with Votes */}
      <div className="item-footer">
        <button
          type="button"
          className={`vote-btn ${hasVoted ? 'vote-btn--voted' : ''}`}
          onClick={handleVote}
          disabled={readOnly}
          aria-label={`${item.votes} votes. Click to ${hasVoted ? 'remove' : 'add'} vote`}
        >
          <span className="vote-icon">👍</span>
          <span className="vote-count">{item.votes}</span>
        </button>

        {item.voters.length > 0 && (
          <div className="voters">
            {item.voters.slice(0, 3).map((voter) => (
              <span key={voter.id} className="voter" title={voter.name}>
                {voter.avatar ? (
                  <img src={voter.avatar} alt={voter.name} />
                ) : (
                  voter.name.charAt(0)
                )}
              </span>
            ))}
            {item.voters.length > 3 && (
              <span className="voters-more">+{item.voters.length - 3}</span>
            )}
          </div>
        )}

        <span className="author">by {item.author.name}</span>
      </div>

      {/* CSS-in-JS Styles */}
      <style>{`
        .retro-item {
          background: #fff;
          border: 1px solid #E5E7EB;
          border-left-width: 4px;
          border-radius: 8px;
          padding: 1rem;
          transition: all 0.15s ease;
        }

        .retro-item:hover {
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
        }

        .retro-item--completed {
          opacity: 0.6;
        }

        .retro-item--completed .item-content {
          text-decoration: line-through;
        }

        .item-header {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          margin-bottom: 0.5rem;
        }

        .item-icon {
          font-size: 1rem;
        }

        .item-time {
          font-size: 0.75rem;
          color: #6B7280;
        }

        .item-actions {
          margin-left: auto;
          display: flex;
          gap: 0.25rem;
          opacity: 0;
          transition: opacity 0.15s ease;
        }

        .retro-item:hover .item-actions {
          opacity: 1;
        }

        .action-btn {
          padding: 0.25rem;
          background: transparent;
          border: none;
          cursor: pointer;
          font-size: 0.75rem;
          opacity: 0.6;
          transition: opacity 0.15s ease;
        }

        .action-btn:hover {
          opacity: 1;
        }

        .action-btn--danger:hover {
          color: #DC2626;
        }

        .item-content {
          margin: 0;
          font-size: 0.875rem;
          color: #374151;
          line-height: 1.5;
        }

        .edit-container {
          margin: 0.5rem 0;
        }

        .edit-textarea {
          width: 100%;
          padding: 0.5rem;
          border: 1px solid #D1D5DB;
          border-radius: 6px;
          font-size: 0.875rem;
          font-family: inherit;
          resize: vertical;
          min-height: 60px;
        }

        .edit-textarea:focus {
          outline: none;
          border-color: #3B82F6;
          box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
        }

        .edit-actions {
          display: flex;
          justify-content: flex-end;
          gap: 0.5rem;
          margin-top: 0.5rem;
        }

        .edit-btn {
          padding: 0.375rem 0.75rem;
          font-size: 0.75rem;
          font-weight: 500;
          border-radius: 4px;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .edit-btn--cancel {
          background: #F3F4F6;
          border: 1px solid #D1D5DB;
          color: #374151;
        }

        .edit-btn--cancel:hover {
          background: #E5E7EB;
        }

        .edit-btn--save {
          background: #3B82F6;
          border: none;
          color: #fff;
        }

        .edit-btn--save:hover {
          background: #2563EB;
        }

        .action-item-meta {
          display: flex;
          align-items: center;
          gap: 1rem;
          margin-top: 0.75rem;
          padding-top: 0.75rem;
          border-top: 1px dashed #E5E7EB;
        }

        .assignee,
        .due-date {
          font-size: 0.75rem;
          color: #6B7280;
        }

        .complete-checkbox {
          margin-left: auto;
          width: 24px;
          height: 24px;
          border-radius: 50%;
          border: 2px solid #D1D5DB;
          background: transparent;
          cursor: pointer;
          font-size: 0.875rem;
          color: #D1D5DB;
          transition: all 0.15s ease;
        }

        .complete-checkbox:hover {
          border-color: #10B981;
          color: #10B981;
        }

        .complete-checkbox--checked {
          background: #10B981;
          border-color: #10B981;
          color: #fff;
        }

        .item-footer {
          display: flex;
          align-items: center;
          gap: 0.75rem;
          margin-top: 0.75rem;
          padding-top: 0.75rem;
          border-top: 1px solid #F3F4F6;
        }

        .vote-btn {
          display: inline-flex;
          align-items: center;
          gap: 0.375rem;
          padding: 0.25rem 0.5rem;
          background: #F3F4F6;
          border: 1px solid #E5E7EB;
          border-radius: 9999px;
          cursor: pointer;
          font-size: 0.75rem;
          transition: all 0.15s ease;
        }

        .vote-btn:hover:not(:disabled) {
          background: #EFF6FF;
          border-color: #3B82F6;
        }

        .vote-btn:disabled {
          cursor: not-allowed;
          opacity: 0.6;
        }

        .vote-btn--voted {
          background: #EFF6FF;
          border-color: #3B82F6;
        }

        .vote-icon {
          font-size: 0.875rem;
        }

        .vote-count {
          font-weight: 600;
          color: #374151;
        }

        .voters {
          display: flex;
          align-items: center;
        }

        .voter {
          width: 20px;
          height: 20px;
          border-radius: 50%;
          background: #E5E7EB;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 0.625rem;
          font-weight: 500;
          color: #374151;
          margin-left: -4px;
          border: 1px solid #fff;
          overflow: hidden;
        }

        .voter:first-child {
          margin-left: 0;
        }

        .voter img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }

        .voters-more {
          margin-left: 0.25rem;
          font-size: 0.625rem;
          color: #6B7280;
        }

        .author {
          margin-left: auto;
          font-size: 0.625rem;
          color: #9CA3AF;
        }
      `}</style>
    </div>
  );
};

RetroItem.displayName = 'RetroItem';

export default RetroItem;
