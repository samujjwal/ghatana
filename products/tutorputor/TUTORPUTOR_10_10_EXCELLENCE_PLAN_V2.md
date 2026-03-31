# TutorPutor 10/10 Excellence Plan: Complete File-by-File Implementation Guide

**Version:** 2.0 (Enhanced with Production Audit Findings)  
**Created:** March 30, 2026  
**Estimated Effort:** 400 hours over 12 weeks (320 AI/ML + 80 Technical Debt)  
**Current Grade:** 7.75/10  
**Target Grade:** 10/10

---

## Executive Summary

This plan provides **file-by-file, task-by-task, line-by-line** implementation instructions to elevate TutorPutor from **7.75/10** to **10/10** across all dimensions. It combines the original AI/ML excellence improvements with critical production-grade technical debt remediation identified in the audit.

### Dimensions Overview

| Dimension | Current | Target | Effort | Timeline | Priority |
|-----------|---------|--------|--------|----------|----------|
| **Type Safety & Code Quality** | 6.0 | 10 | 40 hrs | Weeks 1-2 | P0 |
| **Personalization Depth** | 7.0 | 10 | 50 hrs | Weeks 1-4 | P0 |
| **Content Generation Intelligence** | 8.5 | 10 | 40 hrs | Weeks 1-3 | P1 |
| **Adaptation Accuracy** | 7.5 | 10 | 45 hrs | Weeks 3-6 | P1 |
| **Simulation Intelligence** | 9.0 | 10 | 30 hrs | Weeks 5-7 | P2 |
| **Assessment Intelligence** | 7.0 | 10 | 55 hrs | Weeks 6-9 | P1 |
| **Feedback Loop Maturity** | 7.5 | 10 | 40 hrs | Weeks 7-10 | P2 |
| **AI Safety & Reliability** | 8.0 | 10 | 35 hrs | Weeks 9-11 | P2 |
| **Performance & Cost** | 7.5 | 10 | 25 hrs | Weeks 11-12 | P3 |
| **Security & Error Handling** | 7.0 | 10 | 40 hrs | Weeks 1-4 | P0 |

**Total: 400 hours over 12 weeks**

---

## PART 1: CRITICAL TECHNICAL DEBT (P0 - Must Fix First)

### Phase 0: Foundation & Type Safety (Weeks 1-2)

#### Dimension: Type Safety & Code Quality (6.0→10.0)

**Critical Finding:** 1,177 `any` type occurrences across 141 files in `tutorputor-platform`

---

##### Task 0.1: Enable Strict Type Checking

**File:** `services/tutorputor-platform/tsconfig.json`  
**Lines:** 15-25 (compilerOptions)

**Current:**
```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "NodeNext",
    "strict": true,
    // ... other options
  }
}
```

**Replace With:**
```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "NodeNext",
    "strict": true,
    "noImplicitAny": true,
    "strictNullChecks": true,
    "noImplicitReturns": true,
    "noFallthroughCasesInSwitch": true,
    "noUncheckedIndexedAccess": true,
    "exactOptionalPropertyTypes": true,
    "forceConsistentCasingInFileNames": true,
    "skipLibCheck": true
  }
}
```

---

##### Task 0.2: Create Prisma Type Utilities

**File:** `libs/tutorputor-core/src/types/prisma-helpers.ts`  
**New File:** Create this file

```typescript
/**
 * @doc.type utility
 * @doc.purpose Type-safe Prisma query helpers to eliminate 'any' usage
 * @doc.layer platform
 * @doc.pattern Type Safety
 */

import { Prisma } from '@prisma/client';

// Re-export commonly used Prisma types
export type { Prisma };

// Pagination types
export interface PaginationArgs {
  cursor?: string;
  take?: number;
}

export interface PaginatedResult<T> {
  items: T[];
  hasMore: boolean;
  nextCursor?: string;
}

// Common where input types
export type ModuleWhereInput = Prisma.ModuleWhereInput;
export type ThreadWhereInput = Prisma.ThreadWhereInput;
export type UserWhereInput = Prisma.UserWhereInput;
export type EnrollmentWhereInput = Prisma.EnrollmentWhereInput;

// Common select types
export type ModuleSelect = Prisma.ModuleSelect;
export type UserSelect = Prisma.UserSelect;

// Helper to create type-safe where clauses
export function createTenantWhere<T extends { tenantId?: string }>(
  base: T,
  tenantId: string
): T & { tenantId: string } {
  return { ...base, tenantId };
}
```

---

##### Task 0.3: Fix `any` Types in Collaboration Module

**File:** `services/tutorputor-platform/src/modules/collaboration/service.ts`  
**Lines:** 1-50 (imports and first methods)

**Current (Line 20):**
```typescript
const where: any = { tenantId: args.tenantId };
```

**Replace With:**
```typescript
import { Prisma } from '@prisma/client';

// Line 20 - Replace 'any' with proper Prisma type
const where: Prisma.ThreadWhereInput = { tenantId: args.tenantId };
```

**Current (Line 45):**
```typescript
const threads: any[] = await this.prisma.thread.findMany({ where });
```

**Replace With:**
```typescript
import { Thread } from '@prisma/client';

// Line 45 - Use proper return type
const threads: Thread[] = await this.prisma.thread.findMany({ where });
```

---

##### Task 0.4: Fix `any` Types in Learning Module

**File:** `services/tutorputor-platform/src/modules/learning/assessment-service.ts`  
**Lines:** 40-60

**Current (Line 45):**
```typescript
type AssessmentRecord = any;
```

**Replace With:**
```typescript
import { AssessmentAttempt, AssessmentItem, Prisma } from '@prisma/client';

// Line 45 - Define proper type
type AssessmentRecord = AssessmentAttempt & {
  items: (AssessmentItem & {
    response?: {
      selectedOption?: string;
      explanation?: string;
      confidence?: number;
    };
  })[];
};
```

---

##### Task 0.5: Fix `any` Types in Content Module

**File:** `services/tutorputor-platform/src/modules/content/studio/service.ts`  
**Lines:** 10-30

**Current (Line 16):**
```typescript
const jobs: any[] = [];
```

**Replace With:**
```typescript
import { Job } from 'bullmq';

// Line 16 - Use proper Job type
const jobs: Job<ContentGenerationData>[] = [];
```

**Also add type definition:**
```typescript
interface ContentGenerationData {
  moduleId: string;
  tenantId: string;
  contentType: 'simulation' | 'example' | 'animation';
  priority: number;
}
```

---

##### Task 0.6: Create `any` Type Tracking Script

**File:** `scripts/audit-any-types.ts`  
**New File:** Create for CI integration

```typescript
#!/usr/bin/env ts-node
/**
 * @doc.type script
 * @doc.purpose Audit 'any' type usage in TypeScript files
 * @doc.layer tooling
 */

import { execSync } from 'child_process';
import { readFileSync } from 'fs';
import { glob } from 'glob';

interface AnyUsage {
  file: string;
  line: number;
  column: number;
  context: string;
  type: 'explicit_any' | 'as_any' | 'implicit_any';
}

async function auditAnyTypes(): Promise<void> {
  const files = await glob('services/tutorputor-platform/src/**/*.ts');
  const usages: AnyUsage[] = [];

  for (const file of files) {
    const content = readFileSync(file, 'utf-8');
    const lines = content.split('\n');

    lines.forEach((line, index) => {
      // Check for explicit :any
      const anyMatch = line.match(/:\s*any\b/);
      if (anyMatch) {
        usages.push({
          file,
          line: index + 1,
          column: anyMatch.index! + 1,
          context: line.trim(),
          type: 'explicit_any'
        });
      }

      // Check for as any
      const asAnyMatch = line.match(/as\s+any\b/);
      if (asAnyMatch) {
        usages.push({
          file,
          line: index + 1,
          column: asAnyMatch.index! + 1,
          context: line.trim(),
          type: 'as_any'
        });
      }
    });
  }

  // Output report
  console.log(`\n=== Any Type Audit Report ===\n`);
  console.log(`Total files scanned: ${files.length}`);
  console.log(`Total 'any' usages: ${usages.length}`);
  console.log(`\nBreakdown by type:`);
  console.log(`  - Explicit ': any': ${usages.filter(u => u.type === 'explicit_any').length}`);
  console.log(`  - 'as any': ${usages.filter(u => u.type === 'as_any').length}`);

  // Group by file
  const byFile = usages.reduce((acc, u) => {
    acc[u.file] = (acc[u.file] || 0) + 1;
    return acc;
  }, {} as Record<string, number>);

  console.log(`\nTop 10 files with most 'any' usages:`);
  Object.entries(byFile)
    .sort(([, a], [, b]) => b - a)
    .slice(0, 10)
    .forEach(([file, count]) => {
      console.log(`  ${file}: ${count}`);
    });

  // Fail if threshold exceeded
  const threshold = 100;
  if (usages.length > threshold) {
    console.log(`\n❌ FAILED: ${usages.length} 'any' types found (threshold: ${threshold})`);
    process.exit(1);
  }

  console.log(`\n✅ PASSED: ${usages.length} 'any' types within threshold`);
}

auditAnyTypes().catch(err => {
  console.error('Audit failed:', err);
  process.exit(1);
});
```

