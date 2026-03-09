import React from 'react';
import { Link } from 'react-router';

export interface AiHintBannerProps {
    icon?: React.ReactNode;
    title: string;
    body: string;
    ctaLabel?: string;
    ctaHref?: string;
    onCtaClick?: () => void;
    secondaryCtaLabel?: string;
    secondaryCtaHref?: string;
    onSecondaryCtaClick?: () => void;
    onDismiss?: () => void;
    dismissLabel?: string;
    className?: string;
}

export function AiHintBanner({
    icon,
    title,
    body,
    ctaLabel,
    ctaHref,
    onCtaClick,
    secondaryCtaLabel,
    secondaryCtaHref,
    onSecondaryCtaClick,
    onDismiss,
    dismissLabel,
    className,
}: AiHintBannerProps) {
    const hasCta = Boolean(ctaLabel && (ctaHref || onCtaClick));
    const hasSecondaryCta = Boolean(secondaryCtaLabel && (secondaryCtaHref || onSecondaryCtaClick));

    const containerClasses = [
        'bg-indigo-50 dark:bg-indigo-900/20 border border-indigo-200 dark:border-indigo-800 rounded-lg p-4 text-xs flex flex-col md:flex-row md:items-center md:justify-between gap-3',
        className,
    ]
        .filter(Boolean)
        .join(' ');

    const ctaClasses =
        'inline-flex items-center justify-center px-4 py-2 rounded-lg bg-indigo-600 text-white text-xs font-medium hover:bg-indigo-700 transition';

    const dismissClasses =
        'px-2 py-1 rounded-md bg-slate-100 dark:bg-neutral-800 text-[11px] text-slate-700 dark:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-700';

    return (
        <section className={containerClasses}>
            <div className="space-y-1">
                <div className="flex items-center gap-2">
                    {icon && <span className="text-lg">{icon}</span>}
                    <h2 className="text-sm font-semibold text-indigo-900 dark:text-indigo-100">
                        {title}
                    </h2>
                </div>
                <p className="text-slate-700 dark:text-slate-200">{body}</p>
            </div>
            <div className="flex flex-wrap items-center gap-2 mt-1 md:mt-0">
                {hasCta && (
                    ctaHref && !onCtaClick ? (
                        <Link to={ctaHref} className={ctaClasses}>
                            {ctaLabel}
                        </Link>
                    ) : (
                        <button type="button" onClick={onCtaClick} className={ctaClasses}>
                            {ctaLabel}
                        </button>
                    )
                )}
                {hasSecondaryCta && (
                    secondaryCtaHref && !onSecondaryCtaClick ? (
                        <Link to={secondaryCtaHref} className={ctaClasses}>
                            {secondaryCtaLabel}
                        </Link>
                    ) : (
                        <button type="button" onClick={onSecondaryCtaClick} className={ctaClasses}>
                            {secondaryCtaLabel}
                        </button>
                    )
                )}
                {onDismiss && (
                    <button type="button" onClick={onDismiss} className={dismissClasses}>
                        {dismissLabel || 'Hide'}
                    </button>
                )}
            </div>
        </section>
    );
}
