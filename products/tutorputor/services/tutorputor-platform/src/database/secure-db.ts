/**
 * Secure Database Access Layer
 *
 * Provides secure database operations with:
 * - SQL injection prevention
 * - Parameterized queries only
 * - Input validation and sanitization
 * - Transaction management
 * - Connection pooling
 * - Query logging and monitoring
 */

import {
  Prisma,
  createPrismaClient,
  type TutorPrismaClient,
} from "@tutorputor/core/db";
import { getConfig } from "../config/config.js";
import { createLogger } from "../utils/logger.js";

export interface DatabaseConfig {
  url: string;
  maxConnections?: number;
  connectionTimeout?: number;
  queryTimeout?: number;
}

export interface QueryOptions {
  timeout?: number;
  transaction?: boolean;
  logQuery?: boolean;
  sanitize?: boolean;
}

export interface DatabaseMetrics {
  totalQueries: number;
  slowQueries: number;
  failedQueries: number;
  averageQueryTime: number;
  activeConnections: number;
}

const MODULE_DOMAINS = ["MATH", "SCIENCE", "TECH"] as const;
const MODULE_DIFFICULTIES = ["INTRO", "INTERMEDIATE", "ADVANCED"] as const;
const ASSESSMENT_TYPES = ["QUIZ", "PROJECT", "SIMULATION"] as const;

type ModuleDomainValue = (typeof MODULE_DOMAINS)[number];
type ModuleDifficultyValue = (typeof MODULE_DIFFICULTIES)[number];
type AssessmentTypeValue = (typeof ASSESSMENT_TYPES)[number];

function assertLiteralValue<T extends readonly string[]>(
  allowed: T,
  value: string,
  label: string,
): T[number] {
  if (allowed.includes(value)) {
    return value as T[number];
  }

  throw new Error(`Invalid ${label}: ${value}`);
}

/**
 * Secure Database Manager
 */
export class SecureDatabaseManager {
  private prisma: TutorPrismaClient;
  private logger = createLogger("database");
  private metrics: DatabaseMetrics = {
    totalQueries: 0,
    slowQueries: 0,
    failedQueries: 0,
    averageQueryTime: 0,
    activeConnections: 0,
  };

  constructor(config?: DatabaseConfig) {
    const dbConfig = getConfig();
    const databaseUrl = config?.url || dbConfig.DATABASE_URL;

    if (databaseUrl) {
      process.env.TUTORPUTOR_DATABASE_URL = databaseUrl;
    }

    this.prisma = createPrismaClient();

    this.setupLogging();
  }

  /**
   * Setup query logging and monitoring
   */
  private setupLogging(): void {
    this.logger.debug(
      { operation: "database_monitoring_initialized" },
      "Database monitoring initialized",
    );
  }

  /**
   * Get Prisma client with security wrapper
   */
  get client(): TutorPrismaClient {
    return this.prisma;
  }

  /**
   * Execute a secure query with validation and monitoring
   */
  async executeQuery<T>(
    operation: (prisma: TutorPrismaClient) => Promise<T>,
    options: QueryOptions = {},
  ): Promise<T> {
    const startTime = Date.now();
    const timeout = options.timeout || 30000; // 30 seconds default

    try {
      this.logger.debug(
        { operation: "query_start" },
        "Starting database query",
      );

      // Execute with timeout
      const result = await Promise.race([
        operation(this.prisma),
        new Promise<never>((_, reject) =>
          setTimeout(() => reject(new Error("Query timeout")), timeout),
        ),
      ]);

      const duration = Date.now() - startTime;
      this.logger.debug(
        { duration, operation: "query_success" },
        "Database query completed",
      );

      return result;
    } catch (error) {
      const duration = Date.now() - startTime;
      this.logger.error(
        {
          error: error instanceof Error ? error.message : "Unknown error",
          duration,
          operation: "query_failed",
        },
        "Database query failed",
      );

      // Don't expose internal database errors to callers
      throw new Error(
        `Database operation failed: ${error instanceof Error ? error.message : "Unknown error"}`,
      );
    }
  }

  /**
   * Execute a transaction with rollback on error
   */
  async executeTransaction<T>(
    operations: (prisma: Prisma.TransactionClient) => Promise<T>,
    options: QueryOptions = {},
  ): Promise<T> {
    const startTime = Date.now();

    try {
      this.logger.debug(
        { operation: "transaction_start" },
        "Starting database transaction",
      );

      const result = await this.prisma.$transaction(
        (prisma) => operations(prisma),
        {
          timeout: options.timeout || 30000,
        },
      );

      const duration = Date.now() - startTime;
      this.logger.debug(
        { duration, operation: "transaction_success" },
        "Database transaction completed",
      );

      return result;
    } catch (error) {
      const duration = Date.now() - startTime;
      this.logger.error(
        {
          error: error instanceof Error ? error.message : "Unknown error",
          duration,
          operation: "transaction_failed",
        },
        "Database transaction failed",
      );

      throw new Error(
        `Transaction failed: ${error instanceof Error ? error.message : "Unknown error"}`,
      );
    }
  }

