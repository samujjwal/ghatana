import React from 'react';

interface SkeletonProps {
    width?: string | number;
    height?: string | number;
    count?: number;
    className?: string;
}

export const Skeleton: React.FC<SkeletonProps> = ({
    width = '100%',
    height = '20px',
    count = 1,
    className = '',
}) => {
    return (
        <>
            {Array.from({ length: count }).map((_, i) => (
                <div
                    key={i}
                    className={`bg-gray-300 animate-pulse rounded mb-2 ${className}`}
                    style={{
                        width: typeof width === 'number' ? `${width}px` : width,
                        height: typeof height === 'number' ? `${height}px` : height,
                    }}
                />
            ))}
        </>
    );
};
