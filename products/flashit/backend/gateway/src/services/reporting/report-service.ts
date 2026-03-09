/**
 * Advanced Reporting System for Flashit
 * Automated report generation with PDF export and scheduling
 *
 * @doc.type service
 * @doc.purpose Advanced reporting with multiple formats and scheduling
 * @doc.layer product
 * @doc.pattern ReportingService
 */

import { Queue, Job, Worker } from 'bullmq';
import Redis from 'ioredis';
import PDFDocument from 'pdfkit';
import nodemailer from 'nodemailer';
import fs from 'fs/promises';
import { createObjectCsvWriter } from 'csv-writer';
import { join } from 'path';
import { prisma } from '../../lib/prisma.js';

// Redis connection
const redis = new Redis({
  host: process.env.REDIS_HOST || 'localhost',
  port: parseInt(process.env.REDIS_PORT || '6379'),
  ...(process.env.REDIS_PASSWORD ? { password: process.env.REDIS_PASSWORD } : {}),
  maxRetriesPerRequest: null,
});

// Queue configuration
const REPORTING_QUEUE = 'flashit-reporting';
const REPORTS_DIR = process.env.REPORTS_DIR || '/tmp/flashit-reports';

// Report interfaces
export interface ReportConfig {
  id: string;
  userId: string;
  name: string;
  description: string;
  type: 'personal_summary' | 'productivity_analysis' | 'emotion_trends' | 'sphere_insights' | 'custom';
  format: 'pdf' | 'csv' | 'json' | 'excel';
  schedule?: ReportSchedule;
  filters: ReportFilters;
  sections: ReportSection[];
  created_at: Date;
  updated_at: Date;
}

export interface ReportSchedule {
  frequency: 'daily' | 'weekly' | 'monthly' | 'quarterly';
  time: string; // HH:MM format
  timezone: string;
  enabled: boolean;
  lastRun?: Date;
  nextRun?: Date;
}

export interface ReportFilters {
  dateRange: {
    start: Date;
    end: Date;
  };
  sphereIds?: string[];
  emotions?: string[];
  tags?: string[];
  importance?: {
    min: number;
    max: number;
  };
}

export interface ReportSection {
  type: 'summary' | 'chart' | 'table' | 'insights' | 'raw_data';
  title: string;
  config: Record<string, any>;
  order: number;
}

export interface ReportJob {
  reportConfigId: string;
  userId: string;
  format: 'pdf' | 'csv' | 'json' | 'excel';
  filters: ReportFilters;
  delivery?: {
    method: 'email' | 'download';
    email?: string;
    subject?: string;
  };
}

// Create reporting queue
export const reportingQueue = new Queue<ReportJob>(REPORTING_QUEUE, {
  connection: redis,
  defaultJobOptions: {
    removeOnComplete: 50,
    removeOnFail: 25,
    attempts: 3,
    backoff: {
      type: 'exponential',
      delay: 2000,
    },
  },
});

/**
 * Report data aggregator
 */
export class ReportDataAggregator {

