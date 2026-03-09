/**
 * @doc.type module
 * @doc.purpose Forum service implementation
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from '@ghatana/tutorputor-db';
import type { Redis } from 'ioredis';
import type {
    ForumService,
    TenantId,
    UserId,
    PaginationArgs,
    PaginatedResult,
} from '@ghatana/tutorputor-contracts';

import type {
    Forum,
    ForumScope,
    ForumTopic,
    ForumPost,
    ReactionType,
} from '@ghatana/tutorputor-contracts/v1/social';
import slugify from 'slugify';
import { nanoid } from 'nanoid';

/**
 * Configuration for ForumServiceImpl
 */
export interface ForumServiceConfig {
    prisma: PrismaClient;
    redis?: Redis;
}

/**
 * Implementation of the Forum service.
 */
export class ForumServiceImpl implements ForumService {
    private readonly prisma: PrismaClient;
    private readonly redis?: Redis;

    constructor(config: ForumServiceConfig) {
        this.prisma = config.prisma;
        this.redis = config.redis;
    }

    // ---------------------------------------------------------------------------
    // Forum Management
    // ---------------------------------------------------------------------------

    async createForum(args: {
        tenantId: TenantId;
        name: string;
        description: string;
        scope: ForumScope;
        scopeId?: string;
        categories?: Array<{ name: string; description?: string; color: string }>;
        settings?: {
            allowAnonymousPosts?: boolean;
            requireModeration?: boolean;
            allowAttachments?: boolean;
            allowPolls?: boolean;
        };
    }): Promise<Forum> {
        const categories = args.categories?.map((c, i) => ({
            id: nanoid(8),
            name: c.name,
            description: c.description,
            color: c.color,
            order: i,
        }));

        const forum = await this.prisma.forum.create({
            data: {
                tenantId: args.tenantId,
                name: args.name,
                description: args.description,
                scope: this.mapScopeToDb(args.scope),
                scopeId: args.scopeId,
                studyGroupId: args.scope === 'study_group' ? args.scopeId : null,
                categories: categories ? JSON.stringify(categories) : null,
                allowAnonymousPosts: args.settings?.allowAnonymousPosts ?? false,
                requireModeration: args.settings?.requireModeration ?? false,
                allowAttachments: args.settings?.allowAttachments ?? true,
                allowPolls: args.settings?.allowPolls ?? true,
            },
        });

        return this.mapForumFromDb(forum);
    }

    async getForum(args: {
        tenantId: TenantId;
        forumId: string;
    }): Promise<Forum> {
        const forum = await this.prisma.forum.findFirst({
            where: {
                id: args.forumId,
                tenantId: args.tenantId,
            },
        });

        if (!forum) {
            throw new Error('Forum not found');
        }

        return this.mapForumFromDb(forum);
    }

    async listForums(args: {
        tenantId: TenantId;
        scope?: ForumScope;
        scopeId?: string;
        pagination: PaginationArgs;
    }): Promise<PaginatedResult<Forum>> {
        const where: any = {
            tenantId: args.tenantId,
            status: 'ACTIVE',
        };

        if (args.scope) {
            where.scope = this.mapScopeToDb(args.scope);
        }

        if (args.scopeId) {
            where.scopeId = args.scopeId;
        }

        const [items, total] = await Promise.all([
            this.prisma.forum.findMany({
                where,
                skip: args.pagination.offset ?? 0,
                take: args.pagination.limit ?? 20,
                orderBy: { lastPostAt: 'desc' },
            }),
            this.prisma.forum.count({ where }),
        ]);

        return {
            items: items.map((f: any) => this.mapForumFromDb(f)),
            totalCount: total,
            total,
            hasMore: (args.pagination.offset ?? 0) + items.length < total,
        };
    }

    // ---------------------------------------------------------------------------
    // Topics
    // ---------------------------------------------------------------------------

