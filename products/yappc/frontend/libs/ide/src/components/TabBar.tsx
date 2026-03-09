/**
 * @ghatana/yappc-ide - Tab Bar Component
 * 
 * Editor tab management component with drag-and-drop support.
 * 
 * @doc.type component
 * @doc.purpose Tab management for IDE editor
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useCallback, useState } from 'react';
import { useAtom } from 'jotai';
import { ideOpenTabsAtom, ideActiveFileIdAtom, ideFilesAtom } from '../state/atoms';
import { useIDEFileOperations } from '../hooks/useIDEFileOperations';
import { setActiveTab, getActiveTab } from '../utils/tabManager';
import type { IDETab } from '../types';

/**
 * Tab Bar Props
 */
export interface TabBarProps {
  className?: string;
  maxVisibleTabs?: number;
  showCloseButton?: boolean;
  onTabClose?: (tabId: string) => void;
  onTabSelect?: (tabId: string) => void;
}

/**
 * Single Tab Component
 */
interface TabItemProps {
  tab: IDETab;
  isActive: boolean;
  onSelect: (tabId: string) => void;
  onClose: (tabId: string, e: React.MouseEvent) => void;
  onContextMenu: (tabId: string, e: React.MouseEvent) => void;
}

const TabItem: React.FC<TabItemProps> = ({
  tab,
  isActive,
  onSelect,
  onClose,
  onContextMenu,
}) => {
  return (
    <div
      className={`
        group relative flex items-center gap-2 px-3 py-2 min-w-[120px] max-w-[200px]
        border-r border-gray-200 dark:border-gray-700 cursor-pointer
        ${isActive
          ? 'bg-white dark:bg-gray-800 border-b-2 border-b-blue-500'
          : 'bg-gray-50 dark:bg-gray-900 hover:bg-gray-100 dark:hover:bg-gray-800'
        }
        ${tab.isPinned ? 'border-l-2 border-l-blue-400' : ''}
      `}
      onClick={() => onSelect(tab.id)}
      onContextMenu={(e) => onContextMenu(tab.id, e)}
      role="tab"
      aria-selected={isActive}
      aria-label={`Tab: ${tab.title}`}
      tabIndex={0}
    >
      {/* Pin indicator */}
      {tab.isPinned && (
        <span className="flex-shrink-0 text-xs" aria-label="Pinned">
          📌
        </span>
      )}

      {/* Tab title */}
      <span className="flex-1 truncate text-sm">
        {tab.title}
      </span>

      {/* Dirty indicator */}
      {tab.isDirty && (
        <span className="flex-shrink-0 w-2 h-2 rounded-full bg-orange-500" aria-label="Unsaved changes" />
      )}

      {/* Close button */}
      <button
        className="flex-shrink-0 w-4 h-4 flex items-center justify-center rounded hover:bg-gray-200 dark:hover:bg-gray-700 opacity-0 group-hover:opacity-100"
        onClick={(e) => onClose(tab.id, e)}
        aria-label={`Close ${tab.title}`}
      >
        <span className="text-xs">✕</span>
      </button>
    </div>
  );
};

/**
 * Tab Context Menu Component
 */
interface TabContextMenuProps {
  tabId: string;
  position: { x: number; y: number };
  onClose: () => void;
  onCloseTab: (tabId: string) => void;
  onCloseOthers: (tabId: string) => void;
  onCloseToRight: (tabId: string) => void;
  onCloseAll: () => void;
  onPinTab: (tabId: string) => void;
}

