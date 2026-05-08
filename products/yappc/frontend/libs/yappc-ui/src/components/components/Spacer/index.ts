import * as React from 'react';

export interface SpacerProps extends React.HTMLAttributes<HTMLDivElement> {
  size?: number | string;
  axis?: 'horizontal' | 'vertical';
}

export const Spacer = React.forwardRef<HTMLDivElement, SpacerProps>(
  ({ size = 4, axis = 'vertical', style, ...props }, ref) => {
    const resolvedSize = typeof size === 'number' ? `${size}px` : size;
    return React.createElement('div', {
      ref,
      'aria-hidden': true,
      style: {
        ...style,
        [axis === 'vertical' ? 'height' : 'width']: resolvedSize,
      },
      ...props,
    });
  }
);

Spacer.displayName = 'Spacer';

export { Spacer as SpacerTailwind };
export type { SpacerProps as SpacerTailwindProps };
