import * as React from 'react';
import { tokens } from '@ghatana/tokens';
import { cn } from '@ghatana/utils';
import { sxToStyle, type SxProps } from '../utils/sx';

export interface AvatarGroupProps extends React.HTMLAttributes<HTMLDivElement> {
    /** Maximum avatars to display before showing a "+N" overflow avatar. */
    max?: number;
    /** Override the displayed total (used to compute "+N"). */
    total?: number;
    /** Overlap spacing in pixels. */
    spacing?: number;
    /** MUI-like sx prop (limited support). */
    sx?: SxProps;
    children?: React.ReactNode;
}

export function AvatarGroup({
    max,
    total,
    spacing = 8,
    className,
    style,
    sx,
    children,
    ...rest
}: AvatarGroupProps) {
    const items = React.Children.toArray(children).filter(Boolean);
    const renderedCount = max !== undefined ? Math.min(max, items.length) : items.length;
    const hiddenCount = Math.max(0, (total ?? items.length) - renderedCount);

    return (
        <div
            className={cn('gh-avatar-group', className)}
            style={{
                display: 'inline-flex',
                alignItems: 'center',
                ...style,
                ...sxToStyle(sx),
            }}
            {...rest}
        >
            {items.slice(0, renderedCount).map((child, index) => (
                <span
                    key={(child as React.ReactElement)?.key ?? index}
                    style={{
                        display: 'inline-flex',
                        marginLeft: index === 0 ? 0 : -Math.max(0, spacing),
                        borderRadius: tokens.borderRadius.full,
                        boxShadow: `0 0 0 2px ${tokens.colors.white}`,
                    }}
                >
                    {child}
                </span>
            ))}

            {hiddenCount > 0 ? (
                <span
                    style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        marginLeft: items.length === 0 ? 0 : -Math.max(0, spacing),
                        width: 32,
                        height: 32,
                        borderRadius: tokens.borderRadius.full,
                        backgroundColor: tokens.colors.neutral[200],
                        color: tokens.colors.neutral[800],
                        fontFamily: tokens.typography.fontFamily.sans,
                        fontSize: tokens.typography.fontSize.xs,
                        fontWeight: tokens.typography.fontWeight.semibold,
                        boxShadow: `0 0 0 2px ${tokens.colors.white}`,
                    }}
                    aria-label={`+${hiddenCount} more`}
                >
                    +{hiddenCount}
                </span>
            ) : null}
        </div>
    );
}

AvatarGroup.displayName = 'AvatarGroup';
