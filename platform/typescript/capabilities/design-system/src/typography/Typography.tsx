import * as React from 'react';
import { Text, type TextProps } from './Text';
import { Heading, type HeadingProps } from './Heading';
import { sxToStyle } from '../utils/sx';

export type TypographyVariant =
  | 'h1'
  | 'h2'
  | 'h3'
  | 'h4'
  | 'h5'
  | 'h6'
  | 'subtitle1'
  | 'subtitle2'
  | 'body1'
  | 'body2'
  | 'caption'
  | 'overline'
  | 'button'
  | 'code';

export interface TypographyProps extends React.HTMLAttributes<HTMLElement> {
  variant?: TypographyVariant;
  component?: React.ElementType;
  align?: TextProps['align'];
  color?:
  | 'default'
  | 'subtle'
  | 'muted'
  | 'primary'
  | 'secondary'
  | 'success'
  | 'warning'
  | 'danger'
  | 'info'
  | 'error'
  | 'inherit'
  | 'primary.main'
  | 'secondary.main'
  | 'success.main'
  | 'warning.main'
  | 'error.main'
  | 'info.main'
  | 'text.primary'
  | 'text.secondary'
  | (string & {});
  /** MUI-like prop (compatibility). */
  fontWeight?: React.CSSProperties['fontWeight'];
  noWrap?: boolean;
  gutterBottom?: boolean;

  /** Minimal MUI-like style prop. Supports spacing shorthands. */
  sx?: unknown;
}

export const Typography = React.forwardRef<HTMLElement, TypographyProps>((props, ref) => {
  const {
    variant = 'body1',
    component,
    align,
    color = 'default',
    fontWeight,
    noWrap,
    gutterBottom,
    sx,
    style,
    children,
    ...rest
  } = props;

  const spacingStyle = gutterBottom ? { marginBottom: '0.5rem' } : undefined;

  let tone: TextProps['tone'] = 'default';
  if (color === 'text.secondary') tone = 'muted';
  else if (color === 'text.primary') tone = 'default';
  else if (color === 'inherit') tone = 'default';
  else if (color === 'error' || color === 'error.main') tone = 'danger';
  else if (color === 'primary' || color === 'primary.main') tone = 'primary';
  else if (color === 'secondary' || color === 'secondary.main') tone = 'secondary';
  else if (color === 'success' || color === 'success.main') tone = 'success';
  else if (color === 'warning' || color === 'warning.main') tone = 'warning';
  else if (color === 'danger') tone = 'danger';
  else if (color === 'info' || color === 'info.main') tone = 'info';
  else if (color === 'default') tone = 'default';
  else if (color === 'subtle') tone = 'subtle';
  else if (color === 'muted') tone = 'muted';
  const mergedStyle = {
    ...spacingStyle,
    ...sxToStyle(sx),
    ...(fontWeight != null ? { fontWeight } : null),
    ...style,
  };

  if (variant.startsWith('h')) {
    const level = Number(variant[1]) as HeadingProps['level'];
    return (
      <Heading
        ref={ref as React.Ref<HTMLElement>}
        as={component}
        level={level}
        tone={tone}
        align={align}
        style={mergedStyle}
        {...rest}
      >
        {children}
      </Heading>
    );
  }

  if (variant === 'subtitle1' || variant === 'subtitle2') {
    const level = variant === 'subtitle1' ? 5 : 6;
    return (
      <Heading
        ref={ref as React.Ref<HTMLElement>}
        as={component}
        level={level}
        tone={tone}
        align={align}
        style={{ fontWeight: variant === 'subtitle1' ? 600 : 500, ...mergedStyle }}
        {...rest}
      >
        {children}
      </Heading>
    );
  }

  const textVariantMap: Record<string, TextProps['variant']> = {
    body1: 'body',
    body2: 'body-sm',
    caption: 'caption',
    overline: 'overline',
    button: 'button',
    code: 'code',
  };

  return (
    <Text
      ref={ref as React.Ref<HTMLElement>}
      as={component}
      variant={textVariantMap[variant] ?? 'body'}
      tone={tone}
      align={align}
      noWrap={noWrap}
      style={mergedStyle}
      {...rest}
    >
      {children}
    </Text>
  );
});

Typography.displayName = 'Typography';
