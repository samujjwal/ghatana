/**
 * Formatting Utilities for Ghatana Platform
 *
 * Consolidated from DCMAAR and YAPPC
 *
 * @migrated-from @ghatana/dcmaar-shared-ui-core/utils/formatters
 */

/**
 * Format uptime from a connection timestamp
 *
 * @param connectedSince - ISO timestamp or undefined
 * @returns Formatted uptime string (e.g., "5m", "3h", "2d")
 *
 * @example
 * ```typescript
 * formatUptime('2025-11-04T10:00:00Z') // "30m" (if current time is 10:30)
 * formatUptime(undefined) // "0s"
 * ```
 */
export function formatUptime(connectedSince: string | undefined): string {
  if (!connectedSince) return '0s';

  const since = new Date(connectedSince);
  const now = new Date();
  const diff = Math.floor((now.getTime() - since.getTime()) / 1000);

  if (diff < 60) return `${diff}s`;
  if (diff < 3600) return `${Math.floor(diff / 60)}m`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h`;
  return `${Math.floor(diff / 86400)}d`;
}

/**
 * Format distance to now (relative time)
 *
 * @param date - Date object
 * @returns Human-readable relative time string
 *
 * @example
 * ```typescript
 * formatDistanceToNow(new Date(Date.now() - 30000)) // "just now"
 * formatDistanceToNow(new Date(Date.now() - 300000)) // "5m ago"
 * ```
 */
export function formatDistanceToNow(date: Date): string {
  const now = new Date();
  const diff = Math.floor((now.getTime() - date.getTime()) / 1000);

  if (diff < 60) return 'just now';
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  return `${Math.floor(diff / 86400)}d ago`;
}

/**
 * Format bytes to human-readable size
 *
 * @param bytes - Number of bytes
 * @param decimals - Number of decimal places (default: 2)
 * @returns Formatted size string (e.g., "1.5 MB")
 *
 * @example
 * ```typescript
 * formatBytes(1024) // "1.00 KB"
 * formatBytes(1536, 1) // "1.5 KB"
 * formatBytes(1048576) // "1.00 MB"
 * ```
 */
export function formatBytes(bytes: number, decimals: number = 2): string {
  if (bytes === 0) return '0 B';

  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));

  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(decimals))} ${sizes[i]}`;
}

/**
 * Format number with locale-specific formatting
 *
 * @param num - Number to format
 * @param locale - Locale string (default: 'en-US')
 * @returns Formatted number string with commas/separators
 *
 * @example
 * ```typescript
 * formatNumber(1234567) // "1,234,567"
 * formatNumber(1234567.89) // "1,234,567.89"
 * ```
 */
export function formatNumber(num: number, locale: string = 'en-US'): string {
  return new Intl.NumberFormat(locale).format(num);
}

/**
 * Format number as percentage
 *
 * @param value - Percentage value
 * @param decimals - Number of decimal places (default: 1)
 * @returns Formatted percentage string
 *
 * @example
 * ```typescript
 * formatPercentage(75) // "75.0%"
 * formatPercentage(75.567, 2) // "75.57%"
 * ```
 */
export function formatPercentage(value: number, decimals: number = 1): string {
  return `${value.toFixed(decimals)}%`;
}

/**
 * Format latency in appropriate units
 *
 * @param ms - Latency in milliseconds
 * @returns Formatted latency string (μs, ms, or s)
 *
 * @example
 * ```typescript
 * formatLatency(0.5) // "500μs"
 * formatLatency(50) // "50ms"
 * formatLatency(1500) // "1.50s"
 * ```
 */
export function formatLatency(ms: number): string {
  if (ms < 1) return `${(ms * 1000).toFixed(0)}μs`;
  if (ms < 1000) return `${ms.toFixed(0)}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}

/**
 * Format timestamp to locale string
 *
 * @param timestamp - Timestamp (number or string)
 * @param locale - Locale string (default: 'en-US')
 * @returns Formatted date/time string
 *
 * @example
 * ```typescript
 * formatTimestamp(1699104000000) // "11/4/2025, 10:00:00 AM"
 * formatTimestamp('2025-11-04T10:00:00Z') // "11/4/2025, 10:00:00 AM"
 * ```
 */
export function formatTimestamp(
  timestamp: number | string,
  locale: string = 'en-US'
): string {
  const date = new Date(timestamp);
  return date.toLocaleString(locale);
}

/**
 * Format date to ISO string
 *
 * @param date - Date object
 * @returns ISO 8601 date string
 *
 * @example
 * ```typescript
 * formatDateISO(new Date('2025-11-04')) // "2025-11-04T00:00:00.000Z"
 * ```
 */
export function formatDateISO(date: Date): string {
  return date.toISOString();
}

/**
 * Truncate string with ellipsis
 *
 * @param str - String to truncate
 * @param maxLength - Maximum length before truncation
 * @returns Truncated string with ellipsis if needed
 *
 * @example
 * ```typescript
 * truncate('Hello World', 8) // "Hello..."
 * truncate('Hello', 10) // "Hello"
 * ```
 */
export function truncate(str: string, maxLength: number): string {
  if (str.length <= maxLength) return str;
  return `${str.slice(0, maxLength)}...`;
}

/**
 * Format currency value
 *
 * @param amount - Amount to format
 * @param currency - Currency code (default: 'USD')
 * @param locale - Locale string (default: 'en-US')
 * @returns Formatted currency string
 *
 * @example
 * ```typescript
 * formatCurrency(1234.56) // "$1,234.56"
 * formatCurrency(1234.56, 'EUR', 'de-DE') // "1.234,56 €"
 * ```
 */
export function formatCurrency(
  amount: number,
  currency: string = 'USD',
  locale: string = 'en-US'
): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
  }).format(amount);
}

/**
 * Format duration in milliseconds to human-readable format
 *
 * @param ms - Duration in milliseconds
 * @returns Formatted duration string
 *
 * @example
 * ```typescript
 * formatDuration(1500) // "1.5s"
 * formatDuration(65000) // "1m 5s"
 * formatDuration(3665000) // "1h 1m 5s"
 * ```
 */
export function formatDuration(ms: number): string {
  const seconds = Math.floor(ms / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);

  if (days > 0) {
    return `${days}d ${hours % 24}h ${minutes % 60}m`;
  }
  if (hours > 0) {
    return `${hours}h ${minutes % 60}m ${seconds % 60}s`;
  }
  if (minutes > 0) {
    return `${minutes}m ${seconds % 60}s`;
  }
  if (seconds > 0) {
    return `${seconds}s`;
  }
  return `${ms}ms`;
}

/**
 * Capitalize first letter of string
 *
 * @param str - String to capitalize
 * @returns Capitalized string
 *
 * @example
 * ```typescript
 * capitalize('hello world') // "Hello world"
 * ```
 */
export function capitalize(str: string): string {
  if (!str) return str;
  return str.charAt(0).toUpperCase() + str.slice(1);
}

/**
 * Convert string to title case
 *
 * @param str - String to convert
 * @returns Title case string
 *
 * @example
 * ```typescript
 * titleCase('hello world') // "Hello World"
 * titleCase('HELLO WORLD') // "Hello World"
 * ```
 */
export function titleCase(str: string): string {
  if (!str) return str;
  return str
    .toLowerCase()
    .split(' ')
    .map((word) => capitalize(word))
    .join(' ');
}
