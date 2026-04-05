/**
 * Level Dropdown
 * 
 * Dropdown selector for abstraction level navigation with zoom controls.
 * Replaces the floating breadcrumb navigator for cleaner UI.
 * 
 * Features:
 * - 4 abstraction levels (System, Component, File, Code)
 * - Zoom out/drill down buttons for quick navigation
 * - Full keyboard navigation (Arrow keys, Enter, Escape, Alt+Up/Down)
 * - WCAG 2.1 AA compliant accessibility
 * - Screen reader support with descriptive aria-labels
 * 
 * @doc.type component
 * @doc.purpose Abstraction level selection
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { useState, useRef, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { Globe as Public, LayoutGrid as Apps, File as InsertDriveFile, Braces as DataObject, ChevronDown as KeyboardArrowDown, ZoomIn, ZoomOut } from 'lucide-react';

import { type AbstractionLevel } from '../../../types/canvas';
import { BUTTON, RADIUS, TRANSITIONS, LEVEL_COLORS, Z_INDEX } from '../../../styles/design-tokens';

// ============================================================================
// Types
// ============================================================================

export interface LevelDropdownProps {
    /** Current abstraction level */
    value: AbstractionLevel;
    /** Callback when level changes */
    onChange: (level: AbstractionLevel) => void;
    /** Whether can drill down further */
    canDrillDown?: boolean;
    /** Whether can zoom out */
    canZoomOut?: boolean;
    /** Callback for drill down action */
    onDrillDown?: () => void;
    /** Callback for zoom out action */
    onZoomOut?: () => void;
    /** Whether the dropdown is disabled */
    disabled?: boolean;
    /** Additional CSS classes */
    className?: string;
}

interface LevelOption {
    level: AbstractionLevel;
    label: string;
    icon: React.ElementType;
    description: string;
}

// ============================================================================
// Constants
// ============================================================================

const LEVEL_OPTIONS: LevelOption[] = [
    {
        level: 'system',
        label: 'System',
        icon: Public,
        description: 'High-level architecture view',
    },
    {
        level: 'component',
        label: 'Component',
        icon: Apps,
        description: 'Component relationships',
    },
    {
        level: 'file',
        label: 'File',
        icon: InsertDriveFile,
        description: 'File-level details',
    },
    {
        level: 'code',
        label: 'Code',
        icon: DataObject,
        description: 'Implementation details',
    },
];

// ============================================================================
// Component
// ============================================================================

