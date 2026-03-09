import { Tabs as BaseTabs } from '@base-ui/react/tabs';
import React, { forwardRef } from 'react';

import { cn } from '../../utils/cn';

import type { ReactNode } from 'react';

const TAB_LIST_BASE_CLASSES = 'flex gap-1 border-b border-grey-200';

const flattenChildren = (children: React.ReactNode): React.ReactNode[] => {
  const result: React.ReactNode[] = [];
  React.Children.forEach(children, (child) => {
    if (React.isValidElement(child) && child.type === React.Fragment) {
      const fragmentChildren = (child.props as { children?: React.ReactNode }).children;
      if (fragmentChildren !== undefined) {
        result.push(...flattenChildren(fragmentChildren));
        return;
      }
    } else {
      result.push(child);
    }
  });
  return result;
};

const getDisplayName = (type: React.ElementType): string => {
  if (typeof type === 'string') {
    return type;
  }
  if (typeof type === 'function') {
    return type.displayName ?? type.name ?? '';
  }
  if (typeof (type as { displayName?: string }).displayName === 'string') {
    return (type as { displayName?: string }).displayName ?? '';
  }
  return '';
};

const isElementNamed = (element: React.ReactNode, name: string): boolean =>
  React.isValidElement(element) && getDisplayName(element.type as unknown) === name;

/**
 * Tabs variant types
 */
export type TabsVariant = 'standard' | 'pills' | 'underline';

/**
 * Tabs orientation
 */
export type TabsOrientation = 'horizontal' | 'vertical';

/**
 * Tabs size variants
 */
export type TabsSize = 'small' | 'medium' | 'large';

/**
 * Props for the Tabs component
 */
export interface TabsProps {
  /**
   * The value of the currently selected tab
   */
  value?: number | string;

  /**
   * Callback fired when the tab changes
   */
  onValueChange?: (value: number | string) => void;

  /**
   * Default selected tab value
   */
  defaultValue?: number | string;

  /**
   * Tab list and panels
   */
  children: ReactNode;

  /**
   * Tabs visual variant
   * @default 'standard'
   */
  variant?: TabsVariant;

  /**
   * Tabs orientation
   * @default 'horizontal'
   */
  orientation?: TabsOrientation;

  /**
   * Tabs size
   * @default 'medium'
   */
  size?: TabsSize;

  /**
   * Additional CSS class
   */
  className?: string;
}

/**
 * Props for TabsList component
 */
export interface TabsListProps {
  /**
   * Tab elements
   */
  children: ReactNode;

  /**
   * Additional CSS class
   */
  className?: string;
}

/**
 * Props for Tab component
 */
export interface TabProps extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'value'> {
  /**
   * Tab value
   */
  value: number | string;

  /**
   * Tab label
   */
  children?: ReactNode;

  /**
   * Icon at the start of the tab
   */
  startIcon?: ReactNode;

  /**
   * Icon at the end of the tab
   */
  endIcon?: ReactNode;

  /**
   * Badge to display with the tab
   */
  badge?: ReactNode;

  /**
   * Whether the tab is disabled
   */
  disabled?: boolean;

  /**
   * Additional CSS class
   */
  className?: string;
}

/**
 * Props for TabPanel component
 */
export interface TabPanelProps {
  /**
   * Panel value (must match Tab value)
   */
  value: number | string;

  /**
   * Panel content
   */
  children: ReactNode;

  /**
   * Keep content mounted when not active
   * @default false
   */
  keepMounted?: boolean;

  /**
   * Additional CSS class
   */
  className?: string;
}

/**
 * Tabs component for tabbed navigation using Base UI primitives.
 *
 * Features:
 * - 3 visual variants: standard, pills, underline
 * - Horizontal and vertical orientation
 * - 3 size variants: small, medium, large
 * - Keyboard navigation (Arrow keys, Home, End)
 * - Focus management
 * - Accessible with proper ARIA attributes
 *
 * @example
 * ```tsx
 * <Tabs value={activeTab} onValueChange={setActiveTab} variant="standard">
 *   <TabsList>
 *     <Tab value={0}>Tab 1</Tab>
 *     <Tab value={1} startIcon={<Icon />}>Tab 2</Tab>
 *   </TabsList>
 *   <TabPanel value={0}>Content 1</TabPanel>
 *   <TabPanel value={1}>Content 2</TabPanel>
 * </Tabs>
 * ```
 */
export const Tabs = forwardRef<HTMLDivElement, TabsProps>(
  (
    {
      value,
      onValueChange,
      defaultValue,
      children,
      variant = 'standard',
      orientation = 'horizontal',
      size = 'medium',
      className,
    },
    ref
  ) => {
    const normalizedChildren = React.useMemo(() => {
      const flattened = flattenChildren(children);
      const hasExplicitList = flattened.some((child) =>
        isElementNamed(child, 'TabsList') ||
        isElementNamed(child, 'BaseTabsList') ||
        isElementNamed(child, 'TabsListForwardRef')
      );

      if (hasExplicitList) {
        return children;
      }

      const tabElements: React.ReactNode[] = [];
      const otherElements: React.ReactNode[] = [];

      flattened.forEach((child) => {
        if (isElementNamed(child, 'Tab')) {
          tabElements.push(child);
        } else {
          otherElements.push(child);
        }
      });

      if (!tabElements.length) {
        return children;
      }

      const normalizedOtherElements = otherElements.map((child, index) => (
        React.isValidElement(child) && child.key == null
          ? React.cloneElement(child, { key: `auto-tabs-child-${index}` })
          : child
      ));

      return [
        (
          <BaseTabs.List key="auto-tabs-list" className={TAB_LIST_BASE_CLASSES}>
            {tabElements}
          </BaseTabs.List>
        ),
        ...normalizedOtherElements,
      ];
    }, [children]);

    return (
      <BaseTabs.Root
        ref={ref}
        value={value}
        onValueChange={onValueChange}
        defaultValue={defaultValue}
        orientation={orientation}
        className={cn(
          'flex',
          orientation === 'horizontal' ? 'flex-col' : 'flex-row',
          className
        )}
        data-variant={variant}
        data-size={size}
      >
        {normalizedChildren}
      </BaseTabs.Root>
    );
  }
);

