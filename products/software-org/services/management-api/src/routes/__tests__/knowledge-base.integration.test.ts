import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { knowledgeBaseRoutes } from '../knowledge-base.js';

const { mockKnowledgeBaseService } = vi.hoisted(() => ({
    mockKnowledgeBaseService: {
        getArticles: vi.fn(),
        getArticle: vi.fn(),
        createArticle: vi.fn(),
        getCategories: vi.fn(),
        getTopContributors: vi.fn(),
    },
}));

vi.mock('../../services/knowledge-base.service', () => ({
    knowledgeBaseService: mockKnowledgeBaseService,
}));

describe('Knowledge Base Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(knowledgeBaseRoutes, { prefix: '/api/v1/knowledge-base' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should normalize comma-delimited tags when listing articles', async () => {
        mockKnowledgeBaseService.getArticles.mockResolvedValue([
            { id: 'article-1', title: 'Architecture RFC', tags: ['architecture', 'platform'] },
        ]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/knowledge-base/articles?category=engineering&tags=architecture, platform ,',
        });

        expect(response.statusCode).toBe(200);
        expect(mockKnowledgeBaseService.getArticles).toHaveBeenCalledWith({
            category: 'engineering',
            tags: ['architecture', 'platform'],
        });
        expect(response.json()).toEqual([
            { id: 'article-1', title: 'Architecture RFC', tags: ['architecture', 'platform'] },
        ]);
    });

    it('should return 404 when an article is missing', async () => {
        mockKnowledgeBaseService.getArticle.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/knowledge-base/articles/missing-article',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({ error: 'Article not found' });
    });

    it('should create an article through the service layer', async () => {
        mockKnowledgeBaseService.createArticle.mockResolvedValue({
            id: 'article-2',
            title: 'Incident Runbook',
            category: 'operations',
            tags: ['incident', 'runbook'],
        });

        const payload = {
            title: 'Incident Runbook',
            content: 'Follow the incident response steps.',
            category: 'operations',
            tags: ['incident', 'runbook'],
            authorId: 'author-1',
            isPublished: true,
        };

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/knowledge-base/articles',
            payload,
        });

        expect(response.statusCode).toBe(200);
        expect(mockKnowledgeBaseService.createArticle).toHaveBeenCalledWith(payload);
        expect(response.json()).toEqual(expect.objectContaining({ id: 'article-2', title: 'Incident Runbook' }));
    });

    it('should apply the default contributor limit when none is provided', async () => {
        mockKnowledgeBaseService.getTopContributors.mockResolvedValue([
            { authorId: 'author-1', articleCount: 12 },
        ]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/knowledge-base/contributors',
        });

        expect(response.statusCode).toBe(200);
        expect(mockKnowledgeBaseService.getTopContributors).toHaveBeenCalledWith(5);
        expect(response.json()).toEqual([{ authorId: 'author-1', articleCount: 12 }]);
    });

    it('should return knowledge base categories', async () => {
        mockKnowledgeBaseService.getCategories.mockResolvedValue(['engineering', 'operations']);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/knowledge-base/categories',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual(['engineering', 'operations']);
    });
});