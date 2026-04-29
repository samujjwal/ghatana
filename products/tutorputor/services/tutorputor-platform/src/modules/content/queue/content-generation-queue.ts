import { Queue } from "bullmq";

export const CONTENT_GENERATION_QUEUE = "content-generation";
const REDIS_URL = process.env.REDIS_URL || "redis://localhost:6379";

export type JobPriority = 'low' | 'normal' | 'high' | 'urgent';

export interface ContentGenerationJobOptions {
  priority?: JobPriority;
  delay?: number;
  attempts?: number;
  backoff?: {
    type: 'exponential' | 'fixed';
    delay: number;
  };
  jobId?: string;
  removeOnComplete?: number;
  removeOnFail?: number;
}

export type ContentGenerationQueueLike = {
  add: (
    name: string,
    data: object,
    opts?: ContentGenerationJobOptions,
  ) => Promise<{ id: string | number | null | undefined }>;
  addBulk: (
    jobs: Array<{ name: string; data: object; opts?: ContentGenerationJobOptions }>,
  ) => Promise<{ id: string | number | null | undefined }[]>;
};

let queueSingleton: ContentGenerationQueueLike | null = null;

export function queueConnectionFromUrl(redisUrl: string): {
  host: string;
  port: number;
  password?: string;
  db?: number;
} {
  const parsed = new URL(redisUrl);
  const dbPath = parsed.pathname?.replace("/", "");
  return {
    host: parsed.hostname,
    port: parseInt(parsed.port || "6379", 10),
    ...(parsed.password ? { password: parsed.password } : {}),
    db: dbPath ? parseInt(dbPath, 10) || 0 : 0,
  };
}

/**
 * Convert job priority to BullMQ priority number
 * Higher number = higher priority (BullMQ uses 1-10)
 */
export function priorityToNumber(priority: JobPriority): number {
  switch (priority) {
    case 'low':
      return 1;
    case 'normal':
      return 5;
    case 'high':
      return 8;
    case 'urgent':
      return 10;
    default:
      return 5;
  }
}

/**
 * Convert job options to BullMQ options
 */
export function toBullMQOptions(opts?: ContentGenerationJobOptions): Record<string, unknown> {
  if (!opts) {
    return {};
  }

  const bullMQOptions: Record<string, unknown> = {};

  if (opts.priority) {
    bullMQOptions.priority = priorityToNumber(opts.priority);
  }

  if (opts.delay) {
    bullMQOptions.delay = opts.delay;
  }

  if (opts.attempts) {
    bullMQOptions.attempts = opts.attempts;
  }

  if (opts.backoff) {
    bullMQOptions.backoff = opts.backoff;
  }

  if (opts.jobId) {
    bullMQOptions.jobId = opts.jobId;
  }

  if (typeof opts.removeOnComplete === 'number') {
    bullMQOptions.removeOnComplete = opts.removeOnComplete;
  }

  if (typeof opts.removeOnFail === 'number') {
    bullMQOptions.removeOnFail = opts.removeOnFail;
  }

  return bullMQOptions;
}

export function getContentGenerationQueue(): ContentGenerationQueueLike {
  if (!queueSingleton) {
    const disableQueue =
      process.env.CONTENT_QUEUE_DISABLED === "true" ||
      process.env.NODE_ENV === "test";

    if (disableQueue) {
      queueSingleton = {
        async add(_name, _data, opts) {
          const id =
            typeof opts?.["jobId"] === "string" ? opts["jobId"] : "noop";
          return { id };
        },
        async addBulk(jobs) {
          return jobs.map(job => {
            const id = typeof job.opts?.["jobId"] === "string" ? job.opts["jobId"] : "noop";
            return { id };
          });
        },
      };
    } else {
      const queue = new Queue<any, any, string>(CONTENT_GENERATION_QUEUE, {
        connection: queueConnectionFromUrl(REDIS_URL),
        defaultJobOptions: {
          removeOnComplete: {
            count: 1000,
            age: 3600, // 1 hour
          },
          removeOnFail: {
            count: 5000,
            age: 86400, // 24 hours
          },
          // Auto-retry failed jobs with exponential backoff
          attempts: 3,
          backoff: {
            type: 'exponential',
            delay: 2000, // Start with 2 seconds, then exponentially increase
          },
        },
      });

      queueSingleton = {
        add: async (
          name: string,
          data: object,
          opts?: ContentGenerationJobOptions,
        ) => {
          const bullMQOpts = toBullMQOptions(opts);
          const job = await queue.add(name, data, bullMQOpts);
          return { id: job.id };
        },
        addBulk: async (
          jobs: Array<{
            name: string;
            data: object;
            opts?: ContentGenerationJobOptions;
          }>,
        ) => {
          const bullMQJobs = jobs.map((job) => ({
            name: job.name,
            data: job.data,
            opts: toBullMQOptions(job.opts),
          }));
          const addedJobs = await queue.addBulk(bullMQJobs);
          return addedJobs.map((job) => ({ id: job.id }));
        },
      } as unknown as ContentGenerationQueueLike;
    }
  }

  return queueSingleton as ContentGenerationQueueLike;
}
