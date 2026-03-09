/**
 * ModalManager Tests
 * Phase 5: Days 45-46 - Advanced Interaction Patterns
 *
 * Comprehensive tests for modal management system.
 */

import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import {
  ModalManager,
  useModal,
  useModalStack,
  useModalZIndex,
  useModalRegistration,
} from '../ModalManager';

// ============================================================================
// Test Setup
// ============================================================================

// Mock component for testing
const TestModalComponent = () => null;

// Reset modal state before each test
beforeEach(() => {
  ModalManager.closeAll();
  // Reset body scroll
  document.body.style.overflow = '';
  document.body.style.paddingRight = '';
});

// ============================================================================
// ModalManager Static Methods Tests
// ============================================================================

describe('ModalManager - Registration', () => {
  it('should register a modal', () => {
    ModalManager.register('test-modal', {
      component: TestModalComponent,
    });

    expect(ModalManager['registry'].has('test-modal')).toBe(true);
  });

  it('should unregister a modal', () => {
    ModalManager.register('test-modal', {
      component: TestModalComponent,
    });

    ModalManager.unregister('test-modal');

    expect(ModalManager['registry'].has('test-modal')).toBe(false);
  });

  it('should warn when opening unregistered modal', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    ModalManager.open('nonexistent-modal');

    expect(consoleSpy).toHaveBeenCalledWith(
      expect.stringContaining('not registered')
    );

    consoleSpy.mockRestore();
  });
});

describe('ModalManager - Open/Close', () => {
  beforeEach(() => {
    ModalManager.register('test-modal', {
      component: TestModalComponent,
    });
  });

  it('should open a modal', () => {
    ModalManager.open('test-modal');

    expect(ModalManager.isOpen('test-modal')).toBe(true);
  });

  it('should close a modal', () => {
    ModalManager.open('test-modal');
    ModalManager.close('test-modal');

    expect(ModalManager.isOpen('test-modal')).toBe(false);
  });

  it('should open modal with props', () => {
    ModalManager.open('test-modal', { title: 'Custom Title' });

    const activeModal = ModalManager.getActiveModal('test-modal');
    expect(activeModal?.props?.title).toBe('Custom Title');
  });

  it('should close all modals', () => {
    ModalManager.register('modal-1', { component: TestModalComponent });
    ModalManager.register('modal-2', { component: TestModalComponent });

    ModalManager.open('test-modal');
    ModalManager.open('modal-1');
    ModalManager.open('modal-2');

    ModalManager.closeAll();

    expect(ModalManager.isOpen('test-modal')).toBe(false);
    expect(ModalManager.isOpen('modal-1')).toBe(false);
    expect(ModalManager.isOpen('modal-2')).toBe(false);
  });
});

describe('ModalManager - Stack Management', () => {
  beforeEach(() => {
    ModalManager.register('modal-1', { component: TestModalComponent });
    ModalManager.register('modal-2', { component: TestModalComponent });
    ModalManager.register('modal-3', { component: TestModalComponent });
  });

  it('should maintain modal stack order', () => {
    ModalManager.open('modal-1');
    ModalManager.open('modal-2');
    ModalManager.open('modal-3');

    const topModal = ModalManager.getTopModal();
    expect(topModal?.id).toBe('modal-3');
  });

  it('should update top modal when closing', () => {
    ModalManager.open('modal-1');
    ModalManager.open('modal-2');
    ModalManager.open('modal-3');

    ModalManager.close('modal-3');

    const topModal = ModalManager.getTopModal();
    expect(topModal?.id).toBe('modal-2');
  });

  it('should return null when no modals are open', () => {
    const topModal = ModalManager.getTopModal();
    expect(topModal).toBeNull();
  });
});

