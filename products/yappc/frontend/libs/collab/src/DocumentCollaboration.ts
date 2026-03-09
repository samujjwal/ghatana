/**
 * Document Collaboration Module
 *
 * @description Yjs bindings for real-time document collaboration,
 * supporting text editing with rich formatting and comments.
 */

import * as Y from 'yjs';
import { CollaborationManager, CollaborationUser } from './CollaborationManager';

// =============================================================================
// Types
// =============================================================================

export interface TextCursor {
  userId: string;
  userName: string;
  userColor: string;
  position: number;
  selection?: {
    start: number;
    end: number;
  };
  timestamp: number;
}

export interface DocumentComment {
  id: string;
  userId: string;
  userName: string;
  userColor: string;
  content: string;
  position: {
    start: number;
    end: number;
  };
  resolved: boolean;
  createdAt: number;
  replies: DocumentCommentReply[];
}

export interface DocumentCommentReply {
  id: string;
  userId: string;
  userName: string;
  content: string;
  createdAt: number;
}

export interface DocumentVersion {
  id: string;
  userId: string;
  userName: string;
  snapshot: Uint8Array;
  createdAt: number;
  description?: string;
}

export type DocumentChangeType =
  | 'text-change'
  | 'cursors-change'
  | 'comments-change'
  | 'versions-change';

export interface DocumentChangeEvent {
  type: DocumentChangeType;
  data: unknown;
}

// =============================================================================
// Document Collaboration Class
// =============================================================================

export class DocumentCollaboration {
  private collab: CollaborationManager;
  private text: Y.Text;
  private commentsArray: Y.Array<unknown>;
  private versionsArray: Y.Array<unknown>;
  private metaMap: Y.Map<unknown>;
  private userId: string;
  private listeners: Map<DocumentChangeType, Set<(event: DocumentChangeEvent) => void>>;
  private cursors: Map<string, TextCursor>;

  constructor(
    collab: CollaborationManager,
    userId: string,
    documentId: string = 'main'
  ) {
    this.collab = collab;
    this.userId = userId;
    this.listeners = new Map();
    this.cursors = new Map();

    // Get shared data structures
    const doc = collab.getDocument();
    this.text = doc.getText(`document-${documentId}`);
    this.commentsArray = doc.getArray(`document-${documentId}-comments`);
    this.versionsArray = doc.getArray(`document-${documentId}-versions`);
    this.metaMap = doc.getMap(`document-${documentId}-meta`);

    // Set up observers
    this.setupObservers();
  }

  /**
   * Set up Yjs observers
   */
  private setupObservers(): void {
    // Text changes
    this.text.observe((event) => {
      this.emit('text-change', {
        delta: event.delta,
        text: this.getText(),
        event,
      });
    });

    // Comments changes
    this.commentsArray.observe((event) => {
      this.emit('comments-change', {
        comments: this.getComments(),
        event,
      });
    });

    // Versions changes
    this.versionsArray.observe((event) => {
      this.emit('versions-change', {
        versions: this.getVersions(),
        event,
      });
    });

    // Awareness (cursors) observer
    this.collab.on('awareness-change', ({ users }) => {
      this.updateCursors(users);
    });
  }

  /**
   * Update cursor positions from awareness
   */
  private updateCursors(users: CollaborationUser[]): void {
    const newCursors = new Map<string, TextCursor>();

    users.forEach((user) => {
      if (user.id !== this.userId) {
        newCursors.set(user.id, {
          userId: user.id,
          userName: user.name,
          userColor: user.color,
          position: user.selection?.start || 0,
          selection: user.selection,
          timestamp: user.lastActive,
        });
      }
    });

    this.cursors = newCursors;
    this.emit('cursors-change', { cursors: Array.from(newCursors.values()) });
  }

  // ===========================================================================
  // Text API
  // ===========================================================================

  /**
   * Get the full text content
   */
  getText(): string {
    return this.text.toString();
  }

  /**
   * Get text length
   */
  getLength(): number {
    return this.text.length;
  }

  /**
   * Insert text at position
   */
  insert(position: number, content: string, attributes?: Record<string, unknown>): void {
    this.collab.transact(() => {
      this.text.insert(position, content, attributes);
    });
  }

  /**
   * Delete text
   */
  delete(position: number, length: number): void {
    this.collab.transact(() => {
      this.text.delete(position, length);
    });
  }

  /**
   * Apply a delta operation
   */
  applyDelta(delta: unknown[]): void {
    this.collab.transact(() => {
      this.text.applyDelta(delta);
    });
  }

  /**
   * Set entire text content
   */
  setText(content: string): void {
    this.collab.transact(() => {
      this.text.delete(0, this.text.length);
      this.text.insert(0, content);
    });
  }

  /**
   * Format text at range
   */
  format(start: number, length: number, attributes: Record<string, unknown>): void {
    this.collab.transact(() => {
      this.text.format(start, length, attributes);
    });
  }

  // ===========================================================================
  // Cursor API
  // ===========================================================================

  /**
   * Get all remote cursors
   */
  getCursors(): TextCursor[] {
    return Array.from(this.cursors.values());
  }

  /**
   * Update local cursor position
   */
  updateCursor(position: number): void {
    this.collab.updateSelection(position, position);
  }

  /**
   * Update local selection
   */
  updateSelection(start: number, end: number): void {
    this.collab.updateSelection(start, end);
  }

  /**
   * Clear local selection
   */
  clearSelection(): void {
    this.collab.clearSelection();
  }

