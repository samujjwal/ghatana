/**
 * UI State Management
 * 
 * Centralized UI state atoms for YAPPC application.
 * Includes sidebar, theme, loading states, and error states.
 * 
 * @doc.type module
 * @doc.purpose UI state management
 * @doc.layer infrastructure
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

// ============================================================================
// SIDEBAR STATE
// ============================================================================

export const sidebarOpenAtom = atomWithStorage('yappc:sidebar-open', true);

export const sidebarWidthAtom = atomWithStorage('yappc:sidebar-width', 280);

// ============================================================================
// THEME STATE
// ============================================================================

export type Theme = 'light' | 'dark' | 'system';

export const themeAtom = atomWithStorage<Theme>('yappc:theme', 'system');

// ============================================================================
// LOADING STATES
// ============================================================================

export interface LoadingState {
  isLoading: boolean;
  message?: string;
}

export interface LoadingStates {
  global: LoadingState;
  [key: string]: LoadingState;
}

export const loadingStatesAtom = atom<LoadingStates>({
  global: { isLoading: false },
});

/**
 * Helper atom to set loading state for a specific key
 */
export const setLoadingAtom = atom(
  null,
  (get, set, update: { key: string; isLoading: boolean; message?: string }) => {
    const current = get(loadingStatesAtom);
    set(loadingStatesAtom, {
      ...current,
      [update.key]: {
        isLoading: update.isLoading,
        message: update.message,
      },
    });
  }
);

/**
 * Helper atom to get loading state for a specific key
 */
export const getLoadingAtom = atom((get) => (key: string): LoadingState => {
  const states = get(loadingStatesAtom);
  return states[key] || { isLoading: false };
});

// ============================================================================
// ERROR STATES
// ============================================================================

export interface ErrorState {
  hasError: boolean;
  message?: string;
  code?: string;
  details?: unknown;
}

export interface ErrorStates {
  global: ErrorState;
  [key: string]: ErrorState;
}

export const errorStatesAtom = atom<ErrorStates>({
  global: { hasError: false },
});

/**
 * Helper atom to set error state for a specific key
 */
export const setErrorAtom = atom(
  null,
  (
    get,
    set,
    update: { key: string; hasError: boolean; message?: string; code?: string; details?: unknown }
  ) => {
    const current = get(errorStatesAtom);
    set(errorStatesAtom, {
      ...current,
      [update.key]: {
        hasError: update.hasError,
        message: update.message,
        code: update.code,
        details: update.details,
      },
    });
  }
);

/**
 * Helper atom to get error state for a specific key
 */
export const getErrorAtom = atom((get) => (key: string): ErrorState => {
  const states = get(errorStatesAtom);
  return states[key] || { hasError: false };
});

/**
 * Helper atom to clear error for a specific key
 */
export const clearErrorAtom = atom(null, (_get, set, key: string) => {
  const current = _get(errorStatesAtom);
  const { [key]: _, ...rest } = current;
  set(errorStatesAtom, { ...rest, global: current.global });
});

// ============================================================================
// MODAL STATE
// ============================================================================

export interface ModalState {
  isOpen: boolean;
  modalId?: string;
  data?: unknown;
}

export const modalStateAtom = atom<ModalState>({
  isOpen: false,
});

export const openModalAtom = atom(
  null,
  (_get, set, update: { modalId: string; data?: unknown }) => {
    set(modalStateAtom, {
      isOpen: true,
      modalId: update.modalId,
      data: update.data,
    });
  }
);

export const closeModalAtom = atom(null, (_get, set) => {
  set(modalStateAtom, { isOpen: false });
});

// ============================================================================
// TOAST/NOTIFICATION STATE
// ============================================================================

export interface Toast {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  message: string;
  duration?: number;
}

export const toastsAtom = atom<Toast[]>([]);

export const addToastAtom = atom(
  null,
  (
    get,
    set,
    toast: Omit<Toast, 'id'> & { id?: string }
  ) => {
    const id = toast.id || `toast-${Date.now()}`;
    const current = get(toastsAtom);
    set(toastsAtom, [...current, { ...toast, id }]);

    // Auto-remove after duration
    const duration = toast.duration || 5000;
    setTimeout(() => {
      const updated = get(toastsAtom).filter((t) => t.id !== id);
      set(toastsAtom, updated);
    }, duration);
  }
);

export const removeToastAtom = atom(null, (get, set, id: string) => {
  const current = get(toastsAtom);
  set(toastsAtom, current.filter((t) => t.id !== id));
});
