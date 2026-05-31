/**
 * Tests for useSelection hook
 */

import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useSelection } from "../useSelection";

interface TestItem {
  id: string;
  name: string;
  [key: string]: unknown;
}

describe("useSelection", () => {
  it("initializes with empty selection", () => {
    const items: TestItem[] = [
      { id: "1", name: "Item 1" },
      { id: "2", name: "Item 2" },
    ];

    const { result } = renderHook(() =>
      useSelection({ items, keyFn: (item) => item.id }),
    );

    expect(result.current.selectedIds.size).toBe(0);
    expect(result.current.selectedItems).toEqual([]);
    expect(result.current.isAllSelected).toBe(false);
    expect(result.current.isIndeterminate).toBe(false);
  });

  it("toggles selection for a single item", () => {
    const items: TestItem[] = [
      { id: "1", name: "Item 1" },
      { id: "2", name: "Item 2" },
    ];

    const { result } = renderHook(() =>
      useSelection({ items, keyFn: (item) => item.id }),
    );

    act(() => {
      result.current.toggleSelection("1");
    });

    expect(result.current.selectedIds.has("1")).toBe(true);
    expect(result.current.selectedItems).toEqual([{ id: "1", name: "Item 1" }]);
    expect(result.current.isAllSelected).toBe(false);
    expect(result.current.isIndeterminate).toBe(true);
  });

  it("untoggles selection for a selected item", () => {
    const items: TestItem[] = [
      { id: "1", name: "Item 1" },
      { id: "2", name: "Item 2" },
    ];

    const { result } = renderHook(() =>
      useSelection({ items, keyFn: (item) => item.id }),
    );

    act(() => {
      result.current.toggleSelection("1");
    });

    act(() => {
      result.current.toggleSelection("1");
    });

    expect(result.current.selectedIds.has("1")).toBe(false);
    expect(result.current.selectedItems).toEqual([]);
  });

  it("selects all items when toggleAll is called", () => {
    const items: TestItem[] = [
      { id: "1", name: "Item 1" },
      { id: "2", name: "Item 2" },
      { id: "3", name: "Item 3" },
    ];

    const { result } = renderHook(() =>
      useSelection({ items, keyFn: (item) => item.id }),
    );

    act(() => {
      result.current.toggleAll();
    });

    expect(result.current.selectedIds.size).toBe(3);
    expect(result.current.selectedItems).toEqual(items);
    expect(result.current.isAllSelected).toBe(true);
    expect(result.current.isIndeterminate).toBe(false);
  });

  it("clears all items when toggleAll is called on fully selected", () => {
    const items: TestItem[] = [
      { id: "1", name: "Item 1" },
      { id: "2", name: "Item 2" },
      { id: "3", name: "Item 3" },
    ];

    const { result } = renderHook(() =>
      useSelection({ items, keyFn: (item) => item.id }),
    );

    act(() => {
      result.current.toggleAll();
    });

    act(() => {
      result.current.toggleAll();
    });

    expect(result.current.selectedIds.size).toBe(0);
    expect(result.current.selectedItems).toEqual([]);
    expect(result.current.isAllSelected).toBe(false);
  });

  it("shows indeterminate state when some items are selected", () => {
    const items: TestItem[] = [
      { id: "1", name: "Item 1" },
      { id: "2", name: "Item 2" },
      { id: "3", name: "Item 3" },
    ];

    const { result } = renderHook(() =>
      useSelection({ items, keyFn: (item) => item.id }),
    );

    act(() => {
      result.current.toggleSelection("1");
      result.current.toggleSelection("2");
    });

    expect(result.current.isIndeterminate).toBe(true);
    expect(result.current.isAllSelected).toBe(false);
  });

  it("clears all selections", () => {
    const items: TestItem[] = [
      { id: "1", name: "Item 1" },
      { id: "2", name: "Item 2" },
    ];

    const { result } = renderHook(() =>
      useSelection({ items, keyFn: (item) => item.id }),
    );

    act(() => {
      result.current.toggleAll();
    });

    act(() => {
      result.current.clearSelection();
    });

    expect(result.current.selectedIds.size).toBe(0);
    expect(result.current.selectedItems).toEqual([]);
  });

  it("selects specific IDs", () => {
    const items: TestItem[] = [
      { id: "1", name: "Item 1" },
      { id: "2", name: "Item 2" },
      { id: "3", name: "Item 3" },
    ];

    const { result } = renderHook(() =>
      useSelection({ items, keyFn: (item) => item.id }),
    );

    act(() => {
      result.current.selectIds(["1", "3"]);
    });

    expect(result.current.selectedIds.has("1")).toBe(true);
    expect(result.current.selectedIds.has("3")).toBe(true);
    expect(result.current.selectedIds.has("2")).toBe(false);
    expect(result.current.selectedItems).toEqual([
      { id: "1", name: "Item 1" },
      { id: "3", name: "Item 3" },
    ]);
  });

  it("handles empty items list", () => {
    const items: TestItem[] = [];

    const { result } = renderHook(() =>
      useSelection({ items, keyFn: (item) => item.id }),
    );

    expect(result.current.isAllSelected).toBe(false);
    expect(result.current.isIndeterminate).toBe(false);
    expect(result.current.selectedItems).toEqual([]);
  });

  it("updates selectedItems when items change", () => {
    const items1: TestItem[] = [
      { id: "1", name: "Item 1" },
      { id: "2", name: "Item 2" },
    ];

    const { result, rerender } = renderHook(
      ({ items }) => useSelection({ items, keyFn: (item) => item.id }),
      { initialProps: { items: items1 } },
    );

    act(() => {
      result.current.toggleSelection("1");
    });

    const items2: TestItem[] = [
      { id: "1", name: "Updated Item 1" },
      { id: "2", name: "Updated Item 2" },
    ];

    rerender({ items: items2 });

    expect(result.current.selectedItems).toEqual([
      { id: "1", name: "Updated Item 1" },
    ]);
  });
});