  // ===========================================================================
  // Comments API
  // ===========================================================================

  /**
   * Get all comments
   */
  getComments(): DocumentComment[] {
    return this.commentsArray.toArray();
  }

  /**
   * Get comment by ID
   */
  getComment(id: string): DocumentComment | null {
    const comments = this.getComments();
    return comments.find((c) => c.id === id) || null;
  }

  /**
   * Add a comment
   */
  addComment(
    start: number,
    end: number,
    content: string,
    userName: string,
    userColor: string
  ): string {
    const id = `comment-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    const comment: DocumentComment = {
      id,
      userId: this.userId,
      userName,
      userColor,
      content,
      position: { start, end },
      resolved: false,
      createdAt: Date.now(),
      replies: [],
    };

    this.collab.transact(() => {
      this.commentsArray.push([comment]);
    });

    return id;
  }

  /**
   * Update a comment
   */
  updateComment(id: string, updates: Partial<Pick<DocumentComment, 'content' | 'resolved'>>): void {
    this.collab.transact(() => {
      const index = this.findCommentIndex(id);
      if (index !== -1) {
        const comment = this.commentsArray.get(index);
        this.commentsArray.delete(index, 1);
        this.commentsArray.insert(index, [{ ...comment, ...updates }]);
      }
    });
  }

  /**
   * Resolve a comment
   */
  resolveComment(id: string): void {
    this.updateComment(id, { resolved: true });
  }

  /**
   * Unresolve a comment
   */
  unresolveComment(id: string): void {
    this.updateComment(id, { resolved: false });
  }

  /**
   * Delete a comment
   */
  deleteComment(id: string): void {
    this.collab.transact(() => {
      const index = this.findCommentIndex(id);
      if (index !== -1) {
        this.commentsArray.delete(index, 1);
      }
    });
  }

  /**
   * Add a reply to a comment
   */
  addReply(commentId: string, content: string, userName: string): string {
    const replyId = `reply-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    const reply: DocumentCommentReply = {
      id: replyId,
      userId: this.userId,
      userName,
      content,
      createdAt: Date.now(),
    };

    this.collab.transact(() => {
      const index = this.findCommentIndex(commentId);
      if (index !== -1) {
        const comment = this.commentsArray.get(index);
        this.commentsArray.delete(index, 1);
        this.commentsArray.insert(index, [{
          ...comment,
          replies: [...comment.replies, reply],
        }]);
      }
    });

    return replyId;
  }

  /**
   * Find comment index by ID
   */
  private findCommentIndex(id: string): number {
    const comments = this.getComments();
    return comments.findIndex((c) => c.id === id);
  }

  // ===========================================================================
  // Versioning API
  // ===========================================================================

  /**
   * Get all versions
   */
  getVersions(): DocumentVersion[] {
    return this.versionsArray.toArray();
  }

  /**
   * Create a version snapshot
   */
  createVersion(description: string, userName: string): string {
    const id = `version-${Date.now()}`;
    const doc = this.collab.getDocument();
    const snapshot = Y.encodeStateAsUpdate(doc);

    const version: DocumentVersion = {
      id,
      userId: this.userId,
      userName,
      snapshot,
      createdAt: Date.now(),
      description,
    };

    this.collab.transact(() => {
      this.versionsArray.push([version]);
    });

    return id;
  }

  /**
   * Get version content
   */
  getVersionContent(versionId: string): string | null {
    const versions = this.getVersions();
    const version = versions.find((v) => v.id === versionId);
    if (!version) return null;

    // Create temporary doc to decode snapshot
    const tempDoc = new Y.Doc();
    Y.applyUpdate(tempDoc, version.snapshot);
    const text = tempDoc.getText(`document-main`);
    const content = text.toString();
    tempDoc.destroy();

    return content;
  }

  /**
   * Restore a version
   */
  restoreVersion(versionId: string): boolean {
    const content = this.getVersionContent(versionId);
    if (content === null) return false;

    this.setText(content);
    return true;
  }

  // ===========================================================================
  // Metadata API
  // ===========================================================================

  /**
   * Get document metadata
   */
  getMetadata(): Record<string, unknown> {
    const meta: Record<string, unknown> = {};
    this.metaMap.forEach((value, key) => {
      meta[key] = value;
    });
    return meta;
  }

  /**
   * Set metadata field
   */
  setMetadata(key: string, value: unknown): void {
    this.collab.transact(() => {
      this.metaMap.set(key, value);
    });
  }

  /**
   * Get metadata field
   */
  getMetadataField<T = unknown>(key: string): T | undefined {
    return this.metaMap.get(key);
  }

  // ===========================================================================
  // Event Handling
  // ===========================================================================

  /**
   * Subscribe to document changes
   */
  on(
    type: DocumentChangeType,
    callback: (event: DocumentChangeEvent) => void
  ): () => void {
    if (!this.listeners.has(type)) {
      this.listeners.set(type, new Set());
    }
    this.listeners.get(type)!.add(callback);

    return () => {
      this.listeners.get(type)?.delete(callback);
    };
  }

  /**
   * Emit a document change event
   */
  private emit(type: DocumentChangeType, data: unknown): void {
    const event: DocumentChangeEvent = { type, data };
    this.listeners.get(type)?.forEach((callback) => callback(event));
  }

  // ===========================================================================
  // Cleanup
  // ===========================================================================

  /**
   * Destroy the document collaboration instance
   */
  destroy(): void {
    this.listeners.clear();
    this.cursors.clear();
  }
}

export default DocumentCollaboration;
