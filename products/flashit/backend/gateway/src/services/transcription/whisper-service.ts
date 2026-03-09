/**
 * Whisper Transcription Service for Flashit
 * Async audio transcription pipeline with job management
 *
 * @doc.type service
 * @doc.purpose Audio transcription with OpenAI Whisper integration
 * @doc.layer platform
 * @doc.pattern AsyncService
 */

import { Queue, Job, Worker } from 'bullmq';
import Redis from 'ioredis';
import OpenAI from 'openai';
import { S3Client, GetObjectCommand } from '@aws-sdk/client-s3';
import { createReadStream, createWriteStream, unlinkSync } from 'fs';
import { pipeline } from 'stream/promises';
import { join } from 'path';
import { randomUUID } from 'crypto';
import { prisma } from '../../lib/prisma';
import { Readable } from 'stream';
import { VectorEmbeddingService } from '../embeddings/vector-service.js';

// Redis connection
const redis = new Redis({
  host: process.env.REDIS_HOST || 'localhost',
  port: parseInt(process.env.REDIS_PORT || '6379'),
  password: process.env.REDIS_PASSWORD,
  maxRetriesPerRequest: null, // Required by BullMQ
});

// OpenAI client
const openai = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY,
});

// S3 client (reuse from upload service)
const s3 = new S3Client({
  credentials: {
    accessKeyId: process.env.AWS_ACCESS_KEY_ID!,
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY!,
  },
  region: process.env.AWS_REGION || 'us-east-1',
});

// Job queue configuration
const TRANSCRIPTION_QUEUE = 'flashit-transcription';
const TEMP_DIR = process.env.TEMP_DIR || '/tmp/flashit';

// Job data interface
interface TranscriptionJobData {
  mediaReferenceId: string;
  s3Bucket: string;
  s3Key: string;
  userId: string;
  momentId: string;
  audioFormat: string;
  durationMs?: number;
  priority: 'high' | 'normal' | 'low';
  retryCount?: number;
}

// Transcription result interface
interface TranscriptionResult {
  text: string;
  segments: Array<{
    start: number;
    end: number;
    text: string;
    confidence?: number;
  }>;
  language: string;
  confidence: number;
  processingTimeMs: number;
  model: string;
}

// Create transcription queue
export const transcriptionQueue = new Queue<TranscriptionJobData>(TRANSCRIPTION_QUEUE, {
  connection: redis,
  defaultJobOptions: {
    removeOnComplete: 100, // Keep last 100 completed jobs
    removeOnFail: 50, // Keep last 50 failed jobs
    attempts: 3,
    backoff: {
      type: 'exponential',
      delay: 2000,
    },
  },
});

/**
 * Audio preprocessing utility
 */
class AudioPreprocessor {

  /**
   * Download audio from S3 to temporary file
   */
  static async downloadAudio(s3Bucket: string, s3Key: string): Promise<string> {
    const tempFilePath = join(TEMP_DIR, `${randomUUID()}.audio`);

    try {
      const command = new GetObjectCommand({ Bucket: s3Bucket, Key: s3Key });
      const response = await s3.send(command);

      if (!response.Body) {
        throw new Error('S3 response body is empty');
      }

      const stream = response.Body as Readable;
      const writeStream = createWriteStream(tempFilePath);

      await pipeline(stream, writeStream);
      return tempFilePath;
    } catch (error) {
      throw new Error(`Failed to download audio: ${error}`);
    }
  }

  /**
   * Validate audio format and duration
   */
  static async validateAudio(filePath: string): Promise<{
    isValid: boolean;
    duration?: number;
    format?: string;
    error?: string;
  }> {
    try {
      // In production, you'd use ffprobe or similar
      // For now, basic validation based on file size
      const fs = await import('fs/promises');
      const stats = await fs.stat(filePath);

      if (stats.size > 25 * 1024 * 1024) { // 25MB limit for Whisper
        return {
          isValid: false,
          error: 'Audio file too large for transcription (>25MB)',
        };
      }

      if (stats.size < 1000) { // Too small
        return {
          isValid: false,
          error: 'Audio file too small',
        };
      }

      return {
        isValid: true,
        duration: Math.floor(stats.size / 16000), // Rough estimate
        format: 'audio',
      };
    } catch (error: any) {
      return {
        isValid: false,
        error: `Audio validation failed: ${error.message}`,
      };
    }
  }

