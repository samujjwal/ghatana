import React from 'react';

export interface SkeletonProps {
    width?: string;
    height?: string;
    count?: number;
    circle?: boolean;
    className?: string;
}

export const Skeleton: React.FC<SkeletonProps> = ({
    width = 'w-full',
    height = 'h-4',
    count = 1,
    circle = false,
    className = '',
}) => {
    return (
        <div className={`space-y-2 ${className}`}>
            {Array.from({ length: count }).map((_, i) => (
                <div
                    key={i}
                    className={`skeleton ${width} ${height} ${circle ? 'rounded-full' : 'rounded-lg'}`}
                />
            ))}
        </div>
    );
};
