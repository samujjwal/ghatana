/**
 * Mock Collaboration Provider - For development and testing
 * This simulates real-time collaboration without requiring a backend
 */

import { logger } from '../../utils/Logger';
import { palette } from '@ghatana/yappc-ui';
import {
  CommentReply
} from './types';

import type {
  CollaborationProvider,
  User,
  Presence,
  Comment,
  CollaborationEvent
} from './types';

/**
 *
 */
export class MockCollaborationProvider implements CollaborationProvider {
  private connected = false;
  private roomId = '';
  private currentUser?: User;
  private presence = new Map<string, Presence>();
  private comments = new Map<string, Comment>();

  private presenceCallbacks = new Set<(presence: Map<string, Presence>) => void>();
  private commentsCallbacks = new Set<(comments: Map<string, Comment>) => void>();
  private eventCallbacks = new Set<(event: CollaborationEvent) => void>();

  /**
   *
   */
  async connect(roomId: string, user: User): Promise<void> {
    logger.info('Connecting to collaboration room', 'collaboration', { roomId, userId: user.id });

    this.roomId = roomId;
    this.currentUser = user;
    this.connected = true;

    // Add current user to presence
    this.presence.set(user.id, {
      user,
      status: 'active',
      lastSeen: new Date().toISOString(),
    });

    // Simulate other users joining (for demo)
    this.simulateOtherUsers();

    logger.info('Connected to collaboration room', 'collaboration', { roomId, userId: user.id });

    this.emit({
      type: 'user-joined',
      userId: user.id,
      timestamp: new Date().toISOString(),
      data: { user },
    });
  }

  /**
   *
   */
  async disconnect(): Promise<void> {
    if (this.currentUser) {
      this.presence.delete(this.currentUser.id);
      this.emit({
        type: 'user-left',
        userId: this.currentUser.id,
        timestamp: new Date().toISOString(),
        data: {},
      });
    }

    this.connected = false;
    this.currentUser = undefined;
  }

  /**
   *
   */
  isConnected(): boolean {
    return this.connected;
  }

  /**
   *
   */
  updatePresence(updates: Partial<Presence>): void {
    if (!this.currentUser) return;

    const current = this.presence.get(this.currentUser.id);
    if (current) {
      const updated = {
        ...current,
        ...updates,
        lastSeen: new Date().toISOString(),
      };
      this.presence.set(this.currentUser.id, updated);
      this.notifyPresenceChange();

      this.emit({
        type: 'presence-updated',
        userId: this.currentUser.id,
        timestamp: new Date().toISOString(),
        data: { presence: updated },
      });
    }
  }

  /**
   *
   */
  getPresence(): Map<string, Presence> {
    return new Map(this.presence);
  }

  /**
   *
   */
  onPresenceChange(callback: (presence: Map<string, Presence>) => void): () => void {
    this.presenceCallbacks.add(callback);
    return () => this.presenceCallbacks.delete(callback);
  }

  /**
   *
   */
  async addComment(commentData: Omit<Comment, 'id' | 'createdAt' | 'updatedAt'>): Promise<Comment> {
    const comment: Comment = {
      ...commentData,
      id: `comment-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      replies: [],
    };

    this.comments.set(comment.id, comment);
    this.notifyCommentsChange();

    this.emit({
      type: 'comment-added',
      userId: comment.author.id,
      timestamp: new Date().toISOString(),
      data: { comment },
    });

    return comment;
  }

  /**
   *
   */
  async updateComment(id: string, updates: Partial<Comment>): Promise<void> {
    const comment = this.comments.get(id);
    if (comment) {
      const updated = {
        ...comment,
        ...updates,
        updatedAt: new Date().toISOString(),
      };
      this.comments.set(id, updated);
      this.notifyCommentsChange();

      this.emit({
        type: 'comment-updated',
        userId: comment.author.id,
        timestamp: new Date().toISOString(),
        data: { comment: updated },
      });
    }
  }

  /**
   *
   */
  async deleteComment(id: string): Promise<void> {
    const comment = this.comments.get(id);
    if (comment) {
      this.comments.delete(id);
      this.notifyCommentsChange();
    }
  }

  /**
   *
   */
  async resolveComment(id: string): Promise<void> {
    await this.updateComment(id, { resolved: true });

    this.emit({
      type: 'comment-resolved',
      userId: this.currentUser?.id || '',
      timestamp: new Date().toISOString(),
      data: { commentId: id },
    });
  }

  /**
   *
   */
  getComments(): Map<string, Comment> {
    return new Map(this.comments);
  }

  /**
   *
   */
  onCommentsChange(callback: (comments: Map<string, Comment>) => void): () => void {
    this.commentsCallbacks.add(callback);
    return () => this.commentsCallbacks.delete(callback);
  }

  /**
   *
   */
  onEvent(callback: (event: CollaborationEvent) => void): () => void {
    this.eventCallbacks.add(callback);
    return () => this.eventCallbacks.delete(callback);
  }

  /**
   *
   */
  emit(event: CollaborationEvent): void {
    this.eventCallbacks.forEach(callback => callback(event));
  }

  /**
   *
   */
  private notifyPresenceChange(): void {
    const presence = this.getPresence();
    this.presenceCallbacks.forEach(callback => callback(presence));
  }

  /**
   *
   */
  resetForTests(): void {
    this.comments.clear();
    this.presence.clear();
    this.roomId = '';
    this.currentUser = undefined;
    this.connected = false;
    this.notifyPresenceChange();
    this.notifyCommentsChange();
  }

  /**
   *
   */
  private notifyCommentsChange(): void {
    const comments = this.getComments();
    this.commentsCallbacks.forEach(callback => callback(comments));
  }

  /**
   *
   */
  private simulateOtherUsers(): void {
    // Add some demo users for testing
    const demoUsers: User[] = [
      {
        id: 'user-2',
        name: 'Alice Johnson',
        email: 'alice@example.com',
        color: palette.error.main, // Red for user avatar
      },
      {
        id: 'user-3',
        name: 'Bob Smith',
        email: 'bob@example.com',
        color: palette.info.main, // Teal for user avatar
      },
    ];

    setTimeout(() => {
      demoUsers.forEach(user => {
        this.presence.set(user.id, {
          user,
          status: 'active',
          lastSeen: new Date().toISOString(),
          cursor: {
            x: Math.random() * 800,
            y: Math.random() * 600,
          },
        });
      });
      this.notifyPresenceChange();
    }, 1000);

    // Simulate cursor movement
    setInterval(() => {
      if (!this.connected) return;

      demoUsers.forEach(user => {
        const presence = this.presence.get(user.id);
        if (presence && Math.random() > 0.7) {
          this.presence.set(user.id, {
            ...presence,
            cursor: {
              x: Math.random() * 800,
              y: Math.random() * 600,
            },
            lastSeen: new Date().toISOString(),
          });
        }
      });
      this.notifyPresenceChange();
    }, 2000);
  }
}

// Singleton instance
export const mockCollaborationProvider = new MockCollaborationProvider();
if (typeof window !== 'undefined') {
  (window as unknown).mockCollaborationProvider = mockCollaborationProvider;
}
