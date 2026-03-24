/**
 * IDE UI Components - ContextMenu, TabBar Bridge
 * 
 * @deprecated Use ContextMenu, TabBar from @ghatana/yappc-canvas
 * @see /docs/LIBRARY_CONSOLIDATION_PLAN.md
 */

import React, { useEffect, useState, useCallback } from 'react';

// ============================================================================
// ContextMenu
// ============================================================================

export interface ContextMenuProps {
  /** Menu items to display */
  items: ContextMenuItem[];
  /** Trigger element or selector */
  children?: React.ReactNode;
  /** Position coordinates (if controlled) */
  position?: { x: number; y: number };
  /** Visibility state (if controlled) */
  isOpen?: boolean;
  /** Close handler */
  onClose?: () => void;
  /** Item click handler */
  onItemClick?: (item: ContextMenuItem) => void;
  /** Additional CSS classes */
  className?: string;
}

export interface ContextMenuItem {
  id: string;
  label: string;
  icon?: string;
  shortcut?: string;
  disabled?: boolean;
  separator?: boolean;
  submenu?: ContextMenuItem[];
  action?: () => void;
}

/**
 * ContextMenu - Bridge Component
 */
export const ContextMenu: React.FC<ContextMenuProps> = ({
  items,
  children,
  position,
  isOpen: controlledOpen,
  onClose,
  onItemClick,
  className,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [menuPosition, setMenuPosition] = useState({ x: 0, y: 0 });

  useEffect(() => {
    console.warn(
      '[MIGRATION] ContextMenu from @ghatana/yappc-ide is deprecated. ' +
      'Use ContextMenu or CanvasContextMenu from @ghatana/yappc-canvas.'
    );
  }, []);

  const handleContextMenu = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    setMenuPosition({ x: e.clientX, y: e.clientY });
    setIsOpen(true);
  }, []);

  const handleClose = useCallback(() => {
    setIsOpen(false);
    onClose?.();
  }, [onClose]);

  const handleItemClick = useCallback((item: ContextMenuItem) => {
    item.action?.();
    onItemClick?.(item);
    handleClose();
  }, [onItemClick, handleClose]);

  const open = controlledOpen !== undefined ? controlledOpen : isOpen;
  const pos = position || menuPosition;

  return (
    <div className={`context-menu-wrapper ${className || ''}`} onContextMenu={handleContextMenu}>
      {children}
      {open && (
        <div 
          className="context-menu"
          style={{ position: 'fixed', left: pos.x, top: pos.y, zIndex: 1000 }}
        >
          {items.map(item => (
            <div key={item.id}>
              {item.separator ? (
                <hr className="context-menu-separator" />
              ) : (
                <button
                  className={`context-menu-item ${item.disabled ? 'disabled' : ''}`}
                  onClick={() => !item.disabled && handleItemClick(item)}
                  disabled={item.disabled}
                >
                  {item.icon && <span className="item-icon">{item.icon}</span>}
                  <span className="item-label">{item.label}</span>
                  {item.shortcut && <span className="item-shortcut">{item.shortcut}</span>}
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

// ============================================================================
// TabBar
// ============================================================================

export interface TabBarProps {
  /** Array of tabs */
  tabs: TabItem[];
  /** Active tab index or ID */
  activeTab?: string | number;
  /** Tab change handler */
  onTabChange?: (tabId: string) => void;
  /** Tab close handler */
  onTabClose?: (tabId: string) => void;
  /** Show close buttons */
  closable?: boolean;
  /** Additional CSS classes */
  className?: string;
}

export interface TabItem {
  id: string;
  label: string;
  icon?: string;
  tooltip?: string;
  modified?: boolean;
  disabled?: boolean;
}

/**
 * TabBar - Bridge Component
 */
export const TabBar: React.FC<TabBarProps> = ({
  tabs,
  activeTab,
  onTabChange,
  onTabClose,
  closable = true,
  className,
}) => {
  useEffect(() => {
    console.warn(
      '[MIGRATION] TabBar from @ghatana/yappc-ide is deprecated. ' +
      'Use TabBar or CanvasTabBar from @ghatana/yappc-canvas.'
    );
  }, []);

  return (
    <div className={`tab-bar ${className || ''}`}>
      {tabs.map(tab => (
        <div
          key={tab.id}
          className={`tab-item ${activeTab === tab.id ? 'active' : ''} ${tab.modified ? 'modified' : ''}`}
          onClick={() => onTabChange?.(tab.id)}
          title={tab.tooltip || tab.label}
        >
          {tab.icon && <span className="tab-icon">{tab.icon}</span>}
          <span className="tab-label">{tab.label}</span>
          {tab.modified && <span className="tab-modified-indicator">●</span>}
          {closable && (
            <button 
              className="tab-close"
              onClick={(e) => {
                e.stopPropagation();
                onTabClose?.(tab.id);
              }}
            >
              ×
            </button>
          )}
        </div>
      ))}
    </div>
  );
};

// Re-export with Canvas prefix
export { ContextMenu as CanvasContextMenu };
export { TabBar as CanvasTabBar };
