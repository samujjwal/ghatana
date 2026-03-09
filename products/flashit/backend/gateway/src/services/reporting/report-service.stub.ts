import { randomUUID } from 'crypto';
import { Queue, Worker, Job } from 'bullmq';
import { S3Client, PutObjectCommand } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import PDFDocument from 'pdfkit';
import { createObjectCsvWriter } from 'csv-writer';
import archiver from 'archiver';
import { Readable } from 'stream';
import fs from 'fs';
import path from 'path';
import os from 'os';
import { prisma } from '../../lib/prisma.js';
import type { PrismaClient } from '../../../generated/prisma/index.js';

type ReportFormat = 'pdf' | 'csv' | 'json';
type ReportPeriod = 'week' | 'month' | 'quarter' | 'year';

type ReportParams = {
    format: ReportFormat;
    period: ReportPeriod;
    sphereIds?: string[];
    emotions?: string[];
    tags?: string[];
    importance?: { min?: number; max?: number };
    delivery?: { method: 'download' | 'email'; email?: string };
};

type ReportJob = {
    userId: string;
    params: ReportParams;
    jobId: string;
};

type ReportStatus = 'pending' | 'processing' | 'completed' | 'failed';

// Lazy initialization
let s3Client: S3Client | null = null;
let reportQueue: Queue<ReportJob> | null = null;
let reportWorker: Worker<ReportJob> | null = null;

function getPrisma() {
    return prisma;
}

function getS3Client(): S3Client {
    if (!s3Client) {
        s3Client = new S3Client({
            region: process.env.S3_REGION || 'us-east-1',
            credentials: process.env.AWS_ACCESS_KEY_ID && process.env.AWS_SECRET_ACCESS_KEY ? {
                accessKeyId: process.env.AWS_ACCESS_KEY_ID,
                secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY,
            } : undefined,
        });
    }
    return s3Client;
}

function getReportQueue(): Queue<ReportJob> {
    if (!reportQueue) {
        reportQueue = new Queue<ReportJob>('report-generation', {
            connection: {
                host: process.env.REDIS_HOST || 'localhost',
                port: parseInt(process.env.REDIS_PORT || '6379'),
            },
        });
    }
    return reportQueue;
}

export class ReportingService {
    /**
     * Generate a report (async job)
     */
    static async generateReport(userId: string, params: ReportParams): Promise<{ jobId: string }> {
        const jobId = randomUUID();
        const queue = getReportQueue();
        
        await queue.add('generate-report', {
            userId,
            params,
            jobId,
        }, {
            jobId,
            attempts: 3,
            backoff: {
                type: 'exponential',
                delay: 2000,
            },
        });

        return { jobId };
    }

    /**
     * Get report status and download URL
     */
    static async getReportStatus(jobId: string): Promise<{ 
        status: ReportStatus; 
        url: string | null;
        progress?: number;
        error?: string;
    }> {
        const queue = getReportQueue();
        const job = await queue.getJob(jobId);

        if (!job) {
            return { status: 'failed', url: null, error: 'Job not found' };
        }

        const state = await job.getState();
        
        if (state === 'completed') {
            const result = job.returnvalue as { url: string };
            return { status: 'completed', url: result.url };
        }

        if (state === 'failed') {
            return { 
                status: 'failed', 
                url: null, 
                error: job.failedReason || 'Unknown error'
            };
        }

        if (state === 'active') {
            return { 
                status: 'processing', 
                url: null,
                progress: job.progress as number || 0
            };
        }

        return { status: 'pending', url: null };
    }

