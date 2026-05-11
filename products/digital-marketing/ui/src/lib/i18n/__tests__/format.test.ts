import { describe, expect, it } from 'vitest';
import { formatCurrency, formatDate, formatDateTime, formatNumber, formatPercent } from '@/lib/i18n/format';

describe('i18n formatting helpers', () => {
  it('formats currency with the canonical DMOS currency default', () => {
    expect(formatCurrency(12500)).toBe('$12,500');
  });

  it('formats dates and datetimes with a stable default timezone', () => {
    expect(formatDate('2026-05-11T20:15:00Z')).toBe('May 11, 2026');
    expect(formatDateTime('2026-05-11T20:15:00Z')).toBe('May 11, 2026, 1:15 PM');
  });

  it('formats numbers and percentage values consistently', () => {
    expect(formatNumber(12345.678, { maximumFractionDigits: 1 })).toBe('12,345.7');
    expect(formatPercent(82.345)).toBe('82.3%');
  });

  it('returns explicit fallback text for missing or invalid values', () => {
    expect(formatCurrency(null)).toBe('Unavailable');
    expect(formatDate('not-a-date', { fallback: '-' })).toBe('-');
    expect(formatPercent(Number.NaN)).toBe('Unavailable');
  });
});
