/**
 * Project Preview Route
 *
 * External preview host surface for the current project.
 * Shows a configured preview host in an iframe when available.
 *
 * @doc.type route
 * @doc.purpose External preview host surface with device mode controls
 * @doc.layer product
 * @doc.pattern Route Component
 */

import { useState } from "react";
import { useParams } from "react-router";
import { RefreshCw as Refresh, ExternalLink as OpenInNew, Smartphone, Tablet, Laptop } from 'lucide-react';
import type { PreviewStatusContract } from '@/contracts/workspace-project';

type DeviceMode = 'mobile' | 'tablet' | 'desktop';

const deviceDimensions: Record<DeviceMode, { width: string; label: string }> = {
    mobile: { width: '375px', label: 'Mobile' },
    tablet: { width: '768px', label: 'Tablet' },
    desktop: { width: '100%', label: 'Desktop' }
};

function getPreviewStatusView(status: PreviewStatusContract): {
    label: string;
    className: string;
    detail: string;
} {
    switch (status) {
        case 'external-ready':
            return {
                label: 'External preview ready',
                className: 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-200',
                detail: 'This route embeds a configured external preview host. It does not run a local preview runtime by itself.',
            };
        default:
            return {
                label: 'Preview unavailable',
                className: 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200',
                detail: 'A preview host must be configured before this screen can expose a live preview.',
            };
    }
}

/**
 * Preview page component
 */
export default function PreviewPage() {
    const { projectId } = useParams();
    const [device, setDevice] = useState<DeviceMode>('desktop');
    const [refreshKey, setRefreshKey] = useState(0);

    const previewFeatureEnabled = import.meta.env.VITE_FEATURE_PROJECT_PREVIEW === 'true';
    const previewBaseUrl = import.meta.env.VITE_PREVIEW_BASE_URL;
    const previewStatus: PreviewStatusContract = previewFeatureEnabled && previewBaseUrl ? 'external-ready' : 'unconfigured';
    const previewStatusView = getPreviewStatusView(previewStatus);

    if (previewStatus === 'unconfigured') {
        return (
            <div className="flex h-full items-center justify-center rounded-lg border border-dashed border-divider bg-bg-paper p-8 text-center">
                <div className="max-w-lg">
                    <span className={`inline-flex rounded-full border px-3 py-1 text-xs font-medium ${previewStatusView.className}`} data-testid="preview-status-badge">
                        {previewStatusView.label}
                    </span>
                    <h1 className="text-xl font-semibold text-text-primary">Preview Is Not Configured</h1>
                    <p className="mt-3 text-sm text-text-secondary">
                        {previewStatusView.detail}
                    </p>
                </div>
            </div>
        );
    }

    const previewUrl = `${previewBaseUrl}/preview/${projectId}`;

    const handleRefresh = () => {
        setRefreshKey(prev => prev + 1);
    };

    const handleOpenExternal = () => {
        window.open(previewUrl, '_blank');
    };

    return (
        <div className="flex flex-col h-full">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-divider bg-bg-paper px-4 py-3">
                <div>
                    <p className="text-sm font-semibold text-text-primary">Preview via external host</p>
                    <p className="mt-1 text-xs text-text-secondary">{previewStatusView.detail}</p>
                </div>
                <span className={`inline-flex rounded-full border px-3 py-1 text-xs font-medium ${previewStatusView.className}`} data-testid="preview-status-badge">
                    {previewStatusView.label}
                </span>
            </div>
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
                <span>{deviceDimensions[device].label} View via configured external preview host</span>
            </div>
        </div>
    );
}
