import React from 'react';

interface SliderProps extends React.InputHTMLAttributes<HTMLInputElement> { }

export const Slider = React.forwardRef<HTMLInputElement, SliderProps>(
    ({ className, ...props }, ref) => (
        <input
            ref={ref}
            type="range"
            className={`w-full h-2 bg-gray-200 rounded appearance-none cursor-pointer ${className || ''}`}
            {...props}
        />
    )
);

Slider.displayName = 'Slider';
