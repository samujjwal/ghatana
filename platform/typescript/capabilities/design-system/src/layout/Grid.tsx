import * as React from 'react';
import { spacing, semanticSpacing } from '@ghatana/tokens';
import { cn } from '@ghatana/utils';
import { sxToStyle } from '../utils/sx';

const spacingValues = spacing as Record<string, number>;
const semanticSpacingValues = semanticSpacing as Record<string, number>;

type Breakpoint = 'sm' | 'md' | 'lg' | 'xl' | '2xl';

export interface GridColumns {
  base?: number;
  sm?: number;
  md?: number;
  lg?: number;
  xl?: number;
  '2xl'?: number;
}

export interface GridProps extends React.HTMLAttributes<HTMLElement> {
  as?: React.ElementType;
  columns?: number | GridColumns;
  rows?: number;
  gap?: string | number;
  gapX?: string | number;
  gapY?: string | number;
  /** Legacy MUI-style container flag (maps to a 12-col CSS grid container). */
  container?: boolean;
  /** Legacy MUI-style item flag (maps to a 12-col CSS grid item). */
  item?: boolean;
  /** Legacy MUI-style spacing (maps to grid gap). */
  spacing?: number;
  /** Legacy MUI-style column spans (out of 12). */
  xs?: number | boolean;
  sm?: number | boolean;
  md?: number | boolean;
  lg?: number | boolean;
  xl?: number | boolean;
  autoFit?: boolean;
  minColumnWidth?: string | number;
  alignItems?: React.CSSProperties['alignItems'];
  justifyItems?: React.CSSProperties['justifyItems'];
  alignContent?: React.CSSProperties['alignContent'];
  justifyContent?: React.CSSProperties['justifyContent'];
  fullWidth?: boolean;
  fullHeight?: boolean;
  /** Legacy tailwind-style columns string (e.g., `grid-cols-1 md:grid-cols-3`) */
  cols?: string;
  /** Legacy tailwind-style rows string */
  rowsClassName?: string;
  /** Legacy tailwind gap string */
  gapClassName?: string;

  /** Minimal MUI-like style prop. Supports spacing shorthands. */
  sx?: unknown;
}

function resolveSpacing(value?: string | number): string | undefined {
  if (value === undefined || value === null) return undefined;
  if (typeof value === 'number') return `${value}px`;
  const trimmed = value.trim();
  if (trimmed === '') return undefined;
  if (/^[0-9]+(?:\.[0-9]+)?$/.test(trimmed)) return `${trimmed}px`;
  if (/^[0-9]+(?:\.[0-9]+)?(px|rem|em|%)$/.test(trimmed)) return trimmed;

  const tailwindMatch = /^gap(?:-[xy])?-(.+)$/.exec(trimmed);
  if (tailwindMatch) {
    const key = tailwindMatch[1];
    if (spacingValues[key] !== undefined) return `${spacingValues[key]}px`;
    if (semanticSpacingValues[key] !== undefined) return `${semanticSpacingValues[key]}px`;
  }

  if (spacingValues[trimmed] !== undefined) return `${spacingValues[trimmed]}px`;
  if (semanticSpacingValues[trimmed] !== undefined) return `${semanticSpacingValues[trimmed]}px`;

  return trimmed;
}

function parseTailwindCols(input?: string): GridColumns | undefined {
  if (!input) return undefined;
  const parts = input.split(/\s+/).filter(Boolean);
  const result: GridColumns = {};

  parts.forEach((part) => {
    const match = /^(?:(sm|md|lg|xl|2xl):)?grid-cols-(\d+)$/.exec(part);
    if (match) {
      const breakpoint = match[1] as Breakpoint | undefined;
      const value = Number(match[2]);
      if (!Number.isNaN(value)) {
        if (breakpoint) {
          result[breakpoint] = value;
        } else {
          result.base = value;
        }
      }
    }
  });

  return Object.keys(result).length > 0 ? result : undefined;
}

