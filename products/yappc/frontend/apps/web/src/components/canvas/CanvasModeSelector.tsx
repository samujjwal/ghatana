/**
 * Canvas Mode Selector
 * 
 * UI component for switching between canvas modes.
 * Shows mode tabs with icons, labels, and keyboard shortcuts.
 * Highlights recommended modes for current lifecycle phase.
 * 
 * @doc.type component
 * @doc.purpose Canvas mode switching UI
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { Lightbulb, GitBranch as AccountTree, Palette, Code, FlaskConical as Science, CloudUpload, Eye as Visibility, ChevronDown as KeyboardArrowDown } from 'lucide-react';

import { useCanvasMode, useCanvasModeShortcuts } from '../../hooks/useCanvasMode';
import type { CanvasMode } from '../../types/canvasMode';
import { CANVAS_MODE_CONFIG } from '../../types/canvasMode';

// ============================================================================
// Icon Mapping
// ============================================================================

const MODE_ICONS: Record<CanvasMode, React.ReactNode> = {
    brainstorm: <Lightbulb className="w-4 h-4" />,
    diagram: <AccountTree className="w-4 h-4" />,
    design: <Palette className="w-4 h-4" />,
    code: <Code className="w-4 h-4" />,
    test: <Science className="w-4 h-4" />,
    deploy: <CloudUpload className="w-4 h-4" />,
    observe: <Visibility className="w-4 h-4" />,
};

// ============================================================================
// Types
// ============================================================================

export interface CanvasModeSelectorProps {
    /** Variant style */
    variant?: 'tabs' | 'pills' | 'compact' | 'dropdown';
    /** Orientation */
    orientation?: 'horizontal' | 'vertical';
    /** Show keyboard shortcuts */
    showShortcuts?: boolean;
    /** Additional CSS classes */
    className?: string;
    /** Callback when mode changes */
    onModeChange?: (mode: CanvasMode) => void;
}

// ============================================================================
// Component
// ============================================================================

export function CanvasModeSelector({
    variant = 'tabs',
    orientation = 'horizontal',
    showShortcuts = true,
    className = '',
    onModeChange,
}: CanvasModeSelectorProps) {
    // Enable keyboard shortcuts
    useCanvasModeShortcuts();

    const {
        currentMode,
        allModes,
        recommendedModes,
        setMode,
    } = useCanvasMode();

    const handleModeSelect = (mode: CanvasMode) => {
        setMode(mode);
        onModeChange?.(mode);
    };

    // Render based on variant
    if (variant === 'dropdown') {
        return (
            <DropdownSelector
                currentMode={currentMode}
                modes={allModes}
                recommendedModes={recommendedModes}
                onSelect={handleModeSelect}
                showShortcuts={showShortcuts}
                className={className}
            />
        );
    }

    if (variant === 'compact') {
        return (
            <CompactSelector
                currentMode={currentMode}
                modes={recommendedModes.length > 0 ? recommendedModes : allModes.slice(0, 4)}
                allModes={allModes}
                recommendedModes={recommendedModes}
                onSelect={handleModeSelect}
                className={className}
            />
        );
    }

    const isVertical = orientation === 'vertical';

    return (
        <div
            className={`
                flex ${isVertical ? 'flex-col' : 'flex-row'} 
                ${variant === 'pills' ? 'gap-1 p-1 bg-bg-default rounded-lg' : 'border-b border-divider'}
                ${className}
            `}
            role="tablist"
            aria-label="Canvas mode selector"
        >
            {allModes.map((mode) => {
                const config = CANVAS_MODE_CONFIG[mode];
                const isActive = mode === currentMode;
                const isRecommended = recommendedModes.includes(mode);

                return (
                    <button
                        key={mode}
                        role="tab"
                        aria-selected={isActive}
                        onClick={() => handleModeSelect(mode)}
                        title={`${config.label}: ${config.description}${showShortcuts ? ` (${config.shortcut})` : ''}`}
                        className={`
                            flex items-center gap-2 px-3 py-2 text-sm font-medium transition-all
                            ${variant === 'pills'
                                ? `rounded-md ${isActive
                                    ? 'bg-primary-100 dark:bg-primary-900/30 text-primary-700 dark:text-primary-300'
                                    : 'text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800'
                                }`
                                : `border-b-2 -mb-px ${isActive
                                    ? 'border-primary-500 text-primary-700 dark:text-primary-300'
                                    : 'border-transparent text-text-secondary hover:text-text-primary hover:border-grey-300'
                                }`
                            }
                            ${isRecommended && !isActive ? 'relative' : ''}
                        `}
                    >
                        {/* Icon */}
                        <span className={isActive ? `text-${config.color}` : ''}>
                            {MODE_ICONS[mode]}
                        </span>

                        {/* Label */}
                        <span className={isVertical ? '' : 'hidden sm:inline'}>
                            {config.label}
                        </span>

                        {/* Shortcut Badge */}
                        {showShortcuts && (
                            <span className="hidden lg:inline text-[10px] text-text-tertiary bg-grey-100 dark:bg-grey-700 px-1.5 py-0.5 rounded">
                                {config.shortcut}
                            </span>
                        )}

                        {/* Recommended Indicator */}
                        {isRecommended && !isActive && (
                            <span className="absolute -top-1 -right-1 w-2 h-2 bg-primary-500 rounded-full" />
                        )}
                    </button>
                );
            })}
        </div>
    );
}

