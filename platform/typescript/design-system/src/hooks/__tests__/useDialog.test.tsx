import { renderHook, act, waitFor } from '@testing-library/react';
import { useDialog } from '../useDialog';

describe('useDialog', () => {
  describe('Basic functionality', () => {
    it('should initialize with closed state by default', () => {
      const { result } = renderHook(() => useDialog());
      
      expect(result.current.isOpen).toBe(false);
      expect(result.current.data).toBeUndefined();
      expect(result.current.isLoading).toBe(false);
      expect(result.current.error).toBeNull();
    });

    it('should initialize with open state when defaultOpen is true', () => {
      const { result } = renderHook(() => useDialog({ defaultOpen: true }));
      
      expect(result.current.isOpen).toBe(true);
    });

    it('should open dialog with data', () => {
      const { result } = renderHook(() => useDialog<{ name: string }>());
      
      act(() => {
        result.current.open({ name: 'test' });
      });
      
      expect(result.current.isOpen).toBe(true);
      expect(result.current.data).toEqual({ name: 'test' });
    });

    it('should close dialog', () => {
      const { result } = renderHook(() => useDialog({ defaultOpen: true }));
      
      act(() => {
        result.current.close();
      });
      
      expect(result.current.isOpen).toBe(false);
    });

    it('should toggle dialog state', () => {
      const { result } = renderHook(() => useDialog());
      
      act(() => {
        result.current.toggle();
      });
      expect(result.current.isOpen).toBe(true);
      
      act(() => {
        result.current.toggle();
      });
      expect(result.current.isOpen).toBe(false);
    });
  });

  describe('Async confirm callbacks', () => {
    it('should handle successful confirm callback', async () => {
      const onConfirm = jest.fn().mockResolvedValue('success');
      const { result } = renderHook(() => useDialog({ onConfirm }));
      
      act(() => {
        result.current.open();
      });
      
      await act(async () => {
        await result.current.confirm();
      });
      
      expect(onConfirm).toHaveBeenCalled();
      expect(result.current.isOpen).toBe(false);
      expect(result.current.result).toBe('success');
    });

    it('should handle confirm callback errors', async () => {
      const error = new Error('Confirm failed');
      const onConfirm = jest.fn().mockRejectedValue(error);
      const { result } = renderHook(() => useDialog({ onConfirm }));
      
      act(() => {
        result.current.open();
      });
      
      await act(async () => {
        await result.current.confirm();
      });
      
      expect(result.current.error).toEqual(error);
      expect(result.current.isOpen).toBe(true); // Should stay open on error
    });

    it('should set loading state during confirm', async () => {
      let resolveConfirm: (value: string) => void;
      const onConfirm = jest.fn(() => new Promise<string>((resolve) => {
        resolveConfirm = resolve;
      }));
      
      const { result } = renderHook(() => useDialog({ onConfirm }));
      
      act(() => {
        result.current.open();
      });
      
      const confirmPromise = act(async () => {
        await result.current.confirm();
      });
      
      // Check loading state
      await waitFor(() => {
        expect(result.current.isLoading).toBe(true);
      });
      
      // Resolve the promise
      act(() => {
        resolveConfirm!('done');
      });
      
      await confirmPromise;
      
      expect(result.current.isLoading).toBe(false);
    });
  });

  describe('Cancel callback', () => {
    it('should call onCancel and close dialog', () => {
      const onCancel = jest.fn();
      const { result } = renderHook(() => useDialog({ onCancel, defaultOpen: true }));
      
      act(() => {
        result.current.cancel();
      });
      
      expect(onCancel).toHaveBeenCalled();
      expect(result.current.isOpen).toBe(false);
    });
  });

  describe('Keyboard handling', () => {
    it('should close on Escape key when closeOnEscape is true', () => {
      const { result } = renderHook(() => useDialog({ 
        defaultOpen: true,
        closeOnEscape: true 
      }));
      
      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'Escape' });
        document.dispatchEvent(event);
      });
      
      expect(result.current.isOpen).toBe(false);
    });

    it('should not close on Escape when closeOnEscape is false', () => {
      const { result } = renderHook(() => useDialog({ 
        defaultOpen: true,
        closeOnEscape: false 
      }));
      
      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'Escape' });
        document.dispatchEvent(event);
      });
      
      expect(result.current.isOpen).toBe(true);
    });

    it('should not close on Escape when loading', async () => {
      let resolveConfirm: () => void;
      const onConfirm = jest.fn(() => new Promise<void>((resolve) => {
        resolveConfirm = resolve;
      }));
      
      const { result } = renderHook(() => useDialog({ 
        onConfirm,
        closeOnEscape: true 
      }));
      
      act(() => {
        result.current.open();
      });
      
      // Start confirm (will be loading)
      act(() => {
        result.current.confirm();
      });
      
      await waitFor(() => {
        expect(result.current.isLoading).toBe(true);
      });
      
      // Try to close with Escape
      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'Escape' });
        document.dispatchEvent(event);
      });
      
      expect(result.current.isOpen).toBe(true);
      
      // Cleanup
      act(() => {
        resolveConfirm!();
      });
    });
  });

  describe('Loading state management', () => {
    it('should prevent close while loading when preventCloseWhileLoading is true', async () => {
      let resolveConfirm: () => void;
      const onConfirm = jest.fn(() => new Promise<void>((resolve) => {
        resolveConfirm = resolve;
      }));
      
      const { result } = renderHook(() => useDialog({ 
        onConfirm,
        preventCloseWhileLoading: true 
      }));
      
      act(() => {
        result.current.open();
      });
      
      // Start loading
      act(() => {
        result.current.confirm();
      });
      
      await waitFor(() => {
        expect(result.current.isLoading).toBe(true);
      });
      
      // Try to close
      act(() => {
        result.current.close();
      });
      
      expect(result.current.isOpen).toBe(true);
      
      // Cleanup
      act(() => {
        resolveConfirm!();
      });
    });
  });

  describe('Data management', () => {
    it('should update data with setData', () => {
      const { result } = renderHook(() => useDialog<{ value: number }>());
      
      act(() => {
        result.current.setData({ value: 42 });
      });
      
      expect(result.current.data).toEqual({ value: 42 });
    });

    it('should clear data after close with delay', async () => {
      const { result } = renderHook(() => useDialog<{ value: number }>());
      
      act(() => {
        result.current.open({ value: 42 });
      });
      
      expect(result.current.data).toEqual({ value: 42 });
      
      act(() => {
        result.current.close();
      });
      
      // Data should still be present immediately after close
      expect(result.current.data).toEqual({ value: 42 });
      
      // Wait for cleanup delay (300ms)
      await waitFor(() => {
        expect(result.current.data).toBeUndefined();
      }, { timeout: 500 });
    });

    it('should clear error when opening', () => {
      const onConfirm = jest.fn().mockRejectedValue(new Error('test'));
      const { result } = renderHook(() => useDialog({ onConfirm }));
      
      act(() => {
        result.current.open();
      });
      
      act(async () => {
        await result.current.confirm();
      });
      
      // Should have error
      expect(result.current.error).toBeTruthy();
      
      // Open again
      act(() => {
        result.current.open();
      });
      
      // Error should be cleared
      expect(result.current.error).toBeNull();
    });
  });

  describe('Props generation', () => {
    it('should generate correct props for dialog component', () => {
      const { result } = renderHook(() => useDialog({ defaultOpen: true }));
      
      expect(result.current.props).toEqual({
        isOpen: true,
        onClose: expect.any(Function),
        isLoading: false,
        error: null,
      });
    });

    it('should use noop onClose when closeOnOverlayClick is false', () => {
      const { result } = renderHook(() => useDialog({ 
        defaultOpen: true,
        closeOnOverlayClick: false 
      }));
      
      // Call the onClose from props
      act(() => {
        result.current.props.onClose();
      });
      
      // Should not close
      expect(result.current.isOpen).toBe(true);
    });

    it('should close when closeOnOverlayClick is true', () => {
      const { result } = renderHook(() => useDialog({ 
        defaultOpen: true,
        closeOnOverlayClick: true 
      }));
      
      // Call the onClose from props
      act(() => {
        result.current.props.onClose();
      });
      
      // Should close
      expect(result.current.isOpen).toBe(false);
    });
  });

  describe('Memory cleanup', () => {
    it('should not update state after unmount', async () => {
      const onConfirm = jest.fn().mockResolvedValue('success');
      const { result, unmount } = renderHook(() => useDialog({ onConfirm }));
      
      act(() => {
        result.current.open();
      });
      
      // Start confirm but unmount before it completes
      const confirmPromise = act(async () => {
        await result.current.confirm();
      });
      
      unmount();
      
      // Should not throw error
      await expect(confirmPromise).resolves.not.toThrow();
    });
  });

  describe('Edge cases', () => {
    it('should handle confirm without onConfirm callback', async () => {
      const { result } = renderHook(() => useDialog());
      
      act(() => {
        result.current.open();
      });
      
      await act(async () => {
        await result.current.confirm();
      });
      
      // Should just close
      expect(result.current.isOpen).toBe(false);
    });

    it('should handle non-Error exceptions in confirm', async () => {
      const onConfirm = jest.fn().mockRejectedValue('string error');
      const { result } = renderHook(() => useDialog({ onConfirm }));
      
      act(() => {
        result.current.open();
      });
      
      await act(async () => {
        await result.current.confirm();
      });
      
      expect(result.current.error).toBeInstanceOf(Error);
      expect(result.current.error?.message).toBe('string error');
    });

    it('should handle opening with undefined data', () => {
      const { result } = renderHook(() => useDialog<string>());
      
      act(() => {
        result.current.open(undefined);
      });
      
      expect(result.current.isOpen).toBe(true);
      expect(result.current.data).toBeUndefined();
    });

    it('should handle opening without data parameter', () => {
      const { result } = renderHook(() => useDialog<string>());
      
      act(() => {
        result.current.open();
      });
      
      expect(result.current.isOpen).toBe(true);
      expect(result.current.data).toBeUndefined();
    });
  });
});
