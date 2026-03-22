/**
 * Collaboration types and interfaces
 */

/**
 *
 */
export interface User {
  id: string;
  name: string;
  email: string;
  avatar?: string;
  color: string; // For cursor/presence
}

/**
 *
 */
export interface Comment {
  id: string;
  elementId?: string; // Element this comment is attached to
  position?: { x: number; y: number }; // Canvas position for floating comments
  author: User;
  content: string;
  mentions: string[]; // User IDs mentioned in comment
  createdAt: string;
  updatedAt: string;
  resolved: boolean;
  replies: CommentReply[];
}

/**
 *
 */
export interface CommentReply {
  id: string;
  author: User;
  content: string;
  mentions: string[];
  createdAt: string;
  updatedAt: string;
}

/**
 *
 */
export interface CommentThread {
  id: string;
  comments: Comment[];
  elementId?: string;
  position?: { x: number; y: number };
  resolved: boolean;
  participants: User[];
}

/**
 *
 */
export interface Presence {
  user: User;
  cursor?: { x: number; y: number };
  selection?: string[]; // Selected element IDs
  viewport?: { x: number; y: number; zoom: number };
  lastSeen: string;
  status: 'active' | 'idle' | 'away';
}

/**
 *
 */
export interface CollaborationState {
  users: Map<string, User>;
  presence: Map<string, Presence>;
  comments: Map<string, Comment>;
  threads: Map<string, CommentThread>;
  currentUser?: User;
}

/**
 *
 */
export interface CollaborationEvent {
  type: 'user-joined' | 'user-left' | 'presence-updated' | 'comment-added' | 'comment-updated' | 'comment-resolved';
  userId: string;
  timestamp: string;
  data: unknown;
}

/**
 *
 */
export interface CollaborationProvider {
  // Connection
  connect(roomId: string, user: User): Promise<void>;
  disconnect(): Promise<void>;
  isConnected(): boolean;

  // Presence
  updatePresence(presence: Partial<Presence>): void;
  getPresence(): Map<string, Presence>;
  onPresenceChange(callback: (presence: Map<string, Presence>) => void): () => void;

  // Comments
  addComment(comment: Omit<Comment, 'id' | 'createdAt' | 'updatedAt'>): Promise<Comment>;
  updateComment(id: string, updates: Partial<Comment>): Promise<void>;
  deleteComment(id: string): Promise<void>;
  resolveComment(id: string): Promise<void>;
  getComments(): Map<string, Comment>;
  onCommentsChange(callback: (comments: Map<string, Comment>) => void): () => void;

  // Events
  onEvent(callback: (event: CollaborationEvent) => void): () => void;
  emit(event: CollaborationEvent): void;
}
