import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';

// usePluginMetrics imports `browser` from webextension-polyfill, so we mock it here
const sendMessageMock = vi.fn();

vi.mock('webextension-polyfill', () => {
    return {
        default: {
            runtime: {
                sendMessage: sendMessageMock,
            },
        },
        runtime: {
            sendMessage: sendMessageMock,
        },
    };
});

import { usePluginMetrics } from '../../../ui/hooks/usePluginMetrics';

describe('usePluginMetrics', () => {
    beforeEach(() => {
        vi.useFakeTimers();
        sendMessageMock.mockReset();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('fetches CPU metrics from background via browser.runtime.sendMessage', async () => {
        // First call: CPU metrics (shape expected by usePluginMetrics)
        sendMessageMock.mockResolvedValueOnce({
            ok: true,
            data: {
                pluginId: 'cpu-monitor',
                metrics: {
                    'cpu-monitor': {
                        usage: 42,
                        cores: 4,
                        throttled: false,
                        trend: 'stable',
                    },
                },
            },
        });

        // Subsequent calls for memory/battery can be simple successful responses
        sendMessageMock.mockResolvedValueOnce({ ok: true, data: { pluginId: 'memory-monitor', metrics: {} } } as any);
        sendMessageMock.mockResolvedValueOnce({ ok: true, data: { pluginId: 'battery-monitor', metrics: {} } } as any);

        const { result } = renderHook(() => usePluginMetrics());

        // Advance timers enough for the initial polling tick to fire
        vi.runOnlyPendingTimers();

        await waitFor(() => {
            expect(result.current.metrics.cpu).toBeDefined();
            expect(result.current.metrics.cpu?.usage).toBeCloseTo(42, 5);
        });

        expect(sendMessageMock).toHaveBeenCalled();
    });
});
