import * as React from 'react';
import { cn } from '@ghatana/platform-utils';
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
import { createPressableBehavior, useComponentComposition } from '../core';
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
  titleLevel?: 1 | 2 | 3 | 4 | 5 | 6;
}

export interface CardTitleProps extends React.HTMLAttributes<HTMLHeadingElement> {
  level?: 1 | 2 | 3 | 4 | 5 | 6;
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

export const CardTitle: React.FC<CardTitleProps> = ({
  level = 3,
  className,
  style,
  children,
  ...rest
}) => {
  const headingTags = {
    1: 'h1',
    2: 'h2',
    3: 'h3',
    4: 'h4',
    5: 'h5',
    6: 'h6',
  } as const;
  const HeadingTag = headingTags[level];
  return (
    <HeadingTag
      className={cn("gh-card__title", className)}
      style={{
        fontSize: fontSize.lg,
        fontWeight: fontWeight.semibold,
        margin: 0,
        ...style,
      }}
      {...rest}
    >
      {children}
    </HeadingTag>
  );
};

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

  const composition = useComponentComposition({
    metadata: {
      component: 'card',
      variant,
      state: interactive ? 'interactive' : hover && isHovered ? 'hovered' : 'idle',
      privacy: 'public',
    },
    state: {
      hovered: hover && isHovered,
      interactive,
      padded,
      'has-header': Boolean(title || subtitle || headerActions),
      'has-footer': Boolean(footer),
      'has-media': Boolean(media),
    },
    features: [
      'surface',
      hover ? 'hoverable' : undefined,
      interactive ? 'interactive' : undefined,
    ],
    behaviors: [
      interactive
        ? createPressableBehavior({
          role: role ?? 'button',
          tabIndex: tabIndex ?? 0,
        })
        : undefined,
    ],
    rootProps: {
      className: cn('gh-card', className, hover && 'gh-card--hover'),
      style: mergedStyle,
      role: interactive ? 'button' : role,
      tabIndex: interactive ? 0 : tabIndex,
      onMouseEnter: (event) => {
        if (hover) setIsHovered(true);
        onMouseEnter?.(event as unknown as React.MouseEvent<HTMLDivElement>);
      },
      onMouseLeave: (event) => {
        if (hover) setIsHovered(false);
        onMouseLeave?.(event as unknown as React.MouseEvent<HTMLDivElement>);
      },
      ...rest,
    },
  });

  return (
    <div
      ref={ref}
      {...composition.rootProps}
    >
      {media ? (
        <div
          {...composition.getSlotProps(
            'media',
            {
              className: 'gh-card__media',
              style: { display: 'block' },
            },
            { present: true }
          )}
        >
          {media}
        </div>
      ) : null}

      {(title || subtitle || headerActions) && (
        <div
          {...composition.getSlotProps(
            'header',
            {
              className: 'gh-card__header',
              style: {
                display: 'flex',
                alignItems: subtitle ? 'flex-start' : 'center',
                justifyContent: 'space-between',
                gap: '12px',
                padding: padded ? '20px' : '16px',
              },
            },
            { present: true }
          )}
        >
          <div {...composition.getSlotProps('header-content', { style: { flex: '1 1 auto' } })}>
            {title ? (
              <div
                {...composition.getSlotProps('title', {
                  className: 'gh-card__title',
                  style: {
                    fontSize: fontSize.lg,
                    fontWeight: fontWeight.semibold,
                    marginBottom: subtitle ? '4px' : 0,
                  },
                })}
              >
                {title}
              </div>
            ) : null}
            {subtitle ? (
              <div
                {...composition.getSlotProps('subtitle', {
                  className: 'gh-card__subtitle',
                  style: {
                    fontSize: fontSize.sm,
                    color: surface.text.secondary,
                  },
                })}
              >
                {subtitle}
              </div>
            ) : null}
          </div>
          {headerActions ? (
            <div
              {...composition.getSlotProps('header-actions', {
                className: 'gh-card__actions',
                style: { display: 'inline-flex', gap: '8px' },
              })}
            >
              {headerActions}
            </div>
          ) : null}
        </div>
      )}

      <div
        {...composition.getSlotProps(
          'body',
          {
            className: 'gh-card__body',
            style: {
              flex: '1 1 auto',
              padding: padded ? '20px' : '16px',
              display: 'flex',
              flexDirection: 'column',
              gap: '12px',
            },
          },
          { present: true }
        )}
      >
        {children}
      </div>

      {footer ? (
        <div
          {...composition.getSlotProps(
            'footer',
            {
              className: 'gh-card__footer',
              style: {
                padding: padded ? '20px' : '16px',
                borderTop: `1px solid ${surface.border}`,
              },
            },
            { present: true }
          )}
        >
          {footer}
        </div>
      ) : null}
    </div>
  );
});

Card.displayName = 'Card';

// CardHeader component (exported for consumer composition)
export const CardHeader: React.FC<CardHeaderProps> = ({ title, subheader, action, avatar, titleLevel = 2, className, style, ...rest }) => {
  const TitleElement = `h${titleLevel}` as keyof React.JSX.IntrinsicElements;
  const headerTitle = title ?? rest.children;
  const shouldWrapTitle = title !== undefined;

  return (
    <div
      className={cn('gh-card__header', className)}
      style={{ display: 'flex', alignItems: subheader ? 'flex-start' : 'center', justifyContent: 'space-between', gap: '12px', padding: '16px', ...style }}
      {...rest}
    >
      <div style={{ flex: '1 1 auto' }}>
        {avatar ? <div style={{ display: 'inline-flex', marginRight: '8px' }}>{avatar}</div> : null}
        {headerTitle && shouldWrapTitle ? (
          <TitleElement
            className="gh-card__title"
            style={{ margin: 0, fontSize: fontSize.lg, fontWeight: fontWeight.semibold }}
          >
            {headerTitle}
          </TitleElement>
        ) : null}
        {!shouldWrapTitle && headerTitle ? headerTitle : null}
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

Object.assign(Card, {
  Header: CardHeader,
  Content: CardContent,
  Actions: CardActions,
  Media: CardMedia,
});
