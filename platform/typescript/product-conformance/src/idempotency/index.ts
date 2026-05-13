export type IdempotencyObservationStatus = 'miss' | 'completed' | 'expired' | 'conflict';

export interface IdempotencyObservation {
  readonly operation: string;
  readonly key: string;
  readonly fingerprint: string;
  readonly status: IdempotencyObservationStatus;
  readonly replayed: boolean;
  readonly expired: boolean;
  readonly principalId: string;
  readonly tenantId: string;
  readonly correlationId?: string;
}

export interface IdempotencyValidationOptions {
  readonly requireCorrelationId?: boolean;
  readonly requireConflictObservation?: boolean;
  readonly requireReplayObservation?: boolean;
  readonly requireExpiredObservation?: boolean;
}

export interface IdempotencyValidationResult {
  readonly valid: boolean;
  readonly errors: readonly string[];
  readonly observations?: readonly IdempotencyObservation[];
}

export function validateIdempotencyObservations(
  value: unknown,
  options: IdempotencyValidationOptions = {},
): IdempotencyValidationResult {
  const entries = Array.isArray(value) ? value : null;
  if (!entries) {
    return { valid: false, errors: ['idempotency observations must be an array'] };
  }

  const errors: string[] = [];
  const observations: IdempotencyObservation[] = [];

  for (const [index, entry] of entries.entries()) {
    const parsed = parseObservation(entry, index, errors, options);
    if (parsed) {
      observations.push(parsed);
    }
  }

  if (options.requireReplayObservation && !observations.some((entry) => entry.status === 'completed' && entry.replayed)) {
    errors.push('idempotency observations must include a completed replay');
  }
  if (options.requireExpiredObservation && !observations.some((entry) => entry.status === 'expired' && entry.expired)) {
    errors.push('idempotency observations must include an expired-key execution');
  }
  if (options.requireConflictObservation && !observations.some((entry) => entry.status === 'conflict')) {
    errors.push('idempotency observations must include a same-key fingerprint conflict');
  }

  return errors.length > 0
    ? { valid: false, errors }
    : { valid: true, errors: [], observations };
}

function parseObservation(
  value: unknown,
  index: number,
  errors: string[],
  options: IdempotencyValidationOptions,
): IdempotencyObservation | null {
  const record = toRecord(value);
  const owner = `idempotency[${index}]`;
  if (!record) {
    errors.push(`${owner} must be an object`);
    return null;
  }

  requireNonBlankString(record, 'operation', errors, owner);
  requireNonBlankString(record, 'key', errors, owner);
  requireNonBlankString(record, 'fingerprint', errors, owner);
  requireNonBlankString(record, 'principalId', errors, owner);
  requireNonBlankString(record, 'tenantId', errors, owner);
  validateOptionalString(record, 'correlationId', errors, owner);
  if (options.requireCorrelationId) {
    requireNonBlankString(record, 'correlationId', errors, owner);
  }

  if (!isStatus(record.status)) {
    errors.push(`${owner}.status must be one of miss, completed, expired, conflict`);
  }
  if (typeof record.replayed !== 'boolean') {
    errors.push(`${owner}.replayed must be boolean`);
  }
  if (typeof record.expired !== 'boolean') {
    errors.push(`${owner}.expired must be boolean`);
  }

  if (record.status === 'completed' && record.replayed !== true) {
    errors.push(`${owner}.completed observation must set replayed=true`);
  }
  if (record.status === 'expired' && record.expired !== true) {
    errors.push(`${owner}.expired observation must set expired=true`);
  }
  if (record.status === 'miss' && (record.replayed === true || record.expired === true)) {
    errors.push(`${owner}.miss observation must not be replayed or expired`);
  }

  if (
    !isNonBlankString(record.operation) ||
    !isNonBlankString(record.key) ||
    !isNonBlankString(record.fingerprint) ||
    !isNonBlankString(record.principalId) ||
    !isNonBlankString(record.tenantId) ||
    !isStatus(record.status) ||
    typeof record.replayed !== 'boolean' ||
    typeof record.expired !== 'boolean'
  ) {
    return null;
  }

  return {
    operation: record.operation,
    key: record.key,
    fingerprint: record.fingerprint,
    status: record.status,
    replayed: record.replayed,
    expired: record.expired,
    principalId: record.principalId,
    tenantId: record.tenantId,
    ...(typeof record.correlationId === 'string' ? { correlationId: record.correlationId } : {}),
  };
}

function requireNonBlankString(
  record: Readonly<Record<string, unknown>>,
  key: string,
  errors: string[],
  owner: string,
): void {
  if (!isNonBlankString(record[key])) {
    errors.push(`${owner}.${key} must be a non-empty string`);
  }
}

function validateOptionalString(
  record: Readonly<Record<string, unknown>>,
  key: string,
  errors: string[],
  owner: string,
): void {
  if (record[key] !== undefined && typeof record[key] !== 'string') {
    errors.push(`${owner}.${key} must be a string when present`);
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

function isStatus(value: unknown): value is IdempotencyObservationStatus {
  return value === 'miss' || value === 'completed' || value === 'expired' || value === 'conflict';
}
