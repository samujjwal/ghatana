/**
 * Missing data state utilities
 *
 * Provides explicit handling for missing or unavailable data instead of using
 * fallback values. This makes data absence explicit and traceable.
 *
 * @doc.type module
 * @doc.purpose Explicit missing data state handling
 * @doc.layer platform
 */

/**
 * Explicit missing data state
 */
export type MissingDataState = 'unavailable' | 'restricted' | 'not_provided' | 'pending';

/**
 * Result type that can be a value or an explicit missing state
 */
export type DataResult<T> = 
  | { status: 'success'; data: T }
  | { status: 'missing'; reason: MissingDataState; field?: string };

/**
 * Create a successful data result
 */
export function success<T>(data: T): DataResult<T> {
  return { status: 'success' as const, data };
}

/**
 * Create a missing data result
 */
export function missing<T>(reason: MissingDataState, field?: string): DataResult<T> {
  return { status: 'missing' as const, reason, field };
}

/**
 * Unwrap a data result, throwing if missing
 */
export function unwrapOrThrow<T>(result: DataResult<T>, context?: string): T {
  if (result.status === 'success') {
    return result.data;
  }
  throw new Error(
    `Missing data${context ? ` in ${context}` : ''}: ${result.reason}${result.field ? ` for field ${result.field}` : ''}`
  );
}

/**
 * Unwrap a data result with a default value
 */
export function unwrapOrDefault<T>(result: DataResult<T>, defaultValue: T): T {
  if (result.status === 'success') {
    return result.data;
  }
  return defaultValue;
}

/**
 * Check if a result is successful
 */
export function isSuccess<T>(result: DataResult<T>): result is { status: 'success'; data: T } {
  return result.status === 'success';
}

/**
 * Check if a result is missing
 */
export function isMissing<T>(result: DataResult<T>): result is { status: 'missing'; reason: MissingDataState; field?: string } {
  return result.status === 'missing';
}

/**
 * Map over a successful result
 */
export function map<T, U>(result: DataResult<T>, fn: (data: T) => U): DataResult<U> {
  if (result.status === 'success') {
    return success(fn(result.data));
  }
  return result;
}

/**
 * Chain multiple data results
 */
export function chain<T, U>(result: DataResult<T>, fn: (data: T) => DataResult<U>): DataResult<U> {
  if (result.status === 'success') {
    return fn(result.data);
  }
  return result;
}

/**
 * Optional field wrapper that distinguishes between null, undefined, and explicit missing
 */
export class OptionalField<T> {
  private constructor(
    private readonly value: T | null,
    private readonly state: 'present' | 'null' | 'undefined' | 'missing'
  ) {}

  static present<T>(value: T): OptionalField<T> {
    return new OptionalField(value, 'present');
  }

  static null<T>(): OptionalField<T> {
    return new OptionalField<T>(null, 'null');
  }

  static undefined<T>(): OptionalField<T> {
    return new OptionalField<T>(null, 'undefined');
  }

  static missing<T>(): OptionalField<T> {
    return new OptionalField<T>(null, 'missing');
  }

  isPresent(): boolean {
    return this.state === 'present';
  }

  isNull(): boolean {
    return this.state === 'null';
  }

  isUndefined(): boolean {
    return this.state === 'undefined';
  }

  isMissing(): boolean {
    return this.state === 'missing';
  }

  getValue(): T | null {
    return this.value;
  }

  getValueOrThrow(): T {
    if (this.state !== 'present') {
      throw new Error(`Field is ${this.state}, not present`);
    }
    return this.value as T;
  }

  getValueOrDefault(defaultValue: T): T {
    return this.state === 'present' ? (this.value as T) : defaultValue;
  }

  map<U>(fn: (value: T) => U): OptionalField<U> {
    if (this.state === 'present') {
      return OptionalField.present(fn(this.value as T));
    }
    return new OptionalField<U>(null, this.state);
  }
}

/**
 * Helper to convert nullable values to OptionalField
 */
export function fromNullable<T>(value: T | null | undefined): OptionalField<T> {
  if (value === null) return OptionalField.null<T>();
  if (value === undefined) return OptionalField.undefined<T>();
  return OptionalField.present(value);
}
