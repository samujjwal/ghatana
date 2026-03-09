import { FastifyInstance } from 'fastify';
import { knowledgeBaseService } from '../services/knowledge-base.service';

export async function knowledgeBaseRoutes(fastify: FastifyInstance) {
    fastify.get<{ Querystring: { category?: string; tags?: string | string[] } }>(
        '/articles',
        {
            schema: {
                querystring: {
                    type: 'object',
                    properties: {
                        category: { type: 'string' },
                        tags: {
                            anyOf: [
                                { type: 'string' },
                                { type: 'array', items: { type: 'string' } },
                            ],
                        },
                    },
                },
            },
        },
        async (request) => {
            const { category, tags } = request.query;
            const normalizedTags =
                typeof tags === 'string'
                    ? tags.split(',').map((t) => t.trim()).filter(Boolean)
                    : tags;

            return knowledgeBaseService.getArticles({
                category,
                tags: normalizedTags,
            });
        }
    );

    fastify.get<{ Params: { id: string } }>(
        '/articles/:id',
        {
            schema: {
                params: {
                    type: 'object',
                    required: ['id'],
                    properties: {
                        id: { type: 'string' },
                    },
                },
            },
        },
        async (request, reply) => {
            const { id } = request.params;
            const article = await knowledgeBaseService.getArticle(id);
            if (!article) return reply.status(404).send({ error: 'Article not found' });
            return article;
        }
    );

    fastify.post<{
        Body: {
            title: string;
            content: string;
            summary?: string;
            category: string;
            tags: string[];
            authorId: string;
            isPublished?: boolean;
        };
    }>(
        '/articles',
        {
            schema: {
                body: {
                    type: 'object',
                    required: ['title', 'content', 'category', 'tags', 'authorId'],
                    properties: {
                        title: { type: 'string' },
                        content: { type: 'string' },
                        summary: { type: 'string' },
                        category: { type: 'string' },
                        tags: { type: 'array', items: { type: 'string' } },
                        authorId: { type: 'string' },
                        isPublished: { type: 'boolean' },
                    },
                },
            },
        },
        async (request) => {
            return knowledgeBaseService.createArticle(request.body);
        }
    );

    fastify.get('/categories', async () => {
        return knowledgeBaseService.getCategories();
    });

    fastify.get<{ Querystring: { limit?: number } }>(
        '/contributors',
        {
            schema: {
                querystring: {
                    type: 'object',
                    properties: {
                        limit: { type: 'number', minimum: 1, maximum: 50, default: 5 },
                    },
                },
            },
        },
        async (request) => {
            const limit = request.query.limit ?? 5;
            return knowledgeBaseService.getTopContributors(limit);
        }
    );
}