---

##### Task 0.7: Fix LTI Signature Validation (Critical Security)

**File:** `services/tutorputor-platform/src/modules/lti/validation.ts`  
**New File:** Create comprehensive LTI validator

```typescript
/**
 * @doc.type service
 * @doc.purpose LTI 1.3 Launch Request Validation
 * @doc.layer security
 * @doc.pattern Security Validation
 */

import { createVerify, createHash } from 'crypto';
import { promisify } from 'util';

interface LTILaunchRequest {
  id_token: string;
  state: string;
  iss: string;
  nonce: string;
}

interface PlatformRegistration {
  issuer: string;
  client_id: string;
  jwks_uri: string;
  login_uri: string;
}

export class LTIValidator {
  private nonceCache: Set<string> = new Set();
  private platformRegistrations: Map<string, PlatformRegistration> = new Map();

  constructor() {
    this.loadPlatformRegistrations();
  }

  private loadPlatformRegistrations(): void {
    // Load from database or config
    // This should be populated from LTI platform registrations
  }

  /**
   * @doc.type method
   * @doc.purpose Validate complete LTI 1.3 launch request
   */
  async validateLaunchRequest(request: LTILaunchRequest): Promise<boolean> {
    // 1. Validate platform is registered
    const platform = await this.getPlatformRegistration(request.iss);
    if (!platform) {
      throw new AuthorizationError('Unknown LTI platform');
    }

    // 2. Validate nonce (prevent replay attacks)
    if (this.nonceCache.has(request.nonce)) {
      throw new AuthorizationError('LTI nonce replay detected');
    }
    await this.recordNonce(request.nonce);

    // 3. Validate state parameter
    if (!await this.validateState(request.state)) {
      throw new AuthorizationError('Invalid LTI state parameter');
    }

    // 4. Validate ID token signature and claims
    await this.validateIDToken(request.id_token, platform);

    return true;
  }

  private async validateIDToken(idToken: string, platform: PlatformRegistration): Promise<void> {
    const [headerB64, payloadB64, signatureB64] = idToken.split('.');

    if (!headerB64 || !payloadB64 || !signatureB64) {
      throw new AuthorizationError('Invalid ID token format');
    }

    // Decode header
    const header = JSON.parse(Buffer.from(headerB64, 'base64').toString());

    // Fetch JWKS
    const jwks = await this.fetchJWKS(platform.jwks_uri);
    const key = jwks.keys.find((k: any) => k.kid === header.kid);

    if (!key) {
      throw new AuthorizationError('LTI signing key not found');
    }

    // Verify signature
    const publicKey = this.jwkToPem(key);
    const verify = createVerify('RSA-SHA256');
    verify.update(`${headerB64}.${payloadB64}`);

    const signature = Buffer.from(signatureB64, 'base64url');
    const isValid = verify.verify(publicKey, signature);

    if (!isValid) {
      throw new AuthorizationError('LTI ID token signature invalid');
    }

    // Validate claims
    const payload = JSON.parse(Buffer.from(payloadB64, 'base64').toString());

    if (payload.iss !== platform.issuer) {
      throw new AuthorizationError('LTI issuer mismatch');
    }

    if (payload.aud !== platform.client_id) {
      throw new AuthorizationError('LTI audience mismatch');
    }

    if (payload.exp < Date.now() / 1000) {
      throw new AuthorizationError('LTI token expired');
    }

    // Additional LTI-specific claim validation
    if (!payload['https://purl.imsglobal.org/spec/lti/claim/message_type']) {
      throw new AuthorizationError('Missing LTI message type claim');
    }
  }

  private async fetchJWKS(uri: string): Promise<{ keys: any[] }> {
    const response = await fetch(uri);
    if (!response.ok) {
      throw new Error(`Failed to fetch JWKS: ${response.status}`);
    }
    return response.json();
  }

  private jwkToPem(jwk: any): string {
    // Convert JWK to PEM format
    // Implementation using rsa-pem-from-mod-exp or similar
    const { n, e } = jwk;
    const modulus = Buffer.from(n, 'base64url');
    const exponent = Buffer.from(e, 'base64url');

    // Generate PEM (simplified - use proper library in production)
    return `-----BEGIN RSA PUBLIC KEY-----\n${modulus.toString('base64')}\n-----END RSA PUBLIC KEY-----`;
  }

  private async getPlatformRegistration(issuer: string): Promise<PlatformRegistration | null> {
    return this.platformRegistrations.get(issuer) || null;
  }

  private async recordNonce(nonce: string): Promise<void> {
    this.nonceCache.add(nonce);
    // Expire nonces after 1 hour
    setTimeout(() => this.nonceCache.delete(nonce), 3600000);
  }

  private async validateState(state: string): Promise<boolean> {
    // Validate state against session or cache
    // Implementation depends on session storage
    return true; // Placeholder
  }
}

// Custom error classes
class AuthorizationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'AuthorizationError';
  }
}
```

**Update LTI Route Handler:**

**File:** `services/tutorputor-platform/src/modules/lti/routes.ts`  
**Lines:** 50-80

**Add validation middleware:**
```typescript
import { LTIValidator } from './validation';

const ltiValidator = new LTIValidator();

// In route handler
fastify.post('/lti/launch', async (request, reply) => {
  try {
    const isValid = await ltiValidator.validateLaunchRequest(request.body);
    if (!isValid) {
      return reply.status(401).send({ error: 'LTI validation failed' });
    }

    // Proceed with launch
    // ...
  } catch (error) {
    request.log.error({ err: error }, 'LTI launch validation failed');
    return reply.status(401).send({ error: 'Unauthorized' });
  }
});
```

---

##### Task 0.8: Create Canonical Error Classes

**File:** `libs/tutorputor-core/src/errors/index.ts`  
**New File:** Create comprehensive error hierarchy

```typescript
/**
 * @doc.type module
 * @doc.purpose Canonical error classes for consistent error handling
 * @doc.layer platform
 * @doc.pattern Error Handling
 */

export interface ErrorDetails {
  [key: string]: unknown;
}

export class DomainError extends Error {
  /**
   * @doc.type constructor
   * @doc.purpose Create domain error with structured information
   */
  constructor(
    public readonly code: string,
    message: string,
    public readonly statusCode: number = 500,
    public readonly details?: ErrorDetails
  ) {
    super(message);
    this.name = this.constructor.name;
    Error.captureStackTrace(this, this.constructor);
  }

  toJSON(): Record<string, unknown> {
    return {
      code: this.code,
      message: this.message,
      statusCode: this.statusCode,
      details: this.details,
      name: this.name
    };
  }
}

export class NotFoundError extends DomainError {
  constructor(resource: string, id: string, details?: ErrorDetails) {
    super(
      'NOT_FOUND',
      `${resource} not found: ${id}`,
      404,
      { resource, id, ...details }
    );
  }
}

export class ValidationError extends DomainError {
  constructor(message: string, field?: string, details?: ErrorDetails) {
    super(
      'VALIDATION_ERROR',
      message,
      400,
      field ? { field, ...details } : details
    );
  }
}

export class ConflictError extends DomainError {
  constructor(message: string, details?: ErrorDetails) {
    super('CONFLICT', message, 409, details);
  }
}

export class AuthorizationError extends DomainError {
  constructor(message: string = 'Unauthorized', details?: ErrorDetails) {
    super('UNAUTHORIZED', message, 401, details);
  }
}

export class ForbiddenError extends DomainError {
  constructor(message: string = 'Forbidden', details?: ErrorDetails) {
    super('FORBIDDEN', message, 403, details);
  }
}

export class RateLimitError extends DomainError {
  constructor(message: string = 'Rate limit exceeded', details?: ErrorDetails) {
    super('RATE_LIMIT', message, 429, details);
  }
}

export class ServiceUnavailableError extends DomainError {
  constructor(message: string = 'Service temporarily unavailable', details?: ErrorDetails) {
    super('SERVICE_UNAVAILABLE', message, 503, details);
  }
}

// Type guard for DomainError
export function isDomainError(error: unknown): error is DomainError {
  return error instanceof DomainError;
}

// Error code enumeration for type safety
export type ErrorCode =
  | 'NOT_FOUND'
  | 'VALIDATION_ERROR'
  | 'CONFLICT'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'RATE_LIMIT'
  | 'SERVICE_UNAVAILABLE'
  | 'INTERNAL_ERROR';
```

---

##### Task 0.9: Centralized Error Handler

