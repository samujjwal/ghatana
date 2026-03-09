/**
 * Utility functions for selection management.
 * Provides helper functions for performing common selection operations
 * like range calculation, ID validation, and state transformations.
 */

import type { SelectionItem, SelectionAction } from './types';

/**
 * Selection state helper utility class.
 * Provides static methods for common selection calculations and transformations.
 */
export class SelectionUtils {
  /**
   * Calculate the range of indices between two items.
   * Returns the normalized start and end indices (start <= end).
   *
   * @param startIndex - The starting index
   * @param endIndex - The ending index
   * @returns Object with normalized start and end indices
   */
  static calculateRange(
    startIndex: number,
    endIndex: number
  ): { start: number; end: number } {
    return {
      start: Math.min(startIndex, endIndex),
      end: Math.max(startIndex, endIndex),
    };
  }

  /**
   * Extract IDs from items in a range between start and end indices.
   * Includes both start and end indices (inclusive range).
   *
   * @param items - Array of items to extract from
   * @param startIndex - Start index (inclusive)
   * @param endIndex - End index (inclusive)
   * @returns Array of IDs within the range
   */
  static getRangeIds<T extends SelectionItem>(
    items: T[],
    startIndex: number,
    endIndex: number
  ): string[] {
    const { start, end } = this.calculateRange(startIndex, endIndex);
    return items.slice(start, end + 1).map((item) => item.id);
  }

  /**
   * Merge two arrays of IDs into a deduplicated set, converted back to array.
   * Preserves order of the first array, then appends new IDs from the second.
   *
   * @param existing - Existing array of IDs
   * @param newIds - Array of new IDs to merge
   * @returns Deduplicated array of IDs
   */
  static mergeIds(existing: string[], newIds: string[]): string[] {
    return Array.from(new Set([...existing, ...newIds]));
  }

  /**
   * Remove specified IDs from an array of IDs.
   * Returns a new array with specified IDs filtered out.
   *
   * @param ids - Original array of IDs
   * @param idsToRemove - IDs to remove from the array
   * @returns New array with specified IDs removed
   */
  static removeIds(ids: string[], idsToRemove?: string[]): string[] {
    if (!idsToRemove || idsToRemove.length === 0) {
      return ids;
    }
    return ids.filter((id) => !idsToRemove.includes(id));
  }

  /**
   * Build a Map of items indexed by their ID for O(1) lookup performance.
   * Useful for efficient item lookups by ID during selection operations.
   *
   * @param items - Array of items to index
   * @returns Map with item IDs as keys and objects with item and index as values
   */
  static buildItemsMap<T extends SelectionItem>(
    items: T[]
  ): Map<string, { item: T; index: number }> {
    const map = new Map<string, { item: T; index: number }>();
    items.forEach((item, index) => {
      map.set(item.id, { item, index });
    });
    return map;
  }

  /**
   * Filter and retrieve actual item objects for a set of IDs.
   * Returns only items whose IDs exist in the items collection.
   *
   * @param ids - Array of item IDs
   * @param itemsMap - Map of items indexed by ID
   * @returns Array of item objects corresponding to the IDs
   */
  static getItemsForIds<T extends SelectionItem>(
    ids: string[],
    itemsMap: Map<string, { item: T; index: number }>
  ): T[] {
    return ids
      .map((id) => itemsMap.get(id)?.item)
      .filter((item): item is T => item !== undefined);
  }

  /**
   * Apply max selections limit to an array of selected IDs.
   * Truncates the array if it exceeds the maximum allowed selections.
   *
   * @param selectedIds - Current array of selected IDs
   * @param maxSelections - Maximum allowed selections (Infinity = no limit)
   * @returns Array of selected IDs, potentially truncated to maxSelections
   */
  static applyMaxSelectionsLimit(
    selectedIds: string[],
    maxSelections: number
  ): string[] {
    return maxSelections < Infinity
      ? selectedIds.slice(0, maxSelections)
      : selectedIds;
  }

