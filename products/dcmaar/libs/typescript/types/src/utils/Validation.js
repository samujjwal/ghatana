/**
 * Validation utility type definition
 * Provides validation utilities for common data types
 */
export const Validation = {
    isEmail(value) {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return emailRegex.test(value);
    },
    isUrl(value) {
        try {
            new URL(value);
            return true;
        }
        catch {
            return false;
        }
    },
    isPhoneNumber(value) {
        const phoneRegex = /^[\d\s\-+()]+$/;
        return phoneRegex.test(value) && value.replace(/\D/g, '').length >= 7;
    },
    isStrongPassword(value, minLength = 8) {
        if (value.length < minLength)
            return false;
        return /[a-z]/.test(value) && /[A-Z]/.test(value) && /[0-9]/.test(value) && /[!@#$%^&*]/.test(value);
    },
    isEmpty(value) {
        if (value === null || value === undefined)
            return true;
        if (typeof value === 'string')
            return value.trim().length === 0;
        if (Array.isArray(value))
            return value.length === 0;
        if (typeof value === 'object')
            return Object.keys(value).length === 0;
        return false;
    },
    isNotEmpty(value) {
        return !this.isEmpty(value);
    },
    isBetween(value, min, max) {
        return value >= min && value <= max;
    },
    isValidDate(value) {
        return value instanceof Date && !isNaN(value.getTime());
    },
};
