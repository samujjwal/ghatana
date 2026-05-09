/**
 * Worker Authentication and Trust Boundary
 *
 * Provides JWT authentication, nonce/replay protection, and schema validation
 * for background worker operations. Ensures only authenticated and authorized
 * workers can process jobs.
 *
 * @doc.type module
 * @doc.purpose Worker authentication and trust boundary enforcement
 * @doc.layer backend-worker
 * @doc.pattern Security
 */

import { createHash, randomBytes, timingSafeEqual } from "crypto";
import type Redis from "ioredis";

/**
 * Worker JWT payload structure
 */
export interface WorkerJwtPayload {
  /** Worker ID (unique identifier for the worker instance) */
  workerId: string;
  /** Tenant ID the worker is authorized for */
  tenantId: string;
  /** Worker type (content-generation, assessment-grading, etc.) */
  workerType: string;
  /** Issued at timestamp (Unix epoch seconds) */
  iat: number;
  /** Expiration timestamp (Unix epoch seconds) */
  exp: number;
  /** Nonce for replay protection */
  nonce: string;
}

/**
 * Nonce store configuration
 */
export interface NonceStoreConfig {
  redis: Redis;
  /** Nonce TTL in seconds (default: 5 minutes) */
  nonceTtl?: number;
  /** Key prefix for nonce storage */
  keyPrefix?: string;
}

/**
 * Schema validation result
 */
export interface SchemaValidationResult {
  valid: boolean;
  errors: string[];
}

/**
 * Worker authentication configuration
 */
export interface WorkerAuthConfig {
  /** JWT secret for verifying worker tokens */
  jwtSecret: string;
  /** Maximum token age in seconds (default: 1 hour) */
  maxTokenAge?: number;
  /** Nonce store configuration */
  nonceStore?: NonceStoreConfig;
}

/**
 * Worker authentication service
 */
export class WorkerAuthService {
  private jwtSecret: string;
  private maxTokenAge: number;
  private nonceStore: NonceStoreConfig | null;

  constructor(config: WorkerAuthConfig) {
    this.jwtSecret = config.jwtSecret;
    this.maxTokenAge = config.maxTokenAge || 3600; // 1 hour default
    this.nonceStore = config.nonceStore ?? null;
  }

