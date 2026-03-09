/**
 * Unified Canvas Toolbar
 * 
 * Single unified toolbar that replaces 12+ scattered floating controls.
 * Provides a clean, organized interface for all canvas operations.
 * 
 * Layout:
 * [History] | [Mode ▼] [Level ▼] | [Spacer] | [Score] [AI] [⚡] [?]
 * 
 * @doc.type component
 * @doc.purpose Unified canvas control bar
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { useCallback, useEffect } from 'react';
import { Undo2 as Undo, Redo2 as Redo, Sparkles as AutoAwesome, HelpCircle as Help, CheckCircle, AlertTriangle as Warning, AlertCircle as ErrorIcon, Code as CodeIcon, CloudCheck as CloudDone, CloudOff, RefreshCw as Sync, MoreVertical as MoreVert, Navigation as NavigationIcon, Paintbrush as BrushIcon, GitBranch as DiagramIcon } from 'lucide-react';
import { Menu, MenuItem, Tooltip } from '@ghatana/ui';
import React, { useState } from 'react';

import { ModeDropdown } from './ModeDropdown';
import { LevelDropdown } from './LevelDropdown';
import type { CanvasMode } from '../../../types/canvasMode';
import type { AbstractionLevel } from '../../../types/canvas';
import { type AutoSaveStatus } from '../versioning/AutoSaveIndicator';
import { TOOLBAR, TRANSITIONS, RADIUS, Z_INDEX } from '../../../styles/design-tokens';

// ============================================================================
// Types
// ============================================================================

export interface UnifiedCanvasToolbarProps {
    // Interaction Mode (navigate, sketch, code, diagram)
    interactionMode?: 'navigate' | 'sketch' | 'code' | 'diagram';
    onInteractionModeChange?: (mode: 'navigate' | 'sketch' | 'code' | 'diagram') => void;

    // Mode
    mode?: CanvasMode;
    currentMode?: CanvasMode; // alias for mode
    onModeChange: (mode: CanvasMode) => void;

    // Abstraction Level
    abstractionLevel?: AbstractionLevel;
    currentLevel?: AbstractionLevel; // alias for abstractionLevel
    onLevelChange: (level: AbstractionLevel) => void;
    canDrillDown?: boolean;
    canZoomOut?: boolean;
    onDrillDown?: () => void;
    onZoomOut?: () => void;

    // History
    onUndo?: () => void;
    onRedo?: () => void;
    canUndo?: boolean;
    canRedo?: boolean;

    // Progressive Disclosure
    showHistoryControls?: boolean; // Show undo/redo (default: true after first edit)
    nodeCount?: number; // Used for progressive disclosure logic

    // Validation
    validationScore?: number;
    errorCount?: number;
    warningCount?: number;
    isValidating?: boolean;
    onOpenValidation?: () => void;
    onValidationPanelToggle?: () => void; // alias

    // AI
    aiSuggestionCount?: number;
    isAnalyzing?: boolean;
    onOpenAI?: () => void;
    onAIPanelToggle?: () => void; // alias

    // Code Generation
    canGenerate?: boolean;
    isGenerating?: boolean;
    generatedFileCount?: number;
    onOpenCodeGen?: () => void;
    onCodeGenPanelToggle?: () => void; // alias
    onGenerate?: () => void;
    onValidate?: () => void;

    // Unified Panel
    onOpenUnifiedPanel?: () => void;
    onUnifiedPanelToggle?: () => void; // alias
    unifiedPanelOpen?: boolean;

    // Guidance
    onOpenGuidance?: () => void;
    onGuidanceToggle?: () => void; // alias
    guidanceOpen?: boolean;

    // Onboarding
    onOpenOnboarding?: () => void;

    // Save Status
    saveStatus?: AutoSaveStatus;
    lastSaveTime?: number;
    onRetry?: () => void;
    onRetrySave?: () => void; // alias

    // Sketch Tools (optional, if toolbar manages them)
    sketchTool?: unknown;
    onSketchToolChange?: (tool: unknown) => void;

    /** Additional CSS classes */
    className?: string;
}

// ============================================================================
// Sub-components
// ============================================================================

/**
 * Visual section divider with optional label
 */
interface ToolbarSectionProps {
    label?: string;
    children: React.ReactNode;
}

function ToolbarSection({ label, children }: ToolbarSectionProps) {
    return (
        <div className="flex items-center gap-1">
            {label && (
                <span className="text-xs font-medium text-text-tertiary uppercase tracking-wider mr-1">
                    {label}
                </span>
            )}
            {children}
        </div>
    );
}

