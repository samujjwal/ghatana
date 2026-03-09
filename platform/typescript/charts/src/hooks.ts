import * as React from 'react';
import { useThemeTokens } from '@ghatana/theme';

export interface ChartTheme {
  background: string;
  gridColor: string;
  textColor: string;
  tooltipBackground: string;
  tooltipBorder: string;
  fontFamily: string;
}

/**
 * Derive chart theming from the active platform theme.
 */
export interface UseChartThemeOptions {
  overrides?: Partial<ChartTheme>;
}

export function useChartTheme(options: UseChartThemeOptions = {}): ChartTheme {
  const tokens = useThemeTokens();

  return React.useMemo(() => {
    const base: ChartTheme = {
      background: tokens.colors.background.elevated,
      gridColor: tokens.colors.border,
      textColor: tokens.colors.text.primary,
      tooltipBackground: tokens.colors.background.paper,
      tooltipBorder: tokens.colors.border,
      fontFamily: tokens.fontFamily.sans,
    };

    return {
      ...base,
      ...options.overrides,
    };
  }, [options.overrides, tokens]);
}
