import React from 'react';

interface TooltipProps {
    content: string;
    children: React.ReactNode;
    className?: string;
}

export const Tooltip: React.FC<TooltipProps> = ({ content, children, className = '' }) => {
    return (
        <div className={`group relative inline-block ${className}`}>
            {children}
            <div className="hidden group-hover:block absolute bg-gray-800 text-white text-sm py-1 px-2 rounded whitespace-nowrap mt-2 z-10">
                {content}
            </div>
        </div>
    );
};
