import * as React from 'react';
import { cn } from '@ghatana/utils';
import {
  palette,
  lightColors,
  darkColors,
  transitions,
  fontSize,
  fontWeight,
  touchTargets,
} from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';

import { useFocusRing } from '../hooks/useFocusRing';
import { sxToStyle, type SxProps } from '../utils/sx';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface BottomNavigationAction {
  /** Unique identifier */
  id: string;
  /** Display label */
  label: string;
  /** Icon element */
  icon: React.ReactNode;
  /** Navigation path/href */
  path?: string;
  /** Click handler (alternative to path) */
  onClick?: () => void;
  /** Badge count (0 or undefined = hidden) */
  badge?: number;
  /** Whether this action is disabled */
  disabled?: boolean;
  /** Whether to show the label. In 'auto' mode labels are visible only when selected. */
  showLabel?: boolean;
}

export interface BottomNavigationProps
  extends Omit<React.HTMLAttributes<HTMLElement>, 'onChange'> {
  /** Active item id */
  value?: string;
  /** Change handler */
  onChange?: (id: string) => void;
  /** Items */
  items: BottomNavigationAction[];
  /** Accent tone for active state */
  tone?: 'primary' | 'secondary' | 'neutral';
  /** Whether labels are always visible or only when selected */
  showLabels?: boolean;
  /** MUI-compatible sx prop */
  sx?: SxProps;
}

/**
 * Standalone bottom navigation bar — extracted from MobileShell for reuse.
 * WCAG 2.1 AA: 44 px touch targets, roles, keyboard support.
 */
export const BottomNavigation = React.forwardRef<HTMLElement, BottomNavigationProps>(
  (props, ref) => {
    const {
      value,
      onChange,
      items,
      tone = 'primary',
      showLabels = true,
      className,
      style,
      sx,
      ...rest
    } = props;

    const { resolvedTheme } = useTheme();
    const isDark = resolvedTheme === 'dark';
    const surface = isDark ? darkColors : lightColors;

    const toneEntry = (palette as Record<string, Record<string, string>>)[tone] ?? palette.primary;
    const activeColor = toneEntry[isDark ? 400 : 600] ?? toneEntry[500];

    const barStyle: React.CSSProperties = {
      display: 'flex',
      justifyContent: 'space-around',
      alignItems: 'stretch',
      background: isDark ? darkColors.background.paper : lightColors.background.paper,
      borderTop: `1px solid ${isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)'}`,
      paddingBottom: 'env(safe-area-inset-bottom, 0px)',
      ...(sxToStyle(sx) ?? {}),
      ...(style ?? {}),
    };

    return (
      <nav
        ref={ref}
        role="navigation"
        aria-label="Bottom navigation"
        className={cn('gh-bottom-navigation', className)}
        style={barStyle}
        {...rest}
      >
        {items.map((item) => (
          <BottomNavigationItem
            key={item.id}
            item={item}
            active={value === item.id}
            showLabel={item.showLabel ?? showLabels}
            activeColor={activeColor}
            inactiveColor={surface.text.secondary}
            disabledColor={surface.text.disabled}
            onChange={onChange}
          />
        ))}
      </nav>
    );
  }
);

BottomNavigation.displayName = 'BottomNavigation';

// ---------------------------------------------------------------------------
// Inner item
// ---------------------------------------------------------------------------

interface BottomNavItemInnerProps {
  item: BottomNavigationAction;
  active: boolean;
  showLabel: boolean;
  activeColor: string;
  inactiveColor: string;
  disabledColor: string;
  onChange?: (id: string) => void;
}

function BottomNavigationItem({
  item,
  active,
  showLabel,
  activeColor,
  inactiveColor,
  disabledColor,
  onChange,
}: BottomNavItemInnerProps) {
  const { focusProps, isFocusVisible } = useFocusRing();

  const handleClick = () => {
    if (item.disabled) return;
    item.onClick?.();
    onChange?.(item.id);
  };

  const fg = item.disabled ? disabledColor : active ? activeColor : inactiveColor;
  const labelVisible = showLabel || active;

  const btnStyle: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '2px',
    minWidth: '44px',
    minHeight: '44px',
    padding: '6px 12px',
    background: 'transparent',
    border: 'none',
    color: fg,
    cursor: item.disabled ? 'not-allowed' : 'pointer',
    opacity: item.disabled ? 0.5 : 1,
    position: 'relative',
    fontFamily: 'inherit',
    fontSize: fontSize.xs,
    fontWeight: active ? fontWeight.semibold : fontWeight.normal,
    transition: `color ${transitions.duration.fast} ${transitions.easing.easeInOut}`,
    outline: 'none',
    boxShadow: isFocusVisible ? `0 0 0 2px ${activeColor}` : undefined,
    WebkitTapHighlightColor: 'transparent',
    flex: 1,
  };

  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      aria-label={item.label}
      disabled={item.disabled}
      className={cn(
        'gh-bottom-navigation-item',
        active && 'gh-bottom-navigation-item--active'
      )}
      style={btnStyle}
      onClick={handleClick}
      data-nav-id={item.id}
      data-active={active || undefined}
      {...focusProps}
    >
      <span style={{ position: 'relative', display: 'inline-flex' }}>
        {item.icon}
        {item.badge != null && item.badge > 0 && (
          <span
            aria-label={`${item.badge} notification${item.badge === 1 ? '' : 's'}`}
            style={{
              position: 'absolute',
              top: '-4px',
              right: '-6px',
              background: palette.error[500],
              color: '#fff',
              fontSize: '10px',
              fontWeight: fontWeight.bold,
              lineHeight: 1,
              minWidth: '16px',
              height: '16px',
              borderRadius: '8px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '0 4px',
            }}
          >
            {item.badge > 99 ? '99+' : item.badge}
          </span>
        )}
      </span>
      {labelVisible && (
        <span
          style={{
            maxWidth: '60px',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {item.label}
        </span>
      )}
    </button>
  );
}
