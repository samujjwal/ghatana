/**
 * Formatting helpers for DMOS UI values.
 *
 * @doc.type utility
 * @doc.purpose Centralized locale-aware date, currency, number, and percent formatting
 * @doc.layer frontend
 */

const DEFAULT_LOCALE = 'en-US';
const DEFAULT_CURRENCY = 'USD';
const DEFAULT_TIME_ZONE = 'America/Los_Angeles';
const UNAVAILABLE = 'Unavailable';

interface FormatOptions {
  locale?: string;
}

interface CurrencyFormatOptions extends FormatOptions {
  currency?: string;
  fallback?: string;
  maximumFractionDigits?: number;
}

interface DateFormatOptions extends FormatOptions {
  fallback?: string;
  timeZone?: string;
}

interface NumberFormatOptions extends FormatOptions {
  fallback?: string;
  maximumFractionDigits?: number;
  minimumFractionDigits?: number;
}

export function formatCurrency(
  value: number | null | undefined,
  options: CurrencyFormatOptions = {},
): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return options.fallback ?? UNAVAILABLE;
  }

  return new Intl.NumberFormat(options.locale ?? DEFAULT_LOCALE, {
    style: 'currency',
    currency: options.currency ?? DEFAULT_CURRENCY,
    maximumFractionDigits: options.maximumFractionDigits ?? 0,
  }).format(value);
}

export function formatDate(
  value: string | number | Date | null | undefined,
  options: DateFormatOptions = {},
): string {
  const date = parseDate(value);
  if (!date) {
    return options.fallback ?? UNAVAILABLE;
  }

  return new Intl.DateTimeFormat(options.locale ?? DEFAULT_LOCALE, {
    dateStyle: 'medium',
    timeZone: options.timeZone ?? DEFAULT_TIME_ZONE,
  }).format(date);
}

export function formatDateTime(
  value: string | number | Date | null | undefined,
  options: DateFormatOptions = {},
): string {
  const date = parseDate(value);
  if (!date) {
    return options.fallback ?? UNAVAILABLE;
  }

  return new Intl.DateTimeFormat(options.locale ?? DEFAULT_LOCALE, {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: options.timeZone ?? DEFAULT_TIME_ZONE,
  }).format(date);
}

export function formatNumber(
  value: number | null | undefined,
  options: NumberFormatOptions = {},
): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return options.fallback ?? UNAVAILABLE;
  }

  return new Intl.NumberFormat(options.locale ?? DEFAULT_LOCALE, {
    maximumFractionDigits: options.maximumFractionDigits ?? 0,
    minimumFractionDigits: options.minimumFractionDigits,
  }).format(value);
}

export function formatPercent(
  value: number | null | undefined,
  options: NumberFormatOptions = {},
): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return options.fallback ?? UNAVAILABLE;
  }

  return `${formatNumber(value, {
    locale: options.locale,
    maximumFractionDigits: options.maximumFractionDigits ?? 1,
    minimumFractionDigits: options.minimumFractionDigits ?? 1,
  })}%`;
}

function parseDate(value: string | number | Date | null | undefined): Date | null {
  if (value === null || value === undefined) {
    return null;
  }

  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}
