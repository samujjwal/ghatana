import React, { useState, useRef, useEffect } from 'react';
import { tokens } from '@ghatana/tokens';

export interface MenuItem {
  key: string;
  label: string;
  icon?: React.ReactNode;
  disabled?: boolean;
  divider?: boolean;
  onClick?: () => void;
  children?: MenuItem[];
}

export interface MenuItemProps extends Omit<React.LiHTMLAttributes<HTMLLIElement>, 'children'> {
  children?: React.ReactNode;
  /** MUI-compatible disabled prop */
  disabled?: boolean;
  /** MUI-compatible selected prop */
  selected?: boolean;
  /** MUI-compatible value prop */
  value?: string | number | readonly string[];
  /** MUI-compatible dense prop */
  dense?: boolean;
}

/**
 * MenuItem component — renders as a clickable list item in menus and selects.
 * Works as a standalone clickable element in context menus, dropdowns, and select controls.
 */
export const MenuItem = React.forwardRef<HTMLLIElement, MenuItemProps>(
  ({ children, disabled, selected, dense, className, value, onClick, ...props }, ref) => {
    return (
      <li
        ref={ref}
        role="menuitem"
        aria-disabled={disabled}
        aria-selected={selected}
        data-value={value}
        onClick={disabled ? undefined : onClick}
        className={`flex cursor-pointer items-center px-4 ${dense ? 'py-1' : 'py-2'} text-sm transition-colors hover:bg-neutral-100 dark:hover:bg-neutral-800 ${
          selected ? 'bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300' : 'text-neutral-700 dark:text-neutral-200'
        } ${disabled ? 'pointer-events-none opacity-50' : ''} ${className ?? ''}`}
        {...props}
      >
        {children}
      </li>
    );
  },
);
MenuItem.displayName = 'MenuItem';

export interface MenuProps {
  /** Menu items */
  items: MenuItem[];
  /** Trigger element */
  trigger?: React.ReactNode;
  /** Open state (controlled) */
  open?: boolean;
  /** Open state change handler */
  onOpenChange?: (open: boolean) => void;
  /** Placement */
  placement?: 'bottom-start' | 'bottom-end' | 'top-start' | 'top-end' | 'left' | 'right';
  /** Size */
  size?: 'sm' | 'md' | 'lg';
  /** Additional class name */
  className?: string;
}

