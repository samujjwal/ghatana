/**
 * @doc.type module
 * @doc.purpose Collaboration exports for real-time editing
 * @doc.layer core
 * @doc.pattern Barrel
 */

export {
    usePhysicsCollaboration,
    type CollaborationUser,
    type PhysicsCollaborationState,
    type UsePhysicsCollaborationOptions,
} from './usePhysicsCollaboration';

export {
    UserCursor,
    CollaborationCursors,
    CollaborationStatusBar,
    type UserCursorProps,
    type CollaborationCursorsProps,
    type CollaborationStatusBarProps,
} from './CollaborationComponents';
