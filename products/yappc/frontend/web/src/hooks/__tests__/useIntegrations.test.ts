/**
 * useIntegrations Hook Tests
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useIntegrations } from '../useIntegrations';
import { resetIntegrations } from '../../services/integrations/IntegrationService';

describe('useIntegrations', () => {
  beforeEach(() => {
    resetIntegrations();
  });

  it('should return empty list initially', () => {
    const { result } = renderHook(() => useIntegrations());
    expect(result.current.integrations).toEqual([]);
  });

  it('should register an integration', () => {
    const { result } = renderHook(() => useIntegrations());

    act(() => {
      result.current.register('GitHub', 'vcs', 'GitHub VCS');
    });

    expect(result.current.integrations.length).toBe(1);
    expect(result.current.integrations[0].name).toBe('GitHub');
    expect(result.current.integrations[0].status).toBe('pending');
  });

  it('should connect an integration', () => {
    const { result } = renderHook(() => useIntegrations());

    let id: string;
    act(() => {
      const created = result.current.register('Slack', 'chat', 'Slack');
      id = created.id;
    });

    act(() => {
      result.current.connect(id!, { webhook: 'https://hook.example' });
    });

    expect(result.current.integrations[0].status).toBe('connected');
    expect(result.current.integrations[0].config.webhook).toBe('https://hook.example');
  });

  it('should disconnect an integration', () => {
    const { result } = renderHook(() => useIntegrations());

    let id: string;
    act(() => {
      const created = result.current.register('Slack', 'chat', 'Slack');
      id = created.id;
      result.current.connect(id, {});
    });

    act(() => {
      result.current.disconnect(id!);
    });

    expect(result.current.integrations[0].status).toBe('disconnected');
  });

  it('should remove an integration', () => {
    const { result } = renderHook(() => useIntegrations());

    let id: string;
    act(() => {
      const created = result.current.register('CI', 'ci-cd', 'CI');
      id = created.id;
    });

    act(() => {
      result.current.remove(id!);
    });

    expect(result.current.integrations.length).toBe(0);
  });

  it('should perform health check', () => {
    const { result } = renderHook(() => useIntegrations());

    act(() => {
      result.current.register('GitHub', 'vcs', 'GitHub');
    });

    const health = result.current.healthCheck(result.current.integrations[0]);
    expect(health.integrationId).toBe(result.current.integrations[0].id);
    expect(health.lastChecked).toBeGreaterThan(0);
  });

  it('should refresh integrations', () => {
    const { result } = renderHook(() => useIntegrations());

    act(() => {
      result.current.register('A', 'vcs', 'A');
    });

    act(() => {
      result.current.refresh();
    });

    expect(result.current.integrations.length).toBe(1);
  });
});
