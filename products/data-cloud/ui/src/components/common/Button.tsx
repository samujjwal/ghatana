/**
 * Button Component
 *
 * Thin wrapper around @ghatana/design-system Button.
 * Maps local variant names to design-system variant + tone for backward
 * compatibility. All new code should import Button from @ghatana/design-system
 * directly.
 *
 * @doc.type component
 * @doc.purpose Reusable button component (DS-007 migration wrapper)
 * @doc.layer frontend
 */

import React from 'react';
import {
  Button as DesignSystemButton,
  type ButtonProps as DesignSystemButtonProps,
} from '@ghatana/design-system';

interface ButtonProps extends Omit<DesignSystemButtonProps, 'variant' | 'tone' | 'size'> {
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  isLoading?: boolean;
}

const variantMap: Record<
  NonNullable<ButtonProps['variant']>,
  { variant: DesignSystemButtonProps['variant']; tone: DesignSystemButtonProps['tone'] }
> = {
  primary: { variant: 'solid', tone: 'primary' },
  secondary: { variant: 'solid', tone: 'neutral' },
  outline: { variant: 'outline', tone: 'primary' },
  ghost: { variant: 'ghost', tone: 'neutral' },
  danger: { variant: 'solid', tone: 'danger' },
};

const sizeMap: Record<NonNullable<ButtonProps['size']>, DesignSystemButtonProps['size']> = {
  sm: 'sm',
  md: 'md',
  lg: 'lg',
};

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ variant = 'primary', size = 'md', isLoading = false, disabled, children, className, ...props }, ref) => {
    const mapped = variantMap[variant];

    return (
      <DesignSystemButton
        ref={ref}
        variant={mapped.variant}
        tone={mapped.tone}
        size={sizeMap[size]}
        loading={isLoading}
        disabled={disabled || isLoading}
        className={className}
        {...props}
      >
        {children}
      </DesignSystemButton>
    );
  }
);

Button.displayName = 'Button';

export default Button;

