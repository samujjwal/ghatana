import React from 'react';

interface ChipProps {
    label: string;
    onDelete?: () => void;
    className?: string;
}

export const Chip: React.FC<ChipProps> = ({ label, onDelete, className = '' }) => {
    return (
        <div className={`inline-flex items-center bg-blue-100 text-blue-800 px-3 py-1 rounded-full text-sm ${className}`}>
            {label}
            {onDelete && (
                <button
                    onClick={onDelete}
                    className="ml-2 hover:bg-blue-200 rounded-full"
                >
                    ✕
                </button>
            )}
        </div>
    );
};
