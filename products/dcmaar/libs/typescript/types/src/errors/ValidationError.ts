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

export class ValidationErrorClass extends Error {
  public readonly errors: ValidationError[];
  public readonly timestamp: Date;

  constructor(message: string, errors: ValidationError[] = []) {
    super(message);
    this.name = 'ValidationErrorClass';
    this.errors = errors;
    this.timestamp = new Date();
    Object.setPrototypeOf(this, ValidationErrorClass.prototype);
  }

  toJSON() {
    return {
      name: this.name,
      message: this.message,
      errors: this.errors,
      timestamp: this.timestamp,
    };
  }
}
