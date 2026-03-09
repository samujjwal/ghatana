/**
 * @doc.type module
 * @doc.purpose Collaboration service for Q&A and discussions
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from '@ghatana/tutorputor-db';
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
} from '@ghatana/tutorputor-contracts';

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
    permission: 'view' | 'comment' | 'edit';
    addedAt: Date;
    addedBy: string;
};

export class CollaborationServiceImpl implements CollaborationService {
    constructor(private readonly prisma: PrismaClient) { }

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
                moduleId: args.moduleId,
                title: args.title,
                status: 'OPEN',
                authorId: args.userId,
                authorName: args.authorName,
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
                    orderBy: { createdAt: 'asc' },
                },
            },
        });

        return this.mapToThread(thread);
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
            throw new Error(`Thread ${args.threadId} not found`);
        }

        if (thread.status === 'CLOSED') {
            throw new Error('Cannot reply to a closed thread');
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
        const limit = args.limit ?? 20;
        const take = Math.min(limit, 50);
        const where: any = { tenantId: args.tenantId };

        if (args.moduleId) {
            where.moduleId = args.moduleId;
        }
        if (args.status) {
            where.status = args.status;
        }

        const threads = await this.prisma.thread.findMany({
            where,
            take: take + 1,
            orderBy: { createdAt: 'desc' },
            ...(args.cursor ? { cursor: { id: args.cursor }, skip: 1 } : {}),
            include: {
                posts: {
                    orderBy: { createdAt: 'asc' },
                    take: 3, // Preview first 3 posts
                },
            },
        });

        const hasMore = threads.length > take;
        const trimmed = threads.slice(0, take);

        return {
            items: trimmed.map((t: any) => this.mapToThread(t)),
            nextCursor: hasMore
                ? (trimmed[trimmed.length - 1]?.id as ThreadId)
                : null,
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
                    orderBy: { createdAt: 'asc' },
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
            throw new Error(`Thread ${args.threadId} not found`);
        }

        if (thread.authorId !== args.userId) {
            throw new Error('Only the thread author can mark answers');
        }

        // Clear any existing answers in this thread
        await this.prisma.post.updateMany({
            where: { threadId: args.threadId, isAnswer: true },
            data: { isAnswer: false },
        });

        // Mark the selected post as answer
        await this.prisma.post.update({
            where: { id: args.postId },
            data: { isAnswer: true },
        });

        // Mark thread as resolved
        const updated = await this.prisma.thread.update({
            where: { id: args.threadId },
            data: {
                status: 'RESOLVED',
                resolvedAt: new Date(),
            },
            include: {
                posts: {
                    orderBy: { createdAt: 'asc' },
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
            throw new Error(`Thread ${args.threadId} not found`);
        }

        // Only author or teacher/admin can close (simplified: just author for now)
        if (thread.authorId !== args.userId) {
            throw new Error('Only the thread author can close the thread');
        }

        const updated = await this.prisma.thread.update({
            where: { id: args.threadId },
            data: { status: 'CLOSED' },
            include: {
                posts: {
                    orderBy: { createdAt: 'asc' },
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
        studyGroupId?: string;
    }): Promise<SharedNote> {
        const note = await (this.prisma as any).sharedNote.create({
            data: {
                tenantId: args.tenantId,
                createdBy: args.createdBy,
                title: args.title,
                content: args.content,
                version: 1,
                allowEditing: args.allowEditing ?? true,
                allowComments: args.allowComments ?? true,
                moduleId: args.moduleId ?? null,
                studyGroupId: args.studyGroupId ?? null,
            },
            include: { sharedWith: true },
        });
        return this.mapToNote(note);
    }

    async getSharedNote(args: { noteId: string; tenantId: TenantId }): Promise<SharedNote | null> {
        const note = await (this.prisma as any).sharedNote.findFirst({
            where: { id: args.noteId, tenantId: args.tenantId },
            include: { sharedWith: true },
        });
        return note ? this.mapToNote(note) : null;
    }

    async updateSharedNote(args: {
        noteId: string;
        tenantId: TenantId;
        editorId: UserId;
        title?: string;
        content?: string;
    }): Promise<SharedNote> {
        const note = await (this.prisma as any).sharedNote.update({
            where: { id: args.noteId },
            data: {
                ...(args.title !== undefined && { title: args.title }),
                ...(args.content !== undefined && { content: args.content }),
                lastEditedBy: args.editorId,
                version: { increment: 1 },
                updatedAt: new Date(),
            },
            include: { sharedWith: true },
        });
        return this.mapToNote(note);
    }

    async shareNote(args: {
        noteId: string;
        tenantId: TenantId;
        sharedById: UserId;
        userId: UserId;
        permission: 'view' | 'comment' | 'edit';
    }): Promise<SharedNote> {
        await (this.prisma as any).sharedNoteAccess.upsert({
            where: { noteId_userId: { noteId: args.noteId, userId: args.userId } },
            create: {
                noteId: args.noteId,
                userId: args.userId,
                permission: args.permission,
                addedBy: args.sharedById,
                addedAt: new Date(),
            },
            update: { permission: args.permission },
        });
        const note = await (this.prisma as any).sharedNote.findUniqueOrThrow({
            where: { id: args.noteId },
            include: { sharedWith: true },
        });
        return this.mapToNote(note);
    }

    async listSharedNotes(args: {
        tenantId: TenantId;
        userId?: UserId;
        moduleId?: string;
        studyGroupId?: string;
        pagination?: PaginationArgs;
    }): Promise<PaginatedResult<SharedNote>> {
        const skip = args.pagination?.offset ?? 0;
        const take = args.pagination?.limit ?? 20;
        const where: any = { tenantId: args.tenantId };
        if (args.moduleId) where.moduleId = args.moduleId;
        if (args.studyGroupId) where.studyGroupId = args.studyGroupId;
        if (args.userId) {
            where.OR = [
                { createdBy: args.userId },
                { sharedWith: { some: { userId: args.userId } } },
            ];
        }
        const [items, total] = await Promise.all([
            (this.prisma as any).sharedNote.findMany({
                where,
                skip,
                take,
                orderBy: { updatedAt: 'desc' },
                include: { sharedWith: true },
            }),
            (this.prisma as any).sharedNote.count({ where }),
        ]);
        return {
            items: items.map((n: any) => this.mapToNote(n)),
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

    private mapToThread(thread: any): Thread {
        return {
            id: thread.id as ThreadId,
            tenantId: thread.tenantId as TenantId,
            moduleId: thread.moduleId as ModuleId | undefined,
            title: thread.title,
            status: thread.status as ThreadStatus,
            authorId: thread.authorId as UserId,
            authorName: thread.authorName,
            posts: thread.posts.map((p: any) => this.mapToPost(p)),
            createdAt: thread.createdAt.toISOString(),
            resolvedAt: thread.resolvedAt?.toISOString(),
        };
    }

    private mapToPost(post: any): Post {
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
