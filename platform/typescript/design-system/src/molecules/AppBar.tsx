import React from 'react';
import { tokens } from '@ghatana/tokens';

export interface AppBarProps {
  /** App bar title */
  title?: string | React.ReactNode;
  /** Left content (logo, menu button) */
  left?: React.ReactNode;
  /** Center content */
  center?: React.ReactNode;
  /** Right content (actions, user menu) */
  right?: React.ReactNode;
  /** App bar variant */
  variant?: 'default' | 'elevated' | 'outlined';
  /** Position */
  position?: 'static' | 'sticky' | 'fixed';
  /** Background color */
  backgroundColor?: string;
  /** Text color */
  textColor?: string;
  /** Additional class name */
  className?: string;
}

export const AppBar: React.FC<AppBarProps> = ({
  title,
  left,
  center,
  right,
  variant = 'elevated',
  position = 'static',
  backgroundColor = tokens.colors.white,
  textColor = tokens.colors.neutral[900],
  className,
}) => {
  const getVariantStyles = (): React.CSSProperties => {
    const baseStyles: React.CSSProperties = {
      backgroundColor,
      color: textColor,
      transition: `all ${tokens.transitions.duration.normal} ${tokens.transitions.easing.easeInOut}`,
    };

    if (variant === 'elevated') {
      return {
        ...baseStyles,
        boxShadow: tokens.shadows.md,
      };
    }

    if (variant === 'outlined') {
      return {
        ...baseStyles,
        borderBottom: `${tokens.borderWidth[1]} solid ${tokens.colors.neutral[200]}`,
      };
    }

    return baseStyles;
  };

  const containerStyles: React.CSSProperties = {
    ...getVariantStyles(),
    position: position as React.CSSProperties['position'],
    top: 0,
    left: 0,
    right: 0,
    zIndex: tokens.zIndex.appBar,
    display: 'flex',
    alignItems: 'center',
    padding: `${tokens.spacing[2]} ${tokens.spacing[4]}`,
    gap: tokens.spacing[4],
    minHeight: '64px',
    fontFamily: tokens.typography.fontFamily.sans,
  };

  const sectionStyles: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: tokens.spacing[2],
  };

  const titleStyles: React.CSSProperties = {
    fontSize: tokens.typography.fontSize.lg,
    fontWeight: tokens.typography.fontWeight.semibold,
    margin: 0,
    flex: 1,
  };

  const leftSectionStyles: React.CSSProperties = {
    ...sectionStyles,
    minWidth: 'fit-content',
  };

  const centerSectionStyles: React.CSSProperties = {
    ...sectionStyles,
    flex: 1,
    justifyContent: 'center',
  };

  const rightSectionStyles: React.CSSProperties = {
    ...sectionStyles,
    marginLeft: 'auto',
    minWidth: 'fit-content',
  };

  return (
    <header style={containerStyles} className={className} role="banner">
      {left && <div style={leftSectionStyles}>{left}</div>}

      {center ? (
        <div style={centerSectionStyles}>{center}</div>
      ) : (
        <>
          {title && (
            <h1 style={titleStyles}>
              {typeof title === 'string' ? title : title}
            </h1>
          )}
        </>
      )}

      {right && <div style={rightSectionStyles}>{right}</div>}
    </header>
  );
};

AppBar.displayName = 'AppBar';