export const Menu: React.FC<MenuProps> = ({
  items,
  trigger,
  open: controlledOpen,
  onOpenChange,
  placement = 'bottom-start',
  size = 'md',
  className,
}) => {
  const [internalOpen, setInternalOpen] = useState(false);
  const [activeSubmenu, setActiveSubmenu] = useState<string | null>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLDivElement>(null);

  const isOpen = controlledOpen !== undefined ? controlledOpen : internalOpen;

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        menuRef.current &&
        triggerRef.current &&
        !menuRef.current.contains(event.target as Node) &&
        !triggerRef.current.contains(event.target as Node)
      ) {
        handleClose();
      }
    };

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen]);

  const handleToggle = () => {
    const newOpen = !isOpen;
    setInternalOpen(newOpen);
    onOpenChange?.(newOpen);
  };

  const handleClose = () => {
    setInternalOpen(false);
    onOpenChange?.(false);
    setActiveSubmenu(null);
  };

  const handleItemClick = (item: MenuItem) => {
    if (item.disabled) return;
    if (!item.children) {
      item.onClick?.();
      handleClose();
    }
  };

  const sizeConfig = {
    sm: { padding: `${tokens.spacing[1]} ${tokens.spacing[2]}`, fontSize: tokens.typography.fontSize.sm },
    md: { padding: `${tokens.spacing[2]} ${tokens.spacing[3]}`, fontSize: tokens.typography.fontSize.base },
    lg: { padding: `${tokens.spacing[3]} ${tokens.spacing[4]}`, fontSize: tokens.typography.fontSize.lg },
  };

  const config = sizeConfig[size];

  const placementStyles: Record<string, React.CSSProperties> = {
    'bottom-start': { top: '100%', left: 0, marginTop: tokens.spacing[1] },
    'bottom-end': { top: '100%', right: 0, marginTop: tokens.spacing[1] },
    'top-start': { bottom: '100%', left: 0, marginBottom: tokens.spacing[1] },
    'top-end': { bottom: '100%', right: 0, marginBottom: tokens.spacing[1] },
    left: { right: '100%', top: 0, marginRight: tokens.spacing[1] },
    right: { left: '100%', top: 0, marginLeft: tokens.spacing[1] },
  };

  const containerStyles: React.CSSProperties = {
    position: 'relative',
    display: 'inline-block',
  };

  const menuStyles: React.CSSProperties = {
    ...placementStyles[placement],
    position: 'absolute',
    minWidth: '200px',
    backgroundColor: tokens.colors.white,
    border: `${tokens.borderWidth[1]} solid ${tokens.colors.neutral[200]}`,
    borderRadius: tokens.borderRadius.lg,
    boxShadow: tokens.shadows.lg,
    padding: `${tokens.spacing[1]} 0`,
    zIndex: tokens.zIndex.dropdown,
    opacity: isOpen ? 1 : 0,
    visibility: isOpen ? 'visible' : 'hidden',
    transform: isOpen ? 'scale(1)' : 'scale(0.95)',
    transition: `all ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
  };

  const itemStyles = (item: MenuItem): React.CSSProperties => ({
    ...config,
    display: 'flex',
    alignItems: 'center',
    gap: tokens.spacing[2],
    width: '100%',
    backgroundColor: 'transparent',
    border: 'none',
    color: item.disabled ? tokens.colors.neutral[400] : tokens.colors.neutral[900],
    cursor: item.disabled ? 'not-allowed' : 'pointer',
    fontFamily: tokens.typography.fontFamily.sans,
    textAlign: 'left',
    transition: `background-color ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
    opacity: item.disabled ? 0.5 : 1,
  });

  const dividerStyles: React.CSSProperties = {
    height: '1px',
    backgroundColor: tokens.colors.neutral[200],
    margin: `${tokens.spacing[1]} 0`,
  };

  const renderMenuItem = (item: MenuItem, depth: number = 0) => {
    if (item.divider) {
      return <div key={item.key} style={dividerStyles} role="separator" />;
    }

    const hasSubmenu = item.children && item.children.length > 0;
    const isSubmenuActive = activeSubmenu === item.key;

    return (
      <div key={item.key} style={{ position: 'relative' }}>
        <button
          style={itemStyles(item)}
          onClick={() => handleItemClick(item)}
          onMouseEnter={() => hasSubmenu && setActiveSubmenu(item.key)}
          disabled={item.disabled}
          role="menuitem"
          aria-haspopup={hasSubmenu}
          aria-expanded={hasSubmenu ? isSubmenuActive : undefined}
          onMouseOver={(e) => {
            if (!item.disabled) {
              e.currentTarget.style.backgroundColor = tokens.colors.neutral[50];
            }
          }}
          onMouseOut={(e) => {
            if (!item.disabled) {
              e.currentTarget.style.backgroundColor = 'transparent';
            }
          }}
        >
          {item.icon && <span style={{ display: 'flex', alignItems: 'center' }}>{item.icon}</span>}
          <span style={{ flex: 1 }}>{item.label}</span>
          {hasSubmenu && (
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <polyline points="9 18 15 12 9 6" />
            </svg>
          )}
        </button>
        {hasSubmenu && isSubmenuActive && (
          <div
            style={{
              ...menuStyles,
              position: 'absolute',
              left: '100%',
              top: 0,
              marginLeft: tokens.spacing[1],
              opacity: 1,
              visibility: 'visible',
              transform: 'scale(1)',
            }}
          >
            {item.children!.map((child) => renderMenuItem(child, depth + 1))}
          </div>
        )}
      </div>
    );
  };

  return (
    <div style={containerStyles} className={className}>
      <div ref={triggerRef} onClick={handleToggle} style={{ cursor: 'pointer' }}>
        {trigger || (
          <button
            style={{
              ...config,
              backgroundColor: tokens.colors.white,
              border: `${tokens.borderWidth[1]} solid ${tokens.colors.neutral[300]}`,
              borderRadius: tokens.borderRadius.md,
              cursor: 'pointer',
              fontFamily: tokens.typography.fontFamily.sans,
            }}
          >
            Menu
          </button>
        )}
      </div>
      <div ref={menuRef} style={menuStyles} role="menu">
        {items.map((item) => renderMenuItem(item))}
      </div>
    </div>
  );
};

Menu.displayName = 'Menu';
