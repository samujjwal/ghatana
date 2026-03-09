/**
 * Guardian React Hooks
 * 
 * Composable hooks for Guardian data management.
 * Combines API services with Jotai state management.
 * 
 * REUSES: 
 * - services/api.service.ts for data fetching
 * - stores/guardianStore.ts for state management
 * NO DUPLICATION: Single hook per domain, composable
 */

import { useCallback, useEffect } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { api, type Child, type Device, type Policy, type CreateChildInput, type UpdateChildInput, type CreatePolicyInput, type UpdatePolicyInput, type ImmediateAction, type DeviceActionParams, type RequestDecisionInput } from '../services/api.service';
import {
    childrenAtom,
    childrenLoadingAtom,
    childrenErrorAtom,
    selectedChildIdAtom,
    selectedChildAtom,
    devicesAtom,
    devicesLoadingAtom,
    devicesErrorAtom,
    selectedDeviceIdAtom,
    selectedDeviceAtom,
    childDevicesAtom,
    policiesAtom,
    policiesLoadingAtom,
    policiesErrorAtom,
    selectedPolicyIdAtom,
    selectedPolicyAtom,
    childPoliciesAtom,
    childRequestsAtom,
    requestsLoadingAtom,
    pendingRequestsCountAtom,
    deviceSyncAtom,
    syncLoadingAtom,
    dashboardStatsAtom,
    refreshTriggerAtom,
    triggerRefreshAtom,
} from '../stores/guardianStore';

// ============================================================================
// useChildren Hook
// ============================================================================

export function useChildren() {
    const [children, setChildren] = useAtom(childrenAtom);
    const [loading, setLoading] = useAtom(childrenLoadingAtom);
    const [error, setError] = useAtom(childrenErrorAtom);
    const [selectedId, setSelectedId] = useAtom(selectedChildIdAtom);
    const selectedChild = useAtomValue(selectedChildAtom);
    const refreshTrigger = useAtomValue(refreshTriggerAtom);

    const fetchChildren = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await api.children.list();
            setChildren(data);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to fetch children');
        } finally {
            setLoading(false);
        }
    }, [setChildren, setLoading, setError]);

    const createChild = useCallback(async (input: CreateChildInput): Promise<Child> => {
        const child = await api.children.create(input);
        setChildren(prev => [...prev, child]);
        return child;
    }, [setChildren]);

    const updateChild = useCallback(async (id: string, input: UpdateChildInput): Promise<Child> => {
        const child = await api.children.update(id, input);
        setChildren(prev => prev.map(c => c.id === id ? child : c));
        return child;
    }, [setChildren]);

    const deleteChild = useCallback(async (id: string): Promise<void> => {
        await api.children.delete(id);
        setChildren(prev => prev.filter(c => c.id !== id));
        if (selectedId === id) setSelectedId(null);
    }, [setChildren, selectedId, setSelectedId]);

    const selectChild = useCallback((id: string | null) => {
        setSelectedId(id);
    }, [setSelectedId]);

    // Auto-fetch on mount and refresh trigger
    useEffect(() => {
        fetchChildren();
    }, [fetchChildren, refreshTrigger]);

    return {
        children,
        loading,
        error,
        selectedChild,
        selectedId,
        fetchChildren,
        createChild,
        updateChild,
        deleteChild,
        selectChild,
    };
}

// ============================================================================
// useDevices Hook
// ============================================================================

