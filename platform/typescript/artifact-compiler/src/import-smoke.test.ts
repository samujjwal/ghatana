import { describe, it, expect } from 'vitest';
import { DEFAULT_SCANNER_CONFIG } from './inventory/scanner';

describe('import smoke', () => {
  it('should import scanner', () => {
    expect(DEFAULT_SCANNER_CONFIG.maxFileSizeBytes).toBeGreaterThan(0);
  });
});
