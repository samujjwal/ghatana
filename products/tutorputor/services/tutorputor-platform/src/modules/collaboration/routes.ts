import type { FastifyPluginAsync } from "fastify";
import { CollaborationServiceImpl } from "./service";
import type {
  TenantId,
  UserId,
  ThreadId,
  PostId,
  ModuleId,
} from "@ghatana/tutorputor-contracts/v1/types";

/**
 * Collaboration routes - discussion threads and shared notes.
 *
 * @doc.type routes
 * @doc.purpose HTTP endpoints for collaborative learning features
 * @doc.layer product
 * @doc.pattern REST API
 */
export const collaborationRoutes: FastifyPluginAsync = async (app) => {
  const collaborationService = new CollaborationServiceImpl(app.prisma);

  /**
   * POST /threads
   * Post a new question/discussion thread
   */
  app.post("/threads", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const userId = request.headers["x-user-id"] as UserId;
    const { authorName, moduleId, title, content } = request.body as {
      authorName: string;
      moduleId?: ModuleId;
      title: string;
      content: string;
    };

    if (!tenantId || !userId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!title || !content || !authorName) {
      return reply
        .code(400)
        .send({ error: "Title, content, and author name are required" });
    }

    try {
      const thread = await collaborationService.postQuestion({
        tenantId,
        userId,
        authorName,
        moduleId,
        title,
        content,
      });
      return reply.code(201).send(thread);
    } catch (error) {
      app.log.error(error, "Failed to post question");
      return reply.code(500).send({
        error: "Failed to post question",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * GET /threads
   * List discussion threads with filters
   */
  app.get("/threads", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const { moduleId, status, cursor, limit } = request.query as {
      moduleId?: ModuleId;
      status?: "OPEN" | "RESOLVED" | "CLOSED";
      cursor?: ThreadId;
      limit?: string;
    };

    if (!tenantId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    try {
      const threads = await collaborationService.listThreads({
        tenantId,
        moduleId,
        status,
        cursor,
        limit: limit ? parseInt(limit, 10) : 20,
      });
      return reply.code(200).send(threads);
    } catch (error) {
      app.log.error(error, "Failed to list threads");
      return reply.code(500).send({
        error: "Failed to list threads",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * GET /threads/:threadId
   * Get thread details with all posts
   */
  app.get("/threads/:threadId", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const { threadId } = request.params as { threadId: ThreadId };

    if (!tenantId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    try {
      const thread = await collaborationService.getThread({
        tenantId,
        threadId,
      });
      return reply.code(200).send(thread);
    } catch (error) {
      app.log.error(error, "Failed to get thread");
      if (error instanceof Error && error.message.includes("not found")) {
        return reply.code(404).send({ error: error.message });
      }
      return reply.code(500).send({
        error: "Failed to get thread",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * POST /threads/:threadId/reply
   * Reply to a discussion thread
   */
  app.post("/threads/:threadId/reply", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const userId = request.headers["x-user-id"] as UserId;
    const { threadId } = request.params as { threadId: ThreadId };
    const { authorName, content } = request.body as {
      authorName: string;
      content: string;
    };

    if (!tenantId || !userId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!content || !authorName) {
      return reply
        .code(400)
        .send({ error: "Content and author name are required" });
    }

    try {
      const post = await collaborationService.reply({
        tenantId,
        userId,
        authorName,
        threadId,
        content,
      });
      return reply.code(201).send(post);
    } catch (error) {
      app.log.error(error, "Failed to reply to thread");
      if (error instanceof Error && error.message.includes("not found")) {
        return reply.code(404).send({ error: error.message });
      }
      if (error instanceof Error && error.message.includes("closed")) {
        return reply.code(400).send({ error: error.message });
      }
      return reply.code(500).send({
        error: "Failed to reply",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * POST /threads/:threadId/mark-answer
   * Mark a post as the answer to the question
   */
  app.post("/threads/:threadId/mark-answer", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const userId = request.headers["x-user-id"] as UserId;
    const { threadId } = request.params as { threadId: ThreadId };
    const { postId } = request.body as { postId: PostId };

    if (!tenantId || !userId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!postId) {
      return reply.code(400).send({ error: "Post ID is required" });
    }

    try {
      const thread = await collaborationService.markAsAnswer({
        tenantId,
        userId,
        threadId,
        postId,
      });
      return reply.code(200).send(thread);
    } catch (error) {
      app.log.error(error, "Failed to mark answer");
      if (error instanceof Error && error.message.includes("not found")) {
        return reply.code(404).send({ error: error.message });
      }
      if (error instanceof Error && error.message.includes("author")) {
        return reply.code(403).send({ error: error.message });
      }
      return reply.code(500).send({
        error: "Failed to mark answer",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * POST /threads/:threadId/close
   * Close a discussion thread
   */
  app.post("/threads/:threadId/close", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const userId = request.headers["x-user-id"] as UserId;
    const { threadId } = request.params as { threadId: ThreadId };

    if (!tenantId || !userId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    try {
      const thread = await collaborationService.closeThread({
        tenantId,
        userId,
        threadId,
      });
      return reply.code(200).send(thread);
    } catch (error) {
      app.log.error(error, "Failed to close thread");
      if (error instanceof Error && error.message.includes("not found")) {
        return reply.code(404).send({ error: error.message });
      }
      if (error instanceof Error && error.message.includes("author")) {
        return reply.code(403).send({ error: error.message });
      }
      return reply.code(500).send({
        error: "Failed to close thread",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * POST /notes
   * Create a shared note
   */
  app.post("/notes", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const userId = request.headers["x-user-id"] as UserId;
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

    if (!tenantId || !userId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!title || !content) {
      return reply.code(400).send({ error: "Title and content are required" });
    }

    try {
      const note = await collaborationService.createSharedNote({
        tenantId,
        createdBy: userId,
        title,
        content,
        moduleId,
        lessonId,
        studyGroupId,
        allowEditing,
        allowComments,
      });
      return reply.code(201).send(note);
    } catch (error) {
      app.log.error(error, "Failed to create shared note");
      return reply.code(500).send({
        error: "Failed to create note",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * GET /notes/:noteId
   * Get shared note details
   */
  app.get("/notes/:noteId", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const userId = request.headers["x-user-id"] as UserId;
    const { noteId } = request.params as { noteId: string };

    if (!tenantId || !userId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    try {
      const note = await collaborationService.getSharedNote({
        tenantId,
        noteId,
        userId,
      });
      return reply.code(200).send(note);
    } catch (error) {
      app.log.error(error, "Failed to get shared note");
      if (error instanceof Error && error.message.includes("not found")) {
        return reply.code(404).send({ error: error.message });
      }
      return reply.code(500).send({
        error: "Failed to get note",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * PATCH /notes/:noteId
   * Update shared note content
   */
  app.patch("/notes/:noteId", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const userId = request.headers["x-user-id"] as UserId;
    const { noteId } = request.params as { noteId: string };
    const { content } = request.body as { content: string };

    if (!tenantId || !userId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!content) {
      return reply.code(400).send({ error: "Content is required" });
    }

    try {
      const note = await collaborationService.updateSharedNote({
        tenantId,
        noteId,
        userId,
        content,
      });
      return reply.code(200).send(note);
    } catch (error) {
      app.log.error(error, "Failed to update shared note");
      if (error instanceof Error && error.message.includes("not found")) {
        return reply.code(404).send({ error: error.message });
      }
      if (error instanceof Error && error.message.includes("permission")) {
        return reply.code(403).send({ error: error.message });
      }
      return reply.code(500).send({
        error: "Failed to update note",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * POST /notes/:noteId/share
   * Share note with other users
   */
  app.post("/notes/:noteId/share", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const userId = request.headers["x-user-id"] as UserId;
    const { noteId } = request.params as { noteId: string };
    const { shareWith } = request.body as {
      shareWith: Array<{
        userId: UserId;
        permission: "view" | "comment" | "edit";
      }>;
    };

    if (!tenantId || !userId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!shareWith || !Array.isArray(shareWith) || shareWith.length === 0) {
      return reply.code(400).send({ error: "Share with list is required" });
    }

    try {
      const note = await collaborationService.shareNote({
        tenantId,
        noteId,
        sharedBy: userId,
        shareWith,
      });
      return reply.code(200).send(note);
    } catch (error) {
      app.log.error(error, "Failed to share note");
      if (error instanceof Error && error.message.includes("not found")) {
        return reply.code(404).send({ error: error.message });
      }
      return reply.code(500).send({
        error: "Failed to share note",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * GET /notes
   * List shared notes with filters
   */
  app.get("/notes", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const userId = request.headers["x-user-id"] as UserId;
    const { studyGroupId, moduleId, cursor, limit } = request.query as {
      studyGroupId?: string;
      moduleId?: ModuleId;
      cursor?: string;
      limit?: string;
    };

    if (!tenantId || !userId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    try {
      const notes = await collaborationService.listSharedNotes({
        tenantId,
        userId,
        studyGroupId,
        moduleId,
        pagination: {
          cursor,
          limit: limit ? parseInt(limit, 10) : 20,
        },
      });
      return reply.code(200).send(notes);
    } catch (error) {
      app.log.error(error, "Failed to list shared notes");
      return reply.code(500).send({
        error: "Failed to list notes",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
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
