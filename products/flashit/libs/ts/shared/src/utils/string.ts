/**
 * String utility functions specific to Flashit applications.
 * Generic string utilities (truncate, capitalize) are in @ghatana/platform-utils.
 */

/**
 * Convert a string to title case
 */
export function toTitleCase(str: string): string {
    const capitalized = (word: string) => {
        if (!word) return word;
        return word.charAt(0).toUpperCase() + word.slice(1);
    };
    return str
        .toLowerCase()
        .split(' ')
        .map(capitalized)
        .join(' ');
}

/**
 * Generate a random string of specified length
 */
export function randomString(length: number): string {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    let result = '';
    for (let i = 0; i < length; i++) {
        result += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return result;
}

/**
 * Slugify a string (convert to URL-friendly format)
 */
export function slugify(str: string): string {
    return str
        .toLowerCase()
        .trim()
        .replace(/[^\w\s-]/g, '')
        .replace(/[\s_-]+/g, '-')
        .replace(/^-+|-+$/g, '');
}

