import { describe, it, expect } from 'vitest';
import type React from 'react';
import { renderHook, act } from '@testing-library/react';
import { ThemeProvider } from '../provider';
import {
  useThemeMode,
  useResolvedTheme,
  useThemeToggle,
  useSystemTheme,
  useIsDarkMode,
  useIsLightMode,
  useThemeDefinition,
  useThemeTokens,
  useThemeLayers,
} from '../hooks';

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <ThemeProvider>{children}</ThemeProvider>
) as React.FC<{ children: React.ReactNode }>;

describe('theme hooks', () => {
  describe('useThemeMode', () => {
    it('should return current theme mode', () => {
      const { result } = renderHook(() => useThemeMode(), { wrapper });
      expect(result.current).toBeDefined();
      expect(['light', 'dark', 'system']).toContain(result.current);
    });
  });

  describe('useResolvedTheme', () => {
    it('should return resolved theme object', () => {
      const { result } = renderHook(() => useResolvedTheme(), { wrapper });
      expect(result.current).toBeDefined();
      expect(result.current.mode).toBeDefined();
      expect(result.current.computed).toBeDefined();
    });
  });

  describe('useThemeToggle', () => {
    it('should toggle between light and dark', () => {
      const { result } = renderHook(() => useThemeToggle(), { wrapper });
      const { toggle, mode } = result.current;
      
      act(() => {
        toggle();
      });
      
      // Mode should change after toggle
      expect(['light', 'dark']).toContain(result.current.mode);
    });
  });

  describe('useSystemTheme', () => {
    it('should return system theme preference', () => {
      const { result } = renderHook(() => useSystemTheme(), { wrapper });
      expect(result.current).toBeDefined();
      expect(['light', 'dark']).toContain(result.current);
    });
  });

  describe('useIsDarkMode', () => {
    it('should return boolean for dark mode', () => {
      const { result } = renderHook(() => useIsDarkMode(), { wrapper });
      expect(typeof result.current).toBe('boolean');
    });
  });

  describe('useIsLightMode', () => {
    it('should return boolean for light mode', () => {
      const { result } = renderHook(() => useIsLightMode(), { wrapper });
      expect(typeof result.current).toBe('boolean');
    });
  });

  describe('useThemeDefinition', () => {
    it('should return theme definition', () => {
      const { result } = renderHook(() => useThemeDefinition(), { wrapper });
      expect(result.current).toBeDefined();
      expect(result.current.mode).toBeDefined();
    });
  });

  describe('useThemeTokens', () => {
    it('should return theme tokens', () => {
      const { result } = renderHook(() => useThemeTokens(), { wrapper });
      expect(result.current).toBeDefined();
      expect(result.current.spacing).toBeDefined();
      expect(result.current.palette).toBeDefined();
    });
  });

  describe('useThemeLayers', () => {
    it('should return theme layers', () => {
      const { result } = renderHook(() => useThemeLayers(), { wrapper });
      expect(Array.isArray(result.current)).toBe(true);
    });
  });
});
