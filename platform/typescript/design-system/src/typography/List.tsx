import * as React from 'react';
import { cn } from '@ghatana/utils';
import { spacing } from '@ghatana/tokens';
import { Text, type TextProps } from './Text';

const spacingValues = spacing as Record<string, number>;

export interface ListProps extends React.HTMLAttributes<HTMLUListElement> {
  ordered?: boolean;
  marker?: 'disc' | 'circle' | 'square' | 'decimal' | 'none';
  gap?: keyof typeof spacing | number | string;
  /** MUI-compatible density toggle. */
  dense?: boolean;
  children: React.ReactNode;
}

export const List = React.forwardRef<HTMLOListElement | HTMLUListElement, ListProps>((props, ref) => {
  const { ordered = false, marker, dense = false, gap, className, children, style, ...rest } = props;

  const resolvedGapProp = gap ?? (dense ? '1' : '2');

  const Component = ordered ? 'ol' : 'ul';

  const resolvedGap = (() => {
    if (typeof resolvedGapProp === 'number') return `${resolvedGapProp}px`;
    if (typeof resolvedGapProp === 'string') {
      const trimmed = resolvedGapProp.trim();
      if (/^[0-9]+$/.test(trimmed)) return `${trimmed}px`;
      if (/^[0-9]+(?:\.[0-9]+)?(px|rem|em)$/.test(trimmed)) return trimmed;
      if (spacingValues[trimmed] !== undefined) return `${spacingValues[trimmed]}px`;
      return trimmed;
    }
    return undefined;
  })();

  const markerStyle = marker
    ? marker === 'none'
      ? { listStyleType: 'none' }
      : { listStyleType: marker }
    : undefined;

  return (
    <Component
      ref={ref as React.Ref<HTMLOListElement | HTMLUListElement>}
      className={cn('gh-list', className)}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: resolvedGap,
        paddingLeft: marker === 'none' ? 0 : '1.25rem',
        margin: 0,
        ...markerStyle,
        ...style,
      }}
      {...rest}
    >
      {React.Children.map(children, (child, index) => (
        <li key={index} style={{ margin: 0 }}>
          {typeof child === 'string' || typeof child === 'number' ? (
            <Text as="span">{child}</Text>
          ) : (
            child
          )}
        </li>
      ))}
    </Component>
  );
});

List.displayName = 'List';