  /**
   * Get comprehensive report data for a user
   */
  static async getReportData(userId: string, filters: ReportFilters): Promise<any> {
    const { start, end } = filters.dateRange;

    const [
      userSummary,
      dailyAnalytics,
      sphereData,
      emotionData,
      productivityData,
      searchData,
      insightsData,
      momentsData
    ] = await Promise.all([
      this.getUserSummary(userId, start, end),
      this.getDailyAnalytics(userId, start, end),
      this.getSphereAnalytics(userId, start, end, filters.sphereIds),
      this.getEmotionAnalytics(userId, start, end),
      this.getProductivityAnalytics(userId, start, end),
      this.getSearchAnalytics(userId, start, end),
      this.getInsightsData(userId, start, end),
      this.getMomentsData(userId, start, end, filters),
    ]);

    return {
      summary: userSummary,
      dailyAnalytics,
      sphereData,
      emotionData,
      productivityData,
      searchData,
      insightsData,
      momentsData,
      generatedAt: new Date(),
      period: {
        start,
        end,
        days: Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)),
      },
    };
  }

  private static async getUserSummary(userId: string, start: Date, end: Date): Promise<any> {
    const summary = await prisma.$queryRaw`
      SELECT
        COUNT(DISTINCT DATE(m.captured_at)) as active_days,
        COUNT(m.id) as total_moments,
        COUNT(DISTINCT m.sphere_id) as spheres_accessed,
        AVG(m.importance) as avg_importance,
        COUNT(*) FILTER (WHERE m.content_transcript IS NOT NULL) as transcribed_moments,
        SUM(LENGTH(m.content_text)) as total_content_length
      FROM moments m
      WHERE m.user_id = ${userId}::uuid
        AND m.captured_at >= ${start}
        AND m.captured_at <= ${end}
        AND m.deleted_at IS NULL
    ` as any[];

    const searches = await prisma.$queryRaw`
      SELECT COUNT(*) as search_count
      FROM audit_events
      WHERE user_id = ${userId}::uuid
        AND event_type = 'SEMANTIC_SEARCH_PERFORMED'
        AND created_at >= ${start}
        AND created_at <= ${end}
    ` as any[];

    return {
      ...summary[0],
      searchCount: parseInt(searches[0]?.search_count || '0'),
    };
  }

  private static async getDailyAnalytics(userId: string, start: Date, end: Date): Promise<any[]> {
    return await prisma.$queryRaw`
      SELECT
        date_bucket,
        moments_created,
        productivity_score,
        emotion_diversity_score,
        searches_performed,
        session_duration_minutes
      FROM analytics.user_analytics
      WHERE user_id = ${userId}::uuid
        AND date_bucket >= ${start}
        AND date_bucket <= ${end}
        AND time_bucket = 'day'
      ORDER BY date_bucket ASC
    ` as any[];
  }

  private static async getSphereAnalytics(
    userId: string,
    start: Date,
    end: Date,
    sphereIds?: string[]
  ): Promise<any[]> {
    const whereClause = sphereIds
      ? `AND m.sphere_id = ANY(${JSON.stringify(sphereIds)}::uuid[])`
      : '';

    return await prisma.$queryRaw`
      SELECT
        s.id,
        s.name,
        s.description,
        COUNT(m.id) as moment_count,
        AVG(m.importance) as avg_importance,
        COUNT(DISTINCT DATE(m.captured_at)) as active_days
      FROM spheres s
      JOIN moments m ON s.id = m.sphere_id
      WHERE m.user_id = ${userId}::uuid
        AND m.captured_at >= ${start}
        AND m.captured_at <= ${end}
        AND m.deleted_at IS NULL
        ${whereClause}
      GROUP BY s.id, s.name, s.description
      ORDER BY moment_count DESC
    ` as any[];
  }

  private static async getEmotionAnalytics(userId: string, start: Date, end: Date): Promise<any> {
    const emotions = await prisma.$queryRaw`
      SELECT
        unnest(emotions) as emotion,
        COUNT(*) as count
      FROM moments
      WHERE user_id = ${userId}::uuid
        AND captured_at >= ${start}
        AND captured_at <= ${end}
        AND deleted_at IS NULL
        AND array_length(emotions, 1) > 0
      GROUP BY emotion
      ORDER BY count DESC
    ` as any[];

    const trends = await prisma.$queryRaw`
      SELECT
        DATE(captured_at) as date,
        array_agg(DISTINCT unnest(emotions)) as daily_emotions,
        COUNT(*) as moment_count
      FROM moments
      WHERE user_id = ${userId}::uuid
        AND captured_at >= ${start}
        AND captured_at <= ${end}
        AND deleted_at IS NULL
      GROUP BY DATE(captured_at)
      ORDER BY date ASC
    ` as any[];

    return { emotions, trends };
  }

  private static async getProductivityAnalytics(userId: string, start: Date, end: Date): Promise<any> {
    return await prisma.$queryRaw`
      SELECT
        date_bucket as date,
        productivity_score,
        moments_created,
        importance_avg,
        session_duration_minutes
      FROM analytics.user_analytics
      WHERE user_id = ${userId}::uuid
        AND date_bucket >= ${start}
        AND date_bucket <= ${end}
        AND time_bucket = 'day'
        AND productivity_score IS NOT NULL
      ORDER BY date_bucket ASC
    ` as any[];
  }

  private static async getSearchAnalytics(userId: string, start: Date, end: Date): Promise<any> {
    return await prisma.$queryRaw`
      SELECT
        DATE(created_at) as date,
        COUNT(*) as search_count,
        jsonb_agg(details->>'query') as queries,
        jsonb_agg(details->>'type') as search_types
      FROM audit_events
      WHERE user_id = ${userId}::uuid
        AND event_type = 'SEMANTIC_SEARCH_PERFORMED'
        AND created_at >= ${start}
        AND created_at <= ${end}
      GROUP BY DATE(created_at)
      ORDER BY date ASC
    ` as any[];
  }

  private static async getInsightsData(userId: string, start: Date, end: Date): Promise<any[]> {
    return await prisma.$queryRaw`
      SELECT
        insight_type,
        insight_category,
        title,
        description,
        confidence_score,
        priority_score,
        is_actionable,
        action_taken,
        created_at
      FROM analytics.user_insights
      WHERE user_id = ${userId}::uuid
        AND created_at >= ${start}
        AND created_at <= ${end}
      ORDER BY priority_score DESC, created_at DESC
    ` as any[];
  }

  private static async getMomentsData(
    userId: string,
    start: Date,
    end: Date,
    filters: ReportFilters
  ): Promise<any[]> {
    let whereConditions = [
      `user_id = ${userId}::uuid`,
      `captured_at >= ${start}`,
      `captured_at <= ${end}`,
      `deleted_at IS NULL`,
    ];

    const params: any[] = [];

    if (filters.sphereIds && filters.sphereIds.length > 0) {
      whereConditions.push(`sphere_id = ANY(${JSON.stringify(filters.sphereIds)}::uuid[])`);
    }

    if (filters.emotions && filters.emotions.length > 0) {
      whereConditions.push(`emotions && ${JSON.stringify(filters.emotions)}::text[]`);
    }

    if (filters.tags && filters.tags.length > 0) {
      whereConditions.push(`tags && ${JSON.stringify(filters.tags)}::text[]`);
    }

    if (filters.importance) {
      if (filters.importance.min !== undefined) {
        whereConditions.push(`importance >= ${filters.importance.min}`);
      }
      if (filters.importance.max !== undefined) {
        whereConditions.push(`importance <= ${filters.importance.max}`);
      }
    }

    const query = `
      SELECT
        m.id,
        m.content_text,
        m.content_transcript,
        m.captured_at,
        m.importance,
        m.emotions,
        m.tags,
        s.name as sphere_name
      FROM moments m
      JOIN spheres s ON m.sphere_id = s.id
      WHERE ${whereConditions.join(' AND ')}
      ORDER BY m.captured_at DESC
      LIMIT 1000
    `;

    return await prisma.$queryRawUnsafe(query) as any[];
  }
}

