/**
 * @ghatana/yappc-ide - Collaboration Status Bar Component
 * 
 * Status bar showing active collaborators, connection status,
 * and quick access to collaboration features.
 * 
 * @doc.type component
 * @doc.purpose Collaboration status bar for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback } from 'react';

import { useCollaborativeEditing, generateUserAvatar, type UserPresence } from '../hooks/useCollaborativeEditing';

/**
 * Collaboration status bar props
 */
export interface CollaborationStatusBarProps {
  onOpenSettings?: () => void;
  onOpenConflicts?: () => void;
  showConnectionStatus?: boolean;
  showActiveUsers?: boolean;
  showConflicts?: boolean;
  className?: string;
}

/**
 * User avatar component
 */
interface UserAvatarProps {
  user: UserPresence;
  size?: 'small' | 'medium' | 'large';
  showStatus?: boolean;
  onClick?: () => void;
}

const UserAvatar: React.FC<UserAvatarProps> = ({
  user,
  size = 'medium',
  showStatus = true,
  onClick,
}) => {
  const sizeClasses = {
    small: 'w-6 h-6',
    medium: 'w-8 h-8',
    large: 'w-10 h-10',
  };

  const getActivityIcon = useCallback((activity: UserPresence['activity']) => {
    switch (activity) {
      case 'typing':
        return '✏️';
      case 'selecting':
        return '🎯';
      default:
        return null;
    }
  }, []);

  return (
    <div
      className={`
        relative cursor-pointer group transition-transform hover:scale-110
        ${sizeClasses[size]}
      `}
      onClick={onClick}
      title={`${user.userName} - ${user.activity}`}
    >
      <img
        src={generateUserAvatar(user.userName, user.userColor)}
        alt={user.userName}
        className="w-full h-full rounded-full border-2 border-white shadow-sm"
      />
      
      {/* Status indicator */}
      {showStatus && (
        <div className="absolute -bottom-0 -right-0">
          {user.activity === 'typing' ? (
            <div className="w-3 h-3 bg-green-500 rounded-full border-2 border-white">
              <div className="w-full h-full bg-green-500 rounded-full animate-ping" />
            </div>
          ) : user.isOnline ? (
            <div className="w-3 h-3 bg-green-500 rounded-full border-2 border-white" />
          ) : (
            <div className="w-3 h-3 bg-gray-400 rounded-full border-2 border-white" />
          )}
        </div>
      )}

      {/* Activity icon */}
      {getActivityIcon(user.activity) && (
        <div className="absolute -top-1 -right-1 text-xs">
          {getActivityIcon(user.activity)}
        </div>
      )}

      {/* Tooltip */}
      <div className="absolute bottom-full left-1/2 transform -translate-x-1/2 mb-2 px-2 py-1 bg-gray-900 text-white text-xs rounded opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap pointer-events-none">
        <div className="font-semibold">{user.userName}</div>
        <div className="text-gray-300">
          {user.activity === 'typing' ? 'Typing...' : 
           user.activity === 'selecting' ? 'Selecting...' : 
           user.activity === 'idle' ? 'Idle' : 'Online'}
        </div>
        <div className="absolute top-full left-1/2 transform -translate-x-1/2 w-0 h-0 border-l-2 border-r-2 border-t-2 border-transparent border-t-gray-900" />
      </div>
    </div>
  );
};

/**
 * Connection status indicator
 */
interface ConnectionStatusProps {
  isConnected: boolean;
  latency?: number;
}

const ConnectionStatus: React.FC<ConnectionStatusProps> = ({
  isConnected,
  latency,
}) => {
  const getStatusColor = useCallback(() => {
    if (!isConnected) return 'text-red-500';
    if (!latency) return 'text-gray-500';
    if (latency < 50) return 'text-green-500';
    if (latency < 150) return 'text-yellow-500';
    return 'text-red-500';
  }, [isConnected, latency]);

  const getStatusText = useCallback(() => {
    if (!isConnected) return 'Disconnected';
    if (!latency) return 'Connected';
    return `${latency}ms`;
  }, [isConnected, latency]);

  return (
    <div className="flex items-center gap-2">
      <div className={`w-2 h-2 rounded-full ${isConnected ? 'bg-green-500' : 'bg-red-500'}`} />
      <span className={`text-xs ${getStatusColor()}`}>
        {getStatusText()}
      </span>
    </div>
  );
};

/**
 * Collaboration Status Bar Component
 */