    async createTopic(args: {
        tenantId: TenantId;
        forumId: string;
        authorId: UserId;
        title: string;
        content: string;
        contentFormat?: 'markdown' | 'html' | 'plain';
        categoryId?: string;
        attachments?: Array<{ name: string; type: string; url: string }>;
    }): Promise<ForumTopic> {
        const forum = await this.prisma.forum.findUnique({
            where: { id: args.forumId },
        });

        if (!forum) {
            throw new Error('Forum not found');
        }

        const baseSlug = slugify(args.title, { lower: true, strict: true });
        const slug = `${baseSlug}-${nanoid(6)}`;

        // Determine initial status based on moderation settings
        const status = forum.requireModeration ? 'PENDING' : 'PUBLISHED';

        const topic = await this.prisma.forumTopic.create({
            data: {
                forumId: args.forumId,
                categoryId: args.categoryId,
                title: args.title,
                slug,
                authorId: args.authorId,
                authorName: '', // Would be populated from user service
                content: args.content,
                contentFormat: args.contentFormat ?? 'markdown',
                attachments: args.attachments ? JSON.stringify(args.attachments) : null,
                status,
            },
        });

        // Update forum stats
        await this.prisma.forum.update({
            where: { id: args.forumId },
            data: {
                topicCount: { increment: 1 },
                lastPostAt: new Date(),
            },
        });

        await this.publishActivity(args.tenantId, {
            type: 'CREATED_TOPIC',
            actorId: args.authorId,
            targetType: 'forum_topic',
            targetId: topic.id,
            targetTitle: args.title,
        });

        return this.mapTopicFromDb(topic);
    }

    async getTopic(args: {
        tenantId: TenantId;
        topicId: string;
        userId?: UserId;
    }): Promise<ForumTopic> {
        const topic = await this.prisma.forumTopic.findUnique({
            where: { id: args.topicId },
        });

        if (!topic) {
            throw new Error('Topic not found');
        }

        // Increment view count
        if (args.userId) {
            await this.prisma.forumTopic.update({
                where: { id: args.topicId },
                data: { viewCount: { increment: 1 } },
            });
        }

        return this.mapTopicFromDb(topic);
    }

    async listTopics(args: {
        tenantId: TenantId;
        forumId: string;
        categoryId?: string;
        isPinned?: boolean;
        isAnswered?: boolean;
        searchQuery?: string;
        sortBy?: 'newest' | 'active' | 'popular';
        pagination: PaginationArgs;
    }): Promise<PaginatedResult<ForumTopic>> {
        const where: any = {
            forumId: args.forumId,
            status: 'PUBLISHED',
        };

        if (args.categoryId) {
            where.categoryId = args.categoryId;
        }

        if (args.isPinned !== undefined) {
            where.isPinned = args.isPinned;
        }

        if (args.isAnswered !== undefined) {
            where.isAnswered = args.isAnswered;
        }

        if (args.searchQuery) {
            where.OR = [
                { title: { contains: args.searchQuery } },
                { content: { contains: args.searchQuery } },
            ];
        }

        const orderBy: any = {};
        switch (args.sortBy) {
            case 'active':
                orderBy.lastReplyAt = 'desc';
                break;
            case 'popular':
                orderBy.viewCount = 'desc';
                break;
            case 'newest':
            default:
                orderBy.createdAt = 'desc';
        }

        const [items, total] = await Promise.all([
            this.prisma.forumTopic.findMany({
                where,
                skip: args.pagination.offset ?? 0,
                take: args.pagination.limit ?? 20,
                orderBy: [
                    { isPinned: 'desc' }, // Pinned topics first
                    orderBy,
                ],
            }),
            this.prisma.forumTopic.count({ where }),
        ]);

        return {
            items: items.map((t: any) => this.mapTopicFromDb(t)),
            totalCount: total,
            total,
            hasMore: (args.pagination.offset ?? 0) + items.length < total,
        };
    }

    async updateTopic(args: {
        tenantId: TenantId;
        topicId: string;
        userId: UserId;
        patch: Partial<Pick<ForumTopic, 'title' | 'content' | 'categoryId'>>;
    }): Promise<ForumTopic> {
        const topic = await this.prisma.forumTopic.findUnique({
            where: { id: args.topicId },
        });

        if (!topic) {
            throw new Error('Topic not found');
        }

        if (topic.authorId !== args.userId) {
            throw new Error('Only the author can edit this topic');
        }

        const data: any = { updatedAt: new Date() };
        if (args.patch.title !== undefined) {
            data.title = args.patch.title;
        }
        if (args.patch.content !== undefined) {
            data.content = args.patch.content;
        }
        if (args.patch.categoryId !== undefined) {
            data.categoryId = args.patch.categoryId;
        }

        const updated = await this.prisma.forumTopic.update({
            where: { id: args.topicId },
            data,
        });

        return this.mapTopicFromDb(updated);
    }