/**
 * PDF Report Generator
 */
export class PDFReportGenerator {

  static async generateReport(data: any, config: Partial<ReportConfig>): Promise<Buffer> {
    return new Promise((resolve, reject) => {
      const doc = new PDFDocument({ margin: 50 });
      const chunks: Buffer[] = [];

      doc.on('data', chunk => chunks.push(chunk));
      doc.on('end', () => resolve(Buffer.concat(chunks)));
      doc.on('error', reject);

      try {
        this.addHeader(doc, data, config);
        this.addSummarySection(doc, data);
        this.addProductivitySection(doc, data);
        this.addEmotionSection(doc, data);
        this.addSphereSection(doc, data);
        this.addInsightsSection(doc, data);

        doc.end();
      } catch (error) {
        reject(error);
      }
    });
  }

  private static addHeader(doc: PDFDocument, data: any, config: Partial<ReportConfig>): void {
    // Header
    doc.fontSize(24).text('Flashit Analytics Report', { align: 'center' });
    doc.fontSize(14).text(`${config.name || 'Personal Summary'}`, { align: 'center' });

    const period = data.period;
    doc.text(
      `Period: ${period.start.toLocaleDateString()} - ${period.end.toLocaleDateString()} (${period.days} days)`,
      { align: 'center' }
    );

    doc.text(`Generated: ${data.generatedAt.toLocaleString()}`, { align: 'center' });
    doc.moveDown(2);
  }

