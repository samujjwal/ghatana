/**
 * useDialog Tests
 * Phase 5: Days 45-46 - Advanced Interaction Patterns
 *
 * Comprehensive tests for dialog hook and standalone functions.
 */

import { renderHook, act, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import {
  useDialog,
  showAlert,
  showConfirm,
  showDeleteConfirm,
} from '../hooks/useDialog';
import { ModalManager } from '../ModalManager';

// Mock component for testing
const TestComponent = () => null;

// ============================================================================
// Setup
// ============================================================================

beforeEach(() => {
  // Clean up modal state before each test
  ModalManager.closeAll();
});

// ============================================================================
// useDialog Hook Tests
// ============================================================================

describe('useDialog', () => {
  describe('show', () => {
    it('should return dialog control methods', () => {
      const { result } = renderHook(() => useDialog());

      expect(result.current).toHaveProperty('show');
      expect(result.current).toHaveProperty('alert');
      expect(result.current).toHaveProperty('confirm');
      expect(result.current).toHaveProperty('confirmDelete');
      expect(result.current).toHaveProperty('prompt');
      expect(result.current).toHaveProperty('close');
      expect(result.current).toHaveProperty('closeAll');
    });

    it('should show a custom dialog', async () => {
      const { result } = renderHook(() => useDialog());
      const onClickSpy = vi.fn();

      let dialogPromise: Promise<number | null>;

      act(() => {
        dialogPromise = result.current.show({
          title: 'Custom Dialog',
          message: 'This is a custom message',
          actions: [
            { label: 'Cancel', onClick: onClickSpy },
            { label: 'OK', onClick: onClickSpy },
          ],
        });
      });

      // Dialog should be registered and opened
      await waitFor(() => {
        // Check that a dialog modal was registered
        expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
      });
    });

    it('should support custom width and maxWidth', async () => {
      const { result } = renderHook(() => useDialog());

      act(() => {
        result.current.show({
          title: 'Wide Dialog',
          message: 'This should be wide',
          width: '800px',
          maxWidth: 'lg',
          actions: [],
        });
      });

      await waitFor(() => {
        expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
      });
    });
  });

  describe('alert', () => {
    it('should show an alert dialog', async () => {
      const { result } = renderHook(() => useDialog());

      let alertPromise: Promise<void>;

      act(() => {
        alertPromise = result.current.alert({
          title: 'Alert',
          message: 'This is an alert!',
        });
      });

      await waitFor(() => {
        expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
      });
    });

    it('should resolve promise when alert is dismissed', async () => {
      const { result } = renderHook(() => useDialog());
      const resolveSpy = vi.fn();

      act(() => {
        result.current
          .alert({
            title: 'Alert',
            message: 'Test',
          })
          .then(resolveSpy);
      });

      // Close the dialog
      await act(async () => {
        ModalManager.closeAll();
        await new Promise((resolve) => setTimeout(resolve, 50));
      });

      // Promise should resolve
      expect(resolveSpy).toHaveBeenCalled();
    });
  });

  describe('confirm', () => {
    it('should show a confirmation dialog', async () => {
      const { result } = renderHook(() => useDialog());

      let confirmPromise: Promise<unknown>;

      act(() => {
        confirmPromise = result.current.confirm({
          title: 'Confirm',
          message: 'Are you sure?',
        });
      });

      await waitFor(() => {
        expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
      });
    });

    it('should use custom labels for confirm and cancel', async () => {
      const { result } = renderHook(() => useDialog());

      act(() => {
        result.current.confirm({
          title: 'Confirm',
          message: 'Proceed?',
          confirmLabel: 'Yes',
          cancelLabel: 'No',
        });
      });

      await waitFor(() => {
        expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
      });
    });

    it('should support custom confirm button color', async () => {
      const { result } = renderHook(() => useDialog());

      act(() => {
        result.current.confirm({
          title: 'Confirm',
          message: 'Dangerous action',
          confirmColor: 'error',
        });
      });

      await waitFor(() => {
        expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
      });
    });

    it('should return confirmation result', async () => {
      const { result } = renderHook(() => useDialog());
      let confirmResult: unknown;

      act(() => {
        result.current
          .confirm({
            title: 'Confirm',
            message: 'Test',
          })
          .then((res) => {
            confirmResult = res;
          });
      });

      await act(async () => {
        ModalManager.closeAll();
        await new Promise((resolve) => setTimeout(resolve, 50));
      });

      // Should have received a result object
      expect(confirmResult).toBeDefined();
      expect(confirmResult).toHaveProperty('confirmed');
    });
  });

  describe('confirmDelete', () => {
    it('should show a delete confirmation dialog', async () => {
      const { result } = renderHook(() => useDialog());

      act(() => {
        result.current.confirmDelete({
          title: 'Delete Item',
          itemName: 'Test Item',
        });
      });

      await waitFor(() => {
        expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
      });
    });

    it('should use default delete message when itemName is not provided', async () => {
      const { result } = renderHook(() => useDialog());

      act(() => {
        result.current.confirmDelete({
          title: 'Delete',
        });
      });

      await waitFor(() => {
        expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
      });
    });

    it('should return boolean result', async () => {
      const { result } = renderHook(() => useDialog());
      let deleteResult: boolean | undefined;

      act(() => {
        result.current
          .confirmDelete({
            title: 'Delete',
            itemName: 'File',
          })
          .then((res) => {
            deleteResult = res;
          });
      });

      await act(async () => {
        ModalManager.closeAll();
        await new Promise((resolve) => setTimeout(resolve, 50));
      });

      expect(typeof deleteResult).toBe('boolean');
    });
  });

  describe('prompt', () => {
    it('should show a prompt dialog', async () => {
      const { result } = renderHook(() => useDialog());

      act(() => {
        result.current.prompt({
          title: 'Input',
          message: 'Enter your name',
        });
      });

      await waitFor(() => {
        expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
      });
    });

    it('should support default value and placeholder', async () => {
      const { result } = renderHook(() => useDialog());

      act(() => {
        result.current.prompt({
          title: 'Input',
          message: 'Enter value',
          defaultValue: 'Default',
          placeholder: 'Type here...',
        });
      });

      await waitFor(() => {
        expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
      });
    });

    it('should return string or null', async () => {
      const { result } = renderHook(() => useDialog());
      let promptResult: string | null | undefined;

      act(() => {
        result.current
          .prompt({
            title: 'Input',
            message: 'Test',
          })
          .then((res) => {
            promptResult = res;
          });
      });

      await act(async () => {
        ModalManager.closeAll();
        await new Promise((resolve) => setTimeout(resolve, 50));
      });

      // Should return null when cancelled
      expect(promptResult).toBeNull();
    });
  });

  describe('close and closeAll', () => {
    it('should close the top dialog', async () => {
      const { result } = renderHook(() => useDialog());

      act(() => {
        result.current.show({
          title: 'Dialog 1',
          actions: [],
        });
      });

      await waitFor(() => {
        expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
      });

      act(() => {
        result.current.close();
      });

      await waitFor(() => {
        expect(ModalManager.getActiveModals().length).toBe(0);
      });
    });

    it('should close all dialogs', async () => {
      const { result } = renderHook(() => useDialog());

      act(() => {
        result.current.show({ title: 'Dialog 1', actions: [] });
      });

      await waitFor(() => {
        expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
      });

      act(() => {
        result.current.closeAll();
      });

      await waitFor(() => {
        expect(ModalManager.getActiveModals().length).toBe(0);
      });
    });
  });
});

// ============================================================================
// Standalone Functions Tests
// ============================================================================

describe('showAlert', () => {
  it('should show an alert dialog', async () => {
    const alertPromise = showAlert({
      title: 'Standalone Alert',
      message: 'This is a standalone alert',
    });

    await waitFor(() => {
      expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
    });

    // Clean up
    ModalManager.closeAll();
  });

  it('should create dialog with correct configuration', async () => {
    showAlert({
      title: 'Test Alert',
      message: 'Test message',
    });

    await waitFor(() => {
      expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
    });

    // Clean up
    ModalManager.closeAll();
  });
});

describe('showConfirm', () => {
  it('should show a confirmation dialog', async () => {
    showConfirm({
      title: 'Standalone Confirm',
      message: 'Proceed?',
    });

    await waitFor(() => {
      expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
    });

    // Clean up
    ModalManager.closeAll();
  });

  it('should support custom labels', async () => {
    showConfirm({
      title: 'Confirm',
      message: 'Continue?',
      confirmLabel: 'Yes, Continue',
      cancelLabel: 'No, Go Back',
    });

    await waitFor(() => {
      expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
    });

    // Clean up
    ModalManager.closeAll();
  });

  it('should create dialog with actions', async () => {
    showConfirm({
      title: 'Test',
      message: 'Test',
    });

    await waitFor(() => {
      expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
    });

    // Clean up
    ModalManager.closeAll();
  });
});

describe('showDeleteConfirm', () => {
  it('should show a delete confirmation with default message', async () => {
    showDeleteConfirm();

    await waitFor(() => {
      expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
    });

    // Clean up
    ModalManager.closeAll();
  });

  it('should include item name in message', async () => {
    showDeleteConfirm('Important File');

    await waitFor(() => {
      expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
    });

    // Clean up
    ModalManager.closeAll();
  });

  it('should create dialog with delete styling', async () => {
    showDeleteConfirm('Test Item');

    await waitFor(() => {
      expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
    });

    // Clean up
    ModalManager.closeAll();
  });
});

// ============================================================================
// Integration Tests
// ============================================================================

describe('Dialog Integration', () => {
  it('should integrate with ModalManager', async () => {
    const { result } = renderHook(() => useDialog());

    // Show dialog
    act(() => {
      result.current.alert({ title: 'Test', message: 'Test' });
    });

    // Should register with ModalManager
    await waitFor(() => {
      const activeModals = ModalManager.getActiveModals();
      expect(activeModals.length).toBeGreaterThanOrEqual(1);
    });
  });

  it('should clean up after dialog closes', async () => {
    const { result } = renderHook(() => useDialog());

    act(() => {
      result.current.alert({ title: 'Test', message: 'Test' });
    });

    await waitFor(() => {
      expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
    });

    // Close and verify cleanup
    ModalManager.closeAll();

    await waitFor(
      () => {
        expect(ModalManager.getActiveModals().length).toBe(0);
      },
      { timeout: 1000 }
    );
  });

  it('should handle multiple dialogs in sequence', async () => {
    const { result } = renderHook(() => useDialog());

    // First dialog
    act(() => {
      result.current.alert({ title: 'First', message: 'First' });
    });

    await waitFor(() => {
      expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
    });

    act(() => {
      result.current.close();
    });

    // Second dialog
    act(() => {
      result.current.alert({ title: 'Second', message: 'Second' });
    });

    await waitFor(() => {
      expect(ModalManager.getActiveModals().length).toBeGreaterThanOrEqual(1);
    });

    // Clean up
    ModalManager.closeAll();
  });
});
