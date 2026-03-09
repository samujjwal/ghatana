import * as React from 'react';
import { Grid as GlobalGrid } from '@ghatana/ui';

import type { GridProps as GlobalGridProps } from '@ghatana/yappc-ui';

export interface GridProps extends Omit<GlobalGridProps, 'columns' | 'cols' | 'gap'> {
  cols?: string;
  rows?: string;
  gap?: string;
}

function mapCols(cols?: string): GlobalGridProps['columns'] {
  if (!cols) return undefined;
  const parts = cols.split(/\s+/).filter(Boolean);
  const result: Record<string, number> = {};
  parts.forEach((part) => {
    const match = /^(?:(sm|md|lg|xl|2xl):)?grid-cols-(\d+)$/.exec(part);
    if (match) {
      const breakpoint = match[1] || 'base';
      const value = Number(match[2]);
      if (!Number.isNaN(value)) {
        result[breakpoint] = value;
      }
    }
  });
  if (Object.keys(result).length === 0) return undefined;
  return {
    base: result.base,
    sm: result.sm,
    md: result.md,
    lg: result.lg,
    xl: result.xl,
    '2xl': result['2xl'],
  };
}

function mapRows(rows?: string): number | undefined {
  if (!rows) return undefined;
  const match = /^grid-rows-(\d+)$/.exec(rows);
  if (!match) return undefined;
  const value = Number(match[1]);
  return Number.isNaN(value) ? undefined : value;
}

export const Grid = React.forwardRef<HTMLElement, GridProps>((props, ref) => {
  const { cols, rows, gap, ...rest } = props;

  return React.createElement(GlobalGrid, {
    ref,
    columns: mapCols(cols),
    rows: mapRows(rows),
    gap,
    ...rest,
  });
});

Grid.displayName = 'Grid';

export { Grid as GridTailwind };
export type { GridProps as GridTailwindProps };