export function LevelDropdown({
    value,
    onChange,
    canDrillDown = false,
    canZoomOut = false,
    onDrillDown,
    onZoomOut,
    disabled = false,
    className = '',
}: LevelDropdownProps) {
    const [isOpen, setIsOpen] = useState(false);
    const [focusedIndex, setFocusedIndex] = useState(-1);
    const [dropdownPosition, setDropdownPosition] = useState({ top: 0, left: 0 });
    const dropdownRef = useRef<HTMLDivElement>(null);
    const triggerRef = useRef<HTMLButtonElement>(null);
    const optionRefs = useRef<(HTMLButtonElement | null)[]>([]);

    const currentOption = LEVEL_OPTIONS.find(opt => opt.level === value) || LEVEL_OPTIONS[0];
    const CurrentIcon = currentOption.icon;
    const currentIndex = LEVEL_OPTIONS.findIndex(opt => opt.level === value);

    // Calculate dropdown position when opening
    const updateDropdownPosition = useCallback(() => {
        if (triggerRef.current && typeof document !== 'undefined') {
            const rect = triggerRef.current.getBoundingClientRect();
            setDropdownPosition({
                top: rect.bottom + window.scrollY + 4, // 4px gap
                left: rect.left + window.scrollX,
            });
        }
    }, []);

    // Close on outside click
    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
                setIsOpen(false);
            }
        };

        if (isOpen) {
            document.addEventListener('mousedown', handleClickOutside);
        }
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [isOpen]);

    // Keyboard navigation within dropdown
    const handleKeyDown = useCallback((event: KeyboardEvent) => {
        if (!isOpen) return;

        switch (event.key) {
            case 'Escape':
                event.preventDefault();
                setIsOpen(false);
                setFocusedIndex(-1);
                triggerRef.current?.focus();
                break;
            case 'ArrowDown':
                event.preventDefault();
                setFocusedIndex(prev => {
                    const next = prev < LEVEL_OPTIONS.length - 1 ? prev + 1 : 0;
                    optionRefs.current[next]?.focus();
                    return next;
                });
                break;
            case 'ArrowUp':
                event.preventDefault();
                setFocusedIndex(prev => {
                    const next = prev > 0 ? prev - 1 : LEVEL_OPTIONS.length - 1;
                    optionRefs.current[next]?.focus();
                    return next;
                });
                break;
            case 'Home':
                event.preventDefault();
                setFocusedIndex(0);
                optionRefs.current[0]?.focus();
                break;
            case 'End':
                event.preventDefault();
                setFocusedIndex(LEVEL_OPTIONS.length - 1);
                optionRefs.current[LEVEL_OPTIONS.length - 1]?.focus();
                break;
            case 'Tab':
                setIsOpen(false);
                setFocusedIndex(-1);
                break;
        }
    }, [isOpen]);

    useEffect(() => {
        if (isOpen) {
            document.addEventListener('keydown', handleKeyDown);
            // Focus current option when opening
            const idx = LEVEL_OPTIONS.findIndex(opt => opt.level === value);
            setFocusedIndex(idx >= 0 ? idx : 0);
            setTimeout(() => {
                optionRefs.current[idx >= 0 ? idx : 0]?.focus();
            }, 0);
        }
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, handleKeyDown, value]);

    const handleSelect = useCallback((level: AbstractionLevel) => {
        onChange(level);
        setIsOpen(false);
        setFocusedIndex(-1);
        triggerRef.current?.focus();
    }, [onChange]);

    const handleTriggerKeyDown = useCallback((event: React.KeyboardEvent) => {
        if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            updateDropdownPosition();
            setIsOpen(true);
        }
    }, [updateDropdownPosition]);

    // Get level name for accessibility
    const getNextLevelName = () => {
        const nextIdx = currentIndex + 1;
        return nextIdx < LEVEL_OPTIONS.length ? LEVEL_OPTIONS[nextIdx].label : null;
    };

    const getPrevLevelName = () => {
        const prevIdx = currentIndex - 1;
        return prevIdx >= 0 ? LEVEL_OPTIONS[prevIdx].label : null;
    };

    return (
        <div ref={dropdownRef} className={`relative flex items-center gap-1 ${className}`}>
            {/* Zoom Out Button */}
            {onZoomOut && (
                <button
                    onClick={onZoomOut}
                    disabled={!canZoomOut || disabled}
                    className={`
                        p-1.5 ${RADIUS.button} ${TRANSITIONS.fast}
                        hover:bg-grey-100 dark:hover:bg-grey-800
                        disabled:opacity-30 disabled:cursor-not-allowed
                        focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-1
                    `}
                    aria-label={canZoomOut
                        ? `Zoom out to ${getPrevLevelName()} level. Currently at ${currentOption.label} level. Shortcut: Alt+Up`
                        : `Cannot zoom out. Already at ${currentOption.label} level.`
                    }
                    title={canZoomOut ? `Zoom out to ${getPrevLevelName()} (Alt+↑)` : 'Already at highest level'}
                >
                    <ZoomOut className="w-4 h-4 text-text-secondary" aria-hidden="true" />
                </button>
            )}

            {/* Level Dropdown Trigger */}
            <button
                ref={triggerRef}
                onClick={() => {
                    if (!disabled) {
                        updateDropdownPosition();
                        setIsOpen(!isOpen);
                    }
                }}
                onKeyDown={handleTriggerKeyDown}
                disabled={disabled}
                className={`
                    flex items-center gap-2 px-3 ${BUTTON.default}
                    ${RADIUS.button} ${TRANSITIONS.fast}
                    bg-bg-paper border border-divider
                    hover:bg-grey-50 dark:hover:bg-grey-800
                    disabled:opacity-50 disabled:cursor-not-allowed
                    focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-1
                `}
                aria-haspopup="listbox"
                aria-expanded={isOpen}
                aria-label={`Abstraction level: ${currentOption.label}. ${currentOption.description}. Use zoom controls or select from list.`}
                id="level-dropdown-trigger"
                style={{
                    color: LEVEL_COLORS[value]?.primary || '#14b8a6',
                }}
            >
                <CurrentIcon className="w-4 h-4" aria-hidden="true" />
                <span className="text-sm font-medium">{currentOption.label}</span>
                <KeyboardArrowDown
                    className={`w-4 h-4 text-text-secondary ${TRANSITIONS.fast} ${isOpen ? 'rotate-180' : ''}`}
                    aria-hidden="true"
                />
            </button>

            {/* Drill Down Button */}
            {onDrillDown && (
                <button
                    onClick={onDrillDown}
                    disabled={!canDrillDown || disabled}
                    className={`
                        p-1.5 ${RADIUS.button} ${TRANSITIONS.fast}
                        hover:bg-grey-100 dark:hover:bg-grey-800
                        disabled:opacity-30 disabled:cursor-not-allowed
                        focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-1
                    `}
                    aria-label={canDrillDown
                        ? `Drill down to ${getNextLevelName()} level. Currently at ${currentOption.label} level. Shortcut: Alt+Down`
                        : `Cannot drill down. Already at ${currentOption.label} level.`
                    }
                    title={canDrillDown ? `Drill down to ${getNextLevelName()} (Alt+↓)` : 'Already at most detailed level'}
                >
                    <ZoomIn className="w-4 h-4 text-text-secondary" aria-hidden="true" />
                </button>
            )}

            {/* Dropdown Menu - Rendered via portal to avoid overflow clipping */}
            {isOpen && typeof document !== 'undefined' && createPortal(
                <div
                    ref={dropdownRef}
                    role="listbox"
                    aria-labelledby="level-dropdown-trigger"
                    aria-activedescendant={focusedIndex >= 0 ? `level-option-${LEVEL_OPTIONS[focusedIndex]?.level}` : undefined}
                    className={`
                        fixed min-w-[200px]
                        bg-bg-paper border border-divider ${RADIUS.card}
                        shadow-lg overflow-hidden
                    `}
                    style={{
                        top: `${dropdownPosition.top}px`,
                        left: `${dropdownPosition.left}px`,
                        zIndex: 9999,
                    }}
                >
                    {LEVEL_OPTIONS.map((option, index) => {
                        const Icon = option.icon;
                        const isSelected = option.level === value;
                        const isFocused = index === focusedIndex;

                        return (
                            <button
                                key={option.level}
                                ref={el => { optionRefs.current[index] = el; }}
                                id={`level-option-${option.level}`}
                                role="option"
                                aria-selected={isSelected}
                                aria-label={`${option.label}: ${option.description}`}
                                onClick={() => handleSelect(option.level)}
                                onKeyDown={(e) => {
                                    if (e.key === 'Enter' || e.key === ' ') {
                                        e.preventDefault();
                                        handleSelect(option.level);
                                    }
                                }}
                                className={`
                                    w-full flex items-center gap-3 px-3 py-2.5 text-left
                                    ${TRANSITIONS.fast}
                                    ${isSelected
                                        ? 'bg-secondary-50 dark:bg-secondary-900/30'
                                        : isFocused
                                            ? 'bg-grey-100 dark:bg-grey-700'
                                            : 'hover:bg-grey-50 dark:hover:bg-grey-800'
                                    }
                                    focus:outline-none focus:bg-grey-100 dark:focus:bg-grey-700
                                `}
                                tabIndex={isFocused ? 0 : -1}
                            >
                                <Icon
                                    className={`w-4 h-4 ${isSelected ? 'text-secondary-600' : 'text-text-secondary'}`}
                                    aria-hidden="true"
                                />
                                <div className="flex-1 min-w-0">
                                    <span
                                        className={`text-sm font-medium ${isSelected ? 'text-secondary-700 dark:text-secondary-300' : 'text-text-primary'}`}
                                    >
                                        {option.label}
                                    </span>
                                    <p className="text-xs text-text-secondary mt-0.5" aria-hidden="true">
                                        {option.description}
                                    </p>
                                </div>
                            </button>
                        );
                    })}
                </div>,
                document.body
            )}
        </div>
    );
}
