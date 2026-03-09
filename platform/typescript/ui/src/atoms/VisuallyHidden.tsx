import * as React from 'react';
import { cn } from '@ghatana/utils';

/**
 * Styles replicated from WAI-ARIA authoring practices.
 */
const visuallyHiddenStyles: React.CSSProperties = {
  border: 0,
  clip: 'rect(0 0 0 0)',
  clipPath: 'inset(50%)',
  height: '1px',
  width: '1px',
  margin: '-1px',
  overflow: 'hidden',
  padding: 0,
  position: 'absolute',
  whiteSpace: 'nowrap',
};

export interface VisuallyHiddenProps
  extends React.HTMLAttributes<HTMLSpanElement> {
  as?: React.ElementType;
}

/**
 * Hide content visually while keeping it accessible to assistive tech.
 */
export function VisuallyHidden(props: VisuallyHiddenProps) {
  const { as: Component = 'span', className, style, children, ...rest } = props;

  return (
    <Component
      className={cn('gh-visually-hidden', className)}
      style={style ? { ...visuallyHiddenStyles, ...style } : visuallyHiddenStyles}
      {...rest}
    >
      {children}
    </Component>
  );
}
