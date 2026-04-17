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
import type { StudyGroupServiceImpl } from "./study-groups";
import type { ForumServiceImpl } from "./forums";
import type { PeerTutoringServiceImpl } from "./peer-tutoring";
import type { ChatServiceImpl } from "./chat";
import {
  getTenantId,
  getUserId,
  getUserRole,
} from "../../../core/http/requestContext.js";
import { z } from "zod";

const idParamSchema = z.object({
  id: z.string().trim().min(1),
});

const forumIdParamSchema = z.object({
  forumId: z.string().trim().min(1),
});

const roomIdParamSchema = z.object({
  roomId: z.string().trim().min(1),
});

const userIdParamSchema = z.object({
  userId: z.string().trim().min(1),
});

const followUserIdParamSchema = z.object({
  followUserId: z.string().trim().min(1),
});

const createStudyGroupBodySchema = z.object({
  name: z.string().trim().min(1),
  description: z.string().trim().min(1),
  visibility: z.enum(["public", "private", "classroom_only"]),
  subjects: z.array(z.string()).optional(),
  moduleIds: z.array(z.string()).optional(),
  maxMembers: z.coerce.number().int().positive().optional(),
  requireApproval: z.boolean().optional(),
});

const createForumBodySchema = z.object({
  name: z.string().trim().min(1),
  description: z.string().trim().min(1),
  scope: z.enum(["global", "study_group", "classroom", "module"]),
  scopeId: z.string().optional(),
});

const forumListQuerySchema = z.object({
  contextType: z
    .enum(["global", "study_group", "classroom", "module"])
    .optional(),
  contextId: z.string().optional(),
});

const createTopicBodySchema = z.object({
  title: z.string().trim().min(1),
  content: z.string().trim().min(1),
  contentFormat: z.enum(["markdown", "html", "plain"]).optional(),
  categoryId: z.string().optional(),
  attachments: z
    .array(
      z.object({
        name: z.string().trim().min(1),
        type: z.string().trim().min(1),
        url: z.string().url(),
      }),
    )
    .optional(),
});

const pagingQuerySchema = z.object({
  page: z.coerce.number().int().positive().optional(),
  limit: z.coerce.number().int().positive().max(100).optional(),
});

const createPeerTutoringSessionBodySchema = z.object({
  requestId: z.string().trim().min(1),
  tutorId: z.string().trim().min(1),
  scheduledAt: z.coerce.date(),
  duration: z.coerce.number().int().positive(),
  type: z.enum([
    "text_chat",
    "video_call",
    "screen_share",
    "collaborative_whiteboard",
  ]),
  meetingUrl: z.string().url().optional(),
});

const peerTutoringListQuerySchema = z.object({
  role: z.enum(["student", "tutor"]).optional(),
  status: z
    .enum(["scheduled", "in_progress", "completed", "no_show", "cancelled"])
    .optional(),
  page: z.coerce.number().int().positive().optional(),
  limit: z.coerce.number().int().positive().max(100).optional(),
});

const createChatRoomBodySchema = z.object({
  type: z.enum(["direct", "study_group", "classroom", "tutoring", "support"]),
  participants: z.array(z.string()).optional(),
  studyGroupId: z.string().optional(),
  tutoringSessionId: z.string().optional(),
});

const listRoomsQuerySchema = z.object({
  type: z
    .enum(["direct", "study_group", "classroom", "tutoring", "support"])
    .optional(),
  offset: z.coerce.number().int().nonnegative().optional(),
  limit: z.coerce.number().int().positive().max(100).optional(),
});

const sendMessageBodySchema = z.object({
  type: z
    .enum(["text", "image", "file", "code", "math", "quiz_share", "system"])
    .optional(),
  content: z.string().trim().min(1),
  metadata: z.record(z.unknown()).optional(),
  replyToId: z.string().optional(),
  attachments: z
    .array(
      z.object({
        name: z.string().trim().min(1),
        type: z.string().trim().min(1),
        url: z.string().url(),
      }),
    )
    .optional(),
});

const listMessagesQuerySchema = z.object({
  before: z.string().optional(),
  limit: z.coerce.number().int().positive().max(200).optional(),
});

const followBodySchema = z.object({
  followUserId: z.string().trim().min(1),
});

const feedQuerySchema = z.object({
  limit: z.coerce.number().int().positive().max(100).optional(),
});

const activityBodySchema = z.object({
  activityType: z.string().trim().min(1),
  content: z.string().trim().min(1),
  metadata: z.record(z.unknown()).optional(),
});

function sendValidationError(reply: { code: (code: number) => { send: (payload: unknown) => unknown } }, error: z.ZodError): unknown {
  return reply.code(400).send({
    error: "Invalid request payload",
    issues: error.issues,
  });
}

