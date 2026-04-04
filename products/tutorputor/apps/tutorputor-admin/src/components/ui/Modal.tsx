import React from 'react';

interface ModalProps {
    isOpen?: boolean;
    children?: React.ReactNode;
    className?: string;
}

export const Modal: React.FC<ModalProps> = ({ isOpen = false, children, className = '' }) => {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
            <div className={`bg-white rounded-lg shadow-lg p-6 ${className}`}>
                {children}
            </div>
        </div>
    );
};
