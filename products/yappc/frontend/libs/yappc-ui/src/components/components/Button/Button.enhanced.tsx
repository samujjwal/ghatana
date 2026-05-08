/**
 * Enhanced Button Component
 *
 * A highly composable, accessible button component with full token integration.
 * This enhanced version integrates with the design system tokens.
 *
 * @packageDocumentation
 */

import React from 'react';

import {
  Button as BaseButton,
  type ButtonProps as BaseButtonProps,
} from './Button.tailwind';
import {
  borderRadiusSm,
  borderRadiusMd,
  borderRadiusFull,
  spacingSm,
  spacingMd,
  spacingLg,
  fontWeightMedium,
  fontWeightSemibold,
} from '../../tokens';

import { useAccessibility } from '../../hooks';
import useKeyboardActivate from '../../hooks/useKeyboardActivate';
import {
  getA11yProps,
  computeAriaLabel,
  wrapForTooltip,
} from '../../utils/accessibility';

/**
 * Tooltip placement options
 */
type TooltipPlacement =
  | 'top'
  | 'bottom'
  | 'left'
  | 'right'
  | 'top-start'
  | 'top-end'
  | 'bottom-start'
  | 'bottom-end';

type ButtonTone =
  | 'primary'
  | 'secondary'
  | 'success'
  | 'error'
  | 'danger'
  | 'warning'
  | 'info'
  | 'inherit';

type EnhancedButtonRestProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  startIcon?: React.ReactNode;
  endIcon?: React.ReactNode;
};

/**
 * Props for the enhanced Button component.
 */
export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  /**
   * Button size variant
   * - small: Compact button for dense UIs (min 36px height)
   * - medium: Default size (min 44px height for accessibility)
   * - large: Prominent actions (min 48px height)
   */
  size?: 'small' | 'medium' | 'large';

  /**
   * Button shape variant using design tokens
   * - rounded: Standard rounded corners (8px)
   * - square: Minimal rounding (4px)
   * - pill: Fully rounded ends (9999px)
   */
  shape?: 'rounded' | 'square' | 'pill';

  /**
   * Full width button (100% of container)
   */
  fullWidth?: boolean;

  /**
   * Button elevation (shadow depth)
   * Maps to Material-UI shadow scale
   */
  elevation?: 0 | 1 | 2 | 4 | 8;

  /**
   * Disable ripple effect
   * @default false
   */
  disableRipple?: boolean;

  /**
   * Custom ripple color
   * Accepts any valid CSS color value
   */
  rippleColor?: string;

  /**
   * Tooltip text displayed on hover
   * Automatically adds aria-describedby for accessibility
   */
  tooltip?: string;

  /**
   * Tooltip placement relative to button
   * @default 'top'
   */
  tooltipPlacement?: TooltipPlacement;

  /**
   * Additional tooltip props
   */
  tooltipProps?: Record<string, unknown>;

  /**
   * Loading state. Disables the button and shows the package-local spinner.
   */
  loading?: boolean;

  /**
   * Icon rendered before children.
   */
  startIcon?: React.ReactNode;

  /**
   * Icon rendered after children.
   */
  endIcon?: React.ReactNode;

  /**
   * ARIA label for accessibility
   * Used when button content is not descriptive enough
   */
  'aria-label'?: string;

  /**
   * ID of element that labels this button
   * Alternative to aria-label
   */
  'aria-labelledby'?: string;

  /**
   * ID of element that describes this button
   * Additional descriptive information
   */
  'aria-describedby'?: string;

  /**
   * Whether the button controls an expanded element
   * Used for disclosure buttons (accordion, dropdown, etc.)
   */
  'aria-expanded'?: boolean;

  /**
   * Whether the button has a popup menu
   * Indicates the type of popup triggered
   */
  'aria-haspopup'?: boolean | 'menu' | 'listbox' | 'tree' | 'grid' | 'dialog';

  /**
   * Whether the button is pressed (for toggle buttons)
   * Used for toggle/switch button patterns
   */
  'aria-pressed'?: boolean;

  /**
   * Button variant
   */
  variant?:
    | 'solid'
    | 'outlined'
    | 'text'
    | 'contained'
    | 'outline'
    | 'ghost'
    | 'soft'
    | 'link';

  /**
   * Color tone
   */
  color?: ButtonTone;

  /**
   * Legacy tone alias used by older stories and callers.
   */
  tone?: ButtonTone;
}