  private static addSummarySection(doc: PDFDocument, data: any): void {
    const summary = data.summary;

    doc.fontSize(18).text('Summary Overview', { underline: true });
    doc.moveDown(0.5);

    doc.fontSize(12);
    doc.text(`Active Days: ${summary.active_days || 0}`);
    doc.text(`Total Moments: ${summary.total_moments || 0}`);
    doc.text(`Spheres Accessed: ${summary.spheres_accessed || 0}`);
    doc.text(`Average Importance: ${parseFloat(summary.avg_importance || 0).toFixed(1)}/5`);
    doc.text(`Transcribed Moments: ${summary.transcribed_moments || 0}`);
    doc.text(`Total Searches: ${summary.searchCount || 0}`);

    doc.moveDown(1.5);
  }

  private static addProductivitySection(doc: PDFDocument, data: any): void {
    const productivity = data.productivityData || [];

    doc.fontSize(18).text('Productivity Analysis', { underline: true });
    doc.moveDown(0.5);

    if (productivity.length > 0) {
      const avgProductivity = productivity.reduce((sum: number, day: any) =>
        sum + (parseFloat(day.productivity_score) || 0), 0
      ) / productivity.length;

      const totalMoments = productivity.reduce((sum: number, day: any) =>
        sum + (parseInt(day.moments_created) || 0), 0
      );

      doc.fontSize(12);
      doc.text(`Average Productivity Score: ${avgProductivity.toFixed(1)}%`);
      doc.text(`Total Moments Created: ${totalMoments}`);
      doc.text(`Most Productive Day: ${this.findMostProductiveDay(productivity)}`);

      // Productivity trend
      doc.moveDown(0.5);
      doc.text('Daily Productivity Trend:', { underline: true });
      productivity.slice(0, 7).forEach((day: any) => {
        doc.text(
          `${new Date(day.date).toLocaleDateString()}: ${parseFloat(day.productivity_score || 0).toFixed(1)}%`
        );
      });
    } else {
      doc.fontSize(12).text('No productivity data available for this period.');
    }

    doc.moveDown(1.5);
  }

  private static addEmotionSection(doc: PDFDocument, data: any): void {
    const emotions = data.emotionData?.emotions || [];

    doc.fontSize(18).text('Emotional Insights', { underline: true });
    doc.moveDown(0.5);

    if (emotions.length > 0) {
      doc.fontSize(12);
      doc.text('Top Emotions:');
      emotions.slice(0, 5).forEach((emotion: any) => {
        doc.text(`• ${emotion.emotion}: ${emotion.count} times`);
      });

      // Emotion diversity
      const totalEmotionInstances = emotions.reduce((sum: number, e: any) => sum + parseInt(e.count), 0);
      const uniqueEmotions = emotions.length;
      const diversity = uniqueEmotions > 0 ? (uniqueEmotions / Math.max(totalEmotionInstances, 1)) * 100 : 0;

      doc.moveDown(0.5);
      doc.text(`Emotional Diversity Score: ${diversity.toFixed(1)}%`);
      doc.text(`Total Unique Emotions: ${uniqueEmotions}`);
    } else {
      doc.fontSize(12).text('No emotion data available for this period.');
    }

    doc.moveDown(1.5);
  }

  private static addSphereSection(doc: PDFDocument, data: any): void {
    const spheres = data.sphereData || [];

    doc.fontSize(18).text('Sphere Activity', { underline: true });
    doc.moveDown(0.5);

    if (spheres.length > 0) {
      doc.fontSize(12);
      spheres.forEach((sphere: any) => {
        doc.text(`• ${sphere.name}: ${sphere.moment_count} moments (${parseFloat(sphere.avg_importance || 0).toFixed(1)} avg importance)`);
      });
    } else {
      doc.fontSize(12).text('No sphere activity data available for this period.');
    }

    doc.moveDown(1.5);
  }

  private static addInsightsSection(doc: PDFDocument, data: any): void {
    const insights = data.insightsData || [];

    doc.fontSize(18).text('AI Insights', { underline: true });
    doc.moveDown(0.5);

    if (insights.length > 0) {
      doc.fontSize(12);
      insights.slice(0, 5).forEach((insight: any) => {
        doc.text(`• ${insight.title}`, { underline: true });
        doc.text(`  ${insight.description}`);
        doc.text(`  Confidence: ${parseFloat(insight.confidence_score).toFixed(1)}% | Category: ${insight.insight_category}`);
        doc.moveDown(0.3);
      });
    } else {
      doc.fontSize(12).text('No AI insights available for this period.');
    }
  }

