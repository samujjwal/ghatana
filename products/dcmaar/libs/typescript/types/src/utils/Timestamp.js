/**
 * Timestamp utility type definition
 * Provides timestamp management utilities
 */
export function createTimestamp() {
    return Date.now();
}
export function isValidTimestamp(value) {
    return typeof value === 'number' && value > 0 && value <= Date.now();
}
export function asTimestamp(value) {
    const timestamp = value instanceof Date ? value.getTime() : value;
    if (!isValidTimestamp(timestamp)) {
        throw new Error(`Invalid timestamp: ${timestamp}`);
    }
    return timestamp;
}
export function timestampToDate(timestamp) {
    return new Date(timestamp);
}
export function dateToTimestamp(date) {
    return asTimestamp(date);
}
