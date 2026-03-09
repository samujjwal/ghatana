import React from 'react';
import { cn } from '@/lib/utils';
import { cardStyles } from '../../theme';

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
    padded?: boolean;
}

export const Card = React.forwardRef<HTMLDivElement, CardProps>(
    ({ className, padded, ...props }, ref) => {
        return (
            <div
                ref={ref}
                className={cn(cardStyles.base, padded && cardStyles.padded, className)}
                {...props}
            />
        );
    }
);

Card.displayName = 'Card';