  private static findMostProductiveDay(productivity: any[]): string {
    if (productivity.length === 0) return 'N/A';

    const maxDay = productivity.reduce((max, day) =>
      parseFloat(day.productivity_score || 0) > parseFloat(max.productivity_score || 0) ? day : max
    );

    return `${new Date(maxDay.date).toLocaleDateString()} (${parseFloat(maxDay.productivity_score || 0).toFixed(1)}%)`;
  }
}

/**
 * CSV Report Generator
 */
export class CSVReportGenerator {

  static async generateReport(data: any, config: Partial<ReportConfig>): Promise<string> {
    const csvFilePath = join(REPORTS_DIR, `report-${Date.now()}.csv`);

    // Flatten daily analytics data for CSV
    const records = data.dailyAnalytics.map((day: any) => ({
      date: day.date_bucket,
      moments_created: day.moments_created || 0,
      productivity_score: parseFloat(day.productivity_score || 0).toFixed(2),
      emotion_diversity: parseFloat(day.emotion_diversity_score || 0).toFixed(2),
      searches_performed: day.searches_performed || 0,
      session_duration: parseFloat(day.session_duration_minutes || 0).toFixed(2),
    }));

    const csvWriter = createObjectCsvWriter({
      path: csvFilePath,
      header: [
        { id: 'date', title: 'Date' },
        { id: 'moments_created', title: 'Moments Created' },
        { id: 'productivity_score', title: 'Productivity Score' },
        { id: 'emotion_diversity', title: 'Emotion Diversity' },
        { id: 'searches_performed', title: 'Searches Performed' },
        { id: 'session_duration', title: 'Session Duration (min)' },
      ],
    });

    await csvWriter.writeRecords(records);

    return csvFilePath;
  }
}

/**
 * Report service
 */
export class ReportingService {

  /**
   * Generate a report
   */
  static async generateReport(
    userId: string,
    format: 'pdf' | 'csv' | 'json',
    filters: ReportFilters,
    config?: Partial<ReportConfig>
  ): Promise<string> {
    // Enqueue report generation job
    const job = await reportingQueue.add('generate-report', {
      reportConfigId: config?.id || `temp-${Date.now()}`,
      userId,
      format,
      filters,
    });

    return job.id!;
  }

  /**
   * Schedule a report
   */
  static async scheduleReport(config: ReportConfig): Promise<void> {
    if (!config.schedule?.enabled) return;

    // Calculate next run time
    const nextRun = this.calculateNextRun(config.schedule);

    await reportingQueue.add(
      'scheduled-report',
      {
        reportConfigId: config.id,
        userId: config.userId,
        format: config.format,
        filters: config.filters,
      },
      {
        delay: nextRun.getTime() - Date.now(),
        repeat: {
          pattern: this.getCronPattern(config.schedule),
        },
      }
    );
  }

  /**
   * Get report status
   */
  static async getReportStatus(jobId: string): Promise<any> {
    try {
      const job = await Job.fromId(reportingQueue, jobId);

      if (!job) {
        return { status: 'not_found' };
      }

      const state = await job.getState();

      return {
        status: state,
        progress: job.progress,
        result: state === 'completed' ? job.returnvalue : undefined,
        error: state === 'failed' ? job.failedReason : undefined,
      };
    } catch (error) {
      return { status: 'error', error: 'Failed to get job status' };
    }
  }

  private static calculateNextRun(schedule: ReportSchedule): Date {
    const now = new Date();
    const [hours, minutes] = schedule.time.split(':').map(Number);

    const nextRun = new Date();
    nextRun.setHours(hours, minutes, 0, 0);

    switch (schedule.frequency) {
      case 'daily':
        if (nextRun <= now) {
          nextRun.setDate(nextRun.getDate() + 1);
        }
        break;
      case 'weekly':
        nextRun.setDate(nextRun.getDate() + (7 - nextRun.getDay()));
        break;
      case 'monthly':
        nextRun.setMonth(nextRun.getMonth() + 1, 1);
        break;
      case 'quarterly':
        nextRun.setMonth(nextRun.getMonth() + 3, 1);
        break;
    }

    return nextRun;
  }

