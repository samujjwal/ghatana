import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { WebSocket } from "ws";

import {
  RealTimeCollaboration,
  type CollaborationParticipant,
  type CursorPosition,
} from "../real-time-cursor.js";

type ParticipantJoinedMessage = {
  type: "participant_joined";
  participant: CollaborationParticipant;
};

type ParticipantLeftMessage = {
  type: "participant_left";
  userId: string;
};

type CursorUpdateMessage = {
  type: "cursor_update";
  cursor: CursorPosition;
};

type CollaborationMessage =
  | ParticipantJoinedMessage
  | ParticipantLeftMessage
  | CursorUpdateMessage;

type SocketEvent = "close" | "error";
type SocketListener = (...args: readonly unknown[]) => void;

class MockWebSocket {
  public readyState = 1;
  public sentMessages: CollaborationMessage[] = [];
  private listeners = new Map<SocketEvent, SocketListener[]>();

  on(event: SocketEvent, listener: SocketListener): this {
    const existingListeners = this.listeners.get(event) ?? [];
    existingListeners.push(listener);
    this.listeners.set(event, existingListeners);
    return this;
  }

  send(payload: string): void {
    this.sentMessages.push(JSON.parse(payload) as CollaborationMessage);
  }

  close(): void {
    if (this.readyState === 3) {
      return;
    }

    this.readyState = 3;
    this.emit("close");
  }

  clearMessages(): void {
    this.sentMessages = [];
  }

  private emit(event: SocketEvent, ...args: readonly unknown[]): void {
    for (const listener of this.listeners.get(event) ?? []) {
      listener(...args);
    }
  }
}

function createParticipant(
  userId: string,
  userName: string,
): Omit<CollaborationParticipant, "joinedAt" | "isActive" | "cursor"> {
  return {
    userId,
    userName,
    userColor: RealTimeCollaboration.generateUserColor(userId),
    permissions: {
      canEdit: true,
      canComment: true,
      canDelete: false,
    },
  };
}

function createCursor(
  userId: string,
  userName: string,
  x: number,
  y: number,
  cursorType: CursorPosition["cursorType"] = "pointer",
): Omit<CursorPosition, "timestamp"> {
  return {
    x,
    y,
    userId,
    userName,
    userColor: RealTimeCollaboration.generateUserColor(userId),
    cursorType,
  };
}

function isParticipantJoinedMessage(
  message: CollaborationMessage,
): message is ParticipantJoinedMessage {
  return message.type === "participant_joined";
}

function isParticipantLeftMessage(
  message: CollaborationMessage,
): message is ParticipantLeftMessage {
  return message.type === "participant_left";
}

function isCursorUpdateMessage(
  message: CollaborationMessage,
): message is CursorUpdateMessage {
  return message.type === "cursor_update";
}

