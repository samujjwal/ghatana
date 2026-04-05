/**
 * Skeleton Loader Components
 * 
 * Provides consistent loading skeleton patterns across the application.
 * Follows WCAG accessibility guidelines with proper ARIA attributes.
 * 
 * @doc.type component
 * @doc.purpose Loading state skeletons
 * @doc.layer product
 * @doc.pattern Atom Component
 */

import type { CSSProperties, ReactNode } from 'react';

// ============================================================================
// BASE SKELETON
// ============================================================================

export interface SkeletonProps {
    /** Width of the skeleton (CSS value) */
    width?: string | number;
    /** Height of the skeleton (CSS value) */
    height?: string | number;
    /** Border radius variant */
    variant?: 'text' | 'circular' | 'rectangular' | 'rounded';
    /** Animation type */
    animation?: 'pulse' | 'wave' | 'none';
    /** Custom className */
    className?: string;
    /** Custom styles */
    style?: CSSProperties;
}

/**
 * Base skeleton component with animation
 */
export function Skeleton({
    width = '100%',
    height = '1rem',
    variant = 'text',
    animation = 'pulse',
    className = '',
    style = {},
}: SkeletonProps) {
    const borderRadius = {
        text: '4px',
        circular: '50%',
        rectangular: '0',
        rounded: '8px',
    }[variant];

    const animationClass = animation !== 'none' ? `skeleton-${animation}` : '';

    return (
        <div
            className={`skeleton ${animationClass} ${className}`}
            role="presentation"
            aria-hidden="true"
            style={{
                width: typeof width === 'number' ? `${width}px` : width,
                height: typeof height === 'number' ? `${height}px` : height,
                borderRadius,
                backgroundColor: 'var(--skeleton-bg, #e5e7eb)',
                ...style,
            }}
        />
    );
}

// ============================================================================
// SKELETON PRESETS
// ============================================================================

/**
 * Text line skeleton
 */
export function SkeletonText({ lines = 1, gap = 8 }: { lines?: number; gap?: number }) {
    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: `${gap}px` }}>
            {Array.from({ length: lines }).map((_, i) => (
                <Skeleton
                    key={i}
                    width={i === lines - 1 && lines > 1 ? '70%' : '100%'}
                    height="1rem"
                    variant="text"
                />
            ))}
        </div>
    );
}

/**
 * Avatar/profile picture skeleton
 */
export function SkeletonAvatar({ size = 40 }: { size?: number }) {
    return <Skeleton width={size} height={size} variant="circular" />;
}

/**
 * Card skeleton
 */
export function SkeletonCard({ 
    showImage = true,
    showAvatar = false,
    lines = 3 
}: { 
    showImage?: boolean; 
    showAvatar?: boolean;
    lines?: number;
}) {
    return (
        <div
            style={{
                padding: '16px',
                borderRadius: '8px',
                backgroundColor: 'var(--card-bg, white)',
                boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
            }}
        >
            {showImage && (
                <Skeleton 
                    width="100%" 
                    height={160} 
                    variant="rectangular" 
                    style={{ marginBottom: '16px' }} 
                />
            )}
            {showAvatar && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px' }}>
                    <SkeletonAvatar size={40} />
                    <div style={{ flex: 1 }}>
                        <Skeleton width="60%" height="1rem" />
                        <Skeleton width="40%" height="0.75rem" style={{ marginTop: '4px' }} />
                    </div>
                </div>
            )}
            <SkeletonText lines={lines} />
        </div>
    );
}

// ============================================================================
// PAGE-LEVEL SKELETON LAYOUTS
// ============================================================================

/**
 * Dashboard skeleton with KPI cards and content area
 */
