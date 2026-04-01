/**
 * @doc.type module
 * @doc.purpose Collaboration service for Q&A and discussions
 * @doc.layer product
 * @doc.pattern Service
 */

import { paginate, type Prisma } from "@tutorputor/core";
import type { PrismaClient } from "@tutorputor/core/db";
import type {
  CollaborationService,
  Thread,
  ThreadId,
  ThreadStatus,
  Post,
  PostId,
  ModuleId,
  TenantId,
  UserId,
  PaginationArgs,
  PaginatedResult,
} from "@tutorputor/contracts";
import {
  createHttpError,
  requireOwnership,
  requireTenantAccess,
} from "../../core/http/requestContext.js";

// SharedNote is defined locally until contracts expose it.
export type SharedNote = {
  id: string;
  tenantId: string;
  createdBy: string;
  title: string;
  content: string;
  version: number;
  allowEditing: boolean;
  allowComments: boolean;
  moduleId?: string;
  lessonId?: string;
  studyGroupId?: string;
  sharedWith: SharedNoteAccess[];
  createdAt: Date;
  updatedAt: Date;
  lastEditedBy?: string;
};

export type SharedNoteAccess = {
  userId: string;
  permission: "view" | "comment" | "edit";
  addedAt: Date;
  addedBy: string;
};

type ThreadWithPosts = Prisma.ThreadGetPayload<{
  include: {
    posts: true;
  };
}>;

type PostRecord = Prisma.PostGetPayload<Record<string, never>>;

export class CollaborationServiceImpl implements CollaborationService {
  constructor(private readonly prisma: PrismaClient) {}

  async postQuestion(args: {
    tenantId: TenantId;
    userId: UserId;
    authorName: string;
    moduleId?: ModuleId;
    title: string;
    content: string;
  }): Promise<Thread> {
    const thread = await this.prisma.thread.create({
      data: {
        tenantId: args.tenantId,
        title: args.title,
        status: "OPEN",
        authorId: args.userId,
        authorName: args.authorName,
        ...(args.moduleId ? { moduleId: args.moduleId } : {}),
        posts: {
          create: {
            authorId: args.userId,
            authorName: args.authorName,
            content: args.content,
            isAnswer: false,
          },
        },
      },
      include: {
        posts: {
          orderBy: { createdAt: "asc" },
        },
      },
    });

    return this.mapToThread(thread as ThreadWithPosts);
  }

  async reply(args: {
    tenantId: TenantId;
    userId: UserId;
    authorName: string;
    threadId: ThreadId;
    content: string;
  }): Promise<Post> {
    // Verify thread exists and belongs to tenant
    const thread = await this.prisma.thread.findFirst({
      where: { id: args.threadId, tenantId: args.tenantId },
    });

    if (!thread) {
      throw createHttpError(
        404,
        "NOT_FOUND",
        `Thread ${args.threadId} not found`,
      );
    }

    if (thread.status === "CLOSED") {
      throw new Error("Cannot reply to a closed thread");
    }

    const post = await this.prisma.post.create({
      data: {
        threadId: args.threadId,
        authorId: args.userId,
        authorName: args.authorName,
        content: args.content,
        isAnswer: false,
      },
    });

    return this.mapToPost(post);
  }

  async listThreads(args: {
    tenantId: TenantId;
    moduleId?: ModuleId;
    status?: ThreadStatus;
    cursor?: ThreadId | null;
    limit?: number;
  }): Promise<{ items: Thread[]; nextCursor: ThreadId | null }> {
    const where: Prisma.ThreadWhereInput = { tenantId: args.tenantId };

    if (args.moduleId) {
      where.moduleId = args.moduleId;
    }
    if (args.status) {
      where.status = args.status;
    }

    const paged = await paginate<ThreadWithPosts, Prisma.ThreadWhereInput>(
      {
        findMany: (input) =>
          this.prisma.thread.findMany({
            ...input,
            include: { posts: true },
          }) as Promise<ThreadWithPosts[]>,
        count: (input) => this.prisma.thread.count(input),
      },
      where,
      {
        take: Math.min(args.limit ?? 20, 50),
        ...(args.cursor ? { cursor: args.cursor } : {}),
      },
      {
        orderField: "createdAt",
      },
    );

    const trimmed = paged.items.map((thread) => ({
      ...thread,
      posts: [...thread.posts]
        .sort((left, right) => left.createdAt.getTime() - right.createdAt.getTime())
        .slice(0, 3),
    }));

    return {
      items: trimmed.map((thread) => this.mapToThread(thread)),
      nextCursor: paged.hasMore ? ((paged.nextCursor ?? null) as ThreadId | null) : null,
    };
  }

