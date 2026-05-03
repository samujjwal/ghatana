/**
 * usePhaseFeatureGate Hook Tests
 *
 * Tests for phase feature gating functionality.
 *
 * @doc.type test
 * @doc.purpose Verify phase feature gate behavior
 * @doc.layer product
 */

import { renderHook } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { usePhaseFeatureGate } from '../usePhaseFeatureGate';

describe('usePhaseFeatureGate', () => {
  it('should return isPhaseEnabled function', () => {
    const { result } = renderHook(() => usePhaseFeatureGate());
    expect(typeof result.current.isPhaseEnabled).toBe('function');
  });

  it('should return getEnabledPhases function', () => {
    const { result } = renderHook(() => usePhaseFeatureGate());
    expect(typeof result.current.getEnabledPhases).toBe('function');
  });

  it('should enable core phases by default', () => {
    const { result } = renderHook(() => usePhaseFeatureGate());
    
    expect(result.current.isPhaseEnabled('intent')).toBe(true);
    expect(result.current.isPhaseEnabled('shape')).toBe(true);
    expect(result.current.isPhaseEnabled('validate')).toBe(true);
    expect(result.current.isPhaseEnabled('generate')).toBe(true);
    expect(result.current.isPhaseEnabled('run')).toBe(true);
    expect(result.current.isPhaseEnabled('evolve')).toBe(true);
  });

  it('should disable observe phase by default', () => {
    const { result } = renderHook(() => usePhaseFeatureGate());
    expect(result.current.isPhaseEnabled('observe')).toBe(false);
  });

  it('should disable learn phase by default', () => {
    const { result } = renderHook(() => usePhaseFeatureGate());
    expect(result.current.isPhaseEnabled('learn')).toBe(false);
  });

  it('should return only enabled phases from getEnabledPhases', () => {
    const { result } = renderHook(() => usePhaseFeatureGate());
    const enabledPhases = result.current.getEnabledPhases();
    
    expect(enabledPhases).toHaveLength(6);
    expect(enabledPhases).toContain('intent');
    expect(enabledPhases).toContain('shape');
    expect(enabledPhases).toContain('validate');
    expect(enabledPhases).toContain('generate');
    expect(enabledPhases).toContain('run');
    expect(enabledPhases).toContain('evolve');
    expect(enabledPhases).not.toContain('observe');
    expect(enabledPhases).not.toContain('learn');
  });
});
