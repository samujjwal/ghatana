import { lightColors, palette } from '@ghatana/tokens';

export const flashitMobileTheme = {
  background: {
    canvas: lightColors.background.default,
    surface: lightColors.background.paper,
    accentSurface: lightColors.background.surface,
  },
  text: {
    primary: lightColors.text.primary,
    secondary: lightColors.text.secondary,
    inverse: palette.primary.contrastText,
  },
  border: lightColors.border,
  brand: {
    primary: palette.primary[500],
    primaryStrong: palette.primary[600],
    inactive: palette.gray[400],
  },
} as const;
