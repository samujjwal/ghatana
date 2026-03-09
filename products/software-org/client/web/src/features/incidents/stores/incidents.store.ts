import { atom } from 'jotai';

/**
 * Incident state interface.
 */
export interface IncidentState {
    selectedIncidentId: string | null;
    filterSeverity: string | null;
    filterStatus: string | null;
    sortBy: 'createdAt' | 'severity' | 'duration';
    selectedAssigneeFilter: string | null;
    expandedIncidentId: string | null;
}

/**
 * Incident Jotai store - manages incident management UI state.
 *
 * <p><b>Purpose</b><br>
 * Centralizes incident feature state (selection, filtering, sorting, detail expansion).
 *
 * <p><b>Atoms</b><br>
 * - selectedIncidentIdAtom: Currently selected incident
 * - filterSeverityAtom: Filter by severity (critical/high/medium/low)
 * - filterStatusAtom: Filter by status (open/investigating/resolved/acknowledged)
 * - sortByAtom: Sort incidents by createdAt/severity/duration
 * - selectedAssigneeFilterAtom: Filter by assigned user
 * - expandedIncidentIdAtom: Incident expanded for detail view
 * - showAssignmentModalAtom: Show assignment modal
 * - selectedActionForIncidentAtom: Action selected in incident context
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * import { useAtom } from 'jotai';
 * import { selectedIncidentIdAtom } from '@/features/incidents/stores/incidents.store';
 *
 * function IncidentsPage() {
 *   const [incident, setIncident] = useAtom(selectedIncidentIdAtom);
 *   // Page logic
 * }
 * ```
 *
 * @doc.type configuration
 * @doc.purpose Incident state management
 * @doc.layer product
 * @doc.pattern State Store
 */

/**
 * Selected incident ID atom
 */
export const selectedIncidentIdAtom = atom<string | null>(null);

/**
 * Filter severity atom - filter incidents by severity level
 */
export const filterSeverityAtom = atom<string | null>(null);

/**
 * Filter status atom - filter incidents by status
 */
export const filterStatusAtom = atom<string | null>(null);

/**
 * Sort by atom - sort order for incident list
 */
export const sortByAtom = atom<'createdAt' | 'severity' | 'duration'>('createdAt');

/**
 * Selected assignee filter atom - filter incidents by assigned user
 */
export const selectedAssigneeFilterAtom = atom<string | null>(null);

/**
 * Expanded incident ID atom - incident expanded for detail view
 */
export const expandedIncidentIdAtom = atom<string | null>(null);

/**
 * Show assignment modal atom - controls assignment modal visibility
 */
export const showAssignmentModalAtom = atom<boolean>(false);

/**
 * Selected action for incident atom - action being contextually shown in incident
 */
export const selectedActionForIncidentAtom = atom<string | null>(null);

export default {
    selectedIncidentIdAtom,
    filterSeverityAtom,
    filterStatusAtom,
    sortByAtom,
    selectedAssigneeFilterAtom,
    expandedIncidentIdAtom,
    showAssignmentModalAtom,
    selectedActionForIncidentAtom,
};
