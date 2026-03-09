/**
 * Validation utilities for form fields
 */

/** Represents a single validation rule with a predicate and error message */
export type ValidationRule = {
  validate: (value: unknown) => boolean;
  message: string;
};

/** Mapping of field names to their validation rules */
export type ValidationRules = {
  [key: string]: ValidationRule[];
};

/** Mapping of field names to their error messages */
export type ValidationErrors = {
  [key: string]: string;
};

/**
 * Common validation rules
 */
export const validators = {
  required: (message = 'This field is required'): ValidationRule => ({
    validate: (value: unknown) => {
      if (typeof value === 'string') {
        return value.trim().length > 0;
      }
      return value !== null && value !== undefined && value !== '';
    },
    message,
  }),

  email: (message = 'Please enter a valid email address'): ValidationRule => ({
    validate: (value: unknown) => {
      if (typeof value !== 'string') return false;
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      return emailRegex.test(value);
    },
    message,
  }),

  minLength: (min: number, message?: string): ValidationRule => ({
    validate: (value: unknown) => {
      if (typeof value !== 'string') return false;
      return value.length >= min;
    },
    message: message || `Must be at least ${min} characters`,
  }),

  maxLength: (max: number, message?: string): ValidationRule => ({
    validate: (value: unknown) => {
      if (typeof value !== 'string') return false;
      return value.length <= max;
    },
    message: message || `Must be at most ${max} characters`,
  }),

  min: (min: number, message?: string): ValidationRule => ({
    validate: (value: unknown) => {
      if (typeof value !== 'number') return false;
      return value >= min;
    },
    message: message || `Must be at least ${min}`,
  }),

  max: (max: number, message?: string): ValidationRule => ({
    validate: (value: unknown) => {
      if (typeof value !== 'number') return false;
      return value <= max;
    },
    message: message || `Must be at most ${max}`,
  }),

  pattern: (regex: RegExp, message = 'Invalid format'): ValidationRule => ({
    validate: (value: unknown) => {
      if (typeof value !== 'string') return false;
      return regex.test(value);
    },
    message,
  }),

  url: (message = 'Please enter a valid URL'): ValidationRule => ({
    validate: (value: unknown) => {
      if (typeof value !== 'string') return false;
      try {
        new URL(value);
        return true;
      } catch {
        return false;
      }
    },
    message,
  }),

  phone: (message = 'Please enter a valid phone number'): ValidationRule => ({
    validate: (value: unknown) => {
      if (typeof value !== 'string') return false;
      const phoneRegex = /^[\d\s\-+()]+$/;
      return phoneRegex.test(value) && value.replace(/\D/g, '').length >= 10;
    },
    message,
  }),

  match: (
    fieldName: string,
    getFieldValue: (name: string) => unknown,
    message?: string
  ): ValidationRule => ({
    validate: (value: unknown) => value === getFieldValue(fieldName),
    message: message || `Must match ${fieldName}`,
  }),

  custom: (
    fn: (value: unknown) => boolean,
    message: string
  ): ValidationRule => ({
    validate: fn,
    message,
  }),
};

/**
 * Validate a single field
 */
export function validateField(
  value: unknown,
  rules: ValidationRule[]
): string | null {
  for (const rule of rules) {
    if (!rule.validate(value)) {
      return rule.message;
    }
  }
  return null;
}

/**
 * Validate all fields in a form.
 *
 * @param values - Object containing field values to validate
 * @param rules - Validation rules for each field
 * @returns Object mapping field names to error messages (empty if no errors)
 */
export function validateForm(
  values: Record<string, unknown>,
  rules: ValidationRules
): ValidationErrors {
  const errors: ValidationErrors = {};

  for (const [field, fieldRules] of Object.entries(rules)) {
    // eslint-disable-next-line security/detect-object-injection
    const error = validateField(values[field], fieldRules);
    if (error) {
      // eslint-disable-next-line security/detect-object-injection
      errors[field] = error;
    }
  }

  return errors;
}

/**
 * Check if form has errors
 */
export function hasErrors(errors: ValidationErrors): boolean {
  return Object.keys(errors).length > 0;
}