const TabContextMenu: React.FC<TabContextMenuProps> = ({
  tabId,
  position,
  onClose,
  onCloseTab,
  onCloseOthers,
  onCloseToRight,
  onCloseAll,
  onPinTab,
}) => {
  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 z-40"
        onClick={onClose}
      />

      {/* Menu */}
      <div
        className="fixed z-50 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded shadow-lg py-1 min-w-[180px]"
        style={{ left: position.x, top: position.y }}
      >
        <button
          className="w-full px-4 py-2 text-left text-sm hover:bg-gray-100 dark:hover:bg-gray-700"
          onClick={() => {
            onCloseTab(tabId);
            onClose();
          }}
        >
          Close
        </button>
        <button
          className="w-full px-4 py-2 text-left text-sm hover:bg-gray-100 dark:hover:bg-gray-700"
          onClick={() => {
            onCloseOthers(tabId);
            onClose();
          }}
        >
          Close Others
        </button>
        <button
          className="w-full px-4 py-2 text-left text-sm hover:bg-gray-100 dark:hover:bg-gray-700"
          onClick={() => {
            onCloseToRight(tabId);
            onClose();
          }}
        >
          Close to the Right
        </button>
        <button
          className="w-full px-4 py-2 text-left text-sm hover:bg-gray-100 dark:hover:bg-gray-700"
          onClick={() => {
            onCloseAll();
            onClose();
          }}
        >
          Close All
        </button>
        <div className="border-t border-gray-200 dark:border-gray-700 my-1" />
        <button
          className="w-full px-4 py-2 text-left text-sm hover:bg-gray-100 dark:hover:bg-gray-700"
          onClick={() => {
            onPinTab(tabId);
            onClose();
          }}
        >
          Pin Tab
        </button>
      </div>
    </>
  );
};

/**
 * Tab Bar Component
 * 
 * @doc.param props - Component props
 * @doc.returns Tab bar component
 */
