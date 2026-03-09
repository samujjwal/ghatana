/**
 * Date utility functions shared across Flashit applications
 */

import { format, formatDistanceToNow, parseISO } from 'date-fns';

/**
 * Format a date string or Date object to a readable format
 */
export function formatDate(date: string | Date, formatStr: string = 'PPP'): string {
    const dateObj = typeof date === 'string' ? parseISO(date) : date;
    return format(dateObj, formatStr);
}

/**
 * Format a date as relative time (e.g., "2 hours ago")
 */
export function formatRelativeTime(date: string | Date): string {
    const dateObj = typeof date === 'string' ? parseISO(date) : date;
    return formatDistanceToNow(dateObj, { addSuffix: true });
}

/**
 * Get current ISO timestamp
 */
export function getCurrentTimestamp(): string {
    return new Date().toISOString();
}

/**
 * Check if a date string is valid ISO 8601
 */
export function isValidISODate(dateString: string): boolean {
    try {
        const date = parseISO(dateString);
        return !isNaN(date.getTime());
    } catch {
        return false;
    }
}
