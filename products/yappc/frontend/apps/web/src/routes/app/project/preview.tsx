/**
 * Project Preview Route
 * 
 * Live preview of the project with hot reload.
 * Shows the running application in an iframe with controls.
 */

import { useState } from "react";
import { useParams } from "react-router";
import { RefreshCw as Refresh, ExternalLink as OpenInNew, Smartphone, Tablet, Laptop } from 'lucide-react';

type DeviceMode = 'mobile' | 'tablet' | 'desktop';

const deviceDimensions: Record<DeviceMode, { width: string; label: string }> = {
    mobile: { width: '375px', label: 'Mobile' },
    tablet: { width: '768px', label: 'Tablet' },
    desktop: { width: '100%', label: 'Desktop' }
};

/**
 * Preview page component
 */
export default function PreviewPage() {
    const { projectId } = useParams();
    const [device, setDevice] = useState<DeviceMode>('desktop');
    const [refreshKey, setRefreshKey] = useState(0);

    const previewUrl = `http://localhost:3001/preview/${projectId}`;

    const handleRefresh = () => {
        setRefreshKey(prev => prev + 1);
    };

    const handleOpenExternal = () => {
        window.open(previewUrl, '_blank');
    };

    return (
        <div className="flex flex-col h-full">
            {/* Preview Controls */}
            <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2">
                    <span className="text-sm text-text-secondary">Device:</span>
                    <div className="flex items-center bg-bg-paper border border-divider rounded-lg overflow-hidden">
                        <button
                            onClick={() => setDevice('mobile')}
                            className={`p-2 transition-colors ${
                                device === 'mobile' 
                                    ? 'bg-primary-100 text-primary-600 dark:bg-primary-900/30' 
                                    : 'text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800'
                            }`}
                            title="Mobile"
                        >
                            <Smartphone className="w-5 h-5" />
                        </button>
                        <button
                            onClick={() => setDevice('tablet')}
                            className={`p-2 transition-colors ${
                                device === 'tablet' 
                                    ? 'bg-primary-100 text-primary-600 dark:bg-primary-900/30' 
                                    : 'text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800'
                            }`}
                            title="Tablet"
                        >
                            <Tablet className="w-5 h-5" />
                        </button>
                        <button
                            onClick={() => setDevice('desktop')}
                            className={`p-2 transition-colors ${
                                device === 'desktop' 
                                    ? 'bg-primary-100 text-primary-600 dark:bg-primary-900/30' 
                                    : 'text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800'
                            }`}
                            title="Desktop"
                        >
                            <Laptop className="w-5 h-5" />
                        </button>
                    </div>
                </div>

                <div className="flex items-center gap-2">
                    <button
                        onClick={handleRefresh}
                        className="p-2 text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800 rounded-lg transition-colors"
                        title="Refresh Preview"
                    >
                        <Refresh className="w-5 h-5" />
                    </button>
                    <button
                        onClick={handleOpenExternal}
                        className="p-2 text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800 rounded-lg transition-colors"
                        title="Open in New Tab"
                    >
                        <OpenInNew className="w-5 h-5" />
                    </button>
                </div>
            </div>

            {/* Preview Frame */}
            <div className="flex-1 flex items-center justify-center bg-grey-100 dark:bg-grey-900 rounded-lg overflow-hidden">
                <div 
                    className="h-full bg-white dark:bg-grey-800 shadow-lg transition-all duration-300 overflow-hidden"
                    style={{ width: deviceDimensions[device].width, maxWidth: '100%' }}
                >
                    <iframe
                        key={refreshKey}
                        src={previewUrl}
                        className="w-full h-full border-0"
                        title="Project Preview"
                        sandbox="allow-scripts allow-same-origin allow-forms"
                    />
                </div>
            </div>

            {/* Status Bar */}
            <div className="flex items-center justify-between mt-3 text-xs text-text-secondary">
                <span>Preview URL: {previewUrl}</span>
                <span>{deviceDimensions[device].label} View</span>
            </div>
        </div>
    );
}
