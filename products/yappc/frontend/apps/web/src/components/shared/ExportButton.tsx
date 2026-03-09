/**
 * Export Button Component
 * 
 * Generic export button with format selection.
 * 
 * @doc.type component
 * @doc.purpose Export trigger UI
 * @doc.layer product
 * @doc.pattern Generic Component
 */

import React, { useState } from 'react';
import { Download as FileDownload } from 'lucide-react';

export interface ExportButtonProps {
    onExport: (format: 'json' | 'markdown' | 'pdf') => void;
    disabled?: boolean;
    compact?: boolean;
}

export const ExportButton: React.FC<ExportButtonProps> = ({
    onExport,
    disabled = false,
    compact = false,
}) => {
    const [showMenu, setShowMenu] = useState(false);

    const formats = [
        { id: 'pdf' as const, label: 'PDF Report', icon: '📄' },
        { id: 'markdown' as const, label: 'Markdown', icon: '📝' },
        { id: 'json' as const, label: 'JSON Data', icon: '💾' },
    ];

    const handleExport = (format: typeof formats[number]['id']) => {
        onExport(format);
        setShowMenu(false);
    };

    return (
        <div className="relative">
            <button
                onClick={() => setShowMenu(!showMenu)}
                disabled={disabled}
                className={`flex items-center gap-2 px-4 py-2 text-sm font-medium bg-white border border-divider rounded-md hover:bg-grey-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors ${compact ? 'px-2 py-1' : ''
                    }`}
            >
                <FileDownload className="w-4 h-4" />
                {!compact && 'Export'}
            </button>

            {showMenu && (
                <>
                    <div
                        className="fixed inset-0 z-10"
                        onClick={() => setShowMenu(false)}
                    />
                    <div className="absolute right-0 mt-1 w-48 bg-white border border-divider rounded-md shadow-lg z-20">
                        {formats.map((format) => (
                            <button
                                key={format.id}
                                onClick={() => handleExport(format.id)}
                                className="w-full flex items-center gap-2 px-4 py-2 text-sm text-left hover:bg-grey-50 transition-colors first:rounded-t-md last:rounded-b-md"
                            >
                                <span className="text-lg">{format.icon}</span>
                                <span>{format.label}</span>
                            </button>
                        ))}
                    </div>
                </>
            )}
        </div>
    );
};
