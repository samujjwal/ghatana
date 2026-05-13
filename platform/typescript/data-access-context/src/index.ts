/**
 * @ghatana/data-access-context - Kernel-owned data access and mutation contracts.
 *
 * The package keeps product request adapters thin: products resolve authenticated
 * HTTP/framework details, then map them into this Kernel shape before touching
 * persistence, audit, telemetry, or idempotency stores.
 */

export type DataAccessMetadataValue = string | number | boolean;

export interface DataAccessContext {
  /** Tenant ID for multi-tenancy. Required for authenticated data access. */
  readonly tenantId: string;
  /** Principal/user ID. Required for authenticated data access. */
  readonly principalId: string;
  /** Correlation ID for traces, logs, audit, and cross-service diagnostics. */
  readonly correlationId?: string;
  /** Request ID for transport-level request tracking. */
  readonly requestId?: string;
  /** Client IP address when available from the transport adapter. */
  readonly clientIp?: string;
  /** User agent string when available from the transport adapter. */
  readonly userAgent?: string;
  /** Product or platform audit classification for the operation. */
  readonly auditClassification?: string;
  /** Product-scoped owner/resource scope used by policy and audit systems. */
  readonly dataOwnerScope?: string;
  /** Idempotency key supplied by the caller for mutating operations. */
  readonly idempotencyKey?: string;
  /** Additional safe product metadata. Values must be scalar and serializable. */
  readonly metadata: Readonly<Record<string, DataAccessMetadataValue>>;
  /** Checks whether required tenant and principal fields are populated. */
  isInitialized(): boolean;
  /** Returns the idempotency key or fails closed with a typed Kernel error. */
  requireIdempotencyKey(): string;
}

export interface DataAccessContextBuildOptions {
  readonly requireCorrelationId?: boolean;
  readonly requireAuditClassification?: boolean;
  readonly requireDataOwnerScope?: boolean;
  readonly requireIdempotencyKey?: boolean;
}

export interface CreateDataAccessContextOptions extends DataAccessContextBuildOptions {
  readonly correlationId?: string;
  readonly requestId?: string;
  readonly clientIp?: string;
  readonly userAgent?: string;
  readonly auditClassification?: string;
  readonly dataOwnerScope?: string;
  readonly idempotencyKey?: string;
  readonly metadata?: Readonly<Record<string, DataAccessMetadataValue>>;
}

export interface TenantResolutionInput {
  readonly principalId: string;
  readonly requestedTenantId?: string;
}

export type TenantResolver = (input: TenantResolutionInput) => string;

export class DataAccessContextError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'DataAccessContextError';
  }
}

export class IdempotencyError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'IdempotencyError';
  }
}

const requireNonBlank = (value: string | undefined, fieldName: string): string => {
  if (!value || value.trim().length === 0) {
    throw new DataAccessContextError(`DataAccessContext not initialized: ${fieldName} is null or blank`);
  }
  return value;
};

