/**
 * Dialog Hook
 * Phase 5: Days 45-46 - Advanced Interaction Patterns
 *
 * Programmatic dialog API for showing alerts, confirmations, and custom dialogs.
 * Reuses MUI Dialog components with enhanced functionality.
 *
 * @module interactions/hooks/useDialog
 */

import { useCallback } from 'react';

import { useModal, ModalManager } from '../ModalManager';

import type { DialogOptions, DialogAction, ConfirmResult } from '../types';

// ============================================================================
// Constants
// ============================================================================

const ALERT_MODAL_ID = '__alert__';
const CONFIRM_MODAL_ID = '__confirm__';
const PROMPT_MODAL_ID = '__prompt__';

// ============================================================================
// Dialog Hook
// ============================================================================

/**
 * Hook for programmatic dialog management
 *
 * Provides methods to show alerts, confirmations, and custom dialogs
 * without needing to manage component state.
 *
 * @example
 * ```tsx
 * const { alert, confirm, show, close } = useDialog();
 *
 * // Show alert
 * await alert({ title: 'Success', message: 'Data saved!' });
 *
 * // Show confirmation
 * const result = await confirm({
 *   title: 'Delete Item',
 *   message: 'Are you sure?'
 * });
 * if (result.confirmed) {
 *   deleteItem();
 * }
 *
 * // Show custom dialog
 * show({
 *   title: 'Custom Dialog',
 *   content: <MyCustomContent />,
 *   actions: [
 *     { label: 'Cancel', onClick: close },
 *     { label: 'Save', onClick: handleSave }
 *   ]
 * });
 * ```
 */
export function useDialog() {
  /**
   * Show a custom dialog
   */
  const show = useCallback((options: DialogOptions) => {
    const {
      title,
      message,
      content,
      actions = [],
      closeOnAction = true,
      closeOnBackdrop = false,
      width,
      maxWidth = 'sm',
    } = options;

    // Generate unique ID for this dialog instance
    const dialogId = `__dialog_${Date.now()}`;

    // Create promise that resolves when dialog closes
    return new Promise<number | null>((resolve) => {
      // Register dialog component
      ModalManager.register(dialogId, {
        component: () => null, // Would be actual Dialog component
        defaultProps: {
          title,
          message,
          content,
          actions: actions.map((action, index) => ({
            ...action,
            onClick: async () => {
              await action.onClick();
              if (closeOnAction) {
                ModalManager.close(dialogId);
                resolve(index);
              }
            },
          })),
        },
        options: {
          closeOnBackdrop,
          closeOnEscape: true,
          width,
          maxWidth,
          persistent: !closeOnBackdrop,
        },
      });

      // Open dialog
      ModalManager.open(dialogId);

      // Clean up on close
      const unsubscribe = ModalManager.subscribe((modals) => {
        if (!modals.some((m) => m.id === dialogId)) {
          ModalManager.unregister(dialogId);
          unsubscribe();
          resolve(null);
        }
      });
    });
  }, []);

  /**
   * Show an alert dialog
   * Returns promise that resolves when alert is dismissed
   */
  const alert = useCallback(
    async (options: Omit<DialogOptions, 'actions'>): Promise<void> => {
      await show({
        ...options,
        actions: [
          {
            label: 'OK',
            onClick: () => {},
            variant: 'contained',
            color: 'primary',
            autoFocus: true,
          },
        ],
        closeOnBackdrop: false,
      });
    },
    [show]
  );

  /**
   * Show a confirmation dialog
   * Returns promise that resolves with confirmation result
   */
  const confirm = useCallback(
    async (
      options: Omit<DialogOptions, 'actions'> & {
        confirmLabel?: string;
        cancelLabel?: string;
        confirmColor?: DialogAction['color'];
      }
    ): Promise<ConfirmResult> => {
      const {
        confirmLabel = 'Confirm',
        cancelLabel = 'Cancel',
        confirmColor = 'primary',
        ...dialogOptions
      } = options;

      return new Promise<ConfirmResult>((resolve) => {
        show({
          ...dialogOptions,
          actions: [
            {
              label: cancelLabel,
              onClick: () => {},
              variant: 'text',
            },
            {
              label: confirmLabel,
              onClick: () => {},
              variant: 'contained',
              color: confirmColor,
              autoFocus: true,
            },
          ],
          closeOnAction: true,
          closeOnBackdrop: false,
        }).then((actionIndex) => {
          resolve({
            confirmed: actionIndex === 1,
            actionIndex: actionIndex ?? undefined,
          });
        });
      });
    },
    [show]
  );

  /**
   * Show a destructive confirmation dialog (delete, remove, etc.)
   */
  const confirmDelete = useCallback(
    async (
      options: Omit<DialogOptions, 'actions'> & {
        itemName?: string;
      }
    ): Promise<boolean> => {
      const { itemName, ...dialogOptions } = options;

      const result = await confirm({
        title: dialogOptions.title || 'Confirm Delete',
        message:
          dialogOptions.message ||
          `Are you sure you want to delete ${itemName || 'this item'}? This action cannot be undone.`,
        confirmLabel: 'Delete',
        cancelLabel: 'Cancel',
        confirmColor: 'error',
        ...dialogOptions,
      });

      return result.confirmed;
    },
    [confirm]
  );

  /**
   * Show a prompt dialog (text input)
   * Returns promise that resolves with input value or null if cancelled
   */
  const prompt = useCallback(
    async (
      options: Omit<DialogOptions, 'actions' | 'content'> & {
        defaultValue?: string;
        placeholder?: string;
        confirmLabel?: string;
        cancelLabel?: string;
      }
    ): Promise<string | null> => {
      const {
        defaultValue = '',
        placeholder = '',
        confirmLabel = 'OK',
        cancelLabel = 'Cancel',
        ...dialogOptions
      } = options;

      const inputValue = defaultValue;

      return new Promise<string | null>((resolve) => {
        show({
          ...dialogOptions,
          content: null, // Would be TextField component
          actions: [
            {
              label: cancelLabel,
              onClick: () => {},
              variant: 'text',
            },
            {
              label: confirmLabel,
              onClick: () => {},
              variant: 'contained',
              color: 'primary',
              autoFocus: true,
            },
          ],
        }).then((actionIndex) => {
          resolve(actionIndex === 1 ? inputValue : null);
        });
      });
    },
    [show]
  );

  /**
   * Close active dialog
   */
  const close = useCallback(() => {
    ModalManager.closeTop();
  }, []);

  /**
   * Close all dialogs
   */
  const closeAll = useCallback(() => {
    ModalManager.closeAll();
  }, []);

  return {
    show,
    alert,
    confirm,
    confirmDelete,
    prompt,
    close,
    closeAll,
  };
}

