/**
 * Real-time Collaboration Service
 *
 * WebSocket-based collaboration using Socket.io.
 * Supports multiplayer cursors, live editing, presence, and chat.
 *
 * @doc.type class
 * @doc.purpose WebSocket real-time collaboration
 * @doc.layer product
 * @doc.pattern Service
 */

import { createStandaloneLogger } from '@tutorputor/core/logger';

const logger = createStandaloneLogger({ component: 'CollaborationService' });

import type { Server as HTTPServer } from "http";

/**
 * User presence
 */
export interface UserPresence {
  userId: string;
  userName: string;
  avatar?: string;
  status: "active" | "idle" | "away";
  currentModuleId?: string;
  currentLessonId?: string;
  cursorPosition?: { x: number; y: number };
  lastSeen: Date;
}

/**
 * Collaboration event types
 */
export type CollaborationEventType =
  | "cursor_move"
  | "selection_change"
  | "content_edit"
  | "annotation_add"
  | "annotation_update"
  | "annotation_delete"
  | "chat_message"
  | "user_join"
  | "user_leave"
  | "user_idle"
  | "user_active";

/**
 * Base collaboration event
 */
export interface CollaborationEvent {
  id: string;
  type: CollaborationEventType;
  userId: string;
  moduleId: string;
  timestamp: Date;
  payload: unknown;
}

/**
 * Cursor move event
 */
export interface CursorMoveEvent extends CollaborationEvent {
  type: "cursor_move";
  payload: {
    x: number;
    y: number;
    elementId?: string;
  };
}

/**
 * Content edit event (Operational Transform)
 */
export interface ContentEditEvent extends CollaborationEvent {
  type: "content_edit";
  payload: {
    operation: "insert" | "delete" | "replace";
    path: string[];
    position: number;
    length?: number;
    content: string;
    revision: number;
  };
}

/**
 * Chat message
 */
export interface ChatMessageEvent extends CollaborationEvent {
  type: "chat_message";
  payload: {
    message: string;
    replyTo?: string;
    mentions: string[];
  };
}

/**
 * Annotation
 */
export interface Annotation {
  id: string;
  authorId: string;
  moduleId: string;
  targetElementId: string;
  position: { x: number; y: number };
  content: string;
  type: "comment" | "suggestion" | "question" | "highlight";
  status: "open" | "resolved" | "archived";
  replies: AnnotationReply[];
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Annotation reply
 */
export interface AnnotationReply {
  id: string;
  authorId: string;
  content: string;
  createdAt: Date;
}

/**
 * Collaboration room
 */
export interface CollaborationRoom {
  id: string;
  moduleId: string;
  participants: Map<string, UserPresence>;
  events: CollaborationEvent[];
  annotations: Map<string, Annotation>;
  chatHistory: ChatMessageEvent[];
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Socket.io server instance type
 */
type SocketServer = {
  on: (event: string, handler: (socket: Socket) => void) => void;
  to: (room: string) => { emit: (event: string, data: unknown) => void };
  emit: (event: string, data: unknown) => void;
};

type Socket = {
  id: string;
  data: { userId?: string; userName?: string };
  join: (room: string) => Promise<void>;
  leave: (room: string) => Promise<void>;
  to: (room: string) => { emit: (event: string, data: unknown) => void };
  emit: (event: string, data: unknown) => void;
  on: (event: string, handler: (...args: unknown[]) => void) => void;
  disconnect: () => void;
};

/**
 * Collaboration Service
 */
export class CollaborationService {
  private io: SocketServer | null = null;
  private rooms = new Map<string, CollaborationRoom>();
  private userSockets = new Map<string, string>(); // userId -> socketId
  private presence = new Map<string, UserPresence>();

  private onSocketEvent<T>(
    socket: Socket,
    event: string,
    handler: (data: T) => void,
  ): void {
    socket.on(event, (...args: unknown[]) => {
      const [data] = args;
      handler(data as T);
    });
  }