    async togglePinTopic(args: {
        tenantId: TenantId;
        topicId: string;
        userId: UserId;
        pinned: boolean;
    }): Promise<ForumTopic> {
        // In a real app, check if user is forum moderator
        const topic = await this.prisma.forumTopic.update({
            where: { id: args.topicId },
            data: { isPinned: args.pinned },
        });

        return this.mapTopicFromDb(topic);
    }

    async toggleLockTopic(args: {
        tenantId: TenantId;
        topicId: string;
        userId: UserId;
        locked: boolean;
    }): Promise<ForumTopic> {
        // In a real app, check if user is forum moderator
        const topic = await this.prisma.forumTopic.update({
            where: { id: args.topicId },
            data: { isLocked: args.locked },
        });

        return this.mapTopicFromDb(topic);
    }

    async deleteTopic(args: {
        tenantId: TenantId;
        topicId: string;
        userId: UserId;
    }): Promise<void> {
        const topic = await this.prisma.forumTopic.findUnique({
            where: { id: args.topicId },
        });

        if (!topic) {
            throw new Error('Topic not found');
        }

        // Check if author or moderator
        if (topic.authorId !== args.userId) {
            // In a real app, also check if user is moderator
            throw new Error('Permission denied');
        }

        await this.prisma.forumTopic.update({
            where: { id: args.topicId },
            data: { status: 'DELETED' },
        });

        // Update forum stats
        await this.prisma.forum.update({
            where: { id: topic.forumId },
            data: { topicCount: { decrement: 1 } },
        });
    }

    // ---------------------------------------------------------------------------
    // Posts
    // ---------------------------------------------------------------------------

    async createPost(args: {
        tenantId: TenantId;
        topicId: string;
        authorId: UserId;
        content: string;
        contentFormat?: 'markdown' | 'html' | 'plain';
        parentId?: string;
        isAnonymous?: boolean;
        attachments?: Array<{ name: string; type: string; url: string }>;
    }): Promise<ForumPost> {
        const topic = await this.prisma.forumTopic.findUnique({
            where: { id: args.topicId },
            include: { forum: true },
        });

        if (!topic) {
            throw new Error('Topic not found');
        }

        if (topic.isLocked) {
            throw new Error('Topic is locked');
        }

        let depth = 0;
        if (args.parentId) {
            const parent = await this.prisma.forumPost.findUnique({
                where: { id: args.parentId },
            });
            if (parent) {
                depth = parent.depth + 1;
            }
        }

        const status = topic.forum.requireModeration ? 'PENDING' : 'PUBLISHED';

        const post = await this.prisma.forumPost.create({
            data: {
                topicId: args.topicId,
                authorId: args.authorId,
                authorName: args.isAnonymous ? 'Anonymous' : '', // Would be populated
                isAnonymous: args.isAnonymous ?? false,
                content: args.content,
                contentFormat: args.contentFormat ?? 'markdown',
                parentId: args.parentId,
                depth,
                attachments: args.attachments ? JSON.stringify(args.attachments) : null,
                status,
            },
        });

        // Update topic stats
        await this.prisma.forumTopic.update({
            where: { id: args.topicId },
            data: {
                replyCount: { increment: 1 },
                lastReplyAt: new Date(),
                lastReplyBy: args.authorId,
            },
        });

        // Update forum stats
        await this.prisma.forum.update({
            where: { id: topic.forumId },
            data: {
                postCount: { increment: 1 },
                lastPostAt: new Date(),
            },
        });

        // Notify topic author
        if (topic.authorId !== args.authorId) {
            await this.createNotification(args.tenantId, topic.authorId, {
                type: 'TOPIC_REPLY',
                title: 'New reply to your topic',
                body: `Someone replied to "${topic.title}"`,
                targetType: 'forum_topic',
                targetId: args.topicId,
                actorId: args.isAnonymous ? undefined : args.authorId,
            });
        }

        await this.publishActivity(args.tenantId, {
            type: 'REPLIED_TOPIC',
            actorId: args.authorId,
            targetType: 'forum_post',
            targetId: post.id,
            targetTitle: topic.title,
        });

        return this.mapPostFromDb(post);
    }

