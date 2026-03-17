/**
 * @ghatana/dcmaar-shared-ui-core
 *
 * Core types and utilities shared across DCMAAR apps.
 *
 * @doc.type module
 * @doc.purpose Shared types and utilities for DCMAAR UI apps
 * @doc.layer product
 * @doc.pattern SharedKernel
 */

// ── Connection status ────────────────────────────────────────────────────────
export type ConnectionStatus = 'connected' | 'disconnected' | 'connecting' | 'error';

// ── Time range ───────────────────────────────────────────────────────────────
export type TimeRangeUnit = 'minutes' | 'hours' | 'days' | 'weeks';

export interface TimeRange {
  value: number;
  unit: TimeRangeUnit;
  /** ISO 8601 start timestamp (computed) */
  startIso?: string;
  /** ISO 8601 end timestamp (defaults to now) */
  endIso?: string;
}

export const PRESET_TIME_RANGES: TimeRange[] = [
  { value: 15, unit: 'minutes' },
  { value: 1,  unit: 'hours' },
  { value: 6,  unit: 'hours' },
  { value: 24, unit: 'hours' },
  { value: 7,  unit: 'days' },
  { value: 30, unit: 'days' },
];

// ── Utility helpers ──────────────────────────────────────────────────────────

/**
 * Format an uptime duration in seconds into a human-readable string.
 * e.g. 90061 → "1d 1h 1m 1s"
 */
export function formatUptime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return '—';
  const d = Math.floor(seconds / 86400);
  const h = Math.floor((seconds % 86400) / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  const parts: string[] = [];
  if (d > 0) parts.push(`${d}d`);
  if (h > 0) parts.push(`${h}h`);
  if (m > 0) parts.push(`${m}m`);
  if (s > 0 || parts.length === 0) parts.push(`${s}s`);
  return parts.join(' ');
}