function createResponsiveStyles(id: string, columns?: GridColumns) {
  if (!columns) return undefined;

  const rules: string[] = [];
  const base = columns.base;
  if (base) {
    rules.push(`.gh-grid-${id} { grid-template-columns: repeat(${base}, minmax(0, 1fr)); }`);
  }

  const breakpointMap: Record<Breakpoint, string> = {
    sm: '640px',
    md: '768px',
    lg: '1024px',
    xl: '1280px',
    '2xl': '1536px',
  };

  (Object.keys(breakpointMap) as Breakpoint[]).forEach((key) => {
    const value = columns[key];
    if (!value) return;
    rules.push(`@media (min-width: ${breakpointMap[key]}) { .gh-grid-${id} { grid-template-columns: repeat(${value}, minmax(0, 1fr)); } }`);
  });

  return rules.join('\n');
}

export const Grid = React.forwardRef<HTMLElement, GridProps>((props, ref) => {
  const {
    as: Component = 'div',
    columns,
    rows,
    gap,
    gapX,
    gapY,
    container,
    item,
    spacing: spacingProp,
    xs,
    sm,
    md,
    lg,
    xl,
    autoFit = false,
    minColumnWidth = '250px',
    alignItems,
    justifyItems,
    alignContent,
    justifyContent,
    fullWidth = false,
    fullHeight = false,
    cols,
    rowsClassName,
    gapClassName,
    className,
    sx,
    style,
    children,
    ...rest
  } = props;

  const parsedColumns: GridColumns | undefined = React.useMemo(() => {
    if (typeof columns === 'number') {
      return { base: columns };
    }
    if (columns) {
      return columns;
    }
    if (container) {
      return { base: 12 };
    }
    return parseTailwindCols(cols);
  }, [columns, cols, container]);

  const gridId = React.useId().replace(/:/g, '');
  const responsiveRules = React.useMemo(() => createResponsiveStyles(gridId, parsedColumns), [gridId, parsedColumns]);

  React.useEffect(() => {
    if (!responsiveRules || typeof document === 'undefined') return;
    const styleTag = document.createElement('style');
    styleTag.dataset.ghGrid = gridId;
    styleTag.innerHTML = responsiveRules;
    document.head.appendChild(styleTag);
    return () => {
      document.head.removeChild(styleTag);
    };
  }, [gridId, responsiveRules]);

  const computedStyle: React.CSSProperties = {
    display: container || item || parsedColumns || autoFit ? 'grid' : 'grid',
    gap: resolveSpacing(gap ?? gapClassName ?? (spacingProp !== undefined ? spacingProp * 8 : undefined)),
    columnGap: resolveSpacing(gapX),
    rowGap: resolveSpacing(gapY),
    alignItems,
    justifyItems,
    alignContent,
    justifyContent,
    width: fullWidth ? '100%' : undefined,
    height: fullHeight ? '100%' : undefined,
    ...sxToStyle(sx),
    ...style,
  };

  if (item) {
    const span = xs ?? sm ?? md ?? lg ?? xl;
    if (typeof span === 'number' && span > 0) {
      computedStyle.gridColumn = `span ${span} / span ${span}`;
    }
  }

  if (rows !== undefined) {
    computedStyle.gridTemplateRows = `repeat(${rows}, minmax(0, 1fr))`;
  } else if (rowsClassName) {
    // Minimal support for Tailwind row classes e.g. grid-rows-2
    const match = /^grid-rows-(\d+)$/.exec(rowsClassName);
    if (match) {
      const count = Number(match[1]);
      if (!Number.isNaN(count)) {
        computedStyle.gridTemplateRows = `repeat(${count}, minmax(0, 1fr))`;
      }
    }
  }

  if (autoFit) {
    computedStyle.gridTemplateColumns = `repeat(auto-fit, minmax(${typeof minColumnWidth === 'number' ? `${minColumnWidth}px` : minColumnWidth}, 1fr))`;
  } else if (parsedColumns?.base && !responsiveRules) {
    computedStyle.gridTemplateColumns = `repeat(${parsedColumns.base}, minmax(0, 1fr))`;
  }

  return (
    <Component
      ref={ref as React.Ref<HTMLElement>}
      className={cn('gh-grid', `gh-grid-${gridId}`, className)}
      style={computedStyle}
      {...rest}
    >
      {children}
    </Component>
  );
});

Grid.displayName = 'Grid';
