/**
 * AI Command Bar
 * 
 * Always-visible, context-aware AI input bar positioned below the toolbar.
 * Provides quick access to AI assistance without opening the full panel.
 * 
 * Features:
 * - Persistent visibility (non-intrusive)
 * - Context-aware suggestions based on current mode/phase
 * - Keyboard shortcut support (/ for commands, Cmd+K for palette)
 * - Inline AI suggestions as user types
 * - Expandable for longer prompts
 * 
 * @doc.type component
 * @doc.purpose Persistent AI access
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { useState, useCallback, useRef, useEffect, useMemo } from 'react';
import { Sparkles as AutoAwesome, Send, X as Close, ChevronDown as ExpandMore, ChevronUp as ExpandLess, History } from 'lucide-react';
import { Tooltip, Chip, Spinner as CircularProgress } from '@ghatana/ui';

import { TRANSITIONS, RADIUS } from '../../styles/design-tokens';
import type { CanvasMode } from '../../types/canvasMode';
import { LifecyclePhase, PHASE_LABELS } from '../../types/lifecycle';

// ============================================================================
// Types
// ============================================================================

export interface AICommandBarProps {
    /** Current canvas mode for context-aware suggestions */
    currentMode?: CanvasMode;
    /** Current lifecycle phase for context */
    currentPhase?: LifecyclePhase;
    /** Whether AI is currently processing */
    isProcessing?: boolean;
    /** Submit handler */
    onSubmit: (prompt: string, options?: AISubmitOptions) => Promise<void>;
    /** Cancel handler */
    onCancel?: () => void;
    /** Open full AI panel */
    onOpenFullPanel?: () => void;
    /** Placeholder text */
    placeholder?: string;
    /** Recent prompts for history */
    recentPrompts?: string[];
    /** Additional CSS classes */
    className?: string;
}

export interface AISubmitOptions {
    /** Include current selection context */
    includeSelection?: boolean;
    /** Include canvas context */
    includeCanvas?: boolean;
    /** Action type hint */
    actionType?: 'generate' | 'explain' | 'improve' | 'fix' | 'ask';
}

// ============================================================================
// Constants
// ============================================================================

const QUICK_ACTIONS: Record<CanvasMode, Array<{ label: string; prompt: string; icon: string }>> = {
    brainstorm: [
        { label: 'Expand idea', prompt: 'Help me expand on this idea with more details', icon: '💡' },
        { label: 'Find gaps', prompt: 'What am I missing in this concept?', icon: '🔍' },
        { label: 'Suggest features', prompt: 'Suggest key features for this project', icon: '✨' },
    ],
    diagram: [
        { label: 'Review architecture', prompt: 'Review this architecture for best practices', icon: '🏗️' },
        { label: 'Suggest components', prompt: 'What components should I add?', icon: '🧩' },
        { label: 'Optimize flow', prompt: 'How can I simplify this flow?', icon: '⚡' },
    ],
    design: [
        { label: 'UX review', prompt: 'Review this design for UX issues', icon: '👁️' },
        { label: 'Accessibility', prompt: 'Check accessibility concerns', icon: '♿' },
        { label: 'Responsive', prompt: 'How should this work on mobile?', icon: '📱' },
    ],
    code: [
        { label: 'Generate code', prompt: 'Generate implementation code', icon: '💻' },
        { label: 'Explain code', prompt: 'Explain how this works', icon: '📖' },
        { label: 'Find bugs', prompt: 'Look for potential bugs', icon: '🐛' },
    ],
    test: [
        { label: 'Generate tests', prompt: 'Generate test cases', icon: '🧪' },
        { label: 'Coverage gaps', prompt: 'What test cases am I missing?', icon: '📊' },
        { label: 'Edge cases', prompt: 'What edge cases should I consider?', icon: '🎯' },
    ],
    deploy: [
        { label: 'Config review', prompt: 'Review deployment configuration', icon: '⚙️' },
        { label: 'Security check', prompt: 'Check for security issues', icon: '🔒' },
        { label: 'Optimize', prompt: 'How can I optimize performance?', icon: '🚀' },
    ],
    observe: [
        { label: 'Analyze metrics', prompt: 'Help me understand these metrics', icon: '📈' },
        { label: 'Find issues', prompt: 'What issues should I investigate?', icon: '🔍' },
        { label: 'Suggest alerts', prompt: 'What alerts should I set up?', icon: '🔔' },
    ],
};

