/**
 * IncidentTimeline Component
 *
 * @description Displays incident activity timeline with events, actions,
 * status changes, and communications for incident detail view.
 *
 * @doc.type component
 * @doc.purpose Incident history tracking
 * @doc.layer presentation
 * @doc.phase 4
 */

import React, { useMemo } from 'react';
import { cn } from '@ghatana/ui';

// ============================================================================
// Types
// ============================================================================

export type TimelineEventType =
  | 'created'
  | 'status_change'
  | 'severity_change'
  | 'assignee_change'
  | 'responder_joined'
  | 'responder_left'
  | 'comment'
  | 'action_taken'
  | 'alert_linked'
  | 'runbook_executed'
  | 'resolved'
  | 'postmortem_created';

export interface TimelineActor {
  id: string;
  name: string;
  avatar?: string;
  role?: string;
}

export interface TimelineEvent {
  id: string;
  type: TimelineEventType;
  timestamp: string;
  actor?: TimelineActor;
  title: string;
  description?: string;
  metadata?: {
    oldValue?: string;
    newValue?: string;
    alertId?: string;
    runbookId?: string;
    actionType?: string;
  };
}

export interface IncidentTimelineProps {
  events: TimelineEvent[];
  onEventClick?: (event: TimelineEvent) => void;
  onAddComment?: () => void;
  isLoading?: boolean;
  showRelativeTime?: boolean;
  className?: string;
}

// ============================================================================
// Utility Functions
// ============================================================================

const getEventTypeConfig = (type: TimelineEventType) => {
  const configs: Record<TimelineEventType, { icon: string; color: string; bg: string }> = {
    created: { icon: '🆕', color: '#3B82F6', bg: 'rgba(59, 130, 246, 0.1)' },
    status_change: { icon: '🔄', color: '#8B5CF6', bg: 'rgba(139, 92, 246, 0.1)' },
    severity_change: { icon: '⚠️', color: '#F59E0B', bg: 'rgba(245, 158, 11, 0.1)' },
    assignee_change: { icon: '👤', color: '#06B6D4', bg: 'rgba(6, 182, 212, 0.1)' },
    responder_joined: { icon: '➕', color: '#10B981', bg: 'rgba(16, 185, 129, 0.1)' },
    responder_left: { icon: '➖', color: '#6B7280', bg: 'rgba(107, 114, 128, 0.1)' },
    comment: { icon: '💬', color: '#3B82F6', bg: 'rgba(59, 130, 246, 0.1)' },
    action_taken: { icon: '⚡', color: '#F59E0B', bg: 'rgba(245, 158, 11, 0.1)' },
    alert_linked: { icon: '🔗', color: '#EC4899', bg: 'rgba(236, 72, 153, 0.1)' },
    runbook_executed: { icon: '📋', color: '#8B5CF6', bg: 'rgba(139, 92, 246, 0.1)' },
    resolved: { icon: '✅', color: '#10B981', bg: 'rgba(16, 185, 129, 0.1)' },
    postmortem_created: { icon: '📝', color: '#6366F1', bg: 'rgba(99, 102, 241, 0.1)' },
  };
  return configs[type];
};

const formatTime = (timestamp: string, relative: boolean): string => {
  const date = new Date(timestamp);
  
  if (relative) {
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMins / 60);

    if (diffMins < 1) return 'just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
  }

  return date.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
};

const groupEventsByDate = (events: TimelineEvent[]): Map<string, TimelineEvent[]> => {
  const groups = new Map<string, TimelineEvent[]>();

  events.forEach((event) => {
    const date = new Date(event.timestamp).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });

    if (!groups.has(date)) {
      groups.set(date, []);
    }
    groups.get(date)!.push(event);
  });

  return groups;
};

// ============================================================================
// Timeline Event Sub-component
// ============================================================================

interface TimelineEventItemProps {
  event: TimelineEvent;
  showRelativeTime: boolean;
  onClick?: () => void;
}

