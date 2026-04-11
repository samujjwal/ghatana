/**
 * Collaboration module index
 *
 * @doc.type module
 * @doc.purpose Public exports for canvas collaboration support
 * @doc.layer platform
 * @doc.pattern Index
 */

export type {
  CollaboratorPresence,
  CollaborativeChangeType,
  CollaborativeChange,
  CollaborationSession,
  ChangeListener,
  PresenceListener,
  SessionListener,
  CanvasCollaborationAdapter,
} from "./collab-types.js";

export { noopCollaborationAdapter } from "./collab-types.js";

export {
  CollaborationProvider,
  useCollaboration,
  useCollaborators,
  useRemoteCollaborators,
  type CollaborationContextValue,
  type CollaborationProviderProps,
} from "./collab-provider.js";
