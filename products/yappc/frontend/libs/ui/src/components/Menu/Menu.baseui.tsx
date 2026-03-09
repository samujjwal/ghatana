import { Menu as BaseMenu } from '@base-ui/react/menu';
import { forwardRef } from 'react';

import { cn } from '../../utils/cn';

import type { ReactNode } from 'react';

/**
 * Menu shape variants
 */
export type MenuShape = 'rounded' | 'square' | 'soft';

/**
 * Menu elevation (shadow depth)
 */
export type MenuElevation = 0 | 1 | 2 | 4 | 8;

/**
 * Props for the Menu component
 */
export interface MenuProps {
  /**
   * Whether the menu is open
   */
  open?: boolean;

  /**
   * Callback fired when the menu requests to be closed
   */
  onOpenChange?: (open: boolean) => void;

  /**
   * The trigger element for the menu
   */
  trigger?: ReactNode;

  /**
   * Menu items (children)
   */
  children: ReactNode;

  /**
   * Menu shape variant
   * @default 'rounded'
   */
  shape?: MenuShape;

  /**
   * Menu elevation (shadow depth)
   * @default 2
   */
  elevation?: MenuElevation;

  /**
   * Additional CSS class for the menu popup
   */
  className?: string;
}

/**
 * Props for the MenuItem component
 */
export interface MenuItemProps {
  /**
   * Menu item content (optional, can use text/icon instead)
   */
  children?: ReactNode;

  /**
   * Icon to display at the start of the menu item
   */
  icon?: ReactNode;

  /**
   * Primary text for the menu item (used when not providing children)
   */
  text?: ReactNode;

  /**
   * Secondary text for the menu item
   */
  secondaryText?: ReactNode;

  /**
   * Dense padding for the menu item
   * @default false
   */
  dense?: boolean;

  /**
   * Whether the menu item is disabled
   */
  disabled?: boolean;

  /**
   * Click handler
   */
  onClick?: (event: React.MouseEvent<HTMLDivElement>) => void;

  /**
   * Additional CSS class
   */
  className?: string;
}

/**
 * Props for the MenuDivider component
 */
export interface MenuDividerProps {
  /**
   * Additional CSS class
   */
  className?: string;
}

/**
 * Menu component for dropdown selections using Base UI primitives.
 * 
 * Features:
 * - 3 shape variants: rounded, soft, square
 * - 5 elevation levels (shadow depth)
 * - Keyboard navigation (Arrow keys, Enter, Escape)
 * - Focus management and restoration
 * - Click-outside to close
 * - Accessible with proper ARIA attributes
 * 
 * @example
 * ```tsx
 * <Menu
 *   trigger={<Button>Open Menu</Button>}
 *   shape="rounded"
 *   elevation={2}
 * >
 *   <MenuItem icon={<HomeIcon />} text="Home" />
 *   <MenuItem icon={<SettingsIcon />} text="Settings" />
 *   <MenuDivider />
 *   <MenuItem icon={<LogoutIcon />} text="Logout" />
 * </Menu>
 * ```
 */
export const Menu = forwardRef<HTMLDivElement, MenuProps>(
  (
    {
      open,
      onOpenChange,
      trigger,
      children,
      shape = 'rounded',
      elevation = 2,
      className,
    },
    ref
  ) => {
    // Shape classes for border radius
    const shapeClasses: Record<MenuShape, string> = {
      rounded: 'rounded-md',  // 6px
      soft: 'rounded-lg',     // 8px
      square: 'rounded-sm',   // 2px
    };

    // Elevation classes for box shadow
    const elevationClasses: Record<MenuElevation, string> = {
      0: 'shadow-none',
      1: 'shadow-sm',    // 0 1px 2px
      2: 'shadow',       // 0 1px 3px
      4: 'shadow-md',    // 0 4px 6px
      8: 'shadow-lg',    // 0 10px 15px
    };

    return (
      <BaseMenu.Root open={open} onOpenChange={onOpenChange}>
        {trigger && <BaseMenu.Trigger>{trigger}</BaseMenu.Trigger>}

        <BaseMenu.Portal>
          <BaseMenu.Positioner sideOffset={4}>
            <BaseMenu.Popup
              ref={ref}
              className={cn(
                // Base styles
                'min-w-[180px] bg-white py-2 outline-none',
                // Shape
                shapeClasses[shape],
                // Elevation
                elevationClasses[elevation],
                // Animations
                'transition-all duration-150',
                'data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95',
                'data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95',
                'origin-[var(--transform-origin)]',
                // Custom classes
                className
              )}
            >
              {children}
            </BaseMenu.Popup>
          </BaseMenu.Positioner>
        </BaseMenu.Portal>
      </BaseMenu.Root>
    );
  }
);