/**
 * Social routes - study groups, forums, peer tutoring, chat, and profiles.
 *
 * @doc.type routes
 * @doc.purpose HTTP endpoints for social features
 * @doc.layer product
 * @doc.pattern REST API
 */
export const socialRoutes: FastifyPluginAsync<{
  studyGroupService?: StudyGroupServiceImpl;
  forumService?: ForumServiceImpl;
  peerTutoringService?: PeerTutoringServiceImpl;
  chatService?: ChatServiceImpl;
}> = async (app, options) => {
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
  const studyGroupService =
    options.studyGroupService ??
    new (await import("./study-groups")).StudyGroupServiceImpl({
      prisma: app.prisma,
      redis: app.redis,
    });
  const forumService =
    options.forumService ??
    new (await import("./forums")).ForumServiceImpl({
      prisma: app.prisma,
      redis: app.redis,
    });
  const peerTutoringService =
    options.peerTutoringService ??
    new (await import("./peer-tutoring")).PeerTutoringServiceImpl({
      prisma: app.prisma,
      redis: app.redis,
    });
  const chatService =
    options.chatService ??
    new (await import("./chat")).ChatServiceImpl({
      prisma: app.prisma,
      redis: app.redis,
    });

  // ===========================================================================
  // Study Groups
  // ===========================================================================

  app.post("/study-groups", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;
    const bodyResult = createStudyGroupBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      return sendValidationError(reply, bodyResult.error);
    }
    const {
      name,
      description,
      visibility,
      moduleIds,
      maxMembers,
      requireApproval,
      subjects,
    } = bodyResult.data;

    try {
      const group = await studyGroupService.createGroup({
        tenantId,
        createdBy: userId,
        name,
        description,
        visibility,
        subjects: subjects ?? [],
        moduleIds: (moduleIds ?? []) as ModuleId[],
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
    const paramsResult = idParamSchema.safeParse(request.params);
    if (!paramsResult.success) {
      return sendValidationError(reply, paramsResult.error);
    }
    const { id } = paramsResult.data;

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
    const paramsResult = idParamSchema.safeParse(request.params);
    if (!paramsResult.success) {
      return sendValidationError(reply, paramsResult.error);
    }
    const { id } = paramsResult.data;

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
    const bodyResult = createForumBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      return sendValidationError(reply, bodyResult.error);
    }
    const { name, description, scope, scopeId } = bodyResult.data;

    try {
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
    const queryResult = forumListQuerySchema.safeParse(request.query);
    if (!queryResult.success) {
      return sendValidationError(reply, queryResult.error);
    }
    const query = queryResult.data;

    try {
      const scope = query.contextType;
      const scopeId = query.contextId;
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
    const paramsResult = forumIdParamSchema.safeParse(request.params);
    if (!paramsResult.success) {
      return sendValidationError(reply, paramsResult.error);
    }
    const bodyResult = createTopicBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      return sendValidationError(reply, bodyResult.error);
    }
    const { forumId } = paramsResult.data;
    const { title, content, contentFormat, categoryId, attachments } =
      bodyResult.data;

    try {
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
    const paramsResult = forumIdParamSchema.safeParse(request.params);
    if (!paramsResult.success) {
      return sendValidationError(reply, paramsResult.error);
    }
    const queryResult = pagingQuerySchema.safeParse(request.query);
    if (!queryResult.success) {
      return sendValidationError(reply, queryResult.error);
    }
    const { forumId } = paramsResult.data;
    const query = queryResult.data;

    try {
      const result = await forumService.listTopics({
        tenantId,
        forumId,
        pagination: {
          page: query.page ?? 1,
          limit: query.limit ?? 20,
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
    const bodyResult = createPeerTutoringSessionBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      return sendValidationError(reply, bodyResult.error);
    }
    const { requestId, tutorId, scheduledAt, duration, type, meetingUrl } =
      bodyResult.data;

    try {
      const session = await peerTutoringService.scheduleSession({
        tenantId,
        requestId,
        tutorId: tutorId as UserId,
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
    const queryResult = peerTutoringListQuerySchema.safeParse(request.query);
    if (!queryResult.success) {
      return sendValidationError(reply, queryResult.error);
    }
    const query = queryResult.data;

    try {
      const status = query.status;
      const result = await peerTutoringService.listSessions({
        tenantId,
        userId,
        ...(status !== undefined ? { status } : {}),
        ...(query.role !== undefined ? { role: query.role } : {}),
        pagination: {
          page: query.page ?? 1,
          limit: query.limit ?? 20,
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
    const bodyResult = createChatRoomBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      return sendValidationError(reply, bodyResult.error);
    }
    const body = bodyResult.data;

    try {
      const participants = (body.participants ?? []) as UserId[];
      const studyGroupId = body.studyGroupId;
      const tutoringSessionId = body.tutoringSessionId;
      if (!participants.includes(userId)) {
        participants.push(userId);
      }

      const type = body.type;

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
    const queryResult = listRoomsQuerySchema.safeParse(request.query);
    if (!queryResult.success) {
      return sendValidationError(reply, queryResult.error);
    }
    const query = queryResult.data;

    try {
      const roomType = query.type;
      const result = await chatService.listRooms({
        tenantId,
        userId,
        ...(roomType !== undefined ? { type: roomType } : {}),
        pagination: {
          offset: query.offset ?? 0,
          limit: query.limit ?? 20,
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
    const paramsResult = roomIdParamSchema.safeParse(request.params);
    if (!paramsResult.success) {
      return sendValidationError(reply, paramsResult.error);
    }
    const bodyResult = sendMessageBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      return sendValidationError(reply, bodyResult.error);
    }
    const { roomId } = paramsResult.data;
    const { content, metadata, replyToId, attachments } = bodyResult.data;

    try {
      const message = await chatService.sendMessage({
        tenantId,
        roomId,
        senderId: userId,
        type: bodyResult.data.type ?? "text",
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
    const paramsResult = roomIdParamSchema.safeParse(request.params);
    if (!paramsResult.success) {
      return sendValidationError(reply, paramsResult.error);
    }
    const queryResult = listMessagesQuerySchema.safeParse(request.query);
    if (!queryResult.success) {
      return sendValidationError(reply, queryResult.error);
    }
    const { roomId } = paramsResult.data;
    const query = queryResult.data;

    try {
      const before = query.before;
      const result = await chatService.getMessages({
        tenantId,
        roomId,
        userId,
        ...(before !== undefined ? { before } : {}),
        limit: query.limit ?? 50,
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
    const tenantId = getTenantId(request) as TenantId;
    const bodyResult = followBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      return sendValidationError(reply, bodyResult.error);
    }
    const { followUserId } = bodyResult.data as { followUserId: UserId };
    if (userId === followUserId) {
      return reply.code(400).send({ error: "Cannot follow yourself" });
    }

    try {
      await forumService.followUser({
        tenantId,
        followerId: userId,
        followingId: followUserId,
      });
      return reply.code(204).send();
    } catch (error) {
      app.log.error(error, "Failed to follow user");
      return reply.code(500).send({
        error: "Failed to follow user",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.delete("/follow/:followUserId", async (request, reply) => {
    const paramsResult = followUserIdParamSchema.safeParse(request.params);
    if (!paramsResult.success) {
      return sendValidationError(reply, paramsResult.error);
    }
    const userId = getUserId(request) as UserId;
    const tenantId = getTenantId(request) as TenantId;
    const { followUserId } = paramsResult.data as { followUserId: UserId };

    try {
      await forumService.unfollowUser({
        tenantId,
        followerId: userId,
        followingId: followUserId,
      });
      return reply.code(204).send();
    } catch (error) {
      app.log.error(error, "Failed to unfollow user");
      return reply.code(500).send({
        error: "Failed to unfollow user",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.get("/users/:userId/followers", async (request, reply) => {
    const paramsResult = userIdParamSchema.safeParse(request.params);
    if (!paramsResult.success) {
      return sendValidationError(reply, paramsResult.error);
    }
    const tenantId = getTenantId(request) as TenantId;
    const { userId } = paramsResult.data as { userId: UserId };

    try {
      const followers = await socialService.getFollowers({
        tenantId,
        userId,
      });
      return reply.code(200).send({ followers });
    } catch (error) {
      app.log.error(error, "Failed to get followers");
      return reply.code(500).send({
        error: "Failed to get followers",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.get("/users/:userId/following", async (request, reply) => {
    const paramsResult = userIdParamSchema.safeParse(request.params);
    if (!paramsResult.success) {
      return sendValidationError(reply, paramsResult.error);
    }
    const tenantId = getTenantId(request) as TenantId;
    const { userId } = paramsResult.data as { userId: UserId };

    try {
      const following = await forumService.getFollowing({
        tenantId,
        userId,
      });
      return reply.code(200).send({ following });
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
    const paramsResult = userIdParamSchema.safeParse(request.params);
    if (!paramsResult.success) {
      return sendValidationError(reply, paramsResult.error);
    }
    const { userId } = paramsResult.data as { userId: UserId };
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
    const queryResult = feedQuerySchema.safeParse(request.query);
    if (!queryResult.success) {
      return sendValidationError(reply, queryResult.error);
    }
    const { limit } = queryResult.data;

    const take = limit ?? 20;

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
    const bodyResult = activityBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      return sendValidationError(reply, bodyResult.error);
    }
    const { activityType, content, metadata } = bodyResult.data;

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