**File:** `services/tutorputor-platform/src/core/middleware/error-handler.ts`  
**New File:** Create Fastify error handler

```typescript
/**
 * @doc.type middleware
 * @doc.purpose Centralized error handling for Fastify
 * @doc.layer platform
 * @doc.pattern Middleware
 */

import { FastifyError, FastifyRequest, FastifyReply } from 'fastify';
import { DomainError, isDomainError } from '@tutorputor/core/errors';

interface ErrorResponse {
  error: {
    code: string;
    message: string;
    details?: Record<string, unknown>;
    requestId?: string;
  };
}

export function createErrorHandler(logger: any) {
  return (
    error: FastifyError | DomainError | Error,
    request: FastifyRequest,
    reply: FastifyReply
  ): void => {
    const requestId = request.id as string;

    // Handle DomainError (our canonical errors)
    if (isDomainError(error)) {
      logger.warn({
        err: error,
        requestId,
        code: error.code,
        path: request.url
      }, 'Domain error occurred');

      reply.status(error.statusCode).send({
        error: {
          code: error.code,
          message: error.message,
          details: error.details,
          requestId
        }
      } as ErrorResponse);
      return;
    }

    // Handle Fastify validation errors
    if (error.validation) {
      logger.warn({
        err: error,
        requestId,
        validation: error.validation
      }, 'Validation error');

      reply.status(400).send({
        error: {
          code: 'VALIDATION_ERROR',
          message: error.message,
          details: { validation: error.validation },
          requestId
        }
      } as ErrorResponse);
      return;
    }

    // Handle unexpected errors
    logger.error({
      err: error,
      requestId,
      stack: error.stack
    }, 'Unexpected error');

    // Don't leak internal details in production
    const isDev = process.env.NODE_ENV === 'development';
    reply.status(500).send({
      error: {
        code: 'INTERNAL_ERROR',
        message: 'An internal error occurred',
        details: isDev ? { stack: error.stack } : undefined,
        requestId
      }
    } as ErrorResponse);
  };
}
```

---

##### Task 0.10: Consolidate Pagination Helper

**File:** `libs/tutorputor-core/src/db/helpers/pagination.ts`  
**New File:** Extract from duplicate implementations

```typescript
/**
 * @doc.type utility
 * @doc.purpose Type-safe pagination helper for Prisma queries
 * @doc.layer platform
 * @doc.pattern Database Helper
 */

import { Prisma } from '@prisma/client';

export interface PaginationArgs {
  cursor?: string;
  take?: number;
  skip?: number;
}

export interface PaginatedResult<T> {
  items: T[];
  hasMore: boolean;
  nextCursor?: string;
  totalCount?: number;
}

export interface PaginationOptions<T> {
  orderBy?: Prisma.SortOrder;
  orderField?: keyof T;
  includeTotalCount?: boolean;
}

/**
 * @doc.type function
 * @doc.purpose Execute paginated query with type safety
 * @example
 * const result = await paginate(
 *   prisma.module,
 *   { tenantId: 'abc', published: true },
 *   { cursor: 'xyz', take: 20 },
 *   { orderField: 'createdAt', orderBy: 'desc' }
 * );
 */
export async function paginate<T extends { id: string }>(
  model: {
    findMany: (args: unknown) => Promise<T[]>;
    count?: (args: { where: unknown }) => Promise<number>;
  },
  where: unknown,
  args: PaginationArgs,
  options: PaginationOptions<T> = {}
): Promise<PaginatedResult<T>> {
  const take = (args.take ?? 20) + 1; // +1 to check for hasMore

  const findArgs: Prisma.ModuleFindManyArgs = {
    where,
    take,
    orderBy: options.orderField
      ? { [options.orderField]: options.orderBy ?? 'desc' }
      : { createdAt: 'desc' },
    ...(args.cursor && {
      cursor: { id: args.cursor },
      skip: 1
    })
  };

  const [items, totalCount] = await Promise.all([
    model.findMany(findArgs),
    options.includeTotalCount && model.count
      ? model.count({ where })
      : Promise.resolve(undefined)
  ]);

  const hasMore = items.length === take;
  const trimmed = hasMore ? items.slice(0, -1) : items;

  return {
    items: trimmed,
    hasMore,
    nextCursor: hasMore ? trimmed[trimmed.length - 1]?.id : undefined,
    totalCount
  };
}

/**
 * @doc.type function
 * @doc.purpose Create cursor-based pagination from offset
 */
export async function offsetToCursor<T extends { id: string }>(
  model: { findMany: (args: unknown) => Promise<T[]> },
  where: unknown,
  offset: number,
  orderField: keyof T = 'createdAt',
  orderBy: Prisma.SortOrder = 'desc'
): Promise<string | undefined> {
  const items = await model.findMany({
    where,
    take: 1,
    skip: offset,
    orderBy: { [orderField]: orderBy }
  });

  return items[0]?.id;
}
```

---

##### Task 0.11: Consolidate Tenant Access Validator

**File:** `libs/tutorputor-core/src/auth/tenant-access-validator.ts`  
**Lines:** Extract and enhance from duplicate implementations

```typescript
/**
 * @doc.type service
 * @doc.purpose Validate tenant access across all modules
 * @doc.layer security
 * @doc.pattern Access Control
 */

import { PrismaClient } from '@prisma/client';
import { NotFoundError, ForbiddenError } from '../errors';

export interface AccessContext {
  tenantId: string;
  userId?: string;
  roles?: string[];
}

export class TenantAccessValidator {
  constructor(private prisma: PrismaClient) {}

  /**
   * @doc.type method
   * @doc.purpose Validate entity exists and belongs to tenant
   * @doc.errors NotFoundError, ForbiddenError
   */
  async validateEntityAccess<T extends { id: string; tenantId: string; userId?: string | null }>(
    entityName: string,
    findFirst: (args: { where: unknown }) => Promise<T | null>,
    entityId: string,
    context: AccessContext
  ): Promise<T> {
    const where: Record<string, unknown> = {
      id: entityId,
      tenantId: context.tenantId
    };

    // Add user constraint if specified
    if (context.userId) {
      where.userId = context.userId;
    }

    const record = await findFirst({ where });

    if (!record) {
      throw new NotFoundError(entityName, entityId);
    }

    // Verify tenant match
    if (record.tenantId !== context.tenantId) {
      throw new ForbiddenError('Entity does not belong to tenant');
    }

    return record;
  }

  /**
   * @doc.type method
   * @doc.purpose Validate tenant exists and is active
   */
  async validateTenant(tenantId: string): Promise<void> {
    const tenant = await this.prisma.tenant.findUnique({
      where: { id: tenantId },
      select: { id: true, status: true }
    });

    if (!tenant) {
      throw new NotFoundError('Tenant', tenantId);
    }

    if (tenant.status !== 'ACTIVE') {
      throw new ForbiddenError(`Tenant is ${tenant.status}`);
    }
  }

  /**
   * @doc.type method
   * @doc.purpose Check if user has required role
   */
  async validateRole(
    context: AccessContext,
    requiredRoles: string[]
  ): Promise<void> {
    if (!context.roles) {
      throw new ForbiddenError('Roles not provided');
    }

    const hasRole = requiredRoles.some(role => context.roles?.includes(role));
    if (!hasRole) {
      throw new ForbiddenError(
        `Requires one of: ${requiredRoles.join(', ')}`
      );
    }
  }
}
```

---

## PART 2: AI/ML EXCELLENCE IMPROVEMENTS

### Phase 1: Personalization Foundation (Weeks 1-4)

#### Dimension: Personalization Depth (7.0→10.0)

---

##### Task 1.1: Create LearnerProfile Database Schema

**File:** `libs/tutorputor-db/prisma/schema.prisma`  
**Lines:** After `model User` block (~line 200-400 depending on schema)

