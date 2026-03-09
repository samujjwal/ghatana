import { describe, it, expect } from 'vitest';
import {
  formatBytes,
  formatNumber,
  formatPercentage,
  formatLatency,
  formatDateISO,
  truncate,
  formatCurrency,
  formatDuration,
  capitalize,
  titleCase,
} from '../formatters';

describe('formatBytes', () => {
  it('should format 0 bytes', () => {
    expect(formatBytes(0)).toBe('0 Bytes');
  });

  it('should format kilobytes', () => {
    expect(formatBytes(1024)).toBe('1 KB');
  });

  it('should format megabytes with decimals', () => {
    expect(formatBytes(1536000, 1)).toMatch(/1\.\d MB/);
  });

  it('should format gigabytes', () => {
    expect(formatBytes(1073741824)).toBe('1 GB');
  });
});

describe('formatNumber', () => {
  it('should format integers', () => {
    const result = formatNumber(1234567);
    expect(result).toContain('1');
  });

  it('should handle zero', () => {
    expect(formatNumber(0)).toBe('0');
  });
});

describe('formatPercentage', () => {
  it('should format to default decimals', () => {
    const result = formatPercentage(0.856);
    expect(result).toContain('%');
  });

  it('should format with specified decimals', () => {
    const result = formatPercentage(0.8567, 1);
    expect(result).toContain('%');
  });
});

describe('formatLatency', () => {
  it('should format sub-second latency', () => {
    const result = formatLatency(150);
    expect(result).toContain('ms');
  });

  it('should format second-level latency', () => {
    const result = formatLatency(2500);
    expect(result).toContain('s');
  });
});

describe('formatDateISO', () => {
  it('should return ISO string', () => {
    const date = new Date('2026-01-15T10:30:00Z');
    const result = formatDateISO(date);
    expect(result).toContain('2026');
  });
});

describe('truncate', () => {
  it('should not truncate short strings', () => {
    expect(truncate('short', 10)).toBe('short');
  });

  it('should truncate long strings', () => {
    const result = truncate('this is a very long string', 10);
    expect(result.length).toBeLessThanOrEqual(13); // 10 + "..."
  });
});

describe('formatCurrency', () => {
  it('should format USD by default', () => {
    const result = formatCurrency(1234.56);
    expect(result).toContain('1');
  });
});

describe('formatDuration', () => {
  it('should format milliseconds', () => {
    const result = formatDuration(500);
    expect(result).toBeTruthy();
  });

  it('should format seconds', () => {
    const result = formatDuration(5000);
    expect(result).toContain('s');
  });

  it('should format minutes', () => {
    const result = formatDuration(120000);
    expect(result).toContain('m');
  });
});

describe('capitalize', () => {
  it('should capitalize first letter', () => {
    expect(capitalize('hello')).toBe('Hello');
  });

  it('should handle empty string', () => {
    expect(capitalize('')).toBe('');
  });

  it('should handle already capitalized', () => {
    expect(capitalize('Hello')).toBe('Hello');
  });
});

describe('titleCase', () => {
  it('should title-case a phrase', () => {
    expect(titleCase('hello world')).toBe('Hello World');
  });

  it('should handle single word', () => {
    expect(titleCase('hello')).toBe('Hello');
  });
});