    async listPosts(args: {
        tenantId: TenantId;
        topicId: string;
        parentId?: string;
        pagination: PaginationArgs;
    }): Promise<PaginatedResult<ForumPost>> {
        const where: any = {
            topicId: args.topicId,
            status: 'PUBLISHED',
        };

        if (args.parentId !== undefined) {
            where.parentId = args.parentId ?? null;
        }

        const [items, total] = await Promise.all([
            this.prisma.forumPost.findMany({
                where,
                skip: args.pagination.offset ?? 0,
                take: args.pagination.limit ?? 50,
                orderBy: { createdAt: 'asc' },
                include: {
                    reactions: true,
                },
            }),
            this.prisma.forumPost.count({ where }),
        ]);

        return {
            items: items.map((p: any) => this.mapPostFromDb(p)),
            totalCount: total,
            total,
            hasMore: (args.pagination.offset ?? 0) + items.length < total,
        };
    }

    async updatePost(args: {
        tenantId: TenantId;
        postId: string;
        userId: UserId;
        content: string;
        editReason?: string;
    }): Promise<ForumPost> {
        const post = await this.prisma.forumPost.findUnique({
            where: { id: args.postId },
        });

        if (!post) {
            throw new Error('Post not found');
        }

        if (post.authorId !== args.userId) {
            throw new Error('Only the author can edit this post');
        }

        // Store edit history
        const editHistory = post.editHistory
            ? JSON.parse(post.editHistory)
            : [];
        editHistory.push({
            editedAt: new Date().toISOString(),
            previousContent: post.content,
            editReason: args.editReason,
        });

        const updated = await this.prisma.forumPost.update({
            where: { id: args.postId },
            data: {
                content: args.content,
                isEdited: true,
                editedAt: new Date(),
                editHistory: JSON.stringify(editHistory),
            },
        });

        return this.mapPostFromDb(updated);
    }

    async deletePost(args: {
        tenantId: TenantId;
        postId: string;
        userId: UserId;
    }): Promise<void> {
        const post = await this.prisma.forumPost.findUnique({
            where: { id: args.postId },
        });

        if (!post) {
            throw new Error('Post not found');
        }

        if (post.authorId !== args.userId) {
            throw new Error('Permission denied');
        }

        await this.prisma.forumPost.update({
            where: { id: args.postId },
            data: { status: 'DELETED' },
        });

        // Update topic stats
        await this.prisma.forumTopic.update({
            where: { id: post.topicId },
            data: { replyCount: { decrement: 1 } },
        });
    }

    async markAsAnswer(args: {
        tenantId: TenantId;
        topicId: string;
        postId: string;
        userId: UserId;
    }): Promise<ForumPost> {
        const topic = await this.prisma.forumTopic.findUnique({
            where: { id: args.topicId },
        });

        if (!topic) {
            throw new Error('Topic not found');
        }

        if (topic.authorId !== args.userId) {
            throw new Error('Only the topic author can mark an answer');
        }

        // Unmark previous answer if exists
        if (topic.answerId) {
            await this.prisma.forumPost.update({
                where: { id: topic.answerId },
                data: { isAcceptedAnswer: false },
            });
        }

        // Mark new answer
        const post = await this.prisma.forumPost.update({
            where: { id: args.postId },
            data: { isAcceptedAnswer: true },
        });

        await this.prisma.forumTopic.update({
            where: { id: args.topicId },
            data: {
                isAnswered: true,
                answerId: args.postId,
            },
        });

        return this.mapPostFromDb(post);
    }

    async reactToPost(args: {
        tenantId: TenantId;
        postId: string;
        userId: UserId;
        reaction: ReactionType;
    }): Promise<ForumPost> {
        const existing = await this.prisma.postReaction.findFirst({
            where: {
                postId: args.postId,
                userId: args.userId,
                type: this.mapReactionToDb(args.reaction),
            },
        });

        if (existing) {
            throw new Error('Already reacted');
        }

        await this.prisma.postReaction.create({
            data: {
                postId: args.postId,
                userId: args.userId,
                type: this.mapReactionToDb(args.reaction),
            },
        });

        // Update like count for 'like' reactions
        if (args.reaction === 'like') {
            await this.prisma.forumPost.update({
                where: { id: args.postId },
                data: { likeCount: { increment: 1 } },
            });
        }

        const post = await this.prisma.forumPost.findUnique({
            where: { id: args.postId },
            include: { reactions: true },
        });

        if (!post) {
            throw new Error('Post not found');
        }

        // Notify post author
        if (post.authorId !== args.userId && !post.isAnonymous) {
            await this.createNotification(args.tenantId, post.authorId, {
                type: 'POST_REACTION',
                title: 'New reaction',
                body: `Someone reacted to your post`,
                targetType: 'forum_post',
                targetId: args.postId,
                actorId: args.userId,
            });
        }

        return this.mapPostFromDb(post);
    }