    /**
     * Process report generation job
     */
    static async processReportJob(job: Job<ReportJob>): Promise<{ url: string }> {
        const { userId, params } = job.data;
        const db = getPrisma();

        // Calculate date range
        const endDate = new Date();
        const startDate = new Date();
        
        switch (params.period) {
            case 'week':
                startDate.setDate(endDate.getDate() - 7);
                break;
            case 'month':
                startDate.setMonth(endDate.getMonth() - 1);
                break;
            case 'quarter':
                startDate.setMonth(endDate.getMonth() - 3);
                break;
            case 'year':
                startDate.setFullYear(endDate.getFullYear() - 1);
                break;
        }

        await job.updateProgress(10);

        // Fetch moments data
        const moments = await db.moment.findMany({
            where: {
                userId,
                capturedAt: {
                    gte: startDate,
                    lte: endDate,
                },
                deletedAt: null,
                ...(params.sphereIds && params.sphereIds.length > 0 ? {
                    sphereId: { in: params.sphereIds }
                } : {}),
                ...(params.emotions && params.emotions.length > 0 ? {
                    emotions: { hasSome: params.emotions }
                } : {}),
                ...(params.tags && params.tags.length > 0 ? {
                    tags: { hasSome: params.tags }
                } : {}),
                ...(params.importance ? {
                    importance: {
                        ...(params.importance.min !== undefined ? { gte: params.importance.min } : {}),
                        ...(params.importance.max !== undefined ? { lte: params.importance.max } : {}),
                    }
                } : {}),
            },
            include: {
                sphere: true,
                media: {
                    include: {
                        mediaReference: true,
                    },
                },
            },
            orderBy: {
                capturedAt: 'desc',
            },
        });

        await job.updateProgress(50);

        // Generate report file
        let reportBuffer: Buffer;
        let filename: string;
        let contentType: string;

        if (params.format === 'pdf') {
            reportBuffer = await this.generatePDFReport(moments, params);
            filename = `flashit-report-${params.period}-${Date.now()}.pdf`;
            contentType = 'application/pdf';
        } else if (params.format === 'csv') {
            reportBuffer = await this.generateCSVReport(moments, params);
            filename = `flashit-report-${params.period}-${Date.now()}.csv`;
            contentType = 'text/csv';
        } else {
            reportBuffer = Buffer.from(JSON.stringify(moments, null, 2));
            filename = `flashit-report-${params.period}-${Date.now()}.json`;
            contentType = 'application/json';
        }

        await job.updateProgress(75);

        // Upload to S3
        const s3 = getS3Client();
        const bucket = process.env.S3_BUCKET || 'flashit-reports';
        const key = `reports/${userId}/${filename}`;

        await s3.send(new PutObjectCommand({
            Bucket: bucket,
            Key: key,
            Body: reportBuffer,
            ContentType: contentType,
            Metadata: {
                userId,
                period: params.period,
                format: params.format,
            },
        }));

        await job.updateProgress(90);

        // Generate signed URL (valid for 7 days)
        const command = new PutObjectCommand({ Bucket: bucket, Key: key });
        const url = await getSignedUrl(s3, command, { expiresIn: 7 * 24 * 60 * 60 });

        await job.updateProgress(100);

        return { url };
    }

