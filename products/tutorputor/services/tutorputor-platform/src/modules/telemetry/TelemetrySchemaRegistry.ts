/**
 * Telemetry Schema Registry
 *
 * Versioned registry for telemetry event schemas.
 * Ensures backward compatibility and schema validation for telemetry events.
 *
 * @doc.type class
 * @doc.purpose Versioned telemetry schema registry
 * @doc.layer platform
 * @doc.pattern Registry
 */

import { z } from "zod";
import { createStandaloneLogger } from "@tutorputor/core/logger";

const logger = createStandaloneLogger({ component: "TelemetrySchemaRegistry" });

// ============================================================================
// Schema Version Types
// ============================================================================

export type SchemaVersion = `${number}.${number}.${number}`;

export interface TelemetrySchema {
  eventName: string;
  version: SchemaVersion;
  schema: z.ZodSchema;
  description: string;
  deprecated: boolean;
  deprecatedAt?: Date;
  deprecationMessage?: string;
}

export interface RegisteredSchema {
  eventName: string;
  versions: Map<SchemaVersion, TelemetrySchema>;
  latestVersion: SchemaVersion;
}

// ============================================================================
// Telemetry Event Schemas
// ============================================================================

// sim.start - Simulation started
const simStartV1Schema = z.object({
  userId: z.string().min(1),
  tenantId: z.string().min(1),
  sessionId: z.string().min(1),
  simulationId: z.string().min(1),
  simulationType: z.enum(["physics", "chemistry", "biology", "mathematics", "computerScience"]),
  moduleContext: z.object({
    moduleId: z.string().min(1),
    domain: z.string().min(1),
    difficultyLevel: z.string().optional(),
  }),
  timestamp: z.string().datetime(),
  deviceInfo: z.object({
    userAgent: z.string().optional(),
    platform: z.enum(["web", "ios", "android"]).optional(),
    screenResolution: z.string().optional(),
  }).optional(),
});

// assess.answer - Assessment answer submitted
const assessAnswerV1Schema = z.object({
  userId: z.string().min(1),
  tenantId: z.string().min(1),
  sessionId: z.string().min(1),
  assessmentId: z.string().min(1),
  questionId: z.string().min(1),
  answer: z.union([z.string(), z.number(), z.array(z.any())]),
  isCorrect: z.boolean(),
  timeSpentMs: z.number().int().min(0),
  confidence: z.enum(["low", "medium", "high"]).optional(),
  timestamp: z.string().datetime(),
  moduleContext: z.object({
    moduleId: z.string().min(1),
    domain: z.string().min(1),
  }),
});

// ai.tutor.query - AI tutor query submitted
const aiTutorQueryV1Schema = z.object({
  userId: z.string().min(1),
  tenantId: z.string().min(1),
  sessionId: z.string().min(1),
  query: z.string().min(1),
  context: z.object({
    moduleId: z.string().optional(),
    conceptId: z.string().optional(),
    domain: z.string().optional(),
  }),
  response: z.object({
    answer: z.string(),
    confidence: z.number().min(0).max(1),
    sources: z.array(z.object({
      id: z.string(),
      title: z.string(),
      relevance: z.number().min(0).max(1),
    })).optional(),
  }),
  latencyMs: z.number().int().min(0),
  timestamp: z.string().datetime(),
});

// learner.progress - Learning progress update
const learnerProgressV1Schema = z.object({
  userId: z.string().min(1),
  tenantId: z.string().min(1),
  moduleId: z.string().min(1),
  progress: z.number().min(0).max(1),
  completedUnits: z.array(z.string()),
  currentUnit: z.string().optional(),
  mastery: z.record(z.string(), z.number().min(0).max(1)).optional(),
  timestamp: z.string().datetime(),
});

// content.generated - Content generation event
const contentGeneratedV1Schema = z.object({
  tenantId: z.string().min(1),
  requestId: z.string().min(1),
  contentType: z.enum(["example", "simulation", "animation", "assessment", "explanation"]),
  domain: z.string().min(1),
  gradeRange: z.string().optional(),
  status: z.enum(["pending", "in_progress", "completed", "failed"]),
  aiProvider: z.enum(["openai", "claude", "ollama", "gemini"]).optional(),
  latencyMs: z.number().int().min(0).optional(),
  tokenCount: z.object({
    input: z.number().int().min(0),
    output: z.number().int().min(0),
  }).optional(),
  timestamp: z.string().datetime(),
});

// ============================================================================
// Telemetry Schema Registry
// ============================================================================

export class TelemetrySchemaRegistry {
  private static instance: TelemetrySchemaRegistry;
  private schemas: Map<string, RegisteredSchema>;

  private constructor() {
    this.schemas = new Map();
    this.initializeSchemas();
  }

  static getInstance(): TelemetrySchemaRegistry {
    if (!TelemetrySchemaRegistry.instance) {
      TelemetrySchemaRegistry.instance = new TelemetrySchemaRegistry();
    }
    return TelemetrySchemaRegistry.instance;
  }

  private initializeSchemas(): void {
    // Register sim.start
    this.register({
      eventName: "sim.start",
      version: "1.0.0",
      schema: simStartV1Schema,
      description: "Simulation started event",
      deprecated: false,
    });

    // Register assess.answer
    this.register({
      eventName: "assess.answer",
      version: "1.0.0",
      schema: assessAnswerV1Schema,
      description: "Assessment answer submitted event",
      deprecated: false,
    });

    // Register ai.tutor.query
    this.register({
      eventName: "ai.tutor.query",
      version: "1.0.0",
      schema: aiTutorQueryV1Schema,
      description: "AI tutor query event",
      deprecated: false,
    });

    // Register learner.progress
    this.register({
      eventName: "learner.progress",
      version: "1.0.0",
      schema: learnerProgressV1Schema,
      description: "Learning progress update event",
      deprecated: false,
    });

    // Register content.generated
    this.register({
      eventName: "content.generated",
      version: "1.0.0",
      schema: contentGeneratedV1Schema,
      description: "Content generation event",
      deprecated: false,
    });

    logger.info({
      message: "Telemetry schema registry initialized",
      eventCount: this.schemas.size,
    });
  }

