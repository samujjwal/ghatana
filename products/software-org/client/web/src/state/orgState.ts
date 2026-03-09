import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

/**
 * Org-scoped state using Jotai
 */

export interface OrgData {
    tenantId: string;
    tenantName: string;
    organizationId?: string;
    organizationName?: string;
    status: 'active' | 'inactive' | 'suspended';
}

export interface OrgMetadata {
    id: string;
    name: string;
    tenantId: string;
    createdAt: string;
    status: 'active' | 'inactive' | 'suspended';
}

export interface DepartmentStatus {
    name: string;
    health: 'healthy' | 'degraded' | 'unhealthy';
    activeAgents: number;
    pendingTasks: number;
    lastUpdated: string;
}

export interface HealthStatus {
    status: 'UP' | 'DOWN' | 'DEGRADED';
    service: string;
    version: string;
    departments: Record<string, 'UP' | 'DOWN'>;
    timestamp: string;
}

// Core atoms
export const orgDataAtom = atomWithStorage<OrgData | null>('org-data', {
    tenantId: 'default',
    tenantName: 'Default Tenant',
    status: 'active',
});

export const orgMetadataAtom = atom<OrgMetadata | null>(null);

export const departmentsAtom = atom<DepartmentStatus[]>([]);

export const healthAtom = atom<HealthStatus | null>(null);

// Event streams
export const departmentEventsAtom = atom<any[]>([]);

// KPI dashboard
export const kpiDashboardAtom = atom<Record<string, any> | null>(null);

// Persistent storage
export const tenantIdAtom = atomWithStorage<string>('tenantId', 'default');

export const userPreferencesAtom = atomWithStorage<{
    theme: 'light' | 'dark';
    refreshInterval: number;
    autoScroll: boolean;
}>(
    'user-preferences',
    {
        theme: 'light',
        refreshInterval: 30000,
        autoScroll: true,
    }
);

// Derived atoms
export const orgHealthAtom = atom((get) => {
    const health = get(healthAtom);
    return health?.status || 'DOWN';
});

export const activeDepartmentsAtom = atom((get) => {
    const departments = get(departmentsAtom);
    return departments.filter((d) => d.health === 'healthy');
});

export const degradedDepartmentsAtom = atom((get) => {
    const departments = get(departmentsAtom);
    return departments.filter((d) => d.health === 'degraded' || d.health === 'unhealthy');
});
