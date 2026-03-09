/**
 * @ghatana/yappc-ide - Context Menu Component
 * 
 * Context menu for file operations with keyboard navigation.
 * Integrates with advanced file operations hook.
 * 
 * @doc.type component
 * @doc.purpose Context menu for file operations
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useAdvancedFileOperations } from '../hooks/useAdvancedFileOperations';
import type { IDEFile, IDEFolder } from '../types';

/**
 * Context menu item
 */
export type ContextMenuItem =
  | {
    separator: true;
  }
  | {
    id: string;
    label: string;
    icon?: string;
    shortcut?: string;
    action: () => void;
    submenu?: ContextMenuItem[];
    disabled?: boolean;
    separator?: false;
  };

/**
 * Context Menu Props
 */
export interface ContextMenuProps {
  visible: boolean;
  x: number;
  y: number;
  items: ContextMenuItem[];
  onClose: () => void;
  className?: string;
}

/**
 * Context Menu Component
 */
export const ContextMenu: React.FC<ContextMenuProps> = ({
  visible,
  x,
  y,
  items,
  onClose,
  className = '',
}) => {
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const menuRef = useRef<HTMLDivElement>(null);
  const itemRefs = useRef<(HTMLDivElement | null)[]>([]);

  // Filter out separators for navigation
  const navigableItems = items.filter(item => !item.separator);
  const navigableIndexMap = items.reduce((map, item, index) => {
    if (!item.separator) {
      map.push(index);
    }
    return map;
  }, [] as number[]);

  // Handle keyboard navigation
  const handleKeyDown = useCallback((event: KeyboardEvent) => {
    if (!visible) return;

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        setSelectedIndex(prev => {
          const nextIndex = prev < navigableItems.length - 1 ? prev + 1 : 0;
          const actualIndex = navigableIndexMap[nextIndex];
          itemRefs.current[actualIndex]?.focus();
          return nextIndex;
        });
        break;

      case 'ArrowUp':
        event.preventDefault();
        setSelectedIndex(prev => {
          const prevIndex = prev > 0 ? prev - 1 : navigableItems.length - 1;
          const actualIndex = navigableIndexMap[prevIndex];
          itemRefs.current[actualIndex]?.focus();
          return prevIndex;
        });
        break;

      case 'Enter':
      case ' ':
        event.preventDefault();
        if (selectedIndex >= 0 && selectedIndex < navigableItems.length) {
          const item = navigableItems[selectedIndex];
          if (!item.disabled) {
            item.action();
            onClose();
          }
        }
        break;

      case 'Escape':
        event.preventDefault();
        onClose();
        break;

      case 'ArrowRight':
        event.preventDefault();
        if (selectedIndex >= 0 && selectedIndex < navigableItems.length) {
          const item = navigableItems[selectedIndex];
          if (item.submenu && item.submenu.length > 0) {
            // Handle submenu expansion
          }
        }
        break;

      case 'ArrowLeft':
        event.preventDefault();
        // Handle submenu collapse
        break;
    }
  }, [visible, selectedIndex, navigableItems, navigableIndexMap, onClose]);

  // Handle click outside
  const handleClickOutside = useCallback((event: MouseEvent) => {
    if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
      onClose();
    }
  }, [onClose]);

  // Position menu within viewport
  const positionMenu = useCallback(() => {
    if (!menuRef.current) return;

    const rect = menuRef.current.getBoundingClientRect();
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;

    let adjustedX = x;
    let adjustedY = y;

    // Adjust horizontal position
    if (x + rect.width > viewportWidth) {
      adjustedX = viewportWidth - rect.width - 8;
    }

    // Adjust vertical position
    if (y + rect.height > viewportHeight) {
      adjustedY = viewportHeight - rect.height - 8;
    }

    // Ensure menu doesn't go off-screen
    adjustedX = Math.max(8, adjustedX);
    adjustedY = Math.max(8, adjustedY);

    menuRef.current.style.left = `${adjustedX}px`;
    menuRef.current.style.top = `${adjustedY}px`;
  }, [x, y]);

  // Setup event listeners
  useEffect(() => {
    if (visible) {
      document.addEventListener('keydown', handleKeyDown);
      document.addEventListener('mousedown', handleClickOutside);
      setSelectedIndex(0);

      // Focus first item
      setTimeout(() => {
        const firstItem = itemRefs.current[navigableIndexMap[0]];
        firstItem?.focus();
      }, 0);
    } else {
      setSelectedIndex(-1);
    }

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [visible, handleKeyDown, handleClickOutside, navigableIndexMap]);

  // Position menu when visible
  useEffect(() => {
    if (visible) {
      positionMenu();
    }
  }, [visible, positionMenu]);

  if (!visible) return null;

  return (
    <div
      ref={menuRef}
      className={`fixed z-50 min-w-48 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg py-1 ${className}`}
      style={{ left: x, top: y }}
    >
      {items.map((item, index) => {
        if (item.separator) {
          return (
            <div
              key={`separator-${index}`}
              className="my-1 border-t border-gray-200 dark:border-gray-700"
            />
          );
        }

        const isNavigable = !item.separator;
        const navigableIndex = isNavigable ? navigableIndexMap.indexOf(index) : -1;
        const isSelected = navigableIndex === selectedIndex;

        return (
          <div
            key={item.id}
            ref={el => { itemRefs.current[index] = el; }}
            className={`
              flex items-center gap-2 px-3 py-2 text-sm cursor-pointer
              ${item.disabled
                ? 'text-gray-400 dark:text-gray-600 cursor-not-allowed'
                : 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700'
              }
              ${isSelected && !item.disabled
                ? 'bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-300'
                : ''
              }
            `}
            onClick={() => {
              if (!item.disabled) {
                item.action();
                onClose();
              }
            }}
            role="menuitem"
            tabIndex={isNavigable ? 0 : -1}
            aria-disabled={item.disabled}
          >
            {item.icon && (
              <span className="flex-shrink-0 w-4 h-4 text-center">
                {item.icon}
              </span>
            )}
            <span className="flex-1">{item.label}</span>
            {item.shortcut && (
              <span className="text-xs text-gray-500 dark:text-gray-400">
                {item.shortcut}
              </span>
            )}
            {item.submenu && item.submenu.length > 0 && (
              <span className="text-gray-400">▶</span>
            )}
          </div>
        );
      })}
    </div>
  );
};

