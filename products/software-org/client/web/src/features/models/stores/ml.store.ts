/**
 * ML Feature Store
 *
 * <p><b>Purpose</b><br>
 * Jotai state management for ML Observatory feature, handling model selection,
 * comparison, and notification state.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const [mlState, setMlState] = useAtom(mlStateAtom);
 * setMlState(prev => ({ ...prev, selectedModelId: modelId }));
 * ```
 *
 * @doc.type service
 * @doc.purpose ML Observatory state management
 * @doc.layer product
 * @doc.pattern State Management
 */

import { atom } from 'jotai';

export interface MLState {
    selectedModelId: string | null;
    compareModelIds: string[];
    notification: {
        type: 'success' | 'error' | 'info';
        message: string;
    } | null;
    lastUpdate: Date | null;
    isLoading: boolean;
}

const initialMLState: MLState = {
    selectedModelId: null,
    compareModelIds: [],
    notification: null,
    lastUpdate: null,
    isLoading: false,
};

/**
 * Base ML state atom.
 * Provides core state for model selection and comparison.
 */
export const mlStateAtom = atom<MLState>(initialMLState);

/**
 * Derived atom - Get selected model ID.
 */
export const selectedModelIdAtom = atom((get) => get(mlStateAtom).selectedModelId);

/**
 * Derived atom - Get models being compared.
 */
export const compareModelIdsAtom = atom((get) => get(mlStateAtom).compareModelIds);

/**
 * Derived atom - Get current notification.
 */
export const mlNotificationAtom = atom((get) => get(mlStateAtom).notification);

/**
 * Derived atom - Check if models are being compared.
 */
export const isComparingAtom = atom(
    (get) => get(mlStateAtom).compareModelIds.length > 0
);

/**
 * Action atom - Set selected model.
 */
export const selectModelAtom = atom(null, (_, set, modelId: string) => {
    set(mlStateAtom, (prev) => ({
        ...prev,
        selectedModelId: modelId,
    }));
});

/**
 * Action atom - Add model to comparison.
 */
export const addToComparisonAtom = atom(null, (_, set, modelId: string) => {
    set(mlStateAtom, (prev) => {
        const compareIds = prev.compareModelIds.includes(modelId)
            ? prev.compareModelIds
            : [...prev.compareModelIds, modelId];
        return { ...prev, compareModelIds: compareIds };
    });
});

/**
 * Action atom - Remove model from comparison.
 */
export const removeFromComparisonAtom = atom(null, (_, set, modelId: string) => {
    set(mlStateAtom, (prev) => ({
        ...prev,
        compareModelIds: prev.compareModelIds.filter((id) => id !== modelId),
    }));
});

/**
 * Action atom - Clear comparison.
 */
export const clearComparisonAtom = atom(null, (_, set) => {
    set(mlStateAtom, (prev) => ({
        ...prev,
        compareModelIds: [],
    }));
});

/**
 * Action atom - Show notification.
 */
export const showMLNotificationAtom = atom(
    null,
    (
        _,
        set,
        notification: { type: 'success' | 'error' | 'info'; message: string }
    ) => {
        set(mlStateAtom, (prev) => ({
            ...prev,
            notification,
        }));
    }
);

/**
 * Action atom - Clear notification.
 */
export const clearMLNotificationAtom = atom(null, (_, set) => {
    set(mlStateAtom, (prev) => ({
        ...prev,
        notification: null,
    }));
});

export default {
    mlStateAtom,
    selectedModelIdAtom,
    compareModelIdsAtom,
    mlNotificationAtom,
    isComparingAtom,
    selectModelAtom,
    addToComparisonAtom,
    removeFromComparisonAtom,
    clearComparisonAtom,
    showMLNotificationAtom,
    clearMLNotificationAtom,
};
