import React from 'react';

/**
 * InfoBanner - Reusable informational banner component
 *
 * <p><b>Purpose</b><br>
 * Displays helpful information or guidance text in a prominent banner.
 * Used for tips, navigation help, or context on landing pages.
 *
 * <p><b>Features</b><br>
 * - Multiple tone variants (info, success, warning, error)
 * - Optional icon
 * - Dismissible option
 * - Dark mode support
 * - Flexible content
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <InfoBanner tone="info" icon="ℹ️">
 *   Use the sidebar navigation to switch between feature areas anytime.
 * </InfoBanner>
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Informational banner display
 * @doc.layer product
 * @doc.pattern Molecule
 */

type BannerTone = 'info' | 'success' | 'warning' | 'error';

interface InfoBannerProps {
    children: React.ReactNode;
    icon?: string;
    tone?: BannerTone;
    dismissible?: boolean;
    onDismiss?: () => void;
}

const toneStyles: Record<BannerTone, string> = {
    info: 'bg-blue-50 dark:bg-indigo-600/30 border-blue-200 dark:border-blue-800 text-blue-900 dark:text-blue-100',
    success: 'bg-green-50 dark:bg-green-600/30 border-green-200 dark:border-green-800 text-green-900 dark:text-green-100',
    warning: 'bg-amber-50 dark:bg-amber-900/20 border-amber-200 dark:border-amber-800 text-amber-900 dark:text-amber-100',
    error: 'bg-red-50 dark:bg-rose-600/30 border-red-200 dark:border-red-800 text-red-900 dark:text-red-100',
};

export const InfoBanner: React.FC<InfoBannerProps> = ({
    children,
    icon,
    tone = 'info',
    dismissible = false,
    onDismiss,
}) => {
    const [visible, setVisible] = React.useState(true);

    const handleDismiss = () => {
        setVisible(false);
        onDismiss?.();
    };

    if (!visible) return null;

    return (
        <div className={`rounded-lg border px-4 py-3 sm:px-6 sm:py-4 ${toneStyles[tone]}`}>
            <div className="flex items-start justify-between gap-3">
                <div className="flex items-start gap-3">
                    {icon && <span className="text-lg flex-shrink-0">{icon}</span>}
                    <div className="text-sm leading-relaxed">{children}</div>
                </div>
                {dismissible && (
                    <button
                        onClick={handleDismiss}
                        className="flex-shrink-0 text-lg hover:opacity-70 transition-opacity"
                        aria-label="Dismiss"
                    >
                        ✕
                    </button>
                )}
            </div>
        </div>
    );
};
