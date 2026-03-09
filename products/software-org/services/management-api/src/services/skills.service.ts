import { prisma } from '../db/client';
import type { DevelopmentPlan, Prisma, Skill, SkillGap, UserSkill } from '../../generated/prisma-client/index.js';

export class SkillsService {
    /**
     * Get all defined skills
     */
    async getSkills() {
        return prisma.skill.findMany({
            orderBy: { name: 'asc' },
        });
    }

    /**
     * Get skills for a specific user
     */
    async getUserSkills(userId: string) {
        return prisma.userSkill.findMany({
            where: { userId },
            include: {
                skill: true,
            },
        });
    }

    /**
     * Update or create a user skill
     */
    async updateUserSkill(userId: string, skillId: string, data: { proficiency: number; interest: string }) {
        return prisma.userSkill.upsert({
            where: {
                userId_skillId: {
                    userId,
                    skillId,
                },
            },
            update: {
                proficiency: data.proficiency,
                interest: data.interest,
                lastAssessed: new Date(),
            },
            create: {
                userId,
                skillId,
                proficiency: data.proficiency,
                interest: data.interest,
            },
        });
    }

    /**
     * Get skill gaps (optionally filtered by team/department)
     */
    async getSkillGaps(teamId?: string) {
        const where: Prisma.SkillGapWhereInput = {};
        if (teamId) {
            where.teamId = teamId;
        }

        return prisma.skillGap.findMany({
            where,
            include: {
                skill: true,
            },
        });
    }

    /**
     * Get development plans for a user
     */
    async getDevelopmentPlans(userId: string) {
        return prisma.developmentPlan.findMany({
            where: { userId },
            include: {
                skill: true,
            },
        });
    }

    /**
     * Create a development plan
     */
    async createDevelopmentPlan(data: {
        userId: string;
        skillId: string;
        type: string;
        dueDate?: Date;
    }) {
        return prisma.developmentPlan.create({
            data: {
                ...data,
                status: 'planned',
                progress: 0,
            },
        });
    }

    /**
     * Update development plan progress
     */
    async updateDevelopmentPlan(id: string, data: Partial<DevelopmentPlan>) {
        return prisma.developmentPlan.update({
            where: { id },
            data,
        });
    }
}

export const skillsService = new SkillsService();
