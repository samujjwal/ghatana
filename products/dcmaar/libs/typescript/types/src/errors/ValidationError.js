/**
 * Validation error type definition
 * Specialized errors for validation failures
 */
export class ValidationErrorClass extends Error {
    errors;
    timestamp;
    constructor(message, errors = []) {
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
