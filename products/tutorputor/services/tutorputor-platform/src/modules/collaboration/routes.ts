import type { FastifyPluginAsync } from "fastify";
import type { CollaborationServiceImpl } from "./service";
import type {
  TenantId,
  UserId,
  ThreadId,
  PostId,
  ModuleId,
} from "@tutorputor/contracts/v1/types";
import {
  getTenantId,
  getUserId,
  respondWithErrors,
} from "../../core/http/requestContext.js";
import { z } from "zod";

const threadIdParamsSchema = z.object({
  threadId: z.string().min(1),
});

const noteIdParamsSchema = z.object({
  noteId: z.string().min(1),
});

const createThreadBodySchema = z.object({
  authorName: z.string().min(1),
  moduleId: z.string().min(1).optional(),
  title: z.string().min(1),
  content: z.string().min(1),
});

const listThreadsQuerySchema = z.object({
  moduleId: z.string().min(1).optional(),
  status: z.enum(["OPEN", "RESOLVED", "CLOSED"]).optional(),
  cursor: z.string().min(1).optional(),
  limit: z.coerce.number().int().positive().max(100).optional(),
});

const replyBodySchema = z.object({
  authorName: z.string().min(1),
  content: z.string().min(1),
});

const markAnswerBodySchema = z.object({
  postId: z.string().min(1),
});

const createNoteBodySchema = z.object({
  title: z.string().min(1),
  content: z.string().min(1),
  moduleId: z.string().min(1).optional(),
  lessonId: z.string().min(1).optional(),
  studyGroupId: z.string().min(1).optional(),
  allowEditing: z.boolean().optional(),
  allowComments: z.boolean().optional(),
});

const updateNoteBodySchema = z.object({
  content: z.string().min(1),
});

const shareNoteBodySchema = z.object({
  shareWith: z
    .array(
      z.object({
        userId: z.string().min(1),
        permission: z.enum(["view", "comment", "edit"]),
      }),
    )
    .min(1),
});

const listNotesQuerySchema = z.object({
  studyGroupId: z.string().min(1).optional(),
  moduleId: z.string().min(1).optional(),
  cursor: z.string().min(1).optional(),
  limit: z.coerce.number().int().positive().max(100).optional(),
});

function sendValidationError(
  reply: { code: (statusCode: number) => { send: (payload: unknown) => void } },
  error: z.ZodError,
): void {
  reply.code(400).send({
    error: "Invalid request payload",
    issues: error.issues,
  });
}

/**
 * Collaboration routes - discussion threads and shared notes.
 *
 * @doc.type routes
 * @doc.purpose HTTP endpoints for collaborative learning features
 * @doc.layer product
 * @doc.pattern REST API
 * @doc.gaa.security Resource ownership verified for all write operations
 */