/** Map shape to Tailwind border-radius */
const shapeClasses: Record<string, string> = {
  rounded: 'rounded-lg', // ~8px
  square: 'rounded', // ~4px
  pill: 'rounded-full', // 9999px
};

/** Map elevation to Tailwind shadow */
const elevationClasses: Record<number, string> = {
  0: 'shadow-none',
  1: 'shadow-sm',
  2: 'shadow',
  4: 'shadow-md',
  8: 'shadow-lg',
};

/** Map size to Tailwind classes */
const sizeClasses: Record<string, string> = {
  small: 'min-h-[36px] min-w-[64px] px-4 py-2 text-sm',
  medium: 'min-h-[44px] min-w-[64px] px-5 py-2.5 text-[0.9375rem]',
  large: 'min-h-[48px] min-w-[64px] px-6 py-4 text-base',
};

const mapButtonSize = (
  size: NonNullable<ButtonProps['size']>
): NonNullable<BaseButtonProps['size']> => {
  if (size === 'small') return 'sm';
  if (size === 'large') return 'lg';
  return 'md';
};

const mapButtonVariant = (
  variant: ButtonProps['variant']
): NonNullable<BaseButtonProps['variant']> => {
  if (variant === 'contained') return 'solid';
  if (variant === 'text') return 'ghost';
  if (variant === 'outlined') return 'outline';
  if (variant === 'soft') return 'secondary';
  return variant ?? 'solid';
};

const mapButtonColorScheme = (
  tone: ButtonTone | undefined
): NonNullable<BaseButtonProps['colorScheme']> => {
  if (tone === 'danger') return 'error';
  if (tone === 'inherit' || tone === 'info') return 'grey';
  return tone ?? 'primary';
};

const cloneDecorativeIcon = (icon: React.ReactNode): React.ReactNode => {
  if (!React.isValidElement(icon)) {
    return icon;
  }

  return React.cloneElement(
    icon as React.ReactElement<React.HTMLAttributes<HTMLElement>>,
    { 'aria-hidden': true }
  );
};

/**
 * Button Component
 *
 * A highly composable, accessible button component with full design token integration.
 *
 * ## Features
 * - ✅ WCAG 2.1 AA compliant (minimum 44px touch targets)
 * - ✅ Full keyboard navigation support
 * - ✅ Loading and disabled states
 * - ✅ Multiple variants (text, outlined, contained)
 * - ✅ Size options (small, medium, large)
 * - ✅ Shape options (rounded, square, pill)
 * - ✅ Tooltip integration
 * - ✅ Icon support (start/end)
 * - ✅ Design token based styling
 *
 * @example Basic Usage
 * ```tsx
 * <Button variant="solid" tone="primary">
 *   Click Me
 * </Button>
 * ```
 *
 * @example With Icons
 * ```tsx
 * <Button
 *   variant="outlined"
 *   startIcon={<SaveIcon />}
 *   endIcon={<ArrowForwardIcon />}
 * >
 *   Save and Continue
 * </Button>
 * ```
 *
 * @example Loading State
 * ```tsx
 * <Button loading variant="solid">
 *   Processing...
 * </Button>
 * ```
 *
 * @example With Tooltip
 * ```tsx
 * <Button
 *   tooltip="This saves your changes"
 *   tooltipPlacement="top"
 *   variant="solid"
 * >
 *   Save
 * </Button>
 * ```
 */
