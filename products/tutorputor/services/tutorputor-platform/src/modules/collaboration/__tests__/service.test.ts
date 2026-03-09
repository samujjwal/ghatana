import { describe, it, expect, vi, beforeEach } from "vitest";
import { CollaborationServiceImpl } from "../service.js";

// ---------------------------------------------------------------------------
// Prisma Mock
// ---------------------------------------------------------------------------
const mockThread = {
    id: "thread-1",
    tenantId: "tenant-1",
    moduleId: "module-1",
    title: "Test Thread",
    status: "OPEN",
    authorId: "user-1",
    authorName: "Alice",
    posts: [
        {
            id: "post-1",
            threadId: "thread-1",
            authorId: "user-1",
            authorName: "Alice",
            content: "First post",
            createdAt: new Date("2024-01-01"),
            updatedAt: new Date("2024-01-01"),
            isAnswer: false,
        },
    ],
    createdAt: new Date("2024-01-01"),
    resolvedAt: null,
};

const mockNote = {
    id: "note-1",
    tenantId: "tenant-1",
    createdBy: "user-1",
    title: "Shared Note",
    content: "Content here",
    version: 1,
    allowEditing: true,
    allowComments: true,
    moduleId: null,
    studyGroupId: null,
    sharedWith: [],
    createdAt: new Date("2024-01-01"),
    updatedAt: new Date("2024-01-01"),
    lastEditedBy: null,
};

