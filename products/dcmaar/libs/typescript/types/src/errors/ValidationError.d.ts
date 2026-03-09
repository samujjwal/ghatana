/**
 * Validation error type definition
 * Specialized errors for validation failures
 */
export interface ValidationResult {
    valid: boolean;
    errors: ValidationError[];
}
export interface ValidationError {
    field: string;
    message: string;
    code: string;
    value?: unknown;
}
export interface Validator<T = unknown> {
    validate(data: T): ValidationResult;
    validateAsync(data: T): Promise<ValidationResult>;
}
export interface ValidationRule {
    field: string;
    type: 'required' | 'email' | 'url' | 'number' | 'string' | 'boolean' | 'custom';
    message?: string;
    options?: Record<string, unknown>;
}
export declare class ValidationErrorClass extends Error {
    readonly errors: ValidationError[];
    readonly timestamp: Date;
    constructor(message: string, errors?: ValidationError[]);
    toJSON(): {
        name: string;
        message: string;
        errors: ValidationError[];
        timestamp: Date;
    };
}
//# sourceMappingURL=ValidationError.d.ts.map