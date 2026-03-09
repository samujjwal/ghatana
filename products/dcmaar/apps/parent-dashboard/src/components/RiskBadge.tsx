/**
 * Risk Badge Component
 *
 * Displays a risk score badge with color-coded severity levels.
 * Used in child cards, reports, and risk overview pages.
 *
 * @doc.type component
 * @doc.purpose Risk score visualization
 * @doc.layer frontend
 * @doc.pattern UI Component
 */

// React import not needed with modern JSX transform

export type RiskBucket = 'low' | 'medium' | 'high' | 'critical';

interface RiskBadgeProps {
    score?: number;
    bucket?: RiskBucket;
    showScore?: boolean;
    size?: 'sm' | 'md' | 'lg';
    className?: string;
}

/**
 * Get bucket from score if not provided
 */
function getBucketFromScore(score: number): RiskBucket {
    if (score < 25) return 'low';
    if (score < 50) return 'medium';
    if (score < 75) return 'high';
    return 'critical';
}

/**
 * Get color classes for each risk bucket
 */
function getBucketStyles(bucket: RiskBucket): {
    bg: string;
    text: string;
    border: string;
    icon: string;
} {
    switch (bucket) {
        case 'low':
            return {
                bg: 'bg-green-100 dark:bg-green-900/30',
                text: 'text-green-700 dark:text-green-300',
                border: 'border-green-200 dark:border-green-800',
                icon: '✓',
            };
        case 'medium':
            return {
                bg: 'bg-yellow-100 dark:bg-yellow-900/30',
                text: 'text-yellow-700 dark:text-yellow-300',
                border: 'border-yellow-200 dark:border-yellow-800',
                icon: '!',
            };
        case 'high':
            return {
                bg: 'bg-orange-100 dark:bg-orange-900/30',
                text: 'text-orange-700 dark:text-orange-300',
                border: 'border-orange-200 dark:border-orange-800',
                icon: '⚠',
            };
        case 'critical':
            return {
                bg: 'bg-red-100 dark:bg-red-900/30',
                text: 'text-red-700 dark:text-red-300',
                border: 'border-red-200 dark:border-red-800',
                icon: '🚨',
            };
    }
}

/**
 * Get size classes
 */
function getSizeClasses(size: 'sm' | 'md' | 'lg'): string {
    switch (size) {
        case 'sm':
            return 'px-2 py-0.5 text-xs';
        case 'md':
            return 'px-2.5 py-1 text-sm';
        case 'lg':
            return 'px-3 py-1.5 text-base';
    }
}

/**
 * Get label for bucket
 */
function getBucketLabel(bucket: RiskBucket): string {
    switch (bucket) {
        case 'low':
            return 'Low Risk';
        case 'medium':
            return 'Medium Risk';
        case 'high':
            return 'High Risk';
        case 'critical':
            return 'Critical';
    }
}

export function RiskBadge({
    score,
    bucket,
    showScore = false,
    size = 'md',
    className = '',
}: RiskBadgeProps) {
    const effectiveBucket = bucket ?? (score !== undefined ? getBucketFromScore(score) : 'low');
    const styles = getBucketStyles(effectiveBucket);
    const sizeClasses = getSizeClasses(size);

    return (
        <span
            className={`inline-flex items-center gap-1 rounded-full border font-medium ${styles.bg} ${styles.text} ${styles.border} ${sizeClasses} ${className}`}
        >
            <span>{styles.icon}</span>
            <span>{getBucketLabel(effectiveBucket)}</span>
            {showScore && score !== undefined && (
                <span className="ml-1 opacity-75">({score})</span>
            )}
        </span>
    );
}

/**
 * Compact risk indicator (just icon and score)
 */
export function RiskIndicator({
    score,
    bucket,
    size = 'md',
    className = '',
}: Omit<RiskBadgeProps, 'showScore'>) {
    const effectiveBucket = bucket ?? (score !== undefined ? getBucketFromScore(score) : 'low');
    const styles = getBucketStyles(effectiveBucket);

    const sizeMap = {
        sm: 'w-6 h-6 text-xs',
        md: 'w-8 h-8 text-sm',
        lg: 'w-10 h-10 text-base',
    };

    return (
        <div
            className={`inline-flex items-center justify-center rounded-full ${styles.bg} ${styles.text} ${sizeMap[size]} font-bold ${className}`}
            title={`${getBucketLabel(effectiveBucket)}${score !== undefined ? ` (${score})` : ''}`}
        >
            {score !== undefined ? score : styles.icon}
        </div>
    );
}

/**
 * Risk score bar visualization
 */
export function RiskScoreBar({
    score,
    label,
    showValue = true,
    className = '',
}: {
    score: number;
    label?: string;
    showValue?: boolean;
    className?: string;
}) {
    const bucket = getBucketFromScore(score);
    const styles = getBucketStyles(bucket);

    // Determine bar color based on score
    const getBarColor = (s: number): string => {
        if (s < 25) return 'bg-green-500';
        if (s < 50) return 'bg-yellow-500';
        if (s < 75) return 'bg-orange-500';
        return 'bg-red-500';
    };

    return (
        <div className={`w-full ${className}`}>
            {(label || showValue) && (
                <div className="flex justify-between items-center mb-1">
                    {label && (
                        <span className="text-sm text-gray-600 dark:text-gray-400">{label}</span>
                    )}
                    {showValue && (
                        <span className={`text-sm font-medium ${styles.text}`}>{score}</span>
                    )}
                </div>
            )}
            <div className="w-full h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                <div
                    className={`h-full ${getBarColor(score)} transition-all duration-300`}
                    style={{ width: `${Math.min(100, score)}%` }}
                />
            </div>
        </div>
    );
}

export default RiskBadge;
