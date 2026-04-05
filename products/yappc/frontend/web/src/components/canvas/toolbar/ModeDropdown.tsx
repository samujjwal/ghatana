/**
 * Mode Dropdown
 * 
 * Dropdown selector for canvas mode with keyboard shortcuts displayed.
 * Replaces the floating mode selector pills for cleaner UI.
 * 
 * Features:
 * - 7 canvas modes (Brainstorm, Diagram, Design, Code, Test, Deploy, Observe)
 * - Keyboard shortcuts (1-7) displayed inline
 * - Full keyboard navigation (Arrow keys, Enter, Escape)
 * - WCAG 2.1 AA compliant accessibility
 * - Screen reader support with descriptive aria-labels
 * 
 * @doc.type component
 * @doc.purpose Canvas mode selection
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { useState, useRef, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { Lightbulb as EmojiObjects, GitBranch as AccountTree, Paintbrush as Brush, Code, Bug as BugReport, Rocket as RocketLaunch, Eye as Visibility, ChevronDown as KeyboardArrowDown } from 'lucide-react';

import { type CanvasMode } from '../../../types/canvas';
import { BUTTON, RADIUS, TRANSITIONS, MODE_COLORS, Z_INDEX } from '../../../styles/design-tokens';

// ============================================================================
// Types
// ============================================================================

export interface ModeDropdownProps {
    /** Current mode */
    value: CanvasMode;
    /** Callback when mode changes */
    onChange: (mode: CanvasMode) => void;
    /** Whether the dropdown is disabled */
    disabled?: boolean;
    /** Additional CSS classes */
    className?: string;
}

interface ModeOption {
    mode: CanvasMode;
    label: string;
    icon: React.ElementType;
    shortcut: string;
    description: string;
}

// ============================================================================
// Constants
// ============================================================================

const MODE_OPTIONS: ModeOption[] = [
    {
        mode: 'brainstorm',
        label: 'Brainstorm',
        icon: EmojiObjects,
        shortcut: '1',
        description: 'Sketch ideas freely',
    },
    {
        mode: 'diagram',
        label: 'Diagram',
        icon: AccountTree,
        shortcut: '2',
        description: 'Structure components',
    },
    {
        mode: 'design',
        label: 'Design',
        icon: Brush,
        shortcut: '3',
        description: 'Define UI/UX',
    },
    {
        mode: 'code',
        label: 'Code',
        icon: Code,
        shortcut: '4',
        description: 'Write implementation',
    },
    {
        mode: 'test',
        label: 'Test',
        icon: BugReport,
        shortcut: '5',
        description: 'Validate quality',
    },
    {
        mode: 'deploy',
        label: 'Deploy',
        icon: RocketLaunch,
        shortcut: '6',
        description: 'Ship to production',
    },
    {
        mode: 'observe',
        label: 'Observe',
        icon: Visibility,
        shortcut: '7',
        description: 'Monitor & improve',
    },
];

// ============================================================================
// Component
// ============================================================================

