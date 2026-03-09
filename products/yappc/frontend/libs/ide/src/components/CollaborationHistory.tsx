/**
 * @ghatana/yappc-ide - Collaboration History Component
 * 
 * History and session management for collaborative editing
 * with change tracking, version control, and activity timeline.
 * 
 * @doc.type component
 * @doc.purpose Collaboration history and session management
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback, useMemo } from 'react';

/**
 * History event types
 */
export type HistoryEventType = 
  | 'user-joined'
  | 'user-left'
  | 'file-created'
  | 'file-deleted'
  | 'file-renamed'
  | 'content-changed'
  | 'conflict-resolved'
  | 'session-started'
  | 'session-ended';

/**
 * History event entry
 */
export interface HistoryEvent {
  id: string;
  type: HistoryEventType;
  timestamp: number;
  userId: string;
  userName: string;
  userColor: string;
  data: {
    fileId?: string;
    fileName?: string;
    oldName?: string;
    description?: string;
    changes?: number;
    conflictId?: string;
  };
}

/**
 * Session information
 */
export interface CollaborationSession {
  id: string;
  startTime: number;
  endTime?: number;
  participants: string[];
  totalChanges: number;
  conflictsResolved: number;
  filesCreated: number;
  filesDeleted: number;
}

/**
 * Collaboration history props
 */
export interface CollaborationHistoryProps {
  workspaceId?: string;
  showSessions?: boolean;
  showEvents?: boolean;
  maxEvents?: number;
  className?: string;
}

/**
 * Event icon component
 */
interface EventIconProps {
  type: HistoryEventType;
  userColor: string;
}

const EventIcon: React.FC<EventIconProps> = ({ type, userColor }) => {
  const getIcon = useCallback(() => {
    switch (type) {
      case 'user-joined':
        return '👋';
      case 'user-left':
        return '👋';
      case 'file-created':
        return '📄';
      case 'file-deleted':
        return '🗑️';
      case 'file-renamed':
        return '✏️';
      case 'content-changed':
        return '📝';
      case 'conflict-resolved':
        return '✅';
      case 'session-started':
        return '🚀';
      case 'session-ended':
        return '🏁';
      default:
        return '📌';
    }
  }, [type]);

  return (
    <div
      className="w-8 h-8 rounded-full flex items-center justify-center text-sm"
      style={{ backgroundColor: `${userColor}20`, color: userColor }}
    >
      {getIcon()}
    </div>
  );
};

/**
 * History event item
 */
interface HistoryEventItemProps {
  event: HistoryEvent;
  showUser?: boolean;
  showTime?: boolean;
}

const HistoryEventItem: React.FC<HistoryEventItemProps> = ({
  event,
  showUser = true,
  showTime = true,
}) => {
  const getEventDescription = useCallback(() => {
    switch (event.type) {
      case 'user-joined':
        return 'joined the session';
      case 'user-left':
        return 'left the session';
      case 'file-created':
        return `created ${event.data.fileName || 'a file'}`;
      case 'file-deleted':
        return `deleted ${event.data.fileName || 'a file'}`;
      case 'file-renamed':
        return `renamed ${event.data.oldName || 'a file'} to ${event.data.fileName || 'new name'}`;
      case 'content-changed':
        return `made ${event.data.changes || 1} change${event.data.changes !== 1 ? 's' : ''} to ${event.data.fileName || 'a file'}`;
      case 'conflict-resolved':
        return 'resolved a conflict';
      case 'session-started':
        return 'started the session';
      case 'session-ended':
        return 'ended the session';
      default:
        return event.data.description || 'performed an action';
    }
  }, [event]);

  const formatTime = useCallback((timestamp: number) => {
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now.getTime() - date.getTime();

    if (diff < 60000) {
      return 'just now';
    } else if (diff < 3600000) {
      return `${Math.floor(diff / 60000)}m ago`;
    } else if (diff < 86400000) {
      return `${Math.floor(diff / 3600000)}h ago`;
    } else {
      return date.toLocaleDateString();
    }
  }, []);

  return (
    <div className="flex items-start gap-3 p-3 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors">
      <EventIcon type={event.type} userColor={event.userColor} />
      
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          {showUser && (
            <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
              {event.userName}
            </span>
          )}
          <span className="text-sm text-gray-700 dark:text-gray-300">
            {getEventDescription()}
          </span>
        </div>
        {showTime && (
          <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">
            {formatTime(event.timestamp)}
          </div>
        )}
      </div>
    </div>
  );
};

