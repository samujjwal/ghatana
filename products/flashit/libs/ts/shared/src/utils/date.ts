/**
 * Flashit-specific date utility functions.
 * Generic date utilities (formatDate, getCurrentTimestamp) are in @ghatana/platform-utils.
 */

import { formatDistanceToNow, parseISO } from 'date-fns';

/**
 * Format a date as relative time (e.g., "2 hours ago")
 */
export function formatRelativeTime(date: string | Date): string {
    const dateObj = typeof date === 'string' ? parseISO(date) : date;
    return formatDistanceToNow(dateObj, { addSuffix: true });
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

