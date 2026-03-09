/**
 * @doc.type class
 * @doc.purpose Exports user data for GDPR/CCPA compliance data portability
 * @doc.layer product
 * @doc.pattern Service
 */

import { createWriteStream } from 'fs';
import { mkdir, rm, writeFile } from 'fs/promises';
import { join } from 'path';
import archiver from 'archiver';
import type { PrismaClient } from '@ghatana/tutorputor-db';
import type { ExportedFile } from './types';
import { createLogger } from '../../utils/logger.js';
const logger = createLogger('exporter');

export class DataExporter {
    private prisma: PrismaClient;
    private exportDir: string;

    constructor(prisma: PrismaClient, exportDir: string = '/tmp/exports') {
        this.prisma = prisma;
        this.exportDir = exportDir;
    }

    async exportUserData(
        userId: string,
        tenantId: string
    ): Promise<{ filePath: string; files: ExportedFile[] }> {
        const exportId = `export_${userId}_${Date.now()}`;
        const exportPath = join(this.exportDir, exportId);
        const archivePath = join(this.exportDir, `${exportId}.zip`);

        // Create temp directory
        await mkdir(exportPath, { recursive: true });

        const exportedFiles: ExportedFile[] = [];

        try {
            // Export profile
            exportedFiles.push(
                await this.exportProfile(userId, tenantId, exportPath)
            );

            // Export learning progress (Enrollments)
            exportedFiles.push(
                await this.exportLearningProgress(userId, tenantId, exportPath)
            );

            // Export assessments (AssessmentAttempts)
            exportedFiles.push(
                await this.exportAssessmentAttempts(userId, tenantId, exportPath)
            );

            // Export activity logs (LearningEvent)
            exportedFiles.push(
                await this.exportActivityLogs(userId, tenantId, exportPath)
            );

            // Export content (Threads)
            exportedFiles.push(
                await this.exportUserContent(userId, tenantId, exportPath)
            );

            // Create ZIP archive
            await this.createArchive(exportPath, archivePath);

            // Clean up temp directory
            await rm(exportPath, { recursive: true });

            return {
                filePath: archivePath,
                files: exportedFiles.filter((f) => f.recordCount > 0),
            };
        } catch (error) {
            // Clean up on error
            await rm(exportPath, { recursive: true, force: true }).catch(() => { });
            throw error;
        }
    }

    private async exportProfile(
        userId: string,
        tenantId: string,
        exportPath: string
    ): Promise<ExportedFile> {
        const user = await this.prisma.user.findFirst({
            where: { id: userId, tenantId },
            include: {
                ssoLinks: {
                    select: {
                        providerId: true,
                        externalId: true,
                        linkedAt: true,
                        lastLoginAt: true,
                    },
                },
            },
        });

        const data = {
            profile: user
                ? {
                    id: user.id,
                    email: user.email,
                    displayName: user.displayName,
                    role: user.role,
                    createdAt: user.createdAt,
                    updatedAt: user.updatedAt,
                }
                : null,
            ssoLinks: user?.ssoLinks ?? [],
        };

        const filePath = join(exportPath, 'profile.json');
        const content = JSON.stringify(data, null, 2);
        await writeFile(filePath, content);

        return {
            name: 'profile.json',
            type: 'application/json',
            sizeBytes: Buffer.byteLength(content, 'utf-8'),
            recordCount: user ? 1 : 0,
        };
    }

    private async exportLearningProgress(
        userId: string,
        tenantId: string,
        exportPath: string
    ): Promise<ExportedFile> {
        const enrollments = await this.prisma.enrollment.findMany({
            where: { userId, tenantId },
            include: {
                module: {
                    select: {
                        id: true,
                        title: true,
                    },
                },
            },
            orderBy: { updatedAt: 'desc' },
        });

        const data = enrollments.map((e: any) => ({
            moduleId: e.module.id,
            moduleTitle: e.module.title,
            status: e.status,
            completedAt: e.completedAt,
            startedAt: e.startedAt,
            timeSpentSeconds: e.timeSpentSeconds,
        }));

        const filePath = join(exportPath, 'learning_progress.json');
        const content = JSON.stringify(data, null, 2);
        await writeFile(filePath, content);

        return {
            name: 'learning_progress.json',
            type: 'application/json',
            sizeBytes: Buffer.byteLength(content, 'utf-8'),
            recordCount: data.length,
        };
    }

    private async exportAssessmentAttempts(
        userId: string,
        tenantId: string,
        exportPath: string
    ): Promise<ExportedFile> {
        const attempts = await this.prisma.assessmentAttempt.findMany({
            where: { userId, tenantId },
            include: {
                assessment: {
                    select: {
                        id: true,
                        title: true,
                    },
                },
            },
            orderBy: { startedAt: 'desc' },
        });

        const data = attempts.map((a: any) => ({
            assessmentId: a.assessment.id,
            assessmentTitle: a.assessment.title,
            score: a.score,
            maxScore: a.maxScore,
            startedAt: a.startedAt,
            completedAt: a.completedAt,
        }));

        const filePath = join(exportPath, 'assessment_attempts.json');
        const content = JSON.stringify(data, null, 2);
        await writeFile(filePath, content);

        return {
            name: 'assessment_attempts.json',
            type: 'application/json',
            sizeBytes: Buffer.byteLength(content, 'utf-8'),
            recordCount: data.length,
        };
    }

    private async exportActivityLogs(
        userId: string,
        tenantId: string,
        exportPath: string
    ): Promise<ExportedFile> {
        const activities = await this.prisma.learningEvent.findMany({
            where: { userId, tenantId },
            take: 1000,
            orderBy: { timestamp: 'desc' },
        });

        const filePath = join(exportPath, 'activity_logs.json');
        const content = JSON.stringify(activities, null, 2);
        await writeFile(filePath, content);

        return {
            name: 'activity_logs.json',
            type: 'application/json',
            sizeBytes: Buffer.byteLength(content, 'utf-8'),
            recordCount: activities.length,
        };
    }

    private async exportUserContent(
        userId: string,
        tenantId: string,
        exportPath: string
    ): Promise<ExportedFile> {
        // Check if models exist (assuming based on previous grep)
        // If not, we wrap in try-catch or skip
        let threads: any[] = [];
        try {
            threads = await (this.prisma as any).thread.findMany({
                where: { authorId: userId, tenantId },
                select: { id: true, title: true, content: true, createdAt: true }
            });
        } catch (e) { logger.error({ error: e }, 'Error occurred'); throw e; }

        const data = {
            threads
        };

        const filePath = join(exportPath, 'user_content.json');
        const content = JSON.stringify(data, null, 2);
        await writeFile(filePath, content);

        return {
            name: 'user_content.json',
            type: 'application/json',
            sizeBytes: Buffer.byteLength(content, 'utf-8'),
            recordCount: threads.length,
        };
    }

    private createArchive(sourceDir: string, destPath: string): Promise<void> {
        return new Promise((resolve, reject) => {
            const output = createWriteStream(destPath);
            const archive = archiver('zip', {
                zlib: { level: 9 },
            });

            output.on('close', () => resolve());
            archive.on('error', (err: any) => reject(err));

            archive.pipe(output);
            archive.directory(sourceDir, false);
            archive.finalize();
        });
    }
}
