import React, { useMemo, useState } from 'react';
import { tokens } from '@ghatana/tokens';
import { sxToStyle } from '../utils/sx';

export interface TabConfig {
  key?: string | number;
  /** Legacy alias for `key` (MUI-style). */
  value?: string | number;
  label: React.ReactNode;
  content?: React.ReactNode;
  disabled?: boolean;
  icon?: React.ReactNode;
}

export interface TabProps<TValue extends string | number = string> {
  label: React.ReactNode;
  value?: TValue;
  disabled?: boolean;
  icon?: React.ReactNode;
}

export const Tab = <TValue extends string | number = string,>(_props: TabProps<TValue>) => null;

Tab.displayName = 'Tab';

interface TabsCommonProps {
  /** Orientation */
  orientation?: 'horizontal' | 'vertical';
  /** Variant */
  variant?: 'default' | 'pills' | 'underline';
  /** Size */
  size?: 'sm' | 'md' | 'lg';
  /** Full width tabs */
  fullWidth?: boolean;
  /** Additional class name */
  className?: string;

  /** Minimal MUI-like style prop. Supports spacing shorthands. */
  sx?: unknown;
}

export interface TabsModernProps<TValue extends string | number = string> extends TabsCommonProps {
  /** Tabs configuration */
  tabs: TabConfig[];
  /** Active tab key */
  activeTab?: TValue;
  /** Back-compat alias for `activeTab`. */
  value?: TValue;
  /** Tab change handler */
  onChange?: (key: TValue) => void;
}

export interface TabsLegacyProps<TValue extends string | number = string> extends TabsCommonProps {
  /** Controlled active tab value */
  value?: TValue;
  /** Tab change handler */
  onChange?: (event: React.SyntheticEvent, value: TValue) => void;
  /** Tab elements */
  children?: React.ReactNode;
  tabs?: never;
  activeTab?: never;
}

export type TabsProps<TValue extends string | number = string> =
  | TabsModernProps<TValue>
  | TabsLegacyProps<TValue>;

const isModernProps = <TValue extends string | number>(props: TabsProps<TValue>): props is TabsModernProps<TValue> =>
  'tabs' in props;

