import React from 'react';
import { tokens } from '@ghatana/tokens';

export interface TimelineEvent {
  key: string;
  title: string;
  description?: string;
  timestamp?: string;
  icon?: React.ReactNode;
  variant?: 'default' | 'success' | 'warning' | 'error' | 'info';
}

export interface TimelineProps {
  /** Timeline events */
  events: TimelineEvent[];
  /** Orientation */
  orientation?: 'vertical' | 'horizontal';
  /** Size */
  size?: 'sm' | 'md' | 'lg';
  /** Additional class name */
  className?: string;
}

export const Timeline: React.FC<TimelineProps> = ({
  events,
  orientation = 'vertical',
  size = 'md',
  className,
}) => {
  const sizeConfig = {
    sm: { dotSize: '12px', padding: tokens.spacing[2], fontSize: tokens.typography.fontSize.sm },
    md: { dotSize: '16px', padding: tokens.spacing[3], fontSize: tokens.typography.fontSize.base },
    lg: { dotSize: '20px', padding: tokens.spacing[4], fontSize: tokens.typography.fontSize.lg },
  };

  const config = sizeConfig[size];

  const variantColors = {
    default: tokens.colors.neutral[600],
    success: tokens.colors.success[600],
    warning: tokens.colors.warning[600],
    error: tokens.colors.error[600],
    info: tokens.colors.primary[600],
  };

  const containerStyles: React.CSSProperties = {
    display: 'flex',
    flexDirection: orientation === 'vertical' ? 'column' : 'row',
    gap: orientation === 'vertical' ? 0 : tokens.spacing[4],
    width: '100%',
  };

  const eventStyles: React.CSSProperties = {
    display: 'flex',
    flexDirection: orientation === 'vertical' ? 'row' : 'column',
    gap: tokens.spacing[2],
    position: 'relative',
    flex: orientation === 'horizontal' ? 1 : 'none',
  };

  const getDotStyles = (variant: string = 'default'): React.CSSProperties => ({
    width: config.dotSize,
    height: config.dotSize,
    borderRadius: tokens.borderRadius.full,
    backgroundColor: variantColors[variant as keyof typeof variantColors] || variantColors.default,
    border: `3px solid ${tokens.colors.white}`,
    boxShadow: tokens.shadows.sm,
    flexShrink: 0,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: tokens.colors.white,
    fontSize: tokens.typography.fontSize.xs,
    fontWeight: tokens.typography.fontWeight.bold,
  });

  const contentStyles: React.CSSProperties = {
    flex: 1,
    paddingTop: orientation === 'vertical' ? tokens.spacing[1] : 0,
  };

  const titleStyles: React.CSSProperties = {
    fontFamily: tokens.typography.fontFamily.sans,
    fontSize: config.fontSize,
    fontWeight: tokens.typography.fontWeight.semibold,
    color: tokens.colors.neutral[900],
  };

  const descriptionStyles: React.CSSProperties = {
    fontFamily: tokens.typography.fontFamily.sans,
    fontSize: tokens.typography.fontSize.sm,
    color: tokens.colors.neutral[600],
    marginTop: tokens.spacing[1],
  };

  const timestampStyles: React.CSSProperties = {
    fontFamily: tokens.typography.fontFamily.sans,
    fontSize: tokens.typography.fontSize.xs,
    color: tokens.colors.neutral[500],
    marginTop: tokens.spacing[1],
  };

  const connectorStyles: React.CSSProperties = {
    position: 'absolute',
    backgroundColor: tokens.colors.neutral[300],
    ...(orientation === 'vertical'
      ? {
          left: `calc(${config.dotSize} / 2 - 1px)`,
          top: `calc(${config.dotSize} + ${tokens.spacing[2]})`,
          width: '2px',
          height: `calc(100% - ${config.dotSize} - ${tokens.spacing[2]})`,
        }
      : {
          top: `calc(${config.dotSize} / 2 - 1px)`,
          left: `calc(${config.dotSize} + ${tokens.spacing[2]})`,
          height: '2px',
          width: `calc(100% - ${config.dotSize} - ${tokens.spacing[2]})`,
        }),
  };

  return (
    <div style={containerStyles} className={className}>
      {events.map((event, index) => {
        const isLast = index === events.length - 1;
        const variant = event.variant || 'default';

        return (
          <div key={event.key} style={eventStyles}>
            {orientation === 'vertical' && !isLast && <div style={connectorStyles} />}
            <div style={getDotStyles(variant)}>
              {event.icon ? event.icon : index + 1}
            </div>
            <div style={contentStyles}>
              <div style={titleStyles}>{event.title}</div>
              {event.description && <div style={descriptionStyles}>{event.description}</div>}
              {event.timestamp && <div style={timestampStyles}>{event.timestamp}</div>}
            </div>
            {orientation === 'horizontal' && !isLast && (
              <div
                style={{
                  ...connectorStyles,
                  position: 'relative',
                  top: 'auto',
                  left: 'auto',
                  width: tokens.spacing[4],
                  height: '2px',
                }}
              />
            )}
          </div>
        );
      })}
    </div>
  );
};

Timeline.displayName = 'Timeline';
