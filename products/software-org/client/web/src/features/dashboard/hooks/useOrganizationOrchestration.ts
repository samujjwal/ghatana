/**
 * Organization Dashboard Orchestration Hook
 *
 * Purpose: Custom hook providing organization dashboard orchestration, combining
 * org metadata, health status, KPIs, departments, and real-time events
 * for comprehensive organization monitoring.
 *
 * Usage:
 * ```typescript
 * const {
 *   orgMetadata,
 *   health,
 *   departments,
 *   kpis,
 *   events,
 *   isLoading,
 *   selectDepartment,
 *   isConnected,
 * } = useOrganizationOrchestration(tenantId);
 * ```
 *
 * @doc.type hook
 * @doc.purpose Organization dashboard orchestration and state management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

/* eslint-disable @typescript-eslint/no-explicit-any */
import { useCallback, useState, useMemo } from 'react';
import { useOrgData } from '../../../hooks/useOrgData';
import { useKpis } from '../../../hooks/useKpis';
import { useDepartmentEvents } from '../../../hooks/useDepartmentEvents';

/**
 * Organization orchestration hook interface
 */
export interface UseOrganizationOrchestrationReturn {
    orgMetadata: any | undefined;
    health: any | undefined;
    departments: Array<any> | undefined;
    kpis: any | undefined;
    events: Array<any>;
    isConnected: boolean;
    isLoading: boolean;
    selectedDepartment: string | null;
    selectDepartment: (departmentName: string | null) => void;
}

/**
 * Custom hook for organization dashboard orchestration.
 *
 * @returns Organization orchestration state and methods
 */
export function useOrganizationOrchestration(): UseOrganizationOrchestrationReturn {
    const [selectedDepartment, setSelectedDepartment] = useState<string | null>(null);

    // Use existing hooks for org data, KPIs, and events
    const { orgMetadata, health, departments, isLoading: orgDataLoading } = useOrgData();
    const { kpis } = useKpis();
    const { events, isConnected } = useDepartmentEvents();

    // Combined loading state
    const isLoading = useMemo(() => orgDataLoading, [orgDataLoading]);

    // Handler callbacks
    const selectDepartment = useCallback((departmentName: string | null) => {
        setSelectedDepartment(departmentName);
    }, []);

    return {
        orgMetadata,
        health,
        departments,
        kpis,
        events,
        isConnected,
        isLoading,
        selectedDepartment,
        selectDepartment,
    };
}
