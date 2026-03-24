import { Dialog as BaseDialog } from '@base-ui/react/dialog';
import { forwardRef } from 'react';

import { cn } from '../../utils/cn';

import type { ReactNode } from 'react';

/**
 * Dialog size variants
 */
export type DialogSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl' | 'full';

/**
 * Dialog shape variants
 */
export type DialogShape = 'rounded' | 'square' | 'soft';

/**
 * Dialog content padding variants
 */
export type DialogContentPadding = 'none' | 'normal' | 'dense';

/**
 * Props for the Dialog component
 */
export interface DialogProps {
  /**
   * Whether the dialog is open
   */
  open?: boolean;

  /**
   * Callback fired when the dialog requests to be closed
   */
  onOpenChange?: (open: boolean) => void;

  /**
   * Dialog header content (title)
   */
  header?: ReactNode;

  /**
   * Main dialog content
   */
  children: ReactNode;

  /**
   * Dialog footer actions
   */
  actions?: ReactNode;

  /**
   * Show close button in the title bar
   * @default true
   */
  showCloseButton?: boolean;

  /**
   * Dialog shape variant
   * @default 'rounded'
   */
  shape?: DialogShape;

  /**
   * Dialog size variant
   * @default 'md'
   */
  size?: DialogSize;

  /**
   * Content padding variant
   * @default 'normal'
   */
  contentPadding?: DialogContentPadding;

  /**
   * Additional CSS class for the dialog container
   */
  className?: string;

  /**
   * Additional CSS class for the backdrop
   */
  backdropClassName?: string;
}

/**
 * Dialog component for modal interactions using Base UI primitives.
 * 
 * Features:
 * - 6 size variants: xs (320px) to full (100% viewport)
 * - 3 shape variants: rounded, soft, square
 * - 3 padding variants: none, normal, dense
 * - Optional header with title and close button
 * - Optional footer for actions
 * - Backdrop with opacity transition
 * - Focus trap and escape key handling
 * - Accessible with proper ARIA attributes
 * - Smooth animations for open/close
 * 
 * @example
 * ```tsx
 * <Dialog 
 *   open={isOpen}
 *   onOpenChange={setIsOpen}
 *   header="Confirm Action"
 *   actions={
 *     <>
 *       <Button onClick={onCancel}>Cancel</Button>
 *       <Button onClick={onConfirm}>Confirm</Button>
 *     </>
 *   }
 * >
 *   <p>Are you sure you want to proceed?</p>
 * </Dialog>
 * ```
 */
export const Dialog = forwardRef<HTMLDivElement, DialogProps>(
  (
    {
      open,
      onOpenChange,
      header,
      children,
      actions,
      showCloseButton = true,
      shape = 'rounded',
      size = 'md',
      contentPadding = 'normal',
      className,
      backdropClassName,
    },
    ref
  ) => {
    // Size classes for max-width
    const sizeClasses: Record<DialogSize, string> = {
      xs: 'max-w-xs',      // 320px
      sm: 'max-w-sm',      // 384px
      md: 'max-w-md',      // 448px
      lg: 'max-w-lg',      // 512px
      xl: 'max-w-xl',      // 576px
      full: 'max-w-full',  // 100%
    };

    // Shape classes for border radius
    const shapeClasses: Record<DialogShape, string> = {
      rounded: 'rounded-lg',  // 8px
      soft: 'rounded-2xl',    // 16px
      square: 'rounded',      // 4px
    };

    // Content padding classes
    const paddingClasses: Record<DialogContentPadding, string> = {
      none: 'p-0',
      normal: 'p-6',    // 24px
      dense: 'p-3',     // 12px
    };

    return (
      <BaseDialog.Root open={open} onOpenChange={onOpenChange}>
        {/* Backdrop */}
        <BaseDialog.Backdrop
          className={cn(
            'fixed inset-0 z-50 bg-black/50',
            'transition-opacity duration-200',
            'data-[state=open]:animate-in data-[state=open]:fade-in-0',
            'data-[state=closed]:animate-out data-[state=closed]:fade-out-0',
            backdropClassName
          )}
        />

        {/* Dialog Portal */}
        <BaseDialog.Portal>
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <BaseDialog.Popup
              ref={ref}
              className={cn(
                // Base styles
                'w-full bg-white shadow-2xl',
                'flex flex-col max-h-[90vh]',
                // Size
                sizeClasses[size],
                // Shape
                shapeClasses[shape],
                // Animations
                'transition-all duration-200',
                'data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95',
                'data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95',
                // Custom classes
                className
              )}
            >
              {/* Header */}
              {header && (
                <div className="flex items-center justify-between px-6 py-4 border-b border-grey-200">
                  <BaseDialog.Title className="text-lg font-semibold text-grey-900">
                    {header}
                  </BaseDialog.Title>
                  {showCloseButton && (
                    <BaseDialog.Close
                      className={cn(
                        'rounded p-1 text-grey-400 transition-colors',
                        'hover:text-grey-600 hover:bg-grey-100',
                        'focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2'
                      )}
                      aria-label="Close"
                    >
                      <svg
                        className="w-5 h-5"
                        viewBox="0 0 20 20"
                        fill="currentColor"
                      >
                        <path
                          fillRule="evenodd"
                          d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                          clipRule="evenodd"
                        />
                      </svg>
                    </BaseDialog.Close>
                  )}
                </div>
              )}

              {/* Content */}
              <BaseDialog.Description
                className={cn(
                  'flex-1 overflow-y-auto text-grey-700',
                  paddingClasses[contentPadding]
                )}
              >
                {children}
              </BaseDialog.Description>

              {/* Actions */}
              {actions && (
                <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-grey-200">
                  {actions}
                </div>
              )}
            </BaseDialog.Popup>
          </div>
        </BaseDialog.Portal>
      </BaseDialog.Root>
    );
  }
);

Dialog.displayName = 'Dialog';

/**
 * Re-export Base UI components for advanced use cases
 */
export const DialogTrigger = BaseDialog.Trigger;
export const DialogClose = BaseDialog.Close;
export const DialogTitle = BaseDialog.Title;
export const DialogDescription = BaseDialog.Description;
export const DialogBackdrop = BaseDialog.Backdrop;
export const DialogPopup = BaseDialog.Popup;
export const DialogPortal = BaseDialog.Portal;