  /**
   * Initialize Socket.io server
   */
  async initialize(httpServer: HTTPServer): Promise<void> {
    try {
      const importSocketIo = new Function(
        'return import("socket.io")',
      ) as () => Promise<{ Server: new (server: HTTPServer, options: Record<string, unknown>) => SocketServer }>;
      const { Server } = await importSocketIo();
      this.io = new Server(httpServer, {
        cors: {
          origin: process.env.FRONTEND_URL ?? "http://localhost:3000",
          methods: ["GET", "POST"],
          credentials: true,
        },
        transports: ["websocket", "polling"],
        pingTimeout: 60000,
        pingInterval: 25000,
      }) as unknown as SocketServer;

      this.setupEventHandlers();
      console.log("[Collaboration] Socket.io server initialized");
    } catch (error) {
      throw new Error(
        `Failed to initialize Socket.io: ${error instanceof Error ? error.message : String(error)}`,
      );
    }
  }

  /**
   * Setup Socket.io event handlers
   */
  private setupEventHandlers(): void {
    if (!this.io) return;

    this.io.on("connection", (socket: Socket) => {
      logger.info(`[Collaboration] Client connected: ${socket.id}`);

      // Authenticate user
      this.onSocketEvent(socket, "authenticate", (data: { userId: string; userName: string; token: string }) => {
        this.handleAuthentication(socket, data);
      });

      // Join module room
      this.onSocketEvent(socket, "join:module", (data: { moduleId: string }) => {
        this.handleJoinModule(socket, data.moduleId);
      });

      // Leave module room
      this.onSocketEvent(socket, "leave:module", (data: { moduleId: string }) => {
        this.handleLeaveModule(socket, data.moduleId);
      });

      // Cursor move
      this.onSocketEvent(socket, "cursor:move", (data: { moduleId: string; x: number; y: number; elementId?: string }) => {
        this.handleCursorMove(socket, data);
      });

      // Content edit (with OT)
      this.onSocketEvent(socket, "content:edit", (data: {
        moduleId: string;
        operation: "insert" | "delete" | "replace";
        path: string[];
        position: number;
        length?: number;
        content: string;
        revision: number;
      }) => {
        this.handleContentEdit(socket, data);
      });

      // Add annotation
      this.onSocketEvent(socket, "annotation:add", (data: {
        moduleId: string;
        targetElementId: string;
        position: { x: number; y: number };
        content: string;
        type: Annotation["type"];
      }) => {
        this.handleAddAnnotation(socket, data);
      });

      // Update annotation
      this.onSocketEvent(socket, "annotation:update", (data: { moduleId: string; annotationId: string; content: string }) => {
        this.handleUpdateAnnotation(socket, data);
      });

      // Reply to annotation
      this.onSocketEvent(socket, "annotation:reply", (data: { moduleId: string; annotationId: string; content: string }) => {
        this.handleReplyToAnnotation(socket, data);
      });

      // Resolve annotation
      this.onSocketEvent(socket, "annotation:resolve", (data: { moduleId: string; annotationId: string }) => {
        this.handleResolveAnnotation(socket, data);
      });

      // Chat message
      this.onSocketEvent(socket, "chat:message", (data: { moduleId: string; message: string; replyTo?: string }) => {
        this.handleChatMessage(socket, data);
      });

      // User status change
      this.onSocketEvent(socket, "user:status", (data: { status: UserPresence["status"] }) => {
        this.handleStatusChange(socket, data.status);
      });

      // Disconnect
      socket.on("disconnect", () => {
        this.handleDisconnect(socket);
      });
    });
  }

  /**
   * Handle user authentication
   */
  private handleAuthentication(
    socket: Socket,
    data: { userId: string; userName: string; token: string },
  ): void {
    // Validate token (in production, verify JWT)
    // For now, accept all tokens
    socket.data.userId = data.userId;
    socket.data.userName = data.userName;
    this.userSockets.set(data.userId, socket.id);

    // Set initial presence
    this.presence.set(data.userId, {
      userId: data.userId,
      userName: data.userName,
      status: "active",
      lastSeen: new Date(),
    });

    socket.emit("authenticated", { success: true });
    logger.info(`[Collaboration] User authenticated: ${data.userId}`);
  }