  /**
   * Create a selection summary object with statistics about current selection.
   *
   * @param selectedCount - Number of currently selected items
   * @param totalCount - Total number of available items
   * @returns Summary object with total, selected, and percentage
   */
  static getSelectionSummary(
    selectedCount: number,
    totalCount: number
  ): {
    total: number;
    selected: number;
    percentage: number;
  } {
    return {
      total: totalCount,
      selected: selectedCount,
      percentage:
        totalCount > 0 ? Math.round((selectedCount / totalCount) * 100) : 0,
    };
  }

  /**
   * Undo a selection action by reversing its effects.
   * Calculates the new selected IDs array based on the action type.
   *
   * @param currentSelectedIds - Current array of selected IDs
   * @param action - Selection action to undo
   * @returns New array of selected IDs after undoing the action
   */
  static undoAction(
    currentSelectedIds: string[],
    action: SelectionAction
  ): string[] {
    let newSelectedIds = [...currentSelectedIds];

    switch (action.type) {
      case 'select':
        newSelectedIds = this.removeIds(newSelectedIds, action.items);
        break;
      case 'deselect':
        newSelectedIds = this.mergeIds(newSelectedIds, action.items || []);
        break;
      case 'selectAll':
        newSelectedIds = [];
        break;
      case 'deselectAll':
        newSelectedIds = action.items || [];
        break;
      case 'toggle':
        // Toggle reverses itself by toggling again
        if (action.items) {
          const toggledIds = action.items.filter((id) =>
            currentSelectedIds.includes(id)
          );
          newSelectedIds = this.removeIds(newSelectedIds, toggledIds);
          newSelectedIds = this.mergeIds(
            newSelectedIds,
            action.items.filter((id) => !currentSelectedIds.includes(id))
          );
        }
        break;
      case 'selectRange':
        newSelectedIds = this.removeIds(newSelectedIds, action.items);
        break;
    }

    return newSelectedIds;
  }

  /**
   * Redo a selection action by reapplying its effects.
   * Calculates the new selected IDs array based on the action type.
   *
   * @param currentSelectedIds - Current array of selected IDs
   * @param action - Selection action to redo
   * @param allItems - Array of all available items (needed for selectAll)
   * @returns New array of selected IDs after redoing the action
   */
  static redoAction(
    currentSelectedIds: string[],
    action: SelectionAction,
    allItems: SelectionItem[]
  ): string[] {
    let newSelectedIds = [...currentSelectedIds];

    switch (action.type) {
      case 'select':
        newSelectedIds = this.mergeIds(newSelectedIds, action.items || []);
        break;
      case 'deselect':
        newSelectedIds = this.removeIds(newSelectedIds, action.items);
        break;
      case 'selectAll':
        newSelectedIds = allItems.map((item) => item.id);
        break;
      case 'deselectAll':
        newSelectedIds = [];
        break;
      case 'toggle':
        if (action.items) {
          newSelectedIds = this.removeIds(
            newSelectedIds,
            action.items.filter((id) => currentSelectedIds.includes(id))
          );
          newSelectedIds = this.mergeIds(
            newSelectedIds,
            action.items.filter((id) => !currentSelectedIds.includes(id))
          );
        }
        break;
      case 'selectRange':
        newSelectedIds = this.mergeIds(newSelectedIds, action.items || []);
        break;
    }

    return newSelectedIds;
  }

  /**
   * Validate that imported selection data contains only valid item IDs.
   * Returns a new array with only IDs that exist in the items collection.
   *
   * @param importedIds - Array of IDs from imported data
   * @param itemsMap - Map of valid item IDs
   * @returns Array of validated IDs that exist in the collection
   */
  static validateImportedIds<T extends SelectionItem>(
    importedIds: string[],
    itemsMap: Map<string, { item: T; index: number }>
  ): string[] {
    return importedIds.filter((id) => itemsMap.has(id));
  }
}