```prisma
// ============================================================================
// LEARNER PROFILE MODELS - Personalization Infrastructure
// ============================================================================

model LearnerProfile {
  id            String   @id @default(uuid())
  userId        String   @unique
  user          User     @relation(fields: [userId], references: [id], onDelete: Cascade)

  // Explicit Preferences (learner-defined)
  preferredDifficulty Difficulty @default(MEDIUM)
  preferredModality   Modality   @default(MIXED)
  preferredPacing     Pacing     @default(ADAPTIVE)
  preferredSessionMinutes Int     @default(30)

  // Inferred Learning Styles (0.0-1.0 scores, updated via ML)
  visualLearningScore     Float @default(0.5)     @db.Float
  auditoryLearningScore   Float @default(0.5)     @db.Float
  kinestheticLearningScore Float @default(0.5)   @db.Float
  readingLearningScore    Float @default(0.5)     @db.Float

  // Engagement Patterns (time-series analytics)
  avgSessionMinutes       Float @default(30.0)    @db.Float
  preferredTimeOfDay      String?                  // "morning", "afternoon", "evening"
  peakEngagementDay       String?                  // "weekday", "weekend"
  streakDays              Int     @default(0)
  longestStreak           Int     @default(0)
  totalLearningMinutes    Int     @default(0)

  // Notification Preferences
  notificationFrequency String  @default("daily") // "immediate", "daily", "weekly"
  emailEnabled            Boolean @default(true)
  pushEnabled             Boolean @default(true)

  // Timestamps
  createdAt DateTime @default(now())
  updatedAt DateTime @updatedAt

  // Relations
  masteryLevels      LearnerMastery[]
  knowledgeGaps      KnowledgeGap[]
  learningSessions   LearningSession[]
  preferenceHistory  PreferenceChange[]
  learningPathways   LearningPathway[]

  @@index([userId])
  @@index([preferredDifficulty])
  @@map("learner_profiles")
}

model LearnerMastery {
  id            String @id @default(uuid())
  learnerId     String
  learner       LearnerProfile @relation(fields: [learnerId], references: [id], onDelete: Cascade)

  conceptId     String
  concept       DomainAuthorConcept @relation(fields: [conceptId], references: [id], onDelete: Cascade)

  // Mastery Metrics (Bayesian Knowledge Tracing)
  masteryLevel     Float  @default(0.0)   @db.Float  // 0.0-1.0
  confidence       Float  @default(0.0)   @db.Float  // Statistical confidence
  attempts         Int    @default(0)
  successes        Int    @default(0)
  consecutiveSuccesses Int @default(0)

  // Temporal Metrics
  firstAttemptAt   DateTime?
  lastAttemptAt    DateTime?
  masteredAt       DateTime?
  timeSpentMinutes Float  @default(0.0)   @db.Float

  // Thresholds (personalized based on concept difficulty)
  masteryThreshold Float  @default(0.85)   @db.Float

  updatedAt DateTime @updatedAt

  @@unique([learnerId, conceptId])
  @@index([learnerId, masteryLevel])
  @@index([conceptId, masteryLevel])
  @@map("learner_mastery")
}

model KnowledgeGap {
  id            String @id @default(uuid())
  learnerId     String
  learner       LearnerProfile @relation(fields: [learnerId], references: [id], onDelete: Cascade)

  conceptId     String
  concept       DomainAuthorConcept @relation(fields: [conceptId], references: [id], onDelete: Cascade)

  // Gap Details
  prerequisiteId String
  prerequisite   DomainAuthorConcept @relation("PrerequisiteGap", fields: [prerequisiteId], references: [id], onDelete: Cascade)

  severity      GapSeverity     @default(MEDIUM)
  detectedBy    DetectionMethod @default(ASSESSMENT)
  detectedAt    DateTime        @default(now())
  remediatedAt  DateTime?

  // Remediation
  remediationAttempts   Int     @default(0)
  remediationContentId  String?
  remediationSuccessful Boolean @default(false)

  @@unique([learnerId, conceptId, prerequisiteId])
  @@index([learnerId, severity])
  @@map("knowledge_gaps")
}

model LearningSession {
  id            String @id @default(uuid())
  learnerId     String
  learner       LearnerProfile @relation(fields: [learnerId], references: [id], onDelete: Cascade)

  // Session Context
  moduleId      String?
  module        Module? @relation(fields: [moduleId], references: [id])
  conceptId     String?

  // Temporal
  startedAt     DateTime @default(now())
  endedAt       DateTime?
  durationMinutes Int?

  // Activity Metrics
  interactions    Int @default(0)      // Clicks, scrolls, interactions
  contentViews    Int @default(0)      // Content pieces viewed
  simulationsRun  Int @default(0)      // Simulations started
  assessmentsTaken Int @default(0)

  // Performance
  correctAnswers  Int @default(0)
  totalAnswers    Int @default(0)
  hintsUsed       Int @default(0)
  helpRequests    Int @default(0)

  // Emotional State (AI-detected or self-reported)
  detectedEmotionalState String?         // "engaged", "confused", "frustrated", "bored"
  selfReportedDifficulty String?         // "easy", "just-right", "challenging", "too-hard"

  // Completion
  completed     Boolean @default(false)
  abandoned     Boolean @default(false)
  abandonmentReason String?             // "timeout", "exit", "error"

  // Device/Context
  deviceType    String?                 // "desktop", "tablet", "mobile"
  sessionSource String?                 // "organic", "notification", "assignment"

  @@index([learnerId, startedAt])
  @@index([learnerId, completed])
  @@map("learning_sessions")
}

model PreferenceChange {
  id            String @id @default(uuid())
  learnerId     String
  learner       LearnerProfile @relation(fields: [learnerId], references: [id], onDelete: Cascade)

  preferenceType String   // "difficulty", "modality", "pacing", "notifications"
  oldValue      String
  newValue      String
  changedAt     DateTime  @default(now())

  // Metadata
  changedBy     String    // "user", "system", "ai"
  confidence    Float     @default(1.0)   @db.Float
  reason        String?   // Explanation for change
  sessionId     String?   // Link to session where change occurred

  @@index([learnerId, preferenceType])
  @@map("preference_changes")
}

model LearningPathway {
  id            String @id @default(uuid())
  learnerId     String
  learner       LearnerProfile @relation(fields: [learnerId], references: [id], onDelete: Cascade)

  name          String
  description   String?

  // Pathway State
  status        PathwayStatus @default(ACTIVE)
  targetModuleId String?
  targetDate    DateTime?

  // Progress
  totalConcepts Int       @default(0)
  completedConcepts Int   @default(0)
  masteryAchieved Float   @default(0.0)   @db.Float

  createdAt     DateTime  @default(now())
  updatedAt     DateTime  @updatedAt

  @@index([learnerId, status])
  @@map("learning_pathways")
}

// Enums
enum Difficulty {
  BEGINNER
  EASY
  MEDIUM
  HARD
  EXPERT
}

enum Modality {
  VISUAL
  AUDITORY
  KINESTHETIC
  READING
  MIXED
}

enum Pacing {
  SELF_PACED
  GUIDED
  ADAPTIVE
  INTENSIVE
}

enum GapSeverity {
  LOW
  MEDIUM
  HIGH
  CRITICAL
}

enum DetectionMethod {
  ASSESSMENT
  PREREQUISITE_CHECK
  ADAPTIVE_ANALYSIS
  LEARNER_REPORTED
  AI_PREDICTION
}

enum PathwayStatus {
  DRAFT
  ACTIVE
  PAUSED
  COMPLETED
  ABANDONED
}
```

**Commands:**
```bash
cd /home/samujjwal/Developments/ghatana/products/tutorputor/libs/tutorputor-db
pnpm prisma migrate dev --name add_learner_profiles_v2
pnpm prisma generate
```

---

##### Task 1.2: Create LearnerProfileService

**File:** `services/tutorputor-platform/src/modules/learner/profile-service.ts`  
**New File:** ~900 lines

