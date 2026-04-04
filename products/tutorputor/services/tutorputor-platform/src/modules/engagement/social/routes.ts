import type { FastifyPluginAsync } from "fastify";
import type { ModuleId, TenantId, UserId } from "@tutorputor/contracts";
import type {
  ChatMessageType,
  ChatRoomType,
  ForumScope,
  StudyGroupVisibility,
  TutoringSession,
  TutoringSessionType,
} from "@tutorputor/contracts/v1/social";
import { StudyGroupServiceImpl } from "./study-groups";
import { ForumServiceImpl } from "./forums";
import { PeerTutoringServiceImpl } from "./peer-tutoring";
import { ChatServiceImpl } from "./chat";
import {
  getTenantId,
  getUserId,
  getUserRole,
} from "../../../core/http/requestContext.js";

/**
 * Social routes - study groups, forums, peer tutoring, chat, and profiles.
 *
 * @doc.type routes
 * @doc.purpose HTTP endpoints for social features
 * @doc.layer product
 * @doc.pattern REST API
 */
export const socialRoutes: FastifyPluginAsync = async (app) => {
  const asRecord = (value: unknown): Record<string, unknown> =>
    typeof value === "object" && value !== null
      ? (value as Record<string, unknown>)
      : {};

  const asString = (value: unknown): string | undefined =>
    typeof value === "string" ? value : undefined;

  const asNumber = (value: unknown): number | undefined => {
    if (typeof value === "number" && Number.isFinite(value)) return value;
    if (typeof value === "string" && value.trim()) {
      const parsed = Number(value);
      return Number.isFinite(parsed) ? parsed : undefined;
    }
    return undefined;
  };

  const asStringArray = (value: unknown): string[] =>
    Array.isArray(value)
      ? value.filter((item): item is string => typeof item === "string")
      : [];

  const asAttachments = (
    value: unknown,
  ): Array<{ name: string; type: string; url: string }> | undefined => {
    if (!Array.isArray(value)) return undefined;
    const attachments = value
      .map((item) => asRecord(item))
      .map((item) => ({
        name: asString(item.name),
        type: asString(item.type),
        url: asString(item.url),
      }))
      .filter((item): item is { name: string; type: string; url: string } =>
        Boolean(item.name && item.type && item.url),
      );
    return attachments.length ? attachments : undefined;
  };

  const asMetadata = (value: unknown): Record<string, unknown> | undefined =>
    typeof value === "object" && value !== null
      ? (value as Record<string, unknown>)
      : undefined;

  const parseStudyGroupVisibility = (
    value: unknown,
  ): StudyGroupVisibility | undefined => {
    if (
      value === "public" ||
      value === "private" ||
      value === "classroom_only"
    ) {
      return value;
    }
    return undefined;
  };

  const parseForumScope = (value: unknown): ForumScope | undefined => {
    if (
      value === "global" ||
      value === "study_group" ||
      value === "classroom" ||
      value === "module"
    ) {
      return value;
    }
    return undefined;
  };

  const parseTutoringSessionType = (
    value: unknown,
  ): TutoringSessionType | undefined => {
    if (
      value === "text_chat" ||
      value === "video_call" ||
      value === "screen_share" ||
      value === "collaborative_whiteboard"
    ) {
      return value;
    }
    return undefined;
  };

  const parseTutoringSessionStatus = (
    value: unknown,
  ): TutoringSession["status"] | undefined => {
    if (
      value === "scheduled" ||
      value === "in_progress" ||
      value === "completed" ||
      value === "no_show" ||
      value === "cancelled"
    ) {
      return value;
    }
    return undefined;
  };

  const parseChatRoomType = (value: unknown): ChatRoomType | undefined => {
    if (
      value === "direct" ||
      value === "study_group" ||
      value === "classroom" ||
      value === "tutoring" ||
      value === "support"
    ) {
      return value;
    }
    return undefined;
  };

  const parseChatMessageType = (
    value: unknown,
  ): ChatMessageType | undefined => {
    if (
      value === "text" ||
      value === "image" ||
      value === "file" ||
      value === "code" ||
      value === "math" ||
      value === "quiz_share" ||
      value === "system"
    ) {
      return value;
    }
    return undefined;
  };

  const parseDate = (value: unknown): Date | undefined => {
    if (value instanceof Date && !Number.isNaN(value.getTime())) return value;
    if (typeof value === "string" || typeof value === "number") {
      const date = new Date(value);
      if (!Number.isNaN(date.getTime())) return date;
    }
    return undefined;
  };

  const activityPrisma = app.prisma as typeof app.prisma & {
    socialActivity: unknown;
  };
  const studyGroupService = new StudyGroupServiceImpl({
    prisma: app.prisma,
    redis: app.redis,
  });
  const forumService = new ForumServiceImpl({
    prisma: app.prisma,
    redis: app.redis,
  });
  const peerTutoringService = new PeerTutoringServiceImpl({
    prisma: app.prisma,
    redis: app.redis,
  });
  const chatService = new ChatServiceImpl({
    prisma: app.prisma,
    redis: app.redis,
  });

  // ===========================================================================
  // Study Groups
  // ===========================================================================

  app.post("/study-groups", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;
    const body = asRecord(request.body);
    const name = asString(body.name);
    const description = asString(body.description);
    const visibility = parseStudyGroupVisibility(body.visibility);
    const moduleIds = asStringArray(body.moduleIds) as ModuleId[];
    const maxMembers = asNumber(body.maxMembers);
    const requireApproval =
      typeof body.requireApproval === "boolean"
        ? body.requireApproval
        : undefined;

    try {
      if (!name || !description || !visibility) {
        return reply.code(400).send({
          error: "name, description, and visibility are required",
        });
      }

      const group = await studyGroupService.createGroup({
        tenantId,
        createdBy: userId,
        name,
        description,
        visibility,
        subjects: asStringArray(body.subjects),
        moduleIds,
        ...(maxMembers !== undefined ? { maxMembers } : {}),
        ...(requireApproval !== undefined ? { requireApproval } : {}),
      });
      return reply.code(201).send(group);
    } catch (error) {
      app.log.error(error, "Failed to create study group");
      return reply.code(500).send({
        error: "Failed to create study group",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.get("/study-groups/:id", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;
    const { id } = request.params as { id: string };

    try {
      const group = await studyGroupService.getGroup({
        tenantId,
        groupId: id,
        userId,
      });
      if (!group) return reply.code(404).send({ error: "Group not found" });
      return reply.send(group);
    } catch (error) {
      if (error instanceof Error && error.message.includes("not found")) {
        return reply.code(404).send({ error: "Group not found" });
      }
      app.log.error(error, "Failed to get study group");
      return reply.code(500).send({
        error: "Failed to get study group",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.post("/study-groups/:id/join", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;
    const { id } = request.params as { id: string };

    try {
      await studyGroupService.joinGroup({
        tenantId,
        groupId: id,
        userId,
      });
      return reply.code(200).send({ success: true });
    } catch (error) {
      app.log.error(error, "Failed to join study group");
      return reply.code(500).send({
        error: "Failed to join study group",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  // ===========================================================================
  // Forums
  // ===========================================================================

  app.post("/forums", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const body = asRecord(request.body);
    const name = asString(body.name);
    const description = asString(body.description);
    const scope = parseForumScope(body.scope);
    const scopeId = asString(body.scopeId);

    try {
      if (!name || !description || !scope) {
        return reply.code(400).send({
          error: "name, description, and scope are required",
        });
      }

      const forum = await forumService.createForum({
        tenantId,
        name,
        description,
        scope,
        ...(scopeId !== undefined ? { scopeId } : {}),
      });
      return reply.code(201).send(forum);
    } catch (error) {
      app.log.error(error, "Failed to create forum");
      return reply.code(500).send({
        error: "Failed to create forum",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.get("/forums", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const query = asRecord(request.query);

    try {
      const scope = parseForumScope(query.contextType);
      const scopeId = asString(query.contextId);
      const forums = await forumService.listForums({
        tenantId,
        ...(scope ? { scope } : {}),
        ...(scopeId !== undefined ? { scopeId } : {}),
        pagination: { limit: 100, page: 1 },
      });
      return reply.send(forums);
    } catch (error) {
      app.log.error(error, "Failed to list forums");
      return reply.code(500).send({
        error: "Failed to list forums",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.post("/forums/:forumId/topics", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;
    const { forumId } = request.params as { forumId: string };
    const body = asRecord(request.body);
    const title = asString(body.title);
    const content = asString(body.content);
    const contentFormat = asString(body.contentFormat);
    const categoryId = asString(body.categoryId);
    const attachments = asAttachments(body.attachments);

    try {
      if (!title || !content) {
        return reply
          .code(400)
          .send({ error: "title and content are required" });
      }

      const topic = await forumService.createTopic({
        tenantId,
        forumId,
        authorId: userId,
        title,
        content,
        ...(contentFormat
          ? {
              contentFormat: contentFormat as "markdown" | "html" | "plain",
            }
          : {}),
        ...(categoryId !== undefined ? { categoryId } : {}),
        ...(attachments ? { attachments } : {}),
      });
      return reply.code(201).send(topic);
    } catch (error) {
      app.log.error(error, "Failed to create topic");
      return reply.code(500).send({
        error: "Failed to create topic",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.get("/forums/:forumId/topics", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const { forumId } = request.params as { forumId: string };
    const query = asRecord(request.query);

    try {
      const result = await forumService.listTopics({
        tenantId,
        forumId,
        pagination: {
          page: asNumber(query.page) ?? 1,
          limit: asNumber(query.limit) ?? 20,
        },
      });
      return reply.send(result);
    } catch (error) {
      app.log.error(error, "Failed to get topics");
      return reply.code(500).send({
        error: "Failed to get topics",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  // ===========================================================================
  // Peer Tutoring
  // ===========================================================================

  app.post("/peer-tutoring/sessions", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const body = asRecord(request.body);
    const requestId = asString(body.requestId);
    const tutorId = asString(body.tutorId) as UserId | undefined;
    const scheduledAt = parseDate(body.scheduledAt);
    const duration = asNumber(body.duration);
    const type = parseTutoringSessionType(body.type);
    const meetingUrl = asString(body.meetingUrl);

    try {
      if (!requestId || !tutorId || !scheduledAt || !duration || !type) {
        return reply.code(400).send({
          error:
            "requestId, tutorId, scheduledAt, duration, and type are required",
        });
      }

      const session = await peerTutoringService.scheduleSession({
        tenantId,
        requestId,
        tutorId,
        scheduledAt,
        duration,
        type,
        ...(meetingUrl !== undefined ? { meetingUrl } : {}),
      });
      return reply.code(201).send(session);
    } catch (error) {
      app.log.error(error, "Failed to create peer tutoring session");
      return reply.code(500).send({
        error: "Failed to create peer tutoring session",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.get("/peer-tutoring/sessions", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;
    const query = asRecord(request.query);
    const requestedRole =
      query.role === "student" || query.role === "tutor"
        ? query.role
        : undefined;

    try {
      const status = parseTutoringSessionStatus(query.status);
      const result = await peerTutoringService.listSessions({
        tenantId,
        userId,
        ...(status !== undefined ? { status } : {}),
        ...(requestedRole !== undefined ? { role: requestedRole } : {}),
        pagination: {
          page: asNumber(query.page) ?? 1,
          limit: asNumber(query.limit) ?? 20,
        },
      });
      return reply.send(result);
    } catch (error) {
      app.log.error(error, "Failed to list peer tutoring sessions");
      return reply.code(500).send({
        error: "Failed to list peer tutoring sessions",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  // ===========================================================================
  // Chat
  // ===========================================================================

  app.post("/chat/rooms", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;
    const body = asRecord(request.body);

    try {
      const participants = asStringArray(body.participants) as UserId[];
      const studyGroupId = asString(body.studyGroupId);
      const tutoringSessionId = asString(body.tutoringSessionId);
      if (!participants.includes(userId)) {
        participants.push(userId);
      }

      const type = parseChatRoomType(body.type);
      if (!type) {
        return reply
          .code(400)
          .send({ error: "Valid chat room type is required" });
      }

      const room = await chatService.getOrCreateRoom({
        tenantId,
        createdBy: userId,
        type,
        participants,
        ...(studyGroupId !== undefined ? { studyGroupId } : {}),
        ...(tutoringSessionId !== undefined ? { tutoringSessionId } : {}),
      });
      return reply.code(200).send(room);
    } catch (error) {
      app.log.error(error, "Failed to get/create chat room");
      return reply.code(500).send({
        error: "Failed to get/create chat room",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.get("/chat/rooms", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;
    const query = asRecord(request.query);

    try {
      const roomType = parseChatRoomType(query.type);
      const result = await chatService.listRooms({
        tenantId,
        userId,
        ...(roomType !== undefined ? { type: roomType } : {}),
        pagination: {
          offset: asNumber(query.offset) ?? 0,
          limit: asNumber(query.limit) ?? 20,
        },
      });
      return reply.send(result);
    } catch (error) {
      app.log.error(error, "Failed to list chat rooms");
      return reply.code(500).send({
        error: "Failed to list chat rooms",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.post("/chat/rooms/:roomId/messages", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;
    const { roomId } = request.params as { roomId: string };
    const body = asRecord(request.body);
    const content = asString(body.content);
    const metadata = asMetadata(body.metadata);
    const replyToId = asString(body.replyToId);
    const attachments = asAttachments(body.attachments);

    try {
      if (!content) {
        return reply.code(400).send({ error: "content is required" });
      }

      const message = await chatService.sendMessage({
        tenantId,
        roomId,
        senderId: userId,
        type: parseChatMessageType(body.type) ?? "text",
        content,
        ...(metadata ? { metadata } : {}),
        ...(replyToId !== undefined ? { replyToId } : {}),
        ...(attachments ? { attachments } : {}),
      });
      return reply.code(201).send(message);
    } catch (error) {
      app.log.error(error, "Failed to send message");
      return reply.code(500).send({
        error: "Failed to send message",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.get("/chat/rooms/:roomId/messages", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;
    const { roomId } = request.params as { roomId: string };
    const query = asRecord(request.query);

    try {
      const before = asString(query.before);
      const result = await chatService.getMessages({
        tenantId,
        roomId,
        userId,
        ...(before !== undefined ? { before } : {}),
        limit: asNumber(query.limit) ?? 50,
      });
      return reply.send(result);
    } catch (error) {
      app.log.error(error, "Failed to get messages");
      return reply.code(500).send({
        error: "Failed to get messages",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  // ===========================================================================
  // Profile / Social Graph (Preserved)
  // ===========================================================================

  app.post("/follow", async (request, reply) => {
    const userId = getUserId(request) as UserId;
    const { followUserId } = request.body as { followUserId: UserId };
    if (!followUserId) {
      return reply.code(400).send({ error: "Follow user ID is required" });
    }
    if (userId === followUserId) {
      return reply.code(400).send({ error: "Cannot follow yourself" });
    }

    try {
      return reply.code(501).send({
        error: "Follow graph is not implemented in the current social module",
      });
    } catch (error) {
      app.log.error(error, "Failed to follow user");
      return reply.code(500).send({
        error: "Failed to follow user",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.delete("/follow/:followUserId", async (_request, reply) => {
    try {
      return reply.code(501).send({
        error: "Follow graph is not implemented in the current social module",
      });
    } catch (error) {
      app.log.error(error, "Failed to unfollow user");
      return reply.code(500).send({
        error: "Failed to unfollow user",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.get("/users/:userId/followers", async (_request, reply) => {
    try {
      return reply.code(501).send({
        error: "Follow graph is not implemented in the current social module",
      });
    } catch (error) {
      app.log.error(error, "Failed to get followers");
      return reply.code(500).send({
        error: "Failed to get followers",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.get("/users/:userId/following", async (_request, reply) => {
    try {
      return reply.code(501).send({
        error: "Follow graph is not implemented in the current social module",
      });
    } catch (error) {
      app.log.error(error, "Failed to get following");
      return reply.code(500).send({
        error: "Failed to get following",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.get("/users/:userId/profile", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const currentUserId = getUserId(request) as UserId;
    const currentRole = getUserRole(request);
    const { userId } = request.params as { userId: UserId };
    const canViewSensitiveFields =
      currentUserId === userId ||
      currentRole === "teacher" ||
      currentRole === "admin" ||
      currentRole === "superadmin";

    try {
      const user = await app.prisma.user.findFirst({
        where: { tenantId, id: userId },
        select: {
          id: true,
          displayName: true,
          email: true,
          role: true,
          createdAt: true,
        },
      });

      if (!user) {
        return reply.code(404).send({ error: "User not found" });
      }

      return reply.code(200).send(
        canViewSensitiveFields
          ? user
          : {
              id: user.id,
              displayName: user.displayName,
              createdAt: user.createdAt,
            },
      );
    } catch (error) {
      app.log.error(error, "Failed to get user profile");
      return reply.code(500).send({
        error: "Failed to get profile",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.get("/feed", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;
    const { limit } = request.query as { limit?: string };

    const take = limit ? Math.min(parseInt(limit, 10), 100) : 20;

    try {
      const activities = await activityPrisma.socialActivity.findMany({
        where: { tenantId, actorId: userId },
        orderBy: { createdAt: "desc" },
        take,
      });
      return reply.code(200).send(activities);
    } catch (error) {
      app.log.error(error, "Failed to get activity feed");
      return reply.code(500).send({
        error: "Failed to get feed",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.post("/activity", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;
    const { activityType, content, metadata } = request.body as {
      activityType: string;
      content: string;
      metadata?: Record<string, unknown>;
    };

    if (!activityType || !content) {
      return reply
        .code(400)
        .send({ error: "Activity type and content are required" });
    }

    try {
      const actor = await app.prisma.user.findFirst({
        where: { tenantId, id: userId },
        select: { displayName: true },
      });

      const activity = await activityPrisma.socialActivity.create({
        data: {
          tenantId,
          actorId: userId,
          actorName: actor?.displayName ?? String(userId),
          type: "HELPED_PEER",
          targetType: activityType,
          targetId: `${userId}:${Date.now()}`,
          targetTitle: content,
          metadata: JSON.stringify(metadata || {}),
        },
      });
      return reply.code(201).send(activity);
    } catch (error) {
      app.log.error(error, "Failed to post activity");
      return reply.code(500).send({
        error: "Failed to post activity",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.get("/health", async () => ({ status: "healthy", module: "social" }));

  app.log.info("Social routes registered");
};
