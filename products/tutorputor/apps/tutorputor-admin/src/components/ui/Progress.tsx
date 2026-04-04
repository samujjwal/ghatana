import React from 'react';

interface ProgressProps {
    value?: number;
    max?: number;
    className?: string;
}

export const Progress: React.FC<ProgressProps> = ({ value = 0, max = 100, className = '' }) => {
    const percentage = (value / max) * 100;

    return (
        <div className={`w-full bg-gray-200 rounded h-2 ${className}`}>
            <div
                className="bg-blue-500 h-2 rounded"
                style={{ width: `${percentage}%` }}
            />
        </div>
    );
};
