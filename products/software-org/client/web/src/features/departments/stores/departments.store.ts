import { atom } from 'jotai';

/**
 * Department state interface.
 */
export interface DepartmentState {
    selectedDepartmentId: string | null;
    expandedMetrics: string[];
    filterTeamType: string | null;
    sortBy: 'name' | 'health' | 'velocity';
}

/**
 * Department Jotai store - manages department UI state.
 *
 * <p><b>Purpose</b><br>
 * Centralizes department feature state (selection, filtering, sorting, expansion).
 *
 * <p><b>Atoms</b><br>
 * - selectedDepartmentIdAtom: Currently selected department ID
 * - expandedMetricsAtom: Expanded metric cards for drill-down
 * - filterTeamTypeAtom: Filter by team type (engineering, product, etc.)
 * - sortByAtom: Sort departments by name/health/velocity
 * - departmentSearchQueryAtom: Search/filter query
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * import { useAtom } from 'jotai';
 * import { selectedDepartmentIdAtom } from '@/features/departments/stores/departments.store';
 *
 * function DepartmentsPage() {
 *   const [selectedId, setSelectedId] = useAtom(selectedDepartmentIdAtom);
 *   // Page logic
 * }
 * ```
 *
 * @doc.type configuration
 * @doc.purpose Department state management
 * @doc.layer product
 * @doc.pattern State Store
 */

/**
 * Selected department ID atom - persists to localStorage
 */
export const selectedDepartmentIdAtom = atom<string | null>(null);

/**
 * Expanded metrics atom - tracks which department metrics are expanded for detail view
 */
export const expandedMetricsAtom = atom<string[]>([]);

/**
 * Filter team type atom - filter departments by team type
 */
export const filterTeamTypeAtom = atom<string | null>(null);

/**
 * Sort by atom - sort order for department list
 */
export const sortByAtom = atom<'name' | 'health' | 'velocity'>('health');

/**
 * Search query atom - search/filter departments by name
 */
export const departmentSearchQueryAtom = atom<string>('');

export default {
    selectedDepartmentIdAtom,
    expandedMetricsAtom,
    filterTeamTypeAtom,
    sortByAtom,
    departmentSearchQueryAtom,
};
