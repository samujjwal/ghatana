import { useQuery, useMutation } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { useMemo, useCallback } from 'react';
import { useDepartments } from '@/hooks/useDepartments';
import { departmentApi } from '@/services/api/departmentApi';
import { selectedDepartmentIdAtom, sortByAtom, departmentSearchQueryAtom } from '../stores/departments.store';

/**
 * Department Orchestration Hook
 *
 * <p><b>Purpose</b><br>
 * Orchestrates all department-related operations including listing, filtering, sorting,
 * and detail retrieval. Aggregates data from multiple API sources and state management.
 *
 * <p><b>Features</b><br>
 * - Department list with filters and sorting
 * - Department detail retrieval with KPIs
 * - Team member data aggregation
 * - Mutation handlers for CRUD operations
 * - Error handling and loading states
 * - Memoized selectors and handlers
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const {
 *   departments,
 *   selectedDepartment,
 *   filteredDepartments,
 *   selectDepartment,
 *   isLoading,
 * } = useDepartmentOrchestration();
 * ```
 *
 * @doc.type hook
 * @doc.purpose Department orchestration and data aggregation
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useDepartmentOrchestration() {
    const [selectedDeptId, setSelectedDeptId] = useAtom(selectedDepartmentIdAtom);
    const [sortBy] = useAtom(sortByAtom);
    const [searchQuery] = useAtom(departmentSearchQueryAtom);

    // Fetch departments list
    const { data: departments, isLoading, error } = useDepartments({
        enabled: true,
        refetchInterval: 60 * 1000,
    });

    // Fetch selected department details
    const { data: selectedDepartmentDetail, isLoading: isLoadingDetail } = useQuery({
        queryKey: ['department', selectedDeptId],
        queryFn: async () => {
            if (!selectedDeptId) return null;
            try {
                return await departmentApi.getDepartment(selectedDeptId);
            } catch (error) {
                console.warn('[useDepartmentOrchestration] Failed to fetch detail:', error);
                return null;
            }
        },
        enabled: !!selectedDeptId,
        staleTime: 5 * 60 * 1000,
    });

    // Fetch KPIs for selected department
    const { data: departmentKpis, isLoading: isLoadingKpis } = useQuery({
        queryKey: ['department-kpis', selectedDeptId],
        queryFn: async () => {
            if (!selectedDeptId) return null;
            try {
                return await departmentApi.getDepartmentKpis(selectedDeptId);
            } catch (error) {
                console.warn('[useDepartmentOrchestration] Failed to fetch KPIs:', error);
                return null;
            }
        },
        enabled: !!selectedDeptId,
        staleTime: 10 * 60 * 1000,
    });

    // Filter and sort departments
    const filteredDepartments = useMemo(() => {
        if (!departments) return [];
        let filtered = [...departments];

        // Apply search filter
        if (searchQuery) {
            filtered = filtered.filter((d) =>
                d.name.toLowerCase().includes(searchQuery.toLowerCase())
            );
        }

        // Apply sorting
        filtered.sort((a, b) => {
            if (sortBy === 'name') return a.name.localeCompare(b.name);
            if (sortBy === 'velocity')
                return (b.deploymentFrequency ?? 0) - (a.deploymentFrequency ?? 0);
            // Default: sort by health
            const healthScore = { healthy: 3, degraded: 2, unhealthy: 1 };
            return (
                (healthScore[b.health as keyof typeof healthScore] ?? 0) -
                (healthScore[a.health as keyof typeof healthScore] ?? 0)
            );
        });

        return filtered;
    }, [departments, searchQuery, sortBy]);

    // Get selected department from list
    const selectedDepartment = useMemo(() => {
        return departments?.find((d) => d.id === selectedDeptId) || null;
    }, [departments, selectedDeptId]);

    // Mutation for updating department
    const updateDepartmentMutation = useMutation({
        mutationFn: async (data: any) => {
            if (!selectedDeptId) throw new Error('No department selected');
            return await departmentApi.runPlaybook(selectedDeptId, 'update', data);
        },
    });

    // Handler for selecting department
    const handleSelectDepartment = useCallback(
        (deptId: string) => {
            setSelectedDeptId(deptId);
        },
        [setSelectedDeptId]
    );

    // Handler for updating department
    const handleUpdateDepartment = useCallback(
        async (data: any) => {
            try {
                await updateDepartmentMutation.mutateAsync(data);
            } catch (err) {
                console.error('[useDepartmentOrchestration] Update failed:', err);
                throw err;
            }
        },
        [updateDepartmentMutation]
    );

    return {
        // Data
        departments: departments || [],
        filteredDepartments,
        selectedDepartment,
        selectedDepartmentDetail,
        departmentKpis,
        selectedDeptId,

        // State
        isLoading: isLoading || isLoadingDetail,
        isLoadingKpis,
        error,

        // Actions
        selectDepartment: handleSelectDepartment,
        updateDepartment: handleUpdateDepartment,
        isUpdating: updateDepartmentMutation.isPending,
    };
}
