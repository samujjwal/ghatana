import * as React from 'react';
import { cn } from '@ghatana/utils';
import {
  palette,
  lightColors,
  darkColors,
  fontSize,
  fontWeight,
  spacing,
  componentRadius,
} from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';
import { IconButton } from './IconButton';
import { Text } from '../typography/Text';
import { VisuallyHidden } from './VisuallyHidden';
import { sxToStyle, type SxProps } from '../utils/sx';

export type ChipVariant = 'filled' | 'outlined';
export type ChipTone = 'default' | 'primary' | 'secondary' | 'success' | 'warning' | 'danger' | 'info';
export type ChipSize = 'sm' | 'md';
type ChipColor = ChipTone | 'error';
type ChipSizeAlias = ChipSize | 'small' | 'medium';

export interface ChipProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'color'> {
  label: React.ReactNode;
  variant?: ChipVariant;
  tone?: ChipTone;
  /** Legacy alias for `tone` (MUI-style). */
  color?: ChipColor;
  size?: ChipSizeAlias;
  /** MUI-like avatar slot. */
  avatar?: React.ReactNode;
  icon?: React.ReactNode;
  onDelete?: () => void;
  deleteLabel?: string;
  disabled?: boolean;
  onClick?: (event: React.MouseEvent<HTMLButtonElement | HTMLDivElement>) => void;
  clickable?: boolean;
  sx?: SxProps;
}

const toneMap: Record<ChipTone, { filledBg: string; filledColor: string; outlinedBg: string; outlinedBorder: string; outlinedColor: string; hoverBg: string }> = {
  default: {
    filledBg: palette.gray[200],
    filledColor: palette.gray[800],
    outlinedBg: 'transparent',
    outlinedBorder: palette.gray[300],
    outlinedColor: palette.gray[700],
    hoverBg: palette.gray[300],
  },
  primary: {
    filledBg: palette.primary[100],
    filledColor: palette.primary[700],
    outlinedBg: 'transparent',
    outlinedBorder: palette.primary[300],
    outlinedColor: palette.primary[700],
    hoverBg: palette.primary[200],
  },
  secondary: {
    filledBg: palette.secondary[100],
    filledColor: palette.secondary[700],
    outlinedBg: 'transparent',
    outlinedBorder: palette.secondary[300],
    outlinedColor: palette.secondary[700],
    hoverBg: palette.secondary[200],
  },
  success: {
    filledBg: palette.success.light ?? palette.success[100],
    filledColor: palette.success.dark ?? palette.success[700],
    outlinedBg: 'transparent',
    outlinedBorder: palette.success[300] ?? palette.success[200],
    outlinedColor: palette.success.main ?? palette.success[600],
    hoverBg: palette.success[200] ?? palette.success.light,
  },
  warning: {
    filledBg: palette.warning.light ?? palette.warning[100],
    filledColor: palette.warning.dark ?? palette.warning[800],
    outlinedBg: 'transparent',
    outlinedBorder: palette.warning[300] ?? palette.warning.main,
    outlinedColor: palette.warning.main ?? palette.warning[800],
    hoverBg: palette.warning[200] ?? palette.warning.light,
  },
  danger: {
    filledBg: palette.error.light ?? palette.error[100],
    filledColor: palette.error.dark ?? palette.error[800],
    outlinedBg: 'transparent',
    outlinedBorder: palette.error[300] ?? palette.error.main,
    outlinedColor: palette.error.main ?? palette.error[800],
    hoverBg: palette.error[200] ?? palette.error.light,
  },
  info: {
    filledBg: palette.info.light ?? palette.info[100],
    filledColor: palette.info.dark ?? palette.info[800],
    outlinedBg: 'transparent',
    outlinedBorder: palette.info[300] ?? palette.info.main,
    outlinedColor: palette.info.main ?? palette.info[800],
    hoverBg: palette.info[200] ?? palette.info.light,
  },
};

const getSpacing = (key: string, fallback: number): number => {
  const value = (spacing as Record<string, unknown>)[key];
  return typeof value === 'number' ? value : fallback;
};

