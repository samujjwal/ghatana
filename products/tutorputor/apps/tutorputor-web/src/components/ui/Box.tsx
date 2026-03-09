import React from 'react';
import { cn } from '@/lib/utils';

interface BoxProps extends React.HTMLAttributes<HTMLDivElement> {
    padded?: boolean;
}

export const Box = React.forwardRef<HTMLDivElement, BoxProps>(
    ({ className, padded, ...props }, ref) => {
        return (
            <div
                ref={ref}
                className={cn(
                    padded && 'p-4',
                    className
                )}
                {...props}
            />
        );
    }
);

Box.displayName = 'Box';