const TimelineEventItem: React.FC<TimelineEventItemProps> = ({
  event,
  showRelativeTime,
  onClick,
}) => {
  const config = getEventTypeConfig(event.type);

  return (
    <div
      className="timeline-event"
      onClick={onClick}
      onKeyDown={(e) => {
        if ((e.key === 'Enter' || e.key === ' ') && onClick) {
          e.preventDefault();
          onClick();
        }
      }}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
    >
      {/* Icon */}
      <div
        className="event-icon"
        style={{ backgroundColor: config.bg }}
      >
        <span>{config.icon}</span>
      </div>

      {/* Content */}
      <div className="event-content">
        {/* Header */}
        <div className="event-header">
          {event.actor && (
            <div className="event-actor">
              {event.actor.avatar ? (
                <img
                  src={event.actor.avatar}
                  alt={event.actor.name}
                  className="actor-avatar"
                />
              ) : (
                <div className="actor-avatar-placeholder">
                  {event.actor.name.charAt(0)}
                </div>
              )}
              <span className="actor-name">{event.actor.name}</span>
              {event.actor.role && (
                <span className="actor-role">{event.actor.role}</span>
              )}
            </div>
          )}
          <span className="event-time">
            {formatTime(event.timestamp, showRelativeTime)}
          </span>
        </div>

        {/* Title */}
        <p className="event-title">{event.title}</p>

        {/* Description */}
        {event.description && (
          <p className="event-description">{event.description}</p>
        )}

        {/* Metadata (status/severity changes) */}
        {event.metadata && (event.metadata.oldValue || event.metadata.newValue) && (
          <div className="event-change">
            {event.metadata.oldValue && (
              <span className="change-old">{event.metadata.oldValue}</span>
            )}
            {event.metadata.oldValue && event.metadata.newValue && (
              <span className="change-arrow">→</span>
            )}
            {event.metadata.newValue && (
              <span className="change-new">{event.metadata.newValue}</span>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const IncidentTimeline: React.FC<IncidentTimelineProps> = ({
  events,
  onEventClick,
  onAddComment,
  isLoading = false,
  showRelativeTime = true,
  className,
}) => {
  // Sort events by timestamp (newest first)
  const sortedEvents = useMemo(() => {
    return [...events].sort(
      (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
    );
  }, [events]);

  const groupedEvents = useMemo(() => groupEventsByDate(sortedEvents), [sortedEvents]);

  if (isLoading) {
    return (
      <div className={cn('incident-timeline incident-timeline--loading', className)}>
        <div className="timeline-loading">
          <div className="loading-spinner" />
          <span>Loading timeline...</span>
        </div>
      </div>
    );
  }

  return (
    <div className={cn('incident-timeline', className)}>
      {/* Header */}
      <div className="timeline-header">
        <h3 className="timeline-title">Activity Timeline</h3>
        {onAddComment && (
          <button
            type="button"
            className="add-comment-btn"
            onClick={onAddComment}
          >
            💬 Add Comment
          </button>
        )}
      </div>

      {/* Timeline */}
      <div className="timeline-content">
        {events.length === 0 ? (
          <div className="timeline-empty">
            <span className="empty-icon">📋</span>
            <span className="empty-text">No activity yet</span>
          </div>
        ) : (
          Array.from(groupedEvents.entries()).map(([date, dateEvents]) => (
            <div key={date} className="timeline-date-group">
              <div className="date-header">
                <span className="date-label">{date}</span>
              </div>
              <div className="timeline-events">
                {dateEvents.map((event) => (
                  <TimelineEventItem
                    key={event.id}
                    event={event}
                    showRelativeTime={showRelativeTime}
                    onClick={onEventClick ? () => onEventClick(event) : undefined}
                  />
                ))}
              </div>
            </div>
          ))
        )}
      </div>

      <style>{`
        .incident-timeline {
          display: flex;
          flex-direction: column;
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 12px;
          overflow: hidden;
        }

        .incident-timeline--loading {
          min-height: 200px;
          justify-content: center;
          align-items: center;
        }

        .timeline-loading {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 0.75rem;
          color: #6B7280;
        }

        .loading-spinner {
          width: 24px;
          height: 24px;
          border: 2px solid #E5E7EB;
          border-top-color: #3B82F6;
          border-radius: 50%;
          animation: spin 0.8s linear infinite;
        }

        @keyframes spin {
          to { transform: rotate(360deg); }
        }

        .timeline-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding: 1rem;
          border-bottom: 1px solid #E5E7EB;
        }

        .timeline-title {
          margin: 0;
          font-size: 0.9375rem;
          font-weight: 600;
          color: #111827;
        }

        .add-comment-btn {
          font-size: 0.8125rem;
          font-weight: 500;
          padding: 0.375rem 0.75rem;
          background: #F3F4F6;
          border: 1px solid #E5E7EB;
          border-radius: 6px;
          color: #374151;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .add-comment-btn:hover {
          background: #E5E7EB;
        }

        .timeline-content {
          flex: 1;
          padding: 1rem;
          overflow-y: auto;
        }

        .timeline-empty {
          display: flex;
          flex-direction: column;
          align-items: center;
          padding: 2rem;
          color: #9CA3AF;
        }

        .empty-icon {
          font-size: 2rem;
          margin-bottom: 0.5rem;
        }

        .timeline-date-group {
          margin-bottom: 1.5rem;
        }

        .timeline-date-group:last-child {
          margin-bottom: 0;
        }

        .date-header {
          margin-bottom: 0.75rem;
        }

        .date-label {
          font-size: 0.75rem;
          font-weight: 600;
          color: #6B7280;
          text-transform: uppercase;
          letter-spacing: 0.025em;
        }

        .timeline-events {
          display: flex;
          flex-direction: column;
          gap: 0.75rem;
          padding-left: 0.5rem;
          border-left: 2px solid #E5E7EB;
        }

        .timeline-event {
          display: flex;
          gap: 0.75rem;
          padding: 0.5rem;
          margin-left: -0.5rem;
          border-radius: 8px;
          transition: background 0.15s ease;
        }

        .timeline-event:hover {
          background: #F9FAFB;
        }

        .event-icon {
          width: 32px;
          height: 32px;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          flex-shrink: 0;
          font-size: 0.875rem;
        }

        .event-content {
          flex: 1;
          min-width: 0;
        }

        .event-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 0.5rem;
          margin-bottom: 0.25rem;
        }

        .event-actor {
          display: flex;
          align-items: center;
          gap: 0.375rem;
        }

        .actor-avatar {
          width: 20px;
          height: 20px;
          border-radius: 50%;
          object-fit: cover;
        }

        .actor-avatar-placeholder {
          width: 20px;
          height: 20px;
          border-radius: 50%;
          background: #E5E7EB;
          color: #6B7280;
          font-size: 0.625rem;
          font-weight: 600;
          display: flex;
          align-items: center;
          justify-content: center;
        }

        .actor-name {
          font-size: 0.8125rem;
          font-weight: 500;
          color: #111827;
        }

        .actor-role {
          font-size: 0.6875rem;
          color: #9CA3AF;
          padding: 0.125rem 0.375rem;
          background: #F3F4F6;
          border-radius: 4px;
        }

        .event-time {
          font-size: 0.75rem;
          color: #9CA3AF;
          flex-shrink: 0;
        }

        .event-title {
          margin: 0;
          font-size: 0.8125rem;
          color: #374151;
          line-height: 1.4;
        }

        .event-description {
          margin: 0.25rem 0 0 0;
          font-size: 0.75rem;
          color: #6B7280;
          line-height: 1.4;
        }

        .event-change {
          display: flex;
          align-items: center;
          gap: 0.375rem;
          margin-top: 0.375rem;
          font-size: 0.75rem;
        }

        .change-old {
          color: #EF4444;
          text-decoration: line-through;
        }

        .change-arrow {
          color: #9CA3AF;
        }

        .change-new {
          color: #10B981;
          font-weight: 500;
        }

        @media (prefers-color-scheme: dark) {
          .incident-timeline {
            background: #1F2937;
            border-color: #374151;
          }

          .timeline-header {
            border-bottom-color: #374151;
          }

          .timeline-title {
            color: #F9FAFB;
          }

          .add-comment-btn {
            background: #374151;
            border-color: #4B5563;
            color: #D1D5DB;
          }

          .add-comment-btn:hover {
            background: #4B5563;
          }

          .date-label {
            color: #9CA3AF;
          }

          .timeline-events {
            border-left-color: #374151;
          }

          .timeline-event:hover {
            background: #111827;
          }

          .actor-avatar-placeholder {
            background: #374151;
            color: #9CA3AF;
          }

          .actor-name {
            color: #F9FAFB;
          }

          .actor-role {
            background: #374151;
            color: #9CA3AF;
          }

          .event-title {
            color: #E5E7EB;
          }

          .event-description {
            color: #9CA3AF;
          }
        }
      `}</style>
    </div>
  );
};

IncidentTimeline.displayName = 'IncidentTimeline';

export default IncidentTimeline;