  async getThread(args: {
    tenantId: TenantId;
    threadId: ThreadId;
  }): Promise<Thread> {
    const thread = await this.prisma.thread.findFirst({
      where: { id: args.threadId, tenantId: args.tenantId },
      include: {
        posts: {
          orderBy: { createdAt: "asc" },
        },
      },
    });

    if (!thread) {
      throw new Error(`Thread ${args.threadId} not found`);
    }

    return this.mapToThread(thread);
  }

  async markAsAnswer(args: {
    tenantId: TenantId;
    userId: UserId;
    threadId: ThreadId;
    postId: PostId;
  }): Promise<Thread> {
    // Verify thread exists and user is the author
    const thread = await this.prisma.thread.findFirst({
      where: { id: args.threadId, tenantId: args.tenantId },
    });

    if (!thread) {
      throw createHttpError(
        404,
        "NOT_FOUND",
        `Thread ${args.threadId} not found`,
      );
    }

    requireOwnership(
      thread.authorId,
      args.userId,
      "Only the thread author can mark answers",
    );

    // Clear any existing answers in this thread
    await this.prisma.post.updateMany({
      where: { threadId: args.threadId, isAnswer: true },
      data: { isAnswer: false },
    });

    const post = await this.prisma.post.findFirst({
      where: {
        id: args.postId,
        threadId: args.threadId,
      },
    });

    if (!post) {
      throw createHttpError(
        404,
        "NOT_FOUND",
        `Post ${args.postId} not found in thread ${args.threadId}`,
      );
    }

    // Mark the selected post as answer
    await this.prisma.post.update({
      where: { id: args.postId },
      data: { isAnswer: true },
    });

    // Mark thread as resolved
    const updated = await this.prisma.thread.update({
      where: { id: args.threadId },
      data: {
        status: "RESOLVED",
        resolvedAt: new Date(),
      },
      include: {
        posts: {
          orderBy: { createdAt: "asc" },
        },
      },
    });

    return this.mapToThread(updated);
  }

  async closeThread(args: {
    tenantId: TenantId;
    userId: UserId;
    threadId: ThreadId;
  }): Promise<Thread> {
    // Verify thread exists
    const thread = await this.prisma.thread.findFirst({
      where: { id: args.threadId, tenantId: args.tenantId },
    });

    if (!thread) {
      throw createHttpError(
        404,
        "NOT_FOUND",
        `Thread ${args.threadId} not found`,
      );
    }

    requireOwnership(
      thread.authorId,
      args.userId,
      "Only the thread author can close the thread",
    );

    const updated = await this.prisma.thread.update({
      where: { id: args.threadId },
      data: { status: "CLOSED" },
      include: {
        posts: {
          orderBy: { createdAt: "asc" },
        },
      },
    });

    return this.mapToThread(updated);
  }

  async createSharedNote(args: {
    tenantId: TenantId;
    createdBy: UserId;
    title: string;
    content: string;
    allowEditing?: boolean;
    allowComments?: boolean;
    moduleId?: string;
    lessonId?: string;
    studyGroupId?: string;
  }): Promise<SharedNote> {
    const note = await this.prisma.sharedNote.create({
      data: {
        tenantId: args.tenantId,
        createdBy: args.createdBy,
        title: args.title,
        content: args.content,
        version: 1,
        allowEditing: args.allowEditing ?? true,
        allowComments: args.allowComments ?? true,
        moduleId: args.moduleId ?? null,
        lessonId: args.lessonId ?? null,
        studyGroupId: args.studyGroupId ?? null,
      },
      include: { sharedWith: true },
    });
    return this.mapToNote(note);
  }

  async getSharedNote(args: {
    noteId: string;
    tenantId: TenantId;
    userId: UserId;
  }): Promise<SharedNote> {
    const note = await this.prisma.sharedNote.findFirst({
      where: { id: args.noteId, tenantId: args.tenantId },
      include: { sharedWith: true },
    });
    if (!note) {
      throw createHttpError(
        404,
        "NOT_FOUND",
        `Shared note ${args.noteId} not found`,
      );
    }

    const canAccess =
      note.createdBy === args.userId ||
      (note.sharedWith ?? []).some(
        (entry) => entry.userId === args.userId,
      );
    if (!canAccess) {
      throw createHttpError(
        403,
        "FORBIDDEN",
        "Insufficient permissions to access this note",
      );
    }
    return this.mapToNote(note);
  }

  async updateSharedNote(args: {
    noteId: string;
    tenantId: TenantId;
    userId: UserId;
    content: string;
  }): Promise<SharedNote> {
    const note = await this.prisma.sharedNote.findFirst({
      where: { id: args.noteId, tenantId: args.tenantId },
      include: { sharedWith: true },
    });
    if (!note) {
      throw createHttpError(
        404,
        "NOT_FOUND",
        `Shared note ${args.noteId} not found`,
      );
    }

    const canEdit =
      note.createdBy === args.userId ||
      (note.allowEditing &&
        (note.sharedWith ?? []).some(
          (entry) =>
            entry.userId === args.userId && entry.permission === "edit",
        ));
    if (!canEdit) {
      throw createHttpError(
        403,
        "FORBIDDEN",
        "Insufficient permissions to edit this note",
      );
    }

    const updatedNote = await this.prisma.sharedNote.update({
      where: { id: args.noteId },
      data: {
        content: args.content,
        lastEditedBy: args.userId,
        version: { increment: 1 },
        updatedAt: new Date(),
      },
      include: { sharedWith: true },
    });
    return this.mapToNote(updatedNote);
  }

