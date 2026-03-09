import React from 'react';

import { cn } from '../../utils/cn';

/**
 * List style variant (bullet, numbered, or no markers)
 */
export type ListVariant = 'bullet' | 'numbered' | 'none';

/**
 * Padding size for List
 */
export type ListPadding = 'none' | 'sm' | 'md' | 'lg';

/**
 * Props for the List component
 */
export interface ListProps extends React.HTMLAttributes<HTMLUListElement | HTMLOListElement> {
  /**
   * The content to display inside the List (ListItem children)
   */
  children?: React.ReactNode;

  /**
   * List style variant
   * @default 'none'
   */
  variant?: ListVariant;

  /**
   * Whether to show dividers between items
   * @default false
   */
  dividers?: boolean;

  /**
   * Dense (compact) spacing between items
   * @default false
   */
  dense?: boolean;

  /**
   * Padding around the list
   * @default 'none'
   */
  padding?: ListPadding;

  /**
   * Whether list is inside a bordered container (removes padding)
   * @default false
   */
  disablePadding?: boolean;

  /**
   * Additional CSS classes
   */
  className?: string;
}

/**
 * Padding class mappings
 */
const paddingClasses: Record<ListPadding, string> = {
  none: 'p-0',
  sm: 'p-2',
  md: 'p-4',
  lg: 'p-6',
};

/**
 * List - Semantic list component (ul/ol) with styling options
 * 
 * Simple Tailwind component for rendering ordered and unordered lists.
 * Works with ListItem component for consistent item styling.
 * 
 * @example
 * ```tsx
 * <List variant="bullet">
 *   <ListItem>Item 1</ListItem>
 *   <ListItem>Item 2</ListItem>
 *   <ListItem>Item 3</ListItem>
 * </List>
 * ```
 * 
 * @example
 * ```tsx
 * <List variant="none" dividers>
 *   <ListItem icon={<HomeIcon />}>Home</ListItem>
 *   <ListItem icon={<UserIcon />}>Profile</ListItem>
 *   <ListItem icon={<SettingsIcon />}>Settings</ListItem>
 * </List>
 * ```
 */
export const List = React.forwardRef<HTMLUListElement | HTMLOListElement, ListProps>(
  (
    {
      children,
      variant = 'none',
      dividers = false,
      dense = false,
      padding = 'none',
      disablePadding = false,
      className,
      ...props
    },
    ref
  ) => {
    // Choose semantic HTML element based on variant
    const Component = variant === 'numbered' ? 'ol' : 'ul';

    // Build list-style class based on variant
    const listStyleClass =
      variant === 'bullet'
        ? 'list-disc list-inside'
        : variant === 'numbered'
          ? 'list-decimal list-inside'
          : 'list-none';

    return (
      <Component
        ref={ref as never}
        className={cn(
          // Base styles
          'w-full',

          // List style (bullets, numbers, or none)
          listStyleClass,

          // Spacing between items
          dense ? 'space-y-1' : 'space-y-2',

          // Padding
          !disablePadding && paddingClasses[padding],

          // Dividers (add border to children via CSS)
          dividers && '[&>*]:border-b [&>*]:border-grey-200 [&>*:last-child]:border-b-0',

          // Custom className
          className
        )}
        {...props}
      >
        {children}
      </Component>
    );
  }
);

List.displayName = 'List';
