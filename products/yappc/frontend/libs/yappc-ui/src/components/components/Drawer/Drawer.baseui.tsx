import { Dialog as BaseDialog } from '@base-ui/react/dialog';
import React from 'react';

import { cn } from '../../utils/cn';

/**
 * Drawer position (side of screen)
 */
export type DrawerPosition = 'left' | 'right' | 'top' | 'bottom';

/**
 * Drawer width/height size
 */
export type DrawerSize = 'sm' | 'md' | 'lg' | 'full';

/**
 * Props for the Drawer component
 */
export interface DrawerProps {
  /**
   * Whether the drawer is open
   */
  open?: boolean;

  /**
   * Callback when drawer open state changes
   */
  onOpenChange?: (open: boolean) => void;

  /**
   * Position of the drawer
   * @default 'right'
   */
  position?: DrawerPosition;

  /**
   * Size of the drawer
   * @default 'md'
   */
  size?: DrawerSize;

  /**
   * Optional header content
   */
  header?: React.ReactNode;

  /**
   * Main content
   */
  children?: React.ReactNode;

  /**
   * Optional footer content
   */
  footer?: React.ReactNode;

  /**
   * Whether to show close button
   * @default true
   */
  showCloseButton?: boolean;

  /**
   * Whether to show backdrop
   * @default true
   */
  showBackdrop?: boolean;

  /**
   * Whether clicking backdrop closes drawer
   * @default true
   */
  closeOnBackdropClick?: boolean;

  /**
   * Additional CSS classes for drawer panel
   */
  className?: string;
}

/**
 * Size class mappings for horizontal drawers (left/right)
 */
const horizontalSizeClasses: Record<DrawerSize, string> = {
  sm: 'w-80',
  md: 'w-96',
  lg: 'w-[32rem]',
  full: 'w-full',
};

/**
 * Size class mappings for vertical drawers (top/bottom)
 */
const verticalSizeClasses: Record<DrawerSize, string> = {
  sm: 'h-64',
  md: 'h-96',
  lg: 'h-[32rem]',
  full: 'h-full',
};

/**
 * Position class mappings (placement and animation)
 */
const positionClasses: Record<DrawerPosition, { container: string; enter: string; exit: string }> = {
  left: {
    container: 'left-0 top-0 bottom-0',
    enter: 'data-[state=open]:animate-slide-in-from-left',
    exit: 'data-[state=closed]:animate-slide-out-to-left',
  },
  right: {
    container: 'right-0 top-0 bottom-0',
    enter: 'data-[state=open]:animate-slide-in-from-right',
    exit: 'data-[state=closed]:animate-slide-out-to-right',
  },
  top: {
    container: 'top-0 left-0 right-0',
    enter: 'data-[state=open]:animate-slide-in-from-top',
    exit: 'data-[state=closed]:animate-slide-out-to-top',
  },
  bottom: {
    container: 'bottom-0 left-0 right-0',
    enter: 'data-[state=open]:animate-slide-in-from-bottom',
    exit: 'data-[state=closed]:animate-slide-out-to-bottom',
  },
};

/**
 * Drawer - Slide-in panel component for navigation or content
 * 
 * Built on Base UI Dialog with slide animations. Can appear from any side
 * of the screen.
 * 
 * @example
 * ```tsx
 * const [open, setOpen] = useState(false);
 * 
 * <Drawer
 *   open={open}
 *   onOpenChange={setOpen}
 *   position="right"
 *   header="Settings"
 * >
 *   <p>Drawer content goes here</p>
 * </Drawer>
 * ```
 * 
 * @example
 * ```tsx
 * <Drawer
 *   position="left"
 *   size="sm"
 *   header={<h2>Navigation</h2>}
 *   footer={<Button>Close</Button>}
 * >
 *   <nav>
 *     <a href="/">Home</a>
 *     <a href="/about">About</a>
 *   </nav>
 * </Drawer>
 * ```
 */
export const Drawer = React.forwardRef<HTMLDivElement, DrawerProps>(
  (
    {
      open = false,
      onOpenChange,
      position = 'right',
      size = 'md',
      header,
      children,
      footer,
      showCloseButton = true,
      showBackdrop = true,
      closeOnBackdropClick = true,
      className,
    },
    ref
  ) => {
    const isHorizontal = position === 'left' || position === 'right';
    const sizeClasses = isHorizontal ? horizontalSizeClasses[size] : verticalSizeClasses[size];
    const positionConfig = positionClasses[position];

    return (
      <BaseDialog.Root open={open} onOpenChange={onOpenChange}>
        {/* Backdrop */}
        {showBackdrop && (
          <BaseDialog.Backdrop
            className={cn(
              'fixed inset-0 bg-black/50 z-40',
              'data-[state=open]:animate-in data-[state=open]:fade-in-0',
              'data-[state=closed]:animate-out data-[state=closed]:fade-out-0'
            )}
            onClick={closeOnBackdropClick ? () => onOpenChange?.(false) : undefined}
          />
        )}

        {/* Drawer Panel */}
        <BaseDialog.Portal>
          <BaseDialog.Popup
            ref={ref}
            className={cn(
              // Base styles
              'fixed z-50 bg-white dark:bg-grey-900',
              'flex flex-col',
              'shadow-2xl',

              // Position
              positionConfig.container,

              // Size
              sizeClasses,

              // Animations
              positionConfig.enter,
              positionConfig.exit,
              'duration-300 ease-in-out',

              // Custom className
              className
            )}
          >
            {/* Header */}
            {(header || showCloseButton) && (
              <div className="flex items-center justify-between px-6 py-4 border-b border-grey-200 dark:border-grey-700">
                {header && (
                  <BaseDialog.Title className="text-lg font-semibold text-grey-900 dark:text-white">
                    {header}
                  </BaseDialog.Title>
                )}
                {showCloseButton && (
                  <BaseDialog.Close className="ml-auto p-2 rounded-lg hover:bg-grey-100 dark:hover:bg-grey-800 transition-colors">
                    <svg
                      className="w-5 h-5 text-grey-600 dark:text-grey-400"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </BaseDialog.Close>
                )}
              </div>
            )}

            {/* Content */}
            <div className="flex-1 overflow-y-auto px-6 py-4">
              <BaseDialog.Description className="sr-only">Drawer content</BaseDialog.Description>
              {children}
            </div>

            {/* Footer */}
            {footer && (
              <div className="px-6 py-4 border-t border-grey-200 dark:border-grey-700">{footer}</div>
            )}
          </BaseDialog.Popup>
        </BaseDialog.Portal>
      </BaseDialog.Root>
    );
  }
);

Drawer.displayName = 'Drawer';

/**
 * DrawerTrigger - Button to open the drawer
 */
export const DrawerTrigger = BaseDialog.Trigger;

/**
 * DrawerClose - Button to close the drawer (can be used in content/footer)
 */
export const DrawerClose = BaseDialog.Close;
