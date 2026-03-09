import { prisma } from '../db/client';
import type { Article, Prisma } from '../../generated/prisma-client/index.js';

export class KnowledgeBaseService {
    /**
     * Get all articles with optional filtering
     */
    async getArticles(filters: { category?: string; tags?: string[] } = {}) {
        const where: Prisma.ArticleWhereInput = { isPublished: true };

        if (filters.category) {
            where.category = filters.category;
        }

        if (filters.tags && filters.tags.length > 0) {
            where.tags = { hasSome: filters.tags };
        }

        return prisma.article.findMany({
            where,
            include: {
                author: {
                    select: {
                        id: true,
                        name: true,
                        email: true,
                    },
                },
            },
            orderBy: { createdAt: 'desc' },
        });
    }

    /**
     * Get a single article by ID
     */
    async getArticle(id: string) {
        const article = await prisma.article.findUnique({
            where: { id },
            include: {
                author: {
                    select: {
                        id: true,
                        name: true,
                        email: true,
                    },
                },
            },
        });

        if (article) {
            // Increment views asynchronously
            await prisma.article.update({
                where: { id },
                data: { views: { increment: 1 } },
            });
        }

        return article;
    }

    /**
     * Create a new article
     */
    async createArticle(data: {
        title: string;
        content: string;
        summary?: string;
        category: string;
        tags: string[];
        authorId: string;
        isPublished?: boolean;
    }) {
        return prisma.article.create({
            data,
        });
    }

    /**
     * Update an article
     */
    async updateArticle(id: string, data: Partial<Article>) {
        return prisma.article.update({
            where: { id },
            data,
        });
    }

    /**
     * Get all unique categories
     */
    async getCategories() {
        const categories = await prisma.article.findMany({
            select: { category: true },
            distinct: ['category'],
        });
        return categories.map((c) => c.category);
    }

    /**
     * Get top contributors
     */
    async getTopContributors(limit = 5) {
        const contributors = await prisma.article.groupBy({
            by: ['authorId'],
            _count: {
                id: true,
            },
            orderBy: {
                _count: {
                    id: 'desc',
                },
            },
            take: limit,
        });

        // Fetch user details
        const userIds = contributors.map((c) => c.authorId);
        const users = await prisma.user.findMany({
            where: { id: { in: userIds } },
            select: { id: true, name: true },
        });

        return contributors.map((c) => ({
            authorId: c.authorId,
            count: c._count.id,
            name: users.find((u) => u.id === c.authorId)?.name || 'Unknown',
        }));
    }
}

export const knowledgeBaseService = new KnowledgeBaseService();