/**
 * Session summary card
 */
interface SessionCardProps {
  session: CollaborationSession;
  onExpand?: () => void;
}

const SessionCard: React.FC<SessionCardProps> = ({ session, onExpand }) => {
  const formatDuration = useCallback((startTime: number, endTime?: number) => {
    const end = endTime || Date.now();
    const duration = end - startTime;
    
    if (duration < 60000) {
      return '< 1m';
    } else if (duration < 3600000) {
      return `${Math.floor(duration / 60000)}m`;
    } else {
      return `${Math.floor(duration / 3600000)}h ${Math.floor((duration % 3600000) / 60000)}m`;
    }
  }, []);

  const formatDate = useCallback((timestamp: number) => {
    return new Date(timestamp).toLocaleDateString();
  }, []);

  return (
    <div className="border border-gray-200 dark:border-gray-700 rounded-lg p-4 hover:shadow-md transition-shadow cursor-pointer">
      <div className="flex items-center justify-between mb-3">
        <div>
          <h3 className="text-sm font-medium text-gray-900 dark:text-gray-100">
            Session {formatDate(session.startTime)}
          </h3>
          <p className="text-xs text-gray-500 dark:text-gray-400">
            {formatDuration(session.startTime, session.endTime)} • {session.participants.length} participants
          </p>
        </div>
        <button
          onClick={onExpand}
          className="p-1 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>

      <div className="grid grid-cols-2 gap-4 text-xs">
        <div>
          <div className="text-gray-500 dark:text-gray-400">Changes</div>
          <div className="font-medium text-gray-900 dark:text-gray-100">{session.totalChanges}</div>
        </div>
        <div>
          <div className="text-gray-500 dark:text-gray-400">Conflicts</div>
          <div className="font-medium text-gray-900 dark:text-gray-100">{session.conflictsResolved}</div>
        </div>
        <div>
          <div className="text-gray-500 dark:text-gray-400">Files Created</div>
          <div className="font-medium text-gray-900 dark:text-gray-100">{session.filesCreated}</div>
        </div>
        <div>
          <div className="text-gray-500 dark:text-gray-400">Files Deleted</div>
          <div className="font-medium text-gray-900 dark:text-gray-100">{session.filesDeleted}</div>
        </div>
      </div>
    </div>
  );
};

/**
 * Collaboration History Component
 */
