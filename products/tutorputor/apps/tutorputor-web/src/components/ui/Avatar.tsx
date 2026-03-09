import React from 'react';

interface AvatarProps extends React.HTMLAttributes<HTMLDivElement> {
    src?: string;
    alt?: string;
    name?: string;
    size?: 'sm' | 'md' | 'lg';
    initials?: string;
}

export function Avatar({
    src,
    alt = 'avatar',
    name,
    size = 'md',
    initials,
    className = '',
    ...props
}: AvatarProps) {
    const sizeClasses = {
        sm: 'w-8 h-8 text-xs',
        md: 'w-10 h-10 text-sm',
        lg: 'w-12 h-12 text-base'
    };

    // Generate initials from name if not provided
    const displayInitials = initials || (name?.split(' ').map(n => n[0]).join('').toUpperCase() || 'US');

    if (src) {
        return (
            <img
                src={src}
                alt={alt || name}
                className={`rounded-full object-cover ${sizeClasses[size]} ${className}`}
                {...props}
            />
        );
    }

    return (
        <div
            className={`rounded-full bg-blue-200 text-blue-900 flex items-center justify-center font-semibold ${sizeClasses[size]} ${className}`}
            {...props}
        >
            {displayInitials}
        </div>
    );
}