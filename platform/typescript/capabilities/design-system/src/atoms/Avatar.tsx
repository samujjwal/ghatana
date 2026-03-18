import React, { useState } from 'react';
import { tokens } from '@ghatana/tokens';
import { sxToStyle, type SxProps } from '../utils/sx';

export interface AvatarProps {
  /** Image source URL */
  src?: string;
  /** Alt text for image */
  alt?: string;
  /** Optional tooltip/title attribute (compatibility). */
  title?: string;
  /** Fallback text (initials) */
  fallback?: string;
  /** Size variant */
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl' | '2xl' | 'small' | 'medium' | 'large';
  /** Shape variant */
  shape?: 'circle' | 'square';
  /** Status indicator */
  status?: 'online' | 'offline' | 'away' | 'busy';
  /** Additional class name */
  className?: string;
  /** Inline styles */
  style?: React.CSSProperties;
  /** MUI-like sx prop (limited support). */
  sx?: SxProps;
  /** Children content (e.g., initials), MUI-style. */
  children?: React.ReactNode;
  /** Click handler */
  onClick?: () => void;
}

export const Avatar: React.FC<AvatarProps> = ({
  src,
  alt = 'Avatar',
  title,
  fallback,
  size = 'md',
  shape = 'circle',
  status,
  className,
  style,
  sx,
  children,
  onClick,
}) => {
  const [imageError, setImageError] = useState(false);

  const resolvedSize: Exclude<AvatarProps['size'], 'small' | 'medium' | 'large'> =
    size === 'small' ? 'sm' : size === 'medium' ? 'md' : size === 'large' ? 'lg' : size;

  const sizeConfig = {
    xs: { size: '24px', fontSize: tokens.typography.fontSize.xs },
    sm: { size: '32px', fontSize: tokens.typography.fontSize.sm },
    md: { size: '40px', fontSize: tokens.typography.fontSize.base },
    lg: { size: '48px', fontSize: tokens.typography.fontSize.lg },
    xl: { size: '64px', fontSize: tokens.typography.fontSize.xl },
    '2xl': { size: '96px', fontSize: tokens.typography.fontSize['2xl'] },
  };

  const statusColors = {
    online: tokens.colors.success[500],
    offline: tokens.colors.neutral[400],
    away: tokens.colors.warning[500],
    busy: tokens.colors.error[500],
  };

  const config = sizeConfig[resolvedSize];

  const containerStyles: React.CSSProperties = {
    position: 'relative',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: config.size,
    height: config.size,
    borderRadius: shape === 'circle' ? tokens.borderRadius.full : tokens.borderRadius.md,
    overflow: 'hidden',
    backgroundColor: tokens.colors.neutral[200],
    color: tokens.colors.neutral[700],
    fontSize: config.fontSize,
    fontWeight: tokens.typography.fontWeight.medium,
    fontFamily: tokens.typography.fontFamily.sans,
    cursor: onClick ? 'pointer' : 'default',
    transition: `all ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
  };

  const imageStyles: React.CSSProperties = {
    width: '100%',
    height: '100%',
    objectFit: 'cover',
  };

  const statusIndicatorStyles: React.CSSProperties = {
    position: 'absolute',
    bottom: '0',
    right: '0',
    width: resolvedSize === 'xs' ? '6px' : resolvedSize === 'sm' ? '8px' : '10px',
    height: resolvedSize === 'xs' ? '6px' : resolvedSize === 'sm' ? '8px' : '10px',
    borderRadius: tokens.borderRadius.full,
    backgroundColor: status ? statusColors[status] : 'transparent',
    border: `2px solid ${tokens.colors.white}`,
  };

  const showImage = src && !imageError;
  const showChildren = !showImage && children != null;
  const showFallback = !showImage && !showChildren && fallback;

  return (
    <div
      style={{ ...containerStyles, ...style, ...sxToStyle(sx) }}
      className={className}
      title={title}
      onClick={onClick}
      onMouseEnter={(e) => {
        if (onClick) {
          e.currentTarget.style.opacity = '0.8';
        }
      }}
      onMouseLeave={(e) => {
        if (onClick) {
          e.currentTarget.style.opacity = '1';
        }
      }}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      onKeyDown={(e) => {
        if (onClick && (e.key === 'Enter' || e.key === ' ')) {
          e.preventDefault();
          onClick();
        }
      }}
    >
      {showImage ? (
        <img
          src={src}
          alt={alt}
          style={imageStyles}
          onError={() => setImageError(true)}
        />
      ) : showChildren ? (
        <span>{children}</span>
      ) : showFallback ? (
        <span>{fallback}</span>
      ) : (
        <svg
          width="60%"
          height="60%"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
          <circle cx="12" cy="7" r="4" />
        </svg>
      )}
      {status && <div style={statusIndicatorStyles} />}
    </div>
  );
};

Avatar.displayName = 'Avatar';
