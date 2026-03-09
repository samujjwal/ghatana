/**
 * Forum Service Unit Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for topics, posts, reactions, moderation
 * @doc.layer platform
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ForumServiceImpl } from '../forums';
import type { PrismaClient } from '@ghatana/tutorputor-db';

function makeMockPrisma() {
    return {
        forum: {
            create: vi.fn(),
            findFirst: vi.fn(),
            findUnique: vi.fn(),
            findMany: vi.fn(),
            count: vi.fn(),
            update: vi.fn(),
        },
        forumTopic: {
            create: vi.fn(),
            findUnique: vi.fn(),
            findMany: vi.fn(),
            count: vi.fn(),
            update: vi.fn(),
        },
        forumPost: {
            create: vi.fn(),
            findUnique: vi.fn(),
            findMany: vi.fn(),
            count: vi.fn(),
            update: vi.fn(),
        },
        postReaction: {
            findFirst: vi.fn(),
            create: vi.fn(),
            delete: vi.fn(),
        },
        socialActivity: { create: vi.fn() },
        socialNotification: { create: vi.fn() },
    } as unknown as PrismaClient;
}

function makeForumRow(overrides: Record<string, unknown> = {}) {
    return {
        id: 'forum-1',
        tenantId: 't1',
        name: 'General Discussion',
        description: 'Talk about anything',
        scope: 'GLOBAL',
        scopeId: null,
        studyGroupId: null,
        categories: null,
        allowAnonymousPosts: false,
        requireModeration: false,
        allowAttachments: true,
        allowPolls: true,
        topicCount: 0,
        postCount: 0,
        lastPostAt: null,
        status: 'ACTIVE',
        iconUrl: null,
        createdAt: new Date(),
        updatedAt: new Date(),
        ...overrides,
    };
}

function makeTopicRow(overrides: Record<string, unknown> = {}) {
    return {
        id: 'topic-1',
        forumId: 'forum-1',
        categoryId: null,
        title: 'Test Topic',
        slug: 'test-topic-abc123',
        authorId: 'u1',
        authorName: 'User One',
        content: 'Hello world',
        contentFormat: 'markdown',
        attachments: null,
        viewCount: 0,
        replyCount: 0,
        likeCount: 0,
        isPinned: false,
        isLocked: false,
        isAnswered: false,
        answerId: null,
        lastReplyAt: null,
        lastReplyBy: null,
        status: 'PUBLISHED',
        moderatedBy: null,
        moderatedAt: null,
        moderationNote: null,
        createdAt: new Date(),
        updatedAt: new Date(),
        ...overrides,
    };
}

function makePostRow(overrides: Record<string, unknown> = {}) {
    return {
        id: 'post-1',
        topicId: 'topic-1',
        authorId: 'u2',
        authorName: 'User Two',
        isAnonymous: false,
        content: 'A reply',
        contentFormat: 'markdown',
        attachments: null,
        parentId: null,
        depth: 0,
        likeCount: 0,
        isAcceptedAnswer: false,
        status: 'PUBLISHED',
        moderatedBy: null,
        moderatedAt: null,
        isEdited: false,
        editedAt: null,
        editHistory: null,
        createdAt: new Date(),
        updatedAt: new Date(),
        ...overrides,
    };
}

describe('ForumServiceImpl', () => {
    let service: ForumServiceImpl;
    let prisma: ReturnType<typeof makeMockPrisma>;

    beforeEach(() => {
        vi.clearAllMocks();
        prisma = makeMockPrisma();
        service = new ForumServiceImpl({ prisma });
    });

    // =========================================================================
    // Forum Management
    // =========================================================================
    describe('createForum', () => {
        it('creates a forum with default settings', async () => {
            (prisma.forum.create as any).mockResolvedValue(makeForumRow());

            const forum = await service.createForum({
                tenantId: 't1' as any,
                name: 'General Discussion',
                description: 'Talk about anything',
                scope: 'global',
            });

            expect(forum.name).toBe('General Discussion');
            expect(forum.scope).toBe('global');
            expect(prisma.forum.create).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: expect.objectContaining({
                        allowAnonymousPosts: false,
                        requireModeration: false,
                    }),
                }),
            );
        });

        it('creates forum with categories', async () => {
            (prisma.forum.create as any).mockResolvedValue(
                makeForumRow({ categories: JSON.stringify([{ id: 'c1', name: 'Homework' }]) }),
            );

            const forum = await service.createForum({
                tenantId: 't1' as any,
                name: 'Class Forum',
                description: 'desc',
                scope: 'classroom',
                categories: [{ name: 'Homework', color: '#ff0000' }],
            });

            expect(forum.categories).toHaveLength(1);
        });
    });

    describe('getForum', () => {
        it('returns forum when found', async () => {
            (prisma.forum.findFirst as any).mockResolvedValue(makeForumRow());

            const forum = await service.getForum({ tenantId: 't1' as any, forumId: 'forum-1' });
            expect(forum.id).toBe('forum-1');
        });

        it('throws when forum not found', async () => {
            (prisma.forum.findFirst as any).mockResolvedValue(null);

            await expect(
                service.getForum({ tenantId: 't1' as any, forumId: 'nonexistent' }),
            ).rejects.toThrow('Forum not found');
        });
    });

    describe('listForums', () => {
        it('returns paginated list', async () => {
            (prisma.forum.findMany as any).mockResolvedValue([makeForumRow()]);
            (prisma.forum.count as any).mockResolvedValue(1);

            const result = await service.listForums({
                tenantId: 't1' as any,
                pagination: { limit: 20 },
            });

            expect(result.items).toHaveLength(1);
            expect(result.totalCount).toBe(1);
        });
    });

    // =========================================================================
    // Topics
    // =========================================================================
    describe('createTopic', () => {
        it('creates a published topic when no moderation', async () => {
            (prisma.forum.findUnique as any).mockResolvedValue(makeForumRow({ requireModeration: false }));
            (prisma.forumTopic.create as any).mockResolvedValue(makeTopicRow());
            (prisma.forum.update as any).mockResolvedValue({});

            const topic = await service.createTopic({
                tenantId: 't1' as any,
                forumId: 'forum-1',
                authorId: 'u1' as any,
                title: 'Test Topic',
                content: 'Hello world',
            });

            expect(topic.title).toBe('Test Topic');
            expect(prisma.forumTopic.create).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: expect.objectContaining({ status: 'PUBLISHED' }),
                }),
            );
        });

        it('creates a pending topic when moderation required', async () => {
            (prisma.forum.findUnique as any).mockResolvedValue(makeForumRow({ requireModeration: true }));
            (prisma.forumTopic.create as any).mockResolvedValue(makeTopicRow({ status: 'PENDING' }));
            (prisma.forum.update as any).mockResolvedValue({});

            await service.createTopic({
                tenantId: 't1' as any,
                forumId: 'forum-1',
                authorId: 'u1' as any,
                title: 'Test Topic',
                content: 'Hello',
            });

            expect(prisma.forumTopic.create).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: expect.objectContaining({ status: 'PENDING' }),
                }),
            );
        });

        it('increments forum topic count', async () => {
            (prisma.forum.findUnique as any).mockResolvedValue(makeForumRow());
            (prisma.forumTopic.create as any).mockResolvedValue(makeTopicRow());
            (prisma.forum.update as any).mockResolvedValue({});

            await service.createTopic({
                tenantId: 't1' as any,
                forumId: 'forum-1',
                authorId: 'u1' as any,
                title: 'Topic',
                content: 'Content',
            });

            expect(prisma.forum.update).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: expect.objectContaining({ topicCount: { increment: 1 } }),
                }),
            );
        });
    });

    describe('updateTopic', () => {
        it('allows author to edit', async () => {
            (prisma.forumTopic.findUnique as any).mockResolvedValue(makeTopicRow({ authorId: 'u1' }));
            (prisma.forumTopic.update as any).mockResolvedValue(makeTopicRow({ title: 'Updated' }));

            const topic = await service.updateTopic({
                tenantId: 't1' as any,
                topicId: 'topic-1',
                userId: 'u1' as any,
                patch: { title: 'Updated' },
            });

            expect(topic.title).toBe('Updated');
        });

        it('rejects non-author edit', async () => {
            (prisma.forumTopic.findUnique as any).mockResolvedValue(makeTopicRow({ authorId: 'u1' }));

            await expect(
                service.updateTopic({
                    tenantId: 't1' as any,
                    topicId: 'topic-1',
                    userId: 'u2' as any,
                    patch: { title: 'Hacked' },
                }),
            ).rejects.toThrow('Only the author');
        });
    });

    describe('deleteTopic', () => {
        it('soft-deletes and decrements forum count', async () => {
            (prisma.forumTopic.findUnique as any).mockResolvedValue(makeTopicRow({ authorId: 'u1' }));
            (prisma.forumTopic.update as any).mockResolvedValue({});
            (prisma.forum.update as any).mockResolvedValue({});

            await service.deleteTopic({ tenantId: 't1' as any, topicId: 'topic-1', userId: 'u1' as any });

            expect(prisma.forumTopic.update).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: { status: 'DELETED' },
                }),
            );
            expect(prisma.forum.update).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: { topicCount: { decrement: 1 } },
                }),
            );
        });

        it('rejects non-author delete', async () => {
            (prisma.forumTopic.findUnique as any).mockResolvedValue(makeTopicRow({ authorId: 'u1' }));

            await expect(
                service.deleteTopic({ tenantId: 't1' as any, topicId: 'topic-1', userId: 'u2' as any }),
            ).rejects.toThrow('Permission denied');
        });
    });

    // =========================================================================
    // Posts
    // =========================================================================
    describe('createPost', () => {
        it('creates a reply and updates topic stats', async () => {
            (prisma.forumTopic.findUnique as any).mockResolvedValue({
                ...makeTopicRow(),
                forum: makeForumRow(),
            });
            (prisma.forumPost.create as any).mockResolvedValue(makePostRow());
            (prisma.forumTopic.update as any).mockResolvedValue({});
            (prisma.forum.update as any).mockResolvedValue({});

            const post = await service.createPost({
                tenantId: 't1' as any,
                topicId: 'topic-1',
                authorId: 'u2' as any,
                content: 'A reply',
            });

            expect(post.content).toBe('A reply');
            expect(prisma.forumTopic.update).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: expect.objectContaining({ replyCount: { increment: 1 } }),
                }),
            );
        });

        it('rejects post on locked topic', async () => {
            (prisma.forumTopic.findUnique as any).mockResolvedValue({
                ...makeTopicRow({ isLocked: true }),
                forum: makeForumRow(),
            });

            await expect(
                service.createPost({
                    tenantId: 't1' as any,
                    topicId: 'topic-1',
                    authorId: 'u2' as any,
                    content: 'A reply',
                }),
            ).rejects.toThrow('Topic is locked');
        });
    });

    // =========================================================================
    // Reactions
    // =========================================================================
    describe('reactToPost', () => {
        it('creates a reaction and increments like count', async () => {
            (prisma.postReaction.findFirst as any).mockResolvedValue(null);
            (prisma.postReaction.create as any).mockResolvedValue({});
            (prisma.forumPost.update as any).mockResolvedValue({});
            (prisma.forumPost.findUnique as any).mockResolvedValue(makePostRow({ likeCount: 1 }));

            const post = await service.reactToPost({
                tenantId: 't1' as any,
                postId: 'post-1',
                userId: 'u3' as any,
                reaction: 'like',
            });

            expect(prisma.forumPost.update).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: { likeCount: { increment: 1 } },
                }),
            );
        });

        it('throws if already reacted', async () => {
            (prisma.postReaction.findFirst as any).mockResolvedValue({ id: 'r1' });

            await expect(
                service.reactToPost({
                    tenantId: 't1' as any,
                    postId: 'post-1',
                    userId: 'u3' as any,
                    reaction: 'like',
                }),
            ).rejects.toThrow('Already reacted');
        });
    });

    // =========================================================================
    // Moderation
    // =========================================================================
    describe('moderateContent', () => {
        it('approves a topic', async () => {
            (prisma.forumTopic.update as any).mockResolvedValue({});

            await service.moderateContent({
                tenantId: 't1' as any,
                contentType: 'topic',
                contentId: 'topic-1',
                moderatorId: 'mod-1' as any,
                action: 'approve',
            });

            expect(prisma.forumTopic.update).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: expect.objectContaining({ status: 'PUBLISHED' }),
                }),
            );
        });

        it('hides a post', async () => {
            (prisma.forumPost.update as any).mockResolvedValue({});

            await service.moderateContent({
                tenantId: 't1' as any,
                contentType: 'post',
                contentId: 'post-1',
                moderatorId: 'mod-1' as any,
                action: 'hide',
            });

            expect(prisma.forumPost.update).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: expect.objectContaining({ status: 'HIDDEN' }),
                }),
            );
        });
    });

    // =========================================================================
    // Mark as Answer
    // =========================================================================
    describe('markAsAnswer', () => {
        it('marks a post as accepted answer', async () => {
            (prisma.forumTopic.findUnique as any).mockResolvedValue(makeTopicRow({ authorId: 'u1', answerId: null }));
            (prisma.forumPost.update as any).mockResolvedValue(makePostRow({ isAcceptedAnswer: true }));
            (prisma.forumTopic.update as any).mockResolvedValue({});

            const post = await service.markAsAnswer({
                tenantId: 't1' as any,
                topicId: 'topic-1',
                postId: 'post-1',
                userId: 'u1' as any,
            });

            expect(post.isAcceptedAnswer).toBe(true);
        });

        it('rejects non-topic-author', async () => {
            (prisma.forumTopic.findUnique as any).mockResolvedValue(makeTopicRow({ authorId: 'u1' }));

            await expect(
                service.markAsAnswer({
                    tenantId: 't1' as any,
                    topicId: 'topic-1',
                    postId: 'post-1',
                    userId: 'u2' as any,
                }),
            ).rejects.toThrow('Only the topic author');
        });
    });
});
