/**
 * Modal Manager
 * Phase 5: Days 45-46 - Advanced Interaction Patterns
 *
 * Centralized modal management system using Jotai for state management.
 * Handles modal registration, stacking, focus management, and lifecycle.
 *
 * @module interactions/ModalManager
 */

import { atom, useAtom, useAtomValue, useSetAtom } from 'jotai';
import { useCallback, useEffect } from 'react';

import type { ModalConfig, ModalOptions, ActiveModal } from './types';

// ============================================================================
// Constants
// ============================================================================

const DEFAULT_MODAL_OPTIONS: Required<ModalOptions> = {
  closeOnEscape: true,
  closeOnBackdrop: true,
  persistent: false,
  priority: 1000,
  lockScroll: true,
  trapFocus: true,
  width: 'auto',
  maxWidth: 'sm',
  fullScreen: false,
};

const BASE_Z_INDEX = 1300; // Material-UI modal base z-index
const Z_INDEX_STEP = 10;

// ============================================================================
// Atoms
// ============================================================================

/**
 * Modal registry - stores registered modal configurations
 */
export const modalRegistryAtom = atom<Map<string, ModalConfig>>(new Map());

/**
 * Active modals stack - stores currently open modals
 */
export const activeModalsAtom = atom<ActiveModal[]>([]);

/**
 * Derived atom: Get top modal ID
 */
export const topModalIdAtom = atom((get) => {
  const modals = get(activeModalsAtom);
  return modals.length > 0 ? modals[modals.length - 1].id : null;
});

/**
 * Derived atom: Get modal stack as IDs
 */
export const modalStackAtom = atom((get) => {
  return get(activeModalsAtom).map((m) => m.id);
});

/**
 * Derived atom: Count of active modals
 */
export const modalCountAtom = atom((get) => get(activeModalsAtom).length);

// ============================================================================
// Modal Manager Class
// ============================================================================

/**
 * Static modal management utilities
 * Provides programmatic modal control without React hooks
 */
export class ModalManager {
  private static registry = new Map<string, ModalConfig>();
  private static subscribers = new Set<(modals: ActiveModal[]) => void>();
  private static activeModals: ActiveModal[] = [];

  /**
   * Register a modal configuration
   */
  static register<P = unknown>(
    id: string,
    config: Omit<ModalConfig<P>, 'id'>
  ): void {
    if (this.registry.has(id)) {
      console.warn(`Modal "${id}" is already registered. Overwriting.`);
    }

    this.registry.set(id, {
      id,
      ...config,
    } as ModalConfig);
  }

  /**
   * Unregister a modal
   */
  static unregister(id: string): void {
    this.registry.delete(id);
    this.close(id); // Close if open
  }

  /**
   * Open a modal
   */
  static open(id: string, props?: Record<string, unknown>): void {
    const config = this.registry.get(id);

    if (!config) {
      console.error(
        `Modal "${id}" is not registered. Call ModalManager.register() first.`
      );
      return;
    }

    // Check if already open
    if (this.activeModals.some((m) => m.id === id)) {
      console.warn(`Modal "${id}" is already open.`);
      return;
    }

    // Merge options with defaults
    const options: Required<ModalOptions> = {
      ...DEFAULT_MODAL_OPTIONS,
      ...config.options,
    };

    const activeModal: ActiveModal = {
      id,
      props: { ...config.defaultProps, ...props },
      options,
      openedAt: Date.now(),
    };

    this.activeModals = [...this.activeModals, activeModal];
    this.notifySubscribers();

    // Lock body scroll if needed
    if (options.lockScroll) {
      this.lockBodyScroll();
    }
  }

  /**
   * Close a modal
   */
  static close(id: string): void {
    const index = this.activeModals.findIndex((m) => m.id === id);

    if (index === -1) {
      return; // Not open
    }

    this.activeModals = this.activeModals.filter((m) => m.id !== id);
    this.notifySubscribers();

    // Unlock body scroll if no modals left
    if (this.activeModals.length === 0) {
      this.unlockBodyScroll();
    }
  }

  /**
   * Close top modal (most recent)
   */
  static closeTop(): void {
    if (this.activeModals.length === 0) {
      return;
    }

    const topModal = this.activeModals[this.activeModals.length - 1];
    this.close(topModal.id);
  }

  /**
   * Close all modals
   */
  static closeAll(): void {
    this.activeModals = [];
    this.notifySubscribers();
    this.unlockBodyScroll();
  }

  /**
   * Check if modal is open
   */
  static isOpen(id: string): boolean {
    return this.activeModals.some((m) => m.id === id);
  }

  /**
   * Get active modal by ID
   */
  static getActiveModal(id: string): ActiveModal | null {
    return this.activeModals.find((m) => m.id === id) || null;
  }

  /**
   * Get top modal
   */
  static getTopModal(): ActiveModal | null {
    return this.activeModals.length > 0
      ? this.activeModals[this.activeModals.length - 1]
      : null;
  }

  /**
   * Get all active modals
   */
  static getActiveModals(): ActiveModal[] {
    return [...this.activeModals];
  }

  /**
   * Get modal stack (IDs only)
   */
  static getModalStack(): string[] {
    return this.activeModals.map((m) => m.id);
  }