export function useDevices() {
    const [devices, setDevices] = useAtom(devicesAtom);
    const [loading, setLoading] = useAtom(devicesLoadingAtom);
    const [error, setError] = useAtom(devicesErrorAtom);
    const [selectedId, setSelectedId] = useAtom(selectedDeviceIdAtom);
    const selectedDevice = useAtomValue(selectedDeviceAtom);
    const childDevices = useAtomValue(childDevicesAtom);
    const refreshTrigger = useAtomValue(refreshTriggerAtom);

    const fetchDevices = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await api.devices.list();
            setDevices(data);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to fetch devices');
        } finally {
            setLoading(false);
        }
    }, [setDevices, setLoading, setError]);

    const registerDevice = useCallback(async (input: Parameters<typeof api.devices.register>[0]): Promise<Device> => {
        const device = await api.devices.register(input);
        setDevices(prev => [...prev, device]);
        return device;
    }, [setDevices]);

    const deleteDevice = useCallback(async (id: string): Promise<void> => {
        await api.devices.delete(id);
        setDevices(prev => prev.filter(d => d.id !== id));
        if (selectedId === id) setSelectedId(null);
    }, [setDevices, selectedId, setSelectedId]);

    const toggleDevice = useCallback(async (id: string, isActive: boolean): Promise<Device> => {
        const device = await api.devices.toggle(id, isActive);
        setDevices(prev => prev.map(d => d.id === id ? device : d));
        return device;
    }, [setDevices]);

    const pairDevice = useCallback(async (deviceId: string, childId: string): Promise<Device> => {
        const device = await api.devices.pairWithChild(deviceId, childId);
        setDevices(prev => prev.map(d => d.id === deviceId ? device : d));
        return device;
    }, [setDevices]);

    const unpairDevice = useCallback(async (deviceId: string): Promise<Device> => {
        const device = await api.devices.unpair(deviceId);
        setDevices(prev => prev.map(d => d.id === deviceId ? device : d));
        return device;
    }, [setDevices]);

    const sendAction = useCallback(async (
        deviceId: string,
        action: ImmediateAction,
        params?: DeviceActionParams
    ): Promise<{ command_id: string }> => {
        return api.devices.sendAction(deviceId, action, params);
    }, []);

    const selectDevice = useCallback((id: string | null) => {
        setSelectedId(id);
    }, [setSelectedId]);

    // Auto-fetch on mount and refresh trigger
    useEffect(() => {
        fetchDevices();
    }, [fetchDevices, refreshTrigger]);

    return {
        devices,
        childDevices,
        loading,
        error,
        selectedDevice,
        selectedId,
        fetchDevices,
        registerDevice,
        deleteDevice,
        toggleDevice,
        pairDevice,
        unpairDevice,
        sendAction,
        selectDevice,
    };
}

// ============================================================================
// usePolicies Hook
// ============================================================================

export function usePolicies() {
    const [policies, setPolicies] = useAtom(policiesAtom);
    const [loading, setLoading] = useAtom(policiesLoadingAtom);
    const [error, setError] = useAtom(policiesErrorAtom);
    const [selectedId, setSelectedId] = useAtom(selectedPolicyIdAtom);
    const selectedPolicy = useAtomValue(selectedPolicyAtom);
    const childPolicies = useAtomValue(childPoliciesAtom);
    const refreshTrigger = useAtomValue(refreshTriggerAtom);

    const fetchPolicies = useCallback(async (filters?: Parameters<typeof api.policies.list>[0]) => {
        setLoading(true);
        setError(null);
        try {
            const data = await api.policies.list(filters);
            setPolicies(data);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to fetch policies');
        } finally {
            setLoading(false);
        }
    }, [setPolicies, setLoading, setError]);

    const createPolicy = useCallback(async (input: CreatePolicyInput): Promise<Policy> => {
        const policy = await api.policies.create(input);
        setPolicies(prev => [...prev, policy]);
        return policy;
    }, [setPolicies]);

    const updatePolicy = useCallback(async (id: string, input: UpdatePolicyInput): Promise<Policy> => {
        const policy = await api.policies.update(id, input);
        setPolicies(prev => prev.map(p => p.id === id ? policy : p));
        return policy;
    }, [setPolicies]);

    const deletePolicy = useCallback(async (id: string): Promise<void> => {
        await api.policies.delete(id);
        setPolicies(prev => prev.filter(p => p.id !== id));
        if (selectedId === id) setSelectedId(null);
    }, [setPolicies, selectedId, setSelectedId]);

    const togglePolicy = useCallback(async (id: string, enabled: boolean): Promise<Policy> => {
        const policy = await api.policies.toggle(id, enabled);
        setPolicies(prev => prev.map(p => p.id === id ? policy : p));
        return policy;
    }, [setPolicies]);

    const bulkToggle = useCallback(async (policyIds: string[], enabled: boolean): Promise<number> => {
        const { count } = await api.policies.bulkToggle(policyIds, enabled);
        // Refresh to get updated states
        await fetchPolicies();
        return count;
    }, [fetchPolicies]);

    const selectPolicy = useCallback((id: string | null) => {
        setSelectedId(id);
    }, [setSelectedId]);

    // Auto-fetch on mount and refresh trigger
    useEffect(() => {
        fetchPolicies();
    }, [fetchPolicies, refreshTrigger]);

    return {
        policies,
        childPolicies,
        loading,
        error,
        selectedPolicy,
        selectedId,
        fetchPolicies,
        createPolicy,
        updatePolicy,
        deletePolicy,
        togglePolicy,
        bulkToggle,
        selectPolicy,
    };
}