  /**
   * Register a telemetry schema
   */
  register(schema: TelemetrySchema): void {
    const { eventName, version } = schema;

    if (!this.schemas.has(eventName)) {
      this.schemas.set(eventName, {
        eventName,
        versions: new Map(),
        latestVersion: version,
      });
    }

    const registered = this.schemas.get(eventName)!;
    registered.versions.set(version, schema);

    // Update latest version if this is newer
    if (this.compareVersions(version, registered.latestVersion) > 0) {
      registered.latestVersion = version;
    }

    logger.info({
      message: "Telemetry schema registered",
      eventName,
      version,
      isLatest: version === registered.latestVersion,
    });
  }

  /**
   * Get a specific schema version
   */
  getSchema(eventName: string, version?: SchemaVersion): TelemetrySchema | undefined {
    const registered = this.schemas.get(eventName);
    if (!registered) {
      return undefined;
    }

    if (version) {
      return registered.versions.get(version);
    }

    // Return latest version if no version specified
    return registered.versions.get(registered.latestVersion);
  }

  /**
   * Get the latest schema for an event
   */
  getLatestSchema(eventName: string): TelemetrySchema | undefined {
    const registered = this.schemas.get(eventName);
    if (!registered) {
      return undefined;
    }
    return registered.versions.get(registered.latestVersion);
  }

  /**
   * Validate telemetry data against schema
   */
  validate(eventName: string, data: unknown, version?: SchemaVersion): {
    valid: boolean;
    errors?: z.ZodError;
    schema?: TelemetrySchema;
  } {
    const schema = this.getSchema(eventName, version);
    if (!schema) {
      return {
        valid: false,
        errors: new z.ZodError([
          {
            code: z.ZodIssueCode.custom,
            message: `Schema not found for event: ${eventName}${version ? ` version: ${version}` : ""}`,
            path: [],
          },
        ]),
      };
    }

    const result = schema.schema.safeParse(data);
    if (result.success) {
      return { valid: true, schema };
    }

    return {
      valid: false,
      errors: result.error,
      schema,
    };
  }

  /**
   * Get all registered event names
   */
  getEventNames(): string[] {
    return Array.from(this.schemas.keys());
  }

  /**
   * Get all versions for an event
   */
  getVersions(eventName: string): SchemaVersion[] {
    const registered = this.schemas.get(eventName);
    if (!registered) {
      return [];
    }
    return Array.from(registered.versions.keys()).sort((a, b) =>
      this.compareVersions(b, a)
    );
  }

  /**
   * Check if a schema is deprecated
   */
  isDeprecated(eventName: string, version?: SchemaVersion): boolean {
    const schema = this.getSchema(eventName, version);
    if (!schema) {
      return false;
    }
    return schema.deprecated;
  }

  /**
   * Deprecate a schema version
   */
  deprecate(eventName: string, version: SchemaVersion, message?: string): boolean {
    const registered = this.schemas.get(eventName);
    if (!registered) {
      return false;
    }

    const schema = registered.versions.get(version);
    if (!schema) {
      return false;
    }

    schema.deprecated = true;
    schema.deprecatedAt = new Date();
    schema.deprecationMessage = message;

    logger.warn({
      message: "Telemetry schema deprecated",
      eventName,
      version,
      deprecationMessage: message,
    });

    return true;
  }

  /**
   * Compare version strings (returns positive if a > b, negative if a < b, 0 if equal)
   */
  private compareVersions(a: SchemaVersion, b: SchemaVersion): number {
    const partsA = a.split(".").map(Number);
    const partsB = b.split(".").map(Number);

    for (let i = 0; i < 3; i++) {
      if (partsA[i] !== partsB[i]) {
        return partsA[i] - partsB[i];
      }
    }

    return 0;
  }

  /**
   * Get registry statistics
   */
  getStats(): {
    totalEvents: number;
    totalVersions: number;
    deprecatedVersions: number;
    events: Array<{
      eventName: string;
      versions: number;
      latestVersion: SchemaVersion;
      deprecatedVersions: number;
    }>;
  } {
    let totalVersions = 0;
    let deprecatedVersions = 0;
    const events: Array<{
      eventName: string;
      versions: number;
      latestVersion: SchemaVersion;
      deprecatedVersions: number;
    }> = [];

    for (const [eventName, registered] of this.schemas.entries()) {
      const versionCount = registered.versions.size;
      totalVersions += versionCount;

      let deprecatedCount = 0;
      for (const schema of registered.versions.values()) {
        if (schema.deprecated) {
          deprecatedCount++;
          deprecatedVersions++;
        }
      }

      events.push({
        eventName,
        versions: versionCount,
        latestVersion: registered.latestVersion,
        deprecatedVersions: deprecatedCount,
      });
    }

    return {
      totalEvents: this.schemas.size,
      totalVersions,
      deprecatedVersions,
      events,
    };
  }
}

// Singleton instance
export function getTelemetrySchemaRegistry(): TelemetrySchemaRegistry {
  return TelemetrySchemaRegistry.getInstance();
}
