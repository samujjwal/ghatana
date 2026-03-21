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
export declare const UserCursor: React.FC<UserCursorProps>;
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
export declare const CollaborationCursors: React.FC<CollaborationCursorsProps>;
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
export declare const CollaborationStatusBar: React.FC<CollaborationStatusBarProps>;
//# sourceMappingURL=CollaborationComponents.d.ts.map