export const CollaborationStatusBar: React.FC<CollaborationStatusBarProps> = ({
  onOpenSettings,
  onOpenConflicts,
  showConnectionStatus = true,
  showActiveUsers = true,
  showConflicts = true,
  className = '',
}) => {
  const {
    getUsersInFile,
    getConflictsInFile,
    isCollaborating,
  } = useCollaborativeEditing();

  const [showUserList, setShowUserList] = useState(false);
  const [isConnected] = useState(true); // Would come from WebSocket service
  const [latency] = useState(45); // Would come from WebSocket service

  const usersInCurrentFile = getUsersInFile();
  const conflictsInCurrentFile = getConflictsInFile();

  const handleUserClick = useCallback((user: UserPresence) => {
    // Could open user profile, send message, etc.
    console.log('User clicked:', user);
  }, []);

  const handleConnectionClick = useCallback(() => {
    // Could show connection details, reconnect, etc.
    console.log('Connection status clicked');
  }, []);

  if (!isCollaborating) {
    return null;
  }

  return (
    <div className={`
      flex items-center gap-4 px-3 py-1 bg-gray-100 dark:bg-gray-800 
      border-t border-gray-200 dark:border-gray-700 text-xs
      ${className}
    `}>
      {/* Connection status */}
      {showConnectionStatus && (
        <div
          className="flex items-center gap-2 cursor-pointer hover:bg-gray-200 dark:hover:bg-gray-700 px-2 py-1 rounded transition-colors"
          onClick={handleConnectionClick}
        >
          <ConnectionStatus isConnected={isConnected} latency={latency} />
        </div>
      )}

      {/* Active users */}
      {showActiveUsers && usersInCurrentFile.length > 0 && (
        <div className="flex items-center gap-2">
          <span className="text-gray-500 dark:text-gray-400">
            {usersInCurrentFile.length} active
          </span>
          <div className="flex items-center gap-1">
            {/* Show up to 3 avatars */}
            {usersInCurrentFile.slice(0, 3).map(user => (
              <UserAvatar
                key={user.userId}
                user={user}
                size="small"
                onClick={() => handleUserClick(user)}
              />
            ))}
            
            {/* More users indicator */}
            {usersInCurrentFile.length > 3 && (
              <div
                className="w-6 h-6 bg-gray-300 dark:bg-gray-600 rounded-full border-2 border-white shadow-sm flex items-center justify-center cursor-pointer hover:bg-gray-400 dark:hover:bg-gray-500 transition-colors"
                onClick={() => setShowUserList(!showUserList)}
                title={`+${usersInCurrentFile.length - 3} more users`}
              >
                <span className="text-xs font-medium text-gray-700 dark:text-gray-300">
                  +{usersInCurrentFile.length - 3}
                </span>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Conflicts indicator */}
      {showConflicts && conflictsInCurrentFile.length > 0 && (
        <div
          className="flex items-center gap-2 cursor-pointer hover:bg-gray-200 dark:hover:bg-gray-700 px-2 py-1 rounded transition-colors"
          onClick={onOpenConflicts}
        >
          <div className="w-2 h-2 bg-red-500 rounded-full" />
          <span className="text-red-600 dark:text-red-400">
            {conflictsInCurrentFile.length} conflict{conflictsInCurrentFile.length > 1 ? 's' : ''}
          </span>
        </div>
      )}

      {/* Settings button */}
      <button
        onClick={onOpenSettings}
        className="p-1 text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 transition-colors"
        title="Collaboration settings"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
      </button>

      {/* User list dropdown */}
      {showUserList && (
        <div className="absolute bottom-full left-0 mb-2 w-64 bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg">
          <div className="p-3 border-b border-gray-200 dark:border-gray-700">
            <h3 className="text-sm font-medium text-gray-900 dark:text-gray-100">
              Active Users ({usersInCurrentFile.length})
            </h3>
          </div>
          <div className="max-h-48 overflow-y-auto">
            {usersInCurrentFile.map(user => (
              <div
                key={user.userId}
                className="flex items-center gap-3 p-3 hover:bg-gray-50 dark:hover:bg-gray-800 cursor-pointer transition-colors"
                onClick={() => handleUserClick(user)}
              >
                <UserAvatar user={user} size="small" />
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                    {user.userName}
                  </div>
                  <div className="text-xs text-gray-500 dark:text-gray-400">
                    {user.activity === 'typing' ? 'Typing...' : 
                     user.activity === 'selecting' ? 'Selecting...' : 
                     user.activity === 'idle' ? 'Idle' : 'Online'}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