// ============================================================================
// Standalone Functions (without React)
// ============================================================================

/**
 * Show alert dialog (standalone function)
 * Can be called outside React components
 */
export async function showAlert(
  options: Omit<DialogOptions, 'actions'>
): Promise<void> {
  const dialogId = `__alert_${Date.now()}`;

  return new Promise<void>((resolve) => {
    ModalManager.register(dialogId, {
      component: () => null,
      defaultProps: {
        ...options,
        actions: [
          {
            label: 'OK',
            onClick: () => {
              ModalManager.close(dialogId);
              resolve();
            },
            variant: 'contained' as const,
            color: 'primary' as const,
          },
        ],
      },
      options: {
        closeOnBackdrop: false,
        closeOnEscape: true,
      },
    });

    ModalManager.open(dialogId);
  });
}

/**
 * Show confirmation dialog (standalone function)
 * Can be called outside React components
 */
export async function showConfirm(
  options: Omit<DialogOptions, 'actions'> & {
    confirmLabel?: string;
    cancelLabel?: string;
  }
): Promise<boolean> {
  const {
    confirmLabel = 'Confirm',
    cancelLabel = 'Cancel',
    ...dialogOptions
  } = options;

  const dialogId = `__confirm_${Date.now()}`;

  return new Promise<boolean>((resolve) => {
    ModalManager.register(dialogId, {
      component: () => null,
      defaultProps: {
        ...dialogOptions,
        actions: [
          {
            label: cancelLabel,
            onClick: () => {
              ModalManager.close(dialogId);
              resolve(false);
            },
            variant: 'text' as const,
          },
          {
            label: confirmLabel,
            onClick: () => {
              ModalManager.close(dialogId);
              resolve(true);
            },
            variant: 'contained' as const,
            color: 'primary' as const,
          },
        ],
      },
      options: {
        closeOnBackdrop: false,
        closeOnEscape: true,
      },
    });

    ModalManager.open(dialogId);
  });
}

/**
 * Show delete confirmation (standalone function)
 */
export async function showDeleteConfirm(itemName?: string): Promise<boolean> {
  return showConfirm({
    title: 'Confirm Delete',
    message: `Are you sure you want to delete ${itemName || 'this item'}? This action cannot be undone.`,
    confirmLabel: 'Delete',
    cancelLabel: 'Cancel',
  });
}