    /**
     * Generate PDF report
     */
    private static async generatePDFReport(moments: any[], params: ReportParams): Promise<Buffer> {
        return new Promise((resolve, reject) => {
            const chunks: Buffer[] = [];
            const doc = new PDFDocument({ margin: 50 });

            doc.on('data', (chunk) => chunks.push(chunk));
            doc.on('end', () => resolve(Buffer.concat(chunks)));
            doc.on('error', reject);

            // Title
            doc.fontSize(24).text('Flashit Report', { align: 'center' });
            doc.moveDown();
            
            // Metadata
            doc.fontSize(12);
            doc.text(`Period: ${params.period}`, { align: 'left' });
            doc.text(`Generated: ${new Date().toLocaleString()}`, { align: 'left' });
            doc.text(`Total Moments: ${moments.length}`, { align: 'left' });
            doc.moveDown();

            // Summary stats
            const emotionCounts: Record<string, number> = {};
            const sphereCounts: Record<string, number> = {};
            
            moments.forEach(m => {
                m.emotions?.forEach((e: string) => {
                    emotionCounts[e] = (emotionCounts[e] || 0) + 1;
                });
                const sphereName = m.sphere?.name || 'Unknown';
                sphereCounts[sphereName] = (sphereCounts[sphereName] || 0) + 1;
            });

            doc.fontSize(16).text('Summary', { underline: true });
            doc.moveDown(0.5);
            doc.fontSize(12);
            
            doc.text('By Sphere:');
            Object.entries(sphereCounts).forEach(([sphere, count]) => {
                doc.text(`  ${sphere}: ${count}`, { indent: 20 });
            });
            doc.moveDown(0.5);

            doc.text('By Emotion:');
            Object.entries(emotionCounts).forEach(([emotion, count]) => {
                doc.text(`  ${emotion}: ${count}`, { indent: 20 });
            });
            doc.moveDown();

            // Moments list
            doc.addPage();
            doc.fontSize(16).text('Moments', { underline: true });
            doc.moveDown(0.5);

            moments.forEach((moment, index) => {
                if (index > 0 && index % 5 === 0) {
                    doc.addPage();
                }

                doc.fontSize(12);
                doc.text(`${index + 1}. ${new Date(moment.capturedAt).toLocaleString()}`, { bold: true });
                doc.fontSize(10);
                doc.text(`Sphere: ${moment.sphere?.name || 'Unknown'}`, { indent: 20 });
                if (moment.emotions && moment.emotions.length > 0) {
                    doc.text(`Emotions: ${moment.emotions.join(', ')}`, { indent: 20 });
                }
                if (moment.contentText) {
                    const text = moment.contentText.substring(0, 200) + (moment.contentText.length > 200 ? '...' : '');
                    doc.text(text, { indent: 20 });
                }
                doc.moveDown(0.5);
            });

            doc.end();
        });
    }

    /**
     * Generate CSV report
     */
    private static async generateCSVReport(moments: any[], params: ReportParams): Promise<Buffer> {
        const tempFile = path.join(os.tmpdir(), `report-${Date.now()}.csv`);
        
        const csvWriter = createObjectCsvWriter({
            path: tempFile,
            header: [
                { id: 'capturedAt', title: 'Date' },
                { id: 'sphere', title: 'Sphere' },
                { id: 'contentText', title: 'Content' },
                { id: 'emotions', title: 'Emotions' },
                { id: 'tags', title: 'Tags' },
                { id: 'importance', title: 'Importance' },
            ],
        });

        const records = moments.map(m => ({
            capturedAt: new Date(m.capturedAt).toISOString(),
            sphere: m.sphere?.name || 'Unknown',
            contentText: m.contentText || '',
            emotions: (m.emotions || []).join('; '),
            tags: (m.tags || []).join('; '),
            importance: m.importance || '',
        }));

        await csvWriter.writeRecords(records);
        const buffer = fs.readFileSync(tempFile);
        fs.unlinkSync(tempFile);
        
        return buffer;
    }

    /**
     * Initialize the worker
     */
    static initWorker(): void {
        if (reportWorker) {
            return;
        }

        reportWorker = new Worker<ReportJob>(
            'report-generation',
            async (job) => {
                return await this.processReportJob(job);
            },
            {
                connection: {
                    host: process.env.REDIS_HOST || 'localhost',
                    port: parseInt(process.env.REDIS_PORT || '6379'),
                },
                concurrency: 2,
            }
        );

        reportWorker.on('completed', (job) => {
            console.log(`Report job ${job.id} completed`);
        });

        reportWorker.on('failed', (job, err) => {
            console.error(`Report job ${job?.id} failed:`, err);
        });
    }

    /**
     * Cleanup
     */
    static async cleanup(): Promise<void> {
        if (reportWorker) {
            await reportWorker.close();
            reportWorker = null;
        }
        if (reportQueue) {
            await reportQueue.close();
            reportQueue = null;
        }
        if (prisma) {
            await prisma.$disconnect();
            prisma = null;
        }
    }
}
