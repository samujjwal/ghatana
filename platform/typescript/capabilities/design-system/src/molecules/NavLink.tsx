import React from 'react';
import { tokens } from '@ghatana/tokens';

export interface NavLinkProps {
  href: string;
  children: React.ReactNode;
  active?: boolean;
  icon?: React.ReactNode;
  badge?: string | number;
  disabled?: boolean;
  onClick?: (e: React.MouseEvent) => void;
  className?: string;
}

/**
 * NavLink component for navigation links
 */
export const NavLink: React.FC<NavLinkProps> = ({
  href,
  children,
  active,
  icon,
  badge,
  disabled,
  onClick,
  className,
}) => {
  const linkStyles: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: tokens.spacing[2],
    padding: `${tokens.spacing[2]} ${tokens.spacing[3]}`,
    borderRadius: tokens.borderRadius.md,
    textDecoration: 'none',
    color: active ? tokens.colors.primary[600] : tokens.colors.neutral[700],
    backgroundColor: active ? tokens.colors.primary[50] : 'transparent',
    borderLeft: active ? `4px solid ${tokens.colors.primary[600]}` : '4px solid transparent',
    cursor: disabled ? 'not-allowed' : 'pointer',
    opacity: disabled ? 0.5 : 1,
    transition: `all ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
    fontWeight: active ? 600 : 500,
  };

  const badgeStyles: React.CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: '20px',
    height: '20px',
    padding: '0 6px',
    borderRadius: tokens.borderRadius.full,
    backgroundColor: tokens.colors.primary[600],
    color: tokens.colors.neutral[0],
    fontSize: tokens.typography.fontSize.xs,
    fontWeight: 600,
    marginLeft: 'auto',
  };

  const handleClick = (e: React.MouseEvent) => {
    if (disabled) {
      e.preventDefault();
      return;
    }
    onClick?.(e);
  };

  return (
    <a
      href={href}
      style={linkStyles}
      className={className}
      onClick={handleClick}
      aria-current={active ? 'page' : undefined}
      aria-disabled={disabled}
      role="navigation"
    >
      {icon && <span style={{ display: 'flex', alignItems: 'center' }}>{icon}</span>}
      <span style={{ flex: 1 }}>{children}</span>
      {badge && <div style={badgeStyles}>{badge}</div>}
    </a>
  );
};

NavLink.displayName = 'NavLink';