function ToolbarDivider() {
    return <div className="w-px h-6 bg-divider mx-2" />;
}

interface IconButtonProps {
    onClick?: (event: React.MouseEvent<HTMLElement>) => void;
    disabled?: boolean;
    active?: boolean;
    title?: string;
    children: React.ReactNode;
    variant?: 'default' | 'success' | 'warning' | 'error' | 'primary' | 'secondary';
    size?: 'sm' | 'default';
    'aria-label'?: string;
}

function IconButton({
    onClick,
    disabled = false,
    active = false,
    title,
    children,
    variant = 'default',
    size = 'default',
    'aria-label': ariaLabel,
}: IconButtonProps) {
    const sizeClass = size === 'sm' ? 'p-2 md:p-1' : 'p-3 md:p-1.5';

    const variantClass = {
        default: active
            ? 'bg-grey-100 dark:bg-grey-800 text-text-primary'
            : 'text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800',
        success: 'text-green-600 hover:bg-green-50 dark:hover:bg-green-900/30',
        warning: 'text-yellow-600 hover:bg-yellow-50 dark:hover:bg-yellow-900/30',
        error: 'text-red-600 hover:bg-red-50 dark:hover:bg-red-900/30',
        primary: 'text-primary-600 hover:bg-primary-50 dark:hover:bg-primary-900/30',
        secondary: 'text-secondary-600 hover:bg-secondary-50 dark:hover:bg-secondary-900/30',
    }[variant];

    return (
        <Tooltip title={title || ''} arrow enterDelay={400}>
            {/* Span wrapper needed for disabled button tooltips and flex layout preservation */}
            <span className="inline-flex">
                <button
                    onClick={onClick}
                    disabled={disabled}
                    // title removed - handled by Tooltip
                    aria-label={ariaLabel || title}
                    className={`
                        ${sizeClass} ${RADIUS.button} ${TRANSITIONS.fast}
                        ${variantClass}
                        disabled:opacity-30 disabled:cursor-not-allowed
                        focus:outline-none focus:ring-2 focus:ring-primary-500
                    `}
                >
                    {children}
                </button>
            </span>
        </Tooltip>
    );
}

interface BadgeButtonProps {
    onClick?: () => void;
    disabled?: boolean;
    title?: string;
    label: string | number;
    variant: 'success' | 'warning' | 'error' | 'info' | 'secondary' | 'default';
    isLoading?: boolean;
    icon?: React.ReactNode;
    className?: string;
    'aria-label'?: string;
}

function BadgeButton({
    onClick,
    disabled = false,
    title,
    label,
    variant,
    isLoading = false,
    icon,
    className = '',
    'aria-label': ariaLabel,
}: BadgeButtonProps) {
    const variantClass = {
        success: 'bg-green-500 text-white hover:bg-green-600',
        warning: 'bg-yellow-500 text-black hover:bg-yellow-600',
        error: 'bg-red-500 text-white hover:bg-red-600',
        info: 'bg-blue-500 text-white hover:bg-blue-600',
        secondary: 'bg-secondary-500 text-white hover:bg-secondary-600',
        default: 'bg-grey-200 text-text-secondary hover:bg-grey-300 dark:bg-grey-700 dark:hover:bg-grey-600',
    }[variant];

    return (
        <Tooltip title={title || ''} arrow enterDelay={400}>
            <span className="inline-flex">
                <button
                    onClick={onClick}
                    disabled={disabled || isLoading}
                    // title removed - handled by Tooltip
                    aria-label={ariaLabel || title}
                    className={`
                        flex items-center gap-1 px-3 py-2 md:px-2.5 md:py-1 text-sm md:text-xs font-bold
                        ${RADIUS.button} ${TRANSITIONS.fast}
                        ${variantClass}
                        disabled:opacity-50 disabled:cursor-not-allowed
                        ${className}
                    `}
                    style={{
                        animation: className.includes('animate-pulse-slow') ? 'pulse-slow 3s ease-in-out infinite' : undefined,
                    }}
                >
                    {isLoading ? (
                        <Sync className="w-3 h-3 animate-spin" />
                    ) : icon ? (
                        <span className="flex items-center justify-center w-3 h-3">{icon}</span>
                    ) : null}
                    <span>{label}</span>
                </button>
            </span>
        </Tooltip>
    );
}

// ============================================================================
// Main Component
// ============================================================================