/**
 * Hook for file context menu
 */
export function useFileContextMenu(
  onAction?: (action: string, items: IDEFile[] | IDEFolder[]) => void
) {
  const [contextMenu, setContextMenu] = useState<{
    visible: boolean;
    x: number;
    y: number;
    items: IDEFile[] | IDEFolder[];
  }>({
    visible: false,
    x: 0,
    y: 0,
    items: [],
  });

  const {
    bulkRename,
    bulkDelete,
  } = useAdvancedFileOperations();

  const showContextMenu = useCallback((
    event: React.MouseEvent,
    items: IDEFile[] | IDEFolder[]
  ) => {
    event.preventDefault();
    event.stopPropagation();

    setContextMenu({
      visible: true,
      x: event.clientX,
      y: event.clientY,
      items,
    });
  }, []);

  const hideContextMenu = useCallback(() => {
    setContextMenu(prev => ({ ...prev, visible: false }));
  }, []);

  const contextMenuItems: ContextMenuItem[] = [
    {
      id: 'open',
      label: 'Open',
      icon: '📂',
      shortcut: 'Enter',
      action: () => {
        onAction?.('open', contextMenu.items);
      },
    },
    {
      id: 'rename',
      label: 'Rename',
      icon: '✏️',
      shortcut: 'F2',
      action: () => {
        if (contextMenu.items.length === 1) {
          onAction?.('rename', contextMenu.items);
        }
      },
      disabled: contextMenu.items.length !== 1,
    },
    {
      id: 'duplicate',
      label: 'Duplicate',
      icon: '📋',
      shortcut: 'Ctrl+D',
      action: () => {
        onAction?.('duplicate', contextMenu.items);
      },
    },
    { separator: true },
    {
      id: 'cut',
      label: 'Cut',
      icon: '✂️',
      shortcut: 'Ctrl+X',
      action: () => {
        onAction?.('cut', contextMenu.items);
      },
    },
    {
      id: 'copy',
      label: 'Copy',
      icon: '📋',
      shortcut: 'Ctrl+C',
      action: () => {
        onAction?.('copy', contextMenu.items);
      },
    },
    {
      id: 'paste',
      label: 'Paste',
      icon: '📋',
      shortcut: 'Ctrl+V',
      action: () => {
        onAction?.('paste', contextMenu.items);
      },
    },
    { separator: true },
    {
      id: 'bulk-rename',
      label: 'Bulk Rename',
      icon: '🏷️',
      action: () => {
        bulkRename('{name}_{n}');
      },
      disabled: contextMenu.items.length < 2,
    },
    {
      id: 'move',
      label: 'Move to Folder',
      icon: '📁',
      action: () => {
        onAction?.('move', contextMenu.items);
      },
    },
    { separator: true },
    {
      id: 'delete',
      label: 'Delete',
      icon: '🗑️',
      shortcut: 'Delete',
      action: () => {
        bulkDelete();
      },
    },
  ];

  return {
    contextMenu: {
      ...contextMenu,
      items: contextMenuItems,
    },
    showContextMenu,
    hideContextMenu,
  };
}