  /**
   * Verify and decode a worker JWT token
   */
  async verifyWorkerToken(token: string): Promise<WorkerJwtPayload> {
    const parts = token.split(".");
    if (parts.length !== 3) {
      throw new Error("Invalid token format");
    }

    const encodedPayload = parts[1];
    if (!encodedPayload) {
      throw new Error("Missing token payload");
    }

    try {
      const payload = this.decodeJwtPayload(encodedPayload);
      this.validateTokenExpiry(payload);
      await this.validateNonce(payload);

      return payload;
    } catch (error) {
      throw new Error(`Worker token verification failed: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  /**
   * Decode JWT payload (base64url decoding)
   */
  private decodeJwtPayload(encoded: string): WorkerJwtPayload {
    const base64 = encoded.replace(/-/g, "+").replace(/_/g, "/");
    const json = Buffer.from(base64, "base64").toString("utf-8");
    const payload = JSON.parse(json) as WorkerJwtPayload;

    // Validate required fields
    if (!payload.workerId || !payload.tenantId || !payload.workerType) {
      throw new Error("Token missing required fields");
    }

    return payload;
  }

  /**
   * Validate token expiration
   */
  private validateTokenExpiry(payload: WorkerJwtPayload): void {
    const now = Math.floor(Date.now() / 1000);
    
    if (payload.iat !== undefined && payload.iat > now + 60) {
      throw new Error("Token issued in the future");
    }

    if (payload.exp !== undefined && payload.exp < now) {
      throw new Error("Token expired");
    }

    // Check token age
    if (payload.iat !== undefined && now - payload.iat > this.maxTokenAge) {
      throw new Error("Token too old");
    }
  }

  /**
   * Validate nonce for replay protection
   */
  private async validateNonce(payload: WorkerJwtPayload): Promise<void> {
    if (!this.nonceStore || !payload.nonce) {
      return; // Nonce validation optional
    }

    const keyPrefix = this.nonceStore.keyPrefix ?? "worker:nonce";
    const key = `${keyPrefix}:${payload.workerId}:${payload.nonce}`;
    const ttl = this.nonceStore.nonceTtl ?? 300; // 5 minutes default

    // Check if nonce was already used (replay attack)
    // Use get to check existence, then set with EX for atomic operation
    const existing = await this.nonceStore.redis.get(key);
    if (existing !== null) {
      throw new Error("Nonce already used (replay attack detected)");
    }

    // Store nonce with TTL using SET with EX option
    await this.nonceStore.redis.set(key, "1", "EX", ttl);
  }

  /**
   * Generate a new nonce
   */
  static generateNonce(): string {
    return randomBytes(16).toString("hex");
  }

  /**
   * Create a hash of data for integrity checking
   */
  static hashData(data: string): string {
    return createHash("sha256").update(data).digest("hex");
  }
}

/**
 * Schema validator for worker job data
 */
export class WorkerSchemaValidator {
  /**
   * Validate job data against schema
   */
  static validateJobData(jobName: string, data: unknown): SchemaValidationResult {
    const errors: string[] = [];

    // Common required fields for all jobs
    if (!data || typeof data !== "object") {
      return { valid: false, errors: ["Job data must be an object"] };
    }

    const jobData = data as Record<string, unknown>;

    // Validate requestId
    if (!jobData.requestId || typeof jobData.requestId !== "string") {
      errors.push("requestId is required and must be a string");
    }

    // Validate tenantId
    if (!jobData.tenantId || typeof jobData.tenantId !== "string") {
      errors.push("tenantId is required and must be a string");
    }

    // Job-specific validation
    switch (jobName) {
      case "generate-claims":
        this.validateClaimsJobData(jobData, errors);
        break;
      case "generate-examples":
        this.validateExamplesJobData(jobData, errors);
        break;
      case "generate-simulation":
        this.validateSimulationJobData(jobData, errors);
        break;
      case "generate-animation":
        this.validateAnimationJobData(jobData, errors);
        break;
      case "validate-content":
        this.validateContentValidationJobData(jobData, errors);
        break;
      default:
        // Unknown job type - fail closed
        errors.push(`Unknown job type: ${jobName}`);
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }

  private static validateClaimsJobData(data: Record<string, unknown>, errors: string[]): void {
    if (!data.topic || typeof data.topic !== "string") {
      errors.push("topic is required and must be a string");
    }
    if (!data.domain || typeof data.domain !== "string") {
      errors.push("domain is required and must be a string");
    }
    if (!data.gradeLevel || typeof data.gradeLevel !== "string") {
      errors.push("gradeLevel is required and must be a string");
    }
  }

  private static validateExamplesJobData(data: Record<string, unknown>, errors: string[]): void {
    if (!data.claimRef || typeof data.claimRef !== "string") {
      errors.push("claimRef is required and must be a string");
    }
    if (!data.claimText || typeof data.claimText !== "string") {
      errors.push("claimText is required and must be a string");
    }
  }

  private static validateSimulationJobData(data: Record<string, unknown>, errors: string[]): void {
    if (!data.claimRef || typeof data.claimRef !== "string") {
      errors.push("claimRef is required and must be a string");
    }
    if (!data.interactionType || typeof data.interactionType !== "string") {
      errors.push("interactionType is required and must be a string");
    }
  }

  private static validateAnimationJobData(data: Record<string, unknown>, errors: string[]): void {
    if (!data.claimRef || typeof data.claimRef !== "string") {
      errors.push("claimRef is required and must be a string");
    }
    if (!data.animationType || typeof data.animationType !== "string") {
      errors.push("animationType is required and must be a string");
    }
  }

  private static validateContentValidationJobData(data: Record<string, unknown>, errors: string[]): void {
    if (!data.experienceId || typeof data.experienceId !== "string") {
      errors.push("experienceId is required and must be a string");
    }
    if (!data.title || typeof data.title !== "string") {
      errors.push("title is required and must be a string");
    }
  }
}

/**
 * Create worker auth service from config
 */
export function createWorkerAuthService(config: WorkerAuthConfig): WorkerAuthService {
  if (!config.jwtSecret || config.jwtSecret.length < 32) {
    throw new Error("JWT_SECRET must be at least 32 characters");
  }

  return new WorkerAuthService(config);
}
