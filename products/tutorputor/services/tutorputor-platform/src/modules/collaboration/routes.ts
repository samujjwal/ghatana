import type { FastifyPluginAsync } from "fastify";
import { CollaborationServiceImpl } from "./service";
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
  requireOwnership,
  requireTenantAccess,
  respondWithErrors,
} from "../../core/http/requestContext.js";

/**
 * Collaboration routes - discussion threads and shared notes.
 *
 * @doc.type routes
 * @doc.purpose HTTP endpoints for collaborative learning features
 * @doc.layer product
 * @doc.pattern REST API
 * @doc.gaa.security Resource ownership verified for all write operations
 */
export const collaborationRoutes: FastifyPluginAsync = async (app) => {
  const collaborationService = new CollaborationServiceImpl(app.prisma);

  /**
   * POST /threads
   * Post a new question/discussion thread
   */
  app.post("/threads", async (request, reply) => {
    const tenantId = getTenantId(request);
    const userId = getUserId(request);
    const { authorName, moduleId, title, content } = request.body as {
      authorName: string;
      moduleId?: ModuleId;
      title: string;
      content: string;
    };

    if (!title || !content || !authorName) {
      return reply
        .code(400)
        .send({ error: "Title, content, and author name are required" });
    }

    await respondWithErrors(reply, () =>
      collaborationService.postQuestion({
        tenantId: tenantId as TenantId,
        userId: userId as UserId,
        authorName,
        moduleId,
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
    const { moduleId, status, cursor, limit } = request.query as {
      moduleId?: ModuleId;
      status?: "OPEN" | "RESOLVED" | "CLOSED";
      cursor?: ThreadId;
      limit?: string;
    };

    await respondWithErrors(reply, () =>
      collaborationService.listThreads({
        tenantId: tenantId as TenantId,
        moduleId,
        status,
        cursor,
        limit: limit ? parseInt(limit, 10) : 20,
      }),
    );
  });

  /**
   * GET /threads/:threadId
   * Get thread details with all posts (tenant-scoped)
   */
  app.get("/threads/:threadId", async (request, reply) => {
    const tenantId = getTenantId(request);
    const { threadId } = request.params as { threadId: ThreadId };

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
    const { threadId } = request.params as { threadId: ThreadId };
    const { authorName, content } = request.body as {
      authorName: string;
      content: string;
    };

    if (!content || !authorName) {
      return reply
        .code(400)
        .send({ error: "Content and author name are required" });
    }

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
    const { threadId } = request.params as { threadId: ThreadId };
    const { postId } = request.body as { postId: PostId };

    if (!postId) {
      return reply.code(400).send({ error: "Post ID is required" });
    }

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
    const { threadId } = request.params as { threadId: ThreadId };

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
    const {
      title,
      content,
      moduleId,
      lessonId,
      studyGroupId,
      allowEditing,
      allowComments,
    } = request.body as {
      title: string;
      content: string;
      moduleId?: ModuleId;
      lessonId?: string;
      studyGroupId?: string;
      allowEditing?: boolean;
      allowComments?: boolean;
    };

    if (!title || !content) {
      return reply.code(400).send({ error: "Title and content are required" });
    }

    await respondWithErrors(reply, () =>
      collaborationService.createSharedNote({
        tenantId: tenantId as TenantId,
        createdBy: userId as UserId,
        title,
        content,
        moduleId,
        lessonId,
        studyGroupId,
        allowEditing,
        allowComments,
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
    const { noteId } = request.params as { noteId: string };

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
    const { noteId } = request.params as { noteId: string };
    const { content } = request.body as { content: string };

    if (!content) {
      return reply.code(400).send({ error: "Content is required" });
    }

    await respondWithErrors(reply, () =>
      collaborationService.updateSharedNote({
        tenantId: tenantId as TenantId,
        noteId,
        editorId: userId as UserId,
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
    const { noteId } = request.params as { noteId: string };
    const { shareWith } = request.body as {
      shareWith: Array<{
        userId: UserId;
        permission: "view" | "comment" | "edit";
      }>;
    };

    if (!shareWith || !Array.isArray(shareWith) || shareWith.length === 0) {
      return reply.code(400).send({ error: "Share with list is required" });
    }

    try {
      let note = null;
      for (const share of shareWith) {
        note = await collaborationService.shareNote({
          tenantId: tenantId as TenantId,
          noteId,
          sharedById: userId as UserId,
          userId: share.userId,
          permission: share.permission,
        });
      }
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
    const { studyGroupId, moduleId, cursor, limit } = request.query as {
      studyGroupId?: string;
      moduleId?: ModuleId;
      cursor?: string;
      limit?: string;
    };

    await respondWithErrors(reply, () =>
      collaborationService.listSharedNotes({
        tenantId: tenantId as TenantId,
        userId: userId as UserId,
        studyGroupId,
        moduleId,
        pagination: {
          cursor,
          limit: limit ? parseInt(limit, 10) : 20,
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