export const CollaborationHistory: React.FC<CollaborationHistoryProps> = ({
  workspaceId,
  showSessions = true,
  showEvents = true,
  maxEvents = 50,
  className = '',
}) => {
  const [activeTab, setActiveTab] = useState<'events' | 'sessions'>('events');
  const [expandedSession, setExpandedSession] = useState<string | null>(null);

  // Mock data - in real implementation, this would come from the collaboration service
  const mockEvents: HistoryEvent[] = useMemo(() => [
    {
      id: '1',
      type: 'session-started',
      timestamp: Date.now() - 3600000,
      userId: 'user1',
      userName: 'John Doe',
      userColor: '#3B82F6',
      data: { description: 'Started collaboration session' },
    },
    {
      id: '2',
      type: 'user-joined',
      timestamp: Date.now() - 3500000,
      userId: 'user2',
      userName: 'Jane Smith',
      userColor: '#10B981',
      data: { description: 'Joined the session' },
    },
    {
      id: '3',
      type: 'file-created',
      timestamp: Date.now() - 3400000,
      userId: 'user1',
      userName: 'John Doe',
      userColor: '#3B82F6',
      data: { fileName: 'src/components/NewComponent.tsx' },
    },
    {
      id: '4',
      type: 'content-changed',
      timestamp: Date.now() - 3300000,
      userId: 'user2',
      userName: 'Jane Smith',
      userColor: '#10B981',
      data: { fileName: 'src/utils/helpers.ts', changes: 5 },
    },
    {
      id: '5',
      type: 'conflict-resolved',
      timestamp: Date.now() - 3200000,
      userId: 'user1',
      userName: 'John Doe',
      userColor: '#3B82F6',
      data: { conflictId: 'conflict-1' },
    },
  ], []);

  const mockSessions: CollaborationSession[] = useMemo(() => [
    {
      id: 'session-1',
      startTime: Date.now() - 3600000,
      endTime: Date.now() - 1800000,
      participants: ['John Doe', 'Jane Smith', 'Bob Wilson'],
      totalChanges: 47,
      conflictsResolved: 3,
      filesCreated: 5,
      filesDeleted: 1,
    },
    {
      id: 'session-2',
      startTime: Date.now() - 86400000,
      endTime: Date.now() - 82800000,
      participants: ['John Doe', 'Alice Brown'],
      totalChanges: 23,
      conflictsResolved: 1,
      filesCreated: 2,
      filesDeleted: 0,
    },
  ], []);

  const events = mockEvents.slice(0, maxEvents);
  const sessions = mockSessions;

  const handleExportHistory = useCallback(() => {
    const historyData = {
      events,
      sessions,
      exportedAt: Date.now(),
      workspaceId,
    };

    const blob = new Blob([JSON.stringify(historyData, null, 2)], {
      type: 'application/json',
    });

    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `collaboration-history-${new Date().toISOString().split('T')[0]}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }, [events, sessions, workspaceId]);

  const handleClearHistory = useCallback(() => {
    // In real implementation, this would clear the history
    console.log('Clear history clicked');
  }, []);

  return (
    <div className={`bg-white dark:bg-gray-900 rounded-lg shadow-lg ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-gray-200 dark:border-gray-700">
        <div>
          <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
            Collaboration History
          </h3>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            Track changes and sessions over time
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleExportHistory}
            className="p-2 text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 transition-colors"
            title="Export history"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
          </button>
          <button
            onClick={handleClearHistory}
            className="p-2 text-red-500 hover:text-red-700 dark:hover:text-red-400 transition-colors"
            title="Clear history"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-gray-200 dark:border-gray-700">
        {showEvents && (
          <button
            onClick={() => setActiveTab('events')}
            className={`
              px-4 py-2 text-sm font-medium transition-colors
              ${activeTab === 'events'
                ? 'text-blue-600 dark:text-blue-400 border-b-2 border-blue-600 dark:border-blue-400'
                : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
              }
            `}
          >
            Events ({events.length})
          </button>
        )}
        {showSessions && (
          <button
            onClick={() => setActiveTab('sessions')}
            className={`
              px-4 py-2 text-sm font-medium transition-colors
              ${activeTab === 'sessions'
                ? 'text-blue-600 dark:text-blue-400 border-b-2 border-blue-600 dark:border-blue-400'
                : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
              }
            `}
          >
            Sessions ({sessions.length})
          </button>
        )}
      </div>

      {/* Content */}
      <div className="max-h-96 overflow-y-auto">
        {/* Events */}
        {activeTab === 'events' && showEvents && (
          <div>
            {events.length === 0 ? (
              <div className="p-8 text-center text-gray-500 dark:text-gray-400">
                <div className="text-2xl mb-2">📝</div>
                <p>No events recorded yet</p>
              </div>
            ) : (
              <div className="divide-y divide-gray-200 dark:divide-gray-700">
                {events.map(event => (
                  <HistoryEventItem
                    key={event.id}
                    event={event}
                  />
                ))}
              </div>
            )}
          </div>
        )}

        {/* Sessions */}
        {activeTab === 'sessions' && showSessions && (
          <div className="p-4 space-y-3">
            {sessions.length === 0 ? (
              <div className="text-center text-gray-500 dark:text-gray-400 py-8">
                <div className="text-2xl mb-2">🚀</div>
                <p>No sessions recorded yet</p>
              </div>
            ) : (
              sessions.map(session => (
                <SessionCard
                  key={session.id}
                  session={session}
                  onExpand={() => setExpandedSession(
                    expandedSession === session.id ? null : session.id
                  )}
                />
              ))
            )}
          </div>
        )}
      </div>

      {/* Footer */}
      <div className="p-4 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800">
        <div className="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
          <div>
            {events.length} events • {sessions.length} sessions
          </div>
          <div>
            Last updated: {new Date().toLocaleTimeString()}
          </div>
        </div>
      </div>
    </div>
  );
};