const sizeTokens: Record<ChipSize, { height: number; fontSize: string; paddingX: number; gap: number; iconSize: number }> = {
  sm: {
    height: 24,
    fontSize: fontSize.sm,
    paddingX: getSpacing('2', 8),
    gap: getSpacing('1', 4),
    iconSize: 14,
  },
  md: {
    height: 32,
    fontSize: fontSize.base,
    paddingX: getSpacing('3', 12),
    gap: getSpacing('1.5', 6),
    iconSize: 16,
  },
};

export const Chip = React.forwardRef<HTMLDivElement, ChipProps>((props, ref) => {
  const {
    label,
    variant = 'filled',
    tone: toneProp,
    color: colorProp,
    size: sizeProp = 'md',
    avatar,
    icon,
    onDelete,
    deleteLabel = 'Remove tag',
    disabled = false,
    onClick,
    clickable: clickableProp,
    className,
    sx,
    style,
    ...rest
  } = props;

  const tone: ChipTone = toneProp ?? (colorProp === 'error' ? 'danger' : (colorProp ?? 'default'));
  const size: ChipSize = sizeProp === 'small' ? 'sm' : sizeProp === 'medium' ? 'md' : sizeProp;

  const { resolvedTheme } = useTheme();
  const isDark = resolvedTheme === 'dark';
  const _surface = isDark ? darkColors : lightColors;
  const toneEntry = toneMap[tone];
  const metrics = sizeTokens[size];

  const clickable = Boolean(onClick || clickableProp) && !disabled;

  const backgroundColor = variant === 'filled' ? toneEntry.filledBg : toneEntry.outlinedBg;
  const foregroundColor = variant === 'filled' ? toneEntry.filledColor : toneEntry.outlinedColor;
  const borderColor = variant === 'outlined' ? toneEntry.outlinedBorder : 'transparent';

  const baseStyle: React.CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: `${metrics.gap}px`,
    height: `${metrics.height}px`,
    paddingInline: `${metrics.paddingX}px`,
    borderRadius: `${componentRadius.badge}px`,
    backgroundColor,
    color: foregroundColor,
    borderWidth: 1,
    borderStyle: 'solid',
    borderColor,
    cursor: clickable ? 'pointer' : undefined,
    opacity: disabled ? 0.6 : 1,
    transition: 'background-color 160ms ease',
    ...sxToStyle(sx),
    ...style,
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (disabled) return;
    if ((event.key === 'Enter' || event.key === ' ') && onClick) {
      event.preventDefault();
      onClick(event as unknown as React.MouseEvent<HTMLDivElement>);
    }
  };

  const handleClick = (event: React.MouseEvent<HTMLDivElement>) => {
    if (disabled) return;
    onClick?.(event);
  };

  return (
    <div
      ref={ref}
      className={cn('gh-chip', className)}
      style={baseStyle}
      role={clickable ? 'button' : rest.role}
      tabIndex={clickable ? 0 : rest.tabIndex}
      onKeyDown={handleKeyDown}
      onClick={handleClick}
      aria-disabled={disabled || undefined}
      {...rest}
    >
      {avatar ? (
        <span aria-hidden="true" style={{ display: 'inline-flex', alignItems: 'center' }}>
          {avatar}
        </span>
      ) : null}
      {icon ? (
        <span aria-hidden="true" style={{ display: 'inline-flex', width: metrics.iconSize, height: metrics.iconSize }}>
          {icon}
        </span>
      ) : null}
      <Text as="span" variant={size === 'sm' ? 'body-xs' : 'body-sm'} tone="default" style={{ color: foregroundColor }}>
        {label}
      </Text>
      {onDelete ? (
        <IconButton
          icon={<span aria-hidden="true">×</span>}
          label={deleteLabel}
          tone={tone === 'default' ? 'neutral' : tone}
          size="sm"
          variant="ghost"
          onClick={(event) => {
            event.stopPropagation();
            if (!disabled) {
              onDelete();
            }
          }}
        />
      ) : null}
    </div>
  );
});

Chip.displayName = 'Chip';