  /**
   * Handle join module
   */
  private async handleJoinModule(socket: Socket, moduleId: string): Promise<void> {
    if (!socket.data.userId) {
      socket.emit("error", { message: "Not authenticated" });
      return;
    }

    const roomId = `module:${moduleId}`;
    await socket.join(roomId);

    // Get or create room
    let room = this.rooms.get(roomId);
    if (!room) {
      room = {
        id: roomId,
        moduleId,
        participants: new Map(),
        events: [],
        annotations: new Map(),
        chatHistory: [],
        createdAt: new Date(),
        updatedAt: new Date(),
      };
      this.rooms.set(roomId, room);
    }

    // Add participant
    const presence: UserPresence = {
      userId: socket.data.userId,
      userName: socket.data.userName ?? "Unknown",
      status: "active",
      currentModuleId: moduleId,
      lastSeen: new Date(),
    };
    room.participants.set(socket.data.userId, presence);
    this.presence.set(socket.data.userId, presence);

    // Send room state to new participant
    socket.emit("room:state", {
      participants: [...room.participants.values()],
      annotations: [...room.annotations.values()],
      chatHistory: room.chatHistory.slice(-50), // Last 50 messages
    });

    // Notify others
    socket.to(roomId).emit("user:joined", {
      userId: socket.data.userId,
      userName: socket.data.userName,
      timestamp: new Date(),
    });

    console.log(`[Collaboration] User ${socket.data.userId} joined module ${moduleId}`);
  }

  /**
   * Handle leave module
   */
  private async handleLeaveModule(socket: Socket, moduleId: string): Promise<void> {
    if (!socket.data.userId) return;

    const roomId = `module:${moduleId}`;
    await socket.leave(roomId);

    const room = this.rooms.get(roomId);
    if (room) {
      room.participants.delete(socket.data.userId);
      room.updatedAt = new Date();

      // Notify others
      socket.to(roomId).emit("user:left", {
        userId: socket.data.userId,
        timestamp: new Date(),
      });

      // Clean up empty rooms
      if (room.participants.size === 0) {
        this.rooms.delete(roomId);
      }
    }

    logger.info(`[Collaboration] User ${socket.data.userId} left module ${moduleId}`);
  }

  /**
   * Handle cursor move
   */
  private handleCursorMove(
    socket: Socket,
    data: { moduleId: string; x: number; y: number; elementId?: string },
  ): void {
    if (!socket.data.userId) return;

    const roomId = `module:${data.moduleId}`;

    // Update presence
    const presence = this.presence.get(socket.data.userId);
    if (presence) {
      presence.cursorPosition = { x: data.x, y: data.y };
      presence.lastSeen = new Date();
    }

    // Broadcast to others in room
    socket.to(roomId).emit("cursor:update", {
      userId: socket.data.userId,
      userName: socket.data.userName,
      x: data.x,
      y: data.y,
      elementId: data.elementId,
      timestamp: new Date(),
    });
  }

