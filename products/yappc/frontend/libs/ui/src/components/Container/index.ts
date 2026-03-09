import * as React from 'react';
import { Container as GlobalContainer } from '@ghatana/ui';

import type { ContainerProps as GlobalContainerProps } from '@ghatana/yappc-ui';

export type { GlobalContainerProps as ContainerProps };

const widthMap: Record<string, GlobalContainerProps['maxWidth']> = {
  xs: 'sm',
  sm: 'sm',
  md: 'md',
  lg: 'lg',
  xl: 'xl',
  '2xl': '2xl',
  '3xl': '2xl',
  '4xl': '2xl',
  '5xl': '2xl',
  '6xl': '2xl',
  '7xl': '2xl',
  full: 'full',
};

export const Container = React.forwardRef<HTMLElement, GlobalContainerProps & {
  paddingClass?: string;
}>(
  ({ paddingClass, padding = true, ...rest }, ref) => {
    const paddingValue = paddingClass && !paddingClass.includes(' ')
      ? paddingClass
      : undefined;

    const maxWidth = rest.maxWidth && typeof rest.maxWidth === 'string'
      ? widthMap[rest.maxWidth] ?? rest.maxWidth
      : rest.maxWidth;

    return React.createElement(GlobalContainer, {
      ref,
      padding,
      paddingValue,
      maxWidth,
      ...rest,
    });
  }
);

Container.displayName = 'Container';

export { Container as ContainerTailwind };
export type { GlobalContainerProps as ContainerTailwindProps };
