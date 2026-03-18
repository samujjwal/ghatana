import * as React from 'react';
import { spacing, semanticSpacing } from '@ghatana/tokens';

const spacingValues = spacing as Record<string, number>;
const semanticSpacingValues = semanticSpacing as Record<string, number>;

export type SpacerSize = keyof typeof spacing | keyof typeof semanticSpacing | number | string;

export interface SpacerProps extends React.HTMLAttributes<HTMLDivElement> {
  size?: SpacerSize;
  direction?: 'vertical' | 'horizontal';
  flex?: boolean;
}

function resolveSize(size: SpacerSize = 'md', direction: 'vertical' | 'horizontal'): React.CSSProperties {
  const value = (() => {
    if (typeof size === 'number') return `${size}px`;
    if (typeof size === 'string') {
      const trimmed = size.trim();
      if (/^[0-9]+(?:\.[0-9]+)?$/.test(trimmed)) return `${trimmed}px`;
      if (/^[0-9]+(?:\.[0-9]+)?(px|rem|em|%)$/.test(trimmed)) return trimmed;
      if (spacingValues[trimmed] !== undefined) return `${spacingValues[trimmed]}px`;
      if (semanticSpacingValues[trimmed] !== undefined) return `${semanticSpacingValues[trimmed]}px`;
      const tailwindMatch = /^gap(?:-[xy])?-(.+)$/.exec(trimmed);
      if (tailwindMatch) {
        const key = tailwindMatch[1];
        if (spacingValues[key] !== undefined) return `${spacingValues[key]}px`;
        if (semanticSpacingValues[key] !== undefined) return `${semanticSpacingValues[key]}px`;
      }
      return trimmed;
    }
    return undefined;
  })();

  if (direction === 'vertical') {
    return {
      height: value,
      minHeight: value,
      width: '100%',
    };
  }

  return {
    width: value,
    minWidth: value,
    height: '100%',
  };
}

export const Spacer = React.forwardRef<HTMLDivElement, SpacerProps>((props, ref) => {
  const { size = 'md', direction = 'vertical', flex = false, style, className, ...rest } = props;

  const sizeStyles = resolveSize(size, direction);

  return (
    <div
      ref={ref}
      aria-hidden="true"
      className={className}
      style={{
        display: flex ? 'flex' : direction === 'vertical' ? 'block' : 'inline-block',
        flex: flex ? '1 1 auto' : undefined,
        ...sizeStyles,
        ...style,
      }}
      {...rest}
    />
  );
});

Spacer.displayName = 'Spacer';
