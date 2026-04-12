/**
 * @fileoverview Stack - Vertical/horizontal layout primitive.
 *
 * @doc.type component
 * @doc.purpose Token-driven spacing and alignment primitive.
 * @doc.category primitive
 */

import * as React from 'react';
import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface StackProps {
  readonly children: React.ReactNode;
  readonly direction?: 'vertical' | 'horizontal';
  readonly gap?: 'none' | 'xs' | 'sm' | 'md' | 'lg' | 'xl';
  readonly align?: 'start' | 'center' | 'end' | 'stretch';
  readonly justify?: 'start' | 'center' | 'end' | 'between' | 'around' | 'evenly';
  readonly wrap?: boolean;
  readonly className?: string;
  readonly as?: React.ElementType;
}

const gapClasses: Record<NonNullable<StackProps['gap']>, string> = {
  none: 'gap-0',
  xs: 'gap-1',
  sm: 'gap-2',
  md: 'gap-4',
  lg: 'gap-6',
  xl: 'gap-8',
};

const directionClasses: Record<NonNullable<StackProps['direction']>, string> = {
  vertical: 'flex flex-col',
  horizontal: 'flex flex-row',
};

const alignClasses: Record<NonNullable<StackProps['align']>, string> = {
  start: 'items-start',
  center: 'items-center',
  end: 'items-end',
  stretch: 'items-stretch',
};

const justifyClasses: Record<NonNullable<StackProps['justify']>, string> = {
  start: 'justify-start',
  center: 'justify-center',
  end: 'justify-end',
  between: 'justify-between',
  around: 'justify-around',
  evenly: 'justify-evenly',
};

function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}

export const Stack: React.FC<StackProps> = React.memo(({
  children,
  direction = 'vertical',
  gap = 'md',
  align = 'stretch',
  justify = 'start',
  wrap = false,
  className,
  as: Component = 'div',
  ...props
}) => {
  const classes = cn(
    directionClasses[direction],
    gapClasses[gap],
    alignClasses[align],
    justifyClasses[justify],
    wrap && 'flex-wrap',
    className
  );

  return (
    <Component className={classes} {...props}>
      {children}
    </Component>
  );
});

Stack.displayName = 'Stack';

export const VStack: React.FC<Omit<StackProps, 'direction'>> = (props) => (
  <Stack {...props} direction="vertical" />
);

export const HStack: React.FC<Omit<StackProps, 'direction'>> = (props) => (
  <Stack {...props} direction="horizontal" />
);
