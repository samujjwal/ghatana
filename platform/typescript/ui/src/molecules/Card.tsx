import * as React from 'react';
import { cn } from '@ghatana/utils';
import {
  lightShadows,
  darkShadows,
  lightColors,
  darkColors,
  componentRadius,
  fontSize,
  fontWeight,
} from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';
import { Box, type BoxProps } from '../layout/Box';
import { sxToStyle } from '../utils/sx';

export type CardVariant = 'elevated' | 'outlined' | 'subtle';

export interface CardProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'title'> {
  variant?: CardVariant;
  elevation?: number;
  title?: React.ReactNode;
  subtitle?: React.ReactNode;
  headerActions?: React.ReactNode;
  footer?: React.ReactNode;
  media?: React.ReactNode;
  padded?: boolean;
  hover?: boolean;
  interactive?: boolean;
  /** Minimal MUI-like style prop. Supports spacing shorthands. */
  sx?: unknown;
}

export interface CardHeaderProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'title'> {
  title?: React.ReactNode;
  subheader?: React.ReactNode;
  action?: React.ReactNode;
  avatar?: React.ReactNode;
}

export interface CardContentProps extends BoxProps { }

export interface CardActionsProps extends BoxProps {
  disableSpacing?: boolean;
}

export interface CardMediaProps extends React.ImgHTMLAttributes<HTMLDivElement> {
  component?: React.ElementType;
  image?: string;
  alt?: string;
}

/**
 * Card component – flexible surface container.
 */
export const Card = React.forwardRef<HTMLDivElement, CardProps>((props, ref) => {
  const {
    variant = 'elevated',
    elevation = 6,
    title,
    subtitle,
    headerActions,
    footer,
    media,
    padded = true,
    hover = false,
    interactive = false,
    className,
    children,
    sx,
    style,
    role,
    tabIndex,
    onMouseEnter,
    onMouseLeave,
    ...rest
  } = props;

  const { resolvedTheme } = useTheme();
  const isDark = resolvedTheme === 'dark';
  const surface = isDark ? darkColors : lightColors;
  const [isHovered, setIsHovered] = React.useState(false);

  const background =
    variant === 'subtle' ? surface.background.surface : surface.background.elevated;
  const borderColor = variant === 'outlined' ? surface.border : 'transparent';

  const shadowIndex = Math.min(
    variant === 'outlined' || variant === 'subtle' ? 0 : elevation,
    isDark ? darkShadows.length - 1 : lightShadows.length - 1
  );

  const elevatedShadow = isDark ? darkShadows[shadowIndex] : lightShadows[shadowIndex];
  const hoverShadow = isDark
    ? darkShadows[Math.min(darkShadows.length - 1, shadowIndex + 1)]
    : lightShadows[Math.min(lightShadows.length - 1, shadowIndex + 1)];

  const boxShadow = variant === 'elevated'
    ? (hover && (isHovered || interactive) ? hoverShadow : elevatedShadow)
    : 'none';

  const baseStyle: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    borderRadius: `${componentRadius.card}px`,
    borderWidth: 1,
    borderStyle: 'solid',
    borderColor,
    boxShadow,
    backgroundColor: background,
    color: surface.text.primary,
    overflow: 'hidden',
    transition: hover ? 'transform 180ms ease, box-shadow 180ms ease' : undefined,
    cursor: interactive ? 'pointer' : undefined,
    transform: hover && (isHovered || interactive) ? 'translateY(-2px)' : undefined,
  };

  const mergedStyle = style ? { ...baseStyle, ...sxToStyle(sx), ...style } : { ...baseStyle, ...sxToStyle(sx) };

  return (
    <div
      ref={ref}
      className={cn('gh-card', className, hover && 'gh-card--hover')}
      style={mergedStyle}
      role={interactive ? 'button' : role}
      tabIndex={interactive ? 0 : tabIndex}
      onMouseEnter={(event) => {
        if (hover) setIsHovered(true);
        onMouseEnter?.(event);
      }}
      onMouseLeave={(event) => {
        if (hover) setIsHovered(false);
        onMouseLeave?.(event);
      }}
      {...rest}
    >
      {media ? (
        <div className="gh-card__media" style={{ display: 'block' }}>
          {media}
        </div>
      ) : null}

      {(title || subtitle || headerActions) && (
        <div
          className="gh-card__header"
          style={{
            display: 'flex',
            alignItems: subtitle ? 'flex-start' : 'center',
            justifyContent: 'space-between',
            gap: '12px',
            padding: padded ? '20px' : '16px',
          }}
        >
          <div style={{ flex: '1 1 auto' }}>
            {title ? (
              <div
                className="gh-card__title"
                style={{
                  fontSize: fontSize.lg,
                  fontWeight: fontWeight.semibold,
                  marginBottom: subtitle ? '4px' : 0,
                }}
              >
                {title}
              </div>
            ) : null}
            {subtitle ? (
              <div
                className="gh-card__subtitle"
                style={{
                  fontSize: fontSize.sm,
                  color: surface.text.secondary,
                }}
              >
                {subtitle}
              </div>
            ) : null}
          </div>
          {headerActions ? (
            <div className="gh-card__actions" style={{ display: 'inline-flex', gap: '8px' }}>
              {headerActions}
            </div>
          ) : null}
        </div>
      )}

      <div
        className="gh-card__body"
        style={{
          flex: '1 1 auto',
          padding: padded ? '20px' : '16px',
          display: 'flex',
          flexDirection: 'column',
          gap: '12px',
        }}
      >
        {children}
      </div>

      {footer ? (
        <div
          className="gh-card__footer"
          style={{
            padding: padded ? '20px' : '16px',
            borderTop: `1px solid ${surface.border}`,
          }}
        >
          {footer}
        </div>
      ) : null}
    </div>
  );
});

