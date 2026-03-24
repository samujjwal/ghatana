import { Collapsible } from '@base-ui/react/collapsible';
import * as React from 'react';

import { cn } from '../../utils/cn';

/**
 * AccordionItem component props
 */
export interface AccordionItemProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'title'> {
  /**
   * Unique value for the accordion item
   */
  value: string;
  /**
   * Title text shown in the header
   */
  title: React.ReactNode;
  /**
   * Content to display when expanded
   */
  children?: React.ReactNode;
  /**
   * Whether the item is disabled
   * @default false
   */
  disabled?: boolean;
  /**
   * Custom icon for the header (defaults to chevron)
   */
  icon?: React.ReactNode;
  /**
   * Whether the item is initially expanded
   * @default false
   */
  defaultOpen?: boolean;
}

/**
 * AccordionItem component
 *
 * Individual collapsible item within an Accordion
 */
export const AccordionItem = React.forwardRef<HTMLDivElement, AccordionItemProps>(
  ({ value, title, children, disabled, icon, defaultOpen = false, className, ...props }, ref) => {
    const [isOpen, setIsOpen] = React.useState(defaultOpen);

    return (
      <Collapsible.Root
        ref={ref}
        open={isOpen}
        onOpenChange={setIsOpen}
        disabled={disabled}
        className={cn('border-b border-grey-200 dark:border-grey-700 last:border-b-0', className)}
        {...props}
      >
        <Collapsible.Trigger
          className={cn(
            'w-full flex items-center justify-between px-4 py-3',
            'text-left font-medium text-grey-900 dark:text-white',
            'hover:bg-grey-50 dark:hover:bg-grey-800',
            'transition-colors duration-200',
            'focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-inset',
            disabled && 'opacity-50 cursor-not-allowed hover:bg-transparent'
          )}
        >
          <span>{title}</span>
          <span
            className={cn(
              'text-grey-600 dark:text-grey-400',
              'transition-transform duration-200',
              isOpen && 'rotate-180'
            )}
          >
            {icon || (
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            )}
          </span>
        </Collapsible.Trigger>

        <Collapsible.Panel
          className={cn(
            'overflow-hidden',
            'transition-all duration-300 ease-in-out',
            'data-[state=open]:max-h-[1000px] data-[state=open]:opacity-100',
            'data-[state=closed]:max-h-0 data-[state=closed]:opacity-0'
          )}
        >
          <div className="px-4 py-3 text-grey-700 dark:text-grey-300">{children}</div>
        </Collapsible.Panel>
      </Collapsible.Root>
    );
  }
);

AccordionItem.displayName = 'AccordionItem';

/**
 * Accordion component props
 */
export interface AccordionProps extends React.HTMLAttributes<HTMLDivElement> {
  /**
   * Accordion items (AccordionItem components)
   */
  children: React.ReactNode;
  /**
   * Allow multiple items to be expanded simultaneously
   * @default false
   */
  multiple?: boolean;
  /**
   * Controlled value(s) for expanded items
   */
  value?: string | string[];
  /**
   * Callback when expanded state changes
   */
  onValueChange?: (value: string | string[]) => void;
  /**
   * Default expanded value(s)
   */
  defaultValue?: string | string[];
}

/**
 * Accordion component for collapsible content sections
 *
 * @example
 * ```tsx
 * <Accordion>
 *   <AccordionItem value="item-1" title="Section 1">
 *     Content for section 1
 *   </AccordionItem>
 *   <AccordionItem value="item-2" title="Section 2">
 *     Content for section 2
 *   </AccordionItem>
 * </Accordion>
 * ```
 *
 * @example Controlled with multiple expanded
 * ```tsx
 * const [expanded, setExpanded] = useState(['item-1']);
 *
 * <Accordion multiple value={expanded} onValueChange={setExpanded}>
 *   <AccordionItem value="item-1" title="First">Content 1</AccordionItem>
 *   <AccordionItem value="item-2" title="Second">Content 2</AccordionItem>
 * </Accordion>
 * ```
 */
export const Accordion = React.forwardRef<HTMLDivElement, AccordionProps>(
  ({ children, className, ...props }, ref) => {
    return (
      <div
        ref={ref}
        className={cn(
          'border border-grey-200 dark:border-grey-700',
          'rounded-lg overflow-hidden',
          'bg-white dark:bg-grey-900',
          className
        )}
        {...props}
      >
        {children}
      </div>
    );
  }
);

Accordion.displayName = 'Accordion';
