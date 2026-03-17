/**
 * UnifiedCanvasToolbar Component Tests
 * 
 * Tests for the unified canvas toolbar including:
 * - Full layout rendering
 * - History controls (undo/redo)
 * - Mode and Level dropdowns integration
 * - Quality badges
 * - Save status
 * - Help section
 * 
 * @doc.type test
 * @doc.purpose UnifiedCanvasToolbar unit tests
 * @doc.layer product
 */

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

import { UnifiedCanvasToolbar } from '../UnifiedCanvasToolbar';
import type { CanvasMode } from '@ghatana/yappc-types/canvasMode';
import type { AbstractionLevel } from '@ghatana/yappc-types/abstractionLevel';

describe('UnifiedCanvasToolbar', () => {
    const defaultProps = {
        mode: 'diagram' as CanvasMode,
        onModeChange: vi.fn(),
        abstractionLevel: 'component' as AbstractionLevel,
        onLevelChange: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('Rendering', () => {
        it('renders the toolbar container', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} />);
            
            expect(screen.getByTestId('unified-canvas-toolbar')).toBeInTheDocument();
        });

        it('renders history controls (undo/redo)', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} onUndo={vi.fn()} onRedo={vi.fn()} />);
            
            expect(screen.getByTitle(/undo/i)).toBeInTheDocument();
            expect(screen.getByTitle(/redo/i)).toBeInTheDocument();
        });

        it('renders mode dropdown', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} />);
            
            expect(screen.getByRole('button', { name: /canvas mode/i })).toBeInTheDocument();
        });

        it('renders level dropdown', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} />);
            
            expect(screen.getByRole('button', { name: /abstraction level/i })).toBeInTheDocument();
        });

        it('renders validation score badge', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} validationScore={85} />);
            
            expect(screen.getByText('85')).toBeInTheDocument();
        });

        it('renders AI suggestions badge', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} aiSuggestionCount={3} />);
            
            expect(screen.getByText('3')).toBeInTheDocument();
        });

        it('renders save status indicator', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} saveStatus="saved" />);
            
            expect(screen.getByTitle(/saved/i)).toBeInTheDocument();
        });

        it('renders help button', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} onOpenOnboarding={vi.fn()} />);
            
            expect(screen.getByTitle(/tutorial|guidance/i)).toBeInTheDocument();
        });
    });

    describe('History Controls', () => {
        it('calls onUndo when undo button is clicked', async () => {
            const onUndo = vi.fn();
            const user = userEvent.setup();
            render(<UnifiedCanvasToolbar {...defaultProps} onUndo={onUndo} />);
            
            await user.click(screen.getByTitle(/undo/i));
            
            expect(onUndo).toHaveBeenCalledTimes(1);
        });

        it('calls onRedo when redo button is clicked', async () => {
            const onRedo = vi.fn();
            const user = userEvent.setup();
            render(<UnifiedCanvasToolbar {...defaultProps} onRedo={onRedo} />);
            
            await user.click(screen.getByTitle(/redo/i));
            
            expect(onRedo).toHaveBeenCalledTimes(1);
        });

        it('disables undo when canUndo is false', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} onUndo={vi.fn()} canUndo={false} />);
            
            expect(screen.getByTitle(/undo/i)).toBeDisabled();
        });

        it('disables redo when canRedo is false', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} onRedo={vi.fn()} canRedo={false} />);
            
            expect(screen.getByTitle(/redo/i)).toBeDisabled();
        });
    });

    describe('Mode Integration', () => {
        it('displays current mode in dropdown', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} mode="code" />);
            
            expect(screen.getByText('Code')).toBeInTheDocument();
        });

        it('calls onModeChange when mode is selected', async () => {
            const onModeChange = vi.fn();
            const user = userEvent.setup();
            render(<UnifiedCanvasToolbar {...defaultProps} onModeChange={onModeChange} />);
            
            await user.click(screen.getByRole('button', { name: /canvas mode/i }));
            await user.click(screen.getByRole('option', { name: /code/i }));
            
            expect(onModeChange).toHaveBeenCalledWith('code');
        });
    });

    describe('Level Integration', () => {
        it('displays current level in dropdown', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} abstractionLevel="file" />);
            
            expect(screen.getByText('File')).toBeInTheDocument();
        });

        it('calls onLevelChange when level is selected', async () => {
            const onLevelChange = vi.fn();
            const user = userEvent.setup();
            render(<UnifiedCanvasToolbar {...defaultProps} onLevelChange={onLevelChange} />);
            
            await user.click(screen.getByRole('button', { name: /abstraction level/i }));
            await user.click(screen.getByRole('option', { name: /file/i }));
            
            expect(onLevelChange).toHaveBeenCalledWith('file');
        });

        it('renders zoom controls when handlers are provided', () => {
            render(
                <UnifiedCanvasToolbar 
                    {...defaultProps} 
                    onZoomOut={vi.fn()} 
                    onDrillDown={vi.fn()}
                    canZoomOut={true}
                    canDrillDown={true}
                />
            );
            
            expect(screen.getByRole('button', { name: /zoom out/i })).toBeInTheDocument();
            expect(screen.getByRole('button', { name: /drill down/i })).toBeInTheDocument();
        });
    });

    describe('Validation Badge', () => {
        it('shows success variant when no errors', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} validationScore={100} errorCount={0} warningCount={0} />);
            
            const badge = screen.getByText('100');
            expect(badge.closest('button')).toHaveClass('bg-green-500');
        });

        it('shows warning variant when warnings exist', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} validationScore={80} errorCount={0} warningCount={2} />);
            
            const badge = screen.getByText('80');
            expect(badge.closest('button')).toHaveClass('bg-yellow-500');
        });

        it('shows error variant when errors exist', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} validationScore={60} errorCount={1} warningCount={0} />);
            
            const badge = screen.getByText('60');
            expect(badge.closest('button')).toHaveClass('bg-red-500');
        });

        it('shows loading state when validating', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} isValidating={true} />);
            
            expect(screen.getByTitle(/validating/i)).toBeInTheDocument();
        });

        it('calls onOpenValidation when clicked', async () => {
            const onOpenValidation = vi.fn();
            const user = userEvent.setup();
            render(<UnifiedCanvasToolbar {...defaultProps} onOpenValidation={onOpenValidation} />);
            
            await user.click(screen.getByText('100'));
            
            expect(onOpenValidation).toHaveBeenCalledTimes(1);
        });
    });

    describe('AI Badge', () => {
        it('shows suggestion count when available', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} aiSuggestionCount={5} />);
            
            expect(screen.getByText('5')).toBeInTheDocument();
        });

        it('shows sparkle icon when no suggestions', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} aiSuggestionCount={0} />);
            
            expect(screen.getByText('✨')).toBeInTheDocument();
        });

        it('shows loading state when analyzing', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} isAnalyzing={true} />);
            
            expect(screen.getByTitle(/analyzing/i)).toBeInTheDocument();
        });

        it('calls onOpenAI when clicked', async () => {
            const onOpenAI = vi.fn();
            const user = userEvent.setup();
            render(<UnifiedCanvasToolbar {...defaultProps} onOpenAI={onOpenAI} />);
            
            // Click the AI badge (either count or sparkle)
            const aiBadge = screen.getByTitle(/ai/i);
            await user.click(aiBadge);
            
            expect(onOpenAI).toHaveBeenCalledTimes(1);
        });
    });

    describe('Code Generation Badge', () => {
        it('renders when canGenerate is true', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} canGenerate={true} />);
            
            expect(screen.getByText('Gen')).toBeInTheDocument();
        });

        it('shows file count when files are generated', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} generatedFileCount={3} />);
            
            expect(screen.getByText('3')).toBeInTheDocument();
        });

        it('shows loading state when generating', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} isGenerating={true} canGenerate={true} />);
            
            expect(screen.getByTitle(/generating/i)).toBeInTheDocument();
        });

        it('calls onOpenCodeGen when clicked', async () => {
            const onOpenCodeGen = vi.fn();
            const user = userEvent.setup();
            render(<UnifiedCanvasToolbar {...defaultProps} canGenerate={true} onOpenCodeGen={onOpenCodeGen} />);
            
            await user.click(screen.getByText('Gen'));
            
            expect(onOpenCodeGen).toHaveBeenCalledTimes(1);
        });
    });

    describe('Save Status', () => {
        it('shows saved status', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} saveStatus="saved" />);
            
            expect(screen.getByTitle(/saved/i)).toBeInTheDocument();
        });

        it('shows saving status with animation', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} saveStatus="saving" />);
            
            expect(screen.getByTitle(/saving/i)).toBeInTheDocument();
            expect(screen.getByText('Saving')).toBeInTheDocument();
        });

        it('shows error status with retry option', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} saveStatus="error" onRetry={vi.fn()} />);
            
            expect(screen.getByTitle(/failed.*retry/i)).toBeInTheDocument();
            expect(screen.getByText('Retry')).toBeInTheDocument();
        });

        it('calls onRetry when error status is clicked', async () => {
            const onRetry = vi.fn();
            const user = userEvent.setup();
            render(<UnifiedCanvasToolbar {...defaultProps} saveStatus="error" onRetry={onRetry} />);
            
            await user.click(screen.getByText('Retry'));
            
            expect(onRetry).toHaveBeenCalledTimes(1);
        });

        it('formats last save time correctly', () => {
            const recentTime = Date.now() - 30000; // 30 seconds ago
            render(<UnifiedCanvasToolbar {...defaultProps} saveStatus="saved" lastSaveTime={recentTime} />);
            
            expect(screen.getByText('just now')).toBeInTheDocument();
        });
    });

    describe('Help Section', () => {
        it('renders assistant panel button', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} onOpenUnifiedPanel={vi.fn()} />);
            
            expect(screen.getByTitle(/assistant panel/i)).toBeInTheDocument();
        });

        it('shows active state when panel is open', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} onOpenUnifiedPanel={vi.fn()} unifiedPanelOpen={true} />);
            
            const button = screen.getByTitle(/assistant panel/i);
            expect(button).toHaveClass('bg-grey-100');
        });

        it('calls onOpenUnifiedPanel when clicked', async () => {
            const onOpenUnifiedPanel = vi.fn();
            const user = userEvent.setup();
            render(<UnifiedCanvasToolbar {...defaultProps} onOpenUnifiedPanel={onOpenUnifiedPanel} />);
            
            await user.click(screen.getByTitle(/assistant panel/i));
            
            expect(onOpenUnifiedPanel).toHaveBeenCalledTimes(1);
        });

        it('calls onOpenOnboarding when help button is clicked', async () => {
            const onOpenOnboarding = vi.fn();
            const user = userEvent.setup();
            render(<UnifiedCanvasToolbar {...defaultProps} onOpenOnboarding={onOpenOnboarding} />);
            
            await user.click(screen.getByTitle(/tutorial/i));
            
            expect(onOpenOnboarding).toHaveBeenCalledTimes(1);
        });
    });

    describe('Prop Aliases', () => {
        it('accepts currentMode as alias for mode', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} currentMode="code" mode={undefined} />);
            
            expect(screen.getByText('Code')).toBeInTheDocument();
        });

        it('accepts currentLevel as alias for abstractionLevel', () => {
            render(<UnifiedCanvasToolbar {...defaultProps} currentLevel="file" abstractionLevel={undefined} />);
            
            expect(screen.getByText('File')).toBeInTheDocument();
        });

        it('accepts onValidationPanelToggle as alias for onOpenValidation', async () => {
            const onValidationPanelToggle = vi.fn();
            const user = userEvent.setup();
            render(<UnifiedCanvasToolbar {...defaultProps} onValidationPanelToggle={onValidationPanelToggle} />);
            
            await user.click(screen.getByText('100'));
            
            expect(onValidationPanelToggle).toHaveBeenCalledTimes(1);
        });
    });
});