Card.displayName = 'Card';

// CardHeader component (exported for consumer composition)
export const CardHeader: React.FC<CardHeaderProps> = ({ title, subheader, action, avatar, className, style, ...rest }) => {
  return (
    <div
      className={cn('gh-card__header', className)}
      style={{ display: 'flex', alignItems: subheader ? 'flex-start' : 'center', justifyContent: 'space-between', gap: '12px', padding: '16px', ...style }}
      {...rest}
    >
      <div style={{ flex: '1 1 auto' }}>
        {avatar ? <div style={{ display: 'inline-flex', marginRight: '8px' }}>{avatar}</div> : null}
        {title ? <div className="gh-card__title" style={{ fontSize: fontSize.lg, fontWeight: fontWeight.semibold }}>{title}</div> : null}
        {subheader ? <div className="gh-card__subtitle" style={{ fontSize: fontSize.sm }}>{subheader}</div> : null}
      </div>
      {action ? <div className="gh-card__actions" style={{ display: 'inline-flex', gap: '8px' }}>{action}</div> : null}
    </div>
  );
};

// CardContent component
export const CardContent: React.FC<CardContentProps> = ({ children, className, style, ...rest }) => {
  return (
    <Box className={cn('gh-card__content', className)} style={{ padding: '16px', display: 'flex', flexDirection: 'column', gap: '12px', ...style }} {...rest}>
      {children}
    </Box>
  );
};

// CardActions component
export const CardActions: React.FC<CardActionsProps> = ({ children, disableSpacing = false, className, style, ...rest }) => {
  return (
    <Box className={cn('gh-card__actions', className)} style={{ display: 'flex', gap: disableSpacing ? undefined : '8px', padding: '8px 16px', justifyContent: 'flex-end', ...style }} {...rest}>
      {children}
    </Box>
  );
};

// CardMedia component
export const CardMedia: React.FC<CardMediaProps> = ({ component: Component = 'div', image, alt, style, children, ...rest }) => {
  const Tag = Component as React.ElementType;
  return (
    <Tag className="gh-card__media" style={{ display: 'block', backgroundImage: image ? `url(${image})` : undefined, backgroundSize: 'cover', backgroundPosition: 'center', ...style }} role={image ? undefined : 'img'} aria-label={alt} {...rest}>
      {children}
    </Tag>
  );
};
