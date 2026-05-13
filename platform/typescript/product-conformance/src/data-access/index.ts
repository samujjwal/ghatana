export type DataAccessMetadataValue = string | number | boolean;

export interface DataAccessContextSnapshot {
  readonly tenantId: string;
  readonly principalId: string;
  readonly correlationId?: string;
  readonly auditClassification?: string;
  readonly dataOwnerScope?: string;
  readonly idempotencyKey?: string;
  readonly metadata?: Readonly<Record<string, DataAccessMetadataValue>>;
}

export interface DataAccessValidationOptions {
  readonly requireCorrelationId?: boolean;
  readonly requireAuditClassification?: boolean;
  readonly requireDataOwnerScope?: boolean;
  readonly requireIdempotencyKey?: boolean;
}

export interface DataAccessValidationResult {
  readonly valid: boolean;
  readonly errors: readonly string[];
  readonly snapshot?: DataAccessContextSnapshot;
}

export function validateDataAccessContextSnapshot(
  value: unknown,
  options: DataAccessValidationOptions = {},
): DataAccessValidationResult {
  const errors: string[] = [];
  const record = toRecord(value);

  if (!record) {
    return { valid: false, errors: ['data access context must be an object'] };
  }

  requireNonBlankString(record, 'tenantId', errors);
  requireNonBlankString(record, 'principalId', errors);
  validateOptionalString(record, 'correlationId', errors);
  validateOptionalString(record, 'auditClassification', errors);
  validateOptionalString(record, 'dataOwnerScope', errors);
  validateOptionalString(record, 'idempotencyKey', errors);

  if (options.requireCorrelationId) {
    requireNonBlankString(record, 'correlationId', errors);
  }
  if (options.requireAuditClassification) {
    requireNonBlankString(record, 'auditClassification', errors);
  }
  if (options.requireDataOwnerScope) {
    requireNonBlankString(record, 'dataOwnerScope', errors);
  }
  if (options.requireIdempotencyKey) {
    requireNonBlankString(record, 'idempotencyKey', errors);
  }

  const metadata = parseMetadata(record.metadata, errors);

  if (errors.length > 0) {
    return { valid: false, errors };
  }

  return {
    valid: true,
    errors: [],
    snapshot: {
      tenantId: String(record.tenantId),
      principalId: String(record.principalId),
      ...(typeof record.correlationId === 'string' ? { correlationId: record.correlationId } : {}),
      ...(typeof record.auditClassification === 'string'
        ? { auditClassification: record.auditClassification }
        : {}),
      ...(typeof record.dataOwnerScope === 'string' ? { dataOwnerScope: record.dataOwnerScope } : {}),
      ...(typeof record.idempotencyKey === 'string' ? { idempotencyKey: record.idempotencyKey } : {}),
      ...(metadata ? { metadata } : {}),
    },
  };
}

function parseMetadata(
  value: unknown,
  errors: string[],
): Readonly<Record<string, DataAccessMetadataValue>> | null {
  if (value === undefined) {
    return null;
  }
  const record = toRecord(value);
  if (!record) {
    errors.push('dataAccess.metadata must be an object when present');
    return null;
  }

  const metadata: Record<string, DataAccessMetadataValue> = {};
  for (const [key, metadataValue] of Object.entries(record)) {
    if (key.trim().length === 0) {
      errors.push('dataAccess.metadata keys must be non-empty strings');
      continue;
    }
    if (
      typeof metadataValue !== 'string' &&
      typeof metadataValue !== 'number' &&
      typeof metadataValue !== 'boolean'
    ) {
      errors.push(`dataAccess.metadata.${key} must be a string, number, or boolean`);
      continue;
    }
    metadata[key] = metadataValue;
  }

  return Object.freeze(metadata);
}

function requireNonBlankString(
  record: Readonly<Record<string, unknown>>,
  key: string,
  errors: string[],
): void {
  if (!isNonBlankString(record[key])) {
    errors.push(`dataAccess.${key} must be a non-empty string`);
  }
}

function validateOptionalString(
  record: Readonly<Record<string, unknown>>,
  key: string,
  errors: string[],
): void {
  if (record[key] !== undefined && typeof record[key] !== 'string') {
    errors.push(`dataAccess.${key} must be a string when present`);
  }
}

function toRecord(value: unknown): Readonly<Record<string, unknown>> | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null;
  }
  return value as Readonly<Record<string, unknown>>;
}

function isNonBlankString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}
