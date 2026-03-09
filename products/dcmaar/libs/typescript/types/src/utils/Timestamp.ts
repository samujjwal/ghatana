/**
 * Timestamp utility type definition
 * Provides timestamp management utilities
 */

export type Timestamp = number & { readonly __brand: 'Timestamp' };

export function createTimestamp(): Timestamp {
  return Date.now() as Timestamp;
}

export function isValidTimestamp(value: unknown): value is Timestamp {
  return typeof value === 'number' && value > 0 && value <= Date.now();
}

export function asTimestamp(value: number | Date): Timestamp {
  const timestamp = value instanceof Date ? value.getTime() : value;
  if (!isValidTimestamp(timestamp)) {
    throw new Error(`Invalid timestamp: ${timestamp}`);
  }
  return timestamp as Timestamp;
}

export function timestampToDate(timestamp: Timestamp): Date {
  return new Date(timestamp);
}

export function dateToTimestamp(date: Date): Timestamp {
  return asTimestamp(date);
}

export interface TimestampProvider {
  now(): Timestamp;
  fromDate(date: Date): Timestamp;
  toDate(timestamp: Timestamp): Date;
}