  /**
   * Calculate z-index for modal
   */
  static calculateZIndex(id: string): number {
    const index = this.activeModals.findIndex((m) => m.id === id);
    if (index === -1) return BASE_Z_INDEX;

    const modal = this.activeModals[index];
    return BASE_Z_INDEX + index * Z_INDEX_STEP + (modal.options.priority || 0);
  }

  /**
   * Subscribe to modal changes
   */
  static subscribe(callback: (modals: ActiveModal[]) => void): () => void {
    this.subscribers.add(callback);
    return () => this.subscribers.delete(callback);
  }

  /**
   * Notify subscribers of changes
   */
  private static notifySubscribers(): void {
    this.subscribers.forEach((callback) => callback(this.activeModals));
  }

  /**
   * Lock body scroll
   */
  private static lockBodyScroll(): void {
    if (typeof document === 'undefined') return;

    const scrollbarWidth =
      window.innerWidth - document.documentElement.clientWidth;
    document.body.style.overflow = 'hidden';
    document.body.style.paddingRight = `${scrollbarWidth}px`;
  }

  /**
   * Unlock body scroll
   */
  private static unlockBodyScroll(): void {
    if (typeof document === 'undefined') return;

    document.body.style.overflow = '';
    document.body.style.paddingRight = '';
  }
}

// ============================================================================
// React Hooks
// ============================================================================

/**
 * Hook to manage a specific modal
 *
 * @example
 * ```tsx
 * const { open, close, isOpen } = useModal('confirmDelete');
 *
 * <Button onClick={() => open({ itemId: '123' })}>Delete</Button>
 * ```
 */
export function useModal(id: string) {
  const [activeModals, setActiveModals] = useAtom(activeModalsAtom);
  const registry = useAtomValue(modalRegistryAtom);

  const isOpen = activeModals.some((m) => m.id === id);

  const open = useCallback(
    (props?: Record<string, unknown>) => {
      const config = registry.get(id);

      if (!config) {
        console.error(`Modal "${id}" is not registered.`);
        return;
      }

      // Check if already open
      if (isOpen) {
        console.warn(`Modal "${id}" is already open.`);
        return;
      }

      const options: Required<ModalOptions> = {
        ...DEFAULT_MODAL_OPTIONS,
        ...config.options,
      };

      const activeModal: ActiveModal = {
        id,
        props: { ...config.defaultProps, ...props },
        options,
        openedAt: Date.now(),
      };

      setActiveModals((prev) => [...prev, activeModal]);
    },
    [id, registry, isOpen, setActiveModals]
  );

  const close = useCallback(() => {
    setActiveModals((prev) => prev.filter((m) => m.id !== id));
  }, [id, setActiveModals]);

  const toggle = useCallback(() => {
    if (isOpen) {
      close();
    } else {
      open();
    }
  }, [isOpen, open, close]);

  return {
    open,
    close,
    toggle,
    isOpen,
  };
}

/**
 * Hook to access modal stack information
 *
 * @example
 * ```tsx
 * const { stack, top, count } = useModalStack();
 * console.log(`${count} modals open, top: ${top}`);
 * ```
 */
export function useModalStack() {
  const activeModals = useAtomValue(activeModalsAtom);
  const stack = activeModals.map((m) => m.id);
  const top = stack.length > 0 ? stack[stack.length - 1] : null;
  const count = stack.length;

  return {
    stack,
    top,
    count,
    modals: activeModals,
  };
}

/**
 * Hook to register a modal on mount
 * Useful for colocating modal registration with component
 *
 * @example
 * ```tsx
 * useModalRegistration('myModal', {
 *   component: MyModalContent,
 *   defaultProps: { title: 'Hello' }
 * });
 * ```
 */
export function useModalRegistration<P = unknown>(
  id: string,
  config: Omit<ModalConfig<P>, 'id'>
) {
  const setRegistry = useSetAtom(modalRegistryAtom);

  useEffect(() => {
    setRegistry((prev) => {
      const next = new Map(prev);
      next.set(id, { id, ...config } as ModalConfig);
      return next;
    });

    return () => {
      setRegistry((prev) => {
        const next = new Map(prev);
        next.delete(id);
        return next;
      });
    };
  }, [id, config, setRegistry]);
}

/**
 * Hook to handle modal keyboard shortcuts
 * Closes top modal on Escape if enabled
 */
export function useModalKeyboard() {
  const [activeModals, setActiveModals] = useAtom(activeModalsAtom);

  useEffect(() => {
    if (activeModals.length === 0) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        const topModal = activeModals[activeModals.length - 1];

        if (topModal.options.closeOnEscape) {
          event.preventDefault();
          setActiveModals((prev) => prev.filter((m) => m.id !== topModal.id));
        }
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [activeModals, setActiveModals]);
}

/**
 * Hook to calculate z-index for a modal
 */
export function useModalZIndex(id: string): number {
  const activeModals = useAtomValue(activeModalsAtom);
  const index = activeModals.findIndex((m) => m.id === id);

  if (index === -1) return BASE_Z_INDEX;

  const modal = activeModals[index];
  return BASE_Z_INDEX + index * Z_INDEX_STEP + (modal.options.priority || 0);
}
