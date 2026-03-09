/**
 * @ghatana/yappc-ide - Presence Indicators Component
 * 
 * React component for displaying real-time presence information
 * including active users, cursor positions, and collaboration status.
 * 
 * @doc.type component
 * @doc.purpose Real-time presence visualization
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useMemo, useCallback } from 'react';
import { useAtom } from 'jotai';
import { idePresenceAtom, ideActiveFileAtom } from '../state/atoms';
import type { IDEPresence } from '../types';

/**
 * Presence indicator props
 */
export interface PresenceIndicatorsProps {
  /** Additional CSS classes */
  className?: string;
  /** Maximum number of users to display */
  maxUsers?: number;
  /** Show cursor positions */
  showCursors?: boolean;
  /** Show user avatars */
  showAvatars?: boolean;
  /** Show user names */
  showNames?: boolean;
  /** Compact display mode */
  compact?: boolean;
  /** Position of the indicator */
  position?: 'top-right' | 'top-left' | 'bottom-right' | 'bottom-left';
}

/**
 * User avatar component
 */
const UserAvatar: React.FC<{
  user: IDEPresence;
  size?: 'sm' | 'md' | 'lg';
  showName?: boolean;
}> = ({ user, size = 'md', showName = true }) => {
  const sizeClasses = {
    sm: 'w-6 h-6 text-xs',
    md: 'w-8 h-8 text-sm',
    lg: 'w-10 h-10 text-base',
  };

  const initials = user.userName
    .split(' ')
    .map(word => word[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);

  return (
    <div className="flex items-center space-x-2">
      <div
        className={`
          ${sizeClasses[size]}
          rounded-full
          flex items-center justify-center
          font-medium text-white
          shadow-sm
          border-2 border-white
          relative
        `}
        style={{ backgroundColor: user.userColor }}
        title={user.userName}
      >
        {initials}
        {user.isOnline && (
          <div className="absolute -bottom-0 -right-0 w-3 h-3 bg-green-400 rounded-full border-2 border-white" />
        )}
      </div>
      {showName && (
        <span className="text-sm font-medium text-gray-700">
          {user.userName}
        </span>
      )}
    </div>
  );
};

/**
 * Cursor indicator component
 */
const CursorIndicator: React.FC<{
  user: IDEPresence;
  activeFileId: string | null;
}> = ({ user, activeFileId }) => {
  if (!user.cursorPosition || user.activeFileId !== activeFileId) {
    return null;
  }

  return (
    <div
      className="absolute pointer-events-none z-50"
      style={{
        backgroundColor: user.userColor,
        opacity: 0.8,
      }}
    >
      <div className="relative">
        {/* Cursor line */}
        <div
          className="w-0.5 bg-current"
          style={{
            height: '18px',
            backgroundColor: user.userColor,
          }}
        />
        {/* User label */}
        <div
          className="absolute top-0 left-1 px-1 py-0.5 text-xs text-white rounded whitespace-nowrap"
          style={{ backgroundColor: user.userColor }}
        >
          {user.userName}
        </div>
      </div>
    </div>
  );
};

/**
 * Selection indicator component
 */
const SelectionIndicator: React.FC<{
  user: IDEPresence;
  activeFileId: string | null;
}> = ({ user, activeFileId }) => {
  if (!user.selection || user.activeFileId !== activeFileId) {
    return null;
  }

  return (
    <div
      className="absolute pointer-events-none z-40 opacity-30"
      style={{
        backgroundColor: user.userColor,
        left: 0,
        top: 0,
        width: '100%',
        height: '100%',
      }}
    />
  );
};

/**
 * Presence Indicators Component
 */
export const PresenceIndicators: React.FC<PresenceIndicatorsProps> = ({
  className = '',
  maxUsers = 5,
  showCursors = true,
  showAvatars = true,
  showNames = true,
  compact = false,
  position = 'top-right',
}) => {
  const [presence] = useAtom(idePresenceAtom);
  const [activeFile] = useAtom(ideActiveFileAtom);

  // Filter active users and sort by activity
  const activeUsers = useMemo(() => {
    const users = Object.values(presence).filter(user => user.isOnline);
    return users
      .sort((a, b) => b.lastActivity - a.lastActivity)
      .slice(0, maxUsers);
  }, [presence, maxUsers]);

  // Position classes
  const positionClasses = {
    'top-right': 'top-4 right-4',
    'top-left': 'top-4 left-4',
    'bottom-right': 'bottom-4 right-4',
    'bottom-left': 'bottom-4 left-4',
  };

  // Handle user click
  const handleUserClick = useCallback((user: IDEPresence) => {
    // Could implement user interaction, like jumping to their cursor
    console.log('User clicked:', user.userName);
  }, []);

  if (activeUsers.length === 0) {
    return null;
  }

  return (
    <>
      {/* User avatars */}
      {showAvatars && (
        <div
          className={`
            fixed z-30 bg-white rounded-lg shadow-lg border border-gray-200 p-2
            ${positionClasses[position]}
            ${className}
          `}
        >
          <div className="flex items-center space-x-1">
            {activeUsers.map((user) => (
              <div
                key={user.userId}
                className="cursor-pointer hover:opacity-80 transition-opacity"
                onClick={() => handleUserClick(user)}
              >
                <UserAvatar
                  user={user}
                  size={compact ? 'sm' : 'md'}
                  showName={!compact && showNames}
                />
              </div>
            ))}
            {Object.keys(presence).length > maxUsers && (
              <div className="text-sm text-gray-500 ml-2">
                +{Object.keys(presence).length - maxUsers}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Cursor indicators */}
      {showCursors && activeUsers.map((user) => (
        <CursorIndicator
          key={`cursor-${user.userId}`}
          user={user}
          activeFileId={activeFile?.id || null}
        />
      ))}

      {/* Selection indicators */}
      {showCursors && activeUsers.map((user) => (
        <SelectionIndicator
          key={`selection-${user.userId}`}
          user={user}
          activeFileId={activeFile?.id || null}
        />
      ))}
    </>
  );
};

/**
 * Collaboration status component
 */
export const CollaborationStatus: React.FC<{
  className?: string;
  showDetails?: boolean;
}> = ({ className = '', showDetails = true }) => {
  const [presence] = useAtom(idePresenceAtom);
  const [activeFile] = useAtom(ideActiveFileAtom);

  const activeUsers = Object.values(presence).filter(user => user.isOnline);
  const usersInCurrentFile = activeUsers.filter(
    user => user.activeFileId === activeFile?.id
  );

  const getStatusColor = () => {
    if (activeUsers.length === 0) return 'text-gray-500';
    if (activeUsers.length === 1) return 'text-green-500';
    if (activeUsers.length <= 3) return 'text-blue-500';
    return 'text-orange-500';
  };

  const getStatusText = () => {
    if (activeUsers.length === 0) return 'Offline';
    if (activeUsers.length === 1) return 'Collaborating';
    if (usersInCurrentFile.length > 0) {
      return `${usersInCurrentFile.length} in this file`;
    }
    return `${activeUsers.length} online`;
  };

  return (
    <div className={`flex items-center space-x-2 ${className}`}>
      <div className={`flex items-center space-x-1 ${getStatusColor()}`}>
        {/* Connection indicator */}
        <div className={`w-2 h-2 rounded-full ${
          activeUsers.length > 0 ? 'bg-green-400' : 'bg-gray-400'
        }`} />
        
        {/* Status text */}
        <span className="text-sm font-medium">
          {getStatusText()}
        </span>
      </div>

      {/* Detailed user list */}
      {showDetails && activeUsers.length > 0 && (
        <div className="flex items-center space-x-1">
          {activeUsers.slice(0, 3).map((user) => (
            <div
              key={user.userId}
              className="w-4 h-4 rounded-full border border-white shadow-sm"
              style={{ backgroundColor: user.userColor }}
              title={user.userName}
            />
          ))}
          {activeUsers.length > 3 && (
            <span className="text-xs text-gray-500">
              +{activeUsers.length - 3}
            </span>
          )}
        </div>
      )}
    </div>
  );
};

/**
 * User list component
 */
export const UserList: React.FC<{
  className?: string;
  showInactive?: boolean;
}> = ({ className = '', showInactive = false }) => {
  const [presence] = useAtom(idePresenceAtom);

  const users = useMemo(() => {
    const allUsers = Object.values(presence);
    if (showInactive) {
      return allUsers.sort((a, b) => b.lastActivity - a.lastActivity);
    }
    return allUsers
      .filter(user => user.isOnline)
      .sort((a, b) => b.lastActivity - a.lastActivity);
  }, [presence, showInactive]);

  if (users.length === 0) {
    return (
      <div className={`text-center text-gray-500 py-4 ${className}`}>
        No active users
      </div>
    );
  }

  return (
    <div className={`space-y-2 ${className}`}>
      {users.map((user) => (
        <div
          key={user.userId}
          className="flex items-center justify-between p-2 rounded-lg hover:bg-gray-50"
        >
          <div className="flex items-center space-x-3">
            <UserAvatar user={user} size="sm" showName={false} />
            <div>
              <div className="text-sm font-medium text-gray-900">
                {user.userName}
              </div>
              {user.activeFileId && (
                <div className="text-xs text-gray-500">
                  Editing file
                </div>
              )}
            </div>
          </div>
          <div className="flex items-center space-x-2">
            {/* Status indicator */}
            <div className={`w-2 h-2 rounded-full ${
              user.isOnline ? 'bg-green-400' : 'bg-gray-400'
            }`} />
            
            {/* Last activity */}
            <span className="text-xs text-gray-500">
              {formatLastActivity(user.lastActivity)}
            </span>
          </div>
        </div>
      ))}
    </div>
  );
};

/**
 * Format last activity time
 */
function formatLastActivity(timestamp: number): string {
  const now = Date.now();
  const diff = now - timestamp;

  if (diff < 60000) return 'Active now';
  if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
  return `${Math.floor(diff / 86400000)}d ago`;
}

export default PresenceIndicators;
