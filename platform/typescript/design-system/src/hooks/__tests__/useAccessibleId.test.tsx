import { renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { useAccessibleId } from '../useAccessibleId';

describe('useAccessibleId', () => {
  it('uses the default accessible prefix', () => {
    const { result } = renderHook(() => useAccessibleId());

    expect(result.current.startsWith('gh-ui')).toBe(true);
  });

  it('uses a custom accessible prefix when provided', () => {
    const { result } = renderHook(() => useAccessibleId('dialog'));

    expect(result.current.startsWith('dialog')).toBe(true);
  });
});