Tabs.displayName = 'Tabs';

/**
 * TabsList component for containing Tab elements.
 *
 * @example
 * ```tsx
 * <TabsList>
 *   <Tab value={0}>First</Tab>
 *   <Tab value={1}>Second</Tab>
 * </TabsList>
 * ```
 */
export const TabsList = forwardRef<HTMLDivElement, TabsListProps>(
  ({ children, className }, ref) => {
    return (
      <BaseTabs.List
        ref={ref}
        className={cn(TAB_LIST_BASE_CLASSES, className)}
      >
        {children}
      </BaseTabs.List>
    );
  }
);

TabsList.displayName = 'TabsList';

/**
 * Tab component for individual tab buttons.
 *
 * Features:
 * - Optional start/end icons
 * - Optional badge
 * - Hover and selected states
 * - Keyboard navigation support
 * - Disabled state
 *
 * @example
 * ```tsx
 * <Tab
 *   value={0}
 *   startIcon={<HomeIcon />}
 *   badge={<Badge>3</Badge>}
 * >
 *   Home
 * </Tab>
 * ```
 */
export const Tab = forwardRef<HTMLButtonElement, TabProps>(
  (
    {
      value,
      children,
      startIcon,
      endIcon,
      badge,
      disabled = false,
      className,
      ...rest
    },
    ref
  ) => {
    return (
      <BaseTabs.Tab
        ref={ref}
        value={value}
        disabled={disabled}
        {...rest}
        className={cn(
          // Base styles
          'relative flex items-center gap-2 px-4 py-2',
          'text-sm font-medium text-grey-700 transition-all',
          'cursor-pointer outline-none select-none',
          // Size variants (inherited from parent via data-size)
          'data-[size=small]:px-3 data-[size=small]:py-1.5 data-[size=small]:text-xs',
          'data-[size=large]:px-6 data-[size=large]:py-3 data-[size=large]:text-base',
          // Hover state
          'hover:text-grey-900 hover:bg-grey-50',
          // Selected state - standard variant
          'data-[variant=standard]:data-[selected]:text-primary-600',
          'data-[variant=standard]:data-[selected]:font-semibold',
          'data-[variant=standard]:data-[selected]:border-b-2',
          'data-[variant=standard]:data-[selected]:border-primary-600',
          'data-[variant=standard]:data-[selected]:-mb-[2px]',
          // Selected state - pills variant
          'data-[variant=pills]:rounded-md',
          'data-[variant=pills]:data-[selected]:bg-primary-500',
          'data-[variant=pills]:data-[selected]:text-white',
          // Selected state - underline variant
          'data-[variant=underline]:data-[selected]:text-primary-600',
          'data-[variant=underline]:data-[selected]:font-semibold',
          'data-[variant=underline]:data-[selected]:after:absolute',
          'data-[variant=underline]:data-[selected]:after:bottom-0',
          'data-[variant=underline]:data-[selected]:after:left-0',
          'data-[variant=underline]:data-[selected]:after:right-0',
          'data-[variant=underline]:data-[selected]:after:h-0.5',
          'data-[variant=underline]:data-[selected]:after:bg-primary-600',
          // Focus state
          'focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2',
          // Disabled state
          disabled && 'opacity-50 cursor-not-allowed pointer-events-none',
          className
        )}
      >
        {startIcon && (
          <span className="flex items-center justify-center w-5 h-5">
            {startIcon}
          </span>
        )}
        <span>{children}</span>
        {badge && <span>{badge}</span>}
        {endIcon && (
          <span className="flex items-center justify-center w-5 h-5">
            {endIcon}
          </span>
        )}
      </BaseTabs.Tab>
    );
  }
);

Tab.displayName = 'Tab';

/**
 * TabPanel component for tab content areas.
 *
 * Features:
 * - Content shown when tab is selected
 * - Optional keep-mounted mode
 * - Smooth transitions
 * - Proper ARIA attributes
 *
 * @example
 * ```tsx
 * <TabPanel value={0}>
 *   <p>Content for first tab</p>
 * </TabPanel>
 * ```
 */
export const TabPanel = forwardRef<HTMLDivElement, TabPanelProps>(
  ({ value, children, keepMounted = false, className }, ref) => {
    return (
      <BaseTabs.Panel
        ref={ref}
        value={value}
        keepMounted={keepMounted}
        className={cn(
          'p-6 outline-none',
          'data-[hidden]:hidden',
          className
        )}
      >
        {children}
      </BaseTabs.Panel>
    );
  }
);

TabPanel.displayName = 'TabPanel';

/**
 * Re-export Base UI components for advanced use cases
 */
export const TabsRoot = BaseTabs.Root;
export const TabsIndicator = BaseTabs.Indicator;
