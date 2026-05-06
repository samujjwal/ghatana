/**
 * FlashIt web design-token aliases.
 *
 * Component-local styles should consume these aliases instead of embedding raw
 * color literals. The aliases are backed by Kernel tokens so product UI remains
 * aligned with the shared design system.
 */
import { palette } from '@ghatana/tokens';

export const flashitWebColors = {
  white: palette.primary.contrastText,
  neutral100: palette.neutral[100],
  slate50: palette.gray[50],
  slate100: palette.gray[100],
  slate200: palette.gray[200],
  slate300: palette.gray[300],
  slate400: palette.gray[400],
  slate500: palette.gray[500],
  slate600: palette.gray[600],
  slate700: palette.gray[700],
  slate800: palette.gray[800],
  blue50: palette.primary[50],
  blue100: palette.primary[100],
  blue500: palette.primary[500],
  blue700: palette.primary[700],
  indigo50: palette.secondary[50],
  indigo100: palette.secondary[100],
  indigo200: palette.secondary[200],
  indigo500: palette.secondary[500],
  indigo600: palette.secondary[600],
  purple500: palette.secondary[500],
  teal500: palette.info[500],
  green500: palette.success[500],
  yellow100: palette.warning[100],
  yellow500: palette.warning[500],
  yellow800: palette.warning[800],
  orange500: palette.warning[500],
  red50: palette.error[50],
  red200: palette.error[200],
  red500: palette.error[500],
  red600: palette.error[600],
  red700: palette.error[700],
  pink500: palette.secondary[400],
  chartPurple: palette.secondary[500],
} as const;

export const flashitWebAlpha = {
  shadow: 'rgba(0, 0, 0, 0.1)',
  elevatedShadow: 'rgba(0, 0, 0, 0.2)',
  indigoShadow: 'rgba(99, 102, 241, 0.3)',
  inverseBorder: 'rgba(255, 255, 255, 0.3)',
} as const;

export const flashitWebGradients = {
  errorHeader: `linear-gradient(135deg, ${palette.secondary[400]} 0%, ${palette.secondary[600]} 100%)`,
} as const;