export function SkeletonDashboard() {
    return (
        <div
            role="status"
            aria-live="polite"
            aria-label="Loading dashboard..."
            style={{ padding: '24px' }}
        >
            {/* Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '24px' }}>
                <Skeleton width={200} height={32} variant="rounded" />
                <div style={{ display: 'flex', gap: '8px' }}>
                    <Skeleton width={100} height={36} variant="rounded" />
                    <Skeleton width={100} height={36} variant="rounded" />
                </div>
            </div>

            {/* KPI Cards */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px', marginBottom: '24px' }}>
                {[1, 2, 3, 4].map((i) => (
                    <div key={i} style={{ padding: '16px', borderRadius: '8px', backgroundColor: 'var(--card-bg, white)', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
                        <Skeleton width="60%" height="0.875rem" style={{ marginBottom: '8px' }} />
                        <Skeleton width="40%" height="2rem" />
                    </div>
                ))}
            </div>

            {/* Content grid */}
            <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '24px' }}>
                <SkeletonCard showImage lines={4} />
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    <SkeletonCard showAvatar lines={2} />
                    <SkeletonCard showAvatar lines={2} />
                </div>
            </div>

            <SkeletonStyles />
        </div>
    );
}

/**
 * Project list skeleton
 */
export function SkeletonProjectList() {
    return (
        <div
            role="status"
            aria-live="polite"
            aria-label="Loading projects..."
            style={{ padding: '24px' }}
        >
            {/* Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '24px' }}>
                <Skeleton width={180} height={32} variant="rounded" />
                <Skeleton width={120} height={36} variant="rounded" />
            </div>

            {/* Project cards grid */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '16px' }}>
                {[1, 2, 3, 4, 5, 6].map((i) => (
                    <SkeletonCard key={i} showImage={false} showAvatar lines={2} />
                ))}
            </div>

            <SkeletonStyles />
        </div>
    );
}

/**
 * Canvas skeleton with toolbar and main area
 */
export function SkeletonCanvas() {
    return (
        <div
            role="status"
            aria-live="polite"
            aria-label="Loading canvas..."
            style={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: '400px' }}
        >
            {/* Toolbar */}
            <div style={{ 
                display: 'flex', 
                alignItems: 'center', 
                gap: '8px', 
                padding: '8px 16px', 
                borderBottom: '1px solid var(--border-color, #e5e7eb)' 
            }}>
                <Skeleton width={32} height={32} variant="rounded" />
                <Skeleton width={32} height={32} variant="rounded" />
                <Skeleton width={32} height={32} variant="rounded" />
                <div style={{ flex: 1 }} />
                <Skeleton width={120} height={28} variant="rounded" />
                <Skeleton width={100} height={28} variant="rounded" />
            </div>

            {/* Main canvas area */}
            <div style={{ 
                flex: 1, 
                display: 'flex', 
                alignItems: 'center', 
                justifyContent: 'center',
                backgroundColor: 'var(--canvas-bg, #f9fafb)',
            }}>
                <div style={{ textAlign: 'center' }}>
                    <Skeleton width={120} height={120} variant="circular" style={{ margin: '0 auto 16px' }} />
                    <Skeleton width={200} height={20} variant="rounded" style={{ margin: '0 auto' }} />
                </div>
            </div>

            <SkeletonStyles />
        </div>
    );
}

/**
 * Table skeleton
 */
export function SkeletonTable({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
    return (
        <div
            role="status"
            aria-live="polite"
            aria-label="Loading table..."
        >
            {/* Header */}
            <div style={{ 
                display: 'grid', 
                gridTemplateColumns: `repeat(${cols}, 1fr)`, 
                gap: '16px', 
                padding: '12px 16px',
                borderBottom: '2px solid var(--border-color, #e5e7eb)',
            }}>
                {Array.from({ length: cols }).map((_, i) => (
                    <Skeleton key={i} width="80%" height="1rem" variant="text" />
                ))}
            </div>

            {/* Rows */}
            {Array.from({ length: rows }).map((_, rowIndex) => (
                <div 
                    key={rowIndex}
                    style={{ 
                        display: 'grid', 
                        gridTemplateColumns: `repeat(${cols}, 1fr)`, 
                        gap: '16px', 
                        padding: '12px 16px',
                        borderBottom: '1px solid var(--border-color, #e5e7eb)',
                    }}
                >
                    {Array.from({ length: cols }).map((_, colIndex) => (
                        <Skeleton 
                            key={colIndex} 
                            width={colIndex === 0 ? '90%' : '70%'} 
                            height="1rem" 
                            variant="text" 
                        />
                    ))}
                </div>
            ))}

            <SkeletonStyles />
        </div>
    );
}

/**
 * Page Designer skeleton
 */
export function SkeletonPageDesigner() {
    return (
        <div
            role="status"
            aria-live="polite"
            aria-label="Loading page designer..."
            style={{ display: 'flex', height: '100%', minHeight: '500px' }}
        >
            {/* Left panel - component palette */}
            <div style={{ 
                width: '240px', 
                borderRight: '1px solid var(--border-color, #e5e7eb)', 
                padding: '16px',
                backgroundColor: 'var(--panel-bg, white)',
            }}>
                <Skeleton width="60%" height="1.25rem" style={{ marginBottom: '16px' }} />
                {[1, 2, 3, 4, 5].map((i) => (
                    <div key={i} style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
                        <Skeleton width={24} height={24} variant="rounded" />
                        <Skeleton width="70%" height="1rem" />
                    </div>
                ))}
            </div>

            {/* Center - canvas */}
            <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: 'var(--canvas-bg, #f9fafb)' }}>
                <Skeleton width={400} height={300} variant="rounded" style={{ opacity: 0.5 }} />
            </div>

            {/* Right panel - properties */}
            <div style={{ 
                width: '280px', 
                borderLeft: '1px solid var(--border-color, #e5e7eb)', 
                padding: '16px',
                backgroundColor: 'var(--panel-bg, white)',
            }}>
                <Skeleton width="50%" height="1.25rem" style={{ marginBottom: '16px' }} />
                <SkeletonText lines={4} gap={12} />
            </div>

            <SkeletonStyles />
        </div>
    );
}

