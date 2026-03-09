import * as React from 'react';
import {
  Card as GlobalCard,
  CardHeader as GlobalCardHeader,
  CardContent as GlobalCardContent,
  CardActions as GlobalCardActions,
  CardMedia as GlobalCardMedia,
} from '@ghatana/ui';
import type {
  CardProps as GlobalCardProps,
  CardHeaderProps as GlobalCardHeaderProps,
  CardContentProps as GlobalCardContentProps,
  CardActionsProps as GlobalCardActionsProps,
  CardMediaProps as GlobalCardMediaProps,
} from '@ghatana/ui';

export interface CardProps extends Omit<GlobalCardProps, 'variant'> {
  variant?: 'elevation' | 'outlined' | 'subtle';
  elevation?: 0 | 1 | 2 | 3 | 4 | 6 | 8 | 12 | 16 | 24;
  hover?: boolean;
  interactive?: boolean;
}

export const Card = React.forwardRef<HTMLDivElement, CardProps>((props, ref) => {
  const { variant = 'elevation', elevation = 1, hover, interactive, style, className, ...rest } = props;

  const mappedVariant = variant === 'elevation' ? 'elevated' : variant;

  return React.createElement(GlobalCard, {
    ref,
    variant: mappedVariant as GlobalCardProps['variant'],
    elevation,
    className,
    style: {
      transition: hover ? 'transform 180ms ease, box-shadow 180ms ease' : undefined,
      cursor: interactive ? 'pointer' : undefined,
      ...(style as Record<string, unknown>),
    },
    hover,
    interactive,
    ...rest,
  });
});
Card.displayName = 'Card';

export const CardHeader = GlobalCardHeader;
export const CardContent = GlobalCardContent;
export const CardActions = GlobalCardActions;
export const CardMedia = GlobalCardMedia;

export type {
  GlobalCardHeaderProps as CardHeaderProps,
  GlobalCardContentProps as CardContentProps,
  GlobalCardActionsProps as CardActionsProps,
  GlobalCardMediaProps as CardMediaProps,
};

export { Card as CardTailwind };
export type { CardProps as CardTailwindProps };

// CardActionArea — Tailwind replacement for MUI CardActionArea
export interface CardActionAreaProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  children?: React.ReactNode;
}

export const CardActionArea = React.forwardRef<HTMLButtonElement, CardActionAreaProps>(
  ({ children, className, ...props }, ref) => {
    return React.createElement('button', {
      ref,
      type: 'button' as const,
      className: `w-full text-left transition-colors hover:bg-neutral-100 dark:hover:bg-neutral-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 ${className ?? ''}`,
      ...props,
    }, children);
  },
);
CardActionArea.displayName = 'CardActionArea';
