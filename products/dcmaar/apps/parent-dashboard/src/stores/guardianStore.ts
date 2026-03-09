/**
 * Guardian State Store
 * 
 * Jotai atoms for Guardian app state management.
 * Provides reactive state for children, devices, policies.
 * 
 * REUSES: services/api.service.ts for data fetching
 * NO DUPLICATION: Single source of truth for Guardian state
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';
import type {
    Child,
    Device,
    Policy,
    ChildRequest,
    AgentSyncPayload,
} from '../services/api.service';

// ============================================================================
// Children State
// ============================================================================

/** List of all children for the current user */
export const childrenAtom = atom<Child[]>([]);

/** Currently selected child (for filtering/detail views) */
export const selectedChildIdAtom = atomWithStorage<string | null>('guardian_selected_child', null);

/** Derived atom: Get selected child object */
export const selectedChildAtom = atom((get) => {
    const children = get(childrenAtom);
    const selectedId = get(selectedChildIdAtom);
    return selectedId ? children.find(c => c.id === selectedId) || null : null;
});

/** Loading state for children */
export const childrenLoadingAtom = atom<boolean>(false);

/** Error state for children */
export const childrenErrorAtom = atom<string | null>(null);

// ============================================================================
// Devices State
// ============================================================================

/** List of all devices for the current user */
export const devicesAtom = atom<Device[]>([]);

/** Currently selected device (for detail views) */
export const selectedDeviceIdAtom = atom<string | null>(null);

/** Derived atom: Get selected device object */
export const selectedDeviceAtom = atom((get) => {
    const devices = get(devicesAtom);
    const selectedId = get(selectedDeviceIdAtom);
    return selectedId ? devices.find(d => d.id === selectedId) || null : null;
});

/** Derived atom: Get devices for selected child */
export const childDevicesAtom = atom((get) => {
    const devices = get(devicesAtom);
    const selectedChildId = get(selectedChildIdAtom);
    if (!selectedChildId) return devices;
    return devices.filter(d => d.child_id === selectedChildId);
});

/** Loading state for devices */
export const devicesLoadingAtom = atom<boolean>(false);

/** Error state for devices */
export const devicesErrorAtom = atom<string | null>(null);

// ============================================================================
// Policies State
// ============================================================================

/** List of all policies for the current user */
export const policiesAtom = atom<Policy[]>([]);

/** Currently selected policy (for editing) */
export const selectedPolicyIdAtom = atom<string | null>(null);

/** Derived atom: Get selected policy object */
export const selectedPolicyAtom = atom((get) => {
    const policies = get(policiesAtom);
    const selectedId = get(selectedPolicyIdAtom);
    return selectedId ? policies.find(p => p.id === selectedId) || null : null;
});

/** Derived atom: Get policies for selected child */
export const childPoliciesAtom = atom((get) => {
    const policies = get(policiesAtom);
    const selectedChildId = get(selectedChildIdAtom);
    if (!selectedChildId) return policies.filter(p => !p.child_id); // Global only
    return policies.filter(p => p.child_id === selectedChildId || !p.child_id);
});

/** Loading state for policies */
export const policiesLoadingAtom = atom<boolean>(false);

/** Error state for policies */
export const policiesErrorAtom = atom<string | null>(null);

// ============================================================================
// Child Requests State
// ============================================================================

/** Pending requests from children */
export const childRequestsAtom = atom<ChildRequest[]>([]);

/** Derived atom: Count of pending requests */
export const pendingRequestsCountAtom = atom((get) => {
    const requests = get(childRequestsAtom);
    return requests.filter(r => r.status === 'pending').length;
});

/** Loading state for requests */
export const requestsLoadingAtom = atom<boolean>(false);

// ============================================================================
// Device Sync State (for monitoring)
// ============================================================================

/** Last sync payload for selected device */
export const deviceSyncAtom = atom<AgentSyncPayload | null>(null);

/** Sync loading state */
export const syncLoadingAtom = atom<boolean>(false);

// ============================================================================
// UI State
// ============================================================================

/** Dashboard view mode */
export type DashboardView = 'overview' | 'children' | 'devices' | 'policies' | 'reports';
export const dashboardViewAtom = atomWithStorage<DashboardView>('guardian_dashboard_view', 'overview');

/** Sidebar collapsed state */
export const sidebarCollapsedAtom = atomWithStorage<boolean>('guardian_sidebar_collapsed', false);

/** Modal state */
export interface ModalState {
    type: 'add_child' | 'edit_child' | 'add_device' | 'add_policy' | 'edit_policy' | 'device_action' | 'request_decision' | null;
    data?: Record<string, unknown>;
}
export const modalAtom = atom<ModalState>({ type: null });

// ============================================================================
// Derived Stats Atoms
// ============================================================================

/** Dashboard stats */
export const dashboardStatsAtom = atom((get) => {
    const children = get(childrenAtom);
    const devices = get(devicesAtom);
    const policies = get(policiesAtom);
    const pendingRequests = get(pendingRequestsCountAtom);

    const activeDevices = devices.filter(d => d.is_active).length;
    const onlineDevices = devices.filter(d => {
        if (!d.last_seen_at) return false;
        const lastSeen = new Date(d.last_seen_at);
        const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000);
        return lastSeen > fiveMinutesAgo;
    }).length;

    return {
        totalChildren: children.length,
        activeChildren: children.filter(c => c.is_active).length,
        totalDevices: devices.length,
        activeDevices,
        onlineDevices,
        totalPolicies: policies.length,
        enabledPolicies: policies.filter(p => p.enabled).length,
        pendingRequests,
    };
});

// ============================================================================
// Action Atoms (for triggering side effects)
// ============================================================================

/** Trigger data refresh */
export const refreshTriggerAtom = atom<number>(0);

/** Increment to trigger refresh */
export const triggerRefreshAtom = atom(
    null,
    (get, set) => {
        set(refreshTriggerAtom, get(refreshTriggerAtom) + 1);
    }
);
