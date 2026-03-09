import { TextField as BaseTextField, IconButton } from '@ghatana/ui';
import React from 'react';

import { borderRadius } from '../../tokens';

/**
 * Props for the enhanced TextField component.
 * Extends standard input HTML attributes with shape, icon, and adornment support.
 */
export type TextFieldProps = Omit<React.InputHTMLAttributes<HTMLInputElement>, 'size' | 'prefix'> & {
  /**
   * Input shape variant
   */
  shape?: 'rounded' | 'square' | 'soft';
  
  /**
   * Icon to display at the start of the input
   */
  startIcon?: React.ReactNode;
  
  /**
   * Icon to display at the end of the input
   */
  endIcon?: React.ReactNode;
  
  /**
   * Callback for when the end icon is clicked
   */
  onEndIconClick?: () => void;

  /** Label text */
  label?: string;

  /** Helper/description text */
  helperText?: string;

  /** Error state or error message */
  error?: boolean | string;

  /** Visual variant */
  variant?: 'standard' | 'filled' | 'outlined';

  /** Component size */
  size?: 'small' | 'medium' | 'sm' | 'md' | 'lg';

  /** Full width */
  fullWidth?: boolean;

  /** MUI-compat InputProps */
  InputProps?: {
    startAdornment?: React.ReactNode;
    endAdornment?: React.ReactNode;
    inputProps?: React.InputHTMLAttributes<HTMLInputElement>;
  };

  /** Multiline textarea */
  multiline?: boolean;

  /** Number of rows for multiline */
  rows?: number;
};

/** Map shape prop to Tailwind border-radius */
const shapeClasses: Record<string, string> = {
  rounded: 'rounded',
  soft: 'rounded-lg',
  square: 'rounded-sm',
};

/**
 * TextField component for user input
 */
export const TextField = React.forwardRef<HTMLDivElement, TextFieldProps>(
  (props, ref) => {
    const { 
      shape = 'rounded',
      startIcon,
      endIcon,
      onEndIconClick,
      InputProps: inputPropsProp,
      className,
      ...rest 
    } = props;

    // Build prefix/suffix from icons or InputProps adornments
    const prefix = startIcon || inputPropsProp?.startAdornment || undefined;
    const suffix = endIcon ? (
      onEndIconClick ? (
        <IconButton onClick={onEndIconClick} size="sm">
          {endIcon}
        </IconButton>
      ) : endIcon
    ) : inputPropsProp?.endAdornment || undefined;

    const fieldClassName = [
      shapeClasses[shape] || shapeClasses.rounded,
      className,
    ].filter(Boolean).join(' ');
    
    return (
      <BaseTextField
        ref={ref as React.Ref<HTMLInputElement>}
        prefix={prefix}
        suffix={suffix}
        className={fieldClassName}
        {...(rest as unknown)}
      />
    );
  }
);

TextField.displayName = 'TextField';
