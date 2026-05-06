import { lightColors, palette } from '@ghatana/tokens';

export const flashitMobileTheme = {
  background: {
    canvas: lightColors.background.default,
    surface: lightColors.background.paper,
    accentSurface: lightColors.background.surface,
    muted: lightColors.background.surface,
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
  status: {
    success: palette.success.main ?? palette.success[500],
    error: palette.error.main ?? palette.error[500],
    warning: palette.warning.main ?? palette.warning[500],
    info: palette.info.main ?? palette.info[500],
  },
  shadow: {
    color: palette.gray[900],
  },
} as const;