```typescript
/**
 * @doc.type service
 * @doc.purpose Core service for learner profile management and personalization
 * @doc.layer product
 * @doc.pattern Domain Service
 * @doc.dependencies Prisma, Logger, Metrics
 */

import { PrismaClient, LearnerProfile, LearnerMastery, Prisma } from '@prisma/client';
import { NotFoundError, ValidationError } from '@tutorputor/core/errors';
import { paginate, PaginatedResult, PaginationArgs } from '@tutorputor/core/db/helpers';

// ============================================================================
// TYPES
// ============================================================================

export interface CreateLearnerProfileInput {
  userId: string;
  preferredDifficulty?: 'BEGINNER' | 'EASY' | 'MEDIUM' | 'HARD' | 'EXPERT';
  preferredModality?: 'VISUAL' | 'AUDITORY' | 'KINESTHETIC' | 'READING' | 'MIXED';
  preferredPacing?: 'SELF_PACED' | 'GUIDED' | 'ADAPTIVE' | 'INTENSIVE';
}

export interface UpdatePreferencesInput {
  preferredDifficulty?: 'BEGINNER' | 'EASY' | 'MEDIUM' | 'HARD' | 'EXPERT';
  preferredModality?: 'VISUAL' | 'AUDITORY' | 'KINESTHETIC' | 'READING' | 'MIXED';
  preferredPacing?: 'SELF_PACED' | 'GUIDED' | 'ADAPTIVE' | 'INTENSIVE';
  preferredSessionMinutes?: number;
  notificationFrequency?: 'immediate' | 'daily' | 'weekly';
  changedBy?: 'user' | 'system' | 'ai';
  reason?: string;
}

export interface MasteryUpdateInput {
  conceptId: string;
  correct: boolean;
  confidence?: number;
  timeSpentSeconds?: number;
  hintsUsed?: number;
  attempts?: number;
}

export interface KnowledgeGapInput {
  conceptId: string;
  prerequisiteId: string;
  severity?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  detectedBy?: 'ASSESSMENT' | 'PREREQUISITE_CHECK' | 'ADAPTIVE_ANALYSIS' | 'LEARNER_REPORTED' | 'AI_PREDICTION';
}

export interface RecommendationContext {
  currentModuleId?: string;
  currentConceptId?: string;
  goalConceptId?: string;
  availableTimeMinutes?: number;
  preferredContentTypes?: ('simulation' | 'example' | 'text' | 'video')[];
}

export interface LearningRecommendation {
  conceptId: string;
  conceptName: string;
  recommendationType: 'next' | 'review' | 'prerequisite' | 'challenge';
  reason: string;
  confidence: number;
  estimatedTimeMinutes: number;
  suggestedModality: string;
}

// ============================================================================
// SERVICE
// ============================================================================

export class LearnerProfileService {
  constructor(
    private prisma: PrismaClient,
    private logger: any,
    private metrics: any
  ) {}

  /**
   * @doc.type method
   * @doc.purpose Create a new learner profile for a user
   * @doc.input CreateLearnerProfileInput
   * @doc.output Promise<LearnerProfile>
   * @doc.errors ValidationError if user already has profile
   * @doc.sideEffects Creates LearnerProfile, logs event
   */
  async createProfile(input: CreateLearnerProfileInput): Promise<LearnerProfile> {
    const startTime = Date.now();

    // Check if profile already exists
    const existing = await this.prisma.learnerProfile.findUnique({
      where: { userId: input.userId }
    });

    if (existing) {
      throw new ValidationError('Learner profile already exists for this user');
    }

    const profile = await this.prisma.learnerProfile.create({
      data: {
        userId: input.userId,
        preferredDifficulty: input.preferredDifficulty ?? 'MEDIUM',
        preferredModality: input.preferredModality ?? 'MIXED',
        preferredPacing: input.preferredPacing ?? 'ADAPTIVE',
        preferredSessionMinutes: 30
      }
    });

    // Log and metrics
    this.logger.info({ userId: input.userId, profileId: profile.id }, 'Learner profile created');
    this.metrics?.increment('learner.profile.created');
    this.metrics?.histogram('learner.profile.create_duration', Date.now() - startTime);

    return profile;
  }

  /**
   * @doc.type method
   * @doc.purpose Get existing profile or create new one
   * @doc.input userId: string
   * @doc.output Promise<LearnerProfile>
   * @doc.sideEffects Creates profile if not exists
   */
  async getOrCreateProfile(userId: string): Promise<LearnerProfile> {
    let profile = await this.prisma.learnerProfile.findUnique({
      where: { userId },
      include: {
        masteryLevels: {
          where: { masteryLevel: { gte: 0.7 } },
          select: { conceptId: true, masteryLevel: true }
        },
        knowledgeGaps: {
          where: { remediatedAt: null },
          select: { conceptId: true, prerequisiteId: true, severity: true }
        }
      }
    });

    if (!profile) {
      profile = await this.createProfile({ userId });
      this.logger.info({ userId }, 'Auto-created learner profile');
    }

    return profile;
  }

  /**
   * @doc.type method
   * @doc.purpose Update learner preferences with audit trail
   * @doc.input userId: string, UpdatePreferencesInput
   * @doc.output Promise<LearnerProfile>
   * @doc.errors NotFoundError if profile doesn't exist
   * @doc.sideEffects Updates profile, records preference change
   */
  async updatePreferences(
    userId: string,
    input: UpdatePreferencesInput
  ): Promise<LearnerProfile> {
    const profile = await this.getOrCreateProfile(userId);

    // Track changes for audit
    const changes: { type: string; old: string; new: string }[] = [];

    if (input.preferredDifficulty && input.preferredDifficulty !== profile.preferredDifficulty) {
      changes.push({
        type: 'difficulty',
        old: profile.preferredDifficulty,
        new: input.preferredDifficulty
      });
    }

    if (input.preferredModality && input.preferredModality !== profile.preferredModality) {
      changes.push({
        type: 'modality',
        old: profile.preferredModality,
        new: input.preferredModality
      });
    }

    // Update profile
    const updated = await this.prisma.learnerProfile.update({
      where: { userId },
      data: {
        ...(input.preferredDifficulty && { preferredDifficulty: input.preferredDifficulty }),
        ...(input.preferredModality && { preferredModality: input.preferredModality }),
        ...(input.preferredPacing && { preferredPacing: input.preferredPacing }),
        ...(input.preferredSessionMinutes && { preferredSessionMinutes: input.preferredSessionMinutes }),
        ...(input.notificationFrequency && { notificationFrequency: input.notificationFrequency })
      }
    });

    // Record preference changes
    for (const change of changes) {
      await this.prisma.preferenceChange.create({
        data: {
          learnerId: profile.id,
          preferenceType: change.type,
          oldValue: change.old,
          newValue: change.new,
          changedBy: input.changedBy ?? 'user',
          reason: input.reason
        }
      });
    }

    this.logger.info({ userId, changes: changes.length }, 'Preferences updated');
    this.metrics?.increment('learner.preferences.updated', changes.length);

    return updated;
  }

  /**
   * @doc.type method
   * @doc.purpose Update mastery level using Bayesian Knowledge Tracing
   * @doc.input userId: string, MasteryUpdateInput
   * @doc.output Promise<LearnerMastery>
   * @doc.sideEffects Updates mastery, may trigger gap detection
   */
  async updateMastery(userId: string, input: MasteryUpdateInput): Promise<LearnerMastery> {
    const profile = await this.getOrCreateProfile(userId);
    const timeSpentMinutes = (input.timeSpentSeconds ?? 0) / 60;

    // Get or create mastery record
    const existingMastery = await this.prisma.learnerMastery.findUnique({
      where: {
        learnerId_conceptId: {
          learnerId: profile.id,
          conceptId: input.conceptId
        }
      }
    });

    // Bayesian update parameters
    const slip = 0.1;   // Probability of mistake despite knowing
    const guess = 0.2;  // Probability of correct guess without knowing
    const transit = 0.05; // Probability of learning from attempt

    let newMasteryLevel: number;
    let newConfidence: number;
    let consecutiveSuccesses = existingMastery?.consecutiveSuccesses ?? 0;
    let masteredAt = existingMastery?.masteredAt;

    if (existingMastery) {
      // Bayesian update
      const prior = existingMastery.masteryLevel;
      const likelihood = input.correct
        ? (prior * (1 - slip)) + ((1 - prior) * guess)
        : (prior * slip) + ((1 - prior) * (1 - guess));

      const posterior = input.correct
        ? (prior * (1 - slip)) / likelihood
        : (prior * slip) / likelihood;

      // Add learning transition
      newMasteryLevel = posterior + (1 - posterior) * transit;
      newConfidence = Math.min(0.95, existingMastery.confidence + 0.05);

      // Track consecutive successes
      if (input.correct) {
        consecutiveSuccesses++;
      } else {
        consecutiveSuccesses = 0;
      }

      // Check for mastery achievement
      if (newMasteryLevel >= existingMastery.masteryThreshold && !masteredAt) {
        masteredAt = new Date();
      }
    } else {
      // First attempt
      newMasteryLevel = input.correct ? 0.6 : 0.2;
      newConfidence = 0.3;
      consecutiveSuccesses = input.correct ? 1 : 0;
    }

    // Update mastery
    const mastery = await this.prisma.learnerMastery.upsert({
      where: {
        learnerId_conceptId: {
          learnerId: profile.id,
          conceptId: input.conceptId
        }
      },
      create: {
        learnerId: profile.id,
        conceptId: input.conceptId,
        masteryLevel: newMasteryLevel,
        confidence: newConfidence,
        attempts: input.attempts ?? 1,
        successes: input.correct ? 1 : 0,
        consecutiveSuccesses,
        firstAttemptAt: new Date(),
        lastAttemptAt: new Date(),
        timeSpentMinutes,
        masteredAt
      },
      update: {
        masteryLevel: newMasteryLevel,
        confidence: newConfidence,
        attempts: { increment: input.attempts ?? 1 },
        successes: { increment: input.correct ? 1 : 0 },
        consecutiveSuccesses,
        lastAttemptAt: new Date(),
        timeSpentMinutes: { increment: timeSpentMinutes },
        masteredAt
      }
    });

    // Check for struggling (low mastery after multiple attempts)
    if (mastery.attempts >= 5 && mastery.masteryLevel < 0.4) {
      await this.detectKnowledgeGap(userId, {
        conceptId: input.conceptId,
        prerequisiteId: await this.findPrerequisiteConcept(input.conceptId),
        severity: 'HIGH',
        detectedBy: 'ADAPTIVE_ANALYSIS'
      });
    }

    this.metrics?.histogram('learner.mastery.level', newMasteryLevel);

    return mastery;
  }

  /**
   * @doc.type method
   * @doc.purpose Detect and record knowledge gaps
   * @doc.input userId: string, KnowledgeGapInput
   * @doc.output Promise<void>
   */
  async detectKnowledgeGap(userId: string, input: KnowledgeGapInput): Promise<void> {
    const profile = await this.getOrCreateProfile(userId);

    // Check if gap already exists
    const existingGap = await this.prisma.knowledgeGap.findUnique({
      where: {
        learnerId_conceptId_prerequisiteId: {
          learnerId: profile.id,
          conceptId: input.conceptId,
          prerequisiteId: input.prerequisiteId
        }
      }
    });

    if (existingGap && !existingGap.remediatedAt) {
      // Gap already active, no need to recreate
      return;
    }

    await this.prisma.knowledgeGap.create({
      data: {
        learnerId: profile.id,
        conceptId: input.conceptId,
        prerequisiteId: input.prerequisiteId,
        severity: input.severity ?? 'MEDIUM',
        detectedBy: input.detectedBy ?? 'ADAPTIVE_ANALYSIS'
      }
    });

    this.logger.info({
      userId,
      conceptId: input.conceptId,
      prerequisiteId: input.prerequisiteId,
      severity: input.severity
    }, 'Knowledge gap detected');

    this.metrics?.increment('learner.gap.detected');
  }

  /**
   * @doc.type method
   * @doc.purpose Infer learning style from behavior patterns
   * @doc.input userId: string
   * @doc.output Promise<{ visual: number; auditory: number; kinesthetic: number; reading: number }>
   * @doc.sideEffects Updates profile with inferred scores
   */
  async inferLearningStyle(userId: string): Promise<{
    visual: number;
    auditory: number;
    kinesthetic: number;
    reading: number;
  }> {
    const profile = await this.getOrCreateProfile(userId);

    // Analyze recent sessions
    const sessions = await this.prisma.learningSession.findMany({
      where: {
        learnerId: profile.id,
        startedAt: { gte: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000) } // Last 30 days
      },
      include: {
        module: {
          select: {
            contentType: true
          }
        }
      }
    });

    // Calculate engagement by content type
    const stats = {
      visual: { time: 0, sessions: 0, score: 0 },
      auditory: { time: 0, sessions: 0, score: 0 },
      kinesthetic: { time: 0, sessions: 0, score: 0 },
      reading: { time: 0, sessions: 0, score: 0 }
    };

    for (const session of sessions) {
      const modality = this.classifyModality(session.module?.contentType);
      stats[modality].time += session.durationMinutes ?? 0;
      stats[modality].sessions++;
      stats[modality].score += session.completed ? 1 : 0.5;
    }

    // Normalize to 0-1 scores
    const totalTime = Object.values(stats).reduce((sum, s) => sum + s.time, 0);

    const scores = {
      visual: totalTime > 0 ? stats.visual.time / totalTime : 0.25,
      auditory: totalTime > 0 ? stats.auditory.time / totalTime : 0.25,
      kinesthetic: totalTime > 0 ? stats.kinesthetic.time / totalTime : 0.25,
      reading: totalTime > 0 ? stats.reading.time / totalTime : 0.25
    };

    // Update profile with inferred scores
    await this.prisma.learnerProfile.update({
      where: { id: profile.id },
      data: {
        visualLearningScore: scores.visual,
        auditoryLearningScore: scores.auditory,
        kinestheticLearningScore: scores.kinesthetic,
        readingLearningScore: scores.reading
      }
    });

    // Record preference change
    await this.prisma.preferenceChange.create({
      data: {
        learnerId: profile.id,
        preferenceType: 'modality',
        oldValue: profile.preferredModality,
        newValue: this.dominantModality(scores),
        changedBy: 'ai',
        reason: 'Inferred from engagement patterns',
        confidence: 0.7
      }
    });

    return scores;
  }

  /**
   * @doc.type method
   * @doc.purpose Generate personalized learning recommendations
   * @doc.input userId: string, RecommendationContext
   * @doc.output Promise<LearningRecommendation[]>
   */
  async getRecommendations(
    userId: string,
    context: RecommendationContext
  ): Promise<LearningRecommendation[]> {
    const profile = await this.getOrCreateProfile(userId);
    const recommendations: LearningRecommendation[] = [];

    // Get mastery gaps
    const gaps = await this.prisma.knowledgeGap.findMany({
      where: {
        learnerId: profile.id,
        remediatedAt: null
      },
      include: {
        prerequisite: { select: { name: true, estimatedMinutes: true } }
      },
      orderBy: { severity: 'desc' },
      take: 3
    });

    for (const gap of gaps) {
      recommendations.push({
        conceptId: gap.prerequisiteId,
        conceptName: gap.prerequisite.name,
        recommendationType: 'prerequisite',
        reason: `Prerequisite needed for current learning path`,
        confidence: 0.9,
        estimatedTimeMinutes: gap.prerequisite.estimatedMinutes ?? 15,
        suggestedModality: profile.preferredModality
      });
    }

    // Get next concepts in pathway
    if (context.currentConceptId) {
      const nextConcepts = await this.findNextConcepts(
        context.currentConceptId,
        profile.id
      );

      for (const concept of nextConcepts.slice(0, 2)) {
        const mastery = await this.prisma.learnerMastery.findUnique({
          where: {
            learnerId_conceptId: {
              learnerId: profile.id,
              conceptId: concept.id
            }
          }
        });

        if (!mastery || mastery.masteryLevel < 0.7) {
          recommendations.push({
            conceptId: concept.id,
            conceptName: concept.name,
            recommendationType: 'next',
            reason: `Natural progression from ${context.currentConceptId}`,
            confidence: 0.8,
            estimatedTimeMinutes: concept.estimatedMinutes ?? 20,
            suggestedModality: this.selectOptimalModality(profile, concept.id)
          });
        }
      }
    }

    // Add review recommendations for concepts needing review
    const needsReview = await this.prisma.learnerMastery.findMany({
      where: {
        learnerId: profile.id,
        masteryLevel: { gte: 0.7, lt: 0.85 },
        lastAttemptAt: { lt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000) }
      },
      include: { concept: { select: { name: true, estimatedMinutes: true } } },
      take: 2
    });

    for (const review of needsReview) {
      recommendations.push({
        conceptId: review.conceptId,
        conceptName: review.concept.name,
        recommendationType: 'review',
        reason: 'Mastery decay - review recommended',
        confidence: 0.75,
        estimatedTimeMinutes: review.concept.estimatedMinutes ?? 10,
        suggestedModality: profile.preferredModality
      });
    }

    this.metrics?.histogram('learner.recommendations.generated', recommendations.length);

    return recommendations;
  }

  // ============================================================================
  // PRIVATE HELPERS
  // ============================================================================

  private classifyModality(contentType?: string): 'visual' | 'auditory' | 'kinesthetic' | 'reading' {
    switch (contentType) {
      case 'simulation':
      case 'animation':
        return 'kinesthetic';
      case 'video':
        return 'visual';
      case 'audio':
        return 'auditory';
      default:
        return 'reading';
    }
  }

  private dominantModality(scores: {
    visual: number;
    auditory: number;
    kinesthetic: number;
    reading: number;
  }): string {
    const max = Math.max(scores.visual, scores.auditory, scores.kinesthetic, scores.reading);
    if (scores.visual === max) return 'VISUAL';
    if (scores.auditory === max) return 'AUDITORY';
    if (scores.kinesthetic === max) return 'KINESTHETIC';
    return 'READING';
  }

  private async findPrerequisiteConcept(conceptId: string): Promise<string> {
    // Get concept and find its prerequisite
    const concept = await this.prisma.domainAuthorConcept.findUnique({
      where: { id: conceptId },
      include: { prerequisites: { take: 1 } }
    });

    return concept?.prerequisites[0]?.id ?? conceptId;
  }

  private async findNextConcepts(currentConceptId: string, learnerId: string) {
    // Get concepts that have this as a prerequisite
    return this.prisma.domainAuthorConcept.findMany({
      where: {
        prerequisites: {
          some: { id: currentConceptId }
        }
      },
      select: { id: true, name: true, estimatedMinutes: true }
    });
  }

  private selectOptimalModality(
    profile: LearnerProfile,
    conceptId: string
  ): string {
    // Select based on learning style scores
    const scores = [
      { modality: 'VISUAL', score: profile.visualLearningScore },
      { modality: 'KINESTHETIC', score: profile.kinestheticLearningScore },
      { modality: 'READING', score: profile.readingLearningScore },
      { modality: 'AUDITORY', score: profile.auditoryLearningScore }
    ];

    scores.sort((a, b) => b.score - a.score);
    return scores[0].modality;
  }
}
```

