import React from 'react';

import { cn } from '../../utils/cn';

/**
 * Props for the ListItem component
 */
export interface ListItemProps extends React.HTMLAttributes<HTMLLIElement> {
  /**
   * The main content of the list item
   */
  children?: React.ReactNode;

  /**
   * Optional icon to display before the text
   */
  icon?: React.ReactNode;

  /**
   * Secondary text to display below main content
   */
  secondaryText?: React.ReactNode;

  /**
   * Whether the item is clickable (adds hover effects)
   * @default false
   */
  clickable?: boolean;

  /**
   * Whether the item is currently selected
   * @default false
   */
  selected?: boolean;

  /**
   * Whether the item is disabled
   * @default false
   */
  disabled?: boolean;

  /**
   * Dense (compact) padding
   * @default false
   */
  dense?: boolean;

  /**
   * Alignment of content
   * @default 'start'
   */
  align?: 'start' | 'center';

  /**
   * Additional CSS classes
   */
  className?: string;
}

/**
 * ListItem - Individual list item component
 * 
 * Simple Tailwind component for list items. Supports icons, secondary text,
 * clickable states, and selection highlighting.
 * 
 * @example
 * ```tsx
 * <List>
 *   <ListItem>Simple text item</ListItem>
 *   <ListItem icon={<Icon />}>Item with icon</ListItem>
 *   <ListItem secondaryText="Description">Item with subtitle</ListItem>
 * </List>
 * ```
 * 
 * @example
 * ```tsx
 * <List>
 *   <ListItem clickable onClick={() => console.log('clicked')}>
 *     Clickable item
 *   </ListItem>
 *   <ListItem selected>Selected item</ListItem>
 *   <ListItem disabled>Disabled item</ListItem>
 * </List>
 * ```
 */
export const ListItem = React.forwardRef<HTMLLIElement, ListItemProps>(
  (
    {
      children,
      icon,
      secondaryText,
      clickable = false,
      selected = false,
      disabled = false,
      dense = false,
      align = 'start',
      className,
      ...props
    },
    ref
  ) => {
    // Determine if we should show hover/click effects
    const isInteractive = clickable || props.onClick;

    return (
      <li
        ref={ref}
        className={cn(
          // Base styles
          'flex gap-3 w-full',

          // Padding
          dense ? 'px-3 py-2' : 'px-4 py-3',

          // Alignment
          align === 'center' ? 'items-center' : 'items-start',

          // Interactive states
          isInteractive && !disabled && [
            'cursor-pointer',
            'hover:bg-grey-100 dark:hover:bg-grey-800',
            'active:bg-grey-200 dark:active:bg-grey-700',
            'transition-colors duration-150',
          ],

          // Selected state
          selected && !disabled && 'bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300',

          // Disabled state
          disabled && 'opacity-50 cursor-not-allowed',

          // Custom className
          className
        )}
        aria-selected={selected}
        aria-disabled={disabled}
        {...props}
      >
        {/* Icon */}
        {icon && (
          <span className={cn('flex-shrink-0', dense ? 'w-5 h-5' : 'w-6 h-6', align === 'start' && 'mt-0.5')}>
            {icon}
          </span>
        )}

        {/* Content */}
        <div className="flex-1 min-w-0">
          {/* Primary text */}
          <div className={cn('text-base', dense && 'text-sm')}>{children}</div>

          {/* Secondary text */}
          {secondaryText && (
            <div
              className={cn(
                'text-grey-600 dark:text-grey-400',
                dense ? 'text-xs mt-0.5' : 'text-sm mt-1'
              )}
            >
              {secondaryText}
            </div>
          )}
        </div>
      </li>
    );
  }
);

ListItem.displayName = 'ListItem';
