/**
 * Tests for useSelection hook
 */

import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useSelection, useRangeSelection } from '../useSelection';

describe('useSelection', () => {
  it('starts with empty selection', () => {
    const items = [
      { id: '1', name: 'Item 1' },
      { id: '2', name: 'Item 2' },
    ];

    const { result } = renderHook(() =>
      useSelection({
        items,
        keyFn: (item) => item.id,
      })
    );

    expect(result.current.selectedIds.size).toBe(0);
    expect(result.current.selectedCount).toBe(0);
  });

  it('toggles item selection', () => {
    const items = [
      { id: '1', name: 'Item 1' },
      { id: '2', name: 'Item 2' },
    ];

    const { result } = renderHook(() =>
      useSelection({
        items,
        keyFn: (item) => item.id,
      })
    );

    act(() => {
      result.current.toggle('1');
    });

    expect(result.current.selectedIds.has('1')).toBe(true);
    expect(result.current.selectedCount).toBe(1);

    act(() => {
      result.current.toggle('1');
    });

    expect(result.current.selectedIds.has('1')).toBe(false);
    expect(result.current.selectedCount).toBe(0);
  });

  it('selects multiple items in multiple selection mode', () => {
    const items = [
      { id: '1', name: 'Item 1' },
      { id: '2', name: 'Item 2' },
      { id: '3', name: 'Item 3' },
    ];

    const { result } = renderHook(() =>
      useSelection({
        items,
        keyFn: (item) => item.id,
        allowMultiple: true,
      })
    );

    act(() => {
      result.current.toggle('1');
      result.current.toggle('2');
    });

    expect(result.current.selectedIds.has('1')).toBe(true);
    expect(result.current.selectedIds.has('2')).toBe(true);
    expect(result.current.selectedCount).toBe(2);
  });

  it('selects only one item in single selection mode', () => {
    const items = [
      { id: '1', name: 'Item 1' },
      { id: '2', name: 'Item 2' },
      { id: '3', name: 'Item 3' },
    ];

    const { result } = renderHook(() =>
      useSelection({
        items,
        keyFn: (item) => item.id,
        allowMultiple: false,
      })
    );

    act(() => {
      result.current.toggle('1');
      result.current.toggle('2');
    });

    expect(result.current.selectedIds.has('1')).toBe(false);
    expect(result.current.selectedIds.has('2')).toBe(true);
    expect(result.current.selectedCount).toBe(1);
  });

  it('selects all items', () => {
    const items = [
      { id: '1', name: 'Item 1' },
      { id: '2', name: 'Item 2' },
      { id: '3', name: 'Item 3' },
    ];

    const { result } = renderHook(() =>
      useSelection({
        items,
        keyFn: (item) => item.id,
      })
    );

    act(() => {
      result.current.selectAll();
    });

    expect(result.current.selectedIds.size).toBe(3);
    expect(result.current.isAllSelected).toBe(true);
  });

  it('deselects all items', () => {
    const items = [
      { id: '1', name: 'Item 1' },
      { id: '2', name: 'Item 2' },
    ];

    const { result } = renderHook(() =>
      useSelection({
        items,
        keyFn: (item) => item.id,
        initialSelectedIds: new Set(['1', '2']),
      })
    );

    expect(result.current.selectedIds.size).toBe(2);

    act(() => {
      result.current.deselectAll();
    });

    expect(result.current.selectedIds.size).toBe(0);
  });

  it('toggles all items', () => {
    const items = [
      { id: '1', name: 'Item 1' },
      { id: '2', name: 'Item 2' },
      { id: '3', name: 'Item 3' },
    ];

    const { result } = renderHook(() =>
      useSelection({
        items,
        keyFn: (item) => item.id,
      })
    );

    act(() => {
      result.current.toggleAll();
    });

    expect(result.current.selectedIds.size).toBe(3);

    act(() => {
      result.current.toggleAll();
    });

    expect(result.current.selectedIds.size).toBe(0);
  });

  it('returns selected items', () => {
    const items = [
      { id: '1', name: 'Item 1' },
      { id: '2', name: 'Item 2' },
      { id: '3', name: 'Item 3' },
    ];

    const { result } = renderHook(() =>
      useSelection({
        items,
        keyFn: (item) => item.id,
      })
    );

    act(() => {
      result.current.select('1');
      result.current.select('3');
    });

    expect(result.current.selectedItems).toHaveLength(2);
    expect(result.current.selectedItems[0].id).toBe('1');
    expect(result.current.selectedItems[1].id).toBe('3');
  });

  it('isAllSelected is true when all items selected', () => {
    const items = [
      { id: '1', name: 'Item 1' },
      { id: '2', name: 'Item 2' },
    ];

    const { result } = renderHook(() =>
      useSelection({
        items,
        keyFn: (item) => item.id,
      })
    );

    expect(result.current.isAllSelected).toBe(false);

    act(() => {
      result.current.selectAll();
    });

    expect(result.current.isAllSelected).toBe(true);
  });

  it('isIndeterminate is true when some items selected', () => {
    const items = [
      { id: '1', name: 'Item 1' },
      { id: '2', name: 'Item 2' },
      { id: '3', name: 'Item 3' },
    ];

    const { result } = renderHook(() =>
      useSelection({
        items,
        keyFn: (item) => item.id,
      })
    );

    act(() => {
      result.current.select('1');
    });

    expect(result.current.isIndeterminate).toBe(true);
    expect(result.current.isAllSelected).toBe(false);
  });

  it('isIndeterminate is false when no items selected', () => {
    const items = [
      { id: '1', name: 'Item 1' },
      { id: '2', name: 'Item 2' },
    ];

    const { result } = renderHook(() =>
      useSelection({
        items,
        keyFn: (item) => item.id,
      })
    );

    expect(result.current.isIndeterminate).toBe(false);
  });

  it('isIndeterminate is false when all items selected', () => {
    const items = [
      { id: '1', name: 'Item 1' },
      { id: '2', name: 'Item 2' },
    ];

    const { result } = renderHook(() =>
      useSelection({
        items,
        keyFn: (item) => item.id,
      })
    );

    act(() => {
      result.current.selectAll();
    });

    expect(result.current.isIndeterminate).toBe(false);
  });
});

