/**
 * Application error type definition
 * Represents errors in the DCMAAR system
 */
export declare class AppError extends Error {
    readonly code: string;
    readonly statusCode: number;
    readonly details?: Record<string, unknown>;
    readonly timestamp: Date;
    constructor(message: string, code?: string, statusCode?: number, details?: Record<string, unknown>);
    toJSON(): {
        name: string;
        message: string;
        code: string;
        statusCode: number;
        details: Record<string, unknown> | undefined;
        timestamp: Date;
    };
}
export declare class NotFoundError extends AppError {
    constructor(resource: string, id?: string);
}
export declare class ValidationError extends AppError {
    constructor(message: string, details?: Record<string, unknown>);
}
export declare class UnauthorizedError extends AppError {
    constructor(message?: string);
}
export declare class ForbiddenError extends AppError {
    constructor(message?: string);
}
//# sourceMappingURL=AppError.d.ts.map