/**
 * Data Management Utilities
 *
 * <p><b>Purpose</b><br>
 * Collection of utility functions for handling API responses, data transformations,
 * filtering, sorting, and aggregation operations across all features.
 *
 * <p><b>Functions</b><br>
 * - sortByProperty: Sort arrays by property
 * - filterByStatus: Filter items by status
 * - groupByProperty: Group arrays by property
 * - calculateMetrics: Compute aggregated metrics
 * - formatDuration: Format milliseconds to readable string
 * - filterAnomalies: Filter anomalies by severity
 * - filterAlerts: Filter alerts by type
 * - sortModels: Sort models by accuracy/date
 *
 * @doc.type utility
 * @doc.purpose Data transformation and aggregation utilities
 * @doc.layer product
 * @doc.pattern Utility Module
 */

/**
 * Sort array by property with direction control
 *
 * @param array - Array to sort
 * @param property - Property to sort by
 * @param direction - 'asc' or 'desc'
 * @returns Sorted array
 */
export function sortByProperty<T>(
    array: T[],
    property: keyof T,
    direction: 'asc' | 'desc' = 'asc'
): T[] {
    return [...array].sort((a, b) => {
        const aVal = a[property];
        const bVal = b[property];

        if (aVal === undefined || bVal === undefined) return 0;

        if (typeof aVal === 'string' && typeof bVal === 'string') {
            return direction === 'asc'
                ? aVal.localeCompare(bVal)
                : bVal.localeCompare(aVal);
        }

        if (typeof aVal === 'number' && typeof bVal === 'number') {
            return direction === 'asc' ? aVal - bVal : bVal - aVal;
        }

        return 0;
    });
}

/**
 * Filter array by status property
 *
 * @param array - Array to filter
 * @param status - Status value to filter by
 * @returns Filtered array
 */
export function filterByStatus<T extends { status: string }>(
    array: T[],
    status: string | string[]
): T[] {
    const statuses = Array.isArray(status) ? status : [status];
    return array.filter((item) => statuses.includes(item.status));
}

/**
 * Group array by property value
 *
 * @param array - Array to group
 * @param property - Property to group by
 * @returns Grouped object
 */
export function groupByProperty<T>(
    array: T[],
    property: keyof T
): Record<string, T[]> {
    return array.reduce(
        (acc, item) => {
            const key = String(item[property]);
            if (!acc[key]) {
                acc[key] = [];
            }
            acc[key].push(item);
            return acc;
        },
        {} as Record<string, T[]>
    );
}

/**
 * Calculate statistics for numeric array
 *
 * @param values - Array of numbers
 * @returns Statistics object
 */
export function calculateMetrics(values: number[]) {
    if (values.length === 0) {
        return { min: 0, max: 0, avg: 0, sum: 0, count: 0 };
    }

    const sum = values.reduce((a, b) => a + b, 0);
    const avg = sum / values.length;
    const min = Math.min(...values);
    const max = Math.max(...values);

    return {
        min,
        max,
        avg: parseFloat(avg.toFixed(2)),
        sum,
        count: values.length,
    };
}

/**
 * Format duration in milliseconds to readable string
 *
 * @param ms - Duration in milliseconds
 * @returns Formatted string (e.g., "1h 30m 45s")
 */
export function formatDuration(ms: number): string {
    const seconds = Math.floor((ms / 1000) % 60);
    const minutes = Math.floor((ms / (1000 * 60)) % 60);
    const hours = Math.floor((ms / (1000 * 60 * 60)) % 24);
    const days = Math.floor(ms / (1000 * 60 * 60 * 24));

    const parts = [];
    if (days > 0) parts.push(`${days}d`);
    if (hours > 0) parts.push(`${hours}h`);
    if (minutes > 0) parts.push(`${minutes}m`);
    if (seconds > 0) parts.push(`${seconds}s`);

    return parts.length > 0 ? parts.join(' ') : '0s';
}

