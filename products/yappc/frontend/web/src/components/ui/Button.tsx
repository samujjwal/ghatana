import React from 'react';

type ButtonVariant =
  | 'solid'
  | 'outlined'
  | 'outline'
  | 'text'
  | 'ghost'
  | 'soft'
  | 'link'
  | 'contained'
  | 'primary'
  | 'danger'
  | 'destructive';

type ButtonTone =
  | 'primary'
  | 'secondary'
  | 'success'
  | 'error'
  | 'warning'
  | 'info'
  | 'inherit'
  | 'danger';

type ButtonSize = 'small' | 'medium' | 'large' | 'sm' | 'md' | 'lg';

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  color?: ButtonTone;
  tone?: ButtonTone;
  size?: ButtonSize;
  fullWidth?: boolean;
  loading?: boolean;
  startIcon?: React.ReactNode;
  endIcon?: React.ReactNode;
  tooltip?: string;
}

const SIZE_CLASSES: Record<ButtonSize, string> = {
  sm: 'min-h-[32px] px-3 py-1.5 text-xs',
  small: 'min-h-[32px] px-3 py-1.5 text-xs',
  md: 'min-h-[40px] px-4 py-2 text-sm',
  medium: 'min-h-[40px] px-4 py-2 text-sm',
  lg: 'min-h-[48px] px-5 py-2.5 text-base',
  large: 'min-h-[48px] px-5 py-2.5 text-base',
};

function resolveTone(color?: ButtonTone, tone?: ButtonTone): ButtonTone {
  return tone ?? color ?? 'primary';
}

function getVariantClasses(variant: ButtonVariant, tone: ButtonTone): string {
  const isDanger = tone === 'danger' || tone === 'error' || variant === 'danger' || variant === 'destructive';

  if (variant === 'link') {
    return isDanger
      ? 'text-red-600 hover:text-red-700 underline-offset-4 hover:underline'
      : 'text-blue-600 hover:text-blue-700 underline-offset-4 hover:underline';
  }

  if (variant === 'text' || variant === 'ghost') {
    return isDanger
      ? 'bg-transparent text-red-600 hover:bg-red-50'
      : 'bg-transparent text-slate-700 hover:bg-slate-100';
  }

  if (variant === 'outlined' || variant === 'outline') {
    return isDanger
      ? 'border border-red-300 bg-white text-red-700 hover:bg-red-50'
      : 'border border-slate-300 bg-white text-slate-800 hover:bg-slate-50';
  }

  if (variant === 'soft') {
    return isDanger
      ? 'bg-red-100 text-red-700 hover:bg-red-200'
      : 'bg-slate-100 text-slate-800 hover:bg-slate-200';
  }

  return isDanger
    ? 'bg-red-600 text-white hover:bg-red-700'
    : 'bg-blue-600 text-white hover:bg-blue-700';
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      children,
      className = '',
      variant = 'solid',
      color,
      tone,
      size = 'medium',
      fullWidth = false,
      loading = false,
      disabled,
      startIcon,
      endIcon,
      tooltip,
      type = 'button',
      ...props
    },
    ref
  ) => {
    const resolvedTone = resolveTone(color, tone);
    const classes = [
      'inline-flex items-center justify-center gap-2 rounded-md font-medium transition-colors',
      'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2',
      'disabled:cursor-not-allowed disabled:opacity-60',
      SIZE_CLASSES[size],
      getVariantClasses(variant, resolvedTone),
      fullWidth ? 'w-full' : '',
      className,
    ]
      .filter(Boolean)
      .join(' ');

    const content = (
      <button
        ref={ref}
        type={type}
        disabled={disabled || loading}
        aria-busy={loading || undefined}
        className={classes}
        {...props}
      >
        {loading ? (
          <span
            className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"
            aria-hidden="true"
          />
        ) : null}
        {!loading && startIcon ? <span aria-hidden="true">{startIcon}</span> : null}
        <span>{children}</span>
        {!loading && endIcon ? <span aria-hidden="true">{endIcon}</span> : null}
      </button>
    );

    if (!tooltip) {
      return content;
    }

    return <span title={tooltip}>{content}</span>;
  }
);

Button.displayName = 'Button';

export default Button;