// ============================================================================
// Sub-components
// ============================================================================

function DropdownSelector({
    currentMode,
    modes,
    recommendedModes,
    onSelect,
    showShortcuts,
    className,
}: {
    currentMode: CanvasMode;
    modes: CanvasMode[];
    recommendedModes: CanvasMode[];
    onSelect: (mode: CanvasMode) => void;
    showShortcuts: boolean;
    className: string;
}) {
    const config = CANVAS_MODE_CONFIG[currentMode];

    return (
        <div className={`relative group ${className}`}>
            <button
                className="flex items-center gap-2 px-3 py-2 bg-bg-paper border border-divider rounded-lg hover:bg-grey-50 dark:hover:bg-grey-800 transition-colors"
                aria-haspopup="listbox"
            >
                {MODE_ICONS[currentMode]}
                <span className="font-medium text-text-primary">{config.label}</span>
                <KeyboardArrowDown className="w-4 h-4 text-text-secondary" />
            </button>

            {/* Dropdown Menu */}
            <div className="absolute top-full left-0 mt-1 w-56 bg-bg-paper border border-divider rounded-lg shadow-lg opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all z-50">
                <div className="py-1">
                    {modes.map((mode) => {
                        const modeConfig = CANVAS_MODE_CONFIG[mode];
                        const isActive = mode === currentMode;
                        const isRecommended = recommendedModes.includes(mode);

                        return (
                            <button
                                key={mode}
                                onClick={() => onSelect(mode)}
                                className={`
                                    w-full flex items-center gap-3 px-3 py-2 text-left transition-colors
                                    ${isActive
                                        ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300'
                                        : 'hover:bg-grey-50 dark:hover:bg-grey-800'
                                    }
                                `}
                            >
                                {MODE_ICONS[mode]}
                                <div className="flex-1">
                                    <div className="flex items-center gap-2">
                                        <span className="font-medium text-sm">{modeConfig.label}</span>
                                        {isRecommended && (
                                            <span className="px-1.5 py-0.5 text-[10px] bg-primary-100 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400 rounded">
                                                Recommended
                                            </span>
                                        )}
                                    </div>
                                    <p className="text-xs text-text-secondary">{modeConfig.description}</p>
                                </div>
                                {showShortcuts && (
                                    <span className="text-xs text-text-tertiary">{modeConfig.shortcut}</span>
                                )}
                            </button>
                        );
                    })}
                </div>
            </div>
        </div>
    );
}

function CompactSelector({
    currentMode,
    modes,
    allModes,
    recommendedModes,
    onSelect,
    className,
}: {
    currentMode: CanvasMode;
    modes: CanvasMode[];
    allModes: CanvasMode[];
    recommendedModes: CanvasMode[];
    onSelect: (mode: CanvasMode) => void;
    className: string;
}) {
    // Show only the first N modes + a "more" dropdown
    const visibleModes = modes.slice(0, 4);
    const hiddenModes = allModes.filter((m) => !visibleModes.includes(m));

    return (
        <div className={`flex items-center gap-1 ${className}`}>
            {visibleModes.map((mode) => {
                const config = CANVAS_MODE_CONFIG[mode];
                const isActive = mode === currentMode;
                const isRecommended = recommendedModes.includes(mode);

                return (
                    <button
                        key={mode}
                        onClick={() => onSelect(mode)}
                        title={config.label}
                        className={`
                            relative p-2 rounded-lg transition-all
                            ${isActive
                                ? 'bg-primary-100 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400'
                                : 'text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800'
                            }
                        `}
                    >
                        {MODE_ICONS[mode]}
                        {isRecommended && !isActive && (
                            <span className="absolute top-0 right-0 w-1.5 h-1.5 bg-primary-500 rounded-full" />
                        )}
                    </button>
                );
            })}

            {/* More Button */}
            {hiddenModes.length > 0 && (
                <div className="relative group">
                    <button
                        className="p-2 rounded-lg text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800 transition-colors"
                        title="More modes"
                    >
                        <KeyboardArrowDown className="w-4 h-4" />
                    </button>

                    {/* Dropdown */}
                    <div className="absolute top-full right-0 mt-1 w-48 bg-bg-paper border border-divider rounded-lg shadow-lg opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all z-50">
                        <div className="py-1">
                            {hiddenModes.map((mode) => {
                                const config = CANVAS_MODE_CONFIG[mode];
                                const isActive = mode === currentMode;

                                return (
                                    <button
                                        key={mode}
                                        onClick={() => onSelect(mode)}
                                        className={`
                                            w-full flex items-center gap-2 px-3 py-2 text-left transition-colors
                                            ${isActive
                                                ? 'bg-primary-50 dark:bg-primary-900/20'
                                                : 'hover:bg-grey-50 dark:hover:bg-grey-800'
                                            }
                                        `}
                                    >
                                        {MODE_ICONS[mode]}
                                        <span className="text-sm">{config.label}</span>
                                    </button>
                                );
                            })}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}