  /**
   * Convert audio to optimal format for Whisper
   */
  static async optimizeForWhisper(inputPath: string): Promise<string> {
    // In production, use ffmpeg to convert to optimal format:
    // - 16kHz sample rate
    // - Mono channel
    // - MP3 or WAV format
    // For now, return input path (assume already optimized)
    return inputPath;
  }

  /**
   * Cleanup temporary files
   */
  static cleanup(filePaths: string[]) {
    for (const filePath of filePaths) {
      try {
        unlinkSync(filePath);
      } catch (error) {
        console.warn(`Failed to cleanup file ${filePath}:`, error);
      }
    }
  }
}

/**
 * Whisper transcription service
 */
export class WhisperTranscriptionService {

  /**
   * Add transcription job to queue
   */
  static async enqueueTranscription(jobData: TranscriptionJobData): Promise<string> {
    const job = await transcriptionQueue.add('transcribe', jobData, {
      priority: jobData.priority === 'high' ? 10 : jobData.priority === 'low' ? 1 : 5,
      jobId: `transcription-${jobData.mediaReferenceId}`,
    });

    return job.id!;
  }

  /**
   * Get transcription job status
   */
  static async getJobStatus(jobId: string): Promise<{
    status: string;
    progress?: number;
    result?: TranscriptionResult;
    error?: string;
  }> {
    const job = await Job.fromId(transcriptionQueue, jobId);

    if (!job) {
      return { status: 'not_found' };
    }

    const state = await job.getState();

    return {
      status: state,
      progress: job.progress as number,
      result: state === 'completed' ? job.returnvalue : undefined,
      error: state === 'failed' ? job.failedReason : undefined,
    };
  }

  /**
   * Cancel transcription job
   */
  static async cancelJob(jobId: string): Promise<boolean> {
    try {
      const job = await Job.fromId(transcriptionQueue, jobId);
      if (job) {
        await job.remove();
        return true;
      }
      return false;
    } catch (error) {
      return false;
    }
  }

  /**
   * Get queue statistics
   */
  static async getQueueStats(): Promise<{
    waiting: number;
    active: number;
    completed: number;
    failed: number;
  }> {
    const waiting = await transcriptionQueue.getWaiting();
    const active = await transcriptionQueue.getActive();
    const completed = await transcriptionQueue.getCompleted();
    const failed = await transcriptionQueue.getFailed();

    return {
      waiting: waiting.length,
      active: active.length,
      completed: completed.length,
      failed: failed.length,
    };
  }

  /**
   * Core transcription logic (called by worker)
   */
  static async transcribeAudio(jobData: TranscriptionJobData): Promise<TranscriptionResult> {
    const startTime = Date.now();
    let tempFiles: string[] = [];

    try {
      // Download audio from S3
      const audioPath = await AudioPreprocessor.downloadAudio(jobData.s3Bucket, jobData.s3Key);
      tempFiles.push(audioPath);

      // Validate audio
      const validation = await AudioPreprocessor.validateAudio(audioPath);
      if (!validation.isValid) {
        throw new Error(validation.error);
      }

      // Optimize for Whisper
      const optimizedPath = await AudioPreprocessor.optimizeForWhisper(audioPath);
      if (optimizedPath !== audioPath) {
        tempFiles.push(optimizedPath);
      }

      // Transcribe with OpenAI Whisper
      const transcription = await openai.audio.transcriptions.create({
        file: createReadStream(optimizedPath) as any,
        model: 'whisper-1',
        response_format: 'verbose_json',
        language: 'en', // Auto-detect in production
      });

      // Process Whisper response
      const result: TranscriptionResult = {
        text: transcription.text,
        segments: transcription.segments?.map(segment => ({
          start: segment.start,
          end: segment.end,
          text: segment.text,
          confidence: (segment as any).avg_logprob ? Math.exp((segment as any).avg_logprob) : undefined,
        })) || [],
        language: transcription.language || 'en',
        confidence: this.calculateOverallConfidence(transcription),
        processingTimeMs: Date.now() - startTime,
        model: 'whisper-1',
      };

      return result;

    } catch (error: any) {
      throw new Error(`Transcription failed: ${error.message}`);
    } finally {
      // Cleanup temporary files
      AudioPreprocessor.cleanup(tempFiles);
    }
  }

