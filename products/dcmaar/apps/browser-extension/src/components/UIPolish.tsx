/**
 * @fileoverview UI Polish components: skeletons, loading states, animations
 */

export const LoadingSkeleton: React.FC<{ className?: string }> = ({ className = '' }) => (
    <div className={`animate-pulse bg-gray-200 rounded ${className}`} />
);

export const DashboardSkeleton: React.FC = () => (
    <div className="space-y-6">
        {/* Header skeleton */}
        <div className="bg-gradient-to-r from-blue-600 to-indigo-600 h-32 rounded-lg animate-pulse" />

        {/* Status cards skeleton */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
            {Array.from({ length: 4 }).map((_, i) => (
                <LoadingSkeleton key={i} className="h-32" />
            ))}
        </div>

        {/* Charts skeleton */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {Array.from({ length: 2 }).map((_, i) => (
                <LoadingSkeleton key={i} className="h-80" />
            ))}
        </div>
    </div>
);

export const EmptyState: React.FC<{
    icon?: React.ReactNode;
    title: string;
    description?: string;
    action?: { label: string; onClick: () => void };
}> = ({ icon, title, description, action }) => (
    <div className="flex flex-col items-center justify-center py-12 px-4 text-center">
        {icon && <div className="mb-4 text-gray-400">{icon}</div>}
        <h3 className="text-lg font-semibold text-gray-900 mb-2">{title}</h3>
        {description && <p className="text-gray-600 mb-6 max-w-md">{description}</p>}
        {action && (
            <button
                onClick={action.onClick}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium"
            >
                {action.label}
            </button>
        )}
    </div>
);

export const transitions = {
    fadeIn: 'transition-opacity duration-300',
    slideIn: 'transition-transform duration-300',
    scaleIn: 'transition-transform duration-200',
};

export const animations = {
    spin: 'animate-spin',
    pulse: 'animate-pulse',
    bounce: 'animate-bounce',
    fadeInOut: 'animate-pulse',
};

export const AccessibleButton: React.FC<
    React.ButtonHTMLAttributes<HTMLButtonElement> & {
        isLoading?: boolean;
        variant?: 'primary' | 'secondary' | 'danger';
    }
> = ({ isLoading, variant = 'primary', disabled, className = '', children, ...props }) => {
    const baseClasses = 'px-4 py-2 rounded-lg font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2';
    const variantClasses = {
        primary: 'bg-blue-600 text-white hover:bg-blue-700 focus:ring-blue-500',
        secondary: 'bg-gray-200 text-gray-900 hover:bg-gray-300 focus:ring-gray-500',
        danger: 'bg-red-600 text-white hover:bg-red-700 focus:ring-red-500',
    };

    return (
        <button
            {...props}
            disabled={disabled || isLoading}
            className={`${baseClasses} ${variantClasses[variant]} ${disabled || isLoading ? 'opacity-50 cursor-not-allowed' : ''} ${className}`}
            aria-busy={isLoading}
        >
            {isLoading ? (
                <span className="flex items-center space-x-2">
                    <svg className="animate-spin h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                    </svg>
                    <span>Loading...</span>
                </span>
            ) : (
                children
            )}
        </button>
    );
};

export const Tooltip: React.FC<{
    content: string;
    children: React.ReactNode;
    position?: 'top' | 'bottom' | 'left' | 'right';
}> = ({ content, children, position = 'top' }) => {
    const positionClasses = {
        top: 'bottom-full mb-2',
        bottom: 'top-full mt-2',
        left: 'right-full mr-2',
        right: 'left-full ml-2',
    };

    return (
        <div className="relative inline-block group">
            {children}
            <div
                className={`absolute hidden group-hover:block bg-gray-900 text-white text-xs rounded-lg py-2 px-3 ${positionClasses[position]} z-10 whitespace-nowrap`}
                role="tooltip"
            >
                {content}
            </div>
        </div>
    );
};

export const Badge: React.FC<{
    variant?: 'success' | 'warning' | 'error' | 'info';
    children: React.ReactNode;
}> = ({ variant = 'info', children }) => {
    const colors = {
        success: 'bg-green-100 text-green-800',
        warning: 'bg-yellow-100 text-yellow-800',
        error: 'bg-red-100 text-red-800',
        info: 'bg-blue-100 text-blue-800',
    };

    return (
        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${colors[variant]}`}>
            {children}
        </span>
    );
};

export const Alert: React.FC<{
    type?: 'success' | 'warning' | 'error' | 'info';
    title?: string;
    message: string;
    onClose?: () => void;
}> = ({ type = 'info', title, message, onClose }) => {
    const styles = {
        success: 'bg-green-50 border-green-200 text-green-800',
        warning: 'bg-yellow-50 border-yellow-200 text-yellow-800',
        error: 'bg-red-50 border-red-200 text-red-800',
        info: 'bg-blue-50 border-blue-200 text-blue-800',
    };

    return (
        <div className={`rounded-lg border p-4 ${styles[type]} animate-in slide-in-from-top fade-in`}>
            <div className="flex items-start justify-between">
                <div>
                    {title && <h3 className="font-semibold mb-1">{title}</h3>}
                    <p className="text-sm">{message}</p>
                </div>
                {onClose && (
                    <button
                        onClick={onClose}
                        className="ml-4 text-current hover:opacity-70 transition-opacity"
                        aria-label="Close alert"
                    >
                        ×
                    </button>
                )}
            </div>
        </div>
    );
};

export const Card: React.FC<{
    title?: string;
    subtitle?: string;
    children: React.ReactNode;
    className?: string;
}> = ({ title, subtitle, children, className = '' }) => (
    <div className={`bg-white rounded-xl shadow-md p-6 ${className}`}>
        {(title || subtitle) && (
            <div className="mb-6">
                {title && <h3 className="text-lg font-bold text-gray-900">{title}</h3>}
                {subtitle && <p className="text-sm text-gray-600 mt-1">{subtitle}</p>}
            </div>
        )}
        {children}
    </div>
);

export const Divider: React.FC<{ className?: string }> = ({ className = '' }) => (
    <div className={`border-t border-gray-200 ${className}`} role="separator" />
);

export const DarkModeToggle: React.FC<{
    isDark: boolean;
    onChange: (isDark: boolean) => void;
}> = ({ isDark, onChange }) => (
    <button
        onClick={() => onChange(!isDark)}
        className="relative inline-flex h-8 w-14 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
        style={{
            backgroundColor: isDark ? '#1f2937' : '#e5e7eb',
        }}
        aria-label={`Switch to ${isDark ? 'light' : 'dark'} mode`}
    >
        <span
            className={`inline-block h-6 w-6 transform rounded-full bg-white shadow-lg transition-transform ${isDark ? 'translate-x-7' : 'translate-x-1'
                }`}
        />
    </button>
);
