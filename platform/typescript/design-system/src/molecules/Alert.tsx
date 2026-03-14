import * as React from 'react';
import { cn } from '@ghatana/utils';
import {
  palette,
  lightColors,
  darkColors,
  fontSize,
  fontWeight,
  componentRadius,
} from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';

import { IconButton } from '../atoms/IconButton';
import { VisuallyHidden } from '../atoms/VisuallyHidden';
import { sxToStyle, type SxProps } from '../utils/sx';

type AlertTone = 'info' | 'success' | 'warning' | 'danger' | 'neutral';
type AlertSeverity = 'info' | 'success' | 'warning' | 'error';

const tonePalette: Record<AlertTone, Record<string, string | undefined>> = {
  info: palette.info,
  success: palette.success,
  warning: palette.warning,
  danger: palette.error,
  neutral: palette.gray,
};

export interface AlertProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'title'> {
  tone?: 'info' | 'success' | 'warning' | 'danger' | 'neutral';
  /** Legacy alias for `tone` (MUI-style). */
  severity?: AlertSeverity;
  /** MUI-like sx prop (limited support). */
  sx?: SxProps;
  title?: React.ReactNode;
  description?: React.ReactNode;
  icon?: React.ReactNode;
  onClose?: () => void;
  closeLabel?: string;
  actions?: React.ReactNode;
  /** MUI-like alias for `actions`. */
  action?: React.ReactNode;
}

/**
 * Alert component – communicates contextual feedback.
 */
export const Alert = React.forwardRef<HTMLDivElement, AlertProps>((props, ref) => {
  const {
    tone: toneProp,
    severity,
    sx,
    title,
    description,
    icon,
    onClose,
    closeLabel = 'Dismiss',
    actions,
    action,
    className,
    children,
    ...rest
  } = props;

  const resolvedActions = actions ?? action;

  const tone: AlertTone = toneProp ?? (severity === 'error' ? 'danger' : severity ?? 'info');

  const { resolvedTheme } = useTheme();
  const isDark = resolvedTheme === 'dark';
  const surface = isDark ? darkColors : lightColors;
  const paletteEntry = tonePalette[tone] ?? palette.info;

  const main = paletteEntry[500] ?? palette.info[500];
  const contrast = paletteEntry.contrastText ?? (isDark ? surface.text.primary : '#ffffff');
  const soft = paletteEntry[100] ?? (isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.04)');
  const softBorder = paletteEntry[200] ?? paletteEntry[300] ?? palette.info[300];

  return (
    <div
      ref={ref}
      role="status"
      className={cn('gh-alert', className)}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
        padding: '16px',
        borderRadius: `${componentRadius.panel}px`,
        borderWidth: 1,
        borderStyle: 'solid',
        borderColor: softBorder,
        backgroundColor: soft,
        color: surface.text.primary,
        ...sxToStyle(sx),
      }}
      data-tone={tone}
      {...rest}
    >
      <div
        className="gh-alert__header"
        style={{
          display: 'flex',
          alignItems: title ? 'flex-start' : 'center',
          gap: '12px',
        }}
      >
        {icon ? (
          <div
            className="gh-alert__icon"
            style={{
              flexShrink: 0,
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: '32px',
              height: '32px',
              borderRadius: '50%',
              backgroundColor: main,
              color: contrast,
            }}
          >
            {icon}
          </div>
        ) : null}

        <div style={{ flex: '1 1 auto' }}>
          {title ? (
            <div
              className="gh-alert__title"
              style={{
                fontSize: fontSize.base,
                fontWeight: fontWeight.semibold,
                marginBottom: description || children ? '4px' : 0,
                color: surface.text.primary,
              }}
            >
              {title}
            </div>
          ) : null}

          {description ? (
            <div
              className="gh-alert__description"
              style={{
                fontSize: fontSize.sm,
                color: surface.text.secondary,
              }}
            >
              {description}
            </div>
          ) : null}

          {children}
        </div>

        {onClose ? (
          <IconButton
            icon={<span aria-hidden="true">&times;</span>}
            label={closeLabel}
            onClick={onClose}
            tone="neutral"
            variant="ghost"
            size="sm"
          />
        ) : null}
      </div>

      {resolvedActions ? (
        <div
          className="gh-alert__actions"
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: '8px',
          }}
        >
          {resolvedActions}
        </div>
      ) : null}

      <VisuallyHidden>{tone}</VisuallyHidden>
    </div>
  );
});

Alert.displayName = 'Alert';