// ============================================================================
// useChildRequests Hook
// ============================================================================

export function useChildRequests(childId?: string) {
    const [requests, setRequests] = useAtom(childRequestsAtom);
    const [loading, setLoading] = useAtom(requestsLoadingAtom);
    const pendingCount = useAtomValue(pendingRequestsCountAtom);
    const selectedChildId = useAtomValue(selectedChildIdAtom);
    const refreshTrigger = useAtomValue(refreshTriggerAtom);

    const targetChildId = childId || selectedChildId;

    const fetchRequests = useCallback(async () => {
        if (!targetChildId) {
            setRequests([]);
            return;
        }
        setLoading(true);
        try {
            const data = await api.children.getRequests(targetChildId);
            setRequests(data);
        } catch {
            setRequests([]);
        } finally {
            setLoading(false);
        }
    }, [targetChildId, setRequests, setLoading]);

    const decideRequest = useCallback(async (
        requestId: string,
        decision: RequestDecisionInput
    ): Promise<void> => {
        if (!targetChildId) throw new Error('No child selected');
        await api.children.decideRequest(targetChildId, requestId, decision);
        // Update local state
        setRequests(prev => prev.map(r =>
            r.id === requestId
                ? { ...r, status: decision.decision, decided_at: new Date().toISOString() }
                : r
        ));
    }, [targetChildId, setRequests]);

    // Auto-fetch when child changes or refresh trigger
    useEffect(() => {
        fetchRequests();
    }, [fetchRequests, refreshTrigger]);

    return {
        requests,
        pendingRequests: requests.filter(r => r.status === 'pending'),
        loading,
        pendingCount,
        fetchRequests,
        decideRequest,
    };
}

// ============================================================================
// useDeviceSync Hook
// ============================================================================

export function useDeviceSync(deviceId?: string) {
    const [syncData, setSyncData] = useAtom(deviceSyncAtom);
    const [loading, setLoading] = useAtom(syncLoadingAtom);
    const selectedDeviceId = useAtomValue(selectedDeviceIdAtom);

    const targetDeviceId = deviceId || selectedDeviceId;

    const fetchSync = useCallback(async () => {
        if (!targetDeviceId) {
            setSyncData(null);
            return;
        }
        setLoading(true);
        try {
            const data = await api.devices.getSync(targetDeviceId);
            setSyncData(data);
        } catch {
            setSyncData(null);
        } finally {
            setLoading(false);
        }
    }, [targetDeviceId, setSyncData, setLoading]);

    return {
        syncData,
        loading,
        fetchSync,
    };
}

// ============================================================================
// useDashboard Hook (combines all stats)
// ============================================================================

export function useDashboard() {
    const stats = useAtomValue(dashboardStatsAtom);
    const triggerRefresh = useSetAtom(triggerRefreshAtom);

    return {
        stats,
        refresh: triggerRefresh,
    };
}

// ============================================================================
// Export all hooks
// ============================================================================

export const hooks = {
    useChildren,
    useDevices,
    usePolicies,
    useChildRequests,
    useDeviceSync,
    useDashboard,
};

export default hooks;
