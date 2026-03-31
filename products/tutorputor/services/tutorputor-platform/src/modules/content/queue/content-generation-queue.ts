import { Queue } from "bullmq";

export const CONTENT_GENERATION_QUEUE = "content-generation";
const REDIS_URL = process.env.REDIS_URL || "redis://localhost:6379";

export type ContentGenerationQueueLike = {
  add: (
    name: string,
    data: object,
    opts?: Record<string, unknown>,
  ) => Promise<{ id: string | number | null | undefined }>;
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
      };
    } else {
      queueSingleton = new Queue<any, any, string>(CONTENT_GENERATION_QUEUE, {
        connection: queueConnectionFromUrl(REDIS_URL),
      }) as unknown as ContentGenerationQueueLike;
    }
  }

  return queueSingleton as ContentGenerationQueueLike;
}
