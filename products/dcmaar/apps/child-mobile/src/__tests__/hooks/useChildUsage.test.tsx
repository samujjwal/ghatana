import { renderHook } from '@testing-library/react-hooks';
import type { Device, Alert } from '@/types';
import { useChildUsage, useChildBlocks } from '@/hooks/useChildUsage';
import { useChildUsageStats } from '@/hooks/useChildUsageStats';
import { useBlockedAttempts } from '@/hooks/useBlockedAttempts';
import { getTodayUsageStats, getRecentBlockEvents } from '@/native/usageTrackerBridge';

const mockDevices: Device[] = [
    {
        id: 'device-api',
        name: "API Device",
        type: 'android',
        status: 'online',
        lastSync: new Date('2024-01-01T10:00:00Z'),
        childName: 'API Child',
    },
];

const mockAlerts: Alert[] = [
    {
        id: 'alert-api',
        type: 'policy_violation',
        deviceId: 'device-api',
        message: 'API block event',
        timestamp: new Date('2024-01-01T12:00:00Z'),
        read: false,
        severity: 'warning',
    },
];

jest.mock('@/hooks/useApi', () => {
    return {
        __esModule: true,
        useDevices: jest.fn(() => ({
            data: mockDevices,
            isLoading: false,
            isError: false,
            error: null,
            refetch: jest.fn(),
        })),
        useAlerts: jest.fn(() => ({
            data: mockAlerts,
            isLoading: false,
            isError: false,
            error: null,
            refetch: jest.fn(),
        })),
    };
});

jest.mock('@/native/usageTrackerBridge', () => {
    return {
        __esModule: true,
        getTodayUsageStats: jest.fn(),
        getRecentBlockEvents: jest.fn(),
    };
});

const mockedGetTodayUsageStats = getTodayUsageStats as jest.Mock;
const mockedGetRecentBlockEvents = getRecentBlockEvents as jest.Mock;

describe('useChildUsage and useChildBlocks', () => {
    it('falls back to API devices when native usage data is unavailable', () => {
        const { result } = renderHook(() => useChildUsage());

        expect(result.current.data).toEqual(mockDevices);
        expect(result.current.isLoading).toBe(false);
    });

    it('falls back to API alerts when native block data is unavailable', () => {
        const { result } = renderHook(() => useChildBlocks());

        expect(result.current.data).toEqual(mockAlerts);
        expect(result.current.isLoading).toBe(false);
    });
});

describe('useChildUsageStats', () => {
    it('returns loading state initially', () => {
        const { result } = renderHook(() => useChildUsageStats());
        expect(result.current.isLoading).toBe(true);
    });

    it('returns usage stats data when available', async () => {
        mockedGetTodayUsageStats.mockResolvedValueOnce({
            totalScreenTimeSeconds: 3600,
            sessionCount: 3,
            deviceIds: ['device1', 'device2'],
        });

        const { result, waitFor } = renderHook(() => useChildUsageStats());
        await waitFor(() => !result.current.isLoading);

        expect(result.current.totalScreenTimeSeconds).toBe(3600);
        expect(result.current.deviceCount).toBe(2);
        expect(result.current.error).toBeNull();
    });

    it('returns error state on failure', async () => {
        mockedGetTodayUsageStats.mockRejectedValueOnce(new Error('fail'));

        const { result, waitFor } = renderHook(() => useChildUsageStats());
        await waitFor(() => !result.current.isLoading);

        expect(result.current.error).toBeInstanceOf(Error);
        expect(result.current.stats).toBeNull();
    });
});

describe('useBlockedAttempts hook', () => {
    it('returns filtered blocked alerts from useChildBlocks', () => {
        const { result } = renderHook(() => useBlockedAttempts());

        expect(result.current.isLoading).toBe(false);
        expect(result.current.data).toHaveLength(1);
        expect(result.current.data[0].id).toBe('alert-api');
    });
});