export function UnifiedCanvasToolbar({
    // Interaction Mode
    interactionMode,
    onInteractionModeChange,
    // Mode
    mode: modeProp,
    currentMode,
    onModeChange,
    // Abstraction Level
    abstractionLevel: levelProp,
    currentLevel,
    onLevelChange,
    canDrillDown,
    canZoomOut,
    onDrillDown,
    onZoomOut,
    // History
    onUndo,
    onRedo,
    canUndo = true,
    canRedo = true,
    showHistoryControls = true,
    nodeCount = 0,
    // Validation
    validationScore = 100,
    errorCount = 0,
    warningCount = 0,
    isValidating = false,
    onOpenValidation,
    onValidationPanelToggle,
    onValidate: _onValidate,
    // AI
    aiSuggestionCount = 0,
    isAnalyzing = false,
    onOpenAI,
    onAIPanelToggle,
    // Code Generation
    canGenerate = false,
    isGenerating = false,
    generatedFileCount = 0,
    onOpenCodeGen,
    onCodeGenPanelToggle,
    onGenerate: _onGenerate,
    // Unified Panel
    onOpenUnifiedPanel,
    onUnifiedPanelToggle,
    unifiedPanelOpen = false,
    // Guidance
    onOpenGuidance,
    onGuidanceToggle,
    guidanceOpen = false,
    // Onboarding
    onOpenOnboarding,
    // Save Status
    saveStatus = 'idle',
    lastSaveTime,
    onRetry,
    onRetrySave,
    // Sketch
    sketchTool: _sketchTool,
    onSketchToolChange: _onSketchToolChange,
    className = '',
}: UnifiedCanvasToolbarProps) {
    // Media query for mobile responsiveness
    const theme = useTheme();
    const isMobile = useMediaQuery(theme.breakpoints.down('md'));
    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
    const mobileMenuOpen = Boolean(anchorEl);

    // Inject animation keyframes exactly once — avoids duplicate <style> nodes
    // on HMR reloads and server-side rendering hydration.
    useEffect(() => {
        const STYLE_ID = 'canvas-toolbar-animations';
        if (document.getElementById(STYLE_ID)) return;
        const style = document.createElement('style');
        style.id = STYLE_ID;
        style.textContent = `
            @keyframes pulse-slow {
                0%, 100% { opacity: 1; transform: scale(1); }
                50% { opacity: 0.85; transform: scale(1.02); }
            }
        `;
        document.head.appendChild(style);
    }, []);

    const handleMobileMenuClick = (event: React.MouseEvent<HTMLElement>) => {
        setAnchorEl(event.currentTarget);
    };

    const handleMobileMenuClose = () => {
        setAnchorEl(null);
    };

    // Resolve aliased props
    const mode = modeProp ?? currentMode ?? 'explore';
    const abstractionLevel = levelProp ?? currentLevel ?? 'system';
    const handleOpenValidation = onOpenValidation ?? onValidationPanelToggle;
    const handleOpenAI = onOpenAI ?? onAIPanelToggle;
    const handleOpenCodeGen = onOpenCodeGen ?? onCodeGenPanelToggle;
    const handleOpenUnifiedPanel = onOpenUnifiedPanel ?? onUnifiedPanelToggle;
    const handleOpenGuidance = onOpenGuidance ?? onGuidanceToggle;
    const handleRetry = onRetry ?? onRetrySave;

    // Determine validation badge variant
    const validationVariant =
        errorCount > 0 ? 'error' : warningCount > 0 ? 'warning' : 'success';

    // Format last save time
    const formatSaveTime = useCallback((timestamp?: number) => {
        if (!timestamp) return '';
        const diff = Date.now() - timestamp;
        if (diff < 60000) return 'just now';
        if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
        return new Date(timestamp).toLocaleTimeString();
    }, []);

    // Save status display
    const SaveStatusIconMap: Record<AutoSaveStatus, typeof CloudDone> = {
        idle: CloudDone,
        saving: Sync,
        saved: CloudDone,
        error: CloudOff,
        disabled: CloudOff,
    };
    const SaveStatusIcon = SaveStatusIconMap[saveStatus];

    const saveStatusTitleMap: Record<AutoSaveStatus, string> = {
        idle: 'Auto-save enabled',
        saving: 'Saving...',
        saved: `Saved ${formatSaveTime(lastSaveTime)}`,
        error: 'Save failed - click to retry',
        disabled: 'Auto-save disabled',
    };
    const saveStatusTitle = saveStatusTitleMap[saveStatus];

    // Responsive styles
    const toolbarPosition = isMobile ? {
        bottom: 0,
        left: 0,
        right: 0,
        top: 'auto',
        borderTop: '1px solid var(--border-divider)', // Assuming variable or handle via class
        borderBottom: 'none',
    } : {
        position: 'absolute' as const,
        top: 0,
        left: 0,
        right: 0,
    };

    return (
        <div
            className={`
                ${TOOLBAR.height} px-4 flex items-center gap-3
                ${isMobile ? 'border-t' : 'border-b'} border-divider bg-bg-paper
                ${className}
            `}
            style={{
                ...toolbarPosition,
                zIndex: Z_INDEX.toolbar,
            }}
            data-testid="unified-canvas-toolbar"
            role="toolbar"
            aria-label="Canvas actions"
        >
            {/* Section 1: EDIT - History Controls (Progressive Disclosure - Hidden until first node) */}
            {nodeCount > 0 && (
                <div className="hidden md:flex">
                    <ToolbarSection>
                        <IconButton
                            onClick={onUndo}
                            disabled={!canUndo}
                            title="Undo (⌘Z)"
                            aria-label="Undo"
                        >
                            <Undo className="w-4 h-4" />
                        </IconButton>
                        <IconButton
                            onClick={onRedo}
                            disabled={!canRedo}
                            title="Redo (⌘⇧Z)"
                            aria-label="Redo"
                        >
                            <Redo className="w-4 h-4" />
                        </IconButton>
                    </ToolbarSection>
                    <ToolbarDivider />
                </div>
            )}

            {/* Section 2: VIEW - Interaction Mode & Mode & Abstraction Level */}
            <ToolbarSection>
                {/* Interaction Mode Toggle */}
                {onInteractionModeChange && (
                    <div className="flex items-center gap-1 mr-2">
                        <IconButton
                            onClick={() => onInteractionModeChange('navigate')}
                            active={interactionMode === 'navigate'}
                            title="Navigate Mode (Pan, zoom, select)"
                            aria-label="Navigate mode"
                        >
                            <NavigationIcon className="w-4 h-4" />
                        </IconButton>
                        <IconButton
                            onClick={() => onInteractionModeChange('sketch')}
                            active={interactionMode === 'sketch'}
                            title="Sketch Mode (Draw, annotate)"
                            aria-label="Sketch mode"
                            variant={interactionMode === 'sketch' ? 'primary' : 'default'}
                        >
                            <BrushIcon className="w-4 h-4" />
                        </IconButton>
                        <IconButton
                            onClick={() => onInteractionModeChange('code')}
                            active={interactionMode === 'code'}
                            title="Code Mode (Edit code)"
                            aria-label="Code mode"
                            variant={interactionMode === 'code' ? 'primary' : 'default'}
                        >
                            <CodeIcon className="w-4 h-4" />
                        </IconButton>
                        <IconButton
                            onClick={() => onInteractionModeChange('diagram')}
                            active={interactionMode === 'diagram'}
                            title="Diagram Mode (Create diagrams)"
                            aria-label="Diagram mode"
                            variant={interactionMode === 'diagram' ? 'primary' : 'default'}
                        >
                            <DiagramIcon className="w-4 h-4" />
                        </IconButton>
                    </div>
                )}
                <ModeDropdown
                    value={mode}
                    onChange={onModeChange}
                />
                {!isMobile && (
                    <LevelDropdown
                        value={abstractionLevel}
                        onChange={onLevelChange}
                        canDrillDown={canDrillDown}
                        canZoomOut={canZoomOut}
                        onDrillDown={onDrillDown}
                        onZoomOut={onZoomOut}
                    />
                )}
            </ToolbarSection>

            {/* Spacer - Pushes right sections to the end */}
            <div className="flex-1" />

            {/* Section 3: STATUS - Save & Sync (Icon only on mobile) */}
            <ToolbarSection>
                <button
                    onClick={saveStatus === 'error' ? handleRetry : undefined}
                    className={`
                        flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium
                        ${RADIUS.button} ${TRANSITIONS.fast}
                        ${saveStatus === 'error'
                            ? 'text-red-600 bg-red-50 dark:bg-red-900/20 hover:bg-red-100 dark:hover:bg-red-900/30 cursor-pointer'
                            : saveStatus === 'saving'
                                ? 'text-blue-600 bg-blue-50 dark:bg-blue-900/20'
                                : 'text-text-secondary hover:bg-grey-50 dark:hover:bg-grey-800 cursor-default'
                        }
                    `}
                    title={saveStatusTitle}
                    aria-label={`Save status: ${saveStatusTitle}`}
                >
                    <SaveStatusIcon
                        className={`w-3.5 h-3.5 ${saveStatus === 'saving' ? 'animate-spin' : ''}`}
                    />
                    {!isMobile && (
                        <span>
                            {saveStatus === 'saving' ? 'Saving' :
                                saveStatus === 'error' ? 'Retry' :
                                    saveStatus === 'saved' ? formatSaveTime(lastSaveTime) : 'Synced'}
                        </span>
                    )}
                </button>
            </ToolbarSection>

            {!isMobile && <ToolbarDivider />}

            {/* Section 4: QUALITY & AI (Condensed on mobile) */}
            <ToolbarSection label={isMobile ? undefined : "Quality"}>
                {/* Validation Score Badge (Hidden on very small screens if needed, but useful) */}
                {!isMobile && (
                    <BadgeButton
                        onClick={handleOpenValidation}
                        title={`Quality Score: ${validationScore}`}
                        label={`${validationScore}`}
                        variant={validationVariant}
                        isLoading={isValidating}
                        icon={
                            errorCount > 0 ? <ErrorIcon className="w-3 h-3" /> :
                                warningCount > 0 ? <Warning className="w-3 h-3" /> :
                                    <CheckCircle className="w-3 h-3" />
                        }
                        aria-label="Quality Score"
                    />
                )}

                {/* AI Suggestions Badge - Always visible */}
                <BadgeButton
                    onClick={handleOpenAI}
                    title="AI Assistant"
                    label={aiSuggestionCount > 0 ? aiSuggestionCount : '✨'}
                    variant={aiSuggestionCount > 0 ? "info" : "default"}
                    isLoading={isAnalyzing}
                    icon={<AutoAwesome className={`w-3 h-3 ${aiSuggestionCount > 0 ? 'animate-pulse' : ''}`} />}
                    className={aiSuggestionCount > 0 ? 'ring-2 ring-blue-400 ring-opacity-50 animate-pulse-slow' : ''}
                    aria-label="AI Suggestions"
                />
            </ToolbarSection>

            {/* Mobile Actions Menu */}
            {isMobile && (
                <>
                    <IconButton onClick={handleMobileMenuClick} aria-label="More actions">
                        <MoreVert className="w-4 h-4" />
                    </IconButton>
                    <Menu
                        anchorEl={anchorEl}
                        open={mobileMenuOpen}
                        onClose={handleMobileMenuClose}
                        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
                        transformOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                    >
                        <MenuItem onClick={() => { onUndo?.(); handleMobileMenuClose(); }} disabled={!canUndo}>Undo</MenuItem>
                        <MenuItem onClick={() => { onRedo?.(); handleMobileMenuClose(); }} disabled={!canRedo}>Redo</MenuItem>
                        <MenuItem onClick={() => { handleOpenValidation?.(); handleMobileMenuClose(); }}>Validation Issues ({errorCount + warningCount})</MenuItem>
                        <MenuItem onClick={() => { handleOpenUnifiedPanel?.(); handleMobileMenuClose(); }}>Assistant Panel</MenuItem>
                        {canGenerate && <MenuItem onClick={() => { handleOpenCodeGen?.(); handleMobileMenuClose(); }}>Generate Code</MenuItem>}
                    </Menu>
                </>
            )}

            {/* Desktop Sections */}
            {!isMobile && (
                <>
                    {/* Section 5: GENERATE */}
                    {(canGenerate || generatedFileCount > 0 || isGenerating) && (
                        <>
                            <ToolbarDivider />
                            <ToolbarSection label="Code">
                                <BadgeButton
                                    onClick={handleOpenCodeGen}
                                    disabled={!canGenerate && generatedFileCount === 0}
                                    title="Generate Code"
                                    label={isGenerating ? '...' : generatedFileCount > 0 ? `${generatedFileCount}` : 'Gen'}
                                    variant="secondary"
                                    isLoading={isGenerating}
                                    icon={<CodeIcon className="w-3 h-3" />}
                                    aria-label="Generate Code"
                                />
                            </ToolbarSection>
                        </>
                    )}

                    <ToolbarDivider />

                    {/* Section 6: HELP */}
                    <ToolbarSection label="Help">
                        <IconButton
                            onClick={handleOpenUnifiedPanel}
                            active={unifiedPanelOpen}
                            title="Assistant Panel"
                            variant="primary"
                            aria-label="Assistant Panel"
                        >
                            <AutoAwesome className="w-4 h-4" />
                        </IconButton>
                        <IconButton
                            onClick={onOpenOnboarding || handleOpenGuidance}
                            active={guidanceOpen}
                            title="Help"
                            aria-label="Help"
                        >
                            <Help className="w-4 h-4" />
                        </IconButton>
                    </ToolbarSection>
                </>
            )}
        </div>
    );
}

// Export sub-components for testing
export { IconButton, BadgeButton, ToolbarDivider };
