import * as React from 'react';
import { cn } from '@ghatana/utils';

import { Button, type ButtonProps } from './Button';
import { VisuallyHidden } from './VisuallyHidden';

const dimensionMap: Record<NonNullable<ButtonProps['size']>, number> = {
  sm: 36,
  small: 36,
  md: 40,
  medium: 40,
  lg: 48,
  large: 48,
};

export interface IconButtonProps
  extends Omit<ButtonProps, 'leadingIcon' | 'trailingIcon' | 'fullWidth'> {
  /** Icon rendered inside the button (preferred). */
  icon?: React.ReactNode;

  /**
   * Accessible label announced by screen readers.
   *
   * For backward compatibility, `label` may be omitted if `aria-label` is provided.
   */
  label?: string;

  /** MUI-compatible edge positioning */
  edge?: 'start' | 'end' | false;
}

/**
 * Icon-only button that wraps the base Button component.
 */
export const IconButton = React.forwardRef<HTMLButtonElement, IconButtonProps>((props, ref) => {
  const {
    icon,
    label,
    size = 'md',
    className,
    style,
    variant = 'ghost',
    tone = 'neutral',
    children,
    edge,
    ...rest
  } = props;

  const derivedIcon = icon ?? children;
  const ariaLabel =
    label ??
    (typeof rest['aria-label'] === 'string'
      ? rest['aria-label']
      : typeof rest.title === 'string'
        ? rest.title
        : 'Icon button');

  const dimension = dimensionMap[size];

  const overrides: React.CSSProperties = {
    width: `${dimension}px`,
    height: `${dimension}px`,
    minWidth: `${dimension}px`,
    minHeight: `${dimension}px`,
    paddingInline: '0px',
    paddingBlock: '0px',
    gap: '0px',
  };

  const mergedStyle = style ? { ...overrides, ...style } : overrides;

  return (
    <Button
      ref={ref}
      aria-label={ariaLabel}
      title={rest.title ?? ariaLabel}
      size={size}
      variant={variant}
      tone={tone}
      className={cn(
        'gh-icon-button',
        edge === 'start' && '-ml-1',
        edge === 'end' && '-mr-1',
        className,
      )}
      leadingIcon={derivedIcon}
      data-icon-button
      {...rest}
    >
      <VisuallyHidden>{ariaLabel}</VisuallyHidden>
    </Button>
  );
});

IconButton.displayName = 'IconButton';
