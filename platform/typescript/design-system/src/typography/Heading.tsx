import * as React from 'react';
import { cn } from '@ghatana/utils';
import {
  fontSize,
  fontWeight,
  lineHeight,
  letterSpacing,
} from '@ghatana/tokens';
import { Text, type TextTone, type TextAlign } from './Text';

export type HeadingLevel = 1 | 2 | 3 | 4 | 5 | 6;

export interface HeadingProps extends React.HTMLAttributes<HTMLHeadingElement> {
  as?: React.ElementType;
  level?: HeadingLevel;
  tone?: TextTone;
  align?: TextAlign;
  weight?: 'semibold' | 'bold' | 'extrabold';
  uppercase?: boolean;
}

const levelStyles: Record<HeadingLevel, { fontSize: string; fontWeight: number; lineHeight: number; letterSpacing?: string }> = {
  1: { fontSize: fontSize['4xl'], fontWeight: fontWeight.bold, lineHeight: lineHeight.tight, letterSpacing: letterSpacing.tight },
  2: { fontSize: fontSize['3xl'], fontWeight: fontWeight.bold, lineHeight: lineHeight.tight, letterSpacing: letterSpacing.tight },
  3: { fontSize: fontSize['2xl'], fontWeight: fontWeight.semibold, lineHeight: lineHeight.snug },
  4: { fontSize: fontSize.xl, fontWeight: fontWeight.semibold, lineHeight: lineHeight.snug },
  5: { fontSize: fontSize.lg, fontWeight: fontWeight.medium, lineHeight: lineHeight.normal },
  6: { fontSize: fontSize.base, fontWeight: fontWeight.medium, lineHeight: lineHeight.normal },
};

export const Heading = React.forwardRef<HTMLHeadingElement, HeadingProps>((props, ref) => {
  const {
    level = 2,
    tone = 'default',
    align,
    weight,
    uppercase,
    as,
    className,
    style,
    children,
    ...rest
  } = props;

  const Component = (as || (`h${level}`)) as React.ElementType;
  const styleEntry = levelStyles[level];

  return (
    <Text
      ref={ref as React.Ref<HTMLElement>}
      as={Component}
      variant={level <= 6 ? 'body' : 'body'}
      tone={tone}
      align={align}
      uppercase={uppercase}
      className={cn('gh-heading', className)}
      style={{
        fontSize: styleEntry.fontSize,
        fontWeight: weight
          ? weight === 'bold'
            ? fontWeight.bold
            : weight === 'semibold'
            ? fontWeight.semibold
            : fontWeight.extrabold
          : styleEntry.fontWeight,
        lineHeight: styleEntry.lineHeight,
        letterSpacing: styleEntry.letterSpacing,
        ...style,
      }}
      {...rest}
    >
      {children}
    </Text>
  );
});

Heading.displayName = 'Heading';
