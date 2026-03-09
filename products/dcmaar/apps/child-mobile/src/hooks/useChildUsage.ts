import { useEffect, useState } from 'react';
import type { Device, Alert } from '@/types';
import { useDevices, useAlerts } from './useApi';
import { getNativeUsageOverview, getNativeBlockEvents } from '../services/nativeUsageBridge';
import { mapNativeUsageToDevices, mapNativeBlocksToAlerts } from '../services/nativeUsageMapper';

// Placeholder child hooks; will be redirected to native usage/blocks modules later.
// For now, they use native data when available and fall back to the existing API hooks
// so screens can be wired consistently.

export const useChildUsage = () => {
    const devicesQuery = useDevices();
    const [nativeDevices, setNativeDevices] = useState<Device[] | null>(null);
    const [nativeLoading, setNativeLoading] = useState(false);

    useEffect(() => {
        let isMounted = true;
        setNativeLoading(true);
        void getNativeUsageOverview()
            .then((sessions) => {
                if (!isMounted || !sessions) return;
                setNativeDevices(mapNativeUsageToDevices(sessions));
            })
            .finally(() => {
                if (isMounted) {
                    setNativeLoading(false);
                }
            });

        return () => {
            isMounted = false;
        };
    }, []);

    const data = nativeDevices ?? devicesQuery.data;
    const isLoading = devicesQuery.isLoading || nativeLoading;

    return {
        ...devicesQuery,
        data,
        isLoading,
    };
};

export const useChildBlocks = () => {
    const alertsQuery = useAlerts();
    const [nativeAlerts, setNativeAlerts] = useState<Alert[] | null>(null);
    const [nativeLoading, setNativeLoading] = useState(false);

    useEffect(() => {
        let isMounted = true;
        setNativeLoading(true);
        void getNativeBlockEvents()
            .then((events) => {
                if (!isMounted || !events) return;
                setNativeAlerts(mapNativeBlocksToAlerts(events));
            })
            .finally(() => {
                if (isMounted) {
                    setNativeLoading(false);
                }
            });

        return () => {
            isMounted = false;
        };
    }, []);

    const data = nativeAlerts ?? alertsQuery.data;
    const isLoading = alertsQuery.isLoading || nativeLoading;

    return {
        ...alertsQuery,
        data,
        isLoading,
    };
};
