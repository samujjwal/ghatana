import { atom } from 'jotai';

/**
 * Simulator state interface.
 */
export interface SimulatorState {
    selectedEventType: string | null;
    selectedSchema: string | null;
    payload: Record<string, unknown>;
    recentTemplates: string[];
    isSimulating: boolean;
    simulationResults: Record<string, unknown> | null;
    showEventPreview: boolean;
    aiSuggestionsVisible: boolean;
}

/**
 * Simulator Jotai store - manages event simulator UI state.
 *
 * <p><b>Purpose</b><br>
 * Centralizes simulator feature state (event selection, payload, templates, results).
 *
 * <p><b>Atoms</b><br>
 * - selectedEventTypeAtom: Currently selected event type
 * - selectedSchemaAtom: Currently selected event schema
 * - payloadAtom: Current event payload being edited
 * - recentTemplatesAtom: Recent event templates
 * - isSimulatingAtom: Simulation in progress
 * - simulationResultsAtom: Results from last simulation
 * - showEventPreviewAtom: Show event preview panel
 * - aiSuggestionsVisibleAtom: Show AI suggestions panel
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * import { useAtom } from 'jotai';
 * import { selectedEventTypeAtom, payloadAtom } from '@/features/simulator/stores/simulator.store';
 *
 * function SimulatorPage() {
 *   const [eventType, setEventType] = useAtom(selectedEventTypeAtom);
 *   const [payload, setPayload] = useAtom(payloadAtom);
 *   // Page logic
 * }
 * ```
 *
 * @doc.type configuration
 * @doc.purpose Simulator state management
 * @doc.layer product
 * @doc.pattern State Store
 */

/**
 * Selected event type atom
 */
export const selectedEventTypeAtom = atom<string | null>(null);

/**
 * Selected schema atom - selected event schema for form generation
 */
export const selectedSchemaAtom = atom<string | null>(null);

/**
 * Payload atom - current event payload being edited
 */
export const payloadAtom = atom<Record<string, unknown>>({});

/**
 * Recent templates atom - recent event templates for quick selection
 */
export const recentTemplatesAtom = atom<string[]>([]);

/**
 * Is simulating atom - simulation/event generation in progress
 */
export const isSimulatingAtom = atom<boolean>(false);

/**
 * Simulation results atom - results from last simulation run
 */
export const simulationResultsAtom = atom<Record<string, unknown> | null>(null);

/**
 * Show event preview atom - show event preview panel
 */
export const showEventPreviewAtom = atom<boolean>(true);

/**
 * AI suggestions visible atom - show AI suggestions panel
 */
export const aiSuggestionsVisibleAtom = atom<boolean>(true);

export default {
    selectedEventTypeAtom,
    selectedSchemaAtom,
    payloadAtom,
    recentTemplatesAtom,
    isSimulatingAtom,
    simulationResultsAtom,
    showEventPreviewAtom,
    aiSuggestionsVisibleAtom,
};