const SLASH_COMMANDS = [
    { command: '/generate', description: 'Generate code from selection', action: 'generate' },
    { command: '/explain', description: 'Explain selected element', action: 'explain' },
    { command: '/improve', description: 'Suggest improvements', action: 'improve' },
    { command: '/fix', description: 'Fix issues in selection', action: 'fix' },
    { command: '/test', description: 'Generate test cases', action: 'test' },
    { command: '/doc', description: 'Generate documentation', action: 'doc' },
];

// ============================================================================
// Component
// ============================================================================

export function AICommandBar({
    currentMode = 'brainstorm',
    currentPhase,
    isProcessing = false,
    onSubmit,
    onCancel,
    onOpenFullPanel,
    placeholder,
    recentPrompts = [],
    className = '',
}: AICommandBarProps) {
    const [value, setValue] = useState('');
    const [isExpanded, setIsExpanded] = useState(false);
    const [showHistory, setShowHistory] = useState(false);
    const [showSlashCommands, setShowSlashCommands] = useState(false);
    const inputRef = useRef<HTMLInputElement>(null);
    const textareaRef = useRef<HTMLTextAreaElement>(null);

    // Detect slash commands
    useEffect(() => {
        setShowSlashCommands(value.startsWith('/'));
    }, [value]);

    // Dynamic placeholder based on context
    const contextPlaceholder = useMemo(() => {
        if (placeholder) return placeholder;
        if (currentPhase) {
            return `Ask AI about ${PHASE_LABELS[currentPhase].toLowerCase()}...`;
        }
        return `Ask AI anything... (or press / for commands)`;
    }, [placeholder, currentPhase]);

    // Quick actions for current mode
    const quickActions = useMemo(() => {
        return QUICK_ACTIONS[currentMode] || QUICK_ACTIONS.brainstorm;
    }, [currentMode]);

    // Filtered slash commands
    const filteredCommands = useMemo(() => {
        if (!value.startsWith('/')) return [];
        const query = value.slice(1).toLowerCase();
        return SLASH_COMMANDS.filter(cmd =>
            cmd.command.toLowerCase().includes(query) ||
            cmd.description.toLowerCase().includes(query)
        );
    }, [value]);

    // Handle submit
    const handleSubmit = useCallback(async () => {
        const trimmed = value.trim();
        if (!trimmed || isProcessing) return;

        // Parse slash command if present
        let actionType: AISubmitOptions['actionType'] = 'ask';
        let prompt = trimmed;

        if (trimmed.startsWith('/')) {
            const [cmd, ...rest] = trimmed.split(' ');
            const command = SLASH_COMMANDS.find(c => c.command === cmd);
            if (command) {
                actionType = command.action as AISubmitOptions['actionType'];
                prompt = rest.join(' ') || command.description;
            }
        }

        try {
            await onSubmit(prompt, { actionType, includeCanvas: true });
            setValue('');
            setIsExpanded(false);
        } catch (error) {
            console.error('AI submit error:', error);
        }
    }, [value, isProcessing, onSubmit]);

    // Handle key events
    const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSubmit();
        }
        if (e.key === 'Escape') {
            if (value) {
                setValue('');
            } else {
                onCancel?.();
            }
        }
        // Expand on shift+enter or long text
        if (e.key === 'Enter' && e.shiftKey) {
            setIsExpanded(true);
        }
    }, [handleSubmit, value, onCancel]);

    // Handle quick action click
    const handleQuickAction = useCallback((prompt: string) => {
        if (prompt) {
            setValue(prompt);
            inputRef.current?.focus();
        }
    }, []);

    // Handle history item click
    const handleHistoryClick = useCallback((prompt: string) => {
        setValue(prompt);
        setShowHistory(false);
        inputRef.current?.focus();
    }, []);

    // Handle slash command click
    const handleSlashCommand = useCallback((command: string) => {
        setValue(command + ' ');
        setShowSlashCommands(false);
        inputRef.current?.focus();
    }, []);

    return (
        <div className={`relative ${className}`}>
            {/* Main Input Bar */}
            <div
                className={`
                    flex items-center gap-2 px-3 py-2
                    bg-bg-paper border border-divider
                    ${RADIUS.input} ${TRANSITIONS.default}
                    focus-within:border-primary-500 focus-within:ring-2 focus-within:ring-primary-500/20
                    ${isExpanded ? 'flex-col items-stretch' : ''}
                `}
            >
                {/* AI Icon */}
                <div className="flex items-center gap-2 flex-shrink-0">
                    <AutoAwesome
                        className={`w-5 h-5 text-primary-500 ${isProcessing ? 'animate-pulse' : ''}`}
                    />
                    {currentPhase && !isExpanded && (
                        <Chip
                            label={PHASE_LABELS[currentPhase]}
                            size="sm"
                            className="h-5 text-xs text-[0.65rem]"
                        />
                    )}
                </div>

                {/* Input */}
                {isExpanded ? (
                    <textarea
                        ref={textareaRef}
                        value={value}
                        onChange={(e) => setValue(e.target.value)}
                        onKeyDown={handleKeyDown}
                        placeholder={contextPlaceholder}
                        disabled={isProcessing}
                        rows={3}
                        className={`
                            flex-1 w-full bg-transparent border-none outline-none resize-none
                            text-text-primary placeholder:text-text-tertiary
                            disabled:opacity-50 disabled:cursor-not-allowed
                            text-sm
                        `}
                        aria-label="AI prompt input"
                    />
                ) : (
                    <input
                        ref={inputRef}
                        type="text"
                        value={value}
                        onChange={(e) => setValue(e.target.value)}
                        onKeyDown={handleKeyDown}
                        placeholder={contextPlaceholder}
                        disabled={isProcessing}
                        className={`
                            flex-1 bg-transparent border-none outline-none
                            text-text-primary placeholder:text-text-tertiary
                            disabled:opacity-50 disabled:cursor-not-allowed
                            text-sm
                        `}
                        aria-label="AI prompt input"
                    />
                )}

                {/* Action Buttons */}
                <div className="flex items-center gap-1 flex-shrink-0">
                    {/* History */}
                    {recentPrompts.length > 0 && (
                        <Tooltip title="Recent prompts">
                            <button
                                onClick={() => setShowHistory(!showHistory)}
                                className={`
                                    p-1.5 ${RADIUS.button} ${TRANSITIONS.fast}
                                    text-text-tertiary hover:text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800
                                `}
                                aria-label="Show recent prompts"
                            >
                                <History className="w-4 h-4" />
                            </button>
                        </Tooltip>
                    )}

                    {/* Expand/Collapse */}
                    <Tooltip title={isExpanded ? 'Collapse' : 'Expand'}>
                        <button
                            onClick={() => setIsExpanded(!isExpanded)}
                            className={`
                                p-1.5 ${RADIUS.button} ${TRANSITIONS.fast}
                                text-text-tertiary hover:text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800
                            `}
                            aria-label={isExpanded ? 'Collapse' : 'Expand'}
                        >
                            {isExpanded ? (
                                <ExpandLess className="w-4 h-4" />
                            ) : (
                                <ExpandMore className="w-4 h-4" />
                            )}
                        </button>
                    </Tooltip>

                    {/* Clear */}
                    {value && (
                        <button
                            onClick={() => setValue('')}
                            className={`
                                p-1.5 ${RADIUS.button} ${TRANSITIONS.fast}
                                text-text-tertiary hover:text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800
                            `}
                            aria-label="Clear"
                        >
                            <Close className="w-4 h-4" />
                        </button>
                    )}

                    {/* Submit */}
                    <button
                        onClick={handleSubmit}
                        disabled={!value.trim() || isProcessing}
                        className={`
                            p-1.5 ${RADIUS.button} ${TRANSITIONS.fast}
                            ${value.trim()
                                ? 'bg-primary-500 text-white hover:bg-primary-600'
                                : 'text-text-tertiary cursor-not-allowed'
                            }
                            disabled:opacity-50
                        `}
                        aria-label="Submit"
                    >
                        {isProcessing ? (
                            <CircularProgress size={16} tone="neutral" />
                        ) : (
                            <Send className="w-4 h-4" />
                        )}
                    </button>
                </div>
            </div>

            {/* Quick Actions (show when empty and focused) */}
            {!value && !showHistory && (
                <div className="flex items-center gap-2 mt-2 overflow-x-auto pb-1">
                    {quickActions.map((action, index) => (
                        <button
                            key={index}
                            onClick={() => handleQuickAction(action.prompt)}
                            className={`
                                flex items-center gap-1.5 px-2.5 py-1
                                text-xs font-medium whitespace-nowrap
                                bg-grey-100 dark:bg-grey-800 text-text-secondary
                                hover:bg-grey-200 dark:hover:bg-grey-700
                                ${RADIUS.button} ${TRANSITIONS.fast}
                            `}
                        >
                            <span>{action.icon}</span>
                            <span>{action.label}</span>
                        </button>
                    ))}
                    {onOpenFullPanel && (
                        <button
                            onClick={onOpenFullPanel}
                            className={`
                                px-2.5 py-1 text-xs font-medium
                                text-primary-600 hover:text-primary-700
                                ${TRANSITIONS.fast}
                            `}
                        >
                            Open full panel →
                        </button>
                    )}
                </div>
            )}

            {/* Slash Commands Dropdown */}
            {showSlashCommands && filteredCommands.length > 0 && (
                <div
                    className={`
                        absolute top-full left-0 right-0 mt-1 z-50
                        bg-bg-paper border border-divider shadow-lg
                        ${RADIUS.card} overflow-hidden
                    `}
                >
                    {filteredCommands.map((cmd) => (
                        <button
                            key={cmd.command}
                            onClick={() => handleSlashCommand(cmd.command)}
                            className={`
                                w-full flex items-center gap-3 px-3 py-2 text-left
                                hover:bg-grey-100 dark:hover:bg-grey-800
                                ${TRANSITIONS.fast}
                            `}
                        >
                            <code className="text-sm font-mono text-primary-600">{cmd.command}</code>
                            <span className="text-sm text-text-secondary">{cmd.description}</span>
                        </button>
                    ))}
                </div>
            )}

            {/* History Dropdown */}
            {showHistory && recentPrompts.length > 0 && (
                <div
                    className={`
                        absolute top-full left-0 right-0 mt-1 z-50
                        bg-bg-paper border border-divider shadow-lg
                        ${RADIUS.card} overflow-hidden max-h-48 overflow-y-auto
                    `}
                >
                    <div className="px-3 py-2 text-xs font-medium text-text-tertiary border-b border-divider">
                        Recent Prompts
                    </div>
                    {recentPrompts.map((prompt, index) => (
                        <button
                            key={index}
                            onClick={() => handleHistoryClick(prompt)}
                            className={`
                                w-full px-3 py-2 text-left text-sm
                                text-text-primary truncate
                                hover:bg-grey-100 dark:hover:bg-grey-800
                                ${TRANSITIONS.fast}
                            `}
                        >
                            {prompt}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
}

export default AICommandBar;
