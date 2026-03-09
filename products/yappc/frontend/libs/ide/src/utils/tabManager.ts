/**
 * @ghatana/yappc-ide - Tab Manager Utilities
 * 
 * Utilities for managing IDE tabs and editor groups.
 * 
 * @doc.type module
 * @doc.purpose Tab management utilities for IDE
 * @doc.layer product
 * @doc.pattern Utility Functions
 */

import { v4 as uuidv4 } from 'uuid';
import type { IDETab, IDEEditorGroup } from '../types';

/**
 * Create a new IDE tab
 * 
 * @doc.param fileId - File ID
 * @doc.param title - Tab title
 * @doc.returns New IDE tab
 */
export function createTab(fileId: string, title: string): IDETab {
  return {
    id: uuidv4(),
    fileId,
    title,
    isDirty: false,
    isActive: false,
    isPinned: false,
  };
}

/**
 * Create a new editor group
 * 
 * @doc.param orientation - Group orientation
 * @doc.returns New editor group
 */
export function createEditorGroup(
  orientation: 'horizontal' | 'vertical' = 'horizontal'
): IDEEditorGroup {
  return {
    id: uuidv4(),
    tabs: [],
    activeTabId: null,
    orientation,
  };
}

/**
 * Add tab to tabs array
 * 
 * @doc.param tabs - Current tabs
 * @doc.param tab - Tab to add
 * @doc.returns Updated tabs array
 */
export function addTab(tabs: IDETab[], tab: IDETab): IDETab[] {
  // Check if tab already exists
  const existingIndex = tabs.findIndex((t) => t.fileId === tab.fileId);
  
  if (existingIndex !== -1) {
    // Tab exists, make it active
    return tabs.map((t, i) => ({
      ...t,
      isActive: i === existingIndex,
    }));
  }
  
  // Add new tab and make it active
  return [
    ...tabs.map((t) => ({ ...t, isActive: false })),
    { ...tab, isActive: true },
  ];
}

/**
 * Remove tab from tabs array
 * 
 * @doc.param tabs - Current tabs
 * @doc.param tabId - Tab ID to remove
 * @doc.returns Updated tabs array
 */
export function removeTab(tabs: IDETab[], tabId: string): IDETab[] {
  const index = tabs.findIndex((t) => t.id === tabId);
  if (index === -1) return tabs;
  
  const newTabs = tabs.filter((t) => t.id !== tabId);
  
  // If removed tab was active, activate adjacent tab
  if (tabs[index].isActive && newTabs.length > 0) {
    const newActiveIndex = Math.min(index, newTabs.length - 1);
    return newTabs.map((t, i) => ({
      ...t,
      isActive: i === newActiveIndex,
    }));
  }
  
  return newTabs;
}

/**
 * Set active tab
 * 
 * @doc.param tabs - Current tabs
 * @doc.param tabId - Tab ID to activate
 * @doc.returns Updated tabs array
 */
export function setActiveTab(tabs: IDETab[], tabId: string): IDETab[] {
  return tabs.map((t) => ({
    ...t,
    isActive: t.id === tabId,
  }));
}

/**
 * Mark tab as dirty
 * 
 * @doc.param tabs - Current tabs
 * @doc.param tabId - Tab ID to mark dirty
 * @doc.returns Updated tabs array
 */
export function markTabDirty(tabs: IDETab[], tabId: string): IDETab[] {
  return tabs.map((t) =>
    t.id === tabId ? { ...t, isDirty: true } : t
  );
}

/**
 * Mark tab as clean
 * 
 * @doc.param tabs - Current tabs
 * @doc.param tabId - Tab ID to mark clean
 * @doc.returns Updated tabs array
 */
export function markTabClean(tabs: IDETab[], tabId: string): IDETab[] {
  return tabs.map((t) =>
    t.id === tabId ? { ...t, isDirty: false } : t
  );
}

/**
 * Toggle tab pinned state
 * 
 * @doc.param tabs - Current tabs
 * @doc.param tabId - Tab ID to toggle
 * @doc.returns Updated tabs array
 */
export function toggleTabPinned(tabs: IDETab[], tabId: string): IDETab[] {
  return tabs.map((t) =>
    t.id === tabId ? { ...t, isPinned: !t.isPinned } : t
  );
}

/**
 * Get active tab
 * 
 * @doc.param tabs - Current tabs
 * @doc.returns Active tab or null
 */
export function getActiveTab(tabs: IDETab[]): IDETab | null {
  return tabs.find((t) => t.isActive) || null;
}

/**
 * Get tab by file ID
 * 
 * @doc.param tabs - Current tabs
 * @doc.param fileId - File ID
 * @doc.returns Tab or null
 */
export function getTabByFileId(tabs: IDETab[], fileId: string): IDETab | null {
  return tabs.find((t) => t.fileId === fileId) || null;
}

/**
 * Sort tabs (pinned first, then by order)
 * 
 * @doc.param tabs - Current tabs
 * @doc.returns Sorted tabs array
 */
export function sortTabs(tabs: IDETab[]): IDETab[] {
  return [...tabs].sort((a, b) => {
    if (a.isPinned && !b.isPinned) return -1;
    if (!a.isPinned && b.isPinned) return 1;
    return 0;
  });
}

/**
 * Close all tabs except specified
 * 
 * @doc.param tabs - Current tabs
 * @doc.param exceptTabId - Tab ID to keep
 * @doc.returns Updated tabs array
 */
export function closeOtherTabs(tabs: IDETab[], exceptTabId: string): IDETab[] {
  const keepTab = tabs.find((t) => t.id === exceptTabId);
  if (!keepTab) return tabs;
  
  // Keep pinned tabs and the specified tab
  const newTabs = tabs.filter((t) => t.isPinned || t.id === exceptTabId);
  
  // Make the kept tab active
  return newTabs.map((t) => ({
    ...t,
    isActive: t.id === exceptTabId,
  }));
}

/**
 * Close all tabs to the right of specified tab
 * 
 * @doc.param tabs - Current tabs
 * @doc.param tabId - Tab ID
 * @doc.returns Updated tabs array
 */
export function closeTabsToRight(tabs: IDETab[], tabId: string): IDETab[] {
  const index = tabs.findIndex((t) => t.id === tabId);
  if (index === -1) return tabs;
  
  // Keep tabs up to and including the specified tab, plus pinned tabs
  return tabs.filter((t, i) => i <= index || t.isPinned);
}

/**
 * Close all unpinned tabs
 * 
 * @doc.param tabs - Current tabs
 * @doc.returns Updated tabs array
 */
export function closeAllUnpinnedTabs(tabs: IDETab[]): IDETab[] {
  const newTabs = tabs.filter((t) => t.isPinned);
  
  // If there are tabs left, activate the first one
  if (newTabs.length > 0) {
    return newTabs.map((t, i) => ({
      ...t,
      isActive: i === 0,
    }));
  }
  
  return newTabs;
}

/**
 * Get dirty tabs
 * 
 * @doc.param tabs - Current tabs
 * @doc.returns Array of dirty tabs
 */
export function getDirtyTabs(tabs: IDETab[]): IDETab[] {
  return tabs.filter((t) => t.isDirty);
}

/**
 * Check if any tabs are dirty
 * 
 * @doc.param tabs - Current tabs
 * @doc.returns True if any tabs are dirty
 */
export function hasUnsavedChanges(tabs: IDETab[]): boolean {
  return tabs.some((t) => t.isDirty);
}