export const collaborationRoutes: FastifyPluginAsync<{
  service?: CollaborationServiceImpl;
}> = async (app, options) => {
  const collaborationService = options.service
    ? options.service
    : new (await import("./service")).CollaborationServiceImpl(app.prisma);

  /**
   * POST /threads
   * Post a new question/discussion thread
   */
  app.post("/threads", async (request, reply) => {
    const tenantId = getTenantId(request);
    const userId = getUserId(request);
    const bodyResult = createThreadBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      sendValidationError(reply, bodyResult.error);
      return;
    }
    const { authorName, moduleId, title, content } = bodyResult.data;

    await respondWithErrors(reply, () =>
      collaborationService.postQuestion({
        tenantId: tenantId as TenantId,
        userId: userId as UserId,
        authorName,
        ...(moduleId ? { moduleId } : {}),
        title,
        content,
      }),
    );
  });

  /**
   * GET /threads
   * List discussion threads with filters (scoped to tenant)
   */
  app.get("/threads", async (request, reply) => {
    const tenantId = getTenantId(request);
    const queryResult = listThreadsQuerySchema.safeParse(request.query);
    if (!queryResult.success) {
      sendValidationError(reply, queryResult.error);
      return;
    }
    const { moduleId, status, cursor, limit } = queryResult.data;

    await respondWithErrors(reply, () =>
      collaborationService.listThreads({
        tenantId: tenantId as TenantId,
        ...(moduleId ? { moduleId } : {}),
        ...(status ? { status } : {}),
        ...(cursor ? { cursor } : {}),
        limit: limit ?? 20,
      }),
    );
  });

  /**
   * GET /threads/:threadId
   * Get thread details with all posts (tenant-scoped)
   */
  app.get("/threads/:threadId", async (request, reply) => {
    const tenantId = getTenantId(request);
    const paramsResult = threadIdParamsSchema.safeParse(request.params);
    if (!paramsResult.success) {
      sendValidationError(reply, paramsResult.error);
      return;
    }
    const { threadId } = paramsResult.data;

    await respondWithErrors(reply, () =>
      collaborationService.getThread({
        tenantId: tenantId as TenantId,
        threadId,
      }),
    );
  });

  /**
   * POST /threads/:threadId/reply
   * Reply to a discussion thread (any authenticated user)
   */
  app.post("/threads/:threadId/reply", async (request, reply) => {
    const tenantId = getTenantId(request);
    const userId = getUserId(request);
    const paramsResult = threadIdParamsSchema.safeParse(request.params);
    if (!paramsResult.success) {
      sendValidationError(reply, paramsResult.error);
      return;
    }
    const bodyResult = replyBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      sendValidationError(reply, bodyResult.error);
      return;
    }
    const { threadId } = paramsResult.data;
    const { authorName, content } = bodyResult.data;

    await respondWithErrors(reply, () =>
      collaborationService.reply({
        tenantId: tenantId as TenantId,
        userId: userId as UserId,
        authorName,
        threadId,
        content,
      }),
    );
  });

  /**
   * POST /threads/:threadId/mark-answer
   * Mark a post as the answer to the question (thread owner only)
   */
  app.post("/threads/:threadId/mark-answer", async (request, reply) => {
    const tenantId = getTenantId(request);
    const userId = getUserId(request);
    const paramsResult = threadIdParamsSchema.safeParse(request.params);
    if (!paramsResult.success) {
      sendValidationError(reply, paramsResult.error);
      return;
    }
    const bodyResult = markAnswerBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      sendValidationError(reply, bodyResult.error);
      return;
    }
    const { threadId } = paramsResult.data;
    const { postId } = bodyResult.data;

    await respondWithErrors(reply, () =>
      collaborationService.markAsAnswer({
        tenantId: tenantId as TenantId,
        userId: userId as UserId,
        threadId,
        postId,
      }),
    );
  });

  /**
   * POST /threads/:threadId/close
   * Close a discussion thread (thread owner only)
   */
  app.post("/threads/:threadId/close", async (request, reply) => {
    const tenantId = getTenantId(request);
    const userId = getUserId(request);
    const paramsResult = threadIdParamsSchema.safeParse(request.params);
    if (!paramsResult.success) {
      sendValidationError(reply, paramsResult.error);
      return;
    }
    const { threadId } = paramsResult.data;

    await respondWithErrors(reply, () =>
      collaborationService.closeThread({
        tenantId: tenantId as TenantId,
        userId: userId as UserId,
        threadId,
      }),
    );
  });

  /**
   * POST /notes
   * Create a shared note
   */
  app.post("/notes", async (request, reply) => {
    const tenantId = getTenantId(request);
    const userId = getUserId(request);
    const bodyResult = createNoteBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      sendValidationError(reply, bodyResult.error);
      return;
    }
    const {
      title,
      content,
      moduleId,
      lessonId,
      studyGroupId,
      allowEditing,
      allowComments,
    } = bodyResult.data;

    await respondWithErrors(reply, () =>
      collaborationService.createSharedNote({
        tenantId: tenantId as TenantId,
        createdBy: userId as UserId,
        title,
        content,
        ...(moduleId ? { moduleId } : {}),
        ...(lessonId ? { lessonId } : {}),
        ...(studyGroupId ? { studyGroupId } : {}),
        ...(allowEditing !== undefined ? { allowEditing } : {}),
        ...(allowComments !== undefined ? { allowComments } : {}),
      }),
    );
  });

  /**
   * GET /notes/:noteId
   * Get shared note details (tenant-scoped, user has access)
   */
  app.get("/notes/:noteId", async (request, reply) => {
    const tenantId = getTenantId(request);
    const userId = getUserId(request);
    const paramsResult = noteIdParamsSchema.safeParse(request.params);
    if (!paramsResult.success) {
      sendValidationError(reply, paramsResult.error);
      return;
    }
    const { noteId } = paramsResult.data;

    await respondWithErrors(reply, () =>
      collaborationService.getSharedNote({
        tenantId: tenantId as TenantId,
        noteId,
        userId: userId as UserId,
      }),
    );
  });

  /**
   * PATCH /notes/:noteId
   * Update shared note content (note creator or collaborator with edit permission only)
   */
  app.patch("/notes/:noteId", async (request, reply) => {
    const tenantId = getTenantId(request);
    const userId = getUserId(request);
    const paramsResult = noteIdParamsSchema.safeParse(request.params);
    if (!paramsResult.success) {
      sendValidationError(reply, paramsResult.error);
      return;
    }
    const bodyResult = updateNoteBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      sendValidationError(reply, bodyResult.error);
      return;
    }
    const { noteId } = paramsResult.data;
    const { content } = bodyResult.data;

    await respondWithErrors(reply, () =>
      collaborationService.updateSharedNote({
        tenantId: tenantId as TenantId,
        noteId,
        userId: userId as UserId,
        content,
      }),
    );
  });

  /**
   * POST /notes/:noteId/share
   * Share note with other users (note owner only)
   */
  app.post("/notes/:noteId/share", async (request, reply) => {
    const tenantId = getTenantId(request);
    const userId = getUserId(request);
    const paramsResult = noteIdParamsSchema.safeParse(request.params);
    if (!paramsResult.success) {
      sendValidationError(reply, paramsResult.error);
      return;
    }
    const bodyResult = shareNoteBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      sendValidationError(reply, bodyResult.error);
      return;
    }
    const { noteId } = paramsResult.data;
    const { shareWith } = bodyResult.data;

    try {
      const note = await collaborationService.shareNote({
        tenantId: tenantId as TenantId,
        noteId,
        sharedBy: userId as UserId,
        shareWith,
      });
      return reply.code(200).send(note);
    } catch (error) {
      return reply
        .code(
          error instanceof Error && error.message.includes("permission")
            ? 403
            : 500,
        )
        .send({
          error:
            error instanceof Error ? error.message : "Failed to share note",
        });
    }
  });

  /**
   * GET /notes
   * List shared notes accessible to current user (tenant-scoped)
   */
  app.get("/notes", async (request, reply) => {
    const tenantId = getTenantId(request);
    const userId = getUserId(request);
    const queryResult = listNotesQuerySchema.safeParse(request.query);
    if (!queryResult.success) {
      sendValidationError(reply, queryResult.error);
      return;
    }
    const { studyGroupId, moduleId, cursor, limit } = queryResult.data;

    await respondWithErrors(reply, () =>
      collaborationService.listSharedNotes({
        tenantId: tenantId as TenantId,
        userId: userId as UserId,
        ...(studyGroupId ? { studyGroupId } : {}),
        ...(moduleId ? { moduleId } : {}),
        pagination: {
          ...(cursor ? { cursor } : {}),
          limit: limit ?? 20,
        },
      }),
    );
  });

  /**
   * GET /health
   * Health check endpoint
   */
  app.get("/health", async () => {
    return { status: "healthy", module: "collaboration" };
  });

  app.log.info("Collaboration routes registered");
};