describe('useRangeSelection', () => {
  it('selects range on shift-click', () => {
    const items = [
      { id: '1', name: 'Item 1' },
      { id: '2', name: 'Item 2' },
      { id: '3', name: 'Item 3' },
      { id: '4', name: 'Item 4' },
    ];

    const { result } = renderHook(() =>
      useRangeSelection({
        items,
        keyFn: (item) => item.id,
      })
    );

    // Select first item
    act(() => {
      result.current.handleItemClick(0, false);
    });

    expect(result.current.selectedIds.has('1')).toBe(true);

    // Shift-click third item
    act(() => {
      result.current.handleItemClick(2, true);
    });

    expect(result.current.selectedIds.has('1')).toBe(true);
    expect(result.current.selectedIds.has('2')).toBe(true);
    expect(result.current.selectedIds.has('3')).toBe(true);
    expect(result.current.selectedIds.has('4')).toBe(false);
  });

  it('resets range on normal click', () => {
    const items = [
      { id: '1', name: 'Item 1' },
      { id: '2', name: 'Item 2' },
      { id: '3', name: 'Item 3' },
    ];

    const { result } = renderHook(() =>
      useRangeSelection({
        items,
        keyFn: (item) => item.id,
      })
    );

    act(() => {
      result.current.handleItemClick(0, false);
    });

    act(() => {
      result.current.handleItemClick(2, true);
    });

    expect(result.current.selectedIds.size).toBe(3);

    act(() => {
      result.current.handleItemClick(1, false);
    });

    expect(result.current.selectedIds.size).toBe(1);
    expect(result.current.selectedIds.has('2')).toBe(true);
  });
});