export const TabBar: React.FC<TabBarProps> = ({
  className = '',
  maxVisibleTabs = 20,
  showCloseButton: _showCloseButton = true, // (intentionally unused for now; API preserved)
  onTabClose,
  onTabSelect,
}) => {
  const [openTabs, setOpenTabs] = useAtom(ideOpenTabsAtom);
  const [, setActiveFileId] = useAtom(ideActiveFileIdAtom);
  const [files] = useAtom(ideFilesAtom);
  const { closeFile, saveFile } = useIDEFileOperations();
  const [contextMenu, setContextMenu] = useState<{ tabId: string; x: number; y: number } | null>(null);

  const activeTab = getActiveTab(openTabs);

  const handleTabSelect = useCallback(
    (tabId: string) => {
      const tab = openTabs.find((t) => t.id === tabId);
      if (!tab) return;

      setOpenTabs(setActiveTab(openTabs, tabId));
      setActiveFileId(tab.fileId);
      onTabSelect?.(tabId);
    },
    [openTabs, setOpenTabs, setActiveFileId, onTabSelect]
  );

  const handleTabClose = useCallback(
    (tabId: string, e: React.MouseEvent) => {
      e.stopPropagation();

      const tab = openTabs.find((t) => t.id === tabId);
      if (!tab) return;

      const file = files[tab.fileId];
      if (file?.isDirty) {
        const confirmed = window.confirm(
          `"${file.name}" has unsaved changes. Close anyway?`
        );
        if (!confirmed) return;
      }

      closeFile(tab.fileId);
      onTabClose?.(tabId);
    },
    [openTabs, files, closeFile, onTabClose]
  );

  const handleContextMenu = useCallback(
    (tabId: string, e: React.MouseEvent) => {
      e.preventDefault();
      setContextMenu({ tabId, x: e.clientX, y: e.clientY });
    },
    []
  );

  const handleCloseOthers = useCallback(
    (tabId: string) => {
      const tabsToClose = openTabs.filter((t) => t.id !== tabId && !t.isPinned);
      tabsToClose.forEach((tab) => {
        const file = files[tab.fileId];
        if (!file?.isDirty) {
          closeFile(tab.fileId);
        }
      });
    },
    [openTabs, files, closeFile]
  );

  const handleCloseToRight = useCallback(
    (tabId: string) => {
      const tabIndex = openTabs.findIndex((t) => t.id === tabId);
      if (tabIndex === -1) return;

      const tabsToClose = openTabs.slice(tabIndex + 1).filter((t) => !t.isPinned);
      tabsToClose.forEach((tab) => {
        const file = files[tab.fileId];
        if (!file?.isDirty) {
          closeFile(tab.fileId);
        }
      });
    },
    [openTabs, files, closeFile]
  );

  const handleCloseAll = useCallback(() => {
    const tabsToClose = openTabs.filter((t) => !t.isPinned);
    tabsToClose.forEach((tab) => {
      const file = files[tab.fileId];
      if (!file?.isDirty) {
        closeFile(tab.fileId);
      }
    });
  }, [openTabs, files, closeFile]);

  const closeTabById = useCallback((tabId: string) => {
    const tab = openTabs.find((t) => t.id === tabId);
    if (!tab) return;

    const file = files[tab.fileId];
    if (!file?.isDirty) {
      closeFile(tab.fileId);
    }
  }, [openTabs, files, closeFile]);

  const handlePinTab = useCallback(
    (tabId: string) => {
      setOpenTabs(openTabs.map((t) => (t.id === tabId ? { ...t, isPinned: !t.isPinned } : t)));
    },
    [setOpenTabs]
  );

  const handleSaveAll = useCallback(() => {
    openTabs.forEach((tab) => {
      const file = files[tab.fileId];
      if (file?.isDirty) {
        saveFile(file.id);
      }
    });
  }, [openTabs, files, saveFile]);

  const dirtyTabsCount = openTabs.filter((tab) => tab.isDirty).length;
  const visibleTabs = openTabs.slice(0, maxVisibleTabs);
  const hiddenTabsCount = Math.max(0, openTabs.length - maxVisibleTabs);

  return (
    <div className={`flex items-center bg-gray-50 dark:bg-gray-900 border-b border-gray-200 dark:border-gray-700 ${className}`}>
      {/* Tabs */}
      <div className="flex-1 flex overflow-x-auto scrollbar-thin" role="tablist">
        {visibleTabs.length === 0 ? (
          <div className="px-4 py-2 text-sm text-gray-500 dark:text-gray-400">
            No files open
          </div>
        ) : (
          visibleTabs.map((tab) => (
            <TabItem
              key={tab.id}
              tab={tab}
              isActive={tab.id === activeTab?.id}
              onSelect={handleTabSelect}
              onClose={handleTabClose}
              onContextMenu={handleContextMenu}
            />
          ))
        )}

        {hiddenTabsCount > 0 && (
          <div className="flex items-center px-3 py-2 text-sm text-gray-500 dark:text-gray-400 border-r border-gray-200 dark:border-gray-700">
            +{hiddenTabsCount} more
          </div>
        )}
      </div>

      {/* Actions */}
      <div className="flex items-center gap-2 px-2">
        {dirtyTabsCount > 0 && (
          <button
            className="px-2 py-1 text-xs bg-blue-500 text-white rounded hover:bg-blue-600"
            onClick={handleSaveAll}
            aria-label={`Save ${dirtyTabsCount} unsaved files`}
          >
            Save All ({dirtyTabsCount})
          </button>
        )}

        <button
          className="p-1 text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
          onClick={handleCloseAll}
          aria-label="Close all tabs"
        >
          ✕✕
        </button>
      </div>

      {/* Context Menu */}
      {contextMenu && (
        <TabContextMenu
          tabId={contextMenu.tabId}
          position={{ x: contextMenu.x, y: contextMenu.y }}
          onClose={() => setContextMenu(null)}
          onCloseTab={closeTabById}
          onCloseOthers={handleCloseOthers}
          onCloseToRight={handleCloseToRight}
          onCloseAll={handleCloseAll}
          onPinTab={handlePinTab}
        />
      )}
    </div>
  );
};

export default TabBar;
