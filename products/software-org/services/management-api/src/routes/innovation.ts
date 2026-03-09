import { FastifyInstance } from 'fastify';
import { innovationService } from '../services/innovation.service';

export async function innovationRoutes(fastify: FastifyInstance) {
    fastify.get<{ Querystring: { status?: string } }>(
        '/ideas',
        {
            schema: {
                querystring: {
                    type: 'object',
                    properties: {
                        status: { type: 'string' },
                    },
                },
            },
        },
        async (request) => {
            return innovationService.getIdeas(request.query.status);
        }
    );

    fastify.post<{ Body: { title: string; description: string; authorId: string } }>(
        '/ideas',
        {
            schema: {
                body: {
                    type: 'object',
                    required: ['title', 'description', 'authorId'],
                    properties: {
                        title: { type: 'string' },
                        description: { type: 'string' },
                        authorId: { type: 'string' },
                    },
                },
            },
        },
        async (request) => {
            return innovationService.createIdea(request.body);
        }
    );

    fastify.post<{ Params: { id: string } }>(
        '/ideas/:id/vote',
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
        async (request) => {
            return innovationService.voteIdea(request.params.id);
        }
    );

    fastify.get('/experiments', async () => {
        return innovationService.getExperiments();
    });

    fastify.post<{
        Body: {
            ideaId: string;
            title: string;
            startDate?: string;
            endDate?: string;
        };
    }>(
        '/experiments',
        {
            schema: {
                body: {
                    type: 'object',
                    required: ['ideaId', 'title'],
                    properties: {
                        ideaId: { type: 'string' },
                        title: { type: 'string' },
                        startDate: { type: 'string' },
                        endDate: { type: 'string' },
                    },
                },
            },
        },
        async (request) => {
            const { ideaId, title, startDate, endDate } = request.body;
            return innovationService.createExperiment({
                ideaId,
                title,
                startDate: startDate ? new Date(startDate) : undefined,
                endDate: endDate ? new Date(endDate) : undefined,
            });
        }
    );

    fastify.put<{
        Params: { id: string };
        Body: { status?: string; progress?: number; startDate?: string; endDate?: string };
    }>(
        '/experiments/:id',
        {
            schema: {
                params: {
                    type: 'object',
                    required: ['id'],
                    properties: {
                        id: { type: 'string' },
                    },
                },
                body: {
                    type: 'object',
                    properties: {
                        status: { type: 'string' },
                        progress: { type: 'number', minimum: 0, maximum: 100 },
                        startDate: { type: 'string' },
                        endDate: { type: 'string' },
                    },
                },
            },
        },
        async (request) => {
            const { id } = request.params;
            const { startDate, endDate, ...rest } = request.body;

            return innovationService.updateExperiment(id, {
                ...rest,
                ...(startDate ? { startDate: new Date(startDate) } : {}),
                ...(endDate ? { endDate: new Date(endDate) } : {}),
            });
        }
    );
}
