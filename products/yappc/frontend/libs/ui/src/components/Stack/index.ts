import * as React from 'react';
import { Stack as GlobalStack } from '@ghatana/ui';

import type { StackProps as GlobalStackProps, StackGap } from '@ghatana/yappc-ui';

export type { GlobalStackProps as StackProps };

export interface LegacyStackProps extends Omit<GlobalStackProps, 'gap' | 'align' | 'justify' | 'wrap'> {
  spacing?: string;
  align?: string;
  alignItems?: 'start' | 'center' | 'end' | 'stretch' | 'baseline';
  justify?: string;
  justifyContent?: 'start' | 'center' | 'end' | 'between' | 'around' | 'evenly';
  wrap?: string | boolean;
}

function mapSpacing(spacing?: string): StackGap | undefined {
  if (!spacing) return undefined;
  const match = /^gap(?:-[xy])?-(.+)$/.exec(spacing);
  if (match) {
    return match[1];
  }
  if (/^[0-9]+(px|rem|em|%)$/.test(spacing) || spacing === '0') {
    return spacing;
  }
  return spacing;
}

function mapAlign(align?: string, alignItems?: LegacyStackProps['alignItems']): GlobalStackProps['align'] {
  if (alignItems) {
    return alignItems;
  }
  switch (align) {
    case 'items-start':
      return 'start';
    case 'items-center':
      return 'center';
    case 'items-end':
      return 'end';
    case 'items-stretch':
      return 'stretch';
    case 'items-baseline':
      return 'baseline';
    default:
      return undefined;
  }
}

function mapJustify(justify?: string, justifyContent?: LegacyStackProps['justifyContent']): GlobalStackProps['justify'] {
  if (justifyContent) {
    return justifyContent;
  }
  switch (justify) {
    case 'justify-start':
      return 'start';
    case 'justify-center':
      return 'center';
    case 'justify-end':
      return 'end';
    case 'justify-between':
      return 'between';
    case 'justify-around':
      return 'around';
    case 'justify-evenly':
      return 'evenly';
    default:
      return undefined;
  }
}

function mapWrap(wrap?: string | boolean): GlobalStackProps['wrap'] {
  if (wrap === true || wrap === false) {
    return wrap;
  }
  switch (wrap) {
    case 'flex-wrap':
      return 'wrap';
    case 'flex-wrap-reverse':
      return 'wrap-reverse';
    case 'flex-nowrap':
      return 'nowrap';
    default:
      return undefined;
  }
}

export const Stack = React.forwardRef<HTMLElement, LegacyStackProps>((props, ref) => {
  const {
    spacing,
    align,
    alignItems,
    justify,
    justifyContent,
    wrap,
    className,
    ...rest
  } = props;

  return React.createElement(GlobalStack, {
    ref,
    gap: mapSpacing(spacing),
    align: mapAlign(align, alignItems),
    justify: mapJustify(justify, justifyContent),
    wrap: mapWrap(wrap),
    className,
    ...rest,
  });
});

Stack.displayName = 'Stack';

export { Stack as StackTailwind };