export function Tabs<TValue extends string | number = string>(props: TabsProps<TValue>) {
  const {
    orientation = 'horizontal',
    variant = 'default',
    size = 'md',
    fullWidth = false,
    className,
    sx,
    ...rest
  } = props;

  const isModern = isModernProps(rest as TabsProps<TValue>);

  const derivedTabs: TabConfig[] = useMemo(() => {
    if (isModern) {
      return (rest as TabsModernProps<TValue>).tabs;
    }

    const { children } = rest as TabsLegacyProps<TValue>;

    return React.Children.toArray(children)
      .filter((child): child is React.ReactElement<TabProps<TValue>> =>
        React.isValidElement(child) && child.type === Tab,
      )
      .map((child, index) => {
        const key = (child.props.value ?? (`tab-${index}` as unknown)) as string | number;
        return {
          key,
          label: child.props.label,
          disabled: child.props.disabled,
          icon: child.props.icon,
        };
      });
  }, [isModern, rest]);

  const controlledActiveTab = isModern
    ? ((rest as TabsModernProps<TValue>).activeTab ?? (rest as TabsModernProps<TValue>).value)
    : (rest as TabsLegacyProps<TValue>).value;

  const onChange = isModern
    ? (rest as TabsModernProps<TValue>).onChange
    : (rest as TabsLegacyProps<TValue>).onChange;

  const [internalActiveTab, setInternalActiveTab] = useState((derivedTabs[0]?.key ?? derivedTabs[0]?.value) || '');
  const activeTab = controlledActiveTab !== undefined ? controlledActiveTab : internalActiveTab;

  const handleTabClick = (event: React.SyntheticEvent, key: string | number, disabled?: boolean) => {
    if (disabled) return;
    setInternalActiveTab(key);

    if (isModern) {
      (onChange as TabsModernProps<TValue>['onChange'] | undefined)?.(key as TValue);
    } else {
      (onChange as TabsLegacyProps<TValue>['onChange'] | undefined)?.(event, key as TValue);
    }
  };

  const sizeConfig = {
    sm: { padding: `${tokens.spacing[2]} ${tokens.spacing[3]}`, fontSize: tokens.typography.fontSize.sm },
    md: { padding: `${tokens.spacing[3]} ${tokens.spacing[4]}`, fontSize: tokens.typography.fontSize.base },
    lg: { padding: `${tokens.spacing[4]} ${tokens.spacing[5]}`, fontSize: tokens.typography.fontSize.lg },
  };

  const config = sizeConfig[size];

  const containerStyles: React.CSSProperties = {
    display: 'flex',
    flexDirection: orientation === 'vertical' ? 'row' : 'column',
    gap: orientation === 'vertical' ? tokens.spacing[4] : 0,
    ...sxToStyle(sx),
  };

  const tabListStyles: React.CSSProperties = {
    display: 'flex',
    flexDirection: orientation === 'vertical' ? 'column' : 'row',
    gap: variant === 'pills' ? tokens.spacing[2] : 0,
    borderBottom:
      orientation === 'horizontal' && variant !== 'pills'
        ? `${tokens.borderWidth[2]} solid ${tokens.colors.neutral[200]}`
        : 'none',
    width: orientation === 'vertical' ? 'auto' : '100%',
  };

  const getTabStyles = (tab: TabConfig, isActive: boolean): React.CSSProperties => {
    const baseStyles: React.CSSProperties = {
      ...config,
      display: 'flex',
      alignItems: 'center',
      gap: tokens.spacing[2],
      fontFamily: tokens.typography.fontFamily.sans,
      fontWeight: isActive ? tokens.typography.fontWeight.semibold : tokens.typography.fontWeight.medium,
      color: tab.disabled
        ? tokens.colors.neutral[400]
        : isActive
          ? tokens.colors.primary[600]
          : tokens.colors.neutral[700],
      backgroundColor: 'transparent',
      border: 'none',
      cursor: tab.disabled ? 'not-allowed' : 'pointer',
      transition: `all ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
      whiteSpace: 'nowrap',
      flex: fullWidth && orientation === 'horizontal' ? 1 : 'none',
      justifyContent: fullWidth && orientation === 'horizontal' ? 'center' : 'flex-start',
    };

    if (variant === 'pills') {
      return {
        ...baseStyles,
        borderRadius: tokens.borderRadius.md,
        backgroundColor: isActive ? tokens.colors.primary[100] : 'transparent',
      };
    }

    if (variant === 'underline') {
      return {
        ...baseStyles,
        borderBottom: isActive ? `2px solid ${tokens.colors.primary[600]}` : `2px solid transparent`,
        marginBottom: orientation === 'horizontal' ? '-2px' : 0,
      };
    }

    // default variant
    return {
      ...baseStyles,
      borderBottom: isActive ? `3px solid ${tokens.colors.primary[600]}` : `3px solid transparent`,
      marginBottom: orientation === 'horizontal' ? '-2px' : 0,
    };
  };

  const tabPanelStyles: React.CSSProperties = {
    padding: tokens.spacing[4],
    flex: 1,
  };

  const activeTabContent = isModern
    ? derivedTabs.find((tab) => (tab.key ?? tab.value) === activeTab)?.content
    : undefined;

  return (
    <div style={containerStyles} className={className}>
      <div style={tabListStyles} role="tablist" aria-orientation={orientation}>
        {derivedTabs.map((tab) => {
          const tabKey = tab.key ?? tab.value ?? '';
          const isActive = tabKey === activeTab;
          return (
            <button
              key={tabKey}
              role="tab"
              aria-selected={isActive}
              aria-disabled={tab.disabled}
              tabIndex={isActive ? 0 : -1}
              style={getTabStyles(tab, isActive)}
              onClick={(e) => handleTabClick(e, tabKey, tab.disabled)}
              onMouseEnter={(e) => {
                if (!tab.disabled && !isActive) {
                  if (variant === 'pills') {
                    e.currentTarget.style.backgroundColor = tokens.colors.neutral[100];
                  } else {
                    e.currentTarget.style.color = tokens.colors.primary[600];
                  }
                }
              }}
              onMouseLeave={(e) => {
                if (!tab.disabled && !isActive) {
                  if (variant === 'pills') {
                    e.currentTarget.style.backgroundColor = 'transparent';
                  } else {
                    e.currentTarget.style.color = tokens.colors.neutral[700];
                  }
                }
              }}
            >
              {tab.icon && <span>{tab.icon}</span>}
              {tab.label}
            </button>
          );
        })}
      </div>
      {activeTabContent && (
        <div role="tabpanel" style={tabPanelStyles}>
          {activeTabContent}
        </div>
      )}
    </div>
  );
}

Tabs.displayName = 'Tabs';
