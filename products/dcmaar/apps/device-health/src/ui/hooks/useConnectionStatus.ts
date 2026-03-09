import { useQuery } from '@tanstack/react-query';
import browser from 'webextension-polyfill';
import type { ConnectionStatus } from '@ghatana/dcmaar-shared-ui-core';
import { formatUptime } from '@ghatana/dcmaar-shared-ui-core';

/**
 * Robust connection status hook.
 * Handles different response shapes from the background script and
 * logs the raw response for easier debugging when things go wrong.
 */
export const useConnectionStatus = () => {
  return useQuery({
    queryKey: ['connectionStatus'],
    queryFn: async (): Promise<ConnectionStatus> => {
      try {
        // Try to send test message to background script
        const response = (await browser.runtime.sendMessage({ type: 'TEST_CONNECTION' })) as any;

        // Debug log so we can inspect remote shapes during testing
        if (typeof console !== 'undefined' && console.debug) {
          console.debug('[useConnectionStatus] TEST_CONNECTION response:', response);
        }

        if (!response) {
          throw new Error('No response from background script');
        }

        // Support multiple possible shapes returned by the background:
        // 1) { success: true, connectedSince, address, latency, metrics }
        // 2) { ok: true, data: { success: true, connectedSince, ... } }
        // 3) { ok: true, data: { connectedSince, address, ... } }
        const payload = response.success === true ? response : response.data ?? response;

        const isConnected = Boolean(payload && (payload.success === true || payload.connectedSince));

        return {
          isConnected,
          lastConnectionTime: isConnected ? new Date().toLocaleString() : 'Never',
          uptime: payload && payload.connectedSince ? formatUptime(payload.connectedSince) : '0s',
          serverAddress: payload?.address ?? 'localhost:9774',
          latency: payload?.latency ?? 0,
          metrics: payload?.metrics ?? undefined,
        } as ConnectionStatus;
      } catch (error) {
        console.error('[useConnectionStatus] Connection status check failed:', error);
        return {
          isConnected: false,
          lastConnectionTime: 'Never',
          uptime: '0s',
          serverAddress: 'Error connecting',
        } as ConnectionStatus;
      }
    },
    refetchInterval: 10000,
    staleTime: 5000,
    retry: 3,
    retryDelay: 1000,
  });
};
