/**
 * Transcription Usage Tracking Service
 * 
 * @doc.type service
 * @doc.purpose Track transcription usage for billing and limits enforcement
 * @doc.layer product
 * @doc.pattern Service
 */

import { prisma } from '../../lib/prisma.js';
import type { PrismaClient } from '../../../generated/prisma/index.js';

/**
 * Transcription usage record
 */
export interface TranscriptionUsageRecord {
    userId: string;
    momentId?: string;
    durationSeconds: number;
    model: string;
    costUsd?: number;
    status: 'pending' | 'completed' | 'failed';
    errorMessage?: string;
}

/**
 * Transcription usage statistics
 */
export interface TranscriptionUsageStats {
    userId: string;
    period: {
        start: Date;
        end: Date;
    };
    totalHours: number;
    totalCost: number;
    transcriptionCount: number;
    averageDuration: number;
    modelBreakdown: Record<string, {
        count: number;
        hours: number;
        cost: number;
    }>;
}

/**
 * Track transcription usage
 */
export async function trackTranscriptionUsage(
    record: TranscriptionUsageRecord,
    tx?: PrismaClient
): Promise<string> {
    const db = tx || prisma;

    const usage = await db.transcriptionUsage.create({
        data: {
            userId: record.userId,
            momentId: record.momentId,
            durationSeconds: record.durationSeconds,
            model: record.model,
            costUsd: record.costUsd,
            status: record.status,
            errorMessage: record.errorMessage,
        },
    });

    return usage.id;
}

/**
 * Update transcription usage status
 */
export async function updateTranscriptionStatus(
    usageId: string,
    status: 'completed' | 'failed',
    errorMessage?: string,
    tx?: PrismaClient
): Promise<void> {
    const db = tx || prisma;

    await db.transcriptionUsage.update({
        where: { id: usageId },
        data: {
            status,
            errorMessage,
        },
    });
}

/**
 * Get transcription usage statistics for a user
 */
export async function getTranscriptionUsageStats(
    userId: string,
    startDate?: Date,
    endDate?: Date,
    tx?: PrismaClient
): Promise<TranscriptionUsageStats> {
    const db = tx || prisma;

    const start = startDate || (() => {
        const date = new Date();
        date.setDate(1);
        date.setHours(0, 0, 0, 0);
        return date;
    })();

    const end = endDate || new Date();

    // Get all transcriptions in period
    const transcriptions = await db.transcriptionUsage.findMany({
        where: {
            userId,
            createdAt: {
                gte: start,
                lte: end,
            },
            status: 'completed',
        },
        select: {
            durationSeconds: true,
            model: true,
            costUsd: true,
        },
    });

    // Calculate statistics
    const totalSeconds = transcriptions.reduce((sum, t) => sum + t.durationSeconds, 0);
    const totalHours = totalSeconds / 3600;
    const totalCost = transcriptions.reduce((sum, t) => sum + (Number(t.costUsd) || 0), 0);
    const transcriptionCount = transcriptions.length;
    const averageDuration = transcriptionCount > 0 ? totalSeconds / transcriptionCount : 0;

    // Model breakdown
    const modelBreakdown: Record<string, { count: number; hours: number; cost: number }> = {};

    for (const t of transcriptions) {
        if (!modelBreakdown[t.model]) {
            modelBreakdown[t.model] = { count: 0, hours: 0, cost: 0 };
        }
        modelBreakdown[t.model].count++;
        modelBreakdown[t.model].hours += t.durationSeconds / 3600;
        modelBreakdown[t.model].cost += Number(t.costUsd) || 0;
    }

    return {
        userId,
        period: { start, end },
        totalHours,
        totalCost,
        transcriptionCount,
        averageDuration,
        modelBreakdown,
    };
}

/**
 * Check if user has exceeded transcription limit
 */
export async function checkTranscriptionLimit(
    userId: string,
    tier: 'free' | 'pro' | 'teams',
    additionalSeconds: number = 0,
    tx?: PrismaClient
): Promise<{
    allowed: boolean;
    currentHours: number;
    limitHours: number;
    percentUsed: number;
}> {
    const db = tx || prisma;

    const limits = {
        free: 10,
        pro: 50,
        teams: 200,
    };

    const limitHours = limits[tier];

    // Get current month's usage
    const startOfMonth = new Date();
    startOfMonth.setDate(1);
    startOfMonth.setHours(0, 0, 0, 0);

    const result = await db.transcriptionUsage.aggregate({
        where: {
            userId,
            createdAt: { gte: startOfMonth },
            status: 'completed',
        },
        _sum: {
            durationSeconds: true,
        },
    });

    const currentSeconds = (result._sum.durationSeconds || 0) + additionalSeconds;
    const currentHours = currentSeconds / 3600;
    const percentUsed = (currentHours / limitHours) * 100;
    const allowed = currentHours < limitHours;

    return {
        allowed,
        currentHours,
        limitHours,
        percentUsed,
    };
}

/**
 * Calculate transcription cost based on model and duration
 */
export function calculateTranscriptionCost(
    model: string,
    durationSeconds: number
): number {
    // Pricing per minute (based on Whisper API pricing)
    const pricePerMinute: Record<string, number> = {
        'whisper-1': 0.006, // $0.006 per minute
        'whisper-large': 0.006,
        'whisper-medium': 0.004,
        'whisper-small': 0.002,
    };

    const price = pricePerMinute[model] || pricePerMinute['whisper-1'];
    const minutes = durationSeconds / 60;

    return price * minutes;
}

/**
 * Get recent transcription usage for user
 */
export async function getRecentTranscriptionUsage(
    userId: string,
    limit: number = 10,
    tx?: PrismaClient
): Promise<Array<{
    id: string;
    momentId: string | null;
    durationSeconds: number;
    model: string;
    costUsd: number | null;
    status: string;
    createdAt: Date;
}>> {
    const db = tx || prisma;

    return db.transcriptionUsage.findMany({
        where: { userId },
        orderBy: { createdAt: 'desc' },
        take: limit,
        select: {
            id: true,
            momentId: true,
            durationSeconds: true,
            model: true,
            costUsd: true,
            status: true,
            createdAt: true,
        },
    });
}
