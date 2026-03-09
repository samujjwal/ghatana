import { prisma } from '../db/client';
import type { Prisma } from '../../generated/prisma-client/index.js';

export class InnovationService {
    /**
     * Get all ideas
     */
    async getIdeas(status?: string) {
        const where: Prisma.IdeaWhereInput = {};
        if (status) {
            where.status = status;
        }

        return prisma.idea.findMany({
            where,
            include: {
                author: {
                    select: {
                        id: true,
                        name: true,
                    },
                },
                _count: {
                    select: { experiments: true },
                },
            },
            orderBy: { votes: 'desc' },
        });
    }

    /**
     * Create a new idea
     */
    async createIdea(data: {
        title: string;
        description: string;
        authorId: string;
    }) {
        return prisma.idea.create({
            data: {
                ...data,
                status: 'new',
            },
        });
    }

    /**
     * Vote for an idea
     */
    async voteIdea(id: string) {
        return prisma.idea.update({
            where: { id },
            data: { votes: { increment: 1 } },
        });
    }

    /**
     * Get all experiments
     */
    async getExperiments() {
        return prisma.experiment.findMany({
            include: {
                idea: {
                    select: {
                        title: true,
                        author: {
                            select: { name: true },
                        },
                    },
                },
            },
            orderBy: { updatedAt: 'desc' },
        });
    }

    /**
     * Create an experiment from an idea
     */
    async createExperiment(data: {
        ideaId: string;
        title: string;
        startDate?: Date;
        endDate?: Date;
    }) {
        return prisma.experiment.create({
            data: {
                ...data,
                status: 'planning',
                progress: 0,
            },
        });
    }

    /**
     * Update experiment progress/status
     */
    async updateExperiment(id: string, data: Prisma.ExperimentUpdateInput) {
        return prisma.experiment.update({
            where: { id },
            data,
        });
    }
}

export const innovationService = new InnovationService();
