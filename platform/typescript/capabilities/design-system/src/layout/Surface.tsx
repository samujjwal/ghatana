import * as React from 'react';
import {
  lightColors,
  darkColors,
  lightShadows,
  darkShadows,
  componentRadius,
} from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';
import { Box, type BoxProps } from './Box';

export type SurfaceVariant = 'elevation' | 'outlined';

const _elevationLevels = [0, 1, 2, 3, 4, 6, 8, 12, 16, 24] as const;
export type SurfaceElevation = typeof _elevationLevels[number];

function resolveShadow(elevation: SurfaceElevation, mode: 'light' | 'dark'): string {
  const shadows = mode === 'dark' ? darkShadows : lightShadows;
  const index = Math.min(shadows.length - 1, elevation);
  return shadows[index];
}

export interface SurfaceProps extends Omit<BoxProps, 'shadow' | 'border'> {
  elevation?: SurfaceElevation;
  variant?: SurfaceVariant;
  square?: boolean;
}

export const Surface = React.forwardRef<HTMLDivElement, SurfaceProps>((props, ref) => {
  const {
    elevation = 1,
    variant = 'elevation',
    square = false,
    className,
    style,
    background,
    bg,
    borderRadius,
    rounded,
    ...rest
  } = props;

  const { resolvedTheme } = useTheme();
  const themeMode: 'light' | 'dark' = resolvedTheme === 'dark' ? 'dark' : 'light';
  const surface = themeMode === 'dark' ? darkColors : lightColors;

  const resolvedBackground = bg ?? background ?? surface.background.elevated;

  const mergedStyle: React.CSSProperties = {
    background: resolvedBackground,
    boxShadow: variant === 'elevation' ? resolveShadow(elevation, themeMode) : undefined,
    borderWidth: variant === 'outlined' ? 1 : undefined,
    borderStyle: variant === 'outlined' ? 'solid' : undefined,
    borderColor: variant === 'outlined' ? surface.border : undefined,
    borderRadius: square
      ? 0
      : borderRadius ?? rounded ?? `${componentRadius.panel}px`,
    ...style,
  };

  return (
    <Box
      ref={ref}
      className={className}
      style={mergedStyle}
      role={rest.role ?? 'presentation'}
      {...rest}
    />
  );
});

Surface.displayName = 'Surface';

export const Paper = Surface;
export type { SurfaceProps as PaperProps };
