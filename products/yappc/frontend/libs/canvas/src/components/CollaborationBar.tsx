/**
 * Collaboration Bar Component
 * 
 * Displays active collaborators with avatars and connection status.
 * Shows who's currently viewing/editing the canvas.
 * 
 * @module canvas/components
 */

import React from 'react';
import type { RemoteUser } from '../hooks/useCanvasCollaborationBackend';

export interface CollaborationBarProps {
  /** Current user info */
  currentUser: {
    userId: string;
    userName: string;
    userColor: string;
  };
  /** Remote users in the session */
  remoteUsers: RemoteUser[];
  /** Connection status */
  isConnected: boolean;
  /** Sync status */
  syncStatus?: 'syncing' | 'synced' | 'error' | 'offline';
  /** Custom className */
  className?: string;
}

/**
 * Collaboration Bar Component
 * 
 * Shows active users and connection status for canvas collaboration.
 * 
 * @example
 * ```tsx
 * <CollaborationBar
 *   currentUser={{ userId: '1', userName: 'Alice', userColor: '#FF6B6B' }}
 *   remoteUsers={remoteUsers}
 *   isConnected={true}
 *   syncStatus="synced"
 * />
 * ```
 */
export const CollaborationBar: React.FC<CollaborationBarProps> = ({
  currentUser,
  remoteUsers,
  isConnected,
  syncStatus = 'offline',
  className = '',
}) => {
  const allUsers = [
    { ...currentUser, isOnline: true, lastSeen: Date.now() },
    ...remoteUsers,
  ];

  const onlineCount = allUsers.filter((u) => u.isOnline).length;

  return (
    <div
      className={`collaboration-bar ${className}`}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: '16px',
        padding: '8px 16px',
        backgroundColor: 'rgba(0, 0, 0, 0.05)',
        borderBottom: '1px solid rgba(0, 0, 0, 0.1)',
      }}
    >
      {/* Connection Status */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          fontSize: '14px',
        }}
      >
        <div
          style={{
            width: '8px',
            height: '8px',
            borderRadius: '50%',
            backgroundColor: isConnected ? '#10b981' : '#ef4444',
          }}
        />
        <span style={{ color: '#6b7280', fontWeight: 500 }}>
          {isConnected ? 'Connected' : 'Disconnected'}
        </span>
        {isConnected && syncStatus && (
          <span style={{ color: '#9ca3af', fontSize: '12px' }}>
            • {syncStatus}
          </span>
        )}
      </div>

      {/* User Avatars */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          marginLeft: 'auto',
        }}
      >
        <span style={{ fontSize: '14px', color: '#6b7280' }}>
          {onlineCount} {onlineCount === 1 ? 'person' : 'people'} online
        </span>

        <div style={{ display: 'flex', marginLeft: '8px' }}>
          {allUsers.slice(0, 5).map((user, index) => (
            <div
              key={user.userId}
              title={user.userName}
              style={{
                width: '32px',
                height: '32px',
                borderRadius: '50%',
                backgroundColor: user.userColor,
                border: '2px solid white',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'white',
                fontSize: '12px',
                fontWeight: 600,
                marginLeft: index > 0 ? '-8px' : '0',
                opacity: user.isOnline ? 1 : 0.5,
                position: 'relative',
                zIndex: 10 - index,
                cursor: 'pointer',
                transition: 'transform 0.2s',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.transform = 'scale(1.1)';
                e.currentTarget.style.zIndex = '20';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.transform = 'scale(1)';
                e.currentTarget.style.zIndex = String(10 - index);
              }}
            >
              {user.userName.charAt(0).toUpperCase()}
              
              {/* Online indicator */}
              {user.isOnline && (
                <div
                  style={{
                    position: 'absolute',
                    bottom: '0',
                    right: '0',
                    width: '10px',
                    height: '10px',
                    borderRadius: '50%',
                    backgroundColor: '#10b981',
                    border: '2px solid white',
                  }}
                />
              )}
            </div>
          ))}

          {/* Show +N if more than 5 users */}
          {allUsers.length > 5 && (
            <div
              style={{
                width: '32px',
                height: '32px',
                borderRadius: '50%',
                backgroundColor: '#6b7280',
                border: '2px solid white',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'white',
                fontSize: '12px',
                fontWeight: 600,
                marginLeft: '-8px',
                zIndex: 5,
              }}
            >
              +{allUsers.length - 5}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default CollaborationBar;
