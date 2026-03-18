import React from 'react';
import { tokens } from '@ghatana/tokens'; // tokens import added for design tokens usage
import { sxToStyle, type SxProps } from '../utils/sx';

export interface ListItemProps extends React.LiHTMLAttributes<HTMLLIElement> {
  divider?: boolean;
  disablePadding?: boolean;
  secondaryAction?: React.ReactNode;
  /** MUI-like sx prop (limited support). */
  sx?: SxProps;
}

export const ListItem: React.FC<ListItemProps> = ({ divider, disablePadding, secondaryAction, sx, style, children, ...props }) => {
  return (
    <li
      {...props}
      style={{
        display: 'flex',
        alignItems: 'center',
        padding: disablePadding ? 0 : `${tokens.spacing[1]} ${tokens.spacing[2]}`,
        borderBottom: divider ? `${tokens.borderWidth[1]} solid ${tokens.colors.neutral[200]}` : undefined,
        ...sxToStyle(sx),
        ...style,
      }}
    >
      {children}
      {secondaryAction ? (
        <span style={{ marginLeft: 'auto', display: 'inline-flex', alignItems: 'center' }}>{secondaryAction}</span>
      ) : null}
    </li>
  );
};

export interface ListItemSecondaryActionProps extends React.HTMLAttributes<HTMLSpanElement> { }

export const ListItemSecondaryAction: React.FC<ListItemSecondaryActionProps> = ({ style, children, ...props }) => {
  return (
    <span
      {...props}
      style={{
        marginLeft: 'auto',
        display: 'inline-flex',
        alignItems: 'center',
        ...style,
      }}
    >
      {children}
    </span>
  );
};

export interface ListItemAvatarProps extends React.HTMLAttributes<HTMLDivElement> { }

export const ListItemAvatar: React.FC<ListItemAvatarProps> = ({ style, children, ...props }) => {
  return (
    <div
      {...props}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        marginRight: tokens.spacing[2],
        ...style,
      }}
    >
      {children}
    </div>
  );
};

export interface ListItemTextProps extends React.HTMLAttributes<HTMLDivElement> {
  primary?: React.ReactNode;
  secondary?: React.ReactNode;
}

export const ListItemText: React.FC<ListItemTextProps> = ({ primary, secondary, style, children, ...props }) => {
  return (
    <div {...props} style={{ flex: 1, ...style }}>
      {primary ?? children}
      {secondary ? (
        <div style={{ marginTop: tokens.spacing[1], color: tokens.colors.neutral[600], fontSize: tokens.typography.fontSize.sm }}>
          {secondary}
        </div>
      ) : null}
    </div>
  );
};

export interface ListItemButtonProps
  extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'type'> {
  selected?: boolean;
}

export const ListItemButton: React.FC<ListItemButtonProps> = ({ selected, style, children, ...props }) => {
  return (
    <button
      type="button"
      {...props}
      style={{
        width: '100%',
        display: 'flex',
        alignItems: 'center',
        gap: tokens.spacing[2],
        background: selected ? tokens.colors.primary[50] : 'transparent',
        border: 'none',
        padding: `${tokens.spacing[1]} ${tokens.spacing[2]}`,
        cursor: 'pointer',
        textAlign: 'left',
        ...style,
      }}
    >
      {children}
    </button>
  );
};

export interface InteractiveListItem {
  key: string;
  label: string;
  description?: string;
  icon?: React.ReactNode;
  avatar?: string;
  badge?: string | number;
  onClick?: () => void;
  disabled?: boolean;
  selected?: boolean;
}

export interface InteractiveListProps {
  /** List items */
  items: InteractiveListItem[];
  /** List orientation */
  orientation?: 'vertical' | 'horizontal';
  /** Variant */
  variant?: 'default' | 'bordered' | 'divided';
  /** Size */
  size?: 'sm' | 'md' | 'lg';
  /** Selectable items */
  selectable?: boolean;
  /** Item selection handler */
  onItemSelect?: (key: string) => void;
  /** Additional class name */
  className?: string;
}

/**
 * InteractiveList component for displaying interactive lists
 */
