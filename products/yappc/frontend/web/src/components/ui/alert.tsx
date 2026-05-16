import React from 'react';
import type { HTMLAttributes, ReactNode } from 'react';

type AlertVariant = 'default' | 'destructive';

interface AlertProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
  variant?: AlertVariant;
}

export const Alert: React.FC<AlertProps> = ({ children, variant = 'default', className = '', ...props }) => {
  const variantClass =
    variant === 'destructive'
      ? 'border-red-200 bg-red-50 text-red-900'
      : 'border-zinc-200 bg-zinc-50 text-zinc-900';

  return (
    <div
      role="alert"
      className={[
        'rounded-md border p-4',
        variantClass,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      {...props}
    >
      {children}
    </div>
  );
};

interface AlertTitleProps extends HTMLAttributes<HTMLHeadingElement> {
  children: ReactNode;
}

export const AlertTitle: React.FC<AlertTitleProps> = ({ children, className = '', ...props }) => (
  <h5 className={['mb-1 font-semibold', className].filter(Boolean).join(' ')} {...props}>
    {children}
  </h5>
);

interface AlertDescriptionProps extends HTMLAttributes<HTMLParagraphElement> {
  children: ReactNode;
}

export const AlertDescription: React.FC<AlertDescriptionProps> = ({ children, className = '', ...props }) => (
  <p className={['text-sm', className].filter(Boolean).join(' ')} {...props}>
    {children}
  </p>
);