/**
 * Workflow skeleton
 */
export function SkeletonWorkflow() {
    return (
        <div
            role="status"
            aria-live="polite"
            aria-label="Loading workflow..."
            style={{ padding: '24px' }}
        >
            {/* Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '24px' }}>
                <Skeleton width={240} height={32} variant="rounded" />
                <div style={{ display: 'flex', gap: '8px' }}>
                    <Skeleton width={80} height={36} variant="rounded" />
                    <Skeleton width={100} height={36} variant="rounded" />
                </div>
            </div>

            {/* Workflow items */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                {[1, 2, 3].map((i) => (
                    <div key={i} style={{ 
                        display: 'flex', 
                        alignItems: 'center', 
                        padding: '16px', 
                        borderRadius: '8px',
                        backgroundColor: 'var(--card-bg, white)',
                        boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
                    }}>
                        <Skeleton width={48} height={48} variant="circular" />
                        <div style={{ flex: 1, marginLeft: '16px' }}>
                            <Skeleton width="30%" height="1.125rem" style={{ marginBottom: '8px' }} />
                            <Skeleton width="50%" height="0.875rem" />
                        </div>
                        <Skeleton width={80} height={24} variant="rounded" />
                    </div>
                ))}
            </div>

            <SkeletonStyles />
        </div>
    );
}

// ============================================================================
// STYLES
// ============================================================================

/**
 * Skeleton animation styles - inject once per page
 */
export function SkeletonStyles() {
    return (
        <style>
            {`
                .skeleton {
                    --skeleton-bg: #e5e7eb;
                }
                
                @media (prefers-color-scheme: dark) {
                    .skeleton {
                        --skeleton-bg: #374151;
                    }
                }
                
                .dark .skeleton {
                    --skeleton-bg: #374151;
                }
                
                .skeleton-pulse {
                    animation: skeleton-pulse 1.5s ease-in-out infinite;
                }
                
                .skeleton-wave {
                    position: relative;
                    overflow: hidden;
                }
                
                .skeleton-wave::after {
                    content: '';
                    position: absolute;
                    top: 0;
                    right: 0;
                    bottom: 0;
                    left: 0;
                    transform: translateX(-100%);
                    background: linear-gradient(
                        90deg,
                        transparent,
                        rgba(255, 255, 255, 0.4),
                        transparent
                    );
                    animation: skeleton-wave 1.6s linear infinite;
                }
                
                @keyframes skeleton-pulse {
                    0%, 100% { opacity: 1; }
                    50% { opacity: 0.5; }
                }
                
                @keyframes skeleton-wave {
                    100% { transform: translateX(100%); }
                }
            `}
        </style>
    );
}

// ============================================================================
// CONTAINER WRAPPER
// ============================================================================

/**
 * Loading container that wraps any skeleton with accessible attributes
 */
export function LoadingContainer({ 
    children, 
    message = 'Loading...',
    centered = true,
}: { 
    children: ReactNode; 
    message?: string;
    centered?: boolean;
}) {
    return (
        <div
            role="status"
            aria-live="polite"
            aria-label={message}
            style={centered ? {
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                minHeight: '200px',
            } : undefined}
        >
            {children}
            <span className="sr-only">{message}</span>
        </div>
    );
}

export default Skeleton;
