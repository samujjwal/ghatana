/**
 * Mock for @tanstack/react-query-devtools
 * Used in tests to avoid dependency resolution issues
 */

import { vi } from 'vitest';

export const ReactQueryDevtools = vi.fn(() => null);
