import { forwardRef } from 'react';

import { cn } from '../../utils/cn';

import type { HTMLAttributes, ReactNode } from 'react';

/**
 * Chip variant types
 */
export type ChipVariant = 'filled' | 'outlined';

/**
 * Chip color variants
 */
export type ChipColor = 
  | 'default' 
  | 'primary' 
  | 'secondary' 
  | 'error' 
  | 'warning' 
  | 'info' 
  | 'success';

/**
 * Chip size variants
 */
export type ChipSize = 'small' | 'medium';

/**
 * Props for the Chip component
 */
export interface ChipProps extends Omit<HTMLAttributes<HTMLDivElement>, 'color'> {
  /**
   * Chip label text
   */
  label: string;
  
  /**
   * Visual variant
   * @default 'filled'
   */
  variant?: ChipVariant;
  
  /**
   * Color scheme
   * @default 'default'
   */
  color?: ChipColor;
  
  /**
   * Size variant
   * @default 'medium'
   */
  size?: ChipSize;
  
  /**
   * Icon to display at the start of the chip
   */
  icon?: ReactNode;
  
  /**
   * Callback fired when delete icon is clicked
   */
  onDelete?: () => void;
  
  /**
   * Makes the chip clickable
   */
  onClick?: () => void;
  
  /**
   * Disabled state
   * @default false
   */
  disabled?: boolean;
  
  /**
   * Additional CSS classes
   */
  className?: string;
}

/**
 * Chip component for displaying tags, labels, and compact information.
 * 
 * Features:
 * - Two variants: filled (solid background) and outlined (border only)
 * - 7 color schemes aligned with design system
 * - Two sizes: small and medium
 * - Optional start icon
 * - Optional delete action with X button
 * - Clickable chips with hover states
 * - Disabled state support
 * - Fully keyboard accessible
 * 
 * @example
 * ```tsx
 * // Basic chip
 * <Chip label="React" color="primary" />
 * 
 * // Deletable chip
 * <Chip label="TypeScript" onDelete={() => console.log('deleted')} />
 * 
 * // Clickable chip with icon
 * <Chip 
 *   label="Active" 
 *   icon={<CheckIcon />} 
 *   onClick={() => console.log('clicked')}
 *   variant="outlined"
 * />
 * 
 * // Small disabled chip
 * <Chip label="Disabled" size="small" disabled />
 * ```
 */
export const Chip = forwardRef<HTMLDivElement, ChipProps>(
  (
    {
      label,
      variant = 'filled',
      color = 'default',
      size = 'medium',
      icon,
      onDelete,
      onClick,
      disabled = false,
      className,
      ...props
    },
    ref
  ) => {
    // Size classes
    const sizeClasses: Record<ChipSize, string> = {
      small: 'h-6 text-xs px-2 gap-1',     // 24px height, 12px font, 8px padding
      medium: 'h-8 text-sm px-3 gap-1.5',  // 32px height, 14px font, 12px padding
    };

    // Icon size classes
    const iconSizeClasses: Record<ChipSize, string> = {
      small: 'w-4 h-4',   // 16px icon
      medium: 'w-5 h-5',  // 20px icon
    };

    // Color classes for filled variant
    const filledColorClasses: Record<ChipColor, string> = {
      default: 'bg-grey-200 text-grey-800',
      primary: 'bg-primary-100 text-primary-700',
      secondary: 'bg-secondary-100 text-secondary-700',
      error: 'bg-error-100 text-error-700',
      warning: 'bg-warning-100 text-warning-700',
      info: 'bg-info-100 text-info-700',
      success: 'bg-success-100 text-success-700',
    };

    // Color classes for outlined variant
    const outlinedColorClasses: Record<ChipColor, string> = {
      default: 'border-grey-300 text-grey-700',
      primary: 'border-primary-300 text-primary-700',
      secondary: 'border-secondary-300 text-secondary-700',
      error: 'border-error-300 text-error-700',
      warning: 'border-warning-300 text-warning-700',
      info: 'border-info-300 text-info-700',
      success: 'border-success-300 text-success-700',
    };

    // Hover classes for clickable chips
    const hoverColorClasses: Record<ChipColor, string> = {
      default: 'hover:bg-grey-300',
      primary: 'hover:bg-primary-200',
      secondary: 'hover:bg-secondary-200',
      error: 'hover:bg-error-200',
      warning: 'hover:bg-warning-200',
      info: 'hover:bg-info-200',
      success: 'hover:bg-success-200',
    };

    const isClickable = Boolean(onClick) && !disabled;

    const handleClick = () => {
      if (onClick && !disabled) {
        onClick();
      }
    };

    const handleDelete = (e: React.MouseEvent) => {
      e.stopPropagation(); // Prevent triggering onClick
      if (onDelete && !disabled) {
        onDelete();
      }
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
      if (disabled) return;
      
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        if (onClick) {
          onClick();
        }
      }
      
      if (e.key === 'Backspace' || e.key === 'Delete') {
        if (onDelete) {
          onDelete();
        }
      }
    };

    return (
      <div
        ref={ref}
        role={isClickable ? 'button' : undefined}
        tabIndex={isClickable ? 0 : undefined}
        onClick={handleClick}
        onKeyDown={handleKeyDown}
        className={cn(
          // Base styles
          'inline-flex items-center justify-center rounded-full font-medium transition-colors select-none',
          // Size
          sizeClasses[size],
          // Variant and color
          variant === 'filled' 
            ? filledColorClasses[color]
            : `border ${outlinedColorClasses[color]}`,
          // Interactive states
          isClickable && [
            'cursor-pointer',
            variant === 'filled' && hoverColorClasses[color],
            variant === 'outlined' && 'hover:bg-grey-50',
            'focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-1',
          ],
          // Disabled state
          disabled && 'opacity-50 cursor-not-allowed',
          // Custom classes
          className
        )}
        aria-disabled={disabled}
        {...props}
      >
        {/* Start icon */}
        {icon && (
          <span className={cn('flex-shrink-0', iconSizeClasses[size])}>
            {icon}
          </span>
        )}
        
        {/* Label */}
        <span className="truncate">{label}</span>
        
        {/* Delete button */}
        {onDelete && (
          <button
            type="button"
            onClick={handleDelete}
            disabled={disabled}
            className={cn(
              'flex-shrink-0 rounded-full transition-colors',
              iconSizeClasses[size],
              'hover:bg-black hover:bg-opacity-10',
              'focus:outline-none focus:bg-black focus:bg-opacity-10',
              disabled && 'cursor-not-allowed'
            )}
            aria-label="Delete"
          >
            <svg
              className="w-full h-full"
              viewBox="0 0 20 20"
              fill="currentColor"
            >
              <path
                fillRule="evenodd"
                d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                clipRule="evenodd"
              />
            </svg>
          </button>
        )}
      </div>
    );
  }
);

Chip.displayName = 'Chip';
