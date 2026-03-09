/**
 * @ghatana/yappc-ide - IDE Tabs Hook
 * 
 * React hook for managing IDE tabs with Jotai atoms.
 * 
 * @doc.type hook
 * @doc.purpose IDE tab management
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useCallback } from 'react';
import { useAtom } from 'jotai';
import {
  ideOpenTabsAtom,
  ideActiveFileAtom,
  ideActiveFileIdAtom,
} from '../state/atoms';
import type { IDETab, IDEFile } from '../types';

/**
 * IDE tabs hook return value
 */
export interface UseIDETabsReturn {
  /** Current tabs */
  tabs: IDETab[];
  /** Active file */
  activeFile: IDEFile | null;
  /** Add tab */
  addTab: (file: IDEFile) => void;
  /** Close tab */
  closeTab: (tabId: string) => void;
  /** Move tab */
  moveTab: (tabId: string, newIndex: number) => void;
  /** Update tab */
  updateTab: (tabId: string, updates: Partial<IDETab>) => void;
  /** Set active tab */
  setActiveTab: (tabId: string) => void;
  /** Get tab by file ID */
  getTabByFileId: (fileId: string) => IDETab | undefined;
}

/**
 * Generate unique tab ID
 */
function generateTabId(): string {
  return `tab-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

/**
 * IDE Tabs Hook
 * 
 * @doc.returns Tab management utilities
 */
export function useIDETabs(): UseIDETabsReturn {
  const [tabs, setTabs] = useAtom(ideOpenTabsAtom);
  const [activeFile] = useAtom(ideActiveFileAtom);
  const [activeFileId, setActiveFileId] = useAtom(ideActiveFileIdAtom);


  const addTab = useCallback((file: IDEFile) => {
    const existingTab = tabs.find((tab: IDETab) => tab.fileId === file.id);

    if (existingTab) {
      // Tab already exists, just set it as active
      setActiveFileId(file.id);
      return;
    }

    const newTab: IDETab = {
      id: generateTabId(),
      fileId: file.id,
      title: file.name,
      isDirty: false,
      isPinned: false,
      isActive: true,
    };

    setTabs([...tabs, newTab]);
    setActiveFileId(file.id);
  }, [tabs, setActiveFileId, setTabs]);

  const closeTab = useCallback((tabId: string) => {
    const tabToRemove = tabs.find((tab: IDETab) => tab.id === tabId);
    if (!tabToRemove) return;

    const newTabs = tabs.filter((tab: IDETab) => tab.id !== tabId);

    // If closing the active tab, set a new active tab
    if (tabToRemove.fileId === activeFileId) {
      const remainingTabs = newTabs.filter((tab: IDETab) => tab.fileId !== activeFileId);
      if (remainingTabs.length > 0) {
        const nextTab = remainingTabs[remainingTabs.length - 1];
        setActiveFileId(nextTab.fileId);
      } else {
        setActiveFileId(null);
      }
    }

    setTabs(newTabs);
  }, [tabs, activeFileId, setActiveFileId, setTabs]);

  const moveTab = useCallback((tabId: string, newIndex: number) => {
    const tabToMove = tabs.find((tab: IDETab) => tab.id === tabId);
    if (!tabToMove) return;

    const newTabs = [...tabs];
    const oldIndex = newTabs.findIndex((tab: IDETab) => tab.id === tabId);

    if (oldIndex !== -1) {
      newTabs.splice(oldIndex, 1);
      newTabs.splice(newIndex, 0, tabToMove);
    }

    setTabs(newTabs);
  }, [tabs, setTabs]);

  const updateTab = useCallback((tabId: string, updates: Partial<IDETab>) => {
    setTabs(tabs.map((tab: IDETab) =>
      tab.id === tabId ? { ...tab, ...updates } : tab
    ));
  }, [tabs, setTabs]);

  const setActiveTab = useCallback((tabId: string) => {
    const tab = tabs.find((t: IDETab) => t.id === tabId);
    if (tab) {
      setActiveFileId(tab.fileId);
      setTabs(tabs.map((t: IDETab) =>
        ({ ...t, isActive: t.id === tabId })
      ));
    }
  }, [tabs, setActiveFileId, setTabs]);

  const getTabByFileId = useCallback((fileId: string) => {
    return tabs.find((tab: IDETab) => tab.fileId === fileId);
  }, [tabs]);

  return {
    tabs,
    activeFile,
    addTab,
    closeTab,
    moveTab,
    updateTab,
    setActiveTab,
    getTabByFileId,
  };
}
