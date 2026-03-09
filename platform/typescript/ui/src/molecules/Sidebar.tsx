import React, { useState } from 'react';
import { tokens } from '@ghatana/tokens';

export interface SidebarItem {
  id: string;
  label: string;
  icon?: React.ReactNode;
  href?: string;
  onClick?: () => void;
  children?: SidebarItem[];
  badge?: string | number;
}

export interface SidebarProps {
  items: SidebarItem[];
  collapsible?: boolean;
  defaultCollapsed?: boolean;
  onItemClick?: (item: SidebarItem) => void;
  className?: string;
}

/**
 * Sidebar component for collapsible navigation
 */
export const Sidebar: React.FC<SidebarProps> = ({
  items,
  collapsible = true,
  defaultCollapsed = false,
  onItemClick,
  className,
}) => {
  const [collapsed, setCollapsed] = useState(defaultCollapsed);
  const [expandedItems, setExpandedItems] = useState<Set<string>>(new Set());

  const toggleCollapsed = () => {
    setCollapsed(!collapsed);
  };

  const toggleExpanded = (id: string) => {
    const newExpanded = new Set(expandedItems);
    if (newExpanded.has(id)) {
      newExpanded.delete(id);
    } else {
      newExpanded.add(id);
    }
    setExpandedItems(newExpanded);
  };

  const sidebarStyles: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    height: '100%',
    backgroundColor: tokens.colors.neutral[50],
    borderRight: `1px solid ${tokens.colors.neutral[200]}`,
    transition: `width ${tokens.transitions.duration.normal} ${tokens.transitions.easing.easeInOut}`,
    width: collapsed ? '60px' : '250px',
    overflow: 'hidden',
  };

  const headerStyles: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: tokens.spacing[3],
    borderBottom: `1px solid ${tokens.colors.neutral[200]}`,
  };

  const toggleButtonStyles: React.CSSProperties = {
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    padding: tokens.spacing[1],
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  };

  const contentStyles: React.CSSProperties = {
    flex: 1,
    overflowY: 'auto',
    padding: tokens.spacing[2],
  };

  const renderItem = (item: SidebarItem, level: number = 0) => {
    const isExpanded = expandedItems.has(item.id);
    const hasChildren = item.children && item.children.length > 0;

    const itemStyles: React.CSSProperties = {
      display: 'flex',
      alignItems: 'center',
      gap: tokens.spacing[2],
      padding: `${tokens.spacing[2]} ${tokens.spacing[3]}`,
      marginBottom: tokens.spacing[1],
      borderRadius: tokens.borderRadius.md,
      cursor: 'pointer',
      backgroundColor: 'transparent',
      border: 'none',
      width: '100%',
      textAlign: 'left',
      fontSize: tokens.typography.fontSize.sm,
      color: tokens.colors.neutral[700],
      transition: `all ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
    };

    const handleClick = () => {
      if (hasChildren) {
        toggleExpanded(item.id);
      }
      item.onClick?.();
      onItemClick?.(item);
    };

    return (
      <div key={item.id}>
        <button
          style={itemStyles}
          onClick={handleClick}
          onMouseEnter={(e) => {
            e.currentTarget.style.backgroundColor = tokens.colors.neutral[100];
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.backgroundColor = 'transparent';
          }}
        >
          {item.icon && <span style={{ display: 'flex', flexShrink: 0 }}>{item.icon}</span>}
          {!collapsed && (
            <>
              <span style={{ flex: 1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {item.label}
              </span>
              {item.badge && (
                <span
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    minWidth: '20px',
                    height: '20px',
                    padding: '0 4px',
                    borderRadius: tokens.borderRadius.full,
                    backgroundColor: tokens.colors.primary[600],
                    color: tokens.colors.neutral[0],
                    fontSize: tokens.typography.fontSize.xs,
                    fontWeight: 600,
                  }}
                >
                  {item.badge}
                </span>
              )}
              {hasChildren && (
                <span
                  style={{
                    transform: isExpanded ? 'rotate(90deg)' : 'rotate(0deg)',
                    transition: `transform ${tokens.transitions.duration.fast}`,
                  }}
                >
                  ›
                </span>
              )}
            </>
          )}
        </button>

        {hasChildren && isExpanded && !collapsed && (
          <div style={{ marginLeft: tokens.spacing[4] }}>
            {item.children!.map((child) => renderItem(child, level + 1))}
          </div>
        )}
      </div>
    );
  };

  return (
    <div style={sidebarStyles} className={className}>
      {collapsible && (
        <div style={headerStyles}>
          {!collapsed && <span style={{ fontWeight: 600 }}>Menu</span>}
          <button style={toggleButtonStyles} onClick={toggleCollapsed} aria-label="Toggle sidebar">
            {collapsed ? '›' : '‹'}
          </button>
        </div>
      )}
      <div style={contentStyles}>
        {items.map((item) => renderItem(item))}
      </div>
    </div>
  );
};

Sidebar.displayName = 'Sidebar';
