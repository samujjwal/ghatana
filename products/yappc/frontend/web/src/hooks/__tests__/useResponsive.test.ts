/**
 * useResponsive Hook Tests
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useResponsive, useMediaQuery, useBreakpoint } from '../useResponsive';

describe('useResponsive', () => {
  beforeEach(() => {
    // Reset window size to desktop
    Object.defineProperty(window, 'innerWidth', { value: 1024, writable: true });
    Object.defineProperty(window, 'innerHeight', { value: 768, writable: true });
    window.dispatchEvent(new Event('resize'));
  });

  it('should return initial state', () => {
    const { result } = renderHook(() => useResponsive());

    expect(result.current.isDesktop).toBe(true);
    expect(result.current.isMobile).toBe(false);
    expect(result.current.isTablet).toBe(false);
    expect(result.current.isLg).toBe(true);
  });

  it('should detect mobile viewport', () => {
    Object.defineProperty(window, 'innerWidth', { value: 375, writable: true });
    window.dispatchEvent(new Event('resize'));

    const { result } = renderHook(() => useResponsive());

    expect(result.current.isMobile).toBe(true);
    expect(result.current.isXs).toBe(true);
  });

  it('should detect tablet viewport', () => {
    Object.defineProperty(window, 'innerWidth', { value: 768, writable: true });
    window.dispatchEvent(new Event('resize'));

    const { result } = renderHook(() => useResponsive());

    expect(result.current.isTablet).toBe(true);
    expect(result.current.isMd).toBe(true);
  });

  it('should detect desktop viewport', () => {
    Object.defineProperty(window, 'innerWidth', { value: 1280, writable: true });
    window.dispatchEvent(new Event('resize'));

    const { result } = renderHook(() => useResponsive());

    expect(result.current.isDesktop).toBe(true);
    expect(result.current.isXl).toBe(true);
  });

  it('should detect orientation', () => {
    Object.defineProperty(window, 'innerWidth', { value: 768, writable: true });
    Object.defineProperty(window, 'innerHeight', { value: 1024, writable: true });
    window.dispatchEvent(new Event('resize'));

    const { result } = renderHook(() => useResponsive());

    expect(result.current.isPortrait).toBe(true);
  });

  it('should detect touch capability', () => {
    Object.defineProperty(navigator, 'maxTouchPoints', { value: 1, writable: true });

    const { result } = renderHook(() => useResponsive());

    expect(result.current.isTouch).toBe(true);
  });
});

describe('useMediaQuery', () => {
  it('should match media query', () => {
    const { result } = renderHook(() => useMediaQuery('(min-width: 768px)'));

    expect(result.current).toBe(true);
  });

  it('should not match media query', () => {
    const { result } = renderHook(() => useMediaQuery('(min-width: 2000px)'));

    expect(result.current).toBe(false);
  });
});

describe('useBreakpoint', () => {
  it('should return true for matched breakpoint', () => {
    Object.defineProperty(window, 'innerWidth', { value: 1024, writable: true });
    window.dispatchEvent(new Event('resize'));

    const { result } = renderHook(() => useBreakpoint('lg'));

    expect(result.current).toBe(true);
  });

  it('should return false for unmatched breakpoint', () => {
    Object.defineProperty(window, 'innerWidth', { value: 375, writable: true });
    window.dispatchEvent(new Event('resize'));

    const { result } = renderHook(() => useBreakpoint('lg'));

    expect(result.current).toBe(false);
  });
});