    async removeReaction(args: {
        tenantId: TenantId;
        postId: string;
        userId: UserId;
        reaction: ReactionType;
    }): Promise<ForumPost> {
        const reaction = await this.prisma.postReaction.findFirst({
            where: {
                postId: args.postId,
                userId: args.userId,
                type: this.mapReactionToDb(args.reaction),
            },
        });

        if (reaction) {
            await this.prisma.postReaction.delete({
                where: { id: reaction.id },
            });

            if (args.reaction === 'like') {
                await this.prisma.forumPost.update({
                    where: { id: args.postId },
                    data: { likeCount: { decrement: 1 } },
                });
            }
        }

        const post = await this.prisma.forumPost.findUnique({
            where: { id: args.postId },
            include: { reactions: true },
        });

        if (!post) {
            throw new Error('Post not found');
        }

        return this.mapPostFromDb(post);
    }

    // ---------------------------------------------------------------------------
    // Moderation
    // ---------------------------------------------------------------------------

    async reportContent(args: {
        tenantId: TenantId;
        contentType: 'topic' | 'post';
        contentId: string;
        reporterId: UserId;
        reason: string;
        details?: string;
    }): Promise<{ reportId: string }> {
        const report = await (this.prisma as any).contentReport.create({
            data: {
                tenantId: args.tenantId,
                contentType: args.contentType,
                contentId: args.contentId,
                reporterId: args.reporterId,
                reason: args.reason,
                details: args.details ?? null,
                status: 'PENDING',
            },
        });
        return { reportId: report.id };
    }

    async moderateContent(args: {
        tenantId: TenantId;
        contentType: 'topic' | 'post';
        contentId: string;
        moderatorId: UserId;
        action: 'approve' | 'hide' | 'delete';
        note?: string;
    }): Promise<void> {
        const newStatus =
            args.action === 'approve'
                ? 'PUBLISHED'
                : args.action === 'hide'
                    ? 'HIDDEN'
                    : 'DELETED';

        if (args.contentType === 'topic') {
            await this.prisma.forumTopic.update({
                where: { id: args.contentId },
                data: {
                    status: newStatus,
                    moderatedBy: args.moderatorId,
                    moderatedAt: new Date(),
                    moderationNote: args.note,
                },
            });
        } else {
            await this.prisma.forumPost.update({
                where: { id: args.contentId },
                data: {
                    status: newStatus,
                    moderatedBy: args.moderatorId,
                    moderatedAt: new Date(),
                },
            });
        }
    }

    // ---------------------------------------------------------------------------
    // Private Helpers
    // ---------------------------------------------------------------------------

    private async publishActivity(
        tenantId: string,
        activity: {
            type: string;
            actorId: string;
            targetType: string;
            targetId: string;
            targetTitle: string;
        }
    ): Promise<void> {
        await this.prisma.socialActivity.create({
            data: {
                tenantId,
                actorId: activity.actorId,
                actorName: '',
                type: activity.type as any,
                targetType: activity.targetType,
                targetId: activity.targetId,
                targetTitle: activity.targetTitle,
            },
        });

        if (this.redis) {
            await this.redis.publish(
                `social:activity:${tenantId}`,
                JSON.stringify(activity)
            );
        }
    }

    private async createNotification(
        tenantId: string,
        userId: string,
        notification: {
            type: string;
            title: string;
            body: string;
            targetType?: string;
            targetId?: string;
            actorId?: string;
        }
    ): Promise<void> {
        await this.prisma.socialNotification.create({
            data: {
                tenantId,
                userId,
                type: notification.type as any,
                title: notification.title,
                body: notification.body,
                targetType: notification.targetType,
                targetId: notification.targetId,
                actorId: notification.actorId,
            },
        });

        if (this.redis) {
            await this.redis.publish(
                `social:notification:${userId}`,
                JSON.stringify(notification)
            );
        }
    }