  /**
   * Handle content edit with Operational Transform
   */
  private handleContentEdit(
    socket: Socket,
    data: {
      moduleId: string;
      operation: "insert" | "delete" | "replace";
      path: string[];
      position: number;
      length?: number;
      content: string;
      revision: number;
    },
  ): void {
    if (!socket.data.userId) return;

    const roomId = `module:${data.moduleId}`;
    const room = this.rooms.get(roomId);

    if (!room) return;

    // In production, apply Operational Transform here
    // For now, broadcast the operation
    const event: ContentEditEvent = {
      id: `edit-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      type: "content_edit",
      userId: socket.data.userId,
      moduleId: data.moduleId,
      timestamp: new Date(),
      payload: {
        operation: data.operation,
        path: data.path,
        position: data.position,
        content: data.content,
        revision: data.revision,
      },
    };

    if (data.length != null) {
      event.payload.length = data.length;
    }

    room.events.push(event);
    room.updatedAt = new Date();

    // Broadcast to others (exclude sender)
    socket.to(roomId).emit("content:update", event);

    // Acknowledge to sender
    socket.emit("content:ack", { revision: data.revision + 1 });
  }

  /**
   * Handle add annotation
   */
  private handleAddAnnotation(
    socket: Socket,
    data: {
      moduleId: string;
      targetElementId: string;
      position: { x: number; y: number };
      content: string;
      type: Annotation["type"];
    },
  ): void {
    if (!socket.data.userId) return;

    const roomId = `module:${data.moduleId}`;
    const room = this.rooms.get(roomId);

    if (!room) return;

    const annotation: Annotation = {
      id: `anno-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      authorId: socket.data.userId,
      moduleId: data.moduleId,
      targetElementId: data.targetElementId,
      position: data.position,
      content: data.content,
      type: data.type,
      status: "open",
      replies: [],
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    room.annotations.set(annotation.id, annotation);
    room.updatedAt = new Date();

    // Broadcast to all in room
    this.io?.to(roomId).emit("annotation:created", annotation);
  }

  /**
   * Handle update annotation
   */
  private handleUpdateAnnotation(
    socket: Socket,
    data: { moduleId: string; annotationId: string; content: string },
  ): void {
    if (!socket.data.userId) return;

    const roomId = `module:${data.moduleId}`;
    const room = this.rooms.get(roomId);

    if (!room) return;

    const annotation = room.annotations.get(data.annotationId);
    if (!annotation) return;

    // Only author can update
    if (annotation.authorId !== socket.data.userId) {
      socket.emit("error", { message: "Not authorized to update this annotation" });
      return;
    }

    annotation.content = data.content;
    annotation.updatedAt = new Date();
    room.updatedAt = new Date();

    this.io?.to(roomId).emit("annotation:updated", annotation);
  }

  /**
   * Handle reply to annotation
   */
  private handleReplyToAnnotation(
    socket: Socket,
    data: { moduleId: string; annotationId: string; content: string },
  ): void {
    if (!socket.data.userId) return;

    const roomId = `module:${data.moduleId}`;
    const room = this.rooms.get(roomId);

    if (!room) return;

    const annotation = room.annotations.get(data.annotationId);
    if (!annotation) return;

    const reply: AnnotationReply = {
      id: `reply-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      authorId: socket.data.userId,
      content: data.content,
      createdAt: new Date(),
    };

    annotation.replies.push(reply);
    annotation.updatedAt = new Date();
    room.updatedAt = new Date();

    this.io?.to(roomId).emit("annotation:reply:added", {
      annotationId: annotation.id,
      reply,
    });
  }

  /**
   * Handle resolve annotation
   */
  private handleResolveAnnotation(
    socket: Socket,
    data: { moduleId: string; annotationId: string },
  ): void {
    if (!socket.data.userId) return;

    const roomId = `module:${data.moduleId}`;
    const room = this.rooms.get(roomId);

    if (!room) return;

    const annotation = room.annotations.get(data.annotationId);
    if (!annotation) return;

    annotation.status = "resolved";
    annotation.updatedAt = new Date();
    room.updatedAt = new Date();

    this.io?.to(roomId).emit("annotation:resolved", {
      annotationId: annotation.id,
      resolvedBy: socket.data.userId,
      timestamp: new Date(),
    });
  }

  /**
   * Handle chat message
   */
  private handleChatMessage(
    socket: Socket,
    data: { moduleId: string; message: string; replyTo?: string },
  ): void {
    if (!socket.data.userId) return;

    const roomId = `module:${data.moduleId}`;
    const room = this.rooms.get(roomId);

    if (!room) return;

    // Extract mentions (@username)
    const mentionRegex = /@(\w+)/g;
    const mentions: string[] = [];
    let match;
    while ((match = mentionRegex.exec(data.message)) !== null) {
      const mention = match[1];
      if (mention) {
        mentions.push(mention);
      }
    }

    const event: ChatMessageEvent = {
      id: `chat-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      type: "chat_message",
      userId: socket.data.userId,
      moduleId: data.moduleId,
      timestamp: new Date(),
      payload: {
        message: data.message,
        mentions,
      },
    };

    if (data.replyTo) {
      event.payload.replyTo = data.replyTo;
    }

    room.chatHistory.push(event);
    room.updatedAt = new Date();

    // Trim chat history to last 1000 messages
    if (room.chatHistory.length > 1000) {
      room.chatHistory = room.chatHistory.slice(-1000);
    }

    // Broadcast to all in room
    this.io?.to(roomId).emit("chat:message", event);

    // Notify mentioned users
    for (const mention of mentions) {
      const mentionedSocketId = this.userSockets.get(mention);
      if (mentionedSocketId) {
        // Send notification to mentioned user even if in different room
        // Implementation would go here
      }
    }
  }

  /**
   * Handle status change
   */
  private handleStatusChange(
    socket: Socket,
    status: UserPresence["status"],
  ): void {
    if (!socket.data.userId) return;

    const presence = this.presence.get(socket.data.userId);
    if (presence) {
      presence.status = status;
      presence.lastSeen = new Date();

      // Broadcast to all rooms this user is in
      for (const room of this.rooms.values()) {
        if (room.participants.has(socket.data.userId)) {
          this.io?.to(room.id).emit("user:status:changed", {
            userId: socket.data.userId,
            status,
            timestamp: new Date(),
          });
        }
      }
    }
  }

  /**
   * Handle disconnect
   */
  private handleDisconnect(socket: Socket): void {
    if (!socket.data.userId) return;

    console.log(`[Collaboration] Client disconnected: ${socket.id}`);

    // Update presence
    const presence = this.presence.get(socket.data.userId);
    if (presence) {
      presence.status = "away";
      presence.lastSeen = new Date();
    }

    // Remove from all rooms
    for (const room of this.rooms.values()) {
      if (room.participants.has(socket.data.userId)) {
        room.participants.delete(socket.data.userId);
        room.updatedAt = new Date();

        // Notify others
        this.io?.to(room.id).emit("user:left", {
          userId: socket.data.userId,
          timestamp: new Date(),
        });
      }
    }

    // Clean up
    this.userSockets.delete(socket.data.userId);
  }

  /**
   * Get room information
   */
  getRoomInfo(roomId: string): {
    participantCount: number;
    annotationCount: number;
    messageCount: number;
    createdAt: Date;
    updatedAt: Date;
  } | null {
    const room = this.rooms.get(roomId);
    if (!room) return null;

    return {
      participantCount: room.participants.size,
      annotationCount: room.annotations.size,
      messageCount: room.chatHistory.length,
      createdAt: room.createdAt,
      updatedAt: room.updatedAt,
    };
  }

  /**
   * Get online users count
   */
  getOnlineUsersCount(): number {
    return this.presence.size;
  }

  /**
   * Get active rooms count
   */
  getActiveRoomsCount(): number {
    return [...this.rooms.values()].filter((r) => r.participants.size > 0).length;
  }

  /**
   * Broadcast message to all connected clients
   */
  broadcast(event: string, data: unknown): void {
    this.io?.emit(event, data);
  }

  /**
   * Graceful shutdown
   */
  async shutdown(): Promise<void> {
    // Notify all clients of shutdown
    this.io?.emit("server:shutdown", { message: "Server is restarting" });

    // Give clients time to handle shutdown
    await new Promise((resolve) => setTimeout(resolve, 1000));

    logger.info("[Collaboration] Service shut down");
  }
}

/**
 * Factory function
 */
export function createCollaborationService(): CollaborationService {
  return new CollaborationService();
}
