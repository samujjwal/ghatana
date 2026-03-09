import { NativeModules } from 'react-native';

jest.mock('react-native', () => ({
    NativeModules: {
        RNUsageModule: {
            getUsageOverview: jest.fn(),
        },
        RNBlockModule: {
            getBlockEvents: jest.fn(),
        },
    },
}));

import { getNativeUsageOverview, getNativeBlockEvents } from '@/services/nativeUsageBridge';

const mockedUsageModule = NativeModules.RNUsageModule as {
    getUsageOverview: jest.Mock;
};

const mockedBlockModule = NativeModules.RNBlockModule as {
    getBlockEvents: jest.Mock;
};

describe('nativeUsageBridge - getNativeUsageOverview', () => {
    beforeEach(() => {
        mockedUsageModule.getUsageOverview.mockReset();
    });

    it('returns native sessions when a non-empty array is returned', async () => {
        const nativeSessions = [
            {
                id: 's1',
                deviceId: 'device-1',
                childId: 'child-1',
                itemName: 'App A',
                durationSeconds: 120,
                timestamp: '2024-01-01T10:00:00Z',
            },
        ];

        mockedUsageModule.getUsageOverview.mockResolvedValueOnce(nativeSessions);

        const result = await getNativeUsageOverview();

        expect(result).toEqual(nativeSessions);
    });

    it('returns undefined when native returns an empty array', async () => {
        mockedUsageModule.getUsageOverview.mockResolvedValueOnce([]);

        const result = await getNativeUsageOverview();

        expect(result).toBeUndefined();
    });

    it('returns undefined when native throws', async () => {
        mockedUsageModule.getUsageOverview.mockRejectedValueOnce(new Error('fail'));

        const result = await getNativeUsageOverview();

        expect(result).toBeUndefined();
    });
});

describe('nativeUsageBridge - getNativeBlockEvents', () => {
    beforeEach(() => {
        mockedBlockModule.getBlockEvents.mockReset();
    });

    it('returns native events when a non-empty array is returned', async () => {
        const nativeEvents = [
            {
                id: 'e1',
                deviceId: 'device-1',
                childId: 'child-1',
                reason: 'Blocked app',
                timestamp: '2024-01-01T12:00:00Z',
            },
        ];

        mockedBlockModule.getBlockEvents.mockResolvedValueOnce(nativeEvents);

        const result = await getNativeBlockEvents();

        expect(result).toEqual(nativeEvents);
    });

    it('returns undefined when native returns an empty array', async () => {
        mockedBlockModule.getBlockEvents.mockResolvedValueOnce([]);

        const result = await getNativeBlockEvents();

        expect(result).toBeUndefined();
    });

    it('returns undefined when native throws', async () => {
        mockedBlockModule.getBlockEvents.mockRejectedValueOnce(new Error('fail'));

        const result = await getNativeBlockEvents();

        expect(result).toBeUndefined();
    });
});