function makePrisma() {
    return {
        thread: {
            create: vi.fn().mockResolvedValue(mockThread),
            findFirst: vi.fn().mockResolvedValue(mockThread),
            findMany: vi.fn().mockResolvedValue([mockThread]),
            count: vi.fn().mockResolvedValue(1),
            update: vi.fn().mockResolvedValue({ ...mockThread, status: "RESOLVED", posts: mockThread.posts }),
        },
        post: {
            create: vi.fn().mockResolvedValue(mockThread.posts[0]),
            update: vi.fn().mockResolvedValue({ ...mockThread.posts[0], isAnswer: true }),
            updateMany: vi.fn().mockResolvedValue({ count: 0 }),
        },
        sharedNote: {
            create: vi.fn().mockResolvedValue(mockNote),
            findFirst: vi.fn().mockResolvedValue(mockNote),
            findUniqueOrThrow: vi.fn().mockResolvedValue(mockNote),
            update: vi.fn().mockResolvedValue({ ...mockNote, content: "Updated", version: 2 }),
            findMany: vi.fn().mockResolvedValue([mockNote]),
            count: vi.fn().mockResolvedValue(1),
        },
        sharedNoteAccess: {
            upsert: vi.fn().mockResolvedValue({ noteId: "note-1", userId: "user-2", permission: "view" }),
        },
    };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe("CollaborationServiceImpl", () => {
    let service: CollaborationServiceImpl;
    let prisma: ReturnType<typeof makePrisma>;

    beforeEach(() => {
        prisma = makePrisma();
        service = new CollaborationServiceImpl(prisma as any);
    });

    // -------------------------------------------------------------------------
    // Thread Operations
    // -------------------------------------------------------------------------
    describe("postQuestion", () => {
        it("creates a thread and returns mapped result", async () => {
            const result = await service.postQuestion({
                tenantId: "tenant-1" as any,
                moduleId: "module-1" as any,
                title: "Test Thread",
                userId: "user-1" as any,
                authorName: "Alice",
                content: "First post",
            });
            expect(result.id).toBe("thread-1");
            expect(result.title).toBe("Test Thread");
            expect(result.posts).toHaveLength(1);
            expect(prisma.thread.create).toHaveBeenCalledOnce();
        });
    });

    describe("getThread", () => {
        it("returns thread by id", async () => {
            const result = await service.getThread({
                tenantId: "tenant-1" as any,
                threadId: "thread-1" as any,
            });
            expect(result?.id).toBe("thread-1");
        });

        it("throws when thread not found", async () => {
            prisma.thread.findFirst.mockResolvedValue(null);
            await expect(
                service.getThread({
                    tenantId: "tenant-1" as any,
                    threadId: "missing" as any,
                })
            ).rejects.toThrow("not found");
        });
    });

    describe("listThreads", () => {
        it("returns paginated threads", async () => {
            const result = await service.listThreads({
                tenantId: "tenant-1" as any,
                limit: 10,
            });
            expect(result.items).toHaveLength(1);
        });
    });

    describe("closeThread", () => {
        it("marks thread as closed", async () => {
            // closeThread requires thread.authorId === userId
            prisma.thread.findFirst.mockResolvedValue({ ...mockThread, authorId: "user-1" } as any);
            await service.closeThread({
                tenantId: "tenant-1" as any,
                threadId: "thread-1" as any,
                userId: "user-1" as any,
            });
            expect(prisma.thread.update).toHaveBeenCalledOnce();
        });
    });

    describe("reply", () => {
        it("creates a post in thread", async () => {
            prisma.thread.findFirst.mockResolvedValue({ ...mockThread, status: "OPEN" } as any);
            await service.reply({
                tenantId: "tenant-1" as any,
                threadId: "thread-1" as any,
                userId: "user-1" as any,
                authorName: "Alice",
                content: "Reply here",
            });
            expect(prisma.post.create).toHaveBeenCalledOnce();
        });
    });

    describe("markAsAnswer", () => {
        it("marks post as answer", async () => {
            prisma.thread.findFirst.mockResolvedValue({ ...mockThread, authorId: "user-1" } as any);
            await service.markAsAnswer({
                tenantId: "tenant-1" as any,
                threadId: "thread-1" as any,
                postId: "post-1" as any,
                userId: "user-1" as any,
            });
            expect(prisma.post.update).toHaveBeenCalledOnce();
        });
    });

    // -------------------------------------------------------------------------
    // Shared Notes
    // -------------------------------------------------------------------------
    describe("createSharedNote", () => {
        it("creates a shared note", async () => {
            const result = await service.createSharedNote({
                tenantId: "tenant-1" as any,
                createdBy: "user-1" as any,
                title: "Shared Note",
                content: "Content here",
            });
            expect(result.id).toBe("note-1");
            expect(result.title).toBe("Shared Note");
            expect(prisma.sharedNote.create).toHaveBeenCalledOnce();
        });

        it("defaults allowEditing and allowComments to true", async () => {
            await service.createSharedNote({
                tenantId: "tenant-1" as any,
                createdBy: "user-1" as any,
                title: "Note",
                content: "body",
            });
            const callArg = prisma.sharedNote.create.mock.calls[0][0];
            expect(callArg.data.allowEditing).toBe(true);
            expect(callArg.data.allowComments).toBe(true);
        });
    });

    describe("getSharedNote", () => {
        it("returns a note by id", async () => {
            const result = await service.getSharedNote({
                noteId: "note-1",
                tenantId: "tenant-1" as any,
            });
            expect(result?.id).toBe("note-1");
        });

        it("returns null when not found", async () => {
            prisma.sharedNote.findFirst.mockResolvedValue(null);
            const result = await service.getSharedNote({
                noteId: "missing",
                tenantId: "tenant-1" as any,
            });
            expect(result).toBeNull();
        });
    });

    describe("updateSharedNote", () => {
        it("updates content and increments version", async () => {
            const result = await service.updateSharedNote({
                noteId: "note-1",
                tenantId: "tenant-1" as any,
                editorId: "user-1" as any,
                content: "Updated",
            });
            expect(prisma.sharedNote.update).toHaveBeenCalledOnce();
            const updateArgs = prisma.sharedNote.update.mock.calls[0][0];
            expect(updateArgs.data.version).toEqual({ increment: 1 });
        });
    });

    describe("shareNote", () => {
        it("upserts access record and returns updated note", async () => {
            const result = await service.shareNote({
                noteId: "note-1",
                tenantId: "tenant-1" as any,
                sharedById: "user-1" as any,
                userId: "user-2" as any,
                permission: "view",
            });
            expect(prisma.sharedNoteAccess.upsert).toHaveBeenCalledOnce();
            expect(prisma.sharedNote.findUniqueOrThrow).toHaveBeenCalledOnce();
            expect(result.id).toBe("note-1");
        });
    });

    describe("listSharedNotes", () => {
        it("returns paginated notes", async () => {
            const result = await service.listSharedNotes({
                tenantId: "tenant-1" as any,
                pagination: { limit: 10, offset: 0 },
            });
            expect(result.items).toHaveLength(1);
            expect(result.total).toBe(1);
            expect(result.hasMore).toBe(false);
        });

        it("filters by userId with OR clause", async () => {
            await service.listSharedNotes({
                tenantId: "tenant-1" as any,
                userId: "user-1" as any,
            });
            const callArg = prisma.sharedNote.findMany.mock.calls[0][0];
            expect(callArg.where.OR).toBeDefined();
        });
    });
});
