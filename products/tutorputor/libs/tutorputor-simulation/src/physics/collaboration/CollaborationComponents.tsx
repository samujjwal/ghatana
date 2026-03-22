/**
 * Collaboration User Cursors Component
 *
 * Renders real-time cursor positions for other users in the simulation.
 *
 * @doc.type component
 * @doc.purpose Multi-user cursor visualization
 * @doc.layer core
 * @doc.pattern Component
 */

import React from 'react';

import type { CollaborationUser } from './usePhysicsCollaboration';

/**
 * Props for user cursor component
 */
export interface UserCursorProps {
    user: CollaborationUser;
}

/**
 * Single user cursor with name label
 */
export const UserCursor: React.FC<UserCursorProps> = ({ user }) => {
    if (!user.cursor) return null;

    return (
        <div
            className="pointer-events-none absolute z-50 transition-all duration-75"
            style={{
                left: user.cursor.x,
                top: user.cursor.y,
                transform: 'translate(-2px, -2px)',
            }}
        >
            {/* Cursor arrow */}
            <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="none"
                style={{ filter: 'drop-shadow(0 1px 2px rgba(0,0,0,0.3))' }}
            >
                <path
                    d="M5 3L19 12L12 13L9 20L5 3Z"
                    fill={user.color}
                    stroke="white"
                    strokeWidth="1.5"
                />
            </svg>

            {/* User name label */}
            <div
                className="absolute left-4 top-4 whitespace-nowrap rounded px-2 py-0.5 text-xs font-medium text-white shadow-sm"
                style={{ backgroundColor: user.color }}
            >
                {user.name}
            </div>
        </div>
    );
};

UserCursor.displayName = 'UserCursor';

/**
 * Props for collaboration cursors container
 */
export interface CollaborationCursorsProps {
    users: Record<string, CollaborationUser>;
    className?: string;
}

/**
 * Container for all collaboration cursors
 */
export const CollaborationCursors: React.FC<CollaborationCursorsProps> = ({
    users,
    className = '',
}) => {
    const onlineUsers = Object.values(users).filter((u) => u.isOnline && u.cursor);

    if (onlineUsers.length === 0) return null;

    return (
        <div className={`pointer-events-none absolute inset-0 overflow-hidden ${className}`}>
            {onlineUsers.map((user) => (
                <UserCursor key={user.id} user={user} />
            ))}
        </div>
    );
};

CollaborationCursors.displayName = 'CollaborationCursors';

/**
 * Props for collaboration status bar
 */
export interface CollaborationStatusBarProps {
    isConnected: boolean;
    syncStatus: string;
    users: Record<string, CollaborationUser>;
    currentUser: CollaborationUser;
    className?: string;
}

/**
 * Status bar showing connection and user presence
 */
export const CollaborationStatusBar: React.FC<CollaborationStatusBarProps> = ({
    isConnected,
    syncStatus,
    users,
    currentUser,
    className = '',
}) => {
    const onlineUsers = Object.values(users).filter((u) => u.isOnline);
    const totalUsers = onlineUsers.length + 1; // +1 for current user

    return (
        <div
            className={`flex items-center gap-3 rounded-lg bg-white px-3 py-2 shadow-sm dark:bg-gray-800 ${className}`}
        >
            {/* Connection status */}
            <div className="flex items-center gap-1.5">
                <div
                    className={`h-2 w-2 rounded-full ${isConnected ? 'bg-green-500' : 'bg-gray-400'
                        }`}
                />
                <span className="text-xs text-gray-600 dark:text-gray-400">
                    {syncStatus === 'synced' ? 'Synced' : syncStatus === 'syncing' ? 'Syncing...' : 'Offline'}
                </span>
            </div>

            {/* Divider */}
            <div className="h-4 w-px bg-gray-200 dark:bg-gray-700" />

            {/* User avatars */}
            <div className="flex -space-x-2">
                {/* Current user */}
                <div
                    className="flex h-6 w-6 items-center justify-center rounded-full border-2 border-white text-xs font-medium text-white dark:border-gray-800"
                    style={{ backgroundColor: currentUser.color }}
                    title={`${currentUser.name} (you)`}
                >
                    {currentUser.name.charAt(0).toUpperCase()}
                </div>

                {/* Other users */}
                {onlineUsers.slice(0, 5).map((user) => (
                    <div
                        key={user.id}
                        className="flex h-6 w-6 items-center justify-center rounded-full border-2 border-white text-xs font-medium text-white dark:border-gray-800"
                        style={{ backgroundColor: user.color }}
                        title={user.name}
                    >
                        {user.name.charAt(0).toUpperCase()}
                    </div>
                ))}

                {/* Overflow indicator */}
                {onlineUsers.length > 5 && (
                    <div className="flex h-6 w-6 items-center justify-center rounded-full border-2 border-white bg-gray-100 text-xs font-medium text-gray-600 dark:border-gray-800 dark:bg-gray-700 dark:text-gray-300">
                        +{onlineUsers.length - 5}
                    </div>
                )}
            </div>

            {/* User count */}
            <span className="text-xs text-gray-500 dark:text-gray-400">
                {totalUsers} {totalUsers === 1 ? 'user' : 'users'}
            </span>
        </div>
    );
};

CollaborationStatusBar.displayName = 'CollaborationStatusBar';
