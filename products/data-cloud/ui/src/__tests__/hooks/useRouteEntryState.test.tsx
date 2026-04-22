import { describe, expect, it } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { z } from 'zod';
import { useRouteEntryState } from '@/hooks/useRouteEntryState';
import { TestWrapper } from '../test-utils/wrapper';

describe('useRouteEntryState', () => {
  const schema = z.object({
    tab: z.enum(['overview', 'details']).default('overview'),
    page: z.coerce.number().default(1),
  });

  const defaults = { tab: 'overview' as const, page: 1 };

  it('returns defaults when no search params present', () => {
    const { result } = renderHook(() => useRouteEntryState(schema, defaults), {
      wrapper: TestWrapper,
    });

    expect(result.current.state).toEqual(defaults);
    expect(result.current.isValid).toBe(true);
    expect(result.current.errors).toBeNull();
  });

  it('parses search params from URL', () => {
    window.history.pushState({}, '', '?tab=details&page=2');

    const { result } = renderHook(() => useRouteEntryState(schema, defaults), {
      wrapper: TestWrapper,
    });

    expect(result.current.state.tab).toBe('details');
    expect(result.current.state.page).toBe(2);
    expect(result.current.isValid).toBe(true);
  });

  it('falls back to defaults on invalid params', () => {
    window.history.pushState({}, '', '?tab=invalid');

    const { result } = renderHook(() => useRouteEntryState(schema, defaults), {
      wrapper: TestWrapper,
    });

    expect(result.current.isValid).toBe(false);
    expect(result.current.state).toEqual(defaults);
    expect(result.current.errors).toBeDefined();
  });

  it('updates search params via setState', () => {
    window.history.pushState({}, '', '');

    const { result } = renderHook(() => useRouteEntryState(schema, defaults), {
      wrapper: TestWrapper,
    });

    act(() => {
      result.current.setState({ tab: 'details' });
    });

    expect(result.current.state.tab).toBe('details');
    expect(window.location.search).toContain('tab=details');
  });

  it('resets to defaults via reset', () => {
    window.history.pushState({}, '', '?tab=details&page=5');

    const { result } = renderHook(() => useRouteEntryState(schema, defaults), {
      wrapper: TestWrapper,
    });

    act(() => {
      result.current.reset();
    });

    expect(result.current.state).toEqual(defaults);
    expect(window.location.search).not.toContain('tab=details');
  });

  it('supports prefixed keys', () => {
    window.history.pushState({}, '', '?filter.status=active&filter.type=all');

    const filterSchema = z.object({
      status: z.string().default('all'),
      type: z.string().default('all'),
    });

    const { result } = renderHook(
      () => useRouteEntryState(filterSchema, { status: 'all', type: 'all' }, { prefix: 'filter' }),
      { wrapper: TestWrapper }
    );

    expect(result.current.state.status).toBe('active');
    expect(result.current.isValid).toBe(true);
  });
});
