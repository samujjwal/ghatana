/**
 * Result type for explicit error handling.
 *
 * Replaces exceptions with explicit error values. Use for operations that can fail
 * in expected ways (validation, external calls, business logic).
 *
 * Example usage:
 * ```typescript
 * const result = await userService.findById(id);
 * result.match(
 *   user => console.log(`Found: ${user.name}`),
 *   error => console.error(`Error: ${error.message}`)
 * );
 * ```
 *
 * @doc.type module
 * @doc.purpose Explicit error handling without exceptions
 * @doc.layer infrastructure
 * @doc.pattern Result Type / Railway-Oriented Programming
 */

/**
 * Base error interface for Result failures
 */
export interface ResultError {
    readonly message: string;
    readonly code?: string;
    readonly cause?: unknown;
}

/**
 * Success variant of Result
 */
export type Success<T> = {
    readonly kind: 'success';
    readonly value: T;
};

/**
 * Failure variant of Result
 */
export type Failure<E> = {
    readonly kind: 'failure';
    readonly error: E;
};

/**
 * Result type representing either success or failure
 */
export type Result<T, E = ResultError> = Success<T> | Failure<E>;

/**
 * Create a success result
 */
export function success<T>(value: T): Success<T> {
    return { kind: 'success', value };
}

/**
 * Create a failure result
 */
export function failure<E>(error: E): Failure<E> {
    return { kind: 'failure', error };
}

/**
 * Check if result is success
 */
export function isSuccess<T, E>(result: Result<T, E>): result is Success<T> {
    return result.kind === 'success';
}

/**
 * Check if result is failure
 */
export function isFailure<T, E>(result: Result<T, E>): result is Failure<E> {
    return result.kind === 'failure';
}

/**
 * Get value or throw error
 */
export function getOrThrow<T, E extends ResultError>(result: Result<T, E>): T {
    if (isSuccess(result)) {
        return result.value;
    }
    throw new Error(result.error.message);
}

/**
 * Get value or return default
 */
export function getOrDefault<T, E>(result: Result<T, E>, defaultValue: T): T {
    return isSuccess(result) ? result.value : defaultValue;
}

/**
 * Get value or compute from error
 */
export function getOrElse<T, E>(result: Result<T, E>, onError: (error: E) => T): T {
    return isSuccess(result) ? result.value : onError(result.error);
}

/**
 * Map success value
 */
export function map<T, U, E>(result: Result<T, E>, fn: (value: T) => U): Result<U, E> {
    return isSuccess(result) ? success(fn(result.value)) : result;
}

/**
 * Map success value async
 */
export async function mapAsync<T, U, E>(
    result: Result<T, E>,
    fn: (value: T) => Promise<U>
): Promise<Result<U, E>> {
    return isSuccess(result) ? success(await fn(result.value)) : result;
}

/**
 * Map error value
 */
export function mapError<T, E, F>(result: Result<T, E>, fn: (error: E) => F): Result<T, F> {
    return isFailure(result) ? failure(fn(result.error)) : result;
}

/**
 * Flat map (chain) results
 */
export function flatMap<T, U, E>(
    result: Result<T, E>,
    fn: (value: T) => Result<U, E>
): Result<U, E> {
    return isSuccess(result) ? fn(result.value) : result;
}

/**
 * Flat map async
 */
export async function flatMapAsync<T, U, E>(
    result: Result<T, E>,
    fn: (value: T) => Promise<Result<U, E>>
): Promise<Result<U, E>> {
    return isSuccess(result) ? fn(result.value) : result;
}

/**
 * Recover from failure with new success value
 */
export function recover<T, E>(result: Result<T, E>, fn: (error: E) => T): Result<T, E> {
    return isFailure(result) ? success(fn(result.error)) : result;
}

/**
 * Recover from failure with another result
 */
export function recoverWith<T, E>(
    result: Result<T, E>,
    fn: (error: E) => Result<T, E>
): Result<T, E> {
    return isFailure(result) ? fn(result.error) : result;
}

/**
 * Execute side effect on success
 */
export function tap<T, E>(result: Result<T, E>, fn: (value: T) => void): Result<T, E> {
    if (isSuccess(result)) {
        fn(result.value);
    }
    return result;
}

/**
 * Execute side effect on failure
 */
export function tapError<T, E>(result: Result<T, E>, fn: (error: E) => void): Result<T, E> {
    if (isFailure(result)) {
        fn(result.error);
    }
    return result;
}

/**
 * Match both cases
 */
export function match<T, E, U>(
    result: Result<T, E>,
    onSuccess: (value: T) => U,
    onFailure: (error: E) => U
): U {
    return isSuccess(result) ? onSuccess(result.value) : onFailure(result.error);
}

/**
 * Convert to nullable (discards error)
 */
export function toNullable<T, E>(result: Result<T, E>): T | null {
    return isSuccess(result) ? result.value : null;
}

/**
 * Convert to optional (undefined on failure)
 */
export function toOptional<T, E>(result: Result<T, E>): T | undefined {
    return isSuccess(result) ? result.value : undefined;
}

/**
 * Wrap function that may throw
 */
export function tryCatch<T>(fn: () => T, onError: (error: unknown) => ResultError): Result<T, ResultError> {
    try {
        return success(fn());
    } catch (error) {
        return failure(onError(error));
    }
}

/**
 * Wrap async function that may throw
 */
export async function tryCatchAsync<T>(
    fn: () => Promise<T>,
    onError: (error: unknown) => ResultError
): Promise<Result<T, ResultError>> {
    try {
        return success(await fn());
    } catch (error) {
        return failure(onError(error));
    }
}

/**
 * Combine multiple results - all must succeed
 */
export function all<T, E>(results: Result<T, E>[]): Result<T[], E> {
    const values: T[] = [];
    for (const result of results) {
        if (isFailure(result)) {
            return result;
        }
        values.push(result.value);
    }
    return success(values);
}

/**
 * Combine multiple async results - all must succeed
 */
export async function allAsync<T, E>(
    results: Promise<Result<T, E>>[]
): Promise<Result<T[], E>> {
    const values: T[] = [];
    for (const promise of results) {
        const result = await promise;
        if (isFailure(result)) {
            return result;
        }
        values.push(result.value);
    }
    return success(values);
}

/**
 * Create a not found error
 */
export function notFoundError(resource: string, id?: string): ResultError {
    return {
        message: `${resource}${id ? ` '${id}'` : ''} not found`,
        code: 'NOT_FOUND',
    };
}

/**
 * Create a validation error
 */
export function validationError(message: string, field?: string): ResultError {
    return {
        message: field ? `Validation error for '${field}': ${message}` : message,
        code: 'VALIDATION_ERROR',
    };
}

/**
 * Create an unauthorized error
 */
export function unauthorizedError(message = 'Unauthorized'): ResultError {
    return {
        message,
        code: 'UNAUTHORIZED',
    };
}

/**
 * Create a forbidden error
 */
export function forbiddenError(message = 'Forbidden'): ResultError {
    return {
        message,
        code: 'FORBIDDEN',
    };
}

/**
 * Create a conflict error
 */
export function conflictError(message: string): ResultError {
    return {
        message,
        code: 'CONFLICT',
    };
}

/**
 * Create an external service error
 */
export function externalError(service: string, message: string): ResultError {
    return {
        message: `${service} error: ${message}`,
        code: 'EXTERNAL_ERROR',
    };
}

/**
 * Create an unknown error from any value
 */
export function unknownError(error: unknown): ResultError {
    return {
        message: error instanceof Error ? error.message : String(error),
        code: 'UNKNOWN_ERROR',
        cause: error,
    };
}