  private static getCronPattern(schedule: ReportSchedule): string {
    const [hours, minutes] = schedule.time.split(':');

    switch (schedule.frequency) {
      case 'daily':
        return `${minutes} ${hours} * * *`;
      case 'weekly':
        return `${minutes} ${hours} * * 1`; // Monday
      case 'monthly':
        return `${minutes} ${hours} 1 * *`; // 1st of month
      case 'quarterly':
        return `${minutes} ${hours} 1 */3 *`; // 1st of every 3rd month
      default:
        return `${minutes} ${hours} * * *`;
    }
  }
}

/**
 * Reporting worker - processes report generation jobs
 */
const reportingWorker = new Worker<ReportJob>(
  REPORTING_QUEUE,
  async (job: Job<ReportJob>) => {
    const { data } = job;

    try {
      await job.updateProgress(10);

      // Ensure reports directory exists
      await fs.mkdir(REPORTS_DIR, { recursive: true });

      // Get report data
      const reportData = await ReportDataAggregator.getReportData(data.userId, data.filters);

      await job.updateProgress(50);

      // Generate report based on format
      let filePath: string;
      let buffer: Buffer | null = null;

      switch (data.format) {
        case 'pdf':
          buffer = await PDFReportGenerator.generateReport(reportData, {});
          filePath = join(REPORTS_DIR, `report-${job.id}.pdf`);
          await fs.writeFile(filePath, buffer);
          break;

        case 'csv':
          filePath = await CSVReportGenerator.generateReport(reportData, {});
          break;

        case 'json':
          filePath = join(REPORTS_DIR, `report-${job.id}.json`);
          await fs.writeFile(filePath, JSON.stringify(reportData, null, 2));
          break;

        default:
          throw new Error(`Unsupported format: ${data.format}`);
      }

      await job.updateProgress(90);

      // Handle delivery
      if (data.delivery?.method === 'email' && data.delivery.email) {
        await this.sendEmailReport(data.delivery.email, filePath, data.format);
      }

      await job.updateProgress(100);

      return {
        filePath,
        downloadUrl: `/api/analytics/reports/${job.id}/download`,
        expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000), // 24 hours
      };

    } catch (error: any) {
      console.error('Report generation failed:', error);
      throw error;
    }
  },
  {
    connection: redis,
    concurrency: 3, // Process up to 3 reports concurrently
  }
);

// Email service for report delivery
async function sendEmailReport(email: string, filePath: string, format: string): Promise<void> {
  // Configure nodemailer (would use actual SMTP in production)
  const transporter = nodemailer.createTransporter({
    host: process.env.SMTP_HOST || 'localhost',
    port: parseInt(process.env.SMTP_PORT || '587'),
    secure: false,
    auth: {
      user: process.env.SMTP_USER,
      pass: process.env.SMTP_PASS,
    },
  });

  const attachment = {
    filename: `flashit-report.${format}`,
    path: filePath,
  };

  await transporter.sendMail({
    from: process.env.SMTP_FROM || 'reports@flashit.app',
    to: email,
    subject: 'Your Flashit Analytics Report',
    text: 'Please find your analytics report attached.',
    html: `
      <h2>Your Flashit Analytics Report</h2>
      <p>Hello! Your requested analytics report is ready and attached to this email.</p>
      <p>The report contains insights from your Flashit activity and can help you understand your productivity patterns and growth.</p>
      <p>Best regards,<br>The Flashit Team</p>
    `,
    attachments: [attachment],
  });
}

// Worker event handlers
reportingWorker.on('completed', (job) => {
  console.log(`Report generation job ${job.id} completed successfully`);
});

reportingWorker.on('failed', (job, err) => {
  console.error(`Report generation job ${job?.id} failed:`, err);
});

// Graceful shutdown
process.on('SIGINT', async () => {
  console.log('Shutting down reporting worker...');
  await reportingWorker.close();
  await prisma.$disconnect();
  await redis.quit();
  process.exit(0);
});

export { reportingWorker };