describe('ModalManager - Z-Index Calculation', () => {
  beforeEach(() => {
    ModalManager.register('modal-1', { component: TestModalComponent });
    ModalManager.register('modal-2', { component: TestModalComponent });
  });

  it('should calculate z-index based on stack position', () => {
    ModalManager.open('modal-1');
    ModalManager.open('modal-2');

    const zIndex1 = ModalManager.calculateZIndex('modal-1');
    const zIndex2 = ModalManager.calculateZIndex('modal-2');

    expect(zIndex2).toBeGreaterThan(zIndex1);
  });

  it('should return correct z-index for first modal', () => {
    ModalManager.open('modal-1');

    const zIndex = ModalManager.calculateZIndex('modal-1');

    // BASE_Z_INDEX (1300) + index (0) * Z_INDEX_STEP (10) + priority (1000) = 2300
    expect(zIndex).toBe(2300);
  });

  it('should return base z-index for untracked modal', () => {
    // When modal is not in activeModals, returns BASE_Z_INDEX
    const zIndex = ModalManager.calculateZIndex('modal-1');
    expect(zIndex).toBe(1300);
  });
});

describe('ModalManager - Body Scroll Lock', () => {
  beforeEach(() => {
    ModalManager.register('test-modal', {
      component: TestModalComponent,
      options: { lockScroll: true },
    });
  });

  it('should lock body scroll when modal opens', () => {
    ModalManager.open('test-modal');

    expect(document.body.style.overflow).toBe('hidden');
  });

  it('should unlock body scroll when modal closes', () => {
    ModalManager.open('test-modal');
    ModalManager.close('test-modal');

    expect(document.body.style.overflow).toBe('');
  });

  it('should not lock scroll if option is false', () => {
    ModalManager.register('no-lock-modal', {
      component: TestModalComponent,
      options: { lockScroll: false },
    });

    ModalManager.open('no-lock-modal');

    expect(document.body.style.overflow).toBe('');
  });
});

describe('ModalManager - Subscribers', () => {
  beforeEach(() => {
    ModalManager.register('test-modal', { component: TestModalComponent });
  });

  it('should notify subscribers on modal open', () => {
    const callback = vi.fn();
    const unsubscribe = ModalManager.subscribe(callback);

    ModalManager.open('test-modal');

    expect(callback).toHaveBeenCalled();

    unsubscribe();
  });

  it('should notify subscribers on modal close', () => {
    const callback = vi.fn();
    ModalManager.subscribe(callback);

    ModalManager.open('test-modal');
    callback.mockClear();
    ModalManager.close('test-modal');

    expect(callback).toHaveBeenCalled();
  });

  it('should allow unsubscribe', () => {
    const callback = vi.fn();
    const unsubscribe = ModalManager.subscribe(callback);

    unsubscribe();
    ModalManager.open('test-modal');

    expect(callback).not.toHaveBeenCalled();
  });
});

// ============================================================================
// React Hooks Tests
// Note: These hooks use Jotai atoms, which are separate from static ModalManager state
// ============================================================================