export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  (props, ref) => {
    const {
      children,
      shape = 'rounded',
      elevation = 1,
      fullWidth = false,
      disableRipple = false,
      rippleColor,
      tooltip,
      tooltipPlacement = 'top',
      tooltipProps,
      loading = false,
      disabled,
      size = 'medium',
      className,
      variant,
      color,
      tone,
      startIcon,
      endIcon,
      onKeyDown,
      ...rest
    } = props;

    // Extract accessibility props
    const { a11yProps, rest: otherProps } = getA11yProps(
      rest as Record<string, unknown>
    ) as {
      a11yProps: React.HTMLAttributes<HTMLButtonElement>;
      rest: EnhancedButtonRestProps;
    };

    // Extract and clone icons to mark them as decorative
    const clonedStartIcon = cloneDecorativeIcon(startIcon);
    const clonedEndIcon = cloneDecorativeIcon(endIcon);

    // Use accessibility hook for audit
    const { ref: a11yRef } = useAccessibility<HTMLButtonElement>({
      componentName: 'Button',
      devOnly: true,
      logResults: false,
    });

    // Combine refs
    const setRefs = React.useCallback(
      (element: HTMLButtonElement | null) => {
        a11yRef.current = element;

        if (ref) {
          if (typeof ref === 'function') {
            ref(element);
          } else {
            (ref as React.MutableRefObject<HTMLButtonElement | null>).current =
              element;
          }
        }
      },
      [a11yRef, ref]
    );

    // Keyboard activation handler
    const { onKeyDown: handleKeyDown } = useKeyboardActivate();

    // Compute aria-label from children when not explicitly provided
    const computedAriaLabel = computeAriaLabel(
      children,
      otherProps['aria-label'] || a11yProps['aria-label']
    );

    // Compose Tailwind classes for enhanced styling
    const buttonClassName = [
      shapeClasses[shape] || shapeClasses.rounded,
      elevationClasses[elevation] || elevationClasses[1],
      sizeClasses[size] || sizeClasses.medium,
      'relative overflow-hidden font-medium normal-case',
      'transition-all duration-200 ease-in-out',
      'hover:shadow-md',
      'focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 focus-visible:ring-4 focus-visible:ring-primary/20',
      'active:scale-[0.98] active:transition-transform active:duration-100',
      'disabled:opacity-60 disabled:pointer-events-none disabled:cursor-not-allowed',
      loading ? 'opacity-70 pointer-events-none cursor-wait' : '',
      'contrast-more:border-2 contrast-more:border-current',
      'motion-reduce:transition-none motion-reduce:active:transform-none',
      className,
    ]
      .filter(Boolean)
      .join(' ');

    // Create button element
    const buttonElement = (
      <BaseButton
        ref={setRefs}
        variant={mapButtonVariant(variant)}
        colorScheme={mapButtonColorScheme(tone ?? color)}
        isLoading={loading}
        leftIcon={clonedStartIcon}
        rightIcon={clonedEndIcon}
        disabled={disabled || loading}
        aria-disabled={disabled || loading ? true : undefined}
        aria-busy={loading ? true : undefined}
        size={mapButtonSize(size)}
        className={buttonClassName}
        fullWidth={fullWidth}
        {...otherProps}
        {...a11yProps}
        {...(computedAriaLabel ? { 'aria-label': computedAriaLabel } : {})}
        onKeyDown={(event) => {
          onKeyDown?.(event);
          if (!event.defaultPrevented) {
            handleKeyDown(event);
          }
        }}
      >
        {children}
      </BaseButton>
    );

    // Wrap with tooltip if provided
    if (tooltip) {
      const describedById = `button-tooltip-${Math.random().toString(36).slice(2, 9)}`;
      return (
        <span
          data-tooltip-placement={tooltipPlacement}
          title={tooltip}
          {...(tooltipProps as React.HTMLAttributes<HTMLSpanElement>)}
        >
          {wrapForTooltip(buttonElement, { 'aria-describedby': describedById })}
        </span>
      );
    }

    return buttonElement;
  }
);

Button.displayName = 'Button';

export default Button;
