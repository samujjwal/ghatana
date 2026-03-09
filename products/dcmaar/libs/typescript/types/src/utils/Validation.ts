/**
 * Validation utility type definition
 * Provides validation utilities for common data types
 */

export interface ValidationOptions {
  allowNull?: boolean;
  allowUndefined?: boolean;
  trim?: boolean;
  lowercase?: boolean;
  uppercase?: boolean;
}

export type StringValidator = (value: string, options?: ValidationOptions) => boolean;
export type NumberValidator = (value: number, options?: ValidationOptions) => boolean;
export type DateValidator = (value: Date, options?: ValidationOptions) => boolean;

export const Validation = {
  isEmail(value: string): boolean {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(value);
  },

  isUrl(value: string): boolean {
    try {
      new URL(value);
      return true;
    } catch {
      return false;
    }
  },

  isPhoneNumber(value: string): boolean {
    const phoneRegex = /^[\d\s\-+()]+$/;
    return phoneRegex.test(value) && value.replace(/\D/g, '').length >= 7;
  },

  isStrongPassword(value: string, minLength: number = 8): boolean {
    if (value.length < minLength) return false;
    return /[a-z]/.test(value) && /[A-Z]/.test(value) && /[0-9]/.test(value) && /[!@#$%^&*]/.test(value);
  },

  isEmpty(value: unknown): boolean {
    if (value === null || value === undefined) return true;
    if (typeof value === 'string') return value.trim().length === 0;
    if (Array.isArray(value)) return value.length === 0;
    if (typeof value === 'object') return Object.keys(value).length === 0;
    return false;
  },

  isNotEmpty(value: unknown): boolean {
    return !this.isEmpty(value);
  },

  isBetween(value: number, min: number, max: number): boolean {
    return value >= min && value <= max;
  },

  isValidDate(value: Date): boolean {
    return value instanceof Date && !isNaN(value.getTime());
  },
};
