import { fireEvent, render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CanvasModeSelector } from '../CanvasModeSelector';
import { useCanvasMode, useCanvasModeShortcuts } from '../../../hooks/useCanvasMode';
import { CANVAS_MODE_CONFIG, CANVAS_MODES, type CanvasMode } from '../../../types/canvasMode';
import { LifecyclePhase } from '../../../types/lifecycle';

vi.mock('../../../hooks/useCanvasMode', () => ({
    useCanvasMode: vi.fn(),
    useCanvasModeShortcuts: vi.fn(),
}));

const mockedUseCanvasMode = vi.mocked(useCanvasMode);
const mockedUseCanvasModeShortcuts = vi.mocked(useCanvasModeShortcuts);

describe('CanvasModeSelector', () => {
    const setMode = vi.fn();

    beforeEach(() => {
        setMode.mockReset();
        mockedUseCanvasModeShortcuts.mockReset();
        mockedUseCanvasMode.mockReturnValue({
            currentMode: 'diagram',
            modeConfig: CANVAS_MODE_CONFIG.diagram,
            allModes: CANVAS_MODES,
            recommendedModes: ['diagram', 'design'],
            setMode,
            autoSwitchEnabled: true,
            setAutoSwitch: vi.fn(),
            canAddNodeType: () => true,
            availableTools: [],
            aiContext: '',
            lifecyclePhase: LifecyclePhase.SHAPE,
            isRecommendedMode: true,
            shortcuts: CANVAS_MODES.map((mode: CanvasMode) => ({
                key: CANVAS_MODE_CONFIG[mode].shortcut,
                mode,
                label: CANVAS_MODE_CONFIG[mode].label,
            })),
        });
    });

    it('renders tab modes as shared UI buttons and preserves selected semantics', () => {
        render(<CanvasModeSelector />);

        const diagramTab = screen.getByRole('tab', { name: /diagram/i });

        expect(diagramTab).toHaveAttribute('aria-selected', 'true');
        expect(diagramTab).toHaveClass('inline-flex');
        expect(mockedUseCanvasModeShortcuts).toHaveBeenCalled();
    });

    it('selects a mode through tabs and notifies the caller', () => {
        const onModeChange = vi.fn();

        render(<CanvasModeSelector onModeChange={onModeChange} />);

        fireEvent.click(screen.getByRole('tab', { name: /code/i }));

        expect(setMode).toHaveBeenCalledWith('code');
        expect(onModeChange).toHaveBeenCalledWith('code');
    });

    it('renders compact visible and hidden modes as shared UI buttons', () => {
        render(<CanvasModeSelector variant="compact" />);

        expect(screen.getByTitle('Diagram')).toHaveClass('inline-flex');
        expect(screen.getByTitle('More modes')).toHaveClass('inline-flex');

        const compactSelector = screen.getByTitle('More modes').closest('div');

        expect(compactSelector).not.toBeNull();
        expect(within(compactSelector as HTMLElement).getByRole('button', { name: /code/i })).toHaveClass('inline-flex');
    });
});