  /**
   * Calculate overall confidence from Whisper response
   */
  private static calculateOverallConfidence(transcription: any): number {
    if (!transcription.segments || transcription.segments.length === 0) {
      return 0.8; // Default confidence
    }

    const confidences = transcription.segments
      .map((s: any) => s.avg_logprob ? Math.exp(s.avg_logprob) : 0.8)
      .filter((c: number) => c > 0);

    if (confidences.length === 0) {
      return 0.8;
    }

    return confidences.reduce((sum: number, conf: number) => sum + conf, 0) / confidences.length;
  }
}

/**
 * Transcription worker - processes jobs from the queue
 */
const transcriptionWorker = new Worker<TranscriptionJobData>(
  TRANSCRIPTION_QUEUE,
  async (job: Job<TranscriptionJobData>) => {
    const { data } = job;

    try {
      // Update progress
      await job.updateProgress(10);

      // Use shared Prisma client

      // Update progress
      await job.updateProgress(20);

      // Perform transcription
      const result = await WhisperTranscriptionService.transcribeAudio(data);

      // Update progress
      await job.updateProgress(80);

      // Store transcription result in database
      await prisma.moment.update({
        where: { id: data.momentId },
        data: {
          contentTranscript: result.text,
          metadata: {
            transcription: {
              segments: result.segments,
              language: result.language,
              confidence: result.confidence,
              processingTimeMs: result.processingTimeMs,
              model: result.model,
              transcribedAt: new Date().toISOString(),
            },
          },
        },
      });

      // Update media reference
      await prisma.mediaReference.update({
        where: { id: data.mediaReferenceId },
        data: {
          metadata: {
            transcription: {
              status: 'completed',
              confidence: result.confidence,
              language: result.language,
              wordCount: result.text.split(' ').length,
              processingTimeMs: result.processingTimeMs,
            },
          },
        },
      });

      // Create audit event
      await prisma.auditEvent.create({
        data: {
          eventType: 'MEDIA_TRANSCRIBED' as any, // Add to enum
          userId: data.userId,
          momentId: data.momentId,
          actor: 'system:whisper',
          action: 'TRANSCRIPTION_COMPLETED',
          resourceType: 'media_reference',
          resourceId: data.mediaReferenceId,
          details: {
            language: result.language,
            confidence: result.confidence,
            wordCount: result.text.split(' ').length,
            processingTimeMs: result.processingTimeMs,
          },
        },
      });
// Trigger vector embedding now that we have a transcript
      try {
        const moment = await prisma.moment.findUnique({ where: { id: data.momentId } });
        if (moment) {
          await VectorEmbeddingService.enqueueEmbedding({
            momentId: data.momentId,
            embeddingModelId: 'openai-3-small',
            contentType: 'combined',
            inputText: `${moment.contentText}\n\nTranscript: ${result.text}`,
            userId: data.userId,
            priority: 15, // Higher priority than initial
          });
        }
      } catch (embError) {
        console.error('Failed to trigger embedding after transcription:', embError);
      }

      
      await job.updateProgress(100);

      await prisma.$disconnect();

      return result;

    } catch (error: any) {
      console.error('Transcription job failed:', error);

      // Update database with failure
      try {
        const { PrismaClient } = await import('@prisma/client');
        const prisma = new PrismaClient();

        await prisma.mediaReference.update({
          where: { id: data.mediaReferenceId },
          data: {
            metadata: {
              transcription: {
                status: 'failed',
                error: error.message,
                failedAt: new Date().toISOString(),
                retryCount: (data.retryCount || 0) + 1,
              },
            },
          },
        });

        await prisma.$disconnect();
      } catch (dbError) {
        console.error('Failed to update database with transcription failure:', dbError);
      }

      throw error;
    }
  },
  {
    connection: redis,
    concurrency: 5, // Process up to 5 transcriptions concurrently
  }
);

// Worker event handlers
transcriptionWorker.on('completed', (job) => {
  console.log(`Transcription job ${job.id} completed successfully`);
});

transcriptionWorker.on('failed', (job, err) => {
  console.error(`Transcription job ${job?.id} failed:`, err);
});

transcriptionWorker.on('progress', (job, progress) => {
  console.log(`Transcription job ${job.id} progress: ${progress}%`);
});

// Graceful shutdown
process.on('SIGINT', async () => {
  console.log('Shutting down transcription worker...');
  await transcriptionWorker.close();
  await redis.quit();
  process.exit(0);
});

export { transcriptionWorker };