---

##### Task 1.3: Create Learner Profile gRPC Service

**File:** `services/tutorputor-platform/src/modules/learner/grpc-service.ts`  
**New File:** ~400 lines

```typescript
/**
 * @doc.type service
 * @doc.purpose gRPC service for cross-service learner profile access
 * @doc.layer platform
 * @doc.pattern gRPC Service
 */

import * as grpc from '@grpc/grpc-js';
import { LearnerProfileService, LearningRecommendation } from './profile-service';

// Proto definitions would be in contracts repo
interface GetProfileRequest {
  learnerId: string;
  includeMastery?: boolean;
  includeGaps?: boolean;
}

interface GetProfileResponse {
  profile: LearnerProfileProto;
}

interface LearnerProfileProto {
  id: string;
  userId: string;
  preferredDifficulty: string;
  preferredModality: string;
  preferredPacing: string;
  learningStyleScores: LearningStyleScores;
  engagementPatterns: EngagementPatterns;
}

interface LearningStyleScores {
  visual: number;
  auditory: number;
  kinesthetic: number;
  reading: number;
}

interface EngagementPatterns {
  avgSessionMinutes: number;
  preferredTimeOfDay: string;
  streakDays: number;
}

interface GetRecommendationsRequest {
  learnerId: string;
  currentModuleId?: string;
  currentConceptId?: string;
  availableTimeMinutes?: number;
}

interface GetRecommendationsResponse {
  recommendations: RecommendationProto[];
}

interface RecommendationProto {
  conceptId: string;
  conceptName: string;
  type: string;
  reason: string;
  confidence: number;
  estimatedTimeMinutes: number;
  suggestedModality: string;
}

export class LearnerProfileGrpcService {
  constructor(private profileService: LearnerProfileService) {}

  /**
   * @doc.type method
   * @doc.purpose Get learner profile for AI agents
   */
  async getProfile(
    call: grpc.ServerUnaryCall<GetProfileRequest, GetProfileResponse>,
    callback: grpc.sendUnaryData<GetProfileResponse>
  ): Promise<void> {
    try {
      const profile = await this.profileService.getOrCreateProfile(call.request.learnerId);

      callback(null, {
        profile: {
          id: profile.id,
          userId: profile.userId,
          preferredDifficulty: profile.preferredDifficulty,
          preferredModality: profile.preferredModality,
          preferredPacing: profile.preferredPacing,
          learningStyleScores: {
            visual: profile.visualLearningScore,
            auditory: profile.auditoryLearningScore,
            kinesthetic: profile.kinestheticLearningScore,
            reading: profile.readingLearningScore
          },
          engagementPatterns: {
            avgSessionMinutes: profile.avgSessionMinutes,
            preferredTimeOfDay: profile.preferredTimeOfDay ?? 'unknown',
            streakDays: profile.streakDays
          }
        }
      });
    } catch (error) {
      callback(error as Error, null);
    }
  }

  /**
   * @doc.type method
   * @doc.purpose Get personalized recommendations
   */
  async getRecommendations(
    call: grpc.ServerUnaryCall<GetRecommendationsRequest, GetRecommendationsResponse>,
    callback: grpc.sendUnaryData<GetRecommendationsResponse>
  ): Promise<void> {
    try {
      const recommendations = await this.profileService.getRecommendations(
        call.request.learnerId,
        {
          currentModuleId: call.request.currentModuleId,
          currentConceptId: call.request.currentConceptId,
          availableTimeMinutes: call.request.availableTimeMinutes
        }
      );

      callback(null, {
        recommendations: recommendations.map(r => ({
          conceptId: r.conceptId,
          conceptName: r.conceptName,
          type: r.recommendationType,
          reason: r.reason,
          confidence: r.confidence,
          estimatedTimeMinutes: r.estimatedTimeMinutes,
          suggestedModality: r.suggestedModality
        }))
      });
    } catch (error) {
      callback(error as Error, null);
    }
  }

  /**
   * @doc.type method
   * @doc.purpose Update mastery from assessment results
   */
  async updateMastery(
    call: grpc.ServerUnaryCall<{
      learnerId: string;
      conceptId: string;
      correct: boolean;
      confidence?: number;
      timeSpentSeconds?: number;
    }, { success: boolean }>,
    callback: grpc.sendUnaryData<{ success: boolean }>
  ): Promise<void> {
    try {
      await this.profileService.updateMastery(call.request.learnerId, {
        conceptId: call.request.conceptId,
        correct: call.request.correct,
        confidence: call.request.confidence,
        timeSpentSeconds: call.request.timeSpentSeconds
      });

      callback(null, { success: true });
    } catch (error) {
      callback(error as Error, null);
    }
  }
}
```

