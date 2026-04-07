import { Popover as BasePopover } from '@base-ui/react/popover';
import * as React from 'react';

import { cn } from '../utils/cn';

export type PopoverPlacement =
  | 'top'
  | 'top-start'
  | 'top-end'
  | 'right'
  | 'right-start'
  | 'right-end'
  | 'bottom'
  | 'bottom-start'
  | 'bottom-end'
  | 'left'
  | 'left-start'
  | 'left-end';

export interface PopoverProps {
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  defaultOpen?: boolean;
  trigger: React.ReactNode;
  children: React.ReactNode;
  placement?: PopoverPlacement;
  showArrow?: boolean;
  offset?: number;
  className?: string;
  title?: React.ReactNode;
  description?: React.ReactNode;
}

export const Popover = React.forwardRef<HTMLDivElement, PopoverProps>(
  (
    {
      open,
      onOpenChange,
      defaultOpen = false,
      trigger,
      children,
      placement = 'bottom',
      showArrow = true,
      offset = 8,
      className,
      title,
      description,
    },
    ref
  ) => {
    return (
      <BasePopover.Root
        open={open}
        onOpenChange={onOpenChange}
        defaultOpen={defaultOpen}
      >
        <BasePopover.Trigger>{trigger}</BasePopover.Trigger>

        <BasePopover.Portal>
          <BasePopover.Positioner
            side={
              placement.split('-')[0] as 'top' | 'right' | 'bottom' | 'left'
            }
            sideOffset={offset}
          >
            <BasePopover.Popup
              ref={ref}
              className={cn(
                'z-50 rounded-lg border border-grey-200 dark:border-grey-700',
                'bg-white dark:bg-grey-900 shadow-lg',
                'p-4 max-w-sm',
                'data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95',
                'data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95',
                'data-[side=top]:slide-in-from-bottom-2',
                'data-[side=right]:slide-in-from-left-2',
                'data-[side=bottom]:slide-in-from-top-2',
                'data-[side=left]:slide-in-from-right-2',
                'duration-200',
                className
              )}
            >
              {title && (
                <BasePopover.Title className="text-base font-semibold text-grey-900 dark:text-white mb-2">
                  {title}
                </BasePopover.Title>
              )}

              {description && (
                <BasePopover.Description className="text-sm text-grey-600 dark:text-grey-400 mb-3">
                  {description}
                </BasePopover.Description>
              )}

              <div className="text-grey-700 dark:text-grey-300">{children}</div>

              {showArrow && (
                <BasePopover.Arrow className="fill-white dark:fill-grey-900 stroke-grey-200 dark:stroke-grey-700 stroke-1" />
              )}
            </BasePopover.Popup>
          </BasePopover.Positioner>
        </BasePopover.Portal>
      </BasePopover.Root>
    );
  }
);

Popover.displayName = 'Popover';

export const PopoverTrigger = BasePopover.Trigger;
export const PopoverClose = BasePopover.Close;
