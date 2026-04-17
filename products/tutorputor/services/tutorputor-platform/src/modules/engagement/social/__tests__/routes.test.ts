import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { socialRoutes } from "../routes";

describe("socialRoutes", () => {
  const studyGroupService = {
    createGroup: vi.fn(),
    getGroup: vi.fn(),
    joinGroup: vi.fn(),
  };

  const forumService = {
    createForum: vi.fn(),
    listForums: vi.fn(),
    createTopic: vi.fn(),
    listTopics: vi.fn(),
  };

  const peerTutoringService = {
    scheduleSession: vi.fn(),
    listSessions: vi.fn(),
  };

  const chatService = {
    getOrCreateRoom: vi.fn(),
    listRooms: vi.fn(),
    sendMessage: vi.fn(),
    getMessages: vi.fn(),
  };

  let app: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    vi.clearAllMocks();

    studyGroupService.createGroup.mockResolvedValue({ id: "group-1" });
    forumService.createForum.mockResolvedValue({ id: "forum-1" });
    forumService.listForums.mockResolvedValue({ items: [] });
    forumService.createTopic.mockResolvedValue({ id: "topic-1" });
    forumService.listTopics.mockResolvedValue({ items: [] });
    peerTutoringService.scheduleSession.mockResolvedValue({ id: "session-1" });
    peerTutoringService.listSessions.mockResolvedValue({ items: [] });
    chatService.getOrCreateRoom.mockResolvedValue({ id: "room-1" });
    chatService.listRooms.mockResolvedValue({ items: [] });
    chatService.sendMessage.mockResolvedValue({ id: "msg-1" });
    chatService.getMessages.mockResolvedValue({ items: [] });

    app = Fastify();
    app.decorate("redis", {});
    app.decorate("prisma", {
      user: {
        findFirst: vi.fn().mockResolvedValue({
          id: "user-1",
          displayName: "User One",
          email: "user@example.com",
          role: "student",
          createdAt: new Date(),
        }),
      },
      socialActivity: {
        findMany: vi.fn().mockResolvedValue([]),
        create: vi.fn().mockResolvedValue({ id: "activity-1" }),
      },
    });

    await app.register(socialRoutes, {
      studyGroupService: studyGroupService as never,
      forumService: forumService as never,
      peerTutoringService: peerTutoringService as never,
      chatService: chatService as never,
    });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects malformed study group payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/study-groups",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        name: "",
        description: "desc",
        visibility: "public",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(studyGroupService.createGroup).not.toHaveBeenCalled();
  });

  it("rejects malformed forum topic payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/forums/forum-1/topics",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        title: "Topic",
        content: "",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(forumService.createTopic).not.toHaveBeenCalled();
  });

  it("rejects malformed peer tutoring payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/peer-tutoring/sessions",
      headers: {
        "x-tenant-id": "tenant-1",
      },
      payload: {
        requestId: "req-1",
        tutorId: "tutor-1",
        scheduledAt: "bad-date",
        duration: 30,
        type: "video_call",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(peerTutoringService.scheduleSession).not.toHaveBeenCalled();
  });

  it("rejects malformed chat message payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/chat/rooms/room-1/messages",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        content: "",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(chatService.sendMessage).not.toHaveBeenCalled();
  });

  it("forwards valid study group payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/study-groups",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        name: "Group A",
        description: "Test group",
        visibility: "public",
        subjects: ["math"],
      },
    });

    expect(response.statusCode).toBe(201);
    expect(studyGroupService.createGroup).toHaveBeenCalledTimes(1);
  });

  it("rejects malformed feed query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/feed?limit=0",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(400);
  });
});