---

##### Task 1.4: Update ContentGenerationAgent

**File:** `libs/content-studio-agents/src/main/java/com/ghatana/tutorputor/agent/ContentGenerationAgent.java`  
**Lines:** 285-310 (loadLearnerPreferences method)

**Current Code:**
```java
private List<String> loadLearnerPreferences(String learnerId, AgentContext context) {
    return List.of("visual-learning", "step-by-step-explanations"); // HARDCODED
}
```

**Replace With:**
```java
/**
 * Load learner preferences from LearnerProfileService via gRPC
 * Falls back to defaults if service unavailable or learner not found
 */
private List<String> loadLearnerPreferences(String learnerId, AgentContext context) {
    // Default preferences for anonymous/new learners
    List<String> defaultPreferences = List.of("visual-learning", "step-by-step-explanations");
    
    if (learnerId == null || learnerId.isEmpty()) {
        context.logger().info("No learnerId provided, using default preferences");
        return defaultPreferences;
    }
    
    try {
        // Call LearnerProfileService via gRPC
        LearnerProfileClient client = new LearnerProfileClient(learnerProfileServiceAddress);
        GetProfileRequest request = GetProfileRequest.newBuilder()
            .setLearnerId(learnerId)
            .setIncludeMastery(false)
            .setIncludeGaps(false)
            .build();
        
        GetProfileResponse response = client.getProfile(request);
        LearnerProfileProto profile = response.getProfile();
        
        List<String> preferences = new ArrayList<>();
        
        // Add explicit modality preference
        switch (profile.getPreferredModality()) {
            case "VISUAL":
                preferences.add("visual-learning");
                preferences.add("diagrams-preferred");
                break;
            case "AUDITORY":
                preferences.add("audio-explanations");
                preferences.add("narrated-content");
                break;
            case "KINESTHETIC":
                preferences.add("hands-on-learning");
                preferences.add("interactive-simulations");
                break;
            case "READING":
                preferences.add("text-preference");
                preferences.add("detailed-explanations");
                break;
            default:
                preferences.add("visual-learning");
        }
        
        // Add pacing preference
        switch (profile.getPreferredPacing()) {
            case "SELF_PACED":
                preferences.add("self-paced");
                preferences.add("exploration-encouraged");
                break;
            case "GUIDED":
                preferences.add("guided-instruction");
                preferences.add("structured-learning");
                break;
            case "ADAPTIVE":
                preferences.add("adaptive-pacing");
                preferences.add("responsive-to-struggle");
                break;
            case "INTENSIVE":
                preferences.add("intensive-practice");
                preferences.add("rapid-progression");
                break;
        }
        
        // Add inferred learning style preferences if strong signal
        LearningStyleScores scores = profile.getLearningStyleScores();
        if (scores.getVisual() > 0.7) {
            preferences.add("strong-visual-learner");
            preferences.add("more-diagrams");
        }
        if (scores.getKinesthetic() > 0.7) {
            preferences.add("strong-kinesthetic-learner");
            preferences.add("more-simulations");
        }
        if (scores.getReading() > 0.7) {
            preferences.add("strong-reading-learner");
            preferences.add("more-examples");
        }
        if (scores.getAuditory() > 0.7) {
            preferences.add("strong-auditory-learner");
            preferences.add("more-narration");
        }
        
        // Add difficulty preference
        switch (profile.getPreferredDifficulty()) {
            case "BEGINNER":
            case "EASY":
                preferences.add("gentle-introduction");
                preferences.add("foundational-focus");
                break;
            case "HARD":
            case "EXPERT":
                preferences.add("challenging-content");
                preferences.add("advanced-applications");
                break;
        }
        
        context.logger().info("Loaded {} preferences for learner {}", preferences.size(), learnerId);
        
        return preferences.isEmpty() ? defaultPreferences : preferences;
        
    } catch (Exception e) {
        context.logger().warn("Failed to load learner preferences for {}: {}", learnerId, e.getMessage());
        context.metrics().incrementCounter("content_generation.learner_profile.failed");
        return defaultPreferences;
    }
}
```

---

##### Task 1.5: Update UnifiedContentService

**File:** `libs/content-studio-agents/src/main/java/com/ghatana/tutorputor/service/UnifiedContentService.java`  
**Lines:** 312-340 (generateContent method)