export function ModeDropdown({
    value,
    onChange,
    disabled = false,
    className = '',
}: ModeDropdownProps) {
    const [isOpen, setIsOpen] = useState(false);
    const [focusedIndex, setFocusedIndex] = useState(-1);
    const [dropdownPosition, setDropdownPosition] = useState({ top: 0, left: 0 });
    const dropdownRef = useRef<HTMLDivElement>(null);
    const triggerRef = useRef<HTMLButtonElement>(null);
    const optionRefs = useRef<(HTMLButtonElement | null)[]>([]);

    const currentOption = MODE_OPTIONS.find(opt => opt.mode === value) || MODE_OPTIONS[0];
    const CurrentIcon = currentOption.icon;

    // Calculate dropdown position when opening
    const updateDropdownPosition = useCallback(() => {
        if (triggerRef.current && typeof document !== 'undefined') {
            const rect = triggerRef.current.getBoundingClientRect();
            console.log('ModeDropdown trigger rect:', rect);
            const position = {
                top: rect.bottom + window.scrollY + 4, // 4px gap
                left: rect.left + window.scrollX,
            };
            console.log('ModeDropdown calculated position:', position);
            setDropdownPosition(position);
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
                    const next = prev < MODE_OPTIONS.length - 1 ? prev + 1 : 0;
                    optionRefs.current[next]?.focus();
                    return next;
                });
                break;
            case 'ArrowUp':
                event.preventDefault();
                setFocusedIndex(prev => {
                    const next = prev > 0 ? prev - 1 : MODE_OPTIONS.length - 1;
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
                setFocusedIndex(MODE_OPTIONS.length - 1);
                optionRefs.current[MODE_OPTIONS.length - 1]?.focus();
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
            // Focus first option when opening
            const currentIndex = MODE_OPTIONS.findIndex(opt => opt.mode === value);
            setFocusedIndex(currentIndex >= 0 ? currentIndex : 0);
            setTimeout(() => {
                optionRefs.current[currentIndex >= 0 ? currentIndex : 0]?.focus();
            }, 0);
        }
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, handleKeyDown, value]);

    const handleSelect = useCallback((mode: CanvasMode) => {
        onChange(mode);
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

    return (
        <div ref={dropdownRef} className={`relative ${className}`}>
            {/* Trigger Button */}
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
                aria-label={`Canvas mode: ${currentOption.label}. Press Enter to change mode.`}
                id="mode-dropdown-trigger"
                style={{
                    color: MODE_COLORS[value]?.primary || '#3b82f6',
                }}
            >
                <CurrentIcon className="w-4 h-4" aria-hidden="true" />
                <span className="text-sm font-medium">{currentOption.label}</span>
                <KeyboardArrowDown
                    className={`w-4 h-4 text-text-secondary ${TRANSITIONS.fast} ${isOpen ? 'rotate-180' : ''}`}
                />
            </button>

            {/* Dropdown Menu - Rendered via portal to avoid overflow clipping */}
            {isOpen && typeof document !== 'undefined' && createPortal(
                <div
                    ref={dropdownRef}
                    role="listbox"
                    aria-labelledby="mode-dropdown-trigger"
                    aria-activedescendant={focusedIndex >= 0 ? `mode-option-${MODE_OPTIONS[focusedIndex]?.mode}` : undefined}
                    className={`
                        fixed min-w-[220px]
                        bg-bg-paper border border-divider ${RADIUS.card}
                        shadow-lg overflow-hidden
                    `}
                    style={{
                        top: `${dropdownPosition.top}px`,
                        left: `${dropdownPosition.left}px`,
                        zIndex: 9999,
                    }}
                >
                    {(() => {
                        console.log('ModeDropdown portal rendered with position:', dropdownPosition);
                        return null;
                    })()}
                    {MODE_OPTIONS.map((option, index) => {
                        const Icon = option.icon;
                        const isSelected = option.mode === value;
                        const isFocused = index === focusedIndex;

                        return (
                            <button
                                key={option.mode}
                                ref={el => { optionRefs.current[index] = el; }}
                                id={`mode-option-${option.mode}`}
                                role="option"
                                aria-selected={isSelected}
                                aria-label={`${option.label}: ${option.description}. Press ${option.shortcut} for quick access.`}
                                onClick={() => handleSelect(option.mode)}
                                onKeyDown={(e) => {
                                    if (e.key === 'Enter' || e.key === ' ') {
                                        e.preventDefault();
                                        handleSelect(option.mode);
                                    }
                                }}
                                className={`
                                    w-full flex items-center gap-3 px-3 py-2.5 text-left
                                    ${TRANSITIONS.fast}
                                    ${isSelected
                                        ? 'bg-primary-50 dark:bg-primary-900/30'
                                        : isFocused
                                            ? 'bg-grey-100 dark:bg-grey-700'
                                            : 'hover:bg-grey-50 dark:hover:bg-grey-800'
                                    }
                                    focus:outline-none focus:bg-grey-100 dark:focus:bg-grey-700
                                `}
                                tabIndex={isFocused ? 0 : -1}
                            >
                                <Icon
                                    className={`w-4 h-4 ${isSelected ? 'text-primary-600' : 'text-text-secondary'}`}
                                    aria-hidden="true"
                                />
                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center gap-2">
                                        <span
                                            className={`text-sm font-medium ${isSelected ? 'text-primary-700 dark:text-primary-300' : 'text-text-primary'}`}
                                        >
                                            {option.label}
                                        </span>
                                        <kbd
                                            className="px-1.5 py-0.5 text-[10px] font-mono bg-grey-100 dark:bg-grey-700 rounded"
                                            aria-label={`Keyboard shortcut: ${option.shortcut}`}
                                        >
                                            {option.shortcut}
                                        </kbd>
                                    </div>
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