    // Mapping helpers
    private mapScopeToDb(scope: ForumScope): string {
        const map: Record<ForumScope, string> = {
            global: 'GLOBAL',
            study_group: 'STUDY_GROUP',
            classroom: 'CLASSROOM',
            module: 'MODULE',
        };
        return map[scope];
    }

    private mapScopeFromDb(scope: string): ForumScope {
        const map: Record<string, ForumScope> = {
            GLOBAL: 'global',
            STUDY_GROUP: 'study_group',
            CLASSROOM: 'classroom',
            MODULE: 'module',
        };
        return map[scope] ?? 'global';
    }

    private mapReactionToDb(reaction: ReactionType): string {
        const map: Record<ReactionType, string> = {
            like: 'LIKE',
            helpful: 'HELPFUL',
            insightful: 'INSIGHTFUL',
            question: 'QUESTION',
            celebrate: 'CELEBRATE',
        };
        return map[reaction];
    }

    private mapReactionFromDb(reaction: string): ReactionType {
        const map: Record<string, ReactionType> = {
            LIKE: 'like',
            HELPFUL: 'helpful',
            INSIGHTFUL: 'insightful',
            QUESTION: 'question',
            CELEBRATE: 'celebrate',
        };
        return map[reaction] ?? 'like';
    }

    private mapForumFromDb(forum: any): Forum {
        return {
            id: forum.id,
            tenantId: forum.tenantId,
            name: forum.name,
            description: forum.description,
            iconUrl: forum.iconUrl ?? undefined,
            scope: this.mapScopeFromDb(forum.scope),
            scopeId: forum.scopeId ?? undefined,
            allowAnonymousPosts: forum.allowAnonymousPosts,
            requireModeration: forum.requireModeration,
            allowAttachments: forum.allowAttachments,
            allowPolls: forum.allowPolls,
            categories: forum.categories ? JSON.parse(forum.categories) : [],
            topicCount: forum.topicCount,
            postCount: forum.postCount,
            lastPostAt: forum.lastPostAt ?? undefined,
            status: forum.status.toLowerCase() as any,
            createdAt: forum.createdAt,
            updatedAt: forum.updatedAt,
        };
    }

    private mapTopicFromDb(topic: any): ForumTopic {
        return {
            id: topic.id,
            forumId: topic.forumId,
            categoryId: topic.categoryId ?? undefined,
            title: topic.title,
            slug: topic.slug,
            authorId: topic.authorId,
            authorName: topic.authorName,
            content: topic.content,
            contentFormat: topic.contentFormat as any,
            attachments: topic.attachments ? JSON.parse(topic.attachments) : undefined,
            viewCount: topic.viewCount,
            replyCount: topic.replyCount,
            likeCount: topic.likeCount,
            isPinned: topic.isPinned,
            isLocked: topic.isLocked,
            isAnswered: topic.isAnswered,
            answerId: topic.answerId ?? undefined,
            status: topic.status.toLowerCase() as any,
            moderatedBy: topic.moderatedBy ?? undefined,
            moderatedAt: topic.moderatedAt ?? undefined,
            moderationNote: topic.moderationNote ?? undefined,
            createdAt: topic.createdAt,
            updatedAt: topic.updatedAt,
            lastReplyAt: topic.lastReplyAt ?? undefined,
            lastReplyBy: topic.lastReplyBy ?? undefined,
        };
    }

    private mapPostFromDb(post: any): ForumPost {
        return {
            id: post.id,
            topicId: post.topicId,
            authorId: post.authorId,
            authorName: post.authorName,
            isAnonymous: post.isAnonymous,
            content: post.content,
            contentFormat: post.contentFormat as any,
            attachments: post.attachments ? JSON.parse(post.attachments) : undefined,
            parentId: post.parentId ?? undefined,
            depth: post.depth,
            likeCount: post.likeCount,
            isAcceptedAnswer: post.isAcceptedAnswer,
            status: post.status.toLowerCase() as any,
            moderatedBy: post.moderatedBy ?? undefined,
            moderatedAt: post.moderatedAt ?? undefined,
            isEdited: post.isEdited,
            editedAt: post.editedAt ?? undefined,
            editHistory: post.editHistory ? JSON.parse(post.editHistory) : undefined,
            createdAt: post.createdAt,
            updatedAt: post.updatedAt,
        };
    }
}
