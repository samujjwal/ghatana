import { describe, expect, it, vi } from 'vitest';

import {
  createPluginRuntimeEnvironment,
  createTrustedPluginRuntimePolicy,
  enforceNetworkPolicy,
  type NetworkPolicy,
} from '../PluginRuntimePolicy';

describe('PluginRuntimePolicy', () => {
  const policy: NetworkPolicy = {
    allowNetworkRequests: true,
    allowedDomains: ['api.ghatana.com', '*.api.ghatana.com'],
    blockedDomains: ['blocked.ghatana.com'],
  };

  it('allows exact hosts and explicit wildcard subdomains on the allowlist', () => {
    expect(enforceNetworkPolicy('https://api.ghatana.com/v1/projects', policy)).toEqual({ allowed: true });
    expect(enforceNetworkPolicy('https://assets.api.ghatana.com/logo.svg', policy)).toEqual({ allowed: true });
  });

  it('rejects subdomains when only exact host rules are provided', () => {
    expect(enforceNetworkPolicy('https://assets.api.ghatana.com/logo.svg', {
      allowNetworkRequests: true,
      allowedDomains: ['api.ghatana.com'],
    })).toEqual({
      allowed: false,
      reason: 'Domain not in allowlist',
    });
  });

  it('rejects malicious substring matches', () => {
    expect(enforceNetworkPolicy('https://api.ghatana.com.evil.example/steal', policy)).toEqual({
      allowed: false,
      reason: 'Domain not in allowlist',
    });
  });

  it('rejects blocked domains even when they share the allowed suffix', () => {
    expect(enforceNetworkPolicy('https://blocked.ghatana.com/export', policy)).toEqual({
      allowed: false,
      reason: 'Domain is blocked',
    });
  });

  it('creates a runtime environment that blocks disallowed network access at call time', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response('ok'));
    const runtime = createPluginRuntimeEnvironment({
      pluginId: 'plugin-1',
      version: '1.0.0',
      trusted: false,
      sandboxRequired: true,
      network: policy,
      storage: {
        allowLocalStorage: false,
        allowSessionStorage: false,
        allowIndexedDB: false,
        allowCookies: false,
      },
      browserAPI: {
        allowGeolocation: false,
        allowMediaDevices: false,
        allowClipboard: false,
        allowNotifications: false,
        allowFullscreen: false,
        allowWebSockets: false,
      },
      telemetry: {
        allowTelemetry: false,
      },
    }, {
      fetchImpl: fetchMock,
    });

    await expect(runtime.fetch('https://api.ghatana.com/v1/projects')).resolves.toBeInstanceOf(Response);
    await expect(runtime.fetch('https://evil.example/export')).rejects.toThrow('Plugin network request blocked');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('blocks storage and browser APIs through the runtime boundary', () => {
    const runtime = createPluginRuntimeEnvironment(createTrustedPluginRuntimePolicy('plugin-2', '1.0.0'));

    expect(() => runtime.useStorage('localStorage')).not.toThrow();
    expect(() => runtime.useStorage('indexedDB')).toThrow('Plugin storage access blocked');
    expect(() => runtime.useBrowserAPI('clipboard')).toThrow('Plugin browser API blocked');
  });

  it('enforces telemetry policy before emitting events', () => {
    const telemetryEmitter = vi.fn();
    const runtime = createPluginRuntimeEnvironment({
      pluginId: 'plugin-3',
      version: '1.0.0',
      trusted: false,
      sandboxRequired: true,
      network: {
        allowNetworkRequests: false,
      },
      storage: {
        allowLocalStorage: false,
        allowSessionStorage: false,
        allowIndexedDB: false,
        allowCookies: false,
      },
      browserAPI: {
        allowGeolocation: false,
        allowMediaDevices: false,
        allowClipboard: false,
        allowNotifications: false,
        allowFullscreen: false,
        allowWebSockets: false,
      },
      telemetry: {
        allowTelemetry: true,
        eventAllowlist: ['plugin.loaded'],
        samplingRate: 1,
      },
    }, {
      telemetryEmitter,
    });

    expect(runtime.emitTelemetry('plugin.loaded', { ok: true })).toBe(true);
    expect(runtime.emitTelemetry('plugin.secret-export')).toBe(false);
    expect(telemetryEmitter).toHaveBeenCalledTimes(1);
  });

  it('rejects policy with missing plugin ID before creating any runtime surface', () => {
    expect(() =>
      createPluginRuntimeEnvironment({
        pluginId: '',
        version: '1.0.0',
        trusted: false,
        sandboxRequired: true,
        network: { allowNetworkRequests: false },
        storage: { allowLocalStorage: false, allowSessionStorage: false, allowIndexedDB: false, allowCookies: false },
        browserAPI: { allowGeolocation: false, allowMediaDevices: false, allowClipboard: false, allowNotifications: false, allowFullscreen: false, allowWebSockets: false },
        telemetry: { allowTelemetry: false },
      }),
    ).toThrow(/invalid policy/i);
  });

  it('rejects policy that enables network requests without specifying allowed domains', () => {
    expect(() =>
      createPluginRuntimeEnvironment({
        pluginId: 'plugin-bad-net',
        version: '1.0.0',
        trusted: false,
        sandboxRequired: true,
        network: { allowNetworkRequests: true, allowedDomains: [] },
        storage: { allowLocalStorage: false, allowSessionStorage: false, allowIndexedDB: false, allowCookies: false },
        browserAPI: { allowGeolocation: false, allowMediaDevices: false, allowClipboard: false, allowNotifications: false, allowFullscreen: false, allowWebSockets: false },
        telemetry: { allowTelemetry: false },
      }),
    ).toThrow(/invalid policy/i);
  });
});