  async shareNote(args: {
    noteId: string;
    tenantId: TenantId;
    sharedBy: UserId;
    shareWith: Array<{
      userId: UserId;
      permission: "view" | "comment" | "edit";
    }>;
  }): Promise<SharedNote> {
    const note = await this.prisma.sharedNote.findFirst({
      where: { id: args.noteId, tenantId: args.tenantId },
      include: { sharedWith: true },
    });
    if (!note) {
      throw createHttpError(
        404,
        "NOT_FOUND",
        `Shared note ${args.noteId} not found`,
      );
    }

    requireTenantAccess(note.tenantId, args.tenantId);
    requireOwnership(
      note.createdBy,
      args.sharedBy,
      "Only the note owner can share this note",
    );

    await Promise.all(
      args.shareWith.map((shareTarget) =>
        this.prisma.sharedNoteAccess.upsert({
          where: {
            noteId_userId: { noteId: args.noteId, userId: shareTarget.userId },
          },
          create: {
            noteId: args.noteId,
            userId: shareTarget.userId,
            permission: shareTarget.permission,
            addedBy: args.sharedBy,
            addedAt: new Date(),
          },
          update: { permission: shareTarget.permission },
        }),
      ),
    );
    const updatedNote = await this.prisma.sharedNote.findUniqueOrThrow(
      {
        where: { id: args.noteId },
        include: { sharedWith: true },
      },
    );
    return this.mapToNote(updatedNote);
  }

  async listSharedNotes(args: {
    tenantId: TenantId;
    userId: UserId;
    moduleId?: string;
    studyGroupId?: string;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<SharedNote>> {
    const skip = args.pagination?.offset ?? 0;
    const take = args.pagination?.limit ?? 20;
    const where: Record<string, unknown> = { tenantId: args.tenantId };
    if (args.moduleId) where.moduleId = args.moduleId;
    if (args.studyGroupId) where.studyGroupId = args.studyGroupId;
    where.OR = [
      { createdBy: args.userId },
      { sharedWith: { some: { userId: args.userId } } },
    ];
    const [items, total] = await Promise.all([
      this.prisma.sharedNote.findMany({
        where,
        skip,
        take,
        orderBy: { updatedAt: "desc" },
        include: { sharedWith: true },
      }),
      this.prisma.sharedNote.count({ where }),
    ]);
    return {
      items: items.map((n) => this.mapToNote(n)),
      totalCount: total,
      total,
      hasMore: skip + items.length < total,
    };
  }

  private mapToNote(note: any): SharedNote {
    return {
      id: note.id,
      tenantId: note.tenantId,
      createdBy: note.createdBy,
      title: note.title,
      content: note.content,
      version: note.version,
      allowEditing: note.allowEditing,
      allowComments: note.allowComments,
      moduleId: note.moduleId,
      lessonId: note.lessonId,
      studyGroupId: note.studyGroupId,
      sharedWith: (note.sharedWith ?? []).map((a: any) => ({
        userId: a.userId,
        permission: a.permission,
        addedAt: a.addedAt,
        addedBy: a.addedBy,
      })),
      createdAt: note.createdAt,
      updatedAt: note.updatedAt,
      lastEditedBy: note.lastEditedBy,
    };
  }

  // ===========================================================================
  // Helpers
  // ===========================================================================

  private mapToThread(thread: ThreadWithPosts): Thread {
    return {
      id: thread.id as ThreadId,
      tenantId: thread.tenantId as TenantId,
      title: thread.title,
      status: thread.status as ThreadStatus,
      authorId: thread.authorId as UserId,
      authorName: thread.authorName,
      posts: thread.posts.map((post) => this.mapToPost(post)),
      createdAt: thread.createdAt.toISOString(),
      ...(thread.moduleId ? { moduleId: thread.moduleId as ModuleId } : {}),
      ...(thread.resolvedAt
        ? { resolvedAt: thread.resolvedAt.toISOString() }
        : {}),
    };
  }

  private mapToPost(post: PostRecord): Post {
    return {
      id: post.id as PostId,
      threadId: post.threadId as ThreadId,
      authorId: post.authorId as UserId,
      authorName: post.authorName,
      content: post.content,
      createdAt: post.createdAt.toISOString(),
      updatedAt: post.updatedAt.toISOString(),
      isAnswer: post.isAnswer,
    };
  }
}
