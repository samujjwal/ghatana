/**
 * Unified Icon Component
 * 
 * Standardized icon wrapper for consistent sizing and styling.
 * Wraps MUI icons with consistent sizing and color handling.
 * 
 * @doc.type component
 * @doc.purpose Design system icon wrapper
 * @doc.layer design-system
 */

import React from 'react';
import { cn } from '../../lib/utils';

export type IconSize = 'sm' | 'md' | 'lg' | 'xl';

export interface IconProps extends Omit<SvgIconProps, 'fontSize'> {
    /** Size of the icon */
    size?: IconSize;
    /** Additional CSS classes */
    className?: string;
}

const sizeMap: Record<IconSize, { fontSize: string; className: string }> = {
    sm: { fontSize: '1rem', className: 'w-4 h-4' },      // 16px
    md: { fontSize: '1.25rem', className: 'w-5 h-5' },   // 20px
    lg: { fontSize: '1.5rem', className: 'w-6 h-6' },    // 24px
    xl: { fontSize: '2rem', className: 'w-8 h-8' },      // 32px
};

/**
 * Icon wrapper component
 * 
 * @example
 * ```tsx
 * import { LayoutDashboard as Dashboard } from 'lucide-react';
 * import { Icon } from '@/components/design-system';
 * 
 * <Icon size="md" className="text-primary-600">
 *   <Dashboard />
 * </Icon>
 * ```
 */
export function Icon({
    size = 'md',
    className,
    children,
    ...props
}: IconProps) {
    const sizeConfig = sizeMap[size];

    // Clone the child element and pass the fontSize prop
    const iconElement = React.isValidElement(children)
        ? React.cloneElement(children as React.ReactElement<SvgIconProps>, {
            fontSize: 'inherit',
            className: cn(sizeConfig.className, className),
            style: { fontSize: sizeConfig.fontSize },
            ...props,
        })
        : children;

    return <>{iconElement}</>;
}

/**
 * Icon component with specific MUI icon
 * 
 * @example
 * ```tsx
 * import { LayoutDashboard as Dashboard } from 'lucide-react';
 * import { IconWrapper } from '@/components/design-system';
 * 
 * <IconWrapper icon={Dashboard} size="md" />
 * ```
 */
export interface IconWrapperProps extends IconProps {
    /** MUI Icon component */
    icon: React.ComponentType<SvgIconProps>;
}

export function IconWrapper({
    icon: IconComponent,
    size = 'md',
    className,
    ...props
}: IconWrapperProps) {
    const sizeConfig = sizeMap[size];

    return (
        <IconComponent
            size={undefined}
            className={cn(sizeConfig.className, className)}
            style={{ fontSize: sizeConfig.fontSize }}
            {...props}
        />
    );
}

export default Icon;