describe("RealTimeCollaboration", () => {
  let service: RealTimeCollaboration;

  beforeEach(() => {
    vi.useFakeTimers();
    service = new RealTimeCollaboration();
  });

  afterEach(() => {
    service.destroy();
    vi.useRealTimers();
  });

  it("tracks session membership and broadcasts participant joins", async () => {
    const aliceSocket = new MockWebSocket();
    const bobSocket = new MockWebSocket();

    service.registerWebSocket("user-1", aliceSocket as unknown as WebSocket);
    await service.joinSession(
      "session-1",
      "content-1",
      createParticipant("user-1", "Alice"),
    );

    aliceSocket.clearMessages();

    service.registerWebSocket("user-2", bobSocket as unknown as WebSocket);
    await service.joinSession(
      "session-1",
      "content-1",
      createParticipant("user-2", "Bob"),
    );

    const aliceJoinMessage = aliceSocket.sentMessages.find(
      isParticipantJoinedMessage,
    );
    const bobJoinMessage = bobSocket.sentMessages.find(
      isParticipantJoinedMessage,
    );
    const session = await service.getSessionState("session-1");

    expect(aliceJoinMessage?.participant.userId).toBe("user-2");
    expect(bobJoinMessage?.participant.userId).toBe("user-2");
    expect(session?.contentId).toBe("content-1");
    expect(session?.participants.size).toBe(2);
    expect(await service.getUserSessions("user-2")).toEqual(["session-1"]);
  });

  it("broadcasts the latest cursor position once per throttle window", async () => {
    const aliceSocket = new MockWebSocket();
    const bobSocket = new MockWebSocket();

    service.registerWebSocket("user-1", aliceSocket as unknown as WebSocket);
    service.registerWebSocket("user-2", bobSocket as unknown as WebSocket);

    await service.joinSession(
      "session-1",
      "content-1",
      createParticipant("user-1", "Alice"),
    );
    await service.joinSession(
      "session-1",
      "content-1",
      createParticipant("user-2", "Bob"),
    );

    aliceSocket.clearMessages();
    bobSocket.clearMessages();

    await service.updateCursor(
      "user-1",
      createCursor("user-1", "Alice", 10, 20),
    );
    await service.updateCursor(
      "user-1",
      createCursor("user-1", "Alice", 30, 40, "move"),
    );

    expect(aliceSocket.sentMessages.filter(isCursorUpdateMessage)).toHaveLength(
      0,
    );
    expect(bobSocket.sentMessages.filter(isCursorUpdateMessage)).toHaveLength(
      0,
    );

    vi.advanceTimersByTime(50);

    const aliceCursorMessages = aliceSocket.sentMessages.filter(
      isCursorUpdateMessage,
    );
    const bobCursorMessages = bobSocket.sentMessages.filter(
      isCursorUpdateMessage,
    );
    const session = await service.getSessionState("session-1");

    expect(aliceCursorMessages).toHaveLength(1);
    expect(bobCursorMessages).toHaveLength(1);
    expect(aliceCursorMessages[0]?.cursor.x).toBe(30);
    expect(aliceCursorMessages[0]?.cursor.y).toBe(40);
    expect(aliceCursorMessages[0]?.cursor.cursorType).toBe("move");
    expect(bobCursorMessages[0]?.cursor.x).toBe(30);
    expect(session?.cursors.get("user-1")?.x).toBe(30);
  });

  it("removes participants on socket close and cleans up empty sessions", async () => {
    const aliceSocket = new MockWebSocket();
    const bobSocket = new MockWebSocket();

    service.registerWebSocket("user-1", aliceSocket as unknown as WebSocket);
    service.registerWebSocket("user-2", bobSocket as unknown as WebSocket);

    await service.joinSession(
      "session-1",
      "content-1",
      createParticipant("user-1", "Alice"),
    );
    await service.joinSession(
      "session-1",
      "content-1",
      createParticipant("user-2", "Bob"),
    );

    aliceSocket.clearMessages();

    bobSocket.close();

    const leaveMessage = aliceSocket.sentMessages.find(
      isParticipantLeftMessage,
    );
    const statsAfterBobLeaves = await service.getStats();

    expect(leaveMessage?.userId).toBe("user-2");
    expect(await service.getUserSessions("user-2")).toEqual([]);
    expect(statsAfterBobLeaves.totalSessions).toBe(1);
    expect(statsAfterBobLeaves.totalParticipants).toBe(1);
    expect(statsAfterBobLeaves.activeParticipants).toBe(1);

    aliceSocket.close();

    const statsAfterAliceLeaves = await service.getStats();

    expect(await service.getSessionState("session-1")).toBeNull();
    expect(statsAfterAliceLeaves.totalSessions).toBe(0);
    expect(statsAfterAliceLeaves.totalParticipants).toBe(0);
    expect(statsAfterAliceLeaves.activeParticipants).toBe(0);
  });

  it("cleans up sockets and pending cursor timers on destroy", async () => {
    const aliceSocket = new MockWebSocket();

    service.registerWebSocket("user-1", aliceSocket as unknown as WebSocket);
    await service.joinSession(
      "session-1",
      "content-1",
      createParticipant("user-1", "Alice"),
    );

    aliceSocket.clearMessages();

    await service.updateCursor(
      "user-1",
      createCursor("user-1", "Alice", 5, 15),
    );

    service.destroy();
    vi.runOnlyPendingTimers();

    const stats = await service.getStats();

    expect(aliceSocket.readyState).toBe(3);
    expect(aliceSocket.sentMessages.filter(isCursorUpdateMessage)).toHaveLength(
      0,
    );
    expect(stats.totalSessions).toBe(0);
    expect(stats.totalParticipants).toBe(0);
    expect(stats.activeParticipants).toBe(0);
  });
});