const optionalNonBlank = (value: string | undefined): string | undefined => {
  if (value === undefined) {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
};

const validateMetadata = (
  metadata: Readonly<Record<string, DataAccessMetadataValue>> | undefined,
): Readonly<Record<string, DataAccessMetadataValue>> => {
  if (!metadata) {
    return {};
  }

  for (const [key, value] of Object.entries(metadata)) {
    if (key.trim().length === 0) {
      throw new DataAccessContextError('DataAccessContext metadata keys must be non-blank');
    }
    if (!['string', 'number', 'boolean'].includes(typeof value)) {
      throw new DataAccessContextError(`DataAccessContext metadata value for "${key}" must be scalar`);
    }
  }

  return Object.freeze({ ...metadata });
};

export class DataAccessContextBuilder {
  private tenantId?: string;
  private principalId?: string;
  private correlationId?: string;
  private requestId?: string;
  private clientIp?: string;
  private userAgent?: string;
  private auditClassification?: string;
  private dataOwnerScope?: string;
  private idempotencyKey?: string;
  private metadata?: Readonly<Record<string, DataAccessMetadataValue>>;

  setTenantId(tenantId: string): this {
    this.tenantId = tenantId;
    return this;
  }

  setPrincipalId(principalId: string): this {
    this.principalId = principalId;
    return this;
  }

  setCorrelationId(correlationId: string | undefined): this {
    this.correlationId = optionalNonBlank(correlationId);
    return this;
  }

  setRequestId(requestId: string | undefined): this {
    this.requestId = optionalNonBlank(requestId);
    return this;
  }

  setClientIp(clientIp: string | undefined): this {
    this.clientIp = optionalNonBlank(clientIp);
    return this;
  }

  setUserAgent(userAgent: string | undefined): this {
    this.userAgent = optionalNonBlank(userAgent);
    return this;
  }

  setAuditClassification(auditClassification: string | undefined): this {
    this.auditClassification = optionalNonBlank(auditClassification);
    return this;
  }

  setDataOwnerScope(dataOwnerScope: string | undefined): this {
    this.dataOwnerScope = optionalNonBlank(dataOwnerScope);
    return this;
  }

  setIdempotencyKey(idempotencyKey: string | undefined): this {
    this.idempotencyKey = optionalNonBlank(idempotencyKey);
    return this;
  }

  setMetadata(metadata: Readonly<Record<string, DataAccessMetadataValue>> | undefined): this {
    this.metadata = metadata;
    return this;
  }

  build(options: DataAccessContextBuildOptions = {}): DataAccessContext {
    const tenantId = requireNonBlank(this.tenantId, 'tenantId');
    const principalId = requireNonBlank(this.principalId, 'principalId');
    const correlationId = options.requireCorrelationId
      ? requireNonBlank(this.correlationId, 'correlationId')
      : this.correlationId;
    const auditClassification = options.requireAuditClassification
      ? requireNonBlank(this.auditClassification, 'auditClassification')
      : this.auditClassification;
    const dataOwnerScope = options.requireDataOwnerScope
      ? requireNonBlank(this.dataOwnerScope, 'dataOwnerScope')
      : this.dataOwnerScope;
    const idempotencyKey = options.requireIdempotencyKey
      ? requireNonBlank(this.idempotencyKey, 'idempotencyKey')
      : this.idempotencyKey;

    return createFrozenContext({
      tenantId,
      principalId,
      correlationId,
      requestId: this.requestId,
      clientIp: this.clientIp,
      userAgent: this.userAgent,
      auditClassification,
      dataOwnerScope,
      idempotencyKey,
      metadata: validateMetadata(this.metadata),
    });
  }
}

function createFrozenContext(fields: {
  readonly tenantId: string;
  readonly principalId: string;
  readonly correlationId?: string;
  readonly requestId?: string;
  readonly clientIp?: string;
  readonly userAgent?: string;
  readonly auditClassification?: string;
  readonly dataOwnerScope?: string;
  readonly idempotencyKey?: string;
  readonly metadata: Readonly<Record<string, DataAccessMetadataValue>>;
}): DataAccessContext {
  const context: DataAccessContext = {
    tenantId: fields.tenantId,
    principalId: fields.principalId,
    ...(fields.correlationId ? { correlationId: fields.correlationId } : {}),
    ...(fields.requestId ? { requestId: fields.requestId } : {}),
    ...(fields.clientIp ? { clientIp: fields.clientIp } : {}),
    ...(fields.userAgent ? { userAgent: fields.userAgent } : {}),
    ...(fields.auditClassification ? { auditClassification: fields.auditClassification } : {}),
    ...(fields.dataOwnerScope ? { dataOwnerScope: fields.dataOwnerScope } : {}),
    ...(fields.idempotencyKey ? { idempotencyKey: fields.idempotencyKey } : {}),
    metadata: fields.metadata,
    isInitialized: () => fields.tenantId.length > 0 && fields.principalId.length > 0,
    requireIdempotencyKey: () => requireNonBlank(fields.idempotencyKey, 'idempotencyKey'),
  };

  return Object.freeze(context);
}

export function createDataAccessContext(
  tenantId: string,
  principalId: string,
  options: CreateDataAccessContextOptions = {},
): DataAccessContext {
  return new DataAccessContextBuilder()
    .setTenantId(tenantId)
    .setPrincipalId(principalId)
    .setCorrelationId(options.correlationId)
    .setRequestId(options.requestId)
    .setClientIp(options.clientIp)
    .setUserAgent(options.userAgent)
    .setAuditClassification(options.auditClassification)
    .setDataOwnerScope(options.dataOwnerScope)
    .setIdempotencyKey(options.idempotencyKey)
    .setMetadata(options.metadata)
    .build(options);
}

export function resolveTenantForPrincipal({
  principalId,
  requestedTenantId,
}: TenantResolutionInput): string {
  if (!requestedTenantId || requestedTenantId === principalId) {
    return principalId;
  }

  throw new DataAccessContextError('requested tenant is not authorized for the authenticated principal');
}

export type IdempotencyStatus = 'completed' | 'expired';

export interface IdempotencyReplay<TResponse> {
  readonly status: 'completed';
  readonly response: TResponse;
  readonly audit: IdempotencyAudit;
}

export interface IdempotencyMiss {
  readonly status: 'miss' | 'expired';
  readonly audit: IdempotencyAudit;
}

export type IdempotencyLookupResult<TResponse> = IdempotencyReplay<TResponse> | IdempotencyMiss;

export interface IdempotencyAudit {
  readonly key: string;
  readonly fingerprint: string;
  readonly principalId: string;
  readonly tenantId: string;
  readonly correlationId?: string;
  readonly replayed: boolean;
  readonly expired: boolean;
}

export interface IdempotencyRecord<TResponse> {
  readonly key: string;
  readonly fingerprint: string;
  readonly principalId: string;
  readonly tenantId: string;
  readonly response: TResponse;
  readonly createdAtEpochMs: number;
  readonly expiresAtEpochMs: number;
}

export interface IdempotencyStore<TResponse> {
  get(key: string): Promise<IdempotencyRecord<TResponse> | undefined>;
  put(record: IdempotencyRecord<TResponse>): Promise<void>;
  delete?(key: string): Promise<void>;
}

export interface IdempotentMutationInput<TResponse> {
  readonly context: DataAccessContext;
  readonly fingerprint: string;
  readonly ttlMs: number;
  readonly store: IdempotencyStore<TResponse>;
  readonly nowEpochMs?: () => number;
  readonly execute: () => Promise<TResponse>;
}

export async function runIdempotentMutation<TResponse>({
  context,
  fingerprint,
  ttlMs,
  store,
  nowEpochMs = Date.now,
  execute,
}: IdempotentMutationInput<TResponse>): Promise<IdempotencyLookupResult<TResponse>> {
  const key = context.requireIdempotencyKey();
  const existing = await store.get(key);
  const now = nowEpochMs();

  if (existing && existing.expiresAtEpochMs > now) {
    if (existing.fingerprint !== fingerprint) {
      throw new IdempotencyError('idempotency key replayed with a different request fingerprint');
    }

    return {
      status: 'completed',
      response: existing.response,
      audit: createIdempotencyAudit(context, key, fingerprint, true, false),
    };
  }

  if (existing && existing.expiresAtEpochMs <= now) {
    await store.delete?.(key);
  }

  const response = await execute();
  await store.put({
    key,
    fingerprint,
    principalId: context.principalId,
    tenantId: context.tenantId,
    response,
    createdAtEpochMs: now,
    expiresAtEpochMs: now + ttlMs,
  });

  return {
    status: existing ? 'expired' : 'miss',
    audit: createIdempotencyAudit(context, key, fingerprint, false, Boolean(existing)),
  };
}

function createIdempotencyAudit(
  context: DataAccessContext,
  key: string,
  fingerprint: string,
  replayed: boolean,
  expired: boolean,
): IdempotencyAudit {
  return {
    key,
    fingerprint,
    principalId: context.principalId,
    tenantId: context.tenantId,
    ...(context.correlationId ? { correlationId: context.correlationId } : {}),
    replayed,
    expired,
  };
}

export function createIdempotencyFingerprint(parts: readonly unknown[]): string {
  return JSON.stringify(parts.map(canonicalize));
}

function canonicalize(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(canonicalize);
  }
  if (value && typeof value === 'object') {
    const entries = Object.entries(value as Record<string, unknown>)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, entryValue]) => [key, canonicalize(entryValue)]);
    return Object.fromEntries(entries);
  }
  return value;
}

export function createInMemoryIdempotencyStore<TResponse>(): IdempotencyStore<TResponse> {
  const records = new Map<string, IdempotencyRecord<TResponse>>();

  return {
    async get(key: string): Promise<IdempotencyRecord<TResponse> | undefined> {
      return records.get(key);
    },
    async put(record: IdempotencyRecord<TResponse>): Promise<void> {
      records.set(record.key, record);
    },
    async delete(key: string): Promise<void> {
      records.delete(key);
    },
  };
}