Menu.displayName = 'Menu';

/**
 * MenuItem component for menu options.
 * 
 * Features:
 * - Optional icon and secondary text
 * - Dense padding variant
 * - Hover and focus states
 * - Keyboard navigation support
 * - Disabled state
 * 
 * @example
 * ```tsx
 * <MenuItem 
 *   icon={<HomeIcon />} 
 *   text="Home"
 *   secondaryText="Go to homepage"
 * />
 * ```
 */
export const MenuItem = forwardRef<HTMLDivElement, MenuItemProps>(
  (
    {
      children,
      icon,
      text,
      secondaryText,
      dense = false,
      disabled = false,
      onClick,
      className,
    },
    ref
  ) => {
    // If children are provided, use them directly
    if (children) {
      return (
        <BaseMenu.Item
          ref={ref}
          disabled={disabled}
          onClick={onClick}
          className={cn(
            'flex items-center gap-3 mx-1 rounded-sm cursor-pointer outline-none select-none',
            'text-grey-900 transition-colors',
            dense ? 'px-2 py-1' : 'px-4 py-2',
            'hover:bg-grey-100 focus:bg-grey-100',
            'data-[highlighted]:bg-grey-100',
            disabled && 'opacity-50 cursor-not-allowed pointer-events-none',
            className
          )}
        >
          {children}
        </BaseMenu.Item>
      );
    }

    // Otherwise, render with icon and text structure
    return (
      <BaseMenu.Item
        ref={ref}
        disabled={disabled}
        onClick={onClick}
        className={cn(
          'flex items-center gap-3 mx-1 rounded-sm cursor-pointer outline-none select-none',
          'text-grey-900 transition-colors',
          dense ? 'px-2 py-1' : 'px-4 py-2',
          'hover:bg-grey-100 focus:bg-grey-100',
          'data-[highlighted]:bg-grey-100',
          disabled && 'opacity-50 cursor-not-allowed pointer-events-none',
          className
        )}
      >
        {icon && (
          <span className="flex items-center justify-center w-5 h-5 text-grey-600">
            {icon}
          </span>
        )}
        <div className="flex-1">
          {text && (
            <div className={dense ? 'text-sm' : 'text-base'}>
              {text}
            </div>
          )}
          {secondaryText && (
            <div className="text-xs text-grey-600 mt-0.5">
              {secondaryText}
            </div>
          )}
        </div>
      </BaseMenu.Item>
    );
  }
);

MenuItem.displayName = 'MenuItem';

/**
 * MenuDivider component for separating menu sections.
 * 
 * @example
 * ```tsx
 * <MenuItem text="Copy" />
 * <MenuItem text="Paste" />
 * <MenuDivider />
 * <MenuItem text="Delete" />
 * ```
 */
export const MenuDivider = forwardRef<HTMLHRElement, MenuDividerProps>(
  ({ className }, ref) => {
    return (
      <hr
        ref={ref}
        className={cn(
          'my-2 mx-2 border-t border-grey-200',
          className
        )}
      />
    );
  }
);

MenuDivider.displayName = 'MenuDivider';

/**
 * Re-export Base UI components for advanced use cases
 */
export const MenuTrigger = BaseMenu.Trigger;
export const MenuPopup = BaseMenu.Popup;
export const MenuPortal = BaseMenu.Portal;
export const MenuPositioner = BaseMenu.Positioner;