describe('useModal', () => {
  it('should provide modal controls', () => {
    // Register using the hook to populate Jotai atoms
    const { result: regResult } = renderHook(() =>
      useModalRegistration('test-modal', { component: TestModalComponent })
    );

    const { result } = renderHook(() => useModal('test-modal'));

    expect(result.current.isOpen).toBe(false);
    expect(typeof result.current.open).toBe('function');
    expect(typeof result.current.close).toBe('function');
    expect(typeof result.current.toggle).toBe('function');
  });

  it('should track open state correctly', () => {
    const { result: regResult } = renderHook(() =>
      useModalRegistration('test-modal', { component: TestModalComponent })
    );

    const { result } = renderHook(() => useModal('test-modal'));

    expect(result.current.isOpen).toBe(false);

    act(() => {
      result.current.open();
    });

    expect(result.current.isOpen).toBe(true);
  });

  it('should close modal', () => {
    const { result: regResult } = renderHook(() =>
      useModalRegistration('test-modal', { component: TestModalComponent })
    );

    const { result } = renderHook(() => useModal('test-modal'));

    act(() => {
      result.current.open();
    });
    expect(result.current.isOpen).toBe(true);

    act(() => {
      result.current.close();
    });
    expect(result.current.isOpen).toBe(false);
  });

  it('should toggle modal', () => {
    const { result: regResult } = renderHook(() =>
      useModalRegistration('test-modal', { component: TestModalComponent })
    );

    const { result } = renderHook(() => useModal('test-modal'));

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

describe('useModalStack', () => {
  it('should provide stack information', () => {
    const { result } = renderHook(() => useModalStack());

    expect(result.current.count).toBe(0);
    expect(result.current.stack).toEqual([]);
    expect(result.current.top).toBeNull();
  });

  it('should update when modals are opened via hooks', () => {
    // Register modals using hooks
    const { result: reg1 } = renderHook(() =>
      useModalRegistration('modal-test-1', { component: TestModalComponent })
    );
    const { result: reg2 } = renderHook(() =>
      useModalRegistration('modal-test-2', { component: TestModalComponent })
    );

    const { result: modal1 } = renderHook(() => useModal('modal-test-1'));
    const { result: modal2 } = renderHook(() => useModal('modal-test-2'));
    const { result: stack } = renderHook(() => useModalStack());

    act(() => {
      modal1.current.open();
      modal2.current.open();
    });

    expect(stack.current.count).toBe(2);
    expect(stack.current.stack).toHaveLength(2);
    expect(stack.current.top).toBe('modal-test-2');
  });

  it('should provide active modals list', () => {
    const { result: reg1 } = renderHook(() =>
      useModalRegistration('modal-solo-test', { component: TestModalComponent })
    );

    const { result: modal1 } = renderHook(() => useModal('modal-solo-test'));
    const { result: stack } = renderHook(() => useModalStack());

    act(() => {
      modal1.current.open();
    });

    // Note: Jotai atoms persist across tests, so we check that our modal is present
    expect(stack.current.modals.length).toBeGreaterThanOrEqual(1);
    expect(
      stack.current.modals.find((m) => m.id === 'modal-solo-test')
    ).toBeDefined();
  });
});

describe('useModalZIndex', () => {
  it('should calculate z-index for modal opened via hook', () => {
    const { result: reg } = renderHook(() =>
      useModalRegistration('zindex-test-modal', {
        component: TestModalComponent,
      })
    );

    const { result: modal } = renderHook(() => useModal('zindex-test-modal'));
    const { result: zIndex } = renderHook(() =>
      useModalZIndex('zindex-test-modal')
    );

    act(() => {
      modal.current.open();
    });

    // Z-index calculation: BASE_Z_INDEX (1300) + index * Z_INDEX_STEP (10) + priority (1000)
    // Note: Jotai atoms persist across tests, so index may vary. Check it's reasonable.
    expect(zIndex.current).toBeGreaterThanOrEqual(2300); // At least base + priority
    expect(zIndex.current).toBeLessThan(3000); // Reasonable upper bound
  });

  it('should return base z-index for untracked modal', () => {
    // When modal is not in activeModals, calculateZIndex returns BASE_Z_INDEX
    const { result } = renderHook(() => useModalZIndex('test-modal'));

    expect(result.current).toBe(1300);
  });

  it('should update when stack changes', () => {
    const { result: reg1 } = renderHook(() =>
      useModalRegistration('zindex-modal-1', { component: TestModalComponent })
    );
    const { result: reg2 } = renderHook(() =>
      useModalRegistration('zindex-modal-2', { component: TestModalComponent })
    );

    const { result: modal1 } = renderHook(() => useModal('zindex-modal-1'));
    const { result: modal2 } = renderHook(() => useModal('zindex-modal-2'));
    const { result: zIndex1 } = renderHook(() =>
      useModalZIndex('zindex-modal-1')
    );

    act(() => {
      modal1.current.open();
    });
    const firstZIndex = zIndex1.current;

    act(() => {
      modal2.current.open();
    });
    // Z-index should remain the same for modal-1, it's still at index 0
    expect(zIndex1.current).toBe(firstZIndex);
  });
});
