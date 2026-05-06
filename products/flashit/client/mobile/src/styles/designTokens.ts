/**
 * FlashIt mobile design-token aliases.
 *
 * Product screens import these aliases instead of embedding raw color strings
 * in component files. The values intentionally point at the Kernel token
 * palette so product UI remains design-system governed while React Native can
 * still receive concrete color values at runtime.
 */
import { palette } from '@ghatana/tokens';

export const flashitMobileColors = {
  black: palette.gray[900],
  white: palette.primary.contrastText,
  whiteAlt: palette.primary.contrastText,
  slate50: palette.gray[50],
  slate100: palette.gray[100],
  slate200: palette.gray[200],
  slate300: palette.gray[300],
  slate400: palette.gray[400],
  slate500: palette.gray[500],
  slate600: palette.gray[600],
  slate700: palette.gray[700],
  slate800: palette.gray[800],
  sky50: palette.info[50],
  sky100: palette.info[100],
  sky500: palette.info[500],
  sky600: palette.info[600],
  blue50: palette.primary[50],
  blue100: palette.primary[100],
  blue200: palette.primary[200],
  blue500: palette.primary[500],
  blue700: palette.primary[700],
  indigo50: palette.secondary[50],
  indigo100: palette.secondary[100],
  indigo200: palette.secondary[200],
  indigo500: palette.secondary[500],
  indigo600: palette.secondary[600],
  indigo700: palette.secondary[700],
  purple50: palette.secondary[50],
  purple100: palette.secondary[100],
  purple500: palette.secondary[500],
  pink500: palette.secondary[400],
  green50: palette.success[50],
  green100: palette.success[100],
  green300: palette.success[300],
  green500: palette.success[500],
  green600: palette.success[600],
  yellow50: palette.warning[50],
  yellow100: palette.warning[100],
  yellow300: palette.warning[300],
  yellow500: palette.warning[500],
  yellow700: palette.warning[800],
  orange500: palette.warning[500],
  orange600: palette.warning[600],
  red50: palette.error[50],
  red100: palette.error[100],
  red200: palette.error[200],
  red500: palette.error[500],
  red600: palette.error[600],
  red700: palette.error[700],
  neutral50: palette.neutral[50],
  neutral100: palette.neutral[100],
  neutral200: palette.neutral[200],
  iosBlue: palette.info[500],
  iosGreen: palette.success[500],
  iosOrange: palette.warning[500],
  iosRed: palette.error[500],
  iosPurple: palette.secondary[500],
  iosPink: palette.secondary[400],
  iosGray: palette.gray[500],
  iosText: palette.gray[900],
} as const;

export const flashitMobileAlpha = {
  black: (opacity: number = 1) => `rgba(0, 0, 0, ${opacity})`,
  blue: (opacity: number = 1) => `rgba(59, 130, 246, ${opacity})`,
  green: (opacity: number = 1) => `rgba(34, 197, 94, ${opacity})`,
  orange: (opacity: number = 1) => `rgba(251, 146, 60, ${opacity})`,
  purple: (opacity: number = 1) => `rgba(147, 51, 234, ${opacity})`,
} as const;

export const flashitMobileShadows = {
  soft: '0px 1px 2px rgba(0, 0, 0, 0.05)',
  divider: 'rgba(0,0,0,0.1)',
} as const;
