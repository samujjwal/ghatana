export interface StringKeyedRecord {
  [key: string]: unknown;
}

export function isRecord(value: unknown): value is StringKeyedRecord {
  return typeof value === 'object' && value !== null;
}

export function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export function getString(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined;
}

export function getNumber(value: unknown): number | undefined {
  return typeof value === 'number' ? value : undefined;
}

export function getArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : [];
}