/**
 * Format timestamp to readable string
 *
 * @param timestamp - ISO timestamp string or Date
 * @param includeTime - Include time portion
 * @returns Formatted string
 */
export function formatTimestamp(
    timestamp: string | Date,
    includeTime = true
): string {
    const date = typeof timestamp === 'string' ? new Date(timestamp) : timestamp;
    const dateStr = date.toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
    });

    if (!includeTime) return dateStr;

    const timeStr = date.toLocaleTimeString('en-US', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
    });

    return `${dateStr} ${timeStr}`;
}

/**
 * Filter anomalies by severity level
 *
 * @param anomalies - Array of anomalies
 * @param severity - Severity level(s)
 * @returns Filtered anomalies
 */
export function filterAnomalies(
    anomalies: Array<{ severity: string }>,
    severity?: string | string[]
): Array<{ severity: string }> {
    if (!severity) return anomalies;
    const severities = Array.isArray(severity) ? severity : [severity];
    return anomalies.filter((a) => severities.includes(a.severity));
}

/**
 * Filter alerts by type
 *
 * @param alerts - Array of alerts
 * @param type - Alert type(s)
 * @returns Filtered alerts
 */
export function filterAlerts(
    alerts: Array<{ type?: string }>,
    type?: string | string[]
): Array<{ type?: string }> {
    if (!type) return alerts;
    const types = Array.isArray(type) ? type : [type];
    return alerts.filter((alert) => types.includes(alert.type || ''));
}

/**
 * Sort models by accuracy (highest first)
 *
 * @param models - Array of models
 * @returns Sorted models
 */
export function sortModelsByAccuracy(
    models: Array<{ accuracy?: number; name: string }>
): Array<{ accuracy?: number; name: string }> {
    return [...models].sort(
        (a, b) => (b.accuracy || 0) - (a.accuracy || 0)
    );
}

/**
 * Get severity count statistics
 *
 * @param items - Array of items with severity
 * @returns Severity count object
 */
export function getSeverityCounts(
    items: Array<{ severity: 'critical' | 'high' | 'medium' | 'low' }>
): { critical: number; high: number; medium: number; low: number } {
    const counts = { critical: 0, high: 0, medium: 0, low: 0 };
    items.forEach((item) => {
        counts[item.severity]++;
    });
    return counts;
}

/**
 * Get status count statistics
 *
 * @param items - Array of items with status
 * @returns Status count object
 */
export function getStatusCounts(
    items: Array<{ status: string }>
): Record<string, number> {
    return items.reduce(
        (acc, item) => {
            acc[item.status] = (acc[item.status] || 0) + 1;
            return acc;
        },
        {} as Record<string, number>
    );
}

/**
 * Paginate array
 *
 * @param array - Array to paginate
 * @param pageNumber - Page number (1-indexed)
 * @param pageSize - Items per page
 * @returns Paginated array and metadata
 */
export function paginate<T>(
    array: T[],
    pageNumber: number,
    pageSize: number
): { data: T[]; pageNumber: number; pageSize: number; total: number; pages: number } {
    const total = array.length;
    const pages = Math.ceil(total / pageSize);
    const startIndex = (pageNumber - 1) * pageSize;
    const endIndex = startIndex + pageSize;

    return {
        data: array.slice(startIndex, endIndex),
        pageNumber,
        pageSize,
        total,
        pages,
    };
}

/**
 * Deduplicate array by property
 *
 * @param array - Array to deduplicate
 * @param property - Property to deduplicate by
 * @returns Deduplicated array
 */
export function deduplicateBy<T>(
    array: T[],
    property: keyof T
): T[] {
    const seen = new Set();
    return array.filter((item) => {
        const key = item[property];
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
    });
}

export default {
    sortByProperty,
    filterByStatus,
    groupByProperty,
    calculateMetrics,
    formatDuration,
    formatTimestamp,
    filterAnomalies,
    filterAlerts,
    sortModelsByAccuracy,
    getSeverityCounts,
    getStatusCounts,
    paginate,
    deduplicateBy,
};
