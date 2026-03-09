/**
 * Enhanced Button Component
 *
 * A highly composable, accessible button component with full token integration.
 * This enhanced version integrates with the design system tokens.
 *
 * @packageDocumentation
 */

import { Button as BaseButton, Tooltip } from '@ghatana/ui';
import {
  borderRadiusSm,
  borderRadiusMd,
  borderRadiusFull,
  spacingSm,
  spacingMd,
  spacingLg,
  fontWeightMedium,
  fontWeightSemibold,
} from '@ghatana/yappc-shared-ui-core/tokens';
import React from 'react';

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
type TooltipPlacement = 'top' | 'bottom' | 'left' | 'right' | 'top-start' | 'top-end' | 'bottom-start' | 'bottom-end';

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
  variant?: 'solid' | 'outlined' | 'text' | 'contained' | 'outline' | 'ghost' | 'soft' | 'link';

  /**
   * Color tone
   */
  color?: 'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'info' | 'inherit';
}

/** Map shape to Tailwind border-radius */
const shapeClasses: Record<string, string> = {
  rounded: 'rounded-lg',     // ~8px
  square: 'rounded',         // ~4px
  pill: 'rounded-full',      // 9999px
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
export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>((props, ref) => {
  const {
    children,
    shape = 'rounded',
    elevation = 1,
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
    ...rest
  } = props;

  // Extract accessibility props
  const { a11yProps, rest: otherProps } = getA11yProps(rest);

  // Extract and clone icons to mark them as decorative
  const { startIcon, endIcon } = otherProps as unknown;
  const clonedStartIcon =
    startIcon && React.isValidElement(startIcon)
      ? React.cloneElement(startIcon as React.ReactElement, {
          'aria-hidden': true,
        })
      : startIcon;

  const clonedEndIcon =
    endIcon && React.isValidElement(endIcon)
      ? React.cloneElement(endIcon as React.ReactElement, {
          'aria-hidden': true,
        })
      : endIcon;

  // Build button props
  const buttonProps = { ...otherProps } as unknown;
  if (startIcon) buttonProps.startIcon = clonedStartIcon;
  if (endIcon) buttonProps.endIcon = clonedEndIcon;

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
          (ref as React.MutableRefObject<HTMLButtonElement | null>).current = element;
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
    buttonProps['aria-label'] || a11yProps['aria-label']
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
  ].filter(Boolean).join(' ');

  // Map variant to BaseButton variant
  const baseVariant = variant === 'contained' ? 'solid' : variant === 'text' ? 'ghost' : variant;

  // Create button element
  const buttonElement = (
    <BaseButton
      ref={setRefs}
      variant={baseVariant as unknown}
      loading={loading}
      disabled={disabled || loading}
      aria-disabled={disabled || loading ? true : undefined}
      aria-busy={loading ? true : undefined}
      size={size}
      className={buttonClassName}
      {...buttonProps}
      {...a11yProps}
      {...(computedAriaLabel ? { 'aria-label': computedAriaLabel } : {})}
      onKeyDown={handleKeyDown}
    >
      {children}
    </BaseButton>
  );

  // Wrap with tooltip if provided
  if (tooltip) {
    const describedById = `button-tooltip-${Math.random().toString(36).slice(2, 9)}`;
    return (
      <Tooltip title={tooltip} placement={tooltipPlacement} arrow {...tooltipProps}>
        {wrapForTooltip(buttonElement, { 'aria-describedby': describedById })}
      </Tooltip>
    );
  }

  return buttonElement;
});

Button.displayName = 'Button';

export default Button;