export const InteractiveList: React.FC<InteractiveListProps> = ({
  items,
  orientation = 'vertical',
  variant = 'default',
  size = 'md',
  selectable = false,
  onItemSelect,
  className,
}) => {
  const sizeConfig = {
    sm: { padding: '0.5rem', gap: '0.5rem', fontSize: 14 }, // TODO: Replace with tokens when available
    md: { padding: '0.75rem', gap: '0.75rem', fontSize: 16 }, // TODO: Replace with tokens when available
    lg: { padding: '1rem', gap: '1rem', fontSize: 18 }, // TODO: Replace with tokens when available
  };

  const config = sizeConfig[size];

  const containerStyles: React.CSSProperties = {
    display: 'flex',
    flexDirection: orientation === 'vertical' ? 'column' : 'row',
    gap: config.gap,
    width: '100%',
    listStyle: 'none',
    margin: 0,
    padding: 0,
  };

  const getItemStyles = (item: InteractiveListItem): React.CSSProperties => {
    const baseStyles: React.CSSProperties = {
      display: 'flex',
      alignItems: 'center',
      gap: '0.5rem', // TODO: Replace with tokens.spacing[2] when available
      padding: config.padding,
      borderRadius: '0.25rem', // TODO: Replace with tokens.borderRadius.md when available
      cursor: item.disabled ? 'not-allowed' : selectable ? 'pointer' : 'default',
      transition: 'all 0.2s ease-in-out', // TODO: Replace with tokens.transitions when available
      opacity: item.disabled ? 0.5 : 1,
      backgroundColor: item.selected ? '#E3F2FD' : 'transparent', // TODO: Replace with tokens.colors.primary[50] when available
      border:
        variant === 'bordered'
          ? '1px solid #E0E0E0' // TODO: Replace with tokens when available
          : 'none',
    };

    return baseStyles;
  };

  const dividerStyles: React.CSSProperties = {
    height: orientation === 'vertical' ? '1px' : 'auto',
    width: orientation === 'vertical' ? '100%' : '1px',
    backgroundColor: '#E0E0E0', // TODO: Replace with tokens.colors.neutral[200] when available
  };

  const labelStyles: React.CSSProperties = {
    fontFamily: 'Inter, system-ui, sans-serif', // TODO: Replace with tokens.typography.fontFamily.sans when available
    fontSize: config.fontSize,
    fontWeight: 500, // TODO: Replace with tokens.typography.fontWeight.medium when available
    color: '#212121', // TODO: Replace with tokens.colors.neutral[900] when available
  };

  const descriptionStyles: React.CSSProperties = {
    fontFamily: 'Inter, system-ui, sans-serif', // TODO: Replace with tokens.typography.fontFamily.sans when available
    fontSize: 14, // TODO: Replace with tokens.typography.fontSize.sm when available
    color: '#757575', // TODO: Replace with tokens.colors.neutral[600] when available
    marginTop: '0.25rem', // TODO: Replace with tokens.spacing[1] when available
  };

  const badgeStyles: React.CSSProperties = {
    marginLeft: 'auto',
    padding: '0.25rem 0.5rem', // TODO: Replace with tokens.spacing[1] and tokens.spacing[2] when available
    backgroundColor: '#E3F2FD', // TODO: Replace with tokens.colors.primary[100] when available
    color: '#1976D2', // TODO: Replace with tokens.colors.primary[700] when available
    borderRadius: '9999px', // TODO: Replace with tokens.borderRadius.full when available
    fontSize: 12, // TODO: Replace with tokens.typography.fontSize.xs when available
    fontWeight: 600, // TODO: Replace with tokens.typography.fontWeight.semibold when available
  };

  const renderItem = (item: InteractiveListItem, index: number) => {
    const showDivider = variant === 'divided' && index < items.length - 1;

    return (
      <React.Fragment key={item.key}>
        <li
          style={getItemStyles(item)}
          onClick={() => {
            if (!item.disabled && selectable) {
              onItemSelect?.(item.key);
            }
          }}
          role={selectable ? 'option' : 'listitem'}
          aria-selected={selectable ? item.selected : undefined}
          aria-disabled={item.disabled}
          onMouseEnter={(e) => {
            if (!item.disabled && selectable) {
              e.currentTarget.style.backgroundColor = tokens.colors.primary[100];
            }
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.backgroundColor = item.selected ? tokens.colors.primary[50] : 'transparent';
          }}
        >
          {item.icon && (
            <span style={{ display: 'flex', alignItems: 'center', flexShrink: 0 }}>
              {item.icon}
            </span>
          )}
          {item.avatar && (
            <img
              src={item.avatar}
              alt={item.label}
              style={{
                width: '32px',
                height: '32px',
                borderRadius: tokens.borderRadius.full,
                objectFit: 'cover',
                flexShrink: 0,
              }}
            />
          )}
          <div style={{ flex: 1 }}>
            <div style={labelStyles}>{item.label}</div>
            {item.description && <div style={descriptionStyles}>{item.description}</div>}
          </div>
          {item.badge && <div style={badgeStyles}>{item.badge}</div>}
        </li>
        {showDivider && <li style={dividerStyles} />}
      </React.Fragment>
    );
  };

  return (
    <ul style={containerStyles} className={className} role={selectable ? 'listbox' : 'list'}>
      {items.map((item, index) => renderItem(item, index))}
    </ul>
  );
};

InteractiveList.displayName = 'InteractiveList';
