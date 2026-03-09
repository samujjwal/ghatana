/**
 * Real-Time UI Components for Web
 * Presence indicators, typing indicators, and live updates
 * 
 * @doc.type component
 * @doc.purpose Provide real-time collaboration UI elements
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useMemo } from 'react';
import { PresenceData, TypingIndicator as TypingIndicatorType } from '../../hooks/useRealtime';

/**
 * Avatar with presence status indicator
 */
export function PresenceAvatar({
  user,
  size = 'md',
  showStatus = true,
}: {
  user: PresenceData;
  size?: 'sm' | 'md' | 'lg';
  showStatus?: boolean;
}) {
  const sizeClasses = {
    sm: 'w-6 h-6 text-xs',
    md: 'w-8 h-8 text-sm',
    lg: 'w-10 h-10 text-base',
  };

  const statusClasses = {
    active: 'bg-green-500',
    idle: 'bg-yellow-500',
    away: 'bg-gray-400',
  };

  const statusDotSize = {
    sm: 'w-2 h-2',
    md: 'w-2.5 h-2.5',
    lg: 'w-3 h-3',
  };

  const initials = user.displayName
    .split(' ')
    .map(n => n[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

  return (
    <div className="relative inline-block">
      {user.avatar ? (
        <img
          src={user.avatar}
          alt={user.displayName}
          className={`${sizeClasses[size]} rounded-full object-cover ring-2 ring-white`}
        />
      ) : (
        <div
          className={`${sizeClasses[size]} rounded-full bg-gradient-to-br from-blue-500 to-purple-600 
                      flex items-center justify-center text-white font-medium ring-2 ring-white`}
        >
          {initials}
        </div>
      )}
      
      {showStatus && (
        <span
          className={`absolute bottom-0 right-0 ${statusDotSize[size]} rounded-full 
                      ${statusClasses[user.status]} ring-2 ring-white`}
          title={user.status}
        />
      )}
    </div>
  );
}

/**
 * Presence list showing active users
 */
export function PresenceList({
  users,
  maxVisible = 5,
  onUserClick,
  className = '',
}: {
  users: PresenceData[];
  maxVisible?: number;
  onUserClick?: (user: PresenceData) => void;
  className?: string;
}) {
  const visibleUsers = users.slice(0, maxVisible);
  const overflowCount = users.length - maxVisible;

  if (users.length === 0) {
    return null;
  }

  return (
    <div className={`flex items-center ${className}`}>
      <div className="flex -space-x-2">
        {visibleUsers.map((user) => (
          <button
            key={user.userId}
            onClick={() => onUserClick?.(user)}
            className="focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 rounded-full
                       transition-transform hover:scale-110 hover:z-10"
            title={`${user.displayName} (${user.status})`}
          >
            <PresenceAvatar user={user} size="sm" />
          </button>
        ))}
        
        {overflowCount > 0 && (
          <div
            className="w-6 h-6 rounded-full bg-gray-200 flex items-center justify-center 
                       text-xs font-medium text-gray-600 ring-2 ring-white"
            title={`${overflowCount} more users`}
          >
            +{overflowCount}
          </div>
        )}
      </div>
      
      <span className="ml-2 text-xs text-gray-500">
        {users.length} online
      </span>
    </div>
  );
}

/**
 * Typing indicator bubble
 */
export function TypingIndicator({
  users,
  className = '',
}: {
  users: TypingIndicatorType[];
  className?: string;
}) {
  const message = useMemo(() => {
    if (users.length === 0) return null;
    if (users.length === 1) return `${users[0].displayName} is typing...`;
    if (users.length === 2) return `${users[0].displayName} and ${users[1].displayName} are typing...`;
    return `${users[0].displayName} and ${users.length - 1} others are typing...`;
  }, [users]);

  if (!message) return null;

  return (
    <div className={`flex items-center gap-2 text-sm text-gray-500 ${className}`}>
      <div className="flex space-x-1">
        <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
        <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
        <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
      </div>
      <span>{message}</span>
    </div>
  );
}

/**
 * Live update notification toast
 */
export function LiveUpdateToast({
  message,
  type = 'info',
  onClose,
  autoHideMs = 3000,
}: {
  message: string;
  type?: 'info' | 'success' | 'warning';
  onClose: () => void;
  autoHideMs?: number;
}) {
  React.useEffect(() => {
    if (autoHideMs > 0) {
      const timer = setTimeout(onClose, autoHideMs);
      return () => clearTimeout(timer);
    }
  }, [autoHideMs, onClose]);

  const typeClasses = {
    info: 'bg-blue-50 border-blue-200 text-blue-700',
    success: 'bg-green-50 border-green-200 text-green-700',
    warning: 'bg-yellow-50 border-yellow-200 text-yellow-700',
  };

  const iconClasses = {
    info: 'text-blue-400',
    success: 'text-green-400',
    warning: 'text-yellow-400',
  };

  return (
    <div
      className={`flex items-center gap-3 px-4 py-3 rounded-lg border shadow-lg 
                  animate-slide-in-right ${typeClasses[type]}`}
      role="alert"
    >
      <div className={iconClasses[type]}>
        {type === 'info' && (
          <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
          </svg>
        )}
        {type === 'success' && (
          <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
          </svg>
        )}
        {type === 'warning' && (
          <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
          </svg>
        )}
      </div>
      <p className="flex-1 text-sm">{message}</p>
      <button
        onClick={onClose}
        className="text-gray-400 hover:text-gray-600 transition-colors"
        aria-label="Close"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>
  );
}

/**
 * Connection status indicator
 */
export function ConnectionStatus({
  isConnected,
  isReconnecting,
  error,
  onReconnect,
  className = '',
}: {
  isConnected: boolean;
  isReconnecting: boolean;
  error: string | null;
  onReconnect?: () => void;
  className?: string;
}) {
  if (isConnected) {
    return (
      <div className={`flex items-center gap-2 text-sm text-green-600 ${className}`}>
        <span className="w-2 h-2 bg-green-500 rounded-full animate-pulse" />
        <span>Connected</span>
      </div>
    );
  }

  if (isReconnecting) {
    return (
      <div className={`flex items-center gap-2 text-sm text-yellow-600 ${className}`}>
        <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
        </svg>
        <span>Reconnecting...</span>
      </div>
    );
  }

  return (
    <div className={`flex items-center gap-2 ${className}`}>
      <span className="w-2 h-2 bg-red-500 rounded-full" />
      <span className="text-sm text-red-600">
        {error || 'Disconnected'}
      </span>
      {onReconnect && (
        <button
          onClick={onReconnect}
          className="text-sm text-blue-500 hover:text-blue-700 underline"
        >
          Reconnect
        </button>
      )}
    </div>
  );
}

/**
 * User cursor for collaborative editing
 */
export function UserCursor({
  user,
  position,
  color = '#3B82F6',
}: {
  user: PresenceData;
  position: { x: number; y: number };
  color?: string;
}) {
  return (
    <div
      className="absolute pointer-events-none transition-all duration-75"
      style={{ left: position.x, top: position.y }}
    >
      <svg
        className="w-4 h-4"
        viewBox="0 0 24 24"
        fill={color}
        style={{ transform: 'rotate(-15deg)' }}
      >
        <path d="M5.5 3.21V20.8c0 .45.54.67.85.35l4.86-5.41a1 1 0 01.74-.33h7.8c.45 0 .67-.54.35-.85L5.5 3.21z" />
      </svg>
      <div
        className="absolute left-4 top-0 px-2 py-0.5 rounded text-xs text-white whitespace-nowrap"
        style={{ backgroundColor: color }}
      >
        {user.displayName}
      </div>
    </div>
  );
}

/**
 * Reaction picker and display
 */
export function ReactionBar({
  reactions,
  onReact,
  currentUserId,
  className = '',
}: {
  reactions: Record<string, string[]>; // emoji -> userIds
  onReact: (emoji: string) => void;
  currentUserId: string;
  className?: string;
}) {
  const availableEmojis = ['👍', '❤️', '😄', '🎉', '🤔', '👀'];

  const hasUserReacted = (emoji: string) => 
    reactions[emoji]?.includes(currentUserId);

  return (
    <div className={`flex items-center gap-2 ${className}`}>
      {/* Existing reactions */}
      {Object.entries(reactions).map(([emoji, userIds]) => (
        <button
          key={emoji}
          onClick={() => onReact(emoji)}
          className={`flex items-center gap-1 px-2 py-1 rounded-full text-sm transition-colors
                      ${hasUserReacted(emoji) 
                        ? 'bg-blue-100 border border-blue-300' 
                        : 'bg-gray-100 hover:bg-gray-200 border border-transparent'
                      }`}
        >
          <span>{emoji}</span>
          <span className="text-gray-600">{userIds.length}</span>
        </button>
      ))}

      {/* Add reaction button */}
      <div className="relative group">
        <button
          className="w-8 h-8 rounded-full bg-gray-100 hover:bg-gray-200 flex items-center justify-center
                     text-gray-500 transition-colors"
          aria-label="Add reaction"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.828 14.828a4 4 0 01-5.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </button>
        
        {/* Emoji picker dropdown */}
        <div className="absolute bottom-full left-0 mb-2 hidden group-hover:flex bg-white rounded-lg 
                        shadow-lg border border-gray-200 p-2 gap-1">
          {availableEmojis.map((emoji) => (
            <button
              key={emoji}
              onClick={() => onReact(emoji)}
              className="w-8 h-8 hover:bg-gray-100 rounded flex items-center justify-center text-lg
                         transition-transform hover:scale-125"
            >
              {emoji}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

// Export all components
export default {
  PresenceAvatar,
  PresenceList,
  TypingIndicator,
  LiveUpdateToast,
  ConnectionStatus,
  UserCursor,
  ReactionBar,
};