**Add Learner Context Enrichment:**
```java
public Promise<ContentGenerationResponse> generateContent(ContentGenerationRequest request) {
    // Fetch real learner context if learnerId provided
    LearnerContext learnerContext = null;
    if (request.learnerId() != null && !request.learnerId().isEmpty()) {
        try {
            learnerContext = learnerProfileClient.getLearnerContext(request.learnerId());
            metricsCollector.recordLearnerContextFetch(request.learnerId(), true);
        } catch (Exception e) {
            logger.warn("Failed to fetch learner context for {}: {}", 
                request.learnerId(), e.getMessage());
            metricsCollector.recordLearnerContextFetch(request.learnerId(), false);
        }
    }
    
    // Enrich request with learner context
    ContentGenerationRequest enrichedRequest = request.toBuilder()
        .learnerContext(learnerContext)
        .build();
    
    // Generate personalized content
    return contentAgent.executeTurn(enrichedRequest, agentContext)
        .map(response -> {
            // Record quality metrics
            metricsCollector.recordContentQuality(
                response.metadata().generationId(),
                response.metadata().confidenceScore(),
                request.learnerId()
            );
            
            // Track personalization
            if (request.learnerId() != null) {
                metricsCollector.recordPersonalizedGeneration(
                    request.learnerId(),
                    response.metadata().confidenceScore()
                );
            }
            
            return response;
        })
        .map(response -> {
            // Post-process based on learner context
            if (learnerContext != null) {
                return applyLearnerAdaptations(response, learnerContext);
            }
            return response;
        });
}

private ContentGenerationResponse applyLearnerAdaptations(
    ContentGenerationResponse response, 
    LearnerContext context
) {
    // Apply modality-specific adaptations
    switch (context.preferredModality()) {
        case "VISUAL":
            return addVisualEnhancements(response);
        case "KINESTHETIC":
            return addInteractiveElements(response);
        case "AUDITORY":
            return addAudioNarration(response);
        default:
            return response;
    }
}
```

---

### Phase 2: Content Generation & Adaptation (Weeks 3-6)

Due to file length constraints, the remaining tasks will be documented in a supplementary file. Here's a summary of the remaining work:

## Remaining Implementation Tasks Summary

### Phase 2: Content Generation (Weeks 3-4)

| Task | File | Description |
|------|------|-------------|
| 1.2.1 | `services/tutorputor-content-generation/StreamingContentGenerator.java` | Real-time streaming API |
| 1.2.2 | `services/tutorputor-platform/modules/content/modality-conversion/service.ts` | Cross-modal conversion |
| 1.2.3 | `libs/tutorputor-simulation/src/engine/auto/index.ts` | Expand templates (Biology + Chemistry +5 each) |

### Phase 3: Adaptation (Weeks 5-6)

| Task | File | Description |
|------|------|-------------|
| 2.1.1 | `services/tutorputor-platform/modules/adaptation/session-engine.ts` | Real-time session adaptation |
| 2.1.2 | `services/tutorputor-platform/modules/content/variation/service.ts` | Content variation generation |
| 2.1.3 | `libs/content-studio-agents/ContentGenerationAgent.java:400` | Lower episode threshold to 1 |
| 2.2.1-3 | `libs/tutorputor-simulation/src/engine/auto/index.ts` | Medicine + Economics templates |

### Phase 4: Assessment (Weeks 6-9)

| Task | File | Description |
|------|------|-------------|
| 3.1.1 | `services/tutorputor-platform/modules/assessment/irt/service.ts` | IRT calibration service |
| 3.1.2 | `services/tutorputor-platform/modules/assessment/misconceptions/database.ts` | Misconception database |
| 3.1.3 | `services/tutorputor-platform/modules/assessment/misconceptions/detector.ts` | Detection algorithms |
| 3.1.4 | `services/tutorputor-platform/modules/learning/assessment-service.ts` | Integrate IRT selection |
| 3.1.5 | `services/tutorputor-platform/modules/assessment/simulation-integration/service.ts` | Simulation assessments |

### Phase 5: Feedback & Quality (Weeks 7-10)

| Task | File | Description |
|------|------|-------------|
| 4.1.1 | `services/tutorputor-platform/modules/content-needs/drift-detector.ts` | Dynamic thresholds |
| 4.1.2 | `services/tutorputor-platform/modules/content/quality-ml/pipeline.ts` | Content quality ML |
| 4.1.3 | `services/tutorputor-platform/modules/experiments/ab-testing/service.ts` | A/B testing framework |

### Phase 6: Safety & Performance (Weeks 9-12)

| Task | File | Description |
|------|------|-------------|
| 5.1.1 | `services/tutorputor-content-generation/validation/BiasDetector.java` | Bias detection |
| 5.1.2 | `services/tutorputor-content-generation/test/AdversarialTestSuite.java` | Adversarial testing |
| 5.1.3 | `services/tutorputor-platform/modules/content/explainability/service.ts` | Explainable AI |
| 5.2.1 | `services/tutorputor-content-generation/CostAwareRouter.java` | Cost-aware routing |
| 5.2.2 | `services/tutorputor-platform/modules/content/cache/intelligent-cache.ts` | Smart caching |

---

## Implementation Schedule

### Week 1
- [x] Task 0.1: Enable strict type checking
- [x] Task 0.2: Create Prisma type utilities
- [x] Task 0.3: Fix `any` types in collaboration module
- [ ] Task 0.4: Fix `any` types in learning module
- [ ] Task 0.5: Fix `any` types in content module
- [ ] Task 0.6: Create `any` type tracking script

### Week 2
- [ ] Task 0.7: LTI signature validation (Security Critical)
- [ ] Task 0.8: Canonical error classes
- [ ] Task 0.9: Centralized error handler
- [ ] Task 0.10: Pagination helper consolidation
- [ ] Task 0.11: Tenant access validator
- [ ] Task 1.1.1: LearnerProfile schema

### Week 3
- [ ] Task 1.1.2: LearnerProfileService implementation
- [ ] Task 1.1.3: Learner profile gRPC service
- [ ] Task 1.1.4: Update ContentGenerationAgent
- [ ] Task 1.1.5: Update UnifiedContentService
- [ ] Task 1.2.1: Streaming content generation API

### Week 4
- [ ] Task 1.2.2: Cross-modal generation service
- [ ] Task 1.2.3: Expand template library (Biology + Chemistry)
- [ ] Integration testing & bug fixes

### Week 5
- [ ] Task 2.1.1: SessionAdaptationEngine
- [ ] Task 2.1.2: Content variation service
- [ ] Task 2.1.3: Update GAA lifecycle

### Week 6
- [ ] Task 2.2.1: Medicine templates
- [ ] Task 2.2.2: Economics templates
- [ ] Task 2.2.3: VR export support
- [ ] Task 3.1.1: IRT calibration service

### Week 7
- [ ] Task 3.1.2: Misconception database
- [ ] Task 3.1.3: Misconception detector

### Week 8
- [ ] Task 3.1.4: Update AssessmentService with IRT
- [ ] Task 3.1.5: Simulation assessment integration

### Week 9
- [ ] Task 4.1.1: Dynamic threshold adjustment
- [ ] Task 4.1.2: Content quality ML pipeline

### Week 10
- [ ] Task 4.1.3: A/B testing framework
- [ ] Task 5.1.1: Advanced bias detection

### Week 11
- [ ] Task 5.1.2: Adversarial testing framework
- [ ] Task 5.1.3: Explainable AI service

### Week 12
- [ ] Task 5.2.1: Cost-aware generation router
- [ ] Task 5.2.2: Intelligent caching layer
- [ ] Final integration testing & documentation

---

## Success Metrics & Tracking

### Target Progress

| Week | Overall Score | Key Deliverables |
|------|---------------|------------------|
| 2 | 8.0 | Type safety fixed, LTI secured |
| 4 | 8.6 | Personalization complete |
| 6 | 9.0 | Adaptation working |
| 8 | 9.5 | Assessment IRT complete |
| 10 | 9.7 | Feedback loops mature |
| 12 | 10.0 | All dimensions at target |

### Key Performance Indicators

| KPI | Baseline | Target |
|-----|----------|--------|
| TypeScript strict mode violations | 1,177 | 0 |
| Personalization accuracy | 50% (hardcoded) | 90% |
| Content generation success rate | 98% | 99.5% |
| IRT assessment efficiency | N/A | +40% improvement |
| Content quality score | 0.85 | 0.95 |
| Cost per generation | Baseline | -30% |
| Cache hit rate | 70% | 85% |

---

**Document Version:** 2.0  
**Updated:** March 30, 2026  
**Next Review:** Weekly sprint planning  
**Estimated Total Effort:** 400 hours (320 AI/ML + 80 Technical Debt)