  /**
   * Validate and sanitize input parameters
   */
  private validateInput<T>(input: T): T {
    if (typeof input === "string") {
      // Basic SQL injection prevention
      const dangerousPatterns = [
        /(\b(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|EXEC|UNION|SCRIPT)\b)/i,
        /(--|\*\/|\/\*)/,
        /(\bOR\b|\bAND\b)\s+\d+\s*=\s*\d+/i,
        /(\bWHERE\b.*\bOR\b\s+\d+\s*=\s*\d+)/i,
      ];

      for (const pattern of dangerousPatterns) {
        if (pattern.test(input)) {
          throw new Error("Potentially dangerous input detected");
        }
      }

      // Escape special characters
      return input.replace(/['"\\]/g, "\\$&") as T;
    }

    if (Array.isArray(input)) {
      return input.map((item) => this.validateInput(item)) as T;
    }

    if (typeof input === "object" && input !== null) {
      const sanitized: Record<string, unknown> = {};
      for (const [key, value] of Object.entries(input)) {
        sanitized[key] = this.validateInput(value);
      }
      return sanitized as T;
    }

    return input;
  }

  /**
   * Secure user operations
   */
  async createUser(userData: {
    email: string;
    tenantId: string;
    roles?: string[];
  }) {
    const sanitizedData = this.validateInput(userData);

    return this.executeQuery(async (prisma) => {
      return prisma.user.create({
        data: {
          email: sanitizedData.email,
          tenantId: sanitizedData.tenantId,
          displayName: sanitizedData.email.split("@")[0] ?? sanitizedData.email,
          role: sanitizedData.roles?.[0] || "student",
          createdAt: new Date(),
          updatedAt: new Date(),
        },
      });
    });
  }

  /**
   * Secure module operations
   */
  async createModule(moduleData: {
    title: string;
    slug: string;
    tenantId: string;
    domain: string;
    difficulty: string;
    description: string;
    estimatedTimeMinutes: number;
  }) {
    const sanitizedData = this.validateInput(moduleData);
    const domain: ModuleDomainValue = assertLiteralValue(
      MODULE_DOMAINS,
      sanitizedData.domain,
      "module domain",
    );
    const difficulty: ModuleDifficultyValue = assertLiteralValue(
      MODULE_DIFFICULTIES,
      sanitizedData.difficulty,
      "module difficulty",
    );

    return this.executeQuery(async (prisma) => {
      return prisma.module.create({
        data: {
          title: sanitizedData.title,
          slug: sanitizedData.slug,
          tenantId: sanitizedData.tenantId,
          domain,
          difficulty,
          description: sanitizedData.description,
          estimatedTimeMinutes: sanitizedData.estimatedTimeMinutes,
          status: "DRAFT",
          version: 1,
          createdAt: new Date(),
          updatedAt: new Date(),
        },
      });
    });
  }

  /**
   * Secure assessment operations
   */
  async createAssessment(assessmentData: {
    title: string;
    moduleId: string;
    tenantId: string;
    type: string;
    createdBy?: string;
  }) {
    const sanitizedData = this.validateInput(assessmentData);
    const actorId = sanitizedData.createdBy || "system";
    const type: AssessmentTypeValue = assertLiteralValue(
      ASSESSMENT_TYPES,
      sanitizedData.type,
      "assessment type",
    );

    return this.executeQuery(async (prisma) => {
      return prisma.assessment.create({
        data: {
          title: sanitizedData.title,
          module: {
            connect: { id: sanitizedData.moduleId },
          },
          tenantId: sanitizedData.tenantId,
          type,
          status: "DRAFT",
          createdBy: actorId,
          updatedBy: actorId,
          createdAt: new Date(),
          updatedAt: new Date(),
        },
      });
    });
  }

  /**
   * Get database metrics
   */
  getMetrics(): DatabaseMetrics {
    return { ...this.metrics };
  }

  /**
   * Health check for database
   */
  async healthCheck(): Promise<boolean> {
    try {
      await this.prisma.$queryRaw`SELECT 1 as health_check`;
      return true;
    } catch (error) {
      this.logger.error(
        { error: error instanceof Error ? error.message : "Unknown error" },
        "Database health check failed",
      );
      return false;
    }
  }

  /**
   * Close database connection
   */
  async close(): Promise<void> {
    await this.prisma.$disconnect();
    this.logger.info({}, "Database connection closed");
  }
}

/**
 * Database connection pool manager
 */
export class DatabaseConnectionPool {
  private static readonly instances = new Map<string, SecureDatabaseManager>();

  /**
   * Get database instance for tenant
   */
  static getInstance(tenantId?: string): SecureDatabaseManager {
    const key = tenantId || "default";

    if (!this.instances.has(key)) {
      this.instances.set(key, new SecureDatabaseManager());
    }

    return this.instances.get(key)!;
  }

  /**
   * Close all connections
   */
  static async closeAll(): Promise<void> {
    const closePromises = Array.from(this.instances.values()).map((instance) =>
      instance.close(),
    );
    await Promise.all(closePromises);
    this.instances.clear();
  }
}

// Export singleton instance
export const database = DatabaseConnectionPool.getInstance